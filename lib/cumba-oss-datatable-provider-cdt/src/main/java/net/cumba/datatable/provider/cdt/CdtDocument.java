package net.cumba.datatable.provider.cdt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A <b>text-faithful</b> model of a {@code .cdt} file: the document keeps every byte of its text,
 * knows where each dataset block sits, and can rewrite one block's {@code col} lines and data rows
 * while leaving everything else — the prelude ({@code #!RuleTest}, {@code #test}, {@code #define}
 * and every other directive), comments, blank lines, the other blocks and their alignment — exactly
 * as it was ({@code PLAN-rule-authoring-api} §3.1: the old editor's serializer predated the
 * 2026-09-17 sentinel grammar and corrupted missing values three ways; this model never writes a
 * byte it did not either read or produce through {@link CdtValues}' encoding).
 *
 * <h2>What a block is</h2>
 *
 * <p>
 * Exactly what {@link CdtParser} reads: the {@code dataset …} line, the {@code col …} lines up to
 * the opening fence, the fence, the data rows up to the matching closing fence (or end of file),
 * and that closing fence. Blank and {@code #} lines <em>inside</em> a block belong to it; a rewrite
 * of the block replaces its {@code col} lines and its rows wholesale (comments between rows are not
 * kept — the editor's grid has nowhere to show them), the {@code dataset} line and the two fence
 * lines are kept byte for byte.
 * </p>
 *
 * <h2>Rows</h2>
 *
 * <p>
 * A replacement row is written the way {@link CdtWriter} writes a row: every field in
 * {@link CdtValues#encodeField(String, boolean)}'s encoding (the parser's own: an unquoted missing
 * sentinel verbatim, a literal that would read back as a sentinel backslash-escaped), quoted only
 * where the parser needs the quotes to recover the exact characters, an all-missing row as its
 * cells (never the bare {@code .} line, which the reader folds to every cell missing anyway). A
 * cell the parser cannot read back exactly is refused with an {@link IllegalArgumentException},
 * never written: a line break of any kind ({@code \R}, which the parser splits on before it sees a
 * quote), trailing whitespace (the parser strips it, inside quotes too), and a row whose field
 * count is not the column count. The same holds for a {@code col} line's name and attribute values.
 * </p>
 *
 * <h2>Lines</h2>
 *
 * <p>
 * The text is split exactly where {@link CdtParser} splits it — on every {@code \R} line break
 * ({@code \r\n}, {@code \n}, {@code \r}, {@code U+000B}, {@code U+000C}, {@code U+0085},
 * {@code U+2028}, {@code U+2029}) — and a dataset's name is the parser's own reading of it (quotes
 * removed).
 * </p>
 */
public final class CdtDocument
{

    /** The lines of the text, each with its own line terminator (the last one may have none). */
    private final List<String> lines;

    private final List<DatasetBlock> blocks;

    private CdtDocument(List<String> aLines, List<DatasetBlock> aBlocks)
    {
        lines = List.copyOf(aLines);
        blocks = List.copyOf(aBlocks);
    }

    /**
     * Where a dataset block sits, as 0-based line indices; {@code end} is exclusive and points past
     * the closing fence (or at the end of the file when there is none).
     *
     * @param name
     *            the dataset name as the parser reads it (a quoted name without its quotes)
     * @param headerLine
     *            the {@code dataset …} line
     * @param columnsStart
     *            the first line after the header
     * @param fenceLine
     *            the opening fence
     * @param rowsStart
     *            the first line after the opening fence
     * @param closeLine
     *            the closing fence, or {@code -1} when the block runs to the end of the file
     * @param end
     *            the first line after the block
     */
    public record DatasetBlock(String name, int headerLine, int columnsStart, int fenceLine,
            int rowsStart, int closeLine, int end)
    {

        /** The fence text (trimmed) that opens and closes the block. */
        String fence(List<String> aLines)
        {
            return aLines.get(fenceLine).strip();
        }
    }

    /**
     * Parse a document. The text is kept verbatim; the blocks are located the way {@link CdtParser}
     * locates them, so a text the parser refuses is refused here too.
     *
     * @param aText
     *            the whole file
     * @return the document
     * @throws CdtParseException
     *             when the text is not a {@code .cdt} the parser accepts
     */
    public static CdtDocument parse(String aText)
    {
        Objects.requireNonNull(aText, "text");
        // The parse throws exactly the errors the parser throws and names the datasets; the lines
        // are split on the parser's own \R, so the walk below finds the parser's blocks.
        List<CdtDataset> parsed = CdtParser.parseAll(aText, "document");
        List<String> lines = splitKeepingTerminators(aText);
        List<String> names = new ArrayList<>(parsed.size());
        for (CdtDataset dataset : parsed)
        {
            names.add(dataset.getName());
        }
        return new CdtDocument(lines, locate(lines, names));
    }


    /** The text, byte for byte. */
    public String text()
    {
        StringBuilder sb = new StringBuilder();
        for (String line : lines)
        {
            sb.append(line);
        }
        return sb.toString();
    }


    /** The dataset blocks in file order. */
    public List<DatasetBlock> datasets()
    {
        return blocks;
    }


    /**
     * The block of a dataset.
     *
     * @param aName
     *            the dataset name (matched case-insensitively, as the parser matches)
     * @return the block, or {@code null}
     */
    public @Nullable DatasetBlock dataset(String aName)
    {
        for (DatasetBlock b : blocks)
        {
            if (b.name().equalsIgnoreCase(aName))
            {
                return b;
            }
        }
        return null;
    }


    /**
     * A new document in which one dataset's {@code col} lines and rows are replaced by those of
     * {@code aDataset}; its {@code dataset} line, its fences and every byte outside the block are
     * unchanged. The dataset is matched by {@code aDataset.getName()}.
     *
     * @param aDataset
     *            the replacement (its {@code dataRows} in {@link CdtValues#encodeField} encoding,
     *            as {@link CdtParser} produces and a grid edit hands back)
     * @return the rewritten document
     * @throws IllegalArgumentException
     *             when the document holds no dataset of that name, when {@code aDataset} has no
     *             column, or when a column or a cell cannot be written so that the parser reads it
     *             back exactly (a line break, trailing whitespace, a field count that is not the
     *             column count, a name the parser cannot read as one)
     */
    public CdtDocument withDataset(CdtDataset aDataset)
    {
        DatasetBlock block = dataset(aDataset.getName());
        if (block == null)
        {
            throw new IllegalArgumentException(
                    "no dataset " + aDataset.getName() + " in the document");
        }
        int columnCount = aDataset.getColumns().size();
        if (columnCount == 0)
        {
            throw new IllegalArgumentException(
                    "dataset " + aDataset.getName() + " has no column; the parser refuses it");
        }
        String eol = terminatorOf(block);
        List<String> out = new ArrayList<>(lines.size());
        out.addAll(lines.subList(0, block.columnsStart()));
        for (CdtColumn column : aDataset.getColumns())
        {
            out.add(columnLine(column) + eol);
        }
        String fence = lines.get(block.fenceLine());
        if (!aDataset.getDataRows().isEmpty() && !endsWithTerminator(fence))
        {
            // a file-final fence without a line break: the first row needs one before it
            fence = fence + eol;
        }
        out.add(fence);
        int rowNo = 0;
        for (List<String> row : aDataset.getDataRows())
        {
            rowNo++;
            if (row.size() != columnCount)
            {
                throw new IllegalArgumentException("row " + rowNo + " has " + row.size()
                        + " field(s) but " + columnCount + " column(s) declared");
            }
            out.add(rowLine(row) + eol);
        }
        if (block.closeLine() >= 0)
        {
            out.add(lines.get(block.closeLine()));
        }
        else if (!aDataset.getDataRows().isEmpty() && !endsWithTerminator(lines.getLast()))
        {
            // the last row of a file-final block keeps the file's "no final newline" shape
            String last = out.removeLast();
            out.add(last.substring(0, last.length() - eol.length()));
        }
        out.addAll(lines.subList(block.end(), lines.size()));
        List<String> names = new ArrayList<>(blocks.size());
        for (DatasetBlock b : blocks)
        {
            names.add(b.name());
        }
        return new CdtDocument(out, locate(out, names));
    }

    // ---- rendering ----------------------------------------------------------------------


    /**
     * The {@code col} line of a column, in the writer's spelling; the name is quoted when the
     * parser's tokenizer needs the quotes.
     */
    static String columnLine(CdtColumn aColumn)
    {
        String name = aColumn.getName();
        requireWritable(name, "column name");
        if (name.indexOf('=') >= 0)
        {
            throw new IllegalArgumentException("column name " + name
                    + " holds '=', which the parser reads as a key=value, not a name");
        }
        StringBuilder sb = new StringBuilder(32);
        sb.append("col ").append(quoteAttribute(name));
        if (aColumn.getType() != null)
        {
            sb.append(" type=").append(aColumn.getType().token());
        }
        if (aColumn.getLabel() != null && !aColumn.getLabel().isBlank())
        {
            sb.append(" label=").append(quoteAttribute(aColumn.getLabel()));
        }
        if (aColumn.getLength() != null && aColumn.getLength() > 0)
        {
            sb.append(" length=").append(aColumn.getLength());
        }
        if (aColumn.getFormat() != null && !aColumn.getFormat().isBlank()
                && !isDefaultFormat(aColumn))
        {
            sb.append(" format=").append(quoteAttribute(aColumn.getFormat()));
        }
        if (aColumn.getCodelist() != null && !aColumn.getCodelist().isBlank())
        {
            sb.append(" codelist=").append(quoteAttribute(aColumn.getCodelist()));
        }
        for (Map.Entry<String, String> attr : aColumn.getAttrs().entrySet())
        {
            String key = attr.getKey();
            if (key.isEmpty() || !key.equals(quoteAttribute(key)))
            {
                throw new IllegalArgumentException("column " + name + ": attribute key [" + key
                        + "] is not a bare word the parser can read");
            }
            sb.append(' ').append(key).append('=').append(quoteAttribute(attr.getValue()));
        }
        return sb.toString();
    }


    /**
     * The parser auto-derives a format for a date / time / datetime column; writing that default
     * back would add a token the author never wrote.
     */
    private static boolean isDefaultFormat(CdtColumn aColumn)
    {
        if (aColumn.getType() == null)
        {
            return false;
        }
        return switch (aColumn.getType())
        {
        case DATE -> "DATE9.".equals(aColumn.getFormat());
        case TIME -> "TIME5.".equals(aColumn.getFormat());
        case DATETIME -> "DATETIME20.".equals(aColumn.getFormat());
        default -> false;
        };
    }


    /**
     * A data row from its encoded fields, {@code " | "}-separated, quoted where the parser needs
     * it.
     */
    static String rowLine(List<String> aRow)
    {
        StringBuilder sb = new StringBuilder(64);
        for (int i = 0; i < aRow.size(); i++)
        {
            if (i > 0)
            {
                sb.append(" | ");
            }
            sb.append(renderField(aRow.get(i)));
        }
        if (aRow.size() == 1 && sb.length() == 0)
        {
            // one empty field is byte-identical to a blank line, which the parser skips
            return "\"\"";
        }
        return sb.toString();
    }


    /**
     * One field in the encoding {@link CdtParser} produces: an unquoted missing sentinel stays bare
     * (that is what it means), an escaped literal is unescaped and quoted (the quotes are what keep
     * it a literal), everything else is quoted only when the characters need it.
     */
    static String renderField(@Nullable String aEncoded)
    {
        if (aEncoded == null)
        {
            throw new IllegalArgumentException("a cell is null; a missing value is a sentinel");
        }
        if (CdtValues.isMissingSentinel(aEncoded))
        {
            return aEncoded;
        }
        requireWritable(aEncoded, "cell");
        String text = aEncoded;
        boolean forceQuote = false;
        int i = 0;
        while (i < text.length() && text.charAt(i) == '\\')
        {
            i++;
        }
        if (i > 0 && CdtValues.isMissingSentinel(text.substring(i)))
        {
            // the parser's escape of a literal that looks like a sentinel: one backslash off, and
            // quoted so it reads back as the literal
            text = text.substring(1);
            forceQuote = true;
        }
        if (text.isEmpty())
        {
            return "";
        }
        boolean needQuote = forceQuote || CdtFence.isFence(text);
        if (!needQuote)
        {
            // String.trim() on the row drops every char up to U+0020 at its edges, so such a
            // char at a field's edge needs the quotes as much as whitespace does
            char first = text.charAt(0);
            char last = text.charAt(text.length() - 1);
            if (first <= ' ' || Character.isWhitespace(first) || first == '#' || first == '|'
                    || last <= ' ')
            {
                needQuote = true;
            }
        }
        if (!needQuote)
        {
            for (int k = 0; k < text.length(); k++)
            {
                char ch = text.charAt(k);
                if (ch == '|' || ch == '"' || ch == '\\')
                {
                    needQuote = true;
                    break;
                }
            }
        }
        if (!needQuote)
        {
            return text;
        }
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }


    /**
     * A refusal for text the parser cannot read back exactly: a line break of any kind (the parser
     * splits the file on {@code \R} before it looks at quotes) or trailing whitespace (the parser
     * strips it, inside quotes too).
     */
    private static void requireWritable(String aText, String aWhat)
    {
        for (int i = 0; i < aText.length(); i++)
        {
            if (isLineBreak(aText.charAt(i)))
            {
                throw new IllegalArgumentException(aWhat + " holds a line break (U+"
                        + String.format(Locale.ROOT, "%04X", (int) aText.charAt(i))
                        + "), which a .cdt line cannot carry: " + aText.strip());
            }
        }
        if (!aText.isEmpty() && Character.isWhitespace(aText.charAt(aText.length() - 1)))
        {
            throw new IllegalArgumentException(aWhat + " ends in trailing whitespace, which the"
                    + " parser strips: [" + aText + "]");
        }
    }


    /** A header attribute value, quoted the way {@link CdtWriter} quotes it. */
    static String quoteAttribute(String aValue)
    {
        requireWritable(aValue, "attribute value");
        if (aValue.isEmpty())
        {
            return "\"\"";
        }
        boolean needQuote = false;
        for (int i = 0; i < aValue.length(); i++)
        {
            char ch = aValue.charAt(i);
            if (ch <= ' ' || Character.isWhitespace(ch) || ch == '=' || ch == '"' || ch == '\'')
            {
                needQuote = true;
                break;
            }
        }
        if (!needQuote)
        {
            return aValue;
        }
        return "\"" + aValue.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // ---- locating ---------------------------------------------------------------------------


    /** The line terminator the block's header line uses ({@code \n} when it has none). */
    private String terminatorOf(DatasetBlock aBlock)
    {
        String header = lines.get(aBlock.headerLine());
        String body = stripTerminator(header);
        return body.length() == header.length() ? "\n" : header.substring(body.length());
    }


    /**
     * Whether a char is one of the {@code \R} line breaks {@link CdtParser} splits on ({@code \r\n}
     * is the pair of two of them).
     */
    static boolean isLineBreak(char aCh)
    {
        return aCh == '\n' || aCh == '\r' || aCh == '\u000B' || aCh == '\f' || aCh == '\u0085'
                || aCh == '\u2028' || aCh == '\u2029';
    }


    private static boolean endsWithTerminator(String aLine)
    {
        return !aLine.isEmpty() && isLineBreak(aLine.charAt(aLine.length() - 1));
    }


    /**
     * Split into lines, each keeping its own terminator; a final unterminated line is kept. The
     * split points are {@link CdtParser}'s {@code \R}: {@code \r\n} as one terminator, and each of
     * {@code \n}, {@code \r}, {@code U+000B}, {@code U+000C}, {@code U+0085}, {@code U+2028},
     * {@code U+2029} alone.
     */
    public static List<String> splitKeepingTerminators(String aText)
    {
        List<String> out = new ArrayList<>();
        int start = 0;
        int n = aText.length();
        int i = 0;
        while (i < n)
        {
            char c = aText.charAt(i);
            if (isLineBreak(c))
            {
                int end = c == '\r' && i + 1 < n && aText.charAt(i + 1) == '\n' ? i + 2 : i + 1;
                out.add(aText.substring(start, end));
                start = end;
                i = end;
            }
            else
            {
                i++;
            }
        }
        if (start < n)
        {
            out.add(aText.substring(start));
        }
        return out;
    }


    /** A line without its terminator (one terminator at most, as split). */
    private static String stripTerminator(String aLine)
    {
        if (aLine.endsWith("\r\n"))
        {
            return aLine.substring(0, aLine.length() - 2);
        }
        return endsWithTerminator(aLine) ? aLine.substring(0, aLine.length() - 1) : aLine;
    }


    /**
     * Walk the lines the way the parser does and record where each block sits.
     *
     * @param aLines
     *            the lines, split as {@link #splitKeepingTerminators(String)} splits
     * @param aNames
     *            the parser's dataset names in file order (one per block)
     */
    static List<DatasetBlock> locate(List<String> aLines, List<String> aNames)
    {
        List<DatasetBlock> out = new ArrayList<>();
        int i = 0;
        int n = aLines.size();
        while (i < n)
        {
            String trimmed = stripTerminator(aLines.get(i)).trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#"))
            {
                i++;
                continue;
            }
            if (!trimmed.startsWith("dataset"))
            {
                throw new CdtParseException(
                        "document:" + (i + 1) + ": expected 'dataset' line, got: " + trimmed);
            }
            if (out.size() >= aNames.size())
            {
                throw new IllegalStateException("document:" + (i + 1)
                        + ": more dataset blocks than the parser read (" + aNames.size() + ")");
            }
            String name = aNames.get(out.size());
            int header = i;
            i++;
            int fence = -1;
            while (i < n)
            {
                String t = stripTerminator(aLines.get(i)).trim();
                if (CdtFence.isFence(t))
                {
                    fence = i;
                    i++;
                    break;
                }
                i++;
            }
            if (fence < 0)
            {
                throw new CdtParseException(
                        "document:" + (i + 1) + ": missing fence line between metadata and data");
            }
            String opener = stripTerminator(aLines.get(fence)).trim();
            int rowsStart = i;
            int close = -1;
            while (i < n)
            {
                String t = stripTerminator(aLines.get(i)).trim();
                if (CdtFence.matches(opener, t))
                {
                    close = i;
                    i++;
                    break;
                }
                i++;
            }
            out.add(new DatasetBlock(name, header, header + 1, fence, rowsStart, close, i));
        }
        if (out.size() != aNames.size())
        {
            throw new IllegalStateException("document: " + out.size()
                    + " dataset block(s) located, the parser read " + aNames.size());
        }
        return Collections.unmodifiableList(out);
    }
}
