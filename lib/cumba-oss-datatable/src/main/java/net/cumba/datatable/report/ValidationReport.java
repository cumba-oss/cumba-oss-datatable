package net.cumba.datatable.report;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jspecify.annotations.Nullable;

/**
 * A collection of {@link ValidationReportMember}s that belong together. This is meant to be the
 * collection of all members with all findings referenced in one report.
 */
@Value
@Builder(toBuilder = true)
@Jacksonized
public class ValidationReport
{

    /**
     * All the members in this report.
     */
    private final @Nullable List<ValidationReportMember> members;

    /**
     * All skipped (rule × dataset) pairs of this report, in insertion order. See
     * {@link SkippedRuleEntry}.
     */
    private final @Nullable List<SkippedRuleEntry> skippedRules;

    /**
     * The CORE identifiers of every rule for which at least one <em>non-skipped</em> execution was
     * recorded, in first-seen order and without duplicates.
     *
     * <p>
     * This is the companion {@link #skippedRules} needs to be readable. A clean execution leaves no
     * finding, so "no findings" alone cannot distinguish <em>skipped everywhere</em> from <em>ran
     * and found nothing</em> — and reading a skip entry as "the rule never ran" would mislabel
     * every rule that was skipped on one dataset and executed on another. A rule is therefore
     * reportable as skipped only when it carries a skip entry and does <b>not</b> appear here.
     * </p>
     */
    private final @Nullable List<String> executedCoreIds;

    /**
     * Returns a read only view to the internal list of members.
     *
     * @return a read only view to the internal list of members. This might be an empty list but
     *         will never be null.
     */
    public List<ValidationReportMember> getMembers()
    {
        if (members == null)
        {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(members);
    }


    /**
     * Returns a read only view to the internal list of skipped (rule × dataset) entries.
     *
     * @return a read only view to the internal list of skipped rules. This might be an empty list
     *         but will never be null.
     */
    public List<SkippedRuleEntry> getSkippedRules()
    {
        if (skippedRules == null)
        {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(skippedRules);
    }


    /**
     * Returns a read only view to the internal list of executed rule CORE ids.
     *
     * @return a read only view to the internal list of CORE ids that recorded at least one
     *         non-skipped execution. This might be an empty list but will never be null.
     */
    public List<String> getExecutedCoreIds()
    {
        if (executedCoreIds == null)
        {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(executedCoreIds);
    }


    /**
     * Retrieve the member report for the member with the given name.
     *
     * @param aName
     *            the name of the member. This is first interpreted as table / domain name and as
     *            fallback as file name.
     * @return the first member found for the given name.
     */
    @JsonIgnore
    public @Nullable ValidationReportMember getForMember(@Nullable String aName)
    {
        if (members == null || aName == null)
        {
            return null;
        }

        String domain = aName;
        int idx = aName.indexOf('.');
        if (idx > 0)
        {
            domain = domain.substring(0, idx);
        }

        for (ValidationReportMember vm : members)
        {
            if (domain.equalsIgnoreCase(vm.getDomain()))
            {
                return vm;
            }
        }

        for (ValidationReportMember vm : members)
        {
            if (aName.equals(vm.getFileName()))
            {
                return vm;
            }
        }

        return null;
    }
}
