package net.cumba.datatable.provider.xlsx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.pjfanning.xlsx.StreamingReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.util.List;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.provider.xlsx.testsupport.LoggerCapture;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.MissingValue;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

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
        // Q37: one, not two — legacy .xls was removed from ExcelProviderSupplier.FIS because
        // excel-streaming-reader cannot read it. This assertion named no constant, so a grep for
        // FI_XLS did not find it.
        assertEquals(1, fis.size());
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

    // ==================== text-typed numeric-looking cells (fixed defect) ====================


    /**
     * DEFECT FIX: a column whose cells are all Excel-{@code TEXT}-typed but numeric-looking (e.g. a
     * site/subject ID kept as text specifically to preserve a leading zero) must stay STRING-typed
     * and must not be reparsed into a double — that would silently destroy the leading zero the
     * producer's text formatting existed to protect. Confirmed against an independently
     * (openpyxl-)produced fixture that these are genuinely {@code t="str"} text cells, not merely
     * numeric-looking; POI is used here only as the test-fixture builder, exactly like every other
     * test in this class — {@code cell.setCellValue(String)} creates the same STRING cell type
     * either way.
     */
    @Test
    void testTextTypedNumericLookingColumnStaysStringAndPreservesLeadingZeros() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            s.createRow(0).createCell(0).setCellValue("SITEID");
            s.createRow(1).createCell(0).setCellValue("0001");
            s.createRow(2).createCell(0).setCellValue("0002");
            s.createRow(3).createCell(0).setCellValue("0010");
        });

        IDataTable table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);

        assertEquals(DataValueType.STRING, table.getMetaData().getColumn(0).getType(),
                "a text-typed numeric-looking column must NOT be promoted to DOUBLE");
        assertEquals("0001", table.getValue(0, 0));
        assertEquals("0002", table.getValue(1, 0));
        assertEquals("0010", table.getValue(2, 0),
                "leading zero must survive — this is exactly what TEXT formatting was for");
    }


    /**
     * A single text-typed numeric-looking cell mixed into an otherwise all-numeric column must
     * still disqualify the WHOLE column from DOUBLE — not just that one cell. This is the
     * majority-vote type-inference design (one pass decides the column's type for every row), so
     * the fix has to live in the vote (`isNumberOrMissing`), not in a per-cell special case.
     */
    @Test
    void testOneTextTypedCellKeepsWholeColumnString() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            s.createRow(0).createCell(0).setCellValue("ID");
            s.createRow(1).createCell(0).setCellValue(5.0); // real NUMERIC cell
            s.createRow(2).createCell(0).setCellValue("0007"); // TEXT cell, numeric-looking
        });

        IDataTable table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);

        assertEquals(DataValueType.STRING, table.getMetaData().getColumn(0).getType());
        assertEquals("5.0", table.getValue(0, 0));
        assertEquals("0007", table.getValue(1, 0));
    }


    /**
     * The {@code getDoubleValue} text-parse fallback (distinct from the type-inference fix above)
     * still exists for a column that WAS correctly inferred DOUBLE from its sample, when a text
     * cell beyond the {@code guessingRowCount} window happens to parse as a number — the sample
     * can't see every row in a streamed sheet, so this keeps such a row's real value rather than
     * losing it.
     */
    @Test
    void testTextCellBeyondSampleThatParsesIsStillReadAsItsNumericValue() throws Exception
    {
        provider.setGuessingRowCount(2);
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            s.createRow(0).createCell(0).setCellValue("VAL");
            s.createRow(1).createCell(0).setCellValue(1.0);
            s.createRow(2).createCell(0).setCellValue(2.0);
            // Beyond the 2-row sample: a TEXT cell whose content parses cleanly.
            s.createRow(3).createCell(0).setCellValue("42");
        });

        IDataTable table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);

        assertEquals(DataValueType.DOUBLE, table.getMetaData().getColumn(0).getType());
        assertEquals(42.0, (double) table.getValue(2, 0), 0.0001);
    }

    // ==================== H3 DEFECT FIX: a date column with inconsistent per-cell styling
    // ====================


    /**
     * DEFECT FIX (H3): a date column where not every sampled cell carries the date cell style (a
     * realistic shape — a copy/paste or a partial re-format loses the style from a cell while its
     * value stays numeric) must convert EVERY numeric cell the same way, once the COLUMN as a whole
     * is decided to be a date column. Before the fix, the non-date-styled straggler cell's raw
     * Excel serial (45001) was stored unconverted while its date-styled neighbours (45000, 45002)
     * were converted to SAS-datetime-seconds — the format catalog then rendered the straggler's raw
     * serial as if it WERE SAS-seconds, reading back roughly 63 years wrong (1960 instead of 2023).
     */
    @Test
    void testDateColumnWithOneNonDateStyledCellConvertsAllCellsUniformly() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            DataFormat df = wb.createDataFormat();
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(df.getFormat("yyyy-mm-dd"));

            Row h = s.createRow(0);
            h.createCell(0).setCellValue("VSDTC");

            // Row 1: date-styled, serial 45000 (2023-03-15).
            Row r1 = s.createRow(1);
            org.apache.poi.ss.usermodel.Cell c1 = r1.createCell(0);
            c1.setCellValue(45000.0);
            c1.setCellStyle(dateStyle);

            // Row 2: the straggler — same column, NOT date-styled, serial 45001 (2023-03-16).
            Row r2 = s.createRow(2);
            r2.createCell(0).setCellValue(45001.0);

            // Row 3: date-styled again, serial 45002 (2023-03-17).
            Row r3 = s.createRow(3);
            org.apache.poi.ss.usermodel.Cell c3 = r3.createCell(0);
            c3.setCellValue(45002.0);
            c3.setCellStyle(dateStyle);
        });

        IDataTable table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);

        assertEquals(DataValueType.DOUBLE, table.getMetaData().getColumn(0).getType());
        assertEquals("E8601DT.", table.getMetaData().getColumn(0).getDisplayFormat());

        // All three rows must be in the SAME unit system (SAS-datetime-seconds), one day apart —
        // never a raw Excel serial mixed in among converted values.
        double dayInSeconds = 86_400.0;
        double v0 = ((Number) table.getValue(0, 0)).doubleValue();
        double v1 = ((Number) table.getValue(1, 0)).doubleValue();
        double v2 = ((Number) table.getValue(2, 0)).doubleValue();
        assertEquals(v0 + dayInSeconds, v1, 0.5,
                "the non-date-styled straggler must be converted like its neighbours, not left raw");
        assertEquals(v1 + dayInSeconds, v2, 0.5);
        // Sanity: v0 must be in SAS-seconds range for 2023, not a raw ~45000 Excel serial.
        assertTrue(v0 > 1_000_000_000.0,
                "value must be SAS-datetime-seconds (billions), not a raw Excel serial (~45000)");
    }


    /**
     * A date column with a genuinely missing/blank cell in one row must read that row as
     * {@link MissingValue}, not as the SAS epoch (1960-01-01, i.e. {@code 0.0} seconds) — which is
     * exactly what a zero-initialised-by-default {@code double[]} would produce for a slot the
     * per-cell loop never touched. Pins {@code ExcelRow}'s explicit {@code NaN} initialisation of
     * its date-values array.
     */
    @Test
    void testDateColumnWithMissingCellIsMissingNotSasEpochZero() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            DataFormat df = wb.createDataFormat();
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(df.getFormat("yyyy-mm-dd"));

            Row h = s.createRow(0);
            h.createCell(0).setCellValue("VSDTC");

            Row r1 = s.createRow(1);
            org.apache.poi.ss.usermodel.Cell c1 = r1.createCell(0);
            c1.setCellValue(45000.0);
            c1.setCellStyle(dateStyle);

            // Row 2: no cell at all in this column — a genuine gap, not a value.
            s.createRow(2);

            Row r3 = s.createRow(3);
            org.apache.poi.ss.usermodel.Cell c3 = r3.createCell(0);
            c3.setCellValue(45002.0);
            c3.setCellStyle(dateStyle);
        });

        IDataTable table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);

        assertEquals("E8601DT.", table.getMetaData().getColumn(0).getDisplayFormat());
        assertInstanceOf(MissingValue.class, table.getValue(1, 0),
                "a missing cell in a date column must read as missing, not as SAS-epoch-zero");
    }

    // ==================== H6 DEFECT FIX: formula-cached error must give the real code
    // ====================


    /**
     * DEFECT FIX (H6): a data cell holding a FORMULA whose cached result is an ERROR — {@code =1/0}
     * is exactly how a real spreadsheet gets one; nobody types a literal error constant — must
     * surface the real Excel error code, not the generic {@code "#ERROR"} fallback. Before the fix,
     * EVERY formula-cached error came back as {@code "#ERROR"}, because {@code getErrorCellValue()}
     * throws for a cell whose own type is FORMULA (confirmed against the actual streaming reader).
     */
    @Test
    void testFormulaCachedErrorSurfacesRealCodeNotGenericFallback() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            s.createRow(0).createCell(0).setCellValue("X");
            Row r1 = s.createRow(1);
            r1.createCell(0).setCellFormula("1/0");
            org.apache.poi.ss.usermodel.FormulaEvaluator ev = wb.getCreationHelper()
                    .createFormulaEvaluator();
            ev.evaluateAll();
        });

        IDataTable table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);

        assertEquals(FormulaError.DIV0.getString(), table.getValue(0, 0),
                "a formula-cached error must surface the REAL Excel error code, not '#ERROR'");
    }

    // ==================== H4 DEFECT FIX: one bad cell must not abort the whole sheet
    // ====================


    /**
     * Patches a valid workbook at the raw XML level, replacing one literal fragment of
     * {@code xl/worksheets/sheet1.xml} with another and leaving every other byte of the file
     * untouched — so the result is a targeted, realistic variant rather than a wholesale garbage
     * file. Fails the test if the fragment was not found, so a POI-version change to the emitted
     * XML reds the fixture instead of silently producing an unpatched workbook.
     * <p>
     * Used both to corrupt a numeric cell's cached {@code <v>} (the shape a third-party/legacy
     * writer can leave behind) and to give a formula cell the EMPTY cached value element openpyxl
     * writes.
     */
    private static byte[] patchSheet1Xml(byte[] aXlsxBytes, String aOriginalXml,
            String aReplacementXml)
        throws IOException
    {
        return rewriteSheet1Xml(aXlsxBytes, xml ->
        {
            String patched = xml.replace(aOriginalXml, aReplacementXml);
            assertNotEquals(xml, patched, "fixture setup: the target XML was not found");
            return patched;
        });
    }


    /**
     * Rewrites {@code xl/worksheets/sheet1.xml} of a workbook through the given function, copying
     * every other zip entry verbatim.
     */
    private static byte[] rewriteSheet1Xml(byte[] aXlsxBytes,
            java.util.function.UnaryOperator<String> aPatch)
        throws IOException
    {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipInputStream zin = new java.util.zip.ZipInputStream(
                new java.io.ByteArrayInputStream(aXlsxBytes));
                java.util.zip.ZipOutputStream zout = new java.util.zip.ZipOutputStream(out))
        {
            java.util.zip.ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null)
            {
                byte[] data = zin.readAllBytes();
                if (entry.getName().equals("xl/worksheets/sheet1.xml"))
                {
                    String xml = new String(data, java.nio.charset.StandardCharsets.UTF_8);
                    data = aPatch.apply(xml).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                }
                zout.putNextEntry(new java.util.zip.ZipEntry(entry.getName()));
                zout.write(data);
                zout.closeEntry();
            }
        }
        return out.toByteArray();
    }


    /**
     * DEFECT FIX (H4): a numeric cell whose cached value the streaming reader cannot parse as a
     * number (here, a raw-XML-corrupted {@code <v>N/A</v>}) used to throw from
     * {@code Cell.getCellType()} / {@code DateUtil.isCellDateFormatted()} — calls made BEFORE
     * {@code getCellValue}'s own {@code try/catch}, which exists precisely to degrade one bad cell
     * to {@code null}/missing rather than aborting the whole sheet. The bad cell now correctly
     * comes back as a missing value and every other cell in the sheet, including its own row's
     * sibling cell, is read normally.
     */
    @Test
    void testOneUnparseableNumericCellDegradesToMissingRatherThanAbortingTheSheet() throws Exception
    {
        URI goodUri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("A");
            h.createCell(1).setCellValue("B");
            Row r1 = s.createRow(1);
            r1.createCell(0).setCellValue(1.0);
            r1.createCell(1).setCellValue(2.0);
            Row r2 = s.createRow(2);
            r2.createCell(0).setCellValue(3.0);
            r2.createCell(1).setCellValue(4.0);
        });
        byte[] corrupted = patchSheet1Xml(Files.readAllBytes(new File(goodUri).toPath()),
                "<v>4.0</v>", "<v>N/A</v>");
        File corruptFile = File.createTempFile("xlsxcorrupt", ".xlsx");
        corruptFile.deleteOnExit();
        Files.write(corruptFile.toPath(), corrupted);

        IDataTable table;
        try (LoggerCapture log = LoggerCapture.attach(ExcelTableProvider.class.getName()))
        {
            table = provider.provide(corruptFile.toURI(), ExcelProviderSupplier.FI_XLSX);
            // Two DISTINCT diagnostics fire for this one cell and both must be asserted
            // separately: the type/date probe in ExcelRow's constructor (the H4 guard) and
            // getCellValue's own degrade-to-null catch. Asserting only "some record was logged"
            // leaves either of them removable without a test noticing.
            assertTrue(log.containsMessageContaining("Cell type/date probe failed"),
                    "the H4 probe guard must log the column it could not type: " + log.messages());
            assertTrue(log.containsMessageContaining("For input string"),
                    "getCellValue must log why it degraded the cell to missing: " + log.messages());
        }

        assertEquals(2, table.getColumnCount());
        assertEquals(2, table.getRowCount(), "the corrupted cell's row must still be read");
        assertEquals(2.0, (double) table.getValue(0, 1), 0.0001);
        assertInstanceOf(MissingValue.class, table.getValue(1, 1),
                "the unparseable cell must degrade to missing, not abort the whole sheet");
        assertEquals(3.0, (double) table.getValue(1, 0),
                "the corrupted cell's OWN row-sibling must still read correctly");
    }


    private static void evaluateAllFormulas(Workbook wb)
    {
        FormulaEvaluator ev = wb.getCreationHelper().createFormulaEvaluator();
        ev.evaluateAll();
    }


    /**
     * A workbook whose header row carries two formula cells with an <b>empty</b> cached value
     * element — {@code <f>…</f><v></v>} — which is exactly what openpyxl, and therefore
     * {@code pandas.DataFrame.to_excel}, writes for every formula: neither evaluates formulas, so
     * neither ever stores a cached result. Verified against an openpyxl-produced file (external
     * oracle, not our writer): {@code pandas.read_excel} and {@code openpyxl} both read such a
     * workbook without complaint, pandas naming that very column {@code Unnamed: 1} and keeping its
     * data.
     * <p>
     * POI cannot author the shape directly — an unevaluated {@code XSSFCell} formula is written
     * with no {@code <v>} element at all, which the streaming reader reports as a BLANK cached
     * result (that cell then terminates the header range instead) — so POI builds the workbook and
     * one literal XML patch adds the empty {@code <v></v>} openpyxl would have written.
     */
    private URI writeFormulaHeaderWithEmptyCachedValue() throws IOException
    {
        URI plain = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("NAME");
            h.createCell(1).setCellFormula("CONCATENATE(\"SC\",\"ORE\")");
            h.createCell(2).setCellFormula("CONCATENATE(\"AG\",\"E\")");
            h.createCell(3).setCellValue("SITE");
            Row r1 = s.createRow(1);
            r1.createCell(0).setCellValue("Alice");
            r1.createCell(1).setCellValue(1.0);
            r1.createCell(2).setCellValue(41.0);
            r1.createCell(3).setCellValue("S01");
            Row r2 = s.createRow(2);
            r2.createCell(0).setCellValue("Bob");
            r2.createCell(1).setCellValue(2.0);
            r2.createCell(2).setCellValue(42.0);
            r2.createCell(3).setCellValue("S02");
            // deliberately NOT evaluated — see the javadoc above.
        });
        byte[] bytes = Files.readAllBytes(new File(plain).toPath());
        bytes = patchSheet1Xml(bytes, "<f>CONCATENATE(\"SC\",\"ORE\")</f>",
                "<f>CONCATENATE(\"SC\",\"ORE\")</f><v></v>");
        bytes = patchSheet1Xml(bytes, "<f>CONCATENATE(\"AG\",\"E\")</f>",
                "<f>CONCATENATE(\"AG\",\"E\")</f><v></v>");
        File patched = File.createTempFile("xlsxemptycached", ".xlsx");
        patched.deleteOnExit();
        Files.write(patched.toPath(), bytes);
        return patched.toURI();
    }


    /**
     * Counts the blank-header fallback records, asserting the LEVEL as well as the text: the
     * user-visible half of that contract is that it is a WARNING they see rather than a DEBUG line
     * they do not, and a text-only filter passes unchanged if production downgrades the level.
     */
    private static long blankHeaderWarnings(LoggerCapture aLog)
    {
        return aLog.records().stream().filter(r -> r.getLevel() == java.util.logging.Level.WARNING)
                .filter(r -> r.getMessage().contains("blank name")).count();
    }


    /**
     * The blank-header-name fallback is reachable on a valid workbook, and naming the column
     * positionally is the right answer: the column HAS data, so terminating the header range there
     * would silently drop it (pandas answers the same file the same way, with
     * {@code Unnamed: 1}/{@code Unnamed: 2}).
     * <p>
     * Pins the 1-based fallback numbering ({@code V2} for the second column), the Excel column
     * LETTER in the diagnostic (so the message points at the cell the user must look at), that the
     * header range continues past such a column, and that the data is preserved.
     */
    @Test
    void testHeaderFormulaWithEmptyCachedValueIsNamedPositionallyAndKeepsItsData() throws Exception
    {
        URI uri = writeFormulaHeaderWithEmptyCachedValue();

        IDataTable table;
        try (LoggerCapture log = LoggerCapture.attach(ExcelTableProvider.class.getName()))
        {
            table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);
            assertEquals(2, blankHeaderWarnings(log),
                    "exactly one WARNING per header cell whose name could not be read");
            assertTrue(log.containsMessageContaining("Excel column B"),
                    "the diagnostic must name the Excel column letter: " + log.messages());
            assertTrue(log.containsMessageContaining("'V2'"), () -> "" + log.messages());
            assertTrue(log.containsMessageContaining("Excel column C"), () -> "" + log.messages());
            assertTrue(log.containsMessageContaining("'V3'"), () -> "" + log.messages());
            assertTrue(log.containsMessageContaining("sheet 'DATA'"),
                    "the diagnostic must name the sheet: the URI carries no sheet fragment on this"
                            + " path, so nothing else identifies it in a multi-sheet workbook");
        }

        assertEquals(4, table.getColumnCount(),
                "a header cell with no readable name must not truncate the header range");
        assertEquals("NAME", table.getMetaData().getColumn(0).getName());
        assertEquals("V2", table.getMetaData().getColumn(1).getName());
        assertEquals("V3", table.getMetaData().getColumn(2).getName());
        assertEquals("SITE", table.getMetaData().getColumn(3).getName());
        assertEquals(1.0, (double) table.getValue(0, 1), 0.0001,
                "the unnamed column's data must survive");
        assertEquals(42.0, (double) table.getValue(1, 2), 0.0001);
        assertEquals("S02", table.getValue(1, 3),
                "every column after the unnamed one must still be read");
    }


    /**
     * Same fallback through {@code provideMetaData()} / {@code buildMeta()} — the metadata-only
     * path must name the columns exactly as the full read does, or the library tree and the opened
     * table disagree about what the columns are called.
     */
    @Test
    void testHeaderFormulaWithEmptyCachedValueFallsBackIdenticallyInProvideMetaData()
        throws Exception
    {
        URI uri = writeFormulaHeaderWithEmptyCachedValue();

        DataTableMeta meta;
        try (LoggerCapture log = LoggerCapture.attach(ExcelTableProvider.class.getName()))
        {
            meta = provider.provideMetaData(uri, ExcelProviderSupplier.FI_XLSX);
            assertEquals(2, blankHeaderWarnings(log));
        }

        assertEquals(4, meta.getColumnCount());
        assertEquals("NAME", meta.getColumn(0).getName());
        assertEquals("V2", meta.getColumn(1).getName());
        assertEquals("V3", meta.getColumn(2).getName());
        assertEquals("SITE", meta.getColumn(3).getName());
    }


    /**
     * And through {@code inferColumns()}, the library-tree path — the third copy of the same
     * decision, which must agree with the other two.
     */
    @Test
    void testInferColumnsUsesTheSamePositionalFallbackName() throws Exception
    {
        URI uri = writeFormulaHeaderWithEmptyCachedValue();

        try (LoggerCapture log = LoggerCapture.attach(ExcelTableProvider.class.getName()))
        {
            DataTableColumnMeta[] cols = ExcelTableProvider.inferColumns(openSheet(uri), uri, 50);
            assertEquals(4, cols.length);
            assertEquals("NAME", cols[0].getName());
            assertEquals("V2", cols[1].getName());
            assertEquals("V3", cols[2].getName());
            assertEquals("SITE", cols[3].getName());
            assertEquals(2, blankHeaderWarnings(log));
        }
    }


    /**
     * Opens the first sheet of the given workbook through the real streaming reader. The workbook
     * handle is deliberately left open for the lifetime of the test — a streaming sheet cannot be
     * read after its workbook is closed, and these are temp files in a short-lived test JVM.
     */
    @SuppressWarnings("resource")
    private static Sheet openSheet(URI aUri) throws IOException
    {
        InputStream in = aUri.toURL().openStream();
        Workbook wb = StreamingReader.builder().rowCacheSize(100).bufferSize(4096).open(in);
        return wb.getSheetAt(0);
    }


    /**
     * A blank/title spacer row above the real header — common in exported clinical-trial reports —
     * must fail with ONE clear error naming the sheet, through every entry point.
     * <p>
     * ⚠ The two sheet-level assertions are the load-bearing ones: {@code provide(URI)}'s outer
     * catch wraps <em>any</em> exception as {@code IOException}, so a test that only went through
     * the URI entry point would have passed while {@code provide(Sheet, URI)} was still failing
     * with the internal {@code IllegalArgumentException("At least 1 column must be specified!")}
     * from the downstream parser construction — which is exactly what it did until this test was
     * written, even though the guard had been added to {@code buildMeta}.
     */
    @Test
    void testSpacerHeaderRowFailsWithOneClearErrorThroughEveryEntryPoint() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            Row spacer = s.createRow(0);
            spacer.createCell(0).setCellValue("   "); // title/spacer row: whitespace only
            Row h = s.createRow(1);
            h.createCell(0).setCellValue("NAME");
            h.createCell(1).setCellValue("SCORE");
            Row r = s.createRow(2);
            r.createCell(0).setCellValue("Alice");
            r.createCell(1).setCellValue(1.0);
        });
        String expected = "Sheet 'DATA' header row has no columns!";

        assertEquals(expected, assertThrows(IOException.class,
                () -> provider.provide(uri, ExcelProviderSupplier.FI_XLSX)).getMessage());
        assertEquals(expected,
                assertThrows(IOException.class,
                        () -> provider.provideMetaData(uri, ExcelProviderSupplier.FI_XLSX))
                                .getMessage());
        assertEquals(expected,
                assertThrows(IOException.class, () -> provider.provide(openSheet(uri), uri))
                        .getMessage(),
                "provide(Sheet, URI) must raise the same clear error, not the parser's"
                        + " \"At least 1 column must be specified!\"");
        assertEquals(expected,
                assertThrows(IOException.class, () -> provider.buildMeta(openSheet(uri), uri))
                        .getMessage());
    }


    /**
     * The same clear error for a first row that is a <em>styled, cell-less</em> row element —
     * {@code <row r="1" ht="30" customHeight="1"/>}, which is valid per ECMA-376 and exactly what
     * openpyxl emits for {@code ws.row_dimensions[1].height = 30} with nothing typed into the row
     * (Excel itself writes it for a spacer row whose height was dragged). Distinct from the
     * whitespace-cell case above: {@code Row.getFirstCellNum()} is {@code -1} here, so it exercises
     * {@code determineColumns}' no-cell-range guard rather than its header-cell filter.
     */
    @Test
    void testStyledCellLessFirstRowFailsWithTheSameClearError() throws Exception
    {
        URI plain = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            s.createRow(0).createCell(0).setCellValue(987654.0); // replaced wholesale below
            Row h = s.createRow(1);
            h.createCell(0).setCellValue("NAME");
            h.createCell(1).setCellValue("SCORE");
            Row r = s.createRow(2);
            r.createCell(0).setCellValue("Alice");
            r.createCell(1).setCellValue(1.0);
        });
        byte[] bytes = rewriteSheet1Xml(Files.readAllBytes(new File(plain).toPath()), xml ->
        {
            String patched = xml.replaceFirst("(?s)<row r=\"1\".*?</row>",
                    "<row r=\"1\" ht=\"30\" customHeight=\"1\"/>");
            assertNotEquals(xml, patched, "fixture setup: row 1 element not found");
            return patched;
        });
        File file = File.createTempFile("xlsxemptyrow", ".xlsx");
        file.deleteOnExit();
        Files.write(file.toPath(), bytes);
        URI uri = file.toURI();

        assertEquals(-1, (int) openSheet(uri).iterator().next().getFirstCellNum(),
                "fixture precondition: the first row must have no cell range at all");
        assertEquals("Sheet 'DATA' header row has no columns!", assertThrows(IOException.class,
                () -> provider.provide(uri, ExcelProviderSupplier.FI_XLSX)).getMessage());
        assertEquals("Sheet 'DATA' header row has no columns!",
                assertThrows(IOException.class,
                        () -> provider.provideMetaData(uri, ExcelProviderSupplier.FI_XLSX))
                                .getMessage());
    }

    // ==================== error values POI's FormulaError enum does not know ==================


    /**
     * Builds a workbook in which cell B2 — numeric in the source — is replaced at the raw XML level
     * by the given cell XML. {@code B2} is the second column's first data row, and the column's
     * other data cell stays numeric, so the surrounding sheet is untouched.
     */
    private URI writeTempXlsxWithPatchedB2(String aB2CellXml) throws IOException
    {
        URI plain = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("A");
            h.createCell(1).setCellValue("B");
            Row r1 = s.createRow(1);
            r1.createCell(0).setCellValue("x");
            r1.createCell(1).setCellValue(111.0);
            Row r2 = s.createRow(2);
            r2.createCell(0).setCellValue("y");
            r2.createCell(1).setCellValue(222.0);
        });
        byte[] bytes = patchSheet1Xml(Files.readAllBytes(new File(plain).toPath()),
                "<c r=\"B2\" t=\"n\" s=\"0\"><v>111.0</v></c>", aB2CellXml);
        File patched = File.createTempFile("xlsxunknownerr", ".xlsx");
        patched.deleteOnExit();
        Files.write(patched.toPath(), bytes);
        return patched.toURI();
    }


    /**
     * An Excel error value that POI's {@code FormulaError} enum does not know must come through as
     * the generic {@code "#ERROR"} token rather than as raw, unvalidated text — and the failure to
     * resolve it must be logged.
     * <p>
     * {@code #SPILL!} is a real, current Excel error value (a dynamic-array formula whose result
     * cannot spill; so are {@code #CALC!}, {@code #FIELD!} and {@code #GETTING_DATA}), written into
     * the file by Excel itself as {@code t="e"} with that text — ECMA-376 carries the error as
     * text, while POI's enum only covers the seven classic codes. So this is NOT an unreachable
     * defensive branch: {@code FormulaError.forString} throws on every one of those values.
     * <p>
     * This is the FORMULA-cached form, which is how an error arises in practice (the cell holds a
     * formula; its cached result is the error).
     */
    @Test
    void testFormulaCachedErrorUnknownToPoiFallsBackToGenericErrorToken() throws Exception
    {
        URI uri = writeTempXlsxWithPatchedB2(
                "<c r=\"B2\" t=\"e\" s=\"0\"><f>SORT(A1:A9)</f><v>#SPILL!</v></c>");

        IDataTable table;
        try (LoggerCapture log = LoggerCapture.attach(ExcelTableProvider.class.getName()))
        {
            table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);
            assertTrue(log.containsMessageContaining("Failed to resolve formula-cached error code"),
                    "an unresolvable error label must be logged: " + log.messages());
        }

        assertEquals("#ERROR", table.getValue(0, 1),
                "an error value POI cannot resolve must surface as the generic token, never as raw"
                        + " unvalidated cell text");
        assertEquals(DataValueType.STRING, table.getMetaData().getColumn(1).getType());
    }


    /**
     * The same Excel error value as a DIRECTLY error-typed cell (no formula): POI's
     * {@code getErrorCellValue()} rejects the byte code with {@code "Unexpected error code"}, which
     * {@code errorToken}'s catch turns into the generic {@code "#ERROR"} token plus a diagnostic.
     * Distinct code path from the formula-cached form above.
     */
    @Test
    void testDirectErrorCellUnknownToPoiFallsBackToGenericErrorToken() throws Exception
    {
        URI uri = writeTempXlsxWithPatchedB2("<c r=\"B2\" t=\"e\" s=\"0\"><v>#SPILL!</v></c>");

        IDataTable table;
        try (LoggerCapture log = LoggerCapture.attach(ExcelTableProvider.class.getName()))
        {
            table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);
            assertTrue(log.containsMessageContaining("Failed to resolve POI error code"),
                    "an unresolvable error byte must be logged: " + log.messages());
        }

        assertEquals("#ERROR", table.getValue(0, 1));
    }


    /**
     * DEFECT FIX (H7): a {@code FORMULA} cell whose cached result is numeric must go through the
     * same date channel as a literal numeric cell. {@code =BASELINE+7} is the ordinary way a
     * computed visit date exists in an exported workbook, and the date probe used to test the
     * cell's OWN type — which for a formula cell is always {@code FORMULA}, never {@code NUMERIC} —
     * so such a cell was never recognised as a date at all. Two silent misreads of a clinical value
     * followed, and this one fixture pins both:
     * <ul>
     * <li><b>MIXEDDT</b> (literal date, then a computed one): the column was flagged
     * {@code E8601DT.} by its literal cells, so the converted value was used for every row — and
     * the computed row's was {@code NaN}, reporting a present, correct date as MISSING.</li>
     * <li><b>NEXTDT</b> (every cell computed): no cell was ever flagged as a date, so the column
     * got no {@code E8601DT.} and its raw Excel serial (~45007) was handed to the user as a plain
     * number, ~63 years off when read as a date.</li>
     * </ul>
     * Expected values are derived outside this stack: Excel serial n is {@code 1899-12-30 + n days}
     * in the 1900 date system, and the SAS datetime is that date's seconds since 1960-01-01
     * (cross-checked with Python {@code datetime}: 45000 → 2023-03-15 → 1994457600; 45001 →
     * 1994544000; 45007 → 1995062400; 45008 → 1995148800).
     */
    @Test
    void testFormulaComputedDateCellsAreDatesLikeLiteralOnes() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            DataFormat df = wb.createDataFormat();
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(df.getFormat("yyyy-mm-dd"));

            Row h = s.createRow(0);
            h.createCell(0).setCellValue("VISITDT");
            h.createCell(1).setCellValue("NEXTDT");
            h.createCell(2).setCellValue("MIXEDDT");

            Row r1 = s.createRow(1);
            dateCell(r1, 0, dateStyle).setCellValue(45000.0);
            dateCell(r1, 1, dateStyle).setCellFormula("A2+7"); // computed: 45007
            dateCell(r1, 2, dateStyle).setCellValue(45000.0);

            Row r2 = s.createRow(2);
            dateCell(r2, 0, dateStyle).setCellValue(45001.0);
            dateCell(r2, 1, dateStyle).setCellFormula("A3+7"); // computed: 45008
            dateCell(r2, 2, dateStyle).setCellFormula("C2+1"); // computed: 45001

            evaluateAllFormulas(wb);
        });

        IDataTable table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);

        for (int col = 0; col < 3; col++)
        {
            assertEquals("E8601DT.", table.getMetaData().getColumn(col).getDisplayFormat(),
                    table.getMetaData().getColumn(col).getName()
                            + ": a date-styled column must be a date column whether its cells are"
                            + " literal or computed");
        }
        assertEquals(1_994_457_600.0, (double) table.getValue(0, 0), 0.5);
        assertEquals(1_994_544_000.0, (double) table.getValue(1, 0), 0.5);
        assertEquals(1_995_062_400.0, (double) table.getValue(0, 1), 0.5,
                "a computed date must be converted, not left as a raw Excel serial");
        assertEquals(1_995_148_800.0, (double) table.getValue(1, 1), 0.5);
        assertEquals(1_994_457_600.0, (double) table.getValue(0, 2), 0.5);
        assertEquals(1_994_544_000.0, (double) table.getValue(1, 2), 0.5,
                "the computed row of a mixed date column must be its date, not MISSING");
        for (int row = 0; row < 2; row++)
        {
            for (int col = 0; col < 3; col++)
            {
                assertFalse(table.getValue(row, col) instanceof MissingValue,
                        "no cell of this sheet is missing: [" + row + "," + col + "]");
            }
        }
    }


    /** Creates a date-styled cell. */
    private static org.apache.poi.ss.usermodel.Cell dateCell(Row aRow, int aIdx, CellStyle aStyle)
    {
        org.apache.poi.ss.usermodel.Cell c = aRow.createCell(aIdx);
        c.setCellStyle(aStyle);
        return c;
    }


    /**
     * A HEADER cell whose stored content the streaming reader cannot read at all — {@code t="n"}
     * with a non-numeric {@code <v>}, as a hand-edited or third-party export leaves behind — must
     * come out of the table entry points as one {@code IOException} naming the sheet, not as the
     * bare {@code NumberFormatException} that {@code Cell.getCellType()} throws from inside
     * {@code isHeaderCell}. Same class of leak as the zero-column one above: a method declaring
     * {@code throws IOException} handing the caller an unrelated runtime exception.
     * <p>
     * ⚠ The LIBRARY path deliberately keeps seeing a {@code RuntimeException} for this shape, so
     * one unreadable sheet does not take a whole multi-sheet library open down — pinned by
     * {@code ExcelLibraryTest.testProvideLibraryContinuesWhenOneSheetsColumnInferenceFails}.
     */
    @Test
    void testUnreadableHeaderCellFailsWithOneClearIoExceptionNamingTheSheet() throws Exception
    {
        URI plain = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("NAME");
            h.createCell(1).setCellValue(987654.0); // corrupted below
            Row r = s.createRow(1);
            r.createCell(0).setCellValue("Alice");
            r.createCell(1).setCellValue(1.0);
        });
        byte[] bytes = patchSheet1Xml(Files.readAllBytes(new File(plain).toPath()),
                "<v>987654.0</v>", "<v>N/A</v>");
        File file = File.createTempFile("xlsxbadheader", ".xlsx");
        file.deleteOnExit();
        Files.write(file.toPath(), bytes);
        URI uri = file.toURI();

        for (Executable call : List.<Executable> of(() -> provider.provide(openSheet(uri), uri),
                () -> provider.buildMeta(openSheet(uri), uri),
                () -> provider.provide(uri, ExcelProviderSupplier.FI_XLSX),
                () -> provider.provideMetaData(uri, ExcelProviderSupplier.FI_XLSX)))
        {
            IOException ex = assertThrows(IOException.class, call);
            assertTrue(ex.getMessage().contains("Sheet 'DATA' header row could not be read"),
                    "the error must name the sheet and the header row: " + ex.getMessage());
            assertNotNull(ex.getCause(), "the underlying reader failure must be preserved");
        }
    }

}
