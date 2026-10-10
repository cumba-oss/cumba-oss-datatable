package net.cumba.datatable.dictionary.house;

// OSS-IDENTITY datatable-dictionary: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * PLAN-dictionary-backed-formats B1: the two store binders of the module agree on every NAMED
 * version — the engine's {@link DictionaryStore} (selected-versions semantics) and the format
 * path's {@link DictionaryStoreReader} (DF1a: a blank version binds the most recent installed one).
 * Versioned, flat and mixed layouts, an invalid token, a version not installed. The Phase A
 * cross-reader drift test's store arm, moved into the module now that both binders live here.
 */
class StoreBinderAgreementTest
{

    @TempDir
    Path tmp;

    @Test
    void typeVersionAndLayoutBindAlikeForANamedVersion() throws IOException
    {
        Path syn = ProfileEquivalenceTest.resources("profile-fixtures");
        Path engine = ProfileEquivalenceTest.resources("engine-dictionaries");
        // versioned: two MedDRA releases, a LOINC release
        Path store = tmp.resolve("versioned");
        copy(syn.resolve("meddra-26.0.json"), store.resolve("meddra/26.0/meddra.json"));
        copy(syn.resolve("meddra-upper.json"), store.resolve("meddra/27.1/meddra.json"));
        copy(syn.resolve("loinc.json"), store.resolve("loinc/2.80/loinc.json"));
        for (String v : List.of("26.0", "27.1", "25.0", "../x", "2.80"))
        {
            assertSameBinding(store, "meddra", v);
            assertSameBinding(store, "loinc", v);
        }
        assertSameBinding(store, "whodrug", "SEP_2020");

        // flat: the engine's dummy layout
        Path flat = tmp.resolve("flat");
        copy(engine.resolve("unii.json"), flat.resolve("unii.json"));
        copy(syn.resolve("loinc.json"), flat.resolve("loinc.json"));
        assertSameBinding(flat, "loinc", "2.80");
        assertSameBinding(flat, "loinc", "9.9");
        assertSameBinding(flat, "unii", "4Aug2026");
        // nothing requested on a flat store: both load the file
        assertTrue(new DictionaryStoreReader(flat).resolve("loinc", null).isResolved());
        assertNotNull(DictionaryStore.load(flat, Map.of()).dictionaryOf("loinc"));

        // mixed: the versioned tree wins, the stray flat file is ignored by both
        Path mixed = tmp.resolve("mixed");
        copy(syn.resolve("meddra-26.0.json"), mixed.resolve("meddra/26.0/meddra.json"));
        copy(engine.resolve("unii.json"), mixed.resolve("unii.json"));
        assertSameBinding(mixed, "meddra", "26.0");
        assertSameBinding(mixed, "unii", "4Aug2026");
        assertFalse(new DictionaryStoreReader(mixed).resolve("unii", null).isResolved());

        // DF1a, by design: a BLANK version binds the most recent installed version on the format
        // path; the engine binds the manifest's selection (or nothing). The browser's tooltip names
        // the bound version, so the difference is visible (plan §10, DF1a "stated consequence").
        Files.writeString(store.resolve(DictionaryStoreReader.MANIFEST), "{\"meddra\":\"26.0\"}");
        assertEquals("27.1", new DictionaryStoreReader(store).resolve("meddra", null).version());
        assertEquals("26.0", DictionaryStore.load(store, Map.of()).versionOf("meddra"));
        assertEquals(DictionaryStore.MANIFEST, DictionaryStoreReader.MANIFEST);
    }


    private static void assertSameBinding(Path aStore, String aType, String aVersion)
        throws IOException
    {
        RuntimeDictionaryProvider engine = DictionaryStore.load(aStore, Map.of(aType, aVersion));
        DictionaryStoreReader.Resolution format = new DictionaryStoreReader(aStore).resolve(aType,
                aVersion);
        ValueMapDictionary engineDict = engine.dictionaryOf(aType);
        String what = aStore.getFileName() + " " + aType + " " + aVersion;
        assertEquals(engineDict != null, format.isResolved(), what);
        if (engineDict != null)
        {
            assertEquals(engine.versionOf(aType), format.version(), what);
            assertNotNull(format.dictionary());
            assertEquals(engineDict.getType(), format.dictionary().getType(), what);
        }
    }


    private static void copy(Path aFrom, Path aTo) throws IOException
    {
        Files.createDirectories(aTo.getParent());
        Files.copy(aFrom, aTo);
    }
}
