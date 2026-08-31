package net.cumba.datatable.values;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class DataValueLongTest
{

    @Test
    void testGetValue()
    {
        DataValueLong dv = new DataValueLong(42L);
        assertEquals(Long.valueOf(42L), dv.getValue());
    }


    @Test
    void testGetLong()
    {
        DataValueLong dv = new DataValueLong(123L);
        assertEquals(123L, dv.getLong());
    }


    @Test
    void testGetType()
    {
        DataValueLong dv = new DataValueLong(0L);
        assertEquals(DataValueType.LONG, dv.getType());
    }


    @Test
    void testGetValueAsNumber()
    {
        DataValueLong dv = new DataValueLong(999L);
        assertEquals(999L, dv.getValueAsNumber());
    }


    @Test
    void testGetValueAsLong()
    {
        DataValueLong dv = new DataValueLong(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, dv.getValueAsLong());
    }


    @Test
    void testGetValueAsDouble()
    {
        DataValueLong dv = new DataValueLong(100L);
        assertEquals(100.0, dv.getValueAsDouble(), 0.0001);
    }


    @Test
    void testGetValueAsString()
    {
        DataValueLong dv = new DataValueLong(456L);
        assertEquals("456", dv.getValueAsString());
    }


    @Test
    void testIsMissingOrInvalid()
    {
        DataValueLong dv = new DataValueLong(1L);
        assertFalse(dv.isMissingOrInvalid());
    }


    @Test
    void testToString()
    {
        DataValueLong dv = new DataValueLong(789L);
        assertEquals("789", dv.toString());
    }


    @Test
    void testNegativeValue()
    {
        DataValueLong dv = new DataValueLong(-500L);
        assertEquals(-500L, dv.getLong());
        assertEquals("-500", dv.toString());
    }


    @Test
    void testEquals()
    {
        DataValueLong dv1 = new DataValueLong(100L);
        DataValueLong dv2 = new DataValueLong(100L);
        DataValueLong dv3 = new DataValueLong(200L);

        assertEquals(dv1, dv2);
        assertNotEquals(dv1, dv3);
    }


    @Test
    void testBuilder()
    {
        DataValueLong dv = DataValueLong.builder().value(777L).build();
        assertEquals(777L, dv.getLong());
    }
}
