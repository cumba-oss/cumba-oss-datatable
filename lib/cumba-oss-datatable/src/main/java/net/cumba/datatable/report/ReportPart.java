package net.cumba.datatable.report;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

/**
 * The parts of an attached validation report a manager materialises as a table
 * ({@code IDataTableManager.getReportPartAsTable}): the flattened findings, the rules of the run
 * and its data sets (PLAN-full-validation-report-view §4.1). On the remote protocol the part
 * travels as its {@link #name()}.
 */
public enum ReportPart
{

    /**
     * The findings, one row per row-level (or data-set-level) occurrence — the same table as
     * {@code IDataTableManager.getReportAsTable}.
     */
    FINDINGS,

    /** The rules of the run, one row per {@link ValidationRuleEntry}. */
    RULES,

    /** The data sets of the run, one row per {@link ValidationDatasetEntry}. */
    DATASETS;
}
