package net.cumba.datatable.impl.provider;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.util.function.Predicate;
import lombok.CustomLog;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.io.GenericProviderFactory;
import net.cumba.datatable.library.ILibraryMember;
import net.cumba.datatable.metadata.IMetadataLibrary;
import net.cumba.datatable.provider.IDataTableProvider;
import net.cumba.datatable.provider.IProviderSupplier;
import org.jspecify.annotations.Nullable;

/**
 * A factory for {@link IProviderSupplier}s and {@link IDataTableProvider}s.
 */
@CustomLog
public class DataTableProviderFactory
        extends GenericProviderFactory<IProviderSupplier, IDataTableProvider, IDataTable>
{

    /**
     * the global instance that can be retrieved by {@link #getFactory()}.
     */
    private static volatile @Nullable DataTableProviderFactory instance;

    /**
     * Returns the one and only single global factory instance.
     *
     * @return the one and only single global factory instance.
     */
    public static DataTableProviderFactory getFactory()
    {
        DataTableProviderFactory res = instance;
        if (res != null)
        {
            return res;
        }
        synchronized (DataTableProviderFactory.class)
        {
            res = instance;
            if (res != null)
            {
                return res;
            }
            DataTableProviderFactory created = new DataTableProviderFactory();
            instance = created;
            return created;
        }
    }


    /**
     * Internal constructor. Use {@link #getFactory()} to retrieve the instance.
     */
    private DataTableProviderFactory()
    {
        super(IProviderSupplier.class);
    }


    /**
     * Resolve a {@link FileInfo} for the given file name from the registered providers by matching
     * the file extension (case insensitive). With ambiguous extensions the first match in supplier
     * iteration order wins.
     *
     * @param aFileName
     *            the file name (may be {@code null}).
     * @return the matching FileInfo, or {@code null} when the extension is missing or not
     *         supported.
     */
    public @Nullable FileInfo resolveFormatFromFileName(@Nullable String aFileName)
    {
        return FileInfo.findByFileName(aFileName, getFileInfos());
    }


    /**
     * A special provider method that initializes the providers with the given metadata before the
     * provider is asked to provide the data table.
     *
     * @param aURI
     *            the URI to be loaded by the provider.
     * @param aFileInfo
     *            the optional FileInfo that defines how to load / interpret the URI content.<br/>
     *            This might be null.
     * @param aMetadata
     *            the metadata library to be given to the provider.
     * @return the data table that was loaded. This might be null.
     * @throws IOException
     *             in case no data table can be provided by any of the providers and at least one
     *             provider has thrown an IOException.
     */
    public @Nullable IDataTable provide(URI aURI, @Nullable FileInfo aFileInfo,
            @Nullable IMetadataLibrary aMetadata)
        throws IOException
    {
        Predicate<IDataTableProvider> init = p ->
        {
            p.setMetadata(aMetadata);
            return true;
        };

        return provide(aURI, aFileInfo, init);
    }


    public @Nullable IDataTable provide(ILibraryMember aMember, @Nullable FileInfo aFileInfo,
            @Nullable IMetadataLibrary aMetadata)
        throws IOException
    {
        Predicate<IDataTableProvider> init = p ->
        {
            p.setMetadata(aMetadata);
            return true;
        };

        return provide(aMember, aFileInfo, init);
    }


    @Override
    public @Nullable IDataTable provide(URI aURI, @Nullable FileInfo aFileInfo,
            @Nullable Predicate<IDataTableProvider> aInitializer)
        throws IOException
    {
        @Nullable
        Throwable t = null;

        for (IProviderSupplier ps : getSuppliers())
        {
            if (!ps.canProvideFor(aURI, aFileInfo))
            {
                continue;
            }
            IDataTableProvider provider = ps.getProvider(aURI, aFileInfo);
            if (provider == null)
            {
                continue;
            }
            if (aInitializer != null && !aInitializer.test(provider))
            {
                continue;
            }
            try
            {
                IDataTable res = provider.provide(aURI, aFileInfo);
                if (res != null)
                {
                    return res;
                }
            }
            catch (RuntimeException | IOException ex)
            {
                t = recordSuppressedException(t, ex);
                clearProviderState(provider);
            }
        }

        rethrowAggregatedMetaError(t);
        return null;
    }


    public @Nullable IDataTable provide(ILibraryMember aMember, @Nullable FileInfo aFileInfo,
            @Nullable Predicate<IDataTableProvider> aInitializer)
        throws IOException
    {
        @Nullable
        Throwable t = null;

        for (IProviderSupplier ps : getSuppliers())
        {
            if (!ps.canProvideFor(aMember, aFileInfo))
            {
                continue;
            }
            IDataTableProvider provider = ps.getProvider(aMember, aFileInfo);
            if (provider == null)
            {
                continue;
            }
            if (aInitializer != null && !aInitializer.test(provider))
            {
                continue;
            }
            try
            {
                IDataTable res = callProvide(provider, aMember, aFileInfo);
                if (res != null)
                {
                    return res;
                }
            }
            catch (RuntimeException | IOException ex)
            {
                t = recordSuppressedException(t, ex);
                clearProviderState(provider);
            }
        }

        rethrowAggregatedMetaError(t);
        return null;
    }


    /**
     * Provide only the {@link DataTableMeta} for the given URI without reading row data. The first
     * supplier that can handle the URI wins; the resulting provider is queried via
     * {@link IDataTableProvider#provideMetaData(URI, FileInfo)}.
     *
     * @param aURI
     *            the URI to provide metadata for.
     * @param aFileInfo
     *            the optional FileInfo.
     * @param aMetadata
     *            the metadata library to be given to the provider.
     * @return the loaded metadata. This might be null if no supplier can handle the URI.
     * @throws IOException
     *             in case no metadata can be provided by any of the providers and at least one
     *             provider has thrown an IOException.
     */
    public @Nullable DataTableMeta provideMetaData(URI aURI, @Nullable FileInfo aFileInfo,
            @Nullable IMetadataLibrary aMetadata)
        throws IOException
    {
        @Nullable
        Throwable t = null;

        for (IProviderSupplier ps : getSuppliers())
        {
            if (!ps.canProvideFor(aURI, aFileInfo))
            {
                continue;
            }
            IDataTableProvider provider = ps.getProvider(aURI, aFileInfo);
            if (provider == null)
            {
                continue;
            }
            provider.setMetadata(aMetadata);
            try
            {
                DataTableMeta res = callProvideMetaData(provider, aURI, aFileInfo);
                if (res != null)
                {
                    return res;
                }
            }
            catch (RuntimeException | IOException ex)
            {
                t = recordSuppressedException(t, ex);
                clearProviderState(provider);
            }
        }

        rethrowAggregatedMetaError(t);
        return null;
    }


    /**
     * Combine successive failures during {@code provideMetaData} probing: the first non-null
     * throwable becomes the primary, all subsequent ones are recorded via
     * {@link Throwable#addSuppressed}. Always logs at DEBUG.
     */
    private static Throwable recordSuppressedException(@Nullable Throwable aPrimary, Throwable aNew)
    {
        LOGGER.log(Level.DEBUG, aNew.getMessage(), aNew);
        if (aPrimary == null)
        {
            return aNew;
        }
        // Guard against self-suppression: Throwable.addSuppressed throws
        // IllegalArgumentException if aNew == aPrimary (the same instance, e.g. when a supplier
        // rethrows a singleton sentinel). Keep aPrimary unchanged in that case.
        if (aPrimary != aNew)
        {
            aPrimary.addSuppressed(aNew);
        }
        return aPrimary;
    }


    /**
     * If any supplier threw while probing metadata, re-throw it preserving the original type when
     * it is an {@link IOException} or {@link RuntimeException}; otherwise wrap into an IOException.
     * No-op when {@code aThrowable} is null.
     */
    private static void rethrowAggregatedMetaError(@Nullable Throwable aThrowable)
        throws IOException
    {
        if (aThrowable == null)
        {
            return;
        }
        if (aThrowable instanceof IOException ioe)
        {
            throw ioe;
        }
        if (aThrowable instanceof RuntimeException re)
        {
            throw re;
        }
        throw new IOException(aThrowable);
    }


    /**
     * Provide only the {@link DataTableMeta} for the given library member without reading row data.
     *
     * @param aMember
     *            the library member to provide metadata for.
     * @param aFileInfo
     *            the optional FileInfo.
     * @param aMetadata
     *            the metadata library to be given to the provider.
     * @return the loaded metadata. This might be null if no supplier can handle the member.
     * @throws IOException
     *             in case no metadata can be provided by any of the providers.
     */
    public @Nullable DataTableMeta provideMetaData(ILibraryMember aMember,
            @Nullable FileInfo aFileInfo, @Nullable IMetadataLibrary aMetadata)
        throws IOException
    {
        @Nullable
        Throwable t = null;

        for (IProviderSupplier ps : getSuppliers())
        {
            if (!ps.canProvideFor(aMember, aFileInfo))
            {
                continue;
            }
            IDataTableProvider provider = ps.getProvider(aMember, aFileInfo);
            if (provider == null)
            {
                continue;
            }
            provider.setMetadata(aMetadata);
            try
            {
                DataTableMeta res = callProvideMetaData(provider, aMember, aFileInfo);
                if (res != null)
                {
                    return res;
                }
            }
            catch (RuntimeException | IOException ex)
            {
                t = recordSuppressedException(t, ex);
                clearProviderState(provider);
            }
        }

        rethrowAggregatedMetaError(t);
        return null;
    }


    /**
     * Best-effort cleanup after a probe failure: drop the metadata reference on the provider so a
     * pooled supplier doesn't leak the previous caller's state into subsequent unrelated calls.
     * Failures are logged at DEBUG and never propagate.
     */
    private static void clearProviderState(IDataTableProvider aProvider)
    {
        if (aProvider == null)
        {
            return;
        }
        try
        {
            aProvider.setMetadata(null);
        }
        catch (RuntimeException ex)
        {
            LOGGER.log(Level.DEBUG, "Provider state clear failed: " + ex.getMessage(), ex);
        }
    }


    private static @Nullable IDataTable callProvide(IDataTableProvider aProvider,
            ILibraryMember aMember, @Nullable FileInfo aFileInfo)
        throws IOException
    {
        return aProvider.provide(aMember, aFileInfo);
    }


    private static @Nullable DataTableMeta callProvideMetaData(IDataTableProvider aProvider,
            URI aURI, @Nullable FileInfo aFileInfo)
        throws IOException
    {
        return aProvider.provideMetaData(aURI, aFileInfo);
    }


    private static @Nullable DataTableMeta callProvideMetaData(IDataTableProvider aProvider,
            ILibraryMember aMember, @Nullable FileInfo aFileInfo)
        throws IOException
    {
        return aProvider.provideMetaData(aMember, aFileInfo);
    }

}
