package net.cumba.datatable.merge;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import net.cumba.datatable.manager.RelrecMergeSpec;
import org.jspecify.annotations.Nullable;

/**
 * The RELREC merge of a primary ({@code PLAN-merged-evaluation-table} §3.6): every related domain
 * the study's {@code RELREC} relationships name for the primary's domain becomes a part, and a
 * primary record expands into the cross product of its related records <b>per relationship</b> (MET
 * #17, OD-4, P-17).
 *
 * <ul>
 * <li>{@code relrecDataset} — the RELREC dataset's name, {@code RELREC} by default.</li>
 * <li>{@code selection} — the relationships to merge (the data browser's dialog choice), or
 * {@code null} / empty for all.</li>
 * <li>{@code joinType} — {@code inner} by default (MET #21, S2); the data browser passes
 * {@code left}.</li>
 * <li>{@code cardinality} — {@code null} (the default) takes the IG's cardinality <b>per
 * relationship</b> (OD-5): a dataset-level relationship whose RELREC rows carry {@code RELTYPE}
 * declares each side {@code ONE} or {@code MANY}, and a side {@code ONE} is checked within that
 * relationship; a record-level relationship (no {@code RELTYPE}) is {@code n:m}. An authored value
 * overrides every relationship's default (OD-9) — a log WARNING names each relationship it
 * disagrees with (MET #12 (c)) — and a side declared {@code 1} is checked per related domain over
 * the distinct (primary record, related record) pairs of all relationships. The data browser passes
 * {@code n:m} (P-12: no RELTYPE check).</li>
 * <li>{@code includeUnmatchedDomains} — whether a related domain the relationships name but no
 * record of the primary pairs with still contributes its (not-supplied) columns; {@code true} for a
 * declared-qualifier consumer (P-14), {@code false} to keep the browser's "nothing to merge"
 * result.</li>
 * </ul>
 */
@Value
@Builder
public class RelrecPartSpec
{

    @NonNull
    @Builder.Default
    String relrecDataset = "RELREC";

    @Nullable
    RelrecMergeSpec selection;

    @NonNull
    @Builder.Default
    JoinType joinType = JoinType.INNER;

    @Nullable
    Cardinality cardinality;

    @Builder.Default
    boolean includeUnmatchedDomains = true;
}
