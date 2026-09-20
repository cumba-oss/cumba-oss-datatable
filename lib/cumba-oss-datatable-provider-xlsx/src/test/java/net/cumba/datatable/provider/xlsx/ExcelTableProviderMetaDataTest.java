package net.cumba.datatable.provider.xlsx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.provider.DataTableMetaSupport;
import net.cumba.datatable.values.DataValueType;
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


    /**
     * {@code provideMetaData()} goes through {@link ExcelTableProvider#buildMeta}, a separate code
     * path from {@code provide()}'s inline metadata build — its own {@code setFileFormat} /
     * {@code setDatasetSize} calls were previously unasserted anywhere (survived mutants).
     */
    @Test
    void provideMetaData_recordsFileFormatAndDatasetSize() throws Exception
    {
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            s.createRow(0).createCell(0).setCellValue("A");
            s.createRow(1).createCell(0).setCellValue("v");
        });
        long actualSize = Files.size(new File(uri).toPath());

        DataTableMeta meta = provider.provideMetaData(uri, ExcelProviderSupplier.FI_XLSX);

        assertEquals("XLSX", meta.getMetaData(DataTableMetaSupport.META_KEY_FILE_FORMAT));
        Object size = meta.getMetaData(DataTableMetaSupport.META_KEY_DATASET_SIZE);
        assertNotNull(size);
        assertEquals(actualSize, ((Number) size).longValue());
    }


    /**
     * The metadata-only path must also carry the {@code E8601DT.} displayFormat for a
     * date-formatted column — {@code buildMeta}'s own {@code inferDateFormats} call, exercised
     * separately from {@code provide()}'s.
     */
    @Test
    void provideMetaData_dateFormattedColumnGetsIsoDisplayFormat() throws Exception
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
            cell.setCellValue(java.util.Date.from(java.time.LocalDate.of(2024, 1, 15)
                    .atStartOfDay(java.time.ZoneOffset.UTC).toInstant()));
            cell.setCellStyle(dateStyle);
        });

        DataTableMeta meta = provider.provideMetaData(uri, ExcelProviderSupplier.FI_XLSX);

        assertEquals(DataValueType.DOUBLE, meta.getColumn(0).getType());
        assertEquals("E8601DT.", meta.getColumn(0).getDisplayFormat());
    }


    /**
     * A {@code guessingRowCount} smaller than the sheet's row count must still cap
     * {@code buildMeta}'s own sampling loop — the boundary/negation mutants there are distinct
     * bytecode from {@code provide()}'s equivalent loop.
     */
    @Test
    void provideMetaData_guessingRowCountCapsSample() throws Exception
    {
        provider.setGuessingRowCount(2);
        URI uri = writeTempXlsx(wb ->
        {
            Sheet s = wb.createSheet("DATA");
            s.createRow(0).createCell(0).setCellValue("VAL");
            s.createRow(1).createCell(0).setCellValue(1.0);
            s.createRow(2).createCell(0).setCellValue(2.0);
        });

        DataTableMeta meta = provider.provideMetaData(uri, ExcelProviderSupplier.FI_XLSX);

        assertEquals(DataValueType.DOUBLE, meta.getColumn(0).getType());
    }


    @Test
    void provideMetaData_emptySheetThrows() throws Exception
    {
        URI uri = writeTempXlsx(wb -> wb.createSheet("EMPTY"));
        assertThrows(IOException.class,
                () -> provider.provideMetaData(uri, ExcelProviderSupplier.FI_XLSX));
    }

}
