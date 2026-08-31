package net.cumba.datatable.provider.parquet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.provider.IDataTableProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link ParquetProviderSupplier}.
 */
class ParquetProviderSupplierTest
{

    private ParquetProviderSupplier supplier;

    @BeforeEach
    void setUp()
    {
        supplier = new ParquetProviderSupplier();
    }


    @Test
    void testSupportedFileInfosNotEmpty()
    {
        List<FileInfo> infos = supplier.getSupportedFileInfos();
        assertNotNull(infos);
        assertFalse(infos.isEmpty());
    }


    @Test
    void testSupportedFileInfosContainsParquet()
    {
        List<FileInfo> infos = supplier.getSupportedFileInfos();
        assertTrue(infos.contains(ParquetProviderSupplier.FI_PARQUET));
    }


    @Test
    void testSupportedFileInfosHasExactlyOneEntry()
    {
        List<FileInfo> infos = supplier.getSupportedFileInfos();
        assertEquals(1, infos.size());
    }


    @Test
    void testFiParquetConstantNotNull()
    {
        assertNotNull(ParquetProviderSupplier.FI_PARQUET);
    }


    @Test
    void testFisListIsUnmodifiable()
    {
        List<FileInfo> fis = ParquetProviderSupplier.FIS;
        assertNotNull(fis);

        try
        {
            fis.add(FileInfo.createFor("csv", "CSV"));
            // Should not reach here
            assertTrue(false, "Expected UnsupportedOperationException");
        }
        catch (UnsupportedOperationException _)
        {
            // expected
        }
    }


    @Test
    void testGetProviderReturnsParquet2TableProviderForParquetFile()
    {
        URI uri = URI.create("file:///data/test.parquet");
        IDataTableProvider provider = supplier.getProvider(uri, ParquetProviderSupplier.FI_PARQUET);
        assertNotNull(provider);
        assertInstanceOf(ParquetTableProvider.class, provider);
    }


    @Test
    void testGetProviderReturnsNullForUnknownFileInfo()
    {
        URI uri = URI.create("file:///data/test.csv");
        FileInfo csvInfo = FileInfo.createFor("csv", "CSV");
        IDataTableProvider provider = supplier.getProvider(uri, csvInfo);
        assertNull(provider);
    }


    @Test
    void testGetProviderWithNullFileInfoRejectsNonParquetExtension()
    {
        URI uri = URI.create("file:///data/test.csv");
        IDataTableProvider provider = supplier.getProvider(uri, null);
        assertNull(provider);
    }


    @ParameterizedTest
    @ValueSource(strings =
    {
            "file:///data/test.parquet", "file:///data/test.PARQUET", "file:///data/test.Parquet"
    })
    void testGetProviderWithNullFileInfoMatchesByExtension(String uriString)
    {
        URI uri = URI.create(uriString);
        IDataTableProvider provider = supplier.getProvider(uri, null);
        assertNotNull(provider);
        assertInstanceOf(ParquetTableProvider.class, provider);
    }
}
