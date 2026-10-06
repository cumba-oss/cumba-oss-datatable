package net.cumba.datatable.merge;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import net.cumba.datatable.IDataTable;
import org.jspecify.annotations.Nullable;

/**
 * The caller's way of handing a merge the datasets it names: a partner, a parent, a related domain,
 * {@code RELREC}, a {@code SUPP--}. The merge asks by dataset name and takes {@code null} as "the
 * study has no such dataset"; what that means is the caller's decision
 * ({@code PLAN-merged-evaluation-table} P-1, P-11): the engine reads the not-supplied marker and
 * finds the name in {@link MergedTable#unresolvedDatasets()}, the data browser aborts its merge. A
 * resolver that cannot load a dataset it knows should exist records that on its own side and
 * answers {@code null}; the merge never sees an exception from it.
 */
@FunctionalInterface
public interface DatasetResolver
{

    /**
     * The dataset of the given name, or {@code null} when the study has none.
     *
     * @param aName
     *            the dataset name ({@code DM}, {@code SUPPAE}, {@code RELREC}, ...). Case as the
     *            merge spells it; a resolver matching case-insensitively is free to.
     * @return the table or {@code null}.
     */
    @Nullable
    IDataTable resolve(String aName);
}
