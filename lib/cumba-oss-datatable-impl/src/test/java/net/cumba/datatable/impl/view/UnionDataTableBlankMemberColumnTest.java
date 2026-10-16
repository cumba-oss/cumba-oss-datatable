package net.cumba.datatable.impl.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.cumba.datatable.ColumnContent;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.impl.CachedDataTableColumn;
import net.cumba.datatable.impl.ColumnCachedDataTable;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.MissingValue;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Owner ruling {@code ECT-Q7} ⓐ (2026-10-16, <i>"I agree to a."</i>;
 * {@code PLAN-bugfix-empty-column-type}): in a split-domain union, a member column with no value in
 * any row — every cell a missing value or {@code ""}, {@code ColumnContent.isBlank()} — has no type
 * of its own, so it takes the type of the members that hold a value, and its cells read as that
 * type's absent-column default ({@code SVY OQ8}: {@code ""} for STRING, {@link MissingValue#MIS}
 * otherwise), exactly like a member that lacks the column. Members that hold a value and still
 * disagree keep failing the union; members that are all blank and disagree union as STRING (the
 * Char default of an empty column, {@code ECT}).
 *
 * <p>
 * Byte-identical in {@code cumba-datatable-impl} and {@code cumba-oss-datatable-impl}.
 * </p>
 */
class UnionDataTableBlankMemberColumnTest
{

    /** A column table that counts how often a column's content status is asked. */
    private static final class CountingTable extends ColumnCachedDataTable
    {

        private int contentAsks;

        CountingTable(DataTableMeta aMeta, CachedDataTableColumn[] aColumns)
        {
            super(aMeta, aColumns);
        }


        @Override
        public ColumnContent getColumnContent(int aColumn)
        {
            contentAsks++;
            return super.getColumnContent(aColumn);
        }
    }

    private static DataTableColumnMeta col(String aName, DataValueType aType)
    {
        return DataTableColumnMeta.builder().index(0).name(aName).type(aType).build();
    }


    private static CountingTable table(String aName, DataTableColumnMeta[] aCols, Object[][] aRows)
    {
        CachedDataTableColumn[] columns = new CachedDataTableColumn[aCols.length];
        DataTableColumnMeta[] metas = new DataTableColumnMeta[aCols.length];
        for (int c = 0; c < aCols.length; c++)
        {
            columns[c] = new CachedDataTableColumn(c, aCols[c].getType());
            metas[c] = DataTableColumnMeta.builderFrom(aCols[c]).index(c).build();
        }
        for (Object[] row : aRows)
        {
            for (int c = 0; c < aCols.length; c++)
            {
                columns[c].addElement(row[c]);
            }
        }
        for (CachedDataTableColumn column : columns)
        {
            column.complete();
        }
        DataTableMeta meta = DataTableMeta.builder().name(aName).columns(metas)
                .rowCount(aRows.length).totalRowCount(aRows.length).build();
        return new CountingTable(meta, columns);
    }


    /** A two-column member: {@code USUBJID} (STRING) and {@code aColumn} of the given type. */
    private static CountingTable member(String aName, DataTableColumnMeta aColumn,
            @Nullable Object... aValues)
    {
        Object[][] rows = new Object[aValues.length][];
        for (int r = 0; r < aValues.length; r++)
        {
            rows[r] = new Object[]
            {
                    aName + "-" + r, aValues[r]
            };
        }
        return table(aName, new DataTableColumnMeta[]
        {
                col("USUBJID", DataValueType.STRING), aColumn
        }, rows);
    }


    private static int columnOf(UnionDataTable aUnion, String aName)
    {
        return aUnion.getMetaData().getColumnIndex(aName);
    }


    /**
     * The XLSX shape the ruling was raised for: a questionnaire split whose one member has the
     * result column empty (U1 now loads it STRING, every cell {@code ""}) beside a member holding
     * numbers. The union is DOUBLE, the empty member's rows read {@code MIS} — never its {@code ""}
     * — and the union column's metadata is the valued member's, though the empty member comes
     * first.
     */
    @Test
    void anXlsxEmptyCharMemberBesideADoubleMemberTakesDoubleAndReadsMis()
    {
        CountingTable empty = member("qs1", col("QSSTRESN", DataValueType.STRING), "", "");
        DataTableColumnMeta valued = DataTableColumnMeta.builder().index(0).name("QSSTRESN")
                .type(DataValueType.DOUBLE).label("Numeric Finding").length(8).displayFormat("8.2")
                .nativeType("num").build();
        CountingTable numbers = member("qs2", valued, 1.5, MissingValue.MIS, 3.0);

        UnionDataTable union = new UnionDataTable("QS", empty, numbers);

        int c = columnOf(union, "QSSTRESN");
        DataTableColumnMeta unionCol = union.getMetaData().getColumn(c);
        assertEquals(DataValueType.DOUBLE, unionCol.getType());
        assertEquals("Numeric Finding", unionCol.getLabel());
        assertEquals(8, unionCol.getLength());
        assertEquals("8.2", unionCol.getDisplayFormat());
        assertEquals("num", unionCol.getNativeType());
        assertEquals(c, unionCol.getIndex());
        assertEquals(5, union.getRowCount());
        for (int r = 0; r < 2; r++)
        {
            assertSame(MissingValue.MIS, union.getValue(r, c), "row " + r);
            assertTrue(union.getDataValue(r, c).isMissingOrInvalid(), "row " + r);
            assertTrue(union.isSupplied(r, c), "row " + r);
        }
        assertEquals(1.5, union.getValue(2, c));
        assertSame(MissingValue.MIS, union.getValue(3, c));
        assertEquals(3.0, union.getValue(4, c));
        // the member API reads the deferred column as absent, so a member-bypassing reader agrees
        assertEquals(-1, union.memberColumnOf(0, c));
        assertEquals(1, union.memberColumnOf(1, c));
        // the other columns pass through untouched
        assertEquals("qs1-1", union.getValue(1, columnOf(union, "USUBJID")));
    }


    @Test
    void aMissingOnlyStringMemberBesideALongMemberTakesLong()
    {
        CountingTable missing = member("lb1", col("LBSEQ", DataValueType.STRING), MissingValue.MIS,
                MissingValue.MIS_A, MissingValue.MIS_ERROR);
        CountingTable numbers = member("lb2", col("LBSEQ", DataValueType.LONG), 7L);

        UnionDataTable union = new UnionDataTable("LB", numbers, missing);

        int c = columnOf(union, "LBSEQ");
        assertEquals(DataValueType.LONG, union.getMetaData().getColumn(c).getType());
        assertEquals(7L, union.getValue(0, c));
        for (int r = 1; r < 4; r++)
        {
            assertSame(MissingValue.MIS, union.getValue(r, c), "row " + r);
        }
        assertEquals(-1, union.memberColumnOf(1, c));
    }


    /**
     * The R shape (U3): an all-{@code NA} logical now loads STRING of {@code MissingValue.NA};
     * beside a member whose logical holds values the union is BOOLEAN and the {@code NA} rows read
     * the absent-column default {@code MIS}.
     */
    @Test
    void anAllNaStringMemberBesideABooleanMemberTakesBoolean()
    {
        CountingTable flags = member("ae1", col("AESER", DataValueType.BOOLEAN), true, false);
        CountingTable na = member("ae2", col("AESER", DataValueType.STRING), MissingValue.NA,
                MissingValue.NA);

        UnionDataTable union = new UnionDataTable("AE", flags, na);

        int c = columnOf(union, "AESER");
        assertEquals(DataValueType.BOOLEAN, union.getMetaData().getColumn(c).getType());
        // the valued member's cells pass through as its own raw form (the twins' BOOLEAN buffers
        // differ in it)
        assertEquals(flags.getValue(0, 1), union.getValue(0, c));
        assertEquals(flags.getValue(1, 1), union.getValue(1, c));
        assertFalse(union.getDataValue(0, c).isMissingOrInvalid());
        assertSame(MissingValue.MIS, union.getValue(2, c));
        assertSame(MissingValue.MIS, union.getValue(3, c));
    }


    /**
     * The other direction: a blank numeric member beside text reads the Char default {@code ""}.
     */
    @Test
    void aBlankDoubleMemberBesideAStringMemberTakesStringAndReadsTheEmptyString()
    {
        CountingTable text = member("lb1", col("LBORRES", DataValueType.STRING), "POS");
        CountingTable blank = member("lb2", col("LBORRES", DataValueType.DOUBLE), MissingValue.MIS,
                MissingValue.MIS);

        UnionDataTable union = new UnionDataTable("LB", text, blank);

        int c = columnOf(union, "LBORRES");
        assertEquals(DataValueType.STRING, union.getMetaData().getColumn(c).getType());
        assertEquals("POS", union.getValue(0, c));
        assertEquals("", union.getValue(1, c));
        assertEquals("", union.getValue(2, c));
        assertFalse(union.getDataValue(2, c).isMissingOrInvalid());
    }


    /**
     * A blank member that already has the agreed type keeps its own cells (a {@code ._} stays
     * {@code ._}).
     */
    @Test
    void aBlankMemberOfTheAgreedTypeKeepsItsOwnCells()
    {
        CountingTable empty = member("qs1", col("QSSTRESN", DataValueType.STRING), "");
        CountingTable numbers = member("qs2", col("QSSTRESN", DataValueType.DOUBLE), 2.0);
        CountingTable special = member("qs3", col("QSSTRESN", DataValueType.DOUBLE),
                MissingValue.MIS__);

        UnionDataTable union = new UnionDataTable("QS", empty, numbers, special);

        int c = columnOf(union, "QSSTRESN");
        assertEquals(DataValueType.DOUBLE, union.getMetaData().getColumn(c).getType());
        assertEquals(-1, union.memberColumnOf(0, c));
        assertEquals(1, union.memberColumnOf(2, c));
        assertSame(MissingValue.MIS__, union.getValue(2, c));
    }


    /**
     * Two members that hold values of different types still fail the union, and the message names
     * the two valued members, not the blank one that came first.
     */
    @Test
    void twoValuedMembersOfDifferentTypesStillFailTheUnion()
    {
        CountingTable blank = member("lb0", col("LBSTRESN", DataValueType.BOOLEAN),
                MissingValue.MIS);
        CountingTable numeric = member("lbch", col("LBSTRESN", DataValueType.LONG), 1L);
        CountingTable text = member("lbhe", col("LBSTRESN", DataValueType.STRING), "high");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new UnionDataTable("LB", blank, numeric, text));

        String message = e.getMessage();
        assertTrue(message.contains("LBSTRESN"), message);
        assertTrue(message.contains("lbch"), message);
        assertTrue(message.contains("LONG"), message);
        assertTrue(message.contains("lbhe"), message);
        assertTrue(message.contains("STRING"), message);
        assertFalse(message.contains("lb0"), message);
        assertFalse(message.contains("BOOLEAN"), message);
    }


    /**
     * Every member blank and the types disagree: no member is evidence, so the union column is
     * STRING (the Char default of an empty column) and every cell reads {@code ""} or the STRING
     * member's own blank cell. With no STRING member the metadata is the first member's, retyped
     * STRING and without its (numeric) display format.
     */
    @Test
    void membersThatAreAllBlankAndDisagreeUnionAsString()
    {
        CountingTable blankDouble = member(
                "lb1", DataTableColumnMeta.builder().index(0).name("LBORNRLO")
                        .type(DataValueType.DOUBLE).label("Low").displayFormat("8.2").build(),
                MissingValue.MIS);
        CountingTable blankString = member("lb2", col("LBORNRLO", DataValueType.STRING), "",
                MissingValue.NA);

        UnionDataTable union = new UnionDataTable("LB", blankDouble, blankString);

        int c = columnOf(union, "LBORNRLO");
        assertEquals(DataValueType.STRING, union.getMetaData().getColumn(c).getType());
        assertNull(union.getMetaData().getColumn(c).getDisplayFormat());
        assertEquals("", union.getValue(0, c));
        assertEquals(-1, union.memberColumnOf(0, c));
        assertEquals(1, union.memberColumnOf(1, c));
        assertEquals("", union.getValue(1, c));
        assertSame(MissingValue.NA, union.getValue(2, c));

        CountingTable blankBoolean = member("lb3", col("LBORNRLO", DataValueType.BOOLEAN),
                MissingValue.MIS);
        UnionDataTable noString = new UnionDataTable("LB", blankDouble, blankBoolean);
        DataTableColumnMeta retyped = noString.getMetaData()
                .getColumn(columnOf(noString, "LBORNRLO"));
        assertEquals(DataValueType.STRING, retyped.getType());
        assertEquals("Low", retyped.getLabel());
        assertNull(retyped.getDisplayFormat());
        assertEquals("", noString.getValue(0, columnOf(noString, "LBORNRLO")));
        assertEquals("", noString.getValue(1, columnOf(noString, "LBORNRLO")));
    }


    /** A member with no rows holds no value, so its declared type no longer fails the union. */
    @Test
    void aZeroRowMemberOfAnotherTypeNoLongerFailsTheUnion()
    {
        CountingTable numbers = member("lb1", col("LBSTRESN", DataValueType.DOUBLE), 4.0);
        CountingTable none = member("lb2", col("LBSTRESN", DataValueType.STRING));

        UnionDataTable union = new UnionDataTable("LB", numbers, none);

        assertEquals(DataValueType.DOUBLE,
                union.getMetaData().getColumn(columnOf(union, "LBSTRESN")).getType());
    }


    /**
     * The cost guard: members that agree on every type are never scanned, and a disagreement scans
     * each member's column once per union build.
     */
    @Test
    void agreeingMembersAreNeverScannedAndADisagreementScansOnce()
    {
        CountingTable a = member("lb1", col("LBSTRESN", DataValueType.DOUBLE), 1.0);
        CountingTable b = member("lb2", col("LBSTRESN", DataValueType.DOUBLE), 2.0);
        new UnionDataTable("LB", a, b);
        assertEquals(0, a.contentAsks);
        assertEquals(0, b.contentAsks);

        CountingTable blank = member("lb3", col("LBSTRESN", DataValueType.STRING), "");
        UnionDataTable union = new UnionDataTable("LB", a, blank);
        assertEquals(1, a.contentAsks);
        assertEquals(1, blank.contentAsks);
        int c = columnOf(union, "LBSTRESN");
        union.getValue(0, c);
        union.getValue(1, c);
        assertEquals(1, a.contentAsks, "a cell read never scans");
        assertEquals(1, blank.contentAsks, "a cell read never scans");
    }
}
