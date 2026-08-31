package net.cumba.datatable.provider.sas.xpt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.provider.IDataTableProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class XptProviderSupplierTest
{

    private XptProviderSupplier supplier;

    @BeforeEach
    void setUp()
    {
        supplier = new XptProviderSupplier();
    }


    @Test
    void testFileInfoConstants()
    {
        assertNotNull(XptProviderSupplier.FI_XPT);
        assertEquals("xpt", XptProviderSupplier.FI_XPT.getFileExtension());
    }


    @Test
    void testFileInfoUuid()
    {
        assertEquals("a183d9aa-bd69-4f9a-baa2-e4648d140b86", XptProviderSupplier.FI_XPT.getUuid());
    }


    @Test
    void testFileInfoDescription()
    {
        String desc = XptProviderSupplier.FI_XPT.getDescription();
        assertNotNull(desc);
        assertTrue(desc.contains("Xport"));
    }


    @Test
    void testFisListIsUnmodifiable()
    {
        List<FileInfo> fis = XptProviderSupplier.FIS;
        assertNotNull(fis);
        assertEquals(1, fis.size());
        assertEquals(XptProviderSupplier.FI_XPT, fis.get(0));
    }


    @Test
    void testGetSupportedFileInfos()
    {
        List<FileInfo> supported = supplier.getSupportedFileInfos();
        assertNotNull(supported);
        assertEquals(1, supported.size());
        assertTrue(supported.contains(XptProviderSupplier.FI_XPT));
    }


    @Test
    void testCanProvideForWithMatchingFileInfo()
    {
        URI uri = URI.create("file:///data/test.xpt");
        assertTrue(supplier.canProvideFor(uri, XptProviderSupplier.FI_XPT));
    }


    @Test
    void testCanProvideForWithNonMatchingFileInfo()
    {
        URI uri = URI.create("file:///data/test.csv");
        FileInfo csvInfo = FileInfo.createFor("csv", "CSV Files");
        assertFalse(supplier.canProvideFor(uri, csvInfo));
    }


    @Test
    void testCanProvideForByExtension()
    {
        URI uri = URI.create("file:///data/test.xpt");
        assertTrue(supplier.canProvideFor(uri, null));
    }


    @Test
    void testCanProvideForByExtensionCaseInsensitive()
    {
        URI uri = URI.create("file:///data/test.XPT");
        assertTrue(supplier.canProvideFor(uri, null));
    }


    @Test
    void testCanProvideForByExtensionWrongExt()
    {
        URI uri = URI.create("file:///data/test.csv");
        assertFalse(supplier.canProvideFor(uri, null));
    }


    @Test
    void testCanProvideForNoExtension()
    {
        URI uri = URI.create("file:///data/testfile");
        assertFalse(supplier.canProvideFor(uri, null));
    }


    @Test
    void testGetProviderMatchingUri()
    {
        URI uri = URI.create("file:///data/test.xpt");
        IDataTableProvider provider = supplier.getProvider(uri, null);
        assertNotNull(provider);
        assertTrue(provider instanceof XptTableProvider);
    }


    @Test
    void testGetProviderMatchingFileInfo()
    {
        URI uri = URI.create("file:///data/test.xpt");
        IDataTableProvider provider = supplier.getProvider(uri, XptProviderSupplier.FI_XPT);
        assertNotNull(provider);
        assertTrue(provider instanceof XptTableProvider);
    }


    @Test
    void testGetProviderNonMatching()
    {
        URI uri = URI.create("file:///data/test.csv");
        IDataTableProvider provider = supplier.getProvider(uri, null);
        assertNull(provider);
    }
}
