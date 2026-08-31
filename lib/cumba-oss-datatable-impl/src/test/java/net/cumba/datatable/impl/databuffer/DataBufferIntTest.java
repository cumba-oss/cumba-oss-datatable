package net.cumba.datatable.impl.databuffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.cumba.datatable.values.DataValueBoolean;
import net.cumba.datatable.values.DataValueDouble;
import net.cumba.datatable.values.DataValueLong;
import net.cumba.datatable.values.DataValueMissing;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.IDataValue;
import net.cumba.datatable.values.MissingValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataBufferIntTest
{

    private DataBufferInt buffer;

    @BeforeEach
    void setUp()
    {
        buffer = new DataBufferInt();
    }


    @Test
    void testInitialSize()
    {
        assertEquals(0, buffer.size());
    }


    @Test
    void testAddAndGetValue()
    {
        buffer.addValue(42L);
        buffer.addValue(-7L);

        assertEquals(2, buffer.size());
        assertEquals(42L, buffer.getValue(0));
        assertEquals(-7L, buffer.getValue(1));
    }


    @Test
    void testGetValueReturnsLongNotInt()
    {
        buffer.setLongValue(0, 5);
        // getValue boxes into a Long, not an Integer.
        assertEquals(Long.valueOf(5L), buffer.getValue(0));
    }


    @Test
    void testSetValueNullWritesMissing()
    {
        buffer.setValue(0, null);

        assertEquals(1, buffer.size());
        assertTrue(buffer.isMissing(0));
        assertEquals(MissingValue.MIS, buffer.getValue(0));
    }


    @Test
    void testSetValueMissingValueWritesMissing()
    {
        buffer.setValue(0, MissingValue.MIS_ERROR);

        assertTrue(buffer.isMissing(0));
        // DataBufferInt collapses every missing into the single MIS sentinel.
        assertEquals(MissingValue.MIS, buffer.getValue(0));
    }


    @Test
    void testIsMissingFalseForRealValue()
    {
        buffer.setLongValue(0, 0L);
        assertFalse(buffer.isMissing(0));
    }


    @Test
    void testSetValueBooleanTrue()
    {
        buffer.setValue(0, true);
        assertEquals(1L, buffer.getValue(0));
        assertFalse(buffer.isMissing(0));
    }


    @Test
    void testSetValueBooleanFalse()
    {
        buffer.setValue(0, false);
        assertEquals(0L, buffer.getValue(0));
        assertFalse(buffer.isMissing(0));
    }


    @Test
    void testSetValueNumberInRange()
    {
        buffer.setValue(0, Integer.valueOf(1234));
        assertEquals(1234L, buffer.getValue(0));
    }


    @Test
    void testSetValueRejectsNonNumber()
    {
        assertThrows(IllegalArgumentException.class, () -> buffer.setValue(0, "abc"));
    }


    @Test
    void testCanStore()
    {
        assertTrue(buffer.canStore(null));
        assertTrue(buffer.canStore(MissingValue.MIS));
        assertTrue(buffer.canStore(true));
        assertTrue(buffer.canStore(Integer.valueOf(10)));
        assertTrue(buffer.canStore(Long.valueOf(Integer.MAX_VALUE)));

        assertFalse(buffer.canStore("not a number"));
        // Out of int range.
        assertFalse(buffer.canStore(Long.valueOf((long) Integer.MAX_VALUE + 1)));
        assertFalse(buffer.canStore(Long.valueOf((long) Integer.MIN_VALUE - 1)));
        // Has a fractional part.
        assertFalse(buffer.canStore(Double.valueOf(1.5)));
    }


    @Test
    void testCanStoreDouble()
    {
        assertTrue(buffer.canStoreDouble(100.0));
        assertTrue(buffer.canStoreDouble(Integer.MAX_VALUE));

        assertFalse(buffer.canStoreDouble(1.5));
        assertFalse(buffer.canStoreDouble((double) Integer.MAX_VALUE + 1));
        assertFalse(buffer.canStoreDouble((double) Integer.MIN_VALUE - 1));
    }


    @Test
    void testCanStoreLong()
    {
        assertTrue(buffer.canStoreLong(0L));
        assertTrue(buffer.canStoreLong(Integer.MAX_VALUE));
        assertTrue(buffer.canStoreLong(Integer.MIN_VALUE));

        assertFalse(buffer.canStoreLong((long) Integer.MAX_VALUE + 1));
        assertFalse(buffer.canStoreLong((long) Integer.MIN_VALUE - 1));
    }


    @Test
    void testSetDoubleValue()
    {
        buffer.setDoubleValue(0, 17.0);
        assertEquals(17L, buffer.getValue(0));
        assertEquals(17.0, buffer.getValueAsDouble(0));
    }


    @Test
    void testSetDoubleValueRejectsNonInteger()
    {
        assertThrows(IllegalArgumentException.class, () -> buffer.setDoubleValue(0, 1.25));
    }


    @Test
    void testSetLongValueRejectsOutOfRange()
    {
        assertThrows(IllegalArgumentException.class,
                () -> buffer.setLongValue(0, (long) Integer.MAX_VALUE + 1));
    }


    @Test
    void testTypedAccessors()
    {
        buffer.setLongValue(0, 300L);
        assertEquals(300.0, buffer.getValueAsDouble(0));
        assertEquals(300L, buffer.getValueAsLong(0));
        assertEquals(300, buffer.getValueAsInt(0));
        // 300 truncated to byte wraps to 44.
        assertEquals((byte) 300, buffer.getValueAsByte(0));
        assertEquals((short) 300, buffer.getValueAsShort(0));
    }


    @Test
    void testHashCodeAtMatchesValueHash()
    {
        buffer.setLongValue(0, 99L);
        assertEquals(Long.hashCode(99L), buffer.hashCodeAt(0));
    }


    @Test
    void testHashCodeAtMissing()
    {
        buffer.setValue(0, null);
        assertEquals(MissingValue.MIS.hashCodeStable(), buffer.hashCodeAt(0));
    }


    @Test
    void testSetExpectedSizeTruncates()
    {
        buffer.setLongValue(5, 1L);
        buffer.setExpectedSize(2);
        // The internal array shrank; reading within range still works.
        buffer.setLongValue(0, 7L);
        assertEquals(7L, buffer.getValue(0));
    }


    @Test
    void testTrimToSize()
    {
        buffer.setLongValue(0, 1L);
        buffer.setLongValue(1, 2L);
        buffer.trimToSize();
        assertEquals(2, buffer.size());
        assertEquals(1L, buffer.getValue(0));
        assertEquals(2L, buffer.getValue(1));
    }


    @Test
    void testAutoGrowBeyondInitialCapacity()
    {
        for (int i = 0; i < 1000; i++)
        {
            buffer.setLongValue(i, i);
        }
        assertEquals(1000, buffer.size());
        assertEquals(999L, buffer.getValue(999));
    }


    @Test
    void testGetEstimatedMemoryBytes()
    {
        buffer.setLongValue(0, 1L);
        buffer.setLongValue(1, 2L);
        buffer.setLongValue(2, 3L);
        assertEquals(3L * Integer.BYTES, buffer.getEstimatedMemoryBytes());
    }


    @Test
    void testGetDataValueDouble()
    {
        buffer.setLongValue(0, 5L);
        IDataValue dv = buffer.getDataValue(0, DataValueType.DOUBLE);
        assertTrue(dv instanceof DataValueDouble);
        assertEquals(5.0, dv.getValueAsDouble());
    }


    @Test
    void testGetDataValueLong()
    {
        buffer.setLongValue(0, 5L);
        IDataValue dv = buffer.getDataValue(0, DataValueType.LONG);
        assertTrue(dv instanceof DataValueLong);
        assertEquals(5L, ((DataValueLong) dv).getLong());
    }


    @Test
    void testGetDataValueBooleanTrue()
    {
        buffer.setLongValue(0, 1L);
        IDataValue dv = buffer.getDataValue(0, DataValueType.BOOLEAN);
        assertTrue(dv instanceof DataValueBoolean);
        assertEquals(true, dv.getValue());
    }


    @Test
    void testGetDataValueBooleanFalse()
    {
        buffer.setLongValue(0, 0L);
        IDataValue dv = buffer.getDataValue(0, DataValueType.BOOLEAN);
        assertTrue(dv instanceof DataValueBoolean);
        assertEquals(false, dv.getValue());
    }


    @Test
    void testGetDataValueMissing()
    {
        buffer.setValue(0, null);
        IDataValue dv = buffer.getDataValue(0, DataValueType.LONG);
        assertTrue(dv instanceof DataValueMissing);
        assertEquals(MissingValue.MIS, dv.getValue());
    }


    @Test
    void testGetValuesAsIntDefault()
    {
        buffer.setLongValue(0, 10L);
        buffer.setLongValue(1, 20L);
        buffer.setLongValue(2, 30L);
        assertArrayEquals(new int[]
        {
                10, 20, 30
        }, buffer.getValuesAsInt(0, 3));
    }


    @Test
    void testGetValuesAsIntClampsRange()
    {
        buffer.setLongValue(0, 1L);
        buffer.setLongValue(1, 2L);
        // Negative start clamps to 0, end beyond size clamps to size.
        assertArrayEquals(new int[]
        {
                1, 2
        }, buffer.getValuesAsInt(-5, 99));
    }


    @Test
    void testGetValuesAsIntEmptyForInvertedRange()
    {
        buffer.setLongValue(0, 1L);
        buffer.setLongValue(1, 2L);
        assertEquals(0, buffer.getValuesAsInt(2, 1).length);
    }


    @Test
    void testGetValuesAsLongDefault()
    {
        buffer.setLongValue(0, 100L);
        buffer.setLongValue(1, 200L);
        assertArrayEquals(new long[]
        {
                100L, 200L
        }, buffer.getValuesAsLong(0, 2));
    }


    @Test
    void testGetValuesAsDoubleDefault()
    {
        buffer.setLongValue(0, 4L);
        buffer.setLongValue(1, 8L);
        assertArrayEquals(new double[]
        {
                4.0, 8.0
        }, buffer.getValuesAsDouble(0, 2));
    }
}
