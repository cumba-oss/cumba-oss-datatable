package net.cumba.datatable.provider.xlsx;

import java.net.URI;
import java.util.List;
import net.cumba.datatable.impl.provider.AbstractProviderSupplier;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.provider.IDataTableProvider;
import org.jspecify.annotations.Nullable;

/**
 * Supplies the Excel table provider.
 * <p>
 * <b>Only OOXML ({@code .xlsx}) is offered.</b> Legacy BIFF {@code .xls} used to be advertised here
 * as {@code FI_XLS}, but it was never readable: the underlying {@code excel-streaming-reader} is
 * OOXML-only, so a genuine {@code .xls} threw through every entry point. The format was removed
 * rather than implemented (ruling Q37) — advertising a format that always fails is worse than not
 * offering it. Re-adding it means writing real {@code HSSFWorkbook} support first, not restoring a
 * {@link FileInfo} constant.
 */
public class ExcelProviderSupplier extends AbstractProviderSupplier
{

    private static final String ID_FI_XLSX = "e3efe2d8-78aa-4cab-9378-090dc17d4ef5";

    public static final FileInfo FI_XLSX = FileInfo.createFor("xlsx",
            "Microsoft Excel Open XML Spreadsheet", ID_FI_XLSX);

    public static final List<FileInfo> FIS = List.of(FI_XLSX);

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
