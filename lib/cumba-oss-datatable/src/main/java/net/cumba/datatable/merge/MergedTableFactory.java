package net.cumba.datatable.merge;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

/**
 * The implementation seam behind {@link MergedTables}: provided by {@code cumba-datatable-impl}
 * through {@link java.util.ServiceLoader}, so a caller builds a merged table through this API
 * module alone and never names an implementation class (MQ13).
 */
public interface MergedTableFactory
{

    /**
     * Builds the merged table for the request.
     *
     * @param aRequest
     *            the request.
     * @return the merged table.
     * @throws MergeException
     *             when the merge cannot be built as specified.
     */
    MergedTable build(MergeRequest aRequest) throws MergeException;
}
