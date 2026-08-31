package net.cumba.datatable.library.folder;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.library.ILibraryProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FolderLibrarySupplierTest
{

    @TempDir
    File tempDir;

    private final FolderLibrarySupplier supplier = new FolderLibrarySupplier();

    // ==================== FileInfo ====================

    @Test
    void testFIFolderConstant()
    {
        FileInfo fi = FolderLibrarySupplier.FI_FOLDER;
        assertNotNull(fi);
        assertEquals("", fi.getFileExtension());
        assertNotNull(fi.getUuid());
    }


    @Test
    void testGetSupportedFileInfos()
    {
        List<FileInfo> infos = supplier.getSupportedFileInfos();
        assertEquals(1, infos.size());
        assertSame(FolderLibrarySupplier.FI_FOLDER, infos.get(0));
    }

    // ==================== canProvideFor ====================


    @Test
    void testCanProvideForDirectory()
    {
        URI dirUri = tempDir.toURI();
        assertTrue(supplier.canProvideFor(dirUri, null));
    }


    @Test
    void testCanProvideForFile() throws IOException
    {
        File file = new File(tempDir, "test.txt");
        file.createNewFile();
        URI fileUri = file.toURI();

        assertFalse(supplier.canProvideFor(fileUri, null));
    }


    @Test
    void testCanProvideForNonFileScheme()
    {
        URI httpUri = URI.create("http://example.com/folder");

        assertFalse(supplier.canProvideFor(httpUri, null));
    }


    @Test
    void testCanProvideForNonExistentPath()
    {
        URI nonExistent = new File(tempDir, "does-not-exist").toURI();

        assertFalse(supplier.canProvideFor(nonExistent, null));
    }

    // ==================== getProvider ====================


    @Test
    void testGetProviderForDirectory()
    {
        URI dirUri = tempDir.toURI();
        ILibraryProvider provider = supplier.getProvider(dirUri, null);

        assertNotNull(provider);
        assertTrue(provider instanceof FolderLibraryProvider);
    }


    @Test
    void testGetProviderForNonDirectory()
    {
        URI httpUri = URI.create("http://example.com/folder");
        ILibraryProvider provider = supplier.getProvider(httpUri, null);

        assertNull(provider);
    }

    // ==================== default method delegation ====================


    @Test
    void testCanProvideForDefaultDelegation()
    {
        URI dirUri = tempDir.toURI();
        // Default canProvideFor(URI) should delegate to canProvideFor(URI, null)
        assertEquals(supplier.canProvideFor(dirUri, null), supplier.canProvideFor(dirUri));
    }


    @Test
    void testGetProviderDefaultDelegation()
    {
        URI dirUri = tempDir.toURI();
        // Both should return non-null providers for a valid directory
        ILibraryProvider p1 = supplier.getProvider(dirUri, null);
        ILibraryProvider p2 = supplier.getProvider(dirUri);
        assertNotNull(p1);
        assertNotNull(p2);
    }
}
