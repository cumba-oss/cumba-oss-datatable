package net.cumba.datatable.provider.dsj;

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
 * Tests for {@link DsjProviderSupplier}.
 */
class DsjProviderSupplierTest
{

    private DsjProviderSupplier supplier;

    @BeforeEach
    void setUp()
    {
        supplier = new DsjProviderSupplier();
    }


    @Test
    void testFileInfoConstants()
    {
        assertNotNull(DsjProviderSupplier.FI_DSJ_JSON);
        assertNotNull(DsjProviderSupplier.FI_DSJ_NDJSON);
        assertNotNull(DsjProviderSupplier.FI_DSJ_DSJC);

        assertEquals("json", DsjProviderSupplier.FI_DSJ_JSON.getFileExtension());
        assertEquals("ndjson", DsjProviderSupplier.FI_DSJ_NDJSON.getFileExtension());
        assertEquals("dsjc", DsjProviderSupplier.FI_DSJ_DSJC.getFileExtension());
    }


    @Test
    void testFISListIsUnmodifiable()
    {
        List<FileInfo> fis = DsjProviderSupplier.FIS;
        assertNotNull(fis);
        assertEquals(3, fis.size());

        assertThrows(UnsupportedOperationException.class,
                () -> fis.add(FileInfo.createFor("txt", "Text")));
    }


    @Test
    void testFISContainsAllFormats()
    {
        List<FileInfo> fis = DsjProviderSupplier.FIS;
        assertTrue(fis.contains(DsjProviderSupplier.FI_DSJ_JSON));
        assertTrue(fis.contains(DsjProviderSupplier.FI_DSJ_NDJSON));
        assertTrue(fis.contains(DsjProviderSupplier.FI_DSJ_DSJC));
    }


    @Test
    void testGetProviderForSupportedFormat() throws Exception
    {
        URI uri = new URI("file:///test.json");
        IDataTableProvider provider = supplier.getProvider(uri, DsjProviderSupplier.FI_DSJ_JSON);
        assertNotNull(provider);
        assertInstanceOf(DsjTableProvider.class, provider);
    }


    @Test
    void testGetProviderForNdjsonFormat() throws Exception
    {
        URI uri = new URI("file:///test.ndjson");
        IDataTableProvider provider = supplier.getProvider(uri, DsjProviderSupplier.FI_DSJ_NDJSON);
        assertNotNull(provider);
        assertInstanceOf(DsjTableProvider.class, provider);
    }


    @Test
    void testGetProviderForDsjcFormat() throws Exception
    {
        URI uri = new URI("file:///test.dsjc");
        IDataTableProvider provider = supplier.getProvider(uri, DsjProviderSupplier.FI_DSJ_DSJC);
        assertNotNull(provider);
        assertInstanceOf(DsjTableProvider.class, provider);
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
        URI uri = new URI("file:///test.json");
        IDataTableProvider provider1 = supplier.getProvider(uri, DsjProviderSupplier.FI_DSJ_JSON);
        IDataTableProvider provider2 = supplier.getProvider(uri, DsjProviderSupplier.FI_DSJ_JSON);
        assertNotNull(provider1);
        assertNotNull(provider2);
        assertNotSame(provider1, provider2);
    }

}
