package net.cumba.datatable.impl;

import java.nio.charset.StandardCharsets;

import lombok.Getter;
import net.cumba.datatable.AbstractDataTableColumn;
import net.cumba.datatable.ExMsgs;
import net.cumba.datatable.IDataTableColumn;
import net.cumba.datatable.impl.databuffer.DataBufferFactory;
import net.cumba.datatable.impl.databuffer.IDataBuffer;
import net.cumba.datatable.values.DataValueMissing;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.IDataValue;
import net.cumba.datatable.values.MissingValue;
import org.jspecify.annotations.Nullable;

/**
 * An {@link IDataTableColumn} implementation that stores its data in a local {@link IDataBuffer}.
 * Values are added during table construction and the buffer is compressed on {@link #complete()}.
 * This column owns its data and does not reference back to the table.
 */
public class CachedDataTableColumn extends AbstractDataTableColumn
{

    /**
     * The data buffer that is used to store the values.
     */
    @Getter
    protected IDataBuffer dataBuffer;

    /**
     * The number of values available in this column.
     */
    protected int valueCount;

    @Getter
    private final DataValueType type;

    /**
     * Maximum observed UTF-8 byte length across the values of this column. Populated by
     * {@link #complete()} for {@link DataValueType#STRING} columns; stays at {@code 0} for
     * non-string columns or when the column holds no data. Used by the parser layer to reconcile
     * the declared column length with the actual values so metadata never under-reports length.
     */
    @Getter
    private int maxValueLength;

    public CachedDataTableColumn(int aIndex, DataValueType aType)
    {
        super(aIndex);
        dataBuffer = DataBufferFactory.get().createColumnBuffer(aType);
        type = aType;
    }


    public void setExpectedSize(int aLength)
    {
        dataBuffer.setExpectedSize(aLength);
    }


    @Override
    public long getRowCount()
    {
        return valueCount;
    }


    public void addElement(@Nullable Object aElement)
    {
        dataBuffer.setValue(valueCount, aElement);
        valueCount++;
    }


    public void addElement(double aElement)
    {
        dataBuffer.setValue(valueCount, aElement);
        valueCount++;
    }


    public void addElement(long aElement)
    {
        dataBuffer.setValue(valueCount, aElement);
        valueCount++;
    }


    public void setElement(int aIndex, @Nullable Object aElement)
    {
        dataBuffer.setValue(aIndex, aElement);
        valueCount = Math.max(valueCount, aIndex + 1);
    }


    public void setElement(int aIndex, double aElement)
    {
        dataBuffer.setValue(aIndex, aElement);
        valueCount = Math.max(valueCount, aIndex + 1);
    }


    public void setElement(int aIndex, long aElement)
    {
        dataBuffer.setValue(aIndex, aElement);
        valueCount = Math.max(valueCount, aIndex + 1);
    }


    public long ensureValidRow(long aRow) throws IndexOutOfBoundsException
    {
        long rc = getRowCount();
        if (aRow < 0 || aRow >= rc)
        {
            throw new IndexOutOfBoundsException(ExMsgs.indexOutOfBounds("row", aRow, 0, rc));
        }
        return aRow;
    }


    public void complete(long aRowCount)
    {
        complete();
        setTableRowCount(aRowCount);
    }


    public void complete()
    {
        dataBuffer.trimToSize();

        if (type == DataValueType.STRING)
        {
            maxValueLength = computeMaxValueLength();
        }
    }


    /**
     * Scan the buffer for the largest UTF-8 byte length across its values. {@link MissingValue}
     * entries are rendered via {@link Object#toString()} (matches how they appear in reports /
     * exports). {@code null} contributes length 0.
     */
    private int computeMaxValueLength()
    {
        int max = 0;
        int n = dataBuffer.size();
        for (int i = 0; i < n; i++)
        {
            int len = byteLength(dataBuffer.getValue(i));
            if (len > max)
            {
                max = len;
            }
        }
        return max;
    }


    /**
     * UTF-8 byte length of the given value. {@code null} → 0; {@link MissingValue} and other
     * non-string values are rendered via {@link Object#toString()} first. Matches SAS / DSJ
     * semantics (byte count, not UTF-16 code-unit count).
     */
    private static int byteLength(@Nullable Object aValue)
    {
        if (aValue == null)
        {
            return 0;
        }
        String s;
        if (aValue instanceof String str)
        {
            s = str;
        }
        else
        {
            s = aValue.toString();
        }
        if (s.isEmpty())
        {
            return 0;
        }
        // Fast path: if all characters are ASCII, byte count == char count.
        int len = s.length();
        boolean ascii = true;
        for (int i = 0; i < len; i++)
        {
            if (s.charAt(i) > 0x7F)
            {
                ascii = false;
                break;
            }
        }
        if (ascii)
        {
            return len;
        }
        return s.getBytes(StandardCharsets.UTF_8).length;
    }


    public void setTableRowCount(long aRowCount)
    {
        if (aRowCount > Integer.MAX_VALUE)
        {
            valueCount = Integer.MAX_VALUE;
        }
        else
        {
            valueCount = Math.max(valueCount, (int) aRowCount);
        }
    }


    @Override
    public @Nullable Object getValue(long aRow) throws IndexOutOfBoundsException
    {
        int idx = Math.toIntExact(ensureValidRow(aRow));

        // Buffer may have fewer rows than the column claims (short-buffer edge case).
        if (dataBuffer.size() <= idx)
        {
            return MissingValue.MIS_ERROR;
        }
        // The buffer's getValue already returns MissingValue instances directly for cells
        // flagged as missing — no need to double-check here.
        return dataBuffer.getValue(idx);
    }


    @Override
    public int hashCodeAt(long aRow) throws IndexOutOfBoundsException
    {
        int idx = Math.toIntExact(ensureValidRow(aRow));
        if (dataBuffer.size() <= idx)
        {
            return MissingValue.MIS_ERROR.hashCodeStable();
        }
        return dataBuffer.hashCodeAt(idx);
    }


    @Override
    public boolean isMissingOrNull(long aRow) throws IndexOutOfBoundsException
    {
        int idx = Math.toIntExact(ensureValidRow(aRow));
        if (dataBuffer.size() <= idx)
        {
            return true;
        }
        return dataBuffer.isMissing(idx);
    }


    /**
     * {@inheritDoc}
     * <p>
     * Type-dispatched so the hot path never allocates:
     * </p>
     * <ul>
     * <li><b>Non-STRING columns</b> — an empty string is impossible in a double / long / boolean
     * buffer, so blankness collapses to <em>missing</em> and the answer comes from
     * {@link net.cumba.datatable.impl.databuffer.IDataBuffer#isMissing(int)}, which the numeric
     * buffers implement against their primitive storage. {@code getValue} is never called, so the
     * {@code Double} / {@code Long} box is never created.</li>
     * <li><b>STRING columns</b> — the raw stored object is tested directly. It is the interned
     * {@code String} the loader put there, so no {@code IDataValue} is built and
     * {@code getValueAsString()} (which calls {@code toString()}) is never reached.</li>
     * </ul>
     */
    @Override
    public boolean isEmptyOrMissing(long aRow) throws IndexOutOfBoundsException
    {
        int idx = Math.toIntExact(ensureValidRow(aRow));
        if (dataBuffer.size() <= idx)
        {
            return true;
        }
        if (type != DataValueType.STRING && type != DataValueType.OTHER)
        {
            // No string can live in these buffers ⇒ blank == missing, and isMissing reads the
            // primitive storage without boxing.
            return dataBuffer.isMissing(idx);
        }
        Object v = dataBuffer.getValue(idx);
        return v == null || v instanceof MissingValue || (v instanceof String s && s.isEmpty());
    }


    @Override
    public IDataValue getDataValue(long aRow) throws IndexOutOfBoundsException
    {
        int idx = Math.toIntExact(ensureValidRow(aRow));

        // Buffer may have fewer rows than the column claims (short-buffer edge case).
        if (dataBuffer.size() <= idx)
        {
            return new DataValueMissing(MissingValue.MIS_ERROR);
        }
        return dataBuffer.getDataValue(idx, type);
    }

}
