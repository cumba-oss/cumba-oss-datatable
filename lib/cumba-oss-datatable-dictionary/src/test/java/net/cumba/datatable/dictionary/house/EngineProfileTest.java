package net.cumba.datatable.dictionary.house;

// OSS-IDENTITY datatable-dictionary: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * LANE-dictionary-followup (owner 2026-10-10): the conformance engine's load
 * ({@link ValueMapDictionary.Profile#ENGINE}) skips the one section no engine lookup reads — the
 * house-format-2 {@code decodes} — so the CLI / REST engine (dictionary formats off) no longer
 * holds it; it answers every engine lookup exactly as the {@code FULL} load, and the format path
 * keeps decoding.
 */
class EngineProfileTest
{

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * A house-format-2 WHODrug file in the converter's shape (no fixture carries one): the
     * {@code DRUGCD} level stays in the engine's load — an authored {@code dictionary_term_type}
     * reaches it — while {@code decodes.ATCCD} / {@code decodes.DRUGCD} go.
     */
    static final String WHODRUG_V2 = """
            {"type":"whodrug","version":"SEP_2020","house_format":2,
             "levels":{"PT":{"ASPIRIN":"Aspirin","PARACETAMOL":"Paracetamol"},
                       "ATC":{"ANALGESICS":"Analgesics"},"ATCCD":{"N02":"N02"},
                       "DRUGCD":{"00000101001":"00000101001","00000101002":"00000101002",
                                 "00002001001":"00002001001"}},
             "hierarchy":{},
             "pairs":{"whodrug":{"ASPRO CLEAR":"Aspirin","Paracetamol":"Paracetamol"}},
             "attributes":{"form":{"Aspirin":"tablet"}},
             "decodes":{"ATCCD":{"N02":"Analgesics"},
                        "DRUGCD":{"00000101001":"Aspirin","00000101002":"ASPRO CLEAR",
                                  "00002001001":"Paracetamol"}}}
            """;

    @TempDir
    Path tmp;

    private Path v2MedDra(Path aFile) throws IOException
    {
        Files.createDirectories(aFile.getParent());
        return Files.copy(ProfileEquivalenceTest.resources("profile-fixtures")
                .resolve("meddra-v2-decodes.json"), aFile);
    }


    private static void assertHoldsNoDecodes(ValueMapDictionary aDict)
    {
        assertNotNull(aDict);
        assertNotEquals(ValueMapDictionary.Profile.FULL, aDict.getProfile(),
                "the engine's load is not the full one");
        assertTrue(aDict.isMember("PTCD", "10019211"), "the level stays: " + aDict);
        assertThrows(IllegalStateException.class, () -> aDict.decode("PTCD", "10019211"),
                "a load without decodes refuses decode instead of answering \"no decode\"");
        assertThrows(IllegalStateException.class, () -> aDict.decodes("PTCD"));
    }


    @Test
    void theEnginesFlatAndVersionedLoadsHoldNoDecodes() throws IOException
    {
        v2MedDra(tmp.resolve("flat").resolve("meddra.json"));
        assertHoldsNoDecodes(RuntimeDictionaryProvider.loadDirectory(tmp.resolve("flat"))
                .dictionaryOf("meddra"));

        v2MedDra(tmp.resolve("store").resolve("meddra").resolve("28.0").resolve("meddra.json"));
        assertHoldsNoDecodes(DictionaryStore.load(tmp.resolve("store"), Map.of("meddra", "28.0"))
                .dictionaryOf("meddra"));
    }


    @Test
    void theFormatPathStillDecodesAfterAnEngineLoad_fromOneFullParse() throws IOException
    {
        Path file = v2MedDra(tmp.resolve("meddra").resolve("28.0").resolve("meddra.json"));
        ValueMapDictionary engine = RuntimeDictionaryProvider.loadDirectory(file.getParent())
                .dictionaryOf("meddra");
        assertEquals(ValueMapDictionary.Profile.ENGINE, engine.getProfile());
        ValueMapDictionary format = DictionaryFileCache.load(file,
                ValueMapDictionary.Profile.FORMAT);
        assertNotSame(engine, format, "the engine's load does not serve the format path");
        assertEquals(ValueMapDictionary.Profile.FULL, format.getProfile(),
                "the second profile's request parses the union once");
        assertEquals("Headache", format.decode("PTCD", "10019211"));
        // the union now serves the engine too: one residency
        assertSame(format,
                RuntimeDictionaryProvider.loadDirectory(file.getParent()).dictionaryOf("meddra"));
        assertSame(format, DictionaryFileCache.load(file, ValueMapDictionary.Profile.FORMAT));
    }


    @Test
    void theProfilesServeOnlyThemselvesAndTheUnionServesAll()
    {
        for (ValueMapDictionary.Profile have : ValueMapDictionary.Profile.values())
        {
            for (ValueMapDictionary.Profile want : ValueMapDictionary.Profile.values())
            {
                assertEquals(have == ValueMapDictionary.Profile.FULL || have == want,
                        have.serves(want), have + " serves " + want);
                ValueMapDictionary.Profile join = have.join(want);
                assertTrue(join.serves(have) && join.serves(want), have + " join " + want);
                assertEquals(have == want ? have : ValueMapDictionary.Profile.FULL, join,
                        "the least profile serving both: " + have + " join " + want);
            }
        }
    }


    /**
     * The cache's rule — an entry serves a weaker profile, never a stronger one — over the three
     * profiles: an ENGINE entry serves the engine, a FORMAT request on it (or an ENGINE request on
     * a FORMAT entry) parses the union once, and a stale entry's profile is not joined.
     */
    @Test
    void theCacheServesEachProfileOnlyFromAnEntryThatServesIt() throws IOException
    {
        Path file = v2MedDra(tmp.resolve("a").resolve("meddra.json"));
        ValueMapDictionary engine = DictionaryFileCache.load(file);
        assertEquals(ValueMapDictionary.Profile.ENGINE, engine.getProfile());
        assertSame(engine, DictionaryFileCache.load(file, ValueMapDictionary.Profile.ENGINE));
        assertEquals("meddra", DictionaryFileCache.header(file).type(),
                "every profile holds the header");
        assertEquals(HouseDictionaryReader.readHeader(file), DictionaryFileCache.header(file));
        ValueMapDictionary full = DictionaryFileCache.load(file, ValueMapDictionary.Profile.FORMAT);
        assertEquals(ValueMapDictionary.Profile.FULL, full.getProfile());
        assertSame(full, DictionaryFileCache.load(file));
        assertSame(full, DictionaryFileCache.load(file, ValueMapDictionary.Profile.FULL));

        // the reverse order
        Path other = v2MedDra(tmp.resolve("b").resolve("meddra.json"));
        ValueMapDictionary format = DictionaryFileCache.load(other,
                ValueMapDictionary.Profile.FORMAT);
        assertEquals(ValueMapDictionary.Profile.FORMAT, format.getProfile());
        ValueMapDictionary union = DictionaryFileCache.load(other);
        assertEquals(ValueMapDictionary.Profile.FULL, union.getProfile(),
                "a FORMAT entry never serves the engine; the engine's request parses the union");
        assertSame(union, DictionaryFileCache.load(other, ValueMapDictionary.Profile.FORMAT));

        // a stale entry is replaced in the requested profile, not joined with the stale one's
        Path third = v2MedDra(tmp.resolve("c").resolve("meddra.json"));
        DictionaryFileCache.load(third);
        Files.setLastModifiedTime(third, FileTime.from(Instant.parse("2030-01-01T00:00:00Z")));
        assertEquals(ValueMapDictionary.Profile.FORMAT,
                DictionaryFileCache.load(third, ValueMapDictionary.Profile.FORMAT).getProfile());
    }


    @Test
    void theEnginesProviderTakesEngineAndFullLoads_neverAFormatLoad() throws IOException
    {
        Path file = v2MedDra(tmp.resolve("meddra.json"));
        for (ValueMapDictionary.Profile p : List.of(ValueMapDictionary.Profile.ENGINE,
                ValueMapDictionary.Profile.FULL))
        {
            RuntimeDictionaryProvider provider = new RuntimeDictionaryProvider(
                    Map.of("meddra", ValueMapDictionary.load(file, p)));
            assertTrue(provider.isValidTerm("meddra", "PT", "Headache"), p.name());
        }
        ValueMapDictionary format = ValueMapDictionary.load(file,
                ValueMapDictionary.Profile.FORMAT);
        assertEquals(ValueMapDictionary.Profile.FORMAT, format.getProfile());
        assertThrows(IllegalArgumentException.class,
                () -> new RuntimeDictionaryProvider(Map.of("meddra", format)));
    }


    /**
     * The engine-equality guard: over every engine dummy file, every hand-written fixture and a
     * house-format-2 WHODrug file, the ENGINE load answers every engine lookup — membership (any
     * level, a blank and an absent one), case, hierarchy (both paths, both case flags), pair,
     * decode presence, content and header — exactly as the FULL load.
     */
    @Test
    void theEngineLoadAnswersEveryEngineLookupAsTheFullLoad() throws IOException
    {
        List<Path> files = new ArrayList<>(ProfileEquivalenceTest.fixtures());
        Path whodrug = Files.createDirectories(tmp.resolve("v2")).resolve("whodrug.json");
        Files.writeString(whodrug, WHODRUG_V2);
        files.add(whodrug);
        long probes = 0;
        for (Path file : files)
        {
            probes += compare(file);
        }
        assertTrue(probes > 1_000_000, "probes: " + probes);

        // the DRUGCD level is the engine's (an authored dictionary_term_type reaches it) ...
        ValueMapDictionary engine = ValueMapDictionary.load(whodrug,
                ValueMapDictionary.Profile.ENGINE);
        assertTrue(engine.isValidTerm("DRUGCD", "00000101002"));
        assertTrue(engine.caseMatches("DRUGCD", "00000101002"));
        assertTrue(engine.isValidTerm(null, "00002001001"), "a blank level scans DRUGCD too");
        // ... the decodes are not
        assertThrows(IllegalStateException.class, () -> engine.decode("DRUGCD", "00000101002"));
        assertEquals("ASPRO CLEAR",
                ValueMapDictionary.load(whodrug).decode("DRUGCD", "00000101002"));
        assertFalse(engine.hasDecode(null, "00000101002", true),
                "decode presence never read the decodes section");
    }


    /** Compares the two loads of one file over every probe; returns the number of probes. */
    private static long compare(Path aFile) throws IOException
    {
        ValueMapDictionary full = ValueMapDictionary.load(aFile);
        ValueMapDictionary engine = ValueMapDictionary.load(aFile,
                ValueMapDictionary.Profile.ENGINE);
        String f = aFile.getFileName().toString();
        assertEquals(ValueMapDictionary.Profile.ENGINE, engine.getProfile(), f);
        assertEquals(full.getType(), engine.getType(), f);
        assertEquals(full.getVersion(), engine.getVersion(), f);
        assertEquals(full.getSource(), engine.getSource(), f);
        assertEquals(full.getRetrieved(), engine.getRetrieved(), f);
        assertEquals(full.houseFormat(), engine.houseFormat(), f);
        assertEquals(full.hasContent(), engine.hasContent(), f);
        assertEquals(full.hasHierarchyByLevel(), engine.hasHierarchyByLevel(), f);
        assertEquals(full.toString().replace("FULL", "?"), engine.toString().replace("ENGINE", "?"),
                f);

        JsonNode root = MAPPER.readTree(aFile.toFile());
        Set<String> vocabulary = new LinkedHashSet<>();
        collect(root, vocabulary, 0);
        List<String> terms = new ArrayList<>();
        for (String s : vocabulary)
        {
            terms.addAll(List.of(s, s.toUpperCase(Locale.ROOT), s.toLowerCase(Locale.ROOT)));
        }
        terms.addAll(List.of("ZZZ", ""));
        List<String> parents = new ArrayList<>(vocabulary);
        parents.add("ZZZ");
        Set<String> levelNames = new LinkedHashSet<>();
        root.path("levels").fieldNames().forEachRemaining(levelNames::add);
        root.path(ValueMapDictionary.HIERARCHY_BY_LEVEL).properties().forEach(e ->
        {
            levelNames.add(e.getKey());
            e.getValue().properties()
                    .forEach(t -> t.getValue().fieldNames().forEachRemaining(levelNames::add));
        });
        levelNames.addAll(List.of("NOPE", "LLT", ""));
        List<String> levels = new ArrayList<>(levelNames);
        levels.add(null);
        Set<String> registryNames = new LinkedHashSet<>();
        root.path("pairs").fieldNames().forEachRemaining(registryNames::add);
        root.path("attributes").fieldNames().forEachRemaining(registryNames::add);
        registryNames.addAll(List.of(full.getType(), "NOPE"));
        List<String> registries = new ArrayList<>(registryNames);
        registries.add(null);

        long probes = 0;
        for (String level : levels)
        {
            if (level != null)
            {
                same(full.hasLevel(level), engine.hasLevel(level), f, "hasLevel", level);
                same(full.answersHierarchyAt(level), engine.answersHierarchyAt(level), f,
                        "answersHierarchyAt", level);
            }
            for (String term : terms)
            {
                same(full.isValidTerm(level, term), engine.isValidTerm(level, term), f,
                        "isValidTerm", level, term);
                same(full.caseMatches(level, term), engine.caseMatches(level, term), f,
                        "caseMatches", level, term);
                if (level != null)
                {
                    same(full.isMember(level, term), engine.isMember(level, term), f, "isMember",
                            level, term);
                }
                probes += 3;
            }
            same(full.isValidTerm(level, null), engine.isValidTerm(level, null), f, "isValidTerm",
                    level, null);
        }
        for (String childLevel : levels)
        {
            for (String parentLevel : levels)
            {
                for (String term : terms)
                {
                    for (String parent : parents)
                    {
                        for (boolean cs : new boolean[]
                        {
                                true, false
                        })
                        {
                            same(full.onHierarchyPath(childLevel, term, parentLevel, parent, cs),
                                    engine.onHierarchyPath(childLevel, term, parentLevel, parent,
                                            cs),
                                    f, "onHierarchyPath", childLevel, term, parentLevel, parent);
                            same(full.onHierarchyPathAsBefore(childLevel, term, parentLevel, parent,
                                    cs),
                                    engine.onHierarchyPathAsBefore(childLevel, term, parentLevel,
                                            parent, cs),
                                    f, "onHierarchyPathAsBefore", childLevel, term, parentLevel,
                                    parent);
                            probes += 2;
                        }
                    }
                }
            }
        }
        for (String registry : registries)
        {
            for (String code : terms)
            {
                for (boolean cs : new boolean[]
                {
                        true, false
                })
                {
                    same(full.hasDecode(registry, code, cs), engine.hasDecode(registry, code, cs),
                            f, "hasDecode", registry, code);
                    probes++;
                    for (String decode : parents)
                    {
                        same(full.codeDecodePair(registry, code, decode, cs),
                                engine.codeDecodePair(registry, code, decode, cs), f,
                                "codeDecodePair", registry, code, decode);
                        probes++;
                    }
                }
            }
        }
        return probes;
    }


    /** Every key below the top level and every scalar value, at any depth. */
    private static void collect(JsonNode aNode, Set<String> aOut, int aDepth)
    {
        if (aNode.isObject())
        {
            aNode.properties().forEach(e ->
            {
                if (aDepth >= 1)
                {
                    aOut.add(e.getKey());
                }
                collect(e.getValue(), aOut, aDepth + 1);
            });
        }
        else if (aNode.isArray())
        {
            aNode.forEach(n -> collect(n, aOut, aDepth + 1));
        }
        else if (aNode.isTextual() && aDepth >= 2)
        {
            aOut.add(aNode.asText());
        }
    }


    private static void same(boolean aFull, boolean aEngine, String aFile, String aLookup,
            Object... aArgs)
    {
        if (aFull != aEngine)
        {
            fail(aFile + " " + aLookup + Arrays.toString(aArgs) + ": FULL " + aFull + ", ENGINE "
                    + aEngine);
        }
    }
}
