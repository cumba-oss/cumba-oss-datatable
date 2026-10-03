package net.cumba.datatable.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link ValidationReport#getExecutedCoreIds()} — the companion {@code skippedRules} needs to be
 * readable.
 *
 * <p>
 * A clean execution leaves no finding, so "no findings" cannot distinguish <em>skipped
 * everywhere</em> from <em>ran and found nothing</em>. A rule is reportable as skipped only when it
 * carries a skip entry <b>and</b> does not appear in the executed list — which is the disposition
 * {@link #aRuleSkippedOnOneDatasetAndRunOnAnotherIsNotReportableAsSkipped()} pins.
 * </p>
 */
class ValidationReportExecutedCoreIdsTest
{

    @Test
    void defaultsToAnEmptyListRatherThanNull()
    {
        ValidationReport r = ValidationReport.builder().build();

        assertNotNull(r.getExecutedCoreIds());
        assertTrue(r.getExecutedCoreIds().isEmpty());
    }


    @Test
    void returnsTheIdsInFirstSeenOrder()
    {
        ValidationReport r = ValidationReport.builder()
                .executedCoreIds(List.of("CORE-000123", "CORE-000045", "CORE-000999")).build();

        assertEquals(List.of("CORE-000123", "CORE-000045", "CORE-000999"), r.getExecutedCoreIds());
    }


    @Test
    void theReturnedListIsUnmodifiable()
    {
        ValidationReport r = ValidationReport.builder().executedCoreIds(List.of("CORE-000123"))
                .build();

        assertThrows(UnsupportedOperationException.class,
                () -> r.getExecutedCoreIds().add("CORE-000456"));
    }


    /** A defensive read: mutating the source list must not be observable through the accessor. */
    @Test
    void theAccessorDoesNotExposeAMutableSource()
    {
        List<String> source = new ArrayList<>();
        source.add("CORE-000123");
        ValidationReport r = ValidationReport.builder().executedCoreIds(source).build();

        assertThrows(UnsupportedOperationException.class,
                () -> r.getExecutedCoreIds().add("CORE-000456"));
        assertEquals(1, r.getExecutedCoreIds().size());
    }


    /**
     * The disposition rule the field exists for: a skip entry alone does not mean "never ran".
     */
    @Test
    void aRuleSkippedOnOneDatasetAndRunOnAnotherIsNotReportableAsSkipped()
    {
        ValidationReport r = ValidationReport.builder()
                .skippedRules(List
                        .of(SkippedRuleEntry.builder().coreId("CORE-000123").dataset("AE").build()))
                .executedCoreIds(List.of("CORE-000123")).build();

        assertEquals(1, r.getSkippedRules().size());
        assertTrue(r.getExecutedCoreIds().contains("CORE-000123"));

        boolean reportableAsSkipped = r.getSkippedRules().stream().map(SkippedRuleEntry::getCoreId)
                .anyMatch(id -> !r.getExecutedCoreIds().contains(id));
        assertTrue(!reportableAsSkipped,
                "a rule that ran somewhere must not be reported as skipped");
    }


    @Test
    void aRuleSkippedEverywhereIsReportableAsSkipped()
    {
        ValidationReport r = ValidationReport.builder()
                .skippedRules(List
                        .of(SkippedRuleEntry.builder().coreId("CORE-000777").dataset("AE").build()))
                .executedCoreIds(List.of("CORE-000123")).build();

        boolean reportableAsSkipped = r.getSkippedRules().stream().map(SkippedRuleEntry::getCoreId)
                .anyMatch(id -> !r.getExecutedCoreIds().contains(id));
        assertTrue(reportableAsSkipped);
    }
}
