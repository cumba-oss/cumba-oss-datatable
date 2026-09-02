package net.cumba.datatable.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void outOfRangeRowReadsAsBlankRatherThanThrowing()
    {
        IDataTable t = MockTable.of().col("X", "v").build();

        assertNull(t.getColumn(0).getValue(9));
        assertTrue(t.getColumn(0).isMissingOrNull(9));
        assertTrue(t.getColumn(0).isMissingOrNull(-1));
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
    void columnNamesAreCaseSensitiveByDefault()
    {
        IDataTable t = MockTable.of().col("Abc", "x").build();

        assertEquals(0, t.getMetaData().getColumnIndex("Abc"));
        assertEquals(-1, t.getMetaData().getColumnIndex("ABC"));
        assertEquals(-1, t.getMetaData().getColumnIndex("abc"));
    }


    @Test
    void caseInsensitiveColumnNames_resolvesBothCasings()
    {
        IDataTable t = MockTable.of().col("Abc", "x").caseInsensitiveColumnNames().build();

        assertEquals(0, t.getMetaData().getColumnIndex("ABC"));
        assertEquals(0, t.getMetaData().getColumnIndex("abc"));
        assertEquals(0, t.getMetaData().getColumnIndex("Abc"));
        assertEquals("Abc", t.getMetaData().getOptionalColumn("abc").getName());
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
    void plainStringColumn_parsesANumericCellAsADouble()
    {
        // Covers mockDataValue's Double.parseDouble SUCCESS branch, which no other test reached:
        // every other col(...) value in this class is non-numeric, so only the
        // NumberFormatException
        // arm ran and any mutant in the success arm was unkillable.
        IDataTable t = MockTable.of().col("N", "42", "notnum").build();

        assertEquals(42.0d, t.getDataValue(0, 0).getValueAsDouble());
        assertEquals("42", t.getDataValue(0, 0).getValue());
        assertTrue(Double.isNaN(t.getDataValue(1, 0).getValueAsDouble()));
    }


    @Test
    void cellAt_rowExactlyEqualToRowCountIsOutOfRange()
    {
        // The boundary that separates `row >= length` from `row > length`. The existing
        // out-of-range test reads row 9 of a 1-row column, which both forms answer identically;
        // only row == rowCount distinguishes them, and the mutant throws
        // ArrayIndexOutOfBoundsException instead of answering null.
        IDataTable t = MockTable.of().col("X", "v").build();

        assertEquals("v", t.getColumn(0).getValue(0));
        assertNull(t.getColumn(0).getValue(1));
        assertTrue(t.getColumn(0).isMissingOrNull(1));
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
        assertEquals(1.5d, t.getDataValue(0, 0).getValueAsDouble());
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
