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
 */
package net.cumba.datatable.provider.sas.xpt;

import java.io.IOException;
import java.io.InputStream;
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
import net.cumba.sasutils.xpt.ObservationFrameReaderXpt;
import net.cumba.sasutils.xpt.VariableXpt;
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

    protected ObservationFrameReaderXpt frames;

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
            // The frame reader owns the stream, the pushback, the read buffer and all three
            // end-of-member decisions (F-prov-02 truncated tail, F-prov-11 header-record end,
            // blank padding); this iterator only maps frames to XptObservations.
            frames = new ObservationFrameReaderXpt(aStream, observationSize,
                    member.getObservationStartByte());
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


    protected void readIfNecessary()
    {
        try
        {
            if (needToRead)
            {
                // The frame reader makes all three end-of-member decisions (truncated tail,
                // header-record end, blank padding) and returns null when the member's rows end.
                byte[] frame = frames.nextFrame();
                hasNext = frame != null;
                if (frame != null)
                {
                    buffer = frame;
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
