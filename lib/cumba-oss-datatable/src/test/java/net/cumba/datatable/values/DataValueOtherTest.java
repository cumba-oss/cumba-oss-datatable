package net.cumba.datatable.values;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.junit.jupiter.api.Test;

// Test exercises legacy java.util.Date code paths in the production code.
@SuppressWarnings("JavaUtilDate")
class DataValueOtherTest
{

    @Test
    void testGetValue()
    {
        Object obj = new Object();
        DataValueOther dv = new DataValueOther(obj);
        assertSame(obj, dv.getValue());
    }


    @Test
    void testConstructorRejectsNull()
    {
        assertThrows(NullPointerException.class, () -> new DataValueOther(null));
    }


    @Test
    void testGetType()
    {
        DataValueOther dv = new DataValueOther("anything");
        assertEquals(DataValueType.OTHER, dv.getType());
    }


    @Test
    void testGetValueAsString()
    {
        DataValueOther dv = new DataValueOther(123);
        assertEquals("123", dv.getValueAsString());
    }


    @Test
    void testGetValueAsDouble()
    {
        // With a non-Number object
        DataValueOther dv = new DataValueOther("text");
        assertTrue(Double.isNaN(dv.getValueAsDouble()));
    }


    @Test
    void testGetValueAsDoubleWithNumber()
    {
        // With a Number object, default IDataValue implementation should work
        DataValueOther dv = new DataValueOther(Integer.valueOf(42));
        assertEquals(42.0, dv.getValueAsDouble(), 0.0001);
    }


    @Test
    void testIsMissingOrInvalid()
    {
        DataValueOther dv = new DataValueOther("value");
        assertFalse(dv.isMissingOrInvalid());
    }


    @Test
    void testToString()
    {
        DataValueOther dv = new DataValueOther("hello");
        assertEquals("hello", dv.toString());
    }


    @Test
    void testToStringInteger()
    {
        DataValueOther dv = new DataValueOther(Integer.valueOf(99));
        assertEquals("99", dv.toString());
    }


    @Test
    void testToStringCustomObject()
    {
        Date date = new Date(0); // epoch
        DataValueOther dv = new DataValueOther(date);
        assertEquals(date.toString(), dv.toString());
    }


    @Test
    void testEquals()
    {
        DataValueOther dv1 = new DataValueOther("test");
        DataValueOther dv2 = new DataValueOther("test");
        DataValueOther dv3 = new DataValueOther("other");

        assertEquals(dv1, dv2);
        assertNotEquals(dv1, dv3);
    }


    @Test
    void testBuilder()
    {
        Object obj = new int[]
        {
                1, 2, 3
        };
        DataValueOther dv = DataValueOther.builder().value(obj).build();
        assertSame(obj, dv.getValue());
    }
}
