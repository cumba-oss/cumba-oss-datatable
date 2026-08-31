package net.cumba.datatable.impl.databuffer;

/**
 * A {@link IDataBuffer} for numeric values. This defines some extra conversion methods and allows
 * to set and restore values without boxing.
 */
public interface IDataBufferNumeric extends IDataBuffer
{

    /**
     * Check if this data buffer can store the given double value in a way, that the same value is
     * retrieved by {@link #getValueAsDouble(int)}.
     *
     * @param aValue
     *            the value to be stored.
     * @return true if the value can be stored.
     */
    boolean canStoreDouble(double aValue);


    /**
     * Check if this data buffer can store the given value in a way, that the same value is
     * retrieved by {@link #getValueAsLong(int)}.
     *
     * @param aValue
     *            the value to be stored.
     * @return true if the value can be stored.
     */
    boolean canStoreLong(long aValue);


    /**
     * Set the given double as value at the given position.
     *
     * @param aIndex
     *            the index to set the given value at.
     * @param aValue
     *            the value to set.
     * @throws IllegalArgumentException
     *             in case the given value can not be stored in this buffer.
     */
    void setDoubleValue(int aIndex, double aValue) throws IllegalArgumentException;


    /**
     * Set the given long as value at the given position.
     *
     * @param aIndex
     *            the index to set the given value at.
     * @param aValue
     *            the value to set.
     * @throws IllegalArgumentException
     *             in case the given value can not be stored in this buffer.
     */
    void setLongValue(int aIndex, long aValue) throws IllegalArgumentException;


    /**
     * Retrieve the value at the given position as a double.
     *
     * @param aIndex
     *            the index to retrieve the value from.
     * @return the value currently stored at the given position as a primitive double.
     */
    double getValueAsDouble(int aIndex);


    /**
     * Retrieve the value at the given position as a long.
     *
     * @param aIndex
     *            the index to retrieve the value from.
     * @return the value currently stored at the given position as a primitive long.
     */
    default long getValueAsLong(int aIndex)
    {
        return (long) getValueAsDouble(aIndex);
    }


    /**
     * Retrieve the value at the given position as a int.
     *
     * @param aIndex
     *            the index to retrieve the value from.
     * @return the value currently stored at the given position as a primitive int.
     */
    default int getValueAsInt(int aIndex)
    {
        return (int) getValueAsDouble(aIndex);
    }


    /**
     * Retrieve the value at the given position as a short.
     *
     * @param aIndex
     *            the index to retrieve the value from.
     * @return the value currently stored at the given position as a primitive short.
     */
    default short getValueAsShort(int aIndex)
    {
        return (short) getValueAsDouble(aIndex);
    }


    /**
     * Retrieve the value at the given position as a byte.
     *
     * @param aIndex
     *            the index to retrieve the value from.
     * @return the value currently stored at the given position as a primitive byte.
     */
    default byte getValueAsByte(int aIndex)
    {
        return (byte) getValueAsDouble(aIndex);
    }


    /**
     * Retrieve multiple values of the buffer as an array of int.
     *
     * @param aStart
     *            the index of the first value to include in the result.
     * @param aEnd
     *            the index of the first value to not include in the result anymore.
     * @return an array with the requested values.
     */
    default int[] getValuesAsInt(int aStart, int aEnd)
    {
        if (aStart < 0)
        {
            aStart = 0;
        }
        int size = size();
        if (aEnd < 0 || aEnd > size)
        {
            aEnd = size;
        }

        int count = aEnd - aStart;
        if (count < 1)
        {
            return new int[0];
        }

        int[] res = new int[count];
        for (int i = 0; i < count; i++)
        {
            res[i] = getValueAsInt(i + aStart);
        }
        return res;
    }


    /**
     * Retrieve multiple values of the buffer as an array of long.
     *
     * @param aStart
     *            the index of the first value to include in the result.
     * @param aEnd
     *            the index of the first value to not include in the result anymore.
     * @return an array with the requested values.
     */
    default long[] getValuesAsLong(int aStart, int aEnd)
    {
        if (aStart < 0)
        {
            aStart = 0;
        }
        int size = size();
        if (aEnd < 0 || aEnd > size)
        {
            aEnd = size;
        }

        int count = aEnd - aStart;
        if (count < 1)
        {
            return new long[0];
        }
        long[] res = new long[count];
        for (int i = 0; i < count; i++)
        {
            res[i] = getValueAsLong(i + aStart);
        }
        return res;
    }


    /**
     * Retrieve multiple values of the buffer as an array of double.
     *
     * @param aStart
     *            the index of the first value to include in the result.
     * @param aEnd
     *            the index of the first value to not include in the result anymore.
     * @return an array with the requested values.
     */
    default double[] getValuesAsDouble(int aStart, int aEnd)
    {
        if (aStart < 0)
        {
            aStart = 0;
        }
        int size = size();
        if (aEnd < 0 || aEnd > size)
        {
            aEnd = size;
        }

        int count = aEnd - aStart;
        if (count < 1)
        {
            return new double[0];
        }
        double[] res = new double[count];
        for (int i = 0; i < count; i++)
        {
            res[i] = getValueAsDouble(i + aStart);
        }
        return res;
    }
}
