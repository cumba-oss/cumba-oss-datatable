package net.cumba.datatable.library;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import org.junit.jupiter.api.Test;

/**
 * Exercises the static helper methods on {@link ILibraryProvider}.
 */
class ILibraryProviderStaticsTest
{

    @Test
    void defaultLibraryName_nullUri_returnsFallback()
    {
        assertEquals("library", ILibraryProvider.defaultLibraryName(null, null));
    }


    @Test
    void defaultLibraryName_filePath_stripsDirAndExtension()
    {
        String name = ILibraryProvider
                .defaultLibraryName(URI.create("file:///data/studies/CDISCPILOT01.xpt"), null);
        assertEquals("CDISCPILOT01", name);
    }


    @Test
    void defaultLibraryName_trailingSlash_isStripped()
    {
        // path is "/data/studies/" — trailing slash stripped, last segment is "studies"
        String name = ILibraryProvider.defaultLibraryName(URI.create("file:///data/studies/"),
                null);
        assertEquals("studies", name);
    }


    @Test
    void defaultLibraryName_noExtension_returnsAsIs()
    {
        // CDT.getBeforeLast returns the original string if the separator is absent
        String name = ILibraryProvider.defaultLibraryName(URI.create("file:///data/studies/abc"),
                null);
        assertEquals("abc", name);
    }


    @Test
    void defaultLibraryName_hostOnly_returnsHost()
    {
        String name = ILibraryProvider.defaultLibraryName(URI.create("https://example.com"), null);
        assertEquals("example.com", name);
    }


    @Test
    void defaultLibraryName_blankPathHostNull_returnsFullUri()
    {
        // urn: schemes have no host and no path-like segment
        URI uri = URI.create("urn:foo:bar");
        String name = ILibraryProvider.defaultLibraryName(uri, null);
        // The fallback returns the full URI string
        assertEquals(uri.toString(), name);
    }


    @Test
    void resolveLibraryName_nullCustomDefault_returnsUriDefault()
    {
        URI uri = URI.create("file:///abc.xpt");
        assertEquals("abc", ILibraryProvider.resolveLibraryName(uri, null, null));
    }


    @Test
    void resolveLibraryName_blankCustomDefault_returnsUriDefault()
    {
        URI uri = URI.create("file:///abc.xpt");
        assertEquals("abc", ILibraryProvider.resolveLibraryName(uri, null, ""));
    }


    @Test
    void resolveLibraryName_customDefault_isUsed()
    {
        URI uri = URI.create("file:///abc.xpt");
        assertEquals("CustomDef", ILibraryProvider.resolveLibraryName(uri, null, "CustomDef"));
    }
}
