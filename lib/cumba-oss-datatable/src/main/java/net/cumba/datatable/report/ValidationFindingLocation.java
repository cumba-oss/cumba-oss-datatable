package net.cumba.datatable.report;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jspecify.annotations.Nullable;

/**
 * Where a {@link ValidationFinding} is located in the data, independent of the raw
 * {@code variable = value} pairs the finding reports. A location is self-contained — it describes
 * <em>where</em> a finding sits (data set + variables) but never <em>which rows</em> (row
 * occurrences stay on the finding's {@link RowFindingSlab}).
 *
 * <p>
 * Tied to the finding's {@link FindingScope}:
 * </p>
 * <ul>
 * <li>{@link FindingScope#DATASET}: only {@link #dataset} is meaningful; {@link #variableNames} is
 * empty.</li>
 * <li>{@link FindingScope#VARIABLE}: {@link #dataset} + exactly one entry in {@link #variableNames}
 * (the flagged column).</li>
 * <li>{@link FindingScope#RECORD}: {@link #dataset} + zero or more real columns of that data set
 * whose cells the finding concerns.</li>
 * </ul>
 *
 * <p>
 * The names in {@link #variableNames} are <em>real data-table column names</em> of {@link #dataset}
 * — never report pseudo-tokens ({@code "VARIABLE"}, {@code "variable_name"}), cross-dataset
 * references ({@code RELREC.**TERM}), derived references ({@code SUB:DTHDTC}) or scalar operation
 * refs ({@code $foo}). See {@link FindingLocations} for the helpers producers use to resolve them.
 * </p>
 *
 * <p>
 * A location separates two <em>kinds</em> of column, because they answer different questions:
 * {@link #variableNames} says <b>what is wrong</b> (drives highlighting), while
 * {@link #keyVariableNames} says <b>which row this is</b> (drives identification and cross-run
 * alignment). They are disjoint — a producer that carries a row's identity columns must put them in
 * the key list, never in the flagged list.
 * </p>
 */
@Value
@Builder
@Jacksonized
public class ValidationFindingLocation
{

    /** Shared empty location — no data set, no variables. */
    public static final ValidationFindingLocation EMPTY = new ValidationFindingLocation(null,
            List.of(), List.of(), null);

    /**
     * Data set / member name the finding is located in. Normally equals the enclosing
     * {@link ValidationReportMember#getDomain() member domain} (the library member name — for the
     * engine this is the member name, never the CDISC domain code).
     */
    private final @Nullable String dataset;

    /**
     * Real data-table column names within {@link #dataset} that the finding <em>flags</em> — the
     * columns a UI highlights or scrolls to. Never includes the row-identity columns; those live in
     * {@link #keyVariableNames}.
     */
    private final @Nullable List<String> variableNames;

    /**
     * Ordered real column names that <em>identify</em> the finding's row(s) within {@link #dataset}
     * — the resolved record key. Disjoint from {@link #variableNames}: these columns say <em>which
     * row</em> the finding is on, not <em>what is wrong</em> with it, so a consumer aligns findings
     * across data versions on them but never highlights them.
     *
     * <p>
     * Empty when no key beyond the row's {@code USUBJID} / {@code <DOMAIN>SEQ} could be resolved
     * (always the case unless the producer opts in). Per-row values for these columns are carried
     * on {@link ValidationFinding#getKeyRows()}, whose schema this list is.
     * </p>
     */
    private final @Nullable List<String> keyVariableNames;

    /**
     * Which source produced {@link #keyVariableNames} — how far a consumer should trust an
     * alignment built on it. {@code null} when no key was resolved.
     *
     * <p>
     * The engine's values, strongest first, are {@code DEFINE_KEY} (the sponsor's own Define-XML
     * key — authoritative), {@code STRUCTURAL} (a structural key for a shape with no sequence
     * variable — authoritative), {@code NATURAL} (a natural key derived from library variable roles
     * — usually right, may collide on genuinely duplicated records) and {@code SPONSOR_ID} (only
     * sponsor identifiers such as {@code --SPID} resolved — a hint, not a match). When no key
     * resolved this field is {@code null} and {@link #keyVariableNames} is empty; the two are
     * always set or unset together. Held as a plain string so this module stays free of any
     * CDISC-engine dependency.
     * </p>
     */
    private final @Nullable String keySource;

    /**
     * Returns the location's variable names as an unmodifiable list, never null.
     *
     * @return the location's variable names as an unmodifiable list, or empty when not set.
     */
    public List<String> getVariableNames()
    {
        return variableNames == null ? List.of() : Collections.unmodifiableList(variableNames);
    }


    /**
     * Returns the location's record-key variable names as an unmodifiable list, never null.
     *
     * @return the ordered record-key column names, or empty when no key was resolved.
     */
    public List<String> getKeyVariableNames()
    {
        return keyVariableNames == null ? List.of()
                : Collections.unmodifiableList(keyVariableNames);
    }


    /**
     * Returns {@code true} when this location carries neither a data set nor any variable.
     *
     * @return {@code true} when this location carries neither a data set nor any variable.
     */
    @JsonIgnore
    public boolean isEmpty()
    {
        return (dataset == null || dataset.isEmpty())
                && (variableNames == null || variableNames.isEmpty())
                && (keyVariableNames == null || keyVariableNames.isEmpty());
    }

}
