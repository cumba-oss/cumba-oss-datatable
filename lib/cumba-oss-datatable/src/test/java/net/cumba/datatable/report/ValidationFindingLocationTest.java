package net.cumba.datatable.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.jspecify.annotations.Nullable;
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


    private static ValidationFindingLocation group(@Nullable String aDataset, String... aKeys)
    {
        return ValidationFindingLocation.builder().dataset(aDataset)
                .keyVariableNames(List.of(aKeys))
                .keySource(ValidationFindingLocation.GROUP_KEY_SOURCE).build();
    }


    @Test
    void hiddenAnchorIdentityIsNeverHiddenOnANonGroupFinding()
    {
        ValidationFindingLocation record = ValidationFindingLocation.builder().dataset("AE")
                .keyVariableNames(List.of("STUDYID")).keySource("NATURAL").build();
        assertFalse(record.isHiddenAnchorIdentity("USUBJID"));
        assertFalse(record.isHiddenAnchorIdentity("SEQ"));
        assertFalse(ValidationFindingLocation.EMPTY.isHiddenAnchorIdentity("USUBJID"));
        assertFalse(ValidationFindingLocation.EMPTY.isHiddenAnchorIdentity("SEQ"));
    }


    @Test
    void hiddenAnchorIdentityHidesUsubjidUnlessItIsAGroupKey()
    {
        assertTrue(group("AE", "AETERM").isHiddenAnchorIdentity("USUBJID"));
        assertTrue(group("AE").isHiddenAnchorIdentity("USUBJID"));
        assertFalse(group("AE", "USUBJID", "AETERM").isHiddenAnchorIdentity("USUBJID"));
        // names compare ignoring case, on both sides
        assertFalse(group("AE", "usubjid").isHiddenAnchorIdentity("USUBJID"));
        assertFalse(group("AE", "USUBJID").isHiddenAnchorIdentity("UsubjId"));
        assertTrue(group("AE", "AETERM").isHiddenAnchorIdentity("usubjid"));
    }


    @Test
    void hiddenAnchorIdentityHidesSeqUnlessSeqOrTheDatasetSeqIsAGroupKey()
    {
        assertTrue(group("AE", "USUBJID").isHiddenAnchorIdentity("SEQ"));
        // the data set's <DATASET>SEQ, case-insensitively on key, data set and name
        assertFalse(group("AE", "USUBJID", "AESEQ").isHiddenAnchorIdentity("SEQ"));
        assertFalse(group("ae", "aeseq").isHiddenAnchorIdentity("seq"));
        // a bare SEQ key
        assertFalse(group("AE", "SEQ").isHiddenAnchorIdentity("SEQ"));
        assertFalse(group("AE", "seq").isHiddenAnchorIdentity("Seq"));
        // another data set's sequence variable is not this one's
        assertTrue(group("AE", "CMSEQ").isHiddenAnchorIdentity("SEQ"));
        // a group location without a data set: only a bare SEQ key shows it
        assertTrue(group(null, "AESEQ").isHiddenAnchorIdentity("SEQ"));
        assertFalse(group(null, "SEQ").isHiddenAnchorIdentity("SEQ"));
    }


    @Test
    void hiddenAnchorIdentityNeverHidesAnyOtherColumn()
    {
        ValidationFindingLocation loc = group("AE", "AETERM");
        assertFalse(loc.isHiddenAnchorIdentity("AETERM"));
        assertFalse(loc.isHiddenAnchorIdentity("AESEQ"));
        assertFalse(loc.isHiddenAnchorIdentity("STUDYID"));
        assertFalse(loc.isHiddenAnchorIdentity("$count"));
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
