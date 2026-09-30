package net.cumba.datatable.provider.csv;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.regex.Pattern;
import net.cumba.datatable.values.MissingValue;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * A seeded fuzz of {@link CsvRecordReader} whose oracle is the GENERATOR: every file is written
 * cell by cell, so the test knows which missing-sentinel cells ({@code .}, {@code ._},
 * {@code .A}..{@code .Z}) it enclosed in quotes and which it wrote bare, and asserts the reader's
 * reading of EVERY cell: a bare sentinel is its own SAS missing, anything else none (owner rulings
 * K7 / K7b: <i>"missing only if it is not in quotation marks"</i>, <i>"Agree to special missings as
 * long as they are not in quotation marks"</i>).
 * <p>
 * The files vary what the re-read gate and its twin parse could trip over: {@code \n}, {@code \r\n}
 * and {@code \r} separators, blank and comment lines, a BOM, a missing final newline, multi-line
 * quoted fields holding a dot, the delimiter and an escaped quote, cells padded outside their
 * quotes, the delimiters {@code ,} {@code ;} tab and {@code ||}, a configured single-quote
 * character, tiny input buffers and an unterminated {@code ".} at the end of the input. A file
 * univocity itself reads differently from what was generated (its values, not the flags) is skipped
 * -- the oracle would be wrong there, not the reader -- and the test asserts how few were skipped,
 * so it cannot pass by skipping everything.
 */
class CsvRecordReaderFuzzTest
{

    private static final long SEED = 0x4B37_2026_0930L;

    private static final int FILES = 3000;

    private static final String[] DELIMITERS =
    {
            ",", ";", "\t", "||"
    };

    private static final String[] SEPARATORS =
    {
            "\n", "\r\n", "\r"
    };

    /** The sentinels the generator writes, the plain dot most often. */
    private static final String[] SENTINELS =
    {
            ".", ".", ".", "._", ".A", ".Q", ".Z"
    };

    /** The {@code .cdt} grammar's sentinel -- the oracle's own copy, not the reader's. */
    private static final Pattern SENTINEL = Pattern.compile("\\.[_A-Z]?");

    /** One generated cell: its text in the file, the value univocity must read, and its quoting. */
    private record Cell(String text, String value, boolean quoted)
    {
    }


    /** What one run found, to prove the population was really covered. */
    private static final class Tally
    {

        int files;

        int skipped;

        int quotedDots;

        int unquotedDots;

        int quotedSpecials;

        int unquotedSpecials;

        int sampledQuotedCells;

        int sampledBareCells;

        int multiLineQuotedDotRecords;

        int unterminated;

        final int[] perSeparator = new int[SEPARATORS.length];

        final int[] perDelimiter = new int[DELIMITERS.length];
    }

    @Test
    void readerFlagsEveryGeneratedDotAsItWasWritten()
    {
        Random rnd = new Random(SEED);
        Tally tally = new Tally();
        for (int f = 0; f < FILES; f++)
        {
            fuzzOne(rnd, tally);
        }

        assertEquals(FILES, tally.files);
        assertTrue(tally.skipped * 50 < FILES, "univocity disagreed with the generator on "
                + tally.skipped + " of " + FILES + " files -- the oracle covers too little");
        assertTrue(tally.quotedDots > 2000, "quoted dots checked: " + tally.quotedDots);
        assertTrue(tally.unquotedDots > 2000, "unquoted dots checked: " + tally.unquotedDots);
        assertTrue(tally.quotedSpecials > 1000, "quoted specials checked: " + tally.quotedSpecials);
        assertTrue(tally.unquotedSpecials > 1000,
                "unquoted specials checked: " + tally.unquotedSpecials);
        assertTrue(tally.sampledQuotedCells > 5000,
                "quoted cells checked in a sample: " + tally.sampledQuotedCells);
        assertTrue(tally.sampledBareCells > 5000,
                "bare cells checked in a sample: " + tally.sampledBareCells);
        assertTrue(tally.multiLineQuotedDotRecords > 100,
                "multi-line records with a quoted dot: " + tally.multiLineQuotedDotRecords);
        assertTrue(tally.unterminated > 20, "unterminated \". at EOF: " + tally.unterminated);
        for (int i = 0; i < SEPARATORS.length; i++)
        {
            assertTrue(tally.perSeparator[i] > FILES / 5, "separator #" + i + " under-covered");
        }
        for (int i = 0; i < DELIMITERS.length; i++)
        {
            assertTrue(tally.perDelimiter[i] > FILES / 6, "delimiter #" + i + " under-covered");
        }
    }


    private static void fuzzOne(Random aRnd, Tally aTally)
    {
        aTally.files++;
        int di = aRnd.nextInt(DELIMITERS.length);
        int si = aRnd.nextInt(SEPARATORS.length);
        String delim = DELIMITERS[di];
        String sep = SEPARATORS[si];
        char quote = aRnd.nextInt(5) == 0 ? '\'' : '"';

        CsvParserSettings ps = new CsvParserSettings();
        ps.getFormat().setDelimiter(delim);
        ps.getFormat().setQuote(quote);
        ps.getFormat().setQuoteEscape(quote);
        ps.setMaxCharsPerColumn(-1);
        ps.setMaxColumns(4096);
        ps.setReadInputOnSeparateThread(false);
        if (aRnd.nextBoolean())
        {
            // univocity detects the line separator in its first buffer only, so a tiny buffer
            // goes with a configured separator -- which also takes the twin's no-detection path.
            ps.setInputBufferSize(4 + aRnd.nextInt(24));
            ps.getFormat().setLineSeparator(sep);
        }
        else
        {
            ps.setLineSeparatorDetectionEnabled(true);
        }

        int cols = 1 + aRnd.nextInt(5);
        int rows = 1 + aRnd.nextInt(6);
        boolean bom = aRnd.nextInt(8) == 0;
        boolean finalNewline = aRnd.nextInt(3) != 0;
        boolean unterminated = !finalNewline && aRnd.nextInt(4) == 0;

        StringBuilder text = new StringBuilder();
        if (bom)
        {
            text.append('\uFEFF');
        }
        List<Cell[]> expected = new ArrayList<>();
        for (int r = 0; r < rows; r++)
        {
            junkLines(aRnd, text, sep, quote);
            Cell[] row = new Cell[cols];
            for (int c = 0; c < cols; c++)
            {
                boolean last = r == rows - 1 && c == cols - 1;
                row[c] = last && unterminated ? unterminated(aRnd, quote)
                        : cell(aRnd, delim, sep, quote, cols == 1);
                if (c > 0)
                {
                    text.append(delim);
                }
                text.append(row[c].text());
            }
            expected.add(row);
            if (r < rows - 1 || finalNewline)
            {
                text.append(sep);
            }
        }
        String csv = text.toString();
        List<String[]> plain = plainParse(ps, csv);
        if (!matches(expected, plain))
        {
            aTally.skipped++;
            return;
        }
        aTally.perSeparator[si]++;
        aTally.perDelimiter[di]++;
        if (unterminated)
        {
            aTally.unterminated++;
        }
        check(ps, csv, expected, plain, sep, aRnd.nextInt(rows + 1), aTally);
    }


    /**
     * Reads the first {@code aSample} records as the type-guessing sample (every quoted cell
     * flagged, K7c) and the rest as the records past it (sentinels only).
     */
    private static void check(CsvParserSettings aSettings, String aCsv, List<Cell[]> aExpected,
            List<String[]> aPlain, String aSep, int aSample, Tally aTally)
    {
        CsvRecordReader reader = new CsvRecordReader(aSettings);
        reader.beginParsing(new StringReader(aCsv));
        try
        {
            for (int i = 0; i < aExpected.size(); i++)
            {
                int r = i;
                boolean sampled = r < aSample;
                CsvRecord rec = Objects.requireNonNull(
                        sampled ? reader.nextWithQuoteFlags() : reader.next(),
                        () -> "record " + r + " missing in " + show(aCsv));
                assertArrayEquals(aPlain.get(r), rec.getValues(),
                        () -> "values differ from the plain parse in " + show(aCsv));
                Cell[] row = aExpected.get(r);
                boolean multiLine = false;
                boolean quotedDot = false;
                for (int c = 0; c < row.length; c++)
                {
                    multiLine |= row[c].quoted() && row[c].text().contains(aSep);
                    int col = c;
                    boolean wantQuoted = row[c].quoted();
                    if (sampled)
                    {
                        assertEquals(wantQuoted, rec.isQuoted(c),
                                () -> "sample record " + r + " col " + col + " written "
                                        + (wantQuoted ? "quoted" : "bare") + " in " + show(aCsv));
                        if (wantQuoted)
                        {
                            aTally.sampledQuotedCells++;
                        }
                        else
                        {
                            aTally.sampledBareCells++;
                        }
                    }
                    boolean sentinel = SENTINEL.matcher(row[c].value()).matches();
                    @Nullable
                    MissingValue want = sentinel && !wantQuoted
                            ? MissingValue.forValue(row[c].value())
                            : null;
                    assertSame(want, rec.getUnquotedMissing(c), () -> "record " + r + " col " + col
                            + " written " + (wantQuoted ? "quoted" : "bare") + " in " + show(aCsv));
                    if (!sentinel)
                    {
                        continue;
                    }
                    boolean dot = ".".equals(row[c].value());
                    if (wantQuoted)
                    {
                        quotedDot = true;
                        if (dot)
                        {
                            aTally.quotedDots++;
                        }
                        else
                        {
                            aTally.quotedSpecials++;
                        }
                    }
                    else if (dot)
                    {
                        aTally.unquotedDots++;
                    }
                    else
                    {
                        aTally.unquotedSpecials++;
                    }
                }
                if (multiLine && quotedDot)
                {
                    aTally.multiLineQuotedDotRecords++;
                }
            }
            assertTrue(reader.next() == null, () -> "extra record in " + show(aCsv));
        }
        finally
        {
            reader.stopParsing();
        }
    }


    /** Blank lines and comment lines -- a comment may itself hold a quoted dot. */
    private static void junkLines(Random aRnd, StringBuilder aText, String aSep, char aQuote)
    {
        while (aRnd.nextInt(6) == 0)
        {
            if (aRnd.nextBoolean())
            {
                aText.append(aSep);
            }
            else
            {
                aText.append("# note ").append(aQuote).append('.').append(aQuote).append(aSep);
            }
        }
    }


    /**
     * One cell. A record of a single empty cell is a blank line, which univocity skips, so the only
     * cell of a record is never empty ({@code aNonEmpty}).
     */
    private static Cell cell(Random aRnd, String aDelim, String aSep, char aQuote,
            boolean aNonEmpty)
    {
        String pad = aRnd.nextInt(4) == 0 ? " " : "";
        String padR = aRnd.nextInt(4) == 0 ? " " : "";
        int kind = aRnd.nextInt(5);
        String sentinel = SENTINELS[aRnd.nextInt(SENTINELS.length)];
        if (kind == 0)
        {
            return new Cell(pad + sentinel + padR, sentinel, false);
        }
        if (kind == 1)
        {
            return new Cell(pad + aQuote + sentinel + aQuote + padR, sentinel, true);
        }
        if (kind == 2)
        {
            String bare = bareText(aRnd, aNonEmpty);
            return new Cell(bare, bare, false);
        }
        return quotedText(aRnd, aDelim, aSep, aQuote, pad, padR, aNonEmpty);
    }


    /** An unterminated quoted sentinel at the end of the input. */
    private static Cell unterminated(Random aRnd, char aQuote)
    {
        String sentinel = SENTINELS[aRnd.nextInt(SENTINELS.length)];
        return new Cell(aQuote + sentinel, sentinel, true);
    }


    /**
     * Unquoted text: may hold a dot, never the delimiter, a quote, a line break or a comment. It
     * may happen to spell a sentinel ({@code .B}) -- the oracle reads every cell, so that is
     * covered.
     */
    private static String bareText(Random aRnd, boolean aNonEmpty)
    {
        String[] pieces =
        {
                "a", "1", ".", "x.y", "2.5", "..", "B"
        };
        int n = (aNonEmpty ? 1 : 0) + aRnd.nextInt(3);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++)
        {
            sb.append(pieces[aRnd.nextInt(pieces.length)]);
        }
        String s = sb.toString();
        return ".".equals(s) ? "a." : s;
    }


    /** Quoted text holding dots, the delimiter, an escaped quote and line breaks. */
    private static Cell quotedText(Random aRnd, String aDelim, String aSep, char aQuote,
            String aPad, String aPadR, boolean aNonEmpty)
    {
        StringBuilder raw = new StringBuilder();
        StringBuilder value = new StringBuilder();
        int n = (aNonEmpty ? 1 : 0) + aRnd.nextInt(5);
        for (int i = 0; i < n; i++)
        {
            String piece = switch (aRnd.nextInt(5))
            {
            case 0 -> aRnd.nextBoolean() ? "." : ".A";
            case 1 -> aDelim;
            case 2 -> String.valueOf(aQuote);
            case 3 -> aSep;
            default -> "t";
            };
            value.append(piece);
            // a quote inside a quoted field is written doubled (the escape)
            raw.append(piece.indexOf(aQuote) >= 0 ? piece + piece : piece);
        }
        return new Cell(aPad + aQuote + raw + aQuote + aPadR, value.toString(), true);
    }


    private static List<String[]> plainParse(CsvParserSettings aSettings, String aCsv)
    {
        List<String[]> res = new ArrayList<>();
        CsvParser p = new CsvParser(aSettings);
        p.beginParsing(new StringReader(aCsv));
        try
        {
            String[] row;
            while ((row = p.parseNext()) != null)
            {
                res.add(row);
            }
        }
        finally
        {
            p.stopParsing();
        }
        return res;
    }


    private static boolean matches(List<Cell[]> aExpected, List<String[]> aPlain)
    {
        if (aExpected.size() != aPlain.size())
        {
            return false;
        }
        for (int r = 0; r < aExpected.size(); r++)
        {
            Cell[] want = aExpected.get(r);
            String[] got = aPlain.get(r);
            if (want.length != got.length)
            {
                return false;
            }
            for (int c = 0; c < want.length; c++)
            {
                String g = got[c] == null ? "" : got[c];
                if (!lines(want[c].value()).equals(lines(g)))
                {
                    return false;
                }
            }
        }
        return true;
    }


    /**
     * univocity may or may not normalise a line break inside quotes, depending on whether it had
     * detected the separator yet -- its business, not the quote flags'.
     */
    private static String lines(String aValue)
    {
        return aValue.replace("\r\n", "\n").replace('\r', '\n');
    }


    private static String show(String aCsv)
    {
        return "[" + aCsv.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t") + "]";
    }
}
