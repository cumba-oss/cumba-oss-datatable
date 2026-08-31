package net.cumba.datatable.report;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValidationReportTest
{

    // ==================== Builder ====================

    @Test
    void testBuilder()
    {
        ValidationReportMember member = ValidationReportMember.builder().domain("DM").build();

        ValidationReport report = ValidationReport.builder().members(List.of(member)).build();

        assertEquals(1, report.getMembers().size());
    }


    @Test
    void testBuilderMultipleMembers()
    {
        ValidationReportMember dm = ValidationReportMember.builder().domain("DM").build();
        ValidationReportMember ae = ValidationReportMember.builder().domain("AE").build();
        ValidationReportMember vs = ValidationReportMember.builder().domain("VS").build();

        ValidationReport report = ValidationReport.builder().members(List.of(dm, ae, vs)).build();

        assertEquals(3, report.getMembers().size());
    }

    // ==================== getMembers ====================


    @Test
    void testGetMembersReturnsUnmodifiableList()
    {
        List<ValidationReportMember> members = new ArrayList<>();
        members.add(ValidationReportMember.builder().domain("DM").build());

        ValidationReport report = ValidationReport.builder().members(members).build();

        List<ValidationReportMember> result = report.getMembers();

        assertThrows(UnsupportedOperationException.class, () ->
        {
            result.add(ValidationReportMember.builder().domain("AE").build());
        });
    }


    @Test
    void testGetMembersNullReturnsEmptyList()
    {
        ValidationReport report = ValidationReport.builder().members(null).build();

        List<ValidationReportMember> result = report.getMembers();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }


    @Test
    void testGetMembersEmptyListReturnsEmpty()
    {
        ValidationReport report = ValidationReport.builder().members(List.of()).build();

        List<ValidationReportMember> result = report.getMembers();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==================== getForMember by domain ====================


    @Test
    void testGetForMemberByDomainExactMatch()
    {
        ValidationReportMember dm = ValidationReportMember.builder().domain("DM").build();
        ValidationReportMember ae = ValidationReportMember.builder().domain("AE").build();

        ValidationReport report = ValidationReport.builder().members(List.of(dm, ae)).build();

        ValidationReportMember result = report.getForMember("DM");

        assertNotNull(result);
        assertEquals("DM", result.getDomain());
    }


    @Test
    void testGetForMemberByDomainCaseInsensitive()
    {
        ValidationReportMember dm = ValidationReportMember.builder().domain("DM").build();

        ValidationReport report = ValidationReport.builder().members(List.of(dm)).build();

        // Domain matching is case-insensitive
        assertNotNull(report.getForMember("dm"));
        assertNotNull(report.getForMember("Dm"));
        assertNotNull(report.getForMember("dM"));
    }


    @Test
    void testGetForMemberByDomainWithExtension()
    {
        ValidationReportMember dm = ValidationReportMember.builder().domain("DM").build();

        ValidationReport report = ValidationReport.builder().members(List.of(dm)).build();

        // Name with extension should strip extension and match domain
        ValidationReportMember result = report.getForMember("dm.sas7bdat");

        assertNotNull(result);
        assertEquals("DM", result.getDomain());
    }

    // ==================== getForMember by fileName ====================


    @Test
    void testGetForMemberByFileNameFallback()
    {
        // Domain doesn't match but fileName does
        ValidationReportMember member = ValidationReportMember.builder().domain("SUPPAE")
                .fileName("suppae_custom.csv").build();

        ValidationReport report = ValidationReport.builder().members(List.of(member)).build();

        // Search for fileName that doesn't match domain extraction
        ValidationReportMember result = report.getForMember("suppae_custom.csv");

        assertNotNull(result);
        assertEquals("suppae_custom.csv", result.getFileName());
    }


    @Test
    void testGetForMemberByFileNameCaseSensitive()
    {
        ValidationReportMember member = ValidationReportMember.builder().domain("OTHER")
                .fileName("Data.csv").build();

        ValidationReport report = ValidationReport.builder().members(List.of(member)).build();

        // fileName matching is case-sensitive (uses equals, not equalsIgnoreCase)
        assertNotNull(report.getForMember("Data.csv"));
        assertNull(report.getForMember("data.csv")); // Case mismatch
    }

    // ==================== getForMember not found ====================


    @Test
    void testGetForMemberNotFound()
    {
        ValidationReportMember dm = ValidationReportMember.builder().domain("DM").build();

        ValidationReport report = ValidationReport.builder().members(List.of(dm)).build();

        assertNull(report.getForMember("NONEXISTENT"));
    }


    @Test
    void testGetForMemberNullMembers()
    {
        ValidationReport report = ValidationReport.builder().members(null).build();

        assertNull(report.getForMember("DM"));
    }


    @Test
    void testGetForMemberEmptyMembers()
    {
        ValidationReport report = ValidationReport.builder().members(List.of()).build();

        assertNull(report.getForMember("DM"));
    }

    // ==================== getForMember domain extraction ====================


    @Test
    void testGetForMemberDomainExtractionBeforeDot()
    {
        ValidationReportMember dm = ValidationReportMember.builder().domain("DM").build();

        ValidationReport report = ValidationReport.builder().members(List.of(dm)).build();

        // Should extract "DM" from "DM.xpt" and match
        ValidationReportMember result = report.getForMember("DM.xpt");

        assertNotNull(result);
    }


    @Test
    void testGetForMemberDomainExtractionNoDot()
    {
        ValidationReportMember dm = ValidationReportMember.builder().domain("DM").build();

        ValidationReport report = ValidationReport.builder().members(List.of(dm)).build();

        // Name without dot - use as is
        ValidationReportMember result = report.getForMember("DM");

        assertNotNull(result);
    }

    // ==================== toBuilder ====================


    @Test
    void testToBuilder()
    {
        ValidationReportMember dm = ValidationReportMember.builder().domain("DM").build();

        ValidationReport original = ValidationReport.builder().members(List.of(dm)).build();

        ValidationReportMember ae = ValidationReportMember.builder().domain("AE").build();
        ValidationReport modified = original.toBuilder().members(List.of(ae)).build();

        assertEquals("AE", modified.getMembers().get(0).getDomain());
        assertEquals("DM", original.getMembers().get(0).getDomain()); // Original unchanged
    }

    // ==================== equals / hashCode ====================


    @Test
    void testEqualsAndHashCode()
    {
        ValidationReportMember dm = ValidationReportMember.builder().domain("DM").build();

        ValidationReport a = ValidationReport.builder().members(List.of(dm)).build();

        ValidationReport b = ValidationReport.builder().members(List.of(dm)).build();

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }


    @Test
    void testNotEqualsDifferentMembers()
    {
        ValidationReportMember dm = ValidationReportMember.builder().domain("DM").build();
        ValidationReportMember ae = ValidationReportMember.builder().domain("AE").build();

        ValidationReport a = ValidationReport.builder().members(List.of(dm)).build();

        ValidationReport b = ValidationReport.builder().members(List.of(ae)).build();

        assertNotEquals(a, b);
    }

    // ==================== getSkippedRules ====================


    @Test
    void testGetSkippedRulesNullReturnsEmptyList()
    {
        ValidationReport report = ValidationReport.builder().members(List.of()).build();

        List<SkippedRuleEntry> result = report.getSkippedRules();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }


    @Test
    void testGetSkippedRulesCarriesEntriesInOrder()
    {
        SkippedRuleEntry first = SkippedRuleEntry.builder().coreId("CORE-1").dataset("EX")
                .reason("domain EX not in Scope.Domains.Include [AE]").build();
        SkippedRuleEntry second = SkippedRuleEntry.builder().coreId("CORE-1").dataset("SUPPEX")
                .reason("domain SUPPEX not in Scope.Domains.Include [AE]").build();

        ValidationReport report = ValidationReport.builder().members(List.of())
                .skippedRules(List.of(first, second)).build();

        assertEquals(List.of(first, second), report.getSkippedRules());
    }


    @Test
    void testGetSkippedRulesReturnsUnmodifiableList()
    {
        List<SkippedRuleEntry> skipped = new ArrayList<>();
        skipped.add(SkippedRuleEntry.builder().coreId("CORE-1").dataset("EX").reason("r").build());

        ValidationReport report = ValidationReport.builder().members(List.of())
                .skippedRules(skipped).build();

        List<SkippedRuleEntry> result = report.getSkippedRules();

        assertThrows(UnsupportedOperationException.class, () ->
        {
            result.add(
                    SkippedRuleEntry.builder().coreId("CORE-2").dataset("DM").reason("r").build());
        });
    }

    // ==================== Executed core ids (Fix #225) ====================


    @Test
    void testGetExecutedCoreIdsNullReturnsEmptyList()
    {
        // A report deserialised from a pre-Fix-#225 document carries no executed set. It must read
        // as empty, never null — a consumer that NPEs here would break on every stored report.
        ValidationReport report = ValidationReport.builder().members(List.of()).build();

        List<String> result = report.getExecutedCoreIds();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }


    @Test
    void testGetExecutedCoreIdsCarriesIdsInOrder()
    {
        ValidationReport report = ValidationReport.builder().members(List.of())
                .executedCoreIds(List.of("CORE-1", "CORE-2")).build();

        assertEquals(List.of("CORE-1", "CORE-2"), report.getExecutedCoreIds());
    }


    @Test
    void testGetExecutedCoreIdsReturnsUnmodifiableList()
    {
        List<String> executed = new ArrayList<>();
        executed.add("CORE-1");

        ValidationReport report = ValidationReport.builder().members(List.of())
                .executedCoreIds(executed).build();

        List<String> result = report.getExecutedCoreIds();

        assertThrows(UnsupportedOperationException.class, () -> result.add("CORE-2"));
    }
}
