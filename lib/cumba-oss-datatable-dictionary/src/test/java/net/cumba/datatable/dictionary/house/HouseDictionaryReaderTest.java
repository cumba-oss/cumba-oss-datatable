package net.cumba.datatable.dictionary.house;

// OSS-IDENTITY datatable-dictionary: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The house-format reader and the model's read API (PLAN-dictionary-backed-formats A2): exact
 * membership (DF4), decodes from the level's own source only, house formats 1 and 2, the engine's
 * degrade-silently rule.
 */
class HouseDictionaryReaderTest
{

    @TempDir
    Path dir;

    @Test
    void readsTypeVersionRetrievedAndLevels() throws IOException
    {
        ValueMapDictionary d = HouseDictionaryReader
                .read(TestStore.versioned(dir, "meddra", "26.0", TestStore.meddra("26.0", true)));
        assertEquals("meddra", d.getType());
        assertEquals("26.0", d.getVersion());
        assertEquals("2026-10-01", d.getRetrieved());
        assertEquals(ValueMapDictionary.HOUSE_FORMAT_1, d.houseFormat());
        assertTrue(d.hasContent());
        assertTrue(d.hasLevel("PT"));
        assertTrue(d.hasLevel("SOCCD"));
        assertFalse(d.hasLevel("DRUGCD"));
    }


    @Test
    void membershipIsTheStoredSpelling_exactly() throws IOException
    {
        ValueMapDictionary d = HouseDictionaryReader
                .read(TestStore.versioned(dir, "meddra", "26.0", TestStore.meddra("26.0", false)));
        assertTrue(d.isMember("PT", "Headache"));
        assertFalse(d.isMember("PT", "HEADACHE"), "DF4: a case-only difference is not a member");
        assertFalse(d.isMember("PT", "headache"));
        assertFalse(d.isMember("PT", "Headach"));
        assertFalse(d.isMember("PT", "Headache "), "the caller trims; the model compares verbatim");
        assertTrue(d.isMember("PTCD", "10019211"));
        assertFalse(d.isMember("HLT", "Headache"), "a PT name is not an HLT member");
        assertFalse(d.isMember("NOPE", "Headache"));
    }


    @Test
    void anAllUppercaseLevelMapsTheUppercaseSpelling_andNotTheSentenceCaseOne() throws IOException
    {
        TestStore.House h = TestStore.House.of("meddra").version("1").nameLevel("PT", "HEADACHE");
        ValueMapDictionary d = HouseDictionaryReader
                .read(TestStore.versioned(dir, "meddra", "1", h));
        assertTrue(d.isMember("PT", "HEADACHE"));
        assertFalse(d.isMember("PT", "Headache"), "the stored spelling decides, either way");
    }


    @Test
    void aFileBreakingTheKeyContractAnswersLikeTheEngine() throws IOException
    {
        // key X -> value y where upper(y) != X: the engine's caseMatches looks the term up under
        // upper(term) and finds nothing; so does the model (no "set of values" shortcut)
        TestStore.House h = TestStore.House.of("meddra").version("1").rawLevel("PT",
                Map.of("WRONGKEY", "Headache"));
        ValueMapDictionary d = HouseDictionaryReader
                .read(TestStore.versioned(dir, "meddra", "1", h));
        assertFalse(d.isMember("PT", "Headache"));
        assertFalse(d.isMember("PT", "WRONGKEY"));
    }


    @Test
    void loincDecodesFromItsPairsRegistry_exactlyAsStored_andACodeWithoutANameHasNone()
        throws IOException
    {
        ValueMapDictionary d = HouseDictionaryReader
                .read(TestStore.versioned(dir, "loinc", "2.80", TestStore.loinc("2.80")));
        assertTrue(d.decodes("LOINC"));
        assertEquals("Fasting glucose", d.decode("LOINC", "1558-6"));
        assertEquals("Glucose", d.decode("LOINC", "2345-7"));
        assertNull(d.decode("LOINC", "9999-9"), "a member without a stored decode");
        assertTrue(d.isMember("LOINC", "9999-9"));
        assertNull(d.decode("LOINC", "0000-0"), "a non-member");
        assertFalse(d.isMember("LOINC", "0000-0"));
    }


    @Test
    void decodesAreReturnedInTheirStoredCase_upperOrSentence() throws IOException
    {
        ValueMapDictionary unii = HouseDictionaryReader
                .read(TestStore.versioned(dir, "unii", "4Aug2026", TestStore.unii("4Aug2026")));
        assertEquals("ASPIRIN", unii.decode("UNII", "R16CO5Y76E"), "upper, as the FDA publishes");
        ValueMapDictionary snomed = HouseDictionaryReader
                .read(TestStore.versioned(dir, "snomed", "20240901", TestStore.snomed("20240901")));
        assertEquals("Headache", snomed.decode("SNOMEDCD", "25064002"), "sentence case, as stored");
    }


    @Test
    void aLowerCasedCodeIsNotAMemberAndDoesNotDecode() throws IOException
    {
        ValueMapDictionary unii = HouseDictionaryReader
                .read(TestStore.versioned(dir, "unii", "4Aug2026", TestStore.unii("4Aug2026")));
        assertFalse(unii.isMember("UNII", "r16co5y76e"));
        assertNull(unii.decode("UNII", "r16co5y76e"));
    }


    @Test
    void medRtDecodesFromPairsMedrt_neverMedrtBase() throws IOException
    {
        ValueMapDictionary d = HouseDictionaryReader.read(
                TestStore.versioned(dir, "medrt", "2026.07.06", TestStore.medrt("2026.07.06")));
        assertEquals("Cyclooxygenase Inhibitors", d.decode("MEDRTCD", "N0000000181"));
        // N0000009999 is a member of the code level and sits in NO registry: the base registry
        // would be the engine's fall-through; the format path never falls through
        TestStore.House h = TestStore.medrt("2026.07.07").pairs("medrt-base",
                Map.of("N0000009999", "Only in the base registry"));
        ValueMapDictionary d2 = HouseDictionaryReader
                .read(TestStore.versioned(dir, "medrt", "2026.07.07", h));
        assertNull(d2.decode("MEDRTCD", "N0000009999"), "never medrt-base, never the scan");
        assertTrue(d2.isMember("MEDRTCD", "N0000009999"));
    }


    @Test
    void registryChoice_oneCodeInTwoRegistries_theLevelsOwnRegistryDecodes() throws IOException
    {
        // the UNII code also sits in pairs.snomed with another decode (D2): UNII reads pairs.unii
        TestStore.House h = TestStore.unii("4Aug2026").pairs("snomed",
                Map.of("R16CO5Y76E", "WRONG REGISTRY"));
        ValueMapDictionary d = HouseDictionaryReader
                .read(TestStore.versioned(dir, "unii", "4Aug2026", h));
        assertEquals("ASPIRIN", d.decode("UNII", "R16CO5Y76E"));
    }


    @Test
    void meddraCodesHaveNoDecodeSourceInHouseFormat1() throws IOException
    {
        ValueMapDictionary d = HouseDictionaryReader
                .read(TestStore.versioned(dir, "meddra", "26.0", TestStore.meddra("26.0", true)));
        assertFalse(d.decodes("PTCD"));
        assertNull(d.decode("PTCD", "10019211"));
        assertFalse(d.decodes("PT"));
    }


    @Test
    void houseFormat2DecodesSectionIsRead_andWinsOverAnyRegistry() throws IOException
    {
        TestStore.House h = TestStore.meddra("27.1", true).houseFormat(2).decodes("PTCD",
                Map.of("10019211", "Headache", "10028813", "Nausea"));
        ValueMapDictionary d = HouseDictionaryReader
                .read(TestStore.versioned(dir, "meddra", "27.1", h));
        assertEquals(ValueMapDictionary.HOUSE_FORMAT_2, d.houseFormat());
        assertTrue(d.decodes("PTCD"));
        assertEquals("Headache", d.decode("PTCD", "10019211"));
        assertNull(d.decode("PTCD", "10012735"), "a code the section does not hold");
        assertFalse(d.decodes("PT"));
        // a house_format written as text still reads
        TestStore.House t = TestStore.loinc("2.81").raw("house_format", "2");
        assertEquals(2, HouseDictionaryReader.read(TestStore.versioned(dir, "loinc", "2.81", t))
                .houseFormat());
    }


    @Test
    void gzipFilesRead_andAMisnamedGzFails() throws IOException
    {
        ValueMapDictionary d = HouseDictionaryReader
                .read(TestStore.versionedGz(dir, "loinc", "2.80", TestStore.loinc("2.80")));
        assertEquals("Glucose", d.decode("LOINC", "2345-7"));
        Path notGz = TestStore.text(dir.resolve("x").resolve("1").resolve("x.json.gz"), "{}");
        assertThrows(IOException.class, () -> HouseDictionaryReader.read(notGz));
    }


    @Test
    void misShapedSectionsDegradeToEmpty_unknownKeysAreSkipped_emptyFileHasNoContent()
        throws IOException
    {
        TestStore.House h = TestStore.House.of("meddra").version("1")
                .raw("levels", List.of("not", "an", "object")).raw("pairs", "text")
                .raw("hierarchy_by_level", 42).raw("future_section", Map.of("a", "b"))
                .raw("attributes", Map.of("neoplasm", List.of("array", "not", "map")));
        ValueMapDictionary d = HouseDictionaryReader
                .read(TestStore.versioned(dir, "meddra", "1", h));
        assertFalse(d.hasContent());
        assertFalse(d.hasLevel("PT"));
        assertFalse(d.isMember("PT", "Headache"));
        assertNull(d.decode("PTCD", "1"));

        TestStore.House empty = TestStore.House.of("loinc").version("2.80");
        assertFalse(HouseDictionaryReader.read(TestStore.versioned(dir, "loinc", "2.80", empty))
                .hasContent());
        TestStore.House onlyPairs = TestStore.House.of("loinc").version("2.81").pairs("loinc",
                Map.of("1-1", "x"));
        assertTrue(HouseDictionaryReader.read(TestStore.versioned(dir, "loinc", "2.81", onlyPairs))
                .hasContent());
    }


    @Test
    void aMeddraFileCountsItsBareHierarchyAsContent_butDoesNotHoldIt() throws IOException
    {
        TestStore.House h = TestStore.House.of("meddra").version("1").hierarchy("Headaches",
                List.of("Nervous system disorders"));
        ValueMapDictionary d = HouseDictionaryReader
                .read(TestStore.versioned(dir, "meddra", "1", h));
        assertTrue(d.hasContent(), "the engine's hasContent counts the bare hierarchy");
        TestStore.House emptyList = TestStore.House.of("meddra").version("2").hierarchy("X",
                List.of());
        assertFalse(
                HouseDictionaryReader.read(TestStore.versioned(dir, "meddra", "2", emptyList))
                        .hasContent(),
                "an empty ancestor list is no content (the engine's anyEntry mirror)");
    }


    /**
     * DF25 amended to the format subset (owner ruling OQ2, 2026-10-10): the sections the format
     * path never reads — {@code hierarchy_by_level}, {@code attributes} and every {@code pairs}
     * registry outside the house-format-1 decode table ({@code whodrug}, {@code medrt-base}, …) —
     * are skipped while streaming, so none of their terms is reachable from the model; the kept
     * registry and the levels are (the walk's own sensitivity arm).
     */
    @Test
    void theSectionsTheFormatPathNeverReadsAreNotHeld_oq2() throws IOException
    {
        TestStore.House h = TestStore.House.of("loinc").version("2.80")
                .nameLevel("LOINC", "KEPT-LOINC-CODE")
                .pairs("loinc", Map.of("KEPT-LOINC-CODE", "KEPT-LOINC-NAME"))
                .pairs("whodrug", Map.of("SKIP-WHODRUG-REPORTED", "SKIP-WHODRUG-PT"))
                .pairs("medrt-base", Map.of("SKIP-MEDRT-BASE-CODE", "SKIP-MEDRT-BASE-NAME"))
                .attributes("neoplasm", Map.of("SKIP-ATTR-KEY", "SKIP-ATTR-VALUE"))
                .hierarchyByLevel("PT", "SKIP-HBL-TERM",
                        Map.of("SOC", List.of("SKIP-HBL-ANCESTOR")));
        ValueMapDictionary d = HouseDictionaryReader
                .read(TestStore.versioned(dir, "loinc", "2.80", h));
        Set<String> reachable = new HashSet<>();
        collectStrings(d, reachable, Collections.newSetFromMap(new IdentityHashMap<>()));
        assertTrue(reachable.containsAll(List.of("KEPT-LOINC-CODE", "KEPT-LOINC-NAME")),
                "the walk sees the levels and the kept v1 registry: " + reachable);
        assertEquals(List.of(),
                reachable.stream().filter(t -> t.startsWith("SKIP-")).sorted().toList(),
                "no term of a section the format path never reads is held");
        assertEquals("KEPT-LOINC-NAME", d.decode("LOINC", "KEPT-LOINC-CODE"));
    }


    /**
     * hasContent stays the engine's (A4's hasContent arm): a file whose ONLY content is one skipped
     * section still counts as installed, and the same section empty does not.
     */
    @Test
    void aSkippedSectionAloneStillCountsAsContent_oq2() throws IOException
    {
        List<TestStore.House> withContent = List.of(
                TestStore.House.of("meddra").version("a").hierarchyByLevel("PT", "Headache",
                        Map.of()),
                TestStore.House.of("meddra").version("b").attributes("x", Map.of("k", "v")),
                TestStore.House.of("whodrug").version("c").pairs("whodrug", Map.of("r", "PT")),
                TestStore.House.of("medrt").version("d").pairs("medrt-base", Map.of("N1", "n")));
        for (int i = 0; i < withContent.size(); i++)
        {
            TestStore.House h = withContent.get(i);
            assertTrue(HouseDictionaryReader.read(TestStore.versioned(dir, "t", "with" + i, h))
                    .hasContent(), h.json());
        }
        List<TestStore.House> without = List.of(
                TestStore.House.of("meddra").version("e").raw("hierarchy_by_level",
                        Map.of("PT", Map.of())),
                TestStore.House.of("meddra").version("f").raw("attributes",
                        Map.of("x", Map.of("nested", Map.of("k", "v")))),
                TestStore.House.of("whodrug").version("g").raw("pairs",
                        Map.of("whodrug", Map.of())));
        for (int i = 0; i < without.size(); i++)
        {
            TestStore.House h = without.get(i);
            assertFalse(HouseDictionaryReader.read(TestStore.versioned(dir, "t", "without" + i, h))
                    .hasContent(), h.json());
        }
    }


    /** Every string reachable from the object through its fields, maps and collections. */
    private static void collectStrings(@Nullable Object aObject, Set<String> aOut,
            Set<Object> aSeen)
    {
        if (aObject == null || !aSeen.add(aObject))
        {
            return;
        }
        if (aObject instanceof String str)
        {
            aOut.add(str);
        }
        else if (aObject instanceof Map<?, ?> map)
        {
            map.forEach((k, v) ->
            {
                collectStrings(k, aOut, aSeen);
                collectStrings(v, aOut, aSeen);
            });
        }
        else if (aObject instanceof Collection<?> coll)
        {
            coll.forEach(e -> collectStrings(e, aOut, aSeen));
        }
        else if (aObject.getClass().getPackageName().startsWith("net.cumba."))
        {
            for (Field f : aObject.getClass().getDeclaredFields())
            {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive())
                {
                    continue;
                }
                f.setAccessible(true);
                try
                {
                    collectStrings(f.get(aObject), aOut, aSeen);
                }
                catch (IllegalAccessException e)
                {
                    throw new AssertionError(e);
                }
            }
        }
    }


    @Test
    void notAJsonObjectIsAnIOException() throws IOException
    {
        Path arr = TestStore.text(dir.resolve("a.json"), "[1, 2]");
        assertThrows(IOException.class, () -> HouseDictionaryReader.read(arr));
        Path garbage = TestStore.text(dir.resolve("b.json"), "{ not json");
        assertThrows(IOException.class, () -> HouseDictionaryReader.read(garbage));
        assertThrows(IOException.class,
                () -> HouseDictionaryReader.read(dir.resolve("missing.json")));
    }


    @Test
    void typeFallsBackToEmpty_versionBlankIsNull() throws IOException
    {
        TestStore.House h = TestStore.House.of("").version("  ").nameLevel("PT", "Headache");
        ValueMapDictionary d = HouseDictionaryReader.read(TestStore.versioned(dir, "x", "1", h));
        assertEquals("", d.getType());
        assertNull(d.getVersion());
        assertNull(d.getRetrieved());
        assertEquals("ValueMapDictionary(x, house format 1, FORMAT)", d.toString(),
                "an untyped file is named by the stem the store bound it as");
    }
}
