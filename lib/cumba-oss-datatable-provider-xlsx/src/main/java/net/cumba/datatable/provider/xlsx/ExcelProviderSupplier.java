package net.cumba.datatable.provider.xlsx;

import java.net.URI;
import java.util.List;
import net.cumba.datatable.impl.provider.AbstractProviderSupplier;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.provider.IDataTableProvider;
import org.jspecify.annotations.Nullable;

public class ExcelProviderSupplier extends AbstractProviderSupplier
{

    private static final String ID_FI_XLS = "d99c3f96-47c3-4fc6-906a-5d90b6295d23";

    private static final String ID_FI_XLSX = "e3efe2d8-78aa-4cab-9378-090dc17d4ef5";

    public static final FileInfo FI_XLS = FileInfo.createFor("xls", "Microsoft Excel Spreadsheet",
            ID_FI_XLS);

    public static final FileInfo FI_XLSX = FileInfo.createFor("xlsx",
            "Microsoft Excel Open XML Spreadsheet", ID_FI_XLSX);

    public static final List<FileInfo> FIS = List.of(FI_XLS, FI_XLSX);

    public ExcelProviderSupplier()
    {
        super(FIS);
    }


    @Override
    public @Nullable IDataTableProvider getProvider(URI aUri, @Nullable FileInfo aFileInfo)
    {
        if (canProvideFor(aUri, aFileInfo))
        {
            return new ExcelTableProvider();
        }
        return null;
    }

}
