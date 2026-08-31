package net.cumba.datatable.provider.csv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.MissingValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CsvTableProvider}.
 */
class CsvTableProviderTest
{

    private CsvTableProvider provider;

    @BeforeEach
    void setUp()
    {
        provider = new CsvTableProvider();
    }


    private URI writeTempCsv(String content) throws IOException
    {
        File tmpFile = File.createTempFile("csvtest", ".csv");
        tmpFile.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(tmpFile))
        {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return tmpFile.toURI();
    }


    @Test
    void testGetSupportedFileInfos()
    {
        List<FileInfo> fis = provider.getSupportedFileInfos();
        assertNotNull(fis);
        assertEquals(1, fis.size());
        assertSame(CsvProviderSupplier.FIS, fis);
    }


    @Test
    void testDefaultGuessingRowCount()
    {
        assertEquals(CsvTableProvider.DEFAULT_GUESS_ROW_COUNT, provider.getGuessingRowCount());
    }


    @Test
    void testSetGuessingRowCount()
    {
        provider.setGuessingRowCount(100);
        assertEquals(100, provider.getGuessingRowCount());
    }


    @Test
    void testProvideCommaDelimited() throws Exception
    {
        String csv = "NAME,AGE,SCORE\nAlice,30,95.5\nBob,25,87.3\n";
        URI uri = writeTempCsv(csv);

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertNotNull(table);
        assertEquals(2, table.getRowCount());
        assertEquals(3, table.getColumnCount());
        assertEquals("Alice", table.getValue(0, 0));
        assertEquals("Bob", table.getValue(1, 0));
    }


    @Test
    void testProvideSemicolonDelimited() throws Exception
    {
        String csv = "NAME;VALUE;CODE\nX;1.0;A\nY;2.0;B\n";
        URI uri = writeTempCsv(csv);

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertNotNull(table);
        assertEquals(2, table.getRowCount());
        assertEquals(3, table.getColumnCount());
        assertEquals("X", table.getValue(0, 0));
    }


    @Test
    void testProvideTabDelimited() throws Exception
    {
        String csv = "NAME\tVALUE\nAlpha\t1.0\nBeta\t2.0\n";
        URI uri = writeTempCsv(csv);

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertNotNull(table);
        assertEquals(2, table.getRowCount());
        assertEquals(2, table.getColumnCount());
    }


    @Test
    void testProvideDetectsDoubleColumns() throws Exception
    {
        String csv = "NAME,VALUE\nA,1.5\nB,2.7\nC,3.14\n";
        URI uri = writeTempCsv(csv);

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertEquals(DataValueType.STRING, table.getMetaData().getColumn(0).getType());
        assertEquals(DataValueType.DOUBLE, table.getMetaData().getColumn(1).getType());
    }


    @Test
    void testProvideDetectsStringColumns() throws Exception
    {
        String csv = "A,B\nfoo,1\nbar,baz\n";
        URI uri = writeTempCsv(csv);

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertEquals(DataValueType.STRING, table.getMetaData().getColumn(0).getType());
        assertEquals(DataValueType.STRING, table.getMetaData().getColumn(1).getType());
    }


    @Test
    void testProvideDoubleValues() throws Exception
    {
        String csv = "VAL\n1.5\n2.7\n";
        URI uri = writeTempCsv(csv);

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertEquals(1.5, (double) table.getValue(0, 0), 0.001);
        assertEquals(2.7, (double) table.getValue(1, 0), 0.001);
    }


    @Test
    void testProvideDoubleMissingValuesDot() throws Exception
    {
        String csv = "VAL\n1.0\n.\n3.0\n";
        URI uri = writeTempCsv(csv);

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertEquals(1.0, (double) table.getValue(0, 0), 0.001);
        assertInstanceOf(MissingValue.class, table.getValue(1, 0));
        assertEquals(3.0, (double) table.getValue(2, 0), 0.001);
    }


    @Test
    void testProvideDoubleMissingValuesEmpty() throws Exception
    {
        // Use a two-column CSV so the empty cell doesn't look like an empty line to the parser
        String csv = "VAL,OTHER\n1.0,a\n,b\n3.0,c\n";
        URI uri = writeTempCsv(csv);

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertEquals(3, table.getRowCount());
        assertInstanceOf(MissingValue.class, table.getValue(1, 0));
    }


    @Test
    void testProvideHeaderOnly() throws Exception
    {
        String csv = "COL1,COL2,COL3\n";
        URI uri = writeTempCsv(csv);

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertNotNull(table);
        assertEquals(0, table.getRowCount());
        assertEquals(3, table.getColumnCount());
    }


    @Test
    void testProvideSingleColumn() throws Exception
    {
        String csv = "NAME\nAlice\nBob\n";
        URI uri = writeTempCsv(csv);

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertNotNull(table);
        assertEquals(2, table.getRowCount());
        assertEquals(1, table.getColumnCount());
        assertEquals("Alice", table.getValue(0, 0));
    }


    /**
     * F-B6: an empty CSV file (no header row at all) must surface as IOException with a descriptive
     * message, not as an NPE deep inside {@code CsvRecord}.
     */
    @Test
    void testProvideEmptyFileThrowsIOException() throws Exception
    {
        URI uri = writeTempCsv("");

        IOException ex = assertThrows(IOException.class,
                () -> provider.provide(uri, CsvProviderSupplier.FI_CSV));
        assertTrue(ex.getMessage().toLowerCase(java.util.Locale.ROOT).contains("empty"),
                "exception message must mention emptiness: " + ex.getMessage());
    }


    @Test
    void testProvideInvalidURIThrows() throws Exception
    {
        URI uri = new URI("file:///nonexistent/path/file.csv");
        assertThrows(Exception.class, () -> provider.provide(uri, CsvProviderSupplier.FI_CSV));
    }


    /**
     * Fix #161: a ragged row (fewer fields than the header declares) must resolve its absent cells
     * the same way every other loader resolves a blank cell — <em>by column type</em>. A character
     * column yields {@code ""}, a numeric column yields a {@link MissingValue}. Before Fix #161
     * this site handed out {@code MissingValue.MIS} regardless of type, so the very same blank cell
     * reported differently depending only on whether its row happened to be short.
     * <p>
     * The fixture is built so that <em>only</em> the ragged path can produce the two cells under
     * test: row 2 carries a single field, so column indices 1 and 2 are out of range for it and the
     * ordinary (non-ragged) branch is unreachable for them. {@code guessingRowCount} is lowered to
     * 2 so that type detection samples only the two full rows — a ragged row inside the sampling
     * window makes {@code CsvRecord.getValue} throw, which forces every affected column to STRING
     * and would leave the numeric half of this assertion untestable.
     */
    @Test
    void testProvideShortRowsResolveByColumnType() throws Exception
    {
        // rows 0 and 1 are full width and fix the types; row 2 is ragged.
        String csv = "KEEPCOL,CHARCOL,NUMCOL\nx,A,1\ny,B,2\nz\n";
        URI uri = writeTempCsv(csv);

        provider.setGuessingRowCount(2);
        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertNotNull(table);
        assertEquals(3, table.getRowCount());
        assertEquals(DataValueType.STRING, table.getMetaData().getColumn(1).getType());
        assertEquals(DataValueType.DOUBLE, table.getMetaData().getColumn(2).getType());

        // The character column keeps the house contract: blank, but NOT missing.
        assertEquals("", table.getValue(2, 1));
        assertFalse(table.getDataValue(2, 1).isMissingOrInvalid(),
                "a blank CHARACTER cell must never be missing, whatever the file format");

        // The numeric column is unchanged by Fix #161: still missing.
        assertTrue(table.getDataValue(2, 2).isMissingOrInvalid(),
                "a blank NUMERIC cell stays missing");
        assertInstanceOf(MissingValue.class, table.getValue(2, 2));
    }


    @Test
    void testProvideQuotedFields() throws Exception
    {
        String csv = "NAME,DESC\n\"Alice\",\"Has a, comma\"\n\"Bob\",\"Normal\"\n";
        URI uri = writeTempCsv(csv);

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertEquals(2, table.getRowCount());
        assertEquals("Alice", table.getValue(0, 0));
        assertEquals("Has a, comma", table.getValue(0, 1));
    }


    @Test
    void testProvideWithManyRows() throws Exception
    {
        StringBuilder sb = new StringBuilder("NAME,VAL\n");
        for (int i = 0; i < 100; i++)
        {
            sb.append("Row").append(i).append(",").append(i * 0.5).append("\n");
        }
        URI uri = writeTempCsv(sb.toString());

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertEquals(100, table.getRowCount());
        assertEquals("Row0", table.getValue(0, 0));
        assertEquals("Row99", table.getValue(99, 0));
        assertEquals(0.0, (double) table.getValue(0, 1), 0.001);
        assertEquals(49.5, (double) table.getValue(99, 1), 0.001);
    }


    @Test
    void testProvidePreservesColumnNames() throws Exception
    {
        String csv = "USUBJID,AVAL,VISIT\nS01,1.0,V1\n";
        URI uri = writeTempCsv(csv);

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertEquals("USUBJID", table.getMetaData().getColumn(0).getName());
        assertEquals("AVAL", table.getMetaData().getColumn(1).getName());
        assertEquals("VISIT", table.getMetaData().getColumn(2).getName());
    }


    @Test
    void testProvideQuotedHeaderDelimiterDetection() throws Exception
    {
        // Header has commas inside quotes and semicolons as actual delimiters
        String csv = "\"Name,Full\";Age;City\nAlice;30;NYC\n";
        URI uri = writeTempCsv(csv);

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertEquals(3, table.getColumnCount());
        assertEquals("Name,Full", table.getMetaData().getColumn(0).getName());
    }


    /**
     * A blank (empty / whitespace-only) header cell must not propagate as a blank column name,
     * since {@code DataTableColumnMeta.name} is {@code @NonNull} and a blank name has historically
     * caused exporter / wire-format breakage. The provider substitutes {@code "V" + (idx + 1)}. See
     * {@code PLAN-nonnull-column-name.md} batch 2D.
     */
    @Test
    void testProvideBlankHeaderUsesFallbackName() throws Exception
    {
        // Middle header cell is empty between two valid headers
        String csv = "NAME,,SCORE\nAlice,1,95.5\nBob,2,87.3\n";
        URI uri = writeTempCsv(csv);

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertEquals(3, table.getColumnCount());
        assertEquals("NAME", table.getMetaData().getColumn(0).getName());
        assertEquals("V2", table.getMetaData().getColumn(1).getName());
        assertEquals("SCORE", table.getMetaData().getColumn(2).getName());
    }


    /**
     * The metadata-only path goes through a separate header-handling block. Verify the same
     * fallback applies there.
     */
    @Test
    void testProvideMetaDataBlankHeaderUsesFallbackName() throws Exception
    {
        String csv = "NAME,,SCORE\nAlice,1,95.5\n";
        URI uri = writeTempCsv(csv);

        var meta = provider.provideMetaData(uri, CsvProviderSupplier.FI_CSV);

        assertEquals(3, meta.getColumnCount());
        assertEquals("NAME", meta.getColumn(0).getName());
        assertEquals("V2", meta.getColumn(1).getName());
        assertEquals("SCORE", meta.getColumn(2).getName());
    }


    /**
     * F-B6: the metadata-only path must also raise IOException for an empty file.
     */
    @Test
    void testProvideMetaDataEmptyFileThrowsIOException() throws Exception
    {
        URI uri = writeTempCsv("");

        IOException ex = assertThrows(IOException.class,
                () -> provider.provideMetaData(uri, CsvProviderSupplier.FI_CSV));
        assertTrue(ex.getMessage().toLowerCase(java.util.Locale.ROOT).contains("empty"),
                "exception message must mention emptiness: " + ex.getMessage());
    }


    /**
     * String cells must reach the column right-trimmed, like every other provider (XPT, SAS7BDAT,
     * Parquet, XLSX, Dataset-JSON). The CSV parser only drops trailing whitespace on
     * <i>unquoted</i> fields, so a quoted field is the case that reaches the engine padded — and
     * the engine compares and groups string values verbatim, so {@code "Alice "} would not equal
     * {@code "Alice"}.
     */
    @Test
    void testProvideStringValuesAreRightTrimmed() throws Exception
    {
        String csv = "NAME,CITY\n\"Alice   \",\"Berlin\"\n\"Alice\",\"Berlin\t\"\n";
        URI uri = writeTempCsv(csv);

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertEquals("Alice", table.getValue(0, 0));
        assertEquals("Alice", table.getValue(1, 0));
        assertEquals("Berlin", table.getValue(0, 1));
        assertEquals("Berlin", table.getValue(1, 1));
    }


    /**
     * A non-breaking space counts as trailing whitespace for {@code CDT.trimRight}, but not for the
     * CSV parser (which only strips characters {@code <= ' '}). Without the provider-side trim it
     * would survive into the column.
     */
    @Test
    void testProvideStringValuesTrimTrailingNbsp() throws Exception
    {
        String csv = "NAME,CITY\n\"Alice\u00A0\",\"Berlin\"\n";
        URI uri = writeTempCsv(csv);

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertEquals("Alice", table.getValue(0, 0));
    }


    /**
     * Leading whitespace is data and must be preserved — {@code CORE-000867} flags text variables
     * with leading spaces, so a provider-side left-trim would silently hide findings.
     */
    @Test
    void testProvideStringValuesKeepLeadingWhitespace() throws Exception
    {
        String csv = "NAME,CITY\n\"   Alice   \",\"Berlin\"\n";
        URI uri = writeTempCsv(csv);

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertEquals("   Alice", table.getValue(0, 0));
    }

}
