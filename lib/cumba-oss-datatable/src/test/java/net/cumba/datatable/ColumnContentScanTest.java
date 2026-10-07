package net.cumba.datatable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import net.cumba.datatable.ColumnContent.Fill;
import net.cumba.datatable.ColumnContent.Uniformity;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.MissingValue;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * {@code PLAN-present-empty-column-fold} row 10, the scan: every fill × uniformity cell of C1,
 * {@code ""} against a missing value, {@code .A} against {@code ._}, a NaN-coded missing against
 * its {@link MissingValue}, no rows (C5's {@link ColumnContent#NO_ROWS}), the early exit, and
 * {@link ColumnContent#ofConstant} against the scan over the same constant.
 */
class ColumnContentScanTest
{

    /** A table over raw cells; counts the cell reads for the early-exit pin. */
    private static final class CellsTable implements IDataTable
    {

        private final DataTableMeta meta;

        private final @Nullable Object[][] rows;

        final AtomicInteger reads = new AtomicInteger();

        CellsTable(DataValueType aType, @Nullable Object[]... aRows)
        {
            int cols = aRows.length == 0 ? 1 : aRows[0].length;
            DataTableColumnMeta[] cms = new DataTableColumnMeta[cols];
            for (int c = 0; c < cols; c++)
            {
                cms[c] = DataTableColumnMeta.builder().index(c).name("C" + c).type(aType).build();
            }
            meta = DataTableMeta.builder().name("T").setColumns(cms).rowCount(aRows.length)
                    .totalRowCount(aRows.length).build();
            rows = aRows;
        }


        @Override
        public DataTableMeta getMetaData()
        {
            return meta;
        }


        @Override
        public long getRowCount()
        {
            return rows.length;
        }


        @Override
        public @Nullable Object getValue(long aRow, int aColumn) throws IndexOutOfBoundsException
        {
            reads.incrementAndGet();
            if (aColumn < 0 || aColumn >= meta.getColumnCount())
            {
                throw new IndexOutOfBoundsException("column " + aColumn);
            }
            return rows[Math.toIntExact(aRow)][aColumn];
        }
    }

    private static ColumnContent scan(DataValueType aType, @Nullable Object... aCells)
    {
        @Nullable
        Object[][] rows = new @Nullable Object[aCells.length][];
        for (int r = 0; r < aCells.length; r++)
        {
            rows[r] = new @Nullable Object[]
            {
                    aCells[r]
            };
        }
        return new CellsTable(aType, rows).getColumnContent(0);
    }


    private static ColumnContent of(Fill aFill, Uniformity aUniformity)
    {
        return new ColumnContent(aFill, aUniformity);
    }


    @Test
    void filledMultiValues()
    {
        assertEquals(of(Fill.FILLED, Uniformity.MULTI_VALUES),
                scan(DataValueType.STRING, "a", "b"));
        assertEquals(of(Fill.FILLED, Uniformity.MULTI_VALUES), scan(DataValueType.STRING, "", "b"),
                "one value among blanks is FILLED");
        assertEquals(of(Fill.FILLED, Uniformity.MULTI_VALUES),
                scan(DataValueType.DOUBLE, MissingValue.MIS, 1.0d));
    }


    @Test
    void filledSingleValue()
    {
        assertEquals(of(Fill.FILLED, Uniformity.SINGLE_VALUE),
                scan(DataValueType.STRING, "a", "a"));
        assertEquals(of(Fill.FILLED, Uniformity.SINGLE_VALUE),
                scan(DataValueType.DOUBLE, 2.5d, 2.5d));
        assertEquals(of(Fill.FILLED, Uniformity.SINGLE_VALUE), scan(DataValueType.STRING, "a"),
                "one row is single-valued");
    }


    @Test
    void emptySingleValueIsAllEmptyString()
    {
        assertEquals(of(Fill.EMPTY, Uniformity.SINGLE_VALUE), scan(DataValueType.STRING, "", ""));
    }


    @Test
    void emptyMultiValuesIsEmptyStringMixedWithMissing()
    {
        assertEquals(of(Fill.EMPTY, Uniformity.MULTI_VALUES),
                scan(DataValueType.STRING, "", MissingValue.MIS),
                "\"\" is not a missing value (D34): EMPTY, and two distinct cells");
        assertEquals(of(Fill.EMPTY, Uniformity.MULTI_VALUES),
                scan(DataValueType.STRING, MissingValue.MIS, ""), "order does not matter");
    }


    @Test
    void missingSingleValue()
    {
        assertEquals(of(Fill.MISSING, Uniformity.SINGLE_VALUE),
                scan(DataValueType.DOUBLE, MissingValue.MIS, MissingValue.MIS));
        assertEquals(of(Fill.MISSING, Uniformity.SINGLE_VALUE),
                scan(DataValueType.DOUBLE, MissingValue.MIS_A, MissingValue.MIS_A));
    }


    @Test
    void missingMultiValuesKeepsTheMarkersApart()
    {
        assertEquals(of(Fill.MISSING, Uniformity.MULTI_VALUES),
                scan(DataValueType.DOUBLE, MissingValue.MIS_A, MissingValue.MIS__),
                ".A and ._ are distinct values (D11: each missing marker is its own group)");
        assertEquals(of(Fill.MISSING, Uniformity.MULTI_VALUES),
                scan(DataValueType.DOUBLE, MissingValue.MIS, MissingValue.MIS_A));
    }


    @Test
    void aNaNCodedMissingEqualsTheMissingValueItEncodes()
    {
        // A floating-point buffer hands out its missing as a NaN payload; the scan compares it as
        // the MissingValue it encodes, as the typed read reports it.
        double bare = Double.NaN;
        assertEquals(of(Fill.MISSING, Uniformity.SINGLE_VALUE),
                scan(DataValueType.DOUBLE, bare, MissingValue.MIS),
                "a bare NaN is MissingValue.MIS");
        assertEquals(of(Fill.MISSING, Uniformity.SINGLE_VALUE),
                scan(DataValueType.DOUBLE, bare, bare));
        assertEquals(of(Fill.MISSING, Uniformity.MULTI_VALUES),
                scan(DataValueType.DOUBLE, bare, MissingValue.MIS_A));
    }


    @Test
    void aNullCellIsMissing()
    {
        assertEquals(of(Fill.MISSING, Uniformity.SINGLE_VALUE),
                scan(DataValueType.STRING, (Object) null, null));
    }


    @Test
    void noRowsAnswersTheVacuousStatusAndStillValidatesTheColumn()
    {
        CellsTable empty = new CellsTable(DataValueType.STRING);
        assertEquals(ColumnContent.NO_ROWS, empty.getColumnContent(0));
        assertEquals(of(Fill.MISSING, Uniformity.SINGLE_VALUE), ColumnContent.NO_ROWS,
                "C5: vacuously all-missing and single-valued; callers test the row count");
        assertThrows(IndexOutOfBoundsException.class, () -> empty.getColumnContent(1));
        assertThrows(IndexOutOfBoundsException.class, () -> empty.getColumnContent(-1));
    }


    @Test
    void earlyExitOnceBothAnswersAreFinal()
    {
        CellsTable t = new CellsTable(DataValueType.STRING, row("a"), row("b"), row("c"), row("d"),
                row("e"), row("f"));
        assertEquals(of(Fill.FILLED, Uniformity.MULTI_VALUES), t.getColumnContent(0));
        // Row 0 and row 1 decide FILLED + MULTI_VALUES; the four rows after them are never read.
        // Each visited row costs at most three value reads (the two cell tests and the equality).
        assertTrue(t.reads.get() <= 2 * 4, "reads after the decision: " + t.reads.get());
    }


    @Test
    void getColumnContentsAnswersEveryColumn()
    {
        CellsTable t = new CellsTable(DataValueType.STRING, new @Nullable Object[]
        {
                "a", ""
        }, new @Nullable Object[]
        {
                "b", ""
        });
        ColumnContent[] all = t.getColumnContents();
        assertEquals(2, all.length);
        assertEquals(of(Fill.FILLED, Uniformity.MULTI_VALUES), all[0]);
        assertEquals(of(Fill.EMPTY, Uniformity.SINGLE_VALUE), all[1]);
    }


    @Test
    void isBlankIsEmptyOrMissing()
    {
        assertTrue(of(Fill.EMPTY, Uniformity.SINGLE_VALUE).isBlank());
        assertTrue(of(Fill.MISSING, Uniformity.MULTI_VALUES).isBlank());
        assertFalse(of(Fill.FILLED, Uniformity.SINGLE_VALUE).isBlank());
    }


    @Test
    void ofConstantAgreesWithTheScanOverTheSameConstant()
    {
        @Nullable
        Object[] constants =
        {
                "x", "", MissingValue.MIS, MissingValue.MIS_A, null, Double.NaN, 3.0d, 7L, true
        };
        for (Object c : constants)
        {
            DataValueType type = c instanceof String ? DataValueType.STRING : DataValueType.DOUBLE;
            assertEquals(scan(type, c, c, c), ColumnContent.ofConstant(c), "constant " + c);
        }
    }


    @Test
    void theDefaultContentGenerationIsConstant()
    {
        CellsTable t = new CellsTable(DataValueType.STRING, row("a"));
        assertEquals(0L, t.contentGeneration());
        assertEquals(t.contentGeneration(), t.contentGeneration());
    }


    private static @Nullable Object[] row(@Nullable Object aCell)
    {
        return new @Nullable Object[]
        {
                aCell
        };
    }
}
