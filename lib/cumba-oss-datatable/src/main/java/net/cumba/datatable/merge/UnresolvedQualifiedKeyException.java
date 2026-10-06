package net.cumba.datatable.merge;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import java.io.Serial;

/**
 * A key component that names a column of an earlier merged part ({@code DM.RPATHCD}) which the
 * datatable layer cannot resolve: it reads keys from the primary and the partner only, and leaves
 * the composition of chained keys to the caller ({@code PLAN-merged-evaluation-table} §4).
 */
public final class UnresolvedQualifiedKeyException extends MergeException
{

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * @param aDataset
     *            the partner dataset.
     * @param aQualifiedKey
     *            the qualified key component.
     * @param aReason
     *            why it cannot be resolved.
     */
    public UnresolvedQualifiedKeyException(String aDataset, String aQualifiedKey, String aReason)
    {
        super("join on '" + aDataset + "': qualified key " + aQualifiedKey
                + " cannot be resolved — " + aReason);
    }
}
