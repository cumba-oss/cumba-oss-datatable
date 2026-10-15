package net.cumba.datatable.manager.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import net.cumba.datatable.library.IDataTableLibrary;
import net.cumba.datatable.manager.IDataTableLibraryRef;
import net.cumba.datatable.manager.local.testsupport.LogCapture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Reference counting of library refs (Q44).
 * <p>
 * {@code getLibraryRef(URI, ...)} answers the CACHED instance, so two holders of the same URI share
 * one library. A release that evicted (and closed) it unconditionally pulled it out from under the
 * second holder without either noticing. The hazard is pinned by
 * {@link #twoHoldersOfOneLibrary_oneReleasing_doesNotEvictItUnderTheOther()}.
 * <p>
 * None of this module's libraries overrides the no-op {@code IDataTableLibrary.close()}, so a test
 * that watched for a closed library would confirm itself. The hazard is asserted on what IS
 * observable — the owner count and the cache population — and a library double that counts its
 * {@code close()} calls pins when the close happens
 * ({@link #theLastReleaseClosesTheLibrary_onceAndNoEarlierReleaseDoes()}). This manager exposes no
 * enumeration of its open libraries, so the open set is read from the cache directly.
 */
class LocalDataTableManagerLibraryRefCountTest
{

    @TempDir
    File tempDir;

    private LocalDataTableManager manager;

    @BeforeEach
    void setUp()
    {
        manager = new LocalDataTableManager();
    }


    private URI folder(String aSub, String... aCsvs) throws IOException
    {
        File dir = new File(tempDir, aSub);
        assertTrue(dir.mkdirs());
        for (String csv : aCsvs)
        {
            Files.writeString(new File(dir, csv).toPath(), "USUBJID,VAL\nU1,1\n",
                    StandardCharsets.UTF_8);
        }
        return dir.toURI();
    }


    private LocalCacheSupport cache() throws Exception
    {
        java.lang.reflect.Field f = LocalDataTableManager.class.getDeclaredField("cacheSupport");
        f.setAccessible(true);
        return (LocalCacheSupport) f.get(manager);
    }


    /** The libraries the manager currently serves from its cache (the open set). */
    private List<IDataTableLibrary> openLibraries() throws Exception
    {
        return cache().streamLibraries().toList();
    }


    private static IDataTableLibrary libraryOf(IDataTableLibraryRef aRef)
    {
        return ((LocalDataTableLibraryRef) aRef).getLibrary();
    }


    /**
     * The hazard, end to end: one holder opens a library and keeps the ref, a second holder opens
     * the SAME uri (getting the same cached instance) and releases it when it is done. The first
     * holder's library must stay cached — not evicted, not replaced.
     */
    @Test
    void twoHoldersOfOneLibrary_oneReleasing_doesNotEvictItUnderTheOther() throws Exception
    {
        URI uri = folder("open", "dm.csv");

        IDataTableLibraryRef first = manager.getLibraryRef(uri, null);
        // Owned by the manager; releaseLibraryRef() releases it.
        @SuppressWarnings("PMD.CloseResource")
        IDataTableLibrary shared = libraryOf(first);

        IDataTableLibraryRef second = manager.getLibraryRef(uri, null);
        assertSame(shared, libraryOf(second),
                "the second open must hit the cache, otherwise there is no hazard to fix");
        assertEquals(2, cache().countOwners(uri), "both holders are counted");

        manager.releaseLibraryRef(second);

        assertEquals(1, cache().countOwners(uri), "the first holder still owns the library");
        List<IDataTableLibrary> open = openLibraries();
        assertEquals(1, open.size(),
                "the second holder's release must NOT evict the library the first still holds");
        assertSame(shared, open.get(0), "and must not replace it either");
        assertEquals(1, manager.getLibraryMembers(first).count(),
                "the first holder's ref is still usable after the second released its own");
        IDataTableLibraryRef third = manager.getLibraryRef(uri, null);
        assertSame(shared, libraryOf(third),
                "still cached: opening the uri again answers the same instance, no reload");
        assertEquals(2, cache().countOwners(uri));

        // The last owner evicts it for real.
        manager.releaseLibraryRef(third);
        manager.releaseLibraryRef(first);
        assertEquals(0, cache().countOwners(uri));
        assertEquals(List.of(), openLibraries(), "the last release must still evict");
    }


    /**
     * The only way a close is observable in this module: a library double that counts its
     * {@code close()} calls, placed in the cache under an existing folder's uri and timestamp, so
     * {@code getLibraryRef} answers it from the cache.
     */
    @Test
    void theLastReleaseClosesTheLibrary_onceAndNoEarlierReleaseDoes() throws Exception
    {
        URI uri = folder("closing");
        // Closed by the manager's last release; counting that close is the point of the test.
        @SuppressWarnings("PMD.CloseResource")
        ClosingLibrary lib = new ClosingLibrary(uri);
        cache().storeLibrary(uri, lib, null, cache().getLastModified(uri));

        IDataTableLibraryRef a = manager.getLibraryRef(uri, null);
        IDataTableLibraryRef b = manager.getLibraryRef(uri, null);
        assertSame(lib, libraryOf(a), "precondition: served from the cache");
        assertSame(lib, libraryOf(b), "precondition: served from the cache");

        manager.releaseLibraryRef(a);
        assertEquals(0, lib.closeCount, "an earlier release must not close the shared library");

        manager.releaseLibraryRef(b);
        assertEquals(1, lib.closeCount, "the last release closes it, once");
        assertEquals(List.of(), openLibraries(), "and evicts it");
    }


    /**
     * This manager has no borrowed door (no member-derived ref, no enumeration of the open set),
     * but the public builder makes a ref without a token, and the release must refuse it (Q44-throw
     * ⓐ: warn and refuse, never obey).
     */
    @Test
    void aRefWithoutAToken_isBorrowed_andReleasingItIsRefused() throws Exception
    {
        URI uri = folder("borrowed", "dm.csv");
        IDataTableLibraryRef owner = manager.getLibraryRef(uri, null);
        LocalDataTableLibraryRef borrowed = LocalDataTableLibraryRef.builder().manager(manager)
                .library(libraryOf(owner)).build();
        assertFalse(borrowed.isOwning(), "a ref built without a token acquires nothing");
        assertTrue(((LocalDataTableLibraryRef) owner).isOwning(), "the uri door's ref owns");
        assertEquals(borrowed, owner, "but it stays equal to the owning ref");
        assertEquals(owner.hashCode(), borrowed.hashCode(), "the token is not part of the hash");

        try (LogCapture log = LogCapture.of(LocalDataTableManager.class))
        {
            manager.releaseLibraryRef(borrowed);
            assertTrue(log.anyMessageContains("is a borrowed view"), log.records().toString());
            assertEquals(1, log.count(java.util.logging.Level.WARNING),
                    "the refusal is reported as a WARNING: " + log.records());
        }

        assertEquals(1, cache().countOwners(uri), "releasing a borrowed ref changes no count");
        assertEquals(1, openLibraries().size(),
                "a borrowed ref must not be able to evict a library it does not own");

        manager.releaseLibraryRef(owner);
        assertEquals(0, openLibraries().size());
    }


    @Test
    void releasingTheSameRefTwice_doesNotConsumeAnotherHoldersCount() throws Exception
    {
        URI uri = folder("twice", "dm.csv");
        IDataTableLibraryRef first = manager.getLibraryRef(uri, null);
        IDataTableLibraryRef second = manager.getLibraryRef(uri, null);

        manager.releaseLibraryRef(first);
        manager.releaseLibraryRef(first);

        assertEquals(1, cache().countOwners(uri),
                "the second release of the same ref must not consume the other holder's count");
        assertEquals(1, openLibraries().size(), "so the library is still open");

        manager.releaseLibraryRef(second);
        assertEquals(0, openLibraries().size());
    }


    @Test
    void aFreshLoadUnderTheSameUri_startsFromZeroOwners() throws Exception
    {
        URI uri = folder("stale", "dm.csv");
        IDataTableLibraryRef stale = manager.getLibraryRef(uri, null);
        // Owned by the manager; releaseLibraryRef() releases it.
        @SuppressWarnings("PMD.CloseResource")
        IDataTableLibrary staleLib = libraryOf(stale);

        // Simulate the cache moving on: the entry is dropped (weak collection / stale timestamp)
        // and the uri is opened again, producing a NEW instance under the same key.
        cache().removeLibrary(uri);
        IDataTableLibraryRef fresh = manager.getLibraryRef(uri, null);
        assertEquals(1, cache().countOwners(uri),
                "the new instance must not inherit the old instance's count");

        // The orphaned holder releasing must close its own instance without evicting the successor.
        manager.releaseLibraryRef(stale);
        assertEquals(1, cache().countOwners(uri), "the successor's count is untouched");
        List<IDataTableLibrary> open = openLibraries();
        assertEquals(1, open.size(), "and it stays cached");
        assertSame(libraryOf(fresh), open.get(0),
                "the cached instance is still the fresh one, not the released one");
        assertNotSame(staleLib, libraryOf(fresh),
                "precondition: the reload really produced a different instance");

        manager.releaseLibraryRef(fresh);
        assertEquals(0, openLibraries().size());
    }


    /**
     * What each release path tells the log, and the memory lock the last release drops. This
     * manager has no {@code lockLibraryInMemory}, so the lock is taken on the cache directly.
     */
    @Test
    void eachReleasePathLogsWhatItDid_andTheLastReleaseDropsTheMemoryLock() throws Exception
    {
        URI uri = folder("logs", "dm.csv");
        IDataTableLibraryRef first = manager.getLibraryRef(uri, null);
        IDataTableLibraryRef second = manager.getLibraryRef(uri, null);
        // Owned by the manager; releaseLibraryRef() releases it.
        @SuppressWarnings("PMD.CloseResource")
        IDataTableLibrary lib = libraryOf(first);
        cache().lockLibrary(lib, List.of());
        assertTrue(cache().isLocked(lib));
        try (LogCapture log = LogCapture.of(LocalDataTableManager.class))
        {
            manager.releaseLibraryRef(
                    LocalDataTableLibraryRef.builder().manager(manager).library(lib).build());
            assertTrue(log.anyMessageContains("is a borrowed view"), log.records().toString());

            manager.releaseLibraryRef(first);
            assertTrue(log.anyMessageContains("released by one holder, 1 still open"),
                    log.records().toString());
            manager.releaseLibraryRef(first);
            assertTrue(log.anyMessageContains("this ref was already released"),
                    log.records().toString());
            assertTrue(cache().isLocked(lib), "still held by the second holder: still locked");

            manager.releaseLibraryRef(second);
            assertFalse(cache().isLocked(lib), "the last release drops the memory lock");
        }

        URI staleUri = folder("superseded", "dm.csv");
        IDataTableLibraryRef stale = manager.getLibraryRef(staleUri, null);
        cache().removeLibrary(staleUri);
        IDataTableLibraryRef fresh = manager.getLibraryRef(staleUri, null);
        try (LogCapture log = LogCapture.of(LocalDataTableManager.class))
        {
            manager.releaseLibraryRef(stale);
            assertTrue(log.anyMessageContains("Closing superseded instance of library"),
                    log.records().toString());
        }
        manager.releaseLibraryRef(fresh);
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    /** A library that counts its {@code close()} calls; nothing else about it is used. */
    private static final class ClosingLibrary implements IDataTableLibrary
    {

        private final URI uri;

        private int closeCount;

        ClosingLibrary(URI aUri)
        {
            uri = aUri;
        }


        @Override
        public String getName()
        {
            return "closing";
        }


        @Override
        public String getLabel()
        {
            return "closing";
        }


        @Override
        public URI getUri()
        {
            return uri;
        }


        @Override
        public String getType()
        {
            return "closing";
        }


        @Override
        public void close()
        {
            closeCount++;
        }
    }
}
