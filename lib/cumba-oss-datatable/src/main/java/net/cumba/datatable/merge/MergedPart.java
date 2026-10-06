package net.cumba.datatable.merge;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

/**
 * One part of a {@link MergedTable}: a contiguous column range built from one source dataset
 * through one row map. Part 0 is always the primary.
 *
 * @param kind
 *            the part's kind.
 * @param qualifier
 *            the name the part's columns are qualified with under qualified naming (the entry
 *            {@code Name}, the parent domain, the related {@code RDOMAIN}, the {@code SUPP--}
 *            dataset); the primary's is its dataset name.
 * @param source
 *            the source dataset's name as its metadata spells it.
 * @param firstColumn
 *            the index of the part's first column in the merged table.
 * @param columnCount
 *            the number of columns the part contributes (including its {@code _matched_} flag when
 *            it has one).
 * @param matchedColumn
 *            the index of the part's {@code _matched_} flag column in the merged table, or
 *            {@code -1} when the part has none.
 */
public record MergedPart(MergeKind kind, String qualifier, String source, int firstColumn,
        int columnCount, int matchedColumn)
{

    /**
     * Whether the given merged column belongs to this part.
     *
     * @param aColumn
     *            a merged column index.
     * @return whether it lies in this part's range.
     */
    public boolean contains(int aColumn)
    {
        return aColumn >= firstColumn && aColumn < firstColumn + columnCount;
    }
}
