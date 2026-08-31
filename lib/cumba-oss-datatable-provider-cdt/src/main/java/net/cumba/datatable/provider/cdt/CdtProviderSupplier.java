package net.cumba.datatable.provider.cdt;

import java.net.URI;
import java.util.List;

import net.cumba.datatable.impl.provider.AbstractProviderSupplier;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.provider.IDataTableProvider;

import org.jspecify.annotations.Nullable;

/**
 * SPI supplier that creates {@link CdtTableProvider} instances for {@code .cdt} URIs. Accepts URIs
 * with and without a fragment: a fragment selects a specific dataset within a multi-dataset file; a
 * bare URI returns the first dataset.
 */
public class CdtProviderSupplier extends AbstractProviderSupplier
{

    private static final String ID_FI_CDT = "a1d7c0f8-2e2b-4b6a-9e3a-1f3ef4e6b9c1";

    /**
     * The {@link FileInfo} descriptor for {@code .cdt} files.
     */
    public static final FileInfo FI_CDT = FileInfo.createFor("cdt", "Cumba Data Table", ID_FI_CDT);

    /**
     * Unmodifiable list containing the single supported file info.
     */
    public static final List<FileInfo> FIS = List.of(FI_CDT);

    public CdtProviderSupplier()
    {
        super(FIS);
    }


    @Override
    public @Nullable IDataTableProvider getProvider(URI aUri, @Nullable FileInfo aFileInfo)
    {
        if (canProvideFor(aUri, aFileInfo))
        {
            return new CdtTableProvider();
        }
        return null;
    }
}
