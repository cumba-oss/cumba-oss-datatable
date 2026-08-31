package net.cumba.datatable.provider.xlsx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.values.DataValueType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ExcelTableProvider#provideMetaData(URI, net.cumba.datatable.io.FileInfo)}.
 */
class ExcelTableProviderMetaDataTest
{

    private ExcelTableProvider provider;

    @BeforeEach
    void setUp()
    {
        provider = new ExcelTableProvider();
    }

    @FunctionalInterface
    interface WorkbookBuilder
    {

        void build(Workbook wb);
    }

    private URI writeTempXlsx(WorkbookBuilder builder) throws IOException
    {
        File tmpFile = File.createTempFile("xlsxmetatest", ".xlsx");
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


    @Test
    void provideMetaData_matchesProvideMeta() throws Exception
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

        DataTableMeta metaOnly = provider.provideMetaData(uri, ExcelProviderSupplier.FI_XLSX);
        IDataTable full = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);
        DataTableMeta fullMeta = full.getMetaData();

        assertNotNull(metaOnly);
        assertEquals(fullMeta.getColumnCount(), metaOnly.getColumnCount());
        for (int i = 0; i < fullMeta.getColumnCount(); i++)
        {
            DataTableColumnMeta expected = fullMeta.getColumn(i);
            DataTableColumnMeta actual = metaOnly.getColumn(i);
            assertEquals(expected.getName(), actual.getName(), "column name[" + i + "]");
            assertEquals(expected.getType(), actual.getType(), "column type[" + i + "]");
        }
    }


    @Test
    void provideMetaData_inferredTypes() throws Exception
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

        DataTableMeta meta = provider.provideMetaData(uri, ExcelProviderSupplier.FI_XLSX);

        assertEquals(2, meta.getColumnCount());
        assertEquals("NAME", meta.getColumn(0).getName());
        assertEquals(DataValueType.STRING, meta.getColumn(0).getType());
        assertEquals("SCORE", meta.getColumn(1).getName());
        assertEquals(DataValueType.DOUBLE, meta.getColumn(1).getType());
    }


    @Test
    void provideMetaData_rowCountIsZero() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("NAME");
            for (int i = 1; i <= 20; i++)
            {
                Row r = s.createRow(i);
                r.createCell(0).setCellValue("v" + i);
            }
        });

        DataTableMeta meta = provider.provideMetaData(uri, ExcelProviderSupplier.FI_XLSX);

        // The xlsx provider does not populate rowCount in the metadata-only path.
        assertEquals(0, meta.getRowCount());
        assertEquals(0, meta.getTotalRowCount());
    }


    @Test
    void provideMetaData_sheetFragment() throws Exception
    {
        URI base = writeTempXlsx(wb ->
        {
            Sheet s1 = wb.createSheet("FIRST");
            s1.createRow(0).createCell(0).setCellValue("A");
            s1.createRow(1).createCell(0).setCellValue("a1");

            Sheet s2 = wb.createSheet("SECOND");
            s2.createRow(0).createCell(0).setCellValue("B");
            s2.createRow(1).createCell(0).setCellValue("b1");
        });

        URI uriSecond = URI.create(base.toString() + "#SECOND");
        DataTableMeta meta = provider.provideMetaData(uriSecond, ExcelProviderSupplier.FI_XLSX);

        assertEquals("SECOND", meta.getName());
        assertEquals(1, meta.getColumnCount());
        assertEquals("B", meta.getColumn(0).getName());
    }


    @Test
    void provideMetaData_unknownSheetFragmentThrows() throws Exception
    {
        URI base = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("ONLY");
            s.createRow(0).createCell(0).setCellValue("X");
            s.createRow(1).createCell(0).setCellValue("x1");
        });

        URI badFragment = URI.create(base.toString() + "#MISSING");
        assertThrows(IOException.class,
                () -> provider.provideMetaData(badFragment, ExcelProviderSupplier.FI_XLSX));
    }


    @Test
    void provideMetaData_emptySheetThrows() throws Exception
    {
        URI uri = writeTempXlsx(wb -> wb.createSheet("EMPTY"));
        assertThrows(IOException.class,
                () -> provider.provideMetaData(uri, ExcelProviderSupplier.FI_XLSX));
    }


    @Test
    @SuppressWarnings("JavaUtilDate") // POI's Cell.setCellValue works in java.util.Date
    void provideMetaData_dateColumnCarriesDisplayFormat() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            CellStyle dateStyle = wb.createCellStyle();
            DataFormat fmt = wb.createDataFormat();
            dateStyle.setDataFormat(fmt.getFormat("yyyy-mm-dd"));

            Sheet s = wb.createSheet("DATA");
            s.createRow(0).createCell(0).setCellValue("WHEN");
            for (int r = 1; r <= 2; r++)
            {
                Cell c = s.createRow(r).createCell(0);
                c.setCellValue(new java.util.Date(86_400_000L * r));
                c.setCellStyle(dateStyle);
            }
        });

        DataTableMeta meta = provider.provideMetaData(uri, ExcelProviderSupplier.FI_XLSX);
        assertEquals(1, meta.getColumnCount());
        assertNotNull(meta.getColumn(0).getDisplayFormat(),
                "a date-formatted column should carry a displayFormat");
    }


    @Test
    void provideMetaData_honoursSmallGuessingRowCount() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            s.createRow(0).createCell(0).setCellValue("V");
            for (int r = 1; r <= 5; r++)
            {
                s.createRow(r).createCell(0).setCellValue(r);
            }
        });

        // Only one row is sampled for type inference; the rest are skipped (exercises the
        // guessing-row-count break).
        provider.setGuessingRowCount(1);
        DataTableMeta meta = provider.provideMetaData(uri, ExcelProviderSupplier.FI_XLSX);
        assertEquals(1, meta.getColumnCount());
    }

}
