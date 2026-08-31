package net.cumba.datatable.provider.cdt;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * Parser for the {@code .cdt} text format. Produces a list of {@link CdtDataset} value objects that
 * higher layers (provider, library, writer, test loader) can consume.
 *
 * <h2>Format</h2>
 *
 * <pre>
 * # Comments start with hash. Blank lines allowed anywhere.
 *
 * dataset NAME [class=CLASS] [label="LABEL"] [key=value ...]
 * col NAME [type=Char|Num|Date|Time|DateTime] [label="..."] [length=N] [format=FMT] [codelist=NAME]
 * col NAME ...
 * ---
 * VALUE | VALUE | VALUE
 * VALUE | VALUE | VALUE
 * ---
 *
 * dataset NEXT ...
 * col ...
 * ---
 * ...
 * ---
 * </pre>
 *
 * <h2>Fences</h2>
 *
 * <p>
 * Each data block is opened by a fence — a line of three or more dashes and nothing else. The block
 * is closed by a line byte-identical to its opener, or by end-of-file. A dash line in the data that
 * does not match the opener is kept as data; authors escape literal {@code ---} values by choosing
 * a longer fence (e.g. {@code -----}).
 * </p>
 *
 * <h2>Backward compatibility</h2>
 *
 * <p>
 * Files with a single dataset and no closing fence continue to parse as one dataset reading to
 * end-of-file. Files using the new multi-dataset form must close every data block with a matching
 * fence before the next {@code dataset} line.
 * </p>
 */
public final class CdtParser
{

    private CdtParser()
    {
    }


    /**
     * Parse the given file path into a list of datasets.
     */
    public static List<CdtDataset> parseAll(Path aPath) throws IOException
    {
        String content = Files.readString(aPath, StandardCharsets.UTF_8);
        return parseAll(content, aPath.toString());
    }


    /**
     * Parse the given input stream (read as UTF-8) into a list of datasets.
     */
    public static List<CdtDataset> parseAll(InputStream aIn, String aSource) throws IOException
    {
        String content = new String(aIn.readAllBytes(), StandardCharsets.UTF_8);
        return parseAll(content, aSource);
    }


    /**
     * Parse the given reader into a list of datasets.
     */
    public static List<CdtDataset> parseAll(Reader aReader, String aSource) throws IOException
    {
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[4096];
        int n;
        while ((n = aReader.read(buf)) >= 0)
        {
            sb.append(buf, 0, n);
        }
        return parseAll(sb.toString(), aSource);
    }


    /**
     * Parse the given file content string into a list of datasets.
     *
     * @param aContent
     *            the full file content.
     * @param aSource
     *            a descriptive name used in error messages (e.g. filename).
     * @return the parsed datasets in file order; never empty.
     * @throws CdtParseException
     *             on any parse error.
     */
    public static List<CdtDataset> parseAll(String aContent, String aSource)
    {
        String[] lines = aContent.split("\\R", -1);
        Parser parser = new Parser(lines, aSource);
        return parser.parse();
    }


    /**
     * Parse the given content and return only the first dataset.
     *
     * @throws CdtParseException
     *             if the file is empty or malformed.
     */
    public static CdtDataset parseFirst(String aContent, String aSource)
    {
        List<CdtDataset> all = parseAll(aContent, aSource);
        return all.get(0);
    }


    /**
     * Parse the given content and return the dataset with the given name (case-insensitive), or
     * {@code null} if no such dataset exists.
     */
    public static @Nullable CdtDataset parseNamed(String aContent, String aSource,
            @Nullable String aName)
    {
        if (aName == null)
        {
            return null;
        }
        for (CdtDataset ds : parseAll(aContent, aSource))
        {
            if (aName.equalsIgnoreCase(ds.getName()))
            {
                return ds;
            }
        }
        return null;
    }

    // ---- Internal parser ----

    private static final class Parser
    {

        private final String[] lines;

        private final String source;

        private int lineIdx;

        Parser(String[] aLines, String aSource)
        {
            lines = aLines;
            source = aSource;
            lineIdx = 0;
        }


        List<CdtDataset> parse()
        {
            List<CdtDataset> datasets = new ArrayList<>();

            while (true)
            {
                skipBlankAndComments();
                if (lineIdx >= lines.length)
                {
                    break;
                }
                datasets.add(parseOneDataset());
            }

            if (datasets.isEmpty())
            {
                throw error(lineIdx, "file is empty or contains only comments");
            }
            return datasets;
        }


        private CdtDataset parseOneDataset()
        {
            // Dataset header
            DatasetHeader header = parseDatasetLine(lines[lineIdx].trim(), lineIdx);
            lineIdx++;

            // Column declarations until opening fence
            List<CdtColumn> columns = new ArrayList<>();
            String fence = null;
            while (lineIdx < lines.length)
            {
                String trimmed = lines[lineIdx].trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#"))
                {
                    lineIdx++;
                    continue;
                }
                if (CdtFence.isFence(trimmed))
                {
                    fence = trimmed;
                    lineIdx++;
                    break;
                }
                if (!trimmed.startsWith("col"))
                {
                    throw error(lineIdx, "expected 'col ...' or fence line, got: " + trimmed);
                }
                columns.add(parseColumnLine(trimmed, lineIdx));
                lineIdx++;
            }

            if (fence == null)
            {
                throw error(lineIdx, "missing fence line (e.g. '---') between metadata and data");
            }
            if (columns.isEmpty())
            {
                throw error(lineIdx, "no columns defined");
            }

            // Data rows until matching closer or EOF
            List<List<String>> dataRows = new ArrayList<>();
            while (lineIdx < lines.length)
            {
                String trimmed = lines[lineIdx].trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#"))
                {
                    lineIdx++;
                    continue;
                }
                if (CdtFence.matches(fence, trimmed))
                {
                    // End of this dataset's data section.
                    lineIdx++;
                    break;
                }
                dataRows.add(parseDataRow(trimmed, columns.size(), lineIdx));
                lineIdx++;
            }

            return CdtDataset.builder()//
                    .name(header.name)//
                    .label(header.label)//
                    .attrs(header.attrs)//
                    .columns(columns)//
                    .dataRows(dataRows)//
                    .fence(fence)//
                    .build();
        }


        private void skipBlankAndComments()
        {
            while (lineIdx < lines.length)
            {
                String trimmed = lines[lineIdx].trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#"))
                {
                    lineIdx++;
                }
                else
                {
                    break;
                }
            }
        }


        private DatasetHeader parseDatasetLine(String aLine, int aLineIdx)
        {
            if (!aLine.startsWith("dataset"))
            {
                throw error(aLineIdx, "expected 'dataset' line, got: " + aLine);
            }
            String rest = aLine.substring("dataset".length()).trim();
            if (rest.isEmpty())
            {
                throw error(aLineIdx, "dataset line missing name");
            }
            List<String> tokens = tokenize(rest, aLineIdx);
            if (tokens.isEmpty())
            {
                throw error(aLineIdx, "dataset line missing name");
            }
            String name = tokens.get(0);
            if (name.contains("="))
            {
                throw error(aLineIdx,
                        "dataset line missing name (first token is key=value): " + name);
            }

            DatasetHeader h = new DatasetHeader();
            h.name = name;
            h.attrs = new LinkedHashMap<>();
            for (int i = 1; i < tokens.size(); i++)
            {
                String tok = tokens.get(i);
                int eq = tok.indexOf('=');
                if (eq < 0)
                {
                    throw error(aLineIdx, "expected key=value after dataset name, got: " + tok);
                }
                String key = tok.substring(0, eq);
                String value = tok.substring(eq + 1);
                if ("label".equals(key))
                {
                    h.label = value;
                }
                else
                {
                    h.attrs.put(key, value);
                }
            }
            return h;
        }


        private CdtColumn parseColumnLine(String aLine, int aLineIdx)
        {
            String rest = aLine.substring("col".length()).trim();
            if (rest.isEmpty())
            {
                throw error(aLineIdx, "col line missing name");
            }
            List<String> tokens = tokenize(rest, aLineIdx);
            if (tokens.isEmpty())
            {
                throw error(aLineIdx, "col line missing name");
            }
            String name = tokens.get(0);
            if (name.contains("="))
            {
                throw error(aLineIdx, "col line missing name (first token is key=value): " + name);
            }

            CdtColumn.CdtColumnBuilder b = CdtColumn.builder().name(name).type(CdtType.CHAR);
            String format = null;
            CdtType type = CdtType.CHAR;

            for (int i = 1; i < tokens.size(); i++)
            {
                String tok = tokens.get(i);
                int eq = tok.indexOf('=');
                if (eq < 0)
                {
                    throw error(aLineIdx, "expected key=value after col name, got: " + tok);
                }
                String key = tok.substring(0, eq);
                String value = tok.substring(eq + 1);
                switch (key)
                {
                case "type" ->
                {
                    type = CdtType.parse(value);
                    if (type == null)
                    {
                        throw error(aLineIdx, "unknown column type: " + value
                                + " (expected Char, Num, Date, Time, or DateTime)");
                    }
                    b.type(type);
                }
                case "label" -> b.label(value);
                case "length" ->
                {
                    try
                    {
                        b.length(Integer.parseInt(value));
                    }
                    catch (NumberFormatException _)
                    {
                        throw error(aLineIdx, "invalid length value: " + value);
                    }
                }
                case "format" ->
                {
                    b.format(value);
                    format = value;
                }
                case "codelist" -> b.codelist(value);
                // Unknown key=value — preserved in attrs so consumers can
                // route it onto a DataTableColumnMeta typed field (if one
                // matches) or into the column's custom metadata table.
                default -> b.attr(key, value);
                }
            }

            // Auto-derive format for date/time/datetime when not explicitly set.
            if (format == null)
            {
                switch (type)
                {
                case DATE -> b.format("DATE9.");
                case TIME -> b.format("TIME5.");
                case DATETIME -> b.format("DATETIME20.");
                default ->
                {
                    /* no default */ }
                }
            }
            return b.build();
        }


        private List<String> parseDataRow(String aLine, int aColumnCount, int aLineIdx)
        {
            // Sentinel: a line consisting solely of "." represents a row of all-null fields.
            // Needed to express all-null rows in single-column tables (otherwise
            // indistinguishable from a blank line, which is skipped). A literal "."
            // char value must be quoted (write as "."), see CdtWriter.
            if (".".equals(aLine))
            {
                List<String> result = new ArrayList<>(aColumnCount);
                for (int i = 0; i < aColumnCount; i++)
                {
                    result.add("");
                }
                return result;
            }

            List<String> result = new ArrayList<>(aColumnCount);
            int i = 0;
            boolean expectField = true;
            while (expectField)
            {
                // Skip horizontal whitespace padding around the field.
                while (i < aLine.length() && isHorizWs(aLine.charAt(i)))
                {
                    i++;
                }

                if (i < aLine.length() && aLine.charAt(i) == '"')
                {
                    // Quoted field: content between the opening and next unescaped ",
                    // with \" / \\ escapes. Leading whitespace is preserved; trailing
                    // whitespace is stripped so "any value has trailing spaces removed".
                    i++;
                    StringBuilder field = new StringBuilder();
                    boolean closed = false;
                    while (i < aLine.length())
                    {
                        char c = aLine.charAt(i);
                        if (c == '\\' && i + 1 < aLine.length())
                        {
                            field.append(aLine.charAt(i + 1));
                            i += 2;
                            continue;
                        }
                        if (c == '"')
                        {
                            i++;
                            closed = true;
                            break;
                        }
                        field.append(c);
                        i++;
                    }
                    if (!closed)
                    {
                        throw error(aLineIdx, "unterminated quoted data value: " + aLine);
                    }
                    result.add(stripTrailing(field.toString()));

                    // Skip whitespace between closing quote and next | or EOL.
                    while (i < aLine.length() && isHorizWs(aLine.charAt(i)))
                    {
                        i++;
                    }
                    if (i < aLine.length() && aLine.charAt(i) != '|')
                    {
                        throw error(aLineIdx, "unexpected content after quoted data value: "
                                + aLine.substring(i));
                    }
                }
                else
                {
                    // Unquoted field: read to next | or EOL, then strip both sides
                    // (leading whitespace is padding; trailing is unwanted).
                    int start = i;
                    while (i < aLine.length() && aLine.charAt(i) != '|')
                    {
                        i++;
                    }
                    String field = aLine.substring(start, i).strip();
                    // Unquoted "." is the SAS missing-value sentinel for any column
                    // type (including Char). A literal "." Char value must be quoted
                    // — see CdtWriter.quoteFieldIfNeeded.
                    if (".".equals(field))
                    {
                        field = "";
                    }
                    result.add(field);
                }

                if (i < aLine.length() && aLine.charAt(i) == '|')
                {
                    i++;
                    expectField = true;
                }
                else
                {
                    expectField = false;
                }
            }

            if (result.size() != aColumnCount)
            {
                throw error(aLineIdx, "data row has " + result.size() + " field(s) but "
                        + aColumnCount + " column(s) declared: " + aLine);
            }
            return result;
        }


        private static boolean isHorizWs(char aCh)
        {
            return aCh == ' ' || aCh == '\t';
        }


        private static String stripTrailing(String aValue)
        {
            int end = aValue.length();
            while (end > 0 && Character.isWhitespace(aValue.charAt(end - 1)))
            {
                end--;
            }
            return end == aValue.length() ? aValue : aValue.substring(0, end);
        }


        /**
         * Tokenizes a line into whitespace-separated tokens, treating double quotes as grouping.
         * Example: {@code col NAME type=Char label="Subject ID"} yields
         * {@code [NAME, type=Char, label=Subject ID]}.
         */
        private List<String> tokenize(String aLine, int aLineIdx)
        {
            List<String> tokens = new ArrayList<>();
            StringBuilder cur = new StringBuilder();
            boolean inQuotes = false;
            boolean sawAnything = false;

            for (int i = 0; i < aLine.length(); i++)
            {
                char c = aLine.charAt(i);
                if (c == '"')
                {
                    inQuotes = !inQuotes;
                    sawAnything = true;
                    continue;
                }
                if (inQuotes)
                {
                    // Inside quotes a backslash escapes the next character so values
                    // can contain literal " and \. Matches the output produced by
                    // CdtWriter.quoteIfNeeded.
                    if (c == '\\' && i + 1 < aLine.length())
                    {
                        cur.append(aLine.charAt(++i));
                        sawAnything = true;
                        continue;
                    }
                    cur.append(c);
                    sawAnything = true;
                    continue;
                }
                if (Character.isWhitespace(c))
                {
                    if (sawAnything)
                    {
                        tokens.add(stripTrailing(cur.toString()));
                        cur.setLength(0);
                        sawAnything = false;
                    }
                    continue;
                }
                cur.append(c);
                sawAnything = true;
            }
            if (inQuotes)
            {
                throw error(aLineIdx, "unterminated quoted string");
            }
            if (sawAnything)
            {
                tokens.add(stripTrailing(cur.toString()));
            }
            return tokens;
        }


        private CdtParseException error(int aLineIdx, String aMessage)
        {
            return new CdtParseException(source + ":" + (aLineIdx + 1) + ": " + aMessage);
        }
    }


    // NullAway.Init: a private mutable holder fully populated by parseDatasetLine
    // (name + attrs always assigned) before any field is read; label is optional.
    @SuppressWarnings("NullAway.Init")
    private static final class DatasetHeader
    {

        String name;

        @Nullable
        String label;

        Map<String, String> attrs;
    }
}
