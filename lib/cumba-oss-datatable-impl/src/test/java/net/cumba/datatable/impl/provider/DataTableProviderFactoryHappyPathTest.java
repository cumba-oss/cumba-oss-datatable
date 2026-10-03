package net.cumba.datatable.impl.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.List;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.io.Property;
import net.cumba.datatable.library.ILibraryMember;
import net.cumba.datatable.metadata.IMetadataLibrary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Happy-path tests for {@link DataTableProviderFactory} through the {@link S3ProbeProviderSupplier}
 * SPI fixture: the provider is initialised with the caller's metadata and listener before it is
 * asked to provide, results are handed back untouched, and the lazily-created singleton survives a
 * reflective reset.
 */
class DataTableProviderFactoryHappyPathTest
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


    @Test
    void singletonSurvivesReflectiveResetAndIsStable() throws Exception
    {
        Field f = DataTableProviderFactory.class.getDeclaredField("instance");
        f.setAccessible(true);
        Object before = f.get(null);
        try
        {
            f.set(null, null);
            DataTableProviderFactory first = DataTableProviderFactory.getFactory();
            assertNotNull(first, "first call after reset must create the instance");
            assertSame(first, DataTableProviderFactory.getFactory(),
                    "second call must return the same instance");
        }
        finally
        {
            f.set(null, before);
        }
    }


    @Test
    void provideByUriInitialisesTheProviderAndReturnsItsTable() throws IOException
    {
        IDataTable table = mock(IDataTable.class);
        IMetadataLibrary meta = mock(IMetadataLibrary.class);
        S3ProbeProviderSupplier.successDataTable = table;

        IDataTable res = factory.provide(PROBE_URI, null, meta);

        assertSame(table, res);
        S3ProbeProviderSupplier.S3ProbeProvider p = S3ProbeProviderSupplier.LAST_PROVIDER.get();
        assertNotNull(p);
        assertSame(meta, p.metadata(), "metadata must be set before provide");
    }


    @Test
    void provideByMemberInitialisesTheProviderAndReturnsItsTable() throws IOException
    {
        IDataTable table = mock(IDataTable.class);
        IMetadataLibrary meta = mock(IMetadataLibrary.class);
        S3ProbeProviderSupplier.successDataTable = table;

        ILibraryMember member = mock(ILibraryMember.class);
        when(member.getUri()).thenReturn(PROBE_URI);
        when(member.getFileInfo()).thenReturn(S3ProbeProviderSupplier.PROBE_INFO);

        IDataTable res = factory.provide(member, null, meta);

        assertSame(table, res);
        S3ProbeProviderSupplier.S3ProbeProvider p = S3ProbeProviderSupplier.LAST_PROVIDER.get();
        assertNotNull(p);
        assertSame(meta, p.metadata());
    }


    @Test
    void provideMetaDataSetsMetadataAndReturnsTheMeta() throws IOException
    {
        DataTableMeta meta = DataTableMeta.builder().name("probe").setColumns().rowCount(0)
                .totalRowCount(0).build();
        IMetadataLibrary lib = mock(IMetadataLibrary.class);
        S3ProbeProviderSupplier.successMeta = meta;

        DataTableMeta res = factory.provideMetaData(PROBE_URI, null, lib,
                java.util.Collections.emptyMap());

        assertSame(meta, res);
        S3ProbeProviderSupplier.S3ProbeProvider p = S3ProbeProviderSupplier.LAST_PROVIDER.get();
        assertNotNull(p);
        assertSame(lib, p.metadata(), "provideMetaData must hand the metadata to the provider");
    }


    @Test
    void providerPropertiesAreHandedThroughForUriAndMember()
    {
        Property prop = mock(Property.class);
        S3ProbeProviderSupplier.providerProperties = List.of(prop);

        List<Property> byUri = factory.getProviderProperties(PROBE_URI, null);
        assertEquals(List.of(prop), byUri);

        ILibraryMember member = mock(ILibraryMember.class);
        when(member.getUri()).thenReturn(PROBE_URI);
        when(member.getFileInfo()).thenReturn(S3ProbeProviderSupplier.PROBE_INFO);
        List<Property> byMember = factory.getProviderProperties(member, null);
        assertEquals(List.of(prop), byMember);
    }


    @Test
    void resolveFormatFromFileNameFindsTheProbeInfo()
    {
        assertSame(S3ProbeProviderSupplier.PROBE_INFO,
                factory.resolveFormatFromFileName("x.s3a9probe"));
    }
}
