package net.cumba.datatable.report;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class SkippedRuleEntryTest
{

    // ==================== Builder / getters ====================

    @Test
    void testBuilderAndGetters()
    {
        SkippedRuleEntry entry = SkippedRuleEntry.builder().coreId("CORE-000351").dataset("EX")
                .reason("domain EX not in Scope.Domains.Include [AE]").build();

        assertEquals("CORE-000351", entry.getCoreId());
        assertEquals("EX", entry.getDataset());
        assertEquals("domain EX not in Scope.Domains.Include [AE]", entry.getReason());
    }


    @Test
    void testNullableFieldsMayBeNull()
    {
        SkippedRuleEntry entry = SkippedRuleEntry.builder().dataset("DM").build();

        assertNull(entry.getCoreId());
        assertEquals("DM", entry.getDataset());
        assertNull(entry.getReason());
    }

    // ==================== toBuilder ====================


    @Test
    void testToBuilder()
    {
        SkippedRuleEntry original = SkippedRuleEntry.builder().coreId("CORE-1").dataset("EX")
                .reason("r1").build();

        SkippedRuleEntry modified = original.toBuilder().dataset("SUPPEX").build();

        assertEquals("SUPPEX", modified.getDataset());
        assertEquals("CORE-1", modified.getCoreId());
        assertEquals("EX", original.getDataset()); // original unchanged
    }

    // ==================== equals / hashCode / toString ====================


    @Test
    void testEqualsAndHashCode()
    {
        SkippedRuleEntry a = SkippedRuleEntry.builder().coreId("CORE-1").dataset("EX").reason("r")
                .build();
        SkippedRuleEntry b = SkippedRuleEntry.builder().coreId("CORE-1").dataset("EX").reason("r")
                .build();

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }


    @Test
    void testNotEqualsDifferentReason()
    {
        SkippedRuleEntry a = SkippedRuleEntry.builder().coreId("CORE-1").dataset("EX").reason("r1")
                .build();
        SkippedRuleEntry b = SkippedRuleEntry.builder().coreId("CORE-1").dataset("EX").reason("r2")
                .build();

        assertNotEquals(a, b);
    }


    @Test
    void testToStringCarriesFields()
    {
        SkippedRuleEntry entry = SkippedRuleEntry.builder().coreId("CORE-1").dataset("EX")
                .reason("r").build();

        String s = entry.toString();
        assertTrue(s.contains("CORE-1"));
        assertTrue(s.contains("EX"));
    }

    // ==================== Jackson round-trip ====================


    @Test
    void testJacksonRoundTrip() throws Exception
    {
        SkippedRuleEntry entry = SkippedRuleEntry.builder().coreId("CORE-000351").dataset("SUPPEX")
                .reason("domain SUPPEX matches Scope.Domains.Exclude entry SUPP--").build();

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(entry);
        SkippedRuleEntry back = mapper.readValue(json, SkippedRuleEntry.class);

        assertEquals(entry, back);
    }
}
