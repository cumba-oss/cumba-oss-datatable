package net.cumba.datatable.merge;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import java.util.List;

/**
 * One problem a merge found in its sources and reported through the {@link MergeMeta#MERGE_ERROR}
 * channel instead of aborting (OD-7): what it affects, what went wrong, and the source rows that
 * cause it. What each component holds depends on the kind (T2 review L4: the contract stated what
 * the SUPP part publishes and not the rest):
 *
 * @param kind
 *            the part's kind.
 * @param source
 *            the dataset the rows are counted in: the supplemental dataset for a {@code SUPP} issue
 *            — several stacked ones joined with {@code +} ({@code SUPPQS36+SUPPQS}, P-7) — the
 *            {@code RELREC} dataset for a {@code RELREC} issue, the parent domain for a
 *            {@code CHILD} issue.
 * @param column
 *            for a {@code SUPP} issue the merged column's name as published in the merged table;
 *            for a {@code RELREC} issue the related domain and for a {@code CHILD} issue the parent
 *            domain — the part's qualifier, every column of that part carrying the message.
 * @param message
 *            the message, the value published as {@code MERGE_ERROR}. It names at most 20 rows per
 *            category and then {@code ...}; the row list below is never truncated.
 * @param sourceRows
 *            the offending rows (0-based) in {@code source}: for a {@code SUPP} issue over several
 *            stacked datasets they are positions in the stacked table, in the order the datasets
 *            are joined in {@code source} (the message names each by its own dataset and 1-based
 *            row); for a {@code RELREC} issue the RELREC rows; for a {@code CHILD} issue the
 *            primary's rows carrying the pointer. Empty when the problem is not about particular
 *            rows.
 */
public record MergeIssue(MergeKind kind, String source, String column, String message,
        List<Long> sourceRows)
{

    /**
     * Canonical constructor: the row list is copied.
     */
    public MergeIssue
    {
        sourceRows = List.copyOf(sourceRows);
    }
}
