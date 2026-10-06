package net.cumba.datatable.impl.merge;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.merge.RelrecLinks;
import org.jspecify.annotations.Nullable;

/**
 * The parsed content of one {@code SUPP--} dataset: per non-blank {@code QNAM}, in first-seen
 * order, the rows that carry it, each classified by level. The engine's {@code SuppQnamIndex},
 * moved into the datatable layer by {@code PLAN-merged-evaluation-table} W1 and, by the owner's
 * ruling of 2026-10-05, <b>released once the row maps are built</b>: it is a build-time temporary
 * of {@link SuppMatcher}, which keeps only the {@code QNAM} set and the compact row maps. It was
 * the largest resident structure of a run (77 B per supplemental row, 98.5 MB on the ×20 pilot).
 *
 * <ul>
 * <li>A row whose {@code IDVAR} is blank (or whose dataset carries no active {@code IDVAR} /
 * {@code IDVARVAL} pair) is <b>subject-level</b>: it qualifies every record of its subject (SDTMIG
 * 8.4); naming no subject it qualifies nothing (JKM R6) and is dropped. Whether a row names a
 * subject is read off the row's OWN subject columns ({@link SuppKeyShape#suppSubjectBlank}), not
 * the active key: a pool's row over a base without {@code POOLID} names a pool, so it is not
 * study-wide (T2 review L2).</li>
 * <li>A row with an {@code IDVAR} and an {@code IDVARVAL} is <b>record-level</b>; one with an
 * {@code IDVAR} but no {@code IDVARVAL} qualifies nothing and is dropped.</li>
 * <li>A record-level row naming no subject at all is <b>study-wide</b> under SEND (OD-2, SENDIG
 * 8.3: it applies to every record with that {@code IDVAR} = {@code IDVARVAL}); under SDTM it
 * qualifies nothing and is dropped (JKM R6 — SDTM has no study-wide supplemental row, W1 review
 * M1). A key that names no subject component at all is the author's choice, and its record-level
 * rows are study-wide under either standard.</li>
 * </ul>
 *
 * <p>
 * A {@code QNAM} cell is a value and is recorded exactly, case-sensitively (CIT §3).
 * </p>
 */
final class SuppQnamIndex
{

    /** The level of a supplemental row. */
    enum Level
    {
        /** {@code IDVAR} blank: qualifies every record of the subject. */
        SUBJECT,

        /** {@code IDVAR} = {@code IDVARVAL} within the subject. */
        RECORD,

        /** {@code IDVAR} = {@code IDVARVAL} for every subject (no subject named, OD-2). */
        STUDY_WIDE
    }


    /**
     * One supplemental row that can qualify a record. A class, not a record: its key is an array.
     */
    static final class Entry
    {

        private final long row;

        private final Object[] key;

        private final Level level;

        private final String idvar;

        private final String idvarval;

        /**
         * @param aRow
         *            the row in the supplemental dataset.
         * @param aKey
         *            its key identities (plain then subject, {@link SuppKeyShape#suppKey}); owned
         *            from here on.
         * @param aLevel
         *            its level.
         * @param aIdvar
         *            the {@code IDVAR} text ({@code ""} for a subject-level row).
         * @param aIdvarval
         *            the raw {@code IDVARVAL} text ({@code ""} for a subject-level row).
         */
        Entry(long aRow, Object[] aKey, Level aLevel, String aIdvar, String aIdvarval)
        {
            row = aRow;
            key = aKey;
            level = aLevel;
            idvar = aIdvar;
            idvarval = aIdvarval;
        }


        long row()
        {
            return row;
        }


        /** The key identities, plain then subject — the array itself, never modified. */
        Object[] key()
        {
            return key;
        }


        Level level()
        {
            return level;
        }


        String idvar()
        {
            return idvar;
        }


        String idvarval()
        {
            return idvarval;
        }
    }

    private final Map<String, List<Entry>> byQnam;

    private final Map<String, String> labelByQnam;

    private SuppQnamIndex(Map<String, List<Entry>> aByQnam, Map<String, String> aLabelByQnam)
    {
        byQnam = aByQnam;
        labelByQnam = aLabelByQnam;
    }


    /**
     * Parses the supplemental dataset under the resolved key shape.
     *
     * @param aSupp
     *            the supplemental dataset; must carry {@code QNAM}.
     * @param aShape
     *            the key shape.
     * @param aSend
     *            whether the dataset is read under SEND (a subject-less record-level row is then
     *            study-wide, OD-2) or SDTM (it qualifies nothing).
     * @return the index.
     */
    static SuppQnamIndex of(IDataTable aSupp, SuppKeyShape aShape, boolean aSend)
    {
        DataTableMeta meta = aSupp.getMetaData();
        int qnamIdx = meta.getColumnIndex("QNAM");
        int qlabelIdx = meta.getColumnIndex("QLABEL");
        int idvarIdx = aShape.suppIdvarColumn();
        int idvarvalIdx = aShape.suppIdvarvalColumn();
        Map<String, List<Entry>> byQnam = new LinkedHashMap<>();
        Map<String, String> labels = new LinkedHashMap<>();
        long rowCount = aSupp.getRowCount();
        for (long r = 0; r < rowCount; r++)
        {
            String qnam = RelrecLinks.cell(aSupp, qnamIdx, r);
            if (qnam == null || qnam.isEmpty())
            {
                continue;
            }
            List<Entry> entries = byQnam.computeIfAbsent(qnam, _ -> new ArrayList<>());
            if (qlabelIdx >= 0 && !labels.containsKey(qnam))
            {
                String label = RelrecLinks.cell(aSupp, qlabelIdx, r);
                if (label != null)
                {
                    labels.put(qnam, label);
                }
            }
            String idvar = stripOrEmpty(RelrecLinks.cell(aSupp, idvarIdx, r));
            Object[] key = aShape.suppKey(aSupp, r);
            boolean subjectBlank = aShape.suppSubjectBlank(aSupp, r);
            if (idvar.isEmpty())
            {
                if (subjectBlank)
                {
                    continue; // a subject-level row naming neither subject nor pool: nothing
                }
                entries.add(new Entry(r, key, Level.SUBJECT, "", ""));
                continue;
            }
            String idvarval = RelrecLinks.cell(aSupp, idvarvalIdx, r);
            if (idvarval == null || idvarval.isBlank())
            {
                continue; // a record-level row without a record value qualifies nothing
            }
            Level level = Level.RECORD;
            if (subjectBlank)
            {
                if (aShape.hasSubjectComponents() && !aSend)
                {
                    continue; // SDTM: a record-level row naming no subject qualifies nothing
                }
                level = Level.STUDY_WIDE;
            }
            entries.add(new Entry(r, key, level, idvar, idvarval));
        }
        return new SuppQnamIndex(byQnam, labels);
    }


    private static String stripOrEmpty(@Nullable String aText)
    {
        return aText == null ? "" : aText.strip();
    }


    /** Every {@code QNAM} the dataset carries (the existence half), in first-seen order. */
    Set<String> qnams()
    {
        return Collections.unmodifiableSet(byQnam.keySet());
    }


    /** The rows carrying {@code aQnam} that can qualify a record, in dataset order. */
    List<Entry> entries(String aQnam)
    {
        List<Entry> entries = byQnam.get(aQnam);
        return entries == null ? List.of() : Collections.unmodifiableList(entries);
    }


    /** The {@code QLABEL} of the first row carrying {@code aQnam}, or {@code null}. */
    @Nullable
    String label(String aQnam)
    {
        return labelByQnam.get(aQnam);
    }
}
