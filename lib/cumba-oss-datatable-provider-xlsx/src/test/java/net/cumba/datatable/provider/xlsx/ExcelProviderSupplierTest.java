package net.cumba.datatable.provider.xlsx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertNotNull(ExcelProviderSupplier.FI_XLSX);
        assertEquals("xlsx", ExcelProviderSupplier.FI_XLSX.getFileExtension());
    }


    /**
     * Q37: reversed. This test was {@code testFISListContainsAllFormats} and asserted a two-entry
     * list containing {@code FI_XLS}, i.e. it pinned the advertisement of a format that could never
     * be read. OOXML is now the only format offered.
     */
    @Test
    void testFISListOffersOoxmlOnly()
    {
        List<FileInfo> fis = ExcelProviderSupplier.FIS;
        assertNotNull(fis);
        assertEquals(1, fis.size());
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


    /**
     * Q37: reversed. This test was {@code testGetProviderForXls} and asserted that a {@code .xls}
     * URI yields a provider — a provider that then threw on every read, because
     * {@code excel-streaming-reader} is OOXML-only. Legacy BIFF is no longer offered, so the
     * supplier must refuse it both by {@link FileInfo} and by extension sniffing.
     */
    @Test
    void testGetProviderRefusesLegacyXls() throws Exception
    {
        URI uri = new URI("file:///test.xls");
        FileInfo legacyXls = FileInfo.createFor("xls", "Microsoft Excel Spreadsheet");
        assertNull(supplier.getProvider(uri, legacyXls));
        assertNull(supplier.getProvider(uri, null));
        assertFalse(supplier.canProvideFor(uri));
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


    /**
     * Q37: retargeted. This guarded {@code FI_XLS}'s description against the trailing space found
     * by code review CR-XLSX-08; that constant is gone, so the guard moves to the one format that
     * is still advertised rather than being dropped with it.
     */
    @Test
    void testFiXlsxDescriptionHasNoTrailingSpace()
    {
        String desc = ExcelProviderSupplier.FI_XLSX.getDescription();
        assertNotNull(desc);
        assertEquals(desc.trim(), desc);
    }

}
