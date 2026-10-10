package net.cumba.datatable.dictionary.house;

// OSS-IDENTITY datatable-dictionary: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * PLAN-dictionary-backed-formats B1 (plan D-B0-2): one model class, two load profiles. Over every
 * engine dummy file and every hand-written fixture, the {@link ValueMapDictionary.Profile#FORMAT}
 * load (streamed, the format subset) answers the format-path read API exactly as the
 * {@link ValueMapDictionary.Profile#FULL} load (the engine's tree parse) — the Phase A cross-reader
 * drift guard, now inside the module:
 * <ul>
 * <li>header and {@code hasContent} equal;</li>
 * <li>for every level and every term / code, with case variants and non-members: {@code isMember}
 * equal in both profiles and equal to the engine's {@code caseMatches} (DF4 exact);</li>
 * <li>{@code hasLevel}, {@code decodes} and {@code decode} equal; every house-format-1 decode is a
 * value the engine's {@code codeDecodePair(registry, code, decode, true)} confirms, and a code that
 * sits only in another registry never decodes (the engine's {@code hasDecode} falls through to it —
 * plan D2 / DF23);</li>
 * <li>a FORMAT load refuses the engine-only lookups instead of answering "no".</li>
 * </ul>
 */
class ProfileEquivalenceTest
{

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * House format 1: the level each type's codes decode at, and the registry that decodes them.
     */
    private static final Map<String, Map.Entry<String, String>> V1_DECODE_LEVELS = Map.of("loinc",
            Map.entry("LOINC", "loinc"), "unii", Map.entry("UNII", "unii"), "snomed",
            Map.entry("SNOMEDCD", "snomed"), "medrt", Map.entry("MEDRTCD", "medrt"));

    @TempDir
    Path tmp;

    static Path resources(String aDir)
    {
        return Path.of(System.getProperty("projectBasedir", "."), "src", "test", "resources", aDir);
    }


    static List<Path> fixtures() throws IOException
    {
        List<Path> out = new ArrayList<>();
        for (String dir : List.of("engine-dictionaries", "profile-fixtures"))
        {
            try (Stream<Path> files = Files.list(resources(dir)))
            {
                out.addAll(files.filter(p -> p.toString().endsWith(".json")).sorted().toList());
            }
        }
        assertTrue(out.size() >= 17, "fixtures: " + out.size());
        return out;
    }


    @Test
    void headerAndContentAreEqualInBothProfiles() throws IOException
    {
        for (Path file : fixtures())
        {
            ValueMapDictionary full = ValueMapDictionary.load(file);
            ValueMapDictionary format = HouseDictionaryReader.read(file);
            String what = file.getFileName().toString();
            assertEquals(ValueMapDictionary.Profile.FULL, full.getProfile(), what);
            assertEquals(ValueMapDictionary.Profile.FORMAT, format.getProfile(), what);
            assertEquals(full.getType(), format.getType(), what);
            assertEquals(full.getVersion(), format.getVersion(), what);
            assertEquals(full.getRetrieved(), format.getRetrieved(), what);
            assertEquals(full.getSource(), format.getSource(), what);
            assertEquals(full.houseFormat(), format.houseFormat(), what);
            assertEquals(full.hasContent(), format.hasContent(), what);
        }
    }


    @Test
    void membershipIsEqualInBothProfilesAndIsTheEnginesCaseMatches() throws IOException
    {
        int probes = 0;
        for (Path file : fixtures())
        {
            ValueMapDictionary full = ValueMapDictionary.load(file);
            ValueMapDictionary format = HouseDictionaryReader.read(file);
            JsonNode levels = MAPPER.readTree(file.toFile()).path("levels");
            for (Map.Entry<String, JsonNode> level : levels.properties())
            {
                String l = level.getKey();
                assertEquals(full.hasLevel(l), format.hasLevel(l), file.getFileName() + " " + l);
                assertEquals(full.decodes(l), format.decodes(l), file.getFileName() + " " + l);
                for (Map.Entry<String, JsonNode> term : level.getValue().properties())
                {
                    for (String probe : variants(term.getKey(), term.getValue().asText()))
                    {
                        String what = file.getFileName() + " " + l + " '" + probe + "'";
                        boolean member = full.caseMatches(l, probe);
                        assertEquals(member, full.isMember(l, probe), what);
                        assertEquals(member, format.isMember(l, probe), what);
                        assertEquals(full.decode(l, probe), format.decode(l, probe), what);
                        assertFalse(format.isMember("NOPE", probe));
                        probes++;
                    }
                }
            }
        }
        assertTrue(probes > 500, "probes: " + probes);
    }


    static List<String> variants(String aKey, String aValue)
    {
        List<String> out = new ArrayList<>(List.of(aKey, aValue, aValue.toLowerCase(Locale.ROOT),
                aValue.toUpperCase(Locale.ROOT), swapCase(aValue), aValue + "x", " " + aValue,
                aValue + " ", "ZZZ", ""));
        if (!aValue.isEmpty())
        {
            out.add(aValue.substring(0, 1).toLowerCase(Locale.ROOT) + aValue.substring(1));
        }
        return out;
    }


    private static String swapCase(String aText)
    {
        StringBuilder sb = new StringBuilder(aText.length());
        for (int i = 0; i < aText.length(); i++)
        {
            char c = aText.charAt(i);
            sb.append(
                    Character.isUpperCase(c) ? Character.toLowerCase(c) : Character.toUpperCase(c));
        }
        return sb.toString();
    }


    @Test
    void decodesAreTheValuesThePairLookupConfirms_andNeverAFallThrough() throws IOException
    {
        int confirmed = 0;
        int refusedFallThroughs = 0;
        for (Path file : fixtures())
        {
            ValueMapDictionary full = ValueMapDictionary.load(file);
            ValueMapDictionary format = HouseDictionaryReader.read(file);
            if (full.houseFormat() != ValueMapDictionary.HOUSE_FORMAT_1)
            {
                continue; // v2 decodes are pinned by the decode-section tests
            }
            Map.Entry<String, String> levelAndRegistry = V1_DECODE_LEVELS
                    .get(full.getType().toLowerCase(Locale.ROOT));
            JsonNode pairs = MAPPER.readTree(file.toFile()).path("pairs");
            for (Map.Entry<String, JsonNode> registry : pairs.properties())
            {
                for (Map.Entry<String, JsonNode> pair : registry.getValue().properties())
                {
                    String code = pair.getKey();
                    String decode = pair.getValue().asText();
                    assertTrue(full.codeDecodePair(registry.getKey(), code, decode, true),
                            "the full load confirms its own file: " + file.getFileName());
                    if (levelAndRegistry == null)
                    {
                        continue; // whodrug / neoplasm: no house-format-1 code level decodes
                    }
                    String level = levelAndRegistry.getKey();
                    String decoded = format.decode(level, code);
                    assertEquals(full.decode(level, code), decoded,
                            file.getFileName() + " " + code);
                    if (registry.getKey().equals(levelAndRegistry.getValue()))
                    {
                        if (format.isMember(level, code))
                        {
                            assertEquals(decode, decoded, file.getFileName() + " " + code);
                            assertTrue(full.codeDecodePair(registry.getKey(), code, decoded, true));
                            confirmed++;
                        }
                        else
                        {
                            assertNull(decoded, "a non-member never decodes: " + code);
                        }
                    }
                    else if (!registry.getValue().isEmpty() && format.isMember(level, code)
                            && !pairs.path(levelAndRegistry.getValue()).has(code))
                    {
                        // the code sits ONLY in another registry: hasDecode falls through to it,
                        // decode never does (plan D2 / DF23)
                        assertTrue(full.hasDecode(null, code, true));
                        assertNull(decoded,
                                file.getFileName() + " " + registry.getKey() + " " + code);
                        refusedFallThroughs++;
                    }
                }
            }
        }
        assertTrue(confirmed >= 10, "confirmed decodes: " + confirmed);
        assertTrue(refusedFallThroughs >= 1, "the medrt-base case is in the fixtures");
    }


    @Test
    void theDecodesSectionIsReadByBothProfiles_andInternedWithTheLevels() throws IOException
    {
        Path file = resources("profile-fixtures").resolve("meddra-v2-decodes.json");
        ValueMapDictionary full = ValueMapDictionary.load(file);
        ValueMapDictionary format = HouseDictionaryReader.read(file);
        assertEquals(ValueMapDictionary.HOUSE_FORMAT_2, full.houseFormat());
        JsonNode decodes = MAPPER.readTree(file.toFile()).path(ValueMapDictionary.DECODES);
        int checked = 0;
        for (Map.Entry<String, JsonNode> level : decodes.properties())
        {
            for (Map.Entry<String, JsonNode> e : level.getValue().properties())
            {
                if (full.isMember(level.getKey(), e.getKey()))
                {
                    assertEquals(e.getValue().asText(), full.decode(level.getKey(), e.getKey()));
                    assertEquals(e.getValue().asText(), format.decode(level.getKey(), e.getKey()));
                    checked++;
                }
            }
        }
        assertTrue(checked >= 1, "decodes checked: " + checked);
    }


    @Test
    void aFormatLoadRefusesTheEngineOnlyLookups() throws IOException
    {
        Path file = resources("engine-dictionaries").resolve("meddra.json");
        ValueMapDictionary format = HouseDictionaryReader.read(file);
        assertThrows(IllegalStateException.class, () -> format.hasDecode(null, "X", true));
        assertThrows(IllegalStateException.class,
                () -> format.codeDecodePair(null, "X", "Y", true));
        assertThrows(IllegalStateException.class,
                () -> format.onHierarchyPath("PT", "A", "SOC", "B", true));
        assertThrows(IllegalStateException.class,
                () -> format.onHierarchyPathAsBefore("PT", "A", "SOC", "B", true));
        assertThrows(IllegalStateException.class, () -> format.answersHierarchyAt("PT"));
        assertThrows(IllegalStateException.class, format::hasHierarchyByLevel);
        // the full load answers them
        ValueMapDictionary full = ValueMapDictionary.load(file);
        assertFalse(full.hasDecode(null, "no such code", true));
        assertTrue(full.toString().contains("FULL"), full.toString());
        assertTrue(format.toString().contains("FORMAT"), format.toString());
    }


    @Test
    void theCacheServesAFormatRequestFromAFullEntry_neverTheReverse() throws IOException
    {
        Path file = tmp.resolve("meddra.json");
        Files.copy(resources("engine-dictionaries").resolve("meddra.json"), file);

        // FORMAT first: a FULL request re-parses and replaces the entry ...
        ValueMapDictionary format = DictionaryFileCache.load(file,
                ValueMapDictionary.Profile.FORMAT);
        assertEquals(ValueMapDictionary.Profile.FORMAT, format.getProfile());
        assertSame(format, DictionaryFileCache.load(file, ValueMapDictionary.Profile.FORMAT));
        ValueMapDictionary full = DictionaryFileCache.load(file);
        assertEquals(ValueMapDictionary.Profile.FULL, full.getProfile(),
                "a FORMAT entry never serves the engine");
        // ... which then serves both
        assertSame(full, DictionaryFileCache.load(file, ValueMapDictionary.Profile.FORMAT),
                "one residency: the full parse serves the format path (DF12)");
        assertSame(full, DictionaryFileCache.load(file, ValueMapDictionary.Profile.FULL));
        assertTrue(DictionaryFileCache.isCached(file));
        assertNotNull(DictionaryFileCache.header(file).version() == null ? "" : "v");
    }
}
