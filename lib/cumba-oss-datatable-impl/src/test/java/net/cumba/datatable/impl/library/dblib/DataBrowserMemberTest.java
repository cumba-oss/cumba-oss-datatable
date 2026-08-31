package net.cumba.datatable.impl.library.dblib;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import net.cumba.datatable.impl.library.dblib.beans.DataBrowserLibraryBean;
import org.junit.jupiter.api.Test;

class DataBrowserMemberTest
{

    private static final URI LIB_URI = URI.create("file:///lib/mylib.dblib");

    private DataBrowserLibrary createLibrary()
    {
        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("TestLib").build();
        return new DataBrowserLibrary(LIB_URI, bean);
    }


    @Test
    void testConstructor()
    {
        DataBrowserLibrary lib = createLibrary();
        URI memberUri = URI.create("file:///data/dm.csv");
        DataBrowserMember member = new DataBrowserMember(lib, memberUri, "DM", "Demographics");

        assertEquals("DM", member.getName());
        assertEquals("Demographics", member.getLabel());
        assertEquals(memberUri, member.getUri());
        assertSame(lib, member.getLibrary());
    }


    @Test
    void testNullLabel()
    {
        DataBrowserLibrary lib = createLibrary();
        URI memberUri = URI.create("file:///data/dm.csv");
        DataBrowserMember member = new DataBrowserMember(lib, memberUri, "DM", null);

        assertNull(member.getLabel());
    }


    @Test
    void testEqualsAndHashCode()
    {
        DataBrowserLibrary lib = createLibrary();
        URI uri = URI.create("file:///data/dm.csv");
        DataBrowserMember m1 = new DataBrowserMember(lib, uri, "DM", "Demographics");
        DataBrowserMember m2 = new DataBrowserMember(lib, uri, "DM", "Other Label");

        // Equality is based on library and URI only
        assertEquals(m1, m2);
        assertEquals(m1.hashCode(), m2.hashCode());
    }


    @Test
    void testNotEqualsDifferentUri()
    {
        DataBrowserLibrary lib = createLibrary();
        DataBrowserMember m1 = new DataBrowserMember(lib, URI.create("file:///data/dm.csv"), "DM",
                null);
        DataBrowserMember m2 = new DataBrowserMember(lib, URI.create("file:///data/ae.csv"), "AE",
                null);

        assertNotEquals(m1, m2);
    }
}
