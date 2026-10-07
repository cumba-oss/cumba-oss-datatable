package net.cumba.datatable.impl.merge;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import java.util.List;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.ExMsgs;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.AbstractDataTable;
import net.cumba.datatable.impl.databuffer.IDataBufferNumeric;
import net.cumba.datatable.values.DataValueSupport;
import net.cumba.datatable.values.IDataValue;
import org.jspecify.annotations.Nullable;

/**
 * The supplemental part of a merged table ({@code PLAN-merged-evaluation-table} §3.1, part
 * {@code s}): one column per {@code QNAM}, each a view of the supplemental dataset's {@code QVAL}
 * column through its own compact row map (merged row → base row → supplemental row). A cell whose
 * map says {@code -1} is <b>not supplied</b> and reads the type default; a column nothing can
 * supply (flagged {@code MERGE_ERROR}) has no map at all.
 */
final class SuppPart extends AbstractDataTable
{

    private final IDataTable supp;

    private final int qvalIdx;

    private final @Nullable IDataBufferNumeric baseMap;

    private final @Nullable IDataBufferNumeric[] maps;

    private final IDataValue[] unavailable;

    private final long rowCount;

    /**
     * @param aName
     *            the part's name (the supplemental dataset's, which {@code MergeDataTable}
     *            publishes as {@code MERGED_FROM}).
     * @param aRowCount
     *            the merged row count.
     * @param aSupp
     *            the supplemental dataset.
     * @param aQvalIdx
     *            its {@code QVAL} column.
     * @param aBaseMap
     *            merged row → base row, or {@code null} when they coincide; a {@code -1} is a row
     *            with no base record.
     * @param aColumns
     *            the column metadata, in part order, indices {@code 0..n-1}.
     * @param aMaps
     *            per column, base row → supplemental row ({@code -1} = none), or {@code null} for
     *            an all-unsupplied column.
     */
    SuppPart(String aName, long aRowCount, IDataTable aSupp, int aQvalIdx,
            @Nullable IDataBufferNumeric aBaseMap, List<DataTableColumnMeta> aColumns,
            @Nullable IDataBufferNumeric[] aMaps)
    {
        supp = aSupp;
        qvalIdx = aQvalIdx;
        baseMap = aBaseMap;
        maps = aMaps.clone();
        rowCount = aRowCount;
        unavailable = new IDataValue[aColumns.size()];
        for (int i = 0; i < unavailable.length; i++)
        {
            unavailable[i] = DataValueSupport.defaultForType(aColumns.get(i).getType());
        }
        setMetaData(DataTableMeta.builder().name(aName).setColumns(aColumns).rowCount(aRowCount)
                .totalRowCount(aRowCount).build());
    }


    /**
     * {@inheritDoc}
     *
     * <p>
     * The SUPP table's generation: this part serves its cells through the pivot maps, so a change
     * below it invalidates the cached column contents.
     * </p>
     */
    @Override
    protected long sourceContentGeneration()
    {
        return supp.contentGeneration();
    }


    @Override
    public long getRowCount()
    {
        return rowCount;
    }


    private int ensureValidColumn(int aColumn)
    {
        if (aColumn < 0 || aColumn >= maps.length)
        {
            throw new IndexOutOfBoundsException(
                    ExMsgs.indexOutOfBounds("column", aColumn, 0, maps.length));
        }
        return aColumn;
    }


    /** The supplemental row of a cell, or {@code -1} when the cell is not supplied. */
    private long suppRowOf(long aRow, int aColumn)
    {
        ensureValidRow(aRow);
        IDataBufferNumeric map = maps[ensureValidColumn(aColumn)];
        if (map == null)
        {
            return -1;
        }
        long baseRow = baseMap == null ? aRow : baseMap.getValueAsLong((int) aRow);
        if (baseRow < 0)
        {
            return -1;
        }
        return map.getValueAsLong((int) baseRow);
    }


    @Override
    public boolean isSupplied(long aRow, int aColumn) throws IndexOutOfBoundsException
    {
        return suppRowOf(aRow, aColumn) >= 0;
    }


    @Override
    public @Nullable Object getValue(long aRow, int aColumn) throws IndexOutOfBoundsException
    {
        return getDataValue(aRow, aColumn).getValue();
    }


    @Override
    public IDataValue getDataValue(long aRow, int aColumn)
    {
        long suppRow = suppRowOf(aRow, aColumn);
        if (suppRow < 0)
        {
            return unavailable[aColumn];
        }
        return supp.getDataValue(suppRow, qvalIdx);
    }
}
