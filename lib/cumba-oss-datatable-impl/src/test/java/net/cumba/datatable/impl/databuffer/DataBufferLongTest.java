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

class DataBufferLongTest
{

    private static final long MIN = Long.MIN_VALUE;

    private static final Object BOXED_MIN = Long.MIN_VALUE;

    private static final long MIN_AS_LONG = MIN;

    private static final double MIN_AS_DOUBLE = MIN;

    private static final long BYTES = Long.BYTES;

    private DataBufferLong buffer;

    @BeforeEach
    void setUp()
    {
        buffer = new DataBufferLong();
    }


    @Test
    void testInitialSize()
    {
        assertEquals(0, buffer.size());
    }


    @Test
    void testAddAndGetValue()
    {
        buffer.addValue(123456789012345L);
        assertEquals(1, buffer.size());
        assertEquals(123456789012345L, buffer.getValue(0));
    }


    @Test
    void testSetValueNullWritesMissing()
    {
        buffer.setValue(0, null);
        assertTrue(buffer.isMissing(0));
        assertEquals(MissingValue.MIS, buffer.getValue(0));
    }


    @Test
    void testSetValueMissingKeepsItsIdentity()
    {
        buffer.setValue(0, MissingValue.MIS_UNKNOWN);
        assertTrue(buffer.isMissing(0));
        assertEquals(MissingValue.MIS_UNKNOWN, buffer.getValue(0));
    }


    @Test
    void testIsMissingFalseForRealValue()
    {
        buffer.setLongValue(0, 0L);
        assertFalse(buffer.isMissing(0));
    }


    @Test
    void testCanStore()
    {
        assertTrue(buffer.canStore(null));
        assertTrue(buffer.canStore(MissingValue.MIS));
        assertTrue(buffer.canStore(42L));
        // Integer fits because its double value equals its long value.
        assertTrue(buffer.canStore(42));
        // A whole-number double is storable.
        assertTrue(buffer.canStore(5.0));

        // Non-number rejected.
        assertFalse(buffer.canStore("nope"));
        // Fractional double rejected.
        assertFalse(buffer.canStore(5.5));
    }


    @Test
    void testCanStoreLongAlwaysTrue()
    {
        assertTrue(buffer.canStoreLong(Long.MAX_VALUE));
        assertTrue(buffer.canStoreLong(Long.MIN_VALUE));
        assertTrue(buffer.canStoreLong(0L));
    }


    @Test
    void testCanStoreDouble()
    {
        assertTrue(buffer.canStoreDouble(9.0));
        assertFalse(buffer.canStoreDouble(9.25));
    }


    @Test
    void testSetValueRejectsFractionalDouble()
    {
        assertThrows(IllegalArgumentException.class, () -> buffer.setValue(0, (Object) 1.1));
    }


    @Test
    void testSetValueRejectsNonNumber()
    {
        assertThrows(IllegalArgumentException.class, () -> buffer.setValue(0, new Object()));
    }


    @Test
    void testSetDoubleValue()
    {
        buffer.setDoubleValue(0, 42.0);
        assertEquals(42L, buffer.getValue(0));
    }


    @Test
    void testSetDoubleValueRejectsFraction()
    {
        assertThrows(IllegalArgumentException.class, () -> buffer.setDoubleValue(0, 42.5));
    }


    @Test
    void testTypedAccessors()
    {
        buffer.setLongValue(0, 1000L);
        assertEquals(1000.0, buffer.getValueAsDouble(0));
        assertEquals(1000L, buffer.getValueAsLong(0));
        assertEquals(1000, buffer.getValueAsInt(0));
        assertEquals((byte) 1000, buffer.getValueAsByte(0));
        assertEquals((short) 1000, buffer.getValueAsShort(0));
    }


    @Test
    void testHashCodeAtMatchesValueHash()
    {
        buffer.setLongValue(0, 555L);
        assertEquals(Long.hashCode(555L), buffer.hashCodeAt(0));
    }


    @Test
    void testHashCodeAtMissing()
    {
        buffer.setValue(0, null);
        assertEquals(MissingValue.MIS.hashCodeStable(), buffer.hashCodeAt(0));
    }


    @Test
    void testSetExpectedSize()
    {
        buffer.setExpectedSize(4);
        buffer.setLongValue(0, 1L);
        assertEquals(1, buffer.size());
        assertEquals(1L, buffer.getValue(0));
    }


    @Test
    void testTrimToSize()
    {
        buffer.setLongValue(0, 1L);
        buffer.setLongValue(1, 2L);
        buffer.trimToSize();
        assertEquals(2, buffer.size());
        assertEquals(2L, buffer.getValue(1));
    }


    @Test
    void testAutoGrowBeyondInitialCapacity()
    {
        for (int i = 0; i < 1000; i++)
        {
            buffer.setLongValue(i, i * 1_000_000_000L);
        }
        assertEquals(1000, buffer.size());
        assertEquals(999_000_000_000L, buffer.getValue(999));
    }


    @Test
    void testGetEstimatedMemoryBytes()
    {
        buffer.setLongValue(0, 1L);
        buffer.setLongValue(1, 2L);
        assertEquals(2L * Long.BYTES, buffer.getEstimatedMemoryBytes());
    }


    @Test
    void testGetDataValueDouble()
    {
        buffer.setLongValue(0, 7L);
        IDataValue dv = buffer.getDataValue(0, DataValueType.DOUBLE);
        assertTrue(dv instanceof DataValueDouble);
        assertEquals(7.0, dv.getValueAsDouble());
    }


    @Test
    void testGetDataValueLong()
    {
        buffer.setLongValue(0, 7L);
        IDataValue dv = buffer.getDataValue(0, DataValueType.LONG);
        assertTrue(dv instanceof DataValueLong);
        assertEquals(7L, ((DataValueLong) dv).getLong());
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
    void testGetValuesAsLongOverride()
    {
        buffer.setLongValue(0, 10L);
        buffer.setLongValue(1, 20L);
        buffer.setLongValue(2, 30L);
        assertArrayEquals(new long[]
        {
                10L, 20L, 30L
        }, buffer.getValuesAsLong(0, 3));
    }


    @Test
    void testGetValuesAsLongSubRange()
    {
        buffer.setLongValue(0, 10L);
        buffer.setLongValue(1, 20L);
        buffer.setLongValue(2, 30L);
        assertArrayEquals(new long[]
        {
                20L, 30L
        }, buffer.getValuesAsLong(1, 3));
    }


    @Test
    void testGetValuesAsLongClampsRange()
    {
        buffer.setLongValue(0, 1L);
        buffer.setLongValue(1, 2L);
        assertArrayEquals(new long[]
        {
                1L, 2L
        }, buffer.getValuesAsLong(-3, 100));
    }


    @Test
    void testGetValuesAsLongEmptyForInvertedRange()
    {
        buffer.setLongValue(0, 1L);
        buffer.setLongValue(1, 2L);
        assertEquals(0, buffer.getValuesAsLong(2, 1).length);
    }


    @Test
    void testGetValuesAsIntDefault()
    {
        buffer.setLongValue(0, 5L);
        buffer.setLongValue(1, 6L);
        assertArrayEquals(new int[]
        {
                5, 6
        }, buffer.getValuesAsInt(0, 2));
    }


    @Test
    void testGetValuesAsDoubleDefault()
    {
        buffer.setLongValue(0, 5L);
        buffer.setLongValue(1, 6L);
        assertArrayEquals(new double[]
        {
                5.0, 6.0
        }, buffer.getValuesAsDouble(0, 2));
    }

    // --- Missing identity and a real MIN_VALUE (PLAN-oss-numeric-buffer-missing-identity) ---


    @Test
    void testEveryMissingValueKeepsItsIdentity()
    {
        MissingValue[] all = MissingValue.values();
        for (int i = 0; i < all.length; i++)
        {
            buffer.setValue(i, all[i]);
        }
        assertEquals(all.length, buffer.size());
        for (int i = 0; i < all.length; i++)
        {
            MissingValue mv = all[i];
            assertTrue(buffer.isMissing(i), mv.name());
            assertEquals(mv, buffer.getValue(i), mv.name());
            assertEquals(mv.hashCodeStable(), buffer.hashCodeAt(i), mv.name());
            assertEquals(mv, buffer.getDataValue(i, DataValueType.LONG).getValue(), mv.name());
            assertEquals(mv, buffer.getDataValue(i, DataValueType.DOUBLE).getValue(), mv.name());
        }
    }


    @Test
    void testRealMinValueIsPresent()
    {
        buffer.setLongValue(0, MIN);
        buffer.setValue(1, BOXED_MIN);
        buffer.setDoubleValue(2, MIN);
        buffer.setValue(3, null);
        for (int i = 0; i < 3; i++)
        {
            assertFalse(buffer.isMissing(i));
            assertEquals(MIN_AS_LONG, buffer.getValue(i));
            assertEquals(MIN, buffer.getValueAsLong(i));
            assertEquals(Long.hashCode(MIN), buffer.hashCodeAt(i));
            IDataValue dv = buffer.getDataValue(i, DataValueType.LONG);
            assertTrue(dv instanceof DataValueLong);
            assertEquals(MIN, ((DataValueLong) dv).getLong());
            assertEquals(MIN_AS_DOUBLE,
                    buffer.getDataValue(i, DataValueType.DOUBLE).getValueAsDouble());
        }
        assertTrue(buffer.isMissing(3));
        assertEquals(MissingValue.MIS, buffer.getValue(3));
    }


    @Test
    void testOverwriteReplacesTheCode()
    {
        buffer.setValue(0, MissingValue.MIS_A);
        buffer.setLongValue(0, 5L);
        assertFalse(buffer.isMissing(0));
        assertEquals(5L, buffer.getValue(0));

        buffer.setValue(0, MissingValue.MIS_B);
        buffer.setLongValue(0, MIN);
        assertFalse(buffer.isMissing(0));
        assertEquals(MIN_AS_LONG, buffer.getValue(0));

        buffer.setValue(0, null);
        assertTrue(buffer.isMissing(0));
        assertEquals(MissingValue.MIS, buffer.getValue(0));

        buffer.setValue(0, MissingValue.MIS_Z);
        buffer.setValue(0, MissingValue.MIS);
        assertEquals(MissingValue.MIS, buffer.getValue(0));
        assertEquals(MissingValue.MIS.hashCodeStable(), buffer.hashCodeAt(0));
    }


    @Test
    void testPlainMissingsNeverAllocateTheCodeTable()
    {
        buffer.setLongValue(0, 1L);
        buffer.setValue(1, null);
        buffer.setValue(2, MissingValue.MIS);
        buffer.setLongValue(3, MIN + 1L);
        assertEquals(4L * BYTES, buffer.getEstimatedMemoryBytes());
        assertEquals(MissingValue.MIS, buffer.getValue(1));
        assertEquals(MissingValue.MIS, buffer.getValue(2));
    }


    @Test
    void testSpecialMissingAllocatesOneBytePerRow()
    {
        buffer.setLongValue(0, 1L);
        buffer.setValue(1, MissingValue.MIS_Q);
        buffer.setLongValue(2, 2L);
        assertEquals(3L * BYTES + 3L, buffer.getEstimatedMemoryBytes());
    }


    @Test
    void testRealMinValueAllocatesTheCodeTable()
    {
        buffer.setLongValue(0, MIN);
        assertEquals(BYTES + 1L, buffer.getEstimatedMemoryBytes());
    }


    @Test
    void testCodesSurviveGrowthAndTrim()
    {
        buffer.setValue(0, MissingValue.MIS_C);
        for (int i = 1; i < 2000; i++)
        {
            buffer.setLongValue(i, i);
        }
        buffer.setValue(1500, MissingValue.MIS_D);
        buffer.setLongValue(1999, MIN);
        buffer.trimToSize();
        assertEquals(2000, buffer.size());
        assertEquals(MissingValue.MIS_C, buffer.getValue(0));
        assertEquals(MissingValue.MIS_D, buffer.getValue(1500));
        assertEquals(MIN_AS_LONG, buffer.getValue(1999));
        assertEquals(1499L, buffer.getValue(1499));

        buffer.setValue(2000, MissingValue.MIS_E);
        assertEquals(MissingValue.MIS_E, buffer.getValue(2000));
        assertEquals(MissingValue.MIS_C, buffer.getValue(0));
    }


    @Test
    void testCodesSurviveSetExpectedSize()
    {
        buffer.setValue(0, MissingValue.MIS_F);
        buffer.setExpectedSize(4096);
        buffer.setValue(4000, MissingValue.MIS_G);
        assertEquals(MissingValue.MIS_F, buffer.getValue(0));
        assertEquals(MissingValue.MIS_G, buffer.getValue(4000));
    }


    @Test
    void testCodeTableGrowsWhenFirstAllocatedBeforeGrowth()
    {
        buffer.setValue(0, MissingValue.MIS_H);
        buffer.setValue(600, MissingValue.MIS_I);
        assertEquals(MissingValue.MIS_H, buffer.getValue(0));
        assertEquals(MissingValue.MIS_I, buffer.getValue(600));
        // A slot below size that was never written holds the array default 0, a present value.
        assertFalse(buffer.isMissing(599));
        assertEquals(0L, buffer.getValue(599));
    }
}
