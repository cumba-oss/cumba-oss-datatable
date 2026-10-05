package net.cumba.datatable.merge;

import java.io.Serial;
import java.util.List;

/**
 * A keyed merge whose key has no usable component: every key column is absent from <b>both</b>
 * sides (JKM R7 drops a component absent on both; with none left the key would match every row
 * against every row).
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
        super("join on '" + aDataset + "' has no usable key: every key column ("
                + String.join(", ", aKeys) + ") is absent from both the primary dataset and '"
                + aDataset + "'; an empty key would match every row against every row");
    }
}
