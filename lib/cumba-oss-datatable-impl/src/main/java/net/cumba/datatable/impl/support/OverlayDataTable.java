package net.cumba.datatable.impl.support;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.AbstractDataTable;
import net.cumba.datatable.values.DataValueSupport;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.IDataValue;
import org.jspecify.annotations.Nullable;

/**
 * A virtual overlay data table that wraps an existing {@link IDataTable} and allows modifications
 * without altering the underlying data.
 *
 * <p>
 * Supports:
 * </p>
 * <ul>
 * <li>Setting individual cell values (by row/column)</li>
 * <li>Setting cell values as {@link IDataValue} or {@code DataValueFormatted}</li>
 * <li>Renaming columns</li>
 * <li>Removing columns</li>
 * <li>Changing column attributes (label, format, type, length)</li>
 * </ul>
 *
 * <p>
 * All modifications are stored in overlay structures. The underlying table is never modified.
 * Reading a cell checks the overlay first; if no override exists, the delegate table's value is
 * returned.
 * </p>
 */
public class OverlayDataTable extends AbstractDataTable
{

    private final IDataTable delegate;

    /** Overlay values: key = (row, overlayColumnIndex), value = raw Object. */
    private final Map<Long, Object> valueOverrides = new HashMap<>();

    /** Overlay IDataValue: key = (row, overlayColumnIndex). */
    private final Map<Long, IDataValue> dataValueOverrides = new HashMap<>();

    /** Column metadata overrides: overlayColumnIndex → modified metadata. */
    private final Map<Integer, DataTableColumnMeta> columnMetaOverrides = new LinkedHashMap<>();

    /** Maps overlay column index → delegate column index. -1 if column was added. */
    private final List<Integer> columnMapping = new ArrayList<>();

    /** Maps overlay column index → column name (for removed/renamed tracking). */
    private final List<String> columnNames = new ArrayList<>();

    /** Set of removed delegate column indices. */
    private final Set<Integer> removedDelegateColumns = new java.util.LinkedHashSet<>();

    /** Overridden table name (null = use delegate's name). */
    private @Nullable String nameOverride;

    /** Overridden table label (null = use delegate's label). */
    private @Nullable String labelOverride;

    /** Overridden source URI (null = use delegate's URI). */
    private @Nullable URI uriOverride;

    /** Overridden table-level custom metadata entries. */
    private final Map<String, Object> tableMetaOverrides = new LinkedHashMap<>();

    private @Nullable DataTableMeta cachedMeta;

    private boolean metaDirty = true;

    /**
     * Creates a virtual empty table with the given name, label, and row count. The table starts
     * with zero columns — use {@link #addColumn} to define the schema, then {@link #setValue} to
     * populate cell values.
     *
     * @param aName
     *            the table/domain name (e.g. "SS")
     * @param aLabel
     *            the table label (null defaults to name)
     * @param aRowCount
     *            the number of rows
     * @return a new OverlayDataTable with no columns and null cell values
     */
    public static OverlayDataTable empty(String aName, @Nullable String aLabel, int aRowCount)
    {
        DataTableMeta meta = DataTableMeta.builder().name(aName)
                .label(aLabel != null ? aLabel : aName).rowCount(aRowCount).totalRowCount(aRowCount)
                .columns(new DataTableColumnMeta[0]).build();
        IDataTable emptyDelegate = new AbstractDataTable()
        {

            @Override
            public long getRowCount()
            {
                return aRowCount;
            }


            @Override
            public @Nullable Object getValue(long aRow, int aColumn)
            {
                return null;
            }


            @Override
            public DataTableMeta getMetaData()
            {
                return meta;
            }
        };
        return new OverlayDataTable(emptyDelegate);
    }


    /**
     * Creates a new overlay table wrapping the given delegate.
     *
     * @param aDelegate
     *            the underlying data table (not modified)
     */
    @SuppressWarnings("this-escape")
    public OverlayDataTable(IDataTable aDelegate)
    {
        this.delegate = aDelegate;

        // Initialize column mapping from delegate
        DataTableMeta delegateMeta = aDelegate.getMetaData();
        int colCount = delegateMeta.getColumnCount();
        for (int c = 0; c < colCount; c++)
        {
            columnMapping.add(c);
            columnNames.add(delegateMeta.getColumn(c).getName());
        }

        metaDirty = true;
    }

    // ---- Value overrides ----


    /**
     * Sets a raw value for a specific cell. This overrides the delegate's value.
     *
     * @param aRow
     *            the 0-based row index
     * @param aColumn
     *            the 0-based overlay column index
     * @param aValue
     *            the value to set (may be null)
     */
    public void setValue(long aRow, int aColumn, @Nullable Object aValue)
    {
        long key = cellKey(aRow, aColumn);
        valueOverrides.put(key, aValue);
        dataValueOverrides.remove(key);
    }


    /**
     * Sets a raw value for a specific cell identified by column name.
     *
     * @param aRow
     *            the 0-based row index
     * @param aColumnName
     *            the column name
     * @param aValue
     *            the value to set (may be null)
     */
    public void setValue(long aRow, String aColumnName, @Nullable Object aValue)
    {
        int idx = findColumnIndex(aColumnName);
        if (idx >= 0)
        {
            setValue(aRow, idx, aValue);
        }
    }


    /**
     * Sets an {@link IDataValue} for a specific cell.
     *
     * @param aRow
     *            the 0-based row index
     * @param aColumn
     *            the 0-based overlay column index
     * @param aValue
     *            the data value to set
     */
    public void setDataValue(long aRow, int aColumn, IDataValue aValue)
    {
        long key = cellKey(aRow, aColumn);
        dataValueOverrides.put(key, aValue);
        valueOverrides.remove(key);
    }

    // ---- Column modifications ----


    /**
     * Renames a column.
     *
     * @param aOldName
     *            the current column name
     * @param aNewName
     *            the new column name
     * @return {@code true} if the column was found and renamed
     */
    public boolean renameColumn(String aOldName, String aNewName)
    {
        int idx = findColumnIndex(aOldName);
        if (idx < 0)
        {
            return false;
        }
        columnNames.set(idx, aNewName);

        // Update column meta override
        DataTableColumnMeta existing = getEffectiveColumnMeta(idx);
        columnMetaOverrides.put(idx, existing.toBuilder().name(aNewName).build());
        metaDirty = true;
        return true;
    }


    /**
     * Removes a column by name.
     *
     * @param aColumnName
     *            the column name to remove
     * @return {@code true} if the column was found and removed
     */
    public boolean removeColumn(String aColumnName)
    {
        int idx = findColumnIndex(aColumnName);
        if (idx < 0)
        {
            return false;
        }

        int delegateIdx = columnMapping.get(idx);
        if (delegateIdx >= 0)
        {
            removedDelegateColumns.add(delegateIdx);
        }
        columnMapping.remove(idx);
        columnNames.remove(idx);
        columnMetaOverrides.remove(idx);

        // Re-key overrides that reference columns after the removed one
        reindexOverrides(idx);
        reindexColumnMeta(idx);

        metaDirty = true;
        return true;
    }


    /**
     * Adds a new column with the given name, type, and label. The column has no delegate data — all
     * cell values start as null/missing until set via {@link #setValue}.
     *
     * @param aName
     *            the column name
     * @param aType
     *            the data value type (null defaults to STRING)
     * @param aLabel
     *            the column label (null defaults to name)
     * @return the 0-based index of the new column
     */
    public int addColumn(String aName, DataValueType aType, String aLabel)
    {
        int newIndex = columnMapping.size();
        columnMapping.add(-1);
        columnNames.add(aName);
        columnMetaOverrides.put(newIndex,
                DataTableColumnMeta.builder().index(newIndex).name(aName)
                        .label(aLabel != null ? aLabel : aName)
                        .type(aType != null ? aType : DataValueType.STRING).build());
        metaDirty = true;
        return newIndex;
    }


    /**
     * Adds a new STRING column with the given name.
     *
     * @param aName
     *            the column name
     * @return the 0-based index of the new column
     */
    public int addColumn(String aName)
    {
        return addColumn(aName, DataValueType.STRING, aName);
    }


    /**
     * Overrides the table name (dataset_name) in the metadata.
     *
     * @param aName
     *            the new table name
     */
    public void setTableName(String aName)
    {
        nameOverride = aName;
        metaDirty = true;
    }


    /**
     * Overrides the table label (dataset_label) in the metadata.
     *
     * @param aLabel
     *            the new table label
     */
    public void setTableLabel(String aLabel)
    {
        labelOverride = aLabel;
        metaDirty = true;
    }


    /**
     * Overrides the table's source URI, i.e. what {@code getMetaData().getTableURI()} answers. A
     * virtual table built by {@link #empty(String, String, int)} has none, so this is how a
     * synthetic fixture gives itself the source location that URI-backed accessors (e.g.
     * {@code extract_metadata("dataset_location")}) read.
     *
     * @param aUri
     *            the source URI
     */
    public void setTableURI(URI aUri)
    {
        uriOverride = aUri;
        metaDirty = true;
    }


    /**
     * Sets or overrides a table-level custom metadata entry.
     *
     * @param aKey
     *            the metadata key (e.g. "dataset_size")
     * @param aValue
     *            the metadata value
     */
    public void setTableMetaData(String aKey, Object aValue)
    {
        tableMetaOverrides.put(aKey, aValue);
        metaDirty = true;
    }


    /**
     * Changes the label of a column.
     *
     * @param aColumnName
     *            the column name
     * @param aNewLabel
     *            the new label
     */
    public void setColumnLabel(String aColumnName, String aNewLabel)
    {
        int idx = findColumnIndex(aColumnName);
        if (idx >= 0)
        {
            DataTableColumnMeta existing = getEffectiveColumnMeta(idx);
            columnMetaOverrides.put(idx, existing.toBuilder().label(aNewLabel).build());
            metaDirty = true;
        }
    }


    /**
     * Changes the display format of a column.
     *
     * @param aColumnName
     *            the column name
     * @param aNewFormat
     *            the new display format string
     */
    public void setColumnFormat(String aColumnName, String aNewFormat)
    {
        int idx = findColumnIndex(aColumnName);
        if (idx >= 0)
        {
            DataTableColumnMeta existing = getEffectiveColumnMeta(idx);
            columnMetaOverrides.put(idx, existing.toBuilder().displayFormat(aNewFormat).build());
            metaDirty = true;
        }
    }


    /**
     * Changes the data type of a column.
     *
     * @param aColumnName
     *            the column name
     * @param aNewType
     *            the new data value type
     */
    public void setColumnType(String aColumnName, DataValueType aNewType)
    {
        int idx = findColumnIndex(aColumnName);
        if (idx >= 0)
        {
            DataTableColumnMeta existing = getEffectiveColumnMeta(idx);
            columnMetaOverrides.put(idx, existing.toBuilder().type(aNewType).build());
            metaDirty = true;
        }
    }


    /**
     * Changes the length of a column.
     *
     * @param aColumnName
     *            the column name
     * @param aNewLength
     *            the new length
     */
    public void setColumnLength(String aColumnName, int aNewLength)
    {
        int idx = findColumnIndex(aColumnName);
        if (idx >= 0)
        {
            DataTableColumnMeta existing = getEffectiveColumnMeta(idx);
            columnMetaOverrides.put(idx, existing.toBuilder().length(aNewLength).build());
            metaDirty = true;
        }
    }

    // ---- IDataTable implementation ----


    @Override
    public long getRowCount()
    {
        return delegate.getRowCount();
    }


    @Override
    public @Nullable Object getValue(long aRow, int aColumn) throws IndexOutOfBoundsException
    {
        ensureValidRow(aRow);
        ensureValidColumn(aColumn);

        long key = cellKey(aRow, aColumn);

        // Check value overlay
        if (valueOverrides.containsKey(key))
        {
            return valueOverrides.get(key);
        }

        // Check IDataValue overlay (extract raw value)
        IDataValue dvOverride = dataValueOverrides.get(key);
        if (dvOverride != null)
        {
            return dvOverride.getValue();
        }

        // Delegate to underlying table
        int delegateCol = columnMapping.get(aColumn);
        if (delegateCol < 0)
        {
            return null; // added column with no delegate
        }
        return delegate.getValue(aRow, delegateCol);
    }


    @Override
    public IDataValue getDataValue(long aRow, int aColumn)
    {
        long key = cellKey(aRow, aColumn);

        // Check IDataValue overlay first
        IDataValue dvOverride = dataValueOverrides.get(key);
        if (dvOverride != null)
        {
            return dvOverride;
        }

        // Check raw value overlay
        if (valueOverrides.containsKey(key))
        {
            Object val = valueOverrides.get(key);
            DataValueType t = getMetaData().getColumn(aColumn).getType();
            return DataValueSupport.getAsDataValue(val, t);
        }

        // Delegate
        int delegateCol = columnMapping.get(aColumn);
        if (delegateCol < 0)
        {
            return DataValueSupport.getAsDataValue(null, DataValueType.MISSING);
        }
        return delegate.getDataValue(aRow, delegateCol);
    }


    @Override
    public DataTableMeta getMetaData()
    {
        if (metaDirty || cachedMeta == null)
        {
            cachedMeta = buildMetaData();
            metaDirty = false;
        }
        return cachedMeta;
    }

    // ---- Internal helpers ----


    private int findColumnIndex(String aName)
    {
        for (int i = 0; i < columnNames.size(); i++)
        {
            if (aName.equals(columnNames.get(i)))
            {
                return i;
            }
        }
        // Case-insensitive fallback
        for (int i = 0; i < columnNames.size(); i++)
        {
            if (aName.equalsIgnoreCase(columnNames.get(i)))
            {
                return i;
            }
        }
        return -1;
    }


    private DataTableColumnMeta getEffectiveColumnMeta(int aOverlayIndex)
    {
        // Check if we have an override already
        DataTableColumnMeta override = columnMetaOverrides.get(aOverlayIndex);
        if (override != null)
        {
            return override;
        }

        // Get from delegate
        int delegateIdx = columnMapping.get(aOverlayIndex);
        if (delegateIdx >= 0)
        {
            DataTableColumnMeta delegateMeta = delegate.getMetaData().getColumn(delegateIdx);
            // Return a copy with the overlay index and possibly renamed name
            return delegateMeta.toBuilder().index(aOverlayIndex)
                    .name(columnNames.get(aOverlayIndex)).build();
        }

        // Fallback for added columns
        return DataTableColumnMeta.builder().index(aOverlayIndex)
                .name(columnNames.get(aOverlayIndex)).type(DataValueType.STRING).build();
    }


    private DataTableMeta buildMetaData()
    {
        DataTableMeta delegateMeta = delegate.getMetaData();
        int colCount = columnMapping.size();
        DataTableColumnMeta[] columns = new DataTableColumnMeta[colCount];

        for (int i = 0; i < colCount; i++)
        {
            DataTableColumnMeta cm = getEffectiveColumnMeta(i);
            // Ensure index matches position
            if (cm.getIndex() != i)
            {
                cm = cm.toBuilder().index(i).build();
            }
            columns[i] = cm;
        }

        var builder = DataTableMeta.builderFrom(delegateMeta).columns(columns);
        if (nameOverride != null)
        {
            builder.name(nameOverride);
        }
        if (labelOverride != null)
        {
            builder.label(labelOverride);
        }
        if (uriOverride != null)
        {
            builder.tableURI(uriOverride);
        }
        // Apply table-level metadata overrides
        if (!tableMetaOverrides.isEmpty())
        {
            for (Map.Entry<String, Object> entry : tableMetaOverrides.entrySet())
            {
                builder.addMetaData(entry.getKey(), entry.getValue());
            }
        }
        return builder.build();
    }


    private void ensureValidColumn(int aColumn)
    {
        if (aColumn < 0 || aColumn >= columnMapping.size())
        {
            throw new IndexOutOfBoundsException(
                    "Column index " + aColumn + " out of range [0, " + columnMapping.size() + ")");
        }
    }


    /**
     * Creates a unique key for a cell (row, column) pair. Uses a long with column in upper 16 bits
     * and row in lower 48 bits.
     */
    private static long cellKey(long aRow, int aColumn)
    {
        return ((long) aColumn << 48) | (aRow & 0xFFFFFFFFFFFFL);
    }


    /**
     * Re-indexes cell overrides after a column removal. All entries with column index > removed are
     * shifted down by 1.
     */
    private void reindexOverrides(int aRemovedIndex)
    {
        reindexMap(valueOverrides, aRemovedIndex);
        reindexMap(dataValueOverrides, aRemovedIndex);
    }


    private <V> void reindexMap(Map<Long, V> aMap, int aRemovedIndex)
    {
        Map<Long, V> updated = new HashMap<>();
        var it = aMap.entrySet().iterator();
        while (it.hasNext())
        {
            var entry = it.next();
            long key = entry.getKey();
            int col = (int) (key >> 48);
            long row = key & 0xFFFFFFFFFFFFL;
            if (col == aRemovedIndex)
            {
                it.remove();
            }
            else if (col > aRemovedIndex)
            {
                it.remove();
                updated.put(cellKey(row, col - 1), entry.getValue());
            }
        }
        aMap.putAll(updated);
    }


    private void reindexColumnMeta(int aRemovedIndex)
    {
        Map<Integer, DataTableColumnMeta> updated = new LinkedHashMap<>();
        for (var entry : columnMetaOverrides.entrySet())
        {
            int idx = entry.getKey();
            if (idx > aRemovedIndex)
            {
                DataTableColumnMeta cm = entry.getValue().toBuilder().index(idx - 1).build();
                updated.put(idx - 1, cm);
            }
            else if (idx < aRemovedIndex)
            {
                updated.put(idx, entry.getValue());
            }
            // idx == aRemovedIndex: skip (already removed)
        }
        columnMetaOverrides.clear();
        columnMetaOverrides.putAll(updated);
    }

}
