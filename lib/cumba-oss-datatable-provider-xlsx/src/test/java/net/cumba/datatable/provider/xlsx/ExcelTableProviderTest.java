package net.cumba.datatable.provider.xlsx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.MissingValue;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ExcelTableProvider}.
 */
class ExcelTableProviderTest
{

    private ExcelTableProvider provider;

    @BeforeEach
    void setUp()
    {
        provider = new ExcelTableProvider();
    }


    /**
     * Creates a temp XLSX file with the given workbook content and returns its URI.
     */
    private URI writeTempXlsx(WorkbookBuilder builder) throws IOException
    {
        File tmpFile = File.createTempFile("xlsxtest", ".xlsx");
        tmpFile.deleteOnExit();
        try (Workbook wb = new XSSFWorkbook())
        {
            builder.build(wb);
            try (FileOutputStream fos = new FileOutputStream(tmpFile))
            {
                wb.write(fos);
            }
        }
        return tmpFile.toURI();
    }

    @FunctionalInterface
    interface WorkbookBuilder
    {

        void build(Workbook wb);
    }

    @Test
    void testGetSupportedFileInfos()
    {
        List<FileInfo> fis = provider.getSupportedFileInfos();
        assertNotNull(fis);
        assertEquals(2, fis.size());
        assertSame(ExcelProviderSupplier.FIS, fis);
    }


    @Test
    void testDefaultGuessingRowCount()
    {
        assertEquals(ExcelTableProvider.DEFAULT_GUESS_ROW_COUNT, provider.getGuessingRowCount());
    }


    @Test
    void testSetGuessingRowCount()
    {
        provider.setGuessingRowCount(50);
        assertEquals(50, provider.getGuessingRowCount());
    }


    @Test
    void testProvideSimpleTable() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("NAME");
            h.createCell(1).setCellValue("VALUE");
            Row r1 = s.createRow(1);
            r1.createCell(0).setCellValue("Alice");
            r1.createCell(1).setCellValue(1.5);
            Row r2 = s.createRow(2);
            r2.createCell(0).setCellValue("Bob");
            r2.createCell(1).setCellValue(2.7);
        });

        IDataTable table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);

        assertNotNull(table);
        assertEquals(2, table.getRowCount());
        assertEquals(2, table.getColumnCount());
        assertEquals("Alice", table.getValue(0, 0));
        assertEquals("Bob", table.getValue(1, 0));
    }


    @Test
    void testProvideDetectsDoubleColumn() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("NAME");
            h.createCell(1).setCellValue("SCORE");
            Row r1 = s.createRow(1);
            r1.createCell(0).setCellValue("A");
            r1.createCell(1).setCellValue(95.5);
            Row r2 = s.createRow(2);
            r2.createCell(0).setCellValue("B");
            r2.createCell(1).setCellValue(87.3);
        });

        IDataTable table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);

        assertEquals(DataValueType.STRING, table.getMetaData().getColumn(0).getType());
        assertEquals(DataValueType.DOUBLE, table.getMetaData().getColumn(1).getType());
    }


    @Test
    void testProvideDoubleValues() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("VAL");
            Row r1 = s.createRow(1);
            r1.createCell(0).setCellValue(3.14);
            Row r2 = s.createRow(2);
            r2.createCell(0).setCellValue(-1.5);
        });

        IDataTable table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);

        assertEquals(DataValueType.DOUBLE, table.getMetaData().getColumn(0).getType());
        assertEquals(3.14, (double) table.getValue(0, 0), 0.001);
        assertEquals(-1.5, (double) table.getValue(1, 0), 0.001);
    }


    @Test
    void testProvideMissingDoubleValues() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("VAL");
            Row r1 = s.createRow(1);
            r1.createCell(0).setCellValue(1.0);
            // row 2: blank cell for VAL column
            s.createRow(2);
            Row r3 = s.createRow(3);
            r3.createCell(0).setCellValue(3.0);
        });

        IDataTable table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);

        assertEquals(3, table.getRowCount());
        assertEquals(1.0, (double) table.getValue(0, 0), 0.001);
        assertInstanceOf(MissingValue.class, table.getValue(1, 0));
        assertEquals(3.0, (double) table.getValue(2, 0), 0.001);
    }


    @Test
    void testProvideHeaderOnly() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("COL1");
            h.createCell(1).setCellValue("COL2");
        });

        IDataTable table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);

        assertNotNull(table);
        assertEquals(0, table.getRowCount());
        assertEquals(2, table.getColumnCount());
    }


    @Test
    void testProvideEmptySheetThrows() throws Exception
    {
        URI uri = writeTempXlsx(wb -> wb.createSheet("EMPTY"));

        assertThrows(IOException.class, () -> provider.provide(uri, ExcelProviderSupplier.FI_XLSX));
    }


    @Test
    void testProvideSheetByFragment() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s1 = wb.createSheet("Sheet1");
            Row h1 = s1.createRow(0);
            h1.createCell(0).setCellValue("A");
            Row r1 = s1.createRow(1);
            r1.createCell(0).setCellValue("fromSheet1");

            Sheet s2 = wb.createSheet("Sheet2");
            Row h2 = s2.createRow(0);
            h2.createCell(0).setCellValue("B");
            Row r2 = s2.createRow(1);
            r2.createCell(0).setCellValue("fromSheet2");
        });

        // Add fragment to select Sheet2
        URI uriWithFragment = new URI(uri.getScheme(), uri.getSchemeSpecificPart(), "Sheet2");

        IDataTable table = provider.provide(uriWithFragment, ExcelProviderSupplier.FI_XLSX);

        assertNotNull(table);
        assertEquals(1, table.getRowCount());
        assertEquals("fromSheet2", table.getValue(0, 0));
    }


    @Test
    void testProvideNonExistentSheetThrows() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("COL");
        });

        URI uriWithFragment = new URI(uri.getScheme(), uri.getSchemeSpecificPart(), "NOSUCHSHEET");

        assertThrows(IOException.class,
                () -> provider.provide(uriWithFragment, ExcelProviderSupplier.FI_XLSX));
    }


    @Test
    void testProvidePreservesColumnNames() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("USUBJID");
            h.createCell(1).setCellValue("AVAL");
            h.createCell(2).setCellValue("VISIT");
            Row r = s.createRow(1);
            r.createCell(0).setCellValue("S01");
            r.createCell(1).setCellValue(1.0);
            r.createCell(2).setCellValue("V1");
        });

        IDataTable table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);

        assertEquals("USUBJID", table.getMetaData().getColumn(0).getName());
        assertEquals("AVAL", table.getMetaData().getColumn(1).getName());
        assertEquals("VISIT", table.getMetaData().getColumn(2).getName());
    }


    @Test
    void testProvideWithManyRows() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("NAME");
            h.createCell(1).setCellValue("IDX");
            for (int i = 0; i < 50; i++)
            {
                Row r = s.createRow(i + 1);
                r.createCell(0).setCellValue("Row" + i);
                r.createCell(1).setCellValue(i);
            }
        });

        IDataTable table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);

        assertEquals(50, table.getRowCount());
        assertEquals("Row0", table.getValue(0, 0));
        assertEquals("Row49", table.getValue(49, 0));
    }


    @Test
    void testProvideInvalidURIThrows() throws Exception
    {
        URI uri = new URI("file:///nonexistent/path/file.xlsx");
        assertThrows(Exception.class, () -> provider.provide(uri, ExcelProviderSupplier.FI_XLSX));
    }


    /**
     * A header cell that is STRING-typed but blank (empty or whitespace) must not yield a blank
     * column name; the provider substitutes {@code "V" + (idx + 1)}. See
     * {@code PLAN-nonnull-column-name.md} batch 2D.
     * <p>
     * F-B14: after the determineColumns rework, the FIRST blank cell terminates the header range —
     * middle-blank-headers no longer get a fallback name; they truncate the header.
     */
    @Test
    void testProvideBlankHeaderTerminatesRange() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("NAME");
            h.createCell(1).setCellValue("   "); // whitespace-only, STRING type — terminator
            h.createCell(2).setCellValue("SCORE");
            Row r1 = s.createRow(1);
            r1.createCell(0).setCellValue("Alice");
            r1.createCell(1).setCellValue("x");
            r1.createCell(2).setCellValue("y");
        });

        IDataTable table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);

        assertEquals(1, table.getColumnCount());
        assertEquals("NAME", table.getMetaData().getColumn(0).getName());
    }


    /**
     * The metadata-only path goes through a separate code block. Verify the same termination
     * applies to {@code provideMetaData}.
     */
    @Test
    void testProvideMetaDataBlankHeaderTerminatesRange() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("NAME");
            h.createCell(1).setCellValue(""); // empty STRING header — terminator
            h.createCell(2).setCellValue("SCORE");
            Row r1 = s.createRow(1);
            r1.createCell(0).setCellValue("Alice");
            r1.createCell(1).setCellValue("x");
            r1.createCell(2).setCellValue("y");
        });

        var meta = provider.provideMetaData(uri, ExcelProviderSupplier.FI_XLSX);

        assertEquals(1, meta.getColumnCount());
        assertEquals("NAME", meta.getColumn(0).getName());
    }


    /**
     * F-B14: a numeric header cell is a valid header — its display value (via cell.toString()) is
     * used as the column name. Previously the old STRING-only filter would have dropped these
     * columns entirely.
     */
    @Test
    void testProvideNumericHeaderAccepted() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("NAME");
            h.createCell(1).setCellValue(2026.0); // numeric header — F-B14 accepts this
            h.createCell(2).setCellValue("SCORE");
            Row r1 = s.createRow(1);
            r1.createCell(0).setCellValue("Alice");
            r1.createCell(1).setCellValue(1);
            r1.createCell(2).setCellValue("y");
        });

        IDataTable table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);
        assertEquals(3, table.getColumnCount());
        assertEquals("NAME", table.getMetaData().getColumn(0).getName());
        // Numeric → Double.toString gives e.g. "2026.0"; either way the column is named
        // and is non-blank.
        String middleName = table.getMetaData().getColumn(1).getName();
        assertNotNull(middleName);
        assertTrue(middleName.startsWith("2026"),
                "numeric header should render to a string starting with '2026': " + middleName);
        assertEquals("SCORE", table.getMetaData().getColumn(2).getName());
    }


    /**
     * F-B14: a boolean header cell is also a valid header.
     */
    @Test
    void testProvideBooleanHeaderAccepted() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("NAME");
            h.createCell(1).setCellValue(true); // boolean header
            Row r1 = s.createRow(1);
            r1.createCell(0).setCellValue("Alice");
            r1.createCell(1).setCellValue("v");
        });

        IDataTable table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);
        assertEquals(2, table.getColumnCount());
        String middleName = table.getMetaData().getColumn(1).getName();
        assertNotNull(middleName);
        assertTrue(!middleName.isBlank(), "boolean header should produce a non-blank name");
    }

    // ==================== F-B15: BOOLEAN, DATE-formatted numeric, ERROR cells ====================


    /**
     * F-B15: a BOOLEAN-typed data cell must be carried through as {@code "true"}/{@code "false"} (a
     * non-blank string), not silently dropped to {@code null}. The all-boolean column ends up
     * STRING-typed because boolean values are not parseable as doubles.
     */
    @Test
    void testProvideBooleanDataCellSurfacedAsString() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("FLAG");
            Row r1 = s.createRow(1);
            r1.createCell(0).setCellValue(true);
            Row r2 = s.createRow(2);
            r2.createCell(0).setCellValue(false);
        });

        IDataTable table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);
        assertEquals(2, table.getRowCount());
        assertEquals(DataValueType.STRING, table.getMetaData().getColumn(0).getType());
        assertEquals("true", table.getValue(0, 0));
        assertEquals("false", table.getValue(1, 0));
    }


    /**
     * F-B15: a date-formatted numeric cell stays numeric ({@code DOUBLE}) but acquires an
     * {@code E8601DT.} displayFormat so the SAS format catalog renders the underlying SAS-
     * datetime-seconds value as ISO at display time. The numeric value itself is
     * {@code (epochMillis - SAS_DATETIME_EPOCH_MILLIS) / 1000.0} — convertible back to a calendar
     * date via the format catalog or by adding the SAS epoch.
     */
    @Test
    void testProvideDateFormattedNumericIsDoubleWithIsoDisplayFormat() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            DataFormat df = wb.createDataFormat();
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(df.getFormat("yyyy-mm-dd"));

            Row h = s.createRow(0);
            h.createCell(0).setCellValue("DT");
            Row r1 = s.createRow(1);
            org.apache.poi.ss.usermodel.Cell cell = r1.createCell(0);
            // Use a deterministic UTC instant for Jan 15 2024.
            cell.setCellValue(java.util.Date.from(java.time.LocalDate.of(2024, 1, 15)
                    .atStartOfDay(java.time.ZoneOffset.UTC).toInstant()));
            cell.setCellStyle(dateStyle);
        });

        IDataTable table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);
        assertEquals(1, table.getRowCount());
        assertEquals(DataValueType.DOUBLE, table.getMetaData().getColumn(0).getType(),
                "date-formatted cells stay numeric — they're Excel-style serial dates");
        assertEquals("E8601DT.", table.getMetaData().getColumn(0).getDisplayFormat(),
                "date-formatted column must carry E8601DT. so the format catalog renders ISO");
        // SAS-datetime-seconds for 2024-01-15 00:00:00 UTC =
        // (epoch_2024_01_15 - epoch_1960_01_01) seconds
        // epoch_2024_01_15 = 1705276800
        // epoch_1960_01_01 = -315619200
        // diff = 2020896000 seconds
        Object v = table.getValue(0, 0);
        assertNotNull(v, "date-formatted numeric must not be dropped to null");
        assertEquals(2020896000.0, ((Number) v).doubleValue(), 0.5,
                "value must equal SAS-datetime-seconds of 2024-01-15 UTC");
    }


    /**
     * F-B15: a non-date-formatted numeric cell is still surfaced as a Double (existing behaviour).
     * Pinned to defend against accidental over-reach of the date branch.
     */
    @Test
    void testProvideNumericNotDateFormattedStillNumeric() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("N");
            Row r1 = s.createRow(1);
            r1.createCell(0).setCellValue(42.5);
        });

        IDataTable table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);
        assertEquals(DataValueType.DOUBLE, table.getMetaData().getColumn(0).getType());
        assertEquals(42.5, (double) table.getValue(0, 0), 0.0001);
    }


    /**
     * F-B15: an ERROR-typed cell must round-trip as POI's actual Excel error code string
     * ({@code #DIV/0!}, {@code #N/A}, {@code #REF!}, …) rather than a generic token. Using the real
     * error code gives the user a real diagnosis hook and minimises collision with plain
     * user-authored text.
     */
    @Test
    void testProvideErrorCellSurfacedAsPoiErrorCode() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("X");
            Row r1 = s.createRow(1);
            r1.createCell(0).setCellErrorValue(FormulaError.DIV0.getCode());
        });

        IDataTable table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);
        assertEquals(1, table.getRowCount());
        Object v = table.getValue(0, 0);
        assertNotNull(v, "ERROR cell must not be silently dropped to null");
        assertEquals(FormulaError.DIV0.getString(), v.toString(),
                "ERROR cell must round-trip as the actual Excel error code (e.g. #DIV/0!)");
    }


    @Test
    @SuppressWarnings("JavaUtilDate") // POI's Cell.setCellValue/getDateCellValue work in
                                      // java.util.Date
    void testProvideHandlesDateBooleanAndFormulaCells() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            CellStyle dateStyle = wb.createCellStyle();
            DataFormat fmt = wb.createDataFormat();
            dateStyle.setDataFormat(fmt.getFormat("yyyy-mm-dd"));

            Row h = s.createRow(0);
            h.createCell(0).setCellValue("DT");
            h.createCell(1).setCellValue("FLAG");
            h.createCell(2).setCellValue("CALC");
            h.createCell(3).setCellValue("TXTCALC");

            for (int r = 1; r <= 2; r++)
            {
                Row row = s.createRow(r);
                org.apache.poi.ss.usermodel.Cell dc = row.createCell(0);
                dc.setCellValue(new java.util.Date(86_400_000L * r));
                dc.setCellStyle(dateStyle);
                row.createCell(1).setCellValue(r % 2 == 0);
                row.createCell(2).setCellFormula("1+" + r);
                row.createCell(3).setCellFormula("\"x\"&" + r);
            }
            // Populate cached formula results so the streaming reader returns them.
            wb.getCreationHelper().createFormulaEvaluator().evaluateAll();
        });

        IDataTable table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);
        assertEquals(4, table.getColumnCount());
        assertEquals(2, table.getRowCount());

        // Boolean column surfaces as its string form.
        assertEquals("false", table.getValue(0, 1), "row 0 FLAG should be boolean false");
        // Numeric formula 1+1 = 2 surfaces as a double.
        assertEquals(2.0, ((Number) table.getValue(0, 2)).doubleValue(), 1e-9);
        // String formula "x"&1 surfaces as text.
        assertEquals("x1", table.getValue(0, 3));
        // Date column is present and non-null (encoded as a numeric SAS-epoch value).
        assertNotNull(table.getValue(0, 0), "date cell must not be null");
    }


    @Test
    void inferColumns_emptySheetReturnsNoColumns()
    {
        try (Workbook wb = new XSSFWorkbook())
        {
            Sheet s = wb.createSheet("E");
            assertEquals(0,
                    ExcelTableProvider.inferColumns(s, URI.create("file:///e.xlsx"), 10).length);
        }
        catch (IOException ex)
        {
            throw new AssertionError(ex);
        }
    }


    @Test
    void inferColumns_headerRowWithNoCellsReturnsNoColumns() throws IOException
    {
        try (Workbook wb = new XSSFWorkbook())
        {
            Sheet s = wb.createSheet("H");
            s.createRow(0); // header row with no cells
            s.createRow(1).createCell(0).setCellValue("data");
            assertEquals(0,
                    ExcelTableProvider.inferColumns(s, URI.create("file:///h.xlsx"), 10).length);
        }
    }


    @Test
    void inferColumns_nullCellTerminatesHeaderRange() throws IOException
    {
        try (Workbook wb = new XSSFWorkbook())
        {
            Sheet s = wb.createSheet("G");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("A");
            // index 1 left as a gap (no cell) -> terminates the contiguous header range
            h.createCell(2).setCellValue("C");
            s.createRow(1).createCell(0).setCellValue(1.0);

            DataTableColumnMeta[] cols = ExcelTableProvider.inferColumns(s,
                    URI.create("file:///g.xlsx"), 10);
            assertEquals(1, cols.length);
            assertEquals("A", cols[0].getName());
        }
    }


    @Test
    void inferColumns_acceptsNumericAndFormulaHeadersAndStopsAtError() throws IOException
    {
        try (Workbook wb = new XSSFWorkbook())
        {
            Sheet s = wb.createSheet("F");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("STR");
            h.createCell(1).setCellValue(42.0); // numeric header cell
            h.createCell(2).setCellFormula("1+1"); // numeric-formula header cell
            h.createCell(3).setCellErrorValue(FormulaError.NA.getCode()); // ERROR terminates
            h.createCell(4).setCellValue("AFTER"); // excluded (after the terminator)

            Row d = s.createRow(1);
            d.createCell(0).setCellValue("x");
            d.createCell(1).setCellValue(1.0);
            d.createCell(2).setCellValue(2.0);

            wb.getCreationHelper().createFormulaEvaluator().evaluateAll();

            DataTableColumnMeta[] cols = ExcelTableProvider.inferColumns(s,
                    URI.create("file:///f.xlsx"), 10);
            assertEquals(3, cols.length);
            assertEquals("STR", cols[0].getName());
        }
    }

}
