package net.cumba.datatable.impl.view;

import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.values.DataValueType;
import org.jspecify.annotations.Nullable;

/**
 * The type of a split-domain {@link UnionDataTable} column whose members declare different types
 * (owner ruling {@code ECT-Q7} ⓐ, 2026-10-16, <i>"I agree to a."</i>;
 * {@code PLAN-bugfix-empty-column-type}).
 *
 * <p>
 * A member column with no value in any row — every cell a missing value or {@code ""},
 * {@link net.cumba.datatable.ColumnContent#isBlank()}, the predicate of {@code SVY OQ11} and
 * {@code ECT-Q5} — has no type of its own: the provider inferred one from values it did not have
 * (an empty XLSX or CSV column loads STRING, an all-{@code NA} R logical STRING, a Parquet null
 * column STRING). Such a member is <b>deferred</b>: the column takes the type of the members that
 * hold a value, and the deferred member reads as if it lacked the column — its cells are the union
 * type's absent-column default ({@code SVY OQ8}: {@code ""} for STRING,
 * {@link net.cumba.datatable.values.MissingValue#MIS} otherwise), and its own cells stay unexposed.
 * </p>
 * <ul>
 * <li>Members that hold a value and still disagree fail the union ({@link Clash}), as before.</li>
 * <li>Every member blank and the types disagreeing: no member is evidence, so the column is STRING,
 * the Char default of an empty column ({@code ECT}).</li>
 * <li>A blank member that already declares the resolved type keeps its own cells.</li>
 * </ul>
 * <p>
 * Asked only for a column whose members already disagree, once per union build: members that agree
 * on every type are never scanned, and a cell read never scans. The union column's metadata is the
 * first valued member's (the first-occurrence rule, over the valued members); for an all-blank
 * column the first STRING member's, else the first member's retyped STRING without its display
 * format.
 * </p>
 *
 * <p>
 * Byte-identical in {@code cumba-datatable-impl} and {@code cumba-oss-datatable-impl}; each twin's
 * {@code UnionDataTable} words its own clash message.
 * </p>
 */
final class UnionColumnTypes
{

    /** The outcome for one disagreeing union column. */
    sealed interface Resolution permits Typed, Clash
    {
    }


    /**
     * The column unions: {@code column} is its metadata, before re-indexing.
     *
     * @param column
     *            the union column's metadata, taken from the member named in the class Javadoc.
     */
    record Typed(DataTableColumnMeta column) implements Resolution
    {
    }


    /**
     * Two members hold values of different types: the union fails.
     *
     * @param first
     *            the index of the first member holding a value in the column.
     * @param other
     *            the index of the first member holding a value of another type.
     */
    record Clash(int first, int other) implements Resolution
    {
    }

    private UnionColumnTypes()
    {
    }


    /**
     * Resolves one union column whose members declare different types, deferring every blank member
     * whose type is not the resolved one: its entry in {@code aMaps} becomes {@code -1}, the
     * absent-member mark.
     *
     * @param aMembers
     *            the member tables.
     * @param aMaps
     *            {@code [member][unionColumn]} → member column index or {@code -1}; the deferred
     *            members' entries for {@code aUnionColumn} are set to {@code -1}, and nothing is
     *            changed on a {@link Clash}.
     * @param aUnionColumn
     *            the union column to resolve.
     * @return the resolution.
     */
    static Resolution resolve(IDataTable[] aMembers, int[][] aMaps, int aUnionColumn)
    {
        boolean[] blank = new boolean[aMembers.length];
        int firstValued = -1;
        for (int m = 0; m < aMembers.length; m++)
        {
            int mc = aMaps[m][aUnionColumn];
            if (mc < 0)
            {
                continue;
            }
            blank[m] = aMembers[m].getColumnContent(mc).isBlank();
            if (blank[m])
            {
                continue;
            }
            if (firstValued < 0)
            {
                firstValued = m;
            }
            else if (typeOf(aMembers, aMaps, firstValued, aUnionColumn) != typeOf(aMembers, aMaps,
                    m, aUnionColumn))
            {
                return new Clash(firstValued, m);
            }
        }

        DataTableColumnMeta column = firstValued >= 0
                ? metaOf(aMembers, aMaps, firstValued, aUnionColumn)
                : allBlankColumn(aMembers, aMaps, aUnionColumn);
        DataValueType type = column.getType();
        for (int m = 0; m < aMembers.length; m++)
        {
            if (aMaps[m][aUnionColumn] >= 0 && blank[m]
                    && typeOf(aMembers, aMaps, m, aUnionColumn) != type)
            {
                aMaps[m][aUnionColumn] = -1;
            }
        }
        return new Typed(column);
    }


    /**
     * The metadata of a column blank in every member: the first STRING member's, else the first
     * member's retyped STRING without its display format (a numeric format means nothing on text).
     */
    private static DataTableColumnMeta allBlankColumn(IDataTable[] aMembers, int[][] aMaps,
            int aUnionColumn)
    {
        @Nullable
        DataTableColumnMeta first = null;
        for (int m = 0; m < aMembers.length; m++)
        {
            if (aMaps[m][aUnionColumn] < 0)
            {
                continue;
            }
            DataTableColumnMeta meta = metaOf(aMembers, aMaps, m, aUnionColumn);
            if (meta.getType() == DataValueType.STRING)
            {
                return meta;
            }
            if (first == null)
            {
                first = meta;
            }
        }
        if (first == null)
        {
            // The caller resolves only a column at least two members declare.
            throw new IllegalStateException("no member declares union column " + aUnionColumn);
        }
        return DataTableColumnMeta.builderFrom(first).type(DataValueType.STRING).displayFormat(null)
                .build();
    }


    private static DataTableColumnMeta metaOf(IDataTable[] aMembers, int[][] aMaps, int aMember,
            int aUnionColumn)
    {
        return aMembers[aMember].getMetaData().getColumn(aMaps[aMember][aUnionColumn]);
    }


    private static DataValueType typeOf(IDataTable[] aMembers, int[][] aMaps, int aMember,
            int aUnionColumn)
    {
        return metaOf(aMembers, aMaps, aMember, aUnionColumn).getType();
    }
}
