package net.cumba.datatable.impl.databuffer;

import java.util.Arrays;

import net.cumba.datatable.values.MissingValue;
import org.jspecify.annotations.Nullable;

/**
 * Per-row side table for a primitive numeric buffer that reserves one raw value (its
 * {@code MIN_VALUE}) as the missing sentinel. It keeps what the single sentinel cannot express:
 * WHICH {@link MissingValue} a sentinel slot holds, and whether it holds a real {@code MIN_VALUE}
 * rather than a missing.
 *
 * <p>
 * The table is allocated on first need only. A plain missing ({@code null} or
 * {@link MissingValue#MIS}) is the sentinel alone and never allocates it, so a column holding only
 * ordinary values and plain missings costs nothing beyond its primitive array. The first special
 * missing ({@code .A}-{@code .Z}, {@code ._}, {@link MissingValue#MIS_UNKNOWN},
 * {@link MissingValue#MIS_ERROR}) or real {@code MIN_VALUE} allocates one {@code byte} per slot.
 * </p>
 *
 * <p>
 * A slot's code is meaningful only while the buffer's raw slot holds the sentinel. The code is the
 * missing value's own {@link MissingValue#getValue()} byte, so
 * {@code missingFor(codeFor(mv)) == mv} for every constant; {@link #MIS_CODE} and
 * {@link #PRESENT_MIN} collide with none of them.
 * </p>
 */
final class NumericMissingCodes
{

    /**
     * No entry: a sentinel slot is plain {@link MissingValue#MIS}.
     */
    static final byte MIS_CODE = 0;

    /**
     * The sentinel slot holds a real, present {@code MIN_VALUE}, not a missing.
     */
    static final byte PRESENT_MIN = -1;

    private byte @Nullable [] codes;

    /**
     * The code for a missing value written into a sentinel slot.
     *
     * @param aMissing
     *            the missing value, or {@code null} for a raw null cell (read back as
     *            {@link MissingValue#MIS}).
     * @return {@link #MIS_CODE} for {@code null} and {@link MissingValue#MIS}, otherwise the
     *         missing value's own byte.
     */
    static byte codeFor(@Nullable MissingValue aMissing)
    {
        if (aMissing == null || aMissing == MissingValue.MIS)
        {
            return MIS_CODE;
        }
        return aMissing.getValue();
    }


    /**
     * The missing value a sentinel slot with the given code stands for.
     *
     * @param aCode
     *            a code other than {@link #PRESENT_MIN}.
     * @return the missing value.
     */
    static MissingValue missingFor(byte aCode)
    {
        if (aCode == MIS_CODE)
        {
            return MissingValue.MIS;
        }
        return MissingValue.forValue(aCode);
    }


    /**
     * Record a code for a slot. A {@link #MIS_CODE} never allocates the table.
     *
     * @param aIndex
     *            the slot.
     * @param aCode
     *            the code.
     * @param aCapacity
     *            the buffer's current array length, the length the table is allocated or grown to.
     */
    void set(int aIndex, byte aCode, int aCapacity)
    {
        byte[] table = codes;
        if (table == null)
        {
            if (aCode == MIS_CODE)
            {
                return;
            }
            table = new byte[aCapacity];
            codes = table;
        }
        else if (table.length <= aIndex)
        {
            table = Arrays.copyOf(table, aCapacity);
            codes = table;
        }
        table[aIndex] = aCode;
    }


    /**
     * Reset a slot to {@link #MIS_CODE}, for a write that is not a sentinel. Without it an
     * overwrite of a former special missing would keep its stale code.
     *
     * @param aIndex
     *            the slot.
     */
    void clear(int aIndex)
    {
        byte[] table = codes;
        if (table != null && aIndex < table.length)
        {
            table[aIndex] = MIS_CODE;
        }
    }


    /**
     * The code of a slot.
     *
     * @param aIndex
     *            the slot.
     * @return the code, {@link #MIS_CODE} when the table is not allocated or does not reach it.
     */
    byte get(int aIndex)
    {
        byte[] table = codes;
        if (table == null || aIndex >= table.length)
        {
            return MIS_CODE;
        }
        return table[aIndex];
    }


    /**
     * Follow a resize of the buffer's array (grow, {@code setExpectedSize}, {@code trimToSize}).
     *
     * @param aLength
     *            the buffer's new array length.
     */
    void resize(int aLength)
    {
        byte[] table = codes;
        if (table != null && table.length != aLength)
        {
            codes = Arrays.copyOf(table, aLength);
        }
    }


    /**
     * Estimated heap bytes of the table for a buffer holding the given number of values.
     *
     * @param aSize
     *            the buffer's size.
     * @return {@code 0} while unallocated, otherwise one byte per value.
     */
    long estimatedBytes(int aSize)
    {
        return codes == null ? 0L : aSize;
    }


    /**
     * Whether the table has been allocated.
     *
     * @return true once a special missing or a real {@code MIN_VALUE} has been stored.
     */
    boolean isAllocated()
    {
        return codes != null;
    }
}
