package net.cumba.datatable.merge;

import java.util.List;
import java.util.Set;
import net.cumba.datatable.IDataTable;

/**
 * One evaluation table built from a primary dataset and its declared merges
 * ({@code PLAN-merged-evaluation-table} §3.1, C0, MET #3, MET #8): the primary's columns followed
 * by one column range per part — keyed partners, {@code Child} parents, RELREC-related domains and
 * the {@code SUPP--} qualifiers — every one a <b>view</b> through a row map, never a copy.
 *
 * <ul>
 * <li>A merged column is just a column: {@link #getValue}, {@link #getDataValue},
 * {@link #getMetaData()} answer for it as for any other; its metadata carries the {@link MergeMeta}
 * keys.</li>
 * <li>{@link #isSupplied} is the marker beside the value: {@code false} where the row has no
 * partner record for that part (the value then reads the type default).</li>
 * <li>{@link #getRealRowIndex} answers the primary's native row for every merged row, so a finding
 * on an expanded row attributes to its real record.</li>
 * <li>Problems the sources carry that do not abort the merge are published as {@link #issues()}
 * and, per affected column, as {@link MergeMeta#MERGE_ERROR} (OD-7).</li>
 * <li>{@link #isRowMapped()} tells whether merged row {@code i} is primary row {@code i}.</li>
 * </ul>
 *
 * <p>
 * Built through {@link MergedTables#build(MergeRequest)}.
 * </p>
 */
public interface MergedTable extends IDataTable
{

    /**
     * The primary dataset the table was built on.
     *
     * @return the primary, as given to the request.
     */
    IDataTable primary();


    /**
     * The parts, part 0 being the primary.
     *
     * @return the parts, in column order.
     */
    List<MergedPart> parts();


    /**
     * The part the given merged column belongs to.
     *
     * @param aColumn
     *            a merged column index.
     * @return its part.
     * @throws IndexOutOfBoundsException
     *             for an invalid column.
     */
    MergedPart partOf(int aColumn) throws IndexOutOfBoundsException;


    /**
     * Whether the primary is <b>row-mapped</b>: some merge dropped a primary row (an inner join
     * with no partner) or gave one more than one row (an expansion), so merged row {@code i} is not
     * primary row {@code i}. Decided on identity, never on the row count — an inner merge whose
     * dropped and expanded rows cancel out keeps the count and is row-mapped (W1 review H1). On a
     * table that is not row-mapped every row {@code i} is primary row {@code i}.
     *
     * @return whether the merged rows map the primary's rows other than one to one, in order.
     */
    boolean isRowMapped();


    /**
     * The W1 name of {@link #isRowMapped()}, which it answers. W1 decided it on the row count; it
     * now answers the identity-based fact (W1 review H1). Kept only so the engine's W2 instrument
     * ({@code MergedTableShadowDifferentialTest}, committed against W1) compiles until it moves to
     * {@link #isRowMapped()}; then it goes.
     *
     * @return {@link #isRowMapped()}.
     */
    default boolean isExpanded()
    {
        return isRowMapped();
    }


    /**
     * The problems the sources carried that were reported through the {@code MERGE_ERROR} channel
     * instead of aborting the merge.
     *
     * @return the issues, in part order; empty when the merge is clean.
     */
    List<MergeIssue> issues();


    /**
     * The datasets the request named that the resolver did not supply — a partner, a parent, a
     * related domain, a supplemental dataset — each with the kind of part that asked
     * ({@link UnresolvedDataset}), so a caller can tell a declared partner the study lacks from "no
     * {@code SUPP--}" (W1 review L5). A dataset the merge never saw contributes no columns; the
     * caller decides what its absence means (P-1, P-11).
     *
     * @return the entries, in the order they were asked for; empty when every dataset resolved.
     */
    List<UnresolvedDataset> unresolvedDatasets();


    /**
     * Every {@code QNAM} the primary's supplemental dataset carries, in first-seen order, whether
     * or not it became a column (the existence half of the SUPP merge, kept for the bound schema
     * after the parse structure is released — M8, owner 2026-10-05). Empty when the primary has no
     * SUPP merge.
     *
     * @return the QNAM set.
     */
    Set<String> suppQnams();
}
