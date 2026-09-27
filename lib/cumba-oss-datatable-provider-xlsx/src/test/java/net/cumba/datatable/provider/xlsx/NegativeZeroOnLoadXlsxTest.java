package net.cumba.datatable.provider.xlsx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.values.DataValueType;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * PLAN-negative-zero-on-load, reader row "XLSX" (NZL O1, owner 2026-09-27: <i>"we drop the sign
 * from any 0.0 so a -0.0 gets read as 0.0"</i>). Both decode paths of plan §1.2 can produce a
 * {@code -0.0}: a <b>numeric</b> cell stored as {@code <v>-0.0</v>} (POI's
 * {@code getNumericCellValue()}), and a <b>text</b> cell {@code "-0.0"} in a column the sample
 * typed DOUBLE (the reader's {@code Double.parseDouble} fallback for a cell past the sample). After
 * loading, each cell must be {@code +0.0}.
 *
 * <p>
 * Sheet {@code DATA}, column {@code VAL}, sample capped at 3 rows: row 0 {@code +0.0}, row 1
 * {@code -1.5} (controls), row 2 the numeric {@code -0.0}, row 3 (past the sample) the text
 * {@code "-0.0"}.
 * </p>
 */
class NegativeZeroOnLoadXlsxTest
{

    private static final long NEGATIVE_ZERO_BITS = 0x8000_0000_0000_0000L;

    private static final double CONTROL = -1.5;

    private static final String TEXT = "-0.0";

    @TempDir
    Path tmp;

    private Path writeWorkbook() throws IOException
    {
        Path file = tmp.resolve("negzero.xlsx");
        try (Workbook wb = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(file))
        {
            Sheet s = wb.createSheet("DATA");
            s.createRow(0).createCell(0).setCellValue("VAL");
            s.createRow(1).createCell(0).setCellValue(0.0);
            s.createRow(2).createCell(0).setCellValue(CONTROL);
            s.createRow(3).createCell(0).setCellValue(-0.0);
            s.createRow(4).createCell(0).setCellValue(TEXT);
            wb.write(out);
        }
        return file;
    }


    /**
     * (1) Non-vacuity: the sheet XML stores {@code <v>-0.0</v>}, POI reads that numeric cell back
     * as {@code -0.0}, and the text cell really is the string {@code "-0.0"}.
     */
    @Test
    void theWorkbookHoldsANegativeZero() throws IOException
    {
        Path file = writeWorkbook();
        try (ZipFile zip = new ZipFile(file.toFile()))
        {
            ZipEntry sheet = zip.getEntry("xl/worksheets/sheet1.xml");
            assertNotNull(sheet, "sheet1.xml");
            String xml;
            try (InputStream in = zip.getInputStream(sheet))
            {
                xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            assertTrue(xml.contains("<v>-0.0</v>"), "the numeric cell is stored as -0.0: " + xml);
        }
        try (Workbook wb = new XSSFWorkbook(file.toFile()))
        {
            Row numeric = wb.getSheet("DATA").getRow(3);
            assertSame(CellType.NUMERIC, numeric.getCell(0).getCellType());
            assertEquals(NEGATIVE_ZERO_BITS,
                    Double.doubleToRawLongBits(numeric.getCell(0).getNumericCellValue()),
                    "POI reads the numeric cell back as -0.0");
            Row text = wb.getSheet("DATA").getRow(4);
            assertSame(CellType.STRING, text.getCell(0).getCellType());
            assertEquals(TEXT, text.getCell(0).getStringCellValue());
            assertEquals(NEGATIVE_ZERO_BITS, Double.doubleToRawLongBits(Double.parseDouble(TEXT)),
                    "the text parses to -0.0");
        }
        catch (InvalidFormatException e)
        {
            throw new IOException(e);
        }
    }


    /** (2) The numeric {@code -0.0} cell loads as {@code +0.0}; controls untouched. */
    @Test
    void aNegativeZeroNumericCellLoadsAsPositiveZero() throws IOException
    {
        IDataTable table = load();
        int col = table.getMetaData().getColumnIndex("VAL");
        assertCellBits(table, 0, col, 0L, "row 0 (+0.0 control)");
        assertCellBits(table, 1, col, Double.doubleToRawLongBits(CONTROL), "row 1 (-1.5 control)");
        assertCellBits(table, 2, col, 0L, "row 2 (numeric -0.0)");
    }


    /** (2) The text {@code "-0.0"} past the sample, in a DOUBLE column, loads as {@code +0.0}. */
    @Test
    void aNegativeZeroTextCellLoadsAsPositiveZero() throws IOException
    {
        IDataTable table = load();
        int col = table.getMetaData().getColumnIndex("VAL");
        assertCellBits(table, 3, col, 0L, "row 3 (text \"-0.0\" past the sample)");
    }


    private IDataTable load() throws IOException
    {
        Path file = writeWorkbook();
        ExcelTableProvider provider = new ExcelTableProvider();
        provider.setGuessingRowCount(3);
        IDataTable table = provider.provide(file.toUri(), ExcelProviderSupplier.FI_XLSX);
        assertEquals(4L, table.getRowCount());
        int col = table.getMetaData().getColumnIndex("VAL");
        assertSame(DataValueType.DOUBLE, table.getMetaData().getColumn(col).getType(),
                "the 3-row sample types VAL DOUBLE, so the text cell takes the parse fallback");
        return table;
    }


    private static void assertCellBits(IDataTable table, long row, int col, long expectedBits,
            String where)
    {
        Number raw = assertInstanceOf(Number.class, table.getValue(row, col), where);
        assertEquals(expectedBits, Double.doubleToRawLongBits(raw.doubleValue()),
                where + ": raw bits of getValue");
        assertEquals(expectedBits,
                Double.doubleToRawLongBits(table.getDataValue(row, col).getValueAsDouble()),
                where + ": raw bits of getDataValue().getValueAsDouble()");
    }
}
