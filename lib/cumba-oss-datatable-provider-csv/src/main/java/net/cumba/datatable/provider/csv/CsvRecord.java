package net.cumba.datatable.provider.csv;

import java.text.MessageFormat;
import java.util.Objects;

import lombok.Builder;
import net.cumba.datatable.ExMsgs;
import net.cumba.datatable.help.CDT;
import net.cumba.datatable.values.MissingValue;
import org.jspecify.annotations.Nullable;

/**
 * A simple wrapper structure for a single record of a CSV file.
 */
public class CsvRecord
{

    /**
     * The column values of this record.
     */
    private final String[] values;

    /**
     * Per column: {@code true} where the cell was enclosed in quotation marks in the file;
     * {@code null} when none was (or none that matters -- see below). Otherwise exactly as long as
     * {@link #values}. {@link CsvRecordReader} guarantees the flag for every missing-sentinel cell
     * ({@code .}, {@code ._}, {@code .A}..{@code .Z}) of every record, and for EVERY cell of a
     * type-guessing sample record ({@link CsvRecordReader#nextWithQuoteFlags()}); a non-sentinel
     * cell of a later record may carry {@code false} although quoted, which no reading past the
     * sample depends on. Owner rulings 2026-09-30: K7 <i>"missing only if it is not in quotation
     * marks"</i> and K7b <i>"Agree to special missings as long as they are not in quotation
     * marks"</i> -- an unquoted sentinel is the SAS missing, a quoted one is text; K7c <i>"yes any
     * quoted value is always a string"</i> -- a quoted cell in the sample types its column STRING.
     */
    private final boolean @Nullable [] quoted;

    /**
     * A record none of whose cells is quoted.
     *
     * @param aValues
     *            the column values; must not be {@code null}.
     */
    public CsvRecord(String[] aValues)
    {
        this(aValues, null);
    }


    /**
     * A record with per-column quote flags.
     *
     * @param values
     *            the column values; must not be {@code null}.
     * @param quoted
     *            per column, {@code true} where the cell was quoted; {@code null} when none was.
     *            When given, it must be exactly as long as {@code values}; it is copied.
     * @throws NullPointerException
     *             if {@code values} is {@code null}.
     * @throws IllegalArgumentException
     *             if {@code quoted} is given with a length other than {@code values}'.
     */
    @Builder
    public CsvRecord(String[] values, boolean @Nullable [] quoted)
    {
        this.values = Objects.requireNonNull(values, "values");
        if (quoted != null && quoted.length != values.length)
        {
            throw new IllegalArgumentException(MessageFormat.format(
                    "{0} quote flags for a record of {1} values", quoted.length, values.length));
        }
        this.quoted = quoted == null ? null : quoted.clone();
    }


    /**
     * Returns a defensive copy of the column values array.
     *
     * @return a copy of the values array.
     */
    public String[] getValues()
    {
        return values.clone();
    }


    /**
     * Returns the number of columns in this record.
     *
     * @return the number of columns in this record.
     */
    public int getColumnCount()
    {
        return values.length;
    }


    /**
     * Retrieve the value the requested column as string.
     *
     * @param aColumn
     *            the (0-based) index of the column to get the value for.
     * @return the value of the record for the column with the given index.
     * @throws IndexOutOfBoundsException
     *             in case the given column index is outside of the valid bounds<br/>
     *             ( <code>0 &lt;= aColumn &lt; getColumnCount()</code>)
     */
    public String getValue(int aColumn) throws IndexOutOfBoundsException
    {
        if (aColumn < 0 || aColumn >= values.length)
        {
            throw new IndexOutOfBoundsException(
                    ExMsgs.indexOutOfBounds("column", aColumn, 0, values.length));
        }
        String res = values[aColumn];
        return res != null ? res : "";
    }


    /**
     * Retrieve the value of the given column as a double value.
     *
     * @param aColumn
     *            the (0-based) index of the column to get the value for.
     * @return the value as a double parsed by {@link Double#parseDouble(String)}, in case the value
     *         can not be returned as a double, {@link Double#NaN} is returned.
     * @throws IndexOutOfBoundsException
     *             in case the given column index is outside of the valid bounds<br/>
     *             ( <code>0 &lt;= aColumn &lt; getColumnCount()</code>)
     */
    public double getDoubleValue(int aColumn) throws IndexOutOfBoundsException
    {
        String valStr = getValue(aColumn);

        try
        {
            return Double.parseDouble(valStr);
        }
        catch (NumberFormatException _)
        {
            return Double.NaN;
        }
    }


    /**
     * Retrieve the value of the given column as a long value.
     *
     * @param aColumn
     *            the (0-based) index of the column to get the value for.
     * @return the value as a long.
     * @throws IndexOutOfBoundsException
     *             in case the given column index is outside of the valid bounds<br/>
     *             ( <code>0 &lt;= aColumn &lt; getColumnCount()</code>)
     * @throws IllegalStateException
     *             in case the value can not be parsed using {@link Long#parseLong(String)}.
     */
    public long getLongValue(int aColumn) throws IndexOutOfBoundsException, IllegalStateException
    {
        String valStr = getValue(aColumn);

        try
        {
            return Long.parseLong(valStr);
        }
        catch (NumberFormatException ex)
        {
            String msg = MessageFormat.format("Value {0} in column {1} is not a valid long value!",
                    valStr, aColumn);
            throw new IllegalStateException(msg, ex);
        }
    }


    /**
     * Test if the value at the given column is a double value or a supported missing.<br/>
     * Supported missings are empty Strings and an unquoted missing sentinel ({@code .}, {@code ._},
     * {@code .A}..{@code .Z}); a quoted sentinel is text (owner rulings K7).
     *
     * @param aColumn
     *            the (0-based) index of the column to test.
     * @return true if the value can be parsed as double using {@link Double#parseDouble(String)},
     *         is an empty String, an unquoted missing sentinel or a null value, false otherwise.
     * @throws IndexOutOfBoundsException
     *             in case the given column index is outside of the valid bounds<br/>
     *             ( <code>0 &lt;= aColumn &lt; getColumnCount()</code>)
     */
    public boolean isDoubleOrMissing(int aColumn) throws IndexOutOfBoundsException
    {
        String valStr = getValue(aColumn);

        if (CDT.isBlankOrNull(valStr))
        {
            return true;
        }
        if (isMissingSentinel(valStr))
        {
            // K7: only an UNQUOTED sentinel is the SAS missing; a quoted one is text.
            return !isQuoted(aColumn);
        }

        try
        {
            Double.parseDouble(valStr);
            return true;
        }
        catch (NumberFormatException _)
        {
            return false;
        }
    }


    /**
     * The SAS missing value an unquoted missing sentinel stands for: {@code .} is
     * {@link MissingValue#MIS}, {@code ._} is {@link MissingValue#MIS__} and {@code .A}..{@code .Z}
     * are {@link MissingValue#MIS_A}..{@link MissingValue#MIS_Z} -- the {@code .cdt} grammar
     * ({@code CdtValues}), so a lower-case {@code .a} is ordinary text. Owner rulings K7
     * (2026-09-30): only when NOT enclosed in quotation marks.
     *
     * @param aColumn
     *            the (0-based) index of the column to test.
     * @return the missing value, or {@code null} when the cell is not an unquoted sentinel.
     * @throws IndexOutOfBoundsException
     *             in case the given column index is outside of the valid bounds<br/>
     *             ( <code>0 &lt;= aColumn &lt; getColumnCount()</code>)
     */
    public @Nullable MissingValue getUnquotedMissing(int aColumn) throws IndexOutOfBoundsException
    {
        String valStr = getValue(aColumn);
        if (!isMissingSentinel(valStr) || isQuoted(aColumn))
        {
            return null;
        }
        return MissingValue.forValue(valStr, null);
    }


    /**
     * Test if the cell was enclosed in quotation marks -- exact for every missing-sentinel cell,
     * and for every cell of a type-guessing sample record (see {@link #quoted}). Package-private
     * for that reason: past the sample a quoted non-sentinel cell answers {@code false}, so the
     * flag is no general "was quoted" answer and is read only by this package's typing code.
     *
     * @param aColumn
     *            the (0-based) index of the column to test.
     * @return {@code true} if the cell is known to have been quoted.
     * @throws IndexOutOfBoundsException
     *             in case the given column index is outside of the valid bounds<br/>
     *             ( <code>0 &lt;= aColumn &lt; getColumnCount()</code>)
     */
    boolean isQuoted(int aColumn) throws IndexOutOfBoundsException
    {
        if (aColumn < 0 || aColumn >= values.length)
        {
            throw new IndexOutOfBoundsException(
                    ExMsgs.indexOutOfBounds("column", aColumn, 0, values.length));
        }
        boolean[] q = quoted;
        return q != null && q[aColumn];
    }


    /**
     * Whether the text is exactly one of the 28 SAS missing sentinels: {@code .}, {@code ._} or
     * {@code .A}..{@code .Z} -- the grammar {@code CdtValues} reads, and nothing else.
     *
     * @param aText
     *            the cell text, may be {@code null}.
     * @return {@code true} for a sentinel.
     */
    static boolean isMissingSentinel(@Nullable String aText)
    {
        if (aText == null || aText.isEmpty() || aText.length() > 2 || aText.charAt(0) != '.')
        {
            return false;
        }
        if (aText.length() == 1)
        {
            return true;
        }
        char c = aText.charAt(1);
        return c == '_' || (c >= 'A' && c <= 'Z');
    }


    /**
     * Test if the value can be parsed as a long by {@link Long#parseLong(String)}.
     *
     * @param aColumn
     *            aColumn the (0-based) index of the column to test.
     * @return true if the value at the given column parses as a long.
     * @throws IndexOutOfBoundsException
     *             in case the given column index is outside of the valid bounds<br/>
     *             ( <code>0 &lt;= aColumn &lt; getColumnCount()</code>)
     */
    public boolean isLongValue(int aColumn) throws IndexOutOfBoundsException
    {
        String valStr = getValue(aColumn);
        try
        {
            Long.parseLong(valStr);
            return true;
        }
        catch (NumberFormatException _)
        {
            return false;
        }
    }

}
