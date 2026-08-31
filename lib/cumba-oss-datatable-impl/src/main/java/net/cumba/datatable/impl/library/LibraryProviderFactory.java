package net.cumba.datatable.impl.library;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.net.URI;
import lombok.CustomLog;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.io.GenericProviderFactory;
import net.cumba.datatable.library.IDataTableLibrary;
import net.cumba.datatable.library.ILibraryProvider;
import net.cumba.datatable.library.ILibrarySupplier;
import org.jspecify.annotations.Nullable;

@CustomLog
public class LibraryProviderFactory
        extends GenericProviderFactory<ILibrarySupplier, ILibraryProvider, IDataTableLibrary>
{

    // Lazily-initialised double-checked-locking singleton; null until first getInstance().
    private static volatile @Nullable LibraryProviderFactory instance;

    public static LibraryProviderFactory getInstance()
    {
        LibraryProviderFactory res = instance;

        if (res == null)
        {
            synchronized (LibraryProviderFactory.class)
            {
                res = instance;
                if (res == null)
                {
                    res = instance = new LibraryProviderFactory();
                }
            }
        }
        return res;
    }


    private LibraryProviderFactory()
    {
        super(ILibrarySupplier.class);
    }


    /**
     * Resolve a {@link FileInfo} for the given file name from the registered library providers by
     * matching the file extension (case insensitive). With ambiguous extensions the first match in
     * supplier iteration order wins.
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
     * Provide a library for the given URI and file info.
     *
     * @param aUri
     *            the URI to be loaded.
     * @param aFileInfo
     *            the optional FileInfo.
     * @return the loaded library. This might be null.
     * @throws IOException
     *             in case no library can be provided.
     */
    @Override
    public @Nullable IDataTableLibrary provide(URI aUri, @Nullable FileInfo aFileInfo)
        throws IOException
    {
        @Nullable
        Throwable t = null;

        for (ILibrarySupplier ps : getSuppliers())
        {
            if (ps.canProvideFor(aUri, aFileInfo))
            {
                ILibraryProvider provider = ps.getProvider(aUri, aFileInfo);
                if (provider != null)
                {
                    try
                    {
                        IDataTableLibrary res = provider.provide(aUri, aFileInfo);
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
            return null;
        }
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
