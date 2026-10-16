package net.cumba.datatable.provider.xlsx;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.provider.DataTableMetaSupport;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.provider.xlsx.testsupport.LoggerCapture;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.MissingValue;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
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
        assertSame(MissingValue.MIS, table.getValue(1, 0), "an absent cell is blank: MIS");
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
     * F-B14: a numeric header cell is a valid header, named by its number as text - the shortest
     * plain decimal, {@code 2026} reads {@code "2026"} (ECT-Q2 ⓐ, owner 2026-10-16; it read
     * {@code "2026.0"} before). Previously the old STRING-only filter would have dropped these
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
        assertEquals("2026", table.getMetaData().getColumn(1).getName());
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
            // A LocalDate is written as a zone-free serial, the way Excel stores a date. A
            // java.util.Date would be converted in the JVM's DEFAULT zone - 01:00 in Berlin - and
            // a reader with the mirror-image shift would read it back "correctly".
            cell.setCellValue(java.time.LocalDate.of(2024, 1, 15));
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
        assertEquals("5", table.getValue(0, 0),
                "a number in a Char column reads as its plain text");
        assertEquals("0007", table.getValue(1, 0));
    }


    /**
     * A number in a Char column reads as the shortest plain decimal (ECT-Q2 ⓐ, owner 2026-10-16:
     * the engine's {@code str(3.0)} reads {@code "3"}, and so does the CSV text of the same
     * export): no {@code ".0"}, no exponent, {@code -0.0} as {@code "0"}. A non-finite stored value
     * ({@code 1E+400}) keeps {@code "Infinity"} and the sheet still loads -
     * {@code BigDecimal.valueOf} would throw for it. A number after an empty sample, in the Char
     * column that sample gives (ECT-Q1 ⓐ), reads the same way.
     */
    @Test
    void testNumberInACharColumnReadsAsItsShortestPlainText() throws Exception
    {
        provider.setGuessingRowCount(2);
        URI plain = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("VAL");
            h.createCell(1).setCellValue("LATE");
            double[] numbers =
            {
                    5.0, 0.1, 1.0E7, -0.0, 1.0E-5, 123.456, 777.0
            };
            s.createRow(1).createCell(0).setCellValue("ND");
            for (int i = 0; i < numbers.length; i++)
            {
                s.createRow(i + 2).createCell(0).setCellValue(numbers[i]);
            }
            s.getRow(4).createCell(1).setCellValue(5.0);
            s.getRow(5).createCell(1).setCellValue(2.5E10);
        });
        byte[] bytes = patchSheet1Xml(Files.readAllBytes(new File(plain).toPath()), "<v>777.0</v>",
                "<v>1E+400</v>");
        File patched = File.createTempFile("xlsxchartext", ".xlsx");
        patched.deleteOnExit();
        Files.write(patched.toPath(), bytes);

        IDataTable table = provider.provide(patched.toURI(), ExcelProviderSupplier.FI_XLSX);

        assertEquals(DataValueType.STRING, table.getMetaData().getColumn(0).getType());
        assertEquals(DataValueType.STRING, table.getMetaData().getColumn(1).getType());
        assertAll(() -> assertEquals("ND", table.getValue(0, 0)),
                () -> assertEquals("5", table.getValue(1, 0)),
                () -> assertEquals("0.1", table.getValue(2, 0)),
                () -> assertEquals("10000000", table.getValue(3, 0)),
                () -> assertEquals("0", table.getValue(4, 0), "-0.0"),
                () -> assertEquals("0.00001", table.getValue(5, 0)),
                () -> assertEquals("123.456", table.getValue(6, 0)),
                () -> assertEquals("Infinity", table.getValue(7, 0), "stored 1E+400"),
                () -> assertEquals("", table.getValue(0, 1)),
                () -> assertEquals("5", table.getValue(3, 1), "a number after an empty sample"),
                () -> assertEquals("25000000000", table.getValue(4, 1)));
    }


    /**
     * A column with no value in any sampled row is Char, as CSV types it (owner, 2026-10-16:
     * <i>"agree to Char as default type."</i>; ECT-C1, ECT-C5), through every entry point: a column
     * whose data cells are all absent, one whose cells are all {@code BLANK}, and every column of a
     * header-only sheet. Its cells read as XLSX Char cells read: {@code ""}. The evidence sibling
     * beside them - blank cells and ONE real number - stays {@code DOUBLE}, so the Char default
     * cannot over-reach into a column the sample did see a number in.
     */
    @Test
    void testColumnWithNoValueInTheSampleIsCharInEveryEntryPoint() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("ID");
            h.createCell(1).setCellValue("ABSENT");
            h.createCell(2).setCellValue("BLANK");
            h.createCell(3).setCellValue("ONENUM");
            for (int r = 1; r <= 3; r++)
            {
                Row row = s.createRow(r);
                row.createCell(0).setCellValue("r" + r);
                row.createCell(2); // a BLANK cell
                if (r == 2)
                {
                    row.createCell(3).setCellValue(4.0);
                }
                else
                {
                    row.createCell(3);
                }
            }
        });
        URI headerOnly = writeTempXlsx(wb ->
        {
            Row h = wb.createSheet("DATA").createRow(0);
            h.createCell(0).setCellValue("A");
            h.createCell(1).setCellValue("B");
        });

        IDataTable table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);
        DataTableMeta meta = provider.provideMetaData(uri, ExcelProviderSupplier.FI_XLSX);
        DataTableColumnMeta[] inferred = ExcelTableProvider.inferColumns(openSheet(uri), uri, 50);
        for (DataTableColumnMeta[] cols : List.of(table.getMetaData().getColumns(),
                meta.getColumns(), inferred))
        {
            assertEquals(DataValueType.STRING, cols[1].getType(), "ABSENT");
            assertEquals(DataValueType.STRING, cols[2].getType(), "BLANK");
            assertEquals(DataValueType.DOUBLE, cols[3].getType(), "ONENUM: one number is evidence");
            assertNull(cols[1].getDisplayFormat());
            assertNull(cols[2].getDisplayFormat());
        }
        for (int r = 0; r < 3; r++)
        {
            assertEquals("", table.getValue(r, 1), "ABSENT row " + r);
            assertEquals("", table.getValue(r, 2), "BLANK row " + r);
        }
        assertSame(MissingValue.MIS, table.getValue(0, 3));
        assertEquals(4.0, (double) table.getValue(1, 3), 0.0);

        IDataTable empty = provider.provide(headerOnly, ExcelProviderSupplier.FI_XLSX);
        DataTableMeta emptyMeta = provider.provideMetaData(headerOnly,
                ExcelProviderSupplier.FI_XLSX);
        DataTableColumnMeta[] emptyInferred = ExcelTableProvider.inferColumns(openSheet(headerOnly),
                headerOnly, 50);
        assertEquals(0, empty.getRowCount());
        for (DataTableColumnMeta[] cols : List.of(empty.getMetaData().getColumns(),
                emptyMeta.getColumns(), emptyInferred))
        {
            assertEquals(2, cols.length);
            assertEquals(DataValueType.STRING, cols[0].getType(), "header-only A");
            assertEquals(DataValueType.STRING, cols[1].getType(), "header-only B");
        }
    }


    /**
     * The type comes from the sample only (ECT-Q1 ⓐ, owner 2026-10-16): a column blank for the
     * whole sample is Char, so text after the sample is KEPT - it used to read {@code MIS_ERROR} in
     * the {@code DOUBLE} column the empty sample produced.
     */
    @Test
    void testColumnBlankForTheSampleKeepsTextAfterIt() throws Exception
    {
        provider.setGuessingRowCount(2);
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("ID");
            h.createCell(1).setCellValue("LATE");
            for (int r = 1; r <= 4; r++)
            {
                s.createRow(r).createCell(0).setCellValue("r" + r);
            }
            s.getRow(4).createCell(1).setCellValue("late text");
        });

        IDataTable table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);

        assertEquals(DataValueType.STRING, table.getMetaData().getColumn(1).getType());
        assertEquals("", table.getValue(0, 1));
        assertEquals("late text", table.getValue(3, 1));
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
        assertSame(MissingValue.MIS, table.getValue(1, 0),
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
            FormulaEvaluator ev = wb.getCreationHelper().createFormulaEvaluator();
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
        assertSame(MissingValue.MIS_ERROR, table.getValue(1, 1),
                "the unparseable cell must degrade to MIS_ERROR (BNM-F7), not abort the whole sheet");
        assertEquals(3.0, (double) table.getValue(1, 0),
                "the corrupted cell's OWN row-sibling must still read correctly");
    }

    // ==================== metadata: file_format / dataset_size (provide) ====================


    /**
     * {@code provide()} must record the physical source format as table metadata — this backs the
     * rule-engine {@code extract_metadata("file_format")} accessor. Was previously asserted
     * nowhere, so a pitest mutant removing the {@code setFileFormat} call survived.
     */
    @Test
    void testProvideRecordsFileFormatMetadata() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            s.createRow(0).createCell(0).setCellValue("A");
            s.createRow(1).createCell(0).setCellValue("v");
        });

        IDataTable table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);

        assertEquals("XLSX",
                table.getMetaData().getMetaData(DataTableMetaSupport.META_KEY_FILE_FORMAT));
    }


    /**
     * {@code provide()} must record the size of the file it parsed, for a {@code file:} URI.
     */
    @Test
    void testProvideRecordsDatasetSizeMetadata() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            s.createRow(0).createCell(0).setCellValue("A");
            s.createRow(1).createCell(0).setCellValue("v");
        });
        long actualSize = Files.size(new File(uri).toPath());

        IDataTable table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);

        Object size = table.getMetaData().getMetaData(DataTableMetaSupport.META_KEY_DATASET_SIZE);
        assertEquals(actualSize, ((Number) size).longValue());
    }

    // ==================== guessingRowCount actually caps the sample ====================


    /**
     * With a small {@code guessingRowCount}, type inference must be based ONLY on the sampled rows
     * — rows beyond the cap must not influence the inferred type, even though they are still read
     * as data. Here the sampled rows are all-numeric (⇒ column inferred DOUBLE) while a later,
     * unsampled row holds genuinely non-numeric text; that later row must still be *read* as data
     * (falling back to NaN via {@code getDoubleValue}'s parse-failure path) rather than the column
     * falling back to STRING.
     */
    @Test
    void testGuessingRowCountCapsTypeInferenceSample() throws Exception
    {
        provider.setGuessingRowCount(2);
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            s.createRow(0).createCell(0).setCellValue("VAL");
            s.createRow(1).createCell(0).setCellValue(1.0);
            s.createRow(2).createCell(0).setCellValue(2.0);
            // Beyond the 2-row sample: a non-numeric text cell.
            s.createRow(3).createCell(0).setCellValue("not-a-number");
        });

        IDataTable table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);

        assertEquals(DataValueType.DOUBLE, table.getMetaData().getColumn(0).getType(),
                "type inference must be based on the capped sample, not the whole column");
        assertEquals(3, table.getRowCount());
        assertInstanceOf(MissingValue.class, table.getValue(2, 0),
                "a non-numeric value outside the sample must fail to parse as a missing double");
    }


    /**
     * A {@code guessingRowCount} exactly equal to the number of available data rows must still
     * sample every row (the {@code >=} boundary in the collection loop) — pins the
     * {@code changed conditional boundary} / {@code negated conditional} mutants on that check.
     */
    @Test
    void testGuessingRowCountExactBoundary() throws Exception
    {
        provider.setGuessingRowCount(2);
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            s.createRow(0).createCell(0).setCellValue("VAL");
            s.createRow(1).createCell(0).setCellValue(1.0);
            s.createRow(2).createCell(0).setCellValue(2.0);
        });

        IDataTable table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);

        assertEquals(DataValueType.DOUBLE, table.getMetaData().getColumn(0).getType());
        assertEquals(2, table.getRowCount());
    }

    // ==================== isDate() false path / null displayFormat ====================


    /**
     * A non-date-formatted numeric column must NOT carry an {@code E8601DT.} displayFormat. Pins
     * the {@code isDate} "replaced boolean return with true" mutant — without this assertion a test
     * exercising only date columns cannot tell {@code isDate()} apart from a stub that always
     * returns {@code true}.
     */
    @Test
    void testNonDateColumnHasNullDisplayFormat() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            s.createRow(0).createCell(0).setCellValue("N");
            s.createRow(1).createCell(0).setCellValue(42.5);
        });

        IDataTable table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);

        assertNull(table.getMetaData().getColumn(0).getDisplayFormat(),
                "a plain numeric column must not be tagged with the date displayFormat");
    }

    // ==================== outer catch(Exception): non-OOXML bytes ====================


    /**
     * A file that is not a valid OOXML/zip container at all (a corrupted download, a
     * mislabelled/renamed file) makes {@code StreamingReader.open} throw a runtime exception (not
     * an {@link IOException}) — POI's {@code NotOfficeXmlFileException}. {@code provide()}'s outer
     * {@code catch (Exception ex)} must log it and wrap it as an {@link IOException} rather than
     * letting the raw runtime exception escape. This REACH scenario — a real corrupted or
     * mislabelled upload — had zero coverage (the log call is a survived mutant).
     */
    @Test
    void testProvideNonOoxmlBytesLogsAndWrapsAsIOException() throws Exception
    {
        File tmpFile = File.createTempFile("notxlsx", ".xlsx");
        tmpFile.deleteOnExit();
        Files.write(tmpFile.toPath(), "this is not a zip or OLE2 file at all"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        URI uri = tmpFile.toURI();

        try (LoggerCapture log = LoggerCapture.attach(ExcelTableProvider.class.getName()))
        {
            IOException ex = assertThrows(IOException.class,
                    () -> provider.provide(uri, ExcelProviderSupplier.FI_XLSX));
            assertNotNull(ex.getCause(),
                    "the original runtime exception must be preserved as cause");
            assertFalse(log.records().isEmpty(),
                    "the runtime exception must be logged before being wrapped");
        }
    }


    /**
     * Same outer-catch behaviour, exercised through {@code provideMetaData()}.
     */
    @Test
    void testProvideMetaDataNonOoxmlBytesLogsAndWrapsAsIOException() throws Exception
    {
        File tmpFile = File.createTempFile("notxlsxmeta", ".xlsx");
        tmpFile.deleteOnExit();
        Files.write(tmpFile.toPath(),
                "also not a real workbook".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        URI uri = tmpFile.toURI();

        try (LoggerCapture log = LoggerCapture.attach(ExcelTableProvider.class.getName()))
        {
            IOException ex = assertThrows(IOException.class,
                    () -> provider.provideMetaData(uri, ExcelProviderSupplier.FI_XLSX));
            assertNotNull(ex.getCause());
            assertFalse(log.records().isEmpty());
        }
    }

    // ==================== isHeaderCell: FORMULA and ERROR/BLANK/gap header cells
    // ====================


    private static void evaluateAllFormulas(Workbook wb)
    {
        FormulaEvaluator ev = wb.getCreationHelper().createFormulaEvaluator();
        ev.evaluateAll();
    }


    /**
     * A formula header cell whose cached result is a non-blank STRING is a valid header — a real
     * spreadsheet author can build column headers from a formula (e.g. concatenating a prefix).
     * Nothing previously exercised the FORMULA branch of {@code isHeaderCell} at all.
     */
    @Test
    void testHeaderFormulaCellWithStringResultIsValidHeader() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("NAME");
            h.createCell(1).setCellFormula("\"SC\"&\"ORE\"");
            Row r1 = s.createRow(1);
            r1.createCell(0).setCellValue("Alice");
            r1.createCell(1).setCellValue(1.0);
            evaluateAllFormulas(wb);
        });

        IDataTable table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);

        assertEquals(2, table.getColumnCount());
        assertEquals("SCORE", table.getMetaData().getColumn(1).getName());
    }


    /**
     * A formula header cell whose cached result is a numeric value is a valid header (mirrors the
     * direct-numeric-header case, but through the FORMULA/cached-type branch).
     */
    @Test
    void testHeaderFormulaCellWithNumericResultIsValidHeader() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("NAME");
            h.createCell(1).setCellFormula("1+1");
            Row r1 = s.createRow(1);
            r1.createCell(0).setCellValue("Alice");
            r1.createCell(1).setCellValue(1.0);
            evaluateAllFormulas(wb);
        });

        IDataTable table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);

        assertEquals(2, table.getColumnCount());
    }


    /**
     * A formula header cell whose cached result is blank text (e.g. {@code =""}) - a real
     * spreadsheet can easily have such a formula left over from a template - keeps its column under
     * the positional name, and the range goes on (owner ruling Q37(ii), 2026-10-12: <i>"I would
     * like to get this column in with a temp name, so nothing is lost, additionally log with
     * warning."</i>). It used to end the range, dropping the column and every one after it.
     */
    @Test
    void testHeaderFormulaCellWithBlankStringResultIsKeptPositionally() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("NAME");
            h.createCell(1).setCellFormula("\"\"");
            h.createCell(2).setCellValue("SCORE");
            Row r1 = s.createRow(1);
            r1.createCell(0).setCellValue("Alice");
            evaluateAllFormulas(wb);
        });

        IDataTable table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);

        assertEquals(3, table.getColumnCount(),
                "a formula header cell whose cached result is blank text keeps its column");
        assertEquals("V2", table.getMetaData().getColumn(1).getName());
        assertEquals("SCORE", table.getMetaData().getColumn(2).getName());
    }


    /**
     * A formula header cell whose cached result is an ERROR (e.g. {@code =1/0}) - a completely
     * realistic case when a header formula references a column that has since been deleted or
     * divides by a now-empty cell - keeps its column under the positional name, never the error
     * token, with one WARNING saying why (Q37(ii)).
     */
    @Test
    void testHeaderFormulaCellWithErrorResultIsKeptPositionally() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("NAME");
            h.createCell(1).setCellFormula("1/0");
            h.createCell(2).setCellValue("SCORE");
            Row r1 = s.createRow(1);
            r1.createCell(0).setCellValue("Alice");
            evaluateAllFormulas(wb);
        });

        IDataTable table;
        try (LoggerCapture log = LoggerCapture.attach(ExcelTableProvider.class.getName()))
        {
            table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);
            assertTrue(log.containsMessageContaining(
                    "Excel column B of sheet 'DATA' has an unreadable header (#DIV/0!); using"
                            + " fallback 'V2'"),
                    () -> String.valueOf(log.messages()));
        }

        assertEquals(3, table.getColumnCount(),
                "a formula header cell whose cached result is an error keeps its column");
        assertEquals("V2", table.getMetaData().getColumn(1).getName());
        assertEquals("SCORE", table.getMetaData().getColumn(2).getName());
    }


    /**
     * A directly ERROR-typed header cell (e.g. {@code #REF!} left behind after deleting a column
     * that a header formula/reference used to point at) keeps its column under the positional name,
     * and the columns after it are read (Q37(ii)). Distinct from the FORMULA-cached-error case
     * above: this is a plain {@code CellType.ERROR} cell, not a formula.
     */
    @Test
    void testHeaderDirectErrorCellIsKeptPositionally() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("NAME");
            h.createCell(1).setCellErrorValue(FormulaError.REF.getCode());
            h.createCell(2).setCellValue("SCORE");
            Row r1 = s.createRow(1);
            r1.createCell(0).setCellValue("Alice");
            r1.createCell(1).setCellValue(7.0);
            r1.createCell(2).setCellValue(8.0);
        });

        IDataTable table;
        try (LoggerCapture log = LoggerCapture.attach(ExcelTableProvider.class.getName()))
        {
            table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);
            assertTrue(log.containsMessageContaining("has an unreadable header (#REF!)"),
                    () -> String.valueOf(log.messages()));
        }

        assertEquals(3, table.getColumnCount());
        assertEquals("NAME", table.getMetaData().getColumn(0).getName());
        assertEquals("V2", table.getMetaData().getColumn(1).getName());
        assertEquals("SCORE", table.getMetaData().getColumn(2).getName());
        assertEquals(7.0, (double) table.getValue(0, 1), 0.0, "nothing is lost");
        assertEquals(8.0, (double) table.getValue(0, 2), 0.0);
    }


    /**
     * A positional name never collides with a real header: a text header is never renamed (a
     * duplicate fails the whole sheet), so a fallback that a header of the sheet already spells -
     * ignoring case, before or after it - takes the next free {@code V<n>_<k>}.
     */
    @Test
    void testAPositionalNameThatCollidesTakesTheNextFreeSuffix() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("v2_1");
            h.createCell(1).setCellErrorValue(FormulaError.REF.getCode());
            h.createCell(2).setCellValue("V2");
            h.createCell(3).setCellFormula("\"\"");
            h.createCell(4).setCellValue("v4");
            Row r1 = s.createRow(1);
            for (int c = 0; c < 5; c++)
            {
                r1.createCell(c).setCellValue(c);
            }
            evaluateAllFormulas(wb);
        });

        IDataTable table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);
        DataTableMeta meta = provider.provideMetaData(uri, ExcelProviderSupplier.FI_XLSX);
        DataTableColumnMeta[] inferred = ExcelTableProvider.inferColumns(openSheet(uri), uri, 50);

        List<String> expected = List.of("v2_1", "V2_2", "V2", "V4_1", "v4");
        for (int c = 0; c < expected.size(); c++)
        {
            assertEquals(expected.get(c), table.getMetaData().getColumn(c).getName());
            assertEquals(expected.get(c), meta.getColumn(c).getName());
            assertEquals(expected.get(c), inferred[c].getName());
            assertEquals((double) c, (double) table.getValue(0, c), 0.0);
        }
    }


    /**
     * A numeric header whose rendered name is already used takes the next free {@code <name>_<k>}
     * and logs why (ECT review ② M1, coordinator ruling (b), 2026-10-16): since a number reads as
     * its plain decimal digits (ECT-Q2 ⓐ), a numeric {@code 1} spells the text header {@code "1"},
     * {@code 0} and {@code -0.0} spell the same {@code "0"}, and {@code 1E7} spells
     * {@code "10000000"}; each of these sheets loaded while a number read {@link Double#toString}
     * ({@code "1.0"}, {@code "-0.0"}, {@code "1.0E7"}), and the plan promises that no sheet that
     * loads today stops loading. A text header keeps its name, wherever it stands - the numeric
     * header yields, even when it comes first. Two text headers that spell the same name still fail
     * the sheet, as they always did.
     */
    @Test
    void testANumericHeaderThatCollidesTakesTheNextFreeSuffix() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue(1.0);
            h.createCell(1).setCellValue("1");
            h.createCell(2).setCellValue(0.0);
            h.createCell(3).setCellValue(-0.0);
            h.createCell(4).setCellValue(1.0E7);
            h.createCell(5).setCellValue("10000000");
            h.createCell(6).setCellValue("1_1");
            h.createCell(7).setCellValue(1.0);
            Row r1 = s.createRow(1);
            for (int c = 0; c < 8; c++)
            {
                r1.createCell(c).setCellValue(c);
            }
        });

        IDataTable table;
        try (LoggerCapture log = LoggerCapture.attach(ExcelTableProvider.class.getName()))
        {
            table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);
            assertTrue(log.containsMessageContaining(
                    "Excel column A of sheet 'DATA' has the numeric header 1, a name already used;"
                            + " using '1_2'"),
                    () -> String.valueOf(log.messages()));
            assertTrue(log.containsMessageContaining("using '0_1'"),
                    () -> String.valueOf(log.messages()));
        }
        DataTableMeta meta = provider.provideMetaData(uri, ExcelProviderSupplier.FI_XLSX);
        DataTableColumnMeta[] inferred = ExcelTableProvider.inferColumns(openSheet(uri), uri, 50);

        List<String> expected = List.of("1_2", "1", "0", "0_1", "10000000_1", "10000000", "1_1",
                "1_3");
        for (int c = 0; c < expected.size(); c++)
        {
            assertEquals(expected.get(c), table.getMetaData().getColumn(c).getName());
            assertEquals(expected.get(c), meta.getColumn(c).getName());
            assertEquals(expected.get(c), inferred[c].getName());
            assertEquals((double) c, (double) table.getValue(0, c), 0.0, "nothing is lost");
        }

        URI texts = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("A");
            h.createCell(1).setCellValue("a");
            s.createRow(1).createCell(0).setCellValue(1.0);
        });
        Exception ex = assertThrows(Exception.class,
                () -> provider.provide(texts, ExcelProviderSupplier.FI_XLSX));
        assertTrue(String.valueOf(ex).contains("already defined")
                || String.valueOf(ex.getCause()).contains("already defined"), ex::toString);
    }


    /**
     * A genuinely {@code CellType.BLANK} header cell (a cell object exists — e.g. it carries
     * formatting — but no value was ever set) terminates the header range, distinct from a
     * whitespace-only STRING cell (already covered) and from a missing cell object entirely
     * (covered below).
     */
    @Test
    void testHeaderBlankTypeCellTerminatesRange() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("NAME");
            h.createCell(1, CellType.BLANK); // present cell, BLANK type, no value ever set
            h.createCell(2).setCellValue("SCORE");
            Row r1 = s.createRow(1);
            r1.createCell(0).setCellValue("Alice");
        });

        IDataTable table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);

        assertEquals(1, table.getColumnCount());
    }


    /**
     * A header row with a genuine gap — no {@link org.apache.poi.ss.usermodel.Cell} object at all
     * at an index within the row's cell range (sparse row), as opposed to a present-but-blank cell
     * — also terminates the header range. {@code Row.getCell(idx)} returns {@code null} for such a
     * gap, which {@code isHeaderCell} must treat the same as a present blank cell.
     */
    @Test
    void testHeaderMissingCellObjectTerminatesRange() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("NAME");
            // Deliberately skip index 1 — no cell object created there at all.
            h.createCell(2).setCellValue("SCORE");
            Row r1 = s.createRow(1);
            r1.createCell(0).setCellValue("Alice");
        });

        IDataTable table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);

        assertEquals(1, table.getColumnCount());
        assertEquals("NAME", table.getMetaData().getColumn(0).getName());
    }

    // ============ the blank-header-name fallback is LIVE code, and the header-row guard ========


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
     * result (a different shape, named positionally as well since Q37(ii)) — so POI builds the
     * workbook and one literal XML patch adds the empty {@code <v></v>} openpyxl would have
     * written.
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
            assertTrue(log.containsMessageContaining("'V2'"), () -> String.valueOf(log.messages()));
            assertTrue(log.containsMessageContaining("Excel column C"),
                    () -> String.valueOf(log.messages()));
            assertTrue(log.containsMessageContaining("'V3'"), () -> String.valueOf(log.messages()));
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
    // The returned streaming Sheet reads through this stream and workbook, so neither may be
    // closed here; see above.
    @SuppressWarnings(
    {
            "resource", "PMD.CloseResource"
    })
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

    // ==================== the type-inference sample really is capped ====================


    /**
     * {@code provideMetaData()} must sample exactly {@code guessingRowCount} data rows — no more,
     * no fewer — because the sample decides the column TYPE. Two columns pin the cap from both
     * sides with one fixture: column A turns text in the LAST sampled row (so a sample one row too
     * short would call it DOUBLE) and column B turns text in the FIRST row past the cap (so a
     * sample one row too long would call it STRING).
     * <p>
     * {@code provide()}'s copy of the same loop is pinned by
     * {@link #testGuessingRowCountExactBoundary()}; this is the metadata-only path, whose answer
     * must agree with it.
     */
    @Test
    void testProvideMetaDataSamplesExactlyGuessingRowCountRows() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("A");
            h.createCell(1).setCellValue("B");
            Row r1 = s.createRow(1);
            r1.createCell(0).setCellValue(1.0);
            r1.createCell(1).setCellValue(10.0);
            Row r2 = s.createRow(2);
            r2.createCell(0).setCellValue("text-in-row-2"); // inside the 2-row sample
            r2.createCell(1).setCellValue(20.0);
            Row r3 = s.createRow(3);
            r3.createCell(0).setCellValue(3.0);
            r3.createCell(1).setCellValue("text-in-row-3"); // one row PAST the 2-row sample
        });
        provider.setGuessingRowCount(2);

        DataTableMeta meta = provider.provideMetaData(uri, ExcelProviderSupplier.FI_XLSX);

        assertEquals(DataValueType.STRING, meta.getColumn(0).getType(),
                "a text cell inside the sample must keep the column STRING");
        assertEquals(DataValueType.DOUBLE, meta.getColumn(1).getType(),
                "a text cell past the sample must not be seen: the sample is capped");
    }


    /**
     * The same cap, for {@code inferColumns()}'s own sample loop (the library-tree path) — called
     * here with a sample of 2 so a loop that ran one row too long would see the text cell in row 3
     * and type the column STRING.
     */
    @Test
    void testInferColumnsSamplesExactlyTheRequestedRowCount() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            s.createRow(0).createCell(0).setCellValue("B");
            s.createRow(1).createCell(0).setCellValue(10.0);
            s.createRow(2).createCell(0).setCellValue(20.0);
            s.createRow(3).createCell(0).setCellValue("text-in-row-3");
        });

        DataTableColumnMeta[] cols = ExcelTableProvider.inferColumns(openSheet(uri), uri, 2);

        assertEquals(1, cols.length);
        assertEquals(DataValueType.DOUBLE, cols[0].getType(),
                "a text cell past the requested sample size must not be sampled");
    }

    // ============ H7: a COMPUTED date is a date too (formula cell, cached numeric) =============


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


    @Test
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
                dc.setCellValue(java.time.LocalDate.of(1970, 1, 1).plusDays(r));
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


    /**
     * Numeric and formula header cells are headers; an ERROR header cell no longer ends the range
     * but keeps its column under the positional name, and the columns after it are read (owner
     * ruling Q37(ii), 2026-10-12: <i>"I would like to get this column in with a temp name, so
     * nothing is lost, additionally log with warning."</i>). It used to stop the range there.
     */
    @Test
    void inferColumns_acceptsNumericAndFormulaHeadersAndKeepsAnErrorHeaderPositionally()
        throws IOException
    {
        try (Workbook wb = new XSSFWorkbook())
        {
            Sheet s = wb.createSheet("F");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("STR");
            h.createCell(1).setCellValue(42.0); // numeric header cell
            h.createCell(2).setCellFormula("1+1"); // numeric-formula header cell
            h.createCell(3).setCellErrorValue(FormulaError.NA.getCode()); // ERROR: kept as V4
            h.createCell(4).setCellValue("AFTER"); // read: an error no longer ends the range

            Row d = s.createRow(1);
            d.createCell(0).setCellValue("x");
            d.createCell(1).setCellValue(1.0);
            d.createCell(2).setCellValue(2.0);

            wb.getCreationHelper().createFormulaEvaluator().evaluateAll();

            DataTableColumnMeta[] cols = ExcelTableProvider.inferColumns(s,
                    URI.create("file:///f.xlsx"), 10);
            assertEquals(5, cols.length);
            assertEquals("STR", cols[0].getName());
            assertEquals("V4", cols[3].getName());
            assertEquals("AFTER", cols[4].getName());
        }
    }

    // ============ TZ: a date reads the same in every JVM default time zone ============


    /**
     * DEFECT FIX (TZ): an Excel date is a wall-clock value with no zone, so the SAS datetime read
     * from it must not depend on the JVM's default time zone. The reader used
     * {@code Cell.getDateCellValue()}, which places the serial in the DEFAULT zone, and then read
     * the resulting instant back as UTC - so every date moved by the machine's UTC offset:
     * 2023-03-15 read as 2023-03-14T23:00 in Berlin and as 05:00 in New York. CI runs in UTC, where
     * that offset is zero, so no test run there could see it; this one forces the zone instead.
     * <p>
     * The zones straddle UTC in both directions; under the old code Pacific/Kiritimati (UTC+14)
     * moved a date-only value onto the previous calendar day, and 2023-03-26 02:30 does not exist
     * at all in Berlin (the DST gap). Expected values are derived outside this stack: serial n is
     * {@code 1899-12-30 + n days}, in seconds since 1960-01-01 (Python {@code datetime}: 45000 →
     * 1994457600; 45011 + 2.5/24 → 2023-03-26T02:30 → 1995417000).
     * </p>
     */
    @Test
    void testDateCellsReadTheSameInEveryDefaultTimeZone() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            DataFormat df = wb.createDataFormat();
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(df.getFormat("yyyy-mm-dd hh:mm"));

            s.createRow(0).createCell(0).setCellValue("VISITDTM");
            Row r1 = s.createRow(1);
            r1.createCell(0).setCellValue(45000.0);
            r1.getCell(0).setCellStyle(dateStyle);
            Row r2 = s.createRow(2);
            r2.createCell(0).setCellValue(45011.0 + 2.5 / 24.0);
            r2.getCell(0).setCellStyle(dateStyle);
        });

        TimeZone saved = TimeZone.getDefault();
        try
        {
            for (String zone : List.of("UTC", "Europe/Berlin", "America/New_York",
                    "Pacific/Kiritimati"))
            {
                TimeZone.setDefault(TimeZone.getTimeZone(zone));
                IDataTable table = new ExcelTableProvider().provide(uri,
                        ExcelProviderSupplier.FI_XLSX);

                assertEquals("E8601DT.", table.getMetaData().getColumn(0).getDisplayFormat(), zone);
                assertEquals(1_994_457_600.0, (double) table.getValue(0, 0), 0.5,
                        zone + ": 2023-03-15 must not move by the zone's UTC offset");
                assertEquals(1_995_417_000.0, (double) table.getValue(1, 0), 0.5,
                        zone + ": 2023-03-26T02:30 must survive a local DST gap");
            }
        }
        finally
        {
            TimeZone.setDefault(saved);
        }
    }


    /**
     * The edges of the TZ fix's conversion: Excel's last representable date, 9999-12-31 (serial
     * 2958465), still reads as that date, while a serial past it - here one day past, and
     * {@code 1e308} - is missing rather than a date. Without the guard, POI's
     * {@code DateUtil.getLocalDateTime} overflows on {@code 1e308} and answers 1899-12-31: a
     * present, plausible and wrong date. Expected value: 9999-12-31 is 253717833600 seconds after
     * 1960-01-01 (Python {@code datetime}).
     */
    @Test
    void testASerialPastExcelsLastDateIsMissingNotAWrappedDate() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(wb.createDataFormat().getFormat("yyyy-mm-dd"));
            s.createRow(0).createCell(0).setCellValue("DT");
            double[] serials =
            {
                    2_958_465.0, 2_958_466.0, 1e308
            };
            for (int i = 0; i < serials.length; i++)
            {
                Row r = s.createRow(i + 1);
                r.createCell(0).setCellValue(serials[i]);
                r.getCell(0).setCellStyle(dateStyle);
            }
        });

        IDataTable table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);

        assertEquals(253_717_833_600.0, (double) table.getValue(0, 0), 0.5,
                "Excel's last date, 9999-12-31, is still a date");
        assertSame(MissingValue.MIS_ERROR, table.getValue(1, 0),
                "one day past Excel's last date is not a date: a present value, unreadable");
        assertSame(MissingValue.MIS_ERROR, table.getValue(2, 0),
                "1e308 must be MIS_ERROR, not POI's overflowed 1899-12-31");
    }

    // ============ a numeric cell with a BLANK stored value is MISSING, never 0 ============

    /**
     * The three shapes of a numeric cell whose stored value is blank, each written as raw sheet XML
     * because POI cannot author any of them: an uncalculated formula with an EMPTY cached value
     * element (what openpyxl, and therefore {@code pandas.DataFrame.to_excel}, writes for every
     * formula), a plain cell with an empty value, and an explicitly {@code t="n"}-typed cell with
     * an empty value (the shape excel-streaming-reader's own 5.3.2 change names).
     * {@code WHITESPACE} is a whitespace-only stored value, which the reader cannot read at all (it
     * throws from {@code getCellType()}), so it degrades to missing by a different path.
     */
    private enum BlankNumeric
    {

        UNCALCULATED_FORMULA("", "<f>1+1</f><v></v>"),
        PLAIN("", "<v></v>"),
        TYPE_N(" t=\"n\"", "<v/>"),
        WHITESPACE("", "<v> </v>");

        private final String typeAttribute;

        private final String body;

        BlankNumeric(String aTypeAttribute, String aBody)
        {
            typeAttribute = aTypeAttribute;
            body = aBody;
        }
    }

    /**
     * Writes the workbook {@code aBuilder} builds, then rewrites each cell named in
     * {@code aBlankCells} (by its A1 reference) to the given blank shape, keeping the cell's own
     * attributes - its style above all, so a date-styled placeholder stays date-styled - except
     * {@code t}, which the shape decides. Fails the test when a named cell is not in the sheet XML.
     */
    private URI writeWithBlankNumericCells(WorkbookBuilder aBuilder,
            java.util.Map<String, BlankNumeric> aBlankCells)
        throws IOException
    {
        URI plain = writeTempXlsx(aBuilder);
        byte[] bytes = rewriteSheet1Xml(Files.readAllBytes(new File(plain).toPath()), xml ->
        {
            String out = xml;
            for (java.util.Map.Entry<String, BlankNumeric> e : aBlankCells.entrySet())
            {
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("<c r=\"" + e.getKey() + "\"([^>]*)>.*?</c>").matcher(out);
                assertTrue(m.find(), "fixture setup: cell " + e.getKey() + " not found");
                String attributes = m.group(1).replaceAll(" t=\"[^\"]*\"", "");
                out = out.substring(0, m.start()) + "<c r=\"" + e.getKey() + "\"" + attributes
                        + e.getValue().typeAttribute + ">" + e.getValue().body + "</c>"
                        + out.substring(m.end());
            }
            return out;
        });
        File patched = File.createTempFile("xlsxblanknumeric", ".xlsx");
        patched.deleteOnExit();
        Files.write(patched.toPath(), bytes);
        return patched.toURI();
    }


    /**
     * A numeric DATA cell whose stored value is blank loads as MISSING - in every shape, through
     * every entry point - while a real {@code 0} beside it stays {@code 0.0}. A column made only of
     * such cells is evidence of no type, so it is a {@code STRING} column (owner, 2026-10-16:
     * <i>"agree to Char as default type."</i>), read as XLSX Char cells read: a blank cell is
     * {@code ""}, an uncalculated formula {@code MIS_ERROR}; no display format either way. A column
     * with one real number among the blank cells stays {@code DOUBLE} (the evidence sibling).
     * <p>
     * Characterised on excel-streaming-reader 5.3.0, whose {@code getNumericCellValue()} threw
     * {@code NumberFormatException("empty String")} for a blank stored value; 5.3.2 returns
     * {@code 0.0} for it instead, which would silently load an unevaluated data formula as a
     * present {@code 0}. The provider's own guard keeps the answer (owner ruling R1, 2026-10-14).
     */
    @Test
    void testBlankNumericDataCellsLoadAsMissingNotZeroInEveryShape() throws Exception
    {
        String[] names =
        {
                "ID", "CALC", "ZERO", "ONLYF", "PLAIN", "TYPEN", "ONLYP", "ONLYN"
        };
        URI uri = writeWithBlankNumericCells(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            Row h = s.createRow(0);
            for (int i = 0; i < names.length; i++)
            {
                h.createCell(i).setCellValue(names[i]);
            }
            double[][] data =
            {
                    {
                            0, 0.0, 0, 0, 0, 0, 0
                    },
                    {
                            7.0, 5.0, 0, 8.0, 9.0, 0, 0
                    }
            };
            for (int r = 0; r < 2; r++)
            {
                Row row = s.createRow(r + 1);
                row.createCell(0).setCellValue("r" + (r + 1));
                for (int c = 1; c < names.length; c++)
                {
                    row.createCell(c).setCellValue(data[r][c - 1]);
                }
            }
        }, java.util.Map.of("B2", BlankNumeric.UNCALCULATED_FORMULA, "D2",
                BlankNumeric.UNCALCULATED_FORMULA, "D3", BlankNumeric.UNCALCULATED_FORMULA, "E2",
                BlankNumeric.PLAIN, "F2", BlankNumeric.TYPE_N, "G2", BlankNumeric.PLAIN, "G3",
                BlankNumeric.PLAIN, "H2", BlankNumeric.TYPE_N, "H3", BlankNumeric.TYPE_N));

        IDataTable table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);
        DataTableMeta meta = provider.provideMetaData(uri, ExcelProviderSupplier.FI_XLSX);
        DataTableColumnMeta[] inferred = ExcelTableProvider.inferColumns(openSheet(uri), uri, 50);

        assertEquals(names.length, table.getColumnCount());
        assertEquals(2, table.getRowCount());
        for (int c = 1; c < names.length; c++)
        {
            for (DataTableColumnMeta col : List.of(table.getMetaData().getColumn(c),
                    meta.getColumn(c), inferred[c]))
            {
                assertEquals(names[c], col.getName());
                boolean noEvidence = c == 3 || c == 6 || c == 7;
                assertEquals(noEvidence ? DataValueType.STRING : DataValueType.DOUBLE,
                        col.getType(), names[c]);
                assertNull(col.getDisplayFormat(), names[c]);
            }
        }
        assertSame(MissingValue.MIS_ERROR, table.getValue(0, 1),
                "an uncalculated formula is MIS_ERROR (Q37(i)), not 0");
        assertEquals(7.0, (double) table.getValue(1, 1), 0.0);
        assertEquals(0.0, (double) table.getValue(0, 2), 0.0,
                "a real 0 beside the blank cell stays a present 0");
        assertEquals(5.0, (double) table.getValue(1, 2), 0.0);
        assertSame(MissingValue.MIS, table.getValue(0, 4),
                "a plain numeric cell with an empty value is MIS (R1), not 0");
        assertEquals(8.0, (double) table.getValue(1, 4), 0.0);
        assertSame(MissingValue.MIS, table.getValue(0, 5),
                "a t=\"n\" cell with an empty value is MIS (R1), not 0");
        assertEquals(9.0, (double) table.getValue(1, 5), 0.0);
        for (int c : new int[]
        {
                3, 6, 7
        })
        {
            Object expected = c == 3 ? MissingValue.MIS_ERROR : "";
            for (int r = 0; r < 2; r++)
            {
                assertEquals(expected, table.getValue(r, c), names[c] + " row " + r
                        + ": a Char column of uncalculated formulas is MIS_ERROR, of blank cells"
                        + " \"\"");
            }
        }
    }


    /**
     * A REAL number under a number format that renders it as empty text stays a present number,
     * through every entry point, and a real {@code 0} header under such a format is still named by
     * its number, now {@code "0"} (the shortest plain decimal, ECT-Q2 ⓐ, owner 2026-10-16; it was
     * {@code "0.0"}, as excel-streaming-reader 5.3.0 gave it). The formats are the ordinary
     * zero-hiding ones accounting sheets use - {@code 0;-0;""} and {@code #,##0;-#,##0;""} (third
     * section empty) and {@code [=0]"";General} (a condition) - plus {@code 0;"";""}, which hides
     * every negative number, so a real {@code -5} renders empty as well.
     * <p>
     * Guards the review finding against the first 5.3.2 guard: the streaming reader's
     * {@code getStringCellValue()} is the FORMATTED display text, not the stored value, so deciding
     * blankness from it alone loaded each of these cells as MISSING. Also carries the
     * whitespace-only stored value {@code <v> </v>}, which degrades to missing through the reader
     * throwing from {@code getCellType()} (both versions), not through the blank-value guard.
     */
    @Test
    void testRealNumbersUnderFormatsThatRenderThemEmptyStayPresent() throws Exception
    {
        String[] names =
        {
                "ID", "Z1", "Z2", "Z3", "NEG", "0", "WS"
        };
        String[] formats =
        {
                null, "0;-0;\"\"", "#,##0;-#,##0;\"\"", "[=0]\"\";General", "0;\"\";\"\"",
                "0;-0;\"\"", null
        };
        URI uri = writeWithBlankNumericCells(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            DataFormat df = wb.createDataFormat();
            CellStyle[] styles = new CellStyle[formats.length];
            for (int c = 0; c < formats.length; c++)
            {
                if (formats[c] != null)
                {
                    styles[c] = wb.createCellStyle();
                    styles[c].setDataFormat(df.getFormat(formats[c]));
                }
            }
            Row h = s.createRow(0);
            for (int c = 0; c < names.length; c++)
            {
                if (c == 5)
                {
                    h.createCell(c).setCellValue(0.0);
                    h.getCell(c).setCellStyle(styles[c]);
                }
                else
                {
                    h.createCell(c).setCellValue(names[c]);
                }
            }
            double[][] data =
            {
                    {
                            0, 0, 0, -5, 3, 1
                    },
                    {
                            7, 8, 9, 4, 0, 2
                    }
            };
            for (int r = 0; r < 2; r++)
            {
                Row row = s.createRow(r + 1);
                row.createCell(0).setCellValue("r" + (r + 1));
                for (int c = 1; c < names.length; c++)
                {
                    row.createCell(c).setCellValue(data[r][c - 1]);
                    if (styles[c] != null)
                    {
                        row.getCell(c).setCellStyle(styles[c]);
                    }
                }
            }
        }, java.util.Map.of("G2", BlankNumeric.WHITESPACE));

        IDataTable table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);
        DataTableMeta meta = provider.provideMetaData(uri, ExcelProviderSupplier.FI_XLSX);
        DataTableColumnMeta[] inferred = ExcelTableProvider.inferColumns(openSheet(uri), uri, 50);

        assertEquals(names.length, table.getColumnCount());
        assertEquals(2, table.getRowCount());
        List<Executable> claims = new ArrayList<>();
        for (int c = 1; c <= 3; c++)
        {
            Object value = table.getValue(0, c);
            String name = names[c];
            claims.add(() -> assertEquals((Object) 0.0, value,
                    name + ": a real 0 under a zero-hiding format is a present 0"));
        }
        Object minusFive = -5.0;
        claims.add(() -> assertEquals(minusFive, table.getValue(0, 4),
                "a real -5 under 0;\"\";\"\" is a present -5"));
        claims.add(() -> assertEquals((Object) 0.0, table.getValue(1, 5),
                "a real 0 data cell under the header's zero-hiding format is a present 0"));
        claims.add(() -> assertEquals((Object) 3.0, table.getValue(0, 5)));
        claims.add(() -> assertEquals((Object) 7.0, table.getValue(1, 1)));
        claims.add(() -> assertEquals((Object) 4.0, table.getValue(1, 4)));
        claims.add(() -> assertSame(MissingValue.MIS_ERROR, table.getValue(0, 6),
                "a whitespace-only stored value the reader cannot read (styles table) is"
                        + " MIS_ERROR, not 0 (named gap L11)"));
        claims.add(() -> assertEquals((Object) 2.0, table.getValue(1, 6)));
        for (int c = 1; c < names.length; c++)
        {
            String name = names[c];
            for (DataTableColumnMeta col : List.of(table.getMetaData().getColumn(c),
                    meta.getColumn(c), inferred[c]))
            {
                claims.add(() -> assertEquals(name, col.getName(),
                        "a real 0 header under a zero-hiding format is named by its number, 0"));
                claims.add(() -> assertEquals(DataValueType.DOUBLE, col.getType(), name));
                claims.add(() -> assertNull(col.getDisplayFormat(), name));
            }
        }
        assertAll(claims);
    }


    /**
     * The one shape the public API cannot tell apart: a cell whose number format hides zero AND
     * whose stored value is blank ({@code <c s="…"><v></v></c>} under {@code 0;-0;""}). Its display
     * text is empty and its numeric value reads {@code 0.0} on excel-streaming-reader 5.3.2 -
     * exactly what a real {@code 0} under that format gives. It loads as a present {@code 0} (owner
     * ruling R8, 2026-10-14): the public API cannot tell an uncalculated formula from a real
     * {@code 0} there, and real zeros under zero-hiding formats are the common case. The decision
     * is one branch of {@code BlankNumericProbe.emptyTextMeansBlankValue}. The same blank cell
     * under {@code General} is missing (see
     * {@link #testBlankNumericDataCellsLoadAsMissingNotZeroInEveryShape}).
     */
    @Test
    void testBlankStoredValueUnderAZeroHidingFormatReadsAsPresentZero() throws Exception
    {
        URI uri = writeWithBlankNumericCells(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            CellStyle hideZero = wb.createCellStyle();
            hideZero.setDataFormat(wb.createDataFormat().getFormat("0;-0;\"\""));
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("V");
            h.createCell(1).setCellValue("F");
            for (int r = 1; r <= 2; r++)
            {
                Row row = s.createRow(r);
                for (int c = 0; c < 2; c++)
                {
                    row.createCell(c).setCellValue(r);
                    row.getCell(c).setCellStyle(hideZero);
                }
            }
        }, java.util.Map.of("A2", BlankNumeric.PLAIN, "B2", BlankNumeric.UNCALCULATED_FORMULA));

        IDataTable table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);

        assertEquals(DataValueType.DOUBLE, table.getMetaData().getColumn(0).getType());
        assertEquals((Object) 0.0, table.getValue(0, 0),
                "R8: a blank stored value under a zero-hiding format reads as a present 0");
        assertEquals(2.0, (double) table.getValue(1, 0), 0.0);
        assertEquals((Object) 0.0, table.getValue(0, 1),
                "R8 corner of Q37(i) (named gap): an uncalculated formula under a zero-hiding"
                        + " format reads as a present 0, not MIS_ERROR");
        assertEquals(2.0, (double) table.getValue(1, 1), 0.0);
    }


    /**
     * The same blank shapes, DATE-styled: a blank cell never makes a column a date column and is
     * never converted to a date - serial {@code 0} would be 1899-12-31, a present, plausible and
     * wrong date. In a column whose literal dates make it a date column, the blank row is missing;
     * a column made only of date-styled blank cells has no evidence of a type, so it is a
     * {@code STRING} column (owner, 2026-10-16, Char default) with NO {@code E8601DT.}, through
     * every entry point: a blank cell reads {@code ""}, an uncalculated formula {@code MIS_ERROR}.
     * Characterised on excel-streaming-reader 5.3.0 (see
     * {@link #testBlankNumericDataCellsLoadAsMissingNotZeroInEveryShape}). Expected date: 45000 is
     * 2023-03-15, 1994457600 seconds after 1960-01-01 (Python {@code datetime}).
     */
    @Test
    void testDateStyledBlankNumericCellsAreMissingAndNeverMakeADateColumn() throws Exception
    {
        String[] names =
        {
                "DTF", "DTP", "DTN", "ONLYDTF", "ONLYDTP", "ONLYDTN"
        };
        URI uri = writeWithBlankNumericCells(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(wb.createDataFormat().getFormat("yyyy-mm-dd"));
            Row h = s.createRow(0);
            for (int i = 0; i < names.length; i++)
            {
                h.createCell(i).setCellValue(names[i]);
            }
            for (int r = 1; r <= 2; r++)
            {
                Row row = s.createRow(r);
                for (int c = 0; c < names.length; c++)
                {
                    dateCell(row, c, dateStyle).setCellValue(45000.0);
                }
            }
        }, java.util.Map.of("A3", BlankNumeric.UNCALCULATED_FORMULA, "B3", BlankNumeric.PLAIN, "C3",
                BlankNumeric.TYPE_N, "D2", BlankNumeric.UNCALCULATED_FORMULA, "D3",
                BlankNumeric.UNCALCULATED_FORMULA, "E2", BlankNumeric.PLAIN, "E3",
                BlankNumeric.PLAIN, "F2", BlankNumeric.TYPE_N, "F3", BlankNumeric.TYPE_N));

        IDataTable table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);
        DataTableMeta meta = provider.provideMetaData(uri, ExcelProviderSupplier.FI_XLSX);
        DataTableColumnMeta[] inferred = ExcelTableProvider.inferColumns(openSheet(uri), uri, 50);

        assertEquals(names.length, table.getColumnCount());
        assertEquals(2, table.getRowCount());
        for (int c = 0; c < names.length; c++)
        {
            String expectedFormat = c < 3 ? "E8601DT." : null;
            for (DataTableColumnMeta col : List.of(table.getMetaData().getColumn(c),
                    meta.getColumn(c), inferred[c]))
            {
                assertEquals(names[c], col.getName());
                assertEquals(c < 3 ? DataValueType.DOUBLE : DataValueType.STRING, col.getType(),
                        names[c]);
                assertEquals(expectedFormat, col.getDisplayFormat(), names[c]);
            }
        }
        for (int c = 0; c < 3; c++)
        {
            assertEquals(1_994_457_600.0, (double) table.getValue(0, c), 0.5, names[c]);
            assertSame(c == 0 ? MissingValue.MIS_ERROR : MissingValue.MIS, table.getValue(1, c),
                    names[c] + ": a blank cell in a date column is MIS (an uncalculated formula"
                            + " MIS_ERROR), not 1899-12-31");
        }
        for (int c = 3; c < names.length; c++)
        {
            Object expected = c == 3 ? MissingValue.MIS_ERROR : "";
            for (int r = 0; r < 2; r++)
            {
                assertEquals(expected, table.getValue(r, c), names[c] + " row " + r
                        + ": a date-styled blank cell in a Char column is blank, never a date");
            }
        }
    }

    // ============ BNM-F7 / Q37(i): a non-blank unreadable numeric cell is MIS_ERROR ============


    /**
     * A cell past the type-guessing sample in a column the sample typed {@code DOUBLE}: a BLANK
     * cell (absent, or text that is empty or whitespace only) is {@link MissingValue#MIS}; a
     * NON-BLANK cell that is not a number is {@link MissingValue#MIS_ERROR} (owner ruling BNM-F7,
     * 2026-10-12): text that does not parse, an error cell (literal or formula-cached), a boolean.
     * A number whose value is infinite - text {@code "Infinity"}, or a stored {@code 1E+400} - is
     * {@code MIS_ERROR} too (owner, 2026-10-12: <i>"Should CSV/XLSX text Infinity also become
     * MIS_ERROR: yes."</i>). Text {@code "NaN"} keeps BNM E5 ({@code MIS}); text that parses keeps
     * its number.
     */
    @Test
    void testUnreadableAndInfiniteNumericCellsAreMisErrorAndBlankOnesMis() throws Exception
    {
        provider.setGuessingRowCount(2);
        URI plain = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            s.createRow(0).createCell(0).setCellValue("VAL");
            s.createRow(1).createCell(0).setCellValue(1.0);
            s.createRow(2).createCell(0).setCellValue(2.0);
            s.createRow(3).createCell(0).setCellValue("abc");
            s.createRow(4).createCell(0).setCellErrorValue(FormulaError.NA.getCode());
            s.createRow(5).createCell(0).setCellFormula("1/0");
            s.createRow(6).createCell(0).setCellValue("Infinity");
            s.createRow(7).createCell(0).setCellValue("NaN");
            s.createRow(8).createCell(0).setCellValue("   ");
            s.createRow(9).createCell(0).setCellValue("");
            s.createRow(10).createCell(0).setCellValue(true);
            s.createRow(11).createCell(0).setCellValue("42");
            s.createRow(12).createCell(0).setCellValue(777.0);
            s.createRow(13);
            s.createRow(14).createCell(0).setCellValue(5.0);
            evaluateAllFormulas(wb);
        });
        byte[] bytes = patchSheet1Xml(Files.readAllBytes(new File(plain).toPath()), "<v>777.0</v>",
                "<v>1E+400</v>");
        File patched = File.createTempFile("xlsxinfinite", ".xlsx");
        patched.deleteOnExit();
        Files.write(patched.toPath(), bytes);

        IDataTable table = provider.provide(patched.toURI(), ExcelProviderSupplier.FI_XLSX);

        assertEquals(DataValueType.DOUBLE, table.getMetaData().getColumn(0).getType());
        assertAll(() -> assertSame(MissingValue.MIS_ERROR, table.getValue(2, 0), "abc"),
                () -> assertSame(MissingValue.MIS_ERROR, table.getValue(3, 0), "#N/A cell"),
                () -> assertSame(MissingValue.MIS_ERROR, table.getValue(4, 0), "=1/0 cached"),
                () -> assertSame(MissingValue.MIS_ERROR, table.getValue(5, 0), "text Infinity"),
                () -> assertSame(MissingValue.MIS, table.getValue(6, 0), "text NaN: BNM E5"),
                () -> assertSame(MissingValue.MIS, table.getValue(7, 0), "whitespace-only text"),
                () -> assertSame(MissingValue.MIS, table.getValue(8, 0), "empty text"),
                () -> assertSame(MissingValue.MIS_ERROR, table.getValue(9, 0), "boolean"),
                () -> assertEquals(42.0, (double) table.getValue(10, 0), 0.0),
                () -> assertSame(MissingValue.MIS_ERROR, table.getValue(11, 0), "stored 1E+400"),
                () -> assertSame(MissingValue.MIS, table.getValue(12, 0), "absent cell"),
                () -> assertEquals(5.0, (double) table.getValue(13, 0), 0.0));
    }


    /**
     * The same split in a DATE column (one {@code inferDateFormats} gives {@code E8601DT.}): the
     * blank / non-blank decision is made from the raw cell, not from the converted value - a blank
     * cell is {@code MIS}, a present cell whose date conversion fails is {@code MIS_ERROR}: text
     * past the sample, a serial past Excel's last date, a negative serial, an uncalculated formula.
     */
    @Test
    void testDateColumnSplitsBlankFromUnreadableOnTheRawCell() throws Exception
    {
        provider.setGuessingRowCount(2);
        URI uri = writeWithBlankNumericCells(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(wb.createDataFormat().getFormat("yyyy-mm-dd"));
            s.createRow(0).createCell(0).setCellValue("DT");
            double[] serials =
            {
                    45000.0, 45001.0, 2_958_466.0, -5.0, 45002.0
            };
            for (int i = 0; i < serials.length; i++)
            {
                dateCell(s.createRow(i + 1), 0, dateStyle).setCellValue(serials[i]);
            }
            s.createRow(6).createCell(0).setCellValue("2023-01-01");
            s.createRow(7).createCell(0).setCellValue("   ");
            s.createRow(8);
        }, java.util.Map.of("A6", BlankNumeric.UNCALCULATED_FORMULA));

        IDataTable table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);

        assertEquals("E8601DT.", table.getMetaData().getColumn(0).getDisplayFormat());
        assertAll(() -> assertEquals(1_994_457_600.0, (double) table.getValue(0, 0), 0.5),
                () -> assertSame(MissingValue.MIS_ERROR, table.getValue(2, 0), "past max"),
                () -> assertSame(MissingValue.MIS_ERROR, table.getValue(3, 0), "negative serial"),
                () -> assertSame(MissingValue.MIS_ERROR, table.getValue(4, 0),
                        "uncalculated formula"),
                () -> assertSame(MissingValue.MIS_ERROR, table.getValue(5, 0), "text"),
                () -> assertSame(MissingValue.MIS, table.getValue(6, 0), "whitespace-only text"),
                () -> assertSame(MissingValue.MIS, table.getValue(7, 0), "absent cell"));
    }


    /**
     * The openpyxl-written workbook (external producer; see
     * {@code src/test/resources/openpyxl/generate-openpyxl-fixture.py}): every formula carries an
     * empty cached value, so the formula column reads {@code MIS_ERROR} in every row (Q37(i):
     * <i>"a, log and continue and read as MIS_ERROR."</i>), while a cell openpyxl left out is a
     * blank {@code MIS}. A column of only uncalculated formulas is evidence of no type, so it is
     * {@code STRING} (owner, 2026-10-16, Char default); its cells are {@code MIS_ERROR} either way.
     */
    @Test
    void testOpenpyxlFormulaColumnReadsMisError() throws Exception
    {
        IDataTable table = provider.provide(openpyxlFixture(), ExcelProviderSupplier.FI_XLSX);

        assertEquals("CALC", table.getMetaData().getColumn(1).getName());
        assertEquals(DataValueType.STRING, table.getMetaData().getColumn(1).getType());
        assertEquals(DataValueType.DOUBLE, table.getMetaData().getColumn(2).getType(),
                "the evidence sibling: a column with a real number stays DOUBLE");
        for (int r = 0; r < 3; r++)
        {
            assertSame(MissingValue.MIS_ERROR, table.getValue(r, 1), "CALC row " + r);
        }
        assertEquals(1.5, (double) table.getValue(0, 2), 0.0);
        assertSame(MissingValue.MIS, table.getValue(1, 2), "a cell openpyxl did not write");
    }


    /**
     * Q37(i): <i>"log and continue"</i> - ONE {@code WARNING} per column per load, at the first
     * such cell, naming the sheet, the cell and why; the other two formula cells of the column are
     * not logged. Asserted on the level as well as the text: a DEBUG line the user never sees would
     * not be the ruled log.
     */
    @Test
    void testOpenpyxlFormulaColumnWarnsOncePerColumn() throws Exception
    {
        try (LoggerCapture log = LoggerCapture.attach(ExcelTableProvider.class.getName()))
        {
            provider.provide(openpyxlFixture(), ExcelProviderSupplier.FI_XLSX);
            List<String> warnings = unreadableCellWarnings(log);
            assertEquals(1, warnings.size(), () -> String.valueOf(log.messages()));
            String w = warnings.get(0);
            assertAll(() -> assertTrue(w.contains("Excel cell B2 of sheet 'DATA'"), w),
                    () -> assertTrue(w.contains("column 'CALC'"), w),
                    () -> assertTrue(w.contains("formula without a cached value"), w),
                    () -> assertTrue(w.contains("not logged"), w));
        }
    }


    /** A cell the reader throws on is logged the same way, with its own reason. */
    @Test
    void testAReadFailureIsLoggedOnceAsWell() throws Exception
    {
        URI goodUri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            s.createRow(0).createCell(0).setCellValue("A");
            s.createRow(1).createCell(0).setCellValue(1.0);
            s.createRow(2).createCell(0).setCellValue(4.0);
            s.createRow(3).createCell(0).setCellValue(5.0);
        });
        byte[] bytes = patchSheet1Xml(Files.readAllBytes(new File(goodUri).toPath()), "<v>4.0</v>",
                "<v>N/A</v>");
        bytes = patchSheet1Xml(bytes, "<v>5.0</v>", "<v>N/B</v>");
        File corrupt = File.createTempFile("xlsxreadfail", ".xlsx");
        corrupt.deleteOnExit();
        Files.write(corrupt.toPath(), bytes);

        try (LoggerCapture log = LoggerCapture.attach(ExcelTableProvider.class.getName()))
        {
            IDataTable table = provider.provide(corrupt.toURI(), ExcelProviderSupplier.FI_XLSX);
            assertSame(MissingValue.MIS_ERROR, table.getValue(1, 0));
            assertSame(MissingValue.MIS_ERROR, table.getValue(2, 0));
            List<String> warnings = unreadableCellWarnings(log);
            assertEquals(1, warnings.size(), () -> String.valueOf(log.messages()));
            assertTrue(warnings.get(0).contains("Excel cell A3 of sheet 'DATA'"),
                    warnings::toString);
            assertTrue(warnings.get(0).contains("cell the reader cannot read"), warnings::toString);
        }
    }


    /**
     * The openpyxl workbook's {@code #REF!} header (column D; openpyxl writes the error code as an
     * error cell) keeps its column as {@code V4}, with its data, and the column after it is read -
     * through all three entry points, which must agree (Q37(ii)).
     */
    @Test
    void testOpenpyxlRefHeaderKeepsItsColumnInEveryEntryPoint() throws Exception
    {
        URI uri = openpyxlFixture();
        IDataTable table;
        try (LoggerCapture log = LoggerCapture.attach(ExcelTableProvider.class.getName()))
        {
            table = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);
            assertTrue(log.containsMessageContaining(
                    "Excel column D of sheet 'DATA' has an unreadable header (#REF!); using"
                            + " fallback 'V4'"),
                    () -> String.valueOf(log.messages()));
        }
        DataTableMeta meta = provider.provideMetaData(uri, ExcelProviderSupplier.FI_XLSX);
        DataTableColumnMeta[] inferred = ExcelTableProvider.inferColumns(openSheet(uri), uri, 50);

        List<String> expected = List.of("ID", "CALC", "NUM", "V4", "SITE");
        assertEquals(expected.size(), table.getColumnCount());
        for (int c = 0; c < expected.size(); c++)
        {
            assertEquals(expected.get(c), table.getMetaData().getColumn(c).getName());
            assertEquals(expected.get(c), meta.getColumn(c).getName());
            assertEquals(expected.get(c), inferred[c].getName());
        }
        assertEquals(DataValueType.DOUBLE, table.getMetaData().getColumn(3).getType());
        assertEquals(20.0, (double) table.getValue(1, 3), 0.0);
        assertEquals("S3", table.getValue(2, 4));
    }


    private static List<String> unreadableCellWarnings(LoggerCapture aLog)
    {
        return aLog.records().stream().filter(r -> r.getLevel() == java.util.logging.Level.WARNING)
                .map(java.util.logging.LogRecord::getMessage)
                .filter(m -> m.contains("reads as MIS_ERROR")).toList();
    }


    private URI openpyxlFixture() throws Exception
    {
        return java.util.Objects
                .requireNonNull(getClass().getResource("/openpyxl/uncalculated-formulas.xlsx"))
                .toURI();
    }
}
