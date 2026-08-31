package net.cumba.datatable.manager.local;

import java.io.IOException;
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


    @Override
    public IDataTableLibraryRef getLibraryRef(URI aUri, @Nullable FileInfo aFileInfo)
        throws IOException
    {
        IDataTableLibrary lib = libraryLoadSupport.getLibrary(aUri, aFileInfo);
        return LocalDataTableLibraryRef.builder()//
                .manager(this)//
                .library(lib)//
                .build();
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
