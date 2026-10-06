package net.cumba.datatable.impl.merge;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.merge.Cardinality;
import net.cumba.datatable.merge.CardinalityViolationException;
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
 *
 * <p>
 * An authored {@code Cardinality} is checked like a keyed partner's (P-16, W1 review M4): a parent
 * side declared {@code 1} is a pointer posting holding two parent records, a primary side declared
 * {@code 1} two primary rows carrying one pointer. The IG's {@code n:m} (P-13) checks nothing.
 * </p>
 */
final class ChildMatcher
{

    private static final Logger LOGGER = System.getLogger(ChildMatcher.class.getName());

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
     * A primary row's complete pointer: its parent, pointer column and probe key. A class, not a
     * record, because its key is an array.
     */
    private static final class Pointer
    {

        private final int ordinal;

        private final String idvar;

        private final ParentIndex.@Nullable ColumnIndex index;

        private final Object @Nullable [] key;

        Pointer(int aOrdinal, String aIdvar, ParentIndex.@Nullable ColumnIndex aIndex,
                Object @Nullable [] aKey)
        {
            ordinal = aOrdinal;
            idvar = aIdvar;
            index = aIndex;
            key = aKey;
        }


        int ordinal()
        {
            return ordinal;
        }


        String idvar()
        {
            return idvar;
        }


        ParentIndex.@Nullable ColumnIndex index()
        {
            return index;
        }


        Object @Nullable [] key()
        {
            return key;
        }
    }

    /**
     * The pointer of a primary row, or {@code null} when it names no resolvable parent or leaves
     * {@code IDVAR} / {@code IDVARVAL} blank. The column index is {@code null} when the parent
     * lacks the pointer column, the key then too.
     */
    private @Nullable Pointer pointerOf(int aRow)
    {
        int ordinal = ordinalOf(aRow);
        if (ordinal < 0 || idvarCol < 0 || idvarvalCol < 0)
        {
            return null;
        }
        String idvar = RelrecLinks.cell(primary, idvarCol, aRow);
        String idvarval = RelrecLinks.cell(primary, idvarvalCol, aRow);
        if (idvar == null || idvar.isBlank() || idvarval == null || idvarval.isBlank())
        {
            return null;
        }
        ParentIndex.ColumnIndex ci = indexes.get(ordinal).forColumn(idvar.strip());
        if (ci == null)
        {
            return new Pointer(ordinal, idvar.strip(), null, null);
        }
        Object[] key = new Object[ci.subjectCols().length + 1];
        for (int i = 0; i < ci.subjectCols().length; i++)
        {
            key[i] = RelrecLinks.keyCell(primary, ci.primarySubjectCols()[i], aRow);
        }
        key[key.length - 1] = java.util.Objects
                .requireNonNull(JoinToken.normalize(idvarval, ci.numeric()));
        return new Pointer(ordinal, idvar.strip(), ci, key);
    }


    /**
     * The parent rows a primary row points at, ascending.
     *
     * @param aRow
     *            the primary row.
     * @param aOut
     *            the list to fill (cleared first); empty when the row points at nothing.
     */
    void matchesOf(int aRow, IntList aOut)
    {
        aOut.clear();
        Pointer ptr = pointerOf(aRow);
        if (ptr == null)
        {
            return;
        }
        ParentIndex.ColumnIndex ci = ptr.index();
        Object[] key = ptr.key();
        if (ci == null || key == null)
        {
            int ordinal = ptr.ordinal();
            issues.computeIfAbsent(parentNames.get(ordinal) + "\0" + ptr.idvar(),
                    _ -> new Issue(parentNames.get(ordinal),
                            "pointer column " + ptr.idvar() + " is not a column of parent "
                                    + parentNames.get(ordinal),
                            new ArrayList<>()))
                    .primaryRows().add((long) aRow);
            return;
        }
        IntList rows = ci.rows().get(new ObjKey(key));
        if (rows != null)
        {
            for (int i = 0; i < rows.size(); i++)
            {
                aOut.add(rows.get(i));
            }
        }
    }


    /**
     * Checks the authored cardinality (W1 review M4); the IG's {@code n:m} (unauthored, P-13)
     * checks nothing. An authored value other than {@code n:m} wins and is logged as disagreeing
     * with the IG (MET #12 (c)).
     *
     * @throws CardinalityViolationException
     *             naming the dataset, the pointer and the first two rows sharing it.
     */
    void checkCardinality()
    {
        Cardinality authored = spec.getCardinality();
        if (authored == null || authored == Cardinality.MANY_TO_MANY)
        {
            return;
        }
        LOGGER.log(Level.WARNING,
                "{0}: the authored Cardinality {1} of its Child entry disagrees with the IG''s n:m"
                        + " (a pointer may address a group of parent records); it is checked",
                KeyedMatcher.nameOf(primary), authored.text());
        Map<ObjKey, Integer> firstPrimary = new HashMap<>();
        int rows = Math.toIntExact(primary.getRowCount());
        for (int r = 0; r < rows; r++)
        {
            Pointer ptr = pointerOf(r);
            if (ptr == null || ptr.key() == null || !authored.leftUnique())
            {
                continue;
            }
            Object[] key = java.util.Arrays.copyOf(ptr.key(), ptr.key().length + 2);
            key[key.length - 2] = ptr.ordinal();
            key[key.length - 1] = ptr.idvar().toUpperCase(Locale.ROOT);
            Integer first = firstPrimary.putIfAbsent(new ObjKey(key), r);
            if (first != null)
            {
                throw new CardinalityViolationException(
                        authored, KeyedMatcher.nameOf(primary), "(" + parentNames.get(ptr.ordinal())
                                + "." + ptr.idvar() + "=" + ptr.key()[ptr.key().length - 1] + ")",
                        first, r);
            }
        }
        if (!authored.rightUnique())
        {
            return;
        }
        for (int ordinal = 0; ordinal < indexes.size(); ordinal++)
        {
            for (ParentIndex.ColumnIndex ci : indexes.get(ordinal).built())
            {
                int[] dup = ci.firstDuplicate();
                if (dup != null)
                {
                    throw new CardinalityViolationException(authored,
                            KeyedMatcher.nameOf(parents.get(ordinal)),
                            "(" + ci.column() + "=" + ci.firstDuplicateToken() + ")", dup[0],
                            dup[1]);
                }
            }
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
            private final Map<ObjKey, IntList> rows;

            /** The pointer column's name on the parent. */
            private final String column;

            /** The first two parent rows sharing a posting, in parent order, or {@code null}. */
            private final int @Nullable [] firstDuplicate;

            /** That posting's token. */
            private final @Nullable String firstDuplicateToken;

            @SuppressWarnings("checkstyle:ParameterNumber")
            ColumnIndex(int[] aSubjectCols, int[] aPrimarySubjectCols, boolean aNumeric,
                    Map<ObjKey, IntList> aRows, String aColumn, int @Nullable [] aFirstDuplicate,
                    @Nullable String aFirstDuplicateToken)
            {
                subjectCols = aSubjectCols;
                primarySubjectCols = aPrimarySubjectCols;
                numeric = aNumeric;
                rows = aRows;
                column = aColumn;
                firstDuplicate = aFirstDuplicate;
                firstDuplicateToken = aFirstDuplicateToken;
            }


            String column()
            {
                return column;
            }


            int @Nullable [] firstDuplicate()
            {
                return firstDuplicate;
            }


            @Nullable
            String firstDuplicateToken()
            {
                return firstDuplicateToken;
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


            Map<ObjKey, IntList> rows()
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


        /** The column indexes built so far (the pointer columns some primary row names). */
        List<ColumnIndex> built()
        {
            List<ColumnIndex> out = new ArrayList<>();
            for (java.util.Optional<ColumnIndex> ci : byColumn.values())
            {
                ci.ifPresent(out::add);
            }
            out.sort(java.util.Comparator.comparing(ColumnIndex::column));
            return out;
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
            Map<ObjKey, IntList> rows = new HashMap<>();
            int n = parentSubject.size();
            long rowCount = parent.getRowCount();
            int[] firstDuplicate = null;
            String firstDuplicateToken = null;
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
                String token = java.util.Objects.requireNonNull(JoinToken.normalize(cell, numeric));
                key[n] = token;
                IntList posting = rows.computeIfAbsent(new ObjKey(key), _ -> new IntList(1));
                posting.add((int) r);
                if (firstDuplicate == null && posting.size() == 2)
                {
                    firstDuplicate = new int[]
                    {
                            posting.get(0), posting.get(1)
                    };
                    firstDuplicateToken = token;
                }
            }
            return new ColumnIndex(parentSubject.stream().mapToInt(Integer::intValue).toArray(),
                    primarySubject.stream().mapToInt(Integer::intValue).toArray(), numeric, rows,
                    pm.getColumn(col).getName(), firstDuplicate, firstDuplicateToken);
        }
    }
}
