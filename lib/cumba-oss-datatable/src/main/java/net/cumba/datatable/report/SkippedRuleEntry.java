package net.cumba.datatable.report;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jspecify.annotations.Nullable;

/**
 * One skipped (rule × dataset) pair of a {@link ValidationReport}. Skipping is a per-dataset
 * verdict — the same rule can be skipped on one dataset and executed on another — so a report
 * carries one entry per pair, never a rule-global entry.
 *
 * <p>
 * Entries originate from two sources: rules whose scope did not match the dataset (skipped before
 * execution) and rules whose execution was skipped (e.g. a required metadata provider was not
 * available). The {@code reason} is a short human-readable explanation naming the failing
 * criterion.
 * </p>
 */
@Value
@Builder(toBuilder = true)
@Jacksonized
public class SkippedRuleEntry
{

    /**
     * The rule's CORE identifier (falling back to the rule id when no CORE id is present). May be
     * {@code null} for synthetic rules without any identifier.
     */
    private final @Nullable String coreId;

    /**
     * The dataset (table / domain name) the rule was skipped on.
     */
    private final String dataset;

    /**
     * Human-readable reason naming the failing criterion (e.g. {@code "domain EX not in
     * Scope.Domains.Include [AE, CM]"} or {@code "Rule skipped — no Library access"}).
     */
    private final @Nullable String reason;

}
