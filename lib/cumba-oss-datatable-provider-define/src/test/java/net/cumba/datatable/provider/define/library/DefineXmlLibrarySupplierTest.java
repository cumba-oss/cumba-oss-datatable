package net.cumba.datatable.provider.define.library;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.util.List;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.library.ILibraryProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DefineXmlLibrarySupplierTest
{

    private final DefineXmlLibrarySupplier supplier = new DefineXmlLibrarySupplier();

    // ==================== FileInfo Constant ====================

    @Test
    void testFIDefineXmlConstant()
    {
        FileInfo fi = DefineXmlLibrarySupplier.FI_DEFINE_XML;
        assertNotNull(fi);
        assertEquals("xml", fi.getFileExtension());
        assertNotNull(fi.getUuid());
    }


    @Test
    void testFIDefineXmlPattern()
    {
        FileInfo fi = DefineXmlLibrarySupplier.FI_DEFINE_XML;
        assertEquals("(?i)define\\.xml", fi.getFileNamePattern());
    }

    // ==================== getSupportedFileInfos ====================


    @Test
    void testGetSupportedFileInfos()
    {
        List<FileInfo> infos = supplier.getSupportedFileInfos();
        assertEquals(1, infos.size());
        assertSame(DefineXmlLibrarySupplier.FI_DEFINE_XML, infos.get(0));
    }

    // ==================== canProvideFor with FileInfo ====================


    @Test
    void testCanProvideForWithMatchingFileInfo()
    {
        URI uri = URI.create("file:///study/define.xml");
        assertTrue(supplier.canProvideFor(uri, DefineXmlLibrarySupplier.FI_DEFINE_XML));
    }


    @Test
    void testCanProvideForWithDifferentFileInfo()
    {
        URI uri = URI.create("file:///study/define.xml");
        FileInfo otherFi = FileInfo.createFor("csv", "CSV");

        assertFalse(supplier.canProvideFor(uri, otherFi));
    }

    // ==================== canProvideFor without FileInfo ====================


    /**
     * canProvideFor(uri, null) accepts URIs whose final path segment is "define.xml"
     * (case-insensitive, in any folder) and rejects everything else, including opaque URIs and
     * paths where "define.xml" is not the final segment.
     */
    @ParameterizedTest
    @CsvSource(
    {
            "file:///study/define.xml,true",
            // uses toLowerCase(), so this should match
            "file:///study/DEFINE.XML,true", "file:///study/data.csv,false",
            "file:///root/study/define.xml,true",
            // opaque URI has no path
            "mailto:test@example.com,false", "file:///study/define.xml/something,false"
    })
    void testCanProvideForByPath(String uriString, boolean expected)
    {
        URI uri = URI.create(uriString);
        assertEquals(expected, supplier.canProvideFor(uri, null));
    }

    // ==================== getProvider ====================


    @Test
    void testGetProviderForDefineXml()
    {
        URI uri = URI.create("file:///study/define.xml");
        ILibraryProvider provider = supplier.getProvider(uri, null);

        assertNotNull(provider);
        assertTrue(provider instanceof DefineXmlLibraryProvider);
    }


    @Test
    void testGetProviderForNonDefineXml()
    {
        URI uri = URI.create("file:///study/data.csv");
        ILibraryProvider provider = supplier.getProvider(uri, null);

        assertNull(provider);
    }


    @Test
    void testGetProviderWithMatchingFileInfo()
    {
        URI uri = URI.create("file:///study/something.xml");
        ILibraryProvider provider = supplier.getProvider(uri,
                DefineXmlLibrarySupplier.FI_DEFINE_XML);

        assertNotNull(provider);
    }

    // ==================== default method delegation ====================


    @Test
    void testCanProvideForDefaultDelegation()
    {
        URI uri = URI.create("file:///study/define.xml");
        assertEquals(supplier.canProvideFor(uri, null), supplier.canProvideFor(uri));
    }


    @Test
    void testGetProviderDefaultDelegation()
    {
        URI uri = URI.create("file:///study/define.xml");
        ILibraryProvider p1 = supplier.getProvider(uri, null);
        ILibraryProvider p2 = supplier.getProvider(uri);
        assertNotNull(p1);
        assertNotNull(p2);
    }
}
