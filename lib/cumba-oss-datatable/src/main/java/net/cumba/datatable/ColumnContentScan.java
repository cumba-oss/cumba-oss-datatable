package net.cumba.datatable;

import net.cumba.datatable.values.MissingValue;
import org.jspecify.annotations.Nullable;

/**
 * The one scan behind {@link IDataTable#getColumnContent(int)}: fill from the table's cell tests
 * ({@link IDataTable#isEmptyOrMissing(long, int)} for blankness,
 * {@link IDataTable#isMissingOrNull(long, int)} to tell a missing value from {@code ""}),
 * uniformity by comparing every cell with row 0's under value equality, with
 * {@link IDataTable#hashCodeAt(long, int)} as the allocation-free pre-filter. Exits as soon as both
 * answers are known — a filled column with two different values is decided at the second distinct
 * value.
 *
 * <p>
 * Value equality is {@link java.util.Objects#equals} over the raw cell, with one normalisation: a
 * boxed NaN (the payload-coded missing of a floating-point buffer) is the {@link MissingValue} it
 * encodes ({@link MissingValue#forNaN(double)}) — for the fill (it is missing, never a value, even
 * where the table's own cell test does not decode it) and for the equality, so {@code .A} and
 * {@code ._} stay distinct and a NaN-coded {@code .} equals an explicit {@code MissingValue.MIS},
 * exactly as the typed read reports them.
 * </p>
 */
public final class ColumnContentScan
{

    private ColumnContentScan()
    {
    }


    /**
     * Scans one column.
     *
     * @param aTable
     *            the table
     * @param aColumn
     *            the 0-based column index
     * @return the column's content status; {@link ColumnContent#NO_ROWS} for a table without rows
     * @throws IndexOutOfBoundsException
     *             when the column index is outside the table's columns (from the cell reads)
     */
    public static ColumnContent scan(IDataTable aTable, int aColumn)
    {
        long rowCount = aTable.getRowCount();
        if (rowCount <= 0)
        {
            // The index is still validated: a caller naming a column the table lacks must hear
            // it on an empty table too, not read NO_ROWS for a column that does not exist.
            int cc = aTable.getColumnCount();
            if (aColumn < 0 || aColumn >= cc)
            {
                throw new IndexOutOfBoundsException(
                        ExMsgs.indexOutOfBounds("column", aColumn, 0, cc));
            }
            return ColumnContent.NO_ROWS;
        }
        Accumulator acc = new Accumulator();
        int hash0 = aTable.hashCodeAt(0, aColumn);
        for (long r = 0; r < rowCount; r++)
        {
            boolean blank = aTable.isEmptyOrMissing(r, aColumn);
            // The hash is the allocation-free pre-filter: different hashes are different values
            // — except against a missing row 0, whose NaN-coded or null twin hashes differently
            // and still equals it once normalised. The value is read only where it is needed.
            boolean readValue = !blank || acc.needsValue()
                    || (r > 0 && aTable.hashCodeAt(r, aColumn) == hash0);
            if (r > 0 && !readValue && !acc.firstIsMissing())
            {
                acc.addDifferentValue(blank, blank && aTable.isMissingOrNull(r, aColumn));
            }
            else
            {
                Object value = r == 0 || readValue || acc.firstIsMissing()
                        ? aTable.getValue(r, aColumn)
                        : null;
                acc.add(blank, blank && aTable.isMissingOrNull(r, aColumn), value);
            }
            if (acc.decided())
            {
                break;
            }
        }
        return acc.result();
    }


    /**
     * The raw cell value as the scan compares it: a boxed NaN (the payload-coded missing of a
     * floating-point buffer) and a {@code null} are the {@link MissingValue} they stand for
     * ({@link MissingValue#forNaN(double)} / {@link MissingValue#MIS}); every other value is
     * itself. Shared with every scan over cells the table does not own (a SUPP-qualifier view
     * column, a joined vector), so the three arms cannot drift.
     *
     * @param aValue
     *            the raw cell value
     * @return the comparable value, never {@code null}
     */
    public static Object normalise(@Nullable Object aValue)
    {
        if (aValue == null)
        {
            return MissingValue.MIS;
        }
        if (aValue instanceof Double d && d.isNaN())
        {
            return MissingValue.forNaN(d);
        }
        if (aValue instanceof Float f && f.isNaN())
        {
            return MissingValue.forNaN(f);
        }
        return aValue;
    }

    /**
     * The one fill / uniformity fold, fed one cell at a time by every scan arm — the table scan
     * above, and the arms over cells the table does not own. {@link #add} takes the cell's
     * blankness, its missingness and its raw value (normalised here); {@link #result()} is the
     * status, {@link #decided()} says when the rest of the rows cannot change it.
     */
    public static final class Accumulator
    {

        private boolean anyFilled;

        private boolean allMissing = true;

        private boolean uniform = true;

        private boolean first = true;

        private @Nullable Object firstValue;

        private boolean firstMissing;

        /**
         * Adds one cell.
         *
         * @param aBlank
         *            whether the cell is blank ({@code ""}, missing or null)
         * @param aMissing
         *            whether the cell is a missing value (or null)
         * @param aValue
         *            the raw cell value (a NaN or a null is a missing value)
         */
        public void add(boolean aBlank, boolean aMissing, @Nullable Object aValue)
        {
            Object value = normalise(aValue);
            boolean missing = aMissing || value instanceof MissingValue;
            if (!aBlank && !missing)
            {
                anyFilled = true;
                allMissing = false;
            }
            else if (!missing)
            {
                allMissing = false;
            }
            if (first)
            {
                first = false;
                firstValue = value;
                firstMissing = missing;
            }
            else if (uniform && !java.util.Objects.equals(value, firstValue))
            {
                uniform = false;
            }
        }


        /**
         * Adds a cell already known to differ from row 0's value (a hash pre-filter said so).
         *
         * @param aBlank
         *            whether the cell is blank
         * @param aMissing
         *            whether the cell is a missing value
         */
        public void addDifferentValue(boolean aBlank, boolean aMissing)
        {
            if (!aBlank)
            {
                anyFilled = true;
                allMissing = false;
            }
            else if (!aMissing)
            {
                allMissing = false;
            }
            first = false;
            uniform = false;
        }


        /** Whether the uniformity test still needs the cell's value (row 0 not yet seen). */
        boolean needsValue()
        {
            return first || uniform;
        }


        /** Whether row 0 holds a missing value (then a different hash may still be equal). */
        boolean firstIsMissing()
        {
            return !first && firstMissing;
        }


        /**
         * Whether both answers are final: FILLED cannot change back and MULTI_VALUES cannot.
         *
         * @return whether the remaining cells cannot change the result
         */
        public boolean decided()
        {
            return anyFilled && !uniform;
        }


        /**
         * The status of the cells added so far.
         *
         * @return the content status; {@link ColumnContent#NO_ROWS} when nothing was added
         */
        public ColumnContent result()
        {
            if (first)
            {
                return ColumnContent.NO_ROWS;
            }
            ColumnContent.Fill fill = anyFilled ? ColumnContent.Fill.FILLED
                    : allMissing ? ColumnContent.Fill.MISSING : ColumnContent.Fill.EMPTY;
            return new ColumnContent(fill, uniform ? ColumnContent.Uniformity.SINGLE_VALUE
                    : ColumnContent.Uniformity.MULTI_VALUES);
        }
    }
}
