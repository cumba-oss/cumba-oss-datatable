package net.cumba.datatable.impl.merge;

import java.util.ArrayList;
import java.util.Collections;
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
import net.cumba.datatable.impl.view.ViewDataTable;
import net.cumba.datatable.manager.RelrecMergeSpec;
import net.cumba.datatable.merge.Cardinality;
import net.cumba.datatable.merge.CardinalityViolationException;
import net.cumba.datatable.merge.ChildMergeSpec;
import net.cumba.datatable.merge.JoinType;
import net.cumba.datatable.merge.KeyedMergeSpec;
import net.cumba.datatable.merge.MergeIssue;
import net.cumba.datatable.merge.MergeKind;
import net.cumba.datatable.merge.MergeMeta;
import net.cumba.datatable.merge.MergeNaming;
import net.cumba.datatable.merge.MergeRequest;
import net.cumba.datatable.merge.MergedPart;
import net.cumba.datatable.merge.RelrecLinks;
import net.cumba.datatable.merge.RelrecPartSpec;
import net.cumba.datatable.merge.SuppMergeSpec;
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
 * is itself unless some merge expanded rows, in which case it is row-mapped too and
 * {@code getRealRowIndex} still answers its native row.
 * </p>
 */
final class MergeBuilder
{

    private final MergeRequest request;

    private final IDataTable primary;

    private final int primaryRows;

    private final String primaryName;

    private final String domain;

    private final MergeNaming naming;

    private final List<MergeIssue> issues = new ArrayList<>();

    private final List<String> unresolved = new ArrayList<>();

    private final Set<String> usedNames = new HashSet<>();

    /** The primary's own column names, the set a bare naming leaves partner columns out for. */
    private final Set<String> primaryNames = new HashSet<>();

    private final @Nullable Consumer<SuppQnamIndex> suppIndexObserver;

    /** One keyed partner that resolved. */
    private record KeyedUnit(KeyedMergeSpec spec, IDataTable partner, KeyedMatcher matcher)
    {
    }


    /** The RELREC factor: the domains in part order and, per primary row, its tuples. */
    private record RelrecUnit(RelrecPartSpec spec, RelrecPairer.Pairing pairing,
            List<Integer> ordinals, Map<Integer, List<int[]>> tuplesByPrimary)
    {
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
            unresolved.addAll(child.unresolved());
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
        List<List<Integer>> keyedMatches = new ArrayList<>();
        for (int k = 0; k < nKeyed; k++)
        {
            keyedMatches.add(new ArrayList<>());
        }
        List<Integer> childMatches = new ArrayList<>();
        List<int[]> noRelrec = List.of(unsuppliedTuple(nRelrec));
        List<int[]> relrecTuples = noRelrec;
        int size = 0;
        for (int p = 0; p < primaryRows; p++)
        {
            boolean dropped = false;
            for (int k = 0; k < nKeyed && !dropped; k++)
            {
                KeyedUnit u = keyedUnits.get(k);
                List<Integer> m = keyedMatches.get(k);
                u.matcher().matchesOf(p, m);
                dropped = unmatchedHandling(m, u.spec().getJoinType());
            }
            int childOrdinal = -1;
            if (!dropped && child != null && childSpec != null)
            {
                child.matchesOf(p, childMatches);
                childOrdinal = child.ordinalOf(p);
                dropped = unmatchedHandling(childMatches, childSpec.getJoinType());
            }
            if (!dropped && relrec != null)
            {
                List<int[]> t = relrec.tuplesByPrimary().get(p);
                if (t == null)
                {
                    dropped = relrec.spec().getJoinType() == JoinType.INNER;
                    relrecTuples = noRelrec;
                }
                else
                {
                    relrecTuples = t;
                }
            }
            if (dropped)
            {
                continue;
            }
            // The odometer over the factors: keyed lists, the child list, the RELREC tuples.
            int[] counters = new int[nKeyed + 1];
            int relrecCounter = 0;
            while (true)
            {
                primaryMap.addValue(p);
                for (int k = 0; k < nKeyed; k++)
                {
                    keyedMaps[k].addValue(keyedMatches.get(k).get(counters[k]));
                }
                if (child != null)
                {
                    int match = childMatches.get(counters[nKeyed]);
                    for (int c = 0; c < nChild; c++)
                    {
                        childMaps[c].addValue(c == childOrdinal ? match : -1);
                    }
                }
                int[] tuple = relrecTuples.get(relrecCounter);
                for (int d = 0; d < nRelrec; d++)
                {
                    relrecMaps[d].addValue(tuple[d]);
                }
                size++;
                // advance the odometer, RELREC fastest, then the child, then the keyed lists
                if (++relrecCounter < relrecTuples.size())
                {
                    continue;
                }
                relrecCounter = 0;
                int i = nKeyed;
                while (i >= 0)
                {
                    int limit = i == nKeyed ? (child == null ? 1 : childMatches.size())
                            : keyedMatches.get(i).size();
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
        }
        boolean expanded = size != primaryRows;

        // ---- the parts, named
        List<IDataTable> tables = new ArrayList<>();
        List<MergedPart> parts = new ArrayList<>();
        IDataTable primaryPart = expanded
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
            Map<String, RelrecPairer.DomainIssue> issueByDomain = new LinkedHashMap<>();
            for (RelrecPairer.DomainIssue di : relrec.pairing().issues())
            {
                issueByDomain.merge(di.rdomain(), di,
                        (a, b) -> new RelrecPairer.DomainIssue(a.rdomain(),
                                a.message() + "; " + b.message(),
                                concat(a.relrecRows(), b.relrecRows())));
            }
            for (int d = 0; d < nRelrec; d++)
            {
                int ordinal = relrec.ordinals().get(d);
                String rdomain = relrec.pairing().rdomains().get(ordinal);
                RelrecPairer.DomainIssue di = issueByDomain.get(rdomain);
                addMappedPart(tables, parts, MergeKind.RELREC, rdomain,
                        relrec.pairing().relatedTables().get(ordinal), relrecMaps[d], size,
                        di == null ? null
                                : new MergeIssue(MergeKind.RELREC, relrec.spec().getRelrecDataset(),
                                        rdomain, di.message(), di.relrecRows()));
            }
            if (request.isMatchedFlags() && nRelrec > 0)
            {
                IDataBufferNumeric any = DataBufferFactory.get().createForRange(-1, 0);
                any.setExpectedSize(size);
                for (int i = 0; i < size; i++)
                {
                    boolean matched = false;
                    for (int d = 0; d < nRelrec && !matched; d++)
                    {
                        matched = relrecMaps[d].getValueAsLong(i) >= 0;
                    }
                    any.addValue(matched ? 0 : -1);
                }
                String name = claim(MergeKind.RELREC, "RELREC", naming.matchedFlagName("RELREC"));
                tables.add(new MatchedFlagTable(relrec.spec().getRelrecDataset(), name, "RELREC",
                        MergeKind.RELREC, size, any));
                parts.add(
                        new MergedPart(MergeKind.RELREC, "RELREC", relrec.spec().getRelrecDataset(),
                                columnsSoFar(tables) - 1, 1, columnsSoFar(tables) - 1));
            }
        }
        Set<String> suppQnams = new LinkedHashSet<>();
        if (request.getSupp() != null)
        {
            supps.add(0, new SuppPending(primary, primaryName, true, request.getSupp(),
                    expanded ? primaryMap : null));
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
                unresolved, suppQnams, expanded);
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
    private static boolean unmatchedHandling(List<Integer> aMatches, JoinType aJoinType)
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


    private static int[] unsuppliedTuple(int aSize)
    {
        int[] t = new int[aSize];
        java.util.Arrays.fill(t, -1);
        return t;
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
                unresolved.add(spec.datasetName());
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
            unresolved.add(spec.getRelrecDataset());
            return null;
        }
        RelrecMergeSpec selection = spec.getSelection();
        RelrecLinks.Links links = RelrecLinks.collect(relrecTable, domain,
                selectedRdomains(selection));
        RelrecPairer.Pairing pairing = RelrecPairer.resolve(links, primary, request.getResolver());
        unresolved.addAll(pairing.unresolvedDomains());
        List<RelrecPairer.Pair> pairs = filterToSelection(pairing.pairs(), selection);
        checkRelrecCardinality(spec.getCardinality(), pairing, pairs);
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
        }
        int[] positionOfOrdinal = new int[pairing.relatedTables().size()];
        java.util.Arrays.fill(positionOfOrdinal, -1);
        for (int d = 0; d < ordinals.size(); d++)
        {
            positionOfOrdinal[ordinals.get(d)] = d;
        }
        return new RelrecUnit(spec, pairing, ordinals,
                crossProductPerRelationship(pairs, positionOfOrdinal, ordinals.size()));
    }


    /**
     * The merged rows of every primary record with at least one pair: per relationship (in the
     * order the relationships appear in RELREC) the cross product over the related domains of
     * {@code max(1, n)} factors — a domain the relationship does not name, or a dangling link,
     * gives one row with that domain not supplied (P-17); the rows of a record's relationships are
     * summed (OD-4); a row identical in every domain to one already produced for the same record is
     * produced once (a pair shared by two relationships is one row).
     */
    private static Map<Integer, List<int[]>> crossProductPerRelationship(
            List<RelrecPairer.Pair> aPairs, int[] aPositionOfOrdinal, int aDomains)
    {
        Map<Integer, List<int[]>> out = new LinkedHashMap<>();
        int i = 0;
        while (i < aPairs.size())
        {
            int p = aPairs.get(i).primaryRow();
            // relationship -> position -> related rows (ascending, as the pairs are sorted)
            Map<Integer, Map<Integer, List<Integer>>> byRelationship = new java.util.TreeMap<>();
            while (i < aPairs.size() && aPairs.get(i).primaryRow() == p)
            {
                RelrecPairer.Pair pair = aPairs.get(i);
                int pos = aPositionOfOrdinal[pair.ordinal()];
                if (pos >= 0)
                {
                    byRelationship
                            .computeIfAbsent(pair.relationship(), _ -> new java.util.TreeMap<>())
                            .computeIfAbsent(pos, _ -> new ArrayList<>()).add(pair.relatedRow());
                }
                i++;
            }
            List<int[]> tuples = new ArrayList<>();
            Set<List<Integer>> seen = new HashSet<>();
            for (Map<Integer, List<Integer>> perDomain : byRelationship.values())
            {
                List<List<Integer>> factors = new ArrayList<>(aDomains);
                for (int d = 0; d < aDomains; d++)
                {
                    List<Integer> f = perDomain.get(d);
                    factors.add(f == null ? List.of(-1) : f);
                }
                int[] counters = new int[aDomains];
                while (true)
                {
                    int[] tuple = new int[aDomains];
                    List<Integer> key = new ArrayList<>(aDomains);
                    for (int d = 0; d < aDomains; d++)
                    {
                        tuple[d] = factors.get(d).get(counters[d]);
                        key.add(tuple[d]);
                    }
                    if (seen.add(key))
                    {
                        tuples.add(tuple);
                    }
                    int d = aDomains - 1;
                    while (d >= 0 && ++counters[d] == factors.get(d).size())
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
            if (!tuples.isEmpty())
            {
                out.put(p, tuples);
            }
        }
        return out;
    }


    /**
     * The declared cardinality over the distinct (primary record, related record) pairs of each
     * related domain: a side declared {@code 1} pairs each of its records with at most one record
     * of the other side.
     */
    private static void checkRelrecCardinality(Cardinality aCardinality,
            RelrecPairer.Pairing aPairing, List<RelrecPairer.Pair> aPairs)
    {
        if (!aCardinality.leftUnique() && !aCardinality.rightUnique())
        {
            return;
        }
        Map<Long, Integer> firstRelated = new java.util.HashMap<>();
        Map<Long, Integer> firstPrimary = new java.util.HashMap<>();
        for (RelrecPairer.Pair pair : aPairs)
        {
            long primaryKey = ((long) pair.ordinal() << 32) | pair.primaryRow();
            long relatedKey = ((long) pair.ordinal() << 32) | pair.relatedRow();
            String related = KeyedMatcher.nameOf(aPairing.relatedTables().get(pair.ordinal()));
            if (aCardinality.rightUnique())
            {
                Integer first = firstRelated.putIfAbsent(primaryKey, pair.relatedRow());
                if (first != null && first != pair.relatedRow())
                {
                    throw new CardinalityViolationException(aCardinality, related,
                            "(" + pair.rdomain() + " records related to primary record "
                                    + (pair.primaryRow() + 1) + ")",
                            first, pair.relatedRow());
                }
            }
            if (aCardinality.leftUnique())
            {
                Integer first = firstPrimary.putIfAbsent(relatedKey, pair.primaryRow());
                if (first != null && first != pair.primaryRow())
                {
                    throw new CardinalityViolationException(
                            aCardinality, "the primary dataset", "(primary records related to "
                                    + related + " record " + (pair.relatedRow() + 1) + ")",
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


    /** Adds a supplemental part; returns the base's {@code QNAM} set. */
    private Set<String> addSuppPart(List<IDataTable> aTables, List<MergedPart> aParts,
            SuppPending aPending, int aRows)
    {
        IDataTable base = aPending.base();
        String baseDomain = domainOf(base);
        String suppName = aPending.spec().getSuppDataset();
        IDataTable supp;
        if (suppName != null)
        {
            supp = request.getResolver().resolve(suppName);
        }
        else
        {
            suppName = "SUPP" + KeyedMatcher.nameOf(base);
            supp = request.getResolver().resolve(suppName);
            if (supp == null && !baseDomain.equalsIgnoreCase(KeyedMatcher.nameOf(base)))
            {
                supp = request.getResolver().resolve("SUPP" + baseDomain);
                suppName = supp == null ? suppName : "SUPP" + baseDomain;
            }
        }
        if (supp == null)
        {
            unresolved.add(suppName);
            return Collections.emptySet();
        }
        SuppMatcher.Result result = SuppMatcher.match(base, baseDomain, supp, suppName,
                aPending.spec().effectiveKeys(), suppIndexObserver);
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
        aTables.add(new SuppPart(KeyedMatcher.nameOf(supp), aRows, supp, result.qvalIdx(),
                aPending.baseMap(), columns, maps));
        aParts.add(new MergedPart(MergeKind.SUPP, aPending.qualifier(), KeyedMatcher.nameOf(supp),
                first, columns.size(), -1));
        return result.qnams();
    }
}
