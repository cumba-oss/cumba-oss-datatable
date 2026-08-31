package net.cumba.datatable.manager.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.library.IDataTableLibrary;
import net.cumba.datatable.library.ILibraryMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link DataTableLoadSupport}. Drives the real CSV provider (on test scope) against
 * on-disk CSV files so the load/cache/metadata/column paths are exercised end-to-end. Failure paths
 * use unsupported URIs to assert the {@code Can not open …} translation.
 */
class DataTableLoadSupportTest
{

    @TempDir
    File tempDir;

    private LocalCacheSupport cache;

    private DataTableLoadSupport support;

    @BeforeEach
    void setUp()
    {
        cache = new LocalCacheSupport();
        support = new DataTableLoadSupport(cache);
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
    // Discovery
    // ------------------------------------------------------------------


    @Test
    void getSupportedDataTableInfos_includesCsv()
    {
        List<FileInfo> infos = support.getSupportedDataTableInfos();
        assertNotNull(infos);
        assertFalse(infos.isEmpty(), "the CSV provider must contribute at least one FileInfo");
        boolean hasCsv = infos.stream()
                .anyMatch(fi -> "csv".equalsIgnoreCase(fi.getFileExtension()));
        assertTrue(hasCsv, "csv extension must be supported");
    }


    @Test
    void isSupportedAsTable_csvUri_isTrue() throws IOException
    {
        URI uri = sampleCsv();
        assertTrue(support.isSupportedAsTable(uri, null));
    }


    @Test
    void isSupportedAsTable_unknownExtension_isFalse()
    {
        URI uri = new File(tempDir, "data.unknownext").toURI();
        assertFalse(support.isSupportedAsTable(uri, null));
    }

    // ------------------------------------------------------------------
    // getDataTable(URI, FileInfo)
    // ------------------------------------------------------------------


    @Test
    void getDataTable_fromUri_loadsTableAndColumns() throws IOException
    {
        URI uri = sampleCsv();
        IDataTable tbl = support.getDataTable(uri, null);
        assertNotNull(tbl);
        DataTableMeta meta = tbl.getMetaData();
        List<String> colNames = meta.getAllColumns().map(DataTableColumnMeta::getName)
                .collect(Collectors.toList());
        assertEquals(List.of("ID", "NAME"), colNames);
        assertEquals(2, tbl.getRowCount());
    }


    @Test
    void getDataTable_fromUri_secondCallReturnsCachedInstance() throws IOException
    {
        URI uri = sampleCsv();
        IDataTable first = support.getDataTable(uri, null);
        IDataTable second = support.getDataTable(uri, null);
        // The cache must hand back the same instance for an unchanged file.
        assertSame(first, second);
    }


    @Test
    void getDataTable_fromUri_staleCacheReloadsFreshInstance() throws IOException
    {
        URI uri = sampleCsv();
        IDataTable first = support.getDataTable(uri, null);
        // Touch the file so its last-modified timestamp changes, invalidating the cache entry.
        File f = new File(uri);
        assertTrue(f.setLastModified(f.lastModified() + 10_000L));
        IDataTable second = support.getDataTable(uri, null);
        assertNotNull(second);
        assertEquals(first.getRowCount(), second.getRowCount());
    }


    @Test
    void getDataTable_fromUri_unsupportedUri_throwsIOExceptionWithUri() throws IOException
    {
        URI uri = writeCsv("data.unknownext", "x\n1\n");
        IOException ex = assertThrows(IOException.class, () -> support.getDataTable(uri, null));
        assertTrue(ex.getMessage().contains(uri.toString()),
                "the failure message must name the offending URI: " + ex.getMessage());
        assertTrue(ex.getMessage().startsWith("Can not open"), ex.getMessage());
    }

    // ------------------------------------------------------------------
    // getDataTable(ILibraryMember, FileInfo)
    // ------------------------------------------------------------------


    @Test
    void getDataTable_fromMember_loadsTable() throws IOException
    {
        URI uri = sampleCsv();
        StubMember member = new StubMember(uri, "DM", null);
        IDataTable tbl = support.getDataTable(member, null);
        assertNotNull(tbl);
        assertEquals(2, tbl.getRowCount());
    }


    @Test
    void getDataTable_fromMember_secondCallReturnsCachedInstance() throws IOException
    {
        URI uri = sampleCsv();
        StubMember member = new StubMember(uri, "DM", null);
        IDataTable first = support.getDataTable(member, null);
        IDataTable second = support.getDataTable(member, null);
        assertSame(first, second);
    }


    @Test
    void getDataTable_fromMember_explicitFileInfoIsUsedOverMemberFileInfo() throws IOException
    {
        URI uri = sampleCsv();
        // Member reports a bogus FileInfo, but the explicit (CSV) one passed to getDataTable wins.
        FileInfo csv = support.getSupportedDataTableInfos().stream()
                .filter(fi -> "csv".equalsIgnoreCase(fi.getFileExtension())).findFirst()
                .orElseThrow();
        StubMember member = new StubMember(uri, "DM", FileInfo.createFor("xyz", "Bogus"));
        IDataTable tbl = support.getDataTable(member, csv);
        assertNotNull(tbl);
        assertEquals(2, tbl.getRowCount());
    }


    @Test
    void getDataTable_fromMember_unsupportedUri_throwsIOExceptionWithUri() throws IOException
    {
        URI uri = writeCsv("data.unknownext", "x\n1\n");
        StubMember member = new StubMember(uri, "X", null);
        IOException ex = assertThrows(IOException.class, () -> support.getDataTable(member, null));
        assertTrue(ex.getMessage().contains(uri.toString()), ex.getMessage());
    }

    // ------------------------------------------------------------------
    // provideMetaData(URI / member)
    // ------------------------------------------------------------------


    @Test
    void provideMetaData_fromUri_returnsColumns() throws IOException
    {
        URI uri = sampleCsv();
        DataTableMeta meta = support.provideMetaData(uri, null);
        assertNotNull(meta);
        assertEquals(2, meta.getColumnCount());
    }


    @Test
    void provideMetaData_fromUri_unsupported_throwsIOException() throws IOException
    {
        URI uri = writeCsv("data.unknownext", "x\n1\n");
        IOException ex = assertThrows(IOException.class, () -> support.provideMetaData(uri, null));
        assertTrue(ex.getMessage().contains(uri.toString()), ex.getMessage());
    }


    @Test
    void provideMetaData_fromMember_returnsColumns() throws IOException
    {
        URI uri = sampleCsv();
        StubMember member = new StubMember(uri, "DM", null);
        DataTableMeta meta = support.provideMetaData(member, null);
        assertNotNull(meta);
        assertEquals(2, meta.getColumnCount());
    }


    @Test
    void provideMetaData_fromMember_unsupported_throwsIOExceptionWithMemberUri() throws IOException
    {
        URI uri = writeCsv("data.unknownext", "x\n1\n");
        StubMember member = new StubMember(uri, "X", null);
        IOException ex = assertThrows(IOException.class,
                () -> support.provideMetaData(member, null));
        assertTrue(ex.getMessage().contains(uri.toString()), ex.getMessage());
    }

    // ------------------------------------------------------------------
    // getColumns(ILibraryMember)
    // ------------------------------------------------------------------


    @Test
    void getColumns_usesCachedTableWhenPresent() throws IOException
    {
        URI uri = sampleCsv();
        // Pre-load and cache the table so getColumns takes the fast path.
        IDataTable tbl = support.getDataTable(uri, null);
        assertSame(tbl, cache.findCachedTable(uri));

        StubLibrary lib = new StubLibrary(tempDir.toURI());
        StubMember member = new StubMember(uri, "DM", null, lib);
        List<String> names = support.getColumns(member).map(DataTableColumnMeta::getName)
                .collect(Collectors.toList());
        assertEquals(List.of("ID", "NAME"), names);
    }


    @Test
    void getColumns_fallsBackToProvideMetaDataWhenNotCached() throws IOException
    {
        URI uri = sampleCsv();
        // No table cached; library provider does not enumerate columns for this library, so the
        // fallback path (provideMetaData on the data-table factory) must supply them.
        StubLibrary lib = new StubLibrary(tempDir.toURI());
        StubMember member = new StubMember(uri, "DM", null, lib);
        List<String> names = support.getColumns(member).map(DataTableColumnMeta::getName)
                .collect(Collectors.toList());
        assertEquals(List.of("ID", "NAME"), names);
    }

    // ------------------------------------------------------------------
    // Stubs
    // ------------------------------------------------------------------

    /** Minimal {@link IDataTableLibrary} backed by a directory URI. */
    static final class StubLibrary implements IDataTableLibrary
    {

        private final URI uri;

        StubLibrary(URI aUri)
        {
            uri = aUri;
        }


        @Override
        public String getName()
        {
            return "stub-lib";
        }


        @Override
        public String getLabel()
        {
            return "stub-lib";
        }


        @Override
        public URI getUri()
        {
            return uri;
        }


        @Override
        public String getType()
        {
            return "stub";
        }
    }


    /** Minimal {@link ILibraryMember} pointing at an on-disk file. */
    static final class StubMember implements ILibraryMember
    {

        private final URI uri;

        private final String name;

        private final FileInfo fileInfo;

        private final IDataTableLibrary library;

        StubMember(URI aUri, String aName, FileInfo aFileInfo)
        {
            this(aUri, aName, aFileInfo, null);
        }


        StubMember(URI aUri, String aName, FileInfo aFileInfo, IDataTableLibrary aLibrary)
        {
            uri = aUri;
            name = aName;
            fileInfo = aFileInfo;
            library = aLibrary;
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
        public FileInfo getFileInfo()
        {
            return fileInfo;
        }


        @Override
        public IDataTableLibrary getLibrary()
        {
            return library;
        }
    }
}
