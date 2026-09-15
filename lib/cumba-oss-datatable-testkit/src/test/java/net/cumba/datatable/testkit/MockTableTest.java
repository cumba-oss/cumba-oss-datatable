package net.cumba.datatable.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.NoSuchElementException;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.IDataValue;
import net.cumba.datatable.values.MissingValue;
import org.junit.jupiter.api.Test;

/**
 * Behavioural pins for {@link MockTable}.
 *
 * <p>
 * <b>Every assertion here checks a VALUE, never merely non-null.</b> This class faces a mutation
 * gate ({@code pitest.mutation.target} 55) that is first exercised in CI, two steps downstream,
 * with no local feedback loop — and a happy-path {@code assertNotNull} lets essentially every
 * setter mutation survive. {@code MockTable} is a fluent builder, so the mutants that matter are
 * exactly "this setter stored nothing" and "this stub answered the default".
 * </p>
 */
class MockTableTest
{

    // ---- column values and row count ---------------------------------

    @Test
    void col_storesStringValuesAndRowCount()
    {
        IDataTable t = MockTable.of().col("USUBJID", "S1", "S2", "S3").build();

        assertEquals(3L, t.getRowCount());
        assertEquals(1, t.getMetaData().getColumnCount());
        assertEquals("S1", t.getValue(0, 0));
        assertEquals("S2", t.getValue(1, 0));
        assertEquals("S3", t.getValue(2, 0));
    }


    @Test
    void col_cellsAreStringTypedAndRenderThemselves()
    {
        IDataTable t = MockTable.of().col("AETERM", "HEADACHE").build();
        IDataValue dv = t.getDataValue(0, 0);

        assertEquals(DataValueType.STRING, dv.getType());
        assertEquals("HEADACHE", dv.getValue());
        assertEquals("HEADACHE", dv.getValueAsString());
        assertFalse(dv.isMissingOrInvalid());
    }


    @Test
    void col_nullCellIsMissingAndRendersEmpty()
    {
        IDataTable t = MockTable.of().col("AETERM", "X", null).build();
        IDataValue dv = t.getDataValue(1, 0);

        assertTrue(dv.isMissingOrInvalid());
        assertNull(dv.getValue());
        assertEquals("", dv.getValueAsString());
        assertEquals(DataValueType.MISSING, dv.getType());
        assertTrue(Double.isNaN(dv.getValueAsDouble()));
    }


    @Test
    void col_multipleColumnsKeepInsertionOrderAsColumnIndex()
    {
        IDataTable t = MockTable.of().col("A", "a1").col("B", "b1").col("C", "c1").build();
        DataTableMeta meta = t.getMetaData();

        assertEquals(3, meta.getColumnCount());
        assertEquals(0, meta.getColumnIndex("A"));
        assertEquals(1, meta.getColumnIndex("B"));
        assertEquals(2, meta.getColumnIndex("C"));
        assertEquals("b1", t.getValue(0, 1));
    }

    // ---- typed numeric columns ---------------------------------------


    @Test
    void colLong_cellsReportLongTypeAndBoxedValue()
    {
        IDataTable t = MockTable.of().colLong("AESEQ", 1L, 2L).build();
        IDataValue dv = t.getDataValue(1, 0);

        assertEquals(DataValueType.LONG, dv.getType());
        assertEquals(2L, dv.getValue());
        assertEquals("2", dv.getValueAsString());
        assertEquals(2.0d, dv.getValueAsDouble());
        assertFalse(dv.isMissingOrInvalid());
    }


    @Test
    void colLong_nullCellIsAMissingValueJustLikeARealNumericBuffer()
    {
        // ⚠ Changed 2026-09-01. This used to assert "" / null, i.e. that colLong's missing cell
        // was DISTINGUISHABLE from colSasMissing's. That distinction does not exist in a real
        // table: a typed numeric buffer stores a MISSING_SENTINEL and DataBufferInt.getValue
        // answers MissingValue.MIS, which renders "." -- there is no way to put a "null that is
        // not missing" into an int[]. The old behaviour is what made fold-detection assertions
        // vacuous through colLong, which colSasMissing's javadoc used to warn about.
        IDataValue dv = MockTable.of().colLong("AESEQ", (Long) null).build().getDataValue(0, 0);

        assertTrue(dv.isMissingOrInvalid());
        assertEquals(".", dv.getValueAsString());
        assertEquals(MissingValue.MIS, dv.getValue());
        assertTrue(Double.isNaN(dv.getValueAsDouble()));
        assertEquals(DataValueType.MISSING, dv.getType());
    }


    @Test
    void colDouble_nullCellIsAMissingValueToo()
    {
        IDataValue dv = MockTable.of().colDouble("VSSTRESN", (Double) null).build().getDataValue(0,
                0);

        assertTrue(dv.isMissingOrInvalid());
        assertEquals(".", dv.getValueAsString());
        assertEquals(MissingValue.MIS, dv.getValue());
    }


    @Test
    void colDouble_cellsReportDoubleTypeAndBoxedValue()
    {
        IDataValue dv = MockTable.of().colDouble("VSSTRESN", 36.6d).build().getDataValue(0, 0);

        assertEquals(DataValueType.DOUBLE, dv.getType());
        assertEquals(36.6d, dv.getValue());
        assertEquals("36.6", dv.getValueAsString());
        assertEquals(36.6d, dv.getValueAsDouble());
    }

    // ---- SAS missing markers -----------------------------------------


    @Test
    void colSasMissing_nullCellRendersADotAndCarriesMissingValue()
    {
        IDataValue dv = MockTable.of().colSasMissing("LBSTRESN", (String) null).build()
                .getDataValue(0, 0);

        assertTrue(dv.isMissingOrInvalid());
        assertEquals(".", dv.getValueAsString());
        assertEquals(MissingValue.MIS, dv.getValue());
        assertEquals(DataValueType.MISSING, dv.getType());
        assertTrue(Double.isNaN(dv.getValueAsDouble()));
    }


    @Test
    void colSasMissing_presentCellIsLongTypedAndParsesAsDouble()
    {
        IDataValue dv = MockTable.of().colSasMissing("LBSTRESN", "42").build().getDataValue(0, 0);

        assertFalse(dv.isMissingOrInvalid());
        // ⚑ 42L, not "42". A LONG column hands back a boxed Long; it used to hand back the raw
        // String out of a LONG-typed column, which no real buffer can do.
        assertEquals(42L, dv.getValue());
        assertEquals("42", dv.getValueAsString());
        assertEquals(DataValueType.LONG, dv.getType());
        assertEquals(42.0d, dv.getValueAsDouble());
    }


    @Test
    void colSasMissing_unparseablePresentCellIsNaNNotAnException()
    {
        IDataValue dv = MockTable.of().colSasMissing("LBSTRESN", "NOT-A-NUMBER").build()
                .getDataValue(0, 0);

        assertEquals("NOT-A-NUMBER", dv.getValueAsString());
        assertTrue(Double.isNaN(dv.getValueAsDouble()));
    }

    // ---- blankness fast paths ----------------------------------------


    @Test
    void missingAndBlankFastPathsAgreeWithTheCellValues()
    {
        // The stubs these exercise exist because Mockito answers an unstubbed default method
        // `false`, which silently turned collected values from [1, 3] into ["", ""] once.
        IDataTable t = MockTable.of().col("X", "v", "", null).build();

        assertFalse(t.isMissingOrNull(0, 0));
        assertFalse(t.isEmptyOrMissing(0, 0));

        assertFalse(t.isMissingOrNull(1, 0));
        assertTrue(t.isEmptyOrMissing(1, 0));

        assertTrue(t.isMissingOrNull(2, 0));
        assertTrue(t.isEmptyOrMissing(2, 0));

        assertEquals("v", t.getColumn(0).getValue(0));
        assertTrue(t.getColumn(0).isEmptyOrMissing(1));
    }


    @Test
    void outOfRangeRowThrowsLikeARealColumn()
    {
        // ⚠ This test used to be outOfRangeRowReadsAsBlankRatherThanThrowing, and it PINNED a
        // shape no real column produces. CachedDataTableColumn opens every accessor with
        // ensureValidRow(aRow) and ColumnCachedDataTable re-checks aRow against getRowCount(),
        // so a past-the-end read throws IndexOutOfBoundsException -- it does not answer blank.
        // A downstream test that walked one row too far passed against the mock and would have
        // failed against every real table. Changed 2026-09-11 with the mock.
        IDataTable t = MockTable.of().col("X", "v").build();

        assertThrows(IndexOutOfBoundsException.class, () -> t.getColumn(0).getValue(9));
        assertThrows(IndexOutOfBoundsException.class, () -> t.getColumn(0).isMissingOrNull(9));
        assertThrows(IndexOutOfBoundsException.class, () -> t.getColumn(0).isEmptyOrMissing(9));
        assertThrows(IndexOutOfBoundsException.class, () -> t.getColumn(0).hashCodeAt(9));
        assertThrows(IndexOutOfBoundsException.class, () -> t.getColumn(0).getDataValue(9));
        assertThrows(IndexOutOfBoundsException.class, () -> t.getColumn(0).getValue(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> t.getColumn(0).isMissingOrNull(-1));
        // ... and the same through the TABLE view, which in a real table is literally the same
        // call (ColumnCachedDataTable.getValue(row, col) is getColumn(col).getValue(row)).
        assertThrows(IndexOutOfBoundsException.class, () -> t.getValue(9, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> t.getDataValue(9, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> t.hashCodeAt(9, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> t.isEmptyOrMissing(9, 0));
    }

    // ---- column metadata ---------------------------------------------


    @Test
    void colMeta_setsLabelLengthAndFormat()
    {
        IDataTable t = MockTable.of().col("AESTDTC", "2020-01-01")
                .colMeta("AESTDTC", "Start Date", 19, "ISO8601").build();
        DataTableColumnMeta cm = t.getMetaData().getOptionalColumn("AESTDTC");

        assertEquals("AESTDTC", cm.getName());
        assertEquals(0, cm.getIndex());
        assertEquals("Start Date", cm.getLabel());
        assertEquals(19, cm.getLength());
        assertEquals("ISO8601", cm.getDisplayFormat());
        assertEquals(DataValueType.STRING, cm.getType());
    }


    @Test
    void colMeta_nullLabelNullFormatAndNonPositiveLengthLeaveTheDefaults()
    {
        IDataTable t = MockTable.of().col("A", "x").colMeta("A", null, 0, null).build();
        DataTableColumnMeta cm = t.getMetaData().getOptionalColumn("A");

        assertNull(cm.getLabel());
        assertNull(cm.getDisplayFormat());
        assertEquals(0, cm.getLength());
    }


    @Test
    void colMeta_negativeLengthIsAlsoIgnored()
    {
        IDataTable t = MockTable.of().col("A", "x").colMeta("A", "L", -5, "F").build();

        assertEquals(0, t.getMetaData().getOptionalColumn("A").getLength());
    }


    @Test
    void colLong_columnMetaReportsLongType()
    {
        IDataTable t = MockTable.of().colLong("N", 1L).build();

        assertEquals(DataValueType.LONG, t.getMetaData().getOptionalColumn("N").getType());
    }

    // ---- column-name resolution --------------------------------------


    @Test
    void unknownColumnIsMinusOneAndNull()
    {
        IDataTable t = MockTable.of().col("A", "x").build();

        assertEquals(-1, t.getMetaData().getColumnIndex("NOPE"));
        assertNull(t.getMetaData().getOptionalColumn("NOPE"));
    }


    @Test
    void tableViewAndMetaViewAgreeOnColumnCountAndOnAnUnknownName()
    {
        // ⚠ Both are DEFAULT methods on IDataTable, so Mockito answered 0 for getColumnCount()
        // and 0 -- i.e. "column 0" -- for the index of a name the table does not have, while the
        // meta view answered 2 and -1. LibraryValidator writes table.getColumnCount() into every
        // validation-report record, so each one built over a MockTable recorded zero columns.
        IDataTable t = MockTable.of().col("A", "x").col("B", "y").build();

        assertEquals(2, t.getColumnCount());
        assertEquals(t.getMetaData().getColumnCount(), t.getColumnCount());
        assertEquals(1, t.getColumnIndex("B"));
        assertEquals(-1, t.getColumnIndex("NOPE"));
        assertEquals(t.getMetaData().getColumnIndex("NOPE"), t.getColumnIndex("NOPE"));
    }


    @Test
    void tableGetDataValueDelegatesToTheColumnView()
    {
        // IDataTable.getDataValue(row, col) is a DEFAULT method, `getColumn(col).getDataValue
        // (row)`, and Mockito does NOT run an unstubbed default method -- so MockTable stubs the
        // table view as well, from the SAME cells array as the column view. ⛔ Do not "simplify"
        // by deleting that stub on the strength of a NO_COVERAGE report against it: that reading
        // is a pitest lambda-attribution artifact and deleting the stub took 14 tests red
        // (measured 2026-09-11). This test pins that the two views answer the same INSTANCE, not
        // merely an equal one, which is what makes the duplication safe.
        IDataTable t = MockTable.of().col("A", "x").colLong("L", 7L).build();

        for (int c = 0; c < 2; c++)
        {
            assertSame(t.getColumn(c).getDataValue(0), t.getDataValue(0, c));
        }
        assertEquals("x", t.getDataValue(0, 0).getValue());
        assertEquals(7L, t.getDataValue(0, 1).getValue());
        // ... and the bounds of both dimensions still bite through the delegation.
        assertThrows(IndexOutOfBoundsException.class, () -> t.getDataValue(1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> t.getDataValue(0, 2));
    }


    @Test
    void containsColumnAnswersTrueForTheColumnsTheTableActuallyHas()
    {
        // The three contains*Column methods are DEFAULT methods on IDataTableMeta implemented
        // over getOptionalColumn, so an unstubbed mock answered FALSE for every column the table
        // has -- an affirmatively wrong answer, not merely an empty one.
        DataTableMeta m = MockTable.of().col("A", "x").col("B", "y").build().getMetaData();

        assertTrue(m.containsColumn("A"));
        assertTrue(m.containsColumn("B"));
        assertFalse(m.containsColumn("NOPE"));
        assertTrue(m.containsAllColumns("A", "B"));
        assertFalse(m.containsAllColumns("A", "NOPE"));
        assertTrue(m.containsAllColumns(), "an empty array is vacuously all-present");
        assertTrue(m.containsAnyColumn("NOPE", "B"));
        assertFalse(m.containsAnyColumn("NOPE", "ALSO_NOPE"));
        assertFalse(m.containsAnyColumn(), "an empty array contains nothing");
    }


    @Test
    void theWholeByNameAccessorFamilyAgreesWithItself()
    {
        // ⚠⚠ The inconsistency this pins is worse than a uniformly broken mock: containsColumn
        // and getOptionalColumn were stubbed while getColumn(String) answered null and
        // getAllColumns answered an EMPTY Stream, so a production path that checks containsColumn
        // and then calls getColumn took the happy branch and NPEd. getAllColumns is read at 63
        // main-code sites and the getColumns overloads at 94.
        IDataTable t = MockTable.of().col("A", "x").col("B", "y").build();
        DataTableMeta m = t.getMetaData();

        assertEquals(List.of("A", "B"),
                m.getAllColumns().map(DataTableColumnMeta::getName).toList());
        // A FRESH Stream per call -- a cached one would throw IllegalStateException here.
        assertEquals(2, m.getAllColumns().count());
        assertEquals(2, m.getAllColumns().count());
        assertEquals(List.of("A", "B"),
                java.util.Arrays.stream(m.getColumns()).map(DataTableColumnMeta::getName).toList());

        assertEquals("B", m.getColumn("B").getName());
        assertSame(m.getOptionalColumn("B"), m.getColumn("B"));
        assertEquals(NoSuchElementException.class,
                assertThrows(NoSuchElementException.class, () -> m.getColumn("NOPE")).getClass());

        assertEquals(List.of("B", "A"),
                m.getColumns("B", "A").map(DataTableColumnMeta::getName).toList());
        assertEquals(List.of("A", "B"),
                m.getColumns(List.of("A", "B")).map(DataTableColumnMeta::getName).toList());
        assertEquals(List.of("B", "B"),
                m.getColumns(1, 1).map(DataTableColumnMeta::getName).toList());
        assertEquals(List.of("A", "B"),
                m.getColumns(0, 1).map(DataTableColumnMeta::getName).toList());
        assertThrows(NoSuchElementException.class, () -> m.getColumns("A", "NOPE").toList());
        // ⚠ The MESSAGE again: index == columnCount is the boundary, and past it the raw array
        // access raises an IndexOutOfBoundsException SUBCLASS that a type-only assertion accepts.
        assertEquals("column 2 is out of bounds, valid range is [0, 2)",
                assertThrows(IndexOutOfBoundsException.class, () -> m.getColumns(0, 2).toList())
                        .getMessage());
        assertEquals("column -1 is out of bounds, valid range is [0, 2)",
                assertThrows(IndexOutOfBoundsException.class, () -> m.getColumns(-1).toList())
                        .getMessage());
        // getOptionalColumns SKIPS what it cannot resolve rather than throwing.
        assertEquals(List.of("A"),
                m.getOptionalColumns("A", "NOPE").map(DataTableColumnMeta::getName).toList());

        // ... and the table view mirrors all of it, onto this table's own columns.
        assertSame(t.getColumn(0), t.getColumn("A"));
        assertSame(t.getColumn(1), t.getColumn("B"));
        assertThrows(NoSuchElementException.class, () -> t.getColumn("NOPE"));
        assertEquals(List.of(t.getColumn(0), t.getColumn(1)), t.getColumns().toList());
        assertEquals(List.of(t.getColumn(1), t.getColumn(0)), t.getColumns("B", "A").toList());
        assertEquals(List.of(t.getColumn(0)), t.getColumns(List.of("A")).toList());
        assertEquals(List.of(t.getColumn(0), t.getColumn(1)), t.getColumns(0, 1).toList());
        assertEquals(List.of(t.getColumn(1)), t.getColumns(1).toList());
        assertThrows(NoSuchElementException.class, () -> t.getColumns("NOPE").toList());
        assertEquals("column 2 is out of bounds, valid range is [0, 2)",
                assertThrows(IndexOutOfBoundsException.class, () -> t.getColumns(2).toList())
                        .getMessage());
        assertEquals("column -1 is out of bounds, valid range is [0, 2)",
                assertThrows(IndexOutOfBoundsException.class, () -> t.getColumns(-1).toList())
                        .getMessage());
    }


    @Test
    void aBuiltTableIsFrozen_mutatingTheBuilderAfterwardsChangesNothing()
    {
        // ⚠ The stub-to-answer conversion made this a real hazard: an answer runs at CALL time,
        // so an answer that read the builder's caseInsensitiveColumns field would let a later
        // caseInsensitiveColumnNames() retroactively change an ALREADY BUILT table's name
        // resolution. The flag is captured once in build() instead.
        MockTable builder = MockTable.of().col("Abc", "x");
        IDataTable strict = builder.build();
        assertEquals(-1, strict.getMetaData().getColumnIndex("ABC"));

        builder.caseInsensitiveColumnNames();
        IDataTable lenientTable = builder.build();

        assertEquals(-1, strict.getMetaData().getColumnIndex("ABC"),
                "the already-built table must not have changed");
        assertNull(strict.getMetaData().getOptionalColumn("ABC"));
        assertFalse(strict.getMetaData().containsColumn("ABC"));
        assertEquals(0, lenientTable.getMetaData().getColumnIndex("ABC"),
                "the newly built one picks the flag up");
    }


    @Test
    void outOfRangeColumnIndexThrowsLikeARealTable()
    {
        // ColumnCachedDataTable.getColumn(int) and DataTableMeta.getColumn(int) both range-check
        // and raise IndexOutOfBoundsException; the mock answered null, and table.getValue(row,
        // badCol) answered a plausible-looking null cell.
        IDataTable t = MockTable.of().col("A", "x").build();

        // ⚠ Assert the MESSAGE, not merely the type. Index == columnCount is the boundary that
        // separates `idx >= count` from `idx > count`, and under the `>` mutant the index falls
        // through to colNames[idx] / List.get(idx), which raise IndexOutOfBoundsException
        // SUBCLASSES -- so a type-only assertion is satisfied by the mutant and proves nothing.
        assertEquals("column 1 is out of bounds, valid range is [0, 1)",
                assertThrows(IndexOutOfBoundsException.class, () -> t.getColumn(1)).getMessage());
        assertEquals("column -1 is out of bounds, valid range is [0, 1)",
                assertThrows(IndexOutOfBoundsException.class, () -> t.getColumn(-1)).getMessage());
        assertEquals("column 1 is out of bounds, valid range is [0, 1)",
                assertThrows(IndexOutOfBoundsException.class, () -> t.getMetaData().getColumn(1))
                        .getMessage());
        assertEquals("column -1 is out of bounds, valid range is [0, 1)",
                assertThrows(IndexOutOfBoundsException.class, () -> t.getMetaData().getColumn(-1))
                        .getMessage());
        assertThrows(IndexOutOfBoundsException.class, () -> t.getValue(0, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> t.getDataValue(0, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> t.hashCodeAt(0, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> t.isMissingOrNull(0, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> t.isEmptyOrMissing(0, 1));
        // ... and the index that DOES exist still answers, i.e. the broad default did not
        // swallow the per-column stubs registered after it.
        assertEquals("x", t.getValue(0, 0));
        assertEquals("x", t.getColumn(0).getValue(0));
    }


    @Test
    void columnNamesAreCaseSensitiveByDefault()
    {
        IDataTable t = MockTable.of().col("Abc", "x").build();

        assertEquals(0, t.getMetaData().getColumnIndex("Abc"));
        assertEquals(-1, t.getMetaData().getColumnIndex("ABC"));
        assertEquals(-1, t.getMetaData().getColumnIndex("abc"));
    }


    @Test
    void caseInsensitiveColumnNames_resolvesANYCasing_notJustUpperAndLower()
    {
        // ⚠ The opt-in used to stub exactly two variants, colName.toUpperCase and
        // .toLowerCase. A real table resolves through CDT.equalsIgnoreCase, so "UsubjId" finds a
        // USUBJID column -- and here it did not, with the opt-in ON. Mixed case is the common
        // spelling in a Define-XML ItemDef, so the opt-in's name over-promised.
        IDataTable t = MockTable.of().caseInsensitiveColumnNames().col("USUBJID", "S1").build();
        DataTableMeta m = t.getMetaData();

        for (String spelling : new String[]
        {
                "USUBJID", "usubjid", "UsubjId", "uSuBjId"
        })
        {
            assertEquals(0, m.getColumnIndex(spelling), spelling);
            assertEquals("USUBJID", m.getOptionalColumn(spelling).getName(), spelling);
            assertTrue(m.containsColumn(spelling), spelling);
            assertEquals(0, t.getColumnIndex(spelling), spelling);
        }
        assertEquals(-1, m.getColumnIndex("SUBJID"));
        assertNull(m.getOptionalColumn("SUBJID"));
    }

    // ---- table-level metadata ----------------------------------------


    @Test
    void name_label_uri_roundTrip()
    {
        IDataTable t = MockTable.of().col("A", "x").name("DM").label("Demographics")
                .uri("file:///tmp/dm.xpt").build();
        DataTableMeta meta = t.getMetaData();

        assertEquals("DM", meta.getName());
        assertEquals("Demographics", meta.getLabel());
        assertEquals("file:///tmp/dm.xpt", meta.getTableURI().toString());
    }


    @Test
    void unsetTableMetadataIsNull()
    {
        DataTableMeta meta = MockTable.of().col("A", "x").build().getMetaData();

        assertNull(meta.getName());
        assertNull(meta.getLabel());
        assertNull(meta.getTableURI());
    }


    @Test
    void metaValue_roundTripsAndUnknownKeyIsNull()
    {
        IDataTable t = MockTable.of().col("A", "x").metaValue("source", "SDTM").metaValue("rows", 7)
                .build();

        assertEquals("SDTM", t.getMetaData().getMetaData("source"));
        assertEquals(7, t.getMetaData().getMetaData("rows"));
        assertNull(t.getMetaData().getMetaData("absent"));
    }


    @Test
    void realRowIndexIsTheDisplayRowIndex()
    {
        // MockTable models a non-filtered, non-sorted base table; the unstubbed Mockito default
        // of 0 would mask the actual row in every Violation the engine emits.
        IDataTable t = MockTable.of().col("A", "x", "y", "z").build();

        assertEquals(0L, t.getRealRowIndex(0));
        assertEquals(2L, t.getRealRowIndex(2));
    }

    // ---- shortcuts and failure modes ---------------------------------


    @Test
    void withColumns_buildsOneEmptyRowPerNamedColumn()
    {
        IDataTable t = MockTable.withColumns("A", "B");

        assertEquals(2, t.getMetaData().getColumnCount());
        assertEquals(1L, t.getRowCount());
        assertEquals("", t.getValue(0, 0));
        assertEquals("", t.getValue(0, 1));
        assertEquals(1, t.getMetaData().getColumnIndex("B"));
    }


    @Test
    void of_returnsADistinctBuilderEachCall()
    {
        MockTable a = MockTable.of();
        MockTable b = MockTable.of();
        a.col("A", "x");
        b.col("B", "y");

        assertEquals(1, a.build().getMetaData().getColumnCount());
        assertEquals("B", b.build().getMetaData().getOptionalColumn("B").getName());
    }


    @Test
    void buildWithNoColumnsIsRejected()
    {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> MockTable.of().build());

        assertEquals("Need at least one column", e.getMessage());
    }


    @Test
    void builderMethodsReturnTheSameInstanceSoChainsAccumulate()
    {
        MockTable mt = MockTable.of();

        assertEquals(mt, mt.col("A", "x"));
        assertEquals(mt, mt.colLong("B", 1L));
        assertEquals(mt, mt.colDouble("C", 1.0d));
        assertEquals(mt, mt.colSasMissing("D", "1"));
        assertEquals(mt, mt.colMeta("A", "L", 1, "F"));
        assertEquals(mt, mt.name("T"));
        assertEquals(mt, mt.label("L"));
        assertEquals(mt, mt.uri("file:///x"));
        assertEquals(mt, mt.metaValue("k", "v"));
        assertEquals(mt, mt.caseInsensitiveColumnNames());

        assertEquals(4, mt.build().getMetaData().getColumnCount());
    }


    @Test
    void mixedColumnKindsAreIndexedByKIND_notByDeclarationOrder()
    {
        // ⚠ Column index follows the order build() drains its four per-kind maps --
        // string, then SAS-missing, then long, then double -- NOT the order the builder calls
        // were written. Declaring col/colLong/colDouble/colSasMissing in that order yields
        // indices S=0, M=1, L=2, D=3. Pinned because the natural reading is declaration order,
        // and a test that assumes it reads the wrong column and still passes when the column
        // happens to be blank.
        IDataTable t = MockTable.of().col("S", "a", "b").colLong("L", 1L, 2L)
                .colDouble("D", 1.5d, 2.5d).colSasMissing("M", "1", null).build();

        assertEquals(4, t.getMetaData().getColumnCount());
        assertEquals(2L, t.getRowCount());

        assertEquals(0, t.getMetaData().getColumnIndex("S"));
        assertEquals(1, t.getMetaData().getColumnIndex("M"));
        assertEquals(2, t.getMetaData().getColumnIndex("L"));
        assertEquals(3, t.getMetaData().getColumnIndex("D"));

        assertEquals("b", t.getValue(1, 0));
        assertEquals(MissingValue.MIS, t.getValue(1, 1));
        assertEquals(2L, t.getValue(1, 2));
        assertEquals(2.5d, t.getValue(1, 3));
    }


    @Test
    void tableGetValueAndColumnGetValueAgreeOnEveryColumnKind()
    {
        // The invariant this class exists to hold. In a real table the two are the SAME call --
        // ColumnCachedDataTable.getValue(row, col) is `return getColumn(col).getValue(row)` --
        // so a mock that lets them disagree is not modelling a table.
        //
        // Until 2026-09-01 they DID disagree for a SAS-missing cell: table.getValue answered a
        // literal null from a separate raw array while the column answered MissingValue.MIS.
        // All four columns must declare the SAME row count: MockTable takes the row count from
        // whichever column it drains first and indexes every other column with it.
        IDataTable t = MockTable.of().col("S", "a", "", null).colSasMissing("M", "1", null, "3")
                .colLong("L", 1L, null, 3L).colDouble("D", 1.5d, null, 2.5d).build();

        for (int c = 0; c < t.getMetaData().getColumnCount(); c++)
        {
            for (int r = 0; r < t.getRowCount(); r++)
            {
                assertEquals(t.getColumn(c).getValue(r), t.getValue(r, c),
                        "getValue disagrees at row " + r + " col " + c);
                assertEquals(t.isMissingOrNull(r, c), t.getColumn(c).isMissingOrNull(r),
                        "isMissingOrNull disagrees at row " + r + " col " + c);
                assertEquals(t.isEmptyOrMissing(r, c), t.getColumn(c).isEmptyOrMissing(r),
                        "isEmptyOrMissing disagrees at row " + r + " col " + c);
            }
        }
    }


    @Test
    void sasMissingCell_readsAsMissingValueThroughBOTHAccessors()
    {
        // A real numeric buffer answers MissingValue.MIS from getValue for a missing cell
        // (DataBufferInt.getValue), and IDataBuffer.isMissing is defined as
        // `getValue(i) instanceof MissingValue` -- so answering null here is a shape no real
        // table produces.
        IDataTable t = MockTable.of().colSasMissing("M", "1", null).build();

        assertEquals(MissingValue.MIS, t.getValue(1, 0));
        assertEquals(MissingValue.MIS, t.getColumn(0).getValue(1));
        assertEquals(MissingValue.MIS, t.getDataValue(1, 0).getValue());

        assertEquals(1L, t.getValue(0, 0));
        assertEquals(1L, t.getColumn(0).getValue(0));
        assertTrue(t.isMissingOrNull(1, 0));
        assertTrue(t.getColumn(0).isMissingOrNull(1));
    }

    // ---- mutation-gate gaps named by review (pitest 55, CI-only) ------


    @Test
    void plainStringColumn_answersNaNForANumericCell_likeDataValueString()
    {
        // ⚠⚠ This test used to be plainStringColumn_parsesANumericCellAsADouble and it PINNED a
        // shape no real character column produces. DataValueString -- the cell every real
        // character column hands back -- OVERRIDES getValueAsDouble with a hard
        // `return Double.NaN`, precisely to stop a Char column being read as numeric. The mock
        // parsed the text instead, so "42" answered 42.0 and ScalarSemantics' numeric branch
        // fired on cells where the product falls back to a textual comparison. Changed with the
        // mock, 2026-09-11.
        IDataTable t = MockTable.of().col("N", "42", "x").build();

        assertEquals(DataValueType.STRING, t.getDataValue(0, 0).getType());
        assertEquals("42", t.getDataValue(0, 0).getValue());
        assertEquals("42", t.getDataValue(0, 0).getValueAsString());
        assertTrue(Double.isNaN(t.getDataValue(0, 0).getValueAsDouble()),
                "a STRING cell is NaN however numeric its text looks -- see DataValueString");
        assertTrue(Double.isNaN(t.getDataValue(1, 0).getValueAsDouble()));
    }


    @Test
    void cellAt_rowExactlyEqualToRowCountIsOutOfRange()
    {
        // The boundary that separates `row >= length` from `row > length`. The other
        // out-of-range test reads row 9 of a 1-row column, which both forms answer identically;
        // only row == rowCount distinguishes them -- under the mutant the last row would be
        // readable AND row == rowCount would read aCells[rowCount], i.e. a raw
        // ArrayIndexOutOfBoundsException rather than the IndexOutOfBoundsException a real column
        // raises. Both are subclasses of RuntimeException, so assert the exact exception type.
        IDataTable t = MockTable.of().col("X", "v").build();

        assertEquals("v", t.getColumn(0).getValue(0));
        assertEquals(IndexOutOfBoundsException.class,
                assertThrows(IndexOutOfBoundsException.class, () -> t.getColumn(0).getValue(1))
                        .getClass(),
                "row == rowCount must raise IndexOutOfBoundsException itself, not the"
                        + " ArrayIndexOutOfBoundsException subclass a raw array access raises");
    }

    // ---- ragged tables are rejected ----------------------------------


    @Test
    void raggedTable_aLongerLaterColumnIsRejectedRatherThanTruncated()
    {
        // ⚠⚠ This used to build silently: the row count was taken from the FIRST column and the
        // second column's extra values were simply never stubbed. The fixture then held less
        // data than the test declared, and nothing said so. It really happened -- a downstream
        // rule test in cumba-corej-core declared a two-value partner column beside a one-value
        // coded column and the second value was dropped for as long as that test existed.
        MockTable mt = MockTable.of().col("PARAMCD", "ZZZ").col("PARAM", "Albumin", "Bilirubin");

        IllegalStateException ex = assertThrows(IllegalStateException.class, mt::build);
        assertTrue(ex.getMessage().contains("PARAM"), ex.getMessage());
        assertTrue(ex.getMessage().contains("Ragged"), ex.getMessage());
    }


    @Test
    void raggedTable_aShorterLaterColumnIsRejectedWithANamedError()
    {
        // The other direction used to die inside the stubbing loop with a bare
        // ArrayIndexOutOfBoundsException naming neither the column nor the counts.
        MockTable mt = MockTable.of().col("A", "1", "2").col("B", "x");

        IllegalStateException ex = assertThrows(IllegalStateException.class, mt::build);
        assertTrue(ex.getMessage().contains("'B' has 1 values"), ex.getMessage());
        assertTrue(ex.getMessage().contains("already has 2 rows"), ex.getMessage());
    }


    @Test
    void raggedTable_aZeroRowFirstColumnStillFixesTheRowCount()
    {
        // ⭐ The case that separates `rowCount < 0` from `rowCount <= 0`: a zero-row first column
        // has ALREADY fixed the table at 0 rows, so a later column with values is ragged. Under
        // the boundary mutant the 0 would read as "not yet adopted" and the later column would
        // silently redefine the table -- which is the very truncation this guard removes.
        MockTable mt = MockTable.of().col("EMPTY").col("B", "x");

        IllegalStateException ex = assertThrows(IllegalStateException.class, mt::build);
        assertTrue(ex.getMessage().contains("already has 0 rows"), ex.getMessage());
    }


    @Test
    void raggedTable_isCheckedAcrossEVERYColumnKindNotJustStringColumns()
    {
        // The row count is adopted in four separate loops (string, sas-missing, long, double),
        // and the kinds are visited in that order regardless of declaration order. Each loop
        // must carry the same guard, so drive one disagreement per kind.
        assertThrows(IllegalStateException.class,
                () -> MockTable.of().col("A", "1").colSasMissing("S", "1", "2").build());
        assertThrows(IllegalStateException.class,
                () -> MockTable.of().col("A", "1").colLong("L", 1L, 2L).build());
        assertThrows(IllegalStateException.class,
                () -> MockTable.of().col("A", "1").colDouble("D", 1.0d, 2.0d).build());
        // ... and a sas-missing FIRST column is what fixes the count for the numeric ones, since
        // the string loop runs before it and contributes nothing here.
        assertThrows(IllegalStateException.class,
                () -> MockTable.of().colSasMissing("S", "1").colLong("L", 1L, 2L).build());
        assertThrows(IllegalStateException.class,
                () -> MockTable.of().colLong("L", 1L).colDouble("D", 1.0d, 2.0d).build());
    }


    @Test
    void raggedCheck_acceptsAUniformTableOfEveryKindIncludingAZeroRowOne()
    {
        // The guard must not fire on the shapes that ARE legal, or it would be a gate that
        // rejects everything. Equal lengths across all four kinds, and an all-empty table.
        IDataTable t = MockTable.of().col("A", "1", "2").colSasMissing("S", "3", null)
                .colLong("L", 4L, null).colDouble("D", 5.0d, null).build();
        assertEquals(2L, t.getRowCount());
        assertEquals(4, t.getMetaData().getColumnCount());

        IDataTable empty = MockTable.of().col("A").colLong("L").build();
        assertEquals(0L, empty.getRowCount());
        assertEquals(2, empty.getMetaData().getColumnCount());
        assertThrows(IndexOutOfBoundsException.class, () -> empty.getColumn(0).getValue(0));
    }


    @Test
    void columnAndColumnMetaAccessorsAreStubbed()
    {
        // col.getRowCount(), meta.getColumn(idx) and a NON-ZERO colMeta.getIndex() were all
        // unasserted, so a mutant deleting each stub survived behind Mockito's defaults
        // (0L / null / 0). meta.getColumn(idx) is the accessor ScalarSemantics uses to read a
        // column's declared type.
        IDataTable t = MockTable.of().col("A", "a1", "a2").col("B", "b1", "b2").build();

        assertEquals(2L, t.getColumn(0).getRowCount());
        assertEquals(2L, t.getColumn(1).getRowCount());
        assertEquals("B", t.getMetaData().getColumn(1).getName());
        assertEquals(1, t.getMetaData().getColumn(1).getIndex());
        assertEquals(0, t.getMetaData().getColumn(0).getIndex());
    }

    // ---- type coherence and cell hashing -----------------------------


    @Test
    void colSasMissing_columnTypeFollowsItsData()
    {
        // A real buffer is numeric OR character; it is never a LONG column answering Strings.
        IDataTable numeric = MockTable.of().colSasMissing("N", "1", null, "3").build();
        IDataTable character = MockTable.of().colSasMissing("C", "1", null, "x").build();

        assertEquals(DataValueType.LONG, numeric.getMetaData().getOptionalColumn("N").getType());
        assertEquals(DataValueType.LONG, numeric.getDataValue(0, 0).getType());
        assertEquals(1L, numeric.getValue(0, 0));

        assertEquals(DataValueType.STRING,
                character.getMetaData().getOptionalColumn("C").getType());
        assertEquals(DataValueType.STRING, character.getDataValue(0, 0).getType());
        assertEquals("1", character.getValue(0, 0));

        // Either way the MISSING cell is still a real SAS marker — that is the whole point of
        // this column kind, and what keeps fold-detection assertions non-vacuous.
        assertEquals(MissingValue.MIS, numeric.getValue(1, 0));
        assertEquals(MissingValue.MIS, character.getValue(1, 0));
    }


    @Test
    void colSasMissing_aDecimalIsNotALongSoTheColumnIsCharacter()
    {
        // ⚠ The input that separates "parses as a LONG" from "parses as a NUMBER". Every other
        // colSasMissing fixture in this class uses a value that fails BOTH ("x",
        // "NOT-A-NUMBER"), so without this a mutant swapping Long.parseLong for
        // Double.parseDouble inside allParseAsLong survives: it would classify this column LONG,
        // and Long.valueOf("1.5") would then throw out of build() — failing HERE, loudly, which
        // is exactly what hoisting that parse out of the thenReturn(...) argument bought. Before
        // the hoist the same throw happened with a Mockito stubbing open and resurfaced as an
        // UnfinishedStubbingException in an unrelated later test.
        IDataTable t = MockTable.of().colSasMissing("D", "1.5", null).build();

        assertEquals(DataValueType.STRING, t.getMetaData().getOptionalColumn("D").getType());
        assertEquals("1.5", t.getValue(0, 0));
        assertTrue(Double.isNaN(t.getDataValue(0, 0).getValueAsDouble()),
                "a STRING-typed colSasMissing cell answers NaN, exactly as DataValueString does");
        assertEquals(MissingValue.MIS, t.getValue(1, 0));
    }


    @Test
    void colSasMissing_aNumericColumnRendersItsCanonicalForm()
    {
        // "007" and "7" are the same cell to a real LONG buffer: Objects.equals and hash-equal,
        // so an index folds them into one block. Rendering them differently would show two key
        // spellings inside one block.
        IDataTable t = MockTable.of().colSasMissing("K", "007", "7").build();

        assertEquals(7L, t.getValue(0, 0));
        assertEquals(7L, t.getValue(1, 0));
        assertEquals(t.hashCodeAt(0, 0), t.hashCodeAt(1, 0));
        assertEquals("7", t.getDataValue(0, 0).getValueAsString());
        assertEquals("7", t.getDataValue(1, 0).getValueAsString());
    }


    @Test
    void sasMissingNumericColumnJoinsAgainstAColLongColumn()
    {
        // The concrete defect the type fix closes. KeyHashing.KeyMatcher compares join keys with
        // Objects.equals, so a STRING "5" never matched a LONG 5L and a colSasMissing column
        // silently failed to join against a colLong column where two real numeric columns would.
        IDataTable sas = MockTable.of().colSasMissing("K", "5").build();
        IDataTable lng = MockTable.of().colLong("K", 5L).build();

        assertEquals(lng.getValue(0, 0), sas.getValue(0, 0));
        assertEquals(lng.hashCodeAt(0, 0), sas.hashCodeAt(0, 0));
    }


    @Test
    void hashCodeAtMatchesTheCellValueOnBothViews()
    {
        // hashCodeAt is a DEFAULT method on IDataTable and IDataTableColumn, and Mockito does not
        // run an unstubbed default — so every cell used to hash to 0 and KeyHashing returned a
        // constant for every row. The interface contract is: MissingValue.hashCodeStable() for a
        // missing payload, the boxed value's own hash otherwise.
        IDataTable t = MockTable.of().col("S", "a", null).colLong("L", 7L, null).build();

        assertEquals("a".hashCode(), t.hashCodeAt(0, 0));
        assertEquals("a".hashCode(), t.getColumn(0).hashCodeAt(0));
        assertEquals(Long.valueOf(7L).hashCode(), t.hashCodeAt(0, 1));
        assertEquals(Long.valueOf(7L).hashCode(), t.getColumn(1).hashCodeAt(0));

        // a character null stays null -> 0; a numeric missing is MissingValue.MIS -> stable hash
        assertEquals(0, t.hashCodeAt(1, 0));
        assertEquals(MissingValue.MIS.hashCodeStable(), t.hashCodeAt(1, 1));
        assertEquals(MissingValue.MIS.hashCodeStable(), t.getColumn(1).hashCodeAt(1));
    }

}
