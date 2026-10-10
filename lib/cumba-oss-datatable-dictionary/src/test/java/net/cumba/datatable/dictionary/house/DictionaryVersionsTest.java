package net.cumba.datatable.dictionary.house;

// OSS-IDENTITY datatable-dictionary: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.cumba.datatable.dictionary.house.DictionaryVersions.Installed;
import org.junit.jupiter.api.Test;

/** DF1a: the per-type ordering of version tokens, one test per type, plus the fallback. */
class DictionaryVersionsTest
{

    private static List<Installed> installed(Map<String, String> aTokenToDate)
    {
        List<Installed> out = new ArrayList<>();
        aTokenToDate.forEach((token, date) -> out.add(new Installed(token,
                _ -> date == null || date.isEmpty() ? null : LocalDate.parse(date))));
        return out;
    }


    private static List<Installed> tokens(String... aTokens)
    {
        List<Installed> out = new ArrayList<>();
        for (String t : aTokens)
        {
            out.add(new Installed(t, _ -> null));
        }
        return out;
    }


    @Test
    void meddraAndLoinc_numericMajorMinor_notLexicographic()
    {
        assertEquals(List.of("27.0", "26.1", "26.0", "9.1"),
                DictionaryVersions.order("meddra", tokens("26.0", "9.1", "27.0", "26.1")));
        assertEquals("27.0",
                DictionaryVersions.mostRecent("meddra", tokens("26.0", "9.1", "27.0")));
        assertEquals(List.of("2.80", "2.78", "2.9"),
                DictionaryVersions.order("loinc", tokens("2.78", "2.9", "2.80")),
                "2.80 is eighty, 2.9 is nine");
        assertEquals(List.of("27.0.1", "27.0"),
                DictionaryVersions.order("meddra", tokens("27.0", "27.0.1")));
    }


    @Test
    void whodrug_yearThenMonth()
    {
        assertEquals(List.of("MAR_2021", "SEP_2020", "Mar_2020"),
                DictionaryVersions.order("whodrug", tokens("SEP_2020", "MAR_2021", "Mar_2020")));
        assertArrayEquals(new long[]
        {
                2020, 9
        }, DictionaryVersions.parse("whodrug", "SEP_2020"));
        assertEquals(0, DictionaryVersions.parse("whodrug", "200604").length,
                "the define's own vocabulary is not the store's");
        assertEquals(0, DictionaryVersions.parse("whodrug", "XXX_2020").length);
    }


    @Test
    void snomedNeoplasmMedrtUnii_dates()
    {
        assertEquals(List.of("20240901", "20240301", "20231101"),
                DictionaryVersions.order("snomed", tokens("20240301", "20231101", "20240901")));
        assertEquals(0, DictionaryVersions.parse("snomed", "20241399").length, "not a date");
        assertEquals(List.of("2024-03-29", "2023-12-15"),
                DictionaryVersions.order("neoplasm", tokens("2023-12-15", "2024-03-29")));
        assertEquals(List.of("2026.07.06", "2025.12.01"),
                DictionaryVersions.order("medrt", tokens("2025.12.01", "2026.07.06")));
        assertArrayEquals(new long[]
        {
                20, 1
        }, DictionaryVersions.parse("medrt", "20.1"), "a dotted DTS version number still orders");
        assertEquals(List.of("4Aug2026", "15Mar2026", "1Jan2026"),
                DictionaryVersions.order("unii", tokens("15Mar2026", "1Jan2026", "4Aug2026")));
        assertEquals(0, DictionaryVersions.parse("unii", "4Xyz2026").length);
        assertEquals(0, DictionaryVersions.parse("unknown-type", "1.0").length);
    }


    @Test
    void anUnparseableTokenRanksBelowEveryParsableOne_thenByInstallDate_thenByText()
    {
        List<Installed> in = installed(Map.of("26.0", "2020-01-01", "custom-b", "2026-05-01",
                "custom-a", "2026-06-01", "custom-c", "2026-06-01"));
        assertEquals(List.of("26.0", "custom-c", "custom-a", "custom-b"),
                DictionaryVersions.order("meddra", in));
        // without any date the text decides; a dated one outranks an undated one
        assertEquals(List.of("zz", "aa"), DictionaryVersions.order("meddra", tokens("aa", "zz")));
        assertEquals(List.of("aa", "zz"), DictionaryVersions.order("meddra",
                installed(Map.of("aa", "2026-01-01", "zz", ""))));
    }


    @Test
    void tiesOfParsableTokensBreakByInstallDate()
    {
        // 27.0 and 27.00 parse alike; the later install wins
        assertEquals(List.of("27.00", "27.0"), DictionaryVersions.order("meddra",
                installed(Map.of("27.0", "2026-01-01", "27.00", "2026-02-01"))));
        assertNull(DictionaryVersions.mostRecent("meddra", List.of()));
    }


    /**
     * Lane 1 LOW-2: an install date is read at most once per token per order, however often the
     * sort compares it (the date can cost a file read).
     */
    @Test
    void anInstallDateIsReadOncePerTokenPerOrder_l2()
    {
        Map<String, Integer> reads = new java.util.HashMap<>();
        List<Installed> in = new ArrayList<>();
        for (String t : List.of("27.0", "27.00", "27.000", "27.0000", "27.00000"))
        {
            in.add(new Installed(t, token ->
            {
                reads.merge(token, 1, Integer::sum);
                return LocalDate.parse("2026-01-01");
            }));
        }
        assertEquals(List.of("27.00000", "27.0000", "27.000", "27.00", "27.0"),
                DictionaryVersions.order("meddra", in), "equal dates: the token text decides");
        assertEquals(Map.of("27.0", 1, "27.00", 1, "27.000", 1, "27.0000", 1, "27.00000", 1),
                reads);
    }
}
