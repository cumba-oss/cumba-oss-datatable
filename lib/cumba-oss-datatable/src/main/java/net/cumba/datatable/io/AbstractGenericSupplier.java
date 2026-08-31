package net.cumba.datatable.io;

import java.util.Collections;
import java.util.List;

public abstract class AbstractGenericSupplier<T extends IGenericProvider<?>>
        implements
        IGenericSupplier<T>
{

    private final List<FileInfo> supportedFiles;

    protected AbstractGenericSupplier(List<FileInfo> aSupportedFiles)
    {
        supportedFiles = aSupportedFiles != null ? Collections.unmodifiableList(aSupportedFiles)
                : Collections.emptyList();
    }


    @Override
    public List<FileInfo> getSupportedFileInfos()
    {
        return supportedFiles;
    }

}
