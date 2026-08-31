package net.cumba.datatable.impl.provider;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.metadata.IMetadataLibrary;
import net.cumba.datatable.provider.IDataTableProvider;
import net.cumba.datatable.provider.IProviderSupplier;

/**
 * Test-only {@link IProviderSupplier} used by {@code DataTableProviderFactoryStateClearTest}.
 *
 * <p>
 * Registered via {@code src/test/resources/META-INF/services/} so the singleton
 * {@link DataTableProviderFactory} picks it up alongside any production suppliers. The supplier
 * advertises a single FileInfo with extension {@code s3a9probe} — an extension no other module or
 * test uses — so existing edge-case tests that look for "no supplier matches" results on
 * {@code .unknown} URIs continue to behave unchanged.
 *
 * <p>
 * The supplier exposes the most recently returned provider (per-thread / atomic) so tests can
 * assert that the factory has cleared {@code metadata} after a failed probe — without needing
 * reflection into the factory's internals.
 */
public class S3ProbeProviderSupplier implements IProviderSupplier
{

    /** Unique FileInfo for the probe — never used by any production code path. */
    public static final FileInfo PROBE_INFO = FileInfo.builder().fileExtension("s3a9probe")
            .fileNamePattern("(?i).*\\.s3a9probe").description("Probe (test)")
            .uuid("d3c9b8e1-7f64-4a92-ba51-1f1a4e90a909").build();

    /** Holds the most recently constructed probe provider for inspection by tests. */
    static final AtomicReference<S3ProbeProvider> LAST_PROVIDER = new AtomicReference<>();

    /** Controls whether the next provider should throw on provide / provideMetaData. */
    static volatile boolean failNext = false;

    /** Controls what type of throwable to raise. */
    enum FailMode
    {
        IO_EXCEPTION, RUNTIME_EXCEPTION
    }

    static volatile FailMode failMode = FailMode.IO_EXCEPTION;

    /** If non-null, used as the successful result of provide() / provideMetaData(). */
    static volatile IDataTable successDataTable;

    static volatile DataTableMeta successMeta;

    /**
     * If true, the provider's set-metadata throws — used to test clearProviderState.
     */
    static volatile boolean throwOnSetters = false;

    /**
     * Reset the static control state to a quiescent default. Tests <b>must</b> call this in
     * {@code @BeforeEach} so they don't pollute subsequent tests.
     */
    static void reset()
    {
        failNext = false;
        failMode = FailMode.IO_EXCEPTION;
        successDataTable = null;
        successMeta = null;
        throwOnSetters = false;
        LAST_PROVIDER.set(null);
    }


    public S3ProbeProviderSupplier()
    {
        // required by the service-loader-style createSupplier mechanism
    }


    @Override
    public List<FileInfo> getSupportedFileInfos()
    {
        return List.of(PROBE_INFO);
    }


    @Override
    public IDataTableProvider getProvider(URI aUri, FileInfo aFileInfo)
    {
        S3ProbeProvider p = new S3ProbeProvider(failNext, failMode);
        LAST_PROVIDER.set(p);
        return p;
    }

    /**
     * Provider returned by the test supplier. Captures its set-metadata calls and optionally throws
     * when asked to provide.
     */
    public static final class S3ProbeProvider implements IDataTableProvider
    {

        private final boolean failOnProvide;

        private final FailMode mode;

        private volatile IMetadataLibrary metadata;

        S3ProbeProvider(boolean aFailOnProvide, FailMode aMode)
        {
            this.failOnProvide = aFailOnProvide;
            this.mode = aMode;
        }


        public IMetadataLibrary metadata()
        {
            return metadata;
        }


        @Override
        public String getName()
        {
            return "probe";
        }


        @Override
        public String getDescription()
        {
            return "probe";
        }


        @Override
        public List<FileInfo> getSupportedFileInfos()
        {
            return List.of(PROBE_INFO);
        }


        @Override
        public void setMetadata(IMetadataLibrary aMetadata)
        {
            // Tripwire only on null writes so the initializer path (non-null) can still wire the
            // provider before the catch path attempts to clear it via clearProviderState.
            if (throwOnSetters && aMetadata == null)
            {
                throw new IllegalStateException("setMetadata(null) boom");
            }
            this.metadata = aMetadata;
        }


        @Override
        public IDataTable provide(URI aUri, FileInfo aFileInfo) throws IOException
        {
            if (failOnProvide)
            {
                throwForMode("provide");
            }
            return successDataTable;
        }


        @Override
        public IDataTable provide(net.cumba.datatable.library.ILibraryMember aMember,
                FileInfo aFileInfo)
            throws IOException
        {
            if (failOnProvide)
            {
                throwForMode("provide(member)");
            }
            return successDataTable;
        }


        @Override
        public DataTableMeta provideMetaData(URI aUri, FileInfo aFileInfo) throws IOException
        {
            if (failOnProvide)
            {
                throwForMode("provideMetaData");
            }
            return successMeta;
        }


        private void throwForMode(String aSite) throws IOException
        {
            if (mode == FailMode.IO_EXCEPTION)
            {
                throw new IOException("probe forced failure at " + aSite);
            }
            throw new IllegalStateException("probe forced failure at " + aSite);
        }
    }
}
