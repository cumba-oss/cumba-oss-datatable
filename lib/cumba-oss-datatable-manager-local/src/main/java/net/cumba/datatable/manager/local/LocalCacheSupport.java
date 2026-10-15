package net.cumba.datatable.manager.local;

import java.io.File;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import lombok.Builder;
import lombok.Getter;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.help.URIHelper;
import net.cumba.datatable.library.IDataTableLibrary;
import net.cumba.datatable.library.ILibraryMember;
import net.cumba.datatable.metadata.IMetadataLibrary;
import org.jspecify.annotations.Nullable;

/**
 * Holds the weak-reference caches for {@link IDataTable} and {@link IDataTableLibrary} instances
 * previously managed directly by {@code LocalDataTableManager}. Entries are revalidated via the
 * underlying file's last-modified timestamp. The cache entry types are internal to this class.
 */
class LocalCacheSupport
{

    private static final Logger LOGGER = System.getLogger(LocalCacheSupport.class.getName());

    private final Map<URI, DataTableCacheEntryWeak> dataTableCache = new ConcurrentHashMap<>();

    private final Map<URI, LibraryCacheEntryWeak> libraryCache = new ConcurrentHashMap<>();

    private final Map<URI, MemoryLock> memoryLocks = new ConcurrentHashMap<>();

    /**
     * The reference-count index (Q44, PACK G 17): a cache key maps to the owner set of the library
     * instance CURRENTLY cached under it. The count is kept per loaded instance, not per key: a
     * reload under the same key attaches a new {@link InstanceOwners} and only detaches the
     * previous one, whose holders keep counting it down and close it at their own last release.
     * <p>
     * Every mutation of a key's owner state - the acquire's add, the release's remove, retiring at
     * zero, the evict, the store's attach and detach, the remove's detach - runs inside
     * {@code libraryOwners.compute(key, ...)}. Lock order: a {@code libraryOwners} key first, then
     * {@link #libraryCache} / {@link #memoryLocks}; never the reverse, never a second
     * {@code libraryOwners} call from inside its own compute, and never a call into a library (no
     * I/O, no {@code close()}, not even {@code getUri()}) inside one. A key's {@code libraryCache}
     * entry is installed or removed only inside that compute too (invariant I7), so the index and
     * the cache never disagree about which <b>live</b> instance a key holds: at quiescence the
     * indexed instance is the cached one ({@link #indexedInstance}). I8: a retired set is never
     * indexed - the release that retires a set at zero detaches it in the same compute, and the
     * store and the remove detach a set before they retire it; {@link #acquireIfCurrent} treats a
     * retired set it still finds indexed as a bug, not as a reason to spin.
     */
    private final Map<URI, InstanceOwners> libraryOwners = new ConcurrentHashMap<>();

    /**
     * Returns the last-modified timestamp of the given URI, or {@code -1} if unknown or not a file
     * URI.
     */
    long getLastModified(@Nullable URI aUri)
    {
        if (aUri == null)
        {
            return -1;
        }
        try
        {
            if ("file".equalsIgnoreCase(aUri.getScheme()))
            {
                File f = new File(URIHelper.replaceFragment(aUri, null));
                long res = f.lastModified();
                return res > 0 ? res : -1;
            }
        }
        catch (Exception ex)
        {
            LOGGER.log(Level.ERROR, ex.getMessage(), ex);
        }
        return -1;
    }

    // ------------------------------------------------------------------
    // Data table cache
    // ------------------------------------------------------------------


    /**
     * Look up a cached data table for the given URI. Returns the table if the cache entry is still
     * valid (timestamp matches {@code aLastModified}). If the entry exists but is stale, it is
     * evicted and {@code null} is returned.
     */
    @Nullable
    IDataTable lookupTable(URI aUri, long aLastModified)
    {
        DataTableCacheEntry entry = getTableEntry(aUri);
        if (entry == null)
        {
            return null;
        }
        if (entry.getTableLastModified() == aLastModified)
        {
            return entry.getTable();
        }
        removeTable(aUri);
        return null;
    }


    /**
     * Find any cached data table for the given URI without validating the timestamp. Returns
     * {@code null} if no entry is present or the weak reference has been cleared.
     */
    @Nullable
    IDataTable findCachedTable(URI aUri)
    {
        DataTableCacheEntry entry = getTableEntry(aUri);
        return entry != null ? entry.getTable() : null;
    }


    /**
     * Store a freshly-loaded data table in the cache. {@code aLibrary} and {@code aMember} may be
     * {@code null} for standalone (non-library) tables.
     */
    void storeTable(URI aUri, IDataTable aTable, @Nullable IDataTableLibrary aLibrary,
            @Nullable ILibraryMember aMember, long aLastModified)
    {
        cleanCache();
        DataTableCacheEntry entry = DataTableCacheEntry.builder()//
                .uri(aUri)//
                .table(aTable)//
                .library(aLibrary)//
                .member(aMember)//
                .tableLastModified(aLastModified)//
                .build();
        dataTableCache.put(aUri, entry.getWeak());
    }

    // ------------------------------------------------------------------
    // Library cache
    // ------------------------------------------------------------------


    /**
     * Look up a cached library for the given URI. Returns the library if the cache entry is still
     * valid (timestamp matches {@code aLastModified}). Stale entries are evicted and {@code null}
     * is returned.
     */
    @Nullable
    IDataTableLibrary lookupLibrary(URI aUri, long aLastModified)
    {
        LibraryCacheEntry entry = getLibraryEntry(aUri);
        if (entry == null)
        {
            return null;
        }
        if (entry.getLibraryLastModified() == aLastModified)
        {
            return entry.getLibrary();
        }
        // only the instance this lookup read: another thread may have replaced it meanwhile
        removeLibrary(aUri, entry.getLibrary());
        return null;
    }


    /**
     * Store a freshly-loaded library in the cache under {@code aUri}, with a new, empty owner set
     * ({@link InstanceOwners}). {@code aMetadata} may be {@code null}.
     * <p>
     * The cache key is the URI the caller asked for, not {@code aLibrary.getUri()}: a provider is
     * free to answer a different URI from the library it produced, and the release must find the
     * entry under the key it was acquired with. The memory-lock key, by contrast, IS
     * {@code aLibrary.getUri()} ({@link #lockLibrary}); it is read here, before the compute, and
     * kept in the owner set, so no library call ever runs inside a compute.
     * <p>
     * The previous instance under the key is detached, never emptied: its holders keep counting it
     * down and close it at their own last release. A previous instance nobody ever joined is closed
     * here, once, as it leaves the cache, and its memory lock is dropped with it (a failing close
     * is logged at WARNING, never thrown into the load). Re-storing the instance already cached
     * keeps its owner set - so a library provider's {@code provide()} must return a FRESH instance
     * for a reload: a provider handing back the instance already cached would make the reload a
     * no-op store and the holders of the "old" instance holders of the new one.
     */
    void storeLibrary(URI aUri, IDataTableLibrary aLibrary, @Nullable IMetadataLibrary aMetadata,
            long aLastModified)
    {
        cleanCache();
        URI lockKey = aLibrary.getUri();
        InstanceOwners[] unjoined = new InstanceOwners[1];
        libraryOwners.compute(aUri, (_, indexed) ->
        {
            // everything that can throw first; the cache put last (I7: a throw leaves both maps
            // as they were)
            LibraryCacheEntry entry = LibraryCacheEntry.builder()//
                    .uri(aUri)//
                    .library(aLibrary)//
                    .metadata(aMetadata)//
                    .libraryLastModified(aLastModified)//
                    .build();
            InstanceOwners owners;
            if (indexed != null && indexed.isOf(aLibrary))
            {
                owners = indexed;
            }
            else
            {
                owners = new InstanceOwners(this, aLibrary, lockKey);
                if (indexed != null && indexed.retireIfUnjoined())
                {
                    dropLockOf(indexed);
                    unjoined[0] = indexed;
                }
            }
            libraryCache.put(aUri, entry.getWeak());
            return owners;
        });
        closeUnjoined(unjoined[0]);
    }


    /**
     * Retrieve the cached metadata for the given library, or {@code null} if none is attached (a
     * superseded holder reads its successor's: {@link #entryUnder}).
     */
    @Nullable
    IMetadataLibrary lookupMetadata(@Nullable IDataTableLibrary aLibrary)
    {
        LibraryCacheEntry entry = entryUnder(aLibrary);
        return entry != null ? entry.getMetadata() : null;
    }


    /**
     * Retrieve the cached metadata for the library owning the given member, or {@code null}.
     */
    @Nullable
    IMetadataLibrary lookupMetadata(@Nullable ILibraryMember aMember)
    {
        if (aMember == null)
        {
            return null;
        }
        return lookupMetadata(aMember.getLibrary());
    }


    /**
     * Attach (or replace) metadata on an already-cached library. No-op if the library is not the
     * instance cached under its URI (not cached, or superseded: T11).
     */
    void updateMetadata(@Nullable IDataTableLibrary aLibrary, @Nullable IMetadataLibrary aMetadata)
    {
        if (aLibrary == null)
        {
            return;
        }
        // compute(): the read-modify-write must be atomic, or a concurrent updateReport /
        // storeReportTable on the same library writes back ITS detached snapshot and silently
        // drops this metadata (F-mlocal review 2026-09-14, finding 12).
        libraryCache.computeIfPresent(aLibrary.getUri(), (_, w) ->
        {
            LibraryCacheEntry entry = w.getStrong();
            if (entry == null || entry.getLibrary() != aLibrary)
            {
                return w;
            }
            entry.setMetadata(aMetadata);
            return entry.getWeak();
        });
    }


    /**
     * Remove {@code aExpected}'s entry from the cache - only if the key still holds that very
     * instance (a lookup that found it stale must not remove a successor another thread stored
     * meanwhile). Its memory lock goes with it (by the lock key captured at store time, and only if
     * the lock is this instance's), and its owner set is detached from the index.
     * <p>
     * The detach is not load-bearing for the count: a detached set is reached only through its own
     * tokens, and the next {@link #storeLibrary} would replace the index entry anyway. It keeps the
     * index equal to the cache after an eviction, which is what lets the last release tell an
     * evicted instance from a superseded one. An instance nobody ever joined is closed here, once.
     *
     * @param aUri
     *            the cache key.
     * @param aExpected
     *            the instance to remove.
     */
    void removeLibrary(URI aUri, IDataTableLibrary aExpected)
    {
        InstanceOwners[] unjoined = new InstanceOwners[1];
        libraryOwners.compute(aUri, (_, indexed) ->
        {
            LibraryCacheEntry entry = getLibraryEntry(aUri);
            if (entry == null || entry.getLibrary() != aExpected)
            {
                return indexed;
            }
            libraryCache.remove(aUri);
            if (indexed == null || !indexed.isOf(aExpected))
            {
                return indexed;
            }
            dropLockOf(indexed);
            if (indexed.retireIfUnjoined())
            {
                unjoined[0] = indexed;
            }
            return null;
        });
        cleanCache();
        closeUnjoined(unjoined[0]);
    }

    // ------------------------------------------------------------------
    // Library reference counting (Q44, PACK G 17)
    // ------------------------------------------------------------------


    /**
     * Register one acquisition of {@code aLibrary} under {@code aUri} - only while that very
     * instance is the one cached there and its owner set has not been retired. The add happens
     * inside the same compute as the check, so a concurrent last release can neither retire the set
     * between them nor lose the new token.
     *
     * @param aUri
     *            the key the library is cached under.
     * @param aLibrary
     *            the instance the caller looked up or stored.
     * @return the token identifying this acquisition, or {@code null} when the key no longer holds
     *         {@code aLibrary} (superseded or evicted since the caller found it): the caller looks
     *         up again.
     * @throws IllegalStateException
     *             if the key still indexes {@code aLibrary} but its owner set is retired - I8
     *             breached; a {@code null} here would make the caller spin on a key that never
     *             changes (PACK G fold, G17 review).
     */
    LocalDataTableLibraryRef.@Nullable Ownership acquireIfCurrent(URI aUri,
            IDataTableLibrary aLibrary)
    {
        LocalDataTableLibraryRef.Ownership[] minted = new LocalDataTableLibraryRef.Ownership[1];
        libraryOwners.computeIfPresent(aUri, (_, indexed) ->
        {
            if (indexed.isOf(aLibrary))
            {
                if (indexed.retired)
                {
                    throw new IllegalStateException("library instance under " + aUri
                            + " is still indexed but its owner set is retired (I8)");
                }
                LocalDataTableLibraryRef.Ownership token = new LocalDataTableLibraryRef.Ownership(
                        aUri, indexed);
                indexed.owners.add(token);
                minted[0] = token;
            }
            return indexed;
        });
        return minted[0];
    }


    /**
     * Give back one acquisition: remove the token from its OWN instance's owner set. At zero the
     * set is retired (never joined again), and if the index still holds it the cache entry is
     * evicted and the set detached - in the same compute, so no open can join the instance in
     * between. No unlock and no close here: the caller does both after this returns, in that order.
     *
     * @param aOwnership
     *            a token not released before
     *            ({@link LocalDataTableLibraryRef.Ownership#markReleased()} guards that).
     * @return how many holders the instance still has, and whether this release evicted it.
     * @throws IllegalArgumentException
     *             if the token was minted by another cache (another manager's): its set belongs to
     *             that cache's index, and counting it down here would evict nothing of ours and
     *             close a library of theirs. The manager refuses such a token before it comes here.
     * @throws IllegalStateException
     *             if the token is not in its own set - unreachable while every token is released at
     *             most once, so a breach is a bug.
     */
    Released release(LocalDataTableLibraryRef.Ownership aOwnership)
    {
        InstanceOwners mine = aOwnership.getOwners();
        if (!owns(aOwnership))
        {
            throw new IllegalArgumentException("library ref token was minted by another manager");
        }
        Released[] result = new Released[1];
        libraryOwners.compute(aOwnership.getUri(), (k, indexed) ->
        {
            if (!mine.owners.remove(aOwnership))
            {
                throw new IllegalStateException(
                        "library ref token is not in its own owner set (released twice?)");
            }
            int remaining = mine.owners.size();
            if (remaining > 0)
            {
                result[0] = new Released(remaining, false);
                return indexed;
            }
            mine.retired = true;
            if (indexed != mine)
            {
                result[0] = new Released(0, false);
                return indexed;
            }
            libraryCache.remove(k);
            result[0] = new Released(0, true);
            return null;
        });
        return result[0];
    }

    /**
     * What a {@link #release} did.
     *
     * @param remaining
     *            holders the instance still has.
     * @param evicted
     *            whether the release took the instance out of the cache (it was still the cached
     *            one); {@code false} at zero means it had been superseded.
     */
    record Released(int remaining, boolean evicted)
    {
    }

    /**
     * The number of outstanding acquisitions of the instance currently indexed under a key, read
     * inside the key's compute (the set is not thread-safe outside it). Diagnostics and tests only.
     */
    int countOwners(URI aUri)
    {
        int[] count = new int[1];
        libraryOwners.computeIfPresent(aUri, (_, indexed) ->
        {
            count[0] = indexed.owners.size();
            return indexed;
        });
        return count[0];
    }


    /** Whether this cache minted the token (its owner set is one of this cache's; PACK G fold). */
    boolean owns(LocalDataTableLibraryRef.Ownership aOwnership)
    {
        return aOwnership.getOwners().cache == this;
    }


    /**
     * The instance the index currently holds under a key, or {@code null} - for the tests of
     * invariant I7 (at quiescence it is the cached instance). Reads the weak reference, so an
     * instance only the cache still referenced may already be gone.
     */
    @Nullable
    IDataTableLibrary indexedInstance(URI aUri)
    {
        InstanceOwners indexed = libraryOwners.get(aUri);
        return indexed == null ? null : indexed.instance.get();
    }


    /** Drop the memory lock of the set's instance, if the lock is that instance's (in-compute). */
    private void dropLockOf(InstanceOwners aOwners)
    {
        URI key = aOwners.lockKey;
        // Identity only: the instance belongs to its holders (or is closed by closeUnjoined).
        @SuppressWarnings("PMD.CloseResource")
        IDataTableLibrary instance = aOwners.instance.get();
        if (key != null && instance != null)
        {
            memoryLocks.computeIfPresent(key,
                    (_, lock) -> lock.library() == instance ? null : lock);
        }
    }


    /** Close an instance that left the cache without ever being joined (outside any compute). */
    private static void closeUnjoined(@Nullable InstanceOwners aOwners)
    {
        // Closed right below; a failing close is logged, never thrown into the load.
        @SuppressWarnings("PMD.CloseResource")
        IDataTableLibrary lib = aOwners != null ? aOwners.instance.get() : null;
        if (lib == null)
        {
            return;
        }
        try
        {
            lib.close();
        }
        catch (IOException | RuntimeException ex)
        {
            LOGGER.log(Level.WARNING, "Closing the unused library \"" + lib.getName()
                    + "\" as it left the cache failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * The live acquisitions of ONE loaded library instance - not of a URI (PACK G 17). The token of
     * each acquisition points at its own set, so a release always counts down the instance it
     * acquired, whatever the index holds by then. Mutated only inside {@code libraryOwners.compute}
     * of the key it was created under.
     */
    static final class InstanceOwners
    {

        /** The cache that minted the set: a token of another manager is refused, not counted. */
        private final LocalCacheSupport cache;

        /** Identity only: the cache holds libraries weakly, the index must not pin them. */
        private final WeakReference<IDataTableLibrary> instance;

        /** {@code library.getUri()}, the memory-lock key, read before the store compute. */
        private final @Nullable URI lockKey;

        /** Identity set: a token matches only the acquisition it came from. */
        private final Set<LocalDataTableLibraryRef.Ownership> owners = Collections
                .newSetFromMap(new IdentityHashMap<>());

        /** Reached zero (or left the cache unjoined): closed, never joined again. */
        private boolean retired;

        InstanceOwners(LocalCacheSupport aCache, IDataTableLibrary aInstance,
                @Nullable URI aLockKey)
        {
            cache = aCache;
            instance = new WeakReference<>(aInstance);
            lockKey = aLockKey;
        }


        boolean isOf(IDataTableLibrary aLibrary)
        {
            return instance.get() == aLibrary;
        }


        /** Retire a set nobody joined; {@code true} if this call retired it. */
        boolean retireIfUnjoined()
        {
            if (retired || !owners.isEmpty())
            {
                return false;
            }
            retired = true;
            return true;
        }
    }

    // ------------------------------------------------------------------
    // Memory lock
    // ------------------------------------------------------------------

    /**
     * Lock a library and its eagerly-loaded tables in memory by holding strong references.
     */
    void lockLibrary(@Nullable IDataTableLibrary aLibrary, List<IDataTable> aTables)
    {
        if (aLibrary == null || aLibrary.getUri() == null)
        {
            return;
        }
        memoryLocks.put(aLibrary.getUri(), new MemoryLock(aLibrary, List.copyOf(aTables)));
    }


    /**
     * Remove the memory lock of this very library. No-op if it is not locked - including when
     * another instance under the same URI holds the lock (a superseded holder must not unlock its
     * successor).
     */
    void unlockLibrary(@Nullable IDataTableLibrary aLibrary)
    {
        if (aLibrary == null)
        {
            return;
        }
        URI key = aLibrary.getUri();
        if (key == null)
        {
            return;
        }
        memoryLocks.computeIfPresent(key, (_, lock) -> lock.library() == aLibrary ? null : lock);
    }


    /**
     * Check whether THIS library instance is currently memory-locked - not merely whether its URI
     * holds a lock: a superseded instance is not locked by its successor's lock (PACK G fold, G17
     * review).
     */
    boolean isLocked(@Nullable IDataTableLibrary aLibrary)
    {
        if (aLibrary == null || aLibrary.getUri() == null)
        {
            return false;
        }
        MemoryLock lock = memoryLocks.get(aLibrary.getUri());
        return lock != null && lock.library() == aLibrary;
    }

    private record MemoryLock(IDataTableLibrary library, List<IDataTable> tables)
    {
    }

    /**
     * Remove a data table entry from the cache.
     */
    void removeTable(URI aUri)
    {
        dataTableCache.remove(aUri);
        cleanCache();
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------


    private @Nullable DataTableCacheEntry getTableEntry(@Nullable URI aUri)
    {
        if (aUri == null)
        {
            return null;
        }
        DataTableCacheEntryWeak w = dataTableCache.get(aUri);
        return w != null ? w.getStrong() : null;
    }


    private @Nullable LibraryCacheEntry getLibraryEntry(@Nullable URI aUri)
    {
        if (aUri == null)
        {
            return null;
        }
        LibraryCacheEntryWeak w = libraryCache.get(aUri);
        return w != null ? w.getStrong() : null;
    }


    /**
     * The cache entry a library's READS go through: the entry under its URI - its own, or, for the
     * holder of a superseded instance, its successor's (PACK G fold, G17 review, option (i)). The
     * T11 lookup half that answered {@code null} for a superseded holder cut it off from every
     * cache-entry state at once - define metadata, report badges, the CORE attach - while the
     * pre-G17 behaviour read whatever the URI held; reads fall back to that. The WRITES
     * ({@code updateMetadata}, {@code updateReport}, {@code storeReportTable}, the member-findings
     * cache put) keep the identity check inside their own {@code computeIfPresent}: nothing a
     * superseded holder attaches lands on the successor.
     */
    private @Nullable LibraryCacheEntry entryUnder(@Nullable IDataTableLibrary aLibrary)
    {
        return aLibrary == null ? null : getLibraryEntry(aLibrary.getUri());
    }


    /**
     * Stream all currently-cached {@link IDataTableLibrary} instances whose weak references are
     * still live. Used by the manager to enumerate open libraries (e.g. for the CORE check's
     * reference-library picker).
     */
    java.util.stream.Stream<IDataTableLibrary> streamLibraries()
    {
        return libraryCache.values().stream()//
                .map(LibraryCacheEntryWeak::getStrong)//
                .filter(java.util.Objects::nonNull)//
                .map(LibraryCacheEntry::getLibrary)//
                .filter(java.util.Objects::nonNull);
    }


    private void cleanCache()
    {
        dataTableCache.entrySet().removeIf(e -> !e.getValue().isValid());
        libraryCache.entrySet().removeIf(e -> !e.getValue().isValid());
    }

    // ------------------------------------------------------------------
    // Cache entry types
    // ------------------------------------------------------------------

    @Getter
    @Builder
    private static class DataTableCacheEntry
    {

        private final URI uri;

        private final IDataTable table;

        private final @Nullable IDataTableLibrary library;

        private final @Nullable ILibraryMember member;

        private final long tableLastModified;

        DataTableCacheEntryWeak getWeak()
        {
            return DataTableCacheEntryWeak.builder()//
                    .uri(uri)//
                    .tableRef(new WeakReference<>(table))//
                    .library(library)//
                    .member(member)//
                    .tableLastModified(tableLastModified)//
                    .build();
        }
    }


    @Getter
    @Builder
    private static class DataTableCacheEntryWeak
    {

        private final URI uri;

        private final WeakReference<IDataTable> tableRef;

        private final @Nullable IDataTableLibrary library;

        private final @Nullable ILibraryMember member;

        private final long tableLastModified;

        boolean isValid()
        {
            return tableRef != null && tableRef.get() != null;
        }


        @Nullable
        DataTableCacheEntry getStrong()
        {
            IDataTable tbl = tableRef.get();
            if (tbl == null)
            {
                return null;
            }
            return DataTableCacheEntry.builder()//
                    .uri(uri)//
                    .table(tbl)//
                    .library(library)//
                    .member(member)//
                    .tableLastModified(tableLastModified)//
                    .build();
        }
    }


    @Getter
    @Builder(toBuilder = true)
    private static class LibraryCacheEntry
    {

        private final URI uri;

        private final IDataTableLibrary library;

        @lombok.Setter
        private @Nullable IMetadataLibrary metadata;

        private final long libraryLastModified;

        LibraryCacheEntryWeak getWeak()
        {
            return LibraryCacheEntryWeak.builder()//
                    .uri(uri)//
                    .libraryRef(new WeakReference<>(library))//
                    .metadata(metadata)//
                    .libraryLastModified(libraryLastModified)//
                    .build();
        }
    }


    @Getter
    @Builder
    private static class LibraryCacheEntryWeak
    {

        private final URI uri;

        private final WeakReference<IDataTableLibrary> libraryRef;

        private final @Nullable IMetadataLibrary metadata;

        private final long libraryLastModified;

        boolean isValid()
        {
            return libraryRef != null && libraryRef.get() != null;
        }


        @Nullable
        LibraryCacheEntry getStrong()
        {
            IDataTableLibrary lib = libraryRef.get();
            if (lib == null)
            {
                return null;
            }
            return LibraryCacheEntry.builder()//
                    .uri(uri)//
                    .library(lib)//
                    .metadata(metadata)//
                    .libraryLastModified(libraryLastModified)//
                    .build();
        }
    }
}
