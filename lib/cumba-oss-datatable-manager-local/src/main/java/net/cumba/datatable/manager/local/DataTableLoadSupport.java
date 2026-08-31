package net.cumba.datatable.manager.local;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.List;
import java.util.stream.Stream;

import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.library.LibraryProviderFactory;
import net.cumba.datatable.impl.provider.DataTableProviderFactory;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.library.IDataTableLibrary;
import net.cumba.datatable.library.ILibraryMember;
import net.cumba.datatable.metadata.IMetadataLibrary;
import org.jspecify.annotations.Nullable;

/**
 * Loads {@link IDataTable} instances from URIs and library members; caches the loaded tables via
 * {@link LocalCacheSupport}.
 */
class DataTableLoadSupport
{

    private static final String CAN_NOT_OPEN_AS_DATA_TABLE = "Can not open %s as data table!";

    private final LocalCacheSupport cache;

    DataTableLoadSupport(LocalCacheSupport aCache)
    {
        cache = aCache;
    }

    // ------------------------------------------------------------------
    // Discovery
    // ------------------------------------------------------------------


    List<FileInfo> getSupportedDataTableInfos()
    {
        return DataTableProviderFactory.getFactory().getFileInfos();
    }


    boolean isSupportedAsTable(URI aUri, @Nullable FileInfo aFileInfo)
    {
        return DataTableProviderFactory.getFactory()//
                .getFirstProviderFor(aUri, aFileInfo).isPresent();
    }

    // ------------------------------------------------------------------
    // Metadata
    // ------------------------------------------------------------------


    DataTableMeta provideMetaData(URI aUri, @Nullable FileInfo aFileInfo) throws IOException
    {
        DataTableMeta meta = DataTableProviderFactory.getFactory().provideMetaData(aUri, aFileInfo,
                null);
        if (meta == null)
        {
            throw new IOException(CAN_NOT_OPEN_AS_DATA_TABLE.formatted(aUri));
        }
        return meta;
    }


    DataTableMeta provideMetaData(ILibraryMember aMember, @Nullable FileInfo aFileInfo)
        throws IOException
    {
        FileInfo fi = (aFileInfo != null) ? aFileInfo : aMember.getFileInfo();
        @Nullable
        IMetadataLibrary mdl = cache.lookupMetadata(aMember);
        DataTableMeta meta = DataTableProviderFactory.getFactory().provideMetaData(aMember, fi,
                mdl);
        if (meta == null)
        {
            throw new IOException(CAN_NOT_OPEN_AS_DATA_TABLE.formatted(aMember.getUri()));
        }
        return meta;
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------


    IDataTable getDataTable(URI aUri, @Nullable FileInfo aFileInfo) throws IOException
    {
        long lmodt = cache.getLastModified(aUri);

        IDataTable cached = cache.lookupTable(aUri, lmodt);
        if (cached != null)
        {
            return cached;
        }

        IDataTable tbl = DataTableProviderFactory.getFactory().provide(aUri, aFileInfo,
                (IMetadataLibrary) null);
        if (tbl == null)
        {
            throw new IOException(CAN_NOT_OPEN_AS_DATA_TABLE.formatted(aUri));
        }

        cache.storeTable(aUri, tbl, null, null, lmodt);
        return tbl;
    }


    IDataTable getDataTable(ILibraryMember aMember, @Nullable FileInfo aFileInfo) throws IOException
    {
        URI uri = aMember.getUri();
        long lmodt = cache.getLastModified(uri);

        IDataTable cached = cache.lookupTable(uri, lmodt);
        if (cached != null)
        {
            return cached;
        }

        @Nullable
        IMetadataLibrary mdl = cache.lookupMetadata(aMember);
        FileInfo fi = (aFileInfo != null) ? aFileInfo : aMember.getFileInfo();

        IDataTable tbl = DataTableProviderFactory.getFactory().provide(aMember, fi, mdl);
        if (tbl == null)
        {
            throw new IOException(CAN_NOT_OPEN_AS_DATA_TABLE.formatted(uri));
        }

        cache.storeTable(uri, tbl, aMember.getLibrary(), aMember, lmodt);
        return tbl;
    }

    // ------------------------------------------------------------------
    // Column enumeration (uses the data table cache if available)
    // ------------------------------------------------------------------


    Stream<DataTableColumnMeta> getColumns(ILibraryMember aMember) throws IOException
    {
        IDataTable cached = cache.findCachedTable(aMember.getUri());
        if (cached != null)
        {
            return cached.getMetaData().getAllColumns();
        }

        IDataTableLibrary lib = aMember.getLibrary();
        URI uri = lib.getUri();

        // Ask every library provider first — a provider that can enumerate columns cheaply (e.g.
        // without reading the data file body) wins over the heavier provideMetaData fallback.
        // Materialise the stream so we can detect "no provider contributed anything" before
        // committing to the fallback path.
        List<DataTableColumnMeta> fromLibraryProviders = LibraryProviderFactory.getInstance()
                .getAllProvidersFor(uri, lib.getFileInfo()).stream()
                .<DataTableColumnMeta> flatMap(p ->
                {
                    try
                    {
                        return p.provideLibraryMemberColumns(aMember)
                                .map(c -> (DataTableColumnMeta) c);
                    }
                    catch (IOException ex)
                    {
                        throw new UncheckedIOException(ex);
                    }
                }).toList();
        if (!fromLibraryProviders.isEmpty())
        {
            return fromLibraryProviders.stream();
        }

        // Fallback: use the data-table provider's provideMetaData to read just the columns
        // without materialising the full table. This covers members whose library provider does
        // not enumerate columns (e.g. folder libraries whose member format is discovered only by
        // the data-table factory). Any IOException here is a real read failure and propagates
        // to the caller.
        DataTableMeta meta = provideMetaData(aMember, null);
        return meta.getAllColumns();
    }
}
