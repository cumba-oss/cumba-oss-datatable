package net.cumba.datatable.report;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import net.cumba.datatable.help.CDT;
import org.jspecify.annotations.Nullable;

/**
 * A member (table / domain) of the ValidationReportMember. This groups all findings related to this
 * member.
 */
@Value
@Builder(toBuilder = true)
@Jacksonized
public class ValidationReportMember
{

    /**
     * The file name (if available) of the member.
     */
    private final @Nullable String fileName;

    /**
     * The table / domain name of the member.
     */
    private final String domain;

    /**
     * All findings related to this member.
     */
    private final List<ValidationFinding> findings;

    /**
     * Returns a read only view to the internal list of the findings.
     *
     * @return a read only view to the internal list of the findings. This might be an empty list,
     *         but will never be null.
     */
    public List<ValidationFinding> getFindings()
    {
        if (findings == null)
        {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(findings);
    }


    /**
     * Returns true if the member has at least one finding.
     *
     * @return true if the member has at least one finding.
     */
    @JsonIgnore
    public boolean containsFindings()
    {
        return !CDT.isEmptyOrNull(findings);
    }


    /**
     * Returns the number of findings attached to this member.
     *
     * @return the number of findings attached to this member.
     */
    @JsonIgnore
    public int getFindingsCount()
    {
        return findings != null ? findings.size() : 0;
    }

}
