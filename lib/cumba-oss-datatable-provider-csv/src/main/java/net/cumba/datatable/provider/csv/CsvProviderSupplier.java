package net.cumba.datatable.provider.csv;

import java.net.URI;
import java.util.List;
import net.cumba.datatable.impl.provider.AbstractProviderSupplier;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.provider.IDataTableProvider;
import org.jspecify.annotations.Nullable;

public class CsvProviderSupplier extends AbstractProviderSupplier
{

    private static final String ID_FI_CSV = "211e8abb-734a-4be5-88b9-d37a94b10374";

    public static final FileInfo FI_CSV = FileInfo.createFor("csv", "Comma Separated Values",
            ID_FI_CSV);

    public static final List<FileInfo> FIS = List.of(FI_CSV);

    public CsvProviderSupplier()
    {
        super(FIS);
    }


    @Override
    public @Nullable IDataTableProvider getProvider(URI aUri, @Nullable FileInfo aFileInfo)
    {
        if (canProvideFor(aUri, aFileInfo))
        {
            return new CsvTableProvider();
        }
        return null;
    }

}
