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
 * What a validation report says about the <em>run</em> that produced it: the engine, when it ran,
 * the standard it validated against, and every other run property the source states
 * ({@link #getProperties()}, verbatim and in the source's order). Part of a
 * {@link ValidationRunSummary}; every field is optional because every format carries a different
 * subset (PLAN-full-validation-report-view §3).
 */
@Value
@Builder(toBuilder = true)
@Jacksonized
public class ValidationRunInfo
{

    /**
     * The finding source identifier the report's readers stamp on findings ({@code cdisc.core},
     * {@code cumba.core}, {@code pinnacle21}, …).
     */
    private final @Nullable String source;

    /**
     * A human-readable format name, e.g. {@code "coreJ JSON v2"} or {@code "Pinnacle 21 Excel"}.
     */
    private final @Nullable String format;

    /** The engine's name, when the format states one. */
    private final @Nullable String engineName;

    /** The engine's version. */
    private final @Nullable String engineVersion;

    /** When the report was generated, as the source wrote it. */
    private final @Nullable String generatedAt;

    /** The run's duration as the source wrote it (e.g. {@code "23.79 seconds"}). */
    private final @Nullable String runtimeText;

    /** The run's duration in seconds, when it can be derived; {@code null} otherwise. */
    private final @Nullable Double runtimeSeconds;

    /** The validated standard (e.g. {@code SDTMIG}). */
    private final @Nullable String standard;

    /** The standard's version (e.g. {@code V3.4}). */
    private final @Nullable String version;

    /** The sub-standard, when the format states one. */
    private final @Nullable String subStandard;

    /** The controlled terminology packages the run bound, as the source lists them. */
    private final @Nullable List<String> ctPackages;

    /** The Define-XML version, when stated. */
    private final @Nullable String defineXmlVersion;

    /** The use case (e.g. a TIG use case), when stated. */
    private final @Nullable String useCase;

    /** The issue limit per rule as the source wrote it (e.g. {@code "None"}). */
    private final @Nullable String issueLimitPerRule;

    /** The issue limit per data set as the source wrote it. */
    private final @Nullable String issueLimitPerDataset;

    /** Every other run property the source states, verbatim and in its order. */
    private final @Nullable List<ReportProperty> properties;

    /**
     * Notes a reader attaches about what the format does and does not report (e.g. a Pinnacle 21
     * report cannot tell a clean rule from a not-applicable one, RV4; an older coreJ file says
     * {@code SUCCESS} for rules skipped everywhere, RV3). Shown on the Overview.
     */
    private final @Nullable List<String> notes;

    /**
     * Returns a read only view of the CT packages.
     *
     * @return the CT packages, empty when none; never {@code null}.
     */
    public List<String> getCtPackages()
    {
        return ctPackages == null ? Collections.emptyList()
                : Collections.unmodifiableList(ctPackages);
    }


    /**
     * Returns a read only view of the other run properties.
     *
     * @return the properties, empty when none; never {@code null}.
     */
    public List<ReportProperty> getProperties()
    {
        return properties == null ? Collections.emptyList()
                : Collections.unmodifiableList(properties);
    }


    /**
     * Returns a read only view of the reader's notes.
     *
     * @return the notes, empty when none; never {@code null}.
     */
    public List<String> getNotes()
    {
        return notes == null ? Collections.emptyList() : Collections.unmodifiableList(notes);
    }
}
