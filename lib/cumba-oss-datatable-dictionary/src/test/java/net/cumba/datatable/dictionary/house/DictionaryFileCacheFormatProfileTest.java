package net.cumba.datatable.dictionary.house;

// OSS-IDENTITY datatable-dictionary: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The process-wide cache (DF12, M5): one parse per unchanged file, a fresh one after a re-install.
 */
class DictionaryFileCacheFormatProfileTest
{

    @TempDir
    Path dir;

    @Test
    void twoLoadsOfAnUnchangedFileAnswerTheSameInstance() throws IOException
    {
        Path file = TestStore.versioned(dir, "loinc", "2.80", TestStore.loinc("2.80"));
        ValueMapDictionary first = DictionaryFileCache.load(file,
                ValueMapDictionary.Profile.FORMAT);
        ValueMapDictionary second = DictionaryFileCache.load(file,
                ValueMapDictionary.Profile.FORMAT);
        assertSame(first, second);
        // a relative or un-normalised spelling of the same file is the same key
        Path spelled = file.getParent().resolve("..").resolve("2.80").resolve("loinc.json");
        assertSame(first, DictionaryFileCache.load(spelled, ValueMapDictionary.Profile.FORMAT));
    }


    @Test
    void aReinstallWithAnotherSizeYieldsANewCopy_theOldOneKeepsAnswering() throws IOException
    {
        Path file = TestStore.versioned(dir, "loinc", "2.80", TestStore.loinc("2.80"));
        ValueMapDictionary old = DictionaryFileCache.load(file, ValueMapDictionary.Profile.FORMAT);
        assertEquals("Glucose", old.decode("LOINC", "2345-7"));

        TestStore.House changed = TestStore.loinc("2.80").pairs("loinc",
                Map.of("1558-6", "Fasting glucose", "2345-7", "Glucose [Mass/volume] in Serum"));
        TestStore.versioned(dir, "loinc", "2.80", changed);
        ValueMapDictionary fresh = DictionaryFileCache.load(file,
                ValueMapDictionary.Profile.FORMAT);
        assertNotSame(old, fresh);
        assertEquals("Glucose [Mass/volume] in Serum", fresh.decode("LOINC", "2345-7"));
        assertEquals("Glucose", old.decode("LOINC", "2345-7"),
                "an open table keeps its copy (DF14)");
    }


    @Test
    void aReinstallWithTheSameSizeButAnotherMtimeIsSeen() throws IOException
    {
        Path file = TestStore.versioned(dir, "loinc", "2.80", TestStore.loinc("2.80"));
        ValueMapDictionary old = DictionaryFileCache.load(file, ValueMapDictionary.Profile.FORMAT);
        // same bytes, same size — only the modification time moves
        Files.setLastModifiedTime(file, FileTime.from(Instant.parse("2030-01-01T00:00:00Z")));
        assertNotSame(old, DictionaryFileCache.load(file, ValueMapDictionary.Profile.FORMAT));
    }


    @Test
    void anUnreadableFileThrows_andIsNotCached() throws IOException
    {
        Path file = TestStore.text(dir.resolve("x").resolve("1").resolve("x.json"), "{ broken");
        assertThrows(IOException.class,
                () -> DictionaryFileCache.load(file, ValueMapDictionary.Profile.FORMAT));
        TestStore.text(file, TestStore.loinc("1").json());
        assertNull(DictionaryFileCache.load(file, ValueMapDictionary.Profile.FORMAT).decode("LOINC",
                "0"));
        assertEquals("loinc",
                DictionaryFileCache.load(file, ValueMapDictionary.Profile.FORMAT).getType());
    }


    @Test
    void theCacheIsBoundedByEntries() throws IOException
    {
        for (int i = 0; i < DictionaryFileCache.MAX_ENTRIES + 5; i++)
        {
            Path file = TestStore.versioned(dir, "loinc", "2." + i, TestStore.loinc("2." + i));
            DictionaryFileCache.load(file, ValueMapDictionary.Profile.FORMAT);
            assertTrue(DictionaryFileCache.size() <= DictionaryFileCache.MAX_ENTRIES,
                    "after " + (i + 1) + " loads: " + DictionaryFileCache.size());
        }
        assertEquals(DictionaryFileCache.MAX_ENTRIES, DictionaryFileCache.size(),
                "full after more distinct files than the bound: the LRU keeps exactly the bound");
    }
}
