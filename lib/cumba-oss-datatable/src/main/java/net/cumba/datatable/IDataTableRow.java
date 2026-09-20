package net.cumba.datatable;

import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Stream;
import net.cumba.datatable.help.GenericListView;
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
    default Stream<@Nullable Object> getValues(int... aColumns) throws IndexOutOfBoundsException
    {
        return Arrays.stream(aColumns).<@Nullable Object> mapToObj(this::getValue);
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
    default Stream<@Nullable Object> getValues(String... aColumnNames) throws NoSuchElementException
    {
        return Arrays.stream(aColumnNames).<@Nullable Object> map(this::getValue);
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

    // ⭐ The List-returning views, ported 2026-09-20 with GenericListView itself.
    // They were withheld only because that ONE JDK-only helper class was absent here --
    // not because of any feature. A single helper is not a feature, so it travels and
    // these travel with it. ⚑ The port also forced a real fix: GenericListView's type
    // variable had no @Nullable upper bound, so GenericListView<@Nullable Object> was an
    // illegal instantiation under JSpecify. THIS repository's NullAway caught it; the
    // internal one did not. The bound was added in BOTH trees.
    // The getFormattedDataValue family stays withheld: it reaches values.DataValueFormatted,
    // which belongs to the formats feature and is genuinely out.


    /**
     * Retrieve a virtual list view over the values of this row.
     *
     * <p>
     * ⚠ The element type is {@code @Nullable Object} because {@link #getValue(int)}, the provider
     * this view is built on, is itself {@code @Nullable}. The declaration used to read
     * {@code List<Object>} — a NON-null element type over a nullable provider, which NullAway
     * cannot flag because it does not check a method reference's return against a generic type
     * argument. Nothing about the runtime answer changed. The typed channel,
     * {@link #getDataValue(int)}, is where the value-or-missing contract lives.
     * </p>
     *
     * @return a virtual list that can be used to access the values of this row. The resulting list
     *         is just a view into this row, not a copy.
     */
    default List<@Nullable Object> getValues()
    {
        int colCount = getColumnCount();
        return new GenericListView<@Nullable Object>(this::getValue, colCount);
    }


    /**
     * Retrieve a virtual list view over the values of this row as IDataValue.
     *
     * @return a virtual list that can be used to access the values of this row as IDataValue. The
     *         resulting list is just a view into this row, not a copy.
     */
    default List<IDataValue> getDataValues()
    {
        int colCount = getColumnCount();
        return new GenericListView<>(this::getDataValue, colCount);
    }

}
