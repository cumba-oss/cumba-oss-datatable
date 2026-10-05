package net.cumba.datatable.merge;

import java.util.List;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import org.jspecify.annotations.Nullable;

/**
 * The parent(s) of a {@code Child: true} primary ({@code PLAN-merged-evaluation-table} §3.7, MQ1):
 * a primary such as {@code SUPPAE}, {@code CO} or {@code RELREC} points at a parent record through
 * {@code (RDOMAIN, IDVAR, IDVARVAL)}; the merge adds one part per parent domain, its columns
 * qualified with that domain, supplied only on the rows whose parent is that domain.
 *
 * <ul>
 * <li>{@code parentDataset} — a fixed parent dataset name, or {@code null} to read each row's
 * parent domain from {@code rdomainColumn}.</li>
 * <li>{@code subjectKeys} — the subject-key columns compared besides the pointer, each used only
 * when present on both sides (MET #19); the IG's {@code STUDYID}, {@code USUBJID}, {@code POOLID}
 * by default.</li>
 * <li>{@code idvarColumn} / {@code idvarvalColumn} / {@code rdomainColumn} — the pointer columns on
 * the primary.</li>
 * <li>{@code joinType} — {@code inner} by default.</li>
 * <li>{@code cardinality} — {@code n:m} by default (P-13: {@code IDVAR} may be a grouping variable
 * addressing many parent records, so a row may expand).</li>
 * </ul>
 */
@Value
@Builder
public class ChildMergeSpec
{

    @Nullable
    String parentDataset;

    @NonNull
    @Builder.Default
    List<String> subjectKeys = List.of("STUDYID", "USUBJID", "POOLID");

    @NonNull
    @Builder.Default
    String idvarColumn = "IDVAR";

    @NonNull
    @Builder.Default
    String idvarvalColumn = "IDVARVAL";

    @NonNull
    @Builder.Default
    String rdomainColumn = "RDOMAIN";

    @NonNull
    @Builder.Default
    JoinType joinType = JoinType.INNER;

    @NonNull
    @Builder.Default
    Cardinality cardinality = Cardinality.MANY_TO_MANY;
}
