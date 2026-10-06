package net.cumba.datatable.merge;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import java.util.ServiceLoader;
import org.jspecify.annotations.Nullable;

/**
 * The entry point of the merged evaluation table: {@link #build(MergeRequest)} hands the request to
 * the {@link MergedTableFactory} the implementation module registers.
 */
public final class MergedTables
{

    private MergedTables()
    {
    }


    /**
     * Builds the merged table for the request.
     *
     * @param aRequest
     *            the request.
     * @return the merged table.
     * @throws MergeException
     *             when the merge cannot be built as specified.
     * @throws IllegalStateException
     *             when no implementation is on the class path.
     */
    public static MergedTable build(MergeRequest aRequest) throws MergeException
    {
        MergedTableFactory factory = Holder.FACTORY;
        if (factory == null)
        {
            throw new IllegalStateException("No " + MergedTableFactory.class.getName()
                    + " implementation is registered — is cumba-datatable-impl on the class path?");
        }
        return factory.build(aRequest);
    }

    /** Lazy, thread-safe lookup of the one registered factory ({@code null} when none is). */
    private static final class Holder
    {

        private static final @Nullable MergedTableFactory FACTORY = load();

        private static @Nullable MergedTableFactory load()
        {
            MergedTableFactory found = ServiceLoader
                    .load(MergedTableFactory.class, MergedTableFactory.class.getClassLoader())
                    .findFirst().orElse(null);
            return found != null ? found
                    : ServiceLoader.load(MergedTableFactory.class).findFirst().orElse(null);
        }
    }
}
