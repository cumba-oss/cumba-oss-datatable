package net.cumba.datatable.manager.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.library.IDataTableLibrary;
import net.cumba.datatable.library.ILibraryMember;
import net.cumba.datatable.library.folder.FolderLibrary;
import net.cumba.datatable.metadata.IMetadataLibrary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link LibraryLoadSupport}. Uses a real on-disk folder (resolved via the
 * {@link FolderLibrary} supplier registered through the ServiceLoader) to drive library loading,
 * caching, member enumeration, and metadata fallback. Error paths use non-directory / unsupported
 * URIs.
 */
class LibraryLoadSupportTest
{

    @TempDir
    File tempDir;

    private LocalCacheSupport cache;

    private LibraryLoadSupport support;

    @BeforeEach
    void setUp()
    {
        cache = new LocalCacheSupport();
        support = new LibraryLoadSupport(cache);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------


    private File writeCsv(String aName, String aContent) throws IOException
    {
        File f = new File(tempDir, aName);
        Files.writeString(f.toPath(), aContent, StandardCharsets.UTF_8);
        return f;
    }

    // ------------------------------------------------------------------
    // Discovery
    // ------------------------------------------------------------------


    @Test
    void getSupportedDataLibraryInfos_includesFolder()
    {
        List<FileInfo> infos = support.getSupportedDataLibraryInfos();
        assertNotNull(infos);
        assertTrue(
                infos.contains(net.cumba.datatable.library.folder.FolderLibrarySupplier.FI_FOLDER),
                "the folder library supplier must contribute its FI_FOLDER FileInfo");
    }


    @Test
    void isSupportedAsLibrary_directory_isTrue()
    {
        assertTrue(support.isSupportedAsLibrary(tempDir.toURI(), null));
    }


    @Test
    void isSupportedAsLibrary_uriWithFragment_isFalse()
    {
        // A fragment short-circuits to false regardless of the path.
        URI withFragment = URI.create(tempDir.toURI().toString() + "#frag");
        assertFalse(support.isSupportedAsLibrary(withFragment, null));
    }


    @Test
    void isSupportedAsLibrary_nonExistentFile_isFalse()
    {
        URI missing = new File(tempDir, "does-not-exist").toURI();
        // Not a directory and not matched by any library provider extension.
        assertFalse(support.isSupportedAsLibrary(missing, null));
    }


    @Test
    void isSupportedAsLibrary_nonFileScheme_delegatesToProviderFactory()
    {
        // No registered library provider claims an http URI → false.
        assertFalse(support.isSupportedAsLibrary(URI.create("http://example.com/lib"), null));
    }

    // ------------------------------------------------------------------
    // getURI
    // ------------------------------------------------------------------


    @Test
    void getURI_library_returnsLibraryUri()
    {
        FolderLibrary lib = new FolderLibrary(tempDir);
        assertEquals(tempDir.toURI(), support.getURI(lib));
    }


    @Test
    void getURI_member_returnsMemberUri() throws IOException
    {
        File csv = writeCsv("dm.csv", "ID\n1\n");
        try (FolderLibrary lib = new FolderLibrary(tempDir))
        {
            ILibraryMember member = lib.getMembers().findFirst().orElseThrow();
            assertEquals(csv.toURI(), support.getURI(member));
        }
        catch (Exception ex)
        {
            fail(ex);
        }
    }

    // ------------------------------------------------------------------
    // getLibrary
    // ------------------------------------------------------------------


    @Test
    void getLibrary_directory_loadsFolderLibrary() throws IOException
    {
        IDataTableLibrary lib = support.getLibrary(tempDir.toURI(), null);
        assertNotNull(lib);
        assertTrue(lib instanceof FolderLibrary, "a directory must resolve to a FolderLibrary");
        assertEquals(tempDir.toURI(), lib.getUri());
    }


    @Test
    void getLibrary_secondCall_returnsCachedInstance() throws IOException
    {
        IDataTableLibrary first = support.getLibrary(tempDir.toURI(), null);
        IDataTableLibrary second = support.getLibrary(tempDir.toURI(), null);
        assertSame(first, second, "an unchanged folder must be served from cache");
    }


    @Test
    void getLibrary_unsupportedUri_throwsIOExceptionWithUri()
    {
        // A regular file is not a directory and has no matching library provider.
        URI fileUri;
        try
        {
            fileUri = writeCsv("plain.unknownext", "x").toURI();
        }
        catch (IOException ex)
        {
            throw new AssertionError(ex);
        }
        IOException ex = assertThrows(IOException.class, () -> support.getLibrary(fileUri, null));
        assertTrue(ex.getMessage().contains(fileUri.toString()), ex.getMessage());
        assertTrue(ex.getMessage().startsWith("Can not open"), ex.getMessage());
    }

    // ------------------------------------------------------------------
    // getLibraryMembers
    // ------------------------------------------------------------------


    @Test
    void getLibraryMembers_folder_listsCsvFiles() throws IOException
    {
        writeCsv("dm.csv", "ID\n1\n");
        writeCsv("ae.csv", "ID\n2\n");
        FolderLibrary lib = new FolderLibrary(tempDir);
        List<String> names = support.getLibraryMembers(lib).map(ILibraryMember::getName)
                .collect(Collectors.toList());
        assertTrue(names.contains("DM"), names.toString());
        assertTrue(names.contains("AE"), names.toString());
        assertEquals(2, names.size());
    }


    @Test
    void getLibraryMembers_nonFolderLibrary_isEmpty()
    {
        IDataTableLibrary foreign = new StubLibrary(tempDir.toURI());
        // No folder provider matches a non-FolderLibrary instance whose URI is a directory:
        // the FolderLibraryProvider returns an empty stream for foreign library types.
        assertEquals(0L, support.getLibraryMembers(foreign).count());
    }

    // ------------------------------------------------------------------
    // releaseLibrary
    // ------------------------------------------------------------------


    @Test
    void releaseLibrary_evictsFromCacheAndClosesLibrary() throws IOException
    {
        // A mock library lets us both pin it in the cache and verify close() is called.
        URI libUri = URI.create("file:///some-lib");
        IDataTableLibrary lib = mock(IDataTableLibrary.class);
        when(lib.getUri()).thenReturn(libUri);
        long lm = cache.getLastModified(libUri);
        cache.storeLibrary(libUri, lib, null, lm);
        assertSame(lib, cache.lookupLibrary(libUri, lm), "precondition: library is cached");

        support.releaseLibrary(lib);

        // The cache entry must be gone …
        assertNull(cache.lookupLibrary(libUri, lm),
                "releaseLibrary must evict the library from the cache");
        // … and the library must have been closed exactly once.
        verify(lib).close();
    }

    // ------------------------------------------------------------------
    // getMetadataLibrary
    // ------------------------------------------------------------------


    @Test
    void getMetadataLibrary_cachedMetadataWins() throws IOException
    {
        IDataTableLibrary lib = support.getLibrary(tempDir.toURI(), null);
        IMetadataLibrary cachedMeta = mock(IMetadataLibrary.class);
        cache.updateMetadata(lib, cachedMeta);
        assertSame(cachedMeta, support.getMetadataLibrary(lib),
                "cached metadata takes precedence over attached/derived metadata");
    }


    @Test
    void getMetadataLibrary_fallsBackToAdapterWhenNothingAttached() throws IOException
    {
        IDataTableLibrary lib = support.getLibrary(tempDir.toURI(), null);
        // No metadata cached or attached → a derived adapter is returned (never null).
        IMetadataLibrary md = support.getMetadataLibrary(lib);
        assertNotNull(md, "a derived metadata adapter must be returned as the last fallback");
    }

    // ------------------------------------------------------------------
    // Stubs
    // ------------------------------------------------------------------

    /** Minimal {@link IDataTableLibrary} that is NOT a FolderLibrary. */
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
}
