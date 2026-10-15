package net.cumba.datatable.manager.local;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.util.List;
import java.util.stream.Stream;

import lombok.CustomLog;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.library.IDataTableLibrary;
import net.cumba.datatable.library.ILibraryMember;
import net.cumba.datatable.manager.IDataTableLibraryRef;
import net.cumba.datatable.manager.IDataTableManager;
import net.cumba.datatable.manager.IDataTableRef;
import net.cumba.datatable.manager.ILibraryMemberRef;
import net.cumba.datatable.metadata.IMetadataLibrary;
import org.jspecify.annotations.Nullable;

/**
 * An {@link IDataTableManager} implementation for local data table management. The manager
 * delegates the heavy lifting to a set of focused collaborators:
 * <ul>
 * <li>{@link LocalCacheSupport} — weak-reference caches for data tables and libraries</li>
 * <li>{@link DataTableLoadSupport} — table loading, metadata, provider properties</li>
 * <li>{@link LibraryLoadSupport} — library loading and metadata attachment</li>
 * </ul>
 * The manager itself owns ref unwrapping/wrapping.
 */
@CustomLog
public class LocalDataTableManager implements IDataTableManager
{

    /** The type name of this manager. */
    public static final String TYPE = "LocalDataTableManager";

    /** The version of this manager. */
    public static final String VERSION = "0.1";

    // ------------------------------------------------------------------
    // Collaborators
    // ------------------------------------------------------------------

    private final LocalCacheSupport cacheSupport;

    private final DataTableLoadSupport tableLoadSupport;

    private final LibraryLoadSupport libraryLoadSupport;

    public LocalDataTableManager()
    {
        this.cacheSupport = new LocalCacheSupport();
        this.tableLoadSupport = new DataTableLoadSupport(cacheSupport);
        this.libraryLoadSupport = new LibraryLoadSupport(cacheSupport);
    }


    /**
     * The internal manager's dictionary-formats switch, for source compatibility of the code both
     * builds share (the coreJ CLI and REST construct their manager with it off): this manager
     * resolves no dictionary formats, so the switch changes nothing here.
     *
     * @param aResolveDictionaryFormats
     *            whether dictionary formats would be resolved — ignored, none ever are.
     */
    public LocalDataTableManager(boolean aResolveDictionaryFormats)
    {
        this();
    }


    /**
     * Whether the catalogs this manager serves resolve dictionary formats.
     *
     * @return always {@code false}: this manager resolves none.
     */
    public boolean isResolvingDictionaryFormats()
    {
        return false;
    }

    // ------------------------------------------------------------------
    // Ref unwrapping
    // ------------------------------------------------------------------


    /** Casts the given reference to its local implementation or throws if incompatible. */
    protected LocalDataTableRef asLocalRef(IDataTableRef aRef) throws IOException
    {
        if (aRef instanceof LocalDataTableRef ldr && aRef.getManager() == this)
        {
            return ldr;
        }
        throw new IOException("Given reference is not supported!");
    }


    /** Casts the given reference to its local implementation or throws if incompatible. */
    protected LocalDataTableLibraryRef asLocalRef(IDataTableLibraryRef aRef) throws IOException
    {
        if (aRef instanceof LocalDataTableLibraryRef ldlr && aRef.getManager() == this)
        {
            return ldlr;
        }
        throw new IOException("Given reference is not supported!");
    }


    /** Casts the given reference to its local implementation or throws if incompatible. */
    protected LocalLibraryMemberRef asLocalRef(ILibraryMemberRef aRef) throws IOException
    {
        if (aRef instanceof LocalLibraryMemberRef llmr && aRef.getManager() == this)
        {
            return llmr;
        }
        throw new IOException("Given reference is not supported!");
    }

    // ------------------------------------------------------------------
    // Discovery
    // ------------------------------------------------------------------


    @Override
    public List<FileInfo> getSupportedDataTableInfos()
    {
        return tableLoadSupport.getSupportedDataTableInfos();
    }


    @Override
    public List<FileInfo> getSupportedDataLibraryInfos()
    {
        return libraryLoadSupport.getSupportedDataLibraryInfos();
    }


    @Override
    public boolean isSupportedAsTable(URI aUri, @Nullable FileInfo aFileInfo)
    {
        return tableLoadSupport.isSupportedAsTable(aUri, aFileInfo);
    }


    @Override
    public boolean isSupportedAsLibrary(URI aUri, @Nullable FileInfo aFileInfo)
    {
        return libraryLoadSupport.isSupportedAsLibrary(aUri, aFileInfo);
    }

    // ------------------------------------------------------------------
    // Metadata
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // Table loading — refs + direct access
    // ------------------------------------------------------------------


    @Override
    public IDataTableRef getDataTableRef(URI aUri, @Nullable FileInfo aFileInfo) throws IOException
    {
        IDataTable tbl = tableLoadSupport.getDataTable(aUri, aFileInfo);
        return new LocalDataTableRef(this, tbl);
    }


    @Override
    public IDataTableRef getDataTableRef(ILibraryMemberRef aMember, @Nullable FileInfo aFileInfo)
        throws IOException
    {
        LocalLibraryMemberRef ref = asLocalRef(aMember);
        IDataTable tbl = tableLoadSupport.getDataTable(ref.getMember(), aFileInfo);
        return new LocalDataTableRef(this, tbl);
    }


    @Override
    public IDataTable getDataTable(IDataTableRef aRef) throws IOException
    {
        return asLocalRef(aRef).getTable();
    }

    // ------------------------------------------------------------------
    // Package-level convenience accessors used by internal tests
    // ------------------------------------------------------------------


    /**
     * Load a data table from the given URI. Kept as a package/convenience entry point for unit
     * tests and code inside this module that does not deal with refs.
     */
    IDataTable getDataTable(URI aUri, FileInfo aFileInfo) throws IOException
    {
        return tableLoadSupport.getDataTable(aUri, aFileInfo);
    }


    /**
     * Load a data table for the given library member. Package/convenience entry point for
     * intra-module callers.
     */
    IDataTable getDataTable(ILibraryMember aMember, FileInfo aFileInfo) throws IOException
    {
        return tableLoadSupport.getDataTable(aMember, aFileInfo);
    }

    // ------------------------------------------------------------------
    // Records
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // Editing
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // Library loading
    // ------------------------------------------------------------------


    /**
     * The one and only door that loads and caches a library, and therefore the only one that takes
     * a reference count (Q44). The count is taken on the very instance this call answers, under the
     * URI the cache is keyed by, and is handed to the returned ref as its
     * {@link LocalDataTableLibraryRef.Ownership} token. Two callers asking for the same URI get the
     * same cached library instance and two independent counts, so neither can close it under the
     * other.
     * <p>
     * The lookup (or load) and the count are two steps; the count is taken only if the instance is
     * still the one cached under the URI and not retired. A reload, an eviction or a last release
     * that landed in between makes the join fail, and the door looks up again - so it never hands
     * out a closed or superseded instance, and never counts on another instance (PACK G 17). The
     * retry is unbounded: while the library's files keep changing under concurrent openers each
     * pass may see a fresh reload, but it terminates once the concurrent writes on the URI stop.
     */
    @Override
    public IDataTableLibraryRef getLibraryRef(URI aUri, @Nullable FileInfo aFileInfo)
        throws IOException
    {
        IDataTableLibrary lib;
        LocalDataTableLibraryRef.Ownership token;
        do
        {
            lib = libraryLoadSupport.getLibrary(aUri, aFileInfo);
            token = cacheSupport.acquireIfCurrent(aUri, lib);
        }
        while (token == null);
        return LocalDataTableLibraryRef.builder()//
                .manager(this)//
                .library(lib)//
                .ownership(token)//
                .build();
    }


    /**
     * Give back one acquisition of a library (Q44).
     * <p>
     * The count is kept per loaded instance (PACK G 17): the instance is closed when the LAST
     * owning ref on it is released - unlocked and evicted too if it is still the cached one; an
     * instance a reload superseded closes at its own last release and leaves its successor, its
     * successor's count and its successor's memory lock alone. An earlier release only drops that
     * holder's count. This is what makes it safe for two holders to share one library: a holder
     * releasing its own ref (typically in a {@code finally}) used to close the shared instance and
     * evict it underneath the other holder's still-live ref.
     * <p>
     * A release that cannot be matched to an acquisition is REPORTED, never silently swallowed and
     * never obeyed:
     * <ul>
     * <li>a borrowed ref (any ref not handed out by
     * {@link #getLibraryRef(URI, net.cumba.datatable.io.FileInfo)}, e.g. one built directly through
     * {@code LocalDataTableLibraryRef.builder()}) has no acquisition to give back, so closing on
     * its behalf would be exactly the use-after-close this change removes;</li>
     * <li>an owning ref released again - at any time, also after its library was closed and evicted
     * - would otherwise consume a count, close an instance or unlock a library that belongs to
     * another holder;</li>
     * <li>a ref whose token another manager minted, or a ref built with a copied token whose
     * instance is not this ref's library (PACK G fold, G17 review) - the count it would consume
     * belongs to another manager or another instance.</li>
     * </ul>
     */
    @Override
    public void releaseLibraryRef(IDataTableLibraryRef aLibrary) throws IOException
    {
        LocalDataTableLibraryRef ref = asLocalRef(aLibrary);
        LocalDataTableLibraryRef.Ownership ownership = ref.getOwnership();
        if (ownership == null)
        {
            LOGGER.log(Level.WARNING,
                    ("Ignoring releaseLibraryRef for library \"%s\": the ref is a borrowed view "
                            + "(a ref not obtained from getLibraryRef(URI, ...)) and holds no "
                            + "reference count. Only the ref returned by getLibraryRef(URI, ...) "
                            + "may be released.").formatted(ref.getName()),
                    new IllegalStateException("release of a borrowed library ref"));
            return;
        }
        if (!cacheSupport.owns(ownership))
        {
            LOGGER.log(Level.WARNING,
                    ("Ignoring releaseLibraryRef for library \"%s\": the ref's token was minted "
                            + "by another manager. Nothing is closed, evicted or unlocked here; "
                            + "release it on the manager that opened it.").formatted(ref.getName()),
                    new IllegalStateException("release of another manager's library ref"));
            return;
        }
        if (!ownership.getOwners().isOf(ref.getLibrary()))
        {
            LOGGER.log(Level.WARNING,
                    ("Ignoring releaseLibraryRef for library \"%s\": the ref carries a token "
                            + "of another library instance (a copied token). Nothing is closed, "
                            + "evicted or unlocked.").formatted(ref.getName()),
                    new IllegalStateException("release of a copied library ref token"));
            return;
        }
        if (!ownership.markReleased())
        {
            LOGGER.log(Level.WARNING,
                    ("Ignoring releaseLibraryRef for library \"%s\": this ref was already "
                            + "released. Nothing is closed, evicted or unlocked.")
                                    .formatted(ref.getName()),
                    new IllegalStateException("repeated release of a library ref"));
            return;
        }

        LocalCacheSupport.Released released = cacheSupport.release(ownership);
        if (released.remaining() > 0)
        {
            LOGGER.log(Level.DEBUG,
                    "Library \"{0}\" released by one holder, {1} still open — not closing.",
                    ref.getName(), released.remaining());
            return;
        }
        if (!released.evicted())
        {
            LOGGER.log(Level.DEBUG,
                    "Closing superseded instance of library \"{0}\" — it is no longer cached.",
                    ref.getName());
        }
        // outside the count's compute, in this order: the identity unlock, then the close
        cacheSupport.unlockLibrary(ref.getLibrary());
        ref.getLibrary().close();
    }


    @Override
    public Stream<ILibraryMemberRef> getLibraryMembers(IDataTableLibraryRef aLibrary)
        throws IOException
    {
        LocalDataTableLibraryRef ref = asLocalRef(aLibrary);
        return libraryLoadSupport.getLibraryMembers(ref.getLibrary())
                .map(m -> new LocalLibraryMemberRef(this, m));
    }

    // ------------------------------------------------------------------
    // Metadata attachment / retrieval
    // ------------------------------------------------------------------


    @Override
    public IMetadataLibrary getMetadataLibrary(IDataTableLibraryRef aLibrary) throws IOException
    {
        LocalDataTableLibraryRef ref = asLocalRef(aLibrary);
        return libraryLoadSupport.getMetadataLibrary(ref.getLibrary());
    }

}
