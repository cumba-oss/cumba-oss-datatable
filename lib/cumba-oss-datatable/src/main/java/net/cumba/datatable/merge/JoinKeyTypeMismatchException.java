package net.cumba.datatable.merge;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import java.io.Serial;

/**
 * A keyed merge whose key column is numeric on one side and character on the other (the D4 join-key
 * type identity): a {@code LONG 5} and a {@code STRING "5"} are different keys, so the merge would
 * silently match nothing. A merge declaring {@code joinAsString} compares the keys as text instead
 * and is never refused here.
 */
public final class JoinKeyTypeMismatchException extends MergeException
{

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * @param aDataset
     *            the partner dataset.
     * @param aKeyColumn
     *            the primary-side key column.
     * @param aJoinedColumn
     *            the partner-side key column.
     * @param aPrimaryKind
     *            the primary column's kind ({@code Numeric} / {@code Character}).
     * @param aJoinedKind
     *            the partner column's kind.
     */
    public JoinKeyTypeMismatchException(String aDataset, String aKeyColumn, String aJoinedColumn,
            String aPrimaryKind, String aJoinedKind)
    {
        super("join on '" + aDataset + "': key " + aKeyColumn + " is " + aPrimaryKind
                + " in the primary dataset and "
                + (aKeyColumn.equals(aJoinedColumn) ? ""
                        : "its joined column " + aJoinedColumn + " is ")
                + aJoinedKind + " in " + aDataset
                + "; the keys cannot be equal, declare joinAsString to compare them as text");
    }
}
