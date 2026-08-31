package net.cumba.datatable.report;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValidationReportMemberTest
{

    // ==================== Builder ====================

    @Test
    void testBuilder()
    {
        ValidationReportMember member = ValidationReportMember.builder().fileName("ae.sas7bdat")
                .domain("AE").build();

        assertEquals("ae.sas7bdat", member.getFileName());
        assertEquals("AE", member.getDomain());
    }


    @Test
    void testBuilderWithFindings()
    {
        ValidationFinding finding = ValidationFinding.builder().message("Test finding").build();

        ValidationReportMember member = ValidationReportMember.builder().domain("DM")
                .findings(List.of(finding)).build();

        assertEquals(1, member.getFindings().size());
    }

    // ==================== getFindings ====================


    @Test
    void testGetFindingsReturnsUnmodifiableList()
    {
        List<ValidationFinding> findings = new ArrayList<>();
        findings.add(ValidationFinding.builder().message("Test").build());

        ValidationReportMember member = ValidationReportMember.builder().findings(findings).build();

        List<ValidationFinding> result = member.getFindings();

        assertThrows(UnsupportedOperationException.class, () ->
        {
            result.add(ValidationFinding.builder().message("New").build());
        });
    }


    @Test
    void testGetFindingsNullReturnsEmptyList()
    {
        ValidationReportMember member = ValidationReportMember.builder().findings(null).build();

        List<ValidationFinding> result = member.getFindings();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }


    @Test
    void testGetFindingsEmptyListReturnsEmpty()
    {
        ValidationReportMember member = ValidationReportMember.builder().findings(List.of())
                .build();

        List<ValidationFinding> result = member.getFindings();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==================== containsFindings ====================


    @Test
    void testContainsFindingsTrue()
    {
        ValidationFinding finding = ValidationFinding.builder().message("Test").build();

        ValidationReportMember member = ValidationReportMember.builder().findings(List.of(finding))
                .build();

        assertTrue(member.containsFindings());
    }


    @Test
    void testContainsFindingsFalseNull()
    {
        ValidationReportMember member = ValidationReportMember.builder().findings(null).build();

        assertFalse(member.containsFindings());
    }


    @Test
    void testContainsFindingsFalseEmpty()
    {
        ValidationReportMember member = ValidationReportMember.builder().findings(List.of())
                .build();

        assertFalse(member.containsFindings());
    }

    // ==================== getFindingsCount ====================


    @Test
    void testGetFindingsCountWithFindings()
    {
        ValidationFinding f1 = ValidationFinding.builder().message("Finding 1").build();
        ValidationFinding f2 = ValidationFinding.builder().message("Finding 2").build();
        ValidationFinding f3 = ValidationFinding.builder().message("Finding 3").build();

        ValidationReportMember member = ValidationReportMember.builder()
                .findings(List.of(f1, f2, f3)).build();

        assertEquals(3, member.getFindingsCount());
    }


    @Test
    void testGetFindingsCountNull()
    {
        ValidationReportMember member = ValidationReportMember.builder().findings(null).build();

        assertEquals(0, member.getFindingsCount());
    }


    @Test
    void testGetFindingsCountEmpty()
    {
        ValidationReportMember member = ValidationReportMember.builder().findings(List.of())
                .build();

        assertEquals(0, member.getFindingsCount());
    }

    // ==================== toBuilder ====================


    @Test
    void testToBuilder()
    {
        ValidationReportMember original = ValidationReportMember.builder().fileName("dm.sas7bdat")
                .domain("DM").build();

        ValidationReportMember modified = original.toBuilder().domain("AE").build();

        assertEquals("AE", modified.getDomain());
        assertEquals("dm.sas7bdat", modified.getFileName());
        assertEquals("DM", original.getDomain()); // Original unchanged
    }

    // ==================== equals / hashCode ====================


    @Test
    void testEqualsAndHashCode()
    {
        ValidationReportMember a = ValidationReportMember.builder().fileName("dm.sas7bdat")
                .domain("DM").build();

        ValidationReportMember b = ValidationReportMember.builder().fileName("dm.sas7bdat")
                .domain("DM").build();

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }


    @Test
    void testNotEqualsDifferentDomain()
    {
        ValidationReportMember a = ValidationReportMember.builder().domain("DM").build();

        ValidationReportMember b = ValidationReportMember.builder().domain("AE").build();

        assertNotEquals(a, b);
    }


    @Test
    void testNotEqualsDifferentFileName()
    {
        ValidationReportMember a = ValidationReportMember.builder().fileName("dm.sas7bdat").build();

        ValidationReportMember b = ValidationReportMember.builder().fileName("ae.sas7bdat").build();

        assertNotEquals(a, b);
    }

    // ==================== Getters ====================


    @Test
    void testGetFileName()
    {
        ValidationReportMember member = ValidationReportMember.builder().fileName("vs.sas7bdat")
                .build();

        assertEquals("vs.sas7bdat", member.getFileName());
    }


    @Test
    void testGetDomain()
    {
        ValidationReportMember member = ValidationReportMember.builder().domain("VS").build();

        assertEquals("VS", member.getDomain());
    }
}
