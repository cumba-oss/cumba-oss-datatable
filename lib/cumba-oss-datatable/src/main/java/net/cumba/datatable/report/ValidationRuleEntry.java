package net.cumba.datatable.report;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jspecify.annotations.Nullable;

/**
 * One rule of a validation run as the report's source lists it: its identity, its outcome and the
 * metadata the format carries (PLAN-full-validation-report-view §4.1). Part of a
 * {@link ValidationRunSummary}; the capabilities of the summary say which fields the source reports
 * at all.
 *
 * <p>
 * Counts are {@code -1} when the source does not state them. The declared severity is a <b>string,
 * exactly as reported</b> — ⛔ never routed through {@code Severity.fromJson}, which falls back to
 * {@code WARNING} for a word it does not know (a severity the report never stated). The per-rule
 * skip reasons are not duplicated here: skips live once, in
 * {@link ValidationReport#getSkippedRules()} (RV13), and a table derives them per rule.
 * </p>
 */
@Value
@Builder(toBuilder = true)
@Jacksonized
public class ValidationRuleEntry
{

    /** Unknown count. */
    public static final int UNKNOWN_COUNT = -1;

    /** The rule's identifier as the findings carry it (CORE id, Pinnacle 21 id, …). */
    private final String ruleId;

    /** The rule's version, when reported. */
    private final @Nullable String version;

    /** The normalised outcome; {@link RuleRunStatus#UNKNOWN} when the source states none. */
    private final @Nullable RuleRunStatus status;

    /** The outcome exactly as the source wrote it (RV3). */
    private final @Nullable String rawStatus;

    /** The rule's message — a finding's message, or the rule's declared outcome message. */
    private final @Nullable String message;

    /** The rule's description, when the format carries one. */
    private final @Nullable String description;

    /** The rule's declared severity as reported ({@code "Error"}, …), never parsed. */
    private final @Nullable String severity;

    /** The rule's category, when reported. */
    private final @Nullable String category;

    /** The CDISC rule ids as reported (comma-separated when several). */
    private final @Nullable String cdiscRuleIds;

    /** The FDA rule ids as reported. */
    private final @Nullable String fdaRuleIds;

    /** The PMDA rule ids as reported. */
    private final @Nullable String pmdaRuleIds;

    /** Other publisher ids as reported (a Pinnacle 21 "Publisher ID" cell, …). */
    private final @Nullable String otherRuleIds;

    /** The number of findings the rule reported; {@link #UNKNOWN_COUNT} when unknown. */
    @Builder.Default
    private final int findingCount = UNKNOWN_COUNT;

    /** The data sets (domains) the rule reported findings on, in first-seen order. */
    private final @Nullable List<String> datasets;

    /** How often the rule executed, per (rule × data set); {@link #UNKNOWN_COUNT} when unknown. */
    @Builder.Default
    private final int executedCount = UNKNOWN_COUNT;

    /**
     * How often the rule was skipped, per (rule × data set); {@link #UNKNOWN_COUNT} when unknown.
     */
    @Builder.Default
    private final int skippedCount = UNKNOWN_COUNT;

    /** How often the rule errored, per (rule × data set); {@link #UNKNOWN_COUNT} when unknown. */
    @Builder.Default
    private final int erroredCount = UNKNOWN_COUNT;

    /** The first execution error's message, when the rule errored. */
    private final @Nullable String errorMessage;

    /**
     * The rule's runtime in milliseconds summed over its data sets; {@code -1} when not measured.
     */
    @Builder.Default
    private final long runtimeMillis = -1L;

    /**
     * Returns the normalised outcome.
     *
     * @return the status, {@link RuleRunStatus#UNKNOWN} when none; never {@code null}.
     */
    public RuleRunStatus getStatus()
    {
        return status != null ? status : RuleRunStatus.UNKNOWN;
    }


    /**
     * Returns a read only view of the data sets with findings.
     *
     * @return the data sets, empty when none; never {@code null}.
     */
    public List<String> getDatasets()
    {
        return datasets == null ? Collections.emptyList() : Collections.unmodifiableList(datasets);
    }
}
