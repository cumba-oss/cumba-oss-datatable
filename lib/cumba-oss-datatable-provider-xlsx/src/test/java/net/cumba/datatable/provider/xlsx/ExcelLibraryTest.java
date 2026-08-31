package net.cumba.datatable.provider.xlsx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.stream.Stream;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.library.IDataTableLibrary;
import net.cumba.datatable.library.ILibraryMember;
import net.cumba.datatable.library.ILibraryProvider;
import net.cumba.datatable.provider.xlsx.library.ExcelLibrary;
import net.cumba.datatable.provider.xlsx.library.ExcelLibraryMember;
import net.cumba.datatable.provider.xlsx.library.ExcelLibraryProvider;
import net.cumba.datatable.provider.xlsx.library.ExcelLibrarySupplier;
import net.cumba.datatable.values.DataValueType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ExcelLibraryProvider}, {@link ExcelLibrarySupplier}, {@link ExcelLibrary}, and
 * {@link ExcelLibraryMember}.
 */
@SuppressWarnings("resource")
class ExcelLibraryTest
{

    private ExcelLibrarySupplier supplier;

    @BeforeEach
    void setUp()
    {
        supplier = new ExcelLibrarySupplier();
    }


    private URI writeTempXlsx(String... sheetNames) throws IOException
    {
        File tmpFile = File.createTempFile("xlsxlibtest", ".xlsx");
        tmpFile.deleteOnExit();
        try (Workbook wb = new XSSFWorkbook())
        {
            for (String name : sheetNames)
            {
                Sheet s = wb.createSheet(name);
                Row h = s.createRow(0);
                h.createCell(0).setCellValue("COL1");
                Row r = s.createRow(1);
                r.createCell(0).setCellValue("val");
            }
            try (FileOutputStream fos = new FileOutputStream(tmpFile))
            {
                wb.write(fos);
            }
        }
        return tmpFile.toURI();
    }

    // --- ExcelLibrarySupplier tests ---


    @Test
    void testSupplierFISContainsFormats()
    {
        List<FileInfo> fis = ExcelLibrarySupplier.FIS;
        assertEquals(2, fis.size());
        assertTrue(fis.contains(ExcelProviderSupplier.FI_XLS));
        assertTrue(fis.contains(ExcelProviderSupplier.FI_XLSX));
    }


    @Test
    void testSupplierFISIsUnmodifiable()
    {
        assertThrows(UnsupportedOperationException.class,
                () -> ExcelLibrarySupplier.FIS.add(FileInfo.createFor("txt", "Text")));
    }


    @Test
    void testSupplierCanProvideForXlsx() throws Exception
    {
        URI uri = new URI("file:///test.xlsx");
        assertTrue(supplier.canProvideFor(uri, ExcelProviderSupplier.FI_XLSX));
    }


    @Test
    void testSupplierCanProvideForXls() throws Exception
    {
        URI uri = new URI("file:///test.xls");
        assertTrue(supplier.canProvideFor(uri, ExcelProviderSupplier.FI_XLS));
    }


    @Test
    void testSupplierRejectsURIWithFragment() throws Exception
    {
        URI uri = new URI("file:///test.xlsx#Sheet1");
        assertFalse(supplier.canProvideFor(uri, null));
    }


    @Test
    void testSupplierGetProvider() throws Exception
    {
        URI uri = new URI("file:///test.xlsx");
        ILibraryProvider provider = supplier.getProvider(uri, ExcelProviderSupplier.FI_XLSX);
        assertNotNull(provider);
        assertInstanceOf(ExcelLibraryProvider.class, provider);
    }


    @Test
    void testSupplierGetProviderUnsupported() throws Exception
    {
        URI uri = new URI("file:///test.csv");
        FileInfo csvInfo = FileInfo.createFor("csv", "CSV");
        ILibraryProvider provider = supplier.getProvider(uri, csvInfo);
        assertNull(provider);
    }

    // --- ExcelLibraryProvider tests ---


    @Test
    void testProviderName()
    {
        ExcelLibraryProvider provider = new ExcelLibraryProvider();
        assertEquals("ExcelLibraryProvider", provider.getName());
    }


    @Test
    void testProviderDescription()
    {
        ExcelLibraryProvider provider = new ExcelLibraryProvider();
        assertNotNull(provider.getDescription());
        assertFalse(provider.getDescription().isEmpty());
    }


    @Test
    void testProvideLibraryWithMultipleSheets() throws Exception
    {
        URI uri = writeTempXlsx("Sheet1", "Sheet2", "Sheet3");
        ExcelLibraryProvider provider = new ExcelLibraryProvider();

        IDataTableLibrary library = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);

        assertNotNull(library);
        assertInstanceOf(ExcelLibrary.class, library);

        ExcelLibrary exLib = (ExcelLibrary) library;
        long memberCount = exLib.getMembers().count();
        assertEquals(3, memberCount);
    }


    @Test
    void testProvideLibraryMemberNames() throws Exception
    {
        URI uri = writeTempXlsx("Demographics", "Vitals");
        ExcelLibraryProvider provider = new ExcelLibraryProvider();

        IDataTableLibrary library = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);
        ExcelLibrary exLib = (ExcelLibrary) library;

        List<String> names = exLib.getMembers().map(ILibraryMember::getName).toList();
        assertTrue(names.contains("Demographics"));
        assertTrue(names.contains("Vitals"));
    }


    @Test
    void testProvideLibraryMembersStream() throws Exception
    {
        URI uri = writeTempXlsx("SheetA");
        ExcelLibraryProvider provider = new ExcelLibraryProvider();
        IDataTableLibrary library = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);

        Stream<? extends ILibraryMember> members = provider.provideLibraryMembers(library);
        assertNotNull(members);
        assertEquals(1, members.count());
    }


    @Test
    void testProvideLibraryMembersForWrongTypeReturnsEmpty() throws Exception
    {
        ExcelLibraryProvider provider = new ExcelLibraryProvider();
        // Pass null — not an ExcelLibrary
        Stream<? extends ILibraryMember> members = provider.provideLibraryMembers(null);
        assertEquals(0, members.count());
    }


    @Test
    void testProvideLibraryMemberColumnsReturnsEmpty() throws Exception
    {
        ExcelLibraryProvider provider = new ExcelLibraryProvider();
        ExcelLibraryMember member = ExcelLibraryMember.builder().name("test").label("")
                .uri(new URI("file:///test.xlsx#test")).build();
        // provideLibraryMemberColumns currently returns empty stream
        assertEquals(0, provider.provideLibraryMemberColumns(member).count());
    }


    @Test
    void testGetLibraryAttributeReturnsNull()
    {
        ExcelLibraryProvider provider = new ExcelLibraryProvider();
        assertNull(provider.getLibraryAttribute(null, "anything"));
    }

    // --- ExcelLibrary tests ---


    @Test
    void testExcelLibraryType()
    {
        ExcelLibrary lib = new ExcelLibrary("TEST", "Test", null, null);
        assertEquals("Excel", lib.getType());
    }


    @Test
    void testExcelLibraryGetMembersWhenNull()
    {
        ExcelLibrary lib = new ExcelLibrary("TEST", "Test", null, null);
        // members not set → stream should be empty
        assertEquals(0, lib.getMembers().count());
    }

    // ==================== F-B16: cache columns at library open ====================


    /**
     * Helper: write a temp xlsx with two sheets — one all-numeric column "AGE" and one all-string
     * column "NAME" — so column-type inference has something to bite into.
     */
    private URI writeTypedXlsx() throws IOException
    {
        File tmpFile = File.createTempFile("xlsxlibtypes", ".xlsx");
        tmpFile.deleteOnExit();
        try (Workbook wb = new XSSFWorkbook())
        {
            Sheet s1 = wb.createSheet("Demographics");
            Row h1 = s1.createRow(0);
            h1.createCell(0).setCellValue("USUBJID");
            h1.createCell(1).setCellValue("AGE");
            for (int r = 1; r <= 5; r++)
            {
                Row dr = s1.createRow(r);
                dr.createCell(0).setCellValue("S" + r);
                dr.createCell(1).setCellValue(20.0 + r);
            }

            Sheet s2 = wb.createSheet("Visits");
            Row h2 = s2.createRow(0);
            h2.createCell(0).setCellValue("VISIT");
            for (int r = 1; r <= 3; r++)
            {
                Row dr = s2.createRow(r);
                dr.createCell(0).setCellValue("V" + r);
            }

            try (FileOutputStream fos = new FileOutputStream(tmpFile))
            {
                wb.write(fos);
            }
        }
        return tmpFile.toURI();
    }


    /**
     * F-B16: opening the library must cache column names + types on each {@link ExcelLibraryMember}
     * so the library tree can show them before the sheet is fully loaded.
     */
    @Test
    void testProvideLibraryCachesColumns() throws Exception
    {
        URI uri = writeTypedXlsx();
        ExcelLibraryProvider provider = new ExcelLibraryProvider();

        IDataTableLibrary library = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);
        ExcelLibrary exLib = (ExcelLibrary) library;

        ExcelLibraryMember demo = exLib.getMembers().filter(m -> "Demographics".equals(m.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Demographics sheet member missing"));

        DataTableColumnMeta[] cols = demo.getColumns();
        assertNotNull(cols, "F-B16 requires columns to be cached on each member");
        assertEquals(2, cols.length);
        assertEquals("USUBJID", cols[0].getName());
        assertEquals("AGE", cols[1].getName());
        // AGE column has all-numeric data → inferred as DOUBLE.
        assertEquals(DataValueType.DOUBLE, cols[1].getType());
        // USUBJID is all-string → STRING.
        assertEquals(DataValueType.STRING, cols[0].getType());
    }


    /**
     * F-B16: {@code provideLibraryMemberColumns} returns the cached column stream so the library
     * tree displays the column list without a second workbook open.
     */
    @Test
    void testProvideLibraryMemberColumnsReturnsCached() throws Exception
    {
        URI uri = writeTypedXlsx();
        ExcelLibraryProvider provider = new ExcelLibraryProvider();
        IDataTableLibrary library = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);
        ExcelLibrary exLib = (ExcelLibrary) library;

        ExcelLibraryMember demo = exLib.getMembers().filter(m -> "Demographics".equals(m.getName()))
                .findFirst().orElseThrow();

        List<? extends DataTableColumnMeta> streamed = provider.provideLibraryMemberColumns(demo)
                .toList();
        assertEquals(2, streamed.size());
        assertEquals("USUBJID", streamed.get(0).getName());
        assertEquals("AGE", streamed.get(1).getName());
    }


    /**
     * F-B16: a member built without sampled columns (e.g. constructed by a test or a path that
     * doesn't pre-infer) still produces an empty stream — the caller falls through to a full sheet
     * open.
     */
    @Test
    void testProvideLibraryMemberColumnsAbsentReturnsEmpty() throws Exception
    {
        ExcelLibraryProvider provider = new ExcelLibraryProvider();
        ExcelLibraryMember member = ExcelLibraryMember.builder().name("test").label("")
                .uri(new URI("file:///test.xlsx#test")).build();
        // No columns set on the builder → null columns array → empty stream.
        assertEquals(0, provider.provideLibraryMemberColumns(member).count());
    }


    /**
     * F-B16: an empty sheet (header-only) must not crash the library open; the member should still
     * appear (with zero columns).
     */
    @Test
    void testProvideLibraryWithHeaderOnlySheet() throws Exception
    {
        File tmpFile = File.createTempFile("xlsxlibheaderonly", ".xlsx");
        tmpFile.deleteOnExit();
        try (Workbook wb = new XSSFWorkbook())
        {
            Sheet s = wb.createSheet("OnlyHeader");
            Row h = s.createRow(0);
            h.createCell(0).setCellValue("COL1");
            try (FileOutputStream fos = new FileOutputStream(tmpFile))
            {
                wb.write(fos);
            }
        }
        URI uri = tmpFile.toURI();
        ExcelLibraryProvider provider = new ExcelLibraryProvider();
        IDataTableLibrary library = provider.provide(uri, ExcelProviderSupplier.FI_XLSX);
        ExcelLibrary exLib = (ExcelLibrary) library;
        ExcelLibraryMember m = exLib.getMembers().findFirst().orElseThrow();
        DataTableColumnMeta[] cols = m.getColumns();
        // Header is present; with zero sampled data rows the type-detector's "possibly double"
        // flag stays true and the inferred type defaults to DOUBLE — consistent with the same
        // edge-case behaviour in ExcelTableProvider.buildMeta. The test pins this so a future
        // change to the default surface area is intentional.
        assertNotNull(cols);
        assertEquals(1, cols.length);
        assertEquals("COL1", cols[0].getName());
        // Pin the documented (and quirky) edge-case: no data rows seen → DOUBLE.
        assertEquals(DataValueType.DOUBLE, cols[0].getType());
    }

}
