package net.cumba.datatable.manager.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.net.URI;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.CachedDataTableColumn;
import net.cumba.datatable.impl.ColumnCachedDataTable;
import net.cumba.datatable.library.IDataTableLibrary;
import net.cumba.datatable.library.ILibraryMember;
import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.Test;

/**
 * Tests for the local Ref implementations: {@link LocalDataTableRef},
 * {@link LocalDataTableLibraryRef}, and {@link LocalLibraryMemberRef}.
 */
class LocalRefTest
{

    private final LocalDataTableManager manager = new LocalDataTableManager();

    // ==================== Helper ====================

    private IDataTable createTable(URI aUri)
    {
        DataTableColumnMeta cm = DataTableColumnMeta.builder().index(0).name("COL")
                .type(DataValueType.STRING).build();
        DataTableMeta meta = DataTableMeta.builder().name("TEST").setColumns(cm).rowCount(0)
                .tableURI(aUri).build();
        return new ColumnCachedDataTable(meta, new CachedDataTableColumn(0, DataValueType.STRING));
    }


    private IDataTableLibrary createStubLibrary(String aName, String aLabel, URI aUri)
    {
        return new IDataTableLibrary()
        {

            @Override
            public String getName()
            {
                return aName;
            }


            @Override
            public String getLabel()
            {
                return aLabel;
            }


            @Override
            public URI getUri()
            {
                return aUri;
            }


            @Override
            public String getType()
            {
                return "stub";
            }

        };
    }


    private ILibraryMember createStubMember(String aName, String aLabel, URI aUri,
            IDataTableLibrary aLibrary)
    {
        return new ILibraryMember()
        {

            @Override
            public String getName()
            {
                return aName;
            }


            @Override
            public String getLabel()
            {
                return aLabel;
            }


            @Override
            public URI getUri()
            {
                return aUri;
            }


            @Override
            public IDataTableLibrary getLibrary()
            {
                return aLibrary;
            }
        };
    }

    // ==================== LocalDataTableRef ====================


    @Test
    void testLocalDataTableRef_getName()
    {
        IDataTable table = createTable(null);
        LocalDataTableRef ref = new LocalDataTableRef(manager, table);

        assertEquals("TEST", ref.getName());
    }


    @Test
    void testLocalDataTableRef_getMetaData()
    {
        IDataTable table = createTable(null);
        LocalDataTableRef ref = new LocalDataTableRef(manager, table);

        assertSame(table.getMetaData(), ref.getMetaData());
    }


    @Test
    void testLocalDataTableRef_getManager()
    {
        IDataTable table = createTable(null);
        LocalDataTableRef ref = new LocalDataTableRef(manager, table);

        assertSame(manager, ref.getManager());
    }


    @Test
    void testLocalDataTableRef_getTable()
    {
        IDataTable table = createTable(null);
        LocalDataTableRef ref = new LocalDataTableRef(manager, table);

        assertSame(table, ref.getTable());
    }


    @Test
    void testLocalDataTableRef_getUri()
    {
        URI uri = URI.create("file:///test.csv");
        IDataTable table = createTable(uri);
        LocalDataTableRef ref = new LocalDataTableRef(manager, table);

        assertEquals("file:///test.csv", ref.getUri());
    }


    @Test
    void testLocalDataTableRef_getUriNull()
    {
        IDataTable table = createTable(null);
        LocalDataTableRef ref = new LocalDataTableRef(manager, table);

        assertNull(ref.getUri());
    }


    @Test
    void testLocalDataTableRef_toString()
    {
        IDataTable table = createTable(null);
        LocalDataTableRef ref = new LocalDataTableRef(manager, table);

        assertEquals("TEST", ref.toString());
    }

    // ==================== LocalDataTableLibraryRef ====================


    @Test
    void testLocalDataTableLibraryRef_getName()
    {
        URI uri = URI.create("file:///lib/");
        IDataTableLibrary lib = createStubLibrary("MyLib", "My Library", uri);
        LocalDataTableLibraryRef ref = new LocalDataTableLibraryRef(manager, lib);

        assertEquals("MyLib", ref.getName());
    }


    @Test
    void testLocalDataTableLibraryRef_getLabel()
    {
        IDataTableLibrary lib = createStubLibrary("Lib", "Label", null);
        LocalDataTableLibraryRef ref = new LocalDataTableLibraryRef(manager, lib);

        assertEquals("Label", ref.getLabel());
    }


    @Test
    void testLocalDataTableLibraryRef_getManager()
    {
        IDataTableLibrary lib = createStubLibrary("Lib", null, null);
        LocalDataTableLibraryRef ref = new LocalDataTableLibraryRef(manager, lib);

        assertSame(manager, ref.getManager());
    }


    @Test
    void testLocalDataTableLibraryRef_getUri()
    {
        URI uri = URI.create("file:///lib/");
        IDataTableLibrary lib = createStubLibrary("Lib", null, uri);
        LocalDataTableLibraryRef ref = new LocalDataTableLibraryRef(manager, lib);

        assertEquals("file:///lib/", ref.getUri());
    }


    @Test
    void testLocalDataTableLibraryRef_getUriNull()
    {
        IDataTableLibrary lib = createStubLibrary("Lib", null, null);
        LocalDataTableLibraryRef ref = new LocalDataTableLibraryRef(manager, lib);

        assertNull(ref.getUri());
    }

    // ==================== LocalLibraryMemberRef ====================


    @Test
    void testLocalLibraryMemberRef_getName()
    {
        IDataTableLibrary lib = createStubLibrary("Lib", null, null);
        ILibraryMember member = createStubMember("DM", "Demographics", null, lib);
        LocalLibraryMemberRef ref = new LocalLibraryMemberRef(manager, member);

        assertEquals("DM", ref.getName());
    }


    @Test
    void testLocalLibraryMemberRef_getLabel()
    {
        IDataTableLibrary lib = createStubLibrary("Lib", null, null);
        ILibraryMember member = createStubMember("DM", "Demographics", null, lib);
        LocalLibraryMemberRef ref = new LocalLibraryMemberRef(manager, member);

        assertEquals("Demographics", ref.getLabel());
    }


    @Test
    void testLocalLibraryMemberRef_getUri()
    {
        URI uri = URI.create("file:///dm.sas7bdat");
        IDataTableLibrary lib = createStubLibrary("Lib", null, null);
        ILibraryMember member = createStubMember("DM", null, uri, lib);
        LocalLibraryMemberRef ref = new LocalLibraryMemberRef(manager, member);

        assertEquals("file:///dm.sas7bdat", ref.getUri());
    }


    @Test
    void testLocalLibraryMemberRef_getUriNull()
    {
        IDataTableLibrary lib = createStubLibrary("Lib", null, null);
        ILibraryMember member = createStubMember("DM", null, null, lib);
        LocalLibraryMemberRef ref = new LocalLibraryMemberRef(manager, member);

        assertNull(ref.getUri());
    }


    @Test
    void testLocalLibraryMemberRef_getManager()
    {
        IDataTableLibrary lib = createStubLibrary("Lib", null, null);
        ILibraryMember member = createStubMember("DM", null, null, lib);
        LocalLibraryMemberRef ref = new LocalLibraryMemberRef(manager, member);

        assertSame(manager, ref.getManager());
    }

}
