package net.cumba.datatable.impl.io;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.cumba.datatable.help.SecureTempFiles;
import net.cumba.datatable.help.URIHelper;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.io.IGenericProvider;
import net.cumba.datatable.io.IGenericSupplier;
import org.jspecify.annotations.Nullable;

/**
 * An abstract implementation of the {@link IGenericProvider} interface that provides getter and
 * setter for name and description.
 *
 * @param <V>
 *            the type of value produced by this provider.
 */
// name/description are @NonNull (per IGenericProvider.getName/getDescription) but are populated by
// subclasses through the protected Lombok setters after construction; NullAway cannot see that
// deferred init, so its init check is suppressed here.
@SuppressWarnings("NullAway.Init")
public abstract class AbstractGenericProvider<V> implements IGenericProvider<V>
{

    /** A temp-file suffix {@link #downloadToFile(URI)} keeps: a dot and 1-16 safe characters. */
    private static final java.util.regex.Pattern PLAUSIBLE_EXTENSION = java.util.regex.Pattern
            .compile("\\.[A-Za-z0-9_+-]{1,16}");

    /**
     * Optional field that contains the supplier that created this instance. This might be null and
     * not every provider is created by a supplier.
     */
    @Getter
    private final @Nullable IGenericSupplier<? extends IGenericProvider<? extends V>> supplier;

    @Getter
    @Setter(value = AccessLevel.PROTECTED)
    private String name;

    @Getter
    @Setter(value = AccessLevel.PROTECTED)
    private String description;

    protected AbstractGenericProvider()
    {
        this(null);
    }


    protected <S extends IGenericSupplier<? extends IGenericProvider<? extends V>>> AbstractGenericProvider(
            @Nullable S aSupplier)
    {
        supplier = aSupplier;
    }


    @Override
    public List<FileInfo> getSupportedFileInfos()
    {
        return supplier != null ? supplier.getSupportedFileInfos() : Collections.emptyList();
    }


    /**
     * Download the content of the URI to a local temporary file and return this file.
     *
     * @param aUri
     *            the URI to download the content from.
     * @return the file that was downloaded.
     * @throws IOException
     *             in case download throws an exception.
     */
    protected File downloadToFile(@NonNull URI aUri) throws IOException
    {
        // The suffix is the FILE NAME's extension, dot included, so FileInfo patterns (".*\.csv")
        // still match the temp file. Taken from URIHelper.getFileName, not getPath(): getPath() is
        // null for an opaque jar: URI (a file inside a zip), which used to leave the temp file
        // without any extension; and getAfterLast(path, '.') answers the WHOLE path when there is
        // no dot, a "suffix" full of separators that made createTempFile throw.
        // Only a PLAUSIBLE extension is kept: a trailing dot, a decoded NUL, a backslash or a
        // character Windows reserves after the last dot would otherwise make createTempFile throw
        // an unchecked exception out of this IOException-declaring method.
        String name = URIHelper.getFileName(aUri);
        int dot = name == null ? -1 : name.lastIndexOf('.');
        String ext = name == null || dot < 0 ? "" : name.substring(dot);
        String suffix = PLAUSIBLE_EXTENSION.matcher(ext).matches() ? ext : "";
        File f = SecureTempFiles.createTempFile("temp_cumba", suffix).toFile();
        try
        {
            downloadToFile(aUri, f, 131_072);
        }
        catch (IOException | RuntimeException ex)
        {
            // Don't leak the temp file if the download fails before it is handed back to the
            // caller (the caller is responsible for cleanup only once we return it).
            Files.deleteIfExists(f.toPath());
            throw ex;
        }
        return f;
    }


    /**
     * Download the content of the URI to the specified file.
     *
     * @param aUri
     *            the URI to download the content from.
     * @param aFile
     *            the file to download to.
     * @param aBufferSize
     *            the number of bytes to be used as download buffer. 128k (131_072) is a good value
     * @throws IOException
     *             in case download throws an exception.
     */
    protected void downloadToFile(@NonNull URI aUri, @NonNull File aFile, int aBufferSize)
        throws IOException
    {
        try (InputStream in = URIHelper.openStream(aUri))
        {
            try (FileOutputStream out = new FileOutputStream(aFile))
            {
                byte[] buffer = new byte[aBufferSize];
                int count;
                while ((count = in.read(buffer)) >= 0)
                {
                    out.write(buffer, 0, count);
                }
            }
        }
    }
}
