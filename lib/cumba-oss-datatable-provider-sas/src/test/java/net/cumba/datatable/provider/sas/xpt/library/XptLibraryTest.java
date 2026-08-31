package net.cumba.datatable.provider.sas.xpt.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.net.URI;
import java.util.List;
import java.util.stream.Stream;
import net.cumba.datatable.provider.sas.xpt.XptProviderSupplier;
import org.junit.jupiter.api.Test;

@SuppressWarnings("resource")
class XptLibraryTest
{

    private static final URI TEST_URI = URI.create("file:///data/test.xpt");

    // ==================== Constructor ====================

    @Test
    void testConstructor()
    {

        XptLibrary lib = new XptLibrary("TEST", "Test Library", TEST_URI);

        assertEquals("TEST", lib.getName());
        assertEquals("Test Library", lib.getLabel());
        assertEquals(TEST_URI, lib.getUri());
    }


    @Test
    void testFileInfo()
    {
        XptLibrary lib = new XptLibrary("TEST", null, TEST_URI);
        assertSame(XptProviderSupplier.FI_XPT, lib.getFileInfo());
    }

    // ==================== getType ====================


    @Test
    void testGetType()
    {
        XptLibrary lib = new XptLibrary("TEST", null, TEST_URI);
        assertEquals("Xport", lib.getType());
    }

    // ==================== getMembers ====================


    @Test
    void testGetMembersEmptyWhenNotSet()
    {
        XptLibrary lib = new XptLibrary("TEST", null, TEST_URI);
        Stream<XptLibraryMember> members = lib.getMembers();
        assertEquals(0, members.count());
    }


    @Test
    void testGetMembersAfterSet()
    {
        XptLibrary lib = new XptLibrary("TEST", null, TEST_URI);

        XptLibraryMember m = XptLibraryMember.builder().library(lib).name("DM")
                .label("Demographics").uri(URI.create("file:///data/test.xpt#DM")).build();

        lib.setMembers(List.of(m));

        List<XptLibraryMember> members = lib.getMembers().toList();
        assertEquals(1, members.size());
        assertEquals("DM", members.get(0).getName());
    }


    @Test
    void testGetMembersMultiple()
    {
        XptLibrary lib = new XptLibrary("TEST", null, TEST_URI);

        XptLibraryMember m1 = XptLibraryMember.builder().library(lib).name("DM")
                .uri(URI.create("file:///test.xpt#DM")).build();
        XptLibraryMember m2 = XptLibraryMember.builder().library(lib).name("AE")
                .uri(URI.create("file:///test.xpt#AE")).build();

        lib.setMembers(List.of(m1, m2));

        assertEquals(2, lib.getMembers().count());
    }
}
