package net.cumba.datatable.impl.databuffer;

// OSS-IDENTITY datatable-buffers: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;

import net.cumba.datatable.values.MissingValue;
import org.jspecify.annotations.Nullable;

/**
 * A numeric data buffer that stores values in an internal {@code long[]}, one element per row.
 * {@link Long#MIN_VALUE} is the raw missing sentinel: a {@code null} or a {@link MissingValue} is
 * stored as it, and so is a real {@code Long.MIN_VALUE}.
 *
 * <p>
 * Every value round-trips exactly. A plain missing ({@code null}, {@link MissingValue#MIS}) is the
 * sentinel alone. Which missing a sentinel slot holds, or that it holds a present
 * {@code MIN_VALUE}, is kept in a {@link NumericMissingCodes} table that is allocated only when a
 * special missing or a real {@code MIN_VALUE} is first stored. So {@code .A} reads back as
 * {@code .A}, never as {@code .} (owner rulings D34 #2 / #5-2: each missing is its own value).
 * </p>
 *
 * <p>
 * The primitive reads return raw storage, so a missing reads as {@code MIN_VALUE} through
 * {@link #getValueAsLong(int)}, {@link #getValuesAsLong(int, int)} and
 * {@link #getValueAsDouble(int)}, and as {@code 0} through the narrowing reads
 * ({@code getValueAsInt/Short/Byte}): ask {@link #isMissing(int)} first. Used for
 * {@link net.cumba.datatable.values.DataValueType#LONG} value columns and for index storage that
 * exceeds {@code int} range.
 * </p>
 */
public class DataBufferLong extends AbstractNumericDataBuffer
{

    /**
     * Raw missing sentinel. A slot holding it is missing unless its code says
     * {@link NumericMissingCodes#PRESENT_MIN}.
     */
    public static final long MISSING_SENTINEL = Long.MIN_VALUE;

    private long[] values = new long[512];

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
        if (aValue instanceof Double || aValue instanceof Float)
        {
            return canStoreDouble(num.doubleValue());
        }
        if (aValue instanceof BigDecimal || aValue instanceof BigInteger)
        {
            return isExactIntegral(num);
        }
        double dblVal = num.doubleValue();
        long longVal = num.longValue();
        return dblVal == longVal;
    }


    @Override
    public boolean canStoreDouble(double aValue)
    {
        // (long) saturates: 2^63 and above truncate to Long.MAX_VALUE, which converts back to
        // exactly 2^63, so the round trip alone would accept 2^63 and store MAX_VALUE instead.
        // No double equals MAX_VALUE, so excluding it rejects exactly that case.
        long val = (long) aValue;
        return val == aValue && val != Long.MAX_VALUE;
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
        codes.resize(aLength);
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
            byte code = codes.get(aIndex);
            if (code == NumericMissingCodes.PRESENT_MIN)
            {
                return v;
            }
            return NumericMissingCodes.missingFor(code);
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
        return values[aIndex] == MISSING_SENTINEL
                && codes.get(aIndex) != NumericMissingCodes.PRESENT_MIN;
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
            codes.resize(size);
        }
    }


    @Override
    public int hashCodeAt(int aIndex)
    {
        long v = values[aIndex];
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
    public long getEstimatedMemoryBytes()
    {
        return (long) size * Long.BYTES + codes.estimatedBytes(size);
    }


    /**
     * Whether an arbitrary-precision number is an integer that fits in 64 bits. Its
     * {@code doubleValue} rounds, so the double round trip used for the boxed primitives would
     * accept {@code 1000000000000000000.5} and store it truncated.
     */
    private static boolean isExactIntegral(Number aValue)
    {
        BigInteger integral;
        if (aValue instanceof BigDecimal bd)
        {
            BigDecimal stripped = bd.stripTrailingZeros();
            // precision - scale is the number of integer digits; checking it first keeps
            // toBigInteger() from expanding a huge exponent (1E+50000000 took seconds). In long,
            // because a scale near Integer.MIN_VALUE overflows the int difference.
            if (stripped.scale() > 0 || (long) stripped.precision() - stripped.scale() > 19)
            {
                return false;
            }
            integral = stripped.toBigInteger();
        }
        else
        {
            integral = (BigInteger) aValue;
        }
        return integral.bitLength() < 64;
    }
}
