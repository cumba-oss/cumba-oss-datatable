package net.cumba.datatable.values;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DataValueStringTest
{

    @Test
    void testGetValue()
    {
        DataValueString dv = new DataValueString("hello");
        assertEquals("hello", dv.getValue());
    }


    @Test
    void testConstructorRejectsNull()
    {
        assertThrows(NullPointerException.class, () -> new DataValueString(null));
    }


    @Test
    void testGetType()
    {
        DataValueString dv = new DataValueString("test");
        assertEquals(DataValueType.STRING, dv.getType());
    }


    @Test
    void testGetValueAsString()
    {
        DataValueString dv = new DataValueString("world");
        assertEquals("world", dv.getValueAsString());
    }


    @Test
    void testGetValueAsDouble()
    {
        DataValueString dv = new DataValueString("123");
        assertTrue(Double.isNaN(dv.getValueAsDouble()));
    }


    @Test
    void testIsMissingOrInvalid()
    {
        DataValueString dv = new DataValueString("value");
        assertFalse(dv.isMissingOrInvalid());
    }


    @Test
    void testToString()
    {
        DataValueString dv = new DataValueString("test");
        assertEquals("\"test\"", dv.toString());
    }


    @Test
    void testToStringEmptyString()
    {
        DataValueString dv = new DataValueString("");
        assertEquals("\"\"", dv.toString());
    }


    @Test
    void testEquals()
    {
        DataValueString dv1 = new DataValueString("abc");
        DataValueString dv2 = new DataValueString("abc");
        DataValueString dv3 = new DataValueString("xyz");

        assertEquals(dv1, dv2);
        assertNotEquals(dv1, dv3);
    }


    @Test
    void testBuilder()
    {
        DataValueString dv = DataValueString.builder().value("built").build();
        assertEquals("built", dv.getValue());
    }
}
