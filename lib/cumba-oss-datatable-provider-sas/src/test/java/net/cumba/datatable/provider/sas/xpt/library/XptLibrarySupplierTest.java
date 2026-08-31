package net.cumba.datatable.provider.sas.xpt.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.library.ILibraryProvider;
import net.cumba.datatable.provider.sas.xpt.XptProviderSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class XptLibrarySupplierTest
{

    private final XptLibrarySupplier supplier = new XptLibrarySupplier();

    // ==================== FileInfo ====================

    @Test
    void testFISContainsXptFileInfo()
    {
        assertEquals(1, XptLibrarySupplier.FIS.size());
        assertSame(XptProviderSupplier.FI_XPT, XptLibrarySupplier.FIS.get(0));
    }


    @Test
    void testGetSupportedFileInfos()
    {
        List<FileInfo> infos = supplier.getSupportedFileInfos();
        assertSame(XptLibrarySupplier.FIS, infos);
    }

    // ==================== canProvideFor with FileInfo ====================


    @Test
    void testCanProvideForWithMatchingFileInfo()
    {
        URI uri = URI.create("file:///data/test.xpt");
        assertTrue(supplier.canProvideFor(uri, XptProviderSupplier.FI_XPT));
    }


    @Test
    void testCanProvideForWithDifferentFileInfo()
    {
        URI uri = URI.create("file:///data/test.xpt");
        FileInfo other = FileInfo.createFor("csv", "CSV");
        assertFalse(supplier.canProvideFor(uri, other));
    }

    // ==================== canProvideFor by path ====================


    @Test
    void testCanProvideForXptPath()
    {
        URI uri = URI.create("file:///data/test.xpt");
        assertTrue(supplier.canProvideFor(uri, null));
    }


    @Test
    void testCanProvideForXptCaseInsensitive()
    {
        URI uri = URI.create("file:///data/test.XPT");
        assertTrue(supplier.canProvideFor(uri, null));
    }


    @ParameterizedTest
    @ValueSource(strings =
    {
            // non-xpt extension
            "file:///data/test.csv",
            // XPT with fragment = specific dataset, not a library
            "file:///data/test.xpt#DM",
            // null path (opaque URI)
            "mailto:test@example.com",
            // "textxpt" should NOT match (only ".xpt" should) — requires dot before extension
            "file:///data/textxpt"
    })
    void testCanProvideForReturnsFalse(String aUri)
    {
        URI uri = URI.create(aUri);
        assertFalse(supplier.canProvideFor(uri, null));
    }

    // ==================== getProvider ====================


    @Test
    void testGetProviderForXpt()
    {
        URI uri = URI.create("file:///data/test.xpt");
        ILibraryProvider provider = supplier.getProvider(uri, null);
        assertNotNull(provider);
        assertInstanceOf(XptLibraryProvider.class, provider);
    }


    @Test
    void testGetProviderForNonXpt()
    {
        URI uri = URI.create("file:///data/test.csv");
        ILibraryProvider provider = supplier.getProvider(uri, null);
        assertNull(provider);
    }


    @Test
    void testGetProviderForXptWithFragment()
    {
        URI uri = URI.create("file:///data/test.xpt#DM");
        ILibraryProvider provider = supplier.getProvider(uri, null);
        assertNull(provider);
    }
}
