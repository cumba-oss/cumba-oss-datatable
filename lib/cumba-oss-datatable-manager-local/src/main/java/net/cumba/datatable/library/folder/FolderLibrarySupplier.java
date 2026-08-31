package net.cumba.datatable.library.folder;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.library.ILibraryProvider;
import net.cumba.datatable.library.ILibrarySupplier;
import org.jspecify.annotations.Nullable;

public class FolderLibrarySupplier implements ILibrarySupplier
{

    private static final String ID_FI_FOLDER = "63da4f78-d204-48e0-bd64-3bfb8181487d";

    public static final FileInfo FI_FOLDER = FileInfo.create("", "(?i)[^.]+", "Local Folder",
            ID_FI_FOLDER);

    @Override
    public List<FileInfo> getSupportedFileInfos()
    {
        return Arrays.asList(FI_FOLDER);
    }


    @Override
    public boolean canProvideFor(URI aUri, @Nullable FileInfo aFileInfo)
    {
        if (!Objects.equals("file", aUri.getScheme()))
        {
            return false;
        }

        Path p = Path.of(aUri);
        return Files.isDirectory(p);
    }


    @Override
    public @Nullable ILibraryProvider getProvider(URI aUri, @Nullable FileInfo aFileInfo)
    {
        if (canProvideFor(aUri, aFileInfo))
        {
            return new FolderLibraryProvider();
        }
        return null;
    }

}
