package net.cumba.datatable.formats;

// OSS-IDENTITY datatable-temporal-classifier: byte-identical in cumba-datatable and
// cumba-oss-datatable. Edit in cumba-datatable, then copy; check_oss_identity.py fails on any
// divergence.

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The roster test of PLAN-date-format-classifier-agreement (C3): {@link DisplayFormatCategory}'s
 * SAS sets and {@link Sas94TemporalFormatRoster} are the same roster, in both directions — a name
 * the reference documents that the classifier does not know is a red row here, never a column
 * silently exported as a day count or a second count. This one table-driven test is where the
 * exotic families ({@code NLDATE*}, {@code NLDATM*}, {@code NENGO}, the language-prefixed
 * {@code xxxDF*} forms, the {@code *LZ} / {@code *DZ} / {@code *TX} forms, …) are covered; the
 * common ADaM formats get their own scenario cases in each consumer.
 */
class DisplayFormatCategorySasRosterTest
{

    static List<Sas94TemporalFormatRoster.Entry> roster()
    {
        return Sas94TemporalFormatRoster.ENTRIES;
    }


    /** Every roster name classifies as the kind the reference states, width and decimals apart. */
    @ParameterizedTest
    @MethodSource("roster")
    void everyRosterNameClassifiesAsItsKind(Sas94TemporalFormatRoster.Entry aEntry)
    {
        assertEquals(aEntry.kind(), DisplayFormatCategory.classify(aEntry.name() + "."),
                aEntry.name() + ".: " + aEntry.sentence());
        assertEquals(aEntry.kind(), DisplayFormatCategory.classify(aEntry.name() + "10."),
                aEntry.name() + "10.");
        assertEquals(aEntry.kind(), DisplayFormatCategory.classify(aEntry.name() + "20.3"),
                aEntry.name() + "20.3");
        assertEquals(aEntry.kind(),
                DisplayFormatCategory.classify(aEntry.name().toLowerCase(java.util.Locale.ROOT)),
                aEntry.name() + " lower-case, no dot");
    }


    /**
     * The roster is well-formed: unique names, a kind per row, a reference entry and page, a
     * deciding sentence that names the value kind (or the Details sentence for the week formats),
     * and the SC11 duration flag only on time formats.
     */
    @Test
    void rosterRowsAreWellFormed()
    {
        Set<String> names = new HashSet<>();
        for (Sas94TemporalFormatRoster.Entry e : Sas94TemporalFormatRoster.ENTRIES)
        {
            assertTrue(names.add(e.name()), "duplicate " + e.name());
            assertTrue(e.name().matches("[A-Z][A-Z0-9]*[A-Z]"), e.name());
            assertTrue(e.kind().isSasEpoch(), e.name() + " " + e.kind());
            assertTrue("FMTINFO".equals(e.entry()) ? e.page() == 0
                    : e.page() > 0 && !e.entry().isBlank(), e.name());
            String s = e.sentence().toLowerCase(java.util.Locale.ROOT);
            String expectedWord = switch (e.kind())
            {
            case DATE_SAS -> "date";
            case DATETIME_SAS -> "datetime";
            default -> "time";
            };
            assertTrue(s.contains(expectedWord), e.name() + ": " + e.sentence());
            assertFalse(e.duration() && e.kind() != DisplayFormatCategory.TIME_SAS, e.name());
        }
    }


    /**
     * The counts of the owner's SAS 9.4M9 FMTINFO run (2026-10-15, SC14): 302 date, 75 datetime, 27
     * time names, 404 in all, four of them durations; 161 of them are entries of the SAS 9.4
     * reference. Exact, so that a name added to or dropped from either side is seen here as well as
     * in the bijection.
     */
    @Test
    void rosterCountsMatchTheReference()
    {
        Map<DisplayFormatCategory, Long> byKind = Sas94TemporalFormatRoster.ENTRIES.stream()
                .collect(Collectors.groupingBy(Sas94TemporalFormatRoster.Entry::kind,
                        Collectors.counting()));
        assertEquals(404, Sas94TemporalFormatRoster.ENTRIES.size());
        assertEquals(302L, byKind.get(DisplayFormatCategory.DATE_SAS));
        assertEquals(75L, byKind.get(DisplayFormatCategory.DATETIME_SAS));
        assertEquals(27L, byKind.get(DisplayFormatCategory.TIME_SAS));
        assertEquals(161L, Sas94TemporalFormatRoster.ENTRIES.stream()
                .filter(e -> !"FMTINFO".equals(e.entry())).count(), "reference entries");
        assertEquals(Set.of("TIME", "MMSS", "HHMM", "HOUR"),
                Sas94TemporalFormatRoster.ENTRIES.stream()
                        .filter(Sas94TemporalFormatRoster.Entry::duration)
                        .map(Sas94TemporalFormatRoster.Entry::name).collect(Collectors.toSet()));
    }


    /** Every separator variant of the seven families is a date, with the letter set per entry. */
    @Test
    void separatorVariantsClassifyAsTheirFamily()
    {
        Set<String> names = Sas94TemporalFormatRoster.ENTRIES.stream()
                .map(Sas94TemporalFormatRoster.Entry::name).collect(Collectors.toSet());
        for (String family : List.of("DDMMYY", "MMDDYY", "YYMMDD"))
        {
            for (String x : List.of("B", "C", "D", "N", "P", "S"))
            {
                assertTrue(names.contains(family + x), family + x);
                assertEquals(DisplayFormatCategory.DATE_SAS,
                        DisplayFormatCategory.classify(family + x + "10."), family + x);
            }
        }
        for (String family : List.of("MMYY", "YYMM", "YYQ", "YYQR"))
        {
            for (String x : List.of("C", "D", "N", "P", "S"))
            {
                assertTrue(names.contains(family + x), family + x);
                assertEquals(DisplayFormatCategory.DATE_SAS,
                        DisplayFormatCategory.classify(family + x + "7."), family + x);
            }
            assertFalse(names.contains(family + "B"), family + "B admits no blank separator");
            assertEquals(DisplayFormatCategory.OTHER,
                    DisplayFormatCategory.classify(family + "B7."), family + "B");
        }
    }


    /**
     * SC8: only names SAS files under a temporal category classify — a user format whose name
     * contains {@code DATE}, a {@code $} character format, {@code NLSTRMON} (a month name from a
     * month number) and {@code PDJULG} / {@code PDJULI} (FMTINFO: {@code binary}, SC14) are
     * {@link DisplayFormatCategory#OTHER}.
     */
    @ParameterizedTest
    @ValueSource(strings =
    {
            "MYDATE8.", "DATEFMT.", "$DATEF.", "$DATE9.", "$CHAR10.", "NLSTRMON.", "NLSTRQTR.",
            "NLSTRWK.", "$N8601B.", "PDJULG.", "PDJULI.", "DATEX.", "TIMEX.", "DT.", "BEST12.",
            "8.", "F8.2", "COMMA12."
    })
    void undocumentedAndCharacterNamesAreOther(String aFormat)
    {
        assertEquals(DisplayFormatCategory.OTHER, DisplayFormatCategory.classify(aFormat), aFormat);
    }


    /**
     * The bijection: {@link DisplayFormatCategory#sasFormatNames} and the roster name the same set
     * per kind, in both directions, so a name present in one and absent from the other is a red row
     * here (sensitivity control at the go: one name deleted from a set in the working tree reds
     * this test; restored with {@code cp}, {@code cmp} against the backup).
     */
    @ParameterizedTest
    @MethodSource("kinds")
    void classifierSetsAndRosterAreTheSameRoster(DisplayFormatCategory aKind)
    {
        Set<String> roster = Sas94TemporalFormatRoster.ENTRIES.stream()
                .filter(e -> e.kind() == aKind).map(Sas94TemporalFormatRoster.Entry::name)
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> classifier = new TreeSet<>(DisplayFormatCategory.sasFormatNames(aKind));
        assertFalse(roster.isEmpty(), aKind + " has roster rows");
        assertEquals(roster, classifier, aKind.toString());
    }


    static List<DisplayFormatCategory> kinds()
    {
        return List.of(DisplayFormatCategory.DATE_SAS, DisplayFormatCategory.DATETIME_SAS,
                DisplayFormatCategory.TIME_SAS);
    }


    /** {@code isSasDuration} is exactly the roster's duration flag. */
    @ParameterizedTest
    @MethodSource("roster")
    void durationFlagMatchesIsSasDuration(Sas94TemporalFormatRoster.Entry aEntry)
    {
        assertEquals(aEntry.duration(), DisplayFormatCategory.isSasDuration(aEntry.name() + "8."),
                aEntry.name());
    }


    /**
     * The Unix kinds and OTHER own no SAS names; the three SAS sets are disjoint and unmodifiable.
     */
    @Test
    void sasFormatNamesAreDisjointAndUnmodifiable()
    {
        assertEquals(Set.of(), DisplayFormatCategory.sasFormatNames(DisplayFormatCategory.OTHER));
        assertEquals(Set.of(),
                DisplayFormatCategory.sasFormatNames(DisplayFormatCategory.DATE_UNIX));
        assertEquals(Set.of(),
                DisplayFormatCategory.sasFormatNames(DisplayFormatCategory.DATETIME_UNIX));
        Set<String> all = new HashSet<>();
        int total = 0;
        for (DisplayFormatCategory kind : kinds())
        {
            Set<String> names = DisplayFormatCategory.sasFormatNames(kind);
            total += names.size();
            all.addAll(names);
            org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                    () -> names.add("X"), kind.toString());
        }
        assertEquals(total, all.size(), "the three SAS sets are disjoint");
        assertEquals(Sas94TemporalFormatRoster.ENTRIES.size(), total);
    }


    /** The roster test data is sorted by name, so a row is found where a reader looks for it. */
    @Test
    void rosterIsSortedByName()
    {
        List<String> names = Sas94TemporalFormatRoster.ENTRIES.stream()
                .map(Sas94TemporalFormatRoster.Entry::name).toList();
        assertEquals(List.copyOf(new TreeSet<>(names)), names);
    }
}
