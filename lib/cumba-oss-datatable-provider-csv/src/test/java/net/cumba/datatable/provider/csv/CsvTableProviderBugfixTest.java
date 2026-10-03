package net.cumba.datatable.provider.csv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.io.Property;
import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for defects found and fixed during the clinical-path mutation-hardening pass
 * (see the campaign findings ledger). Each test would have FAILED against the pre-fix code — they
 * pin the corrected behaviour, not the bug.
 */
class CsvTableProviderBugfixTest
{

    private CsvTableProvider provider;

    @BeforeEach
    void setUp()
    {
        provider = new CsvTableProvider();
    }


    private URI writeTempCsv(String content) throws IOException
    {
        File tmpFile = File.createTempFile("csvbugfix", ".csv");
        tmpFile.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(tmpFile))
        {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return tmpFile.toURI();
    }


    /**
     * X1 (HIGH): a column that is present-but-EMPTY in every sampled row used to be typed DOUBLE on
     * zero real evidence (mere presence was enough), silently destroying any text that showed up
     * after the sample -- addData2Column parsed it as a double, got NaN, and stored
     * MissingValue.MIS with no warning. Fixed: only a genuinely non-blank, non-"." cell counts as
     * numeric evidence; a column with none stays STRING, exactly like a column no sampled row
     * reaches at all.
     */
    @Test
    void presentButEmptyColumnStaysCharacterAndKeepsLaterText() throws Exception
    {
        String csv = "STUDYID,USUBJID,COMMENT\nS1,SUBJ-1,\nS1,SUBJ-2,\nS1,SUBJ-3,withdrew consent\n";
        URI uri = writeTempCsv(csv);
        provider.setGuessingRowCount(2);

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertSame(DataValueType.STRING, table.getMetaData().getColumn(2).getType(),
                "an all-empty sample must not be read as numeric evidence");
        assertEquals("withdrew consent", table.getValue(2, 2),
                "text appearing after the sample must survive, not be destroyed as NaN->missing");
    }


    /**
     * X2 (MED-HIGH): {@code CDT.isBlankOrNull} delegates to {@code String.isBlank()}, which is true
     * for a tab character -- so an explicitly configured tab delimiter was silently discarded and
     * auto-detection ran instead. Fixed: only a truly empty string means "auto-detect".
     */
    @Test
    void explicitTabDelimiterIsHonoured() throws Exception
    {
        String csv = "NAME\tVALUE\nAlice\t1.5\n";
        URI uri = writeTempCsv(csv);

        Map<Property, String> props = new HashMap<>();
        props.put(CsvTableProvider.PROP_DELIMITER, "\t");

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV, props);

        assertEquals("\t", provider.getDelimiter(),
                "a tab is not blank -- it must be kept as the explicit delimiter, not reset to auto-detect");
        assertEquals(2, table.getColumnCount());
        assertEquals("Alice", table.getValue(0, 0));
        assertEquals(1.5, ((Number) table.getValue(0, 1)).doubleValue(), 1e-9);
    }


    /**
     * X2's other half: an explicit {@code "\n"} line separator is the one value a user would
     * naturally configure for "Unix line endings", and it must not be discarded as blank either.
     */
    @Test
    void explicitNewlineLineSeparatorIsHonoured() throws Exception
    {
        String csv = "A,B\n1,2\n3,4\n";
        URI uri = writeTempCsv(csv);

        Map<Property, String> props = new HashMap<>();
        props.put(CsvTableProvider.LINE_SEPARATOR, "\n");

        provider.provide(uri, CsvProviderSupplier.FI_CSV, props);

        assertEquals("\n", provider.getLineSeparator(),
                "a newline is not blank -- it must be kept as the explicit line separator");
    }


    /**
     * X3 (MED): two columns sharing a header name used to make {@code addColumn} throw an
     * undocumented {@code IllegalStateException} instead of the {@code IOException} the provider
     * contract promises. Fixed: a colliding name (real-vs-real, or real-vs-fallback) is
     * disambiguated with a numeric suffix instead of causing a crash.
     */
    @Test
    void duplicateHeaderNamesAreDisambiguatedInsteadOfCrashing() throws Exception
    {
        String csv = "NAME,NAME\nAlice,Bob\n";
        URI uri = writeTempCsv(csv);

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertEquals(2, table.getColumnCount());
        assertEquals("NAME", table.getMetaData().getColumn(0).getName());
        assertEquals("NAME_2", table.getMetaData().getColumn(1).getName());
        assertEquals("Alice", table.getValue(0, 0));
        assertEquals("Bob", table.getValue(0, 1));
    }


    /**
     * X3, the collision half nobody would think to check: a real column named exactly like the
     * BLANK-header fallback ("V2") for a later column must not collide either.
     */
    @Test
    void realColumnNamedLikeAFallbackDoesNotCollideWithTheFallback() throws Exception
    {
        String csv = "V2,,X\n1,2,3\n";
        URI uri = writeTempCsv(csv);

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertEquals(3, table.getColumnCount());
        assertEquals("V2", table.getMetaData().getColumn(0).getName());
        assertEquals("V2_2", table.getMetaData().getColumn(1).getName(),
                "the blank column's fallback name collided with the real column named 'V2'");
        assertEquals("X", table.getMetaData().getColumn(2).getName());
    }


    /**
     * X4 (MED): univocity's default 4096-chars-per-field and 512-column caps are comfortably
     * exceeded by real clinical exports and used to fail the whole load with an unchecked
     * {@code TextParsingException}. Fixed: the per-field cap is disabled and the column cap raised.
     */
    @Test
    void aLongFreeTextFieldDoesNotHitUnivocitysDefaultCharacterCap() throws Exception
    {
        String longValue = "x".repeat(5_000);
        String csv = "ID,COMMENT\n1," + longValue + "\n";
        URI uri = writeTempCsv(csv);

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertEquals(longValue, table.getValue(0, 1));
    }


    @Test
    void aWideExportDoesNotHitUnivocitysDefaultColumnCap() throws Exception
    {
        int columnCount = 600;
        StringBuilder header = new StringBuilder();
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < columnCount; i++)
        {
            if (i > 0)
            {
                header.append(',');
                row.append(',');
            }
            header.append("COL").append(i);
            row.append(i);
        }
        URI uri = writeTempCsv(header + "\n" + row + "\n");

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertNotNull(table);
        assertEquals(columnCount, table.getColumnCount());
    }
}
