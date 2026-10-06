package net.cumba.datatable.merge;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

/**
 * The kind of a part of a {@link MergedTable}: where its columns come from and how its row map was
 * built. Published per merged column as {@link MergeMeta#MERGE_KIND} (its {@link #name()}), so a
 * reader can tell a supplemental qualifier from a keyed partner's column without consulting the
 * table that built it ({@code PLAN-merged-evaluation-table} §3.9, MET #7).
 */
public enum MergeKind
{
    /** The evaluated (primary) dataset itself — part 0, never carries {@code MERGED_FROM}. */
    PRIMARY,

    /** A key-matched partner dataset ({@code Match_Datasets} entry, Merge DM / ADSL). */
    KEYED,

    /**
     * A parent dataset of a {@code Child: true} primary, reached through the primary's
     * {@code (RDOMAIN, IDVAR, IDVARVAL)} pointer.
     */
    CHILD,

    /** A related domain reached through the study's {@code RELREC} relationships. */
    RELREC,

    /** The supplemental qualifiers of a {@code SUPP--} dataset, one column per {@code QNAM}. */
    SUPP
}
