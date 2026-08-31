package net.cumba.datatable.library.folder;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.impl.library.AbstractLibraryProvider;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.library.IDataTableLibrary;
import net.cumba.datatable.library.ILibraryMember;
import net.cumba.datatable.library.ILibraryProvider;
import org.jspecify.annotations.Nullable;

public class FolderLibraryProvider extends AbstractLibraryProvider
{

    @SuppressWarnings("this-escape")
    public FolderLibraryProvider()
    {
        setName("Folder Library");
        setDescription("Provides a local folder as library");
    }


    @Override
    public List<FileInfo> getSupportedFileInfos()
    {
        return Arrays.asList(FolderLibrarySupplier.FI_FOLDER);
    }


    @Override
    public IDataTableLibrary provide(URI aUri, @Nullable FileInfo aFileInfo) throws IOException
    {
        Path p = Path.of(aUri);
        if (!Files.isDirectory(p))
        {
            throw new IOException("URI does not point to a local directory.");
        }
        String name = ILibraryProvider.defaultLibraryName(aUri, aFileInfo);
        return new FolderLibrary(p, name);
    }


    @Override
    public Stream<? extends ILibraryMember> provideLibraryMembers(IDataTableLibrary aLibrary)
        throws IOException
    {
        if (aLibrary instanceof FolderLibrary fl)
        {
            return fl.getMembers();
        }
        return Stream.empty();
    }


    @Override
    public Stream<? extends DataTableColumnMeta> provideLibraryMemberColumns(ILibraryMember aMember)
        throws IOException
    {
        if (aMember instanceof FolderMember fm)
        {
            List<? extends DataTableColumnMeta> cols = fm.getColumns();
            return cols.stream();
        }
        return Stream.empty();
    }


    @Override
    public @Nullable Object getLibraryAttribute(IDataTableLibrary aLibrary,
            @Nullable String aAttributeKey)
    {
        return null;
    }

}
