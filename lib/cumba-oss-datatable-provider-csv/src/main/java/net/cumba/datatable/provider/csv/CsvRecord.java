package net.cumba.datatable.provider.csv;

import java.text.MessageFormat;
import java.util.Objects;

import lombok.Builder;
import net.cumba.datatable.ExMsgs;
import net.cumba.datatable.help.CDT;
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
     * Per column: {@code true} where the cell is exactly {@code "."} AND was enclosed in quotation
     * marks in the file. {@code null} when no cell of the record is a quoted {@code "."} -- the
     * common case, which costs nothing. Otherwise exactly as long as {@link #values}. Owner ruling
     * K7 (2026-09-30): <i>"missing only if it is not in quotation marks"</i> -- an unquoted
     * {@code .} is the SAS missing, a quoted one is text.
     */
    private final boolean @Nullable [] quotedDots;

    /**
     * A record none of whose cells is a quoted {@code "."}.
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
     * @param quotedDots
     *            per column, {@code true} where the cell is a quoted {@code "."}; {@code null} when
     *            none is. When given, it must be exactly as long as {@code values}; it is copied.
     * @throws NullPointerException
     *             if {@code values} is {@code null}.
     * @throws IllegalArgumentException
     *             if {@code quotedDots} is given with a length other than {@code values}'.
     */
    @Builder
    public CsvRecord(String[] values, boolean @Nullable [] quotedDots)
    {
        this.values = Objects.requireNonNull(values, "values");
        if (quotedDots != null && quotedDots.length != values.length)
        {
            throw new IllegalArgumentException(
                    MessageFormat.format("{0} quote flags for a record of {1} values",
                            quotedDots.length, values.length));
        }
        this.quotedDots = quotedDots == null ? null : quotedDots.clone();
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
     * Supported missings are empty Strings or an unquoted dot (.) symbol; a quoted {@code "."} is
     * text (owner ruling K7).
     *
     * @param aColumn
     *            the (0-based) index of the column to test.
     * @return true if the value can be parsed as double using {@link Double#parseDouble(String)},
     *         is an empty String, an unquoted dot symbol or a null value, false otherwise.
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
        if (valStr.equals("."))
        {
            // K7: only an UNQUOTED "." is the SAS missing; a quoted "." is text.
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
     * Test if the cell is the SAS missing: exactly {@code "."} and NOT enclosed in quotation marks
     * in the file (owner ruling K7, 2026-09-30).
     *
     * @param aColumn
     *            the (0-based) index of the column to test.
     * @return {@code true} for an unquoted {@code .}, {@code false} for anything else -- including
     *         a quoted {@code "."}, which is the one-character text.
     * @throws IndexOutOfBoundsException
     *             in case the given column index is outside of the valid bounds<br/>
     *             ( <code>0 &lt;= aColumn &lt; getColumnCount()</code>)
     */
    public boolean isUnquotedDot(int aColumn) throws IndexOutOfBoundsException
    {
        return ".".equals(getValue(aColumn)) && !isQuoted(aColumn);
    }


    private boolean isQuoted(int aColumn)
    {
        boolean[] q = quotedDots;
        return q != null && q[aColumn];
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
