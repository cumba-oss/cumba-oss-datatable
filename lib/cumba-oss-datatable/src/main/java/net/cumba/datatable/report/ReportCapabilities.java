package net.cumba.datatable.report;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

/**
 * The capability names a {@link ValidationRunSummary} declares: what the report's source
 * <em>reports</em>, so a consumer can say "this format does not report X" instead of showing an
 * empty part as if the run had nothing to say (PLAN-full-validation-report-view §4.1, RV12).
 *
 * <p>
 * ⚠ Plain strings on the wire ({@link ValidationRunSummary#getCapabilities()} is a
 * {@code List<String>}), never an enum: a newer peer may send a name an older client does not know,
 * and an unknown enum constant would fail the whole JSON-RPC answer. A consumer tests membership
 * with {@link ValidationRunSummary#has(String)} and ignores names it does not know.
 * </p>
 */
public final class ReportCapabilities
{

    /** The run information (engine, standard, dates, run properties) is reported. */
    public static final String REPORTS_RUN_INFO = "REPORTS_RUN_INFO";

    /** A rule list is reported at all ({@link ValidationRunSummary#getRules()} is meaningful). */
    public static final String REPORTS_RULES = "REPORTS_RULES";

    /**
     * The rule list is the <b>whole</b> universe of the run — every rule that was selected, not
     * only the ones with findings.
     */
    public static final String RULE_UNIVERSE_COMPLETE = "RULE_UNIVERSE_COMPLETE";

    /**
     * The source tells a rule that ran clean from one that was skipped: a {@code SKIPPED} status
     * and {@link ValidationReport#getSkippedRules() skip entries} are reported.
     */
    public static final String REPORTS_SKIPS = "REPORTS_SKIPS";

    /** Rule execution errors are reported as a status of their own. */
    public static final String REPORTS_ERRORS = "REPORTS_ERRORS";

    /** The rules carry a declared severity. */
    public static final String REPORTS_RULE_SEVERITY = "REPORTS_RULE_SEVERITY";

    /** The rules carry a category. */
    public static final String REPORTS_RULE_CATEGORY = "REPORTS_RULE_CATEGORY";

    /** The per-rule executed / skipped / errored counts are reported. */
    public static final String REPORTS_EXECUTION_COUNTS = "REPORTS_EXECUTION_COUNTS";

    /** The data sets of the run are reported ({@link ValidationRunSummary#getDatasets()}). */
    public static final String REPORTS_DATASETS = "REPORTS_DATASETS";

    private ReportCapabilities()
    {
    }
}
