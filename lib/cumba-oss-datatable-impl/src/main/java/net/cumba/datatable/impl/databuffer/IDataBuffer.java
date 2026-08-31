package net.cumba.datatable.impl.databuffer;

import java.util.List;

import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.IDataValue;
import net.cumba.datatable.values.MissingValue;
import org.jspecify.annotations.Nullable;

/**
 * Interface for a data buffer implementation. A data buffer stores a list of values of the same
 * type. Similar to {@link List} it automatically extends the maximum number of allowed elements and
 * allows to cut down the maximum number of elements.<br/>
 * The intention is to provide storage optimized implementations, especially for columns with a very
 * limited number of possible values.
 */
public interface IDataBuffer
{

    /**
     * Returns the actual number of elements in the buffer.
     *
     * @return the actual number of elements in the buffer.
     */
    int size();


    /**
     * Set the expected number of elements in this data buffer. If the data buffer is currently
     * smaller than the given number of elements, it internally extends the size of the internal
     * storage to fit this amount of elements in. <b>If the internal buffer is already larger, this
     * method truncates the buffer to the given size.</b> <br/>
     * This method can be called at the beginning to avoid intermediate array resize operations.
     *
     * @param aLength
     *            the number of elements to be able to store.
     */
    void setExpectedSize(int aLength);


    /**
     * Check if this data buffer can store the given value in a way, that the same value is
     * retrieved by {@link #getValue(int)}.
     *
     * @param aValue
     *            the value to be stored.
     * @return true if the value can be stored.
     */
    boolean canStore(@Nullable Object aValue);


    /**
     * Add the given value at the end of the data buffer (after the current last element).
     *
     * @param aValue
     *            the value to add.
     */
    default void addValue(@Nullable Object aValue)
    {
        setValue(size(), aValue);
    }


    default void addValue(double aValue)
    {
        setValue(size(), aValue);
    }


    default void addValue(long aValue)
    {
        setValue(size(), aValue);
    }


    /**
     * Set the given value at the given position and remove the value that is currently at this
     * position.
     *
     * @param aIndex
     *            the index to set the value at.
     * @param aValue
     *            the value to set at this position.
     * @throws IllegalArgumentException
     *             in case the given value can not be stored in this buffer.
     */
    void setValue(int aIndex, @Nullable Object aValue) throws IllegalArgumentException;


    /**
     * Set the given value at the given position and remove the value that is currently at this
     * position.
     *
     * @param aIndex
     *            the index to set the value at.
     * @param aValue
     *            the value to set at this position.
     * @throws IllegalArgumentException
     *             in case the given value can not be stored in this buffer.
     */
    default void setValue(int aIndex, double aValue) throws IllegalArgumentException
    {
        setValue(aIndex, Double.valueOf(aValue));
    }


    default void setValue(int aIndex, long aValue) throws IllegalArgumentException
    {
        setValue(aIndex, Long.valueOf(aValue));
    }


    /**
     * Retrieve the value at the given position.
     *
     * @param aIndex
     *            the index to retrieve the value from.
     * @return the value at the given index.
     */
    @Nullable
    Object getValue(int aIndex);


    /**
     * Retrieve the value at the given index as a IDataValue if possible of the given type.
     *
     * @param aIndex
     *            the index to retrieve the value from.
     * @param aType
     *            the preferred type of the data value to retrieve.
     * @return a data value that contains the value at the given index. If possible this is of the
     *         type specified in aType, but might be of a different type.
     */
    IDataValue getDataValue(int aIndex, DataValueType aType);


    /**
     * Check if the value at a given index is a missing value.
     *
     * @param aIndex
     *            the index to check the value at.
     * @return true if the value at the given index is a {@link MissingValue}, false otherwise.
     */
    default boolean isMissing(int aIndex)
    {
        return getValue(aIndex) instanceof MissingValue;
    }


    /**
     * Trim the internal buffer to the current size. This can be called once all values are added to
     * the buffer to reduce the size of the buffer.
     */
    void trimToSize();


    /**
     * Compute a non-boxing hash for the value at the given index. The default implementation falls
     * back to {@link #getValue(int)} and hashes the boxed result; type-specialized buffers override
     * this to read from primitive storage directly and avoid autoboxing.
     * <p>
     * Contract: for any buffer, {@code hashCodeAt(i)} must equal {@code getValue(i).hashCode()}
     * when {@code getValue(i)} returns a non-{@link MissingValue} object, or
     * {@code ((MissingValue) getValue(i)).hashCodeStable()} when it returns a missing value. This
     * keeps the default fallback and typed overrides interchangeable.
     *
     * @param aIndex
     *            the row index to hash.
     * @return the stable hash of the cell.
     */
    default int hashCodeAt(int aIndex)
    {
        Object v = getValue(aIndex);
        if (v instanceof MissingValue mv)
        {
            return mv.hashCodeStable();
        }
        return v != null ? v.hashCode() : 0;
    }


    /**
     * Estimated heap-memory footprint of this buffer in bytes. Covers the backing arrays plus any
     * per-row accounting (dictionary entries for factor buffers, per-row Strings for object/string
     * buffers, etc.). Used for diagnostic logging; intentionally an estimate, not a precise
     * JVM-level measurement. The default returns {@code 0}; concrete buffers override.
     */
    default long getEstimatedMemoryBytes()
    {
        return 0L;
    }
}
