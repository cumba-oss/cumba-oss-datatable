package net.cumba.datatable.report;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jspecify.annotations.Nullable;

/**
 * A single validation finding that may span multiple rows. The finding identity (source, rule id,
 * severity, kind, scope, rule type, message, variable schema) is stored directly; per-row data (row
 * indices + values) lives in a {@link RowFindingSlab}.
 */
@Value
@Builder(toBuilder = true)
@AllArgsConstructor
@Jacksonized
public class ValidationFinding
{

    /**
     * The source that produced this finding. Stable identifier for the producing system, e.g.
     * {@code "cumba.core"} (engine), {@code "cdisc.core"} (external CORE JSON report),
     * {@code "pinnacle21"} (Pinnacle 21 Excel).
     */
    private final String source;

    /**
     * Canonical identifier of the rule in the producing system (e.g. {@code "CORE-000238"},
     * {@code "SD0001"}).
     */
    private final @Nullable String ruleId;

    /** How this finding was produced — see {@link FindingKind}. */
    private final FindingKind kind;

    private final Severity severity;

    private final @Nullable String executability;

    /**
     * Coarse highlighting scope (DATASET / VARIABLE / RECORD) — for an engine finding, the
     * projection of the rule's evaluation domain. When set, the UI uses this directly;
     * {@link #getEffectiveScope()} falls back to the shape of the finding when this field is null.
     */
    private final @Nullable FindingScope scope;

    private final @Nullable String message;

    /**
     * Rule-level variable schema — the names of the variables this finding touches. Shared across
     * all row occurrences of the finding (the slab stores values only). Part of the finding's
     * identity: two findings with different variable schemas do not merge.
     *
     * <p>
     * These are the <em>raw</em> {@code variable = value} keys exactly as the producing report /
     * engine reported them — they may be report pseudo-tokens ({@code "VARIABLE"},
     * {@code "variable_name"}), cross-dataset / derived references, etc. They drive
     * <em>display</em> only (the report table's Variables / Values columns, tooltips). For
     * <em>navigation and highlighting</em> use {@link #getLocation()}.
     * </p>
     */
    private final List<String> variableNames;

    /**
     * Where this finding is located in the data — the real data-table data set + column name(s) the
     * UI should highlight / scroll to. Separate from {@link #variableNames} (the raw reported
     * pairs). May be {@code null}; use {@link #getLocation()} for a never-null view.
     */
    private final ValidationFindingLocation location;

    /**
     * Per-row values (columnar, row-major). Not part of the finding's identity so merging and
     * hashing are O(identity tuple size) rather than O(row count × variable count).
     */
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private final RowFindingSlab rows;

    /**
     * Per-row <em>record-key</em> values (columnar, row-major), aligned 1:1 with {@link #rows}. Its
     * schema is {@link ValidationFindingLocation#getKeyVariableNames()}, not
     * {@link #variableNames}.
     *
     * <p>
     * Deliberately a slab of its own rather than extra columns on {@link #rows}: the main slab's
     * schema drives the reported {@code variable = value} pairs, so folding identity columns into
     * it would leak them into every consumer's variable list. Excluded from finding identity for
     * the same reason {@link #rows} is.
     * </p>
     */
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private final RowFindingSlab keyRows;

    // ==================== Accessors ====================

    /**
     * Returns the variable schema as an unmodifiable list, or empty when not set.
     *
     * @return the variable schema as an unmodifiable list, or empty when not set.
     */
    public List<String> getVariableNames()
    {
        if (variableNames == null)
        {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(variableNames);
    }


    /**
     * Returns the row slab attached to this finding.
     *
     * @return the slab; never null — {@link RowFindingSlab#EMPTY} when no rows are attached.
     */
    public RowFindingSlab getRows()
    {
        return rows != null ? rows : RowFindingSlab.EMPTY;
    }


    /**
     * Returns the record-key slab attached to this finding.
     *
     * @return the key slab; never null — {@link RowFindingSlab#EMPTY} when no record key was
     *         resolved.
     */
    public RowFindingSlab getKeyRows()
    {
        return keyRows != null ? keyRows : RowFindingSlab.EMPTY;
    }


    /**
     * Returns an ordered record-key name to value map for the given slab position.
     *
     * @param aPos
     *            the row occurrence position.
     * @return the row's record key, or an empty map when none was resolved.
     */
    public Map<String, @Nullable String> getRowKeys(int aPos)
    {
        RowFindingSlab s = getKeyRows();
        List<String> schema = getLocation().getKeyVariableNames();
        // Defensive on all three axes: this is public API, and a producer is free to set keyRows
        // and the location's key schema independently. A mismatched schema would otherwise index
        // past the slab's row width.
        if (aPos < 0 || aPos >= s.rowCount() || schema.size() != s.variableCount())
        {
            return Collections.emptyMap();
        }
        return s.rowValues(aPos, schema);
    }


    /**
     * Returns the finding's location — the real data set + column name(s) the UI highlights /
     * scrolls to.
     *
     * @return the location; never null — {@link ValidationFindingLocation#EMPTY} when no location
     *         is attached (callers then fall back to the enclosing member's domain and highlight
     *         nothing column-specific).
     */
    public ValidationFindingLocation getLocation()
    {
        return location != null ? location : ValidationFindingLocation.EMPTY;
    }

    // ==================== Convenience methods ====================


    /**
     * Effective highlighting scope. Prefers the explicit {@link #scope}, else derives it from the
     * shape of the finding.
     */
    @JsonIgnore
    public FindingScope getEffectiveScope()
    {
        if (scope != null)
        {
            return scope;
        }
        if (hasRows())
        {
            return FindingScope.RECORD;
        }
        if (variableNames != null && !variableNames.isEmpty())
        {
            return FindingScope.VARIABLE;
        }
        return FindingScope.DATASET;
    }


    /**
     * Returns {@code true} if the slab has at least one row occurrence with a non-negative row
     * index.
     *
     * @return {@code true} if the slab has at least one row occurrence with a non-negative row
     *         index.
     */
    @JsonIgnore
    public boolean hasRows()
    {
        RowFindingSlab s = getRows();
        if (s.rowCount() == 0)
        {
            return false;
        }
        // Sorted ascending, so the last entry is the maximum.
        return s.rowIndexAt(s.rowCount() - 1) >= 0;
    }


    /**
     * Returns the number of row occurrences.
     *
     * @return the number of row occurrences.
     */
    @JsonIgnore
    public int getRowCount()
    {
        return getRows().rowCount();
    }


    /**
     * Returns {@code true} if the finding covers the given 0-based row.
     *
     * @return {@code true} if the finding covers the given 0-based row.
     */
    public boolean containsRow(int aRowIndex)
    {
        return getRows().containsRow(aRowIndex);
    }


    /**
     * Returns a stream of all 0-based row indices covered by this finding.
     *
     * @return a stream of all 0-based row indices covered by this finding.
     */
    public IntStream rowIndices()
    {
        return getRows().rowIndicesStream();
    }


    /**
     * Returns the 0-based row index of the first (or only) row occurrence.
     *
     * @return the 0-based row index of the first (or only) row occurrence. Returns {@code -1} when
     *         no rows are attached.
     */
    @JsonIgnore
    public int getRowIndex()
    {
        RowFindingSlab s = getRows();
        return s.rowCount() == 0 ? -1 : s.rowIndexAt(0);
    }


    /**
     * Returns the 1-based row number of the first (or only) row occurrence for display.
     *
     * @return the 1-based row number of the first (or only) row occurrence for display. Returns
     *         {@code 0} when no rows are attached or the index is negative.
     */
    @JsonIgnore
    public int getRow()
    {
        int ri = getRowIndex();
        return ri < 0 ? 0 : ri + 1;
    }


    /**
     * Returns an ordered name to value map for the given slab position.
     *
     * @return an ordered name → value map for the given slab position, using
     *         {@link #getVariableNames()} as the schema.
     */
    public Map<String, @Nullable String> getRowValues(int aPos)
    {
        return getRows().rowValues(aPos, getVariableNames());
    }


    /**
     * Returns an ordered name to value map for the first row occurrence.
     *
     * @return an ordered name → value map for the first row occurrence, or an empty map when no
     *         rows are attached.
     */
    @JsonIgnore
    public Map<String, @Nullable String> getFirstRowValues()
    {
        RowFindingSlab s = getRows();
        if (s.rowCount() == 0)
        {
            return Collections.emptyMap();
        }
        return s.rowValues(0, getVariableNames());
    }

}
