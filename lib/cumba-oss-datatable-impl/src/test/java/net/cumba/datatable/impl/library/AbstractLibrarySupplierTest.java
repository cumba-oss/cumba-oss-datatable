package net.cumba.datatable.impl.library;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.util.List;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.library.ILibraryProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AbstractLibrarySupplierTest
{

    private static final FileInfo FI_CSV = FileInfo.createFor("csv", "CSV Test");

    private static final FileInfo FI_XPT = FileInfo.createFor("xpt", "XPT Test");

    private AbstractLibrarySupplier createSupplier(FileInfo... fileInfos)
    {
        return new AbstractLibrarySupplier(List.of(fileInfos))
        {

            @Override
            public ILibraryProvider getProvider(URI aUri, FileInfo aFileInfo)
            {
                return null;
            }
        };
    }

    // ==================== Constructor ====================


    @Test
    void testConstructorNullThrows()
    {
        assertThrows(NullPointerException.class, () ->
        {
            new AbstractLibrarySupplier(null)
            {

                @Override
                public ILibraryProvider getProvider(URI aUri, FileInfo aFileInfo)
                {
                    return null;
                }
            };
        });
    }

    // ==================== getSupportedFileInfos ====================


    @Test
    void testGetSupportedFileInfos()
    {
        AbstractLibrarySupplier supplier = createSupplier(FI_CSV, FI_XPT);

        List<FileInfo> infos = supplier.getSupportedFileInfos();
        assertEquals(2, infos.size());
    }

    // ==================== canProvideFor with FileInfo ====================


    @Test
    void testCanProvideForWithMatchingFileInfo()
    {
        AbstractLibrarySupplier supplier = createSupplier(FI_CSV);
        URI uri = URI.create("file:///data.csv");

        assertTrue(supplier.canProvideFor(uri, FI_CSV));
    }


    @Test
    void testCanProvideForWithNonMatchingFileInfo()
    {
        AbstractLibrarySupplier supplier = createSupplier(FI_CSV);
        URI uri = URI.create("file:///data.csv");

        assertFalse(supplier.canProvideFor(uri, FI_XPT));
    }

    // ==================== canProvideFor by extension ====================


    @Test
    void testCanProvideForByExtension()
    {
        AbstractLibrarySupplier supplier = createSupplier(FI_CSV);
        URI uri = URI.create("file:///data.csv");

        assertTrue(supplier.canProvideFor(uri, null));
    }


    @Test
    void testCanProvideForByExtensionCaseInsensitive()
    {
        AbstractLibrarySupplier supplier = createSupplier(FI_CSV);
        URI uri = URI.create("file:///data.CSV");

        assertTrue(supplier.canProvideFor(uri, null));
    }


    @ParameterizedTest(name = "uri={0}")
    @ValueSource(strings =
    {
            // non-matching extension
            "file:///data.xpt",
            // no extension at all
            "file:///datafile",
            // no path component
            "mailto:test@example.com"
    })
    void testCanProvideForRejectsNonMatchingUri(String uriString)
    {
        AbstractLibrarySupplier supplier = createSupplier(FI_CSV);
        URI uri = URI.create(uriString);

        assertFalse(supplier.canProvideFor(uri, null));
    }


    @Test
    void testCanProvideForMultipleSupported()
    {
        AbstractLibrarySupplier supplier = createSupplier(FI_CSV, FI_XPT);

        assertTrue(supplier.canProvideFor(URI.create("file:///data.csv"), null));
        assertTrue(supplier.canProvideFor(URI.create("file:///data.xpt"), null));
        assertFalse(supplier.canProvideFor(URI.create("file:///data.json"), null));
    }

    // ==================== default method ====================


    @Test
    void testCanProvideForDefaultDelegation()
    {
        AbstractLibrarySupplier supplier = createSupplier(FI_CSV);
        URI uri = URI.create("file:///data.csv");

        assertEquals(supplier.canProvideFor(uri, null), supplier.canProvideFor(uri));
    }
}
