package net.cumba.datatable.impl.merge;

import java.util.Arrays;

/**
 * A growable list of {@code int}s, reused across the rows of a merge build so the per-row match
 * lists and odometer counters allocate nothing and box nothing (W1 review L7: the build allocated a
 * {@code List<Integer>} entry per match and an {@code int[]} per primary row).
 */
final class IntList
{

    private int[] values;

    private int size;

    IntList()
    {
        this(4);
    }


    IntList(int aCapacity)
    {
        values = new int[Math.max(1, aCapacity)];
    }


    void clear()
    {
        size = 0;
    }


    void add(int aValue)
    {
        if (size == values.length)
        {
            values = Arrays.copyOf(values, values.length * 2);
        }
        values[size++] = aValue;
    }


    int get(int aIndex)
    {
        if (aIndex < 0 || aIndex >= size)
        {
            throw new IndexOutOfBoundsException("index " + aIndex + " not in [0, " + size + ")");
        }
        return values[aIndex];
    }


    int size()
    {
        return size;
    }


    /** Drops every value from {@code aSize} on; a no-op when the list is not longer. */
    void truncate(int aSize)
    {
        if (aSize < 0)
        {
            throw new IndexOutOfBoundsException("size " + aSize);
        }
        size = Math.min(size, aSize);
    }


    boolean isEmpty()
    {
        return size == 0;
    }


    /** Whether the {@code aWidth} values at {@code aFrom} equal those at {@code aOther}. */
    boolean sliceEquals(int aFrom, int aOther, int aWidth)
    {
        return Arrays.equals(values, aFrom, aFrom + aWidth, values, aOther, aOther + aWidth);
    }


    /** A hash of the {@code aWidth} values at {@code aFrom}. */
    int sliceHash(int aFrom, int aWidth)
    {
        int h = 1;
        for (int i = aFrom; i < aFrom + aWidth; i++)
        {
            h = 31 * h + values[i];
        }
        return h;
    }
}
