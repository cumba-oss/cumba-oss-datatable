package net.cumba.datatable.provider.csv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.provider.IDataTableProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CsvProviderSupplier}.
 */
class CsvProviderSupplierTest
{

    private CsvProviderSupplier supplier;

    @BeforeEach
    void setUp()
    {
        supplier = new CsvProviderSupplier();
    }


    @Test
    void testFileInfoConstant()
    {
        assertNotNull(CsvProviderSupplier.FI_CSV);
        assertEquals("csv", CsvProviderSupplier.FI_CSV.getFileExtension());
    }


    @Test
    void testFISListContainsCsv()
    {
        List<FileInfo> fis = CsvProviderSupplier.FIS;
        assertNotNull(fis);
        assertEquals(1, fis.size());
        assertTrue(fis.contains(CsvProviderSupplier.FI_CSV));
    }


    @Test
    void testFISListIsUnmodifiable()
    {
        assertThrows(UnsupportedOperationException.class,
                () -> CsvProviderSupplier.FIS.add(FileInfo.createFor("txt", "Text")));
    }


    @Test
    void testGetProviderForCsvFormat() throws Exception
    {
        URI uri = new URI("file:///test.csv");
        IDataTableProvider provider = supplier.getProvider(uri, CsvProviderSupplier.FI_CSV);
        assertNotNull(provider);
        assertInstanceOf(CsvTableProvider.class, provider);
    }


    @Test
    void testGetProviderForUnsupportedFormat() throws Exception
    {
        URI uri = new URI("file:///test.json");
        FileInfo jsonInfo = FileInfo.createFor("json", "JSON");
        IDataTableProvider provider = supplier.getProvider(uri, jsonInfo);
        assertNull(provider);
    }


    @Test
    void testGetProviderReturnsNewInstanceEachTime() throws Exception
    {
        URI uri = new URI("file:///test.csv");
        IDataTableProvider p1 = supplier.getProvider(uri, CsvProviderSupplier.FI_CSV);
        IDataTableProvider p2 = supplier.getProvider(uri, CsvProviderSupplier.FI_CSV);
        assertNotNull(p1);
        assertNotNull(p2);
        assertNotSame(p1, p2);
    }

}
