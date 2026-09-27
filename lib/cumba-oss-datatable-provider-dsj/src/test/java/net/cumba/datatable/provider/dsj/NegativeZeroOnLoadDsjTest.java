package net.cumba.datatable.provider.dsj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * PLAN-negative-zero-on-load, reader row "Dataset-JSON" (NZL O1, owner 2026-09-27: <i>"we drop the
 * sign from any 0.0 so a -0.0 gets read as 0.0"</i>). Both decode paths of plan §1.2 can produce a
 * {@code -0.0}: a JSON <b>float token</b> ({@code -0.0}, {@code -0e0}; Jackson's
 * {@code getDoubleValue()}) in a {@code double} column, and a <b>decimal-as-string</b>
 * ({@code "-0.0"}) &mdash; through {@code DecimalMapper} on a {@code targetDataType=decimal}
 * column, and through the lenient {@code Double.parseDouble} path on a {@code decimal} column
 * without a {@code targetDataType}. After loading, each cell must be {@code +0.0}.
 *
 * <p>
 * An <b>int token</b> {@code -0} is a control: Jackson reads it as the integer {@code 0}, so it
 * already loads as {@code +0.0}.
 * </p>
 */
class NegativeZeroOnLoadDsjTest
{

    private static final long NEGATIVE_ZERO_BITS = 0x8000_0000_0000_0000L;

    private static final double CONTROL = -1.5;

    /**
     * Rows 0 and 1 are negative zeros in every column; row 2 {@code +0.0}; row 3 {@code -1.5}; row
     * 4 (DBL only) the int token {@code -0}, a control.
     */
    private static final String JSON = """
            {
              "datasetJSONCreationDateTime": "2026-09-27T10:00:00",
              "datasetJSONVersion": "1.1.0",
              "itemGroupOID": "IG.NZ",
              "name": "NZ",
              "label": "Negative zero",
              "records": 5,
              "columns": [
                {"itemOID": "IT.NZ.DBL", "name": "DBL", "label": "float token", "dataType": "double"},
                {"itemOID": "IT.NZ.DEC", "name": "DEC", "label": "mapped decimal string",
                 "dataType": "decimal", "targetDataType": "decimal", "length": 16},
                {"itemOID": "IT.NZ.LEN", "name": "LEN", "label": "lenient decimal string",
                 "dataType": "decimal", "length": 16}
              ],
              "rows": [
                [-0.0, "-0.0", "-0.0"],
                [-0e0, "-0", "-0"],
                [0.0, "0.0", "0.0"],
                [-1.5, "-1.5", "-1.5"],
                [-0, "0", "0"]
              ]
            }
            """;

    @TempDir
    Path tmp;

    /**
     * (1) Non-vacuity: the float tokens decode to {@code -0.0} through Jackson, the decimal strings
     * through {@code Double.valueOf} / {@code Double.parseDouble}; the int token {@code -0} does
     * not.
     */
    @Test
    void theInputsDecodeToANegativeZero() throws IOException
    {
        JsonFactory factory = new JsonFactory();
        for (String token : new String[]
        {
                "-0.0", "-0e0"
        })
        {
            try (JsonParser p = factory.createParser(token))
            {
                assertSame(JsonToken.VALUE_NUMBER_FLOAT, p.nextToken(), token);
                assertEquals(NEGATIVE_ZERO_BITS, Double.doubleToRawLongBits(p.getDoubleValue()),
                        token);
            }
        }
        try (JsonParser p = factory.createParser("-0"))
        {
            assertSame(JsonToken.VALUE_NUMBER_INT, p.nextToken(), "the int token -0");
            assertEquals(0L, Double.doubleToRawLongBits(p.getNumberValue().doubleValue()),
                    "the int token -0 is already +0.0 (the control)");
        }
        for (String text : new String[]
        {
                "-0.0", "-0"
        })
        {
            assertEquals(NEGATIVE_ZERO_BITS, Double.doubleToRawLongBits(Double.parseDouble(text)),
                    text);
        }
    }


    /**
     * (2) Float tokens {@code -0.0} / {@code -0e0} in a {@code double} column load as {@code +0.0}.
     */
    @Test
    void aNegativeZeroFloatTokenLoadsAsPositiveZero() throws IOException
    {
        IDataTable table = load();
        int col = assertDoubleColumn(table, "DBL");
        assertControls(table, col, "DBL");
        assertCellBits(table, 4, col, 0L, "DBL row 4 (int token -0, control)");
        assertCellBits(table, 0, col, 0L, "DBL row 0 (float token -0.0)");
        assertCellBits(table, 1, col, 0L, "DBL row 1 (float token -0e0)");
    }


    /**
     * (2) A decimal string {@code "-0.0"} on a {@code targetDataType=decimal} column loads as
     * {@code +0.0}.
     */
    @Test
    void aNegativeZeroMappedDecimalStringLoadsAsPositiveZero() throws IOException
    {
        IDataTable table = load();
        int col = assertDoubleColumn(table, "DEC");
        assertControls(table, col, "DEC");
        assertCellBits(table, 0, col, 0L, "DEC row 0 (\"-0.0\")");
        assertCellBits(table, 1, col, 0L, "DEC row 1 (\"-0\")");
    }


    /**
     * (2) A decimal string {@code "-0.0"} on the lenient (no {@code targetDataType}) path loads as
     * {@code +0.0}.
     */
    @Test
    void aNegativeZeroLenientDecimalStringLoadsAsPositiveZero() throws IOException
    {
        IDataTable table = load();
        int col = assertDoubleColumn(table, "LEN");
        assertControls(table, col, "LEN");
        assertCellBits(table, 0, col, 0L, "LEN row 0 (\"-0.0\")");
        assertCellBits(table, 1, col, 0L, "LEN row 1 (\"-0\")");
    }


    private IDataTable load() throws IOException
    {
        Path file = tmp.resolve("negzero.json");
        Files.writeString(file, JSON, StandardCharsets.UTF_8);
        IDataTable table = new DsjTableProvider().provide(file.toUri(),
                DsjProviderSupplier.FI_DSJ_JSON);
        assertEquals(5L, table.getRowCount());
        return table;
    }


    private static int assertDoubleColumn(IDataTable table, String name)
    {
        int col = table.getMetaData().getColumnIndex(name);
        assertSame(DataValueType.DOUBLE, table.getMetaData().getColumn(col).getType(), name);
        return col;
    }


    private static void assertControls(IDataTable table, int col, String name)
    {
        assertCellBits(table, 2, col, 0L, name + " row 2 (+0.0 control)");
        assertCellBits(table, 3, col, Double.doubleToRawLongBits(CONTROL),
                name + " row 3 (-1.5 control)");
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
