package net.cumba.datatable.impl.databuffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.cumba.datatable.values.DataValueDouble;
import net.cumba.datatable.values.DataValueLong;
import net.cumba.datatable.values.DataValueMissing;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.IDataValue;
import net.cumba.datatable.values.MissingValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataBufferDoubleTest
{

    private DataBufferDouble buffer;

    @BeforeEach
    void setUp()
    {
        buffer = new DataBufferDouble();
    }


    @Test
    void testInitialSize()
    {
        assertEquals(0, buffer.size());
    }


    @Test
    void testAddAndGetValue()
    {
        buffer.addValue(3.14);
        assertEquals(1, buffer.size());
        assertEquals(3.14, buffer.getValue(0));
        assertFalse(buffer.isMissing(0));
    }


    @Test
    void testSetValueMissingRecoversSameVariant()
    {
        buffer.setValue(0, MissingValue.MIS_ERROR);
        assertTrue(buffer.isMissing(0));
        // The NaN payload encodes the specific missing variant.
        assertEquals(MissingValue.MIS_ERROR, buffer.getValue(0));
    }


    @Test
    void testSetValueMissDifferentFromError()
    {
        buffer.setValue(0, MissingValue.MIS);
        assertEquals(MissingValue.MIS, buffer.getValue(0));
    }


    @Test
    void testSetValueNumber()
    {
        buffer.setValue(0, Integer.valueOf(7));
        assertEquals(7.0, buffer.getValue(0));
    }


    @Test
    void testSetValueWholeLong()
    {
        buffer.setValue(0, Long.valueOf(42L));
        assertEquals(42.0, buffer.getValue(0));
    }


    @Test
    void testCanStore()
    {
        assertTrue(buffer.canStore(MissingValue.MIS));
        assertTrue(buffer.canStore(Double.valueOf(1.5)));
        assertTrue(buffer.canStore(Integer.valueOf(3)));
        // A long whose double value round-trips.
        assertTrue(buffer.canStore(Long.valueOf(100L)));

        // A non-number cannot be stored.
        assertFalse(buffer.canStore("text"));
        // A plain Object is not a Number.
        assertFalse(buffer.canStore(new Object()));
    }


    @Test
    void testCanStoreDoubleAlwaysTrue()
    {
        assertTrue(buffer.canStoreDouble(Double.NaN));
        assertTrue(buffer.canStoreDouble(1.23456789));
        assertTrue(buffer.canStoreDouble(Double.MAX_VALUE));
    }


    @Test
    void testCanStoreLong()
    {
        assertTrue(buffer.canStoreLong(1L));
        assertTrue(buffer.canStoreLong(0L));
        // A long that loses precision when converted to double and back cannot be stored.
        assertFalse(buffer.canStoreLong(Long.MAX_VALUE - 1));
    }


    @Test
    void testSetDoubleValue()
    {
        buffer.setDoubleValue(0, 2.5);
        assertEquals(2.5, buffer.getValueAsDouble(0));
    }


    @Test
    void testSetLongValue()
    {
        buffer.setLongValue(0, 9L);
        assertEquals(9.0, buffer.getValueAsDouble(0));
        assertEquals(9.0, buffer.getValue(0));
    }


    @Test
    void testSetLongValueRejectsNonRoundTrip()
    {
        assertThrows(IllegalArgumentException.class,
                () -> buffer.setLongValue(0, Long.MAX_VALUE - 1));
    }


    @Test
    void testSetValueRejectsNonNumber()
    {
        assertThrows(IllegalArgumentException.class, () -> buffer.setValue(0, "x"));
    }


    @Test
    void testTypedAccessors()
    {
        buffer.setDoubleValue(0, 130.9);
        assertEquals(130.9, buffer.getValueAsDouble(0));
        assertEquals(130L, buffer.getValueAsLong(0));
        assertEquals(130, buffer.getValueAsInt(0));
        assertEquals((byte) 130, buffer.getValueAsByte(0));
        assertEquals((short) 130, buffer.getValueAsShort(0));
    }


    @Test
    void testHashCodeAtMatchesDoubleHash()
    {
        buffer.setDoubleValue(0, 1.75);
        assertEquals(Double.hashCode(1.75), buffer.hashCodeAt(0));
    }


    @Test
    void testHashCodeAtMissing()
    {
        buffer.setValue(0, MissingValue.MIS);
        assertEquals(MissingValue.MIS.hashCodeStable(), buffer.hashCodeAt(0));
    }


    @Test
    void testIsMissingForNan()
    {
        buffer.setDoubleValue(0, Double.NaN);
        assertTrue(buffer.isMissing(0));
    }


    @Test
    void testSetExpectedSize()
    {
        buffer.setExpectedSize(8);
        buffer.setDoubleValue(0, 1.0);
        assertEquals(1, buffer.size());
        assertEquals(1.0, buffer.getValue(0));
    }


    @Test
    void testTrimToSize()
    {
        buffer.setDoubleValue(0, 1.0);
        buffer.setDoubleValue(1, 2.0);
        buffer.trimToSize();
        assertEquals(2, buffer.size());
        assertEquals(2.0, buffer.getValue(1));
    }


    @Test
    void testAutoGrowBeyondInitialCapacity()
    {
        for (int i = 0; i < 1000; i++)
        {
            buffer.setDoubleValue(i, i + 0.5);
        }
        assertEquals(1000, buffer.size());
        assertEquals(999.5, buffer.getValue(999));
    }


    @Test
    void testGetEstimatedMemoryBytes()
    {
        buffer.setDoubleValue(0, 1.0);
        buffer.setDoubleValue(1, 2.0);
        buffer.setDoubleValue(2, 3.0);
        assertEquals(3L * Double.BYTES, buffer.getEstimatedMemoryBytes());
    }


    @Test
    void testGetDataValueDouble()
    {
        buffer.setDoubleValue(0, 1.5);
        IDataValue dv = buffer.getDataValue(0, DataValueType.DOUBLE);
        assertTrue(dv instanceof DataValueDouble);
        assertEquals(1.5, dv.getValueAsDouble());
    }


    @Test
    void testGetDataValueLong()
    {
        buffer.setDoubleValue(0, 4.9);
        IDataValue dv = buffer.getDataValue(0, DataValueType.LONG);
        assertTrue(dv instanceof DataValueLong);
        assertEquals(4L, ((DataValueLong) dv).getLong());
    }


    @Test
    void testGetDataValueMissing()
    {
        buffer.setValue(0, MissingValue.MIS_ERROR);
        IDataValue dv = buffer.getDataValue(0, DataValueType.DOUBLE);
        assertTrue(dv instanceof DataValueMissing);
        assertEquals(MissingValue.MIS_ERROR, dv.getValue());
    }


    @Test
    void testGetValuesAsDoubleOverride()
    {
        buffer.setDoubleValue(0, 1.1);
        buffer.setDoubleValue(1, 2.2);
        buffer.setDoubleValue(2, 3.3);
        assertArrayEquals(new double[]
        {
                1.1, 2.2, 3.3
        }, buffer.getValuesAsDouble(0, 3));
    }


    @Test
    void testGetValuesAsDoubleSubRange()
    {
        buffer.setDoubleValue(0, 1.1);
        buffer.setDoubleValue(1, 2.2);
        buffer.setDoubleValue(2, 3.3);
        assertArrayEquals(new double[]
        {
                2.2, 3.3
        }, buffer.getValuesAsDouble(1, 3));
    }


    @Test
    void testGetValuesAsDoubleClampsRange()
    {
        buffer.setDoubleValue(0, 1.0);
        buffer.setDoubleValue(1, 2.0);
        assertArrayEquals(new double[]
        {
                1.0, 2.0
        }, buffer.getValuesAsDouble(-2, 50));
    }


    @Test
    void testGetValuesAsDoubleEmptyForInvertedRange()
    {
        buffer.setDoubleValue(0, 1.0);
        buffer.setDoubleValue(1, 2.0);
        assertEquals(0, buffer.getValuesAsDouble(2, 1).length);
    }


    @Test
    void testGetValuesAsLongDefault()
    {
        buffer.setDoubleValue(0, 1.9);
        buffer.setDoubleValue(1, 2.9);
        assertArrayEquals(new long[]
        {
                1L, 2L
        }, buffer.getValuesAsLong(0, 2));
    }


    @Test
    void testGetValuesAsIntDefault()
    {
        buffer.setDoubleValue(0, 1.9);
        buffer.setDoubleValue(1, 2.9);
        assertArrayEquals(new int[]
        {
                1, 2
        }, buffer.getValuesAsInt(0, 2));
    }
}
