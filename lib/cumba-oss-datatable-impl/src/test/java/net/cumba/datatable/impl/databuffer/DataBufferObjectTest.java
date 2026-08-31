package net.cumba.datatable.impl.databuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.cumba.datatable.values.DataValueMissing;
import net.cumba.datatable.values.DataValueOther;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.IDataValue;
import net.cumba.datatable.values.MissingValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataBufferObjectTest
{

    private DataBufferObject buffer;

    @BeforeEach
    void setUp()
    {
        buffer = new DataBufferObject();
    }


    @Test
    void testInitialSize()
    {
        assertEquals(0, buffer.size());
    }


    @Test
    void testAddValue()
    {
        buffer.addValue("string");
        buffer.addValue(42);
        buffer.addValue(3.14);

        assertEquals(3, buffer.size());
        assertEquals("string", buffer.getValue(0));
        assertEquals(42L, buffer.getValue(1));
        assertEquals(3.14, buffer.getValue(2));
    }


    @Test
    void testSetValue()
    {
        buffer.setValue(0, "first");
        buffer.setValue(2, "third");

        assertEquals(3, buffer.size());
        assertEquals("first", buffer.getValue(0));
        assertEquals("third", buffer.getValue(2));
    }


    @Test
    void testSetValueNull()
    {
        buffer.setValue(0, null);

        assertEquals(1, buffer.size());
        assertNull(buffer.getValue(0));
    }


    @Test
    void testGetValueBeyondSize()
    {
        buffer.addValue("only");

        // Getting value beyond size returns MissingValue.MIS_ERROR
        assertEquals(MissingValue.MIS_ERROR, buffer.getValue(10));
    }


    @Test
    void testGetDataValue()
    {
        buffer.addValue("test");

        IDataValue dv = buffer.getDataValue(0, DataValueType.OTHER);
        assertTrue(dv instanceof DataValueOther);
        assertEquals("test", dv.getValue());
    }


    @Test
    void testGetDataValueBeyondSize()
    {
        buffer.addValue("only");

        IDataValue dv = buffer.getDataValue(10, DataValueType.OTHER);
        assertTrue(dv instanceof DataValueMissing);
    }


    @Test
    void testCanStore()
    {
        // Can store any object
        assertTrue(buffer.canStore("string"));
        assertTrue(buffer.canStore(42));
        assertTrue(buffer.canStore(3.14));
        assertTrue(buffer.canStore(new Object()));
        assertTrue(buffer.canStore(null));
    }


    @Test
    void testSetExpectedSize()
    {
        buffer.setExpectedSize(1000);
        buffer.addValue("test");
        assertEquals(1, buffer.size());
    }


    @Test
    void testTrimToSize()
    {
        buffer.addValue("one");
        buffer.addValue("two");
        buffer.trimToSize();
        assertEquals(2, buffer.size());
    }


    @Test
    void testAutoGrow()
    {
        // Add more than initial capacity (512)
        for (int i = 0; i < 1000; i++)
        {
            buffer.addValue("value" + i);
        }

        assertEquals(1000, buffer.size());
        assertEquals("value999", buffer.getValue(999));
    }


    @Test
    void testMixedTypes()
    {
        buffer.addValue("string");
        buffer.addValue(42);
        buffer.addValue(3.14);
        buffer.addValue(true);
        buffer.addValue(new int[]
        {
                1, 2, 3
        });

        assertEquals(5, buffer.size());
        assertEquals("string", buffer.getValue(0));
        assertEquals(42L, buffer.getValue(1));
        assertEquals(3.14d, buffer.getValue(2));
        assertEquals(true, buffer.getValue(3));
        assertTrue(buffer.getValue(4) instanceof int[]);
    }


    @Test
    void testOverwriteValue()
    {
        buffer.addValue("original");
        buffer.setValue(0, "modified");

        assertEquals(1, buffer.size());
        assertEquals("modified", buffer.getValue(0));
    }


    @Test
    void testCustomObjects()
    {
        Object customObj = new Object()
        {

            @Override
            public String toString()
            {
                return "custom";
            }
        };

        buffer.addValue(customObj);

        assertEquals(customObj, buffer.getValue(0));
        assertEquals("custom", buffer.getValue(0).toString());
    }
}
