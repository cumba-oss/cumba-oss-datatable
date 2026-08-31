package net.cumba.datatable.provider.csv;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link CsvRecord}.
 */
class CsvRecordTest
{

    @Test
    void testGetColumnCount()
    {
        CsvRecord csvRec = new CsvRecord(new String[]
        {
                "a", "b", "c"
        });
        assertEquals(3, csvRec.getColumnCount());
    }


    @Test
    void testGetColumnCountEmpty()
    {
        CsvRecord csvRec = new CsvRecord(new String[0]);
        assertEquals(0, csvRec.getColumnCount());
    }


    @Test
    void testGetValue()
    {
        CsvRecord csvRec = new CsvRecord(new String[]
        {
                "hello", "world"
        });
        assertEquals("hello", csvRec.getValue(0));
        assertEquals("world", csvRec.getValue(1));
    }


    @Test
    void testGetValueNullReturnsEmpty()
    {
        CsvRecord csvRec = new CsvRecord(new String[]
        {
                null, "val"
        });
        assertEquals("", csvRec.getValue(0));
        assertEquals("val", csvRec.getValue(1));
    }


    @Test
    void testGetValueNegativeIndexThrows()
    {
        CsvRecord csvRec = new CsvRecord(new String[]
        {
                "a"
        });
        assertThrows(IndexOutOfBoundsException.class, () -> csvRec.getValue(-1));
    }


    @Test
    void testGetValueIndexTooHighThrows()
    {
        CsvRecord csvRec = new CsvRecord(new String[]
        {
                "a"
        });
        assertThrows(IndexOutOfBoundsException.class, () -> csvRec.getValue(1));
    }


    @Test
    void testGetDoubleValueValid()
    {
        CsvRecord csvRec = new CsvRecord(new String[]
        {
                "3.14", "-1.5", "0"
        });
        assertEquals(3.14, csvRec.getDoubleValue(0), 0.001);
        assertEquals(-1.5, csvRec.getDoubleValue(1), 0.001);
        assertEquals(0.0, csvRec.getDoubleValue(2), 0.001);
    }


    @ParameterizedTest
    @NullSource
    @ValueSource(strings =
    {
            "abc", ""
    })
    void testGetDoubleValueReturnsNaN(String aInput)
    {
        CsvRecord csvRec = new CsvRecord(new String[]
        {
                aInput
        });
        assertTrue(Double.isNaN(csvRec.getDoubleValue(0)));
    }


    @Test
    void testGetLongValueValid()
    {
        CsvRecord csvRec = new CsvRecord(new String[]
        {
                "42", "-7", "0"
        });
        assertEquals(42L, csvRec.getLongValue(0));
        assertEquals(-7L, csvRec.getLongValue(1));
        assertEquals(0L, csvRec.getLongValue(2));
    }


    @Test
    void testGetLongValueNonNumericThrows()
    {
        CsvRecord csvRec = new CsvRecord(new String[]
        {
                "abc"
        });
        assertThrows(IllegalStateException.class, () -> csvRec.getLongValue(0));
    }


    @Test
    void testGetLongValueDecimalThrows()
    {
        CsvRecord csvRec = new CsvRecord(new String[]
        {
                "3.14"
        });
        assertThrows(IllegalStateException.class, () -> csvRec.getLongValue(0));
    }


    @Test
    void testIsDoubleOrMissingNumeric()
    {
        CsvRecord csvRec = new CsvRecord(new String[]
        {
                "1.5", "42", "-3.14"
        });
        assertTrue(csvRec.isDoubleOrMissing(0));
        assertTrue(csvRec.isDoubleOrMissing(1));
        assertTrue(csvRec.isDoubleOrMissing(2));
    }


    @Test
    void testIsDoubleOrMissingEmpty()
    {
        CsvRecord csvRec = new CsvRecord(new String[]
        {
                "", null
        });
        assertTrue(csvRec.isDoubleOrMissing(0));
        assertTrue(csvRec.isDoubleOrMissing(1));
    }


    @Test
    void testIsDoubleOrMissingDot()
    {
        CsvRecord csvRec = new CsvRecord(new String[]
        {
                "."
        });
        assertTrue(csvRec.isDoubleOrMissing(0));
    }


    @Test
    void testIsDoubleOrMissingString()
    {
        CsvRecord csvRec = new CsvRecord(new String[]
        {
                "hello"
        });
        assertFalse(csvRec.isDoubleOrMissing(0));
    }


    @Test
    void testIsLongValueValid()
    {
        CsvRecord csvRec = new CsvRecord(new String[]
        {
                "42", "0", "-100"
        });
        assertTrue(csvRec.isLongValue(0));
        assertTrue(csvRec.isLongValue(1));
        assertTrue(csvRec.isLongValue(2));
    }


    @Test
    void testIsLongValueDecimal()
    {
        CsvRecord csvRec = new CsvRecord(new String[]
        {
                "3.14"
        });
        assertFalse(csvRec.isLongValue(0));
    }


    @Test
    void testIsLongValueString()
    {
        CsvRecord csvRec = new CsvRecord(new String[]
        {
                "abc"
        });
        assertFalse(csvRec.isLongValue(0));
    }


    @Test
    void testGetValuesReturnsDefensiveCopy()
    {
        String[] original =
        {
                "a", "b"
        };
        CsvRecord csvRec = new CsvRecord(original);
        String[] copy = csvRec.getValues();
        assertArrayEquals(original, copy);
        assertNotSame(original, copy);
        // mutating copy should not affect the record
        copy[0] = "mutated";
        assertEquals("a", csvRec.getValue(0));
    }


    @Test
    void testConstructorRejectsNull()
    {
        assertThrows(NullPointerException.class, () -> new CsvRecord(null));
    }

}
