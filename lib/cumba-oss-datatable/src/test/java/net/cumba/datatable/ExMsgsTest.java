package net.cumba.datatable;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ExMsgsTest
{

    @Test
    void testLongVersion()
    {
        String result = ExMsgs.indexOutOfBounds("row", 5L, 0L, 10L);
        assertEquals("Row 5 is out of range 0 <= row < 10", result);
    }


    @Test
    void testIntVersion()
    {
        String result = ExMsgs.indexOutOfBounds("row", 5, 0, 10);
        assertEquals("Row 5 is out of range 0 <= row < 10", result);
    }


    @Test
    void testCapitalization()
    {
        String result = ExMsgs.indexOutOfBounds("column", 3L, 0L, 8L);
        assertTrue(result.startsWith("Column "));
        assertTrue(result.contains("column"));
    }


    @Test
    void testNullName()
    {
        String result = ExMsgs.indexOutOfBounds(null, 5L, 0L, 10L);
        assertEquals("Value 5 is out of range 0 <= value < 10", result);
    }


    @Test
    void testBlankName()
    {
        String result = ExMsgs.indexOutOfBounds("", 5L, 0L, 10L);
        assertEquals("Value 5 is out of range 0 <= value < 10", result);

        String resultSpaces = ExMsgs.indexOutOfBounds("   ", 5, 0, 10);
        assertEquals("Value 5 is out of range 0 <= value < 10", resultSpaces);
    }


    @Test
    void testNegativeValues()
    {
        String result = ExMsgs.indexOutOfBounds("index", -3L, -10L, -1L);
        assertEquals("Index -3 is out of range -10 <= index < -1", result);
    }


    @Test
    void testZeroRange()
    {
        String result = ExMsgs.indexOutOfBounds("row", 0, 0, 0);
        assertEquals("Row 0 is out of range 0 <= row < 0", result);
    }
}
