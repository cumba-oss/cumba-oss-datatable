package net.cumba.datatable.provider.cdt.library;

import java.net.URI;
import java.util.List;

import net.cumba.datatable.help.CDT;
import net.cumba.datatable.impl.library.AbstractLibrarySupplier;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.library.ILibraryProvider;
import net.cumba.datatable.provider.cdt.CdtProviderSupplier;

import org.jspecify.annotations.Nullable;

/**
 * SPI supplier that registers {@code .cdt} files for library access. Fragment-bearing URIs are
 * rejected — those identify a single dataset and are handled by the single- dataset
 * {@link net.cumba.datatable.provider.cdt.CdtProviderSupplier}.
 */
public class CdtLibrarySupplier extends AbstractLibrarySupplier
{

    public static final List<FileInfo> FIS = List.of(CdtProviderSupplier.FI_CDT);

    public CdtLibrarySupplier()
    {
        super(FIS);
    }


    @Override
    public boolean canProvideFor(URI aUri, @Nullable FileInfo aFileInfo)
    {
        if (aUri == null || !CDT.isBlankOrNull(aUri.getFragment()))
        {
            return false;
        }
        return super.canProvideFor(aUri, aFileInfo);
    }


    @Override
    public @Nullable ILibraryProvider getProvider(URI aUri, @Nullable FileInfo aFileInfo)
    {
        if (canProvideFor(aUri, aFileInfo))
        {
            return new CdtLibraryProvider();
        }
        return null;
    }
}
