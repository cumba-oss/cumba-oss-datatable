package net.cumba.datatable.provider.csv;

import com.univocity.parsers.csv.CsvFormat;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import java.io.Reader;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Reads {@link CsvRecord}s from a univocity {@link CsvParser} and tells a quoted {@code "."} from
 * an unquoted {@code .} -- owner ruling K7 (2026-09-30): <i>"missing only if it is not in quotation
 * marks."</i> The same holds for the special missings {@code ._} and {@code .A}..{@code .Z} (owner,
 * 2026-09-30: <i>"Agree to special missings as long as they are not in quotation marks."</i>);
 * every such sentinel starts with a dot, so what is said of {@code "."} below applies to all 28.
 * <p>
 * univocity reports no per-field "was quoted" flag, and its {@code keepQuotes} mode cannot simply
 * be switched on for the whole file and stripped again: for malformed quoting (an unterminated or
 * unescaped quote) the kept-quote value is not the plain value plus two quotes, so every such cell
 * would silently read differently than it does today. The values therefore still come from the
 * ordinary parse, unchanged. Only a record that holds a {@code "."} cell AND whose raw text
 * ({@code ParsingContext.currentParsedContent()}) has the quote character IMMEDIATELY followed by a
 * dot is read a second time -- through a {@code keepQuotes} twin of the same parser, where a quoted
 * field is exactly one that starts with the quote character. A quoted {@code "."} cannot exist
 * without that pair: its opening quote touches the dot (an unterminated {@code ".} at the end of
 * the input included), so a quote anywhere else -- the ordinary quoted string columns of an export
 * -- costs no second parse. The one exception is a parser that ignores leading whitespace inside
 * quotes, where {@code " ."} is a quoted {@code "."} too; there any quote gates the re-read. Every
 * other record costs one scan of its values for a sentinel.
 * <p>
 * A BLANK cell needs its flag too (owner ruling BNM-F7, 2026-10-12, with K7c): in a numeric column
 * an unquoted blank cell has no value ({@code MIS}), a quoted one is text that is not a number
 * ({@code MIS_ERROR}), and univocity reads a quoted {@code ""} as {@code null}, exactly like an
 * unquoted empty field. Its trace in the raw text is the same kind of pair: a quoted empty value is
 * the quote character twice. So a record holding an empty cell is re-read only when its raw text
 * holds a doubled quote (an escaped quote inside some text costs a second parse it did not need,
 * never a wrong flag), and one holding a whitespace-only cell -- which only quoting keeps, since
 * the parser trims unquoted whitespace -- whenever it holds a quote character at all.
 * <p>
 * The type-guessing sample is read differently ({@link #nextWithQuoteFlags()}): there EVERY quoted
 * cell must be known, because any quoted value is text evidence (owner ruling K7c, 2026-09-30:
 * <i>"yes any quoted value is always a string."</i>), so a sample record is re-read whenever its
 * raw text holds the quote character at all. The sample is bounded (the guess row count), so a
 * quote-everything export pays the second parse for those rows only.
 */
final class CsvRecordReader
{

    private static final String DOT = ".";

    /**
     * {@link #flaggedCellKinds} bit: a missing-sentinel cell ({@code .}, {@code ._}, {@code .A}..).
     */
    private static final int KIND_SENTINEL = 1;

    /** {@link #flaggedCellKinds} bit: an empty cell (a quoted {@code ""} reads as one). */
    private static final int KIND_EMPTY = 2;

    /**
     * {@link #flaggedCellKinds} bit: a non-empty, whitespace-only cell (only quoting keeps one).
     */
    private static final int KIND_WHITESPACE = 4;

    private final CsvParserSettings settings;

    private final CsvParser parser;

    private final char quote;

    /** The quote character followed by a dot: the opening of every quoted {@code "."}. */
    private final String quoteDot;

    /** The quote character twice: every quoted empty value {@code ""}. */
    private final String quoteQuote;

    /** {@code true} when {@code " ."} reads as {@code "."}, so the quote need not touch the dot. */
    private final boolean anyQuoteGates;

    /** The {@code keepQuotes} twin, created on the first record that needs it. */
    private @Nullable CsvParser quoteKeeper;

    /** How many records went through the twin -- the cost this class promises to confine. */
    private int reReads;

    CsvRecordReader(CsvParserSettings aSettings)
    {
        settings = aSettings;
        parser = new CsvParser(aSettings);
        quote = aSettings.getFormat().getQuote();
        quoteDot = quote + DOT;
        quoteQuote = String.valueOf(quote) + quote;
        anyQuoteGates = aSettings.getIgnoreLeadingWhitespacesInQuotes();
    }


    void beginParsing(Reader aReader)
    {
        parser.beginParsing(aReader);
    }


    /**
     * Returns the next record, or {@code null} at the end of the input. Its quote flags are exact
     * for every missing-sentinel cell and every blank cell -- all a record past the type-guessing
     * sample needs.
     *
     * @return the next record, or {@code null} at the end of the input.
     */
    @Nullable
    CsvRecord next()
    {
        return read(false);
    }


    /**
     * Returns the next record with EVERY quoted cell flagged, or {@code null} at the end of the
     * input -- for the type-guessing sample, where any quoted value is text evidence (owner ruling
     * K7c, 2026-09-30: <i>"yes any quoted value is always a string."</i>). Such a record is read a
     * second time whenever its raw text holds the quote character at all.
     *
     * @return the next record, or {@code null} at the end of the input.
     */
    @Nullable
    CsvRecord nextWithQuoteFlags()
    {
        return read(true);
    }


    private @Nullable CsvRecord read(boolean aEveryQuote)
    {
        String[] row = parser.parseNext();
        if (row == null)
        {
            return null;
        }
        int kinds = aEveryQuote ? 0 : flaggedCellKinds(row);
        if (!aEveryQuote && kinds == 0)
        {
            return new CsvRecord(row);
        }
        String raw = Objects.requireNonNullElse(parser.getContext().currentParsedContent(), "");
        if (!mayHoldQuotedFlaggedCell(raw, aEveryQuote || anyQuoteGates, kinds))
        {
            return new CsvRecord(row);
        }
        reReads++;
        return new CsvRecord(row, quotedCells(row, quoteKeeper().parseLine(raw), quote));
    }


    /**
     * Whether the raw text of a record can hold a QUOTED cell of one of the kinds
     * {@link #flaggedCellKinds} found -- the gate in front of the second parse. Every kind leaves
     * its own trace in the raw text: a quoted {@code "."} has the quote touching the dot, a quoted
     * empty value is two quote characters in a row, and a quoted whitespace-only value at least one
     * quote character. With {@code aAnyQuote} (a sample record, or a parser that ignores leading
     * whitespace inside quotes) any quote character gates.
     */
    private boolean mayHoldQuotedFlaggedCell(String aRaw, boolean aAnyQuote, int aKinds)
    {
        if (aAnyQuote || (aKinds & KIND_WHITESPACE) != 0)
        {
            return aRaw.indexOf(quote) >= 0;
        }
        return ((aKinds & KIND_SENTINEL) != 0 && aRaw.contains(quoteDot))
                || ((aKinds & KIND_EMPTY) != 0 && aRaw.contains(quoteQuote));
    }


    /**
     * Returns how many records so far were read a second time through the {@code keepQuotes} twin:
     * a sample record ({@link #nextWithQuoteFlags()}) whose raw text holds the quote character, and
     * a later record holding a missing-sentinel cell whose raw text has the quote character
     * immediately followed by a dot.
     *
     * @return the number of re-read records.
     */
    int reReadCount()
    {
        return reReads;
    }


    void stopParsing()
    {
        parser.stopParsing();
        CsvParser keeper = quoteKeeper;
        if (keeper != null)
        {
            keeper.stopParsing();
        }
    }


    /**
     * Mark the cells of {@code aRow} that were quoted, reading the quotes off {@code aKept} -- the
     * same record parsed with {@code keepQuotes}, where a quoted field starts with the quote
     * character. A cell {@code aKept} does not reach (or a {@code null} {@code aKept}) is not
     * quoted: an unquoted sentinel is the rulings' default reading.
     */
    static boolean[] quotedCells(String[] aRow, String @Nullable [] aKept, char aQuote)
    {
        boolean[] res = new boolean[aRow.length];
        if (aKept != null)
        {
            int n = Math.min(aRow.length, aKept.length);
            for (int i = 0; i < n; i++)
            {
                String k = aKept[i];
                res[i] = k != null && k.indexOf(aQuote) == 0;
            }
        }
        return res;
    }


    /**
     * The kinds of cell in {@code aRow} whose quote flag a record past the sample must carry, as a
     * bit set of {@link #KIND_SENTINEL}, {@link #KIND_EMPTY} and {@link #KIND_WHITESPACE}: a
     * missing sentinel (K7: quoted is text, unquoted the missing), and a blank cell -- in a numeric
     * column an unquoted blank cell is {@code MIS} but a quoted one is text that is not a number
     * (BNM-F7 with K7c), and univocity reads a quoted {@code ""} as {@code null} exactly like an
     * unquoted empty field. One scan of the values; {@code 0} when the record needs no flags.
     */
    private static int flaggedCellKinds(String[] aRow)
    {
        int kinds = 0;
        for (String v : aRow)
        {
            if (v == null || v.isEmpty())
            {
                kinds |= KIND_EMPTY;
            }
            else if (CsvRecord.isMissingSentinel(v))
            {
                kinds |= KIND_SENTINEL;
            }
            else if (v.isBlank())
            {
                kinds |= KIND_WHITESPACE;
            }
        }
        return kinds;
    }


    private CsvParser quoteKeeper()
    {
        CsvParser keeper = quoteKeeper;
        if (keeper == null)
        {
            CsvParserSettings s = settings.clone();
            s.setKeepQuotes(true);
            s.setReadInputOnSeparateThread(false);
            // The raw record ends in the line separator the main parse DETECTED; a twin left to
            // detect again on a single record could decide differently (and a record with no
            // separator at all, the last one, would fall back to the platform default).
            CsvFormat detected = parser.getDetectedFormat();
            if (detected != null)
            {
                s.setLineSeparatorDetectionEnabled(false);
                s.getFormat().setLineSeparator(detected.getLineSeparator());
            }
            keeper = new CsvParser(s);
            quoteKeeper = keeper;
        }
        return keeper;
    }
}
