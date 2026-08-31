package net.cumba.datatable.provider.sas.xpt;

import java.net.URI;
import java.util.List;
import net.cumba.datatable.impl.provider.AbstractProviderSupplier;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.provider.IDataTableProvider;
import net.cumba.datatable.provider.IProviderSupplier;
import org.jspecify.annotations.Nullable;

/**
 * A {@link IProviderSupplier} for SAS XPT (v5 transport) files. This supplier is registered via
 * ServiceLoader and creates {@link XptTableProvider} instances for matching URIs.
 */
public class XptProviderSupplier extends AbstractProviderSupplier
{

    private static final String ID_FI_XPT = "a183d9aa-bd69-4f9a-baa2-e4648d140b86";

    /**
     * The {@link FileInfo} descriptor for .xpt files.
     */
    public static final FileInfo FI_XPT = FileInfo.createFor("xpt", "SAS v5 Xport Format",
            ID_FI_XPT);

    /**
     * An unmodifiable list containing the single supported file info.
     */
    public static final List<FileInfo> FIS = List.of(FI_XPT);

    public XptProviderSupplier()
    {
        super(FIS);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable IDataTableProvider getProvider(URI aUri, @Nullable FileInfo aFileInfo)
    {
        if (canProvideFor(aUri, aFileInfo))
        {
            return new XptTableProvider();
        }
        return null;
    }
}
