package net.cumba.datatable.provider.sas.sas7bdat;

import java.net.URI;
import java.util.List;
import net.cumba.datatable.impl.provider.AbstractProviderSupplier;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.provider.IDataTableProvider;
import net.cumba.datatable.provider.IProviderSupplier;
import org.jspecify.annotations.Nullable;

/**
 * A {@link IProviderSupplier} for SAS sas7bdat (BDAT) files using the sas-utils library. This
 * supplier is currently disabled (commented out) in the ServiceLoader registration. Creates
 * {@link BdatTableProvider} instances for matching URIs.
 */
public class BdatProviderSupplier extends AbstractProviderSupplier
{

    private static final String ID_FI_BDAT = "98d09254-476c-4971-8b8a-8fc382d5a3db";

    /**
     * The {@link FileInfo} descriptor for .sas7bdat files.
     */
    public static final FileInfo FI_BDAT = FileInfo.createFor("sas7bdat", "SAS DataSet v7",
            ID_FI_BDAT);

    /**
     * An unmodifiable list containing the single supported file info.
     */
    public static final List<FileInfo> FIS = List.of(FI_BDAT);

    public BdatProviderSupplier()
    {
        super(FIS);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable IDataTableProvider getProvider(URI aUri, @Nullable FileInfo aFileInfo)
    {
        if (!canProvideFor(aUri, aFileInfo))
        {
            return null;
        }

        return new BdatTableProvider();
    }
}
