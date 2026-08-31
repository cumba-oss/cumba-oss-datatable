package net.cumba.datatable.values;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DataValueBooleanTest
{

    @Test
    void testGetValueTrue()
    {
        DataValueBoolean dv = new DataValueBoolean(true);
        assertEquals(true, dv.getValue());
    }


    @Test
    void testGetValueFalse()
    {
        DataValueBoolean dv = new DataValueBoolean(false);
        assertEquals(false, dv.getValue());
    }


    @Test
    void testGetBooleanTrue()
    {
        DataValueBoolean dv = new DataValueBoolean(true);
        assertTrue(dv.getBoolean());
    }


    @Test
    void testGetBooleanFalse()
    {
        DataValueBoolean dv = new DataValueBoolean(false);
        assertFalse(dv.getBoolean());
    }


    @Test
    void testGetType()
    {
        DataValueBoolean dv = new DataValueBoolean(true);
        assertEquals(DataValueType.BOOLEAN, dv.getType());
    }


    @Test
    void testGetValueAsNumberTrue()
    {
        DataValueBoolean dv = new DataValueBoolean(true);
        assertEquals(1, dv.getValueAsNumber());
    }


    @Test
    void testGetValueAsNumberFalse()
    {
        DataValueBoolean dv = new DataValueBoolean(false);
        assertEquals(0, dv.getValueAsNumber());
    }


    @Test
    void testGetValueAsString()
    {
        DataValueBoolean dvTrue = new DataValueBoolean(true);
        DataValueBoolean dvFalse = new DataValueBoolean(false);

        assertEquals("true", dvTrue.getValueAsString());
        assertEquals("false", dvFalse.getValueAsString());
    }


    @Test
    void testIsMissingOrInvalid()
    {
        DataValueBoolean dv = new DataValueBoolean(true);
        assertFalse(dv.isMissingOrInvalid());
    }


    @Test
    void testToStringTrue()
    {
        DataValueBoolean dv = new DataValueBoolean(true);
        assertEquals("true", dv.toString());
    }


    @Test
    void testToStringFalse()
    {
        DataValueBoolean dv = new DataValueBoolean(false);
        assertEquals("false", dv.toString());
    }


    @Test
    void testEquals()
    {
        DataValueBoolean dv1 = new DataValueBoolean(true);
        DataValueBoolean dv2 = new DataValueBoolean(true);
        DataValueBoolean dv3 = new DataValueBoolean(false);

        assertEquals(dv1, dv2);
        assertNotEquals(dv1, dv3);
    }


    @Test
    void testBuilder()
    {
        DataValueBoolean dv = DataValueBoolean.builder().value(true).build();
        assertTrue(dv.getBoolean());
    }
}
