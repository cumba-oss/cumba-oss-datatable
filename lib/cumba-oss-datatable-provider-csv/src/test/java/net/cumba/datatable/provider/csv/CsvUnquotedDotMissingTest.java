package net.cumba.datatable.provider.csv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.MissingValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Owner ruling K7 (2026-09-30): <i>"missing only if it is not in quotation marks."</i> An
 * <b>unquoted</b> {@code .} in a CSV cell is the SAS numeric missing, {@link MissingValue#MIS}, in
 * every column type; a <b>quoted</b> {@code "."} is the one-character text {@code "."} and counts
 * as evidence of text for type inference. Blank cells are unchanged. (Every other quoted value is
 * text evidence too since K7c -- {@link CsvQuotedValueIsStringTest}.)
 */
class CsvUnquotedDotMissingTest
{

    @TempDir
    Path tempDir;

    private CsvTableProvider provider;

    @BeforeEach
    void setUp()
    {
        provider = new CsvTableProvider();
    }


    private URI write(String aContent) throws IOException
    {
        Path file = Files.createTempFile(tempDir, "k7", ".csv");
        Files.writeString(file, aContent, StandardCharsets.UTF_8);
        return file.toUri();
    }


    /**
     * The motivating shape: a SAS DATA-step export writes a numeric missing as an unquoted
     * {@code .}. The all-{@code .} column carries no type evidence and so stays STRING, as before,
     * but every cell reads as {@code MIS} instead of the text {@code "."}.
     */
    @Test
    void allUnquotedDotColumnBesideNumericTypesAsBeforeAndReadsMissing() throws Exception
    {
        URI uri = write("USUBJID,TR01EDT,TR02EDT\nS1,100,.\nS2,200,.\n");

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertSame(DataValueType.DOUBLE, table.getMetaData().getColumn(1).getType());
        assertSame(DataValueType.STRING, table.getMetaData().getColumn(2).getType(),
                "an unquoted '.' is still no type evidence");
        assertEquals(100.0, (double) table.getValue(0, 1), 0.0);
        assertSame(MissingValue.MIS, table.getValue(0, 2));
        assertSame(MissingValue.MIS, table.getValue(1, 2));
    }


    @Test
    void quotedDotIsTheText() throws Exception
    {
        URI uri = write("ID,FLAG\n1,\".\"\n2,\".\"\n");

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertSame(DataValueType.STRING, table.getMetaData().getColumn(1).getType());
        assertEquals(".", table.getValue(0, 1));
        assertEquals(".", table.getValue(1, 1));
    }


    /**
     * One character column holding every shape: unquoted {@code .} (bare and whitespace-padded),
     * quoted {@code "."}, a blank, a quoted blank and real text. Only the unquoted dots change.
     */
    @Test
    void mixedCharacterColumn() throws Exception
    {
        URI uri = write("ID,C\n1,.\n2,\".\"\n3,\n4,\"\"\n5,abc\n6,  .  \n7,\" . \"\n");

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertSame(DataValueType.STRING, table.getMetaData().getColumn(1).getType());
        assertSame(MissingValue.MIS, table.getValue(0, 1), "unquoted '.'");
        assertEquals(".", table.getValue(1, 1), "quoted '.'");
        assertEquals("", table.getValue(2, 1), "blank is unchanged");
        assertEquals("", table.getValue(3, 1), "quoted blank is unchanged");
        assertEquals("abc", table.getValue(4, 1));
        assertSame(MissingValue.MIS, table.getValue(5, 1),
                "surrounding whitespace of an unquoted value is trimmed by the parser");
        assertEquals(" .", table.getValue(6, 1),
                "a quoted value that is not exactly '.' is text (right-trimmed as every string)");
    }


    /**
     * Unchanged by K7 (it already read MIS on HEAD): stated so the ruling's DOUBLE arm is pinned.
     */
    @Test
    void numericColumnWithUnquotedDotReadsMissing() throws Exception
    {
        URI uri = write("VAL\n1.5\n.\n3\n");

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertSame(DataValueType.DOUBLE, table.getMetaData().getColumn(0).getType());
        assertEquals(1.5, (double) table.getValue(0, 0), 0.0);
        assertSame(MissingValue.MIS, table.getValue(1, 0));
        assertEquals(3.0, (double) table.getValue(2, 0), 0.0);
    }


    /**
     * A quoted {@code "."} is evidence of text: a numeric-looking column holding one is character,
     * so that cell keeps its text. Its unquoted dot is still MIS, its numbers their text.
     */
    @Test
    void quotedDotVetoesNumericTypeAndKeepsItsText() throws Exception
    {
        URI uri = write("VAL\n1\n\".\"\n.\n");

        DataTableMeta meta = provider.provideMetaData(uri, CsvProviderSupplier.FI_CSV);
        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertSame(DataValueType.STRING, meta.getColumn(0).getType());
        assertSame(DataValueType.STRING, table.getMetaData().getColumn(0).getType());
        assertEquals("1", table.getValue(0, 0));
        assertEquals(".", table.getValue(1, 0));
        assertSame(MissingValue.MIS, table.getValue(2, 0));
    }


    /**
     * A record whose OTHER cells are quoted (so the quote character is on the line) must still tell
     * each dot apart, including a quoted value holding the delimiter and a line break.
     */
    @Test
    void quotedNeighboursDoNotBlurTheDots() throws Exception
    {
        URI uri = write("A,B,C,D\n\"x,y\",.,\".\",\"l1\nl2\"\n\"q\"\"q\",\".\",.,z\n");

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertEquals(2, table.getRowCount());
        assertEquals("x,y", table.getValue(0, 0));
        assertSame(MissingValue.MIS, table.getValue(0, 1));
        assertEquals(".", table.getValue(0, 2));
        assertEquals("l1\nl2", table.getValue(0, 3));
        assertEquals("q\"q", table.getValue(1, 0));
        assertEquals(".", table.getValue(1, 1));
        assertSame(MissingValue.MIS, table.getValue(1, 2));
        assertEquals("z", table.getValue(1, 3));
    }


    /** Rows past the type-guessing sample go through the same distinction. */
    @Test
    void dotsBeyondTheGuessSample() throws Exception
    {
        URI uri = write("A,B\nt,u\n\"q\",.\n\"q\",\".\"\n");
        provider.setGuessingRowCount(1);

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertSame(DataValueType.STRING, table.getMetaData().getColumn(1).getType());
        assertSame(MissingValue.MIS, table.getValue(1, 1));
        assertEquals(".", table.getValue(2, 1));
    }


    /** CRLF records and a last record without a line break. */
    @Test
    void crlfAndUnterminatedLastRecord() throws Exception
    {
        URI uri = write("A,B\r\n\"a\",.\r\n\"b\",\".\"\r\n.,\".\"");

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertEquals(3, table.getRowCount());
        assertSame(MissingValue.MIS, table.getValue(0, 1));
        assertEquals(".", table.getValue(1, 1));
        assertSame(MissingValue.MIS, table.getValue(2, 0));
        assertEquals(".", table.getValue(2, 1));
    }


    /**
     * Records separated by a lone {@code \r}, with a {@code \n} inside an unquoted value: the
     * re-read must split the record with the separator the main parse DETECTED, or it ends the
     * record at the {@code \n} and never reaches the quoted dot.
     */
    @Test
    void detectedCarriageReturnSeparatorWithANewlineInsideAValue() throws Exception
    {
        URI uri = write("A,B\rx\ny,\".\"\rz,.\r");

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertEquals(2, table.getRowCount());
        assertEquals("x\ny", table.getValue(0, 0));
        assertEquals(".", table.getValue(0, 1));
        assertSame(MissingValue.MIS, table.getValue(1, 1));
    }


    /** The configured quote character is the one that decides, not a hard-coded {@code "}. */
    @Test
    void configuredQuoteCharacterDecides() throws Exception
    {
        URI uri = write("A,B\n'.',.\n'x',\".\"\n");

        provider.setQuote('\'');
        provider.setQuoteEscape('\'');
        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertEquals(".", table.getValue(0, 0), "quoted with the configured quote");
        assertSame(MissingValue.MIS, table.getValue(0, 1));
        assertEquals("\".\"", table.getValue(1, 1),
                "a double quote is plain text when the quote character is a single quote");
    }


    /**
     * A multi-character delimiter and an explicit, non-newline record separator: the re-read of a
     * record must split it exactly as the main parse did.
     */
    @Test
    void explicitMultiCharDelimiterAndRecordSeparator() throws Exception
    {
        URI uri = write("A||B||C;\"a\"||.||\".\";\"b\"||\".\"||.;");

        provider.setDelimiter("||");
        provider.setLineSeparator(";");
        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertEquals(2, table.getRowCount());
        assertEquals("a", table.getValue(0, 0));
        assertSame(MissingValue.MIS, table.getValue(0, 1));
        assertEquals(".", table.getValue(0, 2));
        assertEquals(".", table.getValue(1, 1));
        assertSame(MissingValue.MIS, table.getValue(1, 2));
    }


    /**
     * A record separated explicitly by {@code ';'} whose dots are split correctly: a re-read that
     * ignored the configured separator would see one field too many.
     */
    @Test
    void explicitRecordSeparatorDots() throws Exception
    {
        URI uri = write("A,B;\"a\",.;\"b\",\".\";");

        provider.setLineSeparator(";");
        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertEquals(2, table.getRowCount());
        assertSame(MissingValue.MIS, table.getValue(0, 1));
        assertEquals(".", table.getValue(1, 1));
    }


    /**
     * Records that straddle the parser's input buffer (1 MiB) and are read on its separate reader
     * thread: every dot is still attributed to its own record.
     */
    @Test
    void recordsAcrossInputBufferBoundaries() throws Exception
    {
        StringBuilder sb = new StringBuilder("A,B,C\n");
        int rows = 150_000;
        for (int i = 0; i < rows; i++)
        {
            if (i % 2 == 0)
            {
                sb.append("\"t").append(i).append("\",.,\".\"\n");
            }
            else
            {
                sb.append("\"t").append(i).append("\",\".\",.\n");
            }
        }
        URI uri = write(sb.toString());

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertEquals(rows, table.getRowCount());
        int mismatches = 0;
        for (int i = 0; i < rows; i++)
        {
            Object b = table.getValue(i, 1);
            Object c = table.getValue(i, 2);
            boolean even = i % 2 == 0;
            boolean ok = even ? b == MissingValue.MIS && ".".equals(c)
                    : ".".equals(b) && c == MissingValue.MIS;
            if (!ok || !("t" + i).equals(table.getValue(i, 0)))
            {
                mismatches++;
            }
        }
        assertEquals(0, mismatches);
    }
}
