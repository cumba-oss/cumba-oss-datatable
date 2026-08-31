package net.cumba.datatable;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import lombok.NonNull;
import net.cumba.datatable.help.CDT;
import org.jspecify.annotations.Nullable;

/**
 * Metadata about a {@link IDataTable}.
 */
public interface IDataTableMeta
{

    /**
     * Returns the name of the table, or {@code null} if the table has no name.
     *
     * @return the name of the table, or {@code null} if the table has no name.
     */
    @Nullable
    String getName();


    /**
     * Returns an optional data table label, or {@code null} if no label is set.
     *
     * @return an optional data table label, or {@code null} if no label is set.
     */
    @Nullable
    String getLabel();


    /**
     * Returns the actual number of rows in this table.
     *
     * @return the actual number of rows in this table. <br/>
     *         For a table with an applied filter, this is the number of rows matching the filter.
     *         <br/>
     *         This might be -1 if the number of rows is unknown.
     */
    long getRowCount();


    /**
     * Returns the total number of rows in this table.
     *
     * @return the total number of rows in this table. <br/>
     *         For a table with an applied filter, this is still the total number of rows,
     *         independent of the filter. <br/>
     *         This might be -1 if the number of rows is unknown.
     */
    long getTotalRowCount();


    /**
     * Returns the number of columns in this table.
     *
     * @return the number of columns in this table.
     */
    int getColumnCount();


    /**
     * Returns the URI of the file this table is stored in, or {@code null} if no URI is available.
     *
     * @return the URI of the file this table is stored in, or {@code null} if no URI is available.
     */
    @Nullable
    URI getTableURI();


    /**
     * Retrieve custom metadata by a key.
     *
     * @param aKey
     *            the key to retrieve metadata for.
     * @return the value for the given key or null if no value is available for the given key.
     */
    @Nullable
    Object getMetaData(@NonNull String aKey);


    /**
     * Retrieve custom metadata by a key.
     *
     * @param aKey
     *            the key to retrieve metadata for.
     * @param aDefault
     *            the default value to be returned if the key is not found.
     * @return the value for the given key or aDefault if no value is available for the given key.
     */
    @Nullable
    Object getMetaData(@NonNull String aKey, @Nullable Object aDefault);


    /**
     * Returns a collection of all available custom metadata keys.
     *
     * @return a collection of all available custom metadata keys.
     */
    Collection<String> getMetaDataKeys();


    /**
     * Returns metadata about the columns as a stream.
     *
     * @return metadata about the columns as a stream.
     */
    Stream<DataTableColumnMeta> getAllColumns();


    /**
     * Retrieve metadata about a column based on the column index.
     *
     * @param aColumnIndex
     *            the 0-based index of the column to retrieve.<br/>
     *            Valid range is <code>0 &lt;= aColumnIndex &lt; columnCount</code>.
     * @return the metadata for the column with the given index.
     * @throws IndexOutOfBoundsException
     *             if aColumnIndex is outside of the valid bounds.
     */
    default DataTableColumnMeta getColumn(int aColumnIndex) throws IndexOutOfBoundsException
    {
        int colCount = getColumnCount();

        if (aColumnIndex < 0 || aColumnIndex >= colCount)
        {
            throw new IndexOutOfBoundsException(
                    ExMsgs.indexOutOfBounds("column", aColumnIndex, 0, colCount));
        }

        DataTableColumnMeta res = getAllColumns().filter(c -> c.getIndex() == aColumnIndex)
                .findFirst().orElse(null);
        if (res != null)
        {
            return res;
        }
        throw new IndexOutOfBoundsException(
                ExMsgs.indexOutOfBounds("column", aColumnIndex, 0, colCount));
    }


    /**
     * Retrieve metadata about a column based on the column name.
     *
     * @param aColumnName
     *            the name of the column.
     * @return the metadata for the column with the given name.
     * @throws NoSuchElementException
     *             in case the name does not reference a column in this table.
     * @throws NullPointerException
     *             in case aColumnName is null.
     * @see #getOptionalColumn(String)
     */
    default DataTableColumnMeta getColumn(@NonNull String aColumnName)
        throws NoSuchElementException, NullPointerException
    {
        Stream<DataTableColumnMeta> cstr = getAllColumns();
        if (isColumnNameCaseSensitive())
        {
            cstr = cstr.filter(c -> Objects.equals(c.getName(), aColumnName));
        }
        else
        {
            cstr = cstr.filter(c -> CDT.equalsIgnoreCase(c.getName(), aColumnName));
        }

        Optional<DataTableColumnMeta> res = cstr.findAny();
        if (res.isEmpty())
        {
            throw new NoSuchElementException(
                    "No column available for name %s.".formatted(aColumnName));
        }
        return res.get();
    }


    /**
     * Retrieve metadata about a column based on the column name.
     *
     * @param aColumnName
     *            the name of the column.
     * @return the metadata for the column with the given name or null if no column is available for
     *         the given name.
     * @throws NullPointerException
     *             in case aColumnName is null.
     * @see #getColumn(String)
     */
    default @Nullable DataTableColumnMeta getOptionalColumn(@NonNull String aColumnName)
        throws NullPointerException
    {
        Stream<DataTableColumnMeta> cstr = getAllColumns();
        if (isColumnNameCaseSensitive())
        {
            cstr = cstr.filter(c -> Objects.equals(c.getName(), aColumnName));
        }
        else
        {
            cstr = cstr.filter(c -> CDT.equalsIgnoreCase(c.getName(), aColumnName));
        }

        return cstr.findAny().orElse(null);
    }


    /**
     * Retrieve column metadata for the columns with the given names.
     *
     * @param aColumnNames
     *            the names of the columns to retrieve metadata about.
     * @return a Stream that contains the metadata of the requested columns. <br/>
     *         If an entry is null, this is ignored.<br/>
     *         If the same column is referenced multiple times, it is returned as many times as it
     *         is requested.<br/>
     *         If a name is provided that does not match any column, this name is ignored.
     * @throws NullPointerException
     *             in case aColumnNames is null.
     */
    default Stream<DataTableColumnMeta> getOptionalColumns(@NonNull String... aColumnNames)
        throws NullPointerException
    {
        return Arrays.stream(aColumnNames)//
                .filter(Objects::nonNull)//
                .mapToInt(this::getColumnIndex)//
                .filter(i -> i >= 0)//
                .mapToObj(this::getColumn);
    }


    /**
     * Retrieve column metadata for the columns with the given names.
     *
     * @param aColumnNames
     *            the names of the columns to retrieve metadata about.
     * @return a Stream that contains the metadata of the requested columns. <br/>
     *         If the same column is referenced multiple times, it is returned as many times as it
     *         is requested.
     * @throws NoSuchElementException
     *             if at least one given column name does not reference a column in this table.
     * @throws NullPointerException
     *             in case aColumnNames or at least one of the elements in aColumnNames is null.
     */
    default Stream<DataTableColumnMeta> getColumns(@NonNull String... aColumnNames)
        throws NoSuchElementException, NullPointerException
    {
        return Arrays.stream(aColumnNames)//
                .map(this::getColumn);
    }


    /**
     * Retrieve column metadata for the columns with the given names.
     *
     * @param aColumnNames
     *            the names of the columns to retrieve metadata about.
     * @return a Stream that contains the metadata of the requested columns.<br/>
     *         If the same column is referenced multiple times, it is returned as many times as it
     *         is requested.<br/>
     * @throws NoSuchElementException
     *             if at least one given column name does not reference a column in this table.
     * @throws NullPointerException
     *             in case aColumnNames or at least one of the elements in aColumnNames is null.
     */
    default Stream<DataTableColumnMeta> getColumns(@NonNull Collection<String> aColumnNames)
        throws NoSuchElementException, NullPointerException
    {
        return aColumnNames.stream().map(this::getColumn);
    }


    /**
     * Retrieve column metadata for the columns with the given indices.
     *
     * @param aColumnIndices
     *            the indices of the columns to retrieve metadata about.
     * @return a Stream that contains the metadata of the requested columns.<br/>
     *         If the same column is referenced multiple times, it is returned as many times as it
     *         is requested.
     * @throws IndexOutOfBoundsException
     *             in case at least one of the provided indices is invalid.
     * @throws NullPointerException
     *             in case aColumnIndices is null.
     */
    default Stream<DataTableColumnMeta> getColumns(@NonNull int... aColumnIndices)
        throws IndexOutOfBoundsException, NullPointerException
    {
        return Arrays.stream(aColumnIndices).mapToObj(this::getColumn);
    }


    /**
     * Retrieve the index of the column for the given name.
     *
     * @param aColumnName
     *            the name of the column.
     * @return the index of the column or -1 if no column is found for the given name.
     * @throws NullPointerException
     *             in case aColumnName is null.
     */
    default int getColumnIndex(@NonNull String aColumnName) throws NullPointerException
    {
        DataTableColumnMeta cm = getOptionalColumn(aColumnName);
        return cm != null ? cm.getIndex() : -1;
    }


    /**
     * Check if the given column is contained in the table metadata. This is a predicate function
     * and implemented as {@code getOptionalColumn(aColumnName) != null}.
     *
     * @param aColumnName
     *            the name of the column to be checked.
     * @return true if the metadata contains a column for the given name, false otherwise.
     * @throws NullPointerException
     *             in case aColumnName is null.
     */
    default boolean containsColumn(@NonNull String aColumnName) throws NullPointerException
    {
        return getOptionalColumn(aColumnName) != null;
    }


    /**
     * Check if all of the given columns are contained in the table metadata.<br/>
     * More precise: This function returns true if (and only if) none of the given columns is not
     * contained in the table metadata.
     *
     * @param aColumnNames
     *            the names of the columns to be checked.
     * @return true if all of the given columns are contained in the metadata, false otherwise.<br/>
     *         If aColumnNames is an empty array true is returned.
     * @throws NullPointerException
     *             if aColumnNames is null.
     */
    default boolean containsAllColumns(@NonNull String... aColumnNames) throws NullPointerException
    {
        for (String cn : aColumnNames)
        {
            if (cn == null || getOptionalColumn(cn) == null)
            {
                return false;
            }
        }
        return true;
    }


    /**
     * Check if at least one of the given columns is contained in the table metadata.<br/>
     * More precise: This function returns true if (and only if) at least one of the given columns
     * is contained in the table metadata.
     *
     * @param aColumnNames
     *            the names of the columns to be checked.
     * @return true if at least one of the given columns is contained in the metadata, false if no
     *         column is contained.<br/>
     *         If aColumnNames is an empty array false is returned.
     * @throws NullPointerException
     *             if aColumnNames is null.
     */
    default boolean containsAnyColumn(@NonNull String... aColumnNames) throws NullPointerException
    {
        for (String cn : aColumnNames)
        {
            if (cn != null && getOptionalColumn(cn) != null)
            {
                return true;
            }
        }
        return false;
    }


    /**
     * Returns true if column names are case sensitive, false otherwise.
     *
     * @return true if column names are case sensitive, false otherwise.
     */
    boolean isColumnNameCaseSensitive();
}
