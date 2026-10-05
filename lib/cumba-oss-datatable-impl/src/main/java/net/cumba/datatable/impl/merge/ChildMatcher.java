package net.cumba.datatable.impl.merge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.merge.ChildMergeSpec;
import net.cumba.datatable.merge.DatasetResolver;
import net.cumba.datatable.merge.JoinToken;
import net.cumba.datatable.merge.RelrecLinks;
import org.jspecify.annotations.Nullable;

/**
 * The parent matching of a {@code Child: true} primary ({@code PLAN-merged-evaluation-table} §3.7,
 * MQ1): each primary row points at a parent record through {@code (RDOMAIN, IDVAR, IDVARVAL)}; the
 * parent rows whose subject key equals the primary row's and whose {@code IDVAR} column equals
 * {@code IDVARVAL} (as text, {@link JoinToken}) are its matches — every one of them, since a
 * grouping {@code IDVAR} may address many parent records (P-13). One part per parent domain, in
 * first-appearance order over the primary rows (or the one fixed parent).
 */
final class ChildMatcher
{

    /** A parent column the pointer names that the parent lacks. */
    record Issue(String parent, String message, List<Long> primaryRows)
    {
    }

    private final IDataTable primary;

    private final ChildMergeSpec spec;

    private final int rdomainCol;

    private final int idvarCol;

    private final int idvarvalCol;

    private final List<String> parentNames = new ArrayList<>();

    private final List<IDataTable> parents = new ArrayList<>();

    private final List<String> unresolved = new ArrayList<>();

    private final Map<String, Integer> ordinalByDomain = new HashMap<>();

    private final List<ParentIndex> indexes = new ArrayList<>();

    private final Map<String, Issue> issues = new LinkedHashMap<>();

    private final int fixedOrdinal;

    private ChildMatcher(IDataTable aPrimary, ChildMergeSpec aSpec, DatasetResolver aResolver)
    {
        primary = aPrimary;
        spec = aSpec;
        DataTableMeta pm = aPrimary.getMetaData();
        rdomainCol = pm.getColumnIndex(aSpec.getRdomainColumn());
        idvarCol = pm.getColumnIndex(aSpec.getIdvarColumn());
        idvarvalCol = pm.getColumnIndex(aSpec.getIdvarvalColumn());
        String fixed = aSpec.getParentDataset();
        if (fixed != null)
        {
            fixedOrdinal = register(fixed, aResolver);
        }
        else
        {
            fixedOrdinal = -1;
            if (rdomainCol >= 0)
            {
                long rows = aPrimary.getRowCount();
                for (long r = 0; r < rows; r++)
                {
                    String d = RelrecLinks.cell(aPrimary, rdomainCol, r);
                    if (d != null && !d.isBlank() && !ordinalByDomain.containsKey(domainKey(d)))
                    {
                        register(d.strip(), aResolver);
                    }
                }
            }
        }
    }


    private static String domainKey(String aDomain)
    {
        return aDomain.strip().toUpperCase(Locale.ROOT);
    }


    private int register(String aDomain, DatasetResolver aResolver)
    {
        IDataTable parent = aResolver.resolve(aDomain);
        if (parent == null)
        {
            unresolved.add(aDomain);
            ordinalByDomain.put(domainKey(aDomain), -1);
            return -1;
        }
        int ordinal = parents.size();
        parentNames.add(aDomain);
        parents.add(parent);
        indexes.add(new ParentIndex(parent));
        ordinalByDomain.put(domainKey(aDomain), ordinal);
        return ordinal;
    }


    /**
     * Builds the matcher: resolves the parent(s) and prepares the lazily built indexes.
     *
     * @param aPrimary
     *            the child primary.
     * @param aSpec
     *            the spec.
     * @param aResolver
     *            the resolver.
     * @return the matcher.
     */
    static ChildMatcher of(IDataTable aPrimary, ChildMergeSpec aSpec, DatasetResolver aResolver)
    {
        return new ChildMatcher(aPrimary, aSpec, aResolver);
    }


    /** The parent domains, by part ordinal. */
    List<String> parentNames()
    {
        return parentNames;
    }


    /** The parent tables, by part ordinal. */
    List<IDataTable> parents()
    {
        return parents;
    }


    /** The parent names the resolver did not supply. */
    List<String> unresolved()
    {
        return unresolved;
    }


    /** The pointer columns the parents lack, by parent. */
    List<Issue> issues()
    {
        return new ArrayList<>(issues.values());
    }


    /**
     * The part ordinal of a primary row's parent, or {@code -1} when the row names no resolvable
     * parent.
     *
     * @param aRow
     *            the primary row.
     * @return the ordinal or {@code -1}.
     */
    int ordinalOf(int aRow)
    {
        if (fixedOrdinal >= 0 || spec.getParentDataset() != null)
        {
            return fixedOrdinal;
        }
        String d = RelrecLinks.cell(primary, rdomainCol, aRow);
        if (d == null || d.isBlank())
        {
            return -1;
        }
        Integer ordinal = ordinalByDomain.get(domainKey(d));
        return ordinal == null ? -1 : ordinal;
    }


    /**
     * The parent rows a primary row points at, ascending.
     *
     * @param aRow
     *            the primary row.
     * @param aOut
     *            the list to fill (cleared first); empty when the row points at nothing.
     */
    void matchesOf(int aRow, List<Integer> aOut)
    {
        aOut.clear();
        int ordinal = ordinalOf(aRow);
        if (ordinal < 0 || idvarCol < 0 || idvarvalCol < 0)
        {
            return;
        }
        String idvar = RelrecLinks.cell(primary, idvarCol, aRow);
        String idvarval = RelrecLinks.cell(primary, idvarvalCol, aRow);
        if (idvar == null || idvar.isBlank() || idvarval == null || idvarval.isBlank())
        {
            return;
        }
        ParentIndex index = indexes.get(ordinal);
        ParentIndex.ColumnIndex ci = index.forColumn(idvar.strip());
        if (ci == null)
        {
            issues.computeIfAbsent(parentNames.get(ordinal) + "\0" + idvar.strip(),
                    _ -> new Issue(parentNames.get(ordinal),
                            "pointer column " + idvar.strip() + " is not a column of parent "
                                    + parentNames.get(ordinal),
                            new ArrayList<>()))
                    .primaryRows().add((long) aRow);
            return;
        }
        Object[] key = new Object[ci.subjectCols().length + 1];
        for (int i = 0; i < ci.subjectCols().length; i++)
        {
            key[i] = RelrecLinks.keyCell(primary, ci.primarySubjectCols()[i], aRow);
        }
        key[key.length - 1] = java.util.Objects
                .requireNonNull(JoinToken.normalize(idvarval, ci.numeric()));
        List<Integer> rows = ci.rows().get(new ObjKey(key));
        if (rows != null)
        {
            aOut.addAll(rows);
        }
    }

    /** The lazily built per-pointer-column indexes of one parent. */
    private final class ParentIndex
    {

        /**
         * One pointer column's index: the parent rows per (subject key, token). A class, not a
         * record, because its components are arrays.
         */
        private static final class ColumnIndex
        {

            /** The parent's subject-key columns used (present on both sides). */
            private final int[] subjectCols;

            /** The primary's, index-aligned. */
            private final int[] primarySubjectCols;

            /** Whether the pointer column is numeric. */
            private final boolean numeric;

            /** (subject identities..., token) → parent rows, ascending. */
            private final Map<ObjKey, List<Integer>> rows;

            ColumnIndex(int[] aSubjectCols, int[] aPrimarySubjectCols, boolean aNumeric,
                    Map<ObjKey, List<Integer>> aRows)
            {
                subjectCols = aSubjectCols;
                primarySubjectCols = aPrimarySubjectCols;
                numeric = aNumeric;
                rows = aRows;
            }


            int[] subjectCols()
            {
                return subjectCols;
            }


            int[] primarySubjectCols()
            {
                return primarySubjectCols;
            }


            boolean numeric()
            {
                return numeric;
            }


            Map<ObjKey, List<Integer>> rows()
            {
                return rows;
            }
        }

        private final IDataTable parent;

        private final Map<String, java.util.Optional<ColumnIndex>> byColumn = new HashMap<>();

        ParentIndex(IDataTable aParent)
        {
            parent = aParent;
        }


        @Nullable
        ColumnIndex forColumn(String aColumn)
        {
            return byColumn.computeIfAbsent(aColumn.toUpperCase(Locale.ROOT),
                    _ -> java.util.Optional.ofNullable(build(aColumn))).orElse(null);
        }


        private @Nullable ColumnIndex build(String aColumn)
        {
            DataTableMeta pm = parent.getMetaData();
            DataTableMeta cm = primary.getMetaData();
            int col = pm.getColumnIndex(aColumn);
            if (col < 0)
            {
                return null;
            }
            List<Integer> parentSubject = new ArrayList<>();
            List<Integer> primarySubject = new ArrayList<>();
            for (String name : spec.getSubjectKeys())
            {
                int pc = pm.getColumnIndex(name);
                int cc = cm.getColumnIndex(name);
                if (pc >= 0 && cc >= 0)
                {
                    parentSubject.add(pc);
                    primarySubject.add(cc);
                }
            }
            boolean numeric = ColumnKind.isNumeric(pm.getColumn(col).getType());
            Map<ObjKey, List<Integer>> rows = new HashMap<>();
            int n = parentSubject.size();
            long rowCount = parent.getRowCount();
            for (long r = 0; r < rowCount; r++)
            {
                String cell = RelrecLinks.cell(parent, col, r);
                if (cell == null)
                {
                    continue;
                }
                Object[] key = new Object[n + 1];
                for (int i = 0; i < n; i++)
                {
                    key[i] = RelrecLinks.keyCell(parent, parentSubject.get(i), r);
                }
                key[n] = java.util.Objects.requireNonNull(JoinToken.normalize(cell, numeric));
                rows.computeIfAbsent(new ObjKey(key), _ -> new ArrayList<>(1)).add((int) r);
            }
            return new ColumnIndex(parentSubject.stream().mapToInt(Integer::intValue).toArray(),
                    primarySubject.stream().mapToInt(Integer::intValue).toArray(), numeric, rows);
        }
    }
}
