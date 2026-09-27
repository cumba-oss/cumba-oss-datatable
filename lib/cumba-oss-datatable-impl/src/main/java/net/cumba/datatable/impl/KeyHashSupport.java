package net.cumba.datatable.impl;

// OSS-IDENTITY datatable-index-identity: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import java.util.Objects;

import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.view.HashLookup;
import org.jspecify.annotations.Nullable;

/**
 * The one <b>key identity of the datatable's hash structures</b>: the multi-column key hash and the
 * key equality every index, merge and split compares rows with. Used by the index factory (grouping
 * — the conformance engine's {@code group=}, {@code within=}, {@code keys=} and
 * {@code Grouping.Variables}), by the key-table merges (the manager's and the data browser's) and
 * by the statistics by-groups.
 *
 * <p>
 * ⭐ <b>The contract: exact, and {@code -0.0}-aware</b> ({@code PLAN-grouping-key-identity}, owner
 * 2026-09-27: <i>"-0 and 0 are to be treated as one key everywhere if this is not touching
 * performance"</i>). Two cells are one key when {@link Objects#equals} says so, or when both are a
 * zero of the same floating type: IEEE 754 defines {@code -0.0 == 0.0}, and only the bit pattern
 * ({@link Double#equals}, {@link Double#hashCode}) separates them — representation, not value
 * (register {@code D84}: <i>"we use own tolerance and will always work on the real value"</i>).
 * Everything else stays exact: {@code 4.9999999999994} and {@code 5.0} are two keys (register
 * {@code D64h}: <i>"key identity always exact"</i>), a {@code Long} never equals a {@code Double}
 * (one column is one type; a merge requires equal column types), and a {@code NaN} equals nothing
 * it did not equal before.
 * </p>
 *
 * <p>
 * <b>Why here and not in a merge pass.</b> The fold costs one {@code int} compare per hashed cell
 * and allocates nothing; {@link #keyEquals} reaches its zero test only on the unequal path, which
 * {@link HashLookup} enters only for a hash-equal candidate. A merge pass after the index would
 * allocate a key per block on every grouping path, and would not reach the datatable's own merges.
 * </p>
 *
 * <p>
 * ⚑ <b>Byte-identical in {@code cumba-oss-datatable}</b> (owner 2026-09-27, Q5 (a)): the identity
 * core — this class, {@link HashLookup}, and the engine-facing {@code GroupKey} and
 * {@code GroupKeyPolicy} — is shared, while each twin's {@code DataTableIndexFactoryImpl} keeps its
 * own storage-specific block assembly and calls {@link RepRowMatcher} and {@link #computeKeyHash}.
 * It reads only {@link IDataTable#getValue} and {@link IDataTable#hashCodeAt}.
 * </p>
 */
public final class KeyHashSupport
{

    /**
     * {@link Double#hashCode(double) Double.hashCode(-0.0)} and {@link Float#hashCode(float)
     * Float.hashCode(-0.0f)}: the sign bit alone. A zero of either sign hashes as {@code 0.0} does.
     * Any other cell whose hash happens to be this value merely shares a bucket with the zeros,
     * which the equality below resolves.
     */
    private static final int NEGATIVE_ZERO_HASH = Integer.MIN_VALUE;

    private KeyHashSupport()
    {
        // utility class
    }


    /**
     * Compute a hash code for the key column values of a single row. Routes through
     * {@link IDataTable#hashCodeAt(long, int)} so buffer-backed tables use the non-boxing typed
     * hash path; a cell hash of {@code -0.0} is folded onto that of {@code 0.0}, so the hash agrees
     * with {@link #keyEquals}.
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
            int cell = aTable.hashCodeAt(aRow, colId);
            h = 31 * h + (cell == NEGATIVE_ZERO_HASH ? 0 : cell);
        }
        return h != 0 ? h : 1;
    }


    /**
     * The key equality of two cell values: {@link Objects#equals}, and additionally {@code true}
     * when both are a zero of the same floating type ({@code -0.0} and {@code 0.0}).
     *
     * @param aValue1
     *            a cell value, as {@link IDataTable#getValue} answers it
     * @param aValue2
     *            the other cell value
     * @return whether the two cells are one key
     */
    public static boolean keyEquals(@Nullable Object aValue1, @Nullable Object aValue2)
    {
        if (Objects.equals(aValue1, aValue2))
        {
            return true;
        }
        if (aValue1 instanceof Double d1 && aValue2 instanceof Double d2)
        {
            return d1 == 0.0 && d2 == 0.0;
        }
        return aValue1 instanceof Float f1 && aValue2 instanceof Float f2 && f1 == 0.0f
                && f2 == 0.0f;
    }

    /**
     * Reusable {@link HashLookup.BiRowMatcher} that compares key column values between two rows of
     * (possibly different) tables through {@link KeyHashSupport#keyEquals}. A single instance can
     * be shared across all iterations of a loop.
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
                if (!keyEquals(table1.getValue(aTable1Row, colIds1[i]),
                        table2.getValue(aTable2Row, colIds2[i])))
                {
                    return false;
                }
            }
            return true;
        }
    }


    /**
     * The index factory's {@link HashLookup.RowMatcher}: the lookup stores <b>group ids</b>, and a
     * candidate group matches when its representative row (looked up via the group-id →
     * representative-row array) agrees with the current row on every key column through
     * {@link KeyHashSupport#keyEquals}. Moved here from both twins'
     * {@code DataTableIndexFactoryImpl} so the grouping identity of the two datatables is one piece
     * of code.
     */
    public static class RepRowMatcher implements HashLookup.RowMatcher
    {

        private final IDataTable table;

        private final int[] colIds;

        private int[] groupRepRow;

        private int currentRow;

        /**
         * A matcher over {@code aTable}'s key columns.
         *
         * @param aTable
         *            the table being indexed
         * @param aColIds
         *            the key column indices
         * @param aGroupRepRow
         *            the group id → representative row array; replace it through
         *            {@link #setGroupRepRow} whenever the caller grows it
         */
        public RepRowMatcher(IDataTable aTable, int[] aColIds, int[] aGroupRepRow)
        {
            table = aTable;
            colIds = aColIds;
            groupRepRow = aGroupRepRow;
        }


        /**
         * Sets the row the next {@link #matches} calls compare against.
         *
         * @param aRow
         *            the row being assigned to a group
         */
        public void setCurrentRow(int aRow)
        {
            currentRow = aRow;
        }


        /**
         * Replaces the representative-row array after the caller grew it.
         *
         * @param aGroupRepRow
         *            the (grown) group id → representative row array
         */
        public void setGroupRepRow(int[] aGroupRepRow)
        {
            groupRepRow = aGroupRepRow;
        }


        @Override
        public boolean matches(int aCandidateGroupId)
        {
            int repRow = groupRepRow[aCandidateGroupId];
            for (int colId : colIds)
            {
                if (!keyEquals(table.getValue(repRow, colId), table.getValue(currentRow, colId)))
                {
                    return false;
                }
            }
            return true;
        }
    }
}
