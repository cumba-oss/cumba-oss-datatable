package net.cumba.datatable.dictionary.house;

// OSS-IDENTITY datatable-dictionary: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.cumba.datatable.dictionary.house.DictionaryStoreReader.Outcome;
import net.cumba.datatable.dictionary.house.DictionaryStoreReader.Resolution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The store reader (plan §3.4 step 3, round-1 L3 / L4, DF1 / DF1a): versioned, flat and mixed
 * layouts with the engine's {@code DictionaryStore} rules; a present version binds by equality
 * only, a blank one binds the most recent installed version.
 */
class DictionaryStoreReaderTest
{

    @TempDir
    Path store;

    @Test
    void anExactVersionBindsItsDirectory() throws IOException
    {
        TestStore.versioned(store, "meddra", "26.0", TestStore.meddra("26.0", true));
        TestStore.versioned(store, "meddra", "27.1", TestStore.meddra("27.1", true));
        Resolution r = new DictionaryStoreReader(store).resolve("meddra", "26.0");
        assertTrue(r.isResolved());
        assertEquals("26.0", r.version());
        assertFalse(r.mostRecent());
        assertNotNull(r.dictionary());
        assertEquals("26.0", r.dictionary().getVersion());
        assertEquals(List.of("27.1", "26.0"), r.installed(), "most recent first");
    }


    @Test
    void anotherInstalledVersionIsNotInstalled_namingTheInstalledOnes() throws IOException
    {
        TestStore.versioned(store, "meddra", "27.1", TestStore.meddra("27.1", true));
        Resolution r = new DictionaryStoreReader(store).resolve("meddra", "26.0");
        assertEquals(Outcome.NOT_INSTALLED, r.outcome());
        assertNull(r.dictionary());
        assertEquals("26.0", r.version());
        assertEquals(List.of("27.1"), r.installed());
        assertTrue(r.detail().contains("26.0"), r.detail());
    }


    @Test
    void aBlankVersionBindsTheMostRecentInstalledOne_df1a() throws IOException
    {
        TestStore.versioned(store, "meddra", "26.0", TestStore.meddra("26.0", true));
        TestStore.versioned(store, "meddra", "27.1", TestStore.meddra("27.1", true));
        TestStore.versioned(store, "meddra", "9.1", TestStore.meddra("9.1", false));
        // the installer's manifest selects the OLD one: never read for binding (DF1a)
        TestStore.text(store.resolve(DictionaryStoreReader.MANIFEST), "{\"meddra\":\"9.1\"}");
        DictionaryStoreReader reader = new DictionaryStoreReader(store);
        for (String blank : new String[]
        {
                null, "", "  "
        })
        {
            Resolution r = reader.resolve("meddra", blank);
            assertTrue(r.isResolved(), String.valueOf(blank));
            assertEquals("27.1", r.version());
            assertTrue(r.mostRecent());
        }
        assertEquals(List.of("27.1", "26.0", "9.1"), reader.installedVersions("meddra"),
                "numeric, not lexicographic (9.1 < 26.0)");
    }


    @Test
    void anInvalidTokenIsRefusedBeforeAnyPathIsBuilt() throws IOException
    {
        TestStore.versioned(store, "meddra", "26.0", TestStore.meddra("26.0", true));
        DictionaryStoreReader reader = new DictionaryStoreReader(store);
        for (String bad : List.of("../x", "26 0", "a/b", ".", "..", "x".repeat(65), "26.0\\"))
        {
            Resolution r = reader.resolve("meddra", bad);
            assertEquals(Outcome.INVALID_TOKEN, r.outcome(), bad);
            assertNull(r.dictionary());
            assertFalse(r.detail().contains(store.toString()),
                    "no path in the status: " + r.detail());
        }
        assertTrue(DictionaryStoreReader.isValidVersionToken("SEP_2020"));
        assertTrue(DictionaryStoreReader.isValidVersionToken("2026.07.06"));
        assertTrue(DictionaryStoreReader.isValidVersionToken("4Aug2026"));
    }


    @Test
    void aTypeThatIsNotInstalledSaysSo() throws IOException
    {
        TestStore.versioned(store, "meddra", "26.0", TestStore.meddra("26.0", true));
        Resolution r = new DictionaryStoreReader(store).resolve("whodrug", "SEP_2020");
        assertEquals(Outcome.NOT_INSTALLED, r.outcome());
        assertTrue(r.installed().isEmpty());
        Resolution blank = new DictionaryStoreReader(store).resolve("whodrug", null);
        assertEquals(Outcome.NOT_INSTALLED, blank.outcome());
        assertNull(blank.version());
        // and a store directory that does not exist at all
        Resolution none = new DictionaryStoreReader(store.resolve("nowhere")).resolve("meddra",
                "26.0");
        assertEquals(Outcome.NOT_INSTALLED, none.outcome());
        assertTrue(new DictionaryStoreReader(store.resolve("nowhere")).installedVersions("meddra")
                .isEmpty());
    }


    @Test
    void inAVersionedStoreNoOtherTypesFileIsRead_l4() throws IOException
    {
        TestStore.versioned(store, "meddra", "26.0", TestStore.meddra("26.0", true));
        // a corrupt stray file of another type inside the version directory
        TestStore.text(store.resolve("meddra").resolve("26.0").resolve("whodrug.json"),
                "{ corrupt");
        // and a corrupt sibling type
        TestStore.text(store.resolve("whodrug").resolve("SEP_2020").resolve("whodrug.json"),
                "{ corrupt");
        Resolution r = new DictionaryStoreReader(store).resolve("meddra", "26.0");
        assertTrue(r.isResolved(), r.detail());
        assertEquals(Outcome.UNREADABLE,
                new DictionaryStoreReader(store).resolve("whodrug", "SEP_2020").outcome());
    }


    @Test
    void aVersionDirectoryWithoutTheTypesFile_orDeclaringAnotherType_isNoFile() throws IOException
    {
        Files.createDirectories(store.resolve("meddra").resolve("26.0"));
        Resolution r = new DictionaryStoreReader(store).resolve("meddra", "26.0");
        assertEquals(Outcome.NO_FILE, r.outcome());
        assertEquals(List.of("26.0"), r.installed());

        TestStore.text(store.resolve("meddra").resolve("26.0").resolve("meddra.json"),
                TestStore.whodrug("x").json());
        assertEquals(Outcome.NO_FILE,
                new DictionaryStoreReader(store).resolve("meddra", "26.0").outcome());
    }


    @Test
    void anEmptyFileIsNoContent_aGzipFileReads() throws IOException
    {
        TestStore.versioned(store, "loinc", "2.80", TestStore.House.of("loinc").version("2.80"));
        assertEquals(Outcome.NO_CONTENT,
                new DictionaryStoreReader(store).resolve("loinc", "2.80").outcome());
        TestStore.versionedGz(store, "loinc", "2.81", TestStore.loinc("2.81"));
        Resolution gz = new DictionaryStoreReader(store).resolve("loinc", "2.81");
        assertTrue(gz.isResolved());
        assertEquals("Glucose", gz.dictionary().decode("LOINC", "2345-7"));
    }


    @Test
    void aFileDeclaringNoTypeBindsByItsDirectory() throws IOException
    {
        TestStore.versioned(store, "loinc", "2.80",
                TestStore.House.of("").version("2.80").codeLevel("LOINC", "1-1"));
        Resolution r = new DictionaryStoreReader(store).resolve("loinc", "2.80");
        assertTrue(r.isResolved());
        assertTrue(r.dictionary().isMember("LOINC", "1-1"));
    }


    @Test
    void flatStore_bindsWhenNothingOrTheDeclaredVersionIsRequested() throws IOException
    {
        TestStore.flat(store, "unii", TestStore.unii("4Aug2026"));
        TestStore.text(store.resolve(DictionaryStoreReader.MANIFEST), "{\"unii\":\"4Aug2026\"}");
        DictionaryStoreReader reader = new DictionaryStoreReader(store);
        Resolution none = reader.resolve("unii", null);
        assertTrue(none.isResolved());
        assertEquals("4Aug2026", none.version());
        assertTrue(none.mostRecent());
        Resolution same = reader.resolve("unii", "4Aug2026");
        assertTrue(same.isResolved());
        assertFalse(same.mostRecent());
        Resolution other = reader.resolve("unii", "1Jan2020");
        assertEquals(Outcome.NOT_INSTALLED, other.outcome());
        assertEquals(List.of("4Aug2026"), other.installed());
        assertTrue(other.detail().contains("4Aug2026"), other.detail());
        assertEquals(List.of("4Aug2026"), reader.installedVersions("unii"));
        assertEquals(Outcome.NOT_INSTALLED, reader.resolve("meddra", null).outcome());
        assertEquals(Outcome.NOT_INSTALLED, reader.resolve("selected-versions", null).outcome(),
                "the manifest is never a dictionary file");
    }


    @Test
    void flatStore_aFileDeclaringNoVersionBindsOnlyWhenNoneIsRequested() throws IOException
    {
        TestStore.flat(store, "loinc", TestStore.House.of("loinc").codeLevel("LOINC", "1-1"));
        DictionaryStoreReader reader = new DictionaryStoreReader(store);
        Resolution none = reader.resolve("loinc", null);
        assertTrue(none.isResolved());
        assertNull(none.version());
        assertFalse(none.mostRecent());
        Resolution asked = reader.resolve("loinc", "2.80");
        assertEquals(Outcome.NOT_INSTALLED, asked.outcome());
        assertTrue(asked.detail().contains("declaring no version"), asked.detail());
        assertTrue(reader.installedVersions("loinc").isEmpty());
    }


    @Test
    void flatStore_corruptOrEmptyOrForeignTypeFiles() throws IOException
    {
        TestStore.text(store.resolve("unii.json"), "{ corrupt");
        assertEquals(Outcome.UNREADABLE,
                new DictionaryStoreReader(store).resolve("unii", null).outcome());
        assertTrue(new DictionaryStoreReader(store).installedVersions("unii").isEmpty());
        TestStore.flat(store, "loinc", TestStore.House.of("loinc").version("2.80"));
        assertEquals(Outcome.NO_CONTENT,
                new DictionaryStoreReader(store).resolve("loinc", null).outcome());
        TestStore.text(store.resolve("snomed.json"), TestStore.unii("x").json());
        assertEquals(Outcome.NO_FILE,
                new DictionaryStoreReader(store).resolve("snomed", null).outcome());
    }


    @Test
    void mixedStore_theVersionedTreeWins_strayFlatFilesAreIgnored() throws IOException
    {
        TestStore.versioned(store, "meddra", "26.0", TestStore.meddra("26.0", true));
        TestStore.flat(store, "unii", TestStore.unii("4Aug2026"));
        DictionaryStoreReader reader = new DictionaryStoreReader(store);
        assertTrue(reader.resolve("meddra", "26.0").isResolved());
        assertEquals(Outcome.NOT_INSTALLED, reader.resolve("unii", "4Aug2026").outcome(),
                "the flat unii.json is not read in a versioned store");
        assertTrue(reader.installedVersions("unii").isEmpty());
    }


    @Test
    void unparseableVersionDirectoriesRankBelowParsableOnes_byInstallDate() throws IOException
    {
        TestStore.versioned(store, "meddra", "custom-b",
                TestStore.meddra("custom-b", true).retrieved("2026-05-01"));
        TestStore.versioned(store, "meddra", "custom-a",
                TestStore.meddra("custom-a", true).retrieved("2026-06-01"));
        TestStore.versioned(store, "meddra", "26.0",
                TestStore.meddra("26.0", true).retrieved("2020-01-01"));
        DictionaryStoreReader reader = new DictionaryStoreReader(store);
        assertEquals(List.of("26.0", "custom-a", "custom-b"), reader.installedVersions("meddra"));
        assertEquals("26.0", reader.resolve("meddra", null).version());
    }


    /**
     * Lane 1 LOW-2: listing the installed versions and ordering tied tokens read only a file's
     * header ({@code version} / {@code retrieved}) — no full parse, nothing pinned in the cache —
     * in the versioned and the flat layout alike, wherever the header fields sit in the file.
     */
    @Test
    void listingAndOrderingReadOnlyTheHeader_noFileIsParsedOrCached_l2(@TempDir Path aFlat)
        throws IOException
    {
        // 27.0 and 27.00 parse to the same key: the order needs both install dates
        Path older = TestStore.versioned(store, "meddra", "27.0",
                TestStore.meddra("27.0", true).retrieved("2026-01-01"));
        Path newer = TestStore.versioned(store, "meddra", "27.00",
                TestStore.meddra("27.00", true).retrieved("2026-02-01"));
        DictionaryStoreReader reader = new DictionaryStoreReader(store);
        assertEquals(List.of("27.00", "27.0"), reader.installedVersions("meddra"));
        assertFalse(DictionaryFileCache.isCached(older), "the order read a header, not the file");
        assertFalse(DictionaryFileCache.isCached(newer));

        Path flat = TestStore.flat(aFlat, "loinc", TestStore.loinc("2.80"));
        assertEquals(List.of("2.80"), new DictionaryStoreReader(aFlat).installedVersions("loinc"));
        assertFalse(DictionaryFileCache.isCached(flat), "the flat listing read a header");

        // control: resolving does parse (and caches) the bound file
        assertTrue(reader.resolve("meddra", "27.0").isResolved());
        assertTrue(DictionaryFileCache.isCached(older));
    }


    /** Lane 1 LOW-2: the header of a file, as the full parse would read it. */
    @Test
    void theHeaderIsWhatTheFullParseReads() throws IOException
    {
        Path file = TestStore.versionedGz(store, "loinc", "2.80", TestStore.loinc("2.80"));
        HouseDictionaryReader.Header header = HouseDictionaryReader.readHeader(file);
        ValueMapDictionary full = HouseDictionaryReader.read(file);
        assertEquals(full.getType(), header.type());
        assertEquals(full.getVersion(), header.version());
        assertEquals(full.getRetrieved(), header.retrieved());
        assertEquals("loinc", header.type());
        assertEquals("2.80", header.version());
        assertEquals("2026-10-03", header.retrieved());

        Path bare = TestStore.text(store.resolve("bare.json"), "{\"levels\":{}}");
        HouseDictionaryReader.Header none = HouseDictionaryReader.readHeader(bare);
        assertEquals("", none.type());
        assertNull(none.version());
        assertNull(none.retrieved());
        assertNull(none.source());
        assertEquals(ValueMapDictionary.HOUSE_FORMAT_1, none.houseFormat());

        // B4: the installer appends the header AFTER the sections, house_format and source too —
        // the header read walks to the end, and agrees with both load profiles
        Path v2 = TestStore.text(store.resolve("v2.json"), "{\"levels\":{\"LOINC\":{\"1-8\":"
                + "\"1-8\"}},\"pairs\":{\"loinc\":{\"1-8\":\"A\"}},\"type\":\"loinc\","
                + "\"version\":\"2.81\",\"source\":\"/raw/loinc\",\"retrieved\":\"2026-10-10\","
                + "\"house_format\":2}");
        HouseDictionaryReader.Header h2 = HouseDictionaryReader.readHeader(v2);
        assertEquals(2, h2.houseFormat());
        assertEquals("/raw/loinc", h2.source());
        assertEquals("2.81", h2.version());
        assertEquals("/raw/loinc", HouseDictionaryReader.read(v2).getSource());
        assertEquals("/raw/loinc", ValueMapDictionary.load(v2).getSource());
        DictionaryFileCache.load(v2, ValueMapDictionary.Profile.FORMAT);
        assertEquals(h2, DictionaryFileCache.header(v2), "the cached header is the read one");
    }


    /**
     * Lane 1 LOW-5: whether a type / version would bind, by the reader's own rule and without
     * loading the file.
     */
    @Test
    void isInstalledFollowsTheResolveRule_withoutLoadingTheFile_l5(@TempDir Path aFlat)
        throws IOException
    {
        Path f = TestStore.versioned(store, "meddra", "26.0", TestStore.meddra("26.0", false));
        DictionaryStoreReader reader = new DictionaryStoreReader(store);
        assertTrue(reader.isInstalled("meddra", "26.0"));
        assertTrue(reader.isInstalled("MedDRA", "26.0"), "the type is case-folded as in resolve");
        assertTrue(reader.isInstalled("meddra", null), "blank: the most recent installed");
        assertFalse(reader.isInstalled("meddra", "27.1"));
        assertFalse(reader.isInstalled("meddra", "../x"), "never a path from an invalid token");
        assertFalse(reader.isInstalled("loinc", null));
        assertFalse(DictionaryFileCache.isCached(f));
        Files.createDirectories(store.resolve("meddra").resolve("28.0"));
        assertFalse(reader.isInstalled("meddra", "28.0"), "a directory without the type's file");

        Path flat = TestStore.flat(aFlat, "loinc",
                TestStore.House.of("loinc").codeLevel("LOINC", "1-1"));
        DictionaryStoreReader flatReader = new DictionaryStoreReader(aFlat);
        assertTrue(flatReader.isInstalled("loinc", null), "a flat file binds a blank request");
        assertFalse(flatReader.isInstalled("loinc", "2.80"), "its declared version is none");
        assertFalse(flatReader.isInstalled("unii", null));
        assertFalse(DictionaryFileCache.isCached(flat));
        assertFalse(new DictionaryStoreReader(aFlat.resolve("missing")).isInstalled("loinc", null));
    }
}
