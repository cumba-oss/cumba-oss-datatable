package net.cumba.datatable.impl.provider;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.CachedDataTableColumn;
import net.cumba.datatable.impl.ColumnCachedDataTable;
import net.cumba.datatable.impl.library.dblib.beans.DataBrowserLibraryBean;
import net.cumba.datatable.impl.metadata.dblib.DataBrowserMetadataLibrary;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.io.Property;
import net.cumba.datatable.library.IDataTableLibrary;
import net.cumba.datatable.library.ILibraryMember;
import net.cumba.datatable.metadata.IMetadataLibrary;
import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pins that every public {@link DataTableProviderFactory} entry point that takes provider
 * properties hands the caller's map to the provider: {@code provide} and {@code provideMetaData},
 * each for a URI and for a library member. Dropping the map at any one of the four sites reds
 * exactly one test here.
 *
 * <p>
 * The {@link S3ProbeProviderSupplier}'s provider records the map its {@code Map}-carrying overloads
 * receive ({@link S3ProbeProviderSupplier#LAST_PROPERTIES}).
 * </p>
 */
class DataTableProviderFactoryPropertiesForwardingTest
{

    private static final URI PROBE_URI = URI.create("file:///tmp/forwarding.s3a9probe");

    private static final Map<Property, String> PROPERTIES = Map
            .of(Property.forString("probe.key", "a probe property", "default"), "chosen");

    private final DataTableProviderFactory factory = DataTableProviderFactory.getFactory();

    @BeforeEach
    void resetProbe()
    {
        S3ProbeProviderSupplier.reset();
        S3ProbeProviderSupplier.successDataTable = table();
        S3ProbeProviderSupplier.successMeta = meta();
    }


    @AfterEach
    void resetAfter()
    {
        S3ProbeProviderSupplier.reset();
    }


    @Test
    void provideUri_forwardsTheProperties() throws IOException
    {
        assertNotNull(factory.provide(PROBE_URI, S3ProbeProviderSupplier.PROBE_INFO,
                metadataLibrary(), PROPERTIES));
        assertSame(PROPERTIES, S3ProbeProviderSupplier.LAST_PROPERTIES.get());
    }


    @Test
    void provideMember_forwardsTheProperties() throws IOException
    {
        assertNotNull(factory.provide(new ProbeMember(), S3ProbeProviderSupplier.PROBE_INFO,
                metadataLibrary(), PROPERTIES));
        assertSame(PROPERTIES, S3ProbeProviderSupplier.LAST_PROPERTIES.get());
    }


    @Test
    void provideMetaDataUri_forwardsTheProperties() throws IOException
    {
        assertNotNull(factory.provideMetaData(PROBE_URI, S3ProbeProviderSupplier.PROBE_INFO,
                metadataLibrary(), PROPERTIES));
        assertSame(PROPERTIES, S3ProbeProviderSupplier.LAST_PROPERTIES.get());
    }


    @Test
    void provideMetaDataMember_forwardsTheProperties() throws IOException
    {
        assertNotNull(factory.provideMetaData(new ProbeMember(), S3ProbeProviderSupplier.PROBE_INFO,
                metadataLibrary(), PROPERTIES));
        assertSame(PROPERTIES, S3ProbeProviderSupplier.LAST_PROPERTIES.get());
    }

    // ================== Helpers ==================


    private static DataTableMeta meta()
    {
        DataTableColumnMeta cm = DataTableColumnMeta.builder().index(0).name("V1")
                .type(DataValueType.STRING).build();
        return DataTableMeta.builder().name("probe").setColumns(cm).rowCount(0).totalRowCount(0)
                .build();
    }


    private static IDataTable table()
    {
        CachedDataTableColumn col = new CachedDataTableColumn(0, DataValueType.STRING);
        col.complete();
        return new ColumnCachedDataTable(meta(), col);
    }


    private static IMetadataLibrary metadataLibrary()
    {
        return DataBrowserMetadataLibrary.from(DataBrowserLibraryBean.builder().name("md").build(),
                URI.create("file:///tmp/forwarding.dblib"));
    }

    /** A library member at the probe URI; nothing here reaches its library. */
    private static final class ProbeMember implements ILibraryMember
    {

        @Override
        public String getName()
        {
            return "PROBE";
        }


        @Override
        public String getLabel()
        {
            return "probe";
        }


        @Override
        public URI getUri()
        {
            return PROBE_URI;
        }


        @Override
        public FileInfo getFileInfo()
        {
            return S3ProbeProviderSupplier.PROBE_INFO;
        }


        @Override
        public IDataTableLibrary getLibrary()
        {
            throw new UnsupportedOperationException("the forwarding tests never reach a library");
        }
    }
}
