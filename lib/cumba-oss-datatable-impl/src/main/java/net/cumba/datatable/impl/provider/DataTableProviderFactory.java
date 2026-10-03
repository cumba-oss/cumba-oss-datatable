package net.cumba.datatable.impl.provider;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import lombok.CustomLog;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.io.GenericProviderFactory;
import net.cumba.datatable.io.Property;
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
        return provide(aURI, aFileInfo, aMetadata, Collections.emptyMap());
    }


    public @Nullable IDataTable provide(ILibraryMember aMember, @Nullable FileInfo aFileInfo,
            @Nullable IMetadataLibrary aMetadata)
        throws IOException
    {
        return provide(aMember, aFileInfo, aMetadata, Collections.emptyMap());
    }


    public @Nullable IDataTable provide(ILibraryMember aMember, @Nullable FileInfo aFileInfo,
            Predicate<IDataTableProvider> aInitializer)
        throws IOException
    {
        return provide(aMember, aFileInfo, aInitializer, Collections.emptyMap());
    }


    /**
     * {@inheritDoc}
     * <p>
     * Routed through the property-carrying loop below (with no properties), so that a provider
     * failing mid-probe has its state cleared before the next supplier is tried — exactly as the
     * metadata-carrying overloads do. The error contract of
     * {@link GenericProviderFactory#provide(URI, FileInfo, Predicate)} is kept: every throwable a
     * provider raises is recorded and the next supplier is tried; the first one is rethrown as is
     * when it is an {@link IOException} or a {@link RuntimeException}, and wrapped in an
     * {@link IOException} otherwise. The one exception is a {@link VirtualMachineError} (out of
     * memory, stack overflow): it propagates at once, unwrapped, and no further supplier is tried.
     * </p>
     */
    @Override
    public @Nullable IDataTable provide(URI aURI, @Nullable FileInfo aFileInfo,
            @Nullable Predicate<IDataTableProvider> aInitializer)
        throws IOException
    {
        return provide(aURI, aFileInfo, aInitializer, Collections.emptyMap());
    }


    /**
     * Provide a data table with the given provider properties.
     *
     * @param aURI
     *            the URI to be loaded.
     * @param aFileInfo
     *            the optional FileInfo.
     * @param aMetadata
     *            the metadata library to be given to the provider.
     * @param aProperties
     *            the provider properties to be used.
     * @return the loaded data table. This might be null.
     * @throws IOException
     *             in case no data table can be provided.
     */
    public @Nullable IDataTable provide(URI aURI, @Nullable FileInfo aFileInfo,
            @Nullable IMetadataLibrary aMetadata, Map<Property, String> aProperties)
        throws IOException
    {
        Predicate<IDataTableProvider> init = p ->
        {
            p.setMetadata(aMetadata);
            return true;
        };

        return provide(aURI, aFileInfo, init, aProperties);
    }


    /**
     * Provide a data table for a library member with the given provider properties.
     *
     * @param aMember
     *            the library member to be loaded.
     * @param aFileInfo
     *            the optional FileInfo.
     * @param aMetadata
     *            the metadata library to be given to the provider.
     * @param aProperties
     *            the provider properties to be used.
     * @return the loaded data table. This might be null.
     * @throws IOException
     *             in case no data table can be provided.
     */
    public @Nullable IDataTable provide(ILibraryMember aMember, @Nullable FileInfo aFileInfo,
            @Nullable IMetadataLibrary aMetadata, Map<Property, String> aProperties)
        throws IOException
    {
        Predicate<IDataTableProvider> init = p ->
        {
            p.setMetadata(aMetadata);
            return true;
        };

        return provide(aMember, aFileInfo, init, aProperties);
    }


    private @Nullable IDataTable provide(URI aURI, @Nullable FileInfo aFileInfo,
            @Nullable Predicate<IDataTableProvider> aInitializer, Map<Property, String> aProperties)
        throws IOException
    {
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
                IDataTable res = provider.provide(aURI, aFileInfo, aProperties);
                if (res != null)
                {
                    return res;
                }
            }
            catch (Throwable ex)
            {
                if (ex instanceof VirtualMachineError fatal)
                {
                    throw fatal;
                }
                t = recordSuppressedException(t, ex);
                clearProviderState(provider);
            }
        }

        rethrowAggregatedMetaError(t);
        return null;
    }


    private @Nullable IDataTable provide(ILibraryMember aMember, @Nullable FileInfo aFileInfo,
            @Nullable Predicate<IDataTableProvider> aInitializer, Map<Property, String> aProperties)
        throws IOException
    {
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
                IDataTable res = provider.provide(aMember, aFileInfo, aProperties);
                if (res != null)
                {
                    return res;
                }
            }
            catch (Throwable ex)
            {
                if (ex instanceof VirtualMachineError fatal)
                {
                    throw fatal;
                }
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
     * {@link IDataTableProvider#provideMetaData(URI, FileInfo, Map)}.
     * <p>
     * The per-supplier error contract is the one of {@link #provide(URI, FileInfo, Predicate)}.
     * </p>
     *
     * @param aURI
     *            the URI to provide metadata for.
     * @param aFileInfo
     *            the optional FileInfo.
     * @param aMetadata
     *            the metadata library to be given to the provider.
     * @param aProperties
     *            the provider properties to be used.
     * @return the loaded metadata. This might be null if no supplier can handle the URI.
     * @throws IOException
     *             in case no metadata can be provided by any of the providers and at least one
     *             provider has thrown an IOException.
     */
    public @Nullable DataTableMeta provideMetaData(URI aURI, @Nullable FileInfo aFileInfo,
            @Nullable IMetadataLibrary aMetadata, Map<Property, String> aProperties)
        throws IOException
    {
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
                DataTableMeta res = provider.provideMetaData(aURI, aFileInfo, aProperties);
                if (res != null)
                {
                    return res;
                }
            }
            catch (Throwable ex)
            {
                if (ex instanceof VirtualMachineError fatal)
                {
                    throw fatal;
                }
                t = recordSuppressedException(t, ex);
                clearProviderState(provider);
            }
        }

        rethrowAggregatedMetaError(t);
        return null;
    }


    /**
     * Combine successive failures while probing the suppliers: the first non-null throwable becomes
     * the primary, all subsequent ones are recorded via {@link Throwable#addSuppressed}. Always
     * logs at DEBUG.
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
     * If any supplier threw while probing, re-throw it preserving the original type when it is an
     * {@link IOException} or {@link RuntimeException}; otherwise wrap into an IOException. No-op
     * when {@code aThrowable} is null.
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
     * <p>
     * The per-supplier error contract is the one of {@link #provide(URI, FileInfo, Predicate)}.
     * </p>
     *
     * @param aMember
     *            the library member to provide metadata for.
     * @param aFileInfo
     *            the optional FileInfo.
     * @param aMetadata
     *            the metadata library to be given to the provider.
     * @param aProperties
     *            the provider properties to be used.
     * @return the loaded metadata. This might be null if no supplier can handle the member.
     * @throws IOException
     *             in case no metadata can be provided by any of the providers.
     */
    public @Nullable DataTableMeta provideMetaData(ILibraryMember aMember,
            @Nullable FileInfo aFileInfo, @Nullable IMetadataLibrary aMetadata,
            Map<Property, String> aProperties)
        throws IOException
    {
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
                DataTableMeta res = provider.provideMetaData(aMember, aFileInfo, aProperties);
                if (res != null)
                {
                    return res;
                }
            }
            catch (Throwable ex)
            {
                if (ex instanceof VirtualMachineError fatal)
                {
                    throw fatal;
                }
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
    private static void clearProviderState(@Nullable IDataTableProvider aProvider)
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


    /**
     * Retrieve the provider properties for the given URI and file info.
     *
     * @param aUri
     *            the URI to retrieve the properties for.
     * @param aFileInfo
     *            the optional FileInfo.
     * @return the list of provider properties. This might be empty but never null.
     */
    public List<Property> getProviderProperties(URI aUri, @Nullable FileInfo aFileInfo)
    {
        Optional<IDataTableProvider> provider = getFirstProviderFor(aUri, aFileInfo);
        return provider.map(p -> p.getProviderProperties(aUri, aFileInfo))
                .orElse(Collections.emptyList());
    }


    /**
     * Retrieve the provider properties for the given library member and file info.
     *
     * @param aMember
     *            the library member to retrieve the properties for.
     * @param aFileInfo
     *            the optional FileInfo.
     * @return the list of provider properties. This might be empty but never null.
     */
    public List<Property> getProviderProperties(ILibraryMember aMember,
            @Nullable FileInfo aFileInfo)
    {
        if (aMember == null)
        {
            return Collections.emptyList();
        }
        FileInfo fi = aFileInfo != null ? aFileInfo : aMember.getFileInfo();
        Optional<IDataTableProvider> provider = getFirstProviderFor(aMember.getUri(), fi);
        return provider.map(p -> p.getProviderProperties(aMember, fi))
                .orElse(Collections.emptyList());
    }

}
