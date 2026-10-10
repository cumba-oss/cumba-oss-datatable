package net.cumba.datatable.dictionary.house;

// OSS-IDENTITY datatable-dictionary: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** DF3 / DF22: the variable → level table and how it is read (D4). */
class DictionaryLevelsTest
{

    /** Every (type, variable, level) row the rule corpus uses (plan §2.3, re-measured in A0). */
    private static final Map<String, Map<String, String>> CORPUS_ROWS = Map.of("meddra",
            Map.ofEntries(Map.entry("AELLT", "LLT"), Map.entry("AELLTCD", "LLTCD"),
                    Map.entry("AEDECOD", "PT"), Map.entry("AEPTCD", "PTCD"),
                    Map.entry("AEHLT", "HLT"), Map.entry("AEHLTCD", "HLTCD"),
                    Map.entry("AEHLGT", "HLGT"), Map.entry("AEHLGTCD", "HLGTCD"),
                    Map.entry("AESOC", "SOC"), Map.entry("AESOCCD", "SOCCD"),
                    Map.entry("AEBODSYS", "SOC"), Map.entry("AEBDSYCD", "SOCCD"),
                    Map.entry("PESTRESC", "PT")),
            "whodrug", Map.of("CMDECOD", "PT", "CMCLAS", "ATC", "CMCLASCD", "ATCCD"), "loinc",
            Map.of("LBLOINC", "LOINC"));

    @Test
    void everyCorpusRowResolves()
    {
        int checked = 0;
        for (Map.Entry<String, Map<String, String>> type : CORPUS_ROWS.entrySet())
        {
            for (Map.Entry<String, String> row : type.getValue().entrySet())
            {
                assertEquals(row.getValue(), DictionaryLevels.levelOf(type.getKey(), row.getKey()),
                        type.getKey() + " " + row.getKey());
                checked++;
            }
        }
        assertEquals(17, checked, "every corpus row of plan §2.3 is in the roster");
    }


    @ParameterizedTest
    @CsvSource(
    {
            "AEHLGTCD, HLGTCD", "AEHLGT, HLGT", "AEHLTCD, HLTCD", "AEHLT, HLT", "AELLTCD, LLTCD",
            "MHDECOD, PT", "mhdecod, PT", "AeSoC, SOC", "CESOCCD, SOCCD", "pestresc, PT"
    })
    void theLongestSuffixWins_andNamesMatchCaseInsensitively(String aColumn, String aLevel)
    {
        assertEquals(aLevel, DictionaryLevels.levelOf("meddra", aColumn));
    }


    @Test
    void aSuffixNeedsExactlyTheTwoCharacterDomainPrefix()
    {
        assertNull(DictionaryLevels.levelOf("meddra", "SOC"), "no domain prefix");
        assertNull(DictionaryLevels.levelOf("meddra", "AEXSOC"),
                "three characters before the suffix");
        assertNull(DictionaryLevels.levelOf("meddra", "AETERM"), "no row: no level, no guess");
        assertNull(DictionaryLevels.levelOf("meddra", null));
        assertNull(DictionaryLevels.levelOf("unii", "TSVALCD"),
                "row-dependent TS rows are not seeded");
        assertNull(DictionaryLevels.levelOf("nosuchtype", "AEDECOD"));
    }


    @Test
    void loincIsSingleLevel_everyColumnResolvesToIt()
    {
        assertEquals("LOINC", DictionaryLevels.levelOf("loinc", "LBLOINC"));
        assertEquals("LOINC", DictionaryLevels.levelOf("loinc", "LBTESTCD"));
        assertEquals("LOINC", DictionaryLevels.levelOf("loinc", "ANYTHING"));
        assertEquals("LOINC", DictionaryLevels.defaultLevelOf("loinc"));
        assertNull(DictionaryLevels.defaultLevelOf("meddra"));
    }


    @Test
    void theRosterOfLevels()
    {
        assertEquals(Set.of("LLT", "LLTCD", "PT", "PTCD", "HLT", "HLTCD", "HLGT", "HLGTCD", "SOC",
                "SOCCD"), Set.copyOf(DictionaryLevels.levelsOf("meddra")));
        assertEquals(10, DictionaryLevels.levelsOf("meddra").size(), "no duplicates");
        assertEquals(Set.of("PT", "ATC", "ATCCD"),
                Set.copyOf(DictionaryLevels.levelsOf("whodrug")));
        assertEquals(List.of("LOINC"), DictionaryLevels.levelsOf("loinc"));
        assertTrue(DictionaryLevels.levelsOf("unii").isEmpty());
    }


    /**
     * D4: the table is read through the class's own loader, never the thread's context loader. The
     * class is loaded FRESH in an isolated loader (its static table is built then), on a thread
     * whose context loader is {@code null} and then a foreign loader that sees nothing — a
     * {@code Thread.currentThread().getContextClassLoader().getResource(...)} would find nothing.
     */
    @Test
    void theTableLoadsUnderANullAndAForeignContextClassLoader() throws Exception
    {
        URL classes = DictionaryLevels.class.getProtectionDomain().getCodeSource().getLocation();
        AtomicReference<@Nullable Object> underNull = new AtomicReference<>();
        AtomicReference<@Nullable Object> underForeign = new AtomicReference<>();
        AtomicReference<@Nullable Throwable> failure = new AtomicReference<>();
        Thread t = new Thread(() ->
        {
            try (URLClassLoader isolated = new URLClassLoader(new URL[]
            {
                    classes
            }, null); URLClassLoader isolated2 = new URLClassLoader(new URL[]
            {
                    classes
            }, null); URLClassLoader foreign = new URLClassLoader(new URL[0], null))
            {
                Thread.currentThread().setContextClassLoader(null);
                Class<?> levels = isolated.loadClass(DictionaryLevels.class.getName());
                java.lang.reflect.Method levelOf = levels.getMethod("levelOf", String.class,
                        String.class);
                underNull.set(levelOf.invoke(null, "meddra", "AEDECOD"));
                Thread.currentThread().setContextClassLoader(foreign);
                Class<?> again = isolated2.loadClass(DictionaryLevels.class.getName());
                underForeign.set(again.getMethod("levelOf", String.class, String.class).invoke(null,
                        "whodrug", "CMCLASCD"));
            }
            catch (Exception | LinkageError e)
            {
                failure.set(e);
            }
        });
        t.start();
        t.join();
        assertNull(failure.get(), String.valueOf(failure.get()));
        assertEquals("PT", underNull.get());
        assertEquals("ATCCD", underForeign.get());
    }


    /**
     * The ordered suffix rows the engine's hierarchy function reads (PLAN-dictionary-backed-formats
     * B3): longest suffix first, every MedDRA corpus row, nothing for a type without rows.
     */
    @Test
    void suffixRowsAreLongestFirst_andTheEngineReadsTheMeddraRows()
    {
        List<Map.Entry<String, String>> rows = DictionaryLevels.suffixRowsOf("MedDRA");
        assertEquals(12, rows.size(), rows.toString());
        assertEquals(6, rows.get(0).getKey().length(), rows.toString());
        assertTrue(rows.contains(Map.entry("HLGTCD", "HLGTCD")));
        for (int i = 1; i < rows.size(); i++)
        {
            assertTrue(rows.get(i - 1).getKey().length() >= rows.get(i).getKey().length(),
                    rows.toString());
        }
        assertTrue(rows.contains(Map.entry("DECOD", "PT")));
        assertTrue(rows.contains(Map.entry("BODSYS", "SOC")));
        assertEquals(List.of(), DictionaryLevels.suffixRowsOf("neoplasm"));
        assertEquals(List.of(Map.entry("LOINC", "LOINC")), DictionaryLevels.suffixRowsOf("loinc"));
    }
}
