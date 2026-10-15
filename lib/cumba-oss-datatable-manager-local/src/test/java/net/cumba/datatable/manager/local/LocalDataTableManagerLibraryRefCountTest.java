package net.cumba.datatable.manager.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import net.cumba.datatable.library.IDataTableLibrary;
import net.cumba.datatable.manager.IDataTableLibraryRef;
import net.cumba.datatable.manager.local.testsupport.ClosingLibrary;
import net.cumba.datatable.manager.local.testsupport.LogCapture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
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


    private static LocalCacheSupport cacheOf(LocalDataTableManager aManager) throws Exception
    {
        java.lang.reflect.Field f = LocalDataTableManager.class.getDeclaredField("cacheSupport");
        f.setAccessible(true);
        return (LocalCacheSupport) f.get(aManager);
    }


    private LocalCacheSupport cache() throws Exception
    {
        java.lang.reflect.Field f = LocalDataTableManager.class.getDeclaredField("cacheSupport");
        f.setAccessible(true);
        return (LocalCacheSupport) f.get(manager);
    }


    /**
     * Put a library into the cache under {@code aUri} with the folder's real timestamp, so
     * {@code getLibraryRef} answers it from the cache (PACK G 17). The one line that differs from
     * the internal twin: {@code storeLibrary} takes no validation report here.
     */
    private void plant(URI aUri, IDataTableLibrary aLibrary) throws Exception
    {
        cache().storeLibrary(aUri, aLibrary, null, cache().getLastModified(aUri));
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
        assertEquals(0, lib.closeCount(), "an earlier release must not close the shared library");

        manager.releaseLibraryRef(b);
        assertEquals(1, lib.closeCount(), "the last release closes it, once");
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
            assertEquals(1, log.count(Level.WARNING),
                    "the refusal is reported as a WARNING: " + log.records());
        }

        assertEquals(1, cache().countOwners(uri), "releasing a borrowed ref changes no count");
        assertEquals(1, openLibraries().size(),
                "a borrowed ref must not be able to evict a library it does not own");

        manager.releaseLibraryRef(owner);
        assertEquals(0, openLibraries().size());
    }


    /**
     * PACK G fold (G17 review): a ref whose token another manager minted is refused with a WARNING
     * - nothing of this manager's is counted down, closed or unlocked, and the token still releases
     * on its own manager.
     */
    @Test
    void releasingAnotherManagersRef_isRefusedAndCountsNothingDown() throws Exception
    {
        URI uri = folder("foreign", "dm.csv");
        LocalDataTableManager other = new LocalDataTableManager();
        IDataTableLibraryRef mine = manager.getLibraryRef(uri, null);
        IDataTableLibraryRef theirs = other.getLibraryRef(uri, null);
        // the other manager's ref itself is refused earlier ("not supported": its manager is not
        // this one); the token reaches the count only inside a ref of THIS manager
        LocalDataTableLibraryRef smuggled = LocalDataTableLibraryRef.builder().manager(manager)
                .library(((LocalDataTableLibraryRef) theirs).getLibrary())
                .ownership(((LocalDataTableLibraryRef) theirs).getOwnership()).build();

        try (LogCapture log = LogCapture.of(LocalDataTableManager.class))
        {
            manager.releaseLibraryRef(smuggled);
            assertEquals(1, log.count(Level.WARNING), log.records()::toString);
            assertTrue(
                    log.records().stream()
                            .anyMatch(r -> r.getMessage().contains("minted by another manager")),
                    () -> log.records().stream().map(java.util.logging.LogRecord::getMessage)
                            .toList().toString());
        }
        assertEquals(1, cache().countOwners(uri), "this manager's holder is untouched");
        assertEquals(1, cacheOf(other).countOwners(uri), "and the other manager's holder too");

        other.releaseLibraryRef(theirs);
        assertEquals(0, cacheOf(other).countOwners(uri), "released where it was opened");
        manager.releaseLibraryRef(mine);
        assertEquals(0, cache().countOwners(uri));
    }


    /**
     * PACK G fold (G17 review, lane b item 5): a ref built with a COPIED token whose library is not
     * the token's instance is refused with a WARNING, and the original still releases.
     */
    @Test
    void releasingARefWithACopiedTokenOfAnotherLibrary_isRefused() throws Exception
    {
        URI uri = folder("copied", "dm.csv");
        URI otherUri = folder("copied-other", "dm.csv");
        IDataTableLibraryRef original = manager.getLibraryRef(uri, null);
        IDataTableLibraryRef otherLib = manager.getLibraryRef(otherUri, null);
        LocalDataTableLibraryRef copy = LocalDataTableLibraryRef.builder().manager(manager)
                .library(((LocalDataTableLibraryRef) otherLib).getLibrary())
                .ownership(((LocalDataTableLibraryRef) original).getOwnership()).build();

        try (LogCapture log = LogCapture.of(LocalDataTableManager.class))
        {
            manager.releaseLibraryRef(copy);
            assertEquals(1, log.count(Level.WARNING), log.records()::toString);
            assertTrue(
                    log.records().stream().anyMatch(r -> r.getMessage().contains("copied token")),
                    () -> log.records().stream().map(java.util.logging.LogRecord::getMessage)
                            .toList().toString());
        }
        assertEquals(1, cache().countOwners(uri), "nothing counted down");
        assertEquals(1, cache().countOwners(otherUri), "nothing closed");

        manager.releaseLibraryRef(original);
        manager.releaseLibraryRef(otherLib);
        assertEquals(0, cache().countOwners(uri), "the originals release as before");
        assertEquals(0, cache().countOwners(otherUri));
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
        cache().removeLibrary(uri, staleLib);
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
        cache().removeLibrary(staleUri, libraryOf(stale));
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
    // PACK G 17: owner sets per loaded instance, a refused second release, the release/open race
    // ------------------------------------------------------------------


    private static IDataTableLibrary heldLibrary(IDataTableLibraryRef aRef)
    {
        return ((LocalDataTableLibraryRef) aRef).getLibrary();
    }


    /**
     * MED: two holders of an instance that is then superseded under its URI. The first holder's
     * release must neither close the instance under the second holder nor drop the successor's
     * memory lock; the instance closes at its own last release, and the successor is untouched.
     */
    @Test
    // The ClosingLibrary doubles are closed by the code under test; counting those closes is
    // the point.
    @SuppressWarnings("PMD.CloseResource")
    void aSupersededLibraryStaysOpenForItsOtherHolder() throws Exception
    {
        URI uri = folder("superseded", "dm.csv");
        ClosingLibrary first = new ClosingLibrary(uri);
        plant(uri, first);
        IDataTableLibraryRef a = manager.getLibraryRef(uri, null);
        IDataTableLibraryRef b = manager.getLibraryRef(uri, null);
        assertSame(first, heldLibrary(a), "precondition: A holds the planted instance");
        assertSame(first, heldLibrary(b), "precondition: B holds the planted instance");

        // the reload: a new instance takes over the URI
        ClosingLibrary second = new ClosingLibrary(uri);
        assertEquals(first.getUri(), second.getUri(), "precondition: one memory-lock key");
        plant(uri, second);
        IDataTableLibraryRef c = manager.getLibraryRef(uri, null);
        assertSame(second, heldLibrary(c), "precondition: C holds the successor");
        cache().lockLibrary(second, List.of());

        manager.releaseLibraryRef(a);
        assertEquals(0, first.closeCount(), "B still holds the superseded instance");
        assertTrue(cache().isLocked(second), "A's release must not drop C's lock on the successor");

        manager.releaseLibraryRef(b);
        assertEquals(1, first.closeCount(), "the superseded instance closes at its last release");
        assertEquals(0, second.closeCount(), "the successor is untouched");
        assertTrue(cache().isLocked(second), "B's last release leaves C's lock alone");
        assertEquals(List.of(second), cache().streamLibraries().toList(),
                "the superseded instance's last release evicts nothing");
        assertEquals(1, cache().countOwners(uri), "C still counts on the successor");

        manager.releaseLibraryRef(c);
        assertEquals(1, second.closeCount());
        assertEquals(1, first.closeCount(), "closed exactly once");
    }


    /**
     * MED through the real stale path: the folder's timestamp moves, the next open finds the cached
     * instance stale ({@code lookupLibrary}'s stale branch) and reloads the folder.
     */
    @Test
    // The ClosingLibrary doubles are closed by the code under test; counting those closes is
    // the point.
    @SuppressWarnings("PMD.CloseResource")
    void aSupersededLibraryStaysOpenForItsOtherHolder_whenTheFolderChanged() throws Exception
    {
        URI uri = folder("reloaded", "dm.csv");
        ClosingLibrary first = new ClosingLibrary(uri);
        plant(uri, first);
        IDataTableLibraryRef a = manager.getLibraryRef(uri, null);
        IDataTableLibraryRef b = manager.getLibraryRef(uri, null);
        assertSame(first, heldLibrary(a), "precondition: A holds the planted instance");

        Path dir = Path.of(uri);
        Files.setLastModifiedTime(dir,
                FileTime.fromMillis(Files.getLastModifiedTime(dir).toMillis() + 10_000));
        IDataTableLibraryRef c = manager.getLibraryRef(uri, null);
        // Owned by the manager; releaseLibraryRef() releases it.
        @SuppressWarnings("PMD.CloseResource")
        IDataTableLibrary reloaded = heldLibrary(c);
        assertNotSame(first, reloaded, "precondition: the moved timestamp reloaded the folder");
        assertEquals(first.getUri(), reloaded.getUri(), "precondition: one memory-lock key");
        cache().lockLibrary(reloaded, List.of());

        manager.releaseLibraryRef(a);
        assertEquals(0, first.closeCount(), "B still holds the superseded instance");
        assertTrue(cache().isLocked(reloaded), "A's release must not drop C's lock");

        manager.releaseLibraryRef(b);
        assertEquals(1, first.closeCount(), "the superseded instance closes at its last release");
        assertTrue(cache().isLocked(reloaded), "B's last release leaves C's lock alone");
        assertEquals(List.of(reloaded), cache().streamLibraries().toList());
        manager.releaseLibraryRef(c);
    }


    /**
     * F1: a ref released again after its release closed and evicted the library. The repeat is
     * refused with the WARNING the contract demands; it closes nothing and leaves the memory lock
     * of the library re-opened under that URI since.
     */
    @Test
    // The ClosingLibrary doubles are closed by the code under test; counting those closes is
    // the point.
    @SuppressWarnings("PMD.CloseResource")
    void aSecondReleaseAfterEvictionIsRefused_andTouchesNoSuccessor() throws Exception
    {
        URI uri = folder("evicted", "dm.csv");
        ClosingLibrary first = new ClosingLibrary(uri);
        plant(uri, first);
        IDataTableLibraryRef a = manager.getLibraryRef(uri, null);
        assertSame(first, heldLibrary(a), "precondition: A holds the planted instance");
        manager.releaseLibraryRef(a);
        assertEquals(1, first.closeCount(), "the sole holder's release closes it");
        assertEquals(List.of(), cache().streamLibraries().toList(), "and evicts it");

        IDataTableLibraryRef c = manager.getLibraryRef(uri, null);
        // Owned by the manager; releaseLibraryRef() releases it.
        @SuppressWarnings("PMD.CloseResource")
        IDataTableLibrary reopened = heldLibrary(c);
        assertNotSame(first, reopened, "precondition: the URI was opened again");
        assertEquals(first.getUri(), reopened.getUri(), "precondition: one memory-lock key");
        cache().lockLibrary(reopened, List.of());

        try (LogCapture log = LogCapture.of(LocalDataTableManager.class))
        {
            manager.releaseLibraryRef(a);
            assertTrue(log.anyMessageContains("this ref was already released"),
                    log.records().toString());
        }
        assertEquals(1, first.closeCount(), "a repeated release closes nothing");
        assertTrue(cache().isLocked(reopened), "and leaves the re-opened library's lock");
        assertEquals(1, cache().countOwners(uri), "and takes no count from C");
        manager.releaseLibraryRef(c);
    }


    /**
     * F3 (c): the last holder's release and a concurrent open of the same URI. The releaser parks
     * where the manager reads the released library's URI; the open runs to completion inside that
     * window. Whatever the opener got, it must not be closed under it, and its count must survive.
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    // The ClosingLibrary doubles are closed by the code under test; counting those closes is
    // the point.
    @SuppressWarnings("PMD.CloseResource")
    void aReleaseToZeroAndAConcurrentOpen_neverCloseTheOpenersLibrary() throws Exception
    {
        URI uri = folder("race", "dm.csv");
        ClosingLibrary lib = new ClosingLibrary(uri);
        plant(uri, lib);
        IDataTableLibraryRef a = manager.getLibraryRef(uri, null);
        assertSame(lib, heldLibrary(a), "precondition: A holds the planted instance");

        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<IDataTableLibraryRef> opened = new AtomicReference<>();
        Thread releaser = new Thread(() ->
        {
            try
            {
                manager.releaseLibraryRef(a);
            }
            catch (Exception | Error ex)
            {
                failure.set(ex);
            }
        }, "g17-releaser");
        lib.latchGetUri(releaser);
        releaser.start();
        assertTrue(lib.awaitParked(), "the releaser must reach the window");

        Thread opener = new Thread(() ->
        {
            try
            {
                opened.set(manager.getLibraryRef(uri, null));
            }
            catch (Exception | Error ex)
            {
                failure.set(ex);
            }
        }, "g17-opener");
        opener.start();
        opener.join(5_000);
        assertFalse(opener.isAlive(), "the open must complete while the releaser is parked");

        lib.resume();
        releaser.join(5_000);
        assertFalse(releaser.isAlive(), "the release must complete once resumed");
        assertNull(failure.get(), () -> String.valueOf(failure.get()));

        IDataTableLibraryRef got = opened.get();
        // Owned by the manager; releaseLibraryRef() releases it.
        @SuppressWarnings("PMD.CloseResource")
        IDataTableLibrary held = heldLibrary(got);
        assertTrue(held != lib || lib.closeCount() == 0,
                "the opener must not hold a library its open raced into closing");
        assertEquals(1, cache().countOwners(uri), "the opener's count survives the release");
        manager.releaseLibraryRef(got);
        assertEquals(0, cache().countOwners(uri));
        assertEquals(1, lib.closeCount(), "the released instance is closed exactly once");
    }
}
