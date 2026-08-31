package net.cumba.datatable.provider.xlsx.library;

import java.net.URI;
import java.util.List;
import net.cumba.datatable.help.CDT;
import net.cumba.datatable.impl.library.AbstractLibrarySupplier;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.library.ILibraryProvider;
import net.cumba.datatable.provider.xlsx.ExcelProviderSupplier;
import org.jspecify.annotations.Nullable;

public class ExcelLibrarySupplier extends AbstractLibrarySupplier
{

    public static final List<FileInfo> FIS = List.of(ExcelProviderSupplier.FI_XLS,
            ExcelProviderSupplier.FI_XLSX);

    public ExcelLibrarySupplier()
    {
        super(FIS);
    }


    @Override
    public boolean canProvideFor(URI aUri, @Nullable FileInfo aFileInfo)
    {
        if (aFileInfo != null)
        {
            return FIS.contains(aFileInfo);
        }

        if (!CDT.isBlankOrNull(aUri.getFragment()))
        {
            return false;
        }

        return super.canProvideFor(aUri, null);
    }


    @Override
    public @Nullable ILibraryProvider getProvider(URI aUri, @Nullable FileInfo aFileInfo)
    {
        if (canProvideFor(aUri, aFileInfo))
        {
            return new ExcelLibraryProvider();
        }
        return null;
    }

}
