package net.cumba.datatable.library.folder;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import lombok.CustomLog;
import lombok.Getter;
import net.cumba.datatable.impl.library.AbstractDataTableLibrary;
import net.cumba.datatable.impl.library.LibraryMemberComparator;
import net.cumba.datatable.impl.provider.DataTableProviderFactory;
import net.cumba.datatable.io.FileInfo;

@CustomLog
public class FolderLibrary extends AbstractDataTableLibrary
{

    @Getter
    private final Path folder;

    private final FileInfo fileInfo;

    private final Pattern fileInfoPattern;

    public FolderLibrary(File aFolder)
    {
        this(aFolder.toPath(), aFolder.getName());
    }


    public FolderLibrary(Path aFolder)
    {
        this(aFolder, aFolder.getFileName() != null ? aFolder.getFileName().toString()
                : aFolder.toString());
    }


    public FolderLibrary(Path aFolder, String aName)
    {
        super(aName, aName, aFolder.toUri(), FolderLibrarySupplier.FI_FOLDER);
        folder = aFolder;
        List<FileInfo> fis = DataTableProviderFactory.getFactory().getFileInfos();
        fileInfo = FileInfo.createCombined(fis);
        fileInfoPattern = Pattern.compile(fileInfo.getFileNamePattern());
    }


    @Override
    public String getType()
    {
        return "folder";
    }


    public Stream<FolderMember> getMembers()
    {
        List<FolderMember> members = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder, this::acceptFile))
        {
            for (Path entry : stream)
            {
                members.add(new FolderMember(this, entry));
            }
        }
        catch (IOException ex)
        {
            LOGGER.log(System.Logger.Level.WARNING, "Failed to list folder: {0}", folder, ex);
            return Stream.empty();
        }
        members.sort(new LibraryMemberComparator());
        return members.stream();
    }


    private boolean acceptFile(Path aPath)
    {
        // Only accept regular files (not directories), matching the file pattern
        try
        {
            if (Files.isDirectory(aPath))
            {
                return false;
            }
        }
        catch (Exception _)
        {
            return false;
        }
        String name = aPath.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileInfoPattern.matcher(name).matches();
    }

}
