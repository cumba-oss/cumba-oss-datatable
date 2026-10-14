package net.cumba.datatable.provider.csv;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.MissingValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A cell in a column the type-guessing sample typed {@code DOUBLE}: a BLANK cell is
 * {@link MissingValue#MIS}, a NON-BLANK cell that is not a number is {@link MissingValue#MIS_ERROR}
 * (owner ruling BNM-F7, 2026-10-12: <i>"same here, should already be aligned if not, then yes
 * MIS_ERROR."</i>) -- as SAS and Dataset-JSON store it, so a malformed value no longer looks like
 * an absent one. Text that parses to an infinity is {@code MIS_ERROR} too (owner, 2026-10-12:
 * <i>"Should CSV/XLSX text Infinity also become MIS_ERROR: yes."</i>), decided on the parsed value,
 * so an overflowing literal is covered. Text {@code "NaN"} keeps BNM E5 ({@code MIS}). Any quoted
 * value is text (K7c), so a quoted {@code ""} or {@code "."} past the sample is a non-blank cell
 * that does not parse.
 */
class CsvUnreadableNumericCellTest
{

    @TempDir
    Path tempDir;

    private CsvTableProvider provider;

    @BeforeEach
    void setUp()
    {
        provider = new CsvTableProvider();
    }


    private IDataTable load(String aContent) throws IOException
    {
        Path file = Files.createTempFile(tempDir, "bnmf7", ".csv");
        Files.writeString(file, aContent, StandardCharsets.UTF_8);
        URI uri = file.toUri();
        return provider.provide(uri, CsvProviderSupplier.FI_CSV);
    }


    /** Every shape of a cell past the sample, in a column the sample typed DOUBLE. */
    @Test
    void blankIsMisAndUnreadableIsMisErrorPastTheSample() throws Exception
    {
        provider.setGuessingRowCount(2);
        IDataTable table = load("S,N\n" //
                + "a,1\n" //
                + "b,2\n" //
                + "c,\n" // 2: blank
                + "d,abc\n" // 3: text
                + "e,\".\"\n" // 4: a quoted sentinel is text
                + "f,\"\"\n" // 5: a quoted empty value is text
                + "g,NaN\n" // 6: BNM E5
                + "h,Infinity\n" // 7
                + "i,-Infinity\n" // 8
                + "j,1e400\n" // 9: overflows to Infinity
                + "k,-1e400\n" // 10
                + "\"say \"\"hi\"\"\",\n" // 11: escaped quotes elsewhere, N unquoted empty
                + "m\n" // 12: short row
                + "n,   \n" // 13: unquoted whitespace
                + "o,.\n" // 14: unquoted sentinel (K7)
                + "p,\"7\"\n" // 15: a quoted number is parsed, as it always was
                + "q,4.5\n"); // 16

        assertSame(DataValueType.DOUBLE, table.getMetaData().getColumn(1).getType());
        assertAll(() -> assertSame(MissingValue.MIS, table.getValue(2, 1), "blank"),
                () -> assertSame(MissingValue.MIS_ERROR, table.getValue(3, 1), "abc"),
                () -> assertSame(MissingValue.MIS_ERROR, table.getValue(4, 1), "quoted '.'"),
                () -> assertSame(MissingValue.MIS_ERROR, table.getValue(5, 1), "quoted \"\""),
                () -> assertSame(MissingValue.MIS, table.getValue(6, 1), "NaN keeps BNM E5"),
                () -> assertSame(MissingValue.MIS_ERROR, table.getValue(7, 1), "Infinity"),
                () -> assertSame(MissingValue.MIS_ERROR, table.getValue(8, 1), "-Infinity"),
                () -> assertSame(MissingValue.MIS_ERROR, table.getValue(9, 1), "1e400"),
                () -> assertSame(MissingValue.MIS_ERROR, table.getValue(10, 1), "-1e400"),
                () -> assertSame(MissingValue.MIS, table.getValue(11, 1),
                        "an unquoted empty cell stays blank beside escaped quotes"),
                () -> assertEquals("say \"hi\"", table.getValue(11, 0)),
                () -> assertSame(MissingValue.MIS, table.getValue(12, 1), "short row"),
                () -> assertSame(MissingValue.MIS, table.getValue(13, 1), "whitespace only"),
                () -> assertSame(MissingValue.MIS, table.getValue(14, 1), "unquoted '.'"),
                () -> assertEquals(7.0, (double) table.getValue(15, 1), 0.0),
                () -> assertEquals(4.5, (double) table.getValue(16, 1), 0.0));
    }


    /**
     * A character column is untouched: a quoted empty value there is the empty text, and text that
     * would not parse is just text.
     */
    @Test
    void aCharacterColumnKeepsItsText() throws Exception
    {
        provider.setGuessingRowCount(2);
        IDataTable table = load("N,S\n1,a\n2,b\n3,\"\"\n4,Infinity\n");

        assertSame(DataValueType.STRING, table.getMetaData().getColumn(1).getType());
        assertEquals("", table.getValue(2, 1));
        assertEquals("Infinity", table.getValue(3, 1));
    }
}
