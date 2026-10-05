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
 * <li>{@code cardinality} — {@code null} (the default) is the IG's {@code n:m} (P-13: {@code IDVAR}
 * may be a grouping variable addressing many parent records, so a row may expand) and checks
 * nothing. An authored value wins, with a log WARNING that it disagrees with the IG (MET #12 (c)),
 * and is checked like a keyed partner's (P-16): a side declared {@code 1} on the parent is a
 * pointer posting holding more than one parent record, on the primary two primary rows carrying one
 * pointer.</li>
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

    @Nullable
    Cardinality cardinality;
}
