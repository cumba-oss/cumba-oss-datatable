package net.cumba.datatable.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.cumba.datatable.values.DataValueMissing;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.IDataValue;
import net.cumba.datatable.values.MissingValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CachedDataTableColumnTest
{

    /**
     * The column types whose blankness fast path reads {@code getValue} rather than the buffer's
     * {@code isMissing} — OSS: the fast arm routes every type but STRING and OTHER to the buffer (a
     * registered twin difference, sweep D7), so only these two read {@code getValue}.
     */
    private static final List<DataValueType> TYPES_READ_BY_VALUE = List.of(DataValueType.STRING,
            DataValueType.OTHER);

    private CachedDataTableColumn stringColumn;

    private CachedDataTableColumn doubleColumn;

    private CachedDataTableColumn longColumn;

    @BeforeEach
    void setUp()
    {
        stringColumn = new CachedDataTableColumn(0, DataValueType.STRING);
        doubleColumn = new CachedDataTableColumn(1, DataValueType.DOUBLE);
        longColumn = new CachedDataTableColumn(2, DataValueType.LONG);
    }


    @Test
    void testInitialState()
    {
        assertEquals(0, stringColumn.getIndex());
        assertEquals(0, stringColumn.getRowCount());
        assertEquals(1, doubleColumn.getIndex());
        assertEquals(0, doubleColumn.getRowCount());
    }


    @Test
    void testAddElementObject()
    {
        stringColumn.addElement("alpha");
        stringColumn.addElement("beta");
        stringColumn.addElement("gamma");

        assertEquals(3, stringColumn.getRowCount());
        assertEquals("alpha", stringColumn.getValue(0));
        assertEquals("beta", stringColumn.getValue(1));
        assertEquals("gamma", stringColumn.getValue(2));
    }


    @Test
    void testAddElementDouble()
    {
        doubleColumn.addElement(1.5);
        doubleColumn.addElement(2.7);
        doubleColumn.addElement(3.9);

        assertEquals(3, doubleColumn.getRowCount());
        assertEquals(1.5, ((Number) doubleColumn.getValue(0)).doubleValue(), 0.0001);
        assertEquals(2.7, ((Number) doubleColumn.getValue(1)).doubleValue(), 0.0001);
        assertEquals(3.9, ((Number) doubleColumn.getValue(2)).doubleValue(), 0.0001);
    }


    @Test
    void testAddElementLong()
    {
        longColumn.addElement(100L);
        longColumn.addElement(200L);
        longColumn.addElement(300L);

        assertEquals(3, longColumn.getRowCount());
        assertEquals(100L, ((Number) longColumn.getValue(0)).longValue());
        assertEquals(200L, ((Number) longColumn.getValue(1)).longValue());
        assertEquals(300L, ((Number) longColumn.getValue(2)).longValue());
    }


    @Test
    void testSetElementObject()
    {
        stringColumn.setElement(5, "value_at_5");

        assertEquals(6, stringColumn.getRowCount());
        assertEquals("value_at_5", stringColumn.getValue(5));
    }


    @Test
    void testSetElementDouble()
    {
        doubleColumn.setElement(3, 42.0);

        assertEquals(4, doubleColumn.getRowCount());
        assertEquals(42.0, ((Number) doubleColumn.getValue(3)).doubleValue(), 0.0001);
    }


    @Test
    void testSetElementLong()
    {
        longColumn.setElement(2, 999L);

        assertEquals(3, longColumn.getRowCount());
        assertEquals(999L, ((Number) longColumn.getValue(2)).longValue());
    }


    @Test
    void testGetRowCount()
    {
        assertEquals(0, stringColumn.getRowCount());

        stringColumn.addElement("a");
        assertEquals(1, stringColumn.getRowCount());

        stringColumn.addElement("b");
        assertEquals(2, stringColumn.getRowCount());

        stringColumn.addElement("c");
        assertEquals(3, stringColumn.getRowCount());
    }


    @Test
    void testEnsureValidRow()
    {
        stringColumn.addElement("x");
        stringColumn.addElement("y");

        assertDoesNotThrow(() -> stringColumn.getValue(0));
        assertDoesNotThrow(() -> stringColumn.getValue(1));
    }


    @Test
    void testEnsureValidRowNegative()
    {
        stringColumn.addElement("x");

        assertThrows(IndexOutOfBoundsException.class, () -> stringColumn.getValue(-1));
    }


    @Test
    void testEnsureValidRowTooLarge()
    {
        stringColumn.addElement("x");

        assertThrows(IndexOutOfBoundsException.class, () -> stringColumn.getValue(1));
        assertThrows(IndexOutOfBoundsException.class, () -> stringColumn.getValue(100));
    }


    @Test
    void testGetValue()
    {
        stringColumn.addElement("hello");
        stringColumn.addElement("world");

        assertEquals("hello", stringColumn.getValue(0));
        assertEquals("world", stringColumn.getValue(1));
    }


    @Test
    void testGetDataValue()
    {
        stringColumn.addElement("test_value");

        IDataValue dataValue = stringColumn.getDataValue(0);
        assertNotNull(dataValue);
    }


    @Test
    void testComplete()
    {
        stringColumn.addElement("same");
        stringColumn.addElement("same");
        stringColumn.addElement("same");

        stringColumn.complete();

        assertEquals(3, stringColumn.getRowCount());
        assertEquals("same", stringColumn.getValue(0));
        assertEquals("same", stringColumn.getValue(1));
        assertEquals("same", stringColumn.getValue(2));
    }


    @Test
    void testCompleteMultiple()
    {
        stringColumn.addElement("alpha");
        stringColumn.addElement("beta");
        stringColumn.addElement("gamma");

        stringColumn.complete();

        assertEquals(3, stringColumn.getRowCount());
        assertEquals("alpha", stringColumn.getValue(0));
        assertEquals("beta", stringColumn.getValue(1));
        assertEquals("gamma", stringColumn.getValue(2));
    }


    @Test
    void testCompleteWithRowCount()
    {
        stringColumn.addElement("a");
        stringColumn.addElement("b");

        stringColumn.complete(10);

        assertEquals(10, stringColumn.getRowCount());
        assertEquals("a", stringColumn.getValue(0));
        assertEquals("b", stringColumn.getValue(1));
    }


    @Test
    void testSetTableRowCount()
    {
        stringColumn.addElement("x");
        assertEquals(1, stringColumn.getRowCount());

        stringColumn.setTableRowCount(5);
        assertEquals(5, stringColumn.getRowCount());
    }


    @Test
    void testSetTableRowCountDoesNotDecrease()
    {
        stringColumn.addElement("a");
        stringColumn.addElement("b");
        stringColumn.addElement("c");
        assertEquals(3, stringColumn.getRowCount());

        stringColumn.setTableRowCount(1);
        assertEquals(3, stringColumn.getRowCount());
    }


    @Test
    void testSetExpectedSize()
    {
        assertDoesNotThrow(() -> stringColumn.setExpectedSize(1000));
        assertDoesNotThrow(() -> doubleColumn.setExpectedSize(500));
    }


    @Test
    void testGetValues()
    {
        stringColumn.addElement("first");
        stringColumn.addElement("second");
        stringColumn.addElement("third");

        List<Object> values = stringColumn.getValues(0, 3).toList();

        assertEquals(3, values.size());
        assertEquals("first", values.get(0));
        assertEquals("second", values.get(1));
        assertEquals("third", values.get(2));
    }


    @Test
    void testGetDataValues()
    {
        stringColumn.addElement("one");
        stringColumn.addElement("two");

        List<IDataValue> dataValues = stringColumn.getDataValues(0, 2).toList();

        assertEquals(2, dataValues.size());
        assertNotNull(dataValues.get(0));
        assertNotNull(dataValues.get(1));
    }


    @Test
    void testDoubleTypeBuffer()
    {
        CachedDataTableColumn col = new CachedDataTableColumn(0, DataValueType.DOUBLE);
        col.addElement(1.0);
        col.addElement(2.0);

        assertEquals(2, col.getRowCount());
        assertEquals(1.0, ((Number) col.getValue(0)).doubleValue(), 0.0001);
        assertEquals(2.0, ((Number) col.getValue(1)).doubleValue(), 0.0001);
    }


    @Test
    void testStringTypeBuffer()
    {
        CachedDataTableColumn col = new CachedDataTableColumn(0, DataValueType.STRING);
        col.addElement("abc");
        col.addElement("def");

        assertEquals(2, col.getRowCount());
        assertEquals("abc", col.getValue(0));
        assertEquals("def", col.getValue(1));
    }


    @Test
    void testCompleteSingleUniqueDouble()
    {
        CachedDataTableColumn col = new CachedDataTableColumn(0, DataValueType.DOUBLE);
        col.addElement(42.0);
        col.addElement(42.0);
        col.addElement(42.0);

        col.complete();

        // After complete with single unique value, values round-trip correctly.
        assertEquals(3, col.getRowCount());
        assertEquals(42.0, ((Number) col.getValue(0)).doubleValue(), 0.0001);
        assertEquals(42.0, ((Number) col.getValue(1)).doubleValue(), 0.0001);
        assertEquals(42.0, ((Number) col.getValue(2)).doubleValue(), 0.0001);
    }

    // --- Tests for getValue simplification and hashCodeAt ---


    @Test
    void testGetValueKnownMissingDouble()
    {
        // A double column with a SAS missing value encoded as NaN payload.
        CachedDataTableColumn col = new CachedDataTableColumn(0, DataValueType.DOUBLE);
        col.addElement(1.0);
        col.addElement(MissingValue.MIS);
        col.addElement(MissingValue.MIS);
        col.addElement(2.0);

        assertEquals(1.0, ((Number) col.getValue(0)).doubleValue(), 0.0001);
        // Known missing must come back as the MissingValue instance.
        assertEquals(MissingValue.MIS, col.getValue(1));
        assertEquals(MissingValue.MIS, col.getValue(2));
        assertEquals(2.0, ((Number) col.getValue(3)).doubleValue(), 0.0001);
    }


    @Test
    void testGetValueShortBufferReturnsMisError()
    {
        // Column claims 5 rows but only 2 values are actually stored.
        CachedDataTableColumn col = new CachedDataTableColumn(0, DataValueType.STRING);
        col.addElement("a");
        col.addElement("b");
        col.setTableRowCount(5);

        assertEquals("a", col.getValue(0));
        assertEquals("b", col.getValue(1));
        // Rows 2..4 are valid (within column row count) but beyond the buffer size.
        assertEquals(MissingValue.MIS_ERROR, col.getValue(2));
        assertEquals(MissingValue.MIS_ERROR, col.getValue(3));
        assertEquals(MissingValue.MIS_ERROR, col.getValue(4));
    }


    @Test
    void testGetValueOutOfBoundsThrows()
    {
        CachedDataTableColumn col = new CachedDataTableColumn(0, DataValueType.STRING);
        col.addElement("only");
        assertThrows(IndexOutOfBoundsException.class, () -> col.getValue(1));
        assertThrows(IndexOutOfBoundsException.class, () -> col.getValue(-1));
    }


    @Test
    void testGetDataValueShortBufferReturnsMisError()
    {
        CachedDataTableColumn col = new CachedDataTableColumn(0, DataValueType.STRING);
        col.addElement("a");
        col.setTableRowCount(3);

        IDataValue dv0 = col.getDataValue(0);
        assertNotNull(dv0);

        IDataValue dv1 = col.getDataValue(1);
        assertEquals(new DataValueMissing(MissingValue.MIS_ERROR), dv1);
        IDataValue dv2 = col.getDataValue(2);
        assertEquals(new DataValueMissing(MissingValue.MIS_ERROR), dv2);
    }


    @Test
    void testHashCodeAtMatchesGetValueHash()
    {
        // Column hashCodeAt must agree with the boxing fallback on every row.
        CachedDataTableColumn col = new CachedDataTableColumn(0, DataValueType.DOUBLE);
        col.addElement(0.0);
        col.addElement(1.5);
        col.addElement(MissingValue.MIS);
        col.addElement(-1.0);
        col.addElement(MissingValue.MIS);

        for (int i = 0; i < col.getRowCount(); i++)
        {
            Object v = col.getValue(i);
            int expected = (v instanceof MissingValue mv) ? mv.hashCodeStable()
                    : (v != null ? v.hashCode() : 0);
            assertEquals(expected, col.hashCodeAt(i),
                    "hashCodeAt(" + i + ") must match getValue(i).hashCode()");
        }
    }


    @Test
    void testHashCodeAtShortBufferReturnsMisErrorHash()
    {
        CachedDataTableColumn col = new CachedDataTableColumn(0, DataValueType.STRING);
        col.addElement("x");
        col.setTableRowCount(3);

        assertEquals("x".hashCode(), col.hashCodeAt(0));
        assertEquals(MissingValue.MIS_ERROR.hashCodeStable(), col.hashCodeAt(1));
        assertEquals(MissingValue.MIS_ERROR.hashCodeStable(), col.hashCodeAt(2));
    }


    @Test
    void testHashCodeAtOutOfBoundsThrows()
    {
        CachedDataTableColumn col = new CachedDataTableColumn(0, DataValueType.STRING);
        col.addElement("a");
        assertThrows(IndexOutOfBoundsException.class, () -> col.hashCodeAt(1));
        assertThrows(IndexOutOfBoundsException.class, () -> col.hashCodeAt(-1));
    }

    // ==================== maxValueLength after complete() ====================


    @Test
    void testMaxValueLengthOnStringColumn()
    {
        CachedDataTableColumn col = new CachedDataTableColumn(0, DataValueType.STRING);
        col.addElement("ab");
        col.addElement("abcdef");
        col.addElement("xyz");
        col.complete();
        assertEquals(6, col.getMaxValueLength());
    }


    @Test
    void testMaxValueLengthUsesFactorDictionaryFastPath()
    {
        // Highly repetitive data — factor buffer keeps one dictionary entry per unique value and
        // our fast path only scans those unique entries. Correctness must hold regardless.
        CachedDataTableColumn col = new CachedDataTableColumn(0, DataValueType.STRING);
        for (int i = 0; i < 1000; i++)
        {
            col.addElement(i % 2 == 0 ? "short" : "a_much_longer_value");
        }
        col.complete();
        assertEquals("a_much_longer_value".length(), col.getMaxValueLength());
    }


    @Test
    void testMaxValueLengthEmptyColumn()
    {
        CachedDataTableColumn col = new CachedDataTableColumn(0, DataValueType.STRING);
        col.complete();
        assertEquals(0, col.getMaxValueLength());
    }


    @Test
    void testMaxValueLengthNonStringColumnStaysZero()
    {
        CachedDataTableColumn col = new CachedDataTableColumn(0, DataValueType.DOUBLE);
        col.addElement(1.5d);
        col.addElement(2.5d);
        col.complete();
        // Non-STRING columns are skipped — length semantics are not meaningful.
        assertEquals(0, col.getMaxValueLength());
    }


    @Test
    void testMaxValueLengthUtf8ByteCountForNonAscii()
    {
        CachedDataTableColumn col = new CachedDataTableColumn(0, DataValueType.STRING);
        col.addElement("abc"); // 3 bytes
        col.addElement("éüö"); // 6 bytes (each char is 2 bytes in UTF-8)
        col.addElement("long ASCII value"); // 16 bytes
        col.complete();
        // Max is the ASCII string at 16 bytes.
        assertEquals(16, col.getMaxValueLength());
    }


    @Test
    void testMaxValueLengthNullTreatedAsZero()
    {
        CachedDataTableColumn col = new CachedDataTableColumn(0, DataValueType.STRING);
        col.addElement("abc");
        col.addElement((Object) null);
        col.complete();
        assertEquals(3, col.getMaxValueLength());
    }


    /**
     * A MISSING-typed column is blank at every row: every cell's data value is a
     * {@code DataValueMissing}, whatever the buffer holds, so the column-level answer must agree
     * with {@code getDataValue(row).isEmptyOrMissing()} for a raw {@code null}, a
     * {@link MissingValue} and a non-missing object alike.
     */
    @Test
    void isEmptyOrMissing_missingTypedColumn_agreesWithGetDataValue()
    {
        CachedDataTableColumn col = new CachedDataTableColumn(0, DataValueType.MISSING);
        col.addElement((Object) null);
        col.addElement(MissingValue.MIS);
        col.addElement("x");
        col.complete();
        for (long row = 0; row < 3; row++)
        {
            assertEquals(col.getDataValue(row).isEmptyOrMissing(), col.isEmptyOrMissing(row),
                    "column vs value level disagree at row " + row);
            assertTrue(col.isEmptyOrMissing(row), "a MISSING-typed cell is blank at row " + row);
        }
    }


    /**
     * PLAN-other-column-nan-blankness: a boxed NaN — {@code Double.NaN} or {@code Float.NaN} — in a
     * column whose fast path reads {@code getValue} (TYPES_READ_BY_VALUE) is decoded as missing by
     * {@code getDataValue}, so both column-level predicates must say so too:
     * {@code isEmptyOrMissing(row)} agrees with {@code getDataValue(row).isEmptyOrMissing()} and
     * {@code isMissingOrNull(row)} with {@code getDataValue(row).isMissingOrInvalid()}. Checked
     * over a mixed column (NaN, Float NaN, {@code ""} except in LONG, {@code null}, a present
     * value) and an all-NaN column, which the default factory may compress to a constant buffer.
     * Every disagreement is collected, so a red run names each type, row and predicate at once.
     */
    @Test
    void isEmptyOrMissing_nanInGeneralPath_agreesWithGetDataValue()
    {
        List<String> disagreements = new ArrayList<>();
        for (DataValueType type : TYPES_READ_BY_VALUE)
        {
            CachedDataTableColumn mixed = new CachedDataTableColumn(0, type);
            mixed.addElement((Object) Double.NaN);
            mixed.addElement((Object) Float.NaN);
            if (type != DataValueType.LONG)
            {
                // A String in a LONG column is a type mismatch the buffer decodes as MIS_ERROR
                // (missing to isMissingOrInvalid, not to isMissingOrNull): the out-of-scope
                // mismatch class of this plan (review L4), not a NaN case.
                mixed.addElement((Object) "");
            }
            mixed.addElement((Object) null);
            mixed.addElement(presentValueFor(type));
            mixed.complete();
            collectDisagreements(type + " mixed", mixed, disagreements);
            for (long row = 0; row < 2; row++)
            {
                // the direction of ruling D1: a boxed NaN is blank, not merely consistent
                if (!mixed.isEmptyOrMissing(row))
                {
                    disagreements.add(type + " mixed row " + row + " NaN not blank (D1)");
                }
            }

            CachedDataTableColumn allNaN = new CachedDataTableColumn(0, type);
            for (int i = 0; i < 3; i++)
            {
                allNaN.addElement((Object) Double.NaN);
            }
            allNaN.complete();
            collectDisagreements(type + " all-NaN", allNaN, disagreements);
        }
        assertEquals(List.of(), disagreements, "column vs value level disagree");
    }


    private static Object presentValueFor(DataValueType aType)
    {
        return aType == DataValueType.LONG ? (Object) Long.valueOf(7L) : "x";
    }


    private static void collectDisagreements(String aLabel, CachedDataTableColumn aColumn,
            List<String> aOut)
    {
        for (long row = 0; row < aColumn.getRowCount(); row++)
        {
            IDataValue dv = aColumn.getDataValue(row);
            if (aColumn.isEmptyOrMissing(row) != dv.isEmptyOrMissing())
            {
                aOut.add(aLabel + " row " + row + " isEmptyOrMissing");
            }
            if (aColumn.isMissingOrNull(row) != dv.isMissingOrInvalid())
            {
                aOut.add(aLabel + " row " + row + " isMissingOrNull");
            }
        }
    }


    /**
     * The MISSING arm answers only after the row check: an out-of-range row on a MISSING-typed
     * column still throws, on either side of the range.
     */
    @Test
    void isEmptyOrMissing_missingTypedColumn_outOfBounds_throws()
    {
        CachedDataTableColumn col = new CachedDataTableColumn(0, DataValueType.MISSING);
        col.addElement((Object) null);
        col.complete();
        assertThrows(IndexOutOfBoundsException.class, () -> col.isEmptyOrMissing(9));
        assertThrows(IndexOutOfBoundsException.class, () -> col.isEmptyOrMissing(-1));
    }


    @Test
    void isEmptyOrMissing_outOfBounds_throws()
    {
        stringColumn.addElement("a");
        assertThrows(IndexOutOfBoundsException.class, () -> stringColumn.isEmptyOrMissing(9));
    }
}
