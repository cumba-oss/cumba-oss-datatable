package net.cumba.datatable.merge;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import java.util.BitSet;
import java.util.List;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/**
 * One key-matched partner of a merge ({@code PLAN-merged-evaluation-table} §3.4, a
 * {@code Match_Datasets} entry; the data browser's Merge DM / ADSL): the partner dataset, the key
 * in its sided form, the join type, the key options and the declared cardinality.
 *
 * <ul>
 * <li>{@code qualifier} — the name the partner's columns are qualified with (the entry
 * {@code Name}); {@code dataset} is the name asked of the resolver, the qualifier when unset.</li>
 * <li>{@code keys} — in key order; a component joins only when both sides carry it: present on one
 * side only or on neither it is dropped ({@code REG-Q3}), and with no component left the merge is
 * refused ({@link DegenerateJoinKeyException}).</li>
 * <li>{@code joinType} — {@code inner} by default (the corpus default).</li>
 * <li>{@code joinAsString} — compare the keys as rendered text (D4-R3).</li>
 * <li>{@code keepMissings} — whether blank-keyed rows take part (JKM R4; {@code true} by
 * default).</li>
 * <li>{@code typeChecked} — whether a numeric / character key mismatch is refused (D4; a
 * text-carried foreign key such as {@code IDVARVAL} is excluded by the caller).</li>
 * <li>{@code cardinality} — {@code n:m} by default; a side declared {@code 1} is checked
 * (P-16).</li>
 * <li>{@code partnerMask} — an optional pre-merge filter over the partner's own rows: a row whose
 * bit is clear can never become a partner (the engine's {@code Filter}, applied at probe time over
 * the unfiltered index, P-3). {@code null} keeps every row.</li>
 * <li>{@code supp} — the partner's own supplemental dataset to serve as {@code <qualifier>.<QNAM>}
 * (MQ5), or {@code null}.</li>
 * </ul>
 */
@Value
@Builder
public class KeyedMergeSpec
{

    @NonNull
    String qualifier;

    @Nullable
    String dataset;

    @NonNull
    @Singular
    List<KeyColumn> keys;

    @NonNull
    @Builder.Default
    JoinType joinType = JoinType.INNER;

    boolean joinAsString;

    @Builder.Default
    boolean keepMissings = true;

    @Builder.Default
    boolean typeChecked = true;

    @NonNull
    @Builder.Default
    Cardinality cardinality = Cardinality.MANY_TO_MANY;

    @Nullable
    BitSet partnerMask;

    @Nullable
    SuppMergeSpec supp;

    /**
     * The dataset name asked of the resolver: {@code dataset} when set, else the qualifier.
     *
     * @return the name.
     */
    public String datasetName()
    {
        return dataset != null ? dataset : qualifier;
    }
}
