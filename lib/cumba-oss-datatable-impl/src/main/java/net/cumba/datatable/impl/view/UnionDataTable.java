package net.cumba.datatable.impl.view;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.ExMsgs;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.help.CDT;
import net.cumba.datatable.impl.AbstractDataTable;
import net.cumba.datatable.values.DataValueSupport;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.IDataValue;
import org.jspecify.annotations.Nullable;

/**
 * A read-only {@link IDataTable} that presents several member tables as one, rows stacked in member
 * order. Columns are the ordered, first-seen union of the members' columns; a cell whose member
 * lacks the column reads as missing ({@link #getDataValue(long, int)} returns a shared MISSING
 * {@link IDataValue}, {@link #getValue(long, int)} returns {@code null}). Built for split CDISC
 * domains ({@code lbch}/{@code lbhe}/{@code lbur} → {@code LB}); copies nothing.
 *
 * <p>
 * {@code getRealRowIndex} is <em>member-local</em> (it forwards to the owning member), so
 * {@link IDataTable#getRowName(long)} and {@link IDataTable#getDisplayRowIndex(long)} are
 * degenerate on a union — two union rows from different members can report the same real row index,
 * and {@code getDisplayRowIndex} returns the first such row. A union is only ever used as a
 * <em>joined</em> table, whose real row index no production consumer reads; consumers needing
 * provenance use {@link #memberOf(long)}. Note also that a downstream {@code RowFindingSlab} built
 * from member-local real row indexes would violate its documented "sorted ascending, distinct"
 * invariant — another reason the union must never become a primary/eval table.
 * </p>
 *
 * <p>
 * Note on {@link IDataTable#hashCodeAt(long, int)}: it is deliberately not overridden. A
 * member-absent cell returns {@code null} from {@code getValue} and therefore hashes to {@code 0},
 * whereas a present-but-missing cell hashes via {@code MissingValue.hashCodeStable()} — the same
 * asymmetry {@code PolymorphicMergedColumn} exhibits, and harmless for joins because key hashing
 * excludes missing-key rows before it ever compares hashes.
 * </p>
 *
 * <p>
 * Guards (checked in the constructor, all {@link IllegalArgumentException}): at least one member;
 * no {@code null} member or member metadata; members must agree on
 * {@link DataTableMeta#isColumnNameCaseSensitive()} (a <b>new</b> guard — {@link MergeDataTable}
 * tolerates disagreement by ANDing the flags, but a row-stacking union cannot map columns across
 * members under two different equality rules); no member may report an unknown row count
 * ({@code -1}, permitted by {@link IDataTable#getRowCount()}); the summed row count must fit in
 * {@code int} (the engine narrows row counts to {@code int} throughout — mirrors
 * {@code ChildMatchPreMerger}'s parent guard); and members must agree on each shared column's
 * {@link DataValueType} (a type clash across members fails the union — the caller maps it to a
 * rule-level ERROR rather than coercing values).
 * </p>
 */
public final class UnionDataTable extends AbstractDataTable
{

    /**
     * Table-level custom metadata key listing the member table names, comma-joined in member order
     * (e.g. {@code "lbch,lbhe,lbur"}). Naming follows {@link MergeDataTable#META_MERGED_FROM} — a
     * <em>column</em>-level convention — rather than the {@code META_KEY_*} table-level keys of
     * {@code DataTableMetaSupport}; chosen deliberately so the two view classes read as a family.
     */
    public static final String META_UNION_OF = "UNION_OF";

    /**
     * Shared "the matched member lacks this column" value — the house idiom (see
     * {@code PolymorphicMergedColumn}); column-type-independent, so no per-column variant is
     * needed. Never {@code null}, and {@code isMissingOrInvalid()} is {@code true}.
     */
    private static final IDataValue MISSING_VALUE = DataValueSupport.getAsDataValue(null,
            DataValueType.MISSING);

    /** The member tables, rows stacked in this order. Defensive copy of the constructor arg. */
    private final IDataTable[] members;

    /**
     * {@code rowOffsets[i]} = first union row of member {@code i}; {@code [members.length]} =
     * total.
     */
    private final long[] rowOffsets;

    /**
     * {@code memberColOfUnionCol[member][unionCol]} → member column index, or {@code -1} when
     * absent.
     */
    private final int[][] memberColOfUnionCol;

    /**
     * Creates the union of the given member tables under the given table name.
     *
     * @param aName
     *            the union's table name — for a split CDISC domain, the domain code (e.g.
     *            {@code LB}). The union's metadata is built fresh: it does <b>not</b> inherit
     *            member 0's {@code tableURI} or custom metadata.
     * @param aMembers
     *            the member tables, in the row-stacking order.
     * @throws IllegalArgumentException
     *             if the members do not meet the requirements listed in the class Javadoc.
     */
    public UnionDataTable(String aName, IDataTable... aMembers) throws IllegalArgumentException
    {
        Objects.requireNonNull(aName, "aName");
        if (CDT.isEmptyOrNull(aMembers))
        {
            throw new IllegalArgumentException("At least 1 member table has to be given!");
        }
        this.members = Arrays.copyOf(aMembers, aMembers.length);
        validateMembers(this.members);

        this.rowOffsets = new long[this.members.length + 1];
        long total = 0;
        for (int i = 0; i < this.members.length; i++)
        {
            rowOffsets[i] = total;
            total += this.members[i].getRowCount();
            if (total > Integer.MAX_VALUE)
            {
                throw new IllegalArgumentException(
                        "member row counts sum past the supported int range (%d rows and counting at member %s)"
                                .formatted(total, nameOf(this.members[i])));
            }
        }
        rowOffsets[this.members.length] = total;

        List<DataTableColumnMeta> unionCols = new ArrayList<>();
        this.memberColOfUnionCol = buildColumnMap(this.members, unionCols);

        // MergeDataTable idiom: assign fields first, setMetaData(...) LAST (AbstractDataTable has
        // no meta-taking constructor).
        setMetaData(createMetaData(aName, this.members, unionCols, total));
    }


    /** Null-member / null-meta / case-sensitivity / unknown-row-count guards. */
    private static void validateMembers(IDataTable[] aMembers)
    {
        Boolean caseSensitive = null;
        for (int i = 0; i < aMembers.length; i++)
        {
            IDataTable t = aMembers[i];
            if (t == null)
            {
                throw new IllegalArgumentException(
                        "Members must not contain any null references. Found null at index %d."
                                .formatted(i));
            }
            DataTableMeta m = t.getMetaData();
            if (m == null)
            {
                throw new IllegalArgumentException(
                        "Members must contain metadata. Found null metadata at index %d."
                                .formatted(i));
            }
            if (t.getRowCount() < 0)
            {
                throw new IllegalArgumentException(
                        "member %s reports an unknown row count (%d); a union needs every member's row count"
                                .formatted(nameOf(t), t.getRowCount()));
            }
            if (caseSensitive == null)
            {
                caseSensitive = m.isColumnNameCaseSensitive();
            }
            else if (caseSensitive != m.isColumnNameCaseSensitive())
            {
                throw new IllegalArgumentException(
                        "members disagree on column-name case-sensitivity (member %s: %s, expected %s)"
                                .formatted(nameOf(t), m.isColumnNameCaseSensitive(),
                                        caseSensitive));
            }
        }
    }


    /**
     * First-seen ordered column union; fills {@code aUnionCols} and returns the per-member
     * union-column → member-column map. A shared column whose {@link DataValueType} differs between
     * members fails the union.
     */
    private static int[][] buildColumnMap(IDataTable[] aMembers,
            List<DataTableColumnMeta> aUnionCols)
    {
        boolean caseSensitive = aMembers[0].getMetaData().isColumnNameCaseSensitive();
        Map<String, Integer> unionIndexOfName = new HashMap<>();
        List<DataTableColumnMeta> firstOccurrence = new ArrayList<>();
        List<String> firstOccurrenceMember = new ArrayList<>();
        List<int[]> memberMaps = new ArrayList<>();

        for (IDataTable t : aMembers)
        {
            DataTableMeta m = t.getMetaData();
            for (int c = 0; c < m.getColumnCount(); c++)
            {
                DataTableColumnMeta cm = m.getColumn(c);
                String key = caseSensitive ? cm.getName() : cm.getName().toLowerCase(Locale.ROOT);
                Integer existing = unionIndexOfName.get(key);
                if (existing == null)
                {
                    unionIndexOfName.put(key, aUnionCols.size());
                    aUnionCols.add(
                            DataTableColumnMeta.builderFrom(cm).index(aUnionCols.size()).build());
                    firstOccurrence.add(cm);
                    firstOccurrenceMember.add(nameOf(t));
                }
                else
                {
                    DataTableColumnMeta first = firstOccurrence.get(existing);
                    if (first.getType() != cm.getType())
                    {
                        throw new IllegalArgumentException(
                                "members disagree on column %s (%s:%s vs %s:%s)".formatted(
                                        first.getName(), firstOccurrenceMember.get(existing),
                                        first.getType(), nameOf(t), cm.getType()));
                    }
                }
            }
        }

        for (IDataTable t : aMembers)
        {
            DataTableMeta m = t.getMetaData();
            int[] map = new int[aUnionCols.size()];
            Arrays.fill(map, -1);
            for (int c = 0; c < m.getColumnCount(); c++)
            {
                String key = caseSensitive ? m.getColumn(c).getName()
                        : m.getColumn(c).getName().toLowerCase(Locale.ROOT);
                // The first pass registered every key, so the lookup cannot miss.
                map[Objects.requireNonNull(unionIndexOfName.get(key))] = c;
            }
            memberMaps.add(map);
        }
        return memberMaps.toArray(int[][]::new);
    }


    private static DataTableMeta createMetaData(String aName, IDataTable[] aMembers,
            List<DataTableColumnMeta> aUnionCols, long aTotalRows)
    {
        StringJoiner memberNames = new StringJoiner(",");
        for (IDataTable t : aMembers)
        {
            memberNames.add(nameOf(t));
        }
        // Built fresh (no builderFrom(member 0)): the union must not inherit member 0's tableURI
        // or custom metadata — DataTableMetaBuilder.addMetaData keeps the FIRST value for a
        // duplicate key, so an inherited UNION_OF would shadow the one added here.
        return DataTableMeta.builder()//
                .name(aName)//
                .columnNameCaseSensitive(aMembers[0].getMetaData().isColumnNameCaseSensitive())//
                .setColumns(aUnionCols)//
                .rowCount(aTotalRows)//
                .totalRowCount(aTotalRows)//
                .addMetaData(META_UNION_OF, memberNames.toString())//
                .build();
    }


    private static String nameOf(IDataTable aTable)
    {
        DataTableMeta meta = aTable.getMetaData();
        String name = meta != null ? meta.getName() : null;
        return name != null ? name : "?";
    }


    /**
     * The member index holding union row {@code aRow}.
     *
     * @param aRow
     *            the 0-based union row index.
     * @return the 0-based member index.
     * @throws IndexOutOfBoundsException
     *             if {@code aRow} is not a valid union row index.
     */
    public int memberOf(long aRow) throws IndexOutOfBoundsException
    {
        ensureValidRow(aRow);
        int m = Arrays.binarySearch(rowOffsets, aRow);
        if (m < 0)
        {
            m = -m - 2;
        }
        else
        {
            // An exact offset hit may sit on a zero-row member (equal consecutive offsets); the
            // row belongs to the first following member that actually has rows.
            while (rowOffsets[m + 1] == aRow)
            {
                m++;
            }
        }
        return m;
    }


    @Override
    public long getRowCount()
    {
        return rowOffsets[members.length];
    }


    @Override
    public long getRealRowIndex(long aRowIndex) throws IndexOutOfBoundsException
    {
        int m = memberOf(aRowIndex);
        return members[m].getRealRowIndex(aRowIndex - rowOffsets[m]);
    }


    @Override
    public @Nullable Object getValue(long aRow, int aColumn) throws IndexOutOfBoundsException
    {
        int m = memberOf(aRow); // bounds-checks the row
        checkColumn(aColumn);
        int mc = memberColOfUnionCol[m][aColumn];
        return mc < 0 ? null : members[m].getValue(aRow - rowOffsets[m], mc);
    }


    @Override
    public IDataValue getDataValue(long aRow, int aColumn)
    {
        int m = memberOf(aRow); // bounds-checks the row
        checkColumn(aColumn);
        int mc = memberColOfUnionCol[m][aColumn];
        return mc < 0 ? MISSING_VALUE : members[m].getDataValue(aRow - rowOffsets[m], mc);
    }


    private void checkColumn(int aColumn)
    {
        int colCount = getColumnCount();
        if (aColumn < 0 || aColumn >= colCount)
        {
            throw new IndexOutOfBoundsException(
                    ExMsgs.indexOutOfBounds("column", aColumn, 0, colCount));
        }
    }

}
