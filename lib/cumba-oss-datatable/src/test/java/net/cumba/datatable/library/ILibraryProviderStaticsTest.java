package net.cumba.datatable.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import net.cumba.datatable.io.Property;
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
    void libraryNameProperty_uriDefault_isNonNull()
    {
        Property p = ILibraryProvider.libraryNameProperty(URI.create("file:///x.xpt"), null);
        assertNotNull(p);
        assertEquals(ILibraryProvider.LIBRARY_NAME_PROPERTY_NAME, p.name());
        assertEquals("x", p.defaultValue());
    }


    @Test
    void libraryNameProperty_customDefault_isUsed()
    {
        Property p = ILibraryProvider.libraryNameProperty(URI.create("file:///x.xpt"), null,
                "CustomName");
        assertEquals("CustomName", p.defaultValue());
    }


    @Test
    void libraryNameProperty_blankCustomDefault_fallsBackToUriDefault()
    {
        Property p = ILibraryProvider.libraryNameProperty(URI.create("file:///abc.xpt"), null, "");
        assertEquals("abc", p.defaultValue());
    }


    @Test
    void libraryNameProperty_nullCustomDefault_fallsBackToUriDefault()
    {
        Property p = ILibraryProvider.libraryNameProperty(URI.create("file:///abc.xpt"), null,
                null);
        assertEquals("abc", p.defaultValue());
    }


    @Test
    void resolveLibraryName_propertyPresent_returnsUserValue()
    {
        URI uri = URI.create("file:///abc.xpt");
        Property prop = ILibraryProvider.libraryNameProperty(uri, null);
        Map<Property, String> props = new HashMap<>();
        props.put(prop, "ChosenName");

        assertEquals("ChosenName", ILibraryProvider.resolveLibraryName(uri, null, props));
    }


    @Test
    void resolveLibraryName_propertyMissing_returnsDefault()
    {
        URI uri = URI.create("file:///abc.xpt");
        assertEquals("abc", ILibraryProvider.resolveLibraryName(uri, null, new HashMap<>()));
    }


    @Test
    void resolveLibraryName_propertyBlankValue_returnsDefault()
    {
        URI uri = URI.create("file:///abc.xpt");
        Property prop = ILibraryProvider.libraryNameProperty(uri, null);
        Map<Property, String> props = new HashMap<>();
        props.put(prop, "");

        assertEquals("abc", ILibraryProvider.resolveLibraryName(uri, null, props));
    }


    @Test
    void resolveLibraryName_nullProperties_returnsDefault()
    {
        URI uri = URI.create("file:///abc.xpt");
        assertEquals("abc", ILibraryProvider.resolveLibraryName(uri, null, null));
    }


    @Test
    void resolveLibraryName_withCustomDefault_propertyMissing_returnsCustomDefault()
    {
        URI uri = URI.create("file:///abc.xpt");
        assertEquals("CustomDef",
                ILibraryProvider.resolveLibraryName(uri, null, new HashMap<>(), "CustomDef"));
    }


    @Test
    void resolveLibraryName_withCustomDefault_propertyPresent_returnsUserValue()
    {
        URI uri = URI.create("file:///abc.xpt");
        Property prop = ILibraryProvider.libraryNameProperty(uri, null, "CustomDef");
        Map<Property, String> props = new HashMap<>();
        props.put(prop, "UserChosen");

        assertEquals("UserChosen",
                ILibraryProvider.resolveLibraryName(uri, null, props, "CustomDef"));
    }
}
