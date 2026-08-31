package net.cumba.datatable.values;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DataValueDoubleTest
{

    @Test
    void testGetValue()
    {
        DataValueDouble dv = new DataValueDouble(3.14);
        assertEquals(Double.valueOf(3.14), dv.getValue());
    }


    @Test
    void testGetType()
    {
        DataValueDouble dv = new DataValueDouble(0.0);
        assertEquals(DataValueType.DOUBLE, dv.getType());
    }


    @Test
    void testGetValueAsNumber()
    {
        DataValueDouble dv = new DataValueDouble(99.9);
        assertEquals(99.9, dv.getValueAsNumber().doubleValue(), 0.0001);
    }


    @Test
    void testGetValueAsDouble()
    {
        DataValueDouble dv = new DataValueDouble(1.5);
        assertEquals(1.5, dv.getValueAsDouble(), 0.0001);
    }


    @Test
    void testGetValueAsLong()
    {
        DataValueDouble dv = new DataValueDouble(10.7);
        assertEquals(10L, dv.getValueAsLong());
    }


    @Test
    void testGetValueAsStringWholeNumber()
    {
        // When close to integer, should return without decimal
        DataValueDouble dv = new DataValueDouble(42.0);
        assertEquals("42", dv.getValueAsString());
    }


    @Test
    void testGetValueAsStringDecimal()
    {
        DataValueDouble dv = new DataValueDouble(3.14159);
        assertEquals("3.14159", dv.getValueAsString());
    }


    @Test
    void testGetValueAsStringNearInteger()
    {
        // Value very close to integer should be displayed as integer
        DataValueDouble dv = new DataValueDouble(100.0000000000001);
        assertEquals("100", dv.getValueAsString());
    }


    @Test
    void testIsMissingOrInvalid()
    {
        DataValueDouble dv = new DataValueDouble(1.0);
        assertFalse(dv.isMissingOrInvalid());
    }


    @Test
    void testIsMissingOrInvalidNaN()
    {
        DataValueDouble dv = new DataValueDouble(Double.NaN);
        // NaN should be considered missing/invalid
        assertTrue(dv.isMissingOrInvalid());
    }


    @Test
    void testToString()
    {
        DataValueDouble dv = new DataValueDouble(5.5);
        assertEquals("5.5", dv.toString());
    }


    @Test
    void testToStringInteger()
    {
        DataValueDouble dv = new DataValueDouble(100.0);
        assertEquals("100", dv.toString());
    }


    @Test
    void testNegativeValue()
    {
        DataValueDouble dv = new DataValueDouble(-123.456);
        assertEquals(-123.456, dv.getValueAsDouble(), 0.0001);
    }


    @Test
    void testEquals()
    {
        DataValueDouble dv1 = new DataValueDouble(1.5);
        DataValueDouble dv2 = new DataValueDouble(1.5);
        DataValueDouble dv3 = new DataValueDouble(2.5);

        assertEquals(dv1, dv2);
        assertNotEquals(dv1, dv3);
    }


    @Test
    void testEpsilonConstant()
    {
        assertEquals(1E-14, DataValueDouble.EPSILON, 0);
    }


    @Test
    void testBuilder()
    {
        DataValueDouble dv = DataValueDouble.builder().value(9.99).build();
        assertEquals(9.99, dv.getValueAsDouble(), 0.0001);
    }


    @Test
    void testSpecialValues()
    {
        DataValueDouble dvInf = new DataValueDouble(Double.POSITIVE_INFINITY);
        assertTrue(Double.isInfinite(dvInf.getValueAsDouble()));

        DataValueDouble dvNegInf = new DataValueDouble(Double.NEGATIVE_INFINITY);
        assertTrue(Double.isInfinite(dvNegInf.getValueAsDouble()));
    }
}
