package net.cumba.datatable.provider.sas.xpt;

import java.util.List;

import lombok.Getter;

/**
 * Holds a single XPT observation (row) as a raw byte buffer together with the list of variable
 * parsers that can extract typed values from it.
 */
public class XptObservation
{

    @Getter
    private final List<XptVarParser> parsers;

    private final byte[] buffer;

    /**
     * Create a new observation from the given parsers and byte buffer.
     *
     * @param aParsers
     *            the variable parsers for extracting column values.
     * @param aBuffer
     *            the raw observation byte data.
     */
    public XptObservation(List<XptVarParser> aParsers, byte[] aBuffer)
    {
        parsers = aParsers;
        buffer = aBuffer;
    }


    /**
     * Get the value of the variable at the given column index.
     *
     * @param aIndex
     *            the column index.
     * @return the parsed value.
     */
    public Object getValue(int aIndex)
    {
        return parsers.get(aIndex).getValue(buffer);
    }
}
