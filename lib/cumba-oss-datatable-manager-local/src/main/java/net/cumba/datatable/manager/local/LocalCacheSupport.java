package net.cumba.datatable.manager.local;

import java.io.File;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.lang.ref.WeakReference;
import java.net.URI;
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
     * Live acquisitions per cached library URI (Q44). One entry per {@code getLibraryRef(URI, ...)}
     * call that has not been released yet; the library is closed and evicted only when the last one
     * goes away. The sets are identity sets ({@link LocalDataTableLibraryRef.Ownership} does not
     * override {@code equals}), so a release matches exactly the acquisition it came from.
     */
    private final Map<URI, Set<LocalDataTableLibraryRef.Ownership>> libraryOwners = //
            new ConcurrentHashMap<>();

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
        removeLibrary(aUri);
        return null;
    }


    /**
     * Store a freshly-loaded library in the cache. {@code aMetadata} may be {@code null}.
     */
    void storeLibrary(URI aUri, IDataTableLibrary aLibrary, @Nullable IMetadataLibrary aMetadata,
            long aLastModified)
    {
        cleanCache();
        // A brand new instance takes over this key, so any acquisition still registered here
        // belongs to a library that is no longer the cached one. Dropping them is what keeps the
        // count honest when a weakly-cached entry was collected without ever being released —
        // otherwise the stale acquisitions would be added to the new instance's count and it
        // could never reach zero again (i.e. never be closed).
        libraryOwners.remove(aUri);
        LibraryCacheEntry entry = LibraryCacheEntry.builder()//
                .uri(aUri)//
                .library(aLibrary)//
                .metadata(aMetadata)//
                .libraryLastModified(aLastModified)//
                .build();
        libraryCache.put(aUri, entry.getWeak());
    }


    /**
     * Retrieve the cached metadata for the given library, or {@code null} if none is attached.
     */
    @Nullable
    IMetadataLibrary lookupMetadata(@Nullable IDataTableLibrary aLibrary)
    {
        if (aLibrary == null)
        {
            return null;
        }
        LibraryCacheEntry entry = getLibraryEntry(aLibrary.getUri());
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
     * Attach (or replace) metadata on an already-cached library. No-op if the library is not
     * cached.
     */
    void updateMetadata(@Nullable IDataTableLibrary aLibrary, @Nullable IMetadataLibrary aMetadata)
    {
        if (aLibrary == null)
        {
            return;
        }
        LibraryCacheEntry entry = getLibraryEntry(aLibrary.getUri());
        if (entry != null)
        {
            entry.setMetadata(aMetadata);
            libraryCache.put(entry.getUri(), entry.getWeak());
        }
    }


    /**
     * Remove a library entry from the cache.
     */
    void removeLibrary(URI aUri)
    {
        libraryCache.remove(aUri);
        memoryLocks.remove(aUri);
        libraryOwners.remove(aUri);
        cleanCache();
    }


    /**
     * Remove the library entry for {@code aUri}, but only if {@code aLibrary} is still the instance
     * cached under that key. A library that was superseded (reloaded after its file changed, or
     * re-opened after its weak entry was collected) must not evict its successor when its last
     * owner finally releases it.
     *
     * @param aUri
     *            the key the library was acquired under.
     * @param aLibrary
     *            the instance being released.
     * @return {@code true} if the entry was removed, {@code false} if a different instance now
     *         holds the key.
     */
    boolean removeLibraryIfCurrent(URI aUri, IDataTableLibrary aLibrary)
    {
        if (!isCurrentLibrary(aUri, aLibrary))
        {
            return false;
        }
        removeLibrary(aUri);
        return true;
    }


    /**
     * Whether {@code aLibrary} is still the instance cached under {@code aUri}. A key with no live
     * entry answers {@code false} — nothing is current there any more.
     */
    boolean isCurrentLibrary(URI aUri, IDataTableLibrary aLibrary)
    {
        LibraryCacheEntry entry = getLibraryEntry(aUri);
        return entry != null && entry.getLibrary() == aLibrary;
    }

    // ------------------------------------------------------------------
    // Library reference counting (Q44)
    // ------------------------------------------------------------------


    /**
     * Register one acquisition of the library cached under {@code aUri} and return its token. The
     * token is what {@link #releaseOwnership(LocalDataTableLibraryRef.Ownership)} matches against,
     * so it must be handed to the ref the caller receives.
     *
     * @param aUri
     *            the key the library is cached under.
     * @return the token identifying this acquisition; never {@code null}.
     */
    LocalDataTableLibraryRef.Ownership acquireLibrary(URI aUri)
    {
        LocalDataTableLibraryRef.Ownership ownership = new LocalDataTableLibraryRef.Ownership(aUri);
        libraryOwners.computeIfAbsent(aUri, _ -> ConcurrentHashMap.newKeySet()).add(ownership);
        return ownership;
    }


    /**
     * Give back one acquisition.
     *
     * @param aOwnership
     *            the token handed out by {@link #acquireLibrary(URI)}.
     * @return the number of acquisitions still outstanding for that key, or {@code -1} when this
     *         token was not registered (any more) — a double release, or a token whose library has
     *         since been superseded in the cache. {@code -1} is deliberately distinguishable from
     *         {@code 0}: the caller must report it rather than treat it as the last release.
     */
    int releaseOwnership(LocalDataTableLibraryRef.Ownership aOwnership)
    {
        int[] remaining =
        {
                -1
        };
        libraryOwners.computeIfPresent(aOwnership.getUri(), (_, owners) ->
        {
            if (!owners.remove(aOwnership))
            {
                return owners;
            }
            remaining[0] = owners.size();
            return owners.isEmpty() ? null : owners;
        });
        return remaining[0];
    }


    /**
     * The number of outstanding acquisitions for a key. Diagnostics and tests only.
     */
    int countOwners(URI aUri)
    {
        Set<LocalDataTableLibraryRef.Ownership> owners = libraryOwners.get(aUri);
        return owners != null ? owners.size() : 0;
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
     * Remove the memory lock for a library. No-op if the library is not locked.
     */
    void unlockLibrary(@Nullable IDataTableLibrary aLibrary)
    {
        if (aLibrary == null || aLibrary.getUri() == null)
        {
            return;
        }
        memoryLocks.remove(aLibrary.getUri());
    }


    /**
     * Check whether a library is currently memory-locked.
     */
    boolean isLocked(@Nullable IDataTableLibrary aLibrary)
    {
        if (aLibrary == null || aLibrary.getUri() == null)
        {
            return false;
        }
        return memoryLocks.containsKey(aLibrary.getUri());
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
