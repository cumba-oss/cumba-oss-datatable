package net.cumba.datatable.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValidationFindingLocationTest
{

    @Test
    void emptyConstantHasNoDatasetAndNoVariables()
    {
        assertTrue(ValidationFindingLocation.EMPTY.isEmpty());
        assertEquals(List.of(), ValidationFindingLocation.EMPTY.getVariableNames());
        assertEquals(null, ValidationFindingLocation.EMPTY.getDataset());
    }


    @Test
    void getVariableNamesIsNeverNull()
    {
        ValidationFindingLocation loc = ValidationFindingLocation.builder().dataset("AE").build();
        assertEquals(List.of(), loc.getVariableNames());
    }


    @Test
    void getVariableNamesIsUnmodifiable()
    {
        ValidationFindingLocation loc = ValidationFindingLocation.builder().dataset("AE")
                .variableNames(List.of("AETERM")).build();
        assertThrows(UnsupportedOperationException.class, () -> loc.getVariableNames().add("X"));
    }


    @Test
    void isEmptyReflectsContent()
    {
        assertTrue(ValidationFindingLocation.builder().build().isEmpty());
        assertFalse(ValidationFindingLocation.builder().dataset("AE").build().isEmpty());
        assertFalse(ValidationFindingLocation.builder().variableNames(List.of("AETERM")).build()
                .isEmpty());
    }


    @Test
    void valueSemanticsEqualsHashCode()
    {
        ValidationFindingLocation a = ValidationFindingLocation.builder().dataset("AE")
                .variableNames(List.of("AETERM")).build();
        ValidationFindingLocation b = ValidationFindingLocation.builder().dataset("AE")
                .variableNames(List.of("AETERM")).build();
        ValidationFindingLocation c = ValidationFindingLocation.builder().dataset("AE")
                .variableNames(List.of("AEDECOD")).build();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }


    @Test
    void groupKeyedIsTheGroupKeySourceEvenWithAnEmptyKeyList()
    {
        // Owner Q-A (2026-10-05): a Group finding is located by its grouping variables; when the
        // data set carries none the key list is empty but the finding is still a group finding,
        // so the GROUP source is the one that may come with an empty list.
        ValidationFindingLocation keyed = ValidationFindingLocation.builder().dataset("AE")
                .keyVariableNames(List.of("USUBJID"))
                .keySource(ValidationFindingLocation.GROUP_KEY_SOURCE).build();
        ValidationFindingLocation oneGroup = ValidationFindingLocation.builder().dataset("AE")
                .keyVariableNames(List.of()).keySource(ValidationFindingLocation.GROUP_KEY_SOURCE)
                .build();
        ValidationFindingLocation record = ValidationFindingLocation.builder().dataset("AE")
                .keyVariableNames(List.of("USUBJID", "AESEQ")).keySource("NATURAL").build();
        assertEquals("GROUP", ValidationFindingLocation.GROUP_KEY_SOURCE);
        assertTrue(keyed.isGroupKeyed());
        assertTrue(oneGroup.isGroupKeyed());
        assertFalse(record.isGroupKeyed());
        assertFalse(ValidationFindingLocation.EMPTY.isGroupKeyed());
    }


    @Test
    void jsonRoundTrip() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();
        ValidationFindingLocation loc = ValidationFindingLocation.builder().dataset("LBHE")
                .variableNames(List.of("LBORRES", "LBSTRESC")).build();
        String json = mapper.writeValueAsString(loc);
        ValidationFindingLocation back = mapper.readValue(json, ValidationFindingLocation.class);
        assertEquals(loc, back);
    }

}
