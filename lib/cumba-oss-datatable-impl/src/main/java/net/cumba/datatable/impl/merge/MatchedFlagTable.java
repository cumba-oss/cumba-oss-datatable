package net.cumba.datatable.impl.merge;

import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.ExMsgs;
import net.cumba.datatable.impl.AbstractDataTable;
import net.cumba.datatable.impl.databuffer.IDataBufferNumeric;
import net.cumba.datatable.merge.MergeKind;
import net.cumba.datatable.merge.MergeMeta;
import net.cumba.datatable.values.DataValueBoolean;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.IDataValue;
import org.jspecify.annotations.Nullable;

/**
 * The synthetic {@code <qualifier>._matched_} column of a keyed, child or RELREC part
 * ({@code PLAN-merged-evaluation-table} §3.1, P-14): a boolean read off the part's row map —
 * {@code true} on a merged row that carries a record of the part, {@code false} where the part is
 * not supplied. Always supplied itself. One single-column table per flag, so the merged table's
 * column dispatch stays uniform.
 */
final class MatchedFlagTable extends AbstractDataTable
{

    private static final IDataValue TRUE = new DataValueBoolean(true);

    private static final IDataValue FALSE = new DataValueBoolean(false);

    private final IDataBufferNumeric map;

    private final long rowCount;

    /**
     * @param aSourceName
     *            the part's source dataset name (the table's name, so {@code MERGED_FROM} names the
     *            same dataset as the part's other columns).
     * @param aColumnName
     *            the merged flag column name.
     * @param aQualifier
     *            the part's qualifier.
     * @param aKind
     *            the part's kind.
     * @param aRowCount
     *            the merged row count.
     * @param aMap
     *            the part's row map (merged row → source row, {@code -1} = not supplied).
     */
    MatchedFlagTable(String aSourceName, String aColumnName, String aQualifier, MergeKind aKind,
            long aRowCount, IDataBufferNumeric aMap)
    {
        map = aMap;
        rowCount = aRowCount;
        DataTableColumnMeta cm = DataTableColumnMeta.builder().index(0).name(aColumnName)
                .label("Matched " + aQualifier).type(DataValueType.BOOLEAN)
                .addMetaData(MergeMeta.MERGE_KIND, aKind.name())
                .addMetaData(MergeMeta.MERGED_SOURCE_COLUMN, MergeMeta.MATCHED_FLAG).build();
        setMetaData(DataTableMeta.builder().name(aSourceName).setColumns(cm).rowCount(aRowCount)
                .totalRowCount(aRowCount).build());
    }


    @Override
    public long getRowCount()
    {
        return rowCount;
    }


    private void ensureValidCell(long aRow, int aColumn)
    {
        ensureValidRow(aRow);
        if (aColumn != 0)
        {
            throw new IndexOutOfBoundsException(ExMsgs.indexOutOfBounds("column", aColumn, 0, 1));
        }
    }


    @Override
    public boolean isSupplied(long aRow, int aColumn) throws IndexOutOfBoundsException
    {
        ensureValidCell(aRow, aColumn);
        return true;
    }


    @Override
    public @Nullable Object getValue(long aRow, int aColumn) throws IndexOutOfBoundsException
    {
        return getDataValue(aRow, aColumn).getValue();
    }


    @Override
    public IDataValue getDataValue(long aRow, int aColumn)
    {
        ensureValidCell(aRow, aColumn);
        return map.getValueAsLong((int) aRow) >= 0 ? TRUE : FALSE;
    }
}
