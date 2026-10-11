package net.cumba.datatable.merge;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import java.io.Serial;
import java.util.List;

/**
 * A keyed merge whose key has no usable component: no key column is present on <b>both</b> sides
 * (owner ruling {@code REG-Q3}: a component present on one side only leaves the key, as one absent
 * from both does; with none left the key would match every row against every row).
 */
public final class DegenerateJoinKeyException extends MergeException
{

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * @param aDataset
     *            the partner dataset.
     * @param aKeys
     *            the authored key column names.
     */
    public DegenerateJoinKeyException(String aDataset, List<String> aKeys)
    {
        super("join on '" + aDataset + "' has no usable key: no key column of ("
                + String.join(", ", aKeys) + ") is present on both the primary dataset and '"
                + aDataset + "'; an empty key would match every row against every row");
    }
}
