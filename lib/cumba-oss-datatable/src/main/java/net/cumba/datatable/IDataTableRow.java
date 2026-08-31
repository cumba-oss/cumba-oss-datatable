package net.cumba.datatable;

import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.stream.Stream;
import net.cumba.datatable.values.IDataValue;
import org.jspecify.annotations.Nullable;

/**
 * Represents a row in an {@link IDataTable} for access to values in this row.
 *
 * @see IDataTableColumn
 */
public interface IDataTableRow
{

    /**
     * Retrieve the index of the row in the table.
     *
     * @return the index of the row in the table.
     */
    long getIndex();


    /**
     * Retrieve the real row index of row.
     *
     * @return the real index of the row.
     */
    long getRealRowIndex();


    /**
     * Retrieve the number of columns in the data table (and this row).
     *
     * @return the number of columns in the data table (and this row).
     */
    int getColumnCount();


    /**
     * Retrieve a value from the row in its original data type. This is equivalent to a call to
     * {@link IDataTable#getValue(long, int)} with the index of this row as row.
     *
     * @param aColumn
     *            the 0-based index of the column to retrieve the value for.<br/>
     *            Valid range is <code>0 &lt;= aColumn &lt; columnCount</code>.
     * @return the data value in native type.
     * @throws IndexOutOfBoundsException
     *             if aColumn is outside of the valid bounds.
     */
    @Nullable
    Object getValue(int aColumn) throws IndexOutOfBoundsException;


    /**
     * Retrieve a value from the row in its original data type. This is equivalent to a call to
     * {@link IDataTable#getValue(long, String)} with the index of this row as row.
     *
     * @param aColumn
     *            the name of the column to retrieve the value from.
     * @return the data value in native type.
     * @throws NoSuchElementException
     *             if no column is found for the given name.
     */
    @Nullable
    Object getValue(String aColumn) throws NoSuchElementException;


    /**
     * Retrieve a value from the row as IDataValue. This is equivalent to a call to
     * {@link IDataTable#getDataValue(long, int)} with the index of this row as row.
     *
     * @param aColumn
     *            the 0-based index of the column to retrieve the value for.<br/>
     *            Valid range is <code>0 &lt;= aColumn &lt; columnCount</code>.
     * @return the data value.
     * @throws IndexOutOfBoundsException
     *             if aColumn is outside of the valid bounds.
     */
    IDataValue getDataValue(int aColumn) throws IndexOutOfBoundsException;


    /**
     * Retrieve a value from the row as IDataValue. This is equivalent to a call to
     * {@link IDataTable#getDataValue(long, String)} with the index of this row as row.
     *
     * @param aColumn
     *            the name of the column to retrieve the value from.
     * @return the data value.
     * @throws NoSuchElementException
     *             if no column is found for the given name.
     */
    IDataValue getDataValue(String aColumn) throws NoSuchElementException;


    /**
     * Retrieve a stream of values from this row that contains the values of the given columns in
     * the given order.
     *
     * @param aColumns
     *            the indices of the columns to retrieve the values for.
     * @return the stream that contains the requested columns values
     * @throws IndexOutOfBoundsException
     *             in case at least 1 column index is invalid.
     */
    default Stream<Object> getValues(int... aColumns) throws IndexOutOfBoundsException
    {
        return Arrays.stream(aColumns).mapToObj(this::getValue);
    }


    /**
     * Retrieve a stream of values from this row that contains the values of the given columns in
     * the given order.
     *
     * @param aColumnNames
     *            the names of the columns to retrieve the values for.
     * @return the stream that contains the requested columns values.
     * @throws NoSuchElementException
     *             in case at least one column name does not reference a valid column in the table
     *             of this row.
     */
    default Stream<Object> getValues(String... aColumnNames) throws NoSuchElementException
    {
        return Arrays.stream(aColumnNames).map(this::getValue);
    }


    /**
     * Retrieve the values of the given columns as IDataValue Stream.
     *
     * @param aColumns
     *            the indices of the columns to retrieve the values for.
     * @return the stream that contains the requested columns values.
     * @throws IndexOutOfBoundsException
     *             in case at least 1 column index is invalid.
     */
    default Stream<IDataValue> getDataValues(int... aColumns) throws IndexOutOfBoundsException
    {
        return Arrays.stream(aColumns)//
                .mapToObj(this::getDataValue);
    }


    /**
     * Retrieve the values of the given columns as IDataValue Stream.
     *
     * @param aColumnNames
     *            the names of the columns to retrieve the values for.
     * @return a stream with the values of the given columns in the same order.
     * @throws NoSuchElementException
     *             in case at least one of the column names does not reference a valid column of the
     *             table.
     */
    default Stream<IDataValue> getDataValues(String... aColumnNames) throws NoSuchElementException
    {
        return Arrays.stream(aColumnNames)//
                .map(this::getDataValue);
    }

}
