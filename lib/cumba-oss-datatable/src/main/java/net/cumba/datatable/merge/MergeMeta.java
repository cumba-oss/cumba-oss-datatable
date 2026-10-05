package net.cumba.datatable.merge;

/**
 * The custom column-metadata keys a {@link MergedTable} publishes on every merged column
 * ({@code PLAN-merged-evaluation-table} §3.9, MET #7), readable through
 * {@link net.cumba.datatable.DataTableColumnMeta#getMetaData(String)}. A column of the primary
 * dataset carries none of them, which is what distinguishes an own column from a merged one.
 */
public final class MergeMeta
{

    /**
     * The name of the source dataset the column was merged from (the partner, parent, related or
     * supplemental dataset). The same key {@code MergeDataTable} has always written for every
     * non-primary part.
     */
    public static final String MERGED_FROM = "MERGED_FROM";

    /**
     * The column's unqualified name in its source dataset — the partner column for a keyed, child
     * or RELREC part, the {@code QNAM} for a supplemental qualifier.
     */
    public static final String MERGED_SOURCE_COLUMN = "MERGED_SOURCE_COLUMN";

    /** The {@link MergeKind#name()} of the part the column belongs to. */
    public static final String MERGE_KIND = "MERGE_KIND";

    /**
     * Present (a {@link String} message) on a column whose source could not serve every row
     * correctly — a supplemental qualifier with duplicate or conflicting rows, an {@code IDVAR} a
     * dataset lacks, a related domain lacking the column RELREC names (OD-7). The affected cells
     * read the not-supplied marker; the message names the offending source rows. A consumer that
     * errors on such a column (the engine: ERROR for every rule reading it, MET #15) and one that
     * warns (the data browser) read the same carrier.
     */
    public static final String MERGE_ERROR = "MERGE_ERROR";

    /**
     * The unqualified name of the synthetic boolean flag column a keyed, child or RELREC part
     * publishes under {@code <qualifier>._matched_}: {@code true} on a merged row that carries a
     * record of that part, {@code false} on a row whose part is not supplied.
     */
    public static final String MATCHED_FLAG = "_matched_";

    private MergeMeta()
    {
    }
}
