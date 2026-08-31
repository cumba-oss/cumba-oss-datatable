package net.cumba.datatable;

import java.util.stream.LongStream;
import java.util.stream.Stream;
import net.cumba.datatable.values.IDataValue;
import net.cumba.datatable.values.MissingValue;
import org.jspecify.annotations.Nullable;

/**
 * Represents a column in an {@link IDataTable} for access to values in this column.
 *
 * @see IDataTableRow
 */
public interface IDataTableColumn
{

    /**
     * Returns the number of rows in this column.
     *
     * @return the number of rows in this column.
     */
    long getRowCount();


    /**
     * Retrieve a value from the column in its original data type. This is equivalent to a call to
     * {@link IDataTable#getValue(long, int)} with the index of this column as column.
     *
     * @param aRow
     *            the 0-based index of the row to retrieve the value from.<br/>
     *            Valid range is <code>0 &lt;= aRow &lt; rowCount</code>.
     * @return the data value in native type.
     * @throws IndexOutOfBoundsException
     *             if aRow is outside of the valid bounds.
     */
    @Nullable
    Object getValue(long aRow) throws IndexOutOfBoundsException;


    /**
     * Returns a stream that contains all values of this column in the order the values are in this
     * column.
     *
     * @return a stream that contains all values of this column in the order the values are in this
     *         column.
     */
    default Stream<Object> getValues()
    {
        return getValues(0, getRowCount());
    }


    /**
     * Retrieve a stream that contains a range of the values of this column.
     *
     * @param aStartRowIndex
     *            the row index of the first value to retrieve in the stream.
     * @param aEndRowIndex
     *            the row index of the first value to not retrieve in the stream anymore.
     * @return a stream that contains the values of the requested range of the column.
     */
    default Stream<Object> getValues(long aStartRowIndex, long aEndRowIndex)
    {
        long rc = getRowCount();
        if (aStartRowIndex < 0 || aStartRowIndex >= rc)
        {
            throw new IndexOutOfBoundsException(
                    ExMsgs.indexOutOfBounds("startRowIndex", aStartRowIndex, 0, rc));
        }
        long end = aEndRowIndex;
        end = (end < 0) ? rc : Math.min(end, rc);
        if (end < aStartRowIndex)
        {
            throw new IllegalArgumentException(
                    "The end row (%d) must be >= start row (%d).".formatted(end, aStartRowIndex));
        }

        return LongStream.range(aStartRowIndex, end).mapToObj(this::getValue);
    }


    /**
     * Retrieve a single value of the column as a IDataValue.
     *
     * @param aRow
     *            the 0-based index of the row to retrieve the value from.<br/>
     *            Valid range is <code>0 &lt;= aRow &lt; rowCount</code>.
     * @return the data value.
     * @throws IndexOutOfBoundsException
     *             if aRow is outside of the valid bounds.
     */
    IDataValue getDataValue(long aRow) throws IndexOutOfBoundsException;


    /**
     * Returns a stream of data values from this column.
     *
     * @return a stream of data values from this column.
     */
    default Stream<IDataValue> getDataValues()
    {
        return getDataValues(0, getRowCount());
    }


    /**
     * Returns a stream of the given range of data values from this column.
     *
     * @param aStartRowIndex
     *            the index of the first row in this range.
     * @param aEndRowIndex
     *            the index of the first row not anymore in this range. This can be -1 to include
     *            all rows from aStartRowIndex (to end of table).
     * @return a stream of the given range of data values from this column.
     */
    default Stream<IDataValue> getDataValues(long aStartRowIndex, long aEndRowIndex)
    {
        long rc = getRowCount();
        if (aStartRowIndex < 0 || aStartRowIndex >= rc)
        {
            throw new IndexOutOfBoundsException(
                    ExMsgs.indexOutOfBounds("startRowIndex", aStartRowIndex, 0, rc));
        }
        long end = aEndRowIndex;
        end = (end < 0) ? rc : Math.min(end, rc);
        if (end < aStartRowIndex)
        {
            throw new IllegalArgumentException(
                    "The end row (%d) must be >= start row (%d).".formatted(end, aStartRowIndex));
        }

        return LongStream.range(aStartRowIndex, end).mapToObj(this::getDataValue);
    }


    /**
     * Compute a non-boxing hash for the value at the given row. The default implementation falls
     * back to {@link #getValue(long)} and hashes the boxed result; buffer-backed columns override
     * this to route directly into the underlying data buffer's typed hash path.
     * <p>
     * Contract: for any column, {@code hashCodeAt(row)} must equal {@code getValue(row).hashCode()}
     * when {@code getValue(row)} returns a non-{@link MissingValue} object, or
     * {@link MissingValue#hashCodeStable()} when it returns a missing value. Bounds semantics match
     * {@link #getValue(long)} — invalid row indices throw {@link IndexOutOfBoundsException}.
     *
     * @param aRow
     *            the 0-based row index to hash.
     * @return the stable hash of the cell.
     * @throws IndexOutOfBoundsException
     *             if {@code aRow} is outside of {@code [0, getRowCount())}.
     */
    default int hashCodeAt(long aRow) throws IndexOutOfBoundsException
    {
        Object v = getValue(aRow);
        if (v instanceof MissingValue mv)
        {
            return mv.hashCodeStable();
        }
        return v != null ? v.hashCode() : 0;
    }


    /**
     * A fast path to check if a value at the given row is missing or null. Basically it should
     * never be null (always missing) but just in case.
     *
     * @param aRow
     *            the 0-based index of the row to check for missing.<br/>
     *            Valid range is <code>0 &lt;= aRow &lt; rowCount</code>.
     * @return true if the value at the given row is missing or null.
     * @throws IndexOutOfBoundsException
     *             if either aRow or aColumn is outside of the valid bounds.
     */
    default boolean isMissingOrNull(long aRow) throws IndexOutOfBoundsException
    {
        Object v = getValue(aRow);
        return v == null || v instanceof MissingValue;
    }


    /**
     * A fast path to check whether the cell at the given row carries <b>no usable value</b> — it is
     * {@code null}, a {@link MissingValue}, or a zero-length {@link String}.
     * <p>
     * This is the <em>blankness</em> notion rule evaluation needs, and it is deliberately wider
     * than {@link #isMissingOrNull(long)}: a source {@code null} in a character column (which the
     * Dataset-JSON and Parquet loaders represent as a {@link MissingValue}) and an empty string the
     * file genuinely contains are <b>both</b> blank, so a rule cannot tell the two apart. Only the
     * grouping-key encoding keeps them as distinct keys.
     * </p>
     * <p>
     * ⚠ Prefer this column-level call over {@code getDataValue(aRow).isEmptyOrMissing()}: it
     * answers from the raw stored value, so no {@code IDataValue} is allocated and no string is
     * materialised. Buffer-backed columns override it to skip boxing for numeric buffers as well.
     * </p>
     *
     * @param aRow
     *            the 0-based index of the row to check.<br/>
     *            Valid range is <code>0 &lt;= aRow &lt; rowCount</code>.
     * @return true if the value at the given row is missing, null or an empty string.
     * @throws IndexOutOfBoundsException
     *             if aRow is outside of the valid bounds.
     */
    default boolean isEmptyOrMissing(long aRow) throws IndexOutOfBoundsException
    {
        Object v = getValue(aRow);
        return v == null || v instanceof MissingValue || (v instanceof String s && s.isEmpty());
    }

}
