package net.cumba.datatable.provider.csv;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.univocity.parsers.csv.CsvParserSettings;
import java.io.StringReader;
import java.util.Objects;
import net.cumba.datatable.values.MissingValue;
import org.junit.jupiter.api.Test;

/**
 * The quote-marking step of {@link CsvRecordReader} on its own: which cells of a record were quoted
 * (owner rulings K7 / K7b), given the same record parsed with {@code keepQuotes}.
 */
class CsvRecordReaderTest
{

    @Test
    void marksQuotedCells()
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
                true, false, true, false, false
        }, CsvRecordReader.quotedCells(row, kept, '"'));
        assertArrayEquals(new boolean[]
        {
                false, false, false, false, true
        }, CsvRecordReader.quotedCells(row, kept, '\''));
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
        }, CsvRecordReader.quotedCells(row, new String[]
        {
                "\".\""
        }, '"'));
        assertArrayEquals(new boolean[]
        {
                false, false
        }, CsvRecordReader.quotedCells(row, null, '"'));
        assertArrayEquals(new boolean[]
        {
                true
        }, CsvRecordReader.quotedCells(new String[]
        {
                "."
        }, new String[]
        {
                "\".\"", "\".\""
        }, '"'));
    }


    /** The cell is an UNQUOTED {@code "."}: the plain SAS missing. */
    private static boolean isMis(CsvRecord aRecord, int aColumn)
    {
        return aRecord.getUnquotedMissing(aColumn) == MissingValue.MIS;
    }


    /**
     * K7b: the special missings go through the same gate -- a quoted one opens with the quote
     * touching the dot as well, so it is re-read and stays text; a bare one is its own missing.
     */
    @Test
    void specialsAreFlaggedLikeTheDot()
    {
        CsvRecordReader r = reader("\"x\",.B\n\"y\",\".A\"\n");
        try
        {
            CsvRecord bare = Objects.requireNonNull(r.next());
            assertEquals(0, r.reReadCount());
            assertSame(MissingValue.MIS_B, bare.getUnquotedMissing(1));

            CsvRecord quoted = Objects.requireNonNull(r.next());
            assertEquals(1, r.reReadCount());
            assertNull(quoted.getUnquotedMissing(1));
            assertEquals(".A", quoted.getValue(1));
            assertNull(r.next());
        }
        finally
        {
            r.stopParsing();
        }
    }


    /**
     * K7c: a record of the type-guessing sample has EVERY quoted cell flagged -- a quoted empty
     * value included -- and is re-read only when its raw text holds the quote character at all. A
     * record past the sample keeps the cheaper sentinel-only reading.
     */
    @Test
    void sampleRecordsFlagEveryQuotedCell()
    {
        CsvRecordReader r = reader("\"a\",b,\"\"\nc,d,e\n  \"1\"  ,x\"y,.\n\"1\",\"2\",.\n");
        try
        {
            CsvRecord quotes = Objects.requireNonNull(r.nextWithQuoteFlags());
            assertEquals(1, r.reReadCount());
            assertTrue(quotes.isQuoted(0));
            assertFalse(quotes.isQuoted(1));
            assertTrue(quotes.isQuoted(2), "a quoted empty value is quoted");

            CsvRecord none = Objects.requireNonNull(r.nextWithQuoteFlags());
            assertEquals(1, r.reReadCount(), "no quote character: nothing to re-read");
            assertFalse(none.isQuoted(0));
            assertFalse(none.isQuoted(1));
            assertFalse(none.isQuoted(2));

            CsvRecord padded = Objects.requireNonNull(r.nextWithQuoteFlags());
            assertEquals(2, r.reReadCount());
            assertTrue(padded.isQuoted(0), "whitespace outside the quotes");
            assertFalse(padded.isQuoted(1), "a quote inside an unquoted value");
            assertEquals("x\"y", padded.getValue(1));
            assertSame(MissingValue.MIS, padded.getUnquotedMissing(2));

            CsvRecord past = Objects.requireNonNull(r.next());
            assertEquals(2, r.reReadCount(), "past the sample: no quote touches the dot");
            assertFalse(past.isQuoted(0), "past the sample only sentinel cells are exact");
            assertSame(MissingValue.MIS, past.getUnquotedMissing(2));
            assertNull(r.nextWithQuoteFlags());
        }
        finally
        {
            r.stopParsing();
        }
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
            assertFalse(isMis(quotesNoDot, 0));
            assertTrue(isMis(dotNoQuote, 1));
            assertTrue(isMis(quotesElsewhere, 1));
            assertTrue(isMis(dotBeforeQuote, 1));

            CsvRecord quoted = Objects.requireNonNull(r.next());
            assertEquals(1, r.reReadCount());
            assertFalse(isMis(quoted, 1));
            assertEquals(".", quoted.getValue(1));

            CsvRecord pairNotADot = Objects.requireNonNull(r.next());
            assertEquals(2, r.reReadCount(), "the gate is a pre-check; the twin decides");
            assertTrue(isMis(pairNotADot, 1));

            CsvRecord bothQuotedNoNewline = Objects.requireNonNull(r.next());
            assertFalse(isMis(bothQuotedNoNewline, 0));
            assertFalse(isMis(bothQuotedNoNewline, 1));
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
            assertFalse(isMis(rec, 1));
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
            assertFalse(isMis(rec, 2));
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
            assertFalse(isMis(rec, 1));
        }
        finally
        {
            r.stopParsing();
        }
    }
}
