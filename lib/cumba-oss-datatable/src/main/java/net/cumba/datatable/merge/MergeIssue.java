package net.cumba.datatable.merge;

import java.util.List;

/**
 * One problem a merge found in its sources and reported through the {@link MergeMeta#MERGE_ERROR}
 * channel instead of aborting (OD-7): the column it affects, what went wrong, and the source rows
 * (0-based, in the source dataset) that cause it.
 *
 * @param kind
 *            the part's kind.
 * @param source
 *            the source dataset's name.
 * @param column
 *            the merged column's name as published in the merged table.
 * @param message
 *            the message, the value published as {@code MERGE_ERROR}.
 * @param sourceRows
 *            the offending source rows (0-based), possibly truncated for a long list; empty when
 *            the problem is not about particular rows.
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
