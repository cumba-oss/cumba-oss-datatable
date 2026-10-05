package net.cumba.datatable.merge;

/**
 * A dataset a merge asked its {@link DatasetResolver} for and did not get
 * ({@link MergedTable#unresolvedDatasets()}): what kind of part asked, and the name it asked for.
 * The kind separates a <b>declared</b> partner the study lacks — a keyed partner
 * ({@link MergeKind#KEYED}), a {@code Child} parent ({@link MergeKind#CHILD}), a related domain or
 * the {@code RELREC} dataset itself ({@link MergeKind#RELREC}) — from a primary or partner that
 * simply has no {@code SUPP--} ({@link MergeKind#SUPP}), which is the common case and no finding at
 * all (P-1).
 *
 * @param kind
 *            the kind of the part that asked.
 * @param name
 *            the dataset name as asked of the resolver.
 */
public record UnresolvedDataset(MergeKind kind, String name)
{
}
