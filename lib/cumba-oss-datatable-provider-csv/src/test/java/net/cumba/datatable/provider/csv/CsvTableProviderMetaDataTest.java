package net.cumba.datatable.provider.csv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CsvTableProvider#provideMetaData(URI, net.cumba.datatable.io.FileInfo)}. Asserts
 * the metadata-only path agrees with full {@code provide(...).getMetaData()} on column names and
 * types, and that no row count is populated (CSV row count is only known after a full parse).
 */
class CsvTableProviderMetaDataTest
{

    private CsvTableProvider provider;

    @BeforeEach
    void setUp()
    {
        provider = new CsvTableProvider();
    }


    private URI writeTempCsv(String content) throws IOException
    {
        File tmpFile = File.createTempFile("csvmetatest", ".csv");
        tmpFile.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(tmpFile))
        {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return tmpFile.toURI();
    }


    @Test
    void provideMetaData_matchesProvideMeta() throws Exception
    {
        String csv = "NAME,AGE,SCORE\nAlice,30,95.5\nBob,25,87.3\nCarol,40,77.1\n";
        URI uri = writeTempCsv(csv);

        DataTableMeta metaOnly = provider.provideMetaData(uri, CsvProviderSupplier.FI_CSV);
        IDataTable full = provider.provide(uri, CsvProviderSupplier.FI_CSV);
        DataTableMeta fullMeta = full.getMetaData();

        assertNotNull(metaOnly);
        assertEquals(fullMeta.getColumnCount(), metaOnly.getColumnCount());
        for (int i = 0; i < fullMeta.getColumnCount(); i++)
        {
            DataTableColumnMeta expected = fullMeta.getColumn(i);
            DataTableColumnMeta actual = metaOnly.getColumn(i);
            assertEquals(expected.getName(), actual.getName(), "column name[" + i + "]");
            assertEquals(expected.getType(), actual.getType(), "column type[" + i + "]");
            assertEquals(expected.getLabel(), actual.getLabel(), "column label[" + i + "]");
        }
    }


    @Test
    void provideMetaData_inferredTypes() throws Exception
    {
        String csv = "NAME,AGE,SCORE\nAlice,30,95.5\nBob,25,87.3\n";
        URI uri = writeTempCsv(csv);

        DataTableMeta meta = provider.provideMetaData(uri, CsvProviderSupplier.FI_CSV);

        assertEquals(3, meta.getColumnCount());
        assertEquals("NAME", meta.getColumn(0).getName());
        assertEquals(DataValueType.STRING, meta.getColumn(0).getType());
        assertEquals("AGE", meta.getColumn(1).getName());
        assertEquals(DataValueType.DOUBLE, meta.getColumn(1).getType());
        assertEquals("SCORE", meta.getColumn(2).getName());
        assertEquals(DataValueType.DOUBLE, meta.getColumn(2).getType());
    }


    @Test
    void provideMetaData_rowCountIsZero() throws Exception
    {
        String csv = "NAME,VALUE\nA,1\nB,2\nC,3\nD,4\n";
        URI uri = writeTempCsv(csv);

        DataTableMeta meta = provider.provideMetaData(uri, CsvProviderSupplier.FI_CSV);

        // CSV provider does not populate rowCount in the metadata-only path.
        assertEquals(0, meta.getRowCount());
        assertEquals(0, meta.getTotalRowCount());
    }


    @Test
    void provideMetaData_semicolonDelimited() throws Exception
    {
        String csv = "NAME;VALUE;CODE\nX;1.0;A\nY;2.0;B\n";
        URI uri = writeTempCsv(csv);

        DataTableMeta meta = provider.provideMetaData(uri, CsvProviderSupplier.FI_CSV);

        assertEquals(3, meta.getColumnCount());
        assertEquals("NAME", meta.getColumn(0).getName());
        assertEquals("VALUE", meta.getColumn(1).getName());
        assertEquals("CODE", meta.getColumn(2).getName());
    }

}
