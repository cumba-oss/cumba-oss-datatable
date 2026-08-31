package net.cumba.datatable.impl.library.dblib;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.cumba.datatable.impl.library.AbstractLibrarySupplier;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.library.ILibraryProvider;
import org.jspecify.annotations.Nullable;

/**
 * Supplier that registers the {@code .dblib} file extension and creates
 * {@link DataBrowserLibraryProvider} instances.
 */
public class DataBrowserLibrarySupplier extends AbstractLibrarySupplier
{

    private static final String ID_FI_DBLIB = "7c3a91e2-4f8b-4d6a-b5e1-9a2c3d4e5f60";

    public static final FileInfo FI_DBLIB = FileInfo.createFor("dblib", "Data Browser Library",
            ID_FI_DBLIB);

    public static final List<FileInfo> FIS = Collections.unmodifiableList(Arrays.asList(FI_DBLIB));

    public DataBrowserLibrarySupplier()
    {
        super(FIS);
    }


    @Override
    public @Nullable ILibraryProvider getProvider(URI aUri, @Nullable FileInfo aFileInfo)
    {
        if (canProvideFor(aUri, aFileInfo))
        {
            return new DataBrowserLibraryProvider();
        }
        return null;
    }

}
