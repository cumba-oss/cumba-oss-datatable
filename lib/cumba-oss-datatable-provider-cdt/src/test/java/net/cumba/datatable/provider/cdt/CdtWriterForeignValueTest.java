package net.cumba.datatable.provider.cdt;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.IDataValue;
import net.cumba.datatable.values.MissingValue;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link CdtWriter}'s value-side fallbacks — the arms it keeps for an {@link IDataTable} whose
 * cells are NOT what this stack's own column storage produces.
 *
 * <p>
 * Every writer test that came before this one builds its fixture out of
 * {@code CachedDataTableColumn}, whose buffers normalise every cell to a primitive {@code double},
 * a {@code String} or a {@code MissingValue} before the writer ever sees it. That made
 * {@code extractRaw}'s {@link IDataValue} arm and the {@code toString()} tails of
 * {@code renderDate} / {@code renderTime} / {@code renderDateTime} / {@code renderNumber}
 * structurally unreachable from those fixtures — eight mutants with NO coverage at all.
 * {@link IDataTable} is a public interface, though, and {@code writeRows} calls exactly two of its
 * methods ({@code getRowCount()} and {@code getValue(long,int)}), so a foreign implementation
 * reaches all of them without any new module dependency: what the writer does with a cell it did
 * not create is part of its contract, not an internal detail.
 * </p>
 */
class CdtWriterForeignValueTest
{

    /**
     * A one-row, one-column table whose single cell is whatever object the caller hands in — the
     * point being that it need not be a value this module's own storage could ever hold.
     */
    private static IDataTable foreignTable(DataValueType aType, String aFormat, Object aValue)
    {
        DataTableColumnMeta.DataTableColumnMetaBuilder cb = DataTableColumnMeta.builder().index(0)
                .name("V").type(aType);
        if (aFormat != null)
        {
            cb.displayFormat(aFormat);
        }
        DataTableMeta meta = DataTableMeta.builder().name("T").rowCount(1).totalRowCount(1)
                .setColumns(cb.build()).build();

        IDataTable table = mock(IDataTable.class);
        when(table.getMetaData()).thenReturn(meta);
        when(table.getRowCount()).thenReturn(1L);
        when(table.getValue(0L, 0)).thenReturn(aValue);
        return table;
    }


    private static String body(IDataTable aTable)
    {
        String text = CdtWriter.toString(aTable);
        // the data section is what follows the first fence line
        int fence = text.indexOf(CdtWriter.FENCE);
        return text.substring(fence + CdtWriter.FENCE.length());
    }

    // ---- extractRaw: an IDataValue cell is unwrapped, not stringified ---------------------------


    @Test
    void dataValueCellIsUnwrappedToItsRawValue()
    {
        IDataValue dv = mock(IDataValue.class);
        when(dv.getValue()).thenReturn("SCREENED");
        when(dv.isMissingOrInvalid()).thenReturn(false);

        assertTrue(body(foreignTable(DataValueType.STRING, null, dv)).contains("SCREENED"),
                "an IDataValue cell must be written as its wrapped value, not as its toString()");
    }


    @Test
    void missingDataValueCellIsWrittenAsMissing()
    {
        IDataValue dv = mock(IDataValue.class);
        when(dv.getValue()).thenReturn(MissingValue.MIS);
        when(dv.isMissingOrInvalid()).thenReturn(true);

        // single-column all-missing row -> the "." sentinel, never the value's own text
        String body = body(foreignTable(DataValueType.STRING, null, dv));
        assertTrue(body.contains("."), "a missing IDataValue must render as an empty field");
        assertTrue(!body.contains("MIS"),
                "the MissingValue's display text must not leak into data");
    }

    // ---- the render* toString() tails ----------------------------------------------------------


    @Test
    void dateColumnHoldingANonNumericCellIsWrittenVerbatim()
    {
        assertTrue(
                body(foreignTable(DataValueType.DOUBLE, "DATE9.", "2024-06-01"))
                        .contains("2024-06-01"),
                "a DATE column whose cell is not a Number must fall back to its own text");
    }


    @Test
    void timeColumnHoldingANonNumericCellIsWrittenVerbatim()
    {
        assertTrue(
                body(foreignTable(DataValueType.DOUBLE, "TIME8.", "12:30:00")).contains("12:30:00"),
                "a TIME column whose cell is not a Number must fall back to its own text");
    }


    @Test
    void dateTimeColumnHoldingANonNumericCellIsWrittenVerbatim()
    {
        assertTrue(
                body(foreignTable(DataValueType.DOUBLE, "DATETIME20.", "2024-06-01 12:30:00"))
                        .contains("2024-06-01 12:30:00"),
                "a DATETIME column whose cell is not a Number must fall back to its own text");
    }


    @Test
    void numericColumnHoldingANonDoubleNumberUsesThatNumbersOwnText()
    {
        // BigDecimal is a Number but not a Double: the Double shortcut must not claim it, and its
        // own toString() must survive - 0.10 is exactly the value a BigDecimal preserves and a
        // double would not.
        assertTrue(body(foreignTable(DataValueType.DOUBLE, null, new BigDecimal("0.10"))).contains(
                "0.10"), "a non-Double Number must be written through its own toString()");
        assertTrue(
                body(foreignTable(DataValueType.DOUBLE, null, Integer.valueOf(42))).contains("42"),
                "an Integer cell must be written as 42");
    }


    @Test
    void numericColumnHoldingANonNumericCellIsWrittenVerbatim()
    {
        assertTrue(body(foreignTable(DataValueType.DOUBLE, null, "n/a")).contains("n/a"),
                "a NUM column whose cell is not a Number at all must fall back to its own text");
    }
}
