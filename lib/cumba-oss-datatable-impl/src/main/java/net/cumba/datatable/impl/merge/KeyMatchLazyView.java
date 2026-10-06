package net.cumba.datatable.impl.merge;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import net.cumba.datatable.ExMsgs;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.view.IDataTableView;

/**
 * The lazy strategy of a key-table alignment over the shared matching: every base row is probed on
 * demand through a {@link KeyedMatcher} (no per-row map is materialised — O(partner rows) memory
 * whatever the base size), with a one-slot cache for the viewport access pattern that reads one
 * row's cells one after another. The successor of {@code HashLookupView}, which probed its own hash
 * table (MET #10: one matching, two strategies).
 */
public final class KeyMatchLazyView implements IDataTableView
{

    private static final long EMPTY_CACHE = -1L;

    private final KeyedMatcher matcher;

    private final long baseRows;

    private volatile long cache = EMPTY_CACHE;

    /**
     * @param aMatcher
     *            the matcher over the base (primary) and the key table (partner).
     * @param aBaseRows
     *            the base table's row count.
     */
    public KeyMatchLazyView(KeyedMatcher aMatcher, long aBaseRows)
    {
        matcher = aMatcher;
        baseRows = aBaseRows;
    }


    @Override
    public long getRowCount(IDataTable aTable)
    {
        return baseRows;
    }


    @Override
    public long getRealRow(IDataTable aTable, long aRow) throws IndexOutOfBoundsException
    {
        if (aRow < 0 || aRow >= baseRows)
        {
            throw new IndexOutOfBoundsException(ExMsgs.indexOutOfBounds("row", aRow, 0, baseRows));
        }
        int baseRow = (int) aRow;
        long c = cache;
        if (c != EMPTY_CACHE && ((int) (c >> 32)) == baseRow)
        {
            return (int) c;
        }
        int keyRow = matcher.firstMatchOf(baseRow);
        cache = ((long) baseRow << 32) | (keyRow & 0xFFFFFFFFL);
        return keyRow;
    }
}
