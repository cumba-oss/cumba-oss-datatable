package net.cumba.datatable;

import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import net.cumba.datatable.values.DataValueSupport;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.IDataValue;
import net.cumba.datatable.values.MissingValue;
import org.jspecify.annotations.Nullable;

/**
 * Provides access to data that is stored in a table structure.<br/>
 */
public interface IDataTable
{

    /**
     * Returns metadata about the table.
     *
     * @return metadata about the table.
     */
    DataTableMeta getMetaData();


    /**
     * Returns the actual (total) number of rows in the table, if this is known.
     *
     * @return the actual (total) number of rows in the table, if this is known. This method might
     *         return -1 if the number of rows is currently unknown.
     */
    long getRowCount();


    /**
     * Returns the number of columns in this table.
     *
     * @return the number of columns in this table.
     */
    default int getColumnCount()
    {
        return getMetaData().getColumnCount();
    }


    /**
     * Retrieve the 0-based index of the column with the given name.
     *
     * @param aColumnName
     *            the name of the column to retrieve the index from.
     * @return the 0-based index of the column with the given name or -1 if no column is found for
     *         the given name.
     */
    default int getColumnIndex(String aColumnName)
    {
        DataTableMeta meta = getMetaData();
        return meta != null ? meta.getColumnIndex(aColumnName) : -1;
    }


    /**
     * Retrieve the name of the row at the given row index.
     *
     * @param aRowIndex
     *            the 0-based index of the row to retrieve the name from.<br/>
     *            Valid range is <code>0 &lt;= aRow &lt; rowCount</code>.
     * @return the name of the row. This is very likely a String that contains the 1-based index of
     *         the row.
     * @throws IndexOutOfBoundsException
     *             if aRow is outside of the valid bounds.
     */
    default String getRowName(long aRowIndex) throws IndexOutOfBoundsException
    {
        return Long.toString(getRealRowIndex(aRowIndex) + 1);
    }


    /**
     * Retrieve the real row index of row with the given index.
     *
     * @param aRowIndex
     *            the 0-based index of the row to retrieve the real index for.
     * @return the real index of the row with the given index.
     * @throws IndexOutOfBoundsException
     *             in case the given row index is invalid.
     * @see #getDisplayRowIndex(long)
     */
    default long getRealRowIndex(long aRowIndex) throws IndexOutOfBoundsException
    {
        long rc = getRowCount();
        if (aRowIndex < 0 || aRowIndex >= rc)
        {
            throw new IndexOutOfBoundsException(ExMsgs.indexOutOfBounds("row", aRowIndex, 0, rc));
        }
        return aRowIndex;
    }


    /**
     * This is the inverse method of {@link #getRealRowIndex(long)}.
     *
     * @param aRealRowIndex
     *            the 0-based real row index to retrieve the display row index for.
     * @return the display row index of the row with the given real row index or -1 if no display
     *         row is available for a row with the given real row index.
     * @see #getRealRowIndex(long)
     */
    default long getDisplayRowIndex(long aRealRowIndex)
    {
        long rc = getRowCount();
        if (aRealRowIndex < 0 || aRealRowIndex >= rc)
        {
            return -1;
        }
        long idx = getRealRowIndex(aRealRowIndex);
        if (idx == aRealRowIndex)
        {
            return idx;
        }
        return -1;
    }


    /**
     * Retrieve the row with the given index for row based access.
     *
     * @param aRow
     *            the 0-based index of the row to retrieve.<br/>
     *            Valid range is <code>0 &lt;= aRow &lt; rowCount</code>.
     * @return the row for the given row index.
     * @throws IndexOutOfBoundsException
     *             if aRow is outside of the valid bounds.
     */
    default IDataTableRow getRow(long aRow) throws IndexOutOfBoundsException
    {
        long rc = getRowCount();
        if (aRow < 0 || aRow >= rc)
        {
            throw new IndexOutOfBoundsException(ExMsgs.indexOutOfBounds("row", aRow, 0, rc));
        }
        return new DefaultDataTableRow(this, aRow);
    }


    /**
     * Retrieve a stream of all rows.
     *
     * @return a stream that contains all rows.
     */
    default Stream<IDataTableRow> getRows()
    {
        return getRows(0, -1);
    }


    /**
     * Retrieve a stream that contains all rows from aStartRowIndex to aEndRowIndex, where the row
     * at aEndRowIndex is not contained.
     *
     * @param aStartRowIndex
     *            the first row to be contained in the resulting stream.
     * @param aEndRowIndex
     *            the first row that is not contained in the resulting stream anymore. This can be
     *            set to -1 to contain all rows after aStartRowIndex (until the end of the table).
     * @return a stream that contains the given range of rows.
     */
    default Stream<IDataTableRow> getRows(long aStartRowIndex, long aEndRowIndex)
    {
        long rc = getRowCount();
        if (aEndRowIndex < 0)
        {
            aEndRowIndex = rc;
        }
        long end = Math.min(aEndRowIndex, rc);

        return LongStream.range(aStartRowIndex, end).mapToObj(this::getRow);
    }


    /**
     * Retrieve the column with the given index for column based access.
     *
     * @param aColumn
     *            the 0-based index of the column to retrieve.<br/>
     *            Valid range is <code>0 &lt;= aColumn &lt; columnCount</code>.
     * @return the column for the given column index.
     * @throws IndexOutOfBoundsException
     *             if aColumn is outside of the valid bounds.
     */
    default IDataTableColumn getColumn(int aColumn) throws IndexOutOfBoundsException
    {
        return new DefaultDataTableColumn(this, aColumn);
    }


    /**
     * Retrieve the column with the given index for column based access.
     *
     * @param aColumnName
     *            the name of the column to retrieve the value from.
     * @return the column for the given column name.
     * @throws NoSuchElementException
     *             in case no column can be found for the given name.
     */
    default IDataTableColumn getColumn(String aColumnName) throws NoSuchElementException
    {
        int idx = getMetaData().getColumnIndex(aColumnName);
        if (idx < 0)
        {
            throw new NoSuchElementException("No column found for name " + aColumnName);
        }
        return getColumn(idx);
    }


    /**
     * Returns a Stream of all columns of this table.
     *
     * @return a Stream of all columns of this table.
     */
    default Stream<IDataTableColumn> getColumns()
    {
        return IntStream.range(0, getColumnCount()).mapToObj(this::getColumn);
    }


    /**
     * Retrieve columns for the given names.
     *
     * @param aColumnNames
     *            the names of the columns to retrieve.
     * @return a Stream that contains the requested columns. <br/>
     *         If the same column is referenced multiple times, it is returned as many times as it
     *         is requested.<br/>
     *         If a name is provided that does not match any column, this name is ignored.
     */
    default Stream<IDataTableColumn> getColumns(String... aColumnNames)
    {
        return getMetaData().getColumns(aColumnNames).map(cm -> getColumn(cm.getIndex()));
    }


    /**
     * Retrieve columns for the given names.
     *
     * @param aColumnNames
     *            the names of the columns to retrieve.
     * @return a Stream that contains the requested columns. <br/>
     *         If the same column is referenced multiple times, it is returned as many times as it
     *         is requested.<br/>
     *         If a name is provided that does not match any column, this name is ignored.
     */
    default Stream<IDataTableColumn> getColumns(Collection<String> aColumnNames)
    {
        return getMetaData().getColumns(aColumnNames).map(cm -> getColumn(cm.getIndex()));
    }


    /**
     * Retrieve columns for the given indices.
     *
     * @param aColumnIndices
     *            the indices of the columns to retrieve.
     * @return a Stream that contains the requested columns.<br/>
     *         If the same column is referenced multiple times, it is returned as many times as it
     *         is requested.<br/>
     *         If an invalid index is provided, this is ignored.
     */
    default Stream<IDataTableColumn> getColumns(int... aColumnIndices)
    {
        return getMetaData().getColumns(aColumnIndices).map(cm -> getColumn(cm.getIndex()));
    }


    /**
     * Retrieve a single value of the table in its original data type.
     *
     * @param aRow
     *            the 0-based index of the row to retrieve the value from.<br/>
     *            Valid range is <code>0 &lt;= aRow &lt; rowCount</code>.
     * @param aColumn
     *            the 0-based index of the column to retrieve the value for.<br/>
     *            Valid range is <code>0 &lt;= aColumn &lt; columnCount</code>.
     * @return the data value in native type
     * @throws IndexOutOfBoundsException
     *             if either aRow or aColumn is outside of the valid bounds.
     */
    @Nullable
    Object getValue(long aRow, int aColumn) throws IndexOutOfBoundsException;


    /**
     * A fast path to check if a value at the given cell (row, column) is missing or null. Basically
     * it should never be null (always missing) but just in case.
     *
     * @param aRow
     *            the 0-based index of the row to check for missing.<br/>
     *            Valid range is <code>0 &lt;= aRow &lt; rowCount</code>.
     * @param aColumn
     *            the 0-based index of the column to check for missing.<br/>
     *            Valid range is <code>0 &lt;= aColumn &lt; columnCount</code>.
     * @return true if the value at the given cell is missing or null.
     * @throws IndexOutOfBoundsException
     *             if either aRow or aColumn is outside of the valid bounds.
     */
    default boolean isMissingOrNull(long aRow, int aColumn) throws IndexOutOfBoundsException
    {
        Object v = getValue(aRow, aColumn);
        return v == null || v instanceof MissingValue;
    }


    /**
     * A fast path to check whether the cell at the given (row, column) carries <b>no usable
     * value</b> — it is {@code null}, a {@link MissingValue}, or a zero-length {@link String}.
     * <p>
     * This is the <em>blankness</em> notion rule evaluation needs, and it is deliberately wider
     * than {@link #isMissingOrNull(long, int)}: a source {@code null} in a character column and an
     * empty string the file genuinely contains are <b>both</b> blank. See
     * {@link IDataTableColumn#isEmptyOrMissing(long)}, to which column-backed tables route.
     * </p>
     *
     * @param aRow
     *            the 0-based index of the row to check.<br/>
     *            Valid range is <code>0 &lt;= aRow &lt; rowCount</code>.
     * @param aColumn
     *            the 0-based index of the column to check.<br/>
     *            Valid range is <code>0 &lt;= aColumn &lt; columnCount</code>.
     * @return true if the value at the given cell is missing, null or an empty string.
     * @throws IndexOutOfBoundsException
     *             if either aRow or aColumn is outside of the valid bounds.
     */
    default boolean isEmptyOrMissing(long aRow, int aColumn) throws IndexOutOfBoundsException
    {
        Object v = getValue(aRow, aColumn);
        return v == null || v instanceof MissingValue || (v instanceof String s && s.isEmpty());
    }


    /**
     * Compute a non-boxing hash for the value at the given row and column. The default
     * implementation falls back to {@link #getValue(long, int)} and hashes the boxed result;
     * buffer-backed tables override this to route directly into the typed hash path of the
     * underlying data buffer.
     * <p>
     * Contract: for any table, {@code hashCodeAt(row, col)} must equal
     * {@code getValue(row, col).hashCode()} when {@code getValue} returns a
     * non-{@link MissingValue} object, or {@link MissingValue#hashCodeStable()} when it returns a
     * missing value. Bounds semantics match {@link #getValue(long, int)}.
     *
     * @param aRow
     *            the 0-based row index.
     * @param aColumn
     *            the 0-based column index.
     * @return the stable hash of the cell.
     * @throws IndexOutOfBoundsException
     *             if either index is outside of the valid bounds.
     */
    default int hashCodeAt(long aRow, int aColumn) throws IndexOutOfBoundsException
    {
        Object v = getValue(aRow, aColumn);
        if (v instanceof MissingValue mv)
        {
            return mv.hashCodeStable();
        }
        return v != null ? v.hashCode() : 0;
    }


    /**
     * Retrieve a single value of the table in its original data type.
     *
     * @param aRow
     *            the 0-based index of the row to retrieve the value from.<br/>
     *            Valid range is <code>0 &lt;= aRow &lt; rowCount</code>.
     * @param aColumn
     *            the name of the column to retrieve the value from.
     * @return the data value in native type
     * @throws IndexOutOfBoundsException
     *             if aRow is outside of the valid bounds.
     * @throws NoSuchElementException
     *             if no column is found for the given name.
     */
    default @Nullable Object getValue(long aRow, String aColumn)
        throws IndexOutOfBoundsException, NoSuchElementException
    {
        int idx = getMetaData().getColumnIndex(aColumn);

        if (idx < 0)
        {
            throw new NoSuchElementException("No column found for name " + aColumn);
        }
        return getValue(aRow, idx);
    }


    /**
     * Retrieve a single value of the table as a IDataValue.<br/>
     * This returns the raw, unformatted data value.
     *
     * @param aRow
     *            the 0-based index of the row to retrieve the value from.<br/>
     *            Valid range is <code>0 &lt;= aRow &lt; rowCount</code>.
     * @param aColumn
     *            the 0-based index of the column to retrieve the value for.<br/>
     *            Valid range is <code>0 &lt;= aColumn &lt; columnCount</code>.
     * @return the data value.
     * @throws IndexOutOfBoundsException
     *             if either aRow or aColumn is outside of the valid bounds.
     */
    default IDataValue getDataValue(long aRow, int aColumn)
    {
        DataValueType t = getMetaData().getColumn(aColumn).getType();
        return DataValueSupport.getAsDataValue(getValue(aRow, aColumn), t);
    }


    /**
     * Retrieve a single value of the table as a IDataValue.
     *
     * @param aRow
     *            the 0-based index of the row to retrieve the value from.<br/>
     *            Valid range is <code>0 &lt;= aRow &lt; rowCount</code>.
     * @param aColumn
     *            the name of the column to retrieve the value from.
     * @return the data value.
     * @throws IndexOutOfBoundsException
     *             if either aRow or aColumn is outside of the valid bounds.
     * @throws IllegalArgumentException
     *             if no column is found for the given name.
     */
    default IDataValue getDataValue(long aRow, String aColumn)
        throws IndexOutOfBoundsException, IllegalArgumentException
    {
        int idx = getColumnIndex(aColumn);
        if (idx < 0)
        {
            throw new IllegalArgumentException("Can not find column %s".formatted(aColumn));
        }
        return getDataValue(aRow, idx);
    }

}
