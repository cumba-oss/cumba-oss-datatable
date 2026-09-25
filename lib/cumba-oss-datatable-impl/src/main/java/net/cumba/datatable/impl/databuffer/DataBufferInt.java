package net.cumba.datatable.impl.databuffer;

// OSS-IDENTITY datatable-buffers: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;

import net.cumba.datatable.values.MissingValue;
import org.jspecify.annotations.Nullable;

/**
 * A numeric data buffer that stores values in an internal {@code int[]}, one element per row.
 * {@link Integer#MIN_VALUE} is the raw missing sentinel: a {@code null} or a {@link MissingValue}
 * is stored as it, and so is a real {@code Integer.MIN_VALUE}.
 *
 * <p>
 * Every value round-trips exactly. A plain missing ({@code null}, {@link MissingValue#MIS}) is the
 * sentinel alone. Which missing a sentinel slot holds, or that it holds a present
 * {@code MIN_VALUE}, is kept in a {@link NumericMissingCodes} table that is allocated only when a
 * special missing or a real {@code MIN_VALUE} is first stored (owner rulings D34 #2 / #5-2: each
 * missing is its own value).
 * </p>
 *
 * <p>
 * The primitive reads return raw storage, so a missing reads as {@code MIN_VALUE} through
 * {@link #getValueAsLong(int)}, {@link #getValueAsInt(int)} and {@link #getValueAsDouble(int)}, and
 * as {@code 0} through {@code getValueAsShort/Byte}: ask {@link #isMissing(int)} first. Suitable
 * for boolean value columns and for compact index storage when the maximum stored value fits in
 * {@code int}.
 * </p>
 */
public class DataBufferInt extends AbstractNumericDataBuffer
{

    /**
     * Raw missing sentinel. A slot holding it is missing unless its code says
     * {@link NumericMissingCodes#PRESENT_MIN}.
     */
    public static final int MISSING_SENTINEL = Integer.MIN_VALUE;

    private int[] values = new int[512];

    private final NumericMissingCodes codes = new NumericMissingCodes();

    protected void ensureCapacity(int aValueCount)
    {
        int oldLen = values.length;
        if (oldLen >= aValueCount)
        {
            return;
        }
        int newCapacity = newLength(oldLen, aValueCount - oldLen, oldLen >> 1);
        values = Arrays.copyOf(values, newCapacity);
        codes.resize(newCapacity);
    }


    @Override
    public void setExpectedSize(int aLength)
    {
        values = Arrays.copyOf(values, aLength);
        codes.resize(aLength);
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
        if (aValue instanceof BigDecimal || aValue instanceof BigInteger)
        {
            return isExactIntegral(num);
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
        if (aValue == MISSING_SENTINEL)
        {
            codes.set(aIndex, NumericMissingCodes.PRESENT_MIN, values.length);
        }
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
            codes.set(aIndex, NumericMissingCodes.codeFor((MissingValue) aValue), values.length);
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
            byte code = codes.get(aIndex);
            if (code == NumericMissingCodes.PRESENT_MIN)
            {
                return (long) v;
            }
            return NumericMissingCodes.missingFor(code);
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
        return values[aIndex] == MISSING_SENTINEL
                && codes.get(aIndex) != NumericMissingCodes.PRESENT_MIN;
    }


    @Override
    public int hashCodeAt(int aIndex)
    {
        int v = values[aIndex];
        if (v == MISSING_SENTINEL)
        {
            byte code = codes.get(aIndex);
            if (code != NumericMissingCodes.PRESENT_MIN)
            {
                return NumericMissingCodes.missingFor(code).hashCodeStable();
            }
        }
        return Long.hashCode(v);
    }


    @Override
    public void trimToSize()
    {
        if (values.length != size)
        {
            values = Arrays.copyOf(values, size);
            codes.resize(size);
        }
    }


    @Override
    public long getEstimatedMemoryBytes()
    {
        return (long) size * Integer.BYTES + codes.estimatedBytes(size);
    }


    /**
     * Whether an arbitrary-precision number is an integer that fits in 32 bits. Its
     * {@code doubleValue} rounds, so the double round trip used for the boxed primitives would
     * accept {@code 1000000000000000000.5} and store it truncated.
     */
    private static boolean isExactIntegral(Number aValue)
    {
        BigInteger integral;
        if (aValue instanceof BigDecimal bd)
        {
            if (bd.stripTrailingZeros().scale() > 0)
            {
                return false;
            }
            integral = bd.toBigInteger();
        }
        else
        {
            integral = (BigInteger) aValue;
        }
        return integral.bitLength() < 32;
    }
}
