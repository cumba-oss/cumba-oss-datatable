package net.cumba.datatable.report;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * PLAN-full-validation-report-view phase 1 / RV12: the summary's wire typing. The JSON-RPC mapper
 * only disables {@code FAIL_ON_UNKNOWN_PROPERTIES} (as here), so an unknown enum value must be
 * absorbed by the enum's own creator, and an unknown capability is a plain string.
 */
class ValidationRunSummaryJsonTest
{

    private static ObjectMapper mapper()
    {
        ObjectMapper m = new ObjectMapper();
        m.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return m;
    }


    private static ValidationRunSummary filled()
    {
        ValidationRunInfo info = ValidationRunInfo.builder().source("cumba.core")
                .format("coreJ JSON v2").engineName("coreJ").engineVersion("0.1.0")
                .generatedAt("2026-10-12T10:00:00").runtimeText("1.5 seconds").runtimeSeconds(1.5)
                .standard("SDTMIG").version("V3.4").ctPackages(List.of("sdtmct-2024-03-29"))
                .properties(List.of(ReportProperty.of("UNII_Version", "not configured")))
                .notes(List.of("note")).build();
        ValidationRuleEntry rule = ValidationRuleEntry.builder().ruleId("CORE-000001")
                .status(RuleRunStatus.SKIPPED).rawStatus("SKIPPED").message("m").severity("Error")
                .cdiscRuleIds("CG0176").findingCount(0).datasets(List.of()).executedCount(0)
                .skippedCount(3).erroredCount(0).runtimeMillis(-1).build();
        ValidationDatasetEntry ds = ValidationDatasetEntry.builder().domain("AE").fileName("ae.xpt")
                .records(961).sizeKb(12.5).build();
        return ValidationRunSummary.builder().runInfo(info).rules(List.of(rule))
                .datasets(List.of(ds))
                .capabilities(
                        List.of(ReportCapabilities.REPORTS_RULES, ReportCapabilities.REPORTS_SKIPS))
                .build();
    }


    @Test
    void roundTrip_filledSummary() throws Exception
    {
        ObjectMapper m = mapper();
        ValidationReport report = ValidationReport.builder().members(List.of()).summary(filled())
                .build();
        String json = m.writeValueAsString(report);
        ValidationReport back = m.readValue(json, ValidationReport.class);

        assertEquals(filled(), back.getSummary());
        assertEquals("SDTMIG", back.getSummary().getRunInfo().getStandard());
        assertEquals(RuleRunStatus.SKIPPED, back.getSummary().getRules().get(0).getStatus());
        assertEquals(3, back.getSummary().getRules().get(0).getSkippedCount());
        assertEquals(961, back.getSummary().getDatasets().get(0).getRecords());
        assertTrue(back.getSummary().has(ReportCapabilities.REPORTS_SKIPS));
        assertFalse(back.getSummary().has(ReportCapabilities.REPORTS_ERRORS));
    }


    @Test
    void getSummary_neverNull_andEmptyIsRecognised() throws Exception
    {
        ValidationReport report = ValidationReport.builder().members(List.of()).build();
        assertNotNull(report.getSummary());
        assertSame(ValidationRunSummary.EMPTY, report.getSummary());
        assertTrue(report.getSummary().isEmpty());
        assertFalse(filled().isEmpty());

        // an older peer's JSON (no summary key at all) reads as EMPTY
        ValidationReport back = mapper().readValue("{\"members\":[]}", ValidationReport.class);
        assertSame(ValidationRunSummary.EMPTY, back.getSummary());
    }


    @Test
    void unknownEnumValue_readsAsUnknown_notAFailure() throws Exception
    {
        String json = "{\"rules\":[{\"ruleId\":\"R1\",\"status\":\"LATER_CONSTANT\","
                + "\"rawStatus\":\"LATER_CONSTANT\"}]}";
        ValidationRunSummary back = mapper().readValue(json, ValidationRunSummary.class);
        assertEquals(RuleRunStatus.UNKNOWN, back.getRules().get(0).getStatus());
        assertEquals("LATER_CONSTANT", back.getRules().get(0).getRawStatus());
        // a null status on the wire reads as UNKNOWN too
        back = mapper().readValue("{\"rules\":[{\"ruleId\":\"R1\",\"status\":null}]}",
                ValidationRunSummary.class);
        assertEquals(RuleRunStatus.UNKNOWN, back.getRules().get(0).getStatus());
    }


    @Test
    void unknownCapabilityString_isCarriedAndIgnorable() throws Exception
    {
        String json = "{\"capabilities\":[\"REPORTS_RULES\",\"LATER_CAPABILITY\"]}";
        ValidationRunSummary back = mapper().readValue(json, ValidationRunSummary.class);
        assertTrue(back.has(ReportCapabilities.REPORTS_RULES));
        assertTrue(back.has("LATER_CAPABILITY"));
        assertFalse(back.has(ReportCapabilities.REPORTS_DATASETS));
    }


    @Test
    void unknownFieldOnAnEntry_isIgnored() throws Exception
    {
        String json = "{\"rules\":[{\"ruleId\":\"R1\",\"laterField\":42}],"
                + "\"datasets\":[{\"domain\":\"AE\",\"laterField\":true}],\"laterTop\":{}}";
        ValidationRunSummary back = mapper().readValue(json, ValidationRunSummary.class);
        assertEquals("R1", back.getRules().get(0).getRuleId());
        assertEquals("AE", back.getDatasets().get(0).getDomain());
    }


    @Test
    void countsDefaultToUnknown_whenAbsentOnTheWire() throws Exception
    {
        ValidationRuleEntry rule = mapper().readValue("{\"ruleId\":\"R1\"}",
                ValidationRuleEntry.class);
        assertEquals(ValidationRuleEntry.UNKNOWN_COUNT, rule.getFindingCount());
        assertEquals(ValidationRuleEntry.UNKNOWN_COUNT, rule.getExecutedCount());
        assertEquals(-1L, rule.getRuntimeMillis());
        assertNull(rule.getSeverity());
        ValidationDatasetEntry ds = mapper().readValue("{\"domain\":\"AE\"}",
                ValidationDatasetEntry.class);
        assertEquals(ValidationDatasetEntry.UNKNOWN_COUNT, ds.getRecords());
        assertNull(ds.getSizeKb());
    }


    @Test
    void fromRaw_mapsTheEngineWords()
    {
        assertEquals(RuleRunStatus.NO_FINDINGS, RuleRunStatus.fromRaw("SUCCESS"));
        assertEquals(RuleRunStatus.FINDINGS, RuleRunStatus.fromRaw("ISSUE_REPORTED"));
        assertEquals(RuleRunStatus.SKIPPED, RuleRunStatus.fromRaw("skipped"));
        assertEquals(RuleRunStatus.ERROR, RuleRunStatus.fromRaw("EXECUTION_ERROR"));
        assertEquals(RuleRunStatus.ERROR, RuleRunStatus.fromRaw("ERROR"));
        assertEquals(RuleRunStatus.UNKNOWN, RuleRunStatus.fromRaw(null));
        assertEquals(RuleRunStatus.UNKNOWN, RuleRunStatus.fromRaw(" "));
        assertEquals(RuleRunStatus.UNKNOWN, RuleRunStatus.fromRaw("WHATEVER"));
    }


    @Test
    void reportProperty_labelFallsBackToKey()
    {
        assertEquals("K", ReportProperty.builder().key("K").value("v").build().getLabel());
        assertEquals("L", ReportProperty.builder().key("K").label("L").build().getLabel());
    }
}
