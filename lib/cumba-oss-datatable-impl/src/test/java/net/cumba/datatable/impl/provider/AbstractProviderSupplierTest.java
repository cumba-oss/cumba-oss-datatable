package net.cumba.datatable.impl.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.provider.IDataTableProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AbstractProviderSupplierTest
{

    // ==================== Test implementation ====================

    private static class TestProviderSupplier extends AbstractProviderSupplier
    {

        private TestProviderSupplier(List<FileInfo> aSupportedFiles)
        {
            super(aSupportedFiles);
        }


        @Override
        public IDataTableProvider getProvider(URI aUri, FileInfo aFileInfo)
        {
            return null;
        }
    }

    // ==================== Constructor ====================

    @Test
    void testConstructorWithSingleFileInfo()
    {
        FileInfo info = FileInfo.createFor("csv", "CSV");
        TestProviderSupplier supplier = new TestProviderSupplier(List.of(info));

        assertEquals(1, supplier.getSupportedFileInfos().size());
    }


    @Test
    void testConstructorWithMultipleFileInfos()
    {
        FileInfo csv = FileInfo.createFor("csv", "CSV");
        FileInfo xpt = FileInfo.createFor("xpt", "XPT");
        TestProviderSupplier supplier = new TestProviderSupplier(List.of(csv, xpt));

        assertEquals(2, supplier.getSupportedFileInfos().size());
    }


    @Test
    void testConstructorNullThrows()
    {
        assertThrows(NullPointerException.class, () ->
        {
            new TestProviderSupplier(null);
        });
    }

    // ==================== getSupportedFileInfos ====================


    @Test
    void testGetSupportedFileInfos()
    {
        FileInfo csv = FileInfo.createFor("csv", "CSV");
        FileInfo xpt = FileInfo.createFor("xpt", "XPT");
        TestProviderSupplier supplier = new TestProviderSupplier(List.of(csv, xpt));

        List<FileInfo> infos = supplier.getSupportedFileInfos();

        assertEquals(2, infos.size());
        assertTrue(infos.contains(csv));
        assertTrue(infos.contains(xpt));
    }


    @Test
    void testGetSupportedFileInfosEmpty()
    {
        TestProviderSupplier supplier = new TestProviderSupplier(List.of());

        List<FileInfo> infos = supplier.getSupportedFileInfos();

        assertNotNull(infos);
        assertTrue(infos.isEmpty());
    }

    // ==================== canProvideFor with FileInfo ====================


    @Test
    void testCanProvideForWithMatchingFileInfo()
    {
        FileInfo csv = FileInfo.createFor("csv", "CSV");
        TestProviderSupplier supplier = new TestProviderSupplier(List.of(csv));

        URI uri = URI.create("file:///data.csv");
        assertTrue(supplier.canProvideFor(uri, csv));
    }


    @Test
    void testCanProvideForWithNonMatchingFileInfo()
    {
        FileInfo csv = FileInfo.createFor("csv", "CSV");
        FileInfo xpt = FileInfo.createFor("xpt", "XPT");
        TestProviderSupplier supplier = new TestProviderSupplier(List.of(csv));

        URI uri = URI.create("file:///data.xpt");
        assertFalse(supplier.canProvideFor(uri, xpt));
    }

    // ==================== canProvideFor with null FileInfo (extension matching)
    // ====================


    /**
     * Parameterized: extension matching with a null FileInfo. Covers the three "match by extension"
     * variants (positive, negative, case-insensitive) collapsed into a single test driven by csv
     * input rows.
     */
    @ParameterizedTest(name = "uri={0} -> {1}")
    @CsvSource(
    {
            "file:///data.csv, true", "file:///data.txt, false",
            // case-insensitive
            "file:///DATA.CSV, true", "file:///data.Csv, true", "file:///data.CSV, true"
    })
    void testCanProvideForNullFileInfoByExtension(String uriString, boolean expected)
    {
        FileInfo csv = FileInfo.createFor("csv", "CSV");
        TestProviderSupplier supplier = new TestProviderSupplier(List.of(csv));

        assertEquals(expected, supplier.canProvideFor(URI.create(uriString), null));
    }


    @Test
    void testCanProvideForNullFileInfoMultipleSupported()
    {
        FileInfo csv = FileInfo.createFor("csv", "CSV");
        FileInfo xpt = FileInfo.createFor("xpt", "XPT");
        TestProviderSupplier supplier = new TestProviderSupplier(List.of(csv, xpt));

        assertTrue(supplier.canProvideFor(URI.create("file:///data.csv"), null));
        assertTrue(supplier.canProvideFor(URI.create("file:///data.xpt"), null));
        assertFalse(supplier.canProvideFor(URI.create("file:///data.txt"), null));
    }

    // ==================== canProvideFor edge cases ====================


    /**
     * Parameterized: URIs whose path component does not yield a usable extension — empty path,
     * missing extension. Replaces two near-identical tests.
     */
    @ParameterizedTest(name = "uri={0}")
    @ValueSource(strings =
    {
            // URI with no path
            "http://example.com",
            // path with filename but no extension
            "file:///filename_without_extension"
    })
    void testCanProvideForRejectsUriWithoutExtension(String uriString)
    {
        FileInfo csv = FileInfo.createFor("csv", "CSV");
        TestProviderSupplier supplier = new TestProviderSupplier(List.of(csv));

        assertFalse(supplier.canProvideFor(URI.create(uriString), null));
    }


    @Test
    void testCanProvideForHiddenFile()
    {
        FileInfo csv = FileInfo.createFor("csv", "CSV");
        TestProviderSupplier supplier = new TestProviderSupplier(List.of(csv));

        // Hidden file starting with dot
        URI uri = URI.create("file:///.hidden.csv");
        assertTrue(supplier.canProvideFor(uri, null));
    }


    @Test
    void testCanProvideForDoubleExtension()
    {
        FileInfo gz = FileInfo.createFor("gz", "GZip");
        TestProviderSupplier supplier = new TestProviderSupplier(List.of(gz));

        // File with double extension - should match last extension
        URI uri = URI.create("file:///data.csv.gz");
        assertTrue(supplier.canProvideFor(uri, null));
    }


    @Test
    void testCanProvideForOnlyDot()
    {
        FileInfo csv = FileInfo.createFor("csv", "CSV");
        TestProviderSupplier supplier = new TestProviderSupplier(List.of(csv));

        // Filename ending with just a dot
        URI uri = URI.create("file:///data.");
        // Extension would be empty string
        assertFalse(supplier.canProvideFor(uri, null));
    }


    @Test
    void testCanProvideForEmptySupportedList()
    {
        TestProviderSupplier supplier = new TestProviderSupplier(List.of());

        URI uri = URI.create("file:///data.csv");
        assertFalse(supplier.canProvideFor(uri, null));
    }


    @Test
    void testCanProvideForWithPath()
    {
        FileInfo csv = FileInfo.createFor("csv", "CSV");
        TestProviderSupplier supplier = new TestProviderSupplier(List.of(csv));

        URI uri = URI.create("file:///path/to/subdirectory/data.csv");
        assertTrue(supplier.canProvideFor(uri, null));
    }


    @Test
    void testCanProvideForOpaqueUri()
    {
        FileInfo csv = FileInfo.createFor("csv", "CSV");
        TestProviderSupplier supplier = new TestProviderSupplier(List.of(csv));

        // Opaque URI (like mailto:) returns null for getPath()
        URI uri = URI.create("mailto:user@example.com");
        assertFalse(supplier.canProvideFor(uri, null));
    }
}
