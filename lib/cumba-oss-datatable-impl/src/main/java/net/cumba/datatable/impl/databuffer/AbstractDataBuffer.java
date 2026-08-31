package net.cumba.datatable.impl.databuffer;

import net.cumba.datatable.values.DataValueBoolean;
import net.cumba.datatable.values.DataValueDouble;
import net.cumba.datatable.values.DataValueLong;
import net.cumba.datatable.values.DataValueMissing;
import net.cumba.datatable.values.DataValueOther;
import net.cumba.datatable.values.DataValueString;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.IDataValue;
import net.cumba.datatable.values.MissingValue;
import org.jspecify.annotations.Nullable;

/**
 * Abstract implementation of a IDataBuffer.
 *
 */
public abstract class AbstractDataBuffer implements IDataBuffer
{

    /**
     * The is a soft maximum length. It is near the absolute technical maximum of a single array
     * length what is 2^31 {@link Integer#MAX_VALUE}.
     */
    public static final int SOFT_MAX_ARRAY_LENGTH = Integer.MAX_VALUE - 8;

    /**
     * Determine the next length of the array if it needs to grow above the current size. This
     * method is taken from jdk.internal.util.ArraysSupport#newLength.
     *
     * @param oldLength
     *            the old length of the array.
     * @param minGrowth
     *            the minimum size to grow for.
     * @param prefGrowth
     *            the preferred size to grow to.
     * @return the new target size of the array.
     */
    protected int newLength(int oldLength, int minGrowth, int prefGrowth)
    {
        int prefLength = oldLength + Math.max(minGrowth, prefGrowth); // might overflow
        if (0 < prefLength && prefLength <= SOFT_MAX_ARRAY_LENGTH)
        {
            return prefLength;
        }
        else if (oldLength < SOFT_MAX_ARRAY_LENGTH)
        {
            return SOFT_MAX_ARRAY_LENGTH;
        }
        throw new OutOfMemoryError("Required length is too large");
    }

    /**
     * The current number of elements in the buffer. This might be less then the size of the value
     * array.<br/>
     * This value should be updates in {@link #setValue(int, Object)}
     */
    protected int size;

    @Override
    public int size()
    {
        return size;
    }


    @Override
    public void addValue(@Nullable Object aElement)
    {
        setValue(size(), aElement);
    }


    @Override
    public IDataValue getDataValue(int aIndex, DataValueType aType)
    {
        if (aIndex >= size())
        {
            return new DataValueMissing(MissingValue.MIS_ERROR);
        }
        return createDataValue(aIndex, aType);
    }


    /**
     * Create an {@link IDataValue} for the value at the given index. This method is called by
     * {@link #getDataValue(int, DataValueType)} after the bounds check. Subclasses can override
     * this to provide optimized implementations.
     *
     * @param aIndex
     *            the index of the value.
     * @param aType
     *            the expected type of the value.
     * @return the data value.
     */
    protected IDataValue createDataValue(int aIndex, DataValueType aType)
    {
        Object val = getValue(aIndex);
        if (val instanceof MissingValue mv)
        {
            return new DataValueMissing(mv);
        }

        switch (aType)
        {
        case BOOLEAN:
            if (val instanceof Boolean bv)
            {
                return new DataValueBoolean(bv);
            }
            return new DataValueMissing(MissingValue.MIS_ERROR);
        case DOUBLE:
            if (val instanceof Number num)
            {
                return new DataValueDouble(num.doubleValue());
            }
            return new DataValueMissing(MissingValue.MIS_ERROR);
        case LONG:
            if (val instanceof Number num)
            {
                return new DataValueLong(num.longValue());
            }
            return new DataValueMissing(MissingValue.MIS_ERROR);
        case MISSING:
            // missing is already handled before so if we are here this is an error
            return new DataValueMissing(MissingValue.MIS_ERROR);
        case STRING:
            // for STRING we map from null to empty string
            return new DataValueString(val != null ? val.toString() : "");
        case OTHER:
        default:
            return val != null ? new DataValueOther(val)
                    : new DataValueMissing(MissingValue.MIS_ERROR);
        }
    }

}
