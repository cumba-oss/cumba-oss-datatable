package net.cumba.datatable.impl.library;

import java.net.URI;
import java.util.List;
import lombok.NonNull;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.library.ILibrarySupplier;
import org.jspecify.annotations.Nullable;

/**
 * Abstract implementation of the ILibrarySupplier interface that handles the list of supported
 * files ( {@link FileInfo}s ) and the {@link #canProvideFor(URI, FileInfo)} check.
 */
public abstract class AbstractLibrarySupplier implements ILibrarySupplier
{

    /**
     * The list of supported file types.
     */
    private final List<FileInfo> supportedFiles;

    /**
     *
     * @param aSupportedFiles
     *            the list of supported file types.
     */
    protected AbstractLibrarySupplier(@NonNull List<FileInfo> aSupportedFiles)
    {
        supportedFiles = aSupportedFiles;
    }


    @Override
    public List<FileInfo> getSupportedFileInfos()
    {
        return supportedFiles;
    }


    @Override
    public boolean canProvideFor(URI aUri, @Nullable FileInfo aFileInfo)
    {
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

}
