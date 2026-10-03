package net.cumba.datatable.impl.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.io.Property;
import net.cumba.datatable.library.ILibraryMember;
import net.cumba.datatable.metadata.IMetadataLibrary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Mutation-hardening tests for {@link DataTableProviderFactory}: the member-based metadata probing,
 * the suppressed-exception aggregation, the provider-property FileInfo fallback, and the
 * double-checked singleton's inner return.
 */
class DataTableProviderFactoryMutationTest
{

    private static final URI PROBE_URI = URI.create("file:///data/table.s3a9probe");

    private final DataTableProviderFactory factory = DataTableProviderFactory.getFactory();

    @BeforeEach
    void setUp()
    {
        S3ProbeProviderSupplier.reset();
    }


    @AfterEach
    void tearDown()
    {
        S3ProbeProviderSupplier.reset();
    }

    // ==================== provideMetaData(member, ...) ====================


    @Test
    void provideMetaDataByMemberSetsMetadataAndReturnsTheProviderResult() throws IOException
    {
        DataTableMeta meta = DataTableMeta.builder().name("probe").setColumns().rowCount(0)
                .totalRowCount(0).build();
        IMetadataLibrary lib = mock(IMetadataLibrary.class);
        S3ProbeProviderSupplier.successMeta = meta;

        ILibraryMember member = mock(ILibraryMember.class);
        when(member.getUri()).thenReturn(PROBE_URI);
        when(member.getFileInfo()).thenReturn(S3ProbeProviderSupplier.PROBE_INFO);

        DataTableMeta res = factory.provideMetaData(member, null, lib, Collections.emptyMap());

        assertSame(meta, res);
        S3ProbeProviderSupplier.S3ProbeProvider p = S3ProbeProviderSupplier.LAST_PROVIDER.get();
        assertNotNull(p);
        assertSame(lib, p.metadata(), "the metadata library must reach the provider");
    }

    // ==================== provide(member, fileInfo, initializer) ====================


    @Test
    void provideWithInitializerPredicateReturnsTheProviderResult() throws IOException
    {
        IDataTable table = mock(IDataTable.class);
        S3ProbeProviderSupplier.successDataTable = table;

        ILibraryMember member = mock(ILibraryMember.class);
        when(member.getUri()).thenReturn(PROBE_URI);
        when(member.getFileInfo()).thenReturn(S3ProbeProviderSupplier.PROBE_INFO);

        IDataTable res = factory.provide(member, null, _ -> true);

        assertSame(table, res);
    }

    // ==================== recordSuppressedException ====================


    private static Throwable record(Throwable aPrimary, Throwable aNew) throws Exception
    {
        Method m = DataTableProviderFactory.class.getDeclaredMethod("recordSuppressedException",
                Throwable.class, Throwable.class);
        m.setAccessible(true);
        try
        {
            return (Throwable) m.invoke(null, aPrimary, aNew);
        }
        catch (InvocationTargetException ex)
        {
            throw (Exception) ex.getCause();
        }
    }


    @Test
    void firstFailureBecomesThePrimary() throws Exception
    {
        IOException first = new IOException("first");
        assertSame(first, record(null, first));
        assertEquals(0, first.getSuppressed().length);
    }


    @Test
    void subsequentFailuresAreSuppressedUnderThePrimary() throws Exception
    {
        IOException first = new IOException("first");
        IOException second = new IOException("second");

        Throwable res = record(first, second);

        assertSame(first, res, "the primary must stay the primary");
        assertEquals(1, first.getSuppressed().length);
        assertSame(second, first.getSuppressed()[0]);
    }


    @Test
    void theSameInstanceIsNeverSelfSuppressed() throws Exception
    {
        IOException only = new IOException("only");
        assertSame(only, record(only, only));
        assertEquals(0, only.getSuppressed().length);
    }

    // ==================== getProviderProperties(member, fileInfo) ====================


    @Test
    void memberFileInfoIsTheFallbackWhenNoneIsPassed()
    {
        Property prop = Property.forString("l5prop", "desc", "x");
        S3ProbeProviderSupplier.providerProperties = List.of(prop);

        // The member's URI alone does not resolve to any provider — only the member's FileInfo
        // identifies the probe supplier, so the fallback must actually be taken.
        ILibraryMember member = mock(ILibraryMember.class);
        when(member.getUri()).thenReturn(URI.create("file:///data/table.unknown"));
        when(member.getFileInfo()).thenReturn(S3ProbeProviderSupplier.PROBE_INFO);

        List<Property> props = factory.getProviderProperties(member, null);

        assertEquals(List.of(prop), props);
    }

    // ==================== singleton: the inner double-check ====================


    // [LockOnNonEnclosingClassLiteral] suppressed: holding DataTableProviderFactory's OWN
    // monitor is the point of this test -- getFactory() synchronizes on that class literal, and
    // the only way to park the main thread inside its double-checked path is to take the very
    // same lock from another thread. Any other monitor would make the test vacuous.
    @SuppressWarnings("LockOnNonEnclosingClassLiteral")
    @Test
    void innerDoubleCheckReturnsTheInstanceInstalledWhileWaitingForTheLock() throws Exception
    {
        Field f = DataTableProviderFactory.class.getDeclaredField("instance");
        f.setAccessible(true);
        DataTableProviderFactory original = DataTableProviderFactory.getFactory();
        try
        {
            f.set(null, null);
            CountDownLatch lockHeld = new CountDownLatch(1);
            Thread mainThread = Thread.currentThread();
            AtomicReference<Exception> error = new AtomicReference<>();
            Thread other = new Thread(() ->
            {
                synchronized (DataTableProviderFactory.class)
                {
                    lockHeld.countDown();
                    try
                    {
                        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                        while (mainThread.getState() != Thread.State.BLOCKED
                                && System.nanoTime() < deadline)
                        {
                            Thread.onSpinWait();
                        }
                        f.set(null, original);
                    }
                    catch (Exception ex)
                    {
                        error.set(ex);
                    }
                }
            });
            other.start();
            lockHeld.await();

            DataTableProviderFactory res = DataTableProviderFactory.getFactory();

            other.join(TimeUnit.SECONDS.toMillis(10));
            assertNull(error.get());
            assertSame(original, res,
                    "the inner double-check must hand back the instance another thread "
                            + "installed while this one was waiting for the lock");
        }
        finally
        {
            f.set(null, original);
        }
    }
}
