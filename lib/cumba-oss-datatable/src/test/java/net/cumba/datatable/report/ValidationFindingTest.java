package net.cumba.datatable.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ValidationFindingTest
{

    // ==================== Helper ====================

    /** Build a RECORD slab with a single row. */
    private static RowFindingSlab slab(int rowIndex, String... nameValuePairs)
    {
        if (nameValuePairs.length == 0)
        {
            return RowFindingSlab.EMPTY;
        }
        int vc = nameValuePairs.length / 2;
        String[] values = IntStream.range(0, vc).mapToObj(i -> nameValuePairs[2 * i + 1])
                .toArray(String[]::new);
        return RowFindingSlab.builder(vc).addRow(rowIndex, values).build();
    }


    private static List<String> names(String... nameValuePairs)
    {
        List<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < nameValuePairs.length; i += 2)
        {
            out.add(nameValuePairs[i]);
        }
        return out;
    }

    // ==================== Builder ====================


    @Test
    void testBuilder()
    {
        ValidationFinding finding = ValidationFinding.builder().source("P3").ruleId("SD0001")
                .kind(FindingKind.RULE_VIOLATION).severity(Severity.ERROR)
                .executability("Fully Executable").message("Variable AGE is missing")
                .variableNames(names("AGE", "25")).rows(slab(4, "AGE", "25")).build();

        assertEquals("P3", finding.getSource());
        assertEquals("SD0001", finding.getRuleId());
        assertEquals(Severity.ERROR, finding.getSeverity());
        assertEquals("Fully Executable", finding.getExecutability());
        assertEquals(FindingKind.RULE_VIOLATION, finding.getKind());
        assertEquals("Variable AGE is missing", finding.getMessage());
        assertEquals(5, finding.getRow());
        assertEquals(4, finding.getRowIndex());
        assertEquals(1, finding.getRowCount());
    }

    // ==================== Slab accessors ====================


    @Test
    void testEmptyRowsDefault()
    {
        ValidationFinding finding = ValidationFinding.builder().build();
        assertEquals(RowFindingSlab.EMPTY, finding.getRows());
        assertEquals(0, finding.getRowCount());
        assertFalse(finding.hasRows());
    }


    @Test
    void testGetRowValues()
    {
        ValidationFinding finding = ValidationFinding.builder()
                .variableNames(List.of("AGE", "WEIGHT"))
                .rows(RowFindingSlab.builder(2).addRow(0, new String[]
                {
                        "25", "70"
                }).build()).build();

        var map = finding.getFirstRowValues();
        assertEquals(2, map.size());
        assertEquals("25", map.get("AGE"));
        assertEquals("70", map.get("WEIGHT"));
    }


    @Test
    void testHasRowsFalseEmpty()
    {
        assertFalse(ValidationFinding.builder().build().hasRows());
    }


    @Test
    void testHasRowsTrue()
    {
        ValidationFinding f = ValidationFinding.builder()
                .rows(RowFindingSlab.builder(0).addRow(3, new String[0]).build()).build();
        assertTrue(f.hasRows());
    }


    @Test
    void testContainsRow()
    {
        ValidationFinding f = ValidationFinding.builder()
                .rows(RowFindingSlab.builder(0).addRow(2, new String[0]).addRow(5, new String[0])
                        .addRow(10, new String[0]).build())
                .build();
        assertTrue(f.containsRow(5));
        assertFalse(f.containsRow(7));
    }


    @Test
    void testRowIndices()
    {
        ValidationFinding f = ValidationFinding.builder()
                .rows(RowFindingSlab.builder(0).addRow(10, new String[0]).addRow(2, new String[0])
                        .addRow(5, new String[0]).build())
                .build();
        int[] indices = f.rowIndices().toArray();
        // builder.build sorts ascending.
        assertEquals(3, indices.length);
        assertEquals(2, indices[0]);
        assertEquals(5, indices[1]);
        assertEquals(10, indices[2]);
    }

    // ==================== toBuilder ====================


    @Test
    void testToBuilder()
    {
        ValidationFinding original = ValidationFinding.builder().severity(Severity.ERROR)
                .message("Original").build();

        ValidationFinding modified = original.toBuilder().severity(Severity.WARNING).build();

        assertEquals(Severity.WARNING, modified.getSeverity());
        assertEquals("Original", modified.getMessage());
        assertEquals(Severity.ERROR, original.getSeverity());
    }

    // ==================== equals / hashCode ====================


    @Test
    void testEqualsAndHashCode()
    {
        ValidationFinding a = ValidationFinding.builder().source("P1").ruleId("E1")
                .severity(Severity.ERROR).message("Test").variableNames(List.of("AGE"))
                .rows(slab(0, "AGE", "25")).build();

        ValidationFinding b = ValidationFinding.builder().source("P1").ruleId("E1")
                .severity(Severity.ERROR).message("Test").variableNames(List.of("AGE"))
                .rows(slab(0, "AGE", "25")).build();

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }


    @Test
    void testVariableNamesArePartOfIdentity()
    {
        ValidationFinding a = ValidationFinding.builder().variableNames(List.of("AGE")).build();
        ValidationFinding b = ValidationFinding.builder().variableNames(List.of("SEX")).build();
        assertNotEquals(a, b);
    }


    @Test
    void testNamedColumnsArePartOfIdentity()
    {
        // PLAN-finding-values-record-level-only V8 (b): the columns a finding lists by name
        // without a value are part of what the finding says, like its variable schema.
        ValidationFinding a = ValidationFinding.builder().variableNames(List.of("$count"))
                .namedColumns(List.of("RANDFL")).build();
        ValidationFinding b = ValidationFinding.builder().variableNames(List.of("$count"))
                .namedColumns(List.of("ARMCD")).build();
        ValidationFinding c = ValidationFinding.builder().variableNames(List.of("$count"))
                .namedColumns(List.of("RANDFL")).build();
        assertNotEquals(a, b);
        assertEquals(a, c);
        assertEquals(a.hashCode(), c.hashCode());
    }


    @Test
    void testNamedColumnsNeverNullAndUnmodifiable()
    {
        ValidationFinding unset = ValidationFinding.builder().build();
        assertEquals(List.of(), unset.getNamedColumns(), "unset lists nothing, never null");
        ValidationFinding set = ValidationFinding.builder()
                .namedColumns(List.of("AEDTC", "AESTDTC")).build();
        assertEquals(List.of("AEDTC", "AESTDTC"), set.getNamedColumns(),
                "the finding's effective order is kept");
        assertThrows(UnsupportedOperationException.class, () -> set.getNamedColumns().add("X"));
        // toBuilder carries the list — a rebuilt finding must not silently drop it (§7 M3).
        assertEquals(List.of("AEDTC", "AESTDTC"),
                set.toBuilder().message("m").build().getNamedColumns());
    }


    @Test
    void testNamedColumnsSurviveJsonRoundTrip() throws Exception
    {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        ValidationFinding f = ValidationFinding.builder().source("cumba.core").ruleId("R")
                .kind(FindingKind.RULE_VIOLATION).scope(FindingScope.DATASET)
                .variableNames(List.of("$count")).namedColumns(List.of("RANDFL"))
                .rows(slab(0, "$count", "3")).build();
        ValidationFinding back = mapper.readValue(mapper.writeValueAsString(f),
                ValidationFinding.class);
        assertEquals(List.of("RANDFL"), back.getNamedColumns());
        assertEquals(f, back);
    }


    @Test
    void testSlabIsNotInIdentity()
    {
        ValidationFinding a = ValidationFinding.builder().variableNames(List.of("AGE"))
                .rows(slab(0, "AGE", "25")).build();
        ValidationFinding b = ValidationFinding.builder().variableNames(List.of("AGE"))
                .rows(slab(1, "AGE", "30")).build();
        assertEquals(a, b);
    }


    @Test
    void testNotEqualsDifferentScope()
    {
        ValidationFinding a = ValidationFinding.builder().scope(FindingScope.RECORD).build();
        ValidationFinding b = ValidationFinding.builder().scope(FindingScope.DATASET).build();
        assertNotEquals(a, b);
    }


    @Test
    void testLocationIsPartOfIdentity()
    {
        ValidationFinding a = ValidationFinding.builder().variableNames(List.of("VARIABLE"))
                .location(ValidationFindingLocation.builder().dataset("AE")
                        .variableNames(List.of("AETERM")).build())
                .build();
        ValidationFinding b = ValidationFinding.builder().variableNames(List.of("VARIABLE"))
                .location(ValidationFindingLocation.builder().dataset("AE")
                        .variableNames(List.of("AEDECOD")).build())
                .build();
        assertNotEquals(a, b);
    }


    @Test
    void testGetLocationNeverNull()
    {
        assertSame(ValidationFindingLocation.EMPTY,
                ValidationFinding.builder().build().getLocation());
        ValidationFindingLocation loc = ValidationFindingLocation.builder().dataset("AE").build();
        assertSame(loc, ValidationFinding.builder().location(loc).build().getLocation());
    }

    // ==================== effective scope ====================


    @Test
    void testEffectiveScopePrefersExplicit()
    {
        ValidationFinding finding = ValidationFinding.builder().scope(FindingScope.DATASET)
                .rows(slab(3, "AGE", "25")).build();
        assertEquals(FindingScope.DATASET, finding.getEffectiveScope(),
                "an explicit scope wins over the row-bearing shape");
    }


    @Test
    void testEffectiveScopeDerivedFromShape()
    {
        assertEquals(FindingScope.RECORD,
                ValidationFinding.builder()
                        .rows(RowFindingSlab.builder(0).addRow(0, new String[0]).build()).build()
                        .getEffectiveScope());
        assertEquals(FindingScope.VARIABLE, ValidationFinding.builder()
                .variableNames(List.of("AGE")).build().getEffectiveScope());
        assertEquals(FindingScope.DATASET, ValidationFinding.builder().build().getEffectiveScope());
    }

    // ==================== Null fields ====================

    // ==================== EC-40 record key ====================


    @Test
    void testNullFields()
    {
        ValidationFinding f = ValidationFinding.builder().build();
        assertNull(f.getSource());
        assertNull(f.getRuleId());
        assertNull(f.getKind());
        assertNull(f.getSeverity());
        assertNull(f.getExecutability());
        assertNull(f.getScope());
        assertNull(f.getMessage());
        assertEquals(0, f.getRow());
        assertEquals(-1, f.getRowIndex());
        assertTrue(f.getVariableNames().isEmpty());
        assertEquals(0, f.getRowCount());
    }
}
