package net.cumba.datatable.io;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A generic provider interface that can be used together with {@link IGenericSupplier}.
 *
 * @param <V>
 *            The type of the value object that can be provided by this provider.
 */
public interface IGenericProvider<V>
{

    /**
     * Returns the name of the provider. This can be used in a UI to identify the provider.
     *
     * @return the name of the provider. This can be used in a UI to identify the provider.
     */
    String getName();


    /**
     * Returns the description of the provider. This can be used in a UI to describe the provider.
     *
     * @return the description of the provider. This can be used in a UI to describe the provider.
     */
    String getDescription();


    /**
     * Returns a list of supported file types as {@link FileInfo}s.
     *
     * @return a list of supported file types as {@link FileInfo}s.
     */
    List<FileInfo> getSupportedFileInfos();


    /**
     * Provide the object that can be created by reading the content of the given URI and
     * interpreting it as the given FileInfo.
     *
     * @param aUri
     *            the URI to read the content from.
     * @param aFileInfo
     *            the optional FileInfo how to interpret the file. If this is not one of the
     *            {@link FileInfo}s from {@link #getSupportedFileInfos()}, the result is undefined.
     * @return the object that was created by reading the content of the given URI. This must not be
     *         null!
     * @throws IOException
     *             in case the content of the URI can not be interpreted in a way to generate a
     *             valid result object. This can be either error when reading the content or error
     *             when interpreting it.
     */
    V provide(URI aUri, @Nullable FileInfo aFileInfo) throws IOException;


    /**
     * Provide the object that can be created by reading the content of the given URI.<br/>
     * This is a short version of {@link #provide(URI, FileInfo)} with null given as FileInfo.r
     *
     * @param aUri
     *            the URI to read the content from.
     * @return the object that was created by reading the content of the given URI. This must not be
     *         null!
     * @throws IOException
     *             in case the content of the URI can not be interpreted in a way to generate a
     *             valid result object. This can be either error when reading the content or error
     *             when interpreting it.
     */
    default V provide(URI aUri) throws IOException
    {
        return provide(aUri, null);
    }
}
