package net.cumba.datatable.provider.csv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * PLAN-negative-zero-on-load, reader row "CSV" (NZL O1, owner 2026-09-27: <i>"we drop the sign from
 * any 0.0 so a -0.0 gets read as 0.0"</i>). The CSV reader parses a numeric cell with
 * {@code Double.parseDouble}, which turns {@code "-0"} and {@code "-0.0"} into a {@code -0.0};
 * after loading, the cell must be {@code +0.0}.
 */
class NegativeZeroOnLoadCsvTest
{

    private static final long NEGATIVE_ZERO_BITS = 0x8000_0000_0000_0000L;

    private static final double CONTROL = -1.5;

    /** Row 0 {@code -0}, row 1 {@code -0.0}, row 2 the {@code +0.0} control, row 3 {@code -1.5}. */
    private static final List<String> CELLS = List.of("-0", "-0.0", "0.0", "-1.5");

    @TempDir
    Path tmp;

    /** (1) Non-vacuity: each negative-zero text really parses to a {@code -0.0}. */
    @Test
    void theNegativeZeroTextsParseToANegativeZero()
    {
        assertEquals(NEGATIVE_ZERO_BITS,
                Double.doubleToRawLongBits(Double.parseDouble(CELLS.get(0))), "-0");
        assertEquals(NEGATIVE_ZERO_BITS,
                Double.doubleToRawLongBits(Double.parseDouble(CELLS.get(1))), "-0.0");
        assertEquals(NEGATIVE_ZERO_BITS, Double.doubleToRawLongBits(new CsvRecord(new String[]
        {
                CELLS.get(0)
        }).getDoubleValue(0)), "-0 through the reader's own CsvRecord decode");
    }


    /** (2) The loaded cells are {@code +0.0}; the controls are untouched. */
    @Test
    void aNegativeZeroCellLoadsAsPositiveZero() throws IOException
    {
        Path file = tmp.resolve("negzero.csv");
        StringBuilder csv = new StringBuilder("ID,VAL\n");
        for (int i = 0; i < CELLS.size(); i++)
        {
            csv.append(i).append(',').append(CELLS.get(i)).append('\n');
        }
        Files.writeString(file, csv, StandardCharsets.UTF_8);
        assertEquals("ID,VAL\n0,-0\n1,-0.0\n2,0.0\n3,-1.5\n",
                Files.readString(file, StandardCharsets.UTF_8), "the file holds the texts");

        IDataTable table = new CsvTableProvider().provide(file.toUri(), CsvProviderSupplier.FI_CSV);
        assertEquals(4L, table.getRowCount());
        int col = table.getMetaData().getColumnIndex("VAL");
        assertSame(DataValueType.DOUBLE, table.getMetaData().getColumn(col).getType(),
                "VAL is typed DOUBLE, so its cells go through the numeric decode");
        assertCellBits(table, 2, col, 0L, "row 2 (+0.0 control)");
        assertCellBits(table, 3, col, Double.doubleToRawLongBits(CONTROL), "row 3 (-1.5 control)");
        assertCellBits(table, 0, col, 0L, "row 0 (\"-0\")");
        assertCellBits(table, 1, col, 0L, "row 1 (\"-0.0\")");
    }


    private static void assertCellBits(IDataTable table, long row, int col, long expectedBits,
            String where)
    {
        Number raw = assertInstanceOf(Number.class, table.getValue(row, col), where);
        assertEquals(expectedBits, Double.doubleToRawLongBits(raw.doubleValue()),
                where + ": raw bits of getValue");
        assertEquals(expectedBits,
                Double.doubleToRawLongBits(table.getDataValue(row, col).getValueAsDouble()),
                where + ": raw bits of getDataValue().getValueAsDouble()");
    }
}
