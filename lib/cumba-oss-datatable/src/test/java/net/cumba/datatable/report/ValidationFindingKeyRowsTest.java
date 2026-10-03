package net.cumba.datatable.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The record-key half of the reporting schema:
 * {@link ValidationFindingLocation#getKeyVariableNames()} / {@code getKeySource()} and
 * {@link ValidationFinding#getKeyRows()} / {@code getRowKeys(int)}.
 *
 * <p>
 * These fields are additive and stay <b>empty</b> in the dataviewer until a {@code cdisc.core} sync
 * brings the producer ({@code ValidationReportBuilder}) across, so the contract that actually
 * matters today is the <em>default-empty, never-null, never-throwing</em> behaviour every existing
 * consumer relies on.
 * </p>
 */
class ValidationFindingKeyRowsTest
{

    private static RowFindingSlab slab(String... aValues)
    {
        RowFindingSlab.Builder b = RowFindingSlab.builder(aValues.length);
        b.addRow(0, aValues);
        return b.build();
    }

    // ===== defaults: nothing set =====


    @Test
    void keyRowsDefaultsToEmptySlabRatherThanNull()
    {
        ValidationFinding f = ValidationFinding.builder().ruleId("CORE-000").build();

        assertSame(RowFindingSlab.EMPTY, f.getKeyRows());
        assertEquals(0, f.getKeyRows().rowCount());
    }


    @Test
    void getRowKeysOnAFindingWithoutAKeyReturnsEmptyMap()
    {
        ValidationFinding f = ValidationFinding.builder().ruleId("CORE-000").build();

        assertTrue(f.getRowKeys(0).isEmpty());
        assertTrue(f.getRowKeys(-1).isEmpty());
        assertTrue(f.getRowKeys(99).isEmpty());
    }


    @Test
    void locationDefaultsToAnEmptyKeyList()
    {
        ValidationFindingLocation loc = ValidationFindingLocation.builder().dataset("AE").build();

        assertNotNull(loc.getKeyVariableNames());
        assertTrue(loc.getKeyVariableNames().isEmpty());
        assertEquals(null, loc.getKeySource());
    }


    @Test
    void sharedEmptyLocationCarriesNoKey()
    {
        assertTrue(ValidationFindingLocation.EMPTY.getKeyVariableNames().isEmpty());
        assertEquals(null, ValidationFindingLocation.EMPTY.getKeySource());
        assertTrue(ValidationFindingLocation.EMPTY.isEmpty());
    }

    // ===== isEmpty() gains a third clause =====


    @Test
    void aLocationWithOnlyAKeyListIsNotEmpty()
    {
        ValidationFindingLocation loc = ValidationFindingLocation.builder()
                .keyVariableNames(List.of("USUBJID", "AESEQ")).keySource("DEFINE_KEY").build();

        assertFalse(loc.isEmpty(),
                "a resolved record key is content, even with no dataset " + "and nothing flagged");
    }


    @Test
    void aLocationWithAnEmptyKeyListIsStillEmpty()
    {
        ValidationFindingLocation loc = ValidationFindingLocation.builder()
                .keyVariableNames(List.of()).build();

        assertTrue(loc.isEmpty());
    }

    // ===== populated key =====


    @Test
    void getRowKeysMapsTheKeySchemaOntoTheKeySlab()
    {
        ValidationFindingLocation loc = ValidationFindingLocation.builder().dataset("AE")
                .variableNames(List.of("AETERM")).keyVariableNames(List.of("USUBJID", "AESEQ"))
                .keySource("DEFINE_KEY").build();
        ValidationFinding f = ValidationFinding.builder().ruleId("CORE-001").location(loc)
                .keyRows(slab("SUBJ-01", "3")).build();

        Map<String, String> keys = f.getRowKeys(0);
        assertEquals(2, keys.size());
        assertEquals("SUBJ-01", keys.get("USUBJID"));
        assertEquals("3", keys.get("AESEQ"));
    }


    /**
     * The three-axis defensiveness the accessor must keep: a producer can set {@code keyRows} and
     * the location's key schema independently, so a mismatch must yield an empty map rather than
     * indexing past the slab's row width.
     */
    @Test
    void getRowKeysReturnsEmptyMapOnASchemaWidthMismatch()
    {
        ValidationFindingLocation loc = ValidationFindingLocation.builder().dataset("AE")
                .keyVariableNames(List.of("USUBJID", "AESEQ", "AETERM")) // 3 names…
                .keySource("NATURAL").build();
        ValidationFinding f = ValidationFinding.builder().ruleId("CORE-002").location(loc)
                .keyRows(slab("SUBJ-01", "3")) // …but a 2-wide slab
                .build();

        assertTrue(f.getRowKeys(0).isEmpty(), "a width mismatch must not throw");
    }


    @Test
    void getRowKeysReturnsEmptyMapOutsideTheSlab()
    {
        ValidationFindingLocation loc = ValidationFindingLocation.builder()
                .keyVariableNames(List.of("USUBJID")).keySource("SPONSOR_ID").build();
        ValidationFinding f = ValidationFinding.builder().ruleId("CORE-003").location(loc)
                .keyRows(slab("SUBJ-01")).build();

        assertEquals("SUBJ-01", f.getRowKeys(0).get("USUBJID"));
        assertTrue(f.getRowKeys(1).isEmpty(), "past the end");
        assertTrue(f.getRowKeys(-1).isEmpty(), "before the start");
    }


    /** A key slab must not change finding identity, exactly as the main row slab does not. */
    @Test
    void keyRowsIsExcludedFromEqualityAndToString()
    {
        ValidationFindingLocation loc = ValidationFindingLocation.builder().dataset("AE")
                .keyVariableNames(List.of("USUBJID")).keySource("DEFINE_KEY").build();

        ValidationFinding withKey = ValidationFinding.builder().ruleId("CORE-004").location(loc)
                .keyRows(slab("SUBJ-01")).build();
        ValidationFinding withoutKey = ValidationFinding.builder().ruleId("CORE-004").location(loc)
                .build();

        assertEquals(withoutKey, withKey);
        assertEquals(withoutKey.hashCode(), withKey.hashCode());
        assertFalse(withKey.toString().contains("SUBJ-01"),
                "a key slab must not leak into toString");
    }


    @Test
    void keyVariableNamesAreUnmodifiable()
    {
        ValidationFindingLocation loc = ValidationFindingLocation.builder()
                .keyVariableNames(List.of("USUBJID")).build();

        assertThrows(UnsupportedOperationException.class,
                () -> loc.getKeyVariableNames().add("HACK"));
    }
}
