package net.cumba.datatable.values;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DataValueMissingTest
{

    @Test
    void testGetValue()
    {
        DataValueMissing dv = new DataValueMissing(MissingValue.MIS);
        assertSame(MissingValue.MIS, dv.getValue());
    }


    @Test
    void testConstructorWithInt()
    {
        DataValueMissing dv = new DataValueMissing(64); // MIS = 64
        assertSame(MissingValue.MIS, dv.getValue());
    }


    @Test
    void testConstructorWithInvalidInt()
    {
        DataValueMissing dv = new DataValueMissing(999);
        assertSame(MissingValue.MIS_ERROR, dv.getValue());
    }


    @Test
    void testGetValueAsDouble()
    {
        DataValueMissing dv = new DataValueMissing(MissingValue.MIS);
        assertTrue(Double.isNaN(dv.getValueAsDouble()));
    }


    @Test
    void testIsMissingOrInvalid()
    {
        DataValueMissing dv = new DataValueMissing(MissingValue.MIS);
        assertTrue(dv.isMissingOrInvalid());
    }


    @Test
    void testToString()
    {
        DataValueMissing dv = new DataValueMissing(MissingValue.MIS);
        assertEquals(".", dv.toString());
    }


    @Test
    void testGetValueAsString()
    {
        DataValueMissing dv = new DataValueMissing(MissingValue.MIS);
        assertEquals(".", dv.getValueAsString());
    }
}
