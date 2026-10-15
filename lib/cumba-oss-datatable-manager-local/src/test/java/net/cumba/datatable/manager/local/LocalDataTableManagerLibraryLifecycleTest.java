package net.cumba.datatable.manager.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import net.cumba.datatable.library.IDataTableLibrary;
import net.cumba.datatable.manager.IDataTableLibraryRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Library lifecycle through the manager: open and release, and the memory lock the release drops.
 * This manager exposes neither an enumeration of its open libraries nor
 * {@code lockLibraryInMemory}, so the open set and the lock are read and taken on its cache
 * directly.
 */
class LocalDataTableManagerLibraryLifecycleTest
{

    @TempDir
    File tempDir;

    private LocalDataTableManager manager;

    @BeforeEach
    void setUp()
    {
        manager = new LocalDataTableManager();
    }


    private IDataTableLibraryRef folderLibrary(String aSub, String... aCsvs) throws IOException
    {
        File dir = new File(tempDir, aSub);
        assertTrue(dir.mkdirs());
        for (String csv : aCsvs)
        {
            Files.writeString(new File(dir, csv).toPath(), "USUBJID,VAL\nU1,1\n",
                    StandardCharsets.UTF_8);
        }
        return manager.getLibraryRef(dir.toURI(), null);
    }


    private LocalCacheSupport cache() throws Exception
    {
        java.lang.reflect.Field f = LocalDataTableManager.class.getDeclaredField("cacheSupport");
        f.setAccessible(true);
        return (LocalCacheSupport) f.get(manager);
    }


    @Test
    void openAndRelease_areVisibleThroughOpenLibraries() throws Exception
    {
        assertEquals(0, cache().streamLibraries().count());
        IDataTableLibraryRef lib = folderLibrary("libA", "dm.csv");
        assertEquals(1, cache().streamLibraries().count());
        manager.releaseLibraryRef(lib);
        assertEquals(0, cache().streamLibraries().count(),
                "a released library must leave the open set");
    }


    @Test
    void lockAndRelease_bracketTheMemoryLock() throws Exception
    {
        IDataTableLibraryRef lib = folderLibrary("locky", "dm.csv");
        // Owned by the manager; releaseLibraryRef() releases it.
        @SuppressWarnings("PMD.CloseResource")
        IDataTableLibrary library = ((LocalDataTableLibraryRef) lib).getLibrary();
        cache().lockLibrary(library, List.of());
        assertTrue(cache().isLocked(library), "precondition: the memory lock is taken");
        manager.releaseLibraryRef(lib);
        assertFalse(cache().isLocked(library),
                "releaseLibraryRef must RELEASE the memory lock, not only evict the cache");
    }
}
