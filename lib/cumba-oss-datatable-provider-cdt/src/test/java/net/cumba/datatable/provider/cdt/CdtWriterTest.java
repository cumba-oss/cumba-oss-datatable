package net.cumba.datatable.provider.cdt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.CachedDataTableColumn;
import net.cumba.datatable.impl.ColumnCachedDataTable;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.MissingValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link CdtWriter}. Most tests build a real {@link IDataTable} (either through the
 * round-trip {@link CdtTableBuilder} path, or by assembling {@link CachedDataTableColumn}s directly
 * when a specific value type / format needs to be pinned) and assert on the exact text
 * {@link CdtWriter#toString(IDataTable)} produces, plus the parser round-trip.
 */
class CdtWriterTest
{

    // ---- helpers ------------------------------------------------------------------

    private static IDataTable buildFromCdt(String aContent)
    {
        CdtDataset ds = CdtParser.parseFirst(aContent, "t");
        return CdtTableBuilder.build(ds, URI.create("test:t"));
    }


    /**
     * Assemble a table directly from column metadata + raw cell values so a test can pin a specific
     * {@link DataValueType} and display format. Each column's cell list must have the same length.
     */
    private static IDataTable directTable(DataTableMeta aMeta, List<List<Object>> aColumnsCells)
    {
        CachedDataTableColumn[] cols = new CachedDataTableColumn[aMeta.getColumnCount()];
        for (int c = 0; c < cols.length; c++)
        {
            cols[c] = new CachedDataTableColumn(c, aMeta.getColumn(c).getType());
            for (Object v : aColumnsCells.get(c))
            {
                cols[c].addElement(v);
            }
            cols[c].complete();
        }
        return new ColumnCachedDataTable(aMeta, cols);
    }


    private static DataTableColumnMeta col(int aIdx, String aName, DataValueType aType,
            String aFormat)
    {
        return DataTableColumnMeta.builder().index(aIdx).name(aName).type(aType)
                .displayFormat(aFormat).build();
    }

    // ---- header / column emission --------------------------------------------------


    @Test
    void writesDatasetHeaderColumnsAndFences()
    {
        IDataTable t = buildFromCdt("""
                dataset DM
                col USUBJID type=Char
                col AGE type=Num
                ---
                S001 | 42
                S002 | 37
                ---
                """);
        String out = CdtWriter.toString(t);

        assertTrue(out.startsWith("dataset DM\n"), out);
        assertTrue(out.contains("col USUBJID type=Char"), out);
        assertTrue(out.contains("col AGE type=Num"), out);
        // Opening + closing fence each appear on their own line.
        assertEquals(2, out.lines().filter("---"::equals).count(), out);
        assertTrue(out.contains("S001 | 42"), out);
        assertTrue(out.contains("S002 | 37"), out);
        assertTrue(out.endsWith("---\n"), out);
    }


    @Test
    void datasetNameFallsBackToDataWhenBlank()
    {
        DataTableMeta meta = DataTableMeta.builder().name(null).label(null).rowCount(1)
                .totalRowCount(1).tableURI(URI.create("test:t")).columns(new DataTableColumnMeta[]
                {
                        col(0, "X", DataValueType.STRING, null)
                }).build();
        IDataTable t = directTable(meta, List.of(List.of("v")));
        String out = CdtWriter.toString(t);
        assertTrue(out.startsWith("dataset DATA\n"), out);
    }


    @Test
    void labelEmittedOnlyWhenDifferentFromName()
    {
        // label equal to name → suppressed.
        IDataTable same = buildFromCdt("""
                dataset DM
                col A type=Char
                ---
                x
                ---
                """);
        assertFalse(CdtWriter.toString(same).contains("label="),
                "label equal to name must not be emitted");

        // label different from name → emitted.
        IDataTable diff = buildFromCdt("""
                dataset DM label="Demographics"
                col A type=Char
                ---
                x
                ---
                """);
        assertTrue(CdtWriter.toString(diff).contains("label=Demographics"));
    }


    @Test
    void datasetClassAttributeEmitted()
    {
        IDataTable t = buildFromCdt("""
                dataset ADSL class=ADSL
                col A type=Char
                ---
                x
                ---
                """);
        String out = CdtWriter.toString(t);
        assertTrue(out.contains("class=ADSL"), out);
    }


    @Test
    void genericDatasetMetadataRoundTrips()
    {
        // A lowercase, dot-free, no-uppercase key must round-trip as a header attr.
        IDataTable t = buildFromCdt("""
                dataset DM standard=foo
                col A type=Char
                ---
                x
                ---
                """);
        String out = CdtWriter.toString(t);
        assertTrue(out.contains("standard=foo"), out);

        CdtDataset back = CdtParser.parseFirst(out, "t");
        assertEquals("foo", back.getAttrs().get("standard"));
    }


    @Test
    void uppercaseAndDottedMetadataKeysAreNotEmitted()
    {
        DataTableMeta meta = DataTableMeta.builder().name("DM").label("DM").rowCount(1)
                .totalRowCount(1).tableURI(URI.create("test:t")).columns(new DataTableColumnMeta[]
                {
                        col(0, "A", DataValueType.STRING, null)
                })//
                .addMetaData("Origin", "derived") // uppercase → skipped
                .addMetaData("col.qualified", "v") // dotted → skipped
                .addMetaData("plain", "kept") // lowercase, no dot → kept
                .build();
        IDataTable t = directTable(meta, List.of(List.of("x")));
        String out = CdtWriter.toString(t);
        assertFalse(out.contains("Origin="), out);
        assertFalse(out.contains("col.qualified="), out);
        assertTrue(out.contains("plain=kept"), out);
    }


    @Test
    void columnLabelLengthFormatAndCodelistEmitted()
    {
        IDataTable t = buildFromCdt("""
                dataset DM
                col USUBJID type=Char label="Subject ID" length=20 codelist=NO
                ---
                S001
                ---
                """);
        String out = CdtWriter.toString(t);
        assertTrue(out.contains("label=\"Subject ID\""), out);
        assertTrue(out.contains("length=20"), out);
        assertTrue(out.contains("codelist=NO"), out);

        // Round-trip: parsing the output recovers the same column metadata.
        CdtDataset back = CdtParser.parseFirst(out, "t");
        CdtColumn c = back.getColumns().get(0);
        assertEquals("Subject ID", c.getLabel());
        assertEquals(Integer.valueOf(20), c.getLength());
        assertEquals("NO", c.getCodelist());
    }


    @Test
    void zeroLengthColumnOmitsLength()
    {
        DataTableMeta meta = DataTableMeta.builder().name("DM").label("DM").rowCount(1)
                .totalRowCount(1).tableURI(URI.create("test:t")).columns(new DataTableColumnMeta[]
                {
                        DataTableColumnMeta.builder().index(0).name("A").type(DataValueType.STRING)
                                .length(0).build()
                }).build();
        IDataTable t = directTable(meta, List.of(List.of("x")));
        assertFalse(CdtWriter.toString(t).contains("length="));
    }


    @Test
    void nativeTypeEmittedWhenDifferentFromKindToken()
    {
        DataTableMeta meta = DataTableMeta.builder().name("DM").label("DM").rowCount(1)
                .totalRowCount(1).tableURI(URI.create("test:t")).columns(new DataTableColumnMeta[]
                {
                        DataTableColumnMeta.builder().index(0).name("A").type(DataValueType.STRING)
                                .nativeType("VARCHAR").build()
                }).build();
        IDataTable t = directTable(meta, List.of(List.of("x")));
        // STRING classifies as Char; nativeType VARCHAR differs → emitted.
        assertTrue(CdtWriter.toString(t).contains("nativeType=VARCHAR"));
    }


    @Test
    void nativeTypeSuppressedWhenEqualToKindToken()
    {
        DataTableMeta meta = DataTableMeta.builder().name("DM").label("DM").rowCount(1)
                .totalRowCount(1).tableURI(URI.create("test:t")).columns(new DataTableColumnMeta[]
                {
                        DataTableColumnMeta.builder().index(0).name("A").type(DataValueType.STRING)
                                .nativeType("Char").build()
                }).build();
        IDataTable t = directTable(meta, List.of(List.of("x")));
        assertFalse(CdtWriter.toString(t).contains("nativeType="));
    }

    // ---- value rendering -----------------------------------------------------------


    @Test
    void numberRenderingIntegerVersusDecimal()
    {
        DataTableMeta meta = DataTableMeta.builder().name("N").label("N").rowCount(3)
                .totalRowCount(3).tableURI(URI.create("test:n")).columns(new DataTableColumnMeta[]
                {
                        col(0, "V", DataValueType.DOUBLE, null)
                }).build();
        IDataTable t = directTable(meta, List.of(java.util.Arrays.asList(42.0, 3.14, -7.0)));
        String out = CdtWriter.toString(t);
        // Whole double → integer form; fractional → toString; negative whole → integer form.
        assertTrue(out.contains("\n42\n"), out);
        assertTrue(out.contains("\n3.14\n"), out);
        assertTrue(out.contains("\n-7\n"), out);
    }


    @Test
    void nanNumberRendersEmpty()
    {
        DataTableMeta meta = DataTableMeta.builder().name("N").label("N").rowCount(1)
                .totalRowCount(1).tableURI(URI.create("test:n")).columns(new DataTableColumnMeta[]
                {
                        col(0, "A", DataValueType.DOUBLE, null),
                        col(1, "B", DataValueType.STRING, null)
                }).build();
        // NaN in a numeric col → "" ; with a second non-null col the row is not all-null.
        IDataTable t = directTable(meta,
                List.of(java.util.Arrays.asList((Object) Double.NaN), List.of("ok")));
        String out = CdtWriter.toString(t);
        // First field empty, then " | ok".
        assertTrue(out.contains("\n | ok\n"), out);
    }


    @Test
    void longTypedColumnRendersWithoutDecimalPoint()
    {
        DataTableMeta meta = DataTableMeta.builder().name("N").label("N").rowCount(1)
                .totalRowCount(1).tableURI(URI.create("test:n")).columns(new DataTableColumnMeta[]
                {
                        col(0, "V", DataValueType.LONG, null)
                }).build();
        IDataTable t = directTable(meta, List.of(List.of((Object) 123L)));
        String out = CdtWriter.toString(t);
        // A Long is a Number but not a Double → renderNumber's Number branch → "123".
        assertTrue(out.contains("\n123\n"), out);
        assertTrue(out.contains("type=Num"), out);
    }


    @Test
    void nullTypeColumnRendersViaToString()
    {
        // type=null exercises renderValue's null-type branch (checkNoControlChars path).
        DataTableMeta meta = DataTableMeta.builder().name("X").label("X").rowCount(1)
                .totalRowCount(1).tableURI(URI.create("test:x")).columns(new DataTableColumnMeta[]
                {
                        DataTableColumnMeta.builder().index(0).name("A").type(null).build()
                }).build();
        IDataTable t = directTable(meta, List.of(List.of((Object) "raw")));
        String out = CdtWriter.toString(t);
        // No type=... token is written when the column type is null.
        assertFalse(out.contains("type="), out);
        assertTrue(out.contains("\nraw\n"), out);
    }


    @Test
    void dateValueRenderedAsIsoFromSasDays()
    {
        // SAS day 0 == 1960-01-01. Build a Date column from a numeric SAS day value.
        DataTableMeta meta = DataTableMeta.builder().name("D").label("D").rowCount(2)
                .totalRowCount(2).tableURI(URI.create("test:d")).columns(new DataTableColumnMeta[]
                {
                        col(0, "DT", DataValueType.DOUBLE, "DATE9.")
                }).build();
        double y2021 = CdtValues.dateToSasDays("2021-03-15");
        IDataTable t = directTable(meta, List.of(java.util.Arrays.asList(0.0, y2021)));
        String out = CdtWriter.toString(t);
        assertTrue(out.contains("\n1960-01-01\n"), out);
        assertTrue(out.contains("\n2021-03-15\n"), out);
        assertTrue(out.contains("type=Date"), out);
    }


    @Test
    void timeValueRenderedAsIsoFromSeconds()
    {
        DataTableMeta meta = DataTableMeta.builder().name("T").label("T").rowCount(2)
                .totalRowCount(2).tableURI(URI.create("test:tm")).columns(new DataTableColumnMeta[]
                {
                        col(0, "TM", DataValueType.DOUBLE, "TIME8.")
                }).build();
        // 3661 seconds == 01:01:01
        IDataTable t = directTable(meta, List.of(java.util.Arrays.asList(0.0, 3661.0)));
        String out = CdtWriter.toString(t);
        assertTrue(out.contains("\n00:00:00\n"), out);
        assertTrue(out.contains("\n01:01:01\n"), out);
        assertTrue(out.contains("type=Time"), out);
    }


    @Test
    void datetimeValueRenderedAsIsoFromSasSeconds()
    {
        DataTableMeta meta = DataTableMeta.builder().name("DT").label("DT").rowCount(1)
                .totalRowCount(1).tableURI(URI.create("test:dt")).columns(new DataTableColumnMeta[]
                {
                        col(0, "X", DataValueType.DOUBLE, "DATETIME20.")
                }).build();
        double secs = CdtValues.datetimeToSasSeconds("1960-01-02T01:00:00");
        IDataTable t = directTable(meta, List.of(List.of((Object) secs)));
        String out = CdtWriter.toString(t);
        assertTrue(out.contains("\n1960-01-02T01:00:00\n"), out);
        assertTrue(out.contains("type=DateTime"), out);
    }


    @Test
    void classifyKindRecognisesVariousFormatPrefixes()
    {
        // Build one table per format family and confirm the column type token.
        assertEquals("Date", typeTokenFor("YYMMDD10."));
        assertEquals("Date", typeTokenFor("MONYY7."));
        assertEquals("Time", typeTokenFor("HHMM5."));
        assertEquals("DateTime", typeTokenFor("E8601DT."));
        assertEquals("Num", typeTokenFor("BEST12."));
        assertEquals("Num", typeTokenFor(null));
    }


    private static String typeTokenFor(String aFormat)
    {
        DataTableMeta meta = DataTableMeta.builder().name("X").label("X").rowCount(1)
                .totalRowCount(1).tableURI(URI.create("test:x")).columns(new DataTableColumnMeta[]
                {
                        col(0, "V", DataValueType.DOUBLE, aFormat)
                }).build();
        IDataTable t = directTable(meta, List.of(List.of((Object) 1.0)));
        String out = CdtWriter.toString(t);
        String colLine = out.lines().filter(l -> l.startsWith("col ")).findFirst()
                .orElseThrow(() -> new AssertionError("no col line in: " + out));
        int idx = colLine.indexOf("type=");
        String after = colLine.substring(idx + "type=".length());
        int sp = after.indexOf(' ');
        return sp < 0 ? after : after.substring(0, sp);
    }

    // ---- missing values ------------------------------------------------------------


    @Test
    void missingValueRendersEmptyAndAllNullRowUsesDotSentinel()
    {
        // A row whose every cell is null collapses to the "." sentinel (multi-column case).
        DataTableMeta meta = DataTableMeta.builder().name("M").label("M").rowCount(2)
                .totalRowCount(2).tableURI(URI.create("test:m")).columns(new DataTableColumnMeta[]
                {
                        col(0, "A", DataValueType.STRING, null),
                        col(1, "B", DataValueType.STRING, null)
                }).build();
        IDataTable t = directTable(meta, List.of(java.util.Arrays.asList("x", null),
                java.util.Arrays.asList((Object) null, null)));
        String out = CdtWriter.toString(t);
        assertTrue(out.contains("\nx | \n"), out); // partial-null row keeps empty field
        assertTrue(out.contains("\n.\n"), out); // all-null row → dot sentinel

        // Round-trip the all-null row back: a "." in a multi-column table → all empty.
        CdtDataset back = CdtParser.parseFirst(out, "t");
        assertEquals(List.of("", ""), back.getDataRows().get(1));
    }


    @Test
    void singleColumnAllNullRowIsNotCollapsedToDot()
    {
        // Single-column tables never collapse (colCount > 1 guard): an empty line is emitted.
        DataTableMeta meta = DataTableMeta.builder().name("S").label("S").rowCount(1)
                .totalRowCount(1).tableURI(URI.create("test:s")).columns(new DataTableColumnMeta[]
                {
                        col(0, "A", DataValueType.STRING, null)
                }).build();
        IDataTable t = directTable(meta, List.of(java.util.Arrays.asList((Object) null)));
        String out = CdtWriter.toString(t);
        assertFalse(out.contains("\n.\n"), out);
    }


    @Test
    void missingValueInstanceRendersEmpty()
    {
        // A MissingValue object (not plain null) must also render as empty via extractRaw.
        DataTableMeta meta = DataTableMeta.builder().name("M").label("M").rowCount(1)
                .totalRowCount(1).tableURI(URI.create("test:m")).columns(new DataTableColumnMeta[]
                {
                        col(0, "A", DataValueType.STRING, null),
                        col(1, "B", DataValueType.STRING, null)
                }).build();
        IDataTable t = directTable(meta, List
                .of(java.util.Arrays.asList((Object) MissingValue.MIS_UNKNOWN), List.of("keep")));
        String out = CdtWriter.toString(t);
        assertTrue(out.contains("\n | keep\n"), out);
    }

    // ---- quoting -------------------------------------------------------------------


    @Test
    void dataFieldQuotingForSpecialCharacters()
    {
        IDataTable t = buildFromCdt("""
                dataset Q
                col A type=Char
                col B type=Char
                col C type=Char
                col D type=Char
                ---
                "  leading" | "has | pipe" | "quote\\"here" | "#hash"
                ---
                """);
        String out = CdtWriter.toString(t);
        // Leading whitespace, embedded pipe, embedded quote, and leading-# all force quoting.
        assertTrue(out.contains("\"  leading\""), out);
        assertTrue(out.contains("\"has | pipe\""), out);
        assertTrue(out.contains("\"quote\\\"here\""), out);
        assertTrue(out.contains("\"#hash\""), out);

        // Full round-trip recovers the original cell values.
        CdtDataset back = CdtParser.parseFirst(out, "t");
        assertEquals(List.of("  leading", "has | pipe", "quote\"here", "#hash"),
                back.getDataRows().get(0));
    }


    @Test
    void literalDotCharValueIsQuoted()
    {
        // A literal "." in a Char column must be quoted so the parser does not treat it as the
        // all-null sentinel.
        IDataTable t = buildFromCdt("""
                dataset Q
                col A type=Char
                col B type=Char
                ---
                "." | x
                ---
                """);
        String out = CdtWriter.toString(t);
        assertTrue(out.contains("\".\" | x"), out);

        CdtDataset back = CdtParser.parseFirst(out, "t");
        assertEquals(List.of(".", "x"), back.getDataRows().get(0));
    }


    @Test
    void backslashInValueIsEscaped()
    {
        IDataTable t = buildFromCdt("""
                dataset Q
                col A type=Char
                ---
                "a\\\\b"
                ---
                """);
        String out = CdtWriter.toString(t);
        assertTrue(out.contains("\"a\\\\b\""), out);
        CdtDataset back = CdtParser.parseFirst(out, "t");
        assertEquals("a\\b", back.getDataRows().get(0).get(0));
    }


    @Test
    void plainValueNotQuoted()
    {
        IDataTable t = buildFromCdt("""
                dataset Q
                col A type=Char
                ---
                plain
                ---
                """);
        assertTrue(CdtWriter.toString(t).contains("\nplain\n"));
    }

    // ---- control-character rejection ----------------------------------------------


    @Test
    void embeddedNewlineInDataCellThrows()
    {
        DataTableMeta meta = DataTableMeta.builder().name("X").label("X").rowCount(1)
                .totalRowCount(1).tableURI(URI.create("test:x")).columns(new DataTableColumnMeta[]
                {
                        col(0, "A", DataValueType.STRING, null)
                }).build();
        IDataTable t = directTable(meta, List.of(List.of((Object) "line1\nline2")));
        IOException ex = assertThrows(IOException.class,
                () -> CdtWriter.writeDataset(t, null, null, null, new StringBuilder()));
        assertTrue(ex.getMessage().contains("control character"), ex.getMessage());
    }


    @Test
    void embeddedCarriageReturnInDataCellThrows()
    {
        DataTableMeta meta = DataTableMeta.builder().name("X").label("X").rowCount(1)
                .totalRowCount(1).tableURI(URI.create("test:x")).columns(new DataTableColumnMeta[]
                {
                        col(0, "A", DataValueType.STRING, null)
                }).build();
        IDataTable t = directTable(meta, List.of(List.of((Object) "a\rb")));
        assertThrows(IOException.class,
                () -> CdtWriter.writeDataset(t, null, null, null, new StringBuilder()));
    }


    @Test
    void newlineInHeaderLabelThrows()
    {
        // toString swallows IOException into IllegalStateException; assert that wrapper fires.
        DataTableMeta meta = DataTableMeta.builder().name("X").label("bad\nlabel").rowCount(1)
                .totalRowCount(1).tableURI(URI.create("test:x")).columns(new DataTableColumnMeta[]
                {
                        col(0, "A", DataValueType.STRING, null)
                }).build();
        IDataTable t = directTable(meta, List.of(List.of((Object) "x")));
        assertThrows(IllegalStateException.class, () -> CdtWriter.toString(t));
    }

    // ---- file write / append -------------------------------------------------------


    @Test
    void writeToFileProducesParsableContent(@TempDir Path tmp) throws IOException
    {
        IDataTable t = buildFromCdt("""
                dataset DM
                col USUBJID type=Char
                col AGE type=Num
                ---
                S001 | 42
                ---
                """);
        Path p = tmp.resolve("out.cdt");
        CdtWriter.write(t, p);
        String content = Files.readString(p, StandardCharsets.UTF_8);

        CdtDataset back = CdtParser.parseFirst(content, p.toString());
        assertEquals("DM", back.getName());
        assertEquals(2, back.getColumns().size());
        assertEquals(List.of("S001", "42"), back.getDataRows().get(0));
    }


    @Test
    void appendAddsSecondDatasetBlock(@TempDir Path tmp) throws IOException
    {
        IDataTable dm = buildFromCdt("""
                dataset DM
                col A type=Char
                ---
                d
                ---
                """);
        IDataTable ae = buildFromCdt("""
                dataset AE
                col B type=Char
                ---
                e
                ---
                """);
        Path p = tmp.resolve("multi.cdt");
        CdtWriter.write(dm, p);
        CdtWriter.write(ae, "AE", null, null, p, true);

        String content = Files.readString(p, StandardCharsets.UTF_8);
        List<CdtDataset> all = CdtParser.parseAll(content, p.toString());
        assertEquals(2, all.size());
        assertEquals("DM", all.get(0).getName());
        assertEquals("AE", all.get(1).getName());
    }


    @Test
    void appendWhenFileMissingBehavesLikeFreshWrite(@TempDir Path tmp) throws IOException
    {
        // aAppend=true but the file does not exist yet → create (no leading blank line).
        IDataTable dm = buildFromCdt("""
                dataset DM
                col A type=Char
                ---
                d
                ---
                """);
        Path p = tmp.resolve("new.cdt");
        CdtWriter.write(dm, "DM", null, null, p, true);
        String content = Files.readString(p, StandardCharsets.UTF_8);
        assertTrue(content.startsWith("dataset DM"), content);
        assertEquals("DM", CdtParser.parseFirst(content, p.toString()).getName());
    }


    @Test
    void overwriteReplacesExistingContent(@TempDir Path tmp) throws IOException
    {
        Path p = tmp.resolve("ow.cdt");
        Files.writeString(p, "stale content that must be replaced\n", StandardCharsets.UTF_8);
        IDataTable dm = buildFromCdt("""
                dataset NEW
                col A type=Char
                ---
                v
                ---
                """);
        CdtWriter.write(dm, p);
        String content = Files.readString(p, StandardCharsets.UTF_8);
        assertFalse(content.contains("stale"), content);
        assertEquals("NEW", CdtParser.parseFirst(content, p.toString()).getName());
    }


    @Test
    void explicitDatasetNameLabelAndClassOverrideTableMeta()
    {
        IDataTable t = buildFromCdt("""
                dataset ORIG
                col A type=Char
                ---
                v
                ---
                """);
        StringBuilder sb = new StringBuilder();
        try
        {
            CdtWriter.writeDataset(t, "OVERRIDE", "My Label", "EVENTS", sb);
        }
        catch (IOException ex)
        {
            throw new AssertionError(ex);
        }
        String out = sb.toString();
        assertTrue(out.startsWith("dataset OVERRIDE"), out);
        assertTrue(out.contains("label=\"My Label\""), out);
        assertTrue(out.contains("class=EVENTS"), out);
    }
}
