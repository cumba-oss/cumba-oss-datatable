package net.cumba.datatable.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class FileInfoTest
{

    @Test
    void testCreateFor()
    {
        FileInfo info = FileInfo.createFor("csv", "CSV Files");

        assertEquals("csv", info.getFileExtension());
        assertEquals("(?i).*\\.csv", info.getFileNamePattern());
        assertEquals("CSV Files (*.csv)", info.getDescription());
        assertNotNull(info.getUuid());
    }


    @Test
    void testCreateForWithUUID()
    {
        FileInfo info = FileInfo.createFor("json", "JSON", "abc-123");

        assertEquals("abc-123", info.getUuid());
    }


    @Test
    void testCreateForWithoutUUID()
    {
        // expect a UUID to be created
        FileInfo info = FileInfo.createFor("csv", "CSV");

        assertNotNull(info.getUuid());
    }


    @Test
    void testFileExtension()
    {
        FileInfo info = FileInfo.createFor("csv", "CSV Files");

        assertEquals("csv", info.getFileExtension());
    }


    @Test
    void testFileNamePattern()
    {
        FileInfo info = FileInfo.createFor("csv", "CSV");

        assertEquals("(?i).*\\.csv", info.getFileNamePattern());
    }


    @Test
    void testFileNamePatternMatches()
    {
        FileInfo info = FileInfo.createFor("csv", "CSV");
        Pattern pattern = Pattern.compile(info.getFileNamePattern());

        assertTrue(pattern.matcher("file.csv").matches());
        assertTrue(pattern.matcher("FILE.CSV").matches());
    }


    @Test
    void testFileNamePatternNoMatch()
    {
        FileInfo info = FileInfo.createFor("csv", "CSV");
        Pattern pattern = Pattern.compile(info.getFileNamePattern());

        assertFalse(pattern.matcher("file.txt").matches());
    }


    @Test
    void testDescription()
    {
        FileInfo info = FileInfo.createFor("csv", "CSV Files");

        assertEquals("CSV Files (*.csv)", info.getDescription());
    }


    @Test
    void testToString()
    {
        FileInfo info = FileInfo.createFor("csv", "CSV Files");

        assertEquals(info.getDescription(), info.toString());
    }


    @Test
    void testBuilder()
    {
        FileInfo info = FileInfo.builder().fileExtension("x").fileNamePattern("p").description("d")
                .uuid("u").build();

        assertEquals("x", info.getFileExtension());
        assertEquals("p", info.getFileNamePattern());
        assertEquals("d", info.getDescription());
        assertEquals("u", info.getUuid());
    }


    @Test
    void testToBuilder()
    {
        FileInfo original = FileInfo.createFor("csv", "CSV Files");
        FileInfo modified = original.toBuilder().fileExtension("tsv").build();

        assertEquals("csv", original.getFileExtension());
        assertEquals("tsv", modified.getFileExtension());
    }


    @Test
    void testEquality()
    {
        FileInfo a = FileInfo.createFor("csv", "CSV Files");
        FileInfo b = FileInfo.createFor("csv", "CSV Files");

        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    // ==================== createCombined ====================


    @Test
    void testCreateCombinedSingleFileInfo()
    {
        FileInfo csv = FileInfo.createFor("csv", "CSV Files");
        FileInfo combined = FileInfo.createCombined(java.util.List.of(csv));
        assertNotNull(combined);
        assertEquals("", combined.getFileExtension());
        assertTrue(combined.getFileNamePattern().contains("csv"));
        assertTrue(combined.getDescription().startsWith("All Supported types"));
        assertTrue(combined.getDescription().contains("csv"));
    }


    @Test
    void testCreateCombinedMultipleFileInfos()
    {
        FileInfo csv = FileInfo.createFor("csv", "CSV Files");
        FileInfo json = FileInfo.createFor("json", "JSON");
        FileInfo xml = FileInfo.createFor("xml", "XML");
        FileInfo combined = FileInfo.createCombined(java.util.List.of(csv, json, xml));
        assertNotNull(combined);
        // pattern is sorted alphabetically and contains all extensions
        String pattern = combined.getFileNamePattern();
        assertTrue(pattern.contains("csv"));
        assertTrue(pattern.contains("json"));
        assertTrue(pattern.contains("xml"));
    }


    @Test
    void testCreateCombinedWithDescriptionPrefix()
    {
        FileInfo csv = FileInfo.createFor("csv", "CSV Files");
        FileInfo combined = FileInfo.createCombined(java.util.List.of(csv), "Custom prefix");
        assertNotNull(combined);
        assertTrue(combined.getDescription().startsWith("Custom prefix"));
    }


    @Test
    void testCreateCombinedWithBlankDescriptionPrefix()
    {
        FileInfo csv = FileInfo.createFor("csv", "CSV Files");
        FileInfo combined = FileInfo.createCombined(java.util.List.of(csv), "");
        assertNotNull(combined);
        assertTrue(combined.getDescription().startsWith("All Supported types"));
    }


    @Test
    void testCreateCombinedTruncatesPastFive()
    {
        java.util.List<FileInfo> many = new java.util.ArrayList<>();
        for (int i = 0; i < 7; i++)
        {
            many.add(FileInfo.createFor("ext" + i, "Files " + i));
        }
        FileInfo combined = FileInfo.createCombined(many);
        assertNotNull(combined);
        // description contains ";..." marker because more than 5 extensions
        assertTrue(combined.getDescription().contains(";..."));
    }

    // ==================== findByFileName ====================


    @Test
    void testFindByFileNameMatch()
    {
        FileInfo csv = FileInfo.createFor("csv", "CSV");
        FileInfo json = FileInfo.createFor("json", "JSON");

        FileInfo found = FileInfo.findByFileName("data.csv", java.util.List.of(csv, json));
        assertNotNull(found);
        assertEquals("csv", found.getFileExtension());
    }


    @Test
    void testFindByFileNameCaseInsensitive()
    {
        FileInfo csv = FileInfo.createFor("csv", "CSV");

        FileInfo found = FileInfo.findByFileName("data.CSV", java.util.List.of(csv));
        assertNotNull(found);
        assertEquals("csv", found.getFileExtension());
    }


    /**
     * Cases where the registered csv {@link FileInfo} must not match the supplied file name — a
     * wrong extension, a null name, no extension at all, and a name ending in a bare dot.
     */
    @ParameterizedTest(name = "fileName={0}")
    @NullSource
    @ValueSource(strings =
    {
            "data.xml", "noextension", "data."
    })
    void testFindByFileNameNoMatchAgainstCsvList(String fileName)
    {
        FileInfo csv = FileInfo.createFor("csv", "CSV");
        assertNull(FileInfo.findByFileName(fileName, java.util.List.of(csv)));
    }


    @Test
    void testFindByFileNameNullList()
    {
        // Separate from the parameterized cases: empty/missing FileInfo registry, not a
        // mismatch against an existing entry.
        assertNull(FileInfo.findByFileName("data.csv", null));
    }


    @Test
    void testFindByFileNameFirstMatchWins()
    {
        FileInfo first = FileInfo.createFor("csv", "First CSV");
        FileInfo second = FileInfo.createFor("csv", "Second CSV");
        FileInfo found = FileInfo.findByFileName("data.csv", java.util.List.of(first, second));
        assertNotNull(found);
        assertEquals("First CSV (*.csv)", found.getDescription());
    }

    // ==================== UUID required ====================


    @Test
    void testBuilderRequiresUuid()
    {
        // build() throws when uuid is null/blank — setters just store values, so the
        // throwing call in the lambda is build() only.
        FileInfo.FileInfoBuilder b = FileInfo.builder().fileExtension("x").fileNamePattern("p")
                .description("d").uuid("   ");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, b::build);
    }
}
