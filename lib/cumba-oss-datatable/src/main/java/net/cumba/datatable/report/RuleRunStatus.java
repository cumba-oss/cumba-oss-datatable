package net.cumba.datatable.report;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * The normalised outcome of one rule in a validation run, as shown in the report view's Rules tab.
 * The source's own word stays beside it ({@link ValidationRuleEntry}'s {@code rawStatus}, RV3: the
 * status is shown as reported).
 *
 * <p>
 * ⚠ Wire typing (RV12): {@link #fromJson(String)} is the Jackson creator and answers
 * {@link #UNKNOWN} for a name it does not know, so a newer peer sending a new constant never fails
 * an older client's deserialisation of the whole summary. Never rely on
 * {@code READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE} being enabled on the mapper — it is not on
 * the JSON-RPC mapper.
 * </p>
 */
public enum RuleRunStatus
{

    /** The rule executed and reported at least one finding. */
    FINDINGS,

    /**
     * The rule executed and reported nothing — or, for a source that cannot tell the two apart
     * (Pinnacle 21, RV4), it was not applicable.
     */
    NO_FINDINGS,

    /** The rule was skipped everywhere it was selected for. */
    SKIPPED,

    /** The rule's execution failed on at least one data set. */
    ERROR,

    /** The source's status is unknown to this client (or the source states none). */
    UNKNOWN;

    /**
     * The Jackson creator: this enum's own names, else {@link #UNKNOWN}.
     *
     * @param aName
     *            the name on the wire, may be {@code null}.
     * @return the constant; {@link #UNKNOWN} for {@code null} or an unknown name.
     */
    @JsonCreator
    public static RuleRunStatus fromJson(@Nullable String aName)
    {
        if (aName == null)
        {
            return UNKNOWN;
        }
        for (RuleRunStatus s : values())
        {
            if (s.name().equalsIgnoreCase(aName))
            {
                return s;
            }
        }
        return UNKNOWN;
    }


    /**
     * The name written to JSON.
     *
     * @return {@link #name()}.
     */
    @JsonValue
    public String toJson()
    {
        return name();
    }


    /**
     * Maps a status word as the coreJ / CDISC CORE {@code Rules_Report} writes it ({@code SUCCESS},
     * {@code ISSUE_REPORTED}, {@code SKIPPED}, {@code EXECUTION_ERROR}) — and the plain words
     * {@code ERROR} / {@code EXECUTED} / {@code FINDINGS} / {@code NO_FINDINGS} — to a constant.
     *
     * @param aRawStatus
     *            the status as written, may be {@code null}.
     * @return the constant; {@link #UNKNOWN} for {@code null}, blank or an unknown word.
     */
    public static RuleRunStatus fromRaw(@Nullable String aRawStatus)
    {
        if (aRawStatus == null || aRawStatus.isBlank())
        {
            return UNKNOWN;
        }
        return switch (aRawStatus.trim().toUpperCase(Locale.ROOT))
        {
        case "SUCCESS", "EXECUTED", "NO_FINDINGS" -> NO_FINDINGS;
        case "ISSUE_REPORTED", "FINDINGS" -> FINDINGS;
        case "SKIPPED" -> SKIPPED;
        case "EXECUTION_ERROR", "ERROR" -> ERROR;
        default -> UNKNOWN;
        };
    }
}
