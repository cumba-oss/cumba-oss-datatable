package net.cumba.datatable.provider.cdt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.IDataValue;
import net.cumba.datatable.values.MissingValue;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CdtTableBuilder}. Focuses on the F-C7 bounds-check in {@code populate} —
 * {@link CdtTableBuilder#build(CdtDataset, URI)} now refuses datasets whose row length disagrees
 * with the column count rather than throwing an opaque {@link IndexOutOfBoundsException} (too few)
 * or silently dropping trailing cells (too many).
 */
class CdtTableBuilderTest
{

    /** Runtime copy with equal content but a distinct identity (never the constant-pool one). */
    private static String copyOf(String aValue)
    {
        return String.valueOf(aValue.toCharArray());
    }


    private static CdtDataset buildDataset(List<List<String>> aRows)
    {
        return CdtDataset.builder()//
                .name("DM")//
                .column(CdtColumn.builder().name("USUBJID").type(CdtType.CHAR).build())//
                .column(CdtColumn.builder().name("AGE").type(CdtType.NUM).build())//
                .dataRows(aRows)//
                .fence("---")//
                .build();
    }


    /** String cells are canonicalised against the app-global pool ({@code CDT.intern}). */
    @Test
    void stringCellsAreCanonicalised()
    {
        // Distinct equal-content instances built at runtime — sharing must come from the pool.
        CdtDataset ds = buildDataset(List.of(//
                List.of(copyOf("S001"), "42"), //
                List.of(copyOf("S001"), "37")));

        IDataTable table = CdtTableBuilder.build(ds, URI.create("test:dm"));
        Object a = table.getValue(0, 0);
        Object b = table.getValue(1, 0);
        assertEquals("S001", a);
        org.junit.jupiter.api.Assertions.assertSame(a, b,
                "equal-content cells must share one canonical instance");
        org.junit.jupiter.api.Assertions.assertSame(a, net.cumba.datatable.help.CDT.intern("S001"),
                "the shared instance must be the CDT-pool one");
    }


    /** Happy path — every row has exactly two columns, table builds without complaint. */
    @Test
    void buildAcceptsRowsWithMatchingColumnCount()
    {
        CdtDataset ds = buildDataset(List.of(//
                List.of("S001", "42"), //
                List.of("S002", "37")));

        IDataTable table = CdtTableBuilder.build(ds, URI.create("test:dm"));
        assertNotNull(table);
        assertEquals(2L, table.getRowCount());
        assertEquals(2, table.getMetaData().getColumnCount());
    }


    /**
     * A built table never exposes {@code null} through {@link IDataTable#getValue(long, int)}: a
     * missing Char cell is {@code ""} and a missing numeric cell is {@link MissingValue#MIS}, which
     * is how every other provider represents missing (see {@code DataValueSupport.defaultForType}).
     */
    @Test
    void missingCellsAreNeverNull()
    {
        CdtDataset ds = buildDataset(List.of(//
                List.of("", ""), //
                List.of("S002", ".")));

        IDataTable table = CdtTableBuilder.build(ds, URI.create("test:dm"));

        assertEquals("", table.getValue(0, 0));
        assertEquals(MissingValue.MIS, table.getValue(0, 1));
        assertEquals("S002", table.getValue(1, 0));
        assertEquals(MissingValue.MIS, table.getValue(1, 1));

        for (long r = 0; r < table.getRowCount(); r++)
        {
            for (int c = 0; c < table.getMetaData().getColumnCount(); c++)
            {
                assertNotNull(table.getValue(r, c), "cell (" + r + "," + c + ") must not be null");
            }
        }
    }


    /**
     * A missing numeric cell decodes back to {@link MissingValue#MIS} — i.e. it renders as
     * {@code "."}. Storing a raw {@code null} used to land a payload-free {@link Double#NaN} in the
     * buffer, which decoded to {@code MIS_UNKNOWN} and surfaced as {@code <UKN>}.
     */
    @Test
    void missingNumericDecodesToMisNotUnknown()
    {
        CdtDataset ds = buildDataset(List.of(//
                List.of("S001", "."), //
                List.of("S002", "37")));

        IDataTable table = CdtTableBuilder.build(ds, URI.create("test:dm"));

        IDataValue dv = table.getDataValue(0, 1);
        assertTrue(dv.isMissingOrInvalid(), "a missing numeric must report as missing");
        assertEquals(MissingValue.MIS, dv.getValue());
        assertEquals(".", dv.getValue().toString());
        assertTrue(table.isMissingOrNull(0, 1));

        // The populated cell is unaffected.
        assertEquals(37.0, table.getDataValue(1, 1).getValueAsDouble());
    }


    /** F-C7: a row that is shorter than the column declaration is rejected explicitly. */
    @Test
    void buildRejectsRowWithTooFewColumns()
    {
        CdtDataset ds = buildDataset(List.of(//
                List.of("S001", "42"), //
                List.of("S002") // missing AGE
        ));

        CdtParseException ex = org.junit.jupiter.api.Assertions.assertThrows(
                CdtParseException.class, () -> CdtTableBuilder.build(ds, URI.create("test:dm")));
        String msg = ex.getMessage();
        assertNotNull(msg);
        assertTrue(msg.contains("row 1"), "message should name the offending row index: " + msg);
        assertTrue(msg.contains("1 columns") && msg.contains("expected 2"),
                "message should report actual and expected counts: " + msg);
    }


    /** F-C7: a row with extra trailing cells is just as broken — also rejected. */
    @Test
    void buildRejectsRowWithTooManyColumns()
    {
        CdtDataset ds = buildDataset(List.of(//
                List.of("S001", "42", "extra")));

        CdtParseException ex = org.junit.jupiter.api.Assertions.assertThrows(
                CdtParseException.class, () -> CdtTableBuilder.build(ds, URI.create("test:dm")));
        assertTrue(ex.getMessage().contains("row 0"));
        assertTrue(ex.getMessage().contains("3 columns"));
        assertTrue(ex.getMessage().contains("expected 2"));
    }


    /**
     * F-C7: the row index reported in the exception is the position in the dataset, not in the file
     * — verifies the loop counter increments correctly even when earlier rows are well-formed.
     */
    @Test
    void buildReportsCorrectRowIndexForMismatchedThirdRow()
    {
        CdtDataset ds = buildDataset(List.of(//
                List.of("S001", "42"), //
                List.of("S002", "37"), //
                List.of("S003"), // <-- index 2 is bad
                List.of("S004", "30")));

        CdtParseException ex = org.junit.jupiter.api.Assertions.assertThrows(
                CdtParseException.class, () -> CdtTableBuilder.build(ds, URI.create("test:dm")));
        assertTrue(ex.getMessage().contains("row 2"), ex.getMessage());
    }


    /**
     * {@code reconcileLengths} widens the declared length of a {@code Char} column when the actual
     * data is longer, leaves a matching or shorter column untouched, and never touches a non-Char
     * column (a declared {@code length} on a numeric column is not a string-truncation guard, so
     * there is nothing to widen). Also exercises the dataset/column label fallback
     * ({@code label ?? name}) in both directions, and the codelist attribute propagation.
     */
    @Test
    void reconcileLengthsWidensOnlyOverflowingCharColumns()
    {
        CdtDataset ds = CdtParser.parseFirst("""
                dataset DM label="Demographics"
                col USUBJID type=Char length=2 codelist=C1
                col AGE type=Num length=2
                col SITE type=Char length=10 label="Site ID"
                ---
                ABCDE | 12345 | X
                ---
                """, "t");

        IDataTable table = CdtTableBuilder.build(ds, URI.create("test:dm"));
        var meta = table.getMetaData();

        assertEquals("Demographics", meta.getLabel());

        assertEquals("USUBJID", meta.getColumn(0).getLabel(), "label falls back to the name");
        assertEquals(5, meta.getColumn(0).getLength(), "ABCDE (5 chars) must widen the declared 2");
        assertEquals("C1", meta.getColumn(0).getMetaData(CdtTableBuilder.COLUMN_META_CODELIST));

        assertEquals(2, meta.getColumn(1).getLength(),
                "a Num column's declared length is never reconciled against string width");

        assertEquals("Site ID", meta.getColumn(2).getLabel(), "explicit label is used as-is");
        assertEquals(10, meta.getColumn(2).getLength(),
                "1-char value does not exceed the declared 10, so it is left untouched");
    }


    /** The dataset label falls back to the dataset name when no {@code label=...} was declared. */
    @Test
    void datasetLabelFallsBackToNameWhenAbsent()
    {
        CdtDataset ds = CdtParser.parseFirst("""
                dataset DM
                col X type=Char
                ---
                v
                ---
                """, "t");
        IDataTable table = CdtTableBuilder.build(ds, URI.create("test:dm"));
        assertEquals("DM", table.getMetaData().getLabel());
    }


    /**
     * A blank cell in a <em>numeric</em> column must load.
     * <p>
     * It used to throw: {@code CdtValues.parseValue} answered {@code null} for any empty field
     * whatever the type, {@link #populateNumericTypes} hands that straight to
     * {@code CachedDataTableColumn.addElement}, and {@code DataBufferDouble.canStore} refuses
     * {@code null} — so the build died with {@code IllegalArgumentException: Invalid value: null}
     * and a {@code .cdt} with a blank {@code Num} cell could not be read at all.
     * <p>
     * All four numeric CDT types are exercised, not just {@code Num}: DATE, TIME and DATETIME are
     * numeric in SAS terms and {@code CdtValues.toDataValueType} maps every one of them to
     * {@link DataValueType#DOUBLE}, so they share the same buffer and the same defect. The CHAR
     * column is in the fixture as the control — its blank cell must stay a blank <em>string</em>,
     * never missing, which is the {@code BlankCellFormatIndependenceTest} contract.
     */
    @Test
    void blankNumericCellLoadsAsMissingForEveryNumericType()
    {
        IDataTable table = populateNumericTypes(List.of(//
                List.of("S001", "42", "2021-01-01", "01:00:00", "1960-01-02T00:00:00"), //
                List.of("", "", "", "", "")));

        assertEquals(2L, table.getRowCount());
        DataTableMeta meta = table.getMetaData();

        for (int c = 0; c < meta.getColumnCount(); c++)
        {
            DataTableColumnMeta cm = meta.getColumn(c);
            IDataValue populated = table.getDataValue(0, c);
            IDataValue blank = table.getDataValue(1, c);

            assertFalse(populated.isMissingOrInvalid(),
                    cm.getName() + " row 0 carries a real value and must not read as missing");

            if (cm.getType() == DataValueType.STRING)
            {
                assertFalse(blank.isMissingOrInvalid(),
                        cm.getName() + ": a blank character cell reads as the empty string");
                assertEquals("", blank.getValueAsString(), cm.getName());
            }
            else
            {
                assertEquals(DataValueType.DOUBLE, cm.getType(), cm.getName());
                assertTrue(blank.isMissingOrInvalid(),
                        cm.getName() + ": a blank numeric cell must read as missing");
            }
        }
    }


    /**
     * The same blank numeric cell, but reached the way a real file does — through
     * {@link CdtParser}. This also covers the SAS {@code .} sentinel, which the parser folds to the
     * empty string before {@code CdtValues.parseValue} ever sees it, and the whole-row {@code .}
     * sentinel line.
     */
    @Test
    void blankNumericCellLoadsFromParsedFileContent()
    {
        String content = """
                dataset DM
                col USUBJID type=Char
                col AGE type=Num
                col RFSTDTC type=Date
                ---
                S001 | 42 | 2021-01-01
                S002 |  | 2021-01-02
                S003 | . | .
                .
                ---
                """;

        List<CdtDataset> parsed = CdtParser.parseAll(content, "test.cdt");
        assertEquals(1, parsed.size());

        IDataTable table = CdtTableBuilder.build(parsed.get(0), URI.create("test:dm"));
        assertEquals(4L, table.getRowCount());

        // Row 0 is the control: without it a table of nothing but missing cells would satisfy the
        // assertions below while proving the loader can still read a value.
        assertFalse(table.getDataValue(0, 1).isMissingOrInvalid(), "row 0 AGE");
        assertEquals(42.0d, table.getDataValue(0, 1).getValueAsDouble(), "row 0 AGE");

        assertTrue(table.getDataValue(1, 1).isMissingOrInvalid(), "row 1 AGE (blank field)");
        assertFalse(table.getDataValue(1, 2).isMissingOrInvalid(), "row 1 RFSTDTC");

        assertTrue(table.getDataValue(2, 1).isMissingOrInvalid(), "row 2 AGE (. sentinel)");
        assertTrue(table.getDataValue(2, 2).isMissingOrInvalid(), "row 2 RFSTDTC (. sentinel)");

        assertTrue(table.getDataValue(3, 1).isMissingOrInvalid(), "row 3 AGE (all-null row)");
        assertTrue(table.getDataValue(3, 2).isMissingOrInvalid(), "row 3 RFSTDTC (all-null row)");
    }


    /**
     * The blank numeric cell survives a write/read round-trip: {@code CdtWriter} renders a missing
     * numeric back to an empty field, which the loader turns into a missing cell again.
     */
    @Test
    void blankNumericCellRoundTripsThroughTheWriter()
    {
        IDataTable original = populateNumericTypes(List.of(//
                List.of("S001", "42", "2021-01-01", "01:00:00", "1960-01-02T00:00:00"), //
                List.of("S002", "", "", "", "")));

        String written = CdtWriter.toString(original);
        List<CdtDataset> reparsed = CdtParser.parseAll(written, "roundtrip.cdt");
        IDataTable table = CdtTableBuilder.build(reparsed.get(0), URI.create("test:dm"));

        assertEquals(2L, table.getRowCount());
        DataTableMeta meta = table.getMetaData();
        int missing = 0;
        for (int c = 0; c < meta.getColumnCount(); c++)
        {
            if (meta.getColumn(c).getType() == DataValueType.DOUBLE)
            {
                assertFalse(table.getDataValue(0, c).isMissingOrInvalid(),
                        meta.getColumn(c).getName() + " row 0 lost its value in the round-trip");
                assertTrue(table.getDataValue(1, c).isMissingOrInvalid(),
                        meta.getColumn(c).getName() + " row 1 must still be missing");
                missing++;
            }
        }
        assertEquals(4, missing, "the round-tripped table must still carry four numeric columns");
    }


    /**
     * Builds a table with one column of every {@link CdtType}, so the numeric buffer is reached for
     * NUM, DATE, TIME and DATETIME alike.
     */
    private static IDataTable populateNumericTypes(List<List<String>> aRows)
    {
        CdtDataset ds = CdtDataset.builder()//
                .name("DM")//
                .column(CdtColumn.builder().name("USUBJID").type(CdtType.CHAR).build())//
                .column(CdtColumn.builder().name("AGE").type(CdtType.NUM).build())//
                .column(CdtColumn.builder().name("RFSTDTC").type(CdtType.DATE).build())//
                .column(CdtColumn.builder().name("RFSTTM").type(CdtType.TIME).build())//
                .column(CdtColumn.builder().name("RFSTDTM").type(CdtType.DATETIME).build())//
                .dataRows(aRows)//
                .fence("---")//
                .build();
        return CdtTableBuilder.build(ds, URI.create("test:dm"));
    }
}
