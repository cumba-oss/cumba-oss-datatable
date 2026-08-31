package net.cumba.datatable.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FindingLocationsTest
{

    // ==================== isNonColumnToken ====================

    @Test
    void plainNameIsAColumn()
    {
        assertFalse(FindingLocations.isNonColumnToken("AETERM"));
        assertFalse(FindingLocations.isNonColumnToken("USUBJID"));
    }


    @Test
    void blankOrNullIsNotAColumn()
    {
        assertTrue(FindingLocations.isNonColumnToken(null));
        assertTrue(FindingLocations.isNonColumnToken(""));
        assertTrue(FindingLocations.isNonColumnToken("   "));
    }


    @Test
    void derivedCrossDatasetScalarWildcardAreNotColumns()
    {
        assertTrue(FindingLocations.isNonColumnToken("SUB:DTHDTC")); // prefixed derived ref
        assertTrue(FindingLocations.isNonColumnToken("DM.DTHDTC")); // dataset-qualified
        assertTrue(FindingLocations.isNonColumnToken("RELREC.**TERM")); // cross + wildcard
        assertTrue(FindingLocations.isNonColumnToken("$adsl_format")); // scalar op ref
        assertTrue(FindingLocations.isNonColumnToken("**DECOD")); // wildcard
    }

    // ==================== filterColumns ====================


    @Test
    void filterColumnsKeepsPlainDropsOthersPreservingOrder()
    {
        List<String> in = Arrays.asList("AEOUT", "AEENDTC", "SUB:DTHDTC", "$x", "RELREC.**T");
        assertEquals(List.of("AEOUT", "AEENDTC"), FindingLocations.filterColumns(in));
    }


    @Test
    void filterColumnsDeduplicatesCaseInsensitivelyKeepingFirst()
    {
        List<String> in = Arrays.asList("Age", "AGE", "sex", "AGE");
        assertEquals(List.of("Age", "sex"), FindingLocations.filterColumns(in));
    }


    @Test
    void filterColumnsNullOrEmptyIsEmpty()
    {
        assertEquals(List.of(), FindingLocations.filterColumns(null));
        assertEquals(List.of(), FindingLocations.filterColumns(List.of()));
    }

    // ==================== markerValue ====================


    @Test
    void markerValueResolvesPairedValueCaseInsensitiveMarker()
    {
        // P21: literal "VARIABLE"/"Variable" → real column from its value.
        assertEquals("AETERM", FindingLocations.markerValue(List.of("Excess", "Variable"),
                List.of("170", "AETERM"), Set.of("VARIABLE")));
        // CORE: "variable_name" → real column.
        assertEquals("AEDECOD",
                FindingLocations.markerValue(List.of("variable_name", "variable_value"),
                        List.of("AEDECOD", "null"), Set.of("variable_name")));
    }


    @Test
    void markerValueNoMarkerReturnsNull()
    {
        assertNull(FindingLocations.markerValue(List.of("AETERM", "AEDECOD"), List.of("x", "y"),
                Set.of("VARIABLE")));
    }


    @Test
    void markerValueBlankOrNonColumnValueReturnsNull()
    {
        assertNull(FindingLocations.markerValue(List.of("VARIABLE"), List.of("   "),
                Set.of("VARIABLE")));
        assertNull(FindingLocations.markerValue(List.of("VARIABLE"), List.of("DM.X"),
                Set.of("VARIABLE")));
        // Missing value (shorter values list).
        assertNull(
                FindingLocations.markerValue(List.of("VARIABLE"), List.of(), Set.of("VARIABLE")));
    }


    @Test
    void markerValueEmptyMarkersOrNullNamesReturnsNull()
    {
        assertNull(FindingLocations.markerValue(List.of("VARIABLE"), List.of("AGE"), Set.of()));
        assertNull(FindingLocations.markerValue(null, List.of("AGE"), Set.of("VARIABLE")));
    }

    // ==================== columnsFor ====================


    @Test
    void columnsForDatasetScopeIsAlwaysEmpty()
    {
        assertEquals(List.of(), FindingLocations.columnsFor(FindingScope.DATASET,
                List.of("DATASET"), List.of("NV"), Set.of("VARIABLE")));
    }


    @Test
    void columnsForMarkerWinsOverPlainTokens()
    {
        // RECORD finding carrying a variable_name marker (CORE-000356 shape).
        assertEquals(List.of("AEDECOD"),
                FindingLocations.columnsFor(FindingScope.RECORD,
                        List.of("variable_name", "variable_value", "USUBJID", "SEQ"),
                        List.of("AEDECOD", "null", "", ""), Set.of("variable_name")));
    }


    @Test
    void columnsForNoMarkerFiltersPlainColumns()
    {
        // CORE-000744 shape: keep FAOBJ, drop the RELREC.** cross refs.
        assertEquals(List.of("FAOBJ", "USUBJID"),
                FindingLocations.columnsFor(FindingScope.RECORD,
                        List.of("FAOBJ", "RELREC.**DECOD", "RELREC.**TERM", "USUBJID"),
                        List.of("ERYTHEMA", "null", "x", "CDISC001"), Set.of("variable_name")));
    }


    @Test
    void columnsForVariableScopeWithoutMarkerKeepsPlainColumn()
    {
        assertEquals(List.of("LBORRES"), FindingLocations.columnsFor(FindingScope.VARIABLE,
                List.of("LBORRES"), List.of(), Set.of("VARIABLE")));
    }

}
