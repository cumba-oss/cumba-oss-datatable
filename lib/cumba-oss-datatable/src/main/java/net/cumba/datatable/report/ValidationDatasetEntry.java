package net.cumba.datatable.report;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jspecify.annotations.Nullable;

/**
 * One data set of a validation run as the report's source lists it
 * (PLAN-full-validation-report-view §4.1). Part of a {@link ValidationRunSummary}. Counts are
 * {@code -1} when the source does not state them.
 */
@Value
@Builder(toBuilder = true)
@Jacksonized
public class ValidationDatasetEntry
{

    /** Unknown count. */
    public static final long UNKNOWN_COUNT = -1L;

    /** The data set's file name, when reported (e.g. {@code ae.xpt}). */
    private final @Nullable String fileName;

    /** The domain / table name (e.g. {@code AE}). */
    private final String domain;

    /** The data set's label. */
    private final @Nullable String label;

    /** The path or location the source reports. */
    private final @Nullable String path;

    /** The modification time stamp as the source wrote it. */
    private final @Nullable String modified;

    /** The data set's class (e.g. {@code EVENTS}), when reported. */
    private final @Nullable String datasetClass;

    /** The number of records; {@link #UNKNOWN_COUNT} when unknown. */
    @Builder.Default
    private final long records = UNKNOWN_COUNT;

    /** The number of columns; {@link #UNKNOWN_COUNT} when unknown. */
    @Builder.Default
    private final long columns = UNKNOWN_COUNT;

    /** The size in kilobytes, {@code null} when unknown. */
    private final @Nullable Double sizeKb;

    /** The data set's validation runtime in milliseconds; {@code -1} when not measured. */
    @Builder.Default
    private final long runtimeMillis = -1L;

    /** The number of findings attributed to the data set; {@link #UNKNOWN_COUNT} when unknown. */
    @Builder.Default
    private final long findingCount = UNKNOWN_COUNT;

    /** How many rules executed on the data set; {@link #UNKNOWN_COUNT} when unknown. */
    @Builder.Default
    private final long rulesExecuted = UNKNOWN_COUNT;

    /** How many rules were selected for the run; {@link #UNKNOWN_COUNT} when unknown. */
    @Builder.Default
    private final long rulesTotal = UNKNOWN_COUNT;

    /** The number of rule errors on the data set; {@link #UNKNOWN_COUNT} when unknown. */
    @Builder.Default
    private final long errorCount = UNKNOWN_COUNT;

    /** The number of rejected records (Pinnacle 21); {@link #UNKNOWN_COUNT} when unknown. */
    @Builder.Default
    private final long rejects = UNKNOWN_COUNT;
}
