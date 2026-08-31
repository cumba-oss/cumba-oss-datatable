package net.cumba.datatable.impl.provider;

import java.util.List;

import lombok.NonNull;
import net.cumba.datatable.io.AbstractGenericSupplier;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.provider.IDataTableProvider;
import net.cumba.datatable.provider.IProviderSupplier;

public abstract class AbstractProviderSupplier extends AbstractGenericSupplier<IDataTableProvider>
        implements
        IProviderSupplier
{

    protected AbstractProviderSupplier(@NonNull List<FileInfo> aSupportedFiles)
    {
        super(aSupportedFiles);
    }

}
