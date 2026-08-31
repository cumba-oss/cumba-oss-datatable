package net.cumba.datatable.provider.sas.xpt.library;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.Test;

class XptLibraryMemberTest
{

    private static final URI LIB_URI = URI.create("file:///data/test.xpt");

    private static final URI MEMBER_URI = URI.create("file:///data/test.xpt#DM");

    private XptLibrary createLibrary()
    {
        return new XptLibrary("TEST", null, LIB_URI);
    }

    // ==================== Builder ====================


    @Test
    void testBuilderAllFields()
    {
        XptLibrary lib = createLibrary();
        DataTableColumnMeta col = DataTableColumnMeta.builder().index(0).name("AGE")
                .type(DataValueType.DOUBLE).build();

        XptLibraryMember member = XptLibraryMember.builder().library(lib).name("DM")
                .label("Demographics").uri(MEMBER_URI).columns(new DataTableColumnMeta[]
                {
                        col
                }).build();

        assertEquals("DM", member.getName());
        assertEquals("Demographics", member.getLabel());
        assertEquals(MEMBER_URI, member.getUri());
        assertSame(lib, member.getLibrary());
        assertEquals(1, member.getColumns().length);
    }


    @Test
    void testBuilderMinimal()
    {
        XptLibraryMember member = XptLibraryMember.builder().name("AE").uri(MEMBER_URI).build();

        assertEquals("AE", member.getName());
        assertNull(member.getLabel());
        assertNull(member.getLibrary());
        assertNull(member.getColumns());
    }
}
