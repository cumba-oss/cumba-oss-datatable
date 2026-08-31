package net.cumba.datatable.library.folder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link FolderLibrary} construction, type, and the {@link FolderLibrary#getMembers()}
 * enumeration (file/directory filtering, pattern matching, sorted output, and the empty-stream
 * fallback when the folder cannot be listed).
 */
class FolderLibraryTest
{

    @TempDir
    File tempDir;

    private File writeCsv(String aName) throws IOException
    {
        File f = new File(tempDir, aName);
        Files.writeString(f.toPath(), "ID\n1\n", StandardCharsets.UTF_8);
        return f;
    }

    // ------------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------------


    @Test
    void constructor_fromFile_usesFolderNameAsLibraryName()
    {
        try (FolderLibrary lib = new FolderLibrary(tempDir))
        {
            assertEquals(tempDir.getName(), lib.getName());
            assertEquals(tempDir.toPath(), lib.getFolder());
        }
        catch (Exception ex)
        {
            fail(ex);
        }
    }


    @Test
    void constructor_fromPath_usesFileNameAsLibraryName()
    {
        Path p = tempDir.toPath();
        try (FolderLibrary lib = new FolderLibrary(p))
        {
            assertEquals(p.getFileName().toString(), lib.getName());
        }
        catch (Exception ex)
        {
            fail(ex);
        }
    }


    @Test
    void constructor_fromPathAndExplicitName_keepsName()
    {
        try (FolderLibrary lib = new FolderLibrary(tempDir.toPath(), "MyLib"))
        {
            assertEquals("MyLib", lib.getName());
            assertEquals("MyLib", lib.getLabel());
        }
        catch (Exception ex)
        {
            fail(ex);
        }
    }


    @Test
    void getType_isFolder()
    {
        try (FolderLibrary lib = new FolderLibrary(tempDir))
        {
            assertEquals("folder", lib.getType());
        }
        catch (Exception ex)
        {
            fail(ex);
        }
    }


    @Test
    void uri_matchesFolderUri()
    {
        try (FolderLibrary lib = new FolderLibrary(tempDir))
        {
            assertEquals(tempDir.toPath().toUri(), lib.getUri());
        }
        catch (Exception ex)
        {
            fail(ex);
        }
    }


    @Test
    void fileInfo_isFolderFileInfo()
    {
        try (FolderLibrary lib = new FolderLibrary(tempDir))
        {
            assertSame(FolderLibrarySupplier.FI_FOLDER, lib.getFileInfo());
        }
        catch (Exception ex)
        {
            fail(ex);
        }
    }

    // ------------------------------------------------------------------
    // getMembers
    // ------------------------------------------------------------------


    @Test
    void getMembers_emptyFolder_returnsEmpty()
    {
        try (FolderLibrary lib = new FolderLibrary(tempDir))
        {
            assertEquals(0L, lib.getMembers().count());
        }
        catch (Exception ex)
        {
            fail(ex);
        }
    }


    @Test
    void getMembers_listsMatchingFiles()
    {
        try (FolderLibrary lib = new FolderLibrary(tempDir))
        {
            writeCsv("dm.csv");
            writeCsv("ae.csv");
            List<String> names = lib.getMembers().map(FolderMember::getName)
                    .collect(Collectors.toList());
            assertEquals(2, names.size());
            assertTrue(names.contains("DM"), names.toString());
            assertTrue(names.contains("AE"), names.toString());
        }
        catch (Exception ex)
        {
            fail(ex);
        }
    }


    @Test
    void getMembers_excludesDirectories()
    {
        try (FolderLibrary lib = new FolderLibrary(tempDir))
        {
            writeCsv("dm.csv");
            File sub = new File(tempDir, "subdir.csv");
            // A directory whose name still matches the file pattern must NOT become a member.
            assertTrue(sub.mkdir());
            List<String> names = lib.getMembers().map(FolderMember::getName)
                    .collect(Collectors.toList());
            assertEquals(List.of("DM"), names,
                    "directories must be filtered out of the member list");
        }
        catch (Exception ex)
        {
            fail(ex);
        }
    }


    @Test
    void getMembers_excludesUnsupportedExtensions()
    {
        try (FolderLibrary lib = new FolderLibrary(tempDir))
        {
            writeCsv("dm.csv");
            // ".unknownext" is not a registered data-table format → filtered out.
            Files.writeString(new File(tempDir, "notes.unknownext").toPath(), "x",
                    StandardCharsets.UTF_8);
            List<String> names = lib.getMembers().map(FolderMember::getName)
                    .collect(Collectors.toList());
            assertEquals(List.of("DM"), names);
        }
        catch (Exception ex)
        {
            fail(ex);
        }
    }


    @Test
    void getMembers_areSortedByComparator()
    {
        try (FolderLibrary lib = new FolderLibrary(tempDir))
        {
            writeCsv("zz.csv");
            writeCsv("aa.csv");
            writeCsv("mm.csv");
            List<String> names = lib.getMembers().map(FolderMember::getName)
                    .collect(Collectors.toList());
            // LibraryMemberComparator sorts members; assert the result is in ascending name order.
            List<String> sorted = names.stream().sorted().collect(Collectors.toList());
            assertEquals(sorted, names, "members must be returned in sorted order");
        }
        catch (Exception ex)
        {
            fail(ex);
        }
    }


    @Test
    void getMembers_missingFolder_returnsEmptyStream()
    {
        // Pointing at a non-existent directory: Files.newDirectoryStream throws IOException, which
        // FolderLibrary.getMembers swallows and converts to an empty stream.
        File missing = new File(tempDir, "does-not-exist");
        assertFalse(missing.exists());
        try (FolderLibrary lib = new FolderLibrary(missing))
        {
            assertEquals(0L, lib.getMembers().count());
        }
        catch (Exception ex)
        {
            fail(ex);
        }
    }
}
