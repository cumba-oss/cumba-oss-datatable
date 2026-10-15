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
     * a reference count (Q44). The count is taken AFTER the load, on the same URI the cache is
     * keyed by, and is handed to the returned ref as its {@link LocalDataTableLibraryRef.Ownership}
     * token. Two callers asking for the same URI get the same cached library instance and two
     * independent counts, so neither can close it under the other.
     */
    @Override
    public IDataTableLibraryRef getLibraryRef(URI aUri, @Nullable FileInfo aFileInfo)
        throws IOException
    {
        IDataTableLibrary lib = libraryLoadSupport.getLibrary(aUri, aFileInfo);
        return LocalDataTableLibraryRef.builder()//
                .manager(this)//
                .library(lib)//
                .ownership(cacheSupport.acquireLibrary(aUri))//
                .build();
    }


    /**
     * Give back one acquisition of a library (Q44).
     * <p>
     * The library is unlocked, evicted and closed when the LAST owning ref is released; an earlier
     * release only drops that holder's count. This is what makes it safe for two holders to share
     * one library: a holder releasing its own ref (typically in a {@code finally}) used to close
     * the shared instance and evict it underneath the other holder's still-live ref.
     * <p>
     * A release that cannot be matched to an acquisition is REPORTED, never silently swallowed and
     * never obeyed:
     * <ul>
     * <li>a borrowed ref (any ref not handed out by
     * {@link #getLibraryRef(URI, net.cumba.datatable.io.FileInfo)}, e.g. one built directly through
     * {@code LocalDataTableLibraryRef.builder()}) has no acquisition to give back, so closing on
     * its behalf would be exactly the use-after-close this change removes;</li>
     * <li>an owning ref released twice would otherwise consume a count belonging to another
     * holder.</li>
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

        int remaining = cacheSupport.releaseOwnership(ownership);
        if (remaining > 0)
        {
            LOGGER.log(Level.DEBUG,
                    "Library \"{0}\" released by one holder, {1} still open — not closing.",
                    ref.getName(), remaining);
            return;
        }
        if (remaining < 0)
        {
            if (cacheSupport.isCurrentLibrary(ownership.getUri(), ref.getLibrary()))
            {
                // Double release. The library is still the cached instance, so other holders may
                // legitimately own it — closing it here is precisely the bug being fixed.
                LOGGER.log(Level.WARNING,
                        ("Ignoring releaseLibraryRef for library \"%s\": this ref was already "
                                + "released. The library stays open for its remaining holders.")
                                        .formatted(ref.getName()),
                        new IllegalStateException("double release of a library ref"));
                return;
            }
            // The cache moved on (the library was reloaded after its file changed, or its weak
            // entry was collected and the URI re-opened). Nothing else can own THIS instance, so
            // close it — releaseLibrary will not touch the successor's cache entry.
            LOGGER.log(Level.DEBUG,
                    "Closing superseded instance of library \"{0}\" — it is no longer cached.",
                    ref.getName());
        }

        cacheSupport.unlockLibrary(ref.getLibrary());
        libraryLoadSupport.releaseLibrary(ref.getLibrary(), ownership.getUri());
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
