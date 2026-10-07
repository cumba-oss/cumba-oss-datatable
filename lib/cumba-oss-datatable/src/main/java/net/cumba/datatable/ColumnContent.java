package net.cumba.datatable;

import net.cumba.datatable.values.MissingValue;
import org.jspecify.annotations.Nullable;

/**
 * The content status of one column of a table: what its cells hold, read over every row
 * ({@code PLAN-present-empty-column-fold} C1). Two independent properties:
 * <ul>
 * <li><b>fill</b> — {@link Fill#FILLED} when at least one cell holds a non-blank value,
 * {@link Fill#MISSING} when every cell is a missing value, {@link Fill#EMPTY} when every cell is
 * blank ({@code ""} or missing) but not every cell is missing;</li>
 * <li><b>uniformity</b> — {@link Uniformity#SINGLE_VALUE} when every cell equals row 0's under
 * value equality ({@link MissingValue} is an enum, so {@code .A} is not {@code ._}, and {@code ""}
 * is not a missing value), {@link Uniformity#MULTI_VALUES} otherwise. For a {@code SINGLE_VALUE}
 * column, row 0's value is the value of every row.</li>
 * </ul>
 * So {@code EMPTY + SINGLE_VALUE} is an all-{@code ""} column and {@code EMPTY + MULTI_VALUES} is
 * {@code ""} mixed with missing values. A table with no rows answers {@link #NO_ROWS}
 * ({@code MISSING + SINGLE_VALUE}): the properties hold vacuously, and a caller that folds or
 * collapses on the status must test the row count itself (C5 — a 0-row table never folds).
 *
 * <p>
 * Computed by {@link ColumnContentScan} ({@link IDataTable#getColumnContent(int)}), cached per
 * table instance where the table can see its own edits (the implementation module's
 * {@code AbstractDataTable}), and answered for free by storage that knows it (a constant-value
 * buffer).
 * </p>
 *
 * @param fill
 *            the fill status
 * @param uniformity
 *            the uniformity status
 */
public record ColumnContent(Fill fill, Uniformity uniformity)
{

    /** How much of the column holds a value. */
    public enum Fill
    {
        /** At least one cell holds a non-blank value. */
        FILLED,
        /** Every cell is {@code ""} or a missing value, and at least one is {@code ""}. */
        EMPTY,
        /** Every cell is a missing value. */
        MISSING
    }


    /** Whether every cell holds the same value. */
    public enum Uniformity
    {
        /** Every cell equals row 0's value. */
        SINGLE_VALUE,
        /** At least two cells differ. */
        MULTI_VALUES
    }

    /** The status of a column with no rows: vacuously all-missing and single-valued (C5). */
    public static final ColumnContent NO_ROWS = new ColumnContent(Fill.MISSING,
            Uniformity.SINGLE_VALUE);

    /**
     * Creates the status.
     *
     * @param fill
     *            the fill status
     * @param uniformity
     *            the uniformity status
     * @throws NullPointerException
     *             when either is {@code null}
     */
    public ColumnContent
    {
        java.util.Objects.requireNonNull(fill, "fill");
        java.util.Objects.requireNonNull(uniformity, "uniformity");
    }


    /**
     * Whether no cell holds a value — the fill is {@link Fill#EMPTY} or {@link Fill#MISSING}: the
     * notion {@code empty(X)} answers on every row.
     *
     * @return {@code true} unless the fill is {@link Fill#FILLED}
     */
    public boolean isBlank()
    {
        return fill != Fill.FILLED;
    }


    /**
     * The status of a column whose every cell holds one constant raw value, over at least one row:
     * the value's own blankness class (a {@code null}, a {@link MissingValue} or a NaN
     * floating-point payload is missing, a zero-length {@link String} is empty, anything else is
     * filled), and {@link Uniformity#SINGLE_VALUE}. Mirrors the cell tests of
     * {@link IDataTable#isEmptyOrMissing(long, int)} and
     * {@link IDataTable#isMissingOrNull(long, int)} as a column-backed table answers them, so a
     * storage that answers through this is interchangeable with {@link ColumnContentScan}.
     *
     * @param aValue
     *            the constant raw value
     * @return the status
     */
    public static ColumnContent ofConstant(@Nullable Object aValue)
    {
        return new ColumnContent(fillOfConstant(aValue), Uniformity.SINGLE_VALUE);
    }


    private static Fill fillOfConstant(@Nullable Object aValue)
    {
        if (aValue == null || aValue instanceof MissingValue)
        {
            return Fill.MISSING;
        }
        if (aValue instanceof Double d && d.isNaN())
        {
            return Fill.MISSING;
        }
        if (aValue instanceof Float f && f.isNaN())
        {
            return Fill.MISSING;
        }
        if (aValue instanceof String s && s.isEmpty())
        {
            return Fill.EMPTY;
        }
        return Fill.FILLED;
    }
}
