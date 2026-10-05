package net.cumba.datatable.impl.merge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.databuffer.DataBufferFactory;
import net.cumba.datatable.impl.databuffer.IDataBufferNumeric;
import net.cumba.datatable.impl.view.UnionDataTable;
import net.cumba.datatable.merge.JoinToken;
import net.cumba.datatable.merge.KeyColumn;
import net.cumba.datatable.merge.RelrecLinks;
import org.jspecify.annotations.Nullable;

/**
 * The supplemental-qualifier matching ({@code PLAN-merged-evaluation-table} §3.5): for every
 * {@code QNAM} of a {@code SUPP--} dataset, the one supplemental row that qualifies each base row,
 * as a compact row map, and the cases in which no single row does — reported through the
 * {@code MERGE_ERROR} channel (OD-7) rather than by aborting:
 *
 * <table>
 * <caption>The outcome per case</caption>
 * <tr>
 * <th>case (§3.5.3)</th>
 * <th>outcome</th>
 * </tr>
 * <tr>
 * <td>1, 4, 7, 8, 9 — one row qualifies the record, or none does</td>
 * <td>the row's {@code QVAL}, or not supplied</td>
 * </tr>
 * <tr>
 * <td>2, 5, 9-overlap — two rows qualify one record (a duplicate, mixed {@code IDVAR}s hitting one
 * record, a study-wide and a subject-/record-level row)</td>
 * <td>that record not supplied; the column flagged, naming the rows</td>
 * </tr>
 * <tr>
 * <td>3 — a subject-level and a record-level row for one subject</td>
 * <td>every record of that subject not supplied; flagged</td>
 * </tr>
 * <tr>
 * <td>6 — {@code IDVAR} names a column the base lacks</td>
 * <td>those rows qualify nothing; flagged</td>
 * </tr>
 * <tr>
 * <td>no subject key usable (P-15)</td>
 * <td>every column flagged, nothing supplied</td>
 * </tr>
 * </table>
 *
 * <p>
 * The compare is by text (MET #11, #14, {@link JoinToken}); the key is the resolved
 * {@link SuppKeyShape}. A {@code QNAM} the base already carries is not a column — the base's own
 * column wins (P-10); two {@code QNAM}s differing only in case collide and the column is flagged.
 * The parse structure ({@link SuppQnamIndex}) is released when this method returns (owner ruling
 * 2026-10-05); only the {@code QNAM} set and the row maps survive.
 * </p>
 *
 * <p>
 * Several supplemental datasets (a split dataset's {@code SUPP<split>} and its domain's, P-7)
 * arrive stacked as one {@link UnionDataTable}: a record two of them qualify for one {@code QNAM}
 * is a duplicate like two rows of one, and a message names each row by its own dataset.
 * </p>
 */
final class SuppMatcher
{

    /** The most offending rows a message names per category. */
    private static final int MAX_LISTED_ROWS = 20;

    /**
     * One qualifier column: its row map over the base rows ({@code -1} = not supplied), or
     * {@code null} for a column nothing can supply, and the problems found.
     *
     * @param qnam
     *            the {@code QNAM}.
     * @param label
     *            its {@code QLABEL}, or {@code null}.
     * @param map
     *            base row → supplemental row, or {@code null} for an all-unsupplied column.
     * @param error
     *            the {@code MERGE_ERROR} message, or {@code null} when the column is clean.
     * @param errorRows
     *            the offending supplemental rows (0-based), for the issue.
     */
    record QnamColumn(String qnam, @Nullable String label, @Nullable IDataBufferNumeric map,
            @Nullable String error, List<Long> errorRows)
    {
    }


    /**
     * The result over one supplemental dataset.
     *
     * @param supp
     *            the supplemental dataset.
     * @param qvalIdx
     *            its {@code QVAL} column, {@code -1} when it has none.
     * @param qnams
     *            every {@code QNAM} it carries, in first-seen order.
     * @param columns
     *            the qualifier columns, in first-seen order of their {@code QNAM}.
     */
    record Result(IDataTable supp, int qvalIdx, Set<String> qnams, List<QnamColumn> columns)
    {
    }


    /** An entry with the dense id of its key half in the base's key table. */
    private record Keyed(SuppQnamIndex.Entry entry, int id)
    {
    }


    /**
     * The probe key of a record-level or study-wide row: the dense id of its key half and its
     * {@code IDVARVAL} token. ⚠ Keyed on BOTH, measured: keyed on the token alone, with the id
     * filtered afterwards, every {@code --SEQ} value's list held one row per subject and the probe
     * walked it per base row — 1.2 M rows × 20 k candidates on the ×20 pilot, 27 s per build.
     */
    private record ProbeKey(int id, String token)
    {
    }


    /** The record-level or study-wide rows naming one {@code IDVAR}. */
    private record IdvarGroup(int baseColumn, boolean numeric, Map<ProbeKey, List<Keyed>> byKey)
    {
    }

    private SuppMatcher()
    {
    }


    /**
     * Matches every qualifier of the supplemental dataset to the base rows.
     *
     * @param aBase
     *            the dataset the qualifiers belong to.
     * @param aBaseDomain
     *            its domain.
     * @param aSupp
     *            the supplemental dataset.
     * @param aSuppName
     *            its name, for messages.
     * @param aKeys
     *            the key in sided form.
     * @param aSend
     *            whether the supplemental dataset is read under SEND (OD-2) rather than SDTM.
     * @param aIndexObserver
     *            a test seam handed the parse structure before it is released, or {@code null}.
     * @return the result.
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    static Result match(IDataTable aBase, String aBaseDomain, IDataTable aSupp, String aSuppName,
            List<KeyColumn> aKeys, boolean aSend, @Nullable Consumer<SuppQnamIndex> aIndexObserver)
    {
        DataTableMeta sm = aSupp.getMetaData();
        int qvalIdx = sm.getColumnIndex("QVAL");
        if (sm.getColumnIndex("QNAM") < 0)
        {
            return new Result(aSupp, qvalIdx, Set.of(), List.of());
        }
        SuppKeyShape shape = SuppKeyShape.resolve(aBase, aBaseDomain, aSupp, aSuppName, aKeys);
        SuppQnamIndex index = SuppQnamIndex.of(aSupp, shape, aSend);
        if (aIndexObserver != null)
        {
            aIndexObserver.accept(index);
        }
        Set<String> qnams = new LinkedHashSet<>(index.qnams());
        List<QnamColumn> columns = new ArrayList<>();
        new Matching(aBase, aSupp, aSuppName, shape, index, qvalIdx).run(columns);
        return new Result(aSupp, qvalIdx, qnams, columns);
    }

    /** One matching run; every structure it allocates dies with it. */
    private static final class Matching
    {

        private final IDataTable base;

        private final IDataTable supp;

        private final String suppName;

        private final SuppKeyShape shape;

        private final SuppQnamIndex index;

        private final int qvalIdx;

        private final int baseRows;

        private final Map<ObjKey, Integer> fullIds = new HashMap<>();

        private final Map<ObjKey, Integer> plainIds = new HashMap<>();

        private final int[] fullIdOfRow;

        private final int[] plainIdOfRow;

        /**
         * Per base {@code IDVAR} column, every base row's normalised token ({@code null} for a
         * missing cell) — computed once and shared by every {@code QNAM} (W1 review L7: each
         * qualifier re-read and re-normalised the column per row).
         */
        private final Map<Integer, @Nullable String[]> tokensByColumn = new HashMap<>();

        private final LongFunction<String> rowLabel;

        Matching(IDataTable aBase, IDataTable aSupp, String aSuppName, SuppKeyShape aShape,
                SuppQnamIndex aIndex, int aQvalIdx)
        {
            base = aBase;
            supp = aSupp;
            suppName = aSuppName;
            shape = aShape;
            index = aIndex;
            qvalIdx = aQvalIdx;
            baseRows = Math.toIntExact(aBase.getRowCount());
            rowLabel = rowLabelOf(aSupp);
            fullIdOfRow = new int[baseRows];
            plainIdOfRow = new int[baseRows];
            for (int r = 0; r < baseRows; r++)
            {
                Object[] key = aShape.baseKey(aBase, r);
                fullIdOfRow[r] = idOf(fullIds, new ObjKey(key));
                plainIdOfRow[r] = idOf(plainIds, new ObjKey(aShape.plainOf(key)));
            }
        }


        private static int idOf(Map<ObjKey, Integer> aIds, ObjKey aKey)
        {
            Integer id = aIds.get(aKey);
            if (id == null)
            {
                id = aIds.size();
                aIds.put(aKey, id);
            }
            return id;
        }


        void run(List<QnamColumn> aOut)
        {
            Map<String, Integer> columnByLowerName = new HashMap<>();
            DataTableMeta bm = base.getMetaData();
            for (String qnam : index.qnams())
            {
                if (bm.getColumnIndex(qnam) >= 0)
                {
                    continue; // P-10: the base's own column always wins
                }
                String lower = qnam.toLowerCase(Locale.ROOT);
                Integer earlier = columnByLowerName.get(lower);
                if (earlier != null)
                {
                    QnamColumn first = aOut.get(earlier);
                    aOut.set(earlier,
                            new QnamColumn(first.qnam(), first.label(), null,
                                    suppName + ": qualifiers " + first.qnam() + " and " + qnam
                                            + " differ only in case and cannot both be columns",
                                    List.of()));
                    continue;
                }
                columnByLowerName.put(lower, aOut.size());
                aOut.add(column(qnam));
            }
        }


        private QnamColumn column(String aQnam)
        {
            String label = index.label(aQnam);
            if (qvalIdx < 0)
            {
                return new QnamColumn(aQnam, label, null,
                        suppName + " has no QVAL column, nothing can supply qualifier " + aQnam,
                        List.of());
            }
            if (!shape.subjectUsable())
            {
                return new QnamColumn(aQnam, label, null, suppName
                        + ": no subject key column (USUBJID, POOLID, ...) is usable on both sides,"
                        + " nothing identifies the record qualifier " + aQnam + " belongs to",
                        List.of());
            }
            Problems problems = new Problems();
            // The rows of this QNAM, grouped for probing. A subject-level row keys by its full
            // key; a record-level row by its full key and its IDVAR's token; a study-wide row by
            // its plain key and the token. A row whose key half the base never carries can match
            // no base row and is left out.
            List<List<Keyed>> subjectLevel = new ArrayList<>();
            for (int i = 0; i < fullIds.size(); i++)
            {
                subjectLevel.add(new ArrayList<>(1));
            }
            Map<String, IdvarGroup> recordGroups = new LinkedHashMap<>();
            Map<String, IdvarGroup> studyWideGroups = new LinkedHashMap<>();
            Set<Integer> recordSubjects = new LinkedHashSet<>();
            for (SuppQnamIndex.Entry e : index.entries(aQnam))
            {
                switch (e.level())
                {
                case SUBJECT ->
                {
                    Integer id = fullIds.get(new ObjKey(e.key()));
                    if (id != null)
                    {
                        subjectLevel.get(id).add(new Keyed(e, id));
                    }
                }
                case RECORD ->
                {
                    Integer id = fullIds.get(new ObjKey(e.key()));
                    if (id != null)
                    {
                        recordSubjects.add(id);
                        addToGroup(recordGroups, e, id, problems);
                    }
                }
                case STUDY_WIDE ->
                {
                    Integer id = plainIds.get(new ObjKey(shape.plainOf(e.key())));
                    if (id != null)
                    {
                        addToGroup(studyWideGroups, e, id, problems);
                    }
                }
                }
            }
            // Case 3: a subject-level and a record-level row for one subject — every record of
            // that subject is unsupplied and the issue names both rows.
            Set<Integer> conflicted = new LinkedHashSet<>();
            for (int id : recordSubjects)
            {
                if (!subjectLevel.get(id).isEmpty())
                {
                    conflicted.add(id);
                    for (Keyed k : subjectLevel.get(id))
                    {
                        problems.conflicts.add(k.entry().row());
                    }
                    for (IdvarGroup g : recordGroups.values())
                    {
                        for (Map.Entry<ProbeKey, List<Keyed>> e : g.byKey().entrySet())
                        {
                            if (e.getKey().id() == id)
                            {
                                for (Keyed k : e.getValue())
                                {
                                    problems.conflicts.add(k.entry().row());
                                }
                            }
                        }
                    }
                }
            }
            IDataBufferNumeric map = DataBufferFactory.get().createForRange(-1,
                    Math.max(0, supp.getRowCount() - 1));
            map.setExpectedSize(baseRows);
            List<Keyed> hits = new ArrayList<>(2);
            for (int r = 0; r < baseRows; r++)
            {
                int fid = fullIdOfRow[r];
                if (conflicted.contains(fid))
                {
                    map.addValue(-1);
                    continue;
                }
                hits.clear();
                hits.addAll(subjectLevel.get(fid));
                probe(recordGroups, r, fid, hits);
                probe(studyWideGroups, r, plainIdOfRow[r], hits);
                if (hits.size() == 1)
                {
                    map.addValue(hits.get(0).entry().row());
                }
                else
                {
                    map.addValue(-1);
                    if (hits.size() > 1)
                    {
                        for (Keyed k : hits)
                        {
                            problems.duplicates.add(k.entry().row());
                        }
                    }
                }
            }
            String error = problems.message(suppName, aQnam, KeyedMatcher.nameOf(base), rowLabel);
            return new QnamColumn(aQnam, label, map, error, problems.rows());
        }


        private void addToGroup(Map<String, IdvarGroup> aGroups, SuppQnamIndex.Entry aEntry,
                int aId, Problems aProblems)
        {
            IdvarGroup group = aGroups.get(aEntry.idvar());
            if (group == null)
            {
                int col = base.getMetaData().getColumnIndex(aEntry.idvar());
                boolean numeric = col >= 0
                        && ColumnKind.isNumeric(base.getMetaData().getColumn(col).getType());
                group = new IdvarGroup(col, numeric, new HashMap<>());
                aGroups.put(aEntry.idvar(), group);
            }
            if (group.baseColumn() < 0)
            {
                aProblems.unknownIdvar.computeIfAbsent(aEntry.idvar(), _ -> new ArrayList<>())
                        .add(aEntry.row());
                return; // case 6: the row can qualify no record
            }
            String token = JoinToken.normalize(aEntry.idvarval(), group.numeric());
            group.byKey().computeIfAbsent(new ProbeKey(aId, token == null ? "" : token),
                    _ -> new ArrayList<>(1)).add(new Keyed(aEntry, aId));
        }


        private void probe(Map<String, IdvarGroup> aGroups, int aRow, int aId, List<Keyed> aHits)
        {
            for (IdvarGroup g : aGroups.values())
            {
                if (g.baseColumn() < 0)
                {
                    continue;
                }
                String token = tokens(g.baseColumn(), g.numeric())[aRow];
                if (token == null)
                {
                    continue;
                }
                List<Keyed> candidates = g.byKey().get(new ProbeKey(aId, token));
                if (candidates != null)
                {
                    aHits.addAll(candidates);
                }
            }
        }


        /** The base column's tokens, computed on first use. */
        private @Nullable String[] tokens(int aColumn, boolean aNumeric)
        {
            return tokensByColumn.computeIfAbsent(aColumn, _ ->
            {
                @Nullable
                String[] out = new String[baseRows];
                // tokens repeat heavily (--SEQ restarts per subject): one instance per distinct
                // token keeps the array the only per-row cost
                Map<String, String> canonical = new HashMap<>();
                for (int r = 0; r < baseRows; r++)
                {
                    String cell = RelrecLinks.cell(base, aColumn, r);
                    String token = cell == null ? null : JoinToken.normalize(cell, aNumeric);
                    out[r] = token == null ? null : canonical.computeIfAbsent(token, t -> t);
                }
                return out;
            });
        }
    }

    /**
     * How a message names a supplemental row: its 1-based number, prefixed with its dataset's name
     * when several datasets are stacked.
     */
    private static LongFunction<String> rowLabelOf(IDataTable aSupp)
    {
        if (aSupp instanceof UnionDataTable u && u.memberCount() > 1)
        {
            return row ->
            {
                int m = u.memberOf(row);
                return KeyedMatcher.nameOf(u.member(m)) + " row " + (row - u.memberRowStart(m) + 1);
            };
        }
        return row -> Long.toString(row + 1);
    }

    /** The problems of one qualifier, by category, each naming the offending rows. */
    private static final class Problems
    {

        private final Set<Long> duplicates = new LinkedHashSet<>();

        private final Set<Long> conflicts = new LinkedHashSet<>();

        private final Map<String, List<Long>> unknownIdvar = new LinkedHashMap<>();

        @Nullable
        String message(String aSuppName, String aQnam, String aBaseName,
                LongFunction<String> aRowLabel)
        {
            if (duplicates.isEmpty() && conflicts.isEmpty() && unknownIdvar.isEmpty())
            {
                return null;
            }
            StringBuilder sb = new StringBuilder(aSuppName).append(": qualifier ").append(aQnam);
            if (!duplicates.isEmpty())
            {
                sb.append(": more than one row qualifies one record (rows ")
                        .append(rowList(duplicates, aRowLabel)).append(')');
            }
            if (!conflicts.isEmpty())
            {
                sb.append(": a subject-level and a record-level row qualify one subject (rows ")
                        .append(rowList(conflicts, aRowLabel)).append(')');
            }
            for (Map.Entry<String, List<Long>> e : unknownIdvar.entrySet())
            {
                sb.append(": IDVAR ").append(e.getKey()).append(" is not a column of ")
                        .append(aBaseName).append(" (rows ")
                        .append(rowList(e.getValue(), aRowLabel)).append(')');
            }
            return sb.toString();
        }


        List<Long> rows()
        {
            List<Long> out = new ArrayList<>(duplicates);
            out.addAll(conflicts);
            for (List<Long> l : unknownIdvar.values())
            {
                out.addAll(l);
            }
            return out;
        }


        /** 1-based, at most {@link #MAX_LISTED_ROWS}, then {@code ...}. */
        private static String rowList(Iterable<Long> aRows, LongFunction<String> aRowLabel)
        {
            StringBuilder sb = new StringBuilder();
            int n = 0;
            for (long r : aRows)
            {
                if (n == MAX_LISTED_ROWS)
                {
                    sb.append(", ...");
                    break;
                }
                if (n > 0)
                {
                    sb.append(", ");
                }
                sb.append(aRowLabel.apply(r));
                n++;
            }
            return sb.toString();
        }
    }
}
