package net.cumba.datatable.impl.merge;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.databuffer.DataBufferFactory;
import net.cumba.datatable.impl.databuffer.IDataBufferNumeric;
import net.cumba.datatable.impl.view.DataTableViewBuffer;
import net.cumba.datatable.impl.view.UnionDataTable;
import net.cumba.datatable.impl.view.ViewDataTable;
import net.cumba.datatable.manager.RelrecMergeSpec;
import net.cumba.datatable.merge.Cardinality;
import net.cumba.datatable.merge.CardinalityViolationException;
import net.cumba.datatable.merge.ChildMergeSpec;
import net.cumba.datatable.merge.JoinType;
import net.cumba.datatable.merge.KeyedMergeSpec;
import net.cumba.datatable.merge.MergeException;
import net.cumba.datatable.merge.MergeIssue;
import net.cumba.datatable.merge.MergeKind;
import net.cumba.datatable.merge.MergeMeta;
import net.cumba.datatable.merge.MergeNaming;
import net.cumba.datatable.merge.MergeRequest;
import net.cumba.datatable.merge.MergedPart;
import net.cumba.datatable.merge.RelrecLinks;
import net.cumba.datatable.merge.RelrecPartSpec;
import net.cumba.datatable.merge.SuppMergeSpec;
import net.cumba.datatable.merge.UnresolvedDataset;
import net.cumba.datatable.values.IDataValue;
import org.jspecify.annotations.Nullable;

/**
 * Builds a {@link MergedTableImpl} from a {@code MergeRequest}
 * ({@code PLAN-merged-evaluation-table} §3.1): each merge kind builds its own part through its own
 * matcher, and this class composes the row maps — every expanding merge contributes a factor, and a
 * primary record's merged rows are the cross product of its factors (several expansions compose as
 * maps, MQ11; the RELREC factor is itself the per-relationship cross product, OD-4) — then names
 * the columns through the request's {@code MergeNaming} and lays the parts side by side.
 *
 * <p>
 * Nothing is copied: a part is a {@link ViewDataTable} over its source through a compact
 * {@link DataTableViewBuffer} row map (a supplemental part its own {@link SuppPart}), the primary
 * is itself unless some merge dropped or expanded a row, in which case it is row-mapped too and
 * {@code getRealRowIndex} still answers its native row.
 * </p>
 */
final class MergeBuilder
{

    private static final Logger LOGGER = System.getLogger(MergeBuilder.class.getName());

    private final MergeRequest request;

    private final IDataTable primary;

    private final int primaryRows;

    private final String primaryName;

    private final String domain;

    private final MergeNaming naming;

    private final List<MergeIssue> issues = new ArrayList<>();

    private final List<UnresolvedDataset> unresolved = new ArrayList<>();

    private final Set<String> usedNames = new HashSet<>();

    /** The primary's own column names, the set a bare naming leaves partner columns out for. */
    private final Set<String> primaryNames = new HashSet<>();

    private final @Nullable Consumer<SuppQnamIndex> suppIndexObserver;

    /** One keyed partner that resolved. */
    private record KeyedUnit(KeyedMergeSpec spec, IDataTable partner, KeyedMatcher matcher)
    {
    }


    /**
     * The RELREC factor: the domains in part order and, per primary row, its tuples — compact (L7):
     * primary row {@code p}'s tuples are {@code tupleStart[p] .. tupleStart[p + 1] - 1}, tuple
     * {@code t} holding one related row (or {@code -1}) per domain at
     * {@code tuples[t * domains .. (t + 1) * domains - 1]}.
     */
    private static final class RelrecUnit
    {

        private final RelrecPartSpec spec;

        private final RelrecPairer.Pairing pairing;

        private final List<Integer> ordinals;

        private final int[] tupleStart;

        private final IntList tuples;

        RelrecUnit(RelrecPartSpec aSpec, RelrecPairer.Pairing aPairing, List<Integer> aOrdinals,
                int[] aTupleStart, IntList aTuples)
        {
            spec = aSpec;
            pairing = aPairing;
            ordinals = aOrdinals;
            tupleStart = aTupleStart;
            tuples = aTuples;
        }


        RelrecPartSpec spec()
        {
            return spec;
        }


        RelrecPairer.Pairing pairing()
        {
            return pairing;
        }


        List<Integer> ordinals()
        {
            return ordinals;
        }


        /** The first tuple of primary row {@code aRow}; {@code aRow + 1} gives its end. */
        int tupleStart(int aRow)
        {
            return tupleStart[aRow];
        }


        IntList tuples()
        {
            return tuples;
        }
    }

    MergeBuilder(MergeRequest aRequest, @Nullable Consumer<SuppQnamIndex> aSuppIndexObserver)
    {
        request = aRequest;
        primary = aRequest.getPrimary();
        primaryRows = Math.toIntExact(primary.getRowCount());
        primaryName = KeyedMatcher.nameOf(primary);
        naming = aRequest.getNaming();
        suppIndexObserver = aSuppIndexObserver;
        String d = aRequest.getPrimaryDomain();
        domain = d != null ? d : domainOf(primary);
        for (DataTableColumnMeta cm : primary.getMetaData().getColumns())
        {
            usedNames.add(MergeNaming.nameKey(cm.getName()));
            primaryNames.add(MergeNaming.nameKey(cm.getName()));
        }
    }


    /**
     * The domain of a dataset: its {@code DOMAIN} column's first value, else its name.
     *
     * @param aTable
     *            the table.
     * @return the domain.
     */
    static String domainOf(IDataTable aTable)
    {
        int col = aTable.getMetaData().getColumnIndex("DOMAIN");
        if (col >= 0 && aTable.getRowCount() > 0)
        {
            IDataValue dv = aTable.getDataValue(0, col);
            if (!dv.isMissingOrInvalid() && !dv.getValueAsString().isBlank())
            {
                return dv.getValueAsString().strip();
            }
        }
        return KeyedMatcher.nameOf(aTable);
    }


    MergedTableImpl build()
    {
        List<KeyedUnit> keyedUnits = resolveKeyed();
        ChildMergeSpec childSpec = request.getChild();
        ChildMatcher child = childSpec == null ? null
                : ChildMatcher.of(primary, childSpec, request.getResolver());
        List<IDataTable> childParents = child == null ? List.of() : child.parents();
        List<String> childNames = child == null ? List.of() : child.parentNames();
        if (child != null)
        {
            for (String name : child.unresolved())
            {
                unresolved.add(new UnresolvedDataset(MergeKind.CHILD, name));
            }
            child.checkCardinality();
        }
        RelrecUnit relrec = resolveRelrec();

        // ---- the expansion pass: one factor per keyed partner, the child part, the RELREC part
        int nKeyed = keyedUnits.size();
        int nChild = childParents.size();
        int nRelrec = relrec == null ? 0 : relrec.ordinals().size();
        IDataBufferNumeric primaryMap = DataBufferFactory.get().createForRange(0,
                Math.max(0, primaryRows - 1L));
        IDataBufferNumeric[] keyedMaps = new IDataBufferNumeric[nKeyed];
        for (int k = 0; k < nKeyed; k++)
        {
            keyedMaps[k] = DataBufferFactory.get().createForRange(-1,
                    Math.max(0, keyedUnits.get(k).partner().getRowCount() - 1));
        }
        IDataBufferNumeric[] childMaps = new IDataBufferNumeric[nChild];
        for (int c = 0; c < nChild; c++)
        {
            childMaps[c] = DataBufferFactory.get().createForRange(-1,
                    Math.max(0, childParents.get(c).getRowCount() - 1));
        }
        IDataBufferNumeric[] relrecMaps = new IDataBufferNumeric[nRelrec];
        for (int d = 0; d < nRelrec && relrec != null; d++)
        {
            IDataTable related = relrec.pairing().relatedTables().get(relrec.ordinals().get(d));
            relrecMaps[d] = DataBufferFactory.get().createForRange(-1,
                    Math.max(0, related.getRowCount() - 1));
        }
        // Reused across the primary rows: nothing below allocates or boxes per row (L7).
        IntList[] keyedMatches = new IntList[nKeyed];
        for (int k = 0; k < nKeyed; k++)
        {
            keyedMatches[k] = new IntList();
        }
        IntList childMatches = new IntList();
        int[] counters = new int[nKeyed + 1];
        int size = 0;
        // H1 (W1 review): row-mapped is decided on IDENTITY — every primary row kept, each giving
        // exactly one row — never on the count, which an inner merge's dropped and expanded rows
        // can leave unchanged while every part but the primary follows the real sequence.
        boolean identity = true;
        for (int p = 0; p < primaryRows; p++)
        {
            boolean dropped = false;
            for (int k = 0; k < nKeyed && !dropped; k++)
            {
                KeyedUnit u = keyedUnits.get(k);
                u.matcher().matchesOf(p, keyedMatches[k]);
                dropped = unmatchedHandling(keyedMatches[k], u.spec().getJoinType());
            }
            int childOrdinal = -1;
            if (!dropped && child != null && childSpec != null)
            {
                child.matchesOf(p, childMatches);
                childOrdinal = child.ordinalOf(p);
                dropped = unmatchedHandling(childMatches, childSpec.getJoinType());
            }
            int tupleFrom = 0;
            int tupleCount = 1; // one row with every related domain not supplied
            boolean noTuple = true;
            if (!dropped && relrec != null)
            {
                tupleFrom = relrec.tupleStart(p);
                int to = relrec.tupleStart(p + 1);
                if (to == tupleFrom)
                {
                    dropped = relrec.spec().getJoinType() == JoinType.INNER;
                }
                else
                {
                    noTuple = false;
                    tupleCount = to - tupleFrom;
                }
            }
            if (dropped)
            {
                identity = false;
                continue;
            }
            // The odometer over the factors: keyed lists, the child list, the RELREC tuples.
            java.util.Arrays.fill(counters, 0);
            int relrecCounter = 0;
            int emitted = 0;
            while (true)
            {
                primaryMap.addValue(p);
                for (int k = 0; k < nKeyed; k++)
                {
                    keyedMaps[k].addValue(keyedMatches[k].get(counters[k]));
                }
                if (child != null)
                {
                    int match = childMatches.get(counters[nKeyed]);
                    for (int c = 0; c < nChild; c++)
                    {
                        childMaps[c].addValue(c == childOrdinal ? match : -1);
                    }
                }
                for (int d = 0; d < nRelrec && relrec != null; d++)
                {
                    relrecMaps[d].addValue(noTuple ? -1
                            : relrec.tuples().get((tupleFrom + relrecCounter) * nRelrec + d));
                }
                size++;
                emitted++;
                // advance the odometer, RELREC fastest, then the child, then the keyed lists
                if (++relrecCounter < tupleCount)
                {
                    continue;
                }
                relrecCounter = 0;
                int i = nKeyed;
                while (i >= 0)
                {
                    int limit = i == nKeyed ? (child == null ? 1 : childMatches.size())
                            : keyedMatches[i].size();
                    if (++counters[i] < limit)
                    {
                        break;
                    }
                    counters[i] = 0;
                    i--;
                }
                if (i < 0)
                {
                    break;
                }
            }
            identity &= emitted == 1;
        }
        boolean rowMapped = !identity;

        // ---- the parts, named
        List<IDataTable> tables = new ArrayList<>();
        List<MergedPart> parts = new ArrayList<>();
        IDataTable primaryPart = rowMapped
                ? new ViewDataTable(primary, new DataTableViewBuffer(primaryMap), null)
                : primary;
        tables.add(primaryPart);
        parts.add(new MergedPart(MergeKind.PRIMARY, primaryName, primaryName, 0,
                primary.getColumnCount(), -1));
        List<SuppPending> supps = new ArrayList<>();
        for (int k = 0; k < nKeyed; k++)
        {
            KeyedUnit u = keyedUnits.get(k);
            addMappedPart(tables, parts, MergeKind.KEYED, u.spec().getQualifier(), u.partner(),
                    keyedMaps[k], size, null);
            SuppMergeSpec partnerSupp = u.spec().getSupp();
            if (partnerSupp != null)
            {
                supps.add(new SuppPending(u.partner(), u.spec().getQualifier(), false, partnerSupp,
                        keyedMaps[k]));
            }
        }
        for (int c = 0; c < nChild && child != null; c++)
        {
            String parentName = childNames.get(c);
            addMappedPart(tables, parts, MergeKind.CHILD, domainOf(childParents.get(c)),
                    childParents.get(c), childMaps[c], size, childIssueOf(child, parentName));
        }
        if (relrec != null)
        {
            addRelrecParts(tables, parts, relrec, relrecMaps, size);
        }
        Set<String> suppQnams = new LinkedHashSet<>();
        if (request.getSupp() != null)
        {
            supps.add(0, new SuppPending(primary, primaryName, true, request.getSupp(),
                    rowMapped ? primaryMap : null));
        }
        for (SuppPending sp : supps)
        {
            Set<String> qnams = addSuppPart(tables, parts, sp, size);
            if (sp.primaryBase())
            {
                suppQnams.addAll(qnams);
            }
        }
        return new MergedTableImpl(primary, tables.toArray(IDataTable[]::new), parts, issues,
                unresolved, suppQnams, rowMapped);
    }


    /**
     * Adds the RELREC parts — one per related domain in part order, each carrying its domain's
     * issue — and the {@code RELREC._matched_} flag. An issue whose domain has no part is still
     * published (M2: the browser's setting used to drop it with the part).
     */
    private void addRelrecParts(List<IDataTable> aTables, List<MergedPart> aParts,
            RelrecUnit aRelrec, IDataBufferNumeric[] aRelrecMaps, int aSize)
    {
        int nRelrec = aRelrec.ordinals().size();
        Map<String, RelrecPairer.DomainIssue> issueByDomain = new LinkedHashMap<>();
        for (RelrecPairer.DomainIssue di : aRelrec.pairing().issues())
        {
            issueByDomain.merge(di.rdomain(), di,
                    (a, b) -> new RelrecPairer.DomainIssue(a.rdomain(),
                            a.message() + "; " + b.message(),
                            concat(a.relrecRows(), b.relrecRows())));
        }
        String relrecName = aRelrec.spec().getRelrecDataset();
        for (int d = 0; d < nRelrec; d++)
        {
            int ordinal = aRelrec.ordinals().get(d);
            String rdomain = aRelrec.pairing().rdomains().get(ordinal);
            RelrecPairer.DomainIssue di = issueByDomain.remove(rdomain);
            addMappedPart(aTables, aParts, MergeKind.RELREC, rdomain,
                    aRelrec.pairing().relatedTables().get(ordinal), aRelrecMaps[d], aSize,
                    di == null ? null
                            : new MergeIssue(MergeKind.RELREC, relrecName, rdomain, di.message(),
                                    di.relrecRows()));
        }
        for (RelrecPairer.DomainIssue di : issueByDomain.values())
        {
            issues.add(new MergeIssue(MergeKind.RELREC, relrecName, di.rdomain(), di.message(),
                    di.relrecRows()));
        }
        if (request.isMatchedFlags() && nRelrec > 0)
        {
            IDataBufferNumeric any = DataBufferFactory.get().createForRange(-1, 0);
            any.setExpectedSize(aSize);
            for (int i = 0; i < aSize; i++)
            {
                boolean matched = false;
                for (int d = 0; d < nRelrec && !matched; d++)
                {
                    matched = aRelrecMaps[d].getValueAsLong(i) >= 0;
                }
                any.addValue(matched ? 0 : -1);
            }
            String name = claim(MergeKind.RELREC, "RELREC", naming.matchedFlagName("RELREC"));
            aTables.add(
                    new MatchedFlagTable(relrecName, name, "RELREC", MergeKind.RELREC, aSize, any));
            aParts.add(new MergedPart(MergeKind.RELREC, "RELREC", relrecName,
                    columnsSoFar(aTables) - 1, 1, columnsSoFar(aTables) - 1));
        }
    }

    /** A supplemental merge still to be added, over its base and the base's row map. */
    private record SuppPending(IDataTable base, String qualifier, boolean primaryBase,
            SuppMergeSpec spec, @Nullable IDataBufferNumeric baseMap)
    {
    }

    /**
     * Applies the join type to an empty match list: {@code left} binds the unsupplied row,
     * {@code inner} drops the primary row.
     *
     * @return whether the primary row is dropped.
     */
    private static boolean unmatchedHandling(IntList aMatches, JoinType aJoinType)
    {
        if (aMatches.isEmpty())
        {
            if (aJoinType == JoinType.INNER)
            {
                return true;
            }
            aMatches.add(-1);
        }
        return false;
    }


    private static List<Long> concat(List<Long> aA, List<Long> aB)
    {
        List<Long> out = new ArrayList<>(aA);
        out.addAll(aB);
        return out;
    }


    private static int columnsSoFar(List<IDataTable> aTables)
    {
        int n = 0;
        for (IDataTable t : aTables)
        {
            n += t.getColumnCount();
        }
        return n;
    }


    private @Nullable MergeIssue childIssueOf(ChildMatcher aChild, String aParent)
    {
        for (ChildMatcher.Issue i : aChild.issues())
        {
            if (i.parent().equals(aParent))
            {
                return new MergeIssue(MergeKind.CHILD, aParent, aParent, i.message(),
                        i.primaryRows());
            }
        }
        return null;
    }

    // ---- keyed


    private List<KeyedUnit> resolveKeyed()
    {
        List<KeyedUnit> units = new ArrayList<>();
        for (KeyedMergeSpec spec : request.getKeyed())
        {
            IDataTable partner = request.getResolver().resolve(spec.datasetName());
            if (partner == null)
            {
                unresolved.add(new UnresolvedDataset(MergeKind.KEYED, spec.datasetName()));
                continue;
            }
            KeyedMatcher matcher = KeyedMatcher.of(primary, partner, spec);
            matcher.checkCardinality(spec.getCardinality());
            units.add(new KeyedUnit(spec, partner, matcher));
        }
        return units;
    }

    // ---- RELREC


    private @Nullable RelrecUnit resolveRelrec()
    {
        RelrecPartSpec spec = request.getRelrec();
        if (spec == null)
        {
            return null;
        }
        IDataTable relrecTable = request.getResolver().resolve(spec.getRelrecDataset());
        if (relrecTable == null)
        {
            unresolved.add(new UnresolvedDataset(MergeKind.RELREC, spec.getRelrecDataset()));
            return null;
        }
        RelrecMergeSpec selection = spec.getSelection();
        RelrecLinks.Links links = RelrecLinks.collect(relrecTable, domain,
                selectedRdomains(selection), request.getStandard());
        RelrecPairer.Pairing pairing = RelrecPairer.resolve(links, primary, request.getResolver());
        for (String rdomain : pairing.unresolvedDomains())
        {
            unresolved.add(new UnresolvedDataset(MergeKind.RELREC, rdomain));
        }
        List<RelrecPairer.Pair> pairs = filterToSelection(pairing.pairs(), selection);
        checkRelrecCardinality(spec.getCardinality(), links, pairing, pairs);
        List<Integer> ordinals;
        if (spec.isIncludeUnmatchedDomains())
        {
            ordinals = new ArrayList<>();
            for (int i = 0; i < pairing.relatedTables().size(); i++)
            {
                ordinals.add(i);
            }
        }
        else
        {
            ordinals = RelrecPairer.usedOrdinalsInPairOrder(
                    new RelrecPairer.Pairing(pairing.rdomains(), pairing.relatedTables(), pairs,
                            pairing.unresolvedDomains(), pairing.issues(), pairing.mixedSkipped()));
            // M2 (W1 review): a domain with an issue keeps its part even when nothing pairs, so
            // its MERGE_ERROR reaches a consumer that reads the column metadata (the browser)
            Set<String> withIssue = new HashSet<>();
            for (RelrecPairer.DomainIssue di : pairing.issues())
            {
                withIssue.add(di.rdomain());
            }
            for (int i = 0; i < pairing.rdomains().size(); i++)
            {
                if (withIssue.contains(pairing.rdomains().get(i)) && !ordinals.contains(i))
                {
                    ordinals.add(i);
                }
            }
        }
        int[] positionOfOrdinal = new int[pairing.relatedTables().size()];
        java.util.Arrays.fill(positionOfOrdinal, -1);
        for (int d = 0; d < ordinals.size(); d++)
        {
            positionOfOrdinal[ordinals.get(d)] = d;
        }
        int[] tupleStart = new int[primaryRows + 1];
        IntList tuples = crossProductPerRelationship(pairs, positionOfOrdinal, ordinals.size(),
                tupleStart);
        return new RelrecUnit(spec, pairing, ordinals, tupleStart, tuples);
    }

    /** One tuple of the flat tuple list, hashed and compared by value (the de-duplication). */
    private record TupleKey(IntList list, int offset, int width, int hash)
    {

        @Override
        public boolean equals(Object aOther)
        {
            return aOther instanceof TupleKey o && o.width == width
                    && list.sliceEquals(offset, o.offset, width);
        }


        @Override
        public int hashCode()
        {
            return hash;
        }
    }

    /**
     * The merged rows of every primary record with at least one pair: per relationship (in the
     * order the relationships appear in RELREC) the cross product over the related domains of
     * {@code max(1, n)} factors — a domain the relationship does not name, or a dangling link,
     * gives one row with that domain not supplied (P-17); the rows of a record's relationships are
     * summed (OD-4); a row identical in every domain to one already produced for the same record is
     * produced once (a pair shared by two relationships is one row).
     *
     * @param aTupleStart
     *            filled: per primary row the index of its first tuple, plus the end.
     * @return the tuples, {@code aDomains} values each.
     */
    private static IntList crossProductPerRelationship(List<RelrecPairer.Pair> aPairs,
            int[] aPositionOfOrdinal, int aDomains, int[] aTupleStart)
    {
        IntList tuples = new IntList(Math.max(1, aPairs.size() * Math.max(1, aDomains)));
        IntList none = new IntList(1);
        none.add(-1);
        IntList[] factors = new IntList[aDomains];
        int[] counters = new int[aDomains];
        int count = 0;
        int nextRow = 0;
        int i = 0;
        while (i < aPairs.size())
        {
            int p = aPairs.get(i).primaryRow();
            for (; nextRow <= p; nextRow++)
            {
                aTupleStart[nextRow] = count;
            }
            // relationship -> position -> related rows (ascending, as the pairs are sorted)
            Map<Integer, Map<Integer, IntList>> byRelationship = new java.util.TreeMap<>();
            while (i < aPairs.size() && aPairs.get(i).primaryRow() == p)
            {
                RelrecPairer.Pair pair = aPairs.get(i);
                int pos = aPositionOfOrdinal[pair.ordinal()];
                if (pos >= 0)
                {
                    byRelationship
                            .computeIfAbsent(pair.relationship(), _ -> new java.util.TreeMap<>())
                            .computeIfAbsent(pos, _ -> new IntList(2)).add(pair.relatedRow());
                }
                i++;
            }
            // one relationship cannot repeat a tuple (its pairs are distinct); only a record in
            // several relationships needs the de-duplication
            Set<TupleKey> seen = byRelationship.size() > 1 ? new HashSet<>() : null;
            for (Map<Integer, IntList> perDomain : byRelationship.values())
            {
                for (int d = 0; d < aDomains; d++)
                {
                    IntList f = perDomain.get(d);
                    factors[d] = f == null ? none : f;
                }
                java.util.Arrays.fill(counters, 0);
                while (true)
                {
                    int offset = count * aDomains;
                    for (int d = 0; d < aDomains; d++)
                    {
                        tuples.add(factors[d].get(counters[d]));
                    }
                    if (seen == null || seen.add(new TupleKey(tuples, offset, aDomains,
                            tuples.sliceHash(offset, aDomains))))
                    {
                        count++;
                    }
                    else
                    {
                        tuples.truncate(offset);
                    }
                    int d = aDomains - 1;
                    while (d >= 0 && ++counters[d] == factors[d].size())
                    {
                        counters[d] = 0;
                        d--;
                    }
                    if (d < 0)
                    {
                        break;
                    }
                }
            }
        }
        for (; nextRow < aTupleStart.length; nextRow++)
        {
            aTupleStart[nextRow] = count;
        }
        return tuples;
    }

    /** A side of a RELREC pair for the cardinality check: (relationship or -1, domain, row). */
    private record PairSide(int relationship, int ordinal, int row)
    {
    }


    /** A dataset-level relationship's link to one related domain, for the IG default (OD-5). */
    private record RelationshipDomain(int relationship, String rdomain)
    {
    }

    /**
     * The RELREC cardinality check. Authored ({@code aAuthored} non-null): over the distinct
     * (primary record, related record) pairs of each related domain across every relationship (W1),
     * overriding each relationship's IG default (OD-9) with a log WARNING per relationship whose
     * {@code RELTYPE} states a different one (MET #12 (c)). Unauthored: each dataset-level
     * relationship's {@code RELTYPE} default (OD-5), checked within that relationship; a
     * record-level relationship is {@code n:m}.
     */
    private void checkRelrecCardinality(@Nullable Cardinality aAuthored, RelrecLinks.Links aLinks,
            RelrecPairer.Pairing aPairing, List<RelrecPairer.Pair> aPairs)
    {
        Map<RelationshipDomain, Cardinality> stated = new LinkedHashMap<>();
        Map<RelationshipDomain, String> relidOf = new HashMap<>();
        for (RelrecLinks.DomainLinks dl : aLinks.domains())
        {
            for (RelrecLinks.Link link : dl.links())
            {
                if (!link.recordLevel() && (link.srcReltype() != null || link.tgtReltype() != null))
                {
                    RelationshipDomain key = new RelationshipDomain(link.relationship(),
                            upper(link.rdomain()));
                    stated.putIfAbsent(key, link.igCardinality());
                    relidOf.putIfAbsent(key, link.relid());
                }
            }
        }
        if (aAuthored != null)
        {
            for (Map.Entry<RelationshipDomain, Cardinality> e : stated.entrySet())
            {
                if (e.getValue() != aAuthored)
                {
                    LOGGER.log(Level.WARNING,
                            "{0}: the authored RELREC Cardinality {1} disagrees with the {2} that"
                                    + " RELTYPE states for relationship {3} with {4}; the authored"
                                    + " value is checked",
                            primaryName, aAuthored.text(), e.getValue().text(),
                            relidOf.get(e.getKey()), e.getKey().rdomain());
                }
            }
            checkPairs(aPairing, aPairs, _ -> aAuthored, false);
            return;
        }
        if (stated.isEmpty())
        {
            return;
        }
        checkPairs(aPairing, aPairs,
                pair -> stated.getOrDefault(
                        new RelationshipDomain(pair.relationship(), upper(pair.rdomain())),
                        Cardinality.MANY_TO_MANY),
                true);
    }


    /**
     * A side declared {@code 1} pairs each of its records with at most one record of the other side
     * — over the distinct pairs of each related domain, within each relationship when
     * {@code aPerRelationship}.
     */
    private static void checkPairs(RelrecPairer.Pairing aPairing, List<RelrecPairer.Pair> aPairs,
            java.util.function.Function<RelrecPairer.Pair, Cardinality> aCardinalityOf,
            boolean aPerRelationship)
    {
        Map<PairSide, Integer> firstRelated = new HashMap<>();
        Map<PairSide, Integer> firstPrimary = new HashMap<>();
        for (RelrecPairer.Pair pair : aPairs)
        {
            Cardinality c = aCardinalityOf.apply(pair);
            if (!c.leftUnique() && !c.rightUnique())
            {
                continue;
            }
            int rel = aPerRelationship ? pair.relationship() : -1;
            String within = aPerRelationship ? " in relationship " + pair.relid() : "";
            String related = KeyedMatcher.nameOf(aPairing.relatedTables().get(pair.ordinal()));
            if (c.rightUnique())
            {
                Integer first = firstRelated.putIfAbsent(
                        new PairSide(rel, pair.ordinal(), pair.primaryRow()), pair.relatedRow());
                if (first != null && first != pair.relatedRow())
                {
                    throw new CardinalityViolationException(c, related,
                            "(" + pair.rdomain() + " records related to primary record "
                                    + (pair.primaryRow() + 1) + within + ")",
                            first, pair.relatedRow());
                }
            }
            if (c.leftUnique())
            {
                Integer first = firstPrimary.putIfAbsent(
                        new PairSide(rel, pair.ordinal(), pair.relatedRow()), pair.primaryRow());
                if (first != null && first != pair.primaryRow())
                {
                    throw new CardinalityViolationException(c, "the primary dataset",
                            "(primary records related to " + related + " record "
                                    + (pair.relatedRow() + 1) + within + ")",
                            first, pair.primaryRow());
                }
            }
        }
    }


    private static @Nullable Set<String> selectedRdomains(@Nullable RelrecMergeSpec aSpec)
    {
        if (aSpec == null || aSpec.getRelationships().isEmpty())
        {
            return null;
        }
        Set<String> out = new HashSet<>();
        for (RelrecMergeSpec.Item it : aSpec.getRelationships())
        {
            out.add(upper(it.getRdomain()));
        }
        return out;
    }


    private static List<RelrecPairer.Pair> filterToSelection(List<RelrecPairer.Pair> aPairs,
            @Nullable RelrecMergeSpec aSpec)
    {
        if (aSpec == null || aSpec.getRelationships().isEmpty())
        {
            return aPairs;
        }
        Set<String> exact = new HashSet<>();
        Set<String> wholeDomains = new HashSet<>();
        for (RelrecMergeSpec.Item it : aSpec.getRelationships())
        {
            if (it.getRelid() == null || it.getRelid().isBlank())
            {
                wholeDomains.add(upper(it.getRdomain()));
            }
            else
            {
                exact.add(upper(it.getRdomain()) + '\0' + it.getRelid());
            }
        }
        return aPairs.stream().filter(p -> wholeDomains.contains(upper(p.rdomain()))
                || exact.contains(upper(p.rdomain()) + '\0' + p.relid())).toList();
    }


    private static String upper(@Nullable String aValue)
    {
        return aValue == null ? "" : aValue.toUpperCase(Locale.ROOT);
    }

    // ---- parts


    /** Claims a merged column name, resolving a clash through the naming. */
    private String claim(MergeKind aKind, String aQualifier, String aName)
    {
        String name = aName;
        if (usedNames.contains(MergeNaming.nameKey(name)))
        {
            name = naming.onClash(aKind, aQualifier, name,
                    n -> usedNames.contains(MergeNaming.nameKey(n)));
        }
        usedNames.add(MergeNaming.nameKey(name));
        return name;
    }


    /**
     * Adds a keyed, child or RELREC part: a {@link ViewDataTable} over the source through the row
     * map, its columns named and tagged, plus its {@code _matched_} flag when the request wants
     * flags.
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    private void addMappedPart(List<IDataTable> aTables, List<MergedPart> aParts, MergeKind aKind,
            String aQualifier, IDataTable aSource, IDataBufferNumeric aMap, int aRows,
            @Nullable MergeIssue aIssue)
    {
        DataTableMeta sm = aSource.getMetaData();
        List<DataTableColumnMeta> columns = new ArrayList<>();
        for (DataTableColumnMeta cm : sm.getColumns())
        {
            String proposed = naming.columnName(aKind, aQualifier, cm.getName(),
                    n -> primaryNames.contains(MergeNaming.nameKey(n)));
            if (proposed == null)
            {
                continue;
            }
            String name = claim(aKind, aQualifier, proposed);
            DataTableColumnMeta.DataTableColumnMetaBuilder b = DataTableColumnMeta.builderFrom(cm)
                    .name(name).addMetaData(MergeMeta.MERGE_KIND, aKind.name())
                    .addMetaData(MergeMeta.MERGED_SOURCE_COLUMN, cm.getName());
            if (aIssue != null)
            {
                b.addMetaData(MergeMeta.MERGE_ERROR, aIssue.message());
            }
            columns.add(b.build());
        }
        if (aIssue != null)
        {
            issues.add(aIssue);
        }
        int first = columnsSoFar(aTables);
        aTables.add(new ViewDataTable(aSource, new DataTableViewBuffer(aMap), columns.stream()));
        int flagColumn = -1;
        int count = columns.size();
        if (request.isMatchedFlags())
        {
            String flagName = claim(aKind, aQualifier, naming.matchedFlagName(aQualifier));
            flagColumn = first + count;
            aTables.add(new MatchedFlagTable(KeyedMatcher.nameOf(aSource), flagName, aQualifier,
                    aKind, aRows, aMap));
            count++;
        }
        aParts.add(new MergedPart(aKind, aQualifier, KeyedMatcher.nameOf(aSource), first, count,
                flagColumn));
    }


    /**
     * Adds a supplemental part; returns the base's {@code QNAM} set. Every source that resolves is
     * fed (P-7: a split dataset's own {@code SUPP} and its domain's, both); several are stacked
     * into one table, so a record two of them qualify for one {@code QNAM} is a duplicate.
     */
    private Set<String> addSuppPart(List<IDataTable> aTables, List<MergedPart> aParts,
            SuppPending aPending, int aRows)
    {
        IDataTable base = aPending.base();
        String baseDomain = domainOf(base);
        List<String> authored = aPending.spec().getSuppDatasets();
        List<String> names = authored.isEmpty()
                ? SuppMergeSpec.defaultSuppNames(KeyedMatcher.nameOf(base), baseDomain)
                : authored;
        List<IDataTable> sources = new ArrayList<>();
        List<String> sourceNames = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String name : names)
        {
            IDataTable t = request.getResolver().resolve(name);
            if (t == null)
            {
                missing.add(name);
            }
            else
            {
                sources.add(t);
                sourceNames.add(name);
            }
        }
        if (sources.isEmpty() || !authored.isEmpty())
        {
            // an authored source is declared; of the IG's names only the most specific is listed
            // when none exists — a split dataset without its own SUPP is the ordinary case
            for (String name : authored.isEmpty() ? missing.subList(0, 1) : missing)
            {
                unresolved.add(new UnresolvedDataset(MergeKind.SUPP, name));
            }
        }
        if (sources.isEmpty())
        {
            return Collections.emptySet();
        }
        String suppName = String.join("+", sourceNames);
        // the part's name is what MergeDataTable publishes as MERGED_FROM: the dataset's own name
        StringBuilder partName = new StringBuilder();
        for (IDataTable source : sources)
        {
            partName.append(partName.length() > 0 ? "+" : "").append(KeyedMatcher.nameOf(source));
        }
        IDataTable supp;
        if (sources.size() == 1)
        {
            supp = sources.get(0);
        }
        else
        {
            try
            {
                supp = new UnionDataTable(suppName, sources.toArray(IDataTable[]::new));
            }
            catch (IllegalArgumentException e)
            {
                throw new MergeException(suppName
                        + ": the supplemental datasets cannot be read as one: " + e.getMessage(),
                        e);
            }
        }
        boolean send = request.getStandard()
                .isSend(supp.getMetaData().getColumnIndex("POOLID") >= 0);
        SuppMatcher.Result result = SuppMatcher.match(base, baseDomain, supp, suppName,
                aPending.spec().effectiveKeys(), send, suppIndexObserver);
        DataTableColumnMeta qval = result.qvalIdx() < 0 ? null
                : supp.getMetaData().getColumn(result.qvalIdx());
        List<DataTableColumnMeta> columns = new ArrayList<>();
        @Nullable
        IDataBufferNumeric[] maps = new IDataBufferNumeric[result.columns().size()];
        int i = 0;
        for (SuppMatcher.QnamColumn qc : result.columns())
        {
            String name = claim(MergeKind.SUPP, aPending.qualifier(),
                    naming.suppColumnName(aPending.qualifier(), aPending.primaryBase(), qc.qnam()));
            DataTableColumnMeta.DataTableColumnMetaBuilder b = qval == null
                    ? DataTableColumnMeta.builder()
                            .type(net.cumba.datatable.values.DataValueType.STRING)
                    : DataTableColumnMeta.builderFrom(qval);
            b.index(i).name(name).label(qc.label())
                    .addMetaData(MergeMeta.MERGE_KIND, MergeKind.SUPP.name())
                    .addMetaData(MergeMeta.MERGED_SOURCE_COLUMN, qc.qnam());
            if (qc.error() != null)
            {
                b.addMetaData(MergeMeta.MERGE_ERROR, qc.error());
                issues.add(
                        new MergeIssue(MergeKind.SUPP, suppName, name, qc.error(), qc.errorRows()));
            }
            columns.add(b.build());
            maps[i] = qc.map();
            i++;
        }
        int first = columnsSoFar(aTables);
        aTables.add(new SuppPart(partName.toString(), aRows, supp, result.qvalIdx(),
                aPending.baseMap(), columns, maps));
        aParts.add(new MergedPart(MergeKind.SUPP, aPending.qualifier(), partName.toString(), first,
                columns.size(), -1));
        return result.qnams();
    }
}
