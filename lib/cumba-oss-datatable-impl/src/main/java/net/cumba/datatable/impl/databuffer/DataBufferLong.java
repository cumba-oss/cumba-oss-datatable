package net.cumba.datatable.impl.databuffer;

import java.util.Arrays;

import net.cumba.datatable.values.MissingValue;
import org.jspecify.annotations.Nullable;

/**
 * A numeric data buffer that stores values in an internal {@code long[]}. Reserves
 * {@link Long#MIN_VALUE} as the missing-value sentinel — reads of that slot return
 * {@link MissingValue#MIS}, writes of any {@link MissingValue} (or {@code null}) write
 * {@link Long#MIN_VALUE}. Used for {@link net.cumba.datatable.values.DataValueType#LONG} value
 * columns and for index storage that exceeds {@code int} range.
 */
public class DataBufferLong extends AbstractNumericDataBuffer
{

    /**
     * Reserved sentinel — a read of this raw long reports as {@link MissingValue#MIS}.
     */
    public static final long MISSING_SENTINEL = Long.MIN_VALUE;

    private long[] values = new long[512];

    protected void ensureCapacity(int aValueCount)
    {
        int oldLen = values.length;
        if (oldLen >= aValueCount)
        {
            return;
        }
        int newCapacity = newLength(oldLen, aValueCount - oldLen, oldLen >> 1);
        values = Arrays.copyOf(values, newCapacity);
    }


    @Override
    public boolean canStore(@Nullable Object aValue)
    {
        if (aValue == null || aValue instanceof MissingValue)
        {
            return true;
        }
        if (!(aValue instanceof Number num))
        {
            return false;
        }
        if (aValue instanceof Long)
        {
            return true;
        }
        double dblVal = num.doubleValue();
        long longVal = num.longValue();
        return dblVal == longVal;
    }


    @Override
    public boolean canStoreDouble(double aValue)
    {
        long val = (long) aValue;
        return val == aValue;
    }


    @Override
    public boolean canStoreLong(long aValue)
    {
        return true;
    }


    @Override
    public void setExpectedSize(int aLength)
    {
        values = Arrays.copyOf(values, aLength);
    }


    @Override
    public void setDoubleValue(int aIndex, double aValue) throws IllegalArgumentException
    {
        if (!canStoreDouble(aValue))
        {
            throw new IllegalArgumentException("Invalid value: " + aValue);
        }
        setLongValue(aIndex, (long) aValue);
    }


    @Override
    public void setLongValue(int aIndex, long aValue) throws IllegalArgumentException
    {
        int newSize = aIndex + 1;
        ensureCapacity(newSize);
        values[aIndex] = aValue;
        size = Math.max(size, newSize);
    }


    @Override
    public void setValue(int aIndex, @Nullable Object aValue)
    {
        if (aValue == null || aValue instanceof MissingValue)
        {
            int newSize = aIndex + 1;
            ensureCapacity(newSize);
            values[aIndex] = MISSING_SENTINEL;
            size = Math.max(size, newSize);
            return;
        }
        if (!canStore(aValue))
        {
            throw new IllegalArgumentException("Invalid value: " + aValue);
        }
        setLongValue(aIndex, ((Number) aValue).longValue());
    }


    @Override
    public Object getValue(int aIndex)
    {
        long v = values[aIndex];
        if (v == MISSING_SENTINEL)
        {
            return MissingValue.MIS;
        }
        return v;
    }


    @Override
    public double getValueAsDouble(int aIndex)
    {
        return values[aIndex];
    }


    @Override
    public long getValueAsLong(int aIndex)
    {
        return values[aIndex];
    }


    @Override
    public int getValueAsInt(int aIndex)
    {
        return (int) values[aIndex];
    }


    @Override
    public byte getValueAsByte(int aIndex)
    {
        return (byte) values[aIndex];
    }


    @Override
    public short getValueAsShort(int aIndex)
    {
        return (short) values[aIndex];
    }


    @Override
    public boolean isMissing(int aIndex)
    {
        return values[aIndex] == MISSING_SENTINEL;
    }


    @Override
    public long[] getValuesAsLong(int aStart, int aEnd)
    {
        if (aStart < 0)
        {
            aStart = 0;
        }
        int sz = size();
        if (aEnd < 0 || aEnd > sz)
        {
            aEnd = sz;
        }
        int count = aEnd - aStart;
        if (count < 1)
        {
            return new long[0];
        }
        long[] res = new long[count];
        System.arraycopy(values, aStart, res, 0, count);
        return res;
    }


    @Override
    public void trimToSize()
    {
        if (values.length != size)
        {
            values = Arrays.copyOf(values, size);
        }
    }


    @Override
    public int hashCodeAt(int aIndex)
    {
        long v = values[aIndex];
        if (v == MISSING_SENTINEL)
        {
            return MissingValue.MIS.hashCodeStable();
        }
        return Long.hashCode(v);
    }


    @Override
    public long getEstimatedMemoryBytes()
    {
        return (long) size * Long.BYTES;
    }
}
