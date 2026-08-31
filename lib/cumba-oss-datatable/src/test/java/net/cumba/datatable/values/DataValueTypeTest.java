package net.cumba.datatable.values;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

// Tests assert ordinal stability — the call to .ordinal() is the test subject.
@SuppressWarnings("EnumOrdinal")
class DataValueTypeTest
{

    @Test
    void testAllTypesExist()
    {
        assertNotNull(DataValueType.STRING);
        assertNotNull(DataValueType.LONG);
        assertNotNull(DataValueType.DOUBLE);
        assertNotNull(DataValueType.BOOLEAN);
        assertNotNull(DataValueType.MISSING);
        assertNotNull(DataValueType.OTHER);
    }


    @Test
    void testValuesCount()
    {
        assertEquals(6, DataValueType.values().length);
    }


    @Test
    void testValueOf()
    {
        assertEquals(DataValueType.STRING, DataValueType.valueOf("STRING"));
        assertEquals(DataValueType.LONG, DataValueType.valueOf("LONG"));
        assertEquals(DataValueType.DOUBLE, DataValueType.valueOf("DOUBLE"));
        assertEquals(DataValueType.BOOLEAN, DataValueType.valueOf("BOOLEAN"));
        assertEquals(DataValueType.MISSING, DataValueType.valueOf("MISSING"));
        assertEquals(DataValueType.OTHER, DataValueType.valueOf("OTHER"));
    }


    @Test
    void testOrdinal()
    {
        // Verify order is as defined in enum
        assertEquals(0, DataValueType.STRING.ordinal());
        assertEquals(1, DataValueType.LONG.ordinal());
        assertEquals(2, DataValueType.DOUBLE.ordinal());
        assertEquals(3, DataValueType.BOOLEAN.ordinal());
        assertEquals(4, DataValueType.MISSING.ordinal());
        assertEquals(5, DataValueType.OTHER.ordinal());
    }
}
