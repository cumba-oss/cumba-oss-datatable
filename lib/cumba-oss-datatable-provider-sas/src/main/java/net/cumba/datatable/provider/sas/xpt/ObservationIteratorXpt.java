/*
 * Derived from theshoeshiner/sas-utils (https://github.com/theshoeshiner/sas-utils), licensed under
 * the Apache License, Version 2.0. This class is a copy of that project's XPT observation iterator,
 * carried here so the provider can stream observations without going through the reader library's
 * own model; a sibling copy lives in the sas-utils reader module.
 *
 * Changed by P300: repackaged to net.cumba.datatable.provider.sas.xpt, adapted to the datatable
 * provider's row model, annotated for null-safety, and adapted to this project's build and
 * static-analysis gates. See this module's README.md for the full attribution notice and
 * LICENSE-APACHE-2.0.txt for the licence.
 *
 * ⚠ The internal twin delegates the observation FRAMING (truncated tail, header-record end, blank
 * padding) to net.cumba.sasutils.xpt.ObservationFrameReaderXpt. That class first ships in the
 * cumba-oss-formats release AFTER 0.4.0, and this repo only pins releases resolvable from Maven
 * Central - so this copy still carries its own framing, byte-for-byte the internal pre-extraction
 * form. Replace it with the frame-reader delegation when the formats pin moves past 0.4.0.
 */
package net.cumba.datatable.provider.sas.xpt;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import lombok.Getter;
import lombok.Setter;
import net.cumba.sasutils.xpt.DatasetXpt;
import net.cumba.sasutils.xpt.VariableXpt;
import net.cumba.sasutils.xpt.XptConstants;
import org.apache.commons.io.IOUtils;
import org.jspecify.annotations.Nullable;
import org.thshsh.struct.Struct;

/**
 * This class is used so that we can stream Observations into memory and not have to read them all
 * at once
 *
 * @author daniel.watson
 *
 */
public class ObservationIteratorXpt implements Iterator<XptObservation>
{

    /**
     * The default charset used for character value parsing.<br/>
     * The SAS v5 XPORT engine officially supports only latin1 7bit chars, but SAS itself simply
     * exports the data with the session encoding. Unfortunately XPT does not store the used
     * encoding.<br/>
     * We use UTF-8 as default here as this is the standard encoding in most recent versions of SAS.
     */
    public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    public static final byte SENTINEL = ' ';

    public static final char NO_VALUE = '.';

    // IBM numeric values are big endian unsigned longs
    public static final Struct<?> IBM = Struct.create(">Q");

    protected int observationSize = 0;

    protected byte[] buffer;

    protected PushbackInputStream input;

    protected DatasetXpt member;

    protected @Nullable Boolean hasNext = null;

    protected Boolean needToRead = true;

    /**
     * The charset to be used when parsing character values from the XPT file.
     */
    @Getter
    @Setter
    private Charset charset = DEFAULT_CHARSET;

    private List<XptVarParser> parsers;

    public ObservationIteratorXpt(DatasetXpt aDataSet, InputStream aStream)
    {
        this(aDataSet, DEFAULT_CHARSET, aStream);
    }


    public ObservationIteratorXpt(DatasetXpt aDataSet, Charset aCharset, InputStream aStream)
    {

        member = aDataSet;
        // F-prov-11: a HEADER RECORD tag can straddle two observation-sized reads, and the
        // comparison below then needs bytes that are not in the buffer yet. Pushback lets it
        // complete the 20-byte tag from the stream and put the bytes back when they turn out to
        // be real observation data, so the discriminator stays the FULL tag - never a prefix.
        input = new PushbackInputStream(aStream, HEADER_TAG_LENGTH);
        charset = aCharset;

        // observations are stored as a packed struct consisting of either bytes or characters for
        // each variable
        parsers = new ArrayList<>();

        int offset = 0;
        for (VariableXpt variable : member.getVariables())
        {
            int varLen = variable.getLength();
            parsers.add(new XptVarParser(variable, offset, charset));
            offset += varLen;
        }
        observationSize = offset;

        try
        {
            IOUtils.skip(input, member.getObservationStartByte());
        }
        catch (IOException e)
        {
            throw new IllegalArgumentException(e);
        }
        buffer = new byte[observationSize];

    }


    @Override
    public boolean hasNext()
    {
        readIfNecessary();
        // readIfNecessary() always assigns hasNext; Boolean.TRUE.equals tolerates the @Nullable
        // field type and avoids an unboxing NPE if it were ever left unset.
        return Boolean.TRUE.equals(hasNext);
    }

    private final byte[] headerBytes = XptConstants.HEADER_TAG
            .getBytes(StandardCharsets.ISO_8859_1);

    /** Length of {@link XptConstants#HEADER_TAG} in bytes - it is pure 7-bit ASCII. */
    private static final int HEADER_TAG_LENGTH = XptConstants.HEADER_TAG.length();

    protected void readIfNecessary()
    {
        try
        {
            if (needToRead)
            {
                int read = IOUtils.read(input, buffer);
                if (read != observationSize)
                {
                    // F-prov-02: IOUtils.read only returns fewer bytes at end of stream. XPT
                    // records are padded to 80-byte boundaries, not observation boundaries,
                    // so a clean end of the observation section may still deliver a short,
                    // all-sentinel tail - but any observation DATA in a short read means the
                    // stream ends mid-observation, i.e. the file is truncated. XPT stores no
                    // row count, so nothing downstream could ever detect the silently lost
                    // rows; the truncation must fail loudly here.
                    for (int i = 0; i < read; i++)
                    {
                        if (buffer[i] != SENTINEL)
                        {
                            throw new IOException(
                                    "Truncated XPT file: the stream ends %d byte(s) into an observation of %d bytes."
                                            .formatted(read, observationSize));
                        }
                    }
                    hasNext = false;
                }
                else
                {
                    hasNext = false;
                    for (int i = 0; i < buffer.length; i++)
                    {
                        byte b = buffer[i];
                        if (b != SENTINEL)
                        {
                            // 'H' == the first byte of "HEADER RECORD*******": the member's
                            // observation section has ended and the next library record begins
                            // inside this read. Everything before i is record padding.
                            if (b == 'H' && startsHeaderRecordAt(i))
                            {
                                // a header, not data --> hasNext stays false
                                break;
                            }
                            hasNext = true;
                            break;
                        }
                    }
                }
                needToRead = false;
            }
        }
        catch (IOException e)
        {
            // F-D16: wrap the IOException as an IllegalStateException so the existing
            // Iterator contract (no checked throws) is preserved. The caller in
            // XptTableProvider.provide unwraps this back into an IOException.
            throw new IllegalStateException(e);
        }
    }


    /**
     * Whether the bytes at {@code aIndex} in {@link #buffer} begin the
     * {@value XptConstants#HEADER_TAG} record that terminates a member's observation section.
     *
     * <p>
     * F-prov-11: the tag is 20 bytes and the buffer is one <em>observation</em> long, so the tag
     * can start so late in the buffer that it is only partly there - and XPT pads the data section
     * to an 80-byte record boundary rather than to an observation boundary, so where the tag falls
     * inside the buffer is just {@code (paddedDataBytes mod observationSize)} and every offset is
     * reachable. The predecessor check was {@code i < buffer.length - 20}, which skipped the
     * comparison in that case <em>and</em> in the exactly-fits case {@code i == buffer.length - 20}
     * where all 20 bytes are present. Either way the tag was read as observation DATA: one garbage
     * row built out of the next member's descriptor records, then more, until a read happened to
     * come back all-sentinel. XPT stores no row count, so nothing downstream could notice.
     * </p>
     *
     * <p>
     * When the tag is split, the missing bytes are read from the stream and pushed back unless they
     * complete the tag - so the test remains an exact 20-byte match and never a prefix match. A
     * prefix match would be unsafe in the other direction: a character value of {@code "H"} at an
     * observation's last byte would end the dataset early and silently drop every remaining row.
     * </p>
     *
     * @param aIndex
     *            the index in {@link #buffer} of the candidate first tag byte.
     * @return {@code true} when the full tag is present (in the buffer, or completed from the
     *         stream); {@code false} for observation data.
     * @throws IOException
     *             if completing the tag from the stream fails.
     */
    private boolean startsHeaderRecordAt(int aIndex) throws IOException
    {
        int inBuffer = buffer.length - aIndex;
        int compare = Math.min(inBuffer, HEADER_TAG_LENGTH);
        if (!Arrays.equals(buffer, aIndex, aIndex + compare, headerBytes, 0, compare))
        {
            return false;
        }
        if (inBuffer >= HEADER_TAG_LENGTH)
        {
            return true;
        }
        byte[] rest = new byte[HEADER_TAG_LENGTH - inBuffer];
        int read = IOUtils.read(input, rest);
        if (read == rest.length
                && Arrays.equals(rest, 0, read, headerBytes, inBuffer, HEADER_TAG_LENGTH))
        {
            // A real header: the iterator stops here, so the consumed bytes are not needed
            // again. Pushing them back anyway keeps the stream position honest for any caller
            // that reads on after us.
            input.unread(rest, 0, read);
            return true;
        }
        if (read > 0)
        {
            input.unread(rest, 0, read);
        }
        return false;
    }


    @Override
    public XptObservation next()
    {

        readIfNecessary();
        if (!Boolean.TRUE.equals(hasNext))
        {
            throw new NoSuchElementException();
        }
        needToRead = true;

        return new XptObservation(parsers, Arrays.copyOf(buffer, buffer.length));
    }

}
