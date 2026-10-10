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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The engine's store binder and provider report every dictionary they do NOT load — loudly, in the
 * log, and as a recorded {@link RuntimeDictionaryProvider.Unavailability} the per-rule SKIP names —
 * and never mistake a stray file for a dictionary. The warnings are the operator-facing half of the
 * "unavailable ⇒ SKIP, never false-pass" contract (PLAN-dictionary-backed-formats B1: the classes
 * moved here with their warnings; this test pins them in the module).
 */
class EngineDictionaryWarningsTest
{

    private static final String LOINC = "{\"type\":\"loinc\",\"version\":\"2.80\","
            + "\"levels\":{\"LOINC\":{\"1-8\":\"1-8\"}}}";

    private static final String MEDDRA_WITH_HBL = "{\"type\":\"meddra\",\"levels\":{\"PT\":"
            + "{\"HEADACHE\":\"Headache\"}},\"hierarchy_by_level\":{\"PT\":{\"Headache\":{\"SOC\":"
            + "[\"Nervous system disorders\"]}}}}";

    private static final String MEDDRA_WITHOUT_HBL = "{\"type\":\"meddra\",\"levels\":{\"PT\":"
            + "{\"HEADACHE\":\"Headache\"}}}";

    @TempDir
    Path dir;

    private final List<LogRecord> records = new ArrayList<>();

    private final Handler capture = new Handler()
    {

        @Override
        public void publish(LogRecord aRecord)
        {
            records.add(aRecord);
        }


        @Override
        public void flush()
        {
            // nothing buffered
        }


        @Override
        public void close()
        {
            // nothing held
        }
    };

    @BeforeEach
    void attach()
    {
        for (Class<?> c : List.of(DictionaryStore.class, RuntimeDictionaryProvider.class))
        {
            Logger.getLogger(c.getName()).addHandler(capture);
        }
    }


    @AfterEach
    void detach()
    {
        for (Class<?> c : List.of(DictionaryStore.class, RuntimeDictionaryProvider.class))
        {
            Logger.getLogger(c.getName()).removeHandler(capture);
        }
    }


    private List<String> warnings()
    {
        return records.stream().filter(r -> r.getLevel().intValue() >= Level.WARNING.intValue())
                .map(r -> java.text.MessageFormat.format(r.getMessage(),
                        r.getParameters() == null ? new Object[0] : r.getParameters()))
                .toList();
    }


    private Path write(String aRelative, String aContent) throws IOException
    {
        Path f = dir.resolve(aRelative);
        Files.createDirectories(f.getParent());
        Files.writeString(f, aContent);
        return f;
    }


    @Test
    void aMixedStoreNamesExactlyItsStrayDictionaryFiles() throws IOException
    {
        write("meddra/27.1/meddra.json", MEDDRA_WITH_HBL);
        write("stray.json", LOINC);
        write("stray2.json.gz.txt", "not a dictionary");
        write("notes.txt", "not a dictionary");
        write(DictionaryStore.MANIFEST, "{\"meddra\":\"27.1\"}");
        Files.createDirectories(dir.resolve("adir.json"));

        RuntimeDictionaryProvider p = DictionaryStore.load(dir, Map.of());

        assertTrue(p.isAvailable("meddra"), "the versioned tree is used");
        assertFalse(p.isAvailable("loinc"), "the stray flat file is ignored");
        List<String> mixed = warnings().stream().filter(w -> w.contains("mixes the versioned"))
                .toList();
        assertEquals(1, mixed.size(), warnings().toString());
        assertTrue(mixed.get(0).contains("stray.json"), mixed.toString());
        for (String notADictionary : List.of("notes.txt", "stray2.json.gz.txt",
                DictionaryStore.MANIFEST, "adir.json"))
        {
            assertFalse(mixed.get(0).contains(notADictionary), notADictionary + ": " + mixed);
        }
    }


    @Test
    void aPurelyVersionedStoreWarnsAboutNothing() throws IOException
    {
        write("meddra/27.1/meddra.json", MEDDRA_WITH_HBL);
        write(DictionaryStore.MANIFEST, "{\"meddra\":\"27.1\"}");

        assertTrue(DictionaryStore.load(dir, Map.of()).isAvailable("meddra"));
        assertEquals(List.of(), warnings());
    }


    @Test
    void everyUnboundTypeIsWarnedAboutAndRecorded() throws IOException
    {
        write("meddra/27.1/meddra.json", MEDDRA_WITH_HBL);
        write("loinc/2.80/loinc.json", LOINC);
        write("unii/x/unii.json", "{}");
        write("snomed/20240901/snomed.json", "{\"type\":\"snomed\"}");
        write("medrt/2026.07.06/medrt.json", "{\"levels\":{\"MEDRTCD\":{\"N1\":\"N1\"}}}");
        write("whodrug/SEP_2020/notes.txt", "x");

        RuntimeDictionaryProvider p = DictionaryStore.load(dir,
                Map.of("meddra", "26.0", "loinc", "../x", "unii", "x", "snomed", "20240901",
                        "medrt", "2026.07.06", "whodrug", "SEP_2020"));

        List<String> w = warnings();
        assertTrue(
                w.stream()
                        .anyMatch(m -> m.contains(
                                "meddra: version 26.0 (from requested) is " + "not installed")),
                w.toString());
        assertTrue(w.stream().anyMatch(m -> m.contains("loinc: the selected version '../x'")),
                w.toString());
        assertTrue(w.stream().anyMatch(m -> m.contains("unii carries no usable terms")),
                w.toString());
        assertTrue(w.stream().anyMatch(m -> m.contains(
                "snomed: version 20240901 is installed but " + "carries no usable snomed file")),
                w.toString());
        assertFalse(w.stream().anyMatch(m -> m.contains("medrt")), "an untyped file binds: " + w);
        assertTrue(
                w.stream()
                        .anyMatch(m -> m.contains("whodrug: version SEP_2020 is installed "
                                + "but carries no usable whodrug file")),
                "a non-JSON file is no dictionary: " + w);
        assertEquals(5, p.unavailabilities().size(), p.unavailabilities().toString());
        assertTrue(p.unavailabilityDetail("unii").contains("no usable terms"));
        assertTrue(p.unavailabilityDetail("loinc").contains("not a usable store version token"));
        assertEquals(RuntimeDictionaryProvider.notInstalledDetail(),
                p.unavailabilityDetail("neoplasm"));
        assertFalse(RuntimeDictionaryProvider.notInstalledDetail().isBlank());
        assertTrue(p.isAvailable("medrt"), "an untyped file binds under its stem");
    }


    @Test
    void aTypeWithNoSelectionIsWarnedAbout_andAFlatStoreRefusesAnotherVersion() throws IOException
    {
        write("meddra/27.1/meddra.json", MEDDRA_WITH_HBL);
        RuntimeDictionaryProvider p = DictionaryStore.load(dir, Map.of());
        assertFalse(p.isAvailable("meddra"));
        assertTrue(warnings().stream().anyMatch(m -> m.contains("meddra: no version selected")),
                warnings().toString());

        records.clear();
        Path flat = dir.resolve("flat");
        Files.createDirectories(flat);
        Files.writeString(flat.resolve("loinc.json"), LOINC);
        RuntimeDictionaryProvider f = DictionaryStore.load(flat, Map.of("loinc", "2.81"));
        assertFalse(f.isAvailable("loinc"));
        assertTrue(warnings().stream()
                .anyMatch(m -> m.contains("version 2.81 was requested, but the flat")
                        && m.contains("holds 2.80")),
                warnings().toString());
        assertTrue(DictionaryStore.load(flat, Map.of("loinc", "2.80")).isAvailable("loinc"));
    }


    @Test
    void anUnreadableManifestIsWarnedAbout() throws IOException
    {
        write("meddra/27.1/meddra.json", MEDDRA_WITH_HBL);
        write(DictionaryStore.MANIFEST, "{not json");
        assertFalse(DictionaryStore.load(dir, Map.of()).isAvailable("meddra"));
        assertTrue(warnings().stream().anyMatch(m -> m.startsWith("Could not read")),
                warnings().toString());
    }


    @Test
    void theProviderWarnsAboutUnreadableEmptyAndStaleFiles_andNothingElse() throws IOException
    {
        write("bad.json", "{not json");
        write(".json", "{not json");
        write("empty.json", "{\"type\":\"unii\",\"levels\":{}}");
        write("meddra.json", MEDDRA_WITHOUT_HBL);
        write("loinc.json", LOINC);
        write("readme.txt", "{not json either, and never read}");

        RuntimeDictionaryProvider p = RuntimeDictionaryProvider.loadDirectory(dir);

        List<String> w = warnings();
        assertEquals(4, w.size(), w.toString());
        assertTrue(w.stream().anyMatch(m -> m.contains("bad.json could not be read")),
                w.toString());
        assertTrue(w.stream().anyMatch(m -> m.contains("unii carries no usable terms")),
                w.toString());
        assertTrue(w.stream().anyMatch(m -> m.contains("meddra has no level-scoped hierarchy")),
                w.toString());
        assertTrue(p.isAvailable("meddra"), "a stale file is reported, never dropped");
        assertTrue(p.isAvailable("loinc"));
        assertNotNull(p.unavailabilityOf("bad"));
        assertNotNull(p.unavailabilityOf(".json"), "a name without a stem keys as itself");
        assertNull(p.unavailabilityOf("readme"), "a non-JSON file is never read");
        assertNull(p.unavailabilityOf(null));
    }


    @Test
    void aMeddraFileWithTheLevelScopedHierarchyAndAnyOtherTypeWithoutItWarnNothing()
        throws IOException
    {
        write("meddra.json", MEDDRA_WITH_HBL);
        write("loinc.json", LOINC);
        RuntimeDictionaryProvider p = RuntimeDictionaryProvider.loadDirectory(dir);
        assertEquals(List.of(), warnings());
        assertTrue(p.hasHierarchyByLevel("meddra"));
        assertFalse(p.hasHierarchyByLevel("loinc"));
    }

    // ------------------------------------------------------------------ the non-MedDRA hierarchy


    /**
     * W4 fold (R5 P3-L3): every type but MedDRA answers hierarchy from its bare-name map as before
     * W4 — verbatim when case-sensitive, both sides folded otherwise; MedDRA does not hold the bare
     * map at all (it answers only from hierarchy_by_level).
     */
    @Test
    void aNonMeddraTypeAnswersFromItsBareHierarchy_meddraNever() throws IOException
    {
        write("snomed.json", "{\"type\":\"snomed\",\"levels\":{\"SNOMED\":{\"CHILD\":\"Child\","
                + "\"PARENT\":\"Parent\"}},\"hierarchy\":{\"Child\":[\"Parent\"],\"Empty\":[]}}");
        write("meddra.json", "{\"type\":\"meddra\",\"levels\":{\"PT\":{\"CHILD\":\"Child\"}},"
                + "\"hierarchy\":{\"Child\":[\"Parent\"]}}");
        RuntimeDictionaryProvider p = RuntimeDictionaryProvider.loadDirectory(dir);

        assertTrue(p.answersHierarchyAt("snomed", null), "any level, as before W4");
        assertFalse(p.answersHierarchyAt("neoplasm", null), "not loaded");
        assertFalse(p.answersHierarchyAt("meddra", "PT"), "no hierarchy_by_level");
        assertFalse(p.answersHierarchyAt("meddra", null));
        assertTrue(p.onHierarchyPath("snomed", null, "Child", null, "Parent", true));
        assertFalse(p.onHierarchyPath("snomed", null, "Child", null, "PARENT", true),
                "case-sensitive compares verbatim");
        assertFalse(p.onHierarchyPath("snomed", null, "CHILD", null, "Parent", true));
        assertTrue(p.onHierarchyPath("snomed", null, "CHILD", null, "parent", false),
                "case-insensitive folds both sides");
        assertFalse(p.onHierarchyPath("snomed", null, "Child", null, "Other", false));
        assertFalse(p.onHierarchyPath("snomed", null, null, null, "Parent", true));
        assertFalse(p.onHierarchyPath("snomed", null, "Child", null, null, true));
        assertFalse(p.onHierarchyPath("snomed", null, "Empty", null, "Parent", true));
        assertFalse(p.onHierarchyPath("neoplasm", null, "Child", null, "Parent", true));
        assertFalse(p.onHierarchyPath("meddra", null, "Child", null, "Parent", true),
                "MedDRA never answers from the bare map");
        assertFalse(p.onHierarchyPath("meddra", "PT", "Child", "SOC", "Parent", true));
        assertTrue(p.dictionaryOf("meddra").hasContent(), "its bare map still counts as content");
    }
}
