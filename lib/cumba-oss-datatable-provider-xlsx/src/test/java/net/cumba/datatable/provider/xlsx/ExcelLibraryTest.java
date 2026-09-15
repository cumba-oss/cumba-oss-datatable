package net.cumba.datatable.provider.xlsx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Stream;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.io.Property;
import net.cumba.datatable.library.IDataTableLibrary;
import net.cumba.datatable.library.ILibraryMember;
import net.cumba.datatable.library.ILibraryProvider;
import net.cumba.datatable.provider.xlsx.library.ExcelLibrary;
import net.cumba.datatable.provider.xlsx.library.ExcelLibraryMember;
import net.cumba.datatable.provider.xlsx.library.ExcelLibraryProvider;
import net.cumba.datatable.provider.xlsx.library.ExcelLibrarySupplier;
import net.cumba.datatable.provider.xlsx.testsupport.LoggerCapture;
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


    /**
     * Q37: reversed. This test was {@code testSupplierFISContainsFormats} and asserted two entries,
     * the second of which was {@code FI_XLS} — a format the library path could not open either.
     */
    @Test
    void testSupplierFISOffersOoxmlOnly()
    {
        List<FileInfo> fis = ExcelLibrarySupplier.FIS;
        assertEquals(1, fis.size());
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


    /**
     * Q37: reversed. This test was {@code testSupplierCanProvideForXls} and asserted the library
     * supplier accepts legacy BIFF. It must now refuse it by {@link FileInfo} and by extension.
     */
    @Test
    void testSupplierRefusesLegacyXls() throws Exception
    {
        URI uri = new URI("file:///test.xls");
        FileInfo legacyXls = FileInfo.createFor("xls", "Microsoft Excel Spreadsheet");
        assertFalse(supplier.canProvideFor(uri, legacyXls));
        assertFalse(supplier.canProvideFor(uri, null));
        assertNull(supplier.getProvider(uri, legacyXls));
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


    /**
     * {@code canProvideFor(uri, null)} with a URI that has NO fragment must fall through to
     * {@code super.canProvideFor}, which resolves by file extension. This exact combination — null
     * {@link FileInfo}, no fragment — was never exercised by any test: the null-FileInfo test above
     * uses a URI WITH a fragment (hits the earlier "reject fragment" branch instead), and the
     * with-fragment test always passes a non-null FileInfo.
     */
    @Test
    void testSupplierCanProvideForNoFragmentDelegatesToExtensionCheck() throws Exception
    {
        assertTrue(supplier.canProvideFor(new URI("file:///test.xlsx"), null));
        assertFalse(supplier.canProvideFor(new URI("file:///test.csv"), null));
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


    /**
     * The two-arg {@code provide(uri, null)} overload — called when the caller has no
     * {@link FileInfo} at all (e.g. auto-detected purely from extension) — must fall back to
     * {@code FI_XLSX} for the library's own FileInfo, not merely tolerate a null. Every other test
     * passes {@code FI_XLSX} explicitly and never observes this fallback.
     */
    @Test
    void testProvideWithNullFileInfoFallsBackToXlsx() throws Exception
    {
        URI uri = writeTempXlsx("Sheet1");
        ExcelLibraryProvider provider = new ExcelLibraryProvider();

        IDataTableLibrary library = provider.provide(uri, null);

        assertEquals(ExcelProviderSupplier.FI_XLSX, library.getFileInfo());
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


    @Test
    void testGetSupportedFileInfosReturnsSupplierFIS()
    {
        ExcelLibraryProvider provider = new ExcelLibraryProvider();
        assertEquals(ExcelLibrarySupplier.FIS, provider.getSupportedFileInfos());
    }


    /**
     * {@code getProviderProperties} must surface the URI-derived default library name — this
     * indirectly exercises {@code getLibraryNameFor} (never called directly by any test; it is
     * {@code protected} in a different package) for both its null-path guard and its
     * strip-path/strip-extension/uppercase computation.
     */
    @Test
    void testGetProviderPropertiesDefaultsToUriDerivedName() throws Exception
    {
        ExcelLibraryProvider provider = new ExcelLibraryProvider();
        URI uri = new URI("file:///data/demographics.xlsx");

        List<Property> props = provider.getProviderProperties(uri, null);

        assertEquals(1, props.size());
        assertEquals("DEMOGRAPHICS", props.get(0).defaultValue());
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

    // ==================== one bad sheet must not take the library open down ====================


    /**
     * Patches every worksheet part of a workbook at the raw XML level, replacing one literal
     * fragment with another and leaving every other byte untouched. Fails the test if the fragment
     * was not found anywhere, so a POI-version change to the emitted XML reds the fixture instead
     * of silently producing an unpatched workbook.
     */
    private static byte[] patchWorksheetXml(byte[] aXlsxBytes, String aOriginalXml,
            String aReplacementXml)
        throws IOException
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        boolean found = false;
        try (java.util.zip.ZipInputStream zin = new java.util.zip.ZipInputStream(
                new java.io.ByteArrayInputStream(aXlsxBytes));
                java.util.zip.ZipOutputStream zout = new java.util.zip.ZipOutputStream(out))
        {
            java.util.zip.ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null)
            {
                byte[] data = zin.readAllBytes();
                if (entry.getName().startsWith("xl/worksheets/"))
                {
                    String xml = new String(data, StandardCharsets.UTF_8);
                    String patched = xml.replace(aOriginalXml, aReplacementXml);
                    found = found || !patched.equals(xml);
                    data = patched.getBytes(StandardCharsets.UTF_8);
                }
                zout.putNextEntry(new java.util.zip.ZipEntry(entry.getName()));
                zout.write(data);
                zout.closeEntry();
            }
        }
        assertTrue(found, "fixture setup: the target XML was not found in any worksheet");
        return out.toByteArray();
    }


    /**
     * Column inference that blows up on ONE sheet must not abort the whole library open: that
     * member still appears (without cached columns, so opening it triggers a full read), every
     * other sheet keeps its inferred columns, and the failure is logged naming the sheet.
     * <p>
     * The failing sheet carries a numeric header cell whose cached {@code <v>} is the non-numeric
     * text {@code N/A} — the shape a third-party or hand-edited export leaves behind. The streaming
     * reader throws from {@code getCellType()} itself on such a cell, i.e. from inside
     * {@code determineColumns}, so the exception escapes {@code inferColumns} as a
     * {@code RuntimeException} — which is the only thing that reaches this catch.
     */
    @Test
    void testProvideLibraryContinuesWhenOneSheetsColumnInferenceFails() throws Exception
    {
        byte[] bytes;
        try (Workbook wb = new XSSFWorkbook())
        {
            Sheet good = wb.createSheet("GOOD");
            Row gh = good.createRow(0);
            gh.createCell(0).setCellValue("USUBJID");
            Row gr = good.createRow(1);
            gr.createCell(0).setCellValue("S01");

            Sheet bad = wb.createSheet("BAD");
            Row bh = bad.createRow(0);
            bh.createCell(0).setCellValue("NAME");
            bh.createCell(1).setCellValue(987654.0); // corrupted to <v>N/A</v> below
            Row br = bad.createRow(1);
            br.createCell(0).setCellValue("Bob");
            br.createCell(1).setCellValue(2.0);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            bytes = bos.toByteArray();
        }
        File tmpFile = File.createTempFile("xlsxlibbadsheet", ".xlsx");
        tmpFile.deleteOnExit();
        Files.write(tmpFile.toPath(), patchWorksheetXml(bytes, "<v>987654.0</v>", "<v>N/A</v>"));

        ExcelLibraryProvider provider = new ExcelLibraryProvider();
        ExcelLibrary lib;
        try (LoggerCapture log = LoggerCapture.attach(ExcelLibraryProvider.class.getName()))
        {
            lib = (ExcelLibrary) provider.provide(tmpFile.toURI(), ExcelProviderSupplier.FI_XLSX);
            assertTrue(log.containsMessageContaining("Column inference failed for sheet 'BAD'"),
                    "the skipped sheet must be logged by name: " + log.messages());
        }

        List<ExcelLibraryMember> members = lib.getMembers().toList();
        assertEquals(2, members.size(), "both sheets must still be listed");
        ExcelLibraryMember good = members.stream().filter(m -> "GOOD".equals(m.getName()))
                .findFirst().orElseThrow();
        ExcelLibraryMember bad = members.stream().filter(m -> "BAD".equals(m.getName())).findFirst()
                .orElseThrow();
        assertNotNull(good.getColumns(), "the healthy sheet must keep its inferred columns");
        assertEquals("USUBJID", good.getColumns()[0].getName());
        assertNull(bad.getColumns(),
                "the sheet whose inference failed must carry no cached columns");
        assertEquals(0, provider.provideLibraryMemberColumns(bad).count());
    }


    /**
     * Bytes that are not an OOXML package at all must be logged and wrapped as {@code IOException}
     * by the library open, exactly as they are by the table provider — the streaming reader throws
     * a {@code RuntimeException} from {@code open()}, which is the outer catch's only input.
     */
    @Test
    void testProvideLibraryNonOoxmlBytesLogsAndWrapsAsIOException() throws Exception
    {
        File tmpFile = File.createTempFile("notxlsxlib", ".xlsx");
        tmpFile.deleteOnExit();
        Files.write(tmpFile.toPath(),
                "this is not a zip or OLE2 file at all".getBytes(StandardCharsets.UTF_8));
        ExcelLibraryProvider provider = new ExcelLibraryProvider();

        try (LoggerCapture log = LoggerCapture.attach(ExcelLibraryProvider.class.getName()))
        {
            IOException ex = assertThrows(IOException.class,
                    () -> provider.provide(tmpFile.toURI(), ExcelProviderSupplier.FI_XLSX));
            assertNotNull(ex.getCause(),
                    "the original runtime exception must be preserved as cause");
            assertFalse(log.records().isEmpty(),
                    "the runtime exception must be logged before being wrapped");
        }
    }


    /**
     * A sheet with no rows at all, and a sheet whose first row is a blank title/spacer row, must
     * both still be listed as members — with no columns rather than an aborted open — while a
     * healthy sheet in the same workbook keeps its columns. These are the two early exits of
     * {@code ExcelTableProvider.inferColumns}, and they must NOT behave like
     * {@code provide}/{@code buildMeta}, which fail the read outright: a library open that threw
     * would make every other sheet in the workbook unreachable.
     */
    @Test
    void testProvideLibraryListsEmptyAndSpacerHeaderSheetsWithoutColumns() throws Exception
    {
        File tmpFile = File.createTempFile("xlsxlibedge", ".xlsx");
        tmpFile.deleteOnExit();
        try (Workbook wb = new XSSFWorkbook())
        {
            wb.createSheet("NOROWS"); // no rows at all

            Sheet spacer = wb.createSheet("SPACER");
            spacer.createRow(0).createCell(0).setCellValue("   "); // whitespace-only title row
            Row sh = spacer.createRow(1);
            sh.createCell(0).setCellValue("NAME");
            Row sr = spacer.createRow(2);
            sr.createCell(0).setCellValue("Alice");

            Sheet good = wb.createSheet("GOOD");
            good.createRow(0).createCell(0).setCellValue("USUBJID");
            good.createRow(1).createCell(0).setCellValue("S01");

            try (FileOutputStream fos = new FileOutputStream(tmpFile))
            {
                wb.write(fos);
            }
        }

        ExcelLibraryProvider provider = new ExcelLibraryProvider();
        ExcelLibrary lib = (ExcelLibrary) provider.provide(tmpFile.toURI(),
                ExcelProviderSupplier.FI_XLSX);
        List<ExcelLibraryMember> members = lib.getMembers().toList();
        assertEquals(3, members.size());
        for (String sheet : List.of("NOROWS", "SPACER"))
        {
            ExcelLibraryMember m = members.stream().filter(x -> sheet.equals(x.getName()))
                    .findFirst().orElseThrow();
            assertNotNull(m.getColumns(),
                    sheet + ": inference succeeded, it just found no columns");
            assertEquals(0, m.getColumns().length, sheet + " must contribute no columns");
        }
        ExcelLibraryMember good = members.stream().filter(m -> "GOOD".equals(m.getName()))
                .findFirst().orElseThrow();
        assertEquals(1, good.getColumns().length);
        assertEquals("USUBJID", good.getColumns()[0].getName());
    }

}
