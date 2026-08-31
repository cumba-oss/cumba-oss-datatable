package net.cumba.datatable.io;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import lombok.CustomLog;
import lombok.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A generic implementation of a service factory that handles {@link IGenericSupplier}'s registered
 * as service.
 *
 * @param <S>
 *            the class of the {@link IGenericSupplier}.
 * @param <P>
 *            the class of the {@link IGenericProvider}.
 * @param <V>
 *            the class of the provider value.
 */
@CustomLog
public class GenericProviderFactory<S extends IGenericSupplier<P>, P extends IGenericProvider<V>, V>
        extends GenericServiceFactory<S, P>
{

    /**
     * Create a new instance for the given supplier class (interface).
     */
    public GenericProviderFactory(@NonNull Class<S> aSupplierClass)
    {
        super(aSupplierClass);
    }


    /**
     * Returns a list of all {@link FileInfo}s from all available suppliers.
     *
     * @return a list of all {@link FileInfo}s from all available suppliers.
     */
    public List<FileInfo> getFileInfos()
    {
        List<FileInfo> res = new ArrayList<>();

        for (S ps : getSuppliers())
        {
            res.addAll(ps.getSupportedFileInfos());
        }
        return res;
    }


    /**
     * Retrieve the first provider that can handle the given combination of URI and FileInfo.
     *
     * @param aURI
     *            the URI to be loaded by the provider.
     * @param aFileInfo
     *            the optional FileInfo that defines how to load / interpret the URI content.<br/>
     *            This might be null.
     * @return the first provider found for the given combination of URI and FileInfo.
     */
    public Optional<P> getFirstProviderFor(URI aURI, @Nullable FileInfo aFileInfo)
    {
        for (S ps : getSuppliers())
        {
            if (ps.canProvideFor(aURI, aFileInfo))
            {
                P res = ps.getProvider(aURI, aFileInfo);
                if (res != null)
                {
                    return Optional.of(res);
                }
            }
        }

        return Optional.empty();
    }


    /**
     * Retrieve all providers that can handle the given combination of URI and FileInfo. This method
     * is mostly to be used with null value as FileInfo to understand if multiple providers might be
     * able to handle the URI and in this case ask the user how to interpret the URI / which
     * provider to be used.
     *
     * @param aURI
     *            the URI to be loaded by the provider.
     * @param aFileInfo
     *            the optional FileInfo that defines how to load / interpret the URI content.<br/>
     *            This might be null.
     * @return the list of all providers that can handle the given combination of URI and FileInfo.
     */
    public List<P> getAllProvidersFor(URI aURI, @Nullable FileInfo aFileInfo)
    {
        List<P> res = new ArrayList<>();

        for (S ps : getSuppliers())
        {
            if (ps.canProvideFor(aURI, aFileInfo))
            {
                P provider = ps.getProvider(aURI, aFileInfo);
                if (provider != null)
                {
                    res.add(provider);
                }
            }
        }

        return res;
    }


    /**
     * Provide requested value by looping over all providers suppliers asking for a matching
     * provider. Then asking available providers to provide. The first non null result is
     * returned.<br/>
     * If no non null result is available but an exception was caught, the exception is thrown,
     * otherwise null is returned.
     *
     * @param aURI
     *            the URI to be loaded by the provider.
     * @param aFileInfo
     *            the optional FileInfo that defines how to load / interpret the URI content.<br/>
     *            This might be null.
     * @return the first value that was provided by a matching provider when looping through all
     *         suppliers or null if no provider can provide a value.
     * @throws IOException
     *             in case no value can be provided by an IOException was thrown by at least one
     *             provider.
     */
    public @Nullable V provide(URI aURI, @Nullable FileInfo aFileInfo) throws IOException
    {
        return provide(aURI, aFileInfo, null);
    }


    /**
     * Provide requested value by looping over all providers suppliers asking for a matching
     * provider. Then asking available providers to provide. The first non null result is
     * returned.<br/>
     * If no non null result is available but an exception was caught, the exception is thrown,
     * otherwise null is returned.
     *
     * @param aURI
     *            the URI to be loaded by the provider.
     * @param aFileInfo
     *            the optional FileInfo that defines how to load / interpret the URI content.<br/>
     *            This might be null.
     * @param aInitializer
     *            a function that initializes the provider that was retrieved from the supplier
     *            before it is asked to provide the value. If the initialization was successful true
     *            is returned, otherwise false. If this function returns false, the provider is not
     *            asked to provide a value.
     * @return the first value that was provided by a matching provider when looping through all
     *         suppliers or null if no provider can provide a value.
     * @throws IOException
     *             in case no value can be provided by an IOException was thrown by at least one
     *             provider.
     */
    public @Nullable V provide(URI aURI, @Nullable FileInfo aFileInfo,
            @Nullable Predicate<P> aInitializer)
        throws IOException
    {
        Throwable t = null;
        for (S ps : getSuppliers())
        {
            if (ps.canProvideFor(aURI, aFileInfo))
            {
                P provider = ps.getProvider(aURI, aFileInfo);
                if (provider != null)
                {
                    // an initializer is given
                    // not initialized --> do not use
                    if (aInitializer != null && !aInitializer.test(provider))
                    {
                        continue;
                    }

                    try
                    {
                        V res = provider.provide(aURI, aFileInfo);
                        if (res != null)
                        {
                            return res;
                        }
                    }
                    catch (Throwable ex)
                    {
                        LOGGER.log(Level.DEBUG, ex.getMessage(), ex);
                        if (t == null)
                        {
                            t = ex;
                        }
                        else
                        {
                            t.addSuppressed(ex);
                        }
                    }
                }
            }
        }

        if (t == null)
        {
            // no exception and no table --> return null
            return null;
        }

        // at least one exception was caught
        if (t instanceof IOException ioexception)
        {
            throw ioexception;
        }
        if (t instanceof RuntimeException runtimeexception)
        {
            throw runtimeexception;
        }
        throw new IOException(t);
    }

}
