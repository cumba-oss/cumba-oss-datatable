package net.cumba.datatable.impl.merge;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntFunction;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.merge.DatasetResolver;
import net.cumba.datatable.merge.JoinToken;
import net.cumba.datatable.merge.RelrecLinks;
import net.cumba.datatable.merge.RelrecLinks.Link;
import net.cumba.datatable.merge.RelrecLinks.SubjectId;
import org.jspecify.annotations.Nullable;

/**
 * Forward RELREC pairing — the one shared implementation ({@code PLAN-merged-evaluation-table}
 * §3.6.1, MET #10): the data browser's {@code RelrecRelationshipResolver}, moved into the datatable
 * layer by W1 (the engine's copy, {@code RelrecRowExpander}, retires in W5). The links come from
 * {@link RelrecLinks}; this class turns them into the matched {@code (primary record, related
 * record)} pairs, tagged with their relationship.
 *
 * <p>
 * The pairing is computed as <b>one hash join per related {@code RDOMAIN}</b>: each table is
 * scanned once to build a per-{@code IDVAR}-column value index keyed by
 * {@code (STUDYID, subject, value)} (record-level and dataset-level alike), and every RELREC link
 * is resolved by index lookup. Each pair is tagged with the relationship that produced it, so the
 * cross product is taken per relationship (OD-4) and a selection can filter by {@code RELID}.
 * </p>
 *
 * <ul>
 * <li><b>Record-level link</b> (both {@code IDVARVAL}s present): each side filtered by
 * {@code IDVAR == IDVARVAL} within the RELREC row's subject, joined on {@code STUDYID}.</li>
 * <li><b>The subject is every identifier the RELREC row populates</b> ({@link Link#srcIdentity()},
 * {@link RelrecLinks#SUBJECT_COLUMNS}: {@code USUBJID}, {@code APID}, {@code POOLID},
 * {@code SPDEVID}; owner ruling G7-R1 (a)): each side is probed on the columns its own row names,
 * so an associated person's records are found by {@code APID}, a device's by {@code SPDEVID}, a
 * device record of a subject by both {@code USUBJID} and {@code SPDEVID}. A row populating none
 * joins within the subject ({@code USUBJID}) — and a table lacking {@code USUBJID} is then a
 * {@link DomainIssue} like a lacking named column (G7 review, PACK G fold), not a silent miss.</li>
 * <li><b>The link value compares by the {@code IDVAR} column's type</b> on each side
 * ({@link JoinToken#normalize}, as the SUPP and Child pointers do, §3.5.2): a numeric column
 * canonicalises ({@code 01}, {@code 1.0} are {@code 1}), a character column compares the stripped
 * text ({@code "01"} is not {@code "1"}; T2 review L1).</li>
 * <li><b>A record "has a relationship"</b> ({@code RELREC._matched_}, P-14) when some RELREC row
 * addresses it — decided on the primary side alone ({@link Pairing#addressed()}), so a related
 * domain that is not supplied (P-1), flagged, or whose record is missing (a dangling link) never
 * empties the record: under {@code inner} it keeps one row with that domain not supplied (T2 review
 * L3).</li>
 * <li><b>Dataset-level link</b>: equi-join on {@code (STUDYID, USUBJID, IDVAR-cell)} (a link whose
 * row names a subject takes the full scan, filtered to it).</li>
 * <li><b>{@code STUDYID} is part of the link identity</b> ({@code PLAN-relrec-studyid-link}):
 * {@code keyCell(primary.STUDYID) == keyCell(related.STUDYID)} on every path, and when the RELREC
 * row names a study both rows are in <i>that</i> study. A blank RELREC {@code STUDYID} names no
 * study.</li>
 * <li><b>SEND</b> (OD-3): a link's subject is every one of {@code USUBJID} and {@code POOLID} its
 * row populates, each side probed on the columns it names, so a relationship may span subjects and
 * pools; pool records never take part in dataset-level relationships.</li>
 * <li>A related domain lacking the {@code IDVAR} column or a subject identifier column RELREC names
 * (or the primary lacking the one the RELREC row names for it) is reported as a {@link DomainIssue}
 * — the merge flags that domain's columns (OD-7) — and those links pair nothing. Before W1 a
 * missing {@code IDVAR} column, and before G7-R1 a missing subject column, was a silent "no
 * pairs".</li>
 * <li>A related domain whose links mix record- and dataset-level joins pairs nothing — only one
 * join shape is supported per {@code (primaryDomain, RDOMAIN)} pair — and is reported as a
 * {@link DomainIssue} (the merge flags its columns, OD-7; W1 review L4: before, an INFO log was the
 * only trace).</li>
 * </ul>
 */
final class RelrecPairer
{

    private static final Logger LOGGER = System.getLogger(RelrecPairer.class.getName());

    private static final String STUDYID = "STUDYID";

    private static final String USUBJID = "USUBJID";

    /**
     * One matched pair.
     *
     * @param primaryRow
     *            the primary row.
     * @param ordinal
     *            the related domain's ordinal in {@link Pairing#relatedTables()}.
     * @param relatedRow
     *            the related row.
     * @param relationship
     *            the relationship's ordinal ({@link Link#relationship()}).
     * @param relid
     *            the {@code RELID} text.
     * @param rdomain
     *            the related domain.
     */
    record Pair(int primaryRow, int ordinal, int relatedRow, int relationship, String relid,
            String rdomain)
    {
    }


    /**
     * A related domain the pairing could not serve correctly.
     *
     * @param rdomain
     *            the related domain.
     * @param message
     *            what is wrong.
     * @param relrecRows
     *            the RELREC rows naming the problem.
     */
    record DomainIssue(String rdomain, String message, List<Long> relrecRows)
    {
    }


    /**
     * The pairing result.
     *
     * @param rdomains
     *            the related domains, by ordinal (first-appearance order; unresolved domains
     *            excluded, a mixed domain included with no pairs and an issue).
     * @param relatedTables
     *            their tables, by ordinal.
     * @param pairs
     *            the pairs, sorted by {@code (primaryRow, ordinal, relatedRow, relationship)}.
     * @param unresolvedDomains
     *            the related domains the resolver did not supply.
     * @param issues
     *            the per-domain problems.
     * @param mixedSkipped
     *            the related domains skipped as mixed.
     * @param addressed
     *            the primary rows some RELREC link addresses — the records having a relationship
     *            (P-14), whether or not a related record was paired.
     */
    record Pairing(List<String> rdomains, List<IDataTable> relatedTables, List<Pair> pairs,
            List<String> unresolvedDomains, List<DomainIssue> issues, List<String> mixedSkipped,
            BitSet addressed)
    {
    }


    /** The de-duplication key of a pair within one relationship. */
    private record PairKey(int primaryRow, int relatedRow, int relationship)
    {
    }

    private RelrecPairer()
    {
    }


    /**
     * Pairs the primary's records with the related domains' records along the links.
     *
     * @param aLinks
     *            the links of the primary domain.
     * @param aPrimary
     *            the primary dataset.
     * @param aResolver
     *            the resolver for the related domains.
     * @return the pairing.
     */
    static Pairing resolve(RelrecLinks.Links aLinks, IDataTable aPrimary, DatasetResolver aResolver)
    {
        List<String> rdomains = new ArrayList<>();
        List<IDataTable> relatedTables = new ArrayList<>();
        List<Pair> pairs = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();
        List<DomainIssue> issues = new ArrayList<>();
        BitSet addressed = new BitSet(Math.toIntExact(aPrimary.getRowCount()));
        TableIndex primaryIndex = new TableIndex(aPrimary);
        for (RelrecLinks.DomainLinks e : aLinks.domains())
        {
            String rdomain = e.rdomain();
            for (Link link : e.links())
            {
                addressPrimary(link, aPrimary, primaryIndex, addressed);
            }
            IDataTable target = aResolver.resolve(rdomain);
            if (target == null)
            {
                unresolved.add(rdomain);
                continue;
            }
            int ordinal = relatedTables.size();
            rdomains.add(rdomain);
            relatedTables.add(target);
            if (aLinks.mixedDomains().contains(rdomain))
            {
                LOGGER.log(Level.INFO,
                        "RELREC relationship for {0} mixes record- and dataset-level links; skipped.",
                        rdomain);
                Set<Long> distinct = new LinkedHashSet<>();
                for (Link link : e.links())
                {
                    distinct.add(link.rightRow());
                }
                List<Long> rows = new ArrayList<>(distinct);
                issues.add(new DomainIssue(rdomain, "RELREC links " + rdomain
                        + " both record-level and dataset-level; only one join shape is supported"
                        + " per related domain, so nothing of " + rdomain
                        + " is merged (RELREC rows " + rowList(rows) + ")", rows));
                continue;
            }
            joinDomain(rdomain, e.links(), aPrimary, primaryIndex, target, ordinal, pairs, issues);
        }
        pairs.sort(Comparator.comparingInt(Pair::primaryRow).thenComparingInt(Pair::ordinal)
                .thenComparingInt(Pair::relatedRow).thenComparingInt(Pair::relationship));
        return new Pairing(List.copyOf(rdomains), List.copyOf(relatedTables), List.copyOf(pairs),
                List.copyOf(unresolved), List.copyOf(issues), aLinks.mixedDomains(), addressed);
    }


    /**
     * Marks the primary rows one link addresses — on the primary side alone, so the answer does not
     * depend on the related domain being supplied or its record existing: a record-level link
     * addresses the records with {@code IDVAR == IDVARVAL} in the link's subject (and study, when
     * named); a dataset-level link every record carrying a value in its {@code IDVAR} column (in
     * the study, when named). The same filters as the join's own primary side.
     */
    private static void addressPrimary(Link aLink, IDataTable aPrimary, TableIndex aPrimaryIndex,
            BitSet aAddressed)
    {
        DataTableMeta pm = aPrimary.getMetaData();
        int pIdvarIdx = pm.getColumnIndex(aLink.srcIdvar());
        if (pIdvarIdx < 0)
        {
            return;
        }
        boolean scan = aLink.recordLevel()
                ? aLink.srcIdentity().isEmpty() || !aPrimaryIndex.hasSubject(aLink.srcIdentity())
                : !aLink.srcIdentity().isEmpty();
        if (scan)
        {
            int pStudyIdx = pm.getColumnIndex(STUDYID);
            int[] pSubjIdx = subjectColumns(pm, aLink.srcIdentity());
            if (pSubjIdx == null)
            {
                return;
            }
            boolean numeric = aPrimaryIndex.isNumeric(aLink.srcIdvar());
            String srcNorm = JoinToken.normalize(aLink.srcIdvarval(), numeric);
            long pRows = aPrimary.getRowCount();
            for (long r = 0; r < pRows; r++)
            {
                if (scanKey(aLink, aPrimary, pStudyIdx, pSubjIdx, pIdvarIdx, r, srcNorm, numeric,
                        aLink.srcIdentity()) != null)
                {
                    aAddressed.set((int) r);
                }
            }
            return;
        }
        KeyRows pIndex = aPrimaryIndex.byColumn(aLink.srcIdvar(), aLink.srcIdentity());
        if (aLink.recordLevel())
        {
            String pValue = Objects.requireNonNull(JoinToken.normalize(aLink.srcIdvarval(),
                    aPrimaryIndex.isNumeric(aLink.srcIdvar())));
            Object pSubject = identityKey(aLink.srcIdentity());
            if (RelrecLinks.isNonBlank(aLink.study()))
            {
                markRows(pIndex, pIndex.find(new RelrecKey(aLink.studyKey(), pSubject, pValue)),
                        aAddressed);
                return;
            }
            for (Object study : pIndex.studies())
            {
                markRows(pIndex, pIndex.find(new RelrecKey(study, pSubject, pValue)), aAddressed);
            }
            return;
        }
        if (RelrecLinks.isNonBlank(aLink.study()))
        {
            int study = pIndex.studyOrdinal(aLink.studyKey());
            if (study == KeyRows.NOT_FOUND)
            {
                return;
            }
            int end = pIndex.studyKeyEnd(study);
            for (int pos = pIndex.studyKeyStart(study); pos < end; pos++)
            {
                markRows(pIndex, pIndex.studyKeyId(pos), aAddressed);
            }
            return;
        }
        for (int id = 0; id < pIndex.keyCount(); id++)
        {
            markRows(pIndex, id, aAddressed);
        }
    }


    /** Marks every row of key {@code aId} ({@code NOT_FOUND} marks nothing). */
    private static void markRows(KeyRows aIndex, int aId, BitSet aAddressed)
    {
        if (aId == KeyRows.NOT_FOUND)
        {
            return;
        }
        int end = aIndex.end(aId);
        for (int pos = aIndex.start(aId); pos < end; pos++)
        {
            aAddressed.set(aIndex.row(pos));
        }
    }


    @SuppressWarnings("checkstyle:ParameterNumber")
    private static void joinDomain(String aRdomain, List<Link> aLinks, IDataTable aPrimary,
            TableIndex aPrimaryIndex, IDataTable aTarget, int aOrdinal, List<Pair> aOut,
            List<DomainIssue> aIssues)
    {
        TableIndex targetIndex = new TableIndex(aTarget);
        Set<PairKey> seen = new HashSet<>();
        Map<String, List<Long>> targetLacks = new LinkedHashMap<>();
        Map<String, List<Long>> primaryLacks = new LinkedHashMap<>();
        DataTableMeta tm = aTarget.getMetaData();
        DataTableMeta pm = aPrimary.getMetaData();
        Map<String, List<Long>> targetLacksSubject = new LinkedHashMap<>();
        Map<String, List<Long>> primaryLacksSubject = new LinkedHashMap<>();
        for (Link link : aLinks)
        {
            // OD-7: the IDVAR column and every subject identifier column the row names (G7-R1).
            // A row naming none joins within the subject, so USUBJID is then the column it needs:
            // a table without it was a silent miss (G7 review, PACK G fold).
            boolean usable = hasColumn(tm, link.tgtIdvar(), link.rightRow(), targetLacks);
            for (SubjectId id : link.tgtIdentity())
            {
                usable &= hasColumn(tm, id.column(), link.rightRow(), targetLacks);
            }
            if (link.tgtIdentity().isEmpty())
            {
                usable &= hasColumn(tm, USUBJID, link.rightRow(), targetLacksSubject);
            }
            usable &= hasColumn(pm, link.srcIdvar(), link.leftRow(), primaryLacks);
            for (SubjectId id : link.srcIdentity())
            {
                usable &= hasColumn(pm, id.column(), link.leftRow(), primaryLacks);
            }
            if (link.srcIdentity().isEmpty())
            {
                usable &= hasColumn(pm, USUBJID, link.leftRow(), primaryLacksSubject);
            }
            if (!usable)
            {
                continue;
            }
            if (link.recordLevel())
            {
                joinRecordLevel(link, aOrdinal, aPrimary, aTarget, aPrimaryIndex, targetIndex, aOut,
                        seen);
            }
            else
            {
                joinDatasetLevel(link, aOrdinal, aPrimary, aTarget, aPrimaryIndex, targetIndex,
                        aOut, seen);
            }
        }
        for (Map.Entry<String, List<Long>> e : targetLacks.entrySet())
        {
            aIssues.add(new DomainIssue(aRdomain,
                    "RELREC names column " + e.getKey() + " of related domain " + aRdomain
                            + ", which " + KeyedMatcher.nameOf(aTarget)
                            + " does not have (RELREC rows " + rowList(e.getValue()) + ")",
                    e.getValue()));
        }
        for (Map.Entry<String, List<Long>> e : primaryLacks.entrySet())
        {
            aIssues.add(new DomainIssue(aRdomain, "RELREC names column " + e.getKey() + " of "
                    + KeyedMatcher.nameOf(aPrimary) + " for its relationship with " + aRdomain
                    + ", which it does not have (RELREC rows " + rowList(e.getValue()) + ")",
                    e.getValue()));
        }
        for (Map.Entry<String, List<Long>> e : targetLacksSubject.entrySet())
        {
            aIssues.add(new DomainIssue(aRdomain,
                    "RELREC names no subject identifier for related" + " domain " + aRdomain
                            + ", so the link joins within " + e.getKey() + ", which "
                            + KeyedMatcher.nameOf(aTarget) + " does not have (RELREC rows "
                            + rowList(e.getValue()) + ")",
                    e.getValue()));
        }
        for (Map.Entry<String, List<Long>> e : primaryLacksSubject.entrySet())
        {
            aIssues.add(new DomainIssue(aRdomain, "RELREC names no subject identifier for "
                    + KeyedMatcher.nameOf(aPrimary) + " in its relationship with " + aRdomain
                    + ", so the link joins within " + e.getKey() + ", which it does not have"
                    + " (RELREC rows " + rowList(e.getValue()) + ")", e.getValue()));
        }
    }


    /**
     * Whether {@code aMeta} has {@code aColumn}; when it does not, records the RELREC row under
     * that column in {@code aLacks}.
     */
    private static boolean hasColumn(DataTableMeta aMeta, String aColumn, long aRelrecRow,
            Map<String, List<Long>> aLacks)
    {
        if (aMeta.getColumnIndex(aColumn) >= 0)
        {
            return true;
        }
        aLacks.computeIfAbsent(aColumn, _ -> new ArrayList<>()).add(aRelrecRow);
        return false;
    }


    /**
     * The table's columns probed for a side's subject: each identifier the RELREC row populates, or
     * {@code USUBJID} when it populates none (the join within the subject); {@code null} when the
     * table lacks one of them.
     */
    private static int @Nullable [] subjectColumns(DataTableMeta aMeta, List<SubjectId> aIdentity)
    {
        if (aIdentity.isEmpty())
        {
            int c = aMeta.getColumnIndex(USUBJID);
            return c < 0 ? null : new int[]
            {
                    c
            };
        }
        int[] cols = new int[aIdentity.size()];
        for (int i = 0; i < cols.length; i++)
        {
            cols[i] = aMeta.getColumnIndex(aIdentity.get(i).column());
            if (cols[i] < 0)
            {
                return null;
            }
        }
        return cols;
    }


    /**
     * The subject half of a {@link RelrecKey} for a side's identity: the one key when the row names
     * one identifier, the list of keys when it names several (a device record of a subject).
     */
    private static Object identityKey(List<SubjectId> aIdentity)
    {
        if (aIdentity.size() == 1)
        {
            return aIdentity.get(0).key();
        }
        List<Object> keys = new ArrayList<>(aIdentity.size());
        for (SubjectId id : aIdentity)
        {
            keys.add(id.key());
        }
        return List.copyOf(keys);
    }


    /** The subject half of a table row's {@link RelrecKey} over the given subject columns. */
    private static Object rowSubjectKey(IDataTable aTable, int[] aSubjIdx, long aRow)
    {
        if (aSubjIdx.length == 1)
        {
            return RelrecLinks.keyCell(aTable, aSubjIdx[0], aRow);
        }
        List<Object> keys = new ArrayList<>(aSubjIdx.length);
        for (int c : aSubjIdx)
        {
            keys.add(RelrecLinks.keyCell(aTable, c, aRow));
        }
        return List.copyOf(keys);
    }


    private static String rowList(List<Long> aRows)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < aRows.size() && i < 20; i++)
        {
            sb.append(i > 0 ? ", " : "").append(aRows.get(i) + 1);
        }
        return aRows.size() > 20 ? sb.append(", ...").toString() : sb.toString();
    }


    /**
     * Record-level link (both IDVARVALs present): each side filtered by {@code IDVAR == IDVARVAL}
     * within the RELREC row's subject, joined on {@code STUDYID}. The probe is
     * {@code (STUDYID, subject, normalize(IDVARVAL))}, key-identified exactly like the index, in
     * the RELREC row's study when one is named and in each study the primary index holds otherwise.
     * A side whose RELREC row names no subject (or a table lacking a subject column) takes the full
     * scan.
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    private static void joinRecordLevel(Link aLink, int aOrdinal, IDataTable aPrimary,
            IDataTable aTarget, TableIndex aPrimaryIndex, TableIndex aTargetIndex, List<Pair> aOut,
            Set<PairKey> aSeen)
    {
        if (aLink.srcIdentity().isEmpty() || aLink.tgtIdentity().isEmpty()
                || !aPrimaryIndex.hasSubject(aLink.srcIdentity())
                || !aTargetIndex.hasSubject(aLink.tgtIdentity()))
        {
            joinByScan(aLink, aOrdinal, aPrimary, aTarget, aOut, aSeen);
            return;
        }
        String pValue = Objects.requireNonNull(JoinToken.normalize(aLink.srcIdvarval(),
                aPrimaryIndex.isNumeric(aLink.srcIdvar())));
        String tValue = Objects.requireNonNull(
                JoinToken.normalize(aLink.tgtIdvarval(), aTargetIndex.isNumeric(aLink.tgtIdvar())));
        KeyRows pIndex = aPrimaryIndex.byColumn(aLink.srcIdvar(), aLink.srcIdentity());
        KeyRows tIndex = aTargetIndex.byColumn(aLink.tgtIdvar(), aLink.tgtIdentity());
        Object pSubject = identityKey(aLink.srcIdentity());
        Object tSubject = identityKey(aLink.tgtIdentity());
        if (RelrecLinks.isNonBlank(aLink.study()))
        {
            emitCross(pIndex, pIndex.find(new RelrecKey(aLink.studyKey(), pSubject, pValue)),
                    tIndex, tIndex.find(new RelrecKey(aLink.studyKey(), tSubject, tValue)),
                    aOrdinal, aLink, aOut, aSeen);
            return;
        }
        for (Object study : pIndex.studies())
        {
            emitCross(pIndex, pIndex.find(new RelrecKey(study, pSubject, pValue)), tIndex,
                    tIndex.find(new RelrecKey(study, tSubject, tValue)), aOrdinal, aLink, aOut,
                    aSeen);
        }
    }


    /**
     * Dataset-level link (either IDVARVAL blank): equi-join on
     * {@code (STUDYID, USUBJID, normKey(IDVAR-cell))} — every primary key in the RELREC row's study
     * when a study is named, every primary key otherwise. A (rare) subject-scoped dataset-level
     * link takes the full scan.
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    private static void joinDatasetLevel(Link aLink, int aOrdinal, IDataTable aPrimary,
            IDataTable aTarget, TableIndex aPrimaryIndex, TableIndex aTargetIndex, List<Pair> aOut,
            Set<PairKey> aSeen)
    {
        if (!aLink.srcIdentity().isEmpty())
        {
            joinByScan(aLink, aOrdinal, aPrimary, aTarget, aOut, aSeen);
            return;
        }
        KeyRows pIndex = aPrimaryIndex.byColumn(aLink.srcIdvar(), List.of());
        KeyRows tIndex = aTargetIndex.byColumn(aLink.tgtIdvar(), List.of());
        if (RelrecLinks.isNonBlank(aLink.study()))
        {
            int study = pIndex.studyOrdinal(aLink.studyKey());
            if (study == KeyRows.NOT_FOUND
                    || tIndex.studyOrdinal(aLink.studyKey()) == KeyRows.NOT_FOUND)
            {
                return;
            }
            int end = pIndex.studyKeyEnd(study);
            for (int pos = pIndex.studyKeyStart(study); pos < end; pos++)
            {
                int id = pIndex.studyKeyId(pos);
                emitCross(pIndex, id, tIndex, tIndex.find(pIndex.key(id)), aOrdinal, aLink, aOut,
                        aSeen);
            }
            return;
        }
        for (int id = 0; id < pIndex.keyCount(); id++)
        {
            emitCross(pIndex, id, tIndex, tIndex.find(pIndex.key(id)), aOrdinal, aLink, aOut,
                    aSeen);
        }
    }


    /** Every pairing of key {@code aPrimaryId}'s rows with key {@code aTargetId}'s rows. */
    @SuppressWarnings("checkstyle:ParameterNumber")
    private static void emitCross(KeyRows aPrimary, int aPrimaryId, KeyRows aTarget, int aTargetId,
            int aOrdinal, Link aLink, List<Pair> aOut, Set<PairKey> aSeen)
    {
        if (aPrimaryId == KeyRows.NOT_FOUND || aTargetId == KeyRows.NOT_FOUND)
        {
            return;
        }
        int primaryEnd = aPrimary.end(aPrimaryId);
        int targetEnd = aTarget.end(aTargetId);
        for (int i = aPrimary.start(aPrimaryId); i < primaryEnd; i++)
        {
            int pr = aPrimary.row(i);
            for (int j = aTarget.start(aTargetId); j < targetEnd; j++)
            {
                int tr = aTarget.row(j);
                emit(pr, tr, aOrdinal, aLink, aOut, aSeen);
            }
        }
    }


    private static void emit(int aPr, int aTr, int aOrdinal, Link aLink, List<Pair> aOut,
            Set<PairKey> aSeen)
    {
        if (aSeen.add(new PairKey(aPr, aTr, aLink.relationship())))
        {
            aOut.add(new Pair(aPr, aOrdinal, aTr, aLink.relationship(), aLink.relid(),
                    aLink.rdomain()));
        }
    }


    /**
     * Faithful full-scan join for one link, used for the rare cases the indexed fast path does not
     * cover (record-level with a blank RELREC subject, or a subject-scoped dataset-level link).
     * Record-level: filter each side by {@code IDVAR == IDVARVAL}, join on
     * {@code (STUDYID, subject)}; dataset-level: join on {@code (STUDYID, subject,
     * normKey(IDVAR-cell))}. When the RELREC row names a study, both sides are filtered to it.
     */
    private static void joinByScan(Link aLink, int aOrdinal, IDataTable aPrimary,
            IDataTable aTarget, List<Pair> aOut, Set<PairKey> aSeen)
    {
        DataTableMeta tm = aTarget.getMetaData();
        int tStudyIdx = tm.getColumnIndex(STUDYID);
        int[] tSubjIdx = subjectColumns(tm, aLink.tgtIdentity());
        int tIdvarIdx = tm.getColumnIndex(aLink.tgtIdvar());
        if (tSubjIdx == null || tIdvarIdx < 0)
        {
            return;
        }
        boolean tNumeric = ColumnKind.isNumeric(tm.getColumn(tIdvarIdx).getType());
        String tgtNorm = JoinToken.normalize(aLink.tgtIdvarval(), tNumeric);
        KeyRows targetIndex = KeyRows.buildSparse(Math.toIntExact(aTarget.getRowCount()),
                r -> scanKey(aLink, aTarget, tStudyIdx, tSubjIdx, tIdvarIdx, r, tgtNorm, tNumeric,
                        aLink.tgtIdentity()));

        DataTableMeta pm = aPrimary.getMetaData();
        int pStudyIdx = pm.getColumnIndex(STUDYID);
        int[] pSubjIdx = subjectColumns(pm, aLink.srcIdentity());
        int pIdvarIdx = pm.getColumnIndex(aLink.srcIdvar());
        if (pSubjIdx == null || pIdvarIdx < 0)
        {
            return;
        }
        boolean pNumeric = ColumnKind.isNumeric(pm.getColumn(pIdvarIdx).getType());
        String srcNorm = JoinToken.normalize(aLink.srcIdvarval(), pNumeric);
        long pRows = aPrimary.getRowCount();
        for (long r = 0; r < pRows; r++)
        {
            RelrecKey key = scanKey(aLink, aPrimary, pStudyIdx, pSubjIdx, pIdvarIdx, r, srcNorm,
                    pNumeric, aLink.srcIdentity());
            int id = key == null ? KeyRows.NOT_FOUND : targetIndex.find(key);
            if (id == KeyRows.NOT_FOUND)
            {
                continue;
            }
            int end = targetIndex.end(id);
            for (int pos = targetIndex.start(id); pos < end; pos++)
            {
                emit((int) r, targetIndex.row(pos), aOrdinal, aLink, aOut, aSeen);
            }
        }
    }


    /**
     * One scanned row's key, or {@code null} when the row does not take part: classified on the RAW
     * RELREC values, then filtered on the key identities. A record-level link keys on
     * {@code (STUDYID, subject)} alone — its value is the {@code IDVAR == IDVARVAL} filter — and a
     * dataset-level link on {@code (STUDYID, subject, normalize(IDVAR-cell))}. ⚠ The identity is
     * the link's SIDE for this table: in SDTM both sides share the relationship's subject but may
     * name more identifiers (a device record of a subject), under SEND the sides may name different
     * subjects or pools (OD-3). {@code aSubjIdx} holds the identity's columns, or {@code USUBJID}
     * alone when the identity is empty.
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    private static @Nullable RelrecKey scanKey(Link aLink, IDataTable aTable, int aStudyIdx,
            int[] aSubjIdx, int aIdvarIdx, long aRow, @Nullable String aIdvarFilter,
            boolean aNumeric, List<SubjectId> aIdentity)
    {
        String idv = RelrecLinks.cell(aTable, aIdvarIdx, aRow);
        if (idv == null)
        {
            return null;
        }
        String idvNorm = Objects.requireNonNull(JoinToken.normalize(idv, aNumeric));
        if (aLink.recordLevel() && !idvNorm.equals(aIdvarFilter))
        {
            return null;
        }
        Object study = RelrecLinks.keyCell(aTable, aStudyIdx, aRow);
        if (RelrecLinks.isNonBlank(aLink.study()) && !study.equals(aLink.studyKey()))
        {
            return null;
        }
        if (aIdentity.isEmpty())
        {
            // No subject named: the join is within the subject, the row's own USUBJID.
            return new RelrecKey(study, RelrecLinks.keyCell(aTable, aSubjIdx[0], aRow),
                    aLink.recordLevel() ? "" : idvNorm);
        }
        for (int i = 0; i < aSubjIdx.length; i++)
        {
            if (!RelrecLinks.keyCell(aTable, aSubjIdx[i], aRow).equals(aIdentity.get(i).key()))
            {
                return null;
            }
        }
        // The key's subject half is empty when the side names its subject, so the two sides of a
        // link meet on one key (each side filtered to its own identity: a SEND cross-subject
        // link, or a subject's record related to a device record of that subject).
        return new RelrecKey(study, "", aLink.recordLevel() ? "" : idvNorm);
    }

    // ---- table index ----

    /**
     * Lazily builds, per {@code (IDVAR column, subject columns)}, one {@link KeyRows} index for one
     * table (rows with a {@code null} value cell are skipped), built once and reused across every
     * link of the related domain. One keying serves both join modes: the key is
     * {@code (keyCell(STUDYID), subject key, normalize(cell))} — the subject key the one subject
     * column's {@code keyCell}, or the list of them over several — so a link that names a study
     * probes that study's keys and a link that names none probes each study the index holds, every
     * key finding only its own study on the other side.
     */
    private static final class TableIndex
    {

        private final IDataTable table;

        private final int studyIdx;

        private final Map<String, KeyRows> byColumn = new HashMap<>();

        TableIndex(IDataTable aTable)
        {
            table = aTable;
            studyIdx = aTable.getMetaData().getColumnIndex(STUDYID);
        }


        /** Whether the table carries every subject column of a side's identity. */
        boolean hasSubject(List<SubjectId> aIdentity)
        {
            return subjectColumns(table.getMetaData(), aIdentity) != null;
        }


        /** Whether the table's column is numeric — the type the link value is normalised by. */
        boolean isNumeric(String aColumn)
        {
            DataTableMeta m = table.getMetaData();
            int c = m.getColumnIndex(aColumn);
            return c >= 0 && ColumnKind.isNumeric(m.getColumn(c).getType());
        }


        /**
         * {@code (keyCell(STUDYID), subject key, normalize(cell)) → rows}, the subject columns
         * those of {@code aIdentity} ({@code USUBJID} when it is empty).
         */
        KeyRows byColumn(String aColumn, List<SubjectId> aIdentity)
        {
            StringBuilder key = new StringBuilder();
            if (aIdentity.isEmpty())
            {
                key.append(USUBJID).append('\0');
            }
            for (SubjectId id : aIdentity)
            {
                key.append(id.column()).append('\0');
            }
            return byColumn.computeIfAbsent(key.append('\0').append(aColumn).toString(),
                    _ -> build(aColumn, subjectColumns(table.getMetaData(), aIdentity)));
        }


        private KeyRows build(String aColumn, int @Nullable [] aSubjIdx)
        {
            int colIdx = table.getMetaData().getColumnIndex(aColumn);
            if (colIdx < 0 || aSubjIdx == null)
            {
                return KeyRows.build(0, _ -> null);
            }
            boolean numeric = ColumnKind.isNumeric(table.getMetaData().getColumn(colIdx).getType());
            return KeyRows.build(Math.toIntExact(table.getRowCount()),
                    r -> indexKey(colIdx, aSubjIdx, numeric, r));
        }


        private @Nullable RelrecKey indexKey(int aColIdx, int[] aSubjIdx, boolean aNumeric,
                long aRow)
        {
            String v = RelrecLinks.cell(table, aColIdx, aRow);
            if (v == null)
            {
                return null;
            }
            return new RelrecKey(RelrecLinks.keyCell(table, studyIdx, aRow),
                    rowSubjectKey(table, aSubjIdx, aRow),
                    Objects.requireNonNull(JoinToken.normalize(v, aNumeric)));
        }
    }


    /**
     * One RELREC key: the {@code STUDYID} and subject key identities (a {@code String} or a
     * {@code MissingValue} constant; the subject a list of them over several subject columns) and
     * one text component — the normalised link value in a {@link KeyRows} index, {@code ""} in a
     * record-level scan. Equality is component-wise. A class and not a record: the hash is computed
     * once and compared first, measured to matter on long USUBJIDs sharing a prefix.
     */
    static final class RelrecKey
    {

        private final Object study;

        private final Object subject;

        private final String value;

        private final int hash;

        RelrecKey(Object aStudy, Object aSubject, String aValue)
        {
            study = aStudy;
            subject = aSubject;
            value = aValue;
            hash = (31 * aStudy.hashCode() + aSubject.hashCode()) * 31 + aValue.hashCode();
        }


        /** Returns the {@code STUDYID} key identity. */
        Object study()
        {
            return study;
        }


        /** Returns the subject key identity. */
        Object subject()
        {
            return subject;
        }


        /** Returns the text component. */
        String value()
        {
            return value;
        }


        @Override
        public boolean equals(@Nullable Object aOther)
        {
            return aOther instanceof RelrecKey key && key.hash == hash && key.study.equals(study)
                    && key.subject.equals(subject) && key.value.equals(value);
        }


        @Override
        public int hashCode()
        {
            return hash;
        }


        @Override
        public String toString()
        {
            return "(" + study + ", " + subject + ", " + value + ")";
        }
    }


    /**
     * A table's {@link RelrecKey}s and, per key, the rows carrying it, ascending — the
     * {@code KeyMatchIndex} shape: an open-addressing table maps a key to a dense key id; the rows
     * are CSR postings; the keys are also kept by id, so a dataset-level join walks them
     * deterministically; the distinct {@code STUDYID} identities are kept in first-appearance order
     * for a record-level link that names no study; and the key ids are laid out by study (a second
     * CSR), so a link that names a study reads that study's keys only. Immutable once built;
     * confined to the pairing that built it.
     */
    static final class KeyRows
    {

        /** Returned by {@link #find} and {@link #studyOrdinal} for a key or study not carried. */
        static final int NOT_FOUND = -1;

        private static final int MIN_CAPACITY = 16;

        private final @Nullable RelrecKey[] slotKeys;

        private final int[] slotIds;

        private final int slotMask;

        private final RelrecKey[] keys;

        private final int[] offsets;

        private final int[] rows;

        private final List<Object> studies;

        private final Map<Object, Integer> studyOrdinals;

        private final int[] studyOffsets;

        private final int[] studyKeyIds;

        @SuppressWarnings("checkstyle:ParameterNumber")
        private KeyRows(@Nullable RelrecKey[] aSlotKeys, int[] aSlotIds, RelrecKey[] aKeys,
                int[] aOffsets, int[] aRows, Map<Object, Integer> aStudyOrdinals,
                List<Object> aStudies, int[] aStudyOffsets, int[] aStudyKeyIds)
        {
            slotKeys = aSlotKeys;
            slotIds = aSlotIds;
            slotMask = aSlotKeys.length - 1;
            keys = aKeys;
            offsets = aOffsets;
            rows = aRows;
            studyOrdinals = aStudyOrdinals;
            studies = aStudies;
            studyOffsets = aStudyOffsets;
            studyKeyIds = aStudyKeyIds;
        }


        /**
         * Builds the index over a table most of whose rows take part: the first pass computes each
         * row's key once, assigns key ids and counts the rows per key; the second lays the rows out
         * by key.
         *
         * @param aRowCount
         *            the table's row count.
         * @param aKeyOfRow
         *            the key of a row, or {@code null} when the row does not take part; called
         *            exactly once per row, in ascending row order.
         * @return the index, never {@code null}.
         */
        static KeyRows build(int aRowCount, IntFunction<@Nullable RelrecKey> aKeyOfRow)
        {
            Builder table = new Builder();
            int[] rowKey = new int[aRowCount];
            for (int r = 0; r < aRowCount; r++)
            {
                RelrecKey key = aKeyOfRow.apply(r);
                rowKey[r] = key == null ? NOT_FOUND : table.idOf(key);
            }
            return table.finish(rowKey, null, aRowCount);
        }


        /**
         * As {@link #build}, for a table most of whose rows do <b>not</b> take part — the full-scan
         * fallback. Its temporaries are proportional to the participating rows.
         *
         * @param aRowCount
         *            the table's row count.
         * @param aKeyOfRow
         *            the key of a row, or {@code null} when the row does not take part.
         * @return the index, never {@code null}.
         */
        static KeyRows buildSparse(int aRowCount, IntFunction<@Nullable RelrecKey> aKeyOfRow)
        {
            Builder table = new Builder();
            int[] entryIds = new int[MIN_CAPACITY];
            int[] entryRows = new int[MIN_CAPACITY];
            int entries = 0;
            for (int r = 0; r < aRowCount; r++)
            {
                RelrecKey key = aKeyOfRow.apply(r);
                if (key == null)
                {
                    continue;
                }
                if (entries == entryIds.length)
                {
                    entryIds = Arrays.copyOf(entryIds, entries * 2);
                    entryRows = Arrays.copyOf(entryRows, entries * 2);
                }
                entryIds[entries] = table.idOf(key);
                entryRows[entries] = r;
                entries++;
            }
            return table.finish(entryIds, entryRows, entries);
        }


        /** The id of {@code aKey}, or {@link #NOT_FOUND}. */
        int find(RelrecKey aKey)
        {
            for (int slot = spread(aKey.hashCode()) & slotMask;; slot = (slot + 1) & slotMask)
            {
                RelrecKey stored = slotKeys[slot];
                if (stored == null)
                {
                    return NOT_FOUND;
                }
                if (stored.equals(aKey))
                {
                    return slotIds[slot];
                }
            }
        }


        /** Returns the number of distinct keys; ids run {@code 0 .. keyCount() - 1}. */
        int keyCount()
        {
            return keys.length;
        }


        /** Returns key {@code aId}. */
        RelrecKey key(int aId)
        {
            return keys[aId];
        }


        /** Returns the first position of key {@code aId}'s slice, for {@link #row}. */
        int start(int aId)
        {
            return offsets[aId];
        }


        /** Returns one past the last position of key {@code aId}'s slice. */
        int end(int aId)
        {
            return offsets[aId + 1];
        }


        /** Returns the row at slice position {@code aPos}. */
        int row(int aPos)
        {
            return rows[aPos];
        }


        /** Returns the distinct {@code STUDYID} key identities, in first-appearance order. */
        List<Object> studies()
        {
            return studies;
        }


        /** The ordinal of a {@code STUDYID} key identity, or {@link #NOT_FOUND}. */
        int studyOrdinal(Object aStudy)
        {
            Integer ordinal = studyOrdinals.get(aStudy);
            return ordinal == null ? NOT_FOUND : ordinal;
        }


        /** Returns the first position of study {@code aOrdinal}'s key ids. */
        int studyKeyStart(int aOrdinal)
        {
            return studyOffsets[aOrdinal];
        }


        /** Returns one past the last position of study {@code aOrdinal}'s key ids. */
        int studyKeyEnd(int aOrdinal)
        {
            return studyOffsets[aOrdinal + 1];
        }


        /** Returns the key id at study-slice position {@code aPos}; ascending within a study. */
        int studyKeyId(int aPos)
        {
            return studyKeyIds[aPos];
        }


        private static int spread(int aHash)
        {
            int h = aHash * 0x9E3779B9;
            return h ^ (h >>> 16);
        }

        /**
         * The growing key table used while building; its slot and id arrays become the index's, the
         * rest is dropped. It keeps each slot's hash in a parallel array only while building, so a
         * colliding slot is rejected, and a key re-placed, without touching the key.
         */
        private static final class Builder
        {

            private @Nullable RelrecKey[] keys = new RelrecKey[MIN_CAPACITY];

            private int[] ids = new int[MIN_CAPACITY];

            private int[] hashes = new int[MIN_CAPACITY];

            private RelrecKey[] byId = new RelrecKey[MIN_CAPACITY];

            private int[] counts = new int[MIN_CAPACITY];

            private int[] keyStudy = new int[MIN_CAPACITY];

            private final Map<Object, Integer> studyOrdinals = new LinkedHashMap<>();

            private int size;

            /** The key's id, assigning the next one on first sight, and counts one row for it. */
            int idOf(RelrecKey aKey)
            {
                int hash = aKey.hashCode();
                int mask = keys.length - 1;
                int slot = spread(hash) & mask;
                for (RelrecKey stored = keys[slot]; stored != null; stored = keys[slot])
                {
                    if (hashes[slot] == hash && stored.equals(aKey))
                    {
                        counts[ids[slot]]++;
                        return ids[slot];
                    }
                    slot = (slot + 1) & mask;
                }
                int id = size++;
                keys[slot] = aKey;
                ids[slot] = id;
                hashes[slot] = hash;
                if (id == byId.length)
                {
                    byId = Arrays.copyOf(byId, id * 2);
                    counts = Arrays.copyOf(counts, id * 2);
                    keyStudy = Arrays.copyOf(keyStudy, id * 2);
                }
                byId[id] = aKey;
                counts[id] = 1;
                Integer study = studyOrdinals.get(aKey.study());
                if (study == null)
                {
                    study = studyOrdinals.size();
                    studyOrdinals.put(aKey.study(), study);
                }
                keyStudy[id] = study;
                if (size * 2 > keys.length)
                {
                    grow();
                }
                return id;
            }


            /**
             * The finished index: the rows laid out by key id (CSR), and the key ids laid out by
             * study (a second CSR).
             *
             * @param aEntryIds
             *            per entry, the key id ({@link #NOT_FOUND} for a row that does not take
             *            part).
             * @param aEntryRows
             *            per entry, its row; {@code null} when entry {@code i} IS row {@code i}.
             * @param aEntries
             *            the number of entries.
             */
            KeyRows finish(int[] aEntryIds, int @Nullable [] aEntryRows, int aEntries)
            {
                int[] offsets = new int[size + 1];
                for (int id = 0; id < size; id++)
                {
                    offsets[id + 1] = offsets[id] + counts[id];
                }
                int[] rows = new int[offsets[size]];
                int[] cursor = Arrays.copyOf(offsets, size);
                for (int i = 0; i < aEntries; i++)
                {
                    int id = aEntryIds[i];
                    if (id != NOT_FOUND)
                    {
                        rows[cursor[id]++] = aEntryRows == null ? i : aEntryRows[i];
                    }
                }
                int studyCount = studyOrdinals.size();
                int[] studyOffsets = new int[studyCount + 1];
                for (int id = 0; id < size; id++)
                {
                    studyOffsets[keyStudy[id] + 1]++;
                }
                for (int s = 0; s < studyCount; s++)
                {
                    studyOffsets[s + 1] += studyOffsets[s];
                }
                int[] studyKeyIds = new int[size];
                int[] studyCursor = Arrays.copyOf(studyOffsets, studyCount);
                for (int id = 0; id < size; id++)
                {
                    studyKeyIds[studyCursor[keyStudy[id]]++] = id;
                }
                return new KeyRows(keys, ids, Arrays.copyOf(byId, size), offsets, rows,
                        Map.copyOf(studyOrdinals), List.copyOf(studyOrdinals.keySet()),
                        studyOffsets, studyKeyIds);
            }


            /** Doubles the slot table, keeping the load factor at or below one half. */
            private void grow()
            {
                @Nullable
                RelrecKey[] oldKeys = keys;
                int[] oldIds = ids;
                int[] oldHashes = hashes;
                keys = new RelrecKey[oldKeys.length * 2];
                ids = new int[oldKeys.length * 2];
                hashes = new int[oldKeys.length * 2];
                int mask = keys.length - 1;
                for (int i = 0; i < oldKeys.length; i++)
                {
                    RelrecKey key = oldKeys[i];
                    if (key == null)
                    {
                        continue;
                    }
                    int slot = spread(oldHashes[i]) & mask;
                    while (keys[slot] != null)
                    {
                        slot = (slot + 1) & mask;
                    }
                    keys[slot] = key;
                    ids[slot] = oldIds[i];
                    hashes[slot] = oldHashes[i];
                }
            }
        }
    }

    /**
     * The order in which related domains contribute parts: first appearance over the sorted pairs
     * (the primary row's order, then the ordinal) — the data browser's part order.
     *
     * @param aPairing
     *            the pairing.
     * @return the ordinals in part order.
     */
    static List<Integer> usedOrdinalsInPairOrder(Pairing aPairing)
    {
        LinkedHashSet<Integer> used = new LinkedHashSet<>();
        for (Pair p : aPairing.pairs())
        {
            used.add(p.ordinal());
        }
        return new ArrayList<>(used);
    }
}
