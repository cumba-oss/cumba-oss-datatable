package net.cumba.datatable.manager.local;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import net.cumba.cdisc.define.DefineCache;
import net.cumba.cdisc.define.DefineSupport;
import net.cumba.datatable.help.CDT;
import net.cumba.datatable.impl.library.AbstractDataTableLibrary;
import net.cumba.datatable.impl.library.LibraryProviderFactory;
import net.cumba.datatable.impl.metadata.DataTableLibraryMetadataAdapter;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.library.IDataTableLibrary;
import net.cumba.datatable.library.ILibraryMember;
import net.cumba.datatable.library.ILibraryProvider;
import net.cumba.datatable.metadata.IMetadataLibrary;
import net.cumba.datatable.provider.define.metadata.DefineMetadataLibrary;
import org.jspecify.annotations.Nullable;

/**
 * Loads {@link IDataTableLibrary} instances and handles their associated metadata. Delegates all
 * cache storage to {@link LocalCacheSupport}.
 */
class LibraryLoadSupport
{

    private static final Logger LOGGER = System.getLogger(LibraryLoadSupport.class.getName());

    private final LocalCacheSupport cache;

    LibraryLoadSupport(LocalCacheSupport aCache)
    {
        cache = aCache;
    }

    // ------------------------------------------------------------------
    // Discovery
    // ------------------------------------------------------------------


    List<FileInfo> getSupportedDataLibraryInfos()
    {
        return LibraryProviderFactory.getInstance().getFileInfos();
    }


    boolean isSupportedAsLibrary(URI aUri, @Nullable FileInfo aFileInfo)
    {
        if (!CDT.isBlankOrNull(aUri.getFragment()))
        {
            return false;
        }
        if ("file".equalsIgnoreCase(aUri.getScheme()))
        {
            try
            {
                if (new File(aUri).isDirectory())
                {
                    return true;
                }
            }
            catch (Exception ex)
            {
                LOGGER.log(Level.DEBUG, ex.getMessage(), ex);
            }
        }
        return LibraryProviderFactory.getInstance()//
                .getFirstProviderFor(aUri, aFileInfo).isPresent();
    }

    // ------------------------------------------------------------------
    // URIs
    // ------------------------------------------------------------------


    URI getURI(IDataTableLibrary aLibrary)
    {
        return aLibrary.getUri();
    }


    URI getURI(ILibraryMember aMember)
    {
        return aMember.getUri();
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------


    IDataTableLibrary getLibrary(URI aUri, @Nullable FileInfo aFileInfo) throws IOException
    {
        long lmdt = cache.getLastModified(aUri);

        IDataTableLibrary cached = cache.lookupLibrary(aUri, lmdt);
        if (cached != null)
        {
            return cached;
        }

        LibraryProviderFactory lpf = LibraryProviderFactory.getInstance();
        IDataTableLibrary lib = lpf.provide(aUri, aFileInfo);
        if (lib == null)
        {
            throw new IOException("Can not open %s as library!".formatted(aUri));
        }

        Optional<ILibraryProvider> provider = LibraryProviderFactory.getInstance()
                .getFirstProviderFor(aUri, lib.getFileInfo());

        @Nullable
        IMetadataLibrary mdl = lib.getMetadata();
        try
        {
            if (mdl == null && provider.isPresent())
            {
                Object mdObj = provider.get().getLibraryAttribute(lib,
                        ILibraryProvider.ATTRIBUTE_META_DATA);
                if (mdObj instanceof IMetadataLibrary imdl)
                {
                    mdl = imdl;
                }
                else
                {
                    Object mdUriObj = provider.get().getLibraryAttribute(lib,
                            ILibraryProvider.ATTRIBUTE_META_DATA_URI);
                    if (mdUriObj instanceof String mdUriStr)
                    {
                        mdl = loadMetadata(URI.create(mdUriStr));
                    }
                }
            }
        }
        catch (Exception ex)
        {
            LOGGER.log(Level.WARNING, ex.getMessage(), ex);
        }

        cache.storeLibrary(aUri, lib, mdl, lmdt);
        return lib;
    }


    void releaseLibrary(IDataTableLibrary aLibrary) throws IOException
    {
        URI uri = getURI(aLibrary);
        cache.removeLibrary(uri);
        aLibrary.close();
    }

    // ------------------------------------------------------------------
    // Members
    // ------------------------------------------------------------------


    Stream<? extends ILibraryMember> getLibraryMembers(IDataTableLibrary aLibrary)
    {
        LibraryProviderFactory lpf = LibraryProviderFactory.getInstance();
        return lpf.getAllProvidersFor(getURI(aLibrary), aLibrary.getFileInfo()).stream()
                .flatMap(p ->
                {
                    try
                    {
                        return p.provideLibraryMembers(aLibrary);
                    }
                    catch (IOException ex)
                    {
                        throw new UncheckedIOException(ex);
                    }
                });
    }

    // ------------------------------------------------------------------
    // Metadata attachment / retrieval
    // ------------------------------------------------------------------


    void attachMetadata(IDataTableLibrary aLibrary, URI aUri) throws IOException
    {
        IMetadataLibrary mdl = loadMetadata(aUri);
        if (aLibrary instanceof AbstractDataTableLibrary ali)
        {
            ali.setMetadata(mdl);
        }
        cache.updateMetadata(aLibrary, mdl);
    }


    IMetadataLibrary getMetadataLibrary(IDataTableLibrary aLibrary) throws IOException
    {
        IMetadataLibrary cached = cache.lookupMetadata(aLibrary);
        if (cached != null)
        {
            return cached;
        }
        IMetadataLibrary attached = aLibrary.getMetadata();
        if (attached != null)
        {
            return attached;
        }
        // Fall back to per-member column metadata derived from the library itself so consumers
        // (e.g. the CDISC CORE engine) still see names/types/labels when no Define-XML or other
        // external metadata has been attached.
        return DataTableLibraryMetadataAdapter.of(aLibrary);
    }


    private IMetadataLibrary loadMetadata(URI aUri) throws IOException
    {
        DefineSupport define = DefineCache.sharedInstance().getOrLoad(aUri);
        return DefineMetadataLibrary.from(define);
    }
}
