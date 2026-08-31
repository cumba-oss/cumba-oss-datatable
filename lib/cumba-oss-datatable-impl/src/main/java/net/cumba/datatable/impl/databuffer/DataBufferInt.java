package net.cumba.datatable.impl.databuffer;

import java.util.Arrays;

import net.cumba.datatable.values.MissingValue;
import org.jspecify.annotations.Nullable;

/**
 * A numeric data buffer that stores values in an internal {@code int[]}. Reserves
 * {@link Integer#MIN_VALUE} as the missing-value sentinel — reads of that slot return
 * {@link MissingValue#MIS}, writes of any {@link MissingValue} (or {@code null}) write
 * {@link Integer#MIN_VALUE}. Suitable for boolean value columns and for compact index storage when
 * the maximum stored value fits in {@code int}.
 */
public class DataBufferInt extends AbstractNumericDataBuffer
{

    /**
     * Reserved sentinel — a read of this raw int reports as {@link MissingValue#MIS}.
     */
    public static final int MISSING_SENTINEL = Integer.MIN_VALUE;

    private int[] values = new int[512];

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
    public void setExpectedSize(int aLength)
    {
        values = Arrays.copyOf(values, aLength);
    }


    @Override
    public boolean canStore(@Nullable Object aValue)
    {
        if (aValue == null || aValue instanceof MissingValue || aValue instanceof Boolean)
        {
            return true;
        }
        if (!(aValue instanceof Number num))
        {
            return false;
        }
        long lv = num.longValue();
        return lv >= Integer.MIN_VALUE && lv <= Integer.MAX_VALUE && num.doubleValue() == lv;
    }


    @Override
    public boolean canStoreDouble(double aValue)
    {
        long lv = (long) aValue;
        return aValue == lv && lv >= Integer.MIN_VALUE && lv <= Integer.MAX_VALUE;
    }


    @Override
    public boolean canStoreLong(long aValue)
    {
        return aValue >= Integer.MIN_VALUE && aValue <= Integer.MAX_VALUE;
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
        if (!canStoreLong(aValue))
        {
            throw new IllegalArgumentException("Invalid value: " + aValue);
        }
        int newSize = aIndex + 1;
        ensureCapacity(newSize);
        values[aIndex] = (int) aValue;
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
        if (aValue instanceof Boolean bv)
        {
            setLongValue(aIndex, bv ? 1L : 0L);
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
        int v = values[aIndex];
        if (v == MISSING_SENTINEL)
        {
            return MissingValue.MIS;
        }
        return (long) v;
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
        return values[aIndex];
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
    public int hashCodeAt(int aIndex)
    {
        int v = values[aIndex];
        if (v == MISSING_SENTINEL)
        {
            return MissingValue.MIS.hashCodeStable();
        }
        return Long.hashCode(v);
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
    public long getEstimatedMemoryBytes()
    {
        return (long) size * Integer.BYTES;
    }
}
