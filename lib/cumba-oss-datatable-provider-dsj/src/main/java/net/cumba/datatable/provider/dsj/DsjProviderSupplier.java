package net.cumba.datatable.provider.dsj;

import java.net.URI;
import java.util.List;
import net.cumba.datatable.impl.provider.AbstractProviderSupplier;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.provider.IDataTableProvider;
import org.jspecify.annotations.Nullable;

/**
 * ServiceLoader supplier that registers DataSet-JSON file format support (JSON, NDJSON, DSJC) and
 * creates {@link DsjTableProvider} instances for reading these formats. This is the row-based
 * variant using {@code AbstractTableDataParser}.
 *
 * <p>
 * Discovered via {@code META-INF/services/net.cumba.datatable.provider.IProviderSupplier}.
 * </p>
 */
public class DsjProviderSupplier extends AbstractProviderSupplier
{

    private static final String ID_DSJ_JSON = "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d";

    private static final String ID_DSJ_NDJSON = "b2c3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e";

    private static final String ID_DSJ_DSJC = "c3d4e5f6-a7b8-4c9d-0e1f-2a3b4c5d6e7f";

    /**
     * File info for standard DataSet-JSON (.json) format.
     */
    public static final FileInfo FI_DSJ_JSON = FileInfo.createFor("json", "DataSet-JSON (v2)",
            ID_DSJ_JSON);

    /**
     * File info for newline-delimited DataSet-JSON (.ndjson) format.
     */
    public static final FileInfo FI_DSJ_NDJSON = FileInfo.createFor("ndjson",
            "New-line Delimited JSON (v2)", ID_DSJ_NDJSON);

    /**
     * File info for compressed DataSet-JSON (.dsjc) format.
     */
    public static final FileInfo FI_DSJ_DSJC = FileInfo.createFor("dsjc",
            "Compressed Dataset-JSON (v2)", ID_DSJ_DSJC);

    /**
     * Unmodifiable list of all supported DataSet-JSON file formats.
     */
    public static final List<FileInfo> FIS = List.of(FI_DSJ_JSON, FI_DSJ_NDJSON, FI_DSJ_DSJC);

    /**
     * Constructs a new supplier and registers all supported DSJ file formats.
     */
    public DsjProviderSupplier()
    {
        super(FIS);
    }


    /**
     * Returns a new {@link DsjTableProvider} if this supplier can handle the given URI and file
     * info.
     *
     * @param aUri
     *            the URI of the data source.
     * @param aFileInfo
     *            the file type information.
     * @return a new {@link DsjTableProvider}, or {@code null} if the format is not supported.
     */
    @Override
    public @Nullable IDataTableProvider getProvider(URI aUri, @Nullable FileInfo aFileInfo)
    {
        if (canProvideFor(aUri, aFileInfo))
        {
            return new DsjTableProvider();
        }
        return null;
    }

}
