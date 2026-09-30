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
        return reader(ps, aCsv);
    }


    private static CsvRecordReader reader(CsvParserSettings aSettings, String aCsv)
    {
        CsvRecordReader r = new CsvRecordReader(aSettings);
        r.beginParsing(new StringReader(aCsv));
        return r;
    }


    /**
     * The second read is confined to records that need it: only a record holding a {@code "."} cell
     * whose raw text has the quote immediately followed by a dot is re-read -- not one with quotes
     * elsewhere, nor one with an unquoted dot only.
     */
    @Test
    void onlyRecordsWithAQuoteBeforeADotAreReRead()
    {
        CsvRecordReader r = reader(
                "\"x\",y\nx,.\n\"x\",.\n\"y.\",.\n\"y\",\".\"\n\".5\",.\n\".\",\".\"");
        try
        {
            CsvRecord quotesNoDot = Objects.requireNonNull(r.next());
            CsvRecord dotNoQuote = Objects.requireNonNull(r.next());
            CsvRecord quotesElsewhere = Objects.requireNonNull(r.next());
            CsvRecord dotBeforeQuote = Objects.requireNonNull(r.next());
            assertEquals(0, r.reReadCount(), "no record so far needed the twin");
            assertFalse(quotesNoDot.isUnquotedDot(0));
            assertTrue(dotNoQuote.isUnquotedDot(1));
            assertTrue(quotesElsewhere.isUnquotedDot(1));
            assertTrue(dotBeforeQuote.isUnquotedDot(1));

            CsvRecord quoted = Objects.requireNonNull(r.next());
            assertEquals(1, r.reReadCount());
            assertFalse(quoted.isUnquotedDot(1));
            assertEquals(".", quoted.getValue(1));

            CsvRecord pairNotADot = Objects.requireNonNull(r.next());
            assertEquals(2, r.reReadCount(), "the gate is a pre-check; the twin decides");
            assertTrue(pairNotADot.isUnquotedDot(1));

            CsvRecord bothQuotedNoNewline = Objects.requireNonNull(r.next());
            assertFalse(bothQuotedNoNewline.isUnquotedDot(0));
            assertFalse(bothQuotedNoNewline.isUnquotedDot(1));
            assertNull(r.next());
        }
        finally
        {
            r.stopParsing();
        }
    }


    /** An unterminated {@code ".} at the end of the input is still a quoted cell. */
    @Test
    void unterminatedQuotedDotAtEofIsQuoted()
    {
        CsvRecordReader r = reader("x,\".");
        try
        {
            CsvRecord rec = Objects.requireNonNull(r.next());
            assertEquals(".", rec.getValue(1));
            assertFalse(rec.isUnquotedDot(1));
            assertEquals(1, r.reReadCount());
        }
        finally
        {
            r.stopParsing();
        }
    }


    /** The gate looks for the CONFIGURED quote character. */
    @Test
    void gateHonoursAConfiguredQuote()
    {
        CsvParserSettings ps = new CsvParserSettings();
        ps.getFormat().setDelimiter(';');
        ps.getFormat().setQuote('\'');
        ps.getFormat().setQuoteEscape('\'');
        ps.setLineSeparatorDetectionEnabled(true);
        CsvRecordReader r = reader(ps, "'a';\".\";'.'\n");
        try
        {
            CsvRecord rec = Objects.requireNonNull(r.next());
            assertEquals("\".\"", rec.getValue(1), "a double quote is plain text here");
            assertEquals(".", rec.getValue(2));
            assertFalse(rec.isUnquotedDot(2));
            assertEquals(1, r.reReadCount());
        }
        finally
        {
            r.stopParsing();
        }
    }


    /**
     * With leading whitespace inside quotes ignored, {@code " ."} reads as a quoted {@code "."}
     * although no quote touches the dot -- the gate must then fall back to any quote.
     */
    @Test
    void ignoredLeadingWhitespaceInQuotesFallsBackToAnyQuote()
    {
        CsvParserSettings ps = new CsvParserSettings();
        ps.getFormat().setDelimiter(',');
        ps.setLineSeparatorDetectionEnabled(true);
        ps.setIgnoreLeadingWhitespacesInQuotes(true);
        CsvRecordReader r = reader(ps, "x,\" .\"\n");
        try
        {
            CsvRecord rec = Objects.requireNonNull(r.next());
            assertEquals(".", rec.getValue(1));
            assertFalse(rec.isUnquotedDot(1));
        }
        finally
        {
            r.stopParsing();
        }
    }
}
