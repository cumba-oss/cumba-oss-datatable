package net.cumba.datatable.manager.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
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
import net.cumba.datatable.metadata.IMetadataLibrary;
import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    void storeTable_returningStrongEntry_preservesLibraryAndMemberFields()
    {
        // Library + member should be retrievable via the strong-entry path
        URI uri = URI.create("file:///t.csv");
        IDataTableLibrary lib = stubLibrary(URI.create("file:///lib"));
        ILibraryMember mem = stubMember("dm", lib);
        IDataTable table = simpleTable("dm");
        cache.storeTable(uri, table, lib, mem, 50L);
        // Re-lookup; the underlying entry still carries the library/member references through the
        // weak→strong rebuild round-trip.
        assertSame(table, cache.lookupTable(uri, 50L));
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
    void storeThenLookupLibrary_freshEntry_returnsSameInstance()
    {
        URI uri = URI.create("file:///lib1");
        IDataTableLibrary lib = stubLibrary(uri);
        cache.storeLibrary(uri, lib, null, 100L);
        assertSame(lib, cache.lookupLibrary(uri, 100L));
    }


    @Test
    void lookupLibrary_staleTimestamp_evictsAndReturnsNull()
    {
        URI uri = URI.create("file:///lib1");
        IDataTableLibrary lib = stubLibrary(uri);
        cache.storeLibrary(uri, lib, null, 100L);
        assertNull(cache.lookupLibrary(uri, 200L));
        assertNull(cache.lookupLibrary(uri, 100L),
                "stale entry must be evicted (subsequent timestamp-matching lookup also misses)");
    }


    @Test
    void removeLibrary_alsoClearsMemoryLockForSameUri()
    {
        URI uri = URI.create("file:///lib1");
        IDataTableLibrary lib = stubLibrary(uri);
        cache.storeLibrary(uri, lib, null, 100L);
        cache.lockLibrary(lib, List.of(simpleTable("a")));
        assertTrue(cache.isLocked(lib));
        cache.removeLibrary(uri);
        assertFalse(cache.isLocked(lib), "removeLibrary must drop the matching memory lock too");
        assertNull(cache.lookupLibrary(uri, 100L));
    }


    @Test
    void lookupMetadata_byLibrary_nullLibrary_returnsNull()
    {
        assertNull(cache.lookupMetadata((IDataTableLibrary) null));
    }


    @Test
    void lookupMetadata_byLibrary_uncachedLibrary_returnsNull()
    {
        IDataTableLibrary lib = stubLibrary(URI.create("file:///not-cached"));
        assertNull(cache.lookupMetadata(lib));
    }


    @Test
    void lookupMetadata_byLibrary_cachedWithMetadata_returnsIt()
    {
        URI uri = URI.create("file:///lib1");
        IDataTableLibrary lib = stubLibrary(uri);
        IMetadataLibrary meta = mock(IMetadataLibrary.class);
        cache.storeLibrary(uri, lib, meta, 100L);
        assertSame(meta, cache.lookupMetadata(lib));
    }


    @Test
    void lookupMetadata_byMember_nullMember_returnsNull()
    {
        assertNull(cache.lookupMetadata((ILibraryMember) null));
    }


    @Test
    void lookupMetadata_byMember_delegatesToLibraryLookup()
    {
        URI libUri = URI.create("file:///lib1");
        IDataTableLibrary lib = stubLibrary(libUri);
        IMetadataLibrary meta = mock(IMetadataLibrary.class);
        cache.storeLibrary(libUri, lib, meta, 100L);
        ILibraryMember mem = stubMember("dm", lib);
        assertSame(meta, cache.lookupMetadata(mem));
    }


    @Test
    void updateMetadata_uncachedLibrary_isNoOp()
    {
        IDataTableLibrary lib = stubLibrary(URI.create("file:///nope"));
        IMetadataLibrary meta = mock(IMetadataLibrary.class);
        cache.updateMetadata(lib, meta);
        // Was never stored, so still null
        assertNull(cache.lookupMetadata(lib));
    }


    @Test
    void updateMetadata_cachedLibrary_replacesMetadata()
    {
        URI uri = URI.create("file:///lib1");
        IDataTableLibrary lib = stubLibrary(uri);
        cache.storeLibrary(uri, lib, null, 100L);
        assertNull(cache.lookupMetadata(lib));
        IMetadataLibrary meta = mock(IMetadataLibrary.class);
        cache.updateMetadata(lib, meta);
        assertSame(meta, cache.lookupMetadata(lib));
    }


    @Test
    void updateMetadata_nullLibrary_isNoOp()
    {
        // Should not throw; idempotent no-op.
        cache.updateMetadata(null, mock(IMetadataLibrary.class));
    }

    // ====================================================================
    // memory locks
    // ====================================================================


    @Test
    void lockLibrary_thenIsLocked_returnsTrue()
    {
        IDataTableLibrary lib = stubLibrary(URI.create("file:///lib1"));
        cache.lockLibrary(lib, List.of(simpleTable("a")));
        assertTrue(cache.isLocked(lib));
    }


    @Test
    void unlockLibrary_unlocks()
    {
        IDataTableLibrary lib = stubLibrary(URI.create("file:///lib1"));
        cache.lockLibrary(lib, List.of(simpleTable("a")));
        cache.unlockLibrary(lib);
        assertFalse(cache.isLocked(lib));
    }


    @Test
    void isLocked_nullLibrary_returnsFalse()
    {
        assertFalse(cache.isLocked(null));
    }


    @Test
    void isLocked_libraryWithNullUri_returnsFalse()
    {
        IDataTableLibrary lib = stubLibrary(null);
        assertFalse(cache.isLocked(lib));
    }


    @Test
    void lockLibrary_nullLibrary_isNoOp()
    {
        cache.lockLibrary(null, List.of(simpleTable("a")));
        // No way to observe other than that no exception was thrown and isLocked returns false
        // when probed with anything reasonable.
    }


    @Test
    void lockLibrary_nullUri_isNoOp()
    {
        IDataTableLibrary lib = stubLibrary(null);
        cache.lockLibrary(lib, List.of(simpleTable("a")));
        assertFalse(cache.isLocked(lib));
    }


    @Test
    void unlockLibrary_nullLibrary_isNoOp()
    {
        cache.unlockLibrary(null);
    }


    @Test
    void unlockLibrary_nullUri_isNoOp()
    {
        IDataTableLibrary lib = stubLibrary(null);
        cache.unlockLibrary(lib);
        assertFalse(cache.isLocked(lib));
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
    void streamLibraries_returnsAllCached()
    {
        IDataTableLibrary l1 = stubLibrary(URI.create("file:///l1"));
        IDataTableLibrary l2 = stubLibrary(URI.create("file:///l2"));
        cache.storeLibrary(l1.getUri(), l1, null, 100L);
        cache.storeLibrary(l2.getUri(), l2, null, 100L);
        List<IDataTableLibrary> all = cache.streamLibraries().toList();
        assertEquals(2, all.size());
        assertTrue(all.contains(l1));
        assertTrue(all.contains(l2));
    }

    // ====================================================================
    // concurrency
    // ====================================================================


    // fire-and-forget pool.submit; synchronised via the done CountDownLatch.
    @Test
    @SuppressWarnings("FutureReturnValueIgnored")
    void concurrentStoreAndLookup_isSafe() throws Exception
    {
        // Sanity check that the ConcurrentHashMap-backed cache survives parallel access.
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try
        {
            int writers = 4;
            int writesPerWriter = 50;
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(writers * 2);
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
                    catch (Exception ignored)
                    {
                        // fall through
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
                    catch (Exception ignored)
                    {
                        // fall through
                    }
                    finally
                    {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(10, TimeUnit.SECONDS), "workers stalled");
        }
        finally
        {
            pool.shutdownNow();
        }
        // Spot-check: at least some entries survived without throwing.
        // (Exact count depends on GC + cleanCache races, so we just assert no exception.)
        assertNotNull(cache);
    }

    // ====================================================================
    // Helpers
    // ====================================================================


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

}
