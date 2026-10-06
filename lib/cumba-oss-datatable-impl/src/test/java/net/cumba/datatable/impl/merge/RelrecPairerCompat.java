package net.cumba.datatable.impl.merge;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.merge.RelrecLinks;
import org.jspecify.annotations.Nullable;

/**
 * The data browser resolver's former entry point, rebuilt over {@link RelrecLinks} and
 * {@link RelrecPairer} so the resolver's test corpus runs unchanged against the shared pairing: the
 * pairs it answers are the shared pairs in the shape the old tests assert on.
 */
final class RelrecPairerCompat
{

    record RelrecPair(long primaryRow, int relatedOrdinal, long relatedRow, @Nullable String relid,
            String rdomain)
    {
    }


    record RelrecPairing(List<IDataTable> relatedTables, List<RelrecPair> pairs)
    {
    }

    private RelrecPairerCompat()
    {
    }


    static RelrecPairing resolve(@Nullable IDataTable aRelrec, IDataTable aPrimary,
            String aPrimaryDomain, Function<String, @Nullable IDataTable> aRelatedResolver,
            @Nullable Set<String> aSelectedRdomains)
    {
        if (aRelrec == null)
        {
            return new RelrecPairing(List.of(), List.of());
        }
        RelrecLinks.Links links = RelrecLinks.collect(aRelrec, aPrimaryDomain, aSelectedRdomains);
        RelrecPairer.Pairing pairing = RelrecPairer.resolve(links, aPrimary,
                aRelatedResolver::apply);
        List<RelrecPair> pairs = new ArrayList<>();
        for (RelrecPairer.Pair p : pairing.pairs())
        {
            pairs.add(new RelrecPair(p.primaryRow(), p.ordinal(), p.relatedRow(), p.relid(),
                    p.rdomain()));
        }
        return new RelrecPairing(pairing.relatedTables(), pairs);
    }
}
