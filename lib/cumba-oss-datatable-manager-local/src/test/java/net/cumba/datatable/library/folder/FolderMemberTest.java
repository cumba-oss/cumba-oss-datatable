package net.cumba.datatable.library.folder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import net.cumba.datatable.DataTableColumnMeta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FolderMemberTest
{

    @TempDir
    File tempDir;

    private FolderLibrary createLibrary(File folder)
    {
        return new FolderLibrary(folder);
    }

    // ==================== Constructor ====================


    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
    {
            // Name is filename without extension, uppercased
            "mydata.csv, MYDATA", "LowerCase.sas7bdat, LOWERCASE",
            // CDT.getBeforeLast returns the string itself when char is not found
            "noext, NOEXT",
            // getBeforeLast uses lastIndexOf('.'), so "file.name" is extracted
            "file.name.csv, FILE.NAME"
    })
    void testConstructorExtractsName(String aFileName, String aExpectedName)
    {
        Path file = new File(tempDir, aFileName).toPath();
        FolderMember member = new FolderMember(createLibrary(tempDir), file);

        assertEquals(aExpectedName, member.getName());
    }

    // ==================== getLabel ====================


    @Test
    void testGetLabelAlwaysNull()
    {
        Path file = new File(tempDir, "data.csv").toPath();
        FolderMember member = new FolderMember(createLibrary(tempDir), file);

        assertNull(member.getLabel());
    }

    // ==================== getURI ====================


    @Test
    void testGetURI()
    {
        File file = new File(tempDir, "data.csv");
        FolderMember member = new FolderMember(createLibrary(tempDir), file.toPath());

        URI uri = member.getUri();
        assertNotNull(uri);
        assertEquals(file.toURI(), uri);
    }

    // ==================== getLibrary ====================


    @Test
    void testGetLibrary()
    {
        FolderLibrary lib = createLibrary(tempDir);
        Path file = new File(tempDir, "data.csv").toPath();
        FolderMember member = new FolderMember(lib, file);

        assertSame(lib, member.getLibrary());
    }

    // ==================== getColumns ====================


    @Test
    void testGetColumnsInitiallyEmpty()
    {
        Path file = new File(tempDir, "data.csv").toPath();
        FolderMember member = new FolderMember(createLibrary(tempDir), file);

        List<DataTableColumnMeta> cols = member.getColumns();
        assertNotNull(cols);
        assertTrue(cols.isEmpty());
    }


    @Test
    void testGetColumnsLazyInitialized()
    {
        Path file = new File(tempDir, "data.csv").toPath();
        FolderMember member = new FolderMember(createLibrary(tempDir), file);

        // First call initializes the list
        List<DataTableColumnMeta> cols1 = member.getColumns();
        // Second call returns the same list
        List<DataTableColumnMeta> cols2 = member.getColumns();
        assertSame(cols1, cols2);
    }
}
