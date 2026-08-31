package net.cumba.datatable.impl.provider;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.URI;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.provider.S3ProbeProviderSupplier.FailMode;
import net.cumba.datatable.impl.provider.S3ProbeProviderSupplier.S3ProbeProvider;
import net.cumba.datatable.library.ILibraryMember;
import net.cumba.datatable.metadata.IMetadataLibrary;
import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * F-A9 / F-A13 — verifies that {@link DataTableProviderFactory} clears the provider's
 * {@code metadata} field in the per-supplier catch block (F-A9).
 *
 * <p>
 * The {@link S3ProbeProviderSupplier} is registered via
 * {@code src/test/resources/META-INF/services/net.cumba.datatable.provider.IProviderSupplier}, so
 * the singleton factory routes any URI ending in {@code .s3a9probe} (or any call carrying the
 * supplier's PROBE_INFO) to a test-controllable provider whose state we can inspect.
 */
class DataTableProviderFactoryStateClearTest
{

    private static final URI PROBE_URI = URI.create("file:///tmp/state-clear.s3a9probe");

    private final DataTableProviderFactory factory = DataTableProviderFactory.getFactory();

    @BeforeEach
    void resetProbe()
    {
        S3ProbeProviderSupplier.reset();
    }


    @AfterEach
    void resetAfter()
    {
        // belt-and-braces: leave no global state for subsequent tests
        S3ProbeProviderSupplier.reset();
    }

    // ================== F-A9: provide(URI) clears state on exception ==================


    @Test
    void provideUri_ioExceptionClearsMetadata()
    {
        S3ProbeProviderSupplier.failNext = true;
        S3ProbeProviderSupplier.failMode = FailMode.IO_EXCEPTION;
        IMetadataLibrary metadata = Mockito.mock(IMetadataLibrary.class);

        assertThrows(IOException.class,
                () -> factory.provide(PROBE_URI, S3ProbeProviderSupplier.PROBE_INFO, metadata));

        S3ProbeProvider p = S3ProbeProviderSupplier.LAST_PROVIDER.get();
        assertNotNull(p, "supplier should have returned a probe provider");
        assertNull(p.metadata(), "F-A9: metadata must be cleared after exception");
    }


    @Test
    void provideUri_runtimeExceptionClearsMetadata()
    {
        S3ProbeProviderSupplier.failNext = true;
        S3ProbeProviderSupplier.failMode = FailMode.RUNTIME_EXCEPTION;
        IMetadataLibrary metadata = Mockito.mock(IMetadataLibrary.class);

        assertThrows(IllegalStateException.class,
                () -> factory.provide(PROBE_URI, S3ProbeProviderSupplier.PROBE_INFO, metadata));

        S3ProbeProvider p = S3ProbeProviderSupplier.LAST_PROVIDER.get();
        assertNotNull(p);
        assertNull(p.metadata(), "F-A9: metadata must be cleared even for RuntimeException");
    }


    @Test
    void provideMember_ioExceptionClearsMetadata()
    {
        S3ProbeProviderSupplier.failNext = true;
        ILibraryMember member = Mockito.mock(ILibraryMember.class);
        Mockito.when(member.getUri()).thenReturn(PROBE_URI);
        Mockito.when(member.getFileInfo()).thenReturn(S3ProbeProviderSupplier.PROBE_INFO);

        IMetadataLibrary metadata = Mockito.mock(IMetadataLibrary.class);

        assertThrows(IOException.class,
                () -> factory.provide(member, S3ProbeProviderSupplier.PROBE_INFO, metadata));

        S3ProbeProvider p = S3ProbeProviderSupplier.LAST_PROVIDER.get();
        assertNotNull(p);
        assertNull(p.metadata(),
                "F-A9: metadata must be cleared after exception (member overload)");
    }


    @Test
    void provideMetaDataUri_ioExceptionClearsMetadata()
    {
        S3ProbeProviderSupplier.failNext = true;
        IMetadataLibrary metadata = Mockito.mock(IMetadataLibrary.class);

        assertThrows(IOException.class, () -> factory.provideMetaData(PROBE_URI,
                S3ProbeProviderSupplier.PROBE_INFO, metadata));

        S3ProbeProvider p = S3ProbeProviderSupplier.LAST_PROVIDER.get();
        assertNotNull(p);
        assertNull(p.metadata(), "F-A9: metadata must be cleared on failed metadata probe");
    }


    @Test
    void provideMetaDataMember_ioExceptionClearsMetadata()
    {
        S3ProbeProviderSupplier.failNext = true;
        ILibraryMember member = Mockito.mock(ILibraryMember.class);
        Mockito.when(member.getUri()).thenReturn(PROBE_URI);
        Mockito.when(member.getFileInfo()).thenReturn(S3ProbeProviderSupplier.PROBE_INFO);

        IMetadataLibrary metadata = Mockito.mock(IMetadataLibrary.class);

        assertThrows(IOException.class, () -> factory.provideMetaData(member,
                S3ProbeProviderSupplier.PROBE_INFO, metadata));

        S3ProbeProvider p = S3ProbeProviderSupplier.LAST_PROVIDER.get();
        assertNotNull(p);
        assertNull(p.metadata());
    }

    // ================== F-A9: clearProviderState absorbs setter failures ==================


    @Test
    void clearProviderState_swallowsSetterException()
    {
        // The throwOnSetters tripwire is wired to fire only on null writes — so the initial
        // setMetadata(metadata) inside the provideMetaData loop succeeds, the metadata read then
        // throws (failNext), the catch block invokes clearProviderState which calls
        // setMetadata(null) → tripwire throws → clearProviderState must absorb it and let the
        // original IOException from the metadata read be rethrown.
        S3ProbeProviderSupplier.failNext = true;
        S3ProbeProviderSupplier.failMode = FailMode.IO_EXCEPTION;
        S3ProbeProviderSupplier.throwOnSetters = true;
        IMetadataLibrary metadata = Mockito.mock(IMetadataLibrary.class);

        // We must see the *original* IOException — not the tripwire IllegalStateException.
        IOException ex = assertThrows(IOException.class, () -> factory.provideMetaData(PROBE_URI,
                S3ProbeProviderSupplier.PROBE_INFO, metadata));
        org.junit.jupiter.api.Assertions.assertTrue(
                ex.getMessage() != null && ex.getMessage().contains("probe forced failure"),
                "primary exception (the probe IOException) must propagate, not the setter tripwire");

        // sanity: probe was reached
        assertNotNull(S3ProbeProviderSupplier.LAST_PROVIDER.get());
    }

    // ================== F-A9 negative: success path leaves metadata wired ==================


    @Test
    void provideMetaDataUri_successLeavesMetadataWired()
    {
        S3ProbeProviderSupplier.failNext = false;
        S3ProbeProviderSupplier.successMeta = mockMeta();
        IMetadataLibrary metadata = Mockito.mock(IMetadataLibrary.class);

        DataTableMeta res = assertDoesNotThrowReturning(() -> factory.provideMetaData(PROBE_URI,
                S3ProbeProviderSupplier.PROBE_INFO, metadata));

        assertNotNull(res, "probe returns the configured successMeta");
        S3ProbeProvider p = S3ProbeProviderSupplier.LAST_PROVIDER.get();
        assertNotNull(p);
        assertSame(metadata, p.metadata(),
                "metadata stayed wired through to the provider on the success path");
    }


    @Test
    void provideUri_successDoesNotClearMetadata()
    {
        S3ProbeProviderSupplier.failNext = false;
        S3ProbeProviderSupplier.successDataTable = Mockito.mock(IDataTable.class);

        IMetadataLibrary metadata = Mockito.mock(IMetadataLibrary.class);

        IDataTable res = assertDoesNotThrowReturning(
                () -> factory.provide(PROBE_URI, S3ProbeProviderSupplier.PROBE_INFO, metadata));

        assertNotNull(res);
        S3ProbeProvider p = S3ProbeProviderSupplier.LAST_PROVIDER.get();
        assertNotNull(p);
        assertSame(metadata, p.metadata(),
                "F-A9: success path must leave the metadata wired through to the caller");
    }

    // ================== Helpers ==================


    private static DataTableMeta mockMeta()
    {
        DataTableColumnMeta cm = DataTableColumnMeta.builder().index(0).name("V1")
                .type(DataValueType.STRING).build();
        return DataTableMeta.builder().name("probe").setColumns(cm).rowCount(0).totalRowCount(0)
                .build();
    }

    private interface ThrowingSupplier<T>
    {

        T get() throws Exception;
    }

    private static <T> T assertDoesNotThrowReturning(ThrowingSupplier<T> aRunnable)
    {
        try
        {
            return aRunnable.get();
        }
        catch (Exception ex)
        {
            throw new AssertionError("expected no exception but got " + ex.getClass().getName()
                    + ": " + ex.getMessage(), ex);
        }
    }
}
