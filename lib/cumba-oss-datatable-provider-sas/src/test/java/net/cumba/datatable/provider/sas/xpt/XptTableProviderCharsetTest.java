package net.cumba.datatable.provider.sas.xpt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class XptTableProviderCharsetTest
{

    // ==================== Default charset ====================

    @Test
    void testDefaultCharsetIsUtf8()
    {
        XptTableProvider provider = new XptTableProvider();
        assertEquals(StandardCharsets.UTF_8, provider.getCharset());
    }


    @Test
    void testSetCharset()
    {
        XptTableProvider provider = new XptTableProvider();
        provider.setCharset(StandardCharsets.ISO_8859_1);
        assertEquals(StandardCharsets.ISO_8859_1, provider.getCharset());
    }


    @Test
    void testDefaultCharsetMatchesIteratorDefault()
    {
        XptTableProvider provider = new XptTableProvider();
        assertEquals(ObservationIteratorXpt.DEFAULT_CHARSET, provider.getCharset());
    }

    // ==================== F-D21: resolveCharset ====================


    @Test
    void testResolveCharsetWithNullNameReturnsDefault()
    {
        assertEquals(ObservationIteratorXpt.DEFAULT_CHARSET, XptTableProvider.resolveCharset(null));
    }


    @Test
    void testResolveCharsetWithBlankNameFallsBackToDefault()
    {
        assertEquals(ObservationIteratorXpt.DEFAULT_CHARSET, XptTableProvider.resolveCharset(""));
    }


    @Test
    void testResolveCharsetWithSupportedNameReturnsThatCharset()
    {
        assertEquals(StandardCharsets.ISO_8859_1, XptTableProvider.resolveCharset("ISO-8859-1"));
    }


    @Test
    void testResolveCharsetWithUnsupportedNameFallsBackToDefaultAndDoesNotThrow()
    {
        // F-D21: unsupported names log a WARN and fall back to the default rather than
        // throwing — the rest of the parse can still proceed (encoded values will be best-
        // effort decoded). We just verify the fallback contract here.
        assertEquals(ObservationIteratorXpt.DEFAULT_CHARSET,
                XptTableProvider.resolveCharset("NoSuchCharsetX-99"));
    }
}
