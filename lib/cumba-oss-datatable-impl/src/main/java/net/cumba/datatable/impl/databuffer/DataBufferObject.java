package net.cumba.datatable.impl.databuffer;

import java.util.Arrays;

import net.cumba.datatable.values.MissingValue;
import org.jspecify.annotations.Nullable;

/**
 * A data buffer that stores arbitrary {@link Object} values in an internal {@code Object[]}. This
 * is the most flexible buffer type but provides no compression. It is used for columns whose values
 * do not fit into any of the more specific buffer types.
 */
public class DataBufferObject extends AbstractDataBuffer
{

    private @Nullable Object[] values = new Object[512];

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
    public void setExpectedSize(int aLength)
    {
        values = Arrays.copyOf(values, aLength);
    }


    @Override
    public boolean canStore(@Nullable Object aValue)
    {
        return true;
    }


    protected void setValueImpl(int aIndex, @Nullable Object aElement)
    {
        values[aIndex] = aElement;
    }


    @Override
    public void setValue(int aIndex, @Nullable Object aElement)
    {
        int newSize = aIndex + 1;
        ensureCapacity(newSize);
        setValueImpl(aIndex, aElement);
        size = Math.max(size, newSize);
    }


    @Override
    public @Nullable Object getValue(int aIndex)
    {
        if (aIndex >= size)
        {
            return MissingValue.MIS_ERROR;
        }
        return values[aIndex];
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
        if (aIndex >= size)
        {
            return MissingValue.MIS_ERROR.hashCodeStable();
        }
        @Nullable
        Object v = values[aIndex];
        if (v instanceof MissingValue mv)
        {
            return mv.hashCodeStable();
        }
        return v != null ? v.hashCode() : 0;
    }


    @Override
    public long getEstimatedMemoryBytes()
    {
        long total = (long) size * 8L;
        for (int i = 0; i < size; i++)
        {
            @Nullable
            Object v = values[i];
            if (v instanceof String str)
            {
                total += str.length() + 8L + 40L;
            }
            else if (v instanceof Double || v instanceof Long)
            {
                total += 24L;
            }
            else if (v instanceof MissingValue)
            {
                total += 24L;
            }
        }
        return total;
    }
}
