package net.cumba.datatable.merge;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.values.GroupKeyPolicy;
import net.cumba.datatable.values.IDataValue;
import org.jspecify.annotations.Nullable;

/**
 * The reading of a {@code RELREC} dataset every consumer shares
 * ({@code PLAN-merged-evaluation-table} §3.6.1, MET #10): which rows form one <b>relationship</b>,
 * which links a primary domain has to which related domains, and whether a link is record-level or
 * dataset-level. The merge pairs records from exactly these links, and the data browser's dialog
 * offers exactly what {@link #scan} lists, so the dialog cannot offer a relationship the merge
 * would not form.
 *
 * <ul>
 * <li><b>The subject identifiers</b> ({@link #SUBJECT_COLUMNS}, SDTM v2.0 §6.1): a RELREC row names
 * its record's subject by {@code USUBJID}, {@code APID}, {@code POOLID} or {@code SPDEVID} — an
 * associated person, a pool, a device. A row identifies its record by <b>every</b> subject
 * identifier it populates (a device record inside a subject carries both {@code USUBJID} and
 * {@code SPDEVID}); a related domain or primary lacking one of them is reported, never silently
 * unpaired ({@code PLAN-relrec-ap-device-subject}, owner ruling G7-R1 (a)).</li>
 * <li><b>SDTM:</b> a relationship is the RELREC rows sharing {@code (STUDYID, subject, RELID)}
 * (SDTMIG 3.4 §8.2), the subject being {@code USUBJID} when populated, else the first populated of
 * {@code APID}, {@code POOLID}, {@code SPDEVID} ({@code RELID} is unique within the identifier that
 * is the subject of the relationship, SDTM v2.0 §6.1; G7-R1 (a)). A row populating none groups by
 * its {@code USUBJID} cell.</li>
 * <li><b>SEND (OD-3):</b> a RELREC read under SEND ({@link MergeStandard}; under
 * {@link MergeStandard#UNKNOWN} one carrying a {@code POOLID} column) is read as SENDIG 3.1.1 §8.2
 * defines it: a relationship is the rows sharing {@code (STUDYID, RELID)}, and each row identifies
 * its record by every one of {@code USUBJID} and {@code POOLID} it populates — one of them for a
 * subject's or a pool's record, both when a row carries both (G7-R1 (a) applies under SEND too; the
 * grouping alone ignores the subject).</li>
 * <li>A link is <b>record-level</b> when both of its rows carry an {@code IDVARVAL} (it joins the
 * records whose {@code IDVAR} column equals it), else <b>dataset-level</b> (it joins within the
 * subject on equal {@code IDVAR} values).</li>
 * <li>The primary's side is the row whose {@code RDOMAIN} is the primary's domain <em>or</em> its
 * dataset name: a split FA dataset's dataset-level relationship names the dataset (SDTMIG 3.4 sec.
 * 4.1.7 rule 8).</li>
 * <li>A related domain whose links <b>mix</b> the two shapes is not merged (today's rule, kept); it
 * is reported as {@link RelrecScan#mixedSkipped()}, and the merge flags it (W1 review L4).</li>
 * <li>Each link carries the {@code RELTYPE} of its two rows: a dataset-level relationship states
 * the IG's cardinality of each side ({@code ONE} / {@code MANY}, OD-5).</li>
 * <li>Every key component is classified by {@code cumba-datatable}'s
 * {@link GroupKeyPolicy#textKeyIdentity(IDataValue)} (JKM R5): a missing {@code STUDYID} or
 * {@code USUBJID} is its own identity, never the empty string; an absent column reads {@code ""} (a
 * RELREC link keeps this rule: {@code REG-Q4} QK7 — not an author-keyed join).</li>
 * </ul>
 */
public final class RelrecLinks
{

    private static final String STUDYID = "STUDYID";

    private static final String USUBJID = "USUBJID";

    private static final String APID = "APID";

    private static final String POOLID = "POOLID";

    private static final String SPDEVID = "SPDEVID";

    private static final String RDOMAIN = "RDOMAIN";

    private static final String IDVAR = "IDVAR";

    private static final String IDVARVAL = "IDVARVAL";

    private static final String RELID = "RELID";

    private static final String RELTYPE = "RELTYPE";

    /**
     * The RELREC subject identifiers of SDTM v2.0 §6.1, in the order that picks a relationship's
     * subject (owner ruling G7-R1 (a)): {@code USUBJID} when populated, else the first populated of
     * {@code APID}, {@code POOLID}, {@code SPDEVID}. The SUPP merge's subject keys are the same
     * set.
     */
    public static final List<String> SUBJECT_COLUMNS = List.of(USUBJID, APID, POOLID, SPDEVID);

    /**
     * One subject identifier a RELREC row populates.
     *
     * @param column
     *            the identifier column, one of {@link #SUBJECT_COLUMNS}.
     * @param key
     *            the cell's key identity ({@link #keyCell}).
     */
    public record SubjectId(String column, Object key)
    {
    }


    /**
     * One link from a primary-domain RELREC row (left) to a related-domain row (right) of the same
     * relationship.
     *
     * @param rdomain
     *            the related domain.
     * @param relid
     *            the {@code RELID} text.
     * @param relationship
     *            the relationship's ordinal (first-appearance order over the RELREC rows); every
     *            link of one relationship shares it.
     * @param recordLevel
     *            whether both rows carry an {@code IDVARVAL}.
     * @param study
     *            the raw {@code STUDYID} text of the left row, {@code null} when missing — names a
     *            study when non-blank.
     * @param studyKey
     *            the left row's {@code STUDYID} key identity.
     * @param srcIdentity
     *            every subject identifier the left row populates, in {@link #SUBJECT_COLUMNS}
     *            order: the primary's record carries each of them (G7-R1 (a)). Empty when the row
     *            populates none; the join is then within the subject ({@code USUBJID}).
     * @param tgtIdentity
     *            every subject identifier the right row populates, likewise.
     * @param srcIdvar
     *            the left row's {@code IDVAR}.
     * @param srcIdvarval
     *            the left row's {@code IDVARVAL}, {@code null} when missing.
     * @param tgtIdvar
     *            the right row's {@code IDVAR}.
     * @param tgtIdvarval
     *            the right row's {@code IDVARVAL}, {@code null} when missing.
     * @param leftRow
     *            the left RELREC row.
     * @param rightRow
     *            the right RELREC row.
     * @param srcReltype
     *            the left row's {@code RELTYPE}, stripped and upper case, {@code null} when blank
     *            or the column is absent.
     * @param tgtReltype
     *            the right row's {@code RELTYPE}, likewise.
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    public record Link(String rdomain, String relid, int relationship, boolean recordLevel,
            @Nullable String study, Object studyKey, List<SubjectId> srcIdentity,
            List<SubjectId> tgtIdentity, String srcIdvar, @Nullable String srcIdvarval,
            String tgtIdvar, @Nullable String tgtIdvarval, long leftRow, long rightRow,
            @Nullable String srcReltype, @Nullable String tgtReltype)
    {

        /** Canonical constructor: the identities are copied. */
        public Link
        {
            srcIdentity = List.copyOf(srcIdentity);
            tgtIdentity = List.copyOf(tgtIdentity);
        }


        /**
         * The IG's cardinality of this link's relationship from the primary's side (OD-5): a
         * dataset-level link whose rows say {@code RELTYPE} {@code ONE} on a side makes that side
         * unique; a record-level link, or a side whose {@code RELTYPE} is not {@code ONE}, is
         * {@code n} on that side.
         *
         * @return the IG default.
         */
        public Cardinality igCardinality()
        {
            if (recordLevel)
            {
                return Cardinality.MANY_TO_MANY;
            }
            boolean left = "ONE".equals(srcReltype);
            boolean right = "ONE".equals(tgtReltype);
            if (left)
            {
                return right ? Cardinality.ONE_TO_ONE : Cardinality.ONE_TO_MANY;
            }
            return right ? Cardinality.MANY_TO_ONE : Cardinality.MANY_TO_MANY;
        }
    }


    /**
     * The links to one related domain.
     *
     * @param rdomain
     *            the related domain.
     * @param links
     *            its links, in RELREC order.
     */
    public record DomainLinks(String rdomain, List<Link> links)
    {

        /** Canonical constructor: the list is copied. */
        public DomainLinks
        {
            links = List.copyOf(links);
        }
    }


    /**
     * The links of one primary domain, grouped by related domain in first-appearance order.
     *
     * @param send
     *            whether the RELREC was read as SEND (it carries {@code POOLID}).
     * @param domains
     *            the links per related domain, domains in first-appearance order.
     * @param mixedDomains
     *            the related domains whose links mix record- and dataset-level joins.
     * @param relationshipCount
     *            the number of relationships seen.
     */
    public record Links(boolean send, List<DomainLinks> domains, List<String> mixedDomains,
            int relationshipCount)
    {

        /** Canonical constructor: the lists are copied. */
        public Links
        {
            domains = List.copyOf(domains);
            mixedDomains = List.copyOf(mixedDomains);
        }


        /** No links at all. */
        public static Links empty()
        {
            return new Links(false, List.of(), List.of(), 0);
        }


        /**
         * The related domains that can be merged: every domain with links, minus the mixed ones.
         *
         * @return the domains, in first-appearance order.
         */
        public List<String> selectableDomains()
        {
            List<String> out = new ArrayList<>();
            for (DomainLinks d : domains)
            {
                if (!mixedDomains.contains(d.rdomain()))
                {
                    out.add(d.rdomain());
                }
            }
            return out;
        }
    }


    /**
     * What a merge dialog offers: the related domains the user can merge and those excluded because
     * their links mix the two join shapes.
     *
     * @param selectable
     *            the mergeable related domains, in first-discovery order.
     * @param mixedSkipped
     *            the related domains excluded as mixed.
     */
    public record RelrecScan(List<String> selectable, List<String> mixedSkipped)
    {

        /** Canonical constructor: the lists are copied. */
        public RelrecScan
        {
            selectable = List.copyOf(selectable);
            mixedSkipped = List.copyOf(mixedSkipped);
        }


        /** Nothing to offer. */
        public static RelrecScan empty()
        {
            return new RelrecScan(List.of(), List.of());
        }
    }


    /** The grouping key of one relationship. */
    private record GroupKey(Object study, Object subject, String relid)
    {
    }

    private RelrecLinks()
    {
    }


    /**
     * Scans RELREC for the related domains in which {@code aPrimaryDomain} participates. Needs the
     * {@code RDOMAIN} and {@code RELID} columns; a RELREC without them, or no primary domain,
     * offers nothing.
     *
     * @param aRelrec
     *            the RELREC dataset.
     * @param aPrimaryDomain
     *            the primary table's domain.
     * @return the scan.
     */
    public static RelrecScan scan(IDataTable aRelrec, @Nullable String aPrimaryDomain)
    {
        return scan(aRelrec, aPrimaryDomain, MergeStandard.UNKNOWN);
    }


    /**
     * {@link #scan(IDataTable, String)} under a known standard: SEND groups a relationship by
     * {@code (STUDYID, RELID)} (OD-3), SDTM by {@code (STUDYID, subject, RELID)} (G7-R1 (a)).
     *
     * @param aRelrec
     *            the RELREC dataset.
     * @param aPrimaryDomain
     *            the primary table's domain.
     * @param aStandard
     *            the standard RELREC is read under.
     * @return the scan.
     */
    public static RelrecScan scan(IDataTable aRelrec, @Nullable String aPrimaryDomain,
            MergeStandard aStandard)
    {
        return scan(aRelrec, aPrimaryDomain, null, aStandard);
    }


    /**
     * {@link #scan(IDataTable, String, MergeStandard)} for a primary that also answers to its
     * dataset name — what the merge pairs for a split dataset (T2 review M1), so a dialog built on
     * this scan offers exactly that.
     *
     * @param aRelrec
     *            the RELREC dataset.
     * @param aPrimaryDomain
     *            the primary table's domain.
     * @param aPrimaryDataset
     *            the primary table's dataset name ({@code FAMH} for a split FA dataset), or
     *            {@code null} when it is the domain.
     * @param aStandard
     *            the standard RELREC is read under.
     * @return the scan.
     */
    public static RelrecScan scan(IDataTable aRelrec, @Nullable String aPrimaryDomain,
            @Nullable String aPrimaryDataset, MergeStandard aStandard)
    {
        DataTableMeta m = aRelrec.getMetaData();
        if (aPrimaryDomain == null || m.getColumnIndex(RDOMAIN) < 0 || m.getColumnIndex(RELID) < 0)
        {
            return RelrecScan.empty();
        }
        Links links = collect(aRelrec, aPrimaryDomain, aPrimaryDataset, null, aStandard);
        return new RelrecScan(links.selectableDomains(), links.mixedDomains());
    }


    /**
     * Collects every link whose left side is {@code aPrimaryDomain}. Needs {@code RDOMAIN},
     * {@code IDVAR}, {@code IDVARVAL} and {@code RELID}; without one of them there are no links
     * ({@code STUDYID} and the {@link #SUBJECT_COLUMNS} are read when present).
     *
     * @param aRelrec
     *            the RELREC dataset.
     * @param aPrimaryDomain
     *            the primary table's domain.
     * @param aSelectedRdomains
     *            upper-cased related domains to restrict to, or {@code null} for all.
     * @return the links.
     */
    public static Links collect(IDataTable aRelrec, String aPrimaryDomain,
            @Nullable Set<String> aSelectedRdomains)
    {
        return collect(aRelrec, aPrimaryDomain, aSelectedRdomains, MergeStandard.UNKNOWN);
    }


    /**
     * {@link #collect(IDataTable, String, Set)} under a known standard ({@link MergeStandard}):
     * SEND groups a relationship by {@code (STUDYID, RELID)} and names each record by
     * {@code USUBJID} or {@code POOLID} (OD-3); SDTM groups by {@code (STUDYID, subject, RELID)},
     * the subject {@code USUBJID} when populated, else the first populated of {@code APID},
     * {@code POOLID}, {@code SPDEVID} (G7-R1 (a)); {@link MergeStandard#UNKNOWN} reads a RELREC
     * carrying {@code POOLID} as SEND.
     *
     * @param aRelrec
     *            the RELREC dataset.
     * @param aPrimaryDomain
     *            the primary table's domain.
     * @param aSelectedRdomains
     *            upper-cased related domains to restrict to, or {@code null} for all.
     * @param aStandard
     *            the standard RELREC is read under.
     * @return the links.
     */
    public static Links collect(IDataTable aRelrec, String aPrimaryDomain,
            @Nullable Set<String> aSelectedRdomains, MergeStandard aStandard)
    {
        return collect(aRelrec, aPrimaryDomain, null, aSelectedRdomains, aStandard);
    }


    /**
     * {@link #collect(IDataTable, String, Set, MergeStandard)} for a primary that also answers to
     * its dataset name: a split FA dataset's dataset-level relationship names the dataset
     * ({@code FAMH}), not the domain (SDTMIG 3.4 sec. 4.1.7 rule 8; T2 review M1). A RELREC row
     * whose {@code RDOMAIN} equals either name is the primary's side; a row naming neither is a
     * related domain's. Both link shapes go through this one classification.
     *
     * @param aRelrec
     *            the RELREC dataset.
     * @param aPrimaryDomain
     *            the primary table's domain.
     * @param aPrimaryDataset
     *            the primary table's dataset name, or {@code null} when it is the domain.
     * @param aSelectedRdomains
     *            upper-cased related domains to restrict to, or {@code null} for all.
     * @param aStandard
     *            the standard RELREC is read under.
     * @return the links.
     */
    public static Links collect(IDataTable aRelrec, String aPrimaryDomain,
            @Nullable String aPrimaryDataset, @Nullable Set<String> aSelectedRdomains,
            MergeStandard aStandard)
    {
        DataTableMeta rm = aRelrec.getMetaData();
        int cStudy = rm.getColumnIndex(STUDYID);
        int[] cSubjects = new int[SUBJECT_COLUMNS.size()];
        for (int i = 0; i < cSubjects.length; i++)
        {
            cSubjects[i] = rm.getColumnIndex(SUBJECT_COLUMNS.get(i));
        }
        int cPool = rm.getColumnIndex(POOLID);
        int cRdom = rm.getColumnIndex(RDOMAIN);
        int cIdvar = rm.getColumnIndex(IDVAR);
        int cIdvarval = rm.getColumnIndex(IDVARVAL);
        int cRelid = rm.getColumnIndex(RELID);
        int cReltype = rm.getColumnIndex(RELTYPE);
        if (cRdom < 0 || cIdvar < 0 || cIdvarval < 0 || cRelid < 0)
        {
            return Links.empty();
        }
        boolean send = aStandard.isSend(cPool >= 0);

        // Group RELREC rows into relationships, each component its key identity (JKM R5).
        Map<GroupKey, List<Long>> groups = new LinkedHashMap<>();
        long rrCount = aRelrec.getRowCount();
        for (long rr = 0; rr < rrCount; rr++)
        {
            String relid = cell(aRelrec, cRelid, rr);
            if (relid == null)
            {
                continue;
            }
            Object subject = send ? "" : subjectOf(aRelrec, cSubjects, rr).groupKey();
            GroupKey key = new GroupKey(keyCell(aRelrec, cStudy, rr), subject, relid);
            groups.computeIfAbsent(key, _ -> new ArrayList<>()).add(rr);
        }

        Map<String, List<Link>> byDomain = new LinkedHashMap<>();
        Set<String> mixed = new LinkedHashSet<>();
        Map<String, boolean[]> modes = new LinkedHashMap<>();
        int relationship = 0;
        for (List<Long> group : groups.values())
        {
            int ordinal = relationship++;
            for (long lrr : group)
            {
                String ldom = cell(aRelrec, cRdom, lrr);
                if (ldom == null || !isPrimary(ldom, aPrimaryDomain, aPrimaryDataset))
                {
                    continue; // the left side is the primary domain (or dataset)
                }
                Subject src = subjectOf(aRelrec, cSubjects, lrr);
                for (long rrr : group)
                {
                    String rdom = cell(aRelrec, cRdom, rrr);
                    if (rdom == null || isPrimary(rdom, aPrimaryDomain, aPrimaryDataset))
                    {
                        continue; // the right side is every other-domain row of the group
                    }
                    if (aSelectedRdomains != null
                            && !aSelectedRdomains.contains(rdom.toUpperCase(Locale.ROOT)))
                    {
                        continue; // selection push-down: an unselected domain is never joined
                    }
                    String srcIdvar = cell(aRelrec, cIdvar, lrr);
                    String tgtIdvar = cell(aRelrec, cIdvar, rrr);
                    if (srcIdvar == null || tgtIdvar == null)
                    {
                        continue;
                    }
                    String srcIdvarval = cell(aRelrec, cIdvarval, lrr);
                    String tgtIdvarval = cell(aRelrec, cIdvarval, rrr);
                    boolean recordLevel = isNonBlank(srcIdvarval) && isNonBlank(tgtIdvarval);
                    Subject tgt = subjectOf(aRelrec, cSubjects, rrr);
                    Link link = new Link(rdom, cellOrEmpty(aRelrec, cRelid, lrr), ordinal,
                            recordLevel, cell(aRelrec, cStudy, lrr), keyCell(aRelrec, cStudy, lrr),
                            src.identity(), tgt.identity(), srcIdvar, srcIdvarval, tgtIdvar,
                            tgtIdvarval, lrr, rrr, reltype(aRelrec, cReltype, lrr),
                            reltype(aRelrec, cReltype, rrr));
                    byDomain.computeIfAbsent(rdom, _ -> new ArrayList<>()).add(link);
                    boolean[] hasMode = modes.computeIfAbsent(rdom, _ -> new boolean[2]);
                    hasMode[recordLevel ? 0 : 1] = true;
                }
            }
        }
        for (Map.Entry<String, boolean[]> e : modes.entrySet())
        {
            if (e.getValue()[0] && e.getValue()[1])
            {
                mixed.add(e.getKey());
            }
        }
        List<DomainLinks> domains = new ArrayList<>();
        for (Map.Entry<String, List<Link>> e : byDomain.entrySet())
        {
            domains.add(new DomainLinks(e.getKey(), e.getValue()));
        }
        return new Links(send, domains, List.copyOf(mixed), relationship);
    }


    /** Whether an {@code RDOMAIN} value names the primary: its domain or its dataset name. */
    private static boolean isPrimary(String aRdomain, String aPrimaryDomain,
            @Nullable String aPrimaryDataset)
    {
        return aRdomain.equalsIgnoreCase(aPrimaryDomain)
                || (aPrimaryDataset != null && aRdomain.equalsIgnoreCase(aPrimaryDataset));
    }

    /**
     * The subject a RELREC row names: the SDTM grouping key (its first populated subject
     * identifier, else its {@code USUBJID} cell — a missing marker is its own key) and every
     * identifier it populates. The raw text and key of the first identifier used to travel on the
     * {@link Link} too ({@code srcSubject} …); nothing read them (PACK G fold, G7 review).
     */
    private record Subject(Object groupKey, List<SubjectId> identity)
    {
    }

    private static Subject subjectOf(IDataTable aRelrec, int[] aCSubjects, long aRow)
    {
        List<SubjectId> identity = List.of();
        for (int i = 0; i < aCSubjects.length; i++)
        {
            String value = cell(aRelrec, aCSubjects[i], aRow);
            if (!isNonBlank(value))
            {
                continue;
            }
            SubjectId id = new SubjectId(SUBJECT_COLUMNS.get(i),
                    keyCell(aRelrec, aCSubjects[i], aRow));
            if (identity.isEmpty())
            {
                identity = List.of(id);
            }
            else
            {
                List<SubjectId> more = new ArrayList<>(identity);
                more.add(id);
                identity = List.copyOf(more);
            }
        }
        if (identity.isEmpty())
        {
            return new Subject(new SubjectId(USUBJID, keyCell(aRelrec, aCSubjects[0], aRow)),
                    identity);
        }
        return new Subject(identity.get(0), identity);
    }


    private static @Nullable String reltype(IDataTable aRelrec, int aCol, long aRow)
    {
        String s = cell(aRelrec, aCol, aRow);
        return s == null || s.isBlank() ? null : s.strip().toUpperCase(Locale.ROOT);
    }


    private static String cellOrEmpty(IDataTable aTable, int aCol, long aRow)
    {
        String s = cell(aTable, aCol, aRow);
        return s == null ? "" : s;
    }


    /**
     * The raw text of a cell, {@code null} for a missing cell or an absent column.
     *
     * @param aTable
     *            the table.
     * @param aCol
     *            the column, {@code -1} for absent.
     * @param aRow
     *            the row.
     * @return the text or {@code null}.
     */
    public static @Nullable String cell(IDataTable aTable, int aCol, long aRow)
    {
        if (aCol < 0)
        {
            return null;
        }
        IDataValue dv = aTable.getColumn(aCol).getDataValue(aRow);
        return dv.isMissingOrInvalid() ? null : dv.getValueAsString();
    }


    /**
     * Whether a raw cell text is present and non-empty.
     *
     * @param aValue
     *            the text or {@code null}.
     * @return whether it is non-blank.
     */
    public static boolean isNonBlank(@Nullable String aValue)
    {
        return aValue != null && !aValue.isEmpty();
    }


    /**
     * One join-key component's <b>identity</b> (JKM R5, {@code PLAN-shared-key-identity} D1 (b) /
     * D2 (c)): {@code cumba-datatable}'s {@link GroupKeyPolicy#textKeyIdentity(IDataValue)} under
     * {@code KEEP_MISSING_KEYS} — a stored or {@code NaN}-coded {@code MissingValue} is its
     * constant, {@code ""} is {@code ""}, a number is its cleaned text, text is text. An absent
     * column is {@code ""}, the character type default (kept for RELREC links, {@code REG-Q4} QK7).
     *
     * @param aTable
     *            the table.
     * @param aCol
     *            the column, {@code -1} for absent.
     * @param aRow
     *            the row.
     * @return the identity object (a {@link String} or a {@code MissingValue}).
     */
    public static Object keyCell(IDataTable aTable, int aCol, long aRow)
    {
        if (aCol < 0)
        {
            return "";
        }
        return GroupKeyPolicy.KEEP_MISSING_KEYS
                .textKeyIdentity(aTable.getColumn(aCol).getDataValue(aRow));
    }
}
