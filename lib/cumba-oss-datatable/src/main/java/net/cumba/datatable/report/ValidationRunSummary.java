package net.cumba.datatable.report;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jspecify.annotations.Nullable;

/**
 * Everything a validation report says beyond its findings: the run ({@code runInfo}), the rules
 * ({@link #getRules()}) and the data sets ({@link #getDatasets()}) — the Overview, Rules and Data
 * sets parts of the report view (PLAN-full-validation-report-view §2). Attached to a
 * {@link ValidationReport} by its reader (a file) or by the engine runner (an internal run); a
 * report whose source carries none of it has an {@link #EMPTY} summary and the view says so.
 *
 * <p>
 * {@link #getCapabilities()} names what the source reports ({@link ReportCapabilities}); every
 * other getter answers an empty list when the source carries nothing. Strings on the wire, so a
 * later capability never breaks an older client (RV12).
 * </p>
 */
@Value
@Builder(toBuilder = true)
@Jacksonized
public class ValidationRunSummary
{

    /** A summary carrying nothing: the value of a report whose source reports no summary. */
    public static final ValidationRunSummary EMPTY = ValidationRunSummary.builder().build();

    /** The run information; {@code null} when the source reports none. */
    private final @Nullable ValidationRunInfo runInfo;

    /** The rules of the run, in the source's order. */
    private final @Nullable List<ValidationRuleEntry> rules;

    /** The data sets of the run, in the source's order. */
    private final @Nullable List<ValidationDatasetEntry> datasets;

    /** The capability names ({@link ReportCapabilities}) the source reports. */
    private final @Nullable List<String> capabilities;

    /**
     * Returns a read only view of the rules.
     *
     * @return the rules, empty when none; never {@code null}.
     */
    public List<ValidationRuleEntry> getRules()
    {
        return rules == null ? Collections.emptyList() : Collections.unmodifiableList(rules);
    }


    /**
     * Returns a read only view of the data sets.
     *
     * @return the data sets, empty when none; never {@code null}.
     */
    public List<ValidationDatasetEntry> getDatasets()
    {
        return datasets == null ? Collections.emptyList() : Collections.unmodifiableList(datasets);
    }


    /**
     * Returns a read only view of the capability names.
     *
     * @return the capabilities, empty when none; never {@code null}.
     */
    public List<String> getCapabilities()
    {
        return capabilities == null ? Collections.emptyList()
                : Collections.unmodifiableList(capabilities);
    }


    /**
     * Whether the source reports the given capability.
     *
     * @param aCapability
     *            a {@link ReportCapabilities} name.
     * @return whether it is listed.
     */
    @JsonIgnore
    public boolean has(String aCapability)
    {
        return capabilities != null && capabilities.contains(aCapability);
    }


    /**
     * Whether this summary carries anything at all: run information, rules, data sets or a
     * capability.
     *
     * @return {@code false} for {@link #EMPTY} and its equals.
     */
    @JsonIgnore
    public boolean isEmpty()
    {
        return runInfo == null && getRules().isEmpty() && getDatasets().isEmpty()
                && getCapabilities().isEmpty();
    }
}
