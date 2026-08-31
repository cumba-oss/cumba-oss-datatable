package net.cumba.datatable.provider.sas.sas7bdat;

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

class BdatProviderSupplierTest
{

    private BdatProviderSupplier supplier;

    @BeforeEach
    void setUp()
    {
        supplier = new BdatProviderSupplier();
    }


    @Test
    void testFileInfoConstants()
    {
        assertNotNull(BdatProviderSupplier.FI_BDAT);
        assertEquals("sas7bdat", BdatProviderSupplier.FI_BDAT.getFileExtension());
    }


    @Test
    void testFileInfoUuid()
    {
        assertEquals("98d09254-476c-4971-8b8a-8fc382d5a3db",
                BdatProviderSupplier.FI_BDAT.getUuid());
    }


    @Test
    void testFileInfoDescription()
    {
        String desc = BdatProviderSupplier.FI_BDAT.getDescription();
        assertNotNull(desc);
        assertTrue(desc.contains("SAS"));
    }


    @Test
    void testFisListIsUnmodifiable()
    {
        List<FileInfo> fis = BdatProviderSupplier.FIS;
        assertNotNull(fis);
        assertEquals(1, fis.size());
        assertEquals(BdatProviderSupplier.FI_BDAT, fis.get(0));
    }


    @Test
    void testGetSupportedFileInfos()
    {
        List<FileInfo> supported = supplier.getSupportedFileInfos();
        assertNotNull(supported);
        assertEquals(1, supported.size());
        assertTrue(supported.contains(BdatProviderSupplier.FI_BDAT));
    }


    @Test
    void testCanProvideForWithMatchingFileInfo()
    {
        URI uri = URI.create("file:///data/test.sas7bdat");
        assertTrue(supplier.canProvideFor(uri, BdatProviderSupplier.FI_BDAT));
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
        URI uri = URI.create("file:///data/test.sas7bdat");
        assertTrue(supplier.canProvideFor(uri, null));
    }


    @Test
    void testCanProvideForByExtensionCaseInsensitive()
    {
        URI uri = URI.create("file:///data/test.SAS7BDAT");
        assertTrue(supplier.canProvideFor(uri, null));
    }


    @Test
    void testCanProvideForByExtensionWrongExt()
    {
        URI uri = URI.create("file:///data/test.csv");
        assertFalse(supplier.canProvideFor(uri, null));
    }


    @Test
    void testGetProviderMatchingUri()
    {
        URI uri = URI.create("file:///data/test.sas7bdat");
        IDataTableProvider provider = supplier.getProvider(uri, null);
        assertNotNull(provider);
        assertTrue(provider instanceof BdatTableProvider);
    }


    @Test
    void testGetProviderNonMatching()
    {
        URI uri = URI.create("file:///data/test.csv");
        IDataTableProvider provider = supplier.getProvider(uri, null);
        assertNull(provider);
    }
}
