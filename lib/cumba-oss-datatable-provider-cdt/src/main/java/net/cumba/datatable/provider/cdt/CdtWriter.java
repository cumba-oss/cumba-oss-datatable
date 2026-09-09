package net.cumba.datatable.provider.cdt;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Locale;

import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.IDataValue;
import net.cumba.datatable.values.MissingValue;

import org.jspecify.annotations.Nullable;

/**
 * Serialises an {@link IDataTable} (or several of them) back to the {@code .cdt} text format parsed
 * by {@link CdtParser}.
 *
 * <p>
 * Every dataset block written by this class is closed with its opening fence line so appending
 * another dataset only requires opening the file for append and calling
 * {@link #writeDataset(IDataTable, String, String, String, Writer)} again.
 * </p>
 */
public final class CdtWriter
{

    /** The fence used to open and close every data block. */
    public static final String FENCE = CdtFence.DEFAULT;

    private CdtWriter()
    {
    }


    /**
     * Write the given table to {@code aPath}, overwriting any existing content. The dataset name is
     * taken from {@code aTable.getMetaData().getName()}.
     */
    public static void write(IDataTable aTable, Path aPath) throws IOException
    {
        write(aTable, null, null, null, aPath, false);
    }


    /**
     * Write or append the given table to {@code aPath}.
     *
     * @param aTable
     *            the table to serialise.
     * @param aDatasetName
     *            the dataset name; {@code null} falls back to the table's name.
     * @param aDatasetLabel
     *            the dataset label; {@code null} falls back to the table's label.
     * @param aDatasetClass
     *            the dataset class attribute; {@code null} to omit.
     * @param aPath
     *            the target path.
     * @param aAppend
     *            when {@code true} and the file already exists, append as an additional dataset
     *            block instead of replacing the file.
     */
    public static void write(IDataTable aTable, @Nullable String aDatasetName,
            @Nullable String aDatasetLabel, @Nullable String aDatasetClass, Path aPath,
            boolean aAppend)
        throws IOException
    {
        StandardOpenOption[] opts;
        boolean appending = aAppend && Files.exists(aPath);
        if (appending)
        {
            opts = new StandardOpenOption[]
            {
                    StandardOpenOption.WRITE, StandardOpenOption.APPEND
            };
        }
        else
        {
            opts = new StandardOpenOption[]
            {
                    StandardOpenOption.WRITE, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            };
        }

        try (Writer w = Files.newBufferedWriter(aPath, StandardCharsets.UTF_8, opts))
        {
            if (appending)
            {
                // Separate the appended block from the previous one with a blank line
                // so the result is visually clean. The parser tolerates blank lines
                // between blocks.
                w.write('\n');
            }
            writeDataset(aTable, aDatasetName, aDatasetLabel, aDatasetClass, w);
        }
    }


    /**
     * Serialise the given table to a string in {@code .cdt} format.
     */
    public static String toString(IDataTable aTable)
    {
        StringBuilder sb = new StringBuilder(256);
        try
        {
            writeDataset(aTable, null, null, null, sb);
        }
        catch (IOException ex)
        {
            // StringBuilder.append doesn't throw, so this is unreachable.
            throw new IllegalStateException(ex);
        }
        return sb.toString();
    }


    /**
     * Write one dataset block to the given {@link Appendable}. The block ends with a closing fence
     * line.
     */
    public static void writeDataset(IDataTable aTable, @Nullable String aDatasetName,
            @Nullable String aDatasetLabel, @Nullable String aDatasetClass, Appendable aOut)
        throws IOException
    {
        DataTableMeta meta = asDataTableMeta(aTable.getMetaData());
        String name = firstNonBlank(aDatasetName, meta.getName(), "DATA");
        String label = firstNonBlank(aDatasetLabel, meta.getLabel());
        String klass = firstNonBlank(aDatasetClass, asString(meta.getMetaData("class")));

        writeDatasetHeader(aOut, name, label, klass, meta);
        writeColumns(aOut, meta);
        aOut.append(FENCE).append('\n');
        writeRows(aOut, aTable, meta);
        aOut.append(FENCE).append('\n');
    }

    // ---- header / columns / rows --------------------------------------------------


    private static void writeDatasetHeader(Appendable aOut, @Nullable String aName,
            @Nullable String aLabel, @Nullable String aClass, DataTableMeta aMeta)
        throws IOException
    {
        aOut.append("dataset ").append(safeIdent(aName));
        if (aLabel != null && !aLabel.isBlank() && !aLabel.equals(aName))
        {
            aOut.append(" label=").append(quoteIfNeeded(aLabel));
        }
        if (aClass != null && !aClass.isBlank())
        {
            aOut.append(" class=").append(quoteIfNeeded(aClass));
        }
        // Forward dataset-level metadata keys that the rule engine may read as key=value
        // header attrs so they round-trip through CdtLoader. Skip built-ins already emitted
        // above, column-qualified keys (contain '.'), and presentation keys (any uppercase
        // letter) introduced by SAS / DatasetJSON loaders — the engine does not read them
        // and emitting them wastes ~10× the file size on large fixtures.
        for (String key : aMeta.getMetaDataKeys())
        {
            if (key == null || "class".equals(key) || "label".equals(key)
                    || "dataset_class".equals(key) || key.indexOf('.') >= 0 || hasUpperCase(key))
            {
                continue;
            }
            Object val = aMeta.getMetaData(key);
            if (val == null)
            {
                continue;
            }
            aOut.append(' ').append(safeIdent(key)).append('=')
                    .append(quoteIfNeeded(val.toString()));
        }
        aOut.append('\n');
    }


    private static void writeColumns(Appendable aOut, DataTableMeta aMeta) throws IOException
    {
        DataTableColumnMeta[] cols = aMeta.getColumns();
        for (DataTableColumnMeta c : cols)
        {
            aOut.append("col ").append(safeIdent(c.getName()));

            CdtKind kind = classifyKind(c.getType(), c.getDisplayFormat());
            if (c.getType() != null)
            {
                aOut.append(" type=").append(kind.token());
            }

            String label = c.getLabel();
            if (label != null && !label.isBlank() && !label.equals(c.getName()))
            {
                aOut.append(" label=").append(quoteIfNeeded(label));
            }

            int length = c.getLength();
            if (length > 0)
            {
                aOut.append(" length=").append(Integer.toString(length));
            }

            String fmt = c.getDisplayFormat();
            if (fmt != null && !fmt.isBlank())
            {
                aOut.append(" format=").append(quoteIfNeeded(fmt));
            }

            String nativeType = c.getNativeType();
            if (nativeType != null && !nativeType.isBlank() && !nativeType.equals(kind.token()))
            {
                aOut.append(" nativeType=").append(quoteIfNeeded(nativeType));
            }

            // Emit every custom metadata entry as a generic key=value attribute.
            // Codelist (stored under COLUMN_META_CODELIST) comes out here too.
            for (String key : c.getMetaDataKeys())
            {
                Object val = c.getMetaData(key);
                if (val == null)
                {
                    continue;
                }
                aOut.append(' ').append(key).append('=').append(quoteIfNeeded(val.toString()));
            }

            aOut.append('\n');
        }
    }


    private static void writeRows(Appendable aOut, IDataTable aTable, DataTableMeta aMeta)
        throws IOException
    {
        int colCount = aMeta.getColumnCount();
        long rowCount = aTable.getRowCount();
        for (long r = 0; r < rowCount; r++)
        {
            boolean allNull = true;
            StringBuilder row = new StringBuilder(64);
            for (int c = 0; c < colCount; c++)
            {
                if (c > 0)
                {
                    row.append(" | ");
                }
                Object raw = extractRaw(aTable.getValue(r, c));
                DataTableColumnMeta cm = aMeta.getColumn(c);
                String rendered = renderValue(raw, cm.getType(), cm.getDisplayFormat(), r,
                        cm.getName());
                if (!rendered.isEmpty())
                {
                    allNull = false;
                }
                row.append(quoteFieldIfNeeded(rendered));
            }
            if (allNull && colCount > 1)
            {
                aOut.append(".\n");
            }
            else
            {
                aOut.append(row).append('\n');
            }
        }
    }


    /**
     * Quote a rendered data field when needed so the parser can recover the exact characters: any
     * field that begins with whitespace, contains {@code |}, contains {@code "} or {@code \}, or is
     * exactly {@code .} (the all-null sentinel) is wrapped in double quotes with backslash
     * escaping.
     */
    private static String quoteFieldIfNeeded(String aValue)
    {
        if (aValue.isEmpty())
        {
            return "";
        }
        boolean needQuote = ".".equals(aValue);
        if (!needQuote)
        {
            char first = aValue.charAt(0);
            // First-column line starts: `#` is a comment, `|` would split early.
            // Quoting any leading-`#` / leading-`|` value keeps round-trip safe
            // regardless of the column's position.
            if (Character.isWhitespace(first) || first == '#' || first == '|')
            {
                needQuote = true;
            }
        }
        if (!needQuote)
        {
            for (int i = 0; i < aValue.length(); i++)
            {
                char ch = aValue.charAt(i);
                if (ch == '|' || ch == '"' || ch == '\\')
                {
                    needQuote = true;
                    break;
                }
            }
        }
        if (!needQuote)
        {
            return aValue;
        }
        String esc = aValue.replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + esc + "\"";
    }


    private static @Nullable Object extractRaw(@Nullable Object aValue)
    {
        if (aValue == null)
        {
            return null;
        }
        if (aValue instanceof MissingValue)
        {
            return null;
        }
        if (aValue instanceof IDataValue dv)
        {
            if (dv.isMissingOrInvalid())
            {
                return null;
            }
            return dv.getValue();
        }
        return aValue;
    }

    // ---- value rendering ----------------------------------------------------------


    private static String renderValue(@Nullable Object aValue, @Nullable DataValueType aType,
            @Nullable String aFormat, long aRowIdx, String aColName)
        throws IOException
    {
        if (aValue == null)
        {
            return "";
        }
        if (aType == null)
        {
            return checkNoControlChars(aValue.toString(), aRowIdx, aColName);
        }
        CdtKind kind = classifyKind(aType, aFormat);
        return switch (kind)
        {
        case DATE -> renderDate(aValue);
        case TIME -> renderTime(aValue, aRowIdx, aColName);
        case DATETIME -> renderDateTime(aValue);
        case NUM -> renderNumber(aValue);
        case CHAR -> checkNoControlChars(aValue.toString(), aRowIdx, aColName);
        };
    }


    /**
     * CDT is a line-oriented format: row terminators are line breaks. A CHAR field that contains an
     * embedded CR or LF cannot round-trip, so the writer rejects it rather than silently producing
     * corrupt output.
     */
    private static String checkNoControlChars(String aValue, long aRowIdx, String aColName)
        throws IOException
    {
        for (int i = 0; i < aValue.length(); i++)
        {
            char ch = aValue.charAt(i);
            if (ch == '\r' || ch == '\n')
            {
                throw new IOException(
                        "CDT export: row %d column %s contains unsupported control character (CR or LF). CDT format does not support multiline cells."
                                .formatted(aRowIdx, aColName));
            }
        }
        return aValue;
    }


    private static String renderDate(Object aValue)
    {
        if (aValue instanceof Number n)
        {
            LocalDate d = CdtValues.SAS_EPOCH.plusDays(n.longValue());
            return d.format(CdtValues.DATE_FMT);
        }
        return aValue.toString();
    }


    private static String renderTime(Object aValue, long aRowIdx, String aColName)
        throws IOException
    {
        if (aValue instanceof Number n)
        {
            long seconds = n.longValue();
            // F-prov-04: a SAS TIME value is not constrained to [0, 86400) - elapsed times
            // beyond 24 h and negative durations are legal in SAS, but the CDT Time field
            // (HH:mm:ss) cannot represent them. The old "h % 24" silently discarded whole
            // days, and a negative value escaped as an unchecked DateTimeException; both
            // cases are refused explicitly instead, the same way checkNoControlChars
            // refuses a CR/LF.
            if (seconds < 0 || seconds >= 86400)
            {
                throw new IOException(
                        "CDT export: row %d column %s holds the TIME value %s which is outside the representable range [0, 86400) seconds. CDT Time fields cannot represent negative times or times of 24 hours and more."
                                .formatted(aRowIdx, aColName, aValue));
            }
            int h = (int) (seconds / 3600);
            int rem = (int) (seconds - h * 3600L);
            int m = rem / 60;
            int s = rem - m * 60;
            return LocalTime.of(h, m, s).format(CdtValues.TIME_FMT);
        }
        return aValue.toString();
    }


    private static String renderDateTime(Object aValue)
    {
        if (aValue instanceof Number n)
        {
            long seconds = n.longValue();
            LocalDateTime dt = CdtValues.SAS_EPOCH.atStartOfDay().plusSeconds(seconds);
            return dt.format(CdtValues.DATETIME_FMT);
        }
        return aValue.toString();
    }


    private static String renderNumber(Object aValue)
    {
        if (aValue instanceof Double d)
        {
            if (Double.isNaN(d))
            {
                return "";
            }
            // F-prov-03: the integral-value shortcut may only take the long path when the
            // value is genuinely representable as a long - Java's double->long narrowing
            // SATURATES, so e.g. 1.0E30 would silently be written as Long.MAX_VALUE. 0x1p63
            // is 2^63: every integral double d with -2^63 <= d < 2^63 fits a long exactly
            // (the largest double below 2^63 is 2^63 - 1024).
            if (d == Math.floor(d) && !Double.isInfinite(d) && d >= -0x1p63 && d < 0x1p63)
            {
                return Long.toString(d.longValue());
            }
            return d.toString();
        }
        if (aValue instanceof Number n)
        {
            return n.toString();
        }
        return aValue.toString();
    }

    private enum CdtKind
    {

        CHAR, NUM, DATE, TIME, DATETIME;

        String token()
        {
            return switch (this)
            {
            case CHAR -> "Char";
            case NUM -> "Num";
            case DATE -> "Date";
            case TIME -> "Time";
            case DATETIME -> "DateTime";
            };
        }
    }

    private static CdtKind classifyKind(DataValueType aType, @Nullable String aFormat)
    {
        if (aType == DataValueType.STRING)
        {
            return CdtKind.CHAR;
        }
        if (aType == DataValueType.LONG || aType == DataValueType.DOUBLE)
        {
            if (aFormat != null)
            {
                String f = aFormat.toUpperCase(Locale.ROOT);
                // F-prov-05: DATEAMPM is a SAS *datetime* format (values are seconds since
                // 1960-01-01) whose name starts with "DATE" - it must be claimed here, in
                // the DATETIME arm, before the DATE prefix arm below can swallow it.
                if (f.startsWith("DATETIME") || f.startsWith("DATEAMPM") || f.startsWith("NLDATM")
                        || f.startsWith("E8601DT") || f.startsWith("IS8601DT")
                        || f.startsWith("B8601DT"))
                {
                    return CdtKind.DATETIME;
                }
                if (f.startsWith("TIME") || f.startsWith("HHMM") || f.startsWith("E8601TM")
                        || f.startsWith("IS8601TM") || f.startsWith("B8601TM"))
                {
                    return CdtKind.TIME;
                }
                if (f.startsWith("DATE") || f.startsWith("DDMMYY") || f.startsWith("MMDDYY")
                        || f.startsWith("YYMMDD") || f.startsWith("E8601DA")
                        || f.startsWith("IS8601DA") || f.startsWith("B8601DA")
                        || f.startsWith("MONYY") || f.startsWith("WEEKDATE"))
                {
                    return CdtKind.DATE;
                }
            }
            return CdtKind.NUM;
        }
        return CdtKind.CHAR;
    }

    // ---- quoting / identifiers ----------------------------------------------------


    private static String quoteIfNeeded(String aValue) throws IOException
    {
        if (aValue.isEmpty())
        {
            return "\"\"";
        }
        boolean needQuote = false;
        for (int i = 0; i < aValue.length(); i++)
        {
            char ch = aValue.charAt(i);
            // CDT header attributes are line-oriented; CR/LF would break out of the line
            // and produce an unparseable file. Reject rather than silently corrupt.
            if (ch == '\r' || ch == '\n')
            {
                throw new IOException(
                        "CDT export: header attribute value contains unsupported control character (CR or LF). CDT format does not support multiline labels, formats, or metadata.");
            }
            if (Character.isWhitespace(ch) || ch == '=' || ch == '"' || ch == '\'')
            {
                needQuote = true;
            }
        }
        if (!needQuote)
        {
            return aValue;
        }
        String esc = aValue.replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + esc + "\"";
    }


    private static String safeIdent(@Nullable String aName) throws IOException
    {
        if (aName == null || aName.isEmpty())
        {
            return "_";
        }
        return quoteIfNeeded(aName);
    }


    private static boolean hasUpperCase(String aName)
    {
        for (int i = 0; i < aName.length(); i++)
        {
            if (Character.isUpperCase(aName.charAt(i)))
            {
                return true;
            }
        }
        return false;
    }

    // ---- meta helpers -------------------------------------------------------------


    private static DataTableMeta asDataTableMeta(net.cumba.datatable.IDataTableMeta aMeta)
    {
        if (aMeta instanceof DataTableMeta m)
        {
            return m;
        }
        return DataTableMeta.cloneFrom(aMeta);
    }


    private static @Nullable String asString(@Nullable Object aValue)
    {
        return aValue instanceof String s ? s : null;
    }


    private static @Nullable String firstNonBlank(@Nullable String... aValues)
    {
        if (aValues == null)
        {
            return null;
        }
        for (String v : aValues)
        {
            if (v != null && !v.isBlank())
            {
                return v;
            }
        }
        return null;
    }
}
