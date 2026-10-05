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
     * Whether some merge expanded rows, i.e. the merged table has more rows than the primary or
     * maps them in a different order. On a non-expanded table every row {@code i} is primary row
     * {@code i}.
     *
     * @return whether rows were expanded.
     */
    boolean isExpanded();


    /**
     * The problems the sources carried that were reported through the {@code MERGE_ERROR} channel
     * instead of aborting the merge.
     *
     * @return the issues, in part order; empty when the merge is clean.
     */
    List<MergeIssue> issues();


    /**
     * The datasets the request named that the resolver did not supply — a partner, a parent, a
     * related domain, a supplemental dataset. Their parts are absent (keyed, child) or present with
     * every cell not supplied (RELREC related domains the relationships name); the caller decides
     * what that means (P-1, P-11).
     *
     * @return the names, in the order they were asked for; empty when every dataset resolved.
     */
    List<String> unresolvedDatasets();


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
