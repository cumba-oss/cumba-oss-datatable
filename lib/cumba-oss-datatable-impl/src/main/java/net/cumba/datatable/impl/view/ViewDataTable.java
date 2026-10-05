package net.cumba.datatable.impl.view;

import java.util.Arrays;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.NonNull;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.ExMsgs;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.AbstractDataTable;
import net.cumba.datatable.values.DataValueMissing;
import net.cumba.datatable.values.DataValueString;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.IDataValue;
import net.cumba.datatable.values.MissingValue;
import net.cumba.datatable.view.IDataTableView;
import org.jspecify.annotations.Nullable;

/**
 * A {@link IDataTable} that is a view to another {@link IDataTable} through an applied
 * {@link IDataTableView} (a row map) and an optional column mapping — the part a merged evaluation
 * table is built from ({@code PLAN-merged-evaluation-table} §3.1).
 *
 * <p>
 * Ported from {@code cumba-datatable} with the features this twin does not carry withheld (the OSS
 * alignment doctrine: a ported class is ported whole unless it reaches an unported feature): the
 * constructors taking a condition and an order, the format catalog and the proposed-order pruning.
 * The {@code (table, view, columns)} constructor and every accessor below are identical in both
 * twins, which is all the shared matching uses.
 * </p>
 */
public class ViewDataTable extends AbstractDataTable
{

    /**
     * the data table to work on.
     */
    @Getter
    private final IDataTable table;

    /**
     * The view used to find the correct row for a given index.
     */
    @Getter
    private final IDataTableView view;

    /**
     * This map handles mapping for columns between view table and real table.<br/>
     * This can be used to hide or re-order columns or to show a column multiple times.
     */
    private final int @Nullable [] columnMap;

    /**
     * The missing sentinel used for non-character cells that map to an invalid column
     * ({@link #getBaseColumnIndex(int)} returned &lt; 0) or an invalid row
     * ({@link IDataTableView#getRealRow(IDataTable, long)} returned &lt; 0). Character columns use
     * the SAS/CDISC convention (empty string) instead — see {@link #columnUnavailable}.
     */
    @Getter
    private MissingValue valueUnavailable = MissingValue.MIS;

    /**
     * Per view-column value returned for cells with no backing row/column, following the SAS/CDISC
     * convention: {@link DataValueType#STRING} columns return an empty string, numeric/other
     * columns return the {@link #valueUnavailable} missing sentinel. Computed once per view (and on
     * {@link #setValueUnavailable}); one shared instance per type so callers can rely on identity.
     */
    private IDataValue[] columnUnavailable;

    /**
     * If this view maps columns, this is the column array (a copy) that was provided.
     */
    private DataTableColumnMeta @Nullable [] baseViewColumns;

    /**
     * Create a new view table with the given original table, view and columns, carrying no
     * condition and no order — the constructor the merged evaluation table builds its parts with,
     * identical in both datatable twins ({@code PLAN-merged-evaluation-table} §4).
     *
     * @param aTable
     *            the table that is the backend of this view table.
     * @param aView
     *            the row map; a mapped row of {@code -1} is a row with no backing row, whose cells
     *            are not supplied. May be null for the native order.
     * @param aColumns
     *            the columns to be displayed, referencing the backend table's indices. May be null
     *            to keep every column.
     */
    @SuppressWarnings("this-escape")
    public ViewDataTable(@NonNull IDataTable aTable, @Nullable IDataTableView aView,
            @Nullable Stream<DataTableColumnMeta> aColumns)
    {
        table = aTable;
        view = aView != null ? aView : NativeOrderView.INSTANCE;

        DataTableMeta md = table.getMetaData();
        DataTableColumnMeta[] columns;
        if (aColumns != null)
        {
            baseViewColumns = aColumns.toArray(DataTableColumnMeta[]::new);
            columns = Arrays.copyOf(baseViewColumns, baseViewColumns.length);

            int[] cmap = new int[columns.length];

            boolean mappingRequired = false;
            // map index values
            for (int i = 0; i < columns.length; i++)
            {
                DataTableColumnMeta cm = columns[i];
                int idx = cm.getIndex();

                mappingRequired = mappingRequired || idx != i;

                cmap[i] = idx;
                columns[i] = DataTableColumnMeta.builderFrom(cm)//
                        .index(i)//
                        .build();
            }
            columnMap = mappingRequired ? cmap : null;
        }
        else
        {
            columns = aTable.getMetaData().getAllColumns().toArray(DataTableColumnMeta[]::new);
            columnMap = null;
        }

        long totalRowCount = Math.max(view.getRowCount(table), md.getTotalRowCount());

        DataTableMeta meta = DataTableMeta.builderFrom(md)//
                .columns(columns)//
                .rowCount(view.getRowCount(table))//
                .totalRowCount(totalRowCount)//
                .build();
        setMetaData(meta);
        columnUnavailable = computeColumnUnavailable(columns);
    }


    /**
     * Set the missing sentinel returned for non-character unavailable cells. Character columns keep
     * the SAS/CDISC empty-string convention regardless.
     *
     * @param aValue
     *            the missing value to use for numeric/other columns.
     */
    public void setValueUnavailable(MissingValue aValue)
    {
        valueUnavailable = aValue;
        columnUnavailable = computeColumnUnavailable(
                getMetaData().getAllColumns().toArray(DataTableColumnMeta[]::new));
    }


    /**
     * Build the per-column unavailable values for the given view columns: empty string for
     * character columns, a shared {@link DataValueMissing} for everything else.
     */
    private IDataValue[] computeColumnUnavailable(DataTableColumnMeta[] aColumns)
    {
        IDataValue missing = new DataValueMissing(valueUnavailable);
        IDataValue emptyString = new DataValueString("");
        IDataValue[] res = new IDataValue[aColumns.length];
        for (int i = 0; i < aColumns.length; i++)
        {
            res[i] = aColumns[i].getType() == DataValueType.STRING ? emptyString : missing;
        }
        return res;
    }


    /**
     * Return the value used for cells of the given view column that have no backing row/column. By
     * default character columns return an empty string and numeric/other columns the
     * {@code getValueUnavailable()} missing sentinel; this can be overridden per column via
     * {@link #setColumnUnavailable(int, IDataValue)}.
     *
     * @param aColumn
     *            the 0-based view column index.
     * @return the unavailable value for that column.
     * @throws IndexOutOfBoundsException
     *             if {@code aColumn} is outside the valid bounds.
     */
    public IDataValue getColumnUnavailable(int aColumn) throws IndexOutOfBoundsException
    {
        return columnUnavailable[ensureValidColumn(aColumn)];
    }


    /**
     * Override the value returned for cells of the given view column that have no backing
     * row/column (e.g. to set a study-specific missing representation). The override persists until
     * the next {@link #setValueUnavailable(MissingValue)} call, which recomputes all columns.
     *
     * @param aColumn
     *            the 0-based view column index.
     * @param aValue
     *            the value to return for unavailable cells of that column.
     * @throws IndexOutOfBoundsException
     *             if {@code aColumn} is outside the valid bounds.
     */
    public void setColumnUnavailable(int aColumn, @NonNull IDataValue aValue)
        throws IndexOutOfBoundsException
    {
        columnUnavailable[ensureValidColumn(aColumn)] = aValue;
    }


    private int ensureValidColumn(int aColumn) throws IndexOutOfBoundsException
    {
        if (aColumn < 0 || aColumn >= columnUnavailable.length)
        {
            throw new IndexOutOfBoundsException(
                    ExMsgs.indexOutOfBounds("column", aColumn, 0, columnUnavailable.length));
        }
        return aColumn;
    }


    /**
     * Return the column mapping used by this view, if any.
     *
     * @return If this view was created with a column mapping, this is the column mapping that was
     *         used, otherwise this is null.
     */
    public DataTableColumnMeta @Nullable [] getBaseViewColumns()
    {
        return (baseViewColumns != null) ? Arrays.copyOf(baseViewColumns, baseViewColumns.length)
                : null;
    }


    @Override
    public long getRowCount()
    {
        return view.getRowCount(table);
    }


    /**
     * Return whether this view uses a column mapping.
     *
     * @return true if this view uses column mapping, false otherwise.
     */
    public boolean usesColumnMapping()
    {
        return columnMap != null;
    }


    /**
     * Retrieve the real column index (in backend table) for the column with the given index.
     *
     * @param aColumn
     *            the column index of the column in the view table.
     * @return the column index of the column in the backend table.
     * @throws IndexOutOfBoundsException
     *             if {@code aColumn} is outside the valid bounds of this view's columns.
     */
    public int getBaseColumnIndex(int aColumn) throws IndexOutOfBoundsException
    {
        ensureValidColumn(aColumn);
        if (columnMap == null)
        {
            return aColumn;
        }
        return columnMap[aColumn];
    }


    /**
     * Translate a row id from view table coordinates to the underlying backend table coordinates.
     *
     * @param aRowId
     *            the row id in view table coordinates.
     * @return the row id in underlying backend table coordinates.
     */
    public long getBaseRowIndex(long aRowId)
    {
        return view.getRealRow(table, aRowId);
    }


    @Override
    public long getRealRowIndex(long aRowIndex) throws IndexOutOfBoundsException
    {
        long bri = getBaseRowIndex(aRowIndex);
        return table.getRealRowIndex(bri);
    }


    @Override
    public long getDisplayRowIndex(long aRealRowIndex)
    {
        long bri = table.getDisplayRowIndex(aRealRowIndex);
        if (bri < 0)
        {
            return -1;
        }
        return view.getDisplayRow(table, bri);
    }


    /**
     * {@inheritDoc}
     *
     * <p>
     * A cell whose mapped base row is {@code -1} (no backing row) or whose column maps to no base
     * column is <b>not supplied</b>; every other cell delegates to the backend table, so a marker
     * of a view underneath this one survives.
     * </p>
     */
    @Override
    public boolean isSupplied(long aRow, int aColumn) throws IndexOutOfBoundsException
    {
        ensureValidColumn(aColumn);
        long realRowId = getBaseRowIndex(aRow);
        int realColId = getBaseColumnIndex(aColumn);
        return realRowId >= 0 && realColId >= 0 && table.isSupplied(realRowId, realColId);
    }


    @Override
    public @Nullable Object getValue(long aRow, int aColumn) throws IndexOutOfBoundsException
    {
        ensureValidColumn(aColumn);
        long realRowId = getBaseRowIndex(aRow);
        if (realRowId < 0)
        {
            return columnUnavailable[aColumn].getValue();
        }
        int realColId = getBaseColumnIndex(aColumn);
        if (realColId < 0)
        {
            return columnUnavailable[aColumn].getValue();
        }
        return table.getValue(realRowId, realColId);
    }


    @Override
    public IDataValue getDataValue(long aRow, int aColumn)
    {
        ensureValidColumn(aColumn);
        long realRowId = getBaseRowIndex(aRow);
        if (realRowId < 0)
        {
            return columnUnavailable[aColumn];
        }
        int realColId = getBaseColumnIndex(aColumn);
        if (realColId < 0)
        {
            return columnUnavailable[aColumn];
        }
        return table.getDataValue(realRowId, realColId);
    }
}
