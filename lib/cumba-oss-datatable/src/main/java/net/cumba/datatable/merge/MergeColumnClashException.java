package net.cumba.datatable.merge;

import java.io.Serial;

/**
 * Two columns of the merged table would carry one name — a partner column named like a primary
 * column under bare naming, or one qualifier served by two sources under qualified naming
 * ({@code PLAN-merged-evaluation-table} P-12, P-14). The merge is refused rather than built with an
 * ambiguous column.
 */
public final class MergeColumnClashException extends MergeException
{

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * @param aColumn
     *            the clashing column name.
     * @param aSource
     *            the source that would add it a second time.
     */
    public MergeColumnClashException(String aColumn, String aSource)
    {
        super("merged column " + aColumn + " from " + aSource
                + " is already defined by an earlier part of the merge");
    }
}
