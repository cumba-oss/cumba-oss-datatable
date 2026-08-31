package net.cumba.datatable.impl.index;

import java.util.Arrays;

import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.KeyHashSupport;
import net.cumba.datatable.impl.databuffer.DataBufferFactory;
import net.cumba.datatable.impl.databuffer.IDataBufferNumeric;
import net.cumba.datatable.impl.view.DataTableViewBuffer;
import net.cumba.datatable.impl.view.HashLookup;
import net.cumba.datatable.index.DataTableIndexFactory;
import net.cumba.datatable.index.IDataTableIndex;
import net.cumba.datatable.view.IDataTableView;

/**
 * This is the default implementation of the DataTableIndexFactory.
 */
public class DataTableIndexFactoryImpl extends DataTableIndexFactory
{

    @Override
    public IDataTableIndex createIndex(IDataTable aTable, String... aColumns)
    {
        if (aTable == null)
        {
            throw new IllegalArgumentException("Table must not be null.");
        }
        if (aColumns == null || aColumns.length == 0)
        {
            throw new IllegalArgumentException("At least one column must be specified.");
        }

        return createHashIndex(aTable, aColumns);
    }


    /**
     * Resolve column names to column indices, validating that all columns exist.
     *
     * @param aMeta
     *            the table metadata.
     * @param aColumns
     *            the column names.
     * @return array of column indices.
     * @throws IllegalArgumentException
     *             if any column is not found.
     */
    private int[] resolveColumnIds(DataTableMeta aMeta, String[] aColumns)
    {
        int[] colIds = new int[aColumns.length];
        for (int i = 0; i < aColumns.length; i++)
        {
            int idx = aMeta.getColumnIndex(aColumns[i]);
            if (idx < 0)
            {
                throw new IllegalArgumentException(
                        "Column '%s' not found in table.".formatted(aColumns[i]));
            }
            colIds[i] = idx;
        }
        return colIds;
    }


    /**
     * Create a hash-based (unsorted) index. Uses {@link HashLookup} for group assignment and a
     * bit-packed {@link IDataBufferNumeric} for memory-efficient group ID storage per row.
     * <p>
     * The HashLookup stores group IDs (not row indices) in its slots. When probing, the matcher
     * resolves the representative row of each candidate group via a separate array.
     */
    IDataTableIndex createHashIndex(IDataTable aTable, String[] aColumns)
    {
        DataTableMeta meta = aTable.getMetaData();
        int[] colIds = resolveColumnIds(meta, aColumns);
        long rowCountL = aTable.getRowCount();

        if (rowCountL == 0)
        {
            return new DefaultDataTableIndex(new IDataTableView[0]);
        }

        int rowCount = Math.toIntExact(rowCountL);

        // Phase 1: Assign group IDs using HashLookup + bit-packed numeric buffer
        HashLookup lookup = new HashLookup(rowCount, 0.75f);

        DataBufferFactory bufFactory = DataBufferFactory.get();

        // group ID per row — at most one group per row.
        IDataBufferNumeric groupIds = bufFactory.createForRange(0, (long) rowCount - 1);
        groupIds.setExpectedSize(rowCount);

        // maps groupId -> representative row index (for equality checking)
        int[] groupRepRow = new int[16];
        int groupCount = 0;

        // The matcher compares the representative row of a candidate group (looked up via
        // groupRepRow[candidateGroupId]) against the current row being assigned.
        GroupRepMatcher matcher = new GroupRepMatcher(aTable, colIds, groupRepRow);

        for (int row = 0; row < rowCount; row++)
        {
            int hash = KeyHashSupport.computeKeyHash(aTable, row, colIds);

            matcher.setCurrentRow(row);
            int existingGroupId = lookup.get(hash, matcher);

            if (existingGroupId >= 0)
            {
                groupIds.setLongValue(row, existingGroupId);
            }
            else
            {
                // new group
                int newGroupId = groupCount++;

                // enlarge groupRepRow if needed
                if (newGroupId >= groupRepRow.length)
                {
                    groupRepRow = Arrays.copyOf(groupRepRow, groupRepRow.length * 2);
                    matcher.setGroupRepRow(groupRepRow);
                }
                groupRepRow[newGroupId] = row;

                groupIds.setLongValue(row, newGroupId);
                lookup.put(hash, newGroupId);
            }
        }

        // Phase 2: Count rows per group
        int[] groupSizes = new int[groupCount];
        for (int row = 0; row < rowCount; row++)
        {
            groupSizes[groupIds.getValueAsInt(row)]++;
        }

        // Phase 3: Distribute rows into blocks using DataBufferXBit-backed views
        return buildBlocks(groupCount, groupSizes, groupIds, rowCount, rowCountL);
    }


    /**
     * Build blocks from group assignments.
     */
    private IDataTableIndex buildBlocks(int aGroupCount, int[] aGroupSizes,
            IDataBufferNumeric aGroupIds, int aRowCount, long aTableRowCount)
    {
        int[][] groupRows = new int[aGroupCount][];
        for (int g = 0; g < aGroupCount; g++)
        {
            groupRows[g] = new int[aGroupSizes[g]];
        }
        int[] offsets = new int[aGroupCount];

        for (int row = 0; row < aRowCount; row++)
        {
            int g = aGroupIds.getValueAsInt(row);
            groupRows[g][offsets[g]++] = row;
        }

        IDataTableView[] blocks = new IDataTableView[aGroupCount];
        for (int g = 0; g < aGroupCount; g++)
        {
            blocks[g] = createCompactView(groupRows[g], aTableRowCount);
        }

        return new DefaultDataTableIndex(blocks);
    }


    /**
     * Create a compact view from the given row indices. The buffer is allocated via
     * {@link DataBufferFactory#createForRange(long, long)} so it picks int- or long-backed storage
     * based on the source row count.
     */
    private IDataTableView createCompactView(int[] aRowIndices, long aTableRowCount)
    {
        long maxRow = Math.max(0L, aTableRowCount - 1);
        IDataBufferNumeric buf = DataBufferFactory.get().createForRange(0, maxRow);
        buf.setExpectedSize(aRowIndices.length);
        for (int i = 0; i < aRowIndices.length; i++)
        {
            buf.setLongValue(i, aRowIndices[i]);
        }
        buf.trimToSize();
        return DataTableViewBuffer.builder().buffer(buf).build();
    }

    /**
     * A {@link HashLookup.RowMatcher} that interprets stored values as group IDs and compares the
     * representative row of the candidate group against the current row being assigned.
     */
    private static class GroupRepMatcher implements HashLookup.RowMatcher
    {

        private final IDataTable table;

        private final int[] colIds;

        private int[] groupRepRow;

        private int currentRow;

        GroupRepMatcher(IDataTable aTable, int[] aColIds, int[] aGroupRepRow)
        {
            table = aTable;
            colIds = aColIds;
            groupRepRow = aGroupRepRow;
        }


        void setCurrentRow(int aRow)
        {
            currentRow = aRow;
        }


        void setGroupRepRow(int[] aGroupRepRow)
        {
            groupRepRow = aGroupRepRow;
        }


        @Override
        public boolean matches(int aCandidateGroupId)
        {
            int repRow = groupRepRow[aCandidateGroupId];
            for (int i = 0; i < colIds.length; i++)
            {
                if (!java.util.Objects.equals(table.getValue(repRow, colIds[i]),
                        table.getValue(currentRow, colIds[i])))
                {
                    return false;
                }
            }
            return true;
        }
    }
}
