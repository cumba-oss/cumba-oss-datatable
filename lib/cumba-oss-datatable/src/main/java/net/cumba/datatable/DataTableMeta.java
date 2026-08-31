package net.cumba.datatable;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.net.URI;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.With;
import lombok.extern.jackson.Jacksonized;
import net.cumba.datatable.help.CDT;
import org.jspecify.annotations.Nullable;

/**
 * A default implementation of the {@link IDataTableMeta} interface.<br/>
 * This implementation uses local final fields for all of the properties.
 */
@Getter
@Builder(toBuilder = true, buildMethodName = "_build")
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@ToString(onlyExplicitlyIncluded = true)
@Jacksonized
@JsonAutoDetect(fieldVisibility = Visibility.ANY, getterVisibility = Visibility.NONE,
        isGetterVisibility = Visibility.NONE, setterVisibility = Visibility.NONE)
// Lombok @Builder/@AllArgsConstructor all-args constructor trips NullAway's @NonNull-field init
// check (NullAway#917); the custom builder + @lombok.NonNull enforce required fields at build().
@SuppressWarnings("NullAway.Init")
public class DataTableMeta implements IDataTableMeta
{

    /**
     * Create a {@link DataTableMetaBuilder} that is initialized from the given meta data.
     *
     * @param aMeta
     *            the meta data to initialize the builder from.
     * @return the builder that is initialized with the given meta data.
     */
    public static DataTableMetaBuilder builderFrom(@NonNull IDataTableMeta aMeta)
    {
        if (aMeta instanceof DataTableMeta dm)
        {
            return dm.toBuilder();
        }

        return DataTableMeta.builder()//
                .name(aMeta.getName())//
                .label(aMeta.getLabel())//
                .rowCount(aMeta.getRowCount())//
                .totalRowCount(aMeta.getTotalRowCount())//
                .columnNameCaseSensitive(aMeta.isColumnNameCaseSensitive())//
                .columns(aMeta.getAllColumns().toArray(DataTableColumnMeta[]::new))//
                .metaTable(getMetaTableFor(aMeta))//
                .tableURI(aMeta.getTableURI())//
        ;
    }


    public static DataTableMeta cloneFrom(IDataTableMeta aMeta)
    {
        if (aMeta instanceof DataTableMeta dmd)
        {
            // DataTableMeta is immutable --> we do not need to copy
            return dmd;
        }

        // we copy by builder
        return builderFrom(aMeta).build();
    }


    /**
     * Create an array that is a table of custom metadata for a DataTableMeta.
     *
     * @param aMeta
     *            the table metadata to retrieve a meta table from.
     * @return the custom metadata table or null if the given table metadata does not have any
     *         custom metadata.
     */
    private static Object @Nullable [] getMetaTableFor(IDataTableMeta aMeta)
    {
        if (aMeta instanceof DataTableMeta dcm)
        {
            // as metaTable is immutable, we can simply return the same table
            return dcm.metaTable;
        }
        // this is any other implementation --> we create a new table array.
        List<String> keys = new ArrayList<>(aMeta.getMetaDataKeys());
        if (keys.isEmpty())
        {
            return null;
        }
        Object[] res = new Object[keys.size() * 2];
        for (int i = 0; i < keys.size(); i++)
        {
            String key = keys.get(i);
            res[i * 2] = key;
            // key came from getMetaDataKeys(), so getMetaData(key) cannot be null.
            res[(i * 2) + 1] = Objects.requireNonNull(aMeta.getMetaData(key));
        }
        return res;
    }

    /**
     * The name of the data table.
     */
    @ToString.Include
    @With
    private final @Nullable String name;

    /**
     * An optional label of the data table.
     */
    @With
    private final @Nullable String label;

    /**
     * The number of rows in the data table. This might be -1 to show that the row count is not
     * available.
     */
    @Builder.Default
    @With
    private final long rowCount = 0;

    /**
     * The total number of rows in this table if no condition is applied.
     */
    @Builder.Default
    @With
    private final long totalRowCount = 0;

    /**
     * Array of column metadata in the table.
     */
    @NonNull
    private final DataTableColumnMeta[] columns;

    /**
     * An internal flag to define if column names are case sensitive (true) or not (false). This is
     * used in functions like {@link #getColumn(String)} or {@link #getColumnIndex(String)}.
     */
    @Builder.Default
    private final boolean columnNameCaseSensitive = false;

    /**
     * The URI of the file, this table is stored in.
     */
    @With
    private final @Nullable URI tableURI;

    /**
     * Additional named metadata objects.
     */
    private final Object @Nullable [] metaTable;

    /**
     * Returns a <b>copy</b> of the internal columns array.
     *
     * @return a <b>copy</b> of the internal columns array.
     */
    public DataTableColumnMeta[] getColumns()
    {
        return Arrays.copyOf(columns, columns.length);
    }


    @Override
    public int getColumnCount()
    {
        return columns.length;
    }


    @Override
    @JsonIgnore
    public Stream<DataTableColumnMeta> getAllColumns()
    {
        return Arrays.stream(columns);
    }


    @Override
    public DataTableColumnMeta getColumn(int aColumnIndex)
    {
        int colCount = getColumnCount();

        if (aColumnIndex < 0 || aColumnIndex >= colCount)
        {
            throw new IndexOutOfBoundsException(
                    ExMsgs.indexOutOfBounds("column", aColumnIndex, 0, colCount));
        }

        return columns[aColumnIndex];
    }


    @Override
    public int getColumnIndex(@NonNull String aColumnName)
    {
        if (columnNameCaseSensitive)
        {
            for (int i = 0; i < columns.length; i++)
            {
                // column name is case sensitive
                if (Objects.equals(aColumnName, columns[i].getName()))
                {
                    return i;
                }
            }
        }
        else
        {
            for (int i = 0; i < columns.length; i++)
            {
                // column name is NOT case sensitive
                if (CDT.equalsIgnoreCase(aColumnName, columns[i].getName()))
                {
                    return i;
                }
            }
        }
        return -1;
    }


    @Override
    public DataTableColumnMeta getColumn(@NonNull String aColumnName) throws NoSuchElementException
    {
        int idx = getColumnIndex(aColumnName);
        if (idx < 0)
        {
            throw new NoSuchElementException(
                    "No column available for name %s.".formatted(aColumnName));
        }
        return columns[idx];
    }


    @Override
    @JsonIgnore
    public @Nullable Object getMetaData(@NonNull String aKey)
    {
        if (metaTable == null)
        {
            return null;
        }

        for (int i = 0; i < metaTable.length - 1; i = i + 2)
        {
            if (aKey.equals(metaTable[i]))
            {
                return metaTable[i + 1];
            }
        }
        return null;
    }


    @Override
    public @Nullable Object getMetaData(@NonNull String aKey, @Nullable Object aDefault)
    {
        if (metaTable == null)
        {
            return aDefault;
        }

        for (int i = 0; i < metaTable.length - 1; i = i + 2)
        {
            if (aKey.equals(metaTable[i]))
            {
                return metaTable[i + 1];
            }
        }
        return aDefault;
    }


    @Override
    public Collection<String> getMetaDataKeys()
    {
        if (metaTable == null)
        {
            return Collections.emptyList();
        }

        List<String> res = new ArrayList<>();
        for (int i = 0; i < metaTable.length - 1; i = i + 2)
        {
            if (metaTable[i] instanceof String key)
            {
                res.add(key);
            }
        }
        return List.copyOf(res);
    }

    public static class DataTableMetaBuilder
    {

        public DataTableMeta build()
        {
            DataTableMeta res = _build();

            for (int i = 0; i < res.columns.length; i++)
            {
                DataTableColumnMeta col = res.columns[i];
                if (col.getIndex() != i)
                {
                    String msg = MessageFormat.format(
                            "Column {0} is located at index {1} but defines index {2}!",
                            col.getName(), i, col.getIndex());
                    throw new IllegalStateException(msg);
                }
            }

            if (res.totalRowCount < res.rowCount)
            {
                String msg = MessageFormat.format(
                        "The totalRowCount is lower than rowCount: ({0} < {1}).", res.totalRowCount,
                        res.rowCount);
                throw new IllegalStateException(msg);
            }

            return res;
        }


        /**
         * Adds a custom metadata key-value pair. Null values are silently ignored. Note that
         * duplicate keys are not detected — if the same key is added multiple times, only the first
         * value is returned by {@link DataTableMeta#getMetaData(String)}.
         *
         * @param aKey
         *            the metadata key.
         * @param aValue
         *            the metadata value, or {@code null} to skip.
         * @return this builder.
         */
        @JsonIgnore
        public DataTableMetaBuilder addMetaData(@NonNull String aKey, @Nullable Object aValue)
        {
            if (aValue == null)
            {
                // we never add null values
                return this;
            }
            if (metaTable == null)
            {
                metaTable = new Object[2];
            }
            else
            {
                metaTable = Arrays.copyOf(metaTable, metaTable.length + 2);
            }
            metaTable[metaTable.length - 2] = aKey;
            metaTable[metaTable.length - 1] = aValue;
            return this;
        }


        // Builder field reset to its uninitialised state; the @lombok.NonNull columns field is
        // enforced at build() time, so leaving the builder slot null here is safe.
        @SuppressWarnings("NullAway")
        @JsonIgnore
        public DataTableMetaBuilder clearColumns()
        {
            columns = null;
            return this;
        }


        @JsonIgnore
        public DataTableMetaBuilder addColumns(DataTableColumnMeta... aColumns)
        {
            if (CDT.isEmptyOrNull(aColumns))
            {
                return this;
            }

            if (CDT.isEmptyOrNull(columns))
            {
                return columns(aColumns);
            }

            int oldCount = columns.length;
            int newCount = oldCount + aColumns.length;
            DataTableColumnMeta[] newCols = Arrays.copyOf(columns, newCount);
            System.arraycopy(aColumns, 0, newCols, oldCount, aColumns.length);
            return columns(newCols);
        }


        /**
         * Set the columns from a List.
         *
         * @param aColumns
         *            the list of columns to set.
         * @return this builder.
         */
        @JsonIgnore
        public DataTableMetaBuilder setColumns(@NonNull List<DataTableColumnMeta> aColumns)
        {
            return columns(aColumns.toArray(DataTableColumnMeta[]::new));
        }


        @JsonIgnore
        public DataTableMetaBuilder setColumns(DataTableColumnMeta... aColumns)
        {
            return columns(aColumns);
        }


        /**
         * Set the columns from an array.
         *
         * @param aColumns
         *            the array of columns to set.
         * @return this builder.
         */
        // Mirrors the runtime contract: a null array resets the builder's columns slot (the
        // @lombok.NonNull columns field is enforced at build()), so the null assignment is safe.
        @SuppressWarnings("NullAway")
        public DataTableMetaBuilder columns(DataTableColumnMeta @Nullable [] aColumns)
        {
            if (aColumns == null)
            {
                columns = null;
                return this;
            }

            for (int i = 0; i < aColumns.length; i++)
            {
                DataTableColumnMeta c = aColumns[i];

                if (c == null)
                {
                    throw new IllegalArgumentException(
                            "Null column found at index %d.".formatted(i));
                }
                int idx = c.getIndex();
                if (i != idx)
                {
                    throw new IllegalArgumentException("Invalid column index %d found in column %d."
                            .formatted(c.getIndex(), i));
                }
            }

            columns = Arrays.stream(aColumns)//
                    .map(DataTableColumnMeta::cloneFrom).toArray(DataTableColumnMeta[]::new);
            return this;
        }


        /**
         * Set the columns from a Stream.
         *
         * @param aColumns
         *            the list of columns to set.
         * @return this builder.
         */
        @JsonIgnore
        public DataTableMetaBuilder setColumns(@NonNull Stream<DataTableColumnMeta> aColumns)
        {
            return columns(aColumns.toArray(DataTableColumnMeta[]::new));
        }


        /**
         * Replace the column metadata at the given index. This method requires an previously set
         * column at the given index.
         *
         * @param aIndex
         *            the index to replace the column metadata at.
         * @param aColumn
         *            the new column metadata for the given index.
         * @return this builder.
         */
        @JsonIgnore
        public DataTableMetaBuilder updateColumn(int aIndex, @NonNull DataTableColumnMeta aColumn)
            throws IndexOutOfBoundsException
        {
            if (CDT.isEmptyOrNull(columns))
            {
                throw new IndexOutOfBoundsException(
                        ExMsgs.indexOutOfBounds("columns", aIndex, 0, 0));
            }
            if (aIndex < 0 || aIndex >= columns.length)
            {
                throw new IndexOutOfBoundsException(
                        ExMsgs.indexOutOfBounds("columns", aIndex, 0, columns.length));
            }

            // we copy the array as we might re-use it in another instance.
            DataTableColumnMeta[] newColumns = Arrays.copyOf(columns, columns.length);
            newColumns[aIndex] = DataTableColumnMeta.cloneFrom(aColumn);
            columns = newColumns;
            return this;
        }
    }
}
