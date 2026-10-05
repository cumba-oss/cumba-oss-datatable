package net.cumba.datatable.merge;

import java.io.Serial;

/**
 * A side declared unique by the merge's {@link Cardinality} carries a key twice
 * ({@code PLAN-merged-evaluation-table} §3.4, MET #12): the message names the dataset, the key
 * values and the first two duplicate rows (1-based, as a user counts them).
 */
public final class CardinalityViolationException extends MergeException
{

    @Serial
    private static final long serialVersionUID = 1L;

    private final String dataset;

    private final long firstRow;

    private final long secondRow;

    /**
     * @param aCardinality
     *            the declared cardinality.
     * @param aDataset
     *            the dataset carrying the duplicate.
     * @param aKeyText
     *            the duplicated key, rendered as {@code (COL=value, ...)}.
     * @param aFirstRow
     *            the first duplicate row (0-based).
     * @param aSecondRow
     *            the second duplicate row (0-based).
     */
    public CardinalityViolationException(Cardinality aCardinality, String aDataset, String aKeyText,
            long aFirstRow, long aSecondRow)
    {
        super("Cardinality " + aCardinality.text() + ": " + aDataset + " has duplicate key "
                + aKeyText + " at rows " + (aFirstRow + 1) + " and " + (aSecondRow + 1));
        dataset = aDataset;
        firstRow = aFirstRow;
        secondRow = aSecondRow;
    }


    /**
     * The dataset carrying the duplicate.
     *
     * @return its name.
     */
    public String dataset()
    {
        return dataset;
    }


    /**
     * The first duplicate row, 0-based.
     *
     * @return the row.
     */
    public long firstRow()
    {
        return firstRow;
    }


    /**
     * The second duplicate row, 0-based.
     *
     * @return the row.
     */
    public long secondRow()
    {
        return secondRow;
    }
}
