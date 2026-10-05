package net.cumba.datatable.merge;

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
 * <li><b>SDTM:</b> a relationship is the RELREC rows sharing {@code (STUDYID, USUBJID, RELID)}
 * (SDTMIG 3.4 §8.2).</li>
 * <li><b>SEND (OD-3):</b> a RELREC read under SEND ({@link MergeStandard}; under
 * {@link MergeStandard#UNKNOWN} one carrying a {@code POOLID} column) is read as SENDIG 3.1.1 §8.2
 * defines it: a relationship is the rows sharing {@code (STUDYID, RELID)}, and each row identifies
 * its record by {@code USUBJID} <em>or</em> {@code POOLID}.</li>
 * <li>A link is <b>record-level</b> when both of its rows carry an {@code IDVARVAL} (it joins the
 * records whose {@code IDVAR} column equals it), else <b>dataset-level</b> (it joins within the
 * subject on equal {@code IDVAR} values).</li>
 * <li>A related domain whose links <b>mix</b> the two shapes is not merged (today's rule, kept); it
 * is reported as {@link RelrecScan#mixedSkipped()}, and the merge flags it (W1 review L4).</li>
 * <li>Each link carries the {@code RELTYPE} of its two rows: a dataset-level relationship states
 * the IG's cardinality of each side ({@code ONE} / {@code MANY}, OD-5).</li>
 * <li>Every key component is classified by {@code cumba-datatable}'s
 * {@link GroupKeyPolicy#textKeyIdentity(IDataValue)} (JKM R5): a missing {@code STUDYID} or
 * {@code USUBJID} is its own identity, never the empty string; an absent column reads {@code ""}
 * (JKM R7).</li>
 * </ul>
 */
public final class RelrecLinks
{

    private static final String STUDYID = "STUDYID";

    private static final String USUBJID = "USUBJID";

    private static final String POOLID = "POOLID";

    private static final String RDOMAIN = "RDOMAIN";

    private static final String IDVAR = "IDVAR";

    private static final String IDVARVAL = "IDVARVAL";

    private static final String RELID = "RELID";

    private static final String RELTYPE = "RELTYPE";

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
     * @param srcSubject
     *            the raw subject text of the left row ({@code USUBJID}, or {@code POOLID} under
     *            SEND when {@code USUBJID} is blank), {@code null} when missing.
     * @param srcSubjectKey
     *            its key identity.
     * @param srcPooled
     *            whether the left row names a pool, i.e. the primary's {@code POOLID} column is the
     *            subject column to probe.
     * @param tgtSubject
     *            the raw subject text of the right row.
     * @param tgtSubjectKey
     *            its key identity.
     * @param tgtPooled
     *            whether the right row names a pool.
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
            @Nullable String study, Object studyKey, @Nullable String srcSubject,
            Object srcSubjectKey, boolean srcPooled, @Nullable String tgtSubject,
            Object tgtSubjectKey, boolean tgtPooled, String srcIdvar, @Nullable String srcIdvarval,
            String tgtIdvar, @Nullable String tgtIdvarval, long leftRow, long rightRow,
            @Nullable String srcReltype, @Nullable String tgtReltype)
    {

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
     * {@code (STUDYID, RELID)} (OD-3), SDTM by {@code (STUDYID, USUBJID, RELID)}.
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
        DataTableMeta m = aRelrec.getMetaData();
        if (aPrimaryDomain == null || m.getColumnIndex(RDOMAIN) < 0 || m.getColumnIndex(RELID) < 0)
        {
            return RelrecScan.empty();
        }
        Links links = collect(aRelrec, aPrimaryDomain, null, aStandard);
        return new RelrecScan(links.selectableDomains(), links.mixedDomains());
    }


    /**
     * Collects every link whose left side is {@code aPrimaryDomain}. Needs {@code RDOMAIN},
     * {@code IDVAR}, {@code IDVARVAL} and {@code RELID}; without one of them there are no links
     * ({@code STUDYID}, {@code USUBJID} and {@code POOLID} are read when present).
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
     * {@code USUBJID} or {@code POOLID} (OD-3); SDTM groups by {@code (STUDYID, USUBJID, RELID)};
     * {@link MergeStandard#UNKNOWN} reads a RELREC carrying {@code POOLID} as SEND.
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
        DataTableMeta rm = aRelrec.getMetaData();
        int cStudy = rm.getColumnIndex(STUDYID);
        int cUsubj = rm.getColumnIndex(USUBJID);
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
            Object subject = send ? "" : keyCell(aRelrec, cUsubj, rr);
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
                if (ldom == null || !ldom.equalsIgnoreCase(aPrimaryDomain))
                {
                    continue; // the left side is the primary domain
                }
                for (long rrr : group)
                {
                    String rdom = cell(aRelrec, cRdom, rrr);
                    if (rdom == null || rdom.equalsIgnoreCase(aPrimaryDomain))
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
                    Subject src = subjectOf(aRelrec, cUsubj, send ? cPool : -1, lrr);
                    Subject tgt = subjectOf(aRelrec, cUsubj, send ? cPool : -1, rrr);
                    Link link = new Link(rdom, cellOrEmpty(aRelrec, cRelid, lrr), ordinal,
                            recordLevel, cell(aRelrec, cStudy, lrr), keyCell(aRelrec, cStudy, lrr),
                            src.text(), src.key(), src.pooled(), tgt.text(), tgt.key(),
                            tgt.pooled(), srcIdvar, srcIdvarval, tgtIdvar, tgtIdvarval, lrr, rrr,
                            reltype(aRelrec, cReltype, lrr), reltype(aRelrec, cReltype, rrr));
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

    /** The subject a RELREC row names: its raw text, key identity and whether it is a pool. */
    private record Subject(@Nullable String text, Object key, boolean pooled)
    {
    }

    private static Subject subjectOf(IDataTable aRelrec, int aCUsubj, int aCPool, long aRow)
    {
        String usubj = cell(aRelrec, aCUsubj, aRow);
        if (aCPool >= 0 && !isNonBlank(usubj))
        {
            String pool = cell(aRelrec, aCPool, aRow);
            if (isNonBlank(pool))
            {
                return new Subject(pool, keyCell(aRelrec, aCPool, aRow), true);
            }
        }
        return new Subject(usubj, keyCell(aRelrec, aCUsubj, aRow), false);
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
     * column is {@code ""}, the character type default (JKM R7).
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
