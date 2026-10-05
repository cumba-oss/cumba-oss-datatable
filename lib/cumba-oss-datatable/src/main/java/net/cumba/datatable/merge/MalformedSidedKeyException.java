package net.cumba.datatable.merge;

import java.io.Serial;

/**
 * A key whose shape cannot be read: a sided element declaring only one side, an empty key, or a
 * supplemental key naming only one half of the {@code IDVAR} / {@code IDVARVAL} pair (P-15).
 */
public final class MalformedSidedKeyException extends MergeException
{

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * @param aDataset
     *            the dataset the key belongs to.
     * @param aDetail
     *            what is malformed.
     */
    public MalformedSidedKeyException(String aDataset, String aDetail)
    {
        super("merge of " + aDataset + ": malformed key — " + aDetail);
    }
}
