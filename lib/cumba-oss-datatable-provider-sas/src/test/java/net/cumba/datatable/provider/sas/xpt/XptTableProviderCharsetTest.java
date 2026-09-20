package net.cumba.datatable.provider.sas.xpt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import net.cumba.datatable.io.Property;
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
    void testResolveCharsetWithNullPropertiesReturnsDefault()
    {
        assertEquals(ObservationIteratorXpt.DEFAULT_CHARSET, XptTableProvider.resolveCharset(null));
    }


    @Test
    void testResolveCharsetWithBlankPropertyFallsBackToDefault()
    {
        Map<Property, String> props = Map.of(XptTableProvider.PROP_CHARSET, "");
        assertEquals(Charset.forName(XptTableProvider.PROP_CHARSET.defaultValue()),
                XptTableProvider.resolveCharset(props));
    }


    @Test
    void testResolveCharsetWithSupportedNameReturnsThatCharset()
    {
        Map<Property, String> props = Map.of(XptTableProvider.PROP_CHARSET, "ISO-8859-1");
        assertEquals(StandardCharsets.ISO_8859_1, XptTableProvider.resolveCharset(props));
    }


    @Test
    void testResolveCharsetWithUnsupportedNameFallsBackToDefaultAndDoesNotThrow()
    {
        // F-D21: unsupported names log a WARN and fall back to the default rather than
        // throwing — the rest of the parse can still proceed (encoded values will be best-
        // effort decoded). We just verify the fallback contract here.
        Map<Property, String> props = Map.of(XptTableProvider.PROP_CHARSET, "NoSuchCharsetX-99");
        assertEquals(ObservationIteratorXpt.DEFAULT_CHARSET,
                XptTableProvider.resolveCharset(props));
    }
}
