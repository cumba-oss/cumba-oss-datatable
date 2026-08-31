package net.cumba.datatable.provider.xlsx;

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
 * Tests for {@link ExcelProviderSupplier}.
 */
class ExcelProviderSupplierTest
{

    private ExcelProviderSupplier supplier;

    @BeforeEach
    void setUp()
    {
        supplier = new ExcelProviderSupplier();
    }


    @Test
    void testFileInfoConstants()
    {
        assertNotNull(ExcelProviderSupplier.FI_XLS);
        assertNotNull(ExcelProviderSupplier.FI_XLSX);
        assertEquals("xls", ExcelProviderSupplier.FI_XLS.getFileExtension());
        assertEquals("xlsx", ExcelProviderSupplier.FI_XLSX.getFileExtension());
    }


    @Test
    void testFISListContainsAllFormats()
    {
        List<FileInfo> fis = ExcelProviderSupplier.FIS;
        assertNotNull(fis);
        assertEquals(2, fis.size());
        assertTrue(fis.contains(ExcelProviderSupplier.FI_XLS));
        assertTrue(fis.contains(ExcelProviderSupplier.FI_XLSX));
    }


    @Test
    void testFISListIsUnmodifiable()
    {
        assertThrows(UnsupportedOperationException.class,
                () -> ExcelProviderSupplier.FIS.add(FileInfo.createFor("txt", "Text")));
    }


    @Test
    void testGetProviderForXlsx() throws Exception
    {
        URI uri = new URI("file:///test.xlsx");
        IDataTableProvider provider = supplier.getProvider(uri, ExcelProviderSupplier.FI_XLSX);
        assertNotNull(provider);
        assertInstanceOf(ExcelTableProvider.class, provider);
    }


    @Test
    void testGetProviderForXls() throws Exception
    {
        URI uri = new URI("file:///test.xls");
        IDataTableProvider provider = supplier.getProvider(uri, ExcelProviderSupplier.FI_XLS);
        assertNotNull(provider);
        assertInstanceOf(ExcelTableProvider.class, provider);
    }


    @Test
    void testGetProviderForUnsupportedFormat() throws Exception
    {
        URI uri = new URI("file:///test.csv");
        FileInfo csvInfo = FileInfo.createFor("csv", "CSV");
        IDataTableProvider provider = supplier.getProvider(uri, csvInfo);
        assertNull(provider);
    }


    @Test
    void testGetProviderReturnsNewInstanceEachTime() throws Exception
    {
        URI uri = new URI("file:///test.xlsx");
        IDataTableProvider p1 = supplier.getProvider(uri, ExcelProviderSupplier.FI_XLSX);
        IDataTableProvider p2 = supplier.getProvider(uri, ExcelProviderSupplier.FI_XLSX);
        assertNotNull(p1);
        assertNotNull(p2);
        assertNotSame(p1, p2);
    }


    @Test
    void testFiXlsDescriptionHasNoTrailingSpace()
    {
        String desc = ExcelProviderSupplier.FI_XLS.getDescription();
        assertNotNull(desc);
        assertEquals(desc.trim(), desc);
    }

}
