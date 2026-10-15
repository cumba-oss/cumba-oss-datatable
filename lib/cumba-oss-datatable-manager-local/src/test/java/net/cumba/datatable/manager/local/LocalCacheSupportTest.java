package net.cumba.datatable.manager.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.CachedDataTableColumn;
import net.cumba.datatable.impl.ColumnCachedDataTable;
import net.cumba.datatable.library.IDataTableLibrary;
import net.cumba.datatable.library.ILibraryMember;
import net.cumba.datatable.manager.local.testsupport.ClosingLibrary;
import net.cumba.datatable.metadata.ICodeList;
import net.cumba.datatable.metadata.IDataTableMetadata;
import net.cumba.datatable.metadata.IMetadataLibrary;
import net.cumba.datatable.values.DataValueType;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link LocalCacheSupport}. Covers the weak-reference cache lifecycle for tables
 * and libraries, validity-by-timestamp evictions, the memory-lock map, and the
 * {@code lookup*}/{@code update*}/{@code remove*} surface. Concurrent access through
 * {@link java.util.concurrent.ConcurrentHashMap} is exercised in a final batch.
 *
 * <p>
 * Most fixtures are simple in-package stubs (cheap to construct, no Mockito overhead).
 * {@link IMetadataLibrary} is mocked because the interface chain (IMetadataElement) is heavier than
 * the test surface requires.
 */
class LocalCacheSupportTest
{

    @TempDir
    File tempDir;

    private LocalCacheSupport cache;

    @BeforeEach
    void setUp()
    {
        cache = new LocalCacheSupport();
    }

    // ====================================================================
    // getLastModified
    // ====================================================================


    @Test
    void getLastModified_nullUri_returnsMinusOne()
    {
        assertEquals(-1, cache.getLastModified(null));
    }


    @Test
    void getLastModified_nonFileScheme_returnsMinusOne()
    {
        // The branch is gated by "file".equalsIgnoreCase — non-file URIs short-circuit to -1.
        assertEquals(-1, cache.getLastModified(URI.create("http://example.com/data.csv")));
        assertEquals(-1, cache.getLastModified(URI.create("ssh://host/path")));
        assertEquals(-1, cache.getLastModified(URI.create("mem:/data")));
    }


    @Test
    void getLastModified_existingFile_returnsActualTimestamp() throws Exception
    {
        File f = new File(tempDir, "a.txt");
        Files.writeString(f.toPath(), "x", StandardCharsets.UTF_8);
        long expected = f.lastModified();
        long actual = cache.getLastModified(f.toURI());
        assertEquals(expected, actual);
        assertTrue(actual > 0, "real timestamp must be > 0");
    }


    @Test
    void getLastModified_missingFile_returnsMinusOne()
    {
        // f.lastModified() returns 0 for missing files; the method maps 0 (and negatives) to -1.
        File missing = new File(tempDir, "does-not-exist.txt");
        assertFalse(missing.exists());
        assertEquals(-1, cache.getLastModified(missing.toURI()));
    }


    @Test
    void getLastModified_fileSchemeCaseInsensitive() throws Exception
    {
        // Kills "negated equalsIgnoreCase" mutations.
        File f = new File(tempDir, "case.txt");
        Files.writeString(f.toPath(), "x", StandardCharsets.UTF_8);
        URI mixed = URI.create("FILE://" + f.toURI().getRawSchemeSpecificPart());
        long mixedResult = cache.getLastModified(mixed);
        // The "FILE" scheme MUST be recognised the same as "file".
        assertEquals(f.lastModified(), mixedResult);
    }

    // ====================================================================
    // data table cache: store / lookup / find / remove
    // ====================================================================


    @Test
    void lookupTable_emptyCache_returnsNull()
    {
        assertNull(cache.lookupTable(URI.create("file:///a"), 0L));
    }


    @Test
    void lookupTable_nullUri_returnsNull()
    {
        assertNull(cache.lookupTable(null, 0L));
    }


    @Test
    void storeThenLookupTable_freshEntry_returnsSameInstance()
    {
        URI uri = URI.create("file:///a.csv");
        IDataTable table = simpleTable("a");
        cache.storeTable(uri, table, null, null, 100L);
        IDataTable result = cache.lookupTable(uri, 100L);
        assertSame(table, result);
    }


    @Test
    void lookupTable_staleTimestamp_evictsAndReturnsNull()
    {
        URI uri = URI.create("file:///a.csv");
        IDataTable table = simpleTable("a");
        cache.storeTable(uri, table, null, null, 100L);
        // Different timestamp → entry must be evicted, future lookups also miss
        assertNull(cache.lookupTable(uri, 200L));
        assertNull(cache.findCachedTable(uri), "stale entry must have been removed from the cache");
    }


    @Test
    void findCachedTable_ignoresTimestamp_butStillReturnsCachedInstance()
    {
        URI uri = URI.create("file:///a.csv");
        IDataTable table = simpleTable("a");
        cache.storeTable(uri, table, null, null, 100L);
        // findCachedTable does not validate timestamp — should return regardless
        assertSame(table, cache.findCachedTable(uri));
    }


    @Test
    void findCachedTable_emptyCache_returnsNull()
    {
        assertNull(cache.findCachedTable(URI.create("file:///nope")));
    }


    @Test
    void removeTable_evictsEntry()
    {
        URI uri = URI.create("file:///a.csv");
        cache.storeTable(uri, simpleTable("a"), null, null, 100L);
        cache.removeTable(uri);
        assertNull(cache.findCachedTable(uri));
        assertNull(cache.lookupTable(uri, 100L));
    }


    @Test
    void storeTable_returningStrongEntry_preservesLibraryAndMemberFields() throws IOException
    {
        // Library + member should be retrievable via the strong-entry path
        URI uri = URI.create("file:///t.csv");
        try (IDataTableLibrary lib = stubLibrary(URI.create("file:///lib")))
        {
            ILibraryMember mem = stubMember("dm", lib);
            IDataTable table = simpleTable("dm");
            cache.storeTable(uri, table, lib, mem, 50L);
            // Re-lookup; the underlying entry still carries the library/member references through
            // the
            // weak→strong rebuild round-trip.
            assertSame(table, cache.lookupTable(uri, 50L));
        }
    }

    // ====================================================================
    // library cache: store / lookup / remove + metadata + report
    // ====================================================================


    @Test
    void lookupLibrary_emptyCache_returnsNull()
    {
        assertNull(cache.lookupLibrary(URI.create("file:///l"), 0L));
    }


    @Test
    void lookupLibrary_nullUri_returnsNull()
    {
        assertNull(cache.lookupLibrary(null, 0L));
    }


    @Test
    void storeThenLookupLibrary_freshEntry_returnsSameInstance() throws IOException
    {
        URI uri = URI.create("file:///lib1");
        try (IDataTableLibrary lib = stubLibrary(uri))
        {
            cache.storeLibrary(uri, lib, null, 100L);
            assertSame(lib, cache.lookupLibrary(uri, 100L));
        }
    }


    @Test
    void lookupLibrary_staleTimestamp_evictsAndReturnsNull() throws IOException
    {
        URI uri = URI.create("file:///lib1");
        try (IDataTableLibrary lib = stubLibrary(uri))
        {
            cache.storeLibrary(uri, lib, null, 100L);
            assertNull(cache.lookupLibrary(uri, 200L));
            assertNull(cache.lookupLibrary(uri, 100L),
                    "stale entry must be evicted (subsequent timestamp-matching lookup also misses)");
        }
    }


    @Test
    void removeLibrary_alsoClearsMemoryLockForSameUri() throws IOException
    {
        URI uri = URI.create("file:///lib1");
        try (IDataTableLibrary lib = stubLibrary(uri))
        {
            cache.storeLibrary(uri, lib, null, 100L);
            cache.lockLibrary(lib, List.of(simpleTable("a")));
            assertTrue(cache.isLocked(lib));
            cache.removeLibrary(uri, lib);
            assertFalse(cache.isLocked(lib),
                    "removeLibrary must drop the matching memory lock too");
            assertNull(cache.lookupLibrary(uri, 100L));
        }
    }


    @Test
    void lookupMetadata_byLibrary_nullLibrary_returnsNull()
    {
        assertNull(cache.lookupMetadata((IDataTableLibrary) null));
    }


    @Test
    void lookupMetadata_byLibrary_uncachedLibrary_returnsNull() throws IOException
    {
        try (IDataTableLibrary lib = stubLibrary(URI.create("file:///not-cached")))
        {
            assertNull(cache.lookupMetadata(lib));
        }
    }


    @Test
    void lookupMetadata_byLibrary_cachedWithMetadata_returnsIt() throws IOException
    {
        URI uri = URI.create("file:///lib1");
        try (IDataTableLibrary lib = stubLibrary(uri))
        {
            IMetadataLibrary meta = mock(IMetadataLibrary.class);
            cache.storeLibrary(uri, lib, meta, 100L);
            assertSame(meta, cache.lookupMetadata(lib));
        }
    }


    @Test
    void lookupMetadata_byMember_nullMember_returnsNull()
    {
        assertNull(cache.lookupMetadata((ILibraryMember) null));
    }


    @Test
    void lookupMetadata_byMember_delegatesToLibraryLookup() throws IOException
    {
        URI libUri = URI.create("file:///lib1");
        try (IDataTableLibrary lib = stubLibrary(libUri))
        {
            IMetadataLibrary meta = mock(IMetadataLibrary.class);
            cache.storeLibrary(libUri, lib, meta, 100L);
            ILibraryMember mem = stubMember("dm", lib);
            assertSame(meta, cache.lookupMetadata(mem));
        }
    }


    @Test
    void updateMetadata_uncachedLibrary_isNoOp() throws IOException
    {
        try (IDataTableLibrary lib = stubLibrary(URI.create("file:///nope")))
        {
            IMetadataLibrary meta = mock(IMetadataLibrary.class);
            cache.updateMetadata(lib, meta);
            // Was never stored, so still null
            assertNull(cache.lookupMetadata(lib));
        }
    }


    @Test
    void updateMetadata_cachedLibrary_replacesMetadata() throws IOException
    {
        URI uri = URI.create("file:///lib1");
        try (IDataTableLibrary lib = stubLibrary(uri))
        {
            cache.storeLibrary(uri, lib, null, 100L);
            assertNull(cache.lookupMetadata(lib));
            IMetadataLibrary meta = mock(IMetadataLibrary.class);
            cache.updateMetadata(lib, meta);
            assertSame(meta, cache.lookupMetadata(lib));
        }
    }


    @Test
    void updateMetadata_nullLibrary_isNoOp() throws IOException
    {
        URI uri = URI.create("file:///lib1");
        try (IDataTableLibrary lib = stubLibrary(uri))
        {
            IMetadataLibrary meta = mock(IMetadataLibrary.class);
            cache.storeLibrary(uri, lib, meta, 100L);

            // A null library is ignored: no entry's metadata is replaced.
            cache.updateMetadata(null, mock(IMetadataLibrary.class));

            assertSame(meta, cache.lookupMetadata(lib));
        }
    }

    // ====================================================================
    // memory locks
    // ====================================================================


    @Test
    void lockLibrary_thenIsLocked_returnsTrue() throws IOException
    {
        try (IDataTableLibrary lib = stubLibrary(URI.create("file:///lib1")))
        {
            cache.lockLibrary(lib, List.of(simpleTable("a")));
            assertTrue(cache.isLocked(lib));
        }
    }


    @Test
    void unlockLibrary_unlocks() throws IOException
    {
        try (IDataTableLibrary lib = stubLibrary(URI.create("file:///lib1")))
        {
            cache.lockLibrary(lib, List.of(simpleTable("a")));
            cache.unlockLibrary(lib);
            assertFalse(cache.isLocked(lib));
        }
    }


    @Test
    void isLocked_nullLibrary_returnsFalse()
    {
        assertFalse(cache.isLocked(null));
    }


    @Test
    void isLocked_libraryWithNullUri_returnsFalse() throws IOException
    {
        try (IDataTableLibrary lib = stubLibrary(null))
        {
            assertFalse(cache.isLocked(lib));
        }
    }


    @Test
    void lockLibrary_nullLibrary_isNoOp() throws IOException
    {
        try (IDataTableLibrary other = stubLibrary(URI.create("file:///other")))
        {
            cache.lockLibrary(null, List.of(simpleTable("a")));

            // Nothing was locked under any key a real library could probe.
            assertFalse(cache.isLocked(null));
            assertFalse(cache.isLocked(other));
        }
    }


    @Test
    void lockLibrary_nullUri_isNoOp() throws IOException
    {
        try (IDataTableLibrary lib = stubLibrary(null))
        {
            cache.lockLibrary(lib, List.of(simpleTable("a")));
            assertFalse(cache.isLocked(lib));
        }
    }


    @Test
    void unlockLibrary_nullLibrary_isNoOp() throws IOException
    {
        try (IDataTableLibrary lib = stubLibrary(URI.create("file:///lib1")))
        {
            cache.lockLibrary(lib, List.of(simpleTable("a")));

            // A null library unlocks nothing: the existing lock survives.
            cache.unlockLibrary(null);

            assertTrue(cache.isLocked(lib));
        }
    }


    @Test
    void unlockLibrary_nullUri_isNoOp() throws IOException
    {
        try (IDataTableLibrary lib = stubLibrary(null))
        {
            cache.unlockLibrary(lib);
            assertFalse(cache.isLocked(lib));
        }
    }

    // ====================================================================
    // library reference counting: owner sets per loaded instance (Q44, PACK G 17)
    // ====================================================================


    @Test
    // The ClosingLibrary doubles are closed by the code under test; counting those closes is
    // the point.
    @SuppressWarnings("PMD.CloseResource")
    void ownership_eachAcquisitionCountsOnce_andAReleasedTokenIsRefusedByItsFlag()
    {
        URI uri = URI.create("file:///owned/");
        ClosingLibrary lib = new ClosingLibrary(uri);
        storeAt(uri, lib, 1L);
        LocalDataTableLibraryRef.Ownership first = cache.acquireIfCurrent(uri, lib);
        LocalDataTableLibraryRef.Ownership second = cache.acquireIfCurrent(uri, lib);
        assertNotNull(first);
        assertNotNull(second);
        assertEquals(2, cache.countOwners(uri));

        assertTrue(first.markReleased());
        assertEquals(new LocalCacheSupport.Released(1, false), cache.release(first),
                "one owner remains");
        assertFalse(first.markReleased(), "a repeated release is refused by the token's flag");

        assertTrue(second.markReleased());
        assertEquals(new LocalCacheSupport.Released(0, true), cache.release(second),
                "the last owner evicts the instance it holds");
        assertNull(cache.lookupLibrary(uri, 1L), "evicted");
        assertNull(cache.acquireIfCurrent(uri, lib), "a retired instance is never joined again");
        assertEquals(0, lib.closeCount(), "closing is the manager's, after the release");
    }


    @Test
    // The ClosingLibrary doubles are closed by the code under test; counting those closes is
    // the point.
    @SuppressWarnings("PMD.CloseResource")
    void storeLibrary_detachesThePreviousInstancesOwnersWithoutEmptyingThem()
    {
        URI uri = URI.create("file:///reloaded/");
        ClosingLibrary old = new ClosingLibrary(uri);
        ClosingLibrary successor = new ClosingLibrary(uri);
        storeAt(uri, old, 1L);
        LocalDataTableLibraryRef.Ownership held = cache.acquireIfCurrent(uri, old);
        assertNotNull(held);

        storeAt(uri, successor, 2L);
        assertEquals(0, cache.countOwners(uri), "the successor starts from zero owners");
        assertEquals(0, old.closeCount(), "a joined instance is not closed by the reload");

        assertTrue(held.markReleased());
        assertEquals(new LocalCacheSupport.Released(0, false), cache.release(held),
                "the old token still counts its own instance down, which is no longer cached");
        assertSame(successor, cache.lookupLibrary(uri, 2L), "the successor stays cached");
    }


    @Test
    // The ClosingLibrary doubles are closed by the code under test; counting those closes is
    // the point.
    @SuppressWarnings("PMD.CloseResource")
    void storeLibrary_ofTheInstanceAlreadyCached_keepsItsOwners()
    {
        URI uri = URI.create("file:///restored/");
        ClosingLibrary lib = new ClosingLibrary(uri);
        storeAt(uri, lib, 1L);
        LocalDataTableLibraryRef.Ownership held = cache.acquireIfCurrent(uri, lib);
        assertNotNull(held);

        storeAt(uri, lib, 2L);
        assertEquals(1, cache.countOwners(uri), "a re-store keeps the instance's owner set");
        assertEquals(0, lib.closeCount(), "and closes nothing");
        assertTrue(held.markReleased());
        assertEquals(new LocalCacheSupport.Released(0, true), cache.release(held));
    }


    @Test
    // The ClosingLibrary doubles are closed by the code under test; counting those closes is
    // the point.
    @SuppressWarnings("PMD.CloseResource")
    void storeLibrary_closesASupersededInstanceNobodyJoined_once()
    {
        URI uri = URI.create("file:///unjoined/");
        ClosingLibrary unjoined = new ClosingLibrary(uri);
        ClosingLibrary successor = new ClosingLibrary(uri);
        storeAt(uri, unjoined, 1L);
        // the lock arm (PACK G fold, G17 review): the unjoined instance's memory lock goes with it
        cache.lockLibrary(unjoined, List.of());
        assertTrue(cache.isLocked(unjoined), "precondition: locked");
        storeAt(uri, successor, 2L);
        assertEquals(1, unjoined.closeCount(), "an instance nobody joined closes as it leaves");
        assertFalse(cache.isLocked(unjoined), "its lock is dropped as it leaves");
        assertFalse(cache.isLocked(successor), "the successor's URI holds no lock of its own");
        storeAt(uri, successor, 3L);
        assertEquals(0, successor.closeCount(), "a re-store is not a supersede");
        assertEquals(1, unjoined.closeCount(), "closed exactly once");
    }


    /**
     * T4's close-failure branch (PACK G fold, G17 review): an unjoined instance whose
     * {@code close()} throws is logged at WARNING, and the store that superseded it completes.
     */
    @Test
    @SuppressWarnings("PMD.CloseResource")
    void storeLibrary_aSupersededInstanceWhoseCloseThrows_isLoggedNotThrown()
    {
        URI uri = URI.create("file:///unjoined-throws/");
        IDataTableLibrary throwing = new ThrowingCloseLibrary(uri);
        ClosingLibrary successor = new ClosingLibrary(uri);
        storeAt(uri, throwing, 1L);
        try (net.cumba.datatable.manager.local.testsupport.LogCapture logs = net.cumba.datatable.manager.local.testsupport.LogCapture
                .of(LocalCacheSupport.class))
        {
            storeAt(uri, successor, 2L);
            assertEquals(1, logs.count(java.util.logging.Level.WARNING), logs.records()::toString);
            assertTrue(
                    logs.records().stream()
                            .anyMatch(r -> r.getMessage().contains("left the cache failed")),
                    () -> logs.records().stream().map(java.util.logging.LogRecord::getMessage)
                            .toList().toString());
        }
        assertSame(successor, cache.lookupLibrary(uri, 2L), "the store completed");
    }


    /**
     * isLocked answers for THIS instance (PACK G fold, G17 review): a superseded instance is not
     * locked by its successor's lock, and the successor not by the old one's.
     */
    @Test
    void isLocked_answersByInstanceNotByUri()
    {
        URI uri = URI.create("file:///locked/");
        try (IDataTableLibrary old = stubLibrary(uri);
                IDataTableLibrary successor = stubLibrary(uri))
        {
            cache.lockLibrary(old, List.of());
            assertTrue(cache.isLocked(old));
            assertFalse(cache.isLocked(successor), "another instance under the URI is not locked");
            cache.unlockLibrary(successor);
            assertTrue(cache.isLocked(old), "a superseded holder cannot unlock its successor");
            cache.unlockLibrary(old);
            assertFalse(cache.isLocked(old));
        }
        catch (IOException ex)
        {
            throw new AssertionError(ex);
        }
    }


    /** A token minted by another cache is refused, not counted down (PACK G fold, G17 review). */
    @Test
    void release_aTokenOfAnotherCache_isRefused() throws IOException
    {
        URI uri = URI.create("file:///foreign/");
        LocalCacheSupport other = new LocalCacheSupport();
        try (IDataTableLibrary lib = stubLibrary(uri))
        {
            other.storeLibrary(uri, lib, null, 1L);
            storeAt(uri, lib, 1L);
            LocalDataTableLibraryRef.Ownership theirs = other.acquireIfCurrent(uri, lib);
            assertNotNull(theirs);
            assertTrue(other.owns(theirs));
            assertFalse(cache.owns(theirs));
            assertThrows(IllegalArgumentException.class, () -> cache.release(theirs));
            assertEquals(1, other.countOwners(uri), "nothing counted down over there");
            assertTrue(theirs.markReleased());
            assertEquals(new LocalCacheSupport.Released(0, true), other.release(theirs));
        }
    }


    /**
     * I8 (PACK G fold, G17 review): a retired owner set still indexed is a bug, and
     * {@code acquireIfCurrent} says so instead of answering {@code null} and letting the caller
     * spin on a key that never changes. Planted by reflection: no API path reaches the state.
     */
    @Test
    void acquireIfCurrent_aRetiredSetStillIndexed_throwsInsteadOfSpinning() throws Exception
    {
        URI uri = URI.create("file:///retired-indexed/");
        try (IDataTableLibrary lib = stubLibrary(uri))
        {
            storeAt(uri, lib, 1L);
            assertSame(lib, cache.indexedInstance(uri));
            java.lang.reflect.Field map = LocalCacheSupport.class.getDeclaredField("libraryOwners");
            map.setAccessible(true);
            Object owners = ((java.util.Map<?, ?>) map.get(cache)).get(uri);
            java.lang.reflect.Field retired = owners.getClass().getDeclaredField("retired");
            retired.setAccessible(true);
            retired.setBoolean(owners, true);
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> cache.acquireIfCurrent(uri, lib));
            assertTrue(ex.getMessage().contains("I8"), ex.getMessage());
        }
    }


    @Test
    // The ClosingLibrary doubles are closed by the code under test; counting those closes is
    // the point.
    @SuppressWarnings("PMD.CloseResource")
    void removeLibrary_detachesThatKeyOnly_andClosesOnlyAnUnjoinedInstance()
    {
        URI uri = URI.create("file:///removed/");
        URI other = URI.create("file:///other/");
        ClosingLibrary unjoined = new ClosingLibrary(uri);
        ClosingLibrary kept = new ClosingLibrary(other);
        storeAt(uri, unjoined, 1L);
        storeAt(other, kept, 1L);
        assertNotNull(cache.acquireIfCurrent(other, kept));

        cache.removeLibrary(uri, unjoined);
        assertNull(cache.lookupLibrary(uri, 1L));
        assertEquals(1, unjoined.closeCount(), "an unjoined instance closes as it leaves");
        assertEquals(1, cache.countOwners(other), "another key's owners are untouched");

        ClosingLibrary joined = new ClosingLibrary(uri);
        storeAt(uri, joined, 2L);
        LocalDataTableLibraryRef.Ownership held = cache.acquireIfCurrent(uri, joined);
        assertNotNull(held);
        cache.removeLibrary(uri, joined);
        assertEquals(0, joined.closeCount(), "a held instance is closed by its last holder");
        assertEquals(0, cache.countOwners(uri), "detached from the index");
        assertTrue(held.markReleased());
        assertEquals(new LocalCacheSupport.Released(0, false), cache.release(held),
                "its holder still counts it down");
    }


    @Test
    // The ClosingLibrary doubles are closed by the code under test; counting those closes is
    // the point.
    @SuppressWarnings("PMD.CloseResource")
    void removeLibrary_ofAnInstanceAlreadyReplaced_isANoOp()
    {
        URI uri = URI.create("file:///aba/");
        ClosingLibrary stale = new ClosingLibrary(uri);
        ClosingLibrary successor = new ClosingLibrary(uri);
        storeAt(uri, stale, 1L);
        storeAt(uri, successor, 2L);
        assertNotNull(cache.acquireIfCurrent(uri, successor));

        cache.removeLibrary(uri, stale);
        assertSame(successor, cache.lookupLibrary(uri, 2L),
                "removing the instance a stale lookup read must not remove its successor");
        assertEquals(1, cache.countOwners(uri), "nor the successor's owners");
    }


    @Test
    // The ClosingLibrary doubles are closed by the code under test; counting those closes is
    // the point.
    @SuppressWarnings("PMD.CloseResource")
    void release_evictsOnlyTheInstanceStillIndexed()
    {
        URI uri = URI.create("file:///current/");
        ClosingLibrary old = new ClosingLibrary(uri);
        ClosingLibrary fresh = new ClosingLibrary(uri);
        storeAt(uri, old, 1L);
        LocalDataTableLibraryRef.Ownership oldToken = cache.acquireIfCurrent(uri, old);
        assertNotNull(oldToken);
        storeAt(uri, fresh, 2L);
        LocalDataTableLibraryRef.Ownership freshToken = cache.acquireIfCurrent(uri, fresh);
        assertNotNull(freshToken);

        assertTrue(oldToken.markReleased());
        assertEquals(new LocalCacheSupport.Released(0, false), cache.release(oldToken));
        assertSame(fresh, cache.lookupLibrary(uri, 2L), "the superseded release evicts nothing");
        assertTrue(freshToken.markReleased());
        assertEquals(new LocalCacheSupport.Released(0, true), cache.release(freshToken));
        assertNull(cache.lookupLibrary(uri, 2L), "the current one's last release evicts it");
    }


    @Test
    // The ClosingLibrary doubles are closed by the code under test; counting those closes is
    // the point.
    @SuppressWarnings("PMD.CloseResource")
    void release_ofATokenMissingFromItsOwnSet_throws()
    {
        URI uri = URI.create("file:///breach/");
        ClosingLibrary lib = new ClosingLibrary(uri);
        storeAt(uri, lib, 1L);
        LocalDataTableLibraryRef.Ownership token = cache.acquireIfCurrent(uri, lib);
        assertNotNull(token);
        cache.release(token);
        // bypassing the released flag on purpose: a breach is a bug, not a WARNING
        assertThrows(IllegalStateException.class, () -> cache.release(token));
    }


    @Test
    // The ClosingLibrary doubles are closed by the code under test; counting those closes is
    // the point.
    @SuppressWarnings("PMD.CloseResource")
    void acquireIfCurrent_afterASupersedeBetweenLookupAndJoin_refusesAndCountsNothing()
    {
        URI uri = URI.create("file:///b1/");
        ClosingLibrary looked = new ClosingLibrary(uri);
        ClosingLibrary successor = new ClosingLibrary(uri);
        storeAt(uri, looked, 1L);
        assertSame(looked, cache.lookupLibrary(uri, 1L), "the opener's lookup");
        storeAt(uri, successor, 1L); // another thread reloads in between
        assertNull(cache.acquireIfCurrent(uri, looked), "the join refuses the superseded instance");
        assertEquals(0, cache.countOwners(uri), "and puts no count on the successor");
    }


    @Test
    // The ClosingLibrary doubles are closed by the code under test; counting those closes is
    // the point.
    @SuppressWarnings("PMD.CloseResource")
    void acquireIfCurrent_afterALastReleaseBetweenLookupAndJoin_refuses()
    {
        URI uri = URI.create("file:///b2/");
        ClosingLibrary lib = new ClosingLibrary(uri);
        storeAt(uri, lib, 1L);
        LocalDataTableLibraryRef.Ownership last = cache.acquireIfCurrent(uri, lib);
        assertNotNull(last);
        assertSame(lib, cache.lookupLibrary(uri, 1L), "the opener's lookup");
        assertTrue(last.markReleased());
        cache.release(last); // the last holder lets go in between: retired and evicted
        assertNull(cache.acquireIfCurrent(uri, lib), "the join refuses the retired instance");
        assertEquals(0, cache.countOwners(uri), "and mints nothing on an empty key");
    }

    // ====================================================================
    // PACK G 17: per-instance identity (F1 unit, T11)
    // ====================================================================


    @Test
    void unlockLibrary_leavesAnotherInstancesLockUnderTheSameUri() throws IOException
    {
        URI uri = URI.create("file:///shared-lock");
        try (IDataTableLibrary old = stubLibrary(uri);
                IDataTableLibrary successor = stubLibrary(uri))
        {
            cache.lockLibrary(successor, List.of(simpleTable("a")));
            cache.unlockLibrary(old);
            assertTrue(cache.isLocked(successor),
                    "unlocking one instance must not drop another instance's lock under its URI");
            cache.unlockLibrary(successor);
            assertFalse(cache.isLocked(successor));
        }
    }


    /**
     * T11, as re-ruled in the PACK G fold (G17 review, option (i)): a holder of a superseded
     * instance attaches and looks up under the same URI as the successor. Nothing it attaches may
     * land on the successor; its READS fall back to the successor's entry (the pre-G17 behaviour:
     * the lookup half that answered null cut a superseded holder off from the define metadata).
     */
    @Test
    void aSupersededHoldersAttachNeverReachesTheSuccessor() throws IOException
    {
        URI uri = URI.create("file:///reloaded-lib");
        try (IDataTableLibrary old = stubLibrary(uri);
                IDataTableLibrary successor = stubLibrary(uri))
        {
            assertEquals(old.getUri(), successor.getUri(), "precondition: one cache key");
            IMetadataLibrary successorMeta = new NamedMetadata("successor");
            cache.storeLibrary(uri, old, null, 1L);
            cache.storeLibrary(uri, successor, successorMeta, 2L);

            cache.updateMetadata(old, new NamedMetadata("old"));

            assertSame(successorMeta, cache.lookupMetadata(successor), "successor's metadata kept");
            assertSame(successorMeta, cache.lookupMetadata(old),
                    "the old holder READS the successor's metadata (option (i))");
            assertSame(successorMeta, cache.lookupMetadata(stubMember("dm", old)),
                    "a member of the old instance reads the successor's metadata");
            assertSame(successorMeta, cache.lookupMetadata(stubMember("dm", successor)));
        }
    }

    /** A metadata library that is nothing but its name (distinct instances, no Mockito). */
    private record NamedMetadata(String name) implements IMetadataLibrary
    {

        @Override
        public String getName()
        {
            return name;
        }


        @Override
        public @Nullable String getVersion()
        {
            return null;
        }


        @Override
        public boolean isColumnNameCaseSensitive()
        {
            return false;
        }


        @Override
        public List<IDataTableMetadata> getDataTables()
        {
            return List.of();
        }


        @Override
        public Optional<IDataTableMetadata> getDataTable(String aDomainName)
        {
            return Optional.empty();
        }


        @Override
        public List<ICodeList> getCodelists()
        {
            return List.of();
        }


        @Override
        public Optional<ICodeList> getCodelist(String aName)
        {
            return Optional.empty();
        }


        @Override
        public Set<String> getMetaKeys()
        {
            return Set.of();
        }


        @Override
        public Optional<Object> getMetaValue(String aKey)
        {
            return Optional.empty();
        }
    }

    // ====================================================================
    // streamLibraries
    // ====================================================================

    @Test
    void streamLibraries_emptyCache_returnsEmptyStream()
    {
        assertEquals(0L, cache.streamLibraries().count());
    }


    @Test
    void streamLibraries_returnsAllCached() throws IOException
    {
        try (IDataTableLibrary l1 = stubLibrary(URI.create("file:///l1"));
                IDataTableLibrary l2 = stubLibrary(URI.create("file:///l2")))
        {
            cache.storeLibrary(l1.getUri(), l1, null, 100L);
            cache.storeLibrary(l2.getUri(), l2, null, 100L);
            List<IDataTableLibrary> all = cache.streamLibraries().toList();
            assertEquals(2, all.size());
            assertTrue(all.contains(l1));
            assertTrue(all.contains(l2));
        }
    }

    // ====================================================================
    // concurrency
    // ====================================================================


    // fire-and-forget pool.submit; synchronised via the done CountDownLatch.
    @Test
    // A deadlocked worker would make the pool's close() wait forever; the timeout turns that
    // hang into a red test. SEPARATE_THREAD: interrupting close() alone would not end the wait.
    @Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    @SuppressWarnings("FutureReturnValueIgnored")
    void concurrentStoreAndLookup_isSafe() throws Exception
    {
        // Sanity check that the ConcurrentHashMap-backed cache survives parallel access.
        try (ExecutorService pool = Executors.newFixedThreadPool(8))
        {
            try
            {
                int writers = 4;
                int writesPerWriter = 50;
                CountDownLatch start = new CountDownLatch(1);
                CountDownLatch done = new CountDownLatch(writers * 2);
                Queue<Throwable> errors = new ConcurrentLinkedQueue<>();
                for (int w = 0; w < writers; w++)
                {
                    final int wid = w;
                    pool.submit(() ->
                    {
                        try
                        {
                            start.await();
                            for (int i = 0; i < writesPerWriter; i++)
                            {
                                URI uri = URI.create("file:///t-" + wid + "-" + i);
                                cache.storeTable(uri, simpleTable("t"), null, null, i);
                            }
                        }
                        catch (Exception | Error e)
                        {
                            errors.add(e);
                        }
                        finally
                        {
                            done.countDown();
                        }
                    });
                    pool.submit(() ->
                    {
                        try
                        {
                            start.await();
                            for (int i = 0; i < writesPerWriter; i++)
                            {
                                URI uri = URI.create("file:///t-" + wid + "-" + i);
                                cache.findCachedTable(uri);
                            }
                        }
                        catch (Exception | Error e)
                        {
                            errors.add(e);
                        }
                        finally
                        {
                            done.countDown();
                        }
                    });
                }
                start.countDown();
                assertTrue(done.await(10, TimeUnit.SECONDS), "workers stalled");
                // A worker that threw must fail the test, not vanish into a swallowed catch.
                assertTrue(errors.isEmpty(), () -> "worker failures: " + errors);
            }
            finally
            {
                pool.shutdownNow();
            }
        }
    }

    // ====================================================================
    // Helpers
    // ====================================================================


    /**
     * Store a library under a key (PACK G 17). The one line that differs from the internal twin:
     * {@code storeLibrary} takes no validation report here.
     */
    private void storeAt(URI aUri, IDataTableLibrary aLibrary, long aLastModified)
    {
        cache.storeLibrary(aUri, aLibrary, null, aLastModified);
    }


    private static ColumnCachedDataTable simpleTable(String aName)
    {
        DataTableColumnMeta cm = DataTableColumnMeta.builder().index(0).name("X")
                .type(DataValueType.STRING).build();
        DataTableMeta meta = DataTableMeta.builder().name(aName).setColumns(cm).rowCount(0).build();
        return new ColumnCachedDataTable(meta, new CachedDataTableColumn(0, DataValueType.STRING));
    }


    private static IDataTableLibrary stubLibrary(URI aUri)
    {
        return new IDataTableLibrary()
        {

            @Override
            public String getName()
            {
                return aUri == null ? "null" : aUri.toString();
            }


            @Override
            public String getLabel()
            {
                return getName();
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


    private static ILibraryMember stubMember(String aName, IDataTableLibrary aLib)
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
                return aName;
            }


            @Override
            public URI getUri()
            {
                return URI.create("file:///" + aName);
            }


            @Override
            public IDataTableLibrary getLibrary()
            {
                return aLib;
            }
        };
    }

    /** A library whose {@code close()} throws: the T4 close-failure branch's double. */
    private static final class ThrowingCloseLibrary implements IDataTableLibrary
    {

        private final URI uri;

        ThrowingCloseLibrary(URI aUri)
        {
            uri = aUri;
        }


        @Override
        public String getName()
        {
            return "throwing";
        }


        @Override
        public String getLabel()
        {
            return getName();
        }


        @Override
        public URI getUri()
        {
            return uri;
        }


        @Override
        public String getType()
        {
            return "throwing";
        }


        @Override
        public void close() throws IOException
        {
            throw new IOException("close refused");
        }
    }
}
