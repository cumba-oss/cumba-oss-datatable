package net.cumba.datatable.provider.sas.sas7bdat;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import lombok.Getter;
import net.cumba.datatable.values.MissingValue;
import net.cumba.sasutils.VariableType;
import net.cumba.sasutils.bdat.VariableBdat;
import org.apache.commons.lang3.ArrayUtils;

/**
 * Parses individual variable values from a BDAT observation byte buffer. Numeric variables are
 * stored as IEEE 754 doubles (potentially in little-endian order). Character variables are decoded
 * using the configured charset.
 */
public class BdatVarParser
{

    private static final Logger LOGGER = System.getLogger(BdatVarParser.class.getName());

    @Getter
    private VariableBdat bdatVar;

    private VariableType type;

    private int length;

    private int offset;

    private Charset charset;

    private ByteOrder byteOrder;

    /**
     * Create a new parser for the given BDAT variable.
     *
     * @param aVar
     *            the BDAT variable descriptor.
     * @param aCharset
     *            the charset for decoding character variables.
     * @param aByteOrder
     *            the byte order used in the BDAT file.
     */
    public BdatVarParser(VariableBdat aVar, Charset aCharset, ByteOrder aByteOrder)
    {
        bdatVar = aVar;
        type = aVar.getType();
        length = aVar.getLength();
        offset = aVar.getOffset().intValue();
        charset = aCharset;
        byteOrder = aByteOrder;
    }


    /**
     * Parse the value of this variable from the given observation byte buffer.
     *
     * @param aBuffer
     *            the observation byte buffer.
     * @return the parsed value: a {@code Double} for numeric variables or a {@code String} for
     *         character variables.
     */
    public Object getValue(byte[] aBuffer)
    {

        return switch (type)
        {
        case NUMERIC -> parseDouble(aBuffer);
        case CHARACTER -> parseString(aBuffer);
        default -> throw new IllegalStateException("Invalid type: " + type);
        };
    }


    /**
     * Parse a numeric value from the observation buffer. Handles byte order reversal for
     * little-endian files and maps SAS NaN missing value bit patterns to {@link MissingValue}
     * encoded doubles.
     *
     * @param aBuffer
     *            the observation byte buffer.
     * @return the parsed IEEE 754 double value, or a NaN encoding a SAS missing value.
     */
    public double parseDouble(byte[] aBuffer)
    {
        // TODO: unpack without copy

        byte[] full = new byte[]
        {
                0, 0, 0, 0, 0, 0, 0, 0
        };
        if (byteOrder == ByteOrder.LITTLE_ENDIAN)
        {
            int subOffset = 8 - length;
            System.arraycopy(aBuffer, offset, full, subOffset, length);
            ArrayUtils.reverse(full);
        }
        else
        {
            System.arraycopy(aBuffer, offset, full, 0, length);
        }

        try
        {
            double res = unpackFloat64(full, 0, 8);
            if (Double.isNaN(res))
            {
                long val = Double.doubleToRawLongBits(res);
                long val2 = 0xFF - ((val >> 40) & 0x7F) - 0x41;
                if (val2 == 0x40)
                {
                    return MissingValue.MIS.asDouble();
                }
                // corej's MissingValue model keeps only MIS / MIS_UNKNOWN / MIS_ERROR. The SAS
                // special-missing values ._ (val2 == 0x3F) and .A-.Z (0x41..0x5A) were dropped in
                // the OSS extraction, so they all collapse to MIS_UNKNOWN here.
                return MissingValue.MIS_UNKNOWN.asDouble();
            }
            return res;
        }
        catch (Exception ex)
        {
            LOGGER.log(Level.ERROR, ex.getMessage(), ex);
            return Double.NaN;
        }

    }


    protected long unpackRaw64(byte[] aBuffer, int aOffset, int aLength)
    {
        if (aLength < 3 || aLength > 8)
        {
            throw new IllegalArgumentException("length must be between 3 and 8 bytes");
        }
        if (aOffset < 0 || aOffset + aLength > aBuffer.length)
        {
            throw new IndexOutOfBoundsException("offset/length out of buffer bounds");
        }

        long res = 0;
        int end = aOffset + aLength;
        for (int i = aOffset; i < end; i++)
        {
            res = (res << 8) | (aBuffer[i] & 0xFF);
        }

        if (aLength < 8)
        {
            // shift further as if this is 8 byte
            int shift = 8 - aLength;
            res = res << shift;
        }

        return res;
    }


    /**
     * Unpack 8 big-endian bytes as an IEEE 754 double.
     *
     * @param val
     *            the byte buffer.
     * @param aOffset
     *            ignored — see {@link #unpackRaw64}.
     * @param aLength
     *            ignored — see {@link #unpackRaw64}.
     * @return the double value.
     */
    protected double unpackFloat64(byte[] val, int aOffset, int aLength)
    {

        long x = unpackRaw64(val, aOffset, aLength);
        return Double.longBitsToDouble(x);
    }


    /**
     * Parse a character value from the observation buffer.
     *
     * @param aBuffer
     *            the observation byte buffer.
     * @return the decoded string value.
     */
    public String parseString(byte[] aBuffer)
    {
        return new String(aBuffer, offset, length, charset);
    }

}
