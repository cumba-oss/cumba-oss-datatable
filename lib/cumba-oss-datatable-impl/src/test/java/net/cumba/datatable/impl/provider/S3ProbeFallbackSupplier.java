package net.cumba.datatable.impl.provider;

import java.net.URI;
import java.util.List;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.metadata.IMetadataLibrary;
import net.cumba.datatable.provider.IDataTableProvider;
import net.cumba.datatable.provider.IProviderSupplier;

/**
 * Test-only second {@link IProviderSupplier} for the {@link S3ProbeProviderSupplier#PROBE_INFO}
 * extension, registered <b>after</b> the probe supplier in
 * {@code src/test/resources/META-INF/services/}. It is dormant unless a test enables it: while
 * {@link #enabled} is false it advertises no {@link FileInfo}, so no factory call reaches it and
 * every existing probe test keeps its "the only supplier failed" outcome.
 *
 * <p>
 * Enabled, it lets a test prove the factory's per-supplier error contract: when the probe provider
 * throws, the factory records the throwable, clears the probe's state and asks this supplier next,
 * whose provider answers with {@link #table}.
 * </p>
 */
// The service loader instantiates this supplier reflectively, so the tests can only steer it
// through static control fields; reset() restores them before each test.
@SuppressWarnings("PMD.MutableStaticState")
public class S3ProbeFallbackSupplier implements IProviderSupplier
{

    /** When false (the default) this supplier advertises nothing and is never asked. */
    static volatile boolean enabled = false;

    /** The table the fallback provider answers with; may be null. */
    static volatile IDataTable table;

    /** Restore the dormant default. {@link S3ProbeProviderSupplier#reset()} calls this. */
    static void reset()
    {
        enabled = false;
        table = null;
    }


    @Override
    public List<FileInfo> getSupportedFileInfos()
    {
        return enabled ? List.of(S3ProbeProviderSupplier.PROBE_INFO) : List.of();
    }


    @Override
    public IDataTableProvider getProvider(URI aUri, FileInfo aFileInfo)
    {
        return new FallbackProvider();
    }

    /** Provider returned by the fallback supplier: never fails, answers with {@link #table}. */
    public static final class FallbackProvider implements IDataTableProvider
    {

        private volatile IMetadataLibrary metadata;

        public IMetadataLibrary metadata()
        {
            return metadata;
        }


        @Override
        public String getName()
        {
            return "probe-fallback";
        }


        @Override
        public String getDescription()
        {
            return "probe-fallback";
        }


        @Override
        public List<FileInfo> getSupportedFileInfos()
        {
            return List.of(S3ProbeProviderSupplier.PROBE_INFO);
        }


        @Override
        public void setMetadata(IMetadataLibrary aMetadata)
        {
            this.metadata = aMetadata;
        }


        @Override
        public IDataTable provide(URI aUri, FileInfo aFileInfo)
        {
            return table;
        }


        @Override
        public IDataTable provide(net.cumba.datatable.library.ILibraryMember aMember,
                FileInfo aFileInfo)
        {
            return table;
        }


        @Override
        public DataTableMeta provideMetaData(URI aUri, FileInfo aFileInfo)
        {
            IDataTable t = table;
            return t != null ? t.getMetaData() : null;
        }
    }
}
