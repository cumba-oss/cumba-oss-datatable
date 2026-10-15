package net.cumba.datatable.dictionary.house;

// OSS-IDENTITY datatable-dictionary: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        // B-T lane 1 MED-3: interned — a PT name in levels.PT and decodes.PTCD is ONE String
        String decoded = full.decode("PTCD", "10019211");
        assertEquals("Headache", decoded);
        assertSame(full.preferredForm("PT", "Headache"), decoded,
                "the decode is the level's own instance (memory first, plan §3.7)");
        assertSame(format.preferredForm("PT", "Headache"), format.decode("PTCD", "10019211"),
                "the FORMAT load interns through one map too");
    }


    /**
     * B-T lane 1 MED-3: the FULL load interns {@code pairs} and {@code attributes} through the same
     * map as {@code levels} and {@code decodes} — a WHODrug trade name is a {@code decodes.DRUGCD}
     * value and a {@code pairs.whodrug} key, and a preferred name is a {@code levels.PT} value and
     * a {@code pairs.whodrug} value: each is held once. Equal strings, so no answer changes.
     */
    @Test
    void aFullLoadInternsPairsAndAttributesWithTheLevelsAndDecodes()
    {
        ValueMapDictionary full = ValueMapDictionary.parse(json("""
                {"type":"whodrug","house_format":2,
                 "levels":{"PT":{"ASPIRIN":"Aspirin"},
                           "DRUGCD":{"00000101001":"00000101001","00000101002":"00000101002"}},
                 "pairs":{"whodrug":{"ASPRO CLEAR":"Aspirin"}},
                 "attributes":{"form":{"Aspirin":"Aspirin"}},
                 "decodes":{"DRUGCD":{"00000101001":"Aspirin","00000101002":"ASPRO CLEAR"}}}
                """));
        String tradeName = full.decode("DRUGCD", "00000101002");
        assertEquals("ASPRO CLEAR", tradeName);
        String pairKey = full.pairRegistry("whodrug").keySet().iterator().next();
        assertEquals(tradeName, pairKey);
        assertSame(pairKey, tradeName, "the trade name is held once (pairs key = decode)");
        String preferred = full.preferredForm("PT", "Aspirin");
        assertSame(preferred, full.pairRegistry("whodrug").get("ASPRO CLEAR"),
                "a pairs value is the level's instance");
        assertSame(preferred, full.decode("DRUGCD", "00000101001"));
        assertSame(preferred, full.attributeMap("form").get("Aspirin"),
                "an attribute value is the level's instance");
        assertSame(preferred, full.attributeMap("form").keySet().iterator().next(),
                "an attribute key too");
        assertTrue(full.codeDecodePair("whodrug", "ASPRO CLEAR", "Aspirin", true),
                "the pair lookup answers as before");
    }


    private static JsonNode json(String aText)
    {
        try
        {
            return MAPPER.readTree(aText);
        }
        catch (IOException e)
        {
            throw new java.io.UncheckedIOException(e);
        }
    }


    /**
     * B-T lane 1 LOW-4: on malformed input the two profiles agree — a container where a scalar
     * belongs is skipped by both (it is no term, no decode, no ancestor), and a blank {@code type}
     * is no type in both (the file stem keys the registry). Before, FULL kept containers as
     * {@code ""} and the blank type verbatim while FORMAT skipped them, so the browser's status
     * depended on which profile parsed the file first. Since PS-D4-T a {@code null} term value is
     * no term either (only a JSON string is), so the membership oracle is {@code isTextual()}.
     */
    @Test
    void onMalformedInputBothProfilesAgree() throws IOException
    {
        List<String[]> cases = List.of(new String[]
        {
                "meddra", "{\"type\":\"meddra\",\"levels\":{\"PT\":{\"HEADACHE\":{}}}}"
        }, new String[]
        {
                "meddra",
                "{\"type\":\"meddra\",\"levels\":{\"PT\":{\"HEADACHE\":[\"x\"],\"A\":\"A\"}}}"
        }, new String[]
        {
                "loinc", "{\"type\":\"  \",\"levels\":{\"LOINC\":{\"1-1\":\"1-1\"}},"
                        + "\"pairs\":{\"loinc\":{\"1-1\":\"Glucose\"}}}"
        }, new String[]
        {
                "loinc", "{\"type\":\"loinc\",\"levels\":{\"LOINC\":{\"1-1\":\"1-1\"}},"
                        + "\"pairs\":{\"loinc\":{\"1-1\":{\"x\":1}}}}"
        }, new String[]
        {
                "neoplasm", "{\"type\":\"neoplasm\",\"attributes\":{\"neoplasm\":{\"A\":{}}}}"
        }, new String[]
        {
                "snomed", "{\"type\":\"snomed\",\"hierarchy\":{\"A\":[{}]}}"
        }, new String[]
        {
                "meddra", "{\"type\":\"meddra\",\"hierarchy_by_level\":{\"PT\":{\"X\":{}}}}"
        }, new String[]
        {
                "meddra",
                "{\"type\":\"meddra\",\"hierarchy_by_level\":" + "{\"PT\":{\"X\":{\"SOC\":[{}]}}}}"
        }, new String[]
        {
                "meddra", "{\"type\":\"meddra\",\"levels\":{\"PTCD\":{\"1\":\"1\"}},"
                        + "\"decodes\":{\"PTCD\":{\"1\":{}}}}"
        }, new String[]
        {
                "unii", "{\"type\":\"unii\",\"levels\":{\"UNII\":{\"A\":null}},"
                        + "\"pairs\":{\"unii\":{\"A\":null},\"other\":{\"B\":{}}}}"
        }, new String[]
        {
                // PS-D4-U: an ancestor counts only as a JSON string, in both profiles
                "snomed", "{\"type\":\"snomed\",\"hierarchy\":{\"A\":[null,5,true]}}"
        }, new String[]
        {
                "meddra", "{\"type\":\"meddra\",\"hierarchy_by_level\":"
                        + "{\"PT\":{\"X\":{\"SOC\":[null,7]}}}}"
        });
        int compared = 0;
        for (String[] c : cases)
        {
            Path named = Files.createDirectories(tmp.resolve("case" + compared))
                    .resolve(c[0] + ".json");
            Files.writeString(named, c[1]);
            ValueMapDictionary full = ValueMapDictionary.load(named);
            ValueMapDictionary format = HouseDictionaryReader.read(named);
            String what = c[1];
            assertEquals(full.getType(), format.getType(), what);
            assertEquals(full.hasContent(), format.hasContent(), what);
            assertEquals(full.toString().replace("FULL", "?"),
                    format.toString().replace("FORMAT", "?"), what);
            JsonNode levels = MAPPER.readTree(c[1]).path("levels");
            for (String level : List.of("PT", "PTCD", "LOINC", "UNII", "NOPE"))
            {
                assertEquals(full.hasLevel(level), format.hasLevel(level), what + " " + level);
                assertEquals(full.decodes(level), format.decodes(level), what + " " + level);
                for (String key : List.of("HEADACHE", "A", "1-1", "1", "", "null"))
                {
                    assertEquals(full.isMember(level, key), format.isMember(level, key),
                            what + " " + level + " " + key);
                    assertEquals(full.isValidTerm(level, key),
                            levels.path(level).path(key.toUpperCase(Locale.ROOT)).isTextual(),
                            "FULL membership holds only string terms: " + what + " " + key);
                    assertEquals(full.decode(level, key), format.decode(level, key),
                            what + " " + level + " " + key);
                }
            }
            compared++;
        }
        assertEquals(cases.size(), compared);
        // the blank type: both key the registry by the file stem, so the LOINC code decodes
        ValueMapDictionary blankType = ValueMapDictionary
                .load(tmp.resolve("case2").resolve("loinc.json"));
        assertEquals("", blankType.getType());
        assertEquals("Glucose", blankType.decode("LOINC", "1-1"));
        // the container cases are no content in either profile
        assertFalse(ValueMapDictionary.load(tmp.resolve("case4").resolve("neoplasm.json"))
                .hasContent());
        assertFalse(
                ValueMapDictionary.load(tmp.resolve("case5").resolve("snomed.json")).hasContent());
        assertFalse(
                ValueMapDictionary.load(tmp.resolve("case6").resolve("meddra.json")).hasContent());
        assertFalse(
                ValueMapDictionary.load(tmp.resolve("case7").resolve("meddra.json")).hasContent());
        // PS-D4-U: lists of non-string ancestors are no content in either profile (the last two)
        assertFalse(ValueMapDictionary
                .load(tmp.resolve("case" + (cases.size() - 2)).resolve("snomed.json")).hasContent(),
                "bare-name ancestors null / 5 / true");
        assertFalse(ValueMapDictionary
                .load(tmp.resolve("case" + (cases.size() - 1)).resolve("meddra.json")).hasContent(),
                "level-scoped ancestors null / 7");
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
        // B-T lane 1 LOW-5: the header served from the cached FULL parse is the file's
        HouseDictionaryReader.Header header = DictionaryFileCache.header(file);
        assertEquals(full.getType(), header.type());
        assertEquals("meddra", header.type());
        assertEquals(full.getVersion(), header.version());
        assertEquals(full.houseFormat(), header.houseFormat());
        assertEquals(HouseDictionaryReader.readHeader(file), header,
                "equal to the header-only streaming read");
    }


    /**
     * PS-D4 (PLAN-pitest-triage-defects, owner 2026-10-15): a header field ({@code type},
     * {@code version}, {@code source}, {@code retrieved}) counts only as a non-blank JSON string;
     * any other JSON value reads as ABSENT, the same in {@link ValueMapDictionary#load}, in
     * {@link HouseDictionaryReader#read} and in {@link HouseDictionaryReader#readHeader}. Before,
     * {@code "version": 2.80} read as {@code "2.80"} in one and {@code "2.8"} in the other, and
     * {@code true} as nothing in one and {@code "true"} in the other. PS-D4-R2: an integral
     * {@code house_format} outside {@code int} is malformed, so it is format 1 in all three
     * (before: the streaming read threw, the tree read truncated).
     */
    @Test
    void onNonStringHeaderValuesAllReadersAgree() throws IOException
    {
        List<String> values = List.of("2.80", "2", "true", "false", "{}", "[]", "null", "\"  \"");
        int n = 0;
        for (String field : List.of("version", "source", "retrieved", "type"))
        {
            List<String> heads = new ArrayList<>();
            for (String v : values)
            {
                heads.add("\"" + field + "\":" + v + ",");
            }
            heads.add("");
            for (String head : heads)
            {
                String text = "{" + head + "\"levels\":{\"PT\":{\"HEADACHE\":\"Headache\"}}}";
                Path file = Files.createDirectories(tmp.resolve("hdr" + n++))
                        .resolve("meddra.json");
                Files.writeString(file, text);
                ValueMapDictionary full = ValueMapDictionary.load(file);
                ValueMapDictionary format = HouseDictionaryReader.read(file);
                HouseDictionaryReader.Header header = HouseDictionaryReader.readHeader(file);
                // the absent value: null for the three provenance fields, "" (the stem keys) for
                // type
                assertNull(full.getVersion(), text);
                assertNull(format.getVersion(), text);
                assertNull(header.version(), text);
                assertNull(full.getSource(), text);
                assertNull(format.getSource(), text);
                assertNull(header.source(), text);
                assertNull(full.getRetrieved(), text);
                assertNull(format.getRetrieved(), text);
                assertNull(header.retrieved(), text);
                assertEquals("", full.getType(), text);
                assertEquals("", format.getType(), text);
                assertEquals("", header.type(), text);
                assertEquals("ValueMapDictionary(meddra, house format 1, FULL)", full.toString(),
                        text);
                assertEquals("ValueMapDictionary(meddra, house format 1, FORMAT)",
                        format.toString(), text);
                assertTrue(full.isMember("PT", "Headache") && format.isMember("PT", "Headache"),
                        text);
            }
        }
        assertEquals(4 * (values.size() + 1), n);
        // a sensitivity control: a string is still read, by all three
        Path good = Files.createDirectories(tmp.resolve("hdr-good")).resolve("meddra.json");
        Files.writeString(good, "{\"type\":\"meddra\",\"version\":\"2.80\",\"source\":\"s\","
                + "\"retrieved\":\"2026-10-15\",\"levels\":{}}");
        assertEquals("2.80", ValueMapDictionary.load(good).getVersion());
        assertEquals("2.80", HouseDictionaryReader.read(good).getVersion());
        assertEquals(new HouseDictionaryReader.Header("meddra", "2.80", "2026-10-15", 1, "s"),
                HouseDictionaryReader.readHeader(good));

        // PS-D4-R2: house_format outside int, or not integral, is format 1 in all three readers
        for (String hf : List.of("2147483648", "-2147483649", "1e3", "2.0", "\"2\""))
        {
            String text = "{\"type\":\"meddra\",\"house_format\":" + hf + ",\"levels\":{}}";
            Path file = Files.createDirectories(tmp.resolve("hf" + n++)).resolve("meddra.json");
            Files.writeString(file, text);
            int expected = "\"2\"".equals(hf) ? 2 : ValueMapDictionary.HOUSE_FORMAT_1;
            assertEquals(expected, ValueMapDictionary.load(file).houseFormat(), text);
            assertEquals(expected, HouseDictionaryReader.read(file).houseFormat(), text);
            assertEquals(expected, HouseDictionaryReader.readHeader(file).houseFormat(), text);
        }
    }


    /**
     * PS-D4-T (owner 2026-10-15, ⓘ): the same rule for the term VALUES of the sections both
     * profiles read ({@code levels}, {@code pairs}, {@code decodes}) — a non-string scalar is no
     * term, as a container already is (B-T LOW-4). Before, the streaming reader kept the raw token
     * text ({@code 2.80}) and the tree reader {@code asText()} ({@code 2.8}).
     */
    @Test
    void onNonStringSectionValuesBothProfilesAgree_andTheValueIsNoTerm() throws IOException
    {
        int n = 0;
        // the two containers carry a string INSIDE: a reader that forgot to skip the container
        // would read "x" as the next entry (the sensitivity control for the skip)
        for (String v : List.of("2.80", "2", "true", "false", "null", "{\"x\":\"y\"}", "[\"y\"]"))
        {
            String text = "{\"type\":\"meddra\",\"house_format\":2,"
                    + "\"levels\":{\"PT\":{\"HEADACHE\":" + v + ",\"NAUSEA\":\"Nausea\"},"
                    + "\"PTCD\":{\"1\":\"1\",\"2\":\"2\",\"x\":\"x\"}},"
                    + "\"decodes\":{\"PTCD\":{\"1\":" + v + ",\"2\":\"Nausea\"}}}";
            Path file = Files.createDirectories(tmp.resolve("sec" + n++)).resolve("meddra.json");
            Files.writeString(file, text);
            ValueMapDictionary full = ValueMapDictionary.load(file);
            ValueMapDictionary format = HouseDictionaryReader.read(file);
            for (ValueMapDictionary d : List.of(full, format))
            {
                // the key is the fold, the value the spelling: a non-string value is no entry
                assertFalse(d.isValidTerm("PT", "HEADACHE"), text + " " + d);
                assertFalse(d.isValidTerm("PT", "x"), text + " " + d);
                assertTrue(d.isMember("PT", "Nausea"), text + " " + d);
                assertNull(d.decode("PTCD", "1"), text + " " + d);
                assertNull(d.decode("PTCD", "x"), text + " " + d);
                assertEquals("Nausea", d.decode("PTCD", "2"), text + " " + d);
            }
            assertEquals(full.toString().replace("FULL", "?"),
                    format.toString().replace("FORMAT", "?"), text);
            // the pairs registry of a house format 1 file: the non-string decode is no decode
            String loinc = "{\"type\":\"loinc\",\"levels\":{\"LOINC\":{\"1-1\":\"1-1\",\"1-2\":\"1-2\","
                    + "\"x\":\"x\"}},\"pairs\":{\"loinc\":{\"1-1\":" + v
                    + ",\"1-2\":\"Glucose\"}}}";
            Path lfile = Files.createDirectories(tmp.resolve("sec" + n++)).resolve("loinc.json");
            Files.writeString(lfile, loinc);
            for (ValueMapDictionary d : List.of(ValueMapDictionary.load(lfile),
                    HouseDictionaryReader.read(lfile)))
            {
                assertNull(d.decode("LOINC", "1-1"), loinc + " " + d);
                assertNull(d.decode("LOINC", "x"), loinc + " " + d);
                assertEquals("Glucose", d.decode("LOINC", "1-2"), loinc + " " + d);
            }
        }
    }
}
