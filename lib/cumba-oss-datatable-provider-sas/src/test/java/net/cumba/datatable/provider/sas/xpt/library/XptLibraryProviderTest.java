package net.cumba.datatable.provider.sas.xpt.library;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.stream.Stream;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.library.IDataTableLibrary;
import net.cumba.datatable.library.ILibraryMember;
import net.cumba.datatable.provider.sas.xpt.XptProviderSupplier;
import net.cumba.datatable.values.DataValueType;
import net.cumba.sasutils.VariableType;
import net.cumba.sasutils.xpt.VariableXpt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class XptLibraryProviderTest
{

    private XptLibraryProvider provider;

    @BeforeEach
    void setUp()
    {
        provider = new XptLibraryProvider();
    }

    // ==================== getName / getDescription ====================


    @Test
    void testGetName()
    {
        assertEquals("XptLibraryProvider", provider.getName());
    }


    @Test
    void testGetDescription()
    {
        assertNotNull(provider.getDescription());
        assertFalse(provider.getDescription().isEmpty());
    }

    // ==================== getSupportedFileInfos ====================


    @Test
    void testGetSupportedFileInfos()
    {
        List<FileInfo> infos = provider.getSupportedFileInfos();
        assertNotNull(infos);
        assertFalse(infos.isEmpty());
    }

    // ==================== getLibraryNameFor ====================


    @Test
    void testGetLibraryNameForSimplePath()
    {
        URI uri = URI.create("file:///data/sdtm.xpt");
        assertEquals("SDTM", provider.getLibraryNameFor(uri));
    }


    @Test
    void testGetLibraryNameForPathWithSubdirs()
    {
        URI uri = URI.create("file:///root/study/data/mylib.xpt");
        assertEquals("MYLIB", provider.getLibraryNameFor(uri));
    }


    @Test
    void testGetLibraryNameForNoExtension()
    {
        URI uri = URI.create("file:///data/mylib");
        assertEquals("MYLIB", provider.getLibraryNameFor(uri));
    }


    @Test
    void testGetLibraryNameForNullPath()
    {
        URI uri = URI.create("mailto:test@example.com");
        assertEquals("", provider.getLibraryNameFor(uri));
    }


    @Test
    void testGetLibraryNameForUppercases()
    {
        URI uri = URI.create("file:///data/lowercase.xpt");
        assertEquals("LOWERCASE", provider.getLibraryNameFor(uri));
    }

    // ==================== getFullFormatName ====================


    @Test
    @Disabled("Requires a mock VariableXpt — behaviour is covered indirectly via integration tests")
    void testGetFullFormatNameReturnsNullWhenNoFormat() // NOSONAR S2699 — @Disabled placeholder; no
                                                        // body by design
    {
        // Would need a mock VariableXpt - skip for now, covered indirectly
    }

    // ==================== provideLibraryMembers ====================


    @Test
    void testProvideLibraryMembersWithXptLibrary() throws IOException
    {
        XptLibrary lib = new XptLibrary("TEST", null, URI.create("file:///test.xpt"));
        XptLibraryMember m = XptLibraryMember.builder().library(lib).name("DM")
                .uri(URI.create("file:///test.xpt#DM")).build();
        lib.setMembers(List.of(m));

        Stream<? extends ILibraryMember> members = provider.provideLibraryMembers(lib);
        assertEquals(1, members.count());
    }


    @Test
    void testProvideLibraryMembersWithNonXptLibrary() throws IOException
    {
        IDataTableLibrary other = stubLibrary();

        Stream<? extends ILibraryMember> members = provider.provideLibraryMembers(other);
        assertEquals(0, members.count());
    }

    // ==================== provideLibraryMemberColumns ====================


    @Test
    void testProvideLibraryMemberColumnsWithXptMember() throws IOException
    {
        DataTableColumnMeta col = DataTableColumnMeta.builder().index(0).name("AGE")
                .type(DataValueType.DOUBLE).build();

        XptLibraryMember member = XptLibraryMember.builder().name("DM")
                .uri(URI.create("file:///test.xpt#DM")).columns(new DataTableColumnMeta[]
                {
                        col
                }).build();

        Stream<? extends DataTableColumnMeta> cols = provider.provideLibraryMemberColumns(member);
        assertEquals(1, cols.count());
    }


    @Test
    void testProvideLibraryMemberColumnsWithNullColumns() throws IOException
    {
        XptLibraryMember member = XptLibraryMember.builder().name("DM")
                .uri(URI.create("file:///test.xpt#DM")).build();

        Stream<? extends DataTableColumnMeta> cols = provider.provideLibraryMemberColumns(member);
        assertEquals(0, cols.count());
    }


    @Test
    void testProvideLibraryMemberColumnsWithNonXptMember() throws IOException
    {
        ILibraryMember other = stubMember();

        Stream<? extends DataTableColumnMeta> cols = provider.provideLibraryMemberColumns(other);
        assertEquals(0, cols.count());
    }

    // ==================== getLibraryAttribute ====================


    @Test
    void testGetLibraryAttributeAlwaysNull()
    {
        XptLibrary lib = new XptLibrary("TEST", null, URI.create("file:///test.xpt"));
        assertNull(provider.getLibraryAttribute(lib, "any-key"));
    }

    // ==================== provide non-file scheme ====================


    @Test
    void testProvideNonFileSchemeReturnsNull() throws IOException
    {
        URI uri = URI.create("http://example.com/test.xpt");
        assertNull(provider.provide(uri, null));
    }

    // ==================== mapToColumnMeta ====================


    @Test
    void testMapToColumnMetaNumeric()
    {
        VariableXpt v = makeVar("AGE", VariableType.NUMERIC, "Age in years", (short) 8, "BEST",
                (short) 12, (short) 2);
        DataTableColumnMeta meta = provider.mapToColumnMeta(v, 0, URI.create("file:///x.xpt"));
        assertEquals(0, meta.getIndex());
        assertEquals("AGE", meta.getName());
        assertEquals(DataValueType.DOUBLE, meta.getType());
        assertEquals("Age in years", meta.getLabel());
        assertEquals("BEST12.2", meta.getDisplayFormat());
    }


    @Test
    void testMapToColumnMetaCharacter()
    {
        VariableXpt v = makeVar("NAME", VariableType.CHARACTER, "Subject Name", (short) 20, null,
                (short) 0, (short) 0);
        DataTableColumnMeta meta = provider.mapToColumnMeta(v, 5, URI.create("file:///x.xpt"));
        assertEquals(5, meta.getIndex());
        assertEquals("NAME", meta.getName());
        assertEquals(DataValueType.STRING, meta.getType());
        assertEquals("Subject Name", meta.getLabel());
        assertNull(meta.getDisplayFormat());
    }


    @Test
    void testMapToColumnMetaSubstitutesFallbackWhenNameIsNull()
    {
        VariableXpt v = makeVar(null, VariableType.CHARACTER, "anon", (short) 8, null, (short) 0,
                (short) 0);
        DataTableColumnMeta meta = provider.mapToColumnMeta(v, 2, URI.create("file:///x.xpt"));
        assertEquals(2, meta.getIndex());
        assertEquals("V3", meta.getName());
        assertEquals(DataValueType.STRING, meta.getType());
    }

    // ==================== getFullFormatName ====================


    @Test
    void testGetFullFormatNameNullName()
    {
        VariableXpt v = makeVar("X", VariableType.NUMERIC, "", (short) 8, null, (short) 0,
                (short) 0);
        assertNull(provider.getFullFormatName(v));
    }


    @Test
    void testGetFullFormatNameBlankName()
    {
        VariableXpt v = makeVar("X", VariableType.NUMERIC, "", (short) 8, "   ", (short) 0,
                (short) 0);
        assertNull(provider.getFullFormatName(v));
    }


    @Test
    void testGetFullFormatNameWidthAndDecimals()
    {
        VariableXpt v = makeVar("X", VariableType.NUMERIC, "", (short) 8, "BEST", (short) 12,
                (short) 2);
        assertEquals("BEST12.2", provider.getFullFormatName(v));
    }


    @Test
    void testGetFullFormatNameWidthNoDecimals()
    {
        VariableXpt v = makeVar("X", VariableType.NUMERIC, "", (short) 8, "DATE", (short) 9,
                (short) 0);
        assertEquals("DATE9.", provider.getFullFormatName(v));
    }


    @Test
    void testGetFullFormatNameNoWidthNoDecimals()
    {
        VariableXpt v = makeVar("X", VariableType.NUMERIC, "", (short) 8, "BEST", (short) 0,
                (short) 0);
        assertEquals("BEST.", provider.getFullFormatName(v));
    }

    // ==================== provide() against real XPT files ====================


    @Test
    void testProvideMultiDatasetLibrary() throws IOException
    {
        File f = new File(System.getProperty("repoRoot"), "testdata/xpt/05_multiple/sdtm.xpt");
        assertTrue(f.isFile(), () -> "expected fixture: " + f.getAbsolutePath());

        IDataTableLibrary lib = provider.provide(f.toURI(), XptProviderSupplier.FI_XPT);

        assertNotNull(lib);
        assertInstanceOf(XptLibrary.class, lib);
        assertEquals("SDTM", lib.getName());
        assertEquals(f.toURI(), lib.getUri());

        XptLibrary xptLib = (XptLibrary) lib;
        List<XptLibraryMember> members = xptLib.getMembers().toList();
        assertFalse(members.isEmpty(), "multi-dataset XPT should have at least one member");

        // every member should have:
        // - a name and parent library set
        // - a URI with the dataset name as fragment
        // - at least one column
        // - the charset that was resolved from properties
        for (XptLibraryMember m : members)
        {
            assertNotNull(m.getName());
            assertSame(xptLib, m.getLibrary());
            assertEquals(m.getName(), m.getUri().getFragment());
            assertNotNull(m.getColumns());
            assertTrue(m.getColumns().length > 0);
            // default charset should have been resolved
            assertNotNull(m.getCharset());
        }
    }


    @Test
    void testProvideSingleDatasetXpt() throws IOException
    {
        File f = new File(System.getProperty("repoRoot"), "testdata/xpt/01_plain/adsl.xpt");
        assertTrue(f.isFile(), () -> "expected fixture: " + f.getAbsolutePath());

        IDataTableLibrary lib = provider.provide(f.toURI(), XptProviderSupplier.FI_XPT);

        assertNotNull(lib);
        assertInstanceOf(XptLibrary.class, lib);
        assertEquals("ADSL", lib.getName());

        XptLibrary xptLib = (XptLibrary) lib;
        List<XptLibraryMember> members = xptLib.getMembers().toList();
        assertEquals(1, members.size(), "single-dataset XPT should have exactly one member");
        XptLibraryMember only = members.get(0);
        assertNotNull(only.getColumns());
        assertTrue(only.getColumns().length > 0);
    }

    // ==================== helpers ====================


    /**
     * A minimal non-XPT {@link IDataTableLibrary} stub. corej does not ship
     * {@code impl.library.DefaultDataTableLibrary}, so the "non-XPT library returns no members"
     * path is exercised with a hand-rolled stub.
     */
    private static IDataTableLibrary stubLibrary()
    {
        return new IDataTableLibrary()
        {

            @Override
            public String getName()
            {
                return "test";
            }


            @Override
            public String getLabel()
            {
                return "label";
            }


            @Override
            public URI getUri()
            {
                return URI.create("file:///test");
            }


            @Override
            public String getType()
            {
                return "stub";
            }
        };
    }


    /**
     * A minimal non-XPT {@link ILibraryMember} stub (corej does not ship
     * {@code impl.library.DefaultLibraryMember}).
     */
    private static ILibraryMember stubMember()
    {
        return new ILibraryMember()
        {

            @Override
            public String getName()
            {
                return "m";
            }


            @Override
            public String getLabel()
            {
                return "label";
            }


            @Override
            public URI getUri()
            {
                return URI.create("file:///m");
            }


            @Override
            public IDataTableLibrary getLibrary()
            {
                return null;
            }
        };
    }


    private static VariableXpt makeVar(String name, VariableType type, String label, short length,
            String formatName, short formatLen, short formatDecimals)
    {
        VariableXpt v = new VariableXpt();
        v.name = name;
        v.variableTypeId = (short) (type == VariableType.NUMERIC ? 1 : 2);
        v.length = length;
        v.label = label;
        v.formatTypeString = formatName;
        v.formatLength = formatLen;
        v.formatDecimals = formatDecimals;
        return v;
    }
}
