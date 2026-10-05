package net.cumba.datatable.impl.view;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.DataTableMeta.DataTableMetaBuilder;
import net.cumba.datatable.ExMsgs;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.help.CDT;
import net.cumba.datatable.impl.AbstractDataTable;
import net.cumba.datatable.values.IDataValue;
import org.jspecify.annotations.Nullable;

/**
 * A {@link IDataTable} that can display two data tables next to each other. Both data tables must
 * contain the same number of rows and must not define columns with the same name.
 */
public class MergeDataTable extends AbstractDataTable
{

    /**
     * Custom metadata key added to columns that originate from a merged (non-primary) table. The
     * value is the name of the source table the column was merged from.
     */
    public static final String META_MERGED_FROM = "MERGED_FROM";

    /**
     * The tables that are merged side-by-side.
     */
    private final IDataTable[] tables;

    /**
     * Per merged column, the index of the part that holds it — the O(1) dispatch that replaced the
     * linear scan over the parts (a merged evaluation table with a part per joined dataset put the
     * last column 3–6× behind a primary read, {@code PLAN-merged-evaluation-table} M3).
     */
    private final int[] partOfColumn;

    /** Per merged column, its index within its part. */
    private final int[] columnInPart;

    /**
     * Determine if column names are case-sensitive across all given tables.
     *
     * @param aTables
     *            the tables to check.
     * @return true if all tables use case-sensitive column names, false otherwise.
     */
    private static boolean columnNamesCaseSensitive(IDataTable... aTables)
    {
        for (IDataTable t : aTables)
        {
            if (!t.getMetaData().isColumnNameCaseSensitive())
            {
                return false;
            }
        }
        return true;
    }


    /**
     * Create the merged table metadata from the given tables.
     *
     * @param aBaseMeta
     *            an optional base metadata to initialize from. May be null.
     * @param aTables
     *            the tables to merge. All must have the same row count and unique column names.
     * @return the merged metadata.
     * @throws IllegalArgumentException
     *             if tables have different row counts, null entries, or duplicate column names.
     */
    private static DataTableMeta createMetaData(@Nullable DataTableMeta aBaseMeta,
            IDataTable... aTables)
        throws IllegalArgumentException
    {
        Set<String> colNames = new HashSet<>();

        List<DataTableColumnMeta> columnMeta = new ArrayList<>();

        for (int tblIdx = 0; tblIdx < aTables.length; tblIdx++)
        {
            IDataTable t = aTables[tblIdx];

            if (t == null)
            {
                throw new IllegalArgumentException(
                        "Tables must not contain any null references. Found null at index %d."
                                .formatted(tblIdx));
            }

            DataTableMeta m = t.getMetaData();
            if (m == null)
            {
                throw new IllegalArgumentException(
                        "Tables must contain metadata. Found null metadata at index %d."
                                .formatted(tblIdx));
            }

            if (tblIdx > 0 && t.getRowCount() != aTables[0].getRowCount())
            {
                String msg = MessageFormat.format(
                        "Table {0} ({1}) has invalid row count {2} (expected={3})!", tblIdx,
                        m.getName(), t.getRowCount(), aTables[0].getRowCount());
                throw new IllegalArgumentException(msg);
            }
        }

        boolean colNamesCaseSensitive = columnNamesCaseSensitive(aTables);

        for (int tblIdx = 0; tblIdx < aTables.length; tblIdx++)
        {
            IDataTable t = aTables[tblIdx];
            DataTableMeta m = t.getMetaData();
            for (int colIdx = 0; colIdx < m.getColumnCount(); colIdx++)
            {
                DataTableColumnMeta cm = m.getColumn(colIdx);
                String colNameKey = colNamesCaseSensitive ? cm.getName()
                        : cm.getName().toLowerCase(Locale.ROOT);
                if (colNames.contains(colNameKey))
                {
                    throw new IllegalArgumentException(
                            "Table %d (%s) defines column %s, that was already defined previously. This is not allowed."
                                    .formatted(tblIdx, m.getName(), cm.getName()));
                }
                colNames.add(colNameKey);

                DataTableColumnMeta.DataTableColumnMetaBuilder cmb = DataTableColumnMeta
                        .builderFrom(cm).index(columnMeta.size());

                // Tag columns from the 2nd+ tables as merged
                if (tblIdx > 0)
                {
                    cmb.addMetaData(META_MERGED_FROM, m.getName());
                }

                columnMeta.add(cmb.build());
            }
        }

        DataTableMetaBuilder mb;
        if (aBaseMeta != null)
        {
            mb = DataTableMeta.builderFrom(aBaseMeta);
        }
        else
        {
            mb = DataTableMeta.builderFrom(aTables[0].getMetaData())
                    .name(aTables[0].getMetaData().getName() + "+");
        }

        mb//
                .setColumns(columnMeta)//
                .rowCount(aTables[0].getRowCount())//
                .totalRowCount(aTables[0].getRowCount());

        return mb.build();
    }


    /**
     * Create a new merge table from the given tables.
     *
     * @param aTables
     *            the tables to merge side-by-side.
     * @throws IllegalArgumentException
     *             if the tables do not match all requirements for merging.
     */
    public MergeDataTable(IDataTable... aTables) throws IllegalArgumentException
    {
        this(null, aTables);
    }


    /**
     * Create a new merge table from the given tables with optional base metadata.
     *
     * @param aBaseMeta
     *            an optional base metadata to initialize the merged metadata from. May be null.
     * @param aTables
     *            the tables to merge side-by-side.
     * @throws IllegalArgumentException
     *             if the tables do not match all requirements for merging.
     */
    @SuppressWarnings("this-escape")
    public MergeDataTable(@Nullable DataTableMeta aBaseMeta, IDataTable... aTables)
        throws IllegalArgumentException
    {
        if (CDT.isEmptyOrNull(aTables))
        {
            throw new IllegalArgumentException("At least 1 table has to be given!");
        }

        tables = Arrays.copyOf(aTables, aTables.length);

        // createMetaData performs the null / row-count validation. Run it first so a bad input
        // throws IllegalArgumentException (the documented contract) before any aTables[0]
        // dereference would NPE.
        setMetaData(createMetaData(aBaseMeta, tables));
        int total = 0;
        for (IDataTable t : tables)
        {
            total += t.getColumnCount();
        }
        partOfColumn = new int[total];
        columnInPart = new int[total];
        int c = 0;
        for (int t = 0; t < tables.length; t++)
        {
            int colCount = tables[t].getColumnCount();
            for (int i = 0; i < colCount; i++, c++)
            {
                partOfColumn[c] = t;
                columnInPart[c] = i;
            }
        }
    }


    /**
     * The number of tables merged side by side.
     *
     * @return the part count.
     */
    public int partCount()
    {
        return tables.length;
    }


    /**
     * The table holding the given merged column.
     *
     * @param aColumn
     *            a merged column index.
     * @return the index of its part, {@code 0} for the first table.
     * @throws IndexOutOfBoundsException
     *             for an invalid column.
     */
    public int partOfColumn(int aColumn) throws IndexOutOfBoundsException
    {
        ensureValidColumn(aColumn);
        return partOfColumn[aColumn];
    }


    /**
     * The table at the given part index.
     *
     * @param aPart
     *            a part index, {@code 0 <= aPart < partCount()}.
     * @return the part's table.
     * @throws IndexOutOfBoundsException
     *             for an invalid part.
     */
    public IDataTable part(int aPart) throws IndexOutOfBoundsException
    {
        if (aPart < 0 || aPart >= tables.length)
        {
            throw new IndexOutOfBoundsException(
                    ExMsgs.indexOutOfBounds("part", aPart, 0, tables.length));
        }
        return tables[aPart];
    }


    private void ensureValidColumn(int aColumn) throws IndexOutOfBoundsException
    {
        int totalColCount = partOfColumn.length;
        if (aColumn < 0 || aColumn >= totalColCount)
        {
            throw new IndexOutOfBoundsException(
                    ExMsgs.indexOutOfBounds("column", aColumn, 0, totalColCount));
        }
    }


    /**
     * {@inheritDoc}
     *
     * <p>
     * Delegates to the part holding the column, so a part's not-supplied marker survives the merge.
     * </p>
     */
    @Override
    public boolean isSupplied(long aRow, int aColumn) throws IndexOutOfBoundsException
    {
        ensureValidColumn(aColumn);
        return tables[partOfColumn[aColumn]].isSupplied(aRow, columnInPart[aColumn]);
    }


    @Override
    public long getRowCount()
    {
        return tables[0].getRowCount();
    }


    @Override
    public long getRealRowIndex(long aRowIndex) throws IndexOutOfBoundsException
    {
        return tables[0].getRealRowIndex(aRowIndex);
    }


    @Override
    public @Nullable Object getValue(long aRow, int aColumn) throws IndexOutOfBoundsException
    {
        ensureValidColumn(aColumn);
        return tables[partOfColumn[aColumn]].getValue(aRow, columnInPart[aColumn]);
    }


    @Override
    public IDataValue getDataValue(long aRow, int aColumn)
    {
        ensureValidColumn(aColumn);
        return tables[partOfColumn[aColumn]].getDataValue(aRow, columnInPart[aColumn]);
    }

}
