package net.cumba.datatable.provider.sas.xpt;

import java.nio.charset.Charset;

import net.cumba.datatable.values.MissingValue;
import net.cumba.sasutils.VariableType;
import net.cumba.sasutils.xpt.VariableXpt;

/**
 * Parses individual variable values from an XPT observation byte buffer. Numeric variables are
 * converted from IBM System/360 floating-point format to IEEE 754 double. Character variables are
 * decoded using the configured charset.
 */
public class XptVarParser
{

    private VariableXpt variable;

    private VariableType type;

    private int length;

    private int offset;

    private Charset charset;

    /**
     * Create a new parser for the given XPT variable.
     *
     * @param aVariable
     *            the XPT variable descriptor.
     * @param aOffset
     *            the byte offset of this variable within the observation buffer.
     * @param aCharset
     *            the charset for decoding character variables.
     */
    public XptVarParser(VariableXpt aVariable, int aOffset, Charset aCharset)
    {
        variable = aVariable;
        type = variable.getType();
        offset = aOffset;
        length = variable.getLength();
        charset = aCharset;
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
     * Parse a numeric value from the observation buffer using IBM-to-IEEE conversion.
     *
     * @param aBuffer
     *            the observation byte buffer.
     * @return the parsed IEEE 754 double value.
     */
    public double parseDouble(byte[] aBuffer)
    {
        return ibmToIeee(aBuffer, offset, variable.length);
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


    /**
     * Convert an IBM System/360 floating-point number to an IEEE 754 double. Handles SAS missing
     * values encoded as special byte patterns when the mantissa is zero: {@code '.'} maps to
     * {@link MissingValue#MIS}, {@code '_'} to {@link MissingValue#MIS__}, and
     * {@code 'A'}-{@code 'Z'} to the per-letter constants {@link MissingValue#MIS_A} onwards;
     * anything else falls back to {@link MissingValue#MIS_UNKNOWN}.
     *
     * @param aBuffer
     *            the byte buffer containing the IBM float.
     * @param aOffset
     *            the byte offset within the buffer.
     * @param aLength
     *            the number of bytes (1-8) comprising the IBM float.
     * @return the IEEE 754 double value, or a NaN encoding a SAS missing value.
     */
    public static double ibmToIeee(byte[] aBuffer, int aOffset, int aLength)
    {
        // Extract sign bit (bit 0)
        int sign = (aBuffer[aOffset] & 0x80) != 0 ? 1 : 0;

        // Extract exponent (bits 1-7, biased by 64)
        int exponent = aBuffer[aOffset] & 0x7F;

        // Extract mantissa (56 bits in bytes 1-7)
        long mantissa = 0;
        for (int i = 1; i < aLength; i++)
        {
            mantissa = (mantissa << 8) | (aBuffer[aOffset + i] & 0xFF);
        }

        if (aLength < 8)
        {
            // handle numbers with length < 8
            mantissa = mantissa << ((8 - aLength) * 8);
        }

        // Handle zero
        if (mantissa == 0)
        {
            switch (aBuffer[aOffset] & 0xFF)
            {
            case 0x00 ->
            {
                return 0.0d;
            }
            case 0x80 ->
            {
                return -0.0d;
            }
            case '.' ->
            {
                return MissingValue.MIS.asDouble();
            }
            case '_' ->
            {
                return MissingValue.MIS__.asDouble();
            }
            default ->
            {
                /* fall through to letter handling below */ }
            }

            if (aBuffer[aOffset] >= 'A' && aBuffer[aOffset] <= 'Z')
            {
                int diff = aBuffer[aOffset] - 'A';
                int mv = MissingValue.MIS_A.getValue() + diff;
                return MissingValue.forValue(mv).asDouble();
            }
            return MissingValue.MIS_UNKNOWN.asDouble();
        }

        double value = mantissa / Math.pow(2, 56);
        value *= Math.pow(16, (double) exponent - 64);

        return sign != 0 ? -value : value;
    }

}
