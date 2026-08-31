package net.cumba.datatable.provider.parquet;

import java.net.URI;
import java.util.List;
import net.cumba.datatable.impl.provider.AbstractProviderSupplier;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.provider.IDataTableProvider;
import org.jspecify.annotations.Nullable;

/**
 * ServiceLoader entry point for the Carpet-based Parquet format provider. Registered via
 * {@code META-INF/services/net.cumba.datatable.provider.IProviderSupplier} and supplies
 * {@link ParquetTableProvider} instances for {@code .parquet} files.
 */
public class ParquetProviderSupplier extends AbstractProviderSupplier
{

    private static final String ID_FI_PARQUET = "4091435e-cacc-42d1-8478-5fb72e5cb976";

    /**
     * File info descriptor for the Parquet format.
     */
    public static final FileInfo FI_PARQUET = FileInfo.createFor("parquet", "Parquet (Carpet)",
            ID_FI_PARQUET);

    /**
     * Unmodifiable list of all file formats supported by this supplier.
     */
    public static final List<FileInfo> FIS = List.of(FI_PARQUET);

    /**
     * Creates a new supplier with Parquet file info registered.
     */
    public ParquetProviderSupplier()
    {
        super(FIS);
    }


    /**
     * Returns a {@link ParquetTableProvider} if this supplier can handle the given URI and file
     * info, or {@code null} otherwise.
     *
     * @param aUri
     *            the URI of the data source
     * @param aFileInfo
     *            the file info describing the format
     * @return a new {@link ParquetTableProvider}, or {@code null}
     */
    @Override
    public @Nullable IDataTableProvider getProvider(URI aUri, @Nullable FileInfo aFileInfo)
    {
        if (canProvideFor(aUri, aFileInfo))
        {
            return new ParquetTableProvider();
        }
        return null;
    }

}
