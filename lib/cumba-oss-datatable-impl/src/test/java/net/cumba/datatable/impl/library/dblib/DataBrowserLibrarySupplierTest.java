package net.cumba.datatable.impl.library.dblib;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.util.List;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.library.ILibraryProvider;
import org.junit.jupiter.api.Test;

class DataBrowserLibrarySupplierTest
{

    private final DataBrowserLibrarySupplier supplier = new DataBrowserLibrarySupplier();

    // ==================== FileInfo Constants ====================

    @Test
    void testFIDblibConstant()
    {
        FileInfo fi = DataBrowserLibrarySupplier.FI_DBLIB;
        assertNotNull(fi);
        assertEquals("dblib", fi.getFileExtension());
        assertNotNull(fi.getUuid());
    }


    @Test
    void testFISListImmutable()
    {
        List<FileInfo> fis = DataBrowserLibrarySupplier.FIS;
        assertThrows(UnsupportedOperationException.class, () ->
        {
            fis.add(DataBrowserLibrarySupplier.FI_DBLIB);
        });
    }

    // ==================== getSupportedFileInfos ====================


    @Test
    void testGetSupportedFileInfos()
    {
        List<FileInfo> infos = supplier.getSupportedFileInfos();
        assertEquals(1, infos.size());
        assertSame(DataBrowserLibrarySupplier.FI_DBLIB, infos.get(0));
    }

    // ==================== canProvideFor ====================


    @Test
    void testCanProvideForWithMatchingFileInfo()
    {
        URI uri = URI.create("file:///lib/mylib.dblib");
        assertTrue(supplier.canProvideFor(uri, DataBrowserLibrarySupplier.FI_DBLIB));
    }


    @Test
    void testCanProvideForWithDifferentFileInfo()
    {
        URI uri = URI.create("file:///lib/mylib.dblib");
        FileInfo otherFi = FileInfo.createFor("csv", "CSV");
        assertFalse(supplier.canProvideFor(uri, otherFi));
    }


    @Test
    void testCanProvideForByExtension()
    {
        URI uri = URI.create("file:///lib/mylib.dblib");
        assertTrue(supplier.canProvideFor(uri, null));
    }


    @Test
    void testCanProvideForByExtensionCaseInsensitive()
    {
        URI uri = URI.create("file:///lib/mylib.DBLIB");
        assertTrue(supplier.canProvideFor(uri, null));
    }


    @Test
    void testCanProvideForNonMatchingExtension()
    {
        URI uri = URI.create("file:///lib/mylib.json");
        assertFalse(supplier.canProvideFor(uri, null));
    }

    // ==================== getProvider ====================


    @Test
    void testGetProviderForDblib()
    {
        URI uri = URI.create("file:///lib/mylib.dblib");
        ILibraryProvider provider = supplier.getProvider(uri, null);

        assertNotNull(provider);
        assertInstanceOf(DataBrowserLibraryProvider.class, provider);
    }


    @Test
    void testGetProviderForNonDblib()
    {
        URI uri = URI.create("file:///lib/mylib.json");
        ILibraryProvider provider = supplier.getProvider(uri, null);

        assertNull(provider);
    }


    @Test
    void testGetProviderWithMatchingFileInfo()
    {
        URI uri = URI.create("file:///lib/something");
        ILibraryProvider provider = supplier.getProvider(uri, DataBrowserLibrarySupplier.FI_DBLIB);

        assertNotNull(provider);
    }
}
