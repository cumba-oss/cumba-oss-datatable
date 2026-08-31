package net.cumba.datatable.provider.csv;

import java.text.MessageFormat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NonNull;
import net.cumba.datatable.ExMsgs;
import net.cumba.datatable.help.CDT;

/**
 * A simple wrapper structure for a single record of a CSV file.
 */
@AllArgsConstructor
@Builder
public class CsvRecord
{

    /**
     * The column values of this record.
     */
    @NonNull
    private final String[] values;

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
     * Supported missings are empty Strings or the dot (.) symbol.
     *
     * @param aColumn
     *            the (0-based) index of the column to test.
     * @return true if the value can be parsed as double using {@link Double#parseDouble(String)},
     *         is an empty String, the dot symbol or a null value, false otherwise.
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
            return true;
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
