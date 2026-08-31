package net.cumba.datatable.io;

import java.net.URI;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A generic supplier interface that can be used together with {@link IGenericProvider}.
 *
 * @param <T>
 *            the provider type that can be retrieved by this supplier.
 */
public interface IGenericSupplier<T extends IGenericProvider<?>>
{

    /**
     * Returns a list of supported {@link FileInfo}'s.
     *
     * @return a list of supported {@link FileInfo}'s.
     */
    List<FileInfo> getSupportedFileInfos();


    /**
     * Test if this supplier can provide a provider of type {@code T} for the given URI when
     * interpreted as the given {@link FileInfo}.
     *
     * @param aUri
     *            the URI to test for.
     * @param aFileInfo
     *            the optional {@link FileInfo} to test with.<br/>
     *            This can be null.<br/>
     *            A client should only provide {@link FileInfo}s received by
     *            {@link #getSupportedFileInfos()} but an implementation <b>MUST</b> be able to
     *            handle any value here.
     * @return true if the supplier expects that it can provide a provider of type {@code T} for the
     *         given URI and FileInfo, false otherwise. This method might return true even if it
     *         finally can not provide a provider, but it will never return false when it can
     *         provide a provider.
     */
    default boolean canProvideFor(URI aUri, @Nullable FileInfo aFileInfo)
    {
        List<FileInfo> supportedFiles = getSupportedFileInfos();
        if (aFileInfo != null)
        {
            return supportedFiles.contains(aFileInfo);
        }

        String path = aUri.getPath();
        if (path == null)
        {
            return false;
        }
        int idx = path.lastIndexOf('.');
        if (idx >= 0)
        {
            String ext = path.substring(idx + 1);
            for (FileInfo fi : supportedFiles)
            {
                if (ext.equalsIgnoreCase(fi.getFileExtension()))
                {
                    return true;
                }
            }
        }
        return false;
    }


    /**
     * Test if this supplier can provide a provider of type {@code T} for the given URI.<br/>
     * This is a short call to {@link #canProvideFor(URI, FileInfo)} with null being passed as
     * FileInfo.
     *
     * @param aUri
     *            the URI to test for.
     * @return true if the supplier expects that it can provide a provider of type {@code T} for the
     *         given URI and FileInfo, false otherwise. This method might return true even if it
     *         finally can not provide a provider, but it will never return false when it can
     *         provide a provider.
     */
    default boolean canProvideFor(URI aUri)
    {
        return canProvideFor(aUri, null);
    }


    /**
     * Retrieve a provider for the given URI when interpreted as the given FileInfo.
     *
     * @param aUri
     *            the URI to be handled by the provider.
     * @param aFileInfo
     *            the file info that provides information on how to interpret the URI.<br/>
     *            This can be null.<br/>
     *            A client should only provide {@link FileInfo}s received by
     *            {@link #getSupportedFileInfos()} but an implementation <b>MUST</b> be able to
     *            handle any value here.
     * @return a provider or null if no provider can be retrieved for the given combination of URI
     *         and FileInfo.
     */
    @Nullable
    T getProvider(URI aUri, @Nullable FileInfo aFileInfo);


    /**
     * Retrieve a provider for the given URI.<br/>
     * This is a short call to {@link #getProvider(URI, FileInfo)} with null being passed as
     * FileInfo.
     *
     * @param aUri
     *            the URI to be handled by the provider.
     * @return a provider or null if no provider can be retrieved for the given URI.
     */
    default @Nullable T getProvider(URI aUri)
    {
        return getProvider(aUri, null);
    }

}
