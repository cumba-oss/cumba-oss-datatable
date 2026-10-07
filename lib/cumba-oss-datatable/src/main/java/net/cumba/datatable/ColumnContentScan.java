package net.cumba.datatable;

import java.util.Objects;
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
 * Value equality is {@link Objects#equals} over the raw cell, with one normalisation: a boxed NaN
 * (the payload-coded missing of a floating-point buffer) is the {@link MissingValue} it encodes
 * ({@link MissingValue#forNaN(double)}) — for the fill (it is missing, never a value, even where
 * the table's own cell test does not decode it) and for the equality, so {@code .A} and {@code ._}
 * stay distinct and a NaN-coded {@code .} equals an explicit {@code MissingValue.MIS}, exactly as
 * the typed read reports them.
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
        boolean anyFilled = false;
        boolean allMissing = true;
        boolean uniform = true;
        int hash0 = aTable.hashCodeAt(0, aColumn);
        Object value0 = normalise(aTable.getValue(0, aColumn));
        for (long r = 0; r < rowCount; r++)
        {
            if (aTable.isEmptyOrMissing(r, aColumn))
            {
                if (allMissing && !aTable.isMissingOrNull(r, aColumn))
                {
                    allMissing = false;
                }
            }
            else if (!(normalise(aTable.getValue(r, aColumn)) instanceof MissingValue))
            {
                // A raw NaN a table does not decode itself is still a missing value (the typed
                // read reports it as one), so only a genuine value makes the column FILLED.
                anyFilled = true;
                allMissing = false;
            }
            if (uniform && r > 0)
            {
                // The hash is the allocation-free pre-filter: different hashes are different
                // values — except against a missing row 0, whose NaN-coded twin hashes as a
                // Double and still equals it once normalised.
                if (aTable.hashCodeAt(r, aColumn) != hash0 && !(value0 instanceof MissingValue))
                {
                    uniform = false;
                }
                else if (!Objects.equals(normalise(aTable.getValue(r, aColumn)), value0))
                {
                    uniform = false;
                }
            }
            if (anyFilled && !uniform)
            {
                break; // both answers are final: FILLED cannot change, MULTI_VALUES cannot change
            }
        }
        ColumnContent.Fill fill = anyFilled ? ColumnContent.Fill.FILLED
                : allMissing ? ColumnContent.Fill.MISSING : ColumnContent.Fill.EMPTY;
        return new ColumnContent(fill, uniform ? ColumnContent.Uniformity.SINGLE_VALUE
                : ColumnContent.Uniformity.MULTI_VALUES);
    }


    private static @Nullable Object normalise(@Nullable Object aValue)
    {
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
}
