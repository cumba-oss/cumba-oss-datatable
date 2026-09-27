package net.cumba.datatable.provider.cdt;

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
 * PLAN-negative-zero-on-load, reader row "CDT" (NZL O1, owner 2026-09-27: <i>"we drop the sign from
 * any 0.0 so a -0.0 gets read as 0.0"</i>). A {@code Num} cell is decoded by
 * {@code CdtValues.parseValue} with {@code Double.parseDouble}, which turns {@code -0},
 * {@code -0.0} and {@code -0e0} into a {@code -0.0}; after loading, the cell must be {@code +0.0}.
 */
class NegativeZeroOnLoadCdtTest
{

    private static final long NEGATIVE_ZERO_BITS = 0x8000_0000_0000_0000L;

    private static final double CONTROL = -1.5;

    /** The negative-zero spellings, rows 0..2; row 3 is {@code 0.0}, row 4 {@code -1.5}. */
    private static final List<String> NEGATIVE_ZERO_TEXTS = List.of("-0", "-0.0", "-0e0");

    private static final String CONTENT = """
            dataset T
            col ID type=Num
            col N type=Num
            ---
            0 | -0
            1 | -0.0
            2 | -0e0
            3 | 0.0
            4 | -1.5
            ---
            """;

    @TempDir
    Path tmp;

    /**
     * (1) Non-vacuity: each spelling really decodes to a {@code -0.0} through the reader's decode.
     */
    @Test
    void theNegativeZeroTextsDecodeToANegativeZero()
    {
        for (String text : NEGATIVE_ZERO_TEXTS)
        {
            Double decoded = assertInstanceOf(Double.class, CdtValues.parseValue(text, CdtType.NUM),
                    text);
            assertEquals(NEGATIVE_ZERO_BITS, Double.doubleToRawLongBits(decoded), text);
        }
    }


    /** (2) The loaded cells are {@code +0.0}; the controls are untouched. */
    @Test
    void aNegativeZeroCellLoadsAsPositiveZero() throws IOException
    {
        Path file = tmp.resolve("negzero.cdt");
        Files.writeString(file, CONTENT, StandardCharsets.UTF_8);
        for (String text : NEGATIVE_ZERO_TEXTS)
        {
            assertEquals(1,
                    Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                            .filter(l -> l.endsWith("| " + text)).count(),
                    "the file holds " + text);
        }

        IDataTable table = new CdtTableProvider().provide(file.toUri(), CdtProviderSupplier.FI_CDT);
        assertEquals(5L, table.getRowCount());
        int col = table.getMetaData().getColumnIndex("N");
        assertSame(DataValueType.DOUBLE, table.getMetaData().getColumn(col).getType(), "N");
        assertCellBits(table, 3, col, 0L, "row 3 (+0.0 control)");
        assertCellBits(table, 4, col, Double.doubleToRawLongBits(CONTROL), "row 4 (-1.5 control)");
        for (int row = 0; row < NEGATIVE_ZERO_TEXTS.size(); row++)
        {
            assertCellBits(table, row, col, 0L,
                    "row " + row + " (\"" + NEGATIVE_ZERO_TEXTS.get(row) + "\")");
        }
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
