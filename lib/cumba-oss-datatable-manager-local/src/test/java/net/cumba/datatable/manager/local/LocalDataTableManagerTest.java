package net.cumba.datatable.manager.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.library.IDataTableLibrary;
import net.cumba.datatable.library.ILibraryMember;
import net.cumba.datatable.manager.IDataTableLibraryRef;
import net.cumba.datatable.manager.IDataTableRef;
import net.cumba.datatable.manager.ILibraryMemberRef;
import net.cumba.datatable.metadata.IMetadataLibrary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link LocalDataTableManager}: ref unwrapping (the {@code asLocalRef} guards),
 * discovery delegation, and the public table/library/member ref creation surface. End-to-end paths
 * are driven against real CSV files and a real on-disk folder library.
 */
class LocalDataTableManagerTest
{

    @TempDir
    File tempDir;

    private LocalDataTableManager manager;

    @BeforeEach
    void setUp()
    {
        manager = new LocalDataTableManager();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------


    private URI writeCsv(String aName, String aContent) throws IOException
    {
        File f = new File(tempDir, aName);
        Files.writeString(f.toPath(), aContent, StandardCharsets.UTF_8);
        return f.toURI();
    }


    private URI sampleCsv() throws IOException
    {
        return writeCsv("dm.csv", "ID,NAME\n1,Alice\n2,Bob\n");
    }

    // ------------------------------------------------------------------
    // asLocalRef — table ref
    // ------------------------------------------------------------------


    @Test
    void asLocalRef_tableRef_correctManager_returnsImpl() throws IOException
    {
        URI uri = sampleCsv();
        IDataTableRef ref = manager.getDataTableRef(uri, null);
        LocalDataTableRef impl = manager.asLocalRef(ref);
        assertNotNull(impl);
        assertSame(((LocalDataTableRef) ref).getTable(), impl.getTable());
    }


    @Test
    void asLocalRef_tableRef_foreignType_throwsIOException()
    {
        IDataTableRef foreign = mock(IDataTableRef.class);
        assertThrows(IOException.class, () -> manager.asLocalRef(foreign));
    }


    @Test
    void asLocalRef_tableRef_otherManager_throwsIOException() throws IOException
    {
        // A LocalDataTableRef belonging to a DIFFERENT manager must be rejected.
        LocalDataTableManager other = new LocalDataTableManager();
        URI uri = sampleCsv();
        IDataTableRef otherRef = other.getDataTableRef(uri, null);
        assertThrows(IOException.class, () -> manager.asLocalRef(otherRef));
    }

    // ------------------------------------------------------------------
    // asLocalRef — library ref
    // ------------------------------------------------------------------


    @Test
    void asLocalRef_libraryRef_correctManager_returnsImpl() throws IOException
    {
        IDataTableLibraryRef ref = manager.getLibraryRef(tempDir.toURI(), null);
        LocalDataTableLibraryRef impl = manager.asLocalRef(ref);
        assertNotNull(impl);
        assertSame(((LocalDataTableLibraryRef) ref).getLibrary(), impl.getLibrary());
    }


    @Test
    void asLocalRef_libraryRef_foreignType_throwsIOException()
    {
        IDataTableLibraryRef foreign = mock(IDataTableLibraryRef.class);
        assertThrows(IOException.class, () -> manager.asLocalRef(foreign));
    }


    @Test
    void asLocalRef_libraryRef_otherManager_throwsIOException() throws IOException
    {
        LocalDataTableManager other = new LocalDataTableManager();
        IDataTableLibraryRef otherRef = other.getLibraryRef(tempDir.toURI(), null);
        assertThrows(IOException.class, () -> manager.asLocalRef(otherRef));
    }

    // ------------------------------------------------------------------
    // asLocalRef — member ref
    // ------------------------------------------------------------------


    @Test
    void asLocalRef_memberRef_correctManager_returnsImpl() throws IOException
    {
        IDataTableLibraryRef libRef = manager.getLibraryRef(tempDir.toURI(), null);
        // Member ref via a stub member is enough to exercise the unwrap guard.
        ILibraryMember member = new StubMember(URI.create("file:///dm.csv"), "DM");
        ILibraryMemberRef memberRef = new LocalLibraryMemberRef(manager, member);
        LocalLibraryMemberRef impl = manager.asLocalRef(memberRef);
        assertNotNull(impl);
        assertSame(member, impl.getMember());
        assertNotNull(libRef);
    }


    @Test
    void asLocalRef_memberRef_foreignType_throwsIOException()
    {
        ILibraryMemberRef foreign = mock(ILibraryMemberRef.class);
        assertThrows(IOException.class, () -> manager.asLocalRef(foreign));
    }


    @Test
    void asLocalRef_memberRef_otherManager_throwsIOException()
    {
        LocalDataTableManager other = new LocalDataTableManager();
        ILibraryMember member = new StubMember(URI.create("file:///dm.csv"), "DM");
        ILibraryMemberRef otherRef = new LocalLibraryMemberRef(other, member);
        assertThrows(IOException.class, () -> manager.asLocalRef(otherRef));
    }

    // ------------------------------------------------------------------
    // Discovery delegation
    // ------------------------------------------------------------------


    @Test
    void getSupportedDataTableInfos_delegatesAndIncludesCsv()
    {
        List<FileInfo> infos = manager.getSupportedDataTableInfos();
        assertNotNull(infos);
        assertTrue(infos.stream().anyMatch(fi -> "csv".equalsIgnoreCase(fi.getFileExtension())));
    }


    @Test
    void getSupportedDataLibraryInfos_delegatesAndIncludesFolder()
    {
        List<FileInfo> infos = manager.getSupportedDataLibraryInfos();
        assertNotNull(infos);
        assertTrue(
                infos.contains(net.cumba.datatable.library.folder.FolderLibrarySupplier.FI_FOLDER));
    }


    @Test
    void isSupportedAsTable_csv_isTrue() throws IOException
    {
        assertTrue(manager.isSupportedAsTable(sampleCsv(), null));
    }


    @Test
    void isSupportedAsTable_unknown_isFalse()
    {
        assertFalse(manager.isSupportedAsTable(new File(tempDir, "x.unknownext").toURI(), null));
    }


    @Test
    void isSupportedAsLibrary_directory_isTrue()
    {
        assertTrue(manager.isSupportedAsLibrary(tempDir.toURI(), null));
    }


    @Test
    void isSupportedAsLibrary_plainFile_isFalse() throws IOException
    {
        URI fileUri = writeCsv("x.unknownext", "x");
        assertFalse(manager.isSupportedAsLibrary(fileUri, null));
    }

    // ------------------------------------------------------------------
    // getDataTableRef / getDataTable
    // ------------------------------------------------------------------


    @Test
    void getDataTableRef_fromUri_wrapsLoadedTable() throws IOException
    {
        URI uri = sampleCsv();
        IDataTableRef ref = manager.getDataTableRef(uri, null);
        assertNotNull(ref);
        assertSame(manager, ((LocalDataTableRef) ref).getManager());
        assertEquals(2, manager.getDataTable(ref).getRowCount());
    }


    @Test
    void getDataTableRef_fromMember_wrapsLoadedTable() throws IOException
    {
        URI uri = sampleCsv();
        ILibraryMember member = new StubMember(uri, "DM");
        ILibraryMemberRef memberRef = new LocalLibraryMemberRef(manager, member);
        IDataTableRef ref = manager.getDataTableRef(memberRef, null);
        assertNotNull(ref);
        assertEquals(2, manager.getDataTable(ref).getRowCount());
    }


    @Test
    void getDataTable_packageUriAccessor_loadsTable() throws IOException
    {
        URI uri = sampleCsv();
        IDataTable tbl = manager.getDataTable(uri, null);
        assertNotNull(tbl);
        assertEquals(2, tbl.getRowCount());
    }


    @Test
    void getDataTable_packageMemberAccessor_loadsTable() throws IOException
    {
        URI uri = sampleCsv();
        ILibraryMember member = new StubMember(uri, "DM");
        IDataTable tbl = manager.getDataTable(member, null);
        assertNotNull(tbl);
        assertEquals(2, tbl.getRowCount());
    }

    // ------------------------------------------------------------------
    // getLibraryRef / getLibraryMembers / getMetadataLibrary
    // ------------------------------------------------------------------


    @Test
    void getLibraryRef_directory_wrapsFolderLibrary() throws IOException
    {
        IDataTableLibraryRef ref = manager.getLibraryRef(tempDir.toURI(), null);
        assertNotNull(ref);
        assertSame(manager, ((LocalDataTableLibraryRef) ref).getManager());
        // The ref exposes the wrapped library's own (normalised) URI string.
        IDataTableLibrary lib = ((LocalDataTableLibraryRef) ref).getLibrary();
        assertEquals(lib.getUri().toString(), ref.getUri());
        assertTrue(ref.getUri().endsWith(tempDir.getName() + "/"), ref.getUri());
    }


    @Test
    void getLibraryMembers_listsMembersAsRefs() throws IOException
    {
        writeCsv("dm.csv", "ID\n1\n");
        writeCsv("ae.csv", "ID\n2\n");
        IDataTableLibraryRef ref = manager.getLibraryRef(tempDir.toURI(), null);
        List<ILibraryMemberRef> members = manager.getLibraryMembers(ref)
                .collect(Collectors.toList());
        assertEquals(2, members.size());
        for (ILibraryMemberRef m : members)
        {
            assertTrue(m instanceof LocalLibraryMemberRef);
            assertSame(manager, ((LocalLibraryMemberRef) m).getManager());
        }
    }


    @Test
    void getMetadataLibrary_returnsNonNullFallback() throws IOException
    {
        IDataTableLibraryRef ref = manager.getLibraryRef(tempDir.toURI(), null);
        IMetadataLibrary md = manager.getMetadataLibrary(ref);
        assertNotNull(md, "manager must return a derived metadata adapter as last fallback");
    }


    @Test
    void getMetadataLibrary_attachedMetadataWins() throws IOException
    {
        // A library whose getMetadata() already returns metadata short-circuits the fallback.
        IMetadataLibrary attached = mock(IMetadataLibrary.class);
        IDataTableLibrary lib = mock(IDataTableLibrary.class);
        when(lib.getUri()).thenReturn(URI.create("file:///never-cached-lib"));
        when(lib.getMetadata()).thenReturn(attached);
        IDataTableLibraryRef ref = LocalDataTableLibraryRef.builder().manager(manager).library(lib)
                .build();
        assertSame(attached, manager.getMetadataLibrary(ref));
    }

    // ------------------------------------------------------------------
    // Constants
    // ------------------------------------------------------------------


    @Test
    void typeAndVersionConstants()
    {
        assertEquals("LocalDataTableManager", LocalDataTableManager.TYPE);
        assertEquals("0.1", LocalDataTableManager.VERSION);
    }

    // ------------------------------------------------------------------
    // Stubs
    // ------------------------------------------------------------------

    /** Minimal {@link ILibraryMember} backed by an on-disk file URI. */
    static final class StubMember implements ILibraryMember
    {

        private final URI uri;

        private final String name;

        StubMember(URI aUri, String aName)
        {
            uri = aUri;
            name = aName;
        }


        @Override
        public String getName()
        {
            return name;
        }


        @Override
        public String getLabel()
        {
            return name;
        }


        @Override
        public URI getUri()
        {
            return uri;
        }


        @Override
        public IDataTableLibrary getLibrary()
        {
            return null;
        }
    }
}
