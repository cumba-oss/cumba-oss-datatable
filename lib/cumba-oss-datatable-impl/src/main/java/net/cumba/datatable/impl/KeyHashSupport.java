package net.cumba.datatable.impl;

import java.util.Objects;

import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.view.HashLookup;

/**
 * Shared utility for computing multi-column key hashes and comparing key column values between
 * rows. Used by both view/merge operations and index creation.
 */
public final class KeyHashSupport
{

    private KeyHashSupport()
    {
        // utility class
    }


    /**
     * Compute a hash code for the key column values of a single row. Routes through
     * {@link IDataTable#hashCodeAt(long, int)} so buffer-backed tables use the non-boxing typed
     * hash path.
     *
     * @param aTable
     *            the table to read values from.
     * @param aRow
     *            the row index.
     * @param aColIds
     *            the column indices to include in the hash.
     * @return a 32-bit hash code (never 0).
     */
    public static int computeKeyHash(IDataTable aTable, long aRow, int[] aColIds)
    {
        int h = 0;
        for (int colId : aColIds)
        {
            h = 31 * h + aTable.hashCodeAt(aRow, colId);
        }
        return h != 0 ? h : 1;
    }

    /**
     * Reusable {@link HashLookup.BiRowMatcher} that compares key column values between two rows of
     * (possibly different) tables. A single instance can be shared across all iterations of a loop.
     */
    public static class KeyColumnMatcher implements HashLookup.BiRowMatcher
    {

        private final IDataTable table1;

        private final int[] colIds1;

        private final IDataTable table2;

        private final int[] colIds2;

        public KeyColumnMatcher(IDataTable aTable1, int[] aColIds1, IDataTable aTable2,
                int[] aColIds2)
        {
            table1 = aTable1;
            colIds1 = aColIds1;
            table2 = aTable2;
            colIds2 = aColIds2;
        }


        @Override
        public boolean matches(int aTable1Row, int aTable2Row)
        {
            for (int i = 0; i < colIds1.length; i++)
            {
                if (!Objects.equals(table1.getValue(aTable1Row, colIds1[i]),
                        table2.getValue(aTable2Row, colIds2[i])))
                {
                    return false;
                }
            }
            return true;
        }
    }
}
