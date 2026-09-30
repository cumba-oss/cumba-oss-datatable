package net.cumba.datatable.provider.csv;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.univocity.parsers.csv.CsvParserSettings;
import java.io.StringReader;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/**
 * The quote-marking step of {@link CsvRecordReader} on its own: which cells of a record are a
 * quoted {@code "."} (owner ruling K7), given the same record parsed with {@code keepQuotes}.
 */
class CsvRecordReaderTest
{

    @Test
    void marksOnlyQuotedDots()
    {
        String[] row =
        {
                ".", ".", "x", null, "."
        };
        String[] kept =
        {
                "\".\"", ".", "\"x\"", null, "'.'"
        };

        assertArrayEquals(new boolean[]
        {
                true, false, false, false, false
        }, CsvRecordReader.quotedDots(row, kept, '"'));
        assertArrayEquals(new boolean[]
        {
                false, false, false, false, true
        }, CsvRecordReader.quotedDots(row, kept, '\''));
    }


    /**
     * A twin parse that did not reach a cell leaves it unquoted -- the ruling's default reading.
     */
    @Test
    void cellsTheKeptParseDoesNotReachAreUnquoted()
    {
        String[] row =
        {
                ".", "."
        };

        assertArrayEquals(new boolean[]
        {
                true, false
        }, CsvRecordReader.quotedDots(row, new String[]
        {
                "\".\""
        }, '"'));
        assertArrayEquals(new boolean[]
        {
                false, false
        }, CsvRecordReader.quotedDots(row, null, '"'));
        assertArrayEquals(new boolean[]
        {
                true
        }, CsvRecordReader.quotedDots(new String[]
        {
                "."
        }, new String[]
        {
                "\".\"", "\".\""
        }, '"'));
    }


    private static CsvRecordReader reader(String aCsv)
    {
        CsvParserSettings ps = new CsvParserSettings();
        ps.getFormat().setDelimiter(',');
        ps.setLineSeparatorDetectionEnabled(true);
        CsvRecordReader r = new CsvRecordReader(ps);
        r.beginParsing(new StringReader(aCsv));
        return r;
    }


    /**
     * The second read is confined to records that need it: a record with quotes but no dot, or a
     * dot but no quote, is never re-read.
     */
    @Test
    void onlyRecordsWithADotAndAQuoteAreReRead()
    {
        CsvRecordReader r = reader("\"x\",y\nx,.\n\"x\",.\n\"y\",\".\"\n");
        try
        {
            CsvRecord quotesNoDot = Objects.requireNonNull(r.next());
            CsvRecord dotNoQuote = Objects.requireNonNull(r.next());
            assertEquals(0, r.reReadCount(), "no record so far needed the twin");
            assertTrue(dotNoQuote.isUnquotedDot(1));
            assertFalse(quotesNoDot.isUnquotedDot(0));

            CsvRecord unquoted = Objects.requireNonNull(r.next());
            CsvRecord quoted = Objects.requireNonNull(r.next());
            assertEquals(2, r.reReadCount());
            assertTrue(unquoted.isUnquotedDot(1));
            assertFalse(quoted.isUnquotedDot(1));
            assertEquals(".", quoted.getValue(1));
            assertNull(r.next());
        }
        finally
        {
            r.stopParsing();
        }
    }
}
