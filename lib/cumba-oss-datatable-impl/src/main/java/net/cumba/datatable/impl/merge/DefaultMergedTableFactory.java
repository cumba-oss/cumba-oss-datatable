package net.cumba.datatable.impl.merge;

import net.cumba.datatable.merge.MergeRequest;
import net.cumba.datatable.merge.MergedTable;
import net.cumba.datatable.merge.MergedTableFactory;

/**
 * The {@link MergedTableFactory} this module registers through {@code META-INF/services}: builds a
 * {@link MergedTableImpl} through {@link MergeBuilder}.
 */
public final class DefaultMergedTableFactory implements MergedTableFactory
{

    @Override
    public MergedTable build(MergeRequest aRequest)
    {
        return new MergeBuilder(aRequest, null).build();
    }
}
