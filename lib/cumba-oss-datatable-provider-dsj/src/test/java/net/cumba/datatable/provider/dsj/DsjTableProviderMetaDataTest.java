package net.cumba.datatable.provider.dsj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link DsjTableProvider#provideMetaData(URI, FileInfo)} (dsj2 variant). Covers all
 * three supported transports: JSON, NDJSON and DSJC.
 */
class DsjTableProviderMetaDataTest
{

    private DsjTableProvider provider;

    @BeforeEach
    void setUp()
    {
        provider = new DsjTableProvider();
    }


    /**
     * Copies a classpath fixture to a temp file. When {@code compress} is true, the fixture is
     * zlib-compressed on the way out (yielding the DSJC on-disk shape).
     */
    private URI materialiseFixture(String fixtureName, String suffix, boolean compress)
        throws IOException
    {
        File tmp = File.createTempFile("dsj2metatest", suffix);
        tmp.deleteOnExit();
        try (InputStream in = getClass().getResourceAsStream("/fixtures/dsj2/" + fixtureName))
        {
            assertNotNull(in, "fixture not found: " + fixtureName);
            if (compress)
            {
                try (Deflater df = new Deflater(Deflater.BEST_SPEED);
                        DeflaterOutputStream dos = new DeflaterOutputStream(
                                Files.newOutputStream(tmp.toPath()), df))
                {
                    in.transferTo(dos);
                }
            }
            else
            {
                Files.copy(in, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return tmp.toURI();
    }


    private void assertMetaMatchesFull(URI uri, FileInfo fileInfo) throws Exception
    {
        DataTableMeta metaOnly = provider.provideMetaData(uri, fileInfo);
        IDataTable full = provider.provide(uri, fileInfo);
        DataTableMeta fullMeta = full.getMetaData();

        assertNotNull(metaOnly);
        assertEquals(fullMeta.getName(), metaOnly.getName());
        assertEquals(fullMeta.getLabel(), metaOnly.getLabel());
        assertEquals(fullMeta.getColumnCount(), metaOnly.getColumnCount());
        for (int i = 0; i < fullMeta.getColumnCount(); i++)
        {
            DataTableColumnMeta expected = fullMeta.getColumn(i);
            DataTableColumnMeta actual = metaOnly.getColumn(i);
            assertEquals(expected.getName(), actual.getName(), "column name[" + i + "]");
            assertEquals(expected.getType(), actual.getType(), "column type[" + i + "]");
            assertEquals(expected.getLabel(), actual.getLabel(), "column label[" + i + "]");
            assertEquals(expected.getLength(), actual.getLength(), "column length[" + i + "]");
        }
    }


    @Test
    void provideMetaData_jsonMatchesProvide() throws Exception
    {
        URI uri = materialiseFixture("meta-test-3rows.json", ".json", false);
        assertMetaMatchesFull(uri, DsjProviderSupplier.FI_DSJ_JSON);
    }


    @Test
    void provideMetaData_ndjsonMatchesProvide() throws Exception
    {
        URI uri = materialiseFixture("meta-test-3rows.ndjson", ".ndjson", false);
        assertMetaMatchesFull(uri, DsjProviderSupplier.FI_DSJ_NDJSON);
    }


    @Test
    void provideMetaData_dsjcMatchesProvide() throws Exception
    {
        URI uri = materialiseFixture("meta-test-3rows.json", ".dsjc", true);
        assertMetaMatchesFull(uri, DsjProviderSupplier.FI_DSJ_DSJC);
    }


    @Test
    void provideMetaData_populatesRowCountFromHeader() throws Exception
    {
        URI uri = materialiseFixture("meta-test-7rows.json", ".json", false);

        DataTableMeta meta = provider.provideMetaData(uri, DsjProviderSupplier.FI_DSJ_JSON);

        // dsj2 populates rowCount/totalRowCount from the 'records' header attribute.
        assertEquals(7, meta.getRowCount());
        assertEquals(7, meta.getTotalRowCount());
    }


    @Test
    void provideMetaData_columnTypes() throws Exception
    {
        URI uri = materialiseFixture("meta-test-2rows.json", ".json", false);

        DataTableMeta meta = provider.provideMetaData(uri, DsjProviderSupplier.FI_DSJ_JSON);

        assertEquals(2, meta.getColumnCount());
        assertEquals(DataValueType.STRING, meta.getColumn(0).getType());
        assertEquals(DataValueType.DOUBLE, meta.getColumn(1).getType());
    }


    @Test
    void provideMetaData_invalidUriThrows()
    {
        URI bad = URI.create("file:///nonexistent_" + System.nanoTime() + ".json");
        assertThrows(IOException.class,
                () -> provider.provideMetaData(bad, DsjProviderSupplier.FI_DSJ_JSON));
    }

}
