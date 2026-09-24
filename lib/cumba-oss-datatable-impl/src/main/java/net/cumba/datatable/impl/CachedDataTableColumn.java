package net.cumba.datatable.impl;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import lombok.Getter;
import net.cumba.datatable.AbstractDataTableColumn;
import net.cumba.datatable.ExMsgs;
import net.cumba.datatable.IDataTableColumn;
import net.cumba.datatable.impl.databuffer.DataBufferDouble;
import net.cumba.datatable.impl.databuffer.DataBufferFactory;
import net.cumba.datatable.impl.databuffer.DataBufferInt;
import net.cumba.datatable.impl.databuffer.DataBufferLong;
import net.cumba.datatable.impl.databuffer.DataBufferObject;
import net.cumba.datatable.impl.databuffer.IDataBuffer;
import net.cumba.datatable.impl.databuffer.IDataBufferNumeric;
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
     * The buffers whose typed read is this repository's {@code AbstractDataBuffer.createDataValue}
     * UNCHANGED — the class overrides neither {@code getDataValue} nor {@code createDataValue} — so
     * a cell's data value is decided by its raw {@code getValue(idx)}. Derived for
     * {@link #presentStringValue(long)} and {@link #presentNumericValue(long)}
     * (PLAN-identity-safe-join-caches D6).
     *
     * <p>
     * ⚠ Derived from THIS repository's value creation, which differs from the internal twin's: here
     * the LONG arm has no NaN check, so a boxed NaN in a LONG column reads back as a PRESENT
     * {@code DataValueLong(0)} (the internal twin answers {@code MIS_UNKNOWN}), and the STRING arm
     * has none either ({@code DataValueString("NaN")}). The fast reads below answer every NaN the
     * slow way, which is correct under either rule.
     * </p>
     *
     * <p>
     * Dispatch is on the EXACT class ({@code getClass()}), never {@code instanceof}: a subclass may
     * override the typed read, and a buffer class that was not analysed here must fall back to the
     * slow path. Package-private so the test can check that every concrete {@link IDataBuffer} is
     * either handled here or knowingly excluded.
     * </p>
     */
    static final Set<Class<? extends IDataBuffer>> FAST_OBJECT_BUFFERS = Set
            .of(DataBufferObject.class);

    /**
     * The buffers whose typed read is this repository's
     * {@code AbstractNumericDataBuffer.createDataValue} UNCHANGED: {@code isMissing} is tested
     * FIRST for every type (so a {@code DataBufferLong} / {@code DataBufferInt} sentinel is missing
     * even though its {@code getValueAsDouble} is an ordinary number), then DOUBLE is
     * {@code getValueAsDouble} and LONG is {@code getValueAsLong}. Only the numeric arms are fast
     * here: the raw value of these three buffers is never a {@code String}, so a STRING column
     * backed by one always takes the slow path. See {@link #FAST_OBJECT_BUFFERS} for why the
     * dispatch is on the exact class.
     */
    static final Set<Class<? extends IDataBuffer>> FAST_NUMERIC_BUFFERS = Set
            .of(DataBufferDouble.class, DataBufferLong.class, DataBufferInt.class);

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


    /**
     * {@inheritDoc}
     *
     * <p>
     * ⚠ {@link IDataBuffer#isMissing(int)} is <b>not</b> sufficient on its own: its contract — and
     * its default implementation — only recognise {@link MissingValue} instances, so a stored
     * {@code null} answers {@code false} there while this method's contract (and
     * {@link #isEmptyOrMissing(long)} right below) count it as missing. The general path below
     * therefore reads the value and applies the interface's own {@code null || MissingValue} test.
     * {@code DataBuffer0Bit} already pre-computes missingness that way, so this only repairs the
     * buffers that inherit the default.
     * </p>
     */
    @Override
    public boolean isMissingOrNull(long aRow) throws IndexOutOfBoundsException
    {
        int idx = Math.toIntExact(ensureValidRow(aRow));
        if (dataBuffer.size() <= idx)
        {
            return true;
        }
        if (type == DataValueType.DOUBLE)
        {
            // No null can live in a double buffer, and isMissing reads the primitive storage
            // without boxing the value the general path below would box (see isEmptyOrMissing).
            return dataBuffer.isMissing(idx);
        }
        Object v = dataBuffer.getValue(idx);
        return v == null || v instanceof MissingValue;
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


    /**
     * The cell as a PRESENT, non-empty character value, read without building a data value — or
     * {@code null} when this method cannot say so cheaply, in which case the caller must read
     * {@link #getDataValue(long)}.
     *
     * <p>
     * Contract: for every row where the result is non-null, {@code getDataValue(aRow)} is a
     * {@code DataValueString} whose value equals the result and is non-empty. Only for a
     * {@link DataValueType#STRING} column; always {@code null} otherwise. Throws exactly as
     * {@code getDataValue} does for an invalid row (it calls {@link #ensureValidRow(long)} first).
     * </p>
     *
     * @param aRow
     *            the row index.
     * @return the present, non-empty string, or {@code null} for "read {@code getDataValue}".
     * @throws IndexOutOfBoundsException
     *             if the row is not a valid row of this column.
     */
    public @Nullable String presentStringValue(long aRow) throws IndexOutOfBoundsException
    {
        int idx = Math.toIntExact(ensureValidRow(aRow));
        if (type != DataValueType.STRING)
        {
            return null;
        }
        // Read the field once per call and never cache it: a column may reassign it.
        IDataBuffer buffer = dataBuffer;
        if (idx >= buffer.size() || !FAST_OBJECT_BUFFERS.contains(buffer.getClass()))
        {
            // A short buffer answers MIS_ERROR; any other buffer class takes the slow path.
            return null;
        }
        // AbstractDataBuffer.createDataValue's STRING arm: a String is DataValueString(s) -- a
        // MissingValue or null never is -- and "" is present but EMPTY, so it goes the slow way.
        return buffer.getValue(idx) instanceof String s && !s.isEmpty() ? s : null;
    }


    /**
     * The cell as a PRESENT number, read without building a data value — or {@code NaN} when this
     * method cannot say so cheaply, in which case the caller must read {@link #getDataValue(long)}.
     *
     * <p>
     * Contract: for every row where the result is not NaN, {@code getDataValue(aRow)} is a
     * non-missing {@code DataValueLong} ({@link DataValueType#LONG} column) or
     * {@code DataValueDouble} ({@link DataValueType#DOUBLE} column) whose
     * {@code getValue().doubleValue()} is exactly (bit for bit, {@code -0.0} included) the result.
     * Only for a LONG or DOUBLE column; always NaN otherwise. Throws exactly as
     * {@code getDataValue} does for an invalid row (it calls {@link #ensureValidRow(long)} first).
     * </p>
     *
     * <p>
     * ⚠ A NaN result means "slow path", so a present value that genuinely is NaN can never be
     * answered here.
     * </p>
     *
     * @param aRow
     *            the row index.
     * @return the present number, or NaN for "read {@code getDataValue}".
     * @throws IndexOutOfBoundsException
     *             if the row is not a valid row of this column.
     */
    public double presentNumericValue(long aRow) throws IndexOutOfBoundsException
    {
        int idx = Math.toIntExact(ensureValidRow(aRow));
        boolean isLong = type == DataValueType.LONG;
        if (!isLong && type != DataValueType.DOUBLE)
        {
            return Double.NaN;
        }
        // Read the field once per call and never cache it: a column may reassign it.
        IDataBuffer buffer = dataBuffer;
        if (idx >= buffer.size())
        {
            // getDataValue answers MIS_ERROR for a short buffer.
            return Double.NaN;
        }
        Class<?> bufferClass = buffer.getClass();
        if (FAST_OBJECT_BUFFERS.contains(bufferClass))
        {
            // AbstractDataBuffer.createDataValue: a Number is DataValueDouble(n.doubleValue()) or
            // DataValueLong(n.longValue()); a MissingValue is not a Number. ⚠ This repository's
            // LONG arm turns a NaN into a PRESENT DataValueLong(0); answering it the slow way is
            // still correct, and keeps a NaN slow on both arms.
            if (buffer.getValue(idx) instanceof Number n)
            {
                double d = n.doubleValue();
                if (!Double.isNaN(d))
                {
                    if (isLong)
                    {
                        return n.longValue();
                    }
                    return d;
                }
            }
            return Double.NaN;
        }
        if (FAST_NUMERIC_BUFFERS.contains(bufferClass))
        {
            // AbstractNumericDataBuffer.createDataValue tests isMissing BEFORE the type switch, so
            // the missing sentinel of a long/int buffer (an ordinary number through
            // getValueAsDouble) must be caught here for the DOUBLE arm too.
            IDataBufferNumeric numeric = (IDataBufferNumeric) buffer;
            if (numeric.isMissing(idx))
            {
                return Double.NaN;
            }
            if (isLong)
            {
                return numeric.getValueAsLong(idx);
            }
            return numeric.getValueAsDouble(idx);
        }
        return Double.NaN;
    }

}
