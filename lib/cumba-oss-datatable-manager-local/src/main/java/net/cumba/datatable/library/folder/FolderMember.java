package net.cumba.datatable.library.folder;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.help.CDT;
import net.cumba.datatable.library.ILibraryMember;
import org.jspecify.annotations.Nullable;

@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class FolderMember implements ILibraryMember
{

    @Getter
    @EqualsAndHashCode.Include
    private final FolderLibrary library;

    @EqualsAndHashCode.Include
    private final Path file;

    @Getter
    private final String name;

    private @Nullable List<DataTableColumnMeta> columns;

    public FolderMember(FolderLibrary aLibrary, Path aFile)
    {
        library = aLibrary;
        file = aFile;
        String fileName = aFile.getFileName() != null ? aFile.getFileName().toString()
                : aFile.toString();
        name = CDT.getBeforeLast(fileName, '.').toUpperCase(Locale.ROOT);
    }


    @Override
    public @Nullable String getLabel()
    {
        return null;
    }


    @Override
    public URI getUri()
    {
        return file.toUri();
    }


    public List<DataTableColumnMeta> getColumns()
    {
        if (columns == null)
        {
            columns = new ArrayList<>();
            // TODO: fill columns
        }
        return columns;
    }

}
