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
 * marks."</i>
 * <p>
 * univocity reports no per-field "was quoted" flag, and its {@code keepQuotes} mode cannot simply
 * be switched on for the whole file and stripped again: for malformed quoting (an unterminated or
 * unescaped quote) the kept-quote value is not the plain value plus two quotes, so every such cell
 * would silently read differently than it does today. The values therefore still come from the
 * ordinary parse, unchanged. Only a record that holds a {@code "."} cell AND whose raw text
 * contains the quote character at all is read a second time -- its raw text
 * ({@code ParsingContext.currentParsedContent()}) goes through a {@code keepQuotes} twin of the
 * same parser, where a quoted field is exactly one that starts with the quote character. Every
 * other record costs one scan of its values for a {@code "."}.
 */
final class CsvRecordReader
{

    private static final String DOT = ".";

    private final CsvParserSettings settings;

    private final CsvParser parser;

    private final char quote;

    /** The {@code keepQuotes} twin, created on the first record that needs it. */
    private @Nullable CsvParser quoteKeeper;

    /** How many records went through the twin -- the cost this class promises to confine. */
    private int reReads;

    CsvRecordReader(CsvParserSettings aSettings)
    {
        settings = aSettings;
        parser = new CsvParser(aSettings);
        quote = aSettings.getFormat().getQuote();
    }


    void beginParsing(Reader aReader)
    {
        parser.beginParsing(aReader);
    }


    /**
     * Returns the next record, or {@code null} at the end of the input.
     *
     * @return the next record, or {@code null} at the end of the input.
     */
    @Nullable
    CsvRecord next()
    {
        String[] row = parser.parseNext();
        if (row == null)
        {
            return null;
        }
        if (!containsDot(row))
        {
            return new CsvRecord(row);
        }
        String raw = Objects.requireNonNullElse(parser.getContext().currentParsedContent(), "");
        if (raw.indexOf(quote) < 0)
        {
            return new CsvRecord(row);
        }
        reReads++;
        return new CsvRecord(row, quotedDots(row, quoteKeeper().parseLine(raw), quote));
    }


    /**
     * Returns how many records so far were read a second time through the {@code keepQuotes} twin:
     * only those holding a {@code "."} cell whose raw text contains the quote character.
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
     * Mark the cells of {@code aRow} that are exactly {@code "."} and quoted, reading the quotes
     * off {@code aKept} -- the same record parsed with {@code keepQuotes}, where a quoted field
     * starts with the quote character. A cell {@code aKept} does not reach (or a {@code null}
     * {@code aKept}) is not quoted: an unquoted {@code .} is the ruling's default reading.
     */
    static boolean[] quotedDots(String[] aRow, String @Nullable [] aKept, char aQuote)
    {
        boolean[] res = new boolean[aRow.length];
        if (aKept != null)
        {
            int n = Math.min(aRow.length, aKept.length);
            for (int i = 0; i < n; i++)
            {
                String k = aKept[i];
                res[i] = DOT.equals(aRow[i]) && k != null && k.indexOf(aQuote) == 0;
            }
        }
        return res;
    }


    private static boolean containsDot(String[] aRow)
    {
        for (String v : aRow)
        {
            if (DOT.equals(v))
            {
                return true;
            }
        }
        return false;
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
