package net.cumba.datatable.impl.databuffer;

import java.util.Arrays;

import net.cumba.datatable.values.MissingValue;
import org.jspecify.annotations.Nullable;

/**
 * A numeric data buffer that stores values in an internal {@code double[]}. This is the primary
 * buffer for floating-point data. {@link Double#NaN} is reserved as the missing-value encoding; the
 * specific {@link MissingValue} variant is recovered from the NaN payload via
 * {@link MissingValue#forValue(double, MissingValue)}.
 */
public class DataBufferDouble extends AbstractNumericDataBuffer
{

    private double[] values = new double[500];

    protected void ensureCapacity(int aValueCount)
    {
        int oldLen = values.length;

        if (oldLen >= aValueCount)
        {
            // array is big enough
            return;
        }
        int newCapacity = newLength(oldLen, aValueCount - oldLen, oldLen >> 1);

        values = Arrays.copyOf(values, newCapacity);

    }


    @Override
    public boolean canStore(@Nullable Object aValue)
    {
        if (aValue instanceof MissingValue)
        {
            return true;
        }

        if (!(aValue instanceof Number num))
        {
            return false;
        }

        if (aValue instanceof Long)
        {
            // we can't store all long values so we need to check
            double dblVal = num.doubleValue();
            long longVal = num.longValue();
            return dblVal == longVal;
        }
        // we can store all other numbers (expecting only primitive type numbers are used)
        return true;
    }


    @Override
    public boolean canStoreDouble(double aValue)
    {
        return true;
    }


    @Override
    public boolean canStoreLong(long aValue)
    {
        double dblVal = aValue;
        return aValue == (long) dblVal;
    }


    @Override
    public void setExpectedSize(int aLength)
    {
        values = Arrays.copyOf(values, aLength);
    }


    @Override
    public void setDoubleValue(int aIndex, double aValue)
    {
        int newSize = aIndex + 1;
        ensureCapacity(newSize);
        values[aIndex] = aValue;
        size = Math.max(size, newSize);
    }


    @Override
    public void setLongValue(int aIndex, long aValue) throws IllegalArgumentException
    {
        if (!canStoreLong(aValue))
        {
            throw new IllegalArgumentException("Invalid value: " + aValue);
        }
        setDoubleValue(aIndex, (double) aValue);
    }


    @Override
    public void setValue(int aIndex, @Nullable Object aValue)
    {
        if (!canStore(aValue))
        {
            throw new IllegalArgumentException("Invalid value: " + aValue);
        }

        if (aValue instanceof MissingValue mv)
        {
            setDoubleValue(aIndex, mv.asDouble());
        }
        else if (aValue instanceof Number num)
        {
            setDoubleValue(aIndex, num.doubleValue());
        }
        else
        {
            // canStore already rejected null and non-Number values above.
            throw new IllegalArgumentException("Invalid value: " + aValue);
        }
    }


    @Override
    public Object getValue(int aIndex)
    {
        double dblVal = getValueAsDouble(aIndex);
        if (Double.isNaN(dblVal))
        {
            MissingValue mis = MissingValue.forValue(dblVal, null);
            if (mis != null)
            {
                return mis;
            }
        }
        return dblVal;
    }


    @Override
    public boolean isMissing(int aIndex)
    {
        return Double.isNaN(values[aIndex]);
    }


    @Override
    public int hashCodeAt(int aIndex)
    {
        double v = values[aIndex];
        if (Double.isNaN(v))
        {
            int h = MissingValue.hashCodeForNan(v);
            if (h != 0)
            {
                return h;
            }
            // unknown NaN payload — fall through to the numeric hash
        }
        return Double.hashCode(v);
    }


    @Override
    public double getValueAsDouble(int aIndex)
    {
        return values[aIndex];
    }


    @Override
    public long getValueAsLong(int aIndex)
    {
        return (long) values[aIndex];
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
    public double[] getValuesAsDouble(int aStart, int aEnd)
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
        System.arraycopy(values, aStart, res, 0, count);
        return res;
    }


    @Override
    public void trimToSize()
    {
        if (values.length > size)
        {
            values = Arrays.copyOf(values, size);
        }
    }


    @Override
    public long getEstimatedMemoryBytes()
    {
        return (long) size * Double.BYTES;
    }
}
