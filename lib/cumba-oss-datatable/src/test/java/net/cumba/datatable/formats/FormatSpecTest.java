package net.cumba.datatable.formats;

// OSS-IDENTITY datatable-temporal-classifier: byte-identical in cumba-datatable and
// cumba-oss-datatable. Edit in cumba-datatable, then copy; check_oss_identity.py fails on any
// divergence.

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FormatSpecTest
{

    @Test
    void testNull()
    {
        assertNull(FormatSpec.getFormatSpec(null));
    }


    @Test
    void testSimpleFormat()
    {
        FormatSpec fs = FormatSpec.getFormatSpec("F8.2");
        assertEquals("F", fs.getName());
        assertEquals(8, fs.getWidth());
        assertEquals(2, fs.getDecimals());
    }


    @Test
    void testBestFormat()
    {
        FormatSpec fs = FormatSpec.getFormatSpec("BEST12.");
        assertEquals("BEST", fs.getName());
        assertEquals(12, fs.getWidth());
        assertEquals(0, fs.getDecimals());
    }


    @Test
    void testCharFormat()
    {
        FormatSpec fs = FormatSpec.getFormatSpec("$CHAR20.");
        assertEquals("$CHAR", fs.getName());
        assertEquals(20, fs.getWidth());
        assertEquals(0, fs.getDecimals());
    }


    @Test
    void testNoDot()
    {
        FormatSpec fs = FormatSpec.getFormatSpec("F8");
        assertEquals("F", fs.getName());
        assertEquals(8, fs.getWidth());
        assertEquals(0, fs.getDecimals());
    }


    @Test
    void testWidthOnly()
    {
        FormatSpec fs = FormatSpec.getFormatSpec("8.");
        assertEquals("", fs.getName());
        assertEquals(8, fs.getWidth());
        assertEquals(0, fs.getDecimals());
    }


    @Test
    void testNameOnly()
    {
        FormatSpec fs = FormatSpec.getFormatSpec("BEST.");
        assertEquals("BEST", fs.getName());
        assertEquals(0, fs.getWidth());
        assertEquals(0, fs.getDecimals());
    }


    @Test
    void testDotOnly()
    {
        FormatSpec fs = FormatSpec.getFormatSpec(".");
        assertEquals("", fs.getName());
        assertEquals(0, fs.getWidth());
        assertEquals(0, fs.getDecimals());
    }


    @Test
    void testCaseInsensitive()
    {
        FormatSpec fs = FormatSpec.getFormatSpec("best12.3");
        assertEquals("BEST", fs.getName());
        assertEquals(12, fs.getWidth());
        assertEquals(3, fs.getDecimals());
    }


    @Test
    void testUnderscoreName()
    {
        FormatSpec fs = FormatSpec.getFormatSpec("my_fmt8.2");
        assertEquals("MY_FMT", fs.getName());
        assertEquals(8, fs.getWidth());
        assertEquals(2, fs.getDecimals());
    }


    @Test
    void testDollarSign()
    {
        FormatSpec fs = FormatSpec.getFormatSpec("$8.");
        assertEquals("$", fs.getName());
        assertEquals(8, fs.getWidth());
        assertEquals(0, fs.getDecimals());
    }


    @Test
    void testBuilder()
    {
        FormatSpec fs = FormatSpec.builder().name("X").width(5).decimals(2).build();
        assertEquals("X", fs.getName());
        assertEquals(5, fs.getWidth());
        assertEquals(2, fs.getDecimals());
    }


    @Test
    void testToString()
    {
        FormatSpec fs = FormatSpec.builder().name("TEST").width(10).decimals(3).build();
        assertNotNull(fs.toString());
    }


    @Test
    void aCharacterFormatIsOneWhoseNameStartsWithADollar()
    {
        assertTrue(FormatSpec.isCharacterFormat("$CHAR20."));
        assertTrue(FormatSpec.isCharacterFormat("$8."));
        assertTrue(FormatSpec.isCharacterFormat("$"));
        assertTrue(FormatSpec.isCharacterFormat("  $SEX."), "leading white space is ignored");
        assertFalse(FormatSpec.isCharacterFormat("DATE9."));
        assertFalse(FormatSpec.isCharacterFormat("8."));
        assertFalse(FormatSpec.isCharacterFormat("SEX."));
        assertFalse(FormatSpec.isCharacterFormat("A$B."), "only a leading $ counts");
        assertFalse(FormatSpec.isCharacterFormat(""));
        assertFalse(FormatSpec.isCharacterFormat("   "));
        assertFalse(FormatSpec.isCharacterFormat(null));
    }


    @Test
    void aFormatFitsTheStorageOfItsOwnKindOnly()
    {
        // a character column takes a $ format only
        assertTrue(FormatSpec.fitsStorage("$CHAR8.", true));
        assertFalse(FormatSpec.fitsStorage("DATE9.", true));
        assertFalse(FormatSpec.fitsStorage("8.", true));
        // a numeric column takes anything but a $ format
        assertTrue(FormatSpec.fitsStorage("BEST12.", false));
        assertTrue(FormatSpec.fitsStorage("DATE9.", false));
        assertFalse(FormatSpec.fitsStorage("$CHAR8.", false));
        assertFalse(FormatSpec.fitsStorage(" $8.", false));
    }


    @Test
    void noFormatFitsEveryColumn()
    {
        for (boolean character : new boolean[]
        {
                true, false
        })
        {
            assertTrue(FormatSpec.fitsStorage(null, character));
            assertTrue(FormatSpec.fitsStorage("", character));
            assertTrue(FormatSpec.fitsStorage("  ", character));
        }
    }
}
