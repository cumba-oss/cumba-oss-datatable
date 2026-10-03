package net.cumba.datatable.provider.csv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.provider.DataTableMetaSupport;
import net.cumba.datatable.io.Property;
import net.cumba.datatable.library.ILibraryMember;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.MissingValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Targeted kills for the census's remaining SURVIVED / NO_COVERAGE mutants that
 * {@link CsvTableProviderTest}, {@link CsvTableProviderPropertiesTest},
 * {@link CsvTableProviderMetaDataTest} and {@link CsvTableProviderLoggingTest} did not already
 * exercise precisely enough to pin.
 */
class CsvTableProviderSurvivorTest
{

    private CsvTableProvider provider;

    @BeforeEach
    void setUp()
    {
        provider = new CsvTableProvider();
    }


    private URI writeTempCsv(String content) throws IOException
    {
        File tmpFile = File.createTempFile("csvsurvtest", ".csv");
        tmpFile.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(tmpFile))
        {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return tmpFile.toURI();
    }

    // --- applyProperties: setCharset / setQuote must actually take effect, not merely agree
    // with the field default (PROP_CHARSET="UTF-8"/PROP_QUOTE="\"" match the class defaults, so a
    // test using only defaults cannot tell a removed call from a working one). ---


    @Test
    void applyProperties_nonDefaultCharsetIsActuallyUsedToDecode() throws Exception
    {
        // 0xE9 is 'é' in ISO-8859-1/windows-1252 but is not valid standalone UTF-8, so decoding
        // with the wrong charset produces a different (replacement/garbled) character.
        byte[] bytes =
        {
                'A', ',', 'B', '\n', (byte) 0xE9, ',', '2', '\n'
        };
        File tmpFile = File.createTempFile("csvcharset", ".csv");
        tmpFile.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(tmpFile))
        {
            fos.write(bytes);
        }

        Map<Property, String> props = new HashMap<>();
        props.put(CsvTableProvider.PROP_CHARSET, "windows-1252");

        IDataTable table = provider.provide(tmpFile.toURI(), CsvProviderSupplier.FI_CSV, props);

        assertEquals("é", table.getValue(0, 0),
                "the configured charset must actually be applied to decode the byte, not the UTF-8 default");
    }


    @Test
    void applyProperties_nonDefaultQuoteIsActuallyUsed() throws Exception
    {
        // Single-quote as the quote character; the comma inside it must stay part of one field.
        String csv = "A,B\n'x,y',z\n";
        URI uri = writeTempCsv(csv);

        Map<Property, String> props = new HashMap<>();
        props.put(CsvTableProvider.PROP_QUOTE, "'");

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV, props);

        assertEquals(2, table.getColumnCount(),
                "with quote=' the quoted comma must not split the field");
        assertEquals("x,y", table.getValue(0, 0));
        assertEquals("z", table.getValue(0, 1));
    }


    @Test
    void applyProperties_nonDefaultQuoteEscapeIsActuallyUsed() throws Exception
    {
        // Backslash as the escape character instead of the default doubled-quote.
        String csv = "A,B\n\"she said \\\"hi\\\"\",z\n";
        URI uri = writeTempCsv(csv);

        Map<Property, String> props = new HashMap<>();
        props.put(CsvTableProvider.PROP_QUOTE_ESCAPE, "\\");

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV, props);

        assertEquals("she said \"hi\"", table.getValue(0, 0),
                "with a backslash quote-escape configured, \\\" must decode to a literal quote"
                        + " while staying inside the field");
        assertEquals("z", table.getValue(0, 1));
    }

    // --- buildParserSettings: the explicit LINE_SEPARATOR branch, and CRLF via detection. ---


    @Test
    void explicitLineSeparatorPropertyIsHonoured() throws Exception
    {
        // univocity's line separator accepts at most 2 characters.
        String csv = "A,B~1,2~3,4~";
        URI uri = writeTempCsv(csv);

        Map<Property, String> props = new HashMap<>();
        props.put(CsvTableProvider.LINE_SEPARATOR, "~");

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV, props);

        assertEquals(2, table.getRowCount());
        assertEquals(2.0, ((Number) table.getValue(0, 1)).doubleValue(), 1e-9);
        assertEquals(4.0, ((Number) table.getValue(1, 1)).doubleValue(), 1e-9);
    }


    @Test
    void crlfTerminatedFileParsesCleanlyViaAutoDetection() throws Exception
    {
        String csv = "NAME,VAL\r\nAlice,1.5\r\nBob,2.5\r\n";
        URI uri = writeTempCsv(csv);

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertEquals(2, table.getRowCount());
        assertEquals("Alice", table.getValue(0, 0));
        assertEquals("Bob", table.getValue(1, 0));
        assertEquals(1.5, ((Number) table.getValue(0, 1)).doubleValue(), 1e-9);
        assertEquals(2.5, ((Number) table.getValue(1, 1)).doubleValue(), 1e-9);
    }


    /**
     * A plain CRLF-terminated file parses identically whether or not
     * {@code setLineSeparatorDetectionEnabled(true)} was called, and does not by itself kill that
     * mutant -- univocity's raw reader already recognises the embedded {@code \n} of a CRLF pair
     * regardless. A LONE-CR file is what actually depends on it: measured directly, removing the
     * call makes the parser's line separator default to the JVM's system separator ({@code \n} on
     * Linux, which this box runs), so a bare {@code \r} is never recognised as ANY kind of break --
     * not between records, and not around the quoted field's own embedded {@code \r}. The whole
     * three-line file then collapses into a single line, which becomes the ONE header row: zero
     * data rows, and the quote/comma parsing does still run, but on garbled column names.
     */
    @Test
    void loneCrFileParsesCorrectlyOnlyWithLineSeparatorDetectionEnabled() throws Exception
    {
        String csv = "A,B\r\"line1\rline2\",z\r";
        URI uri = writeTempCsv(csv);

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertEquals(1, table.getRowCount(),
                "the lone-CR terminators must be recognised as breaks");
        assertEquals(2, table.getColumnCount());
        assertEquals("A", table.getMetaData().getColumn(0).getName());
        assertEquals("B", table.getMetaData().getColumn(1).getName());
        assertEquals("line1\nline2", table.getValue(0, 0),
                "the embedded lone-CR break inside the quoted field must read back as a plain \\n");
        assertEquals("z", table.getValue(0, 1));
    }


    /**
     * buildParserSettings' delimiter branch: an EXPLICITLY configured delimiter must be used even
     * when the header would auto-detect to a different character. Pipe is configured; the header
     * also contains an unquoted comma that findDelimChar would otherwise prefer.
     */
    @Test
    void explicitDelimiterWinsOverWhatAutoDetectionWouldHavePicked() throws Exception
    {
        String csv = "A|B,C\n1|2,3\n";
        URI uri = writeTempCsv(csv);

        Map<Property, String> props = new HashMap<>();
        props.put(CsvTableProvider.PROP_DELIMITER, "|");

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV, props);

        assertEquals(2, table.getColumnCount(),
                "the explicit pipe delimiter must be used, not the comma auto-detection would prefer");
        assertEquals("A", table.getMetaData().getColumn(0).getName());
        assertEquals("B,C", table.getMetaData().getColumn(1).getName());
        assertEquals("2,3", table.getValue(0, 1));
    }

    // --- findDelimChar: quote-tracking and the comma/tab/semicolon tie-break preference. ---


    @Test
    void findDelimChar_quotedDelimitersAreNotCountedEvenWhenTheyOutnumberTheRealOne()
        throws Exception
    {
        // Three commas live inside the quoted field; the two real delimiters are semicolons. If
        // quote-tracking is broken (the `inQuotes = !inQuotes` toggle turned into a no-op),
        // every comma gets counted too (3 >= 2) and comma wins instead of semicolon.
        URI uri = writeTempCsv("\"a,b,c,d\";X;Y\n\"1,2,3,4\";9;9\n");

        List<Property> properties = provider.getProviderProperties(uri, CsvProviderSupplier.FI_CSV);
        Property delim = properties.get(1);
        assertEquals(";", delim.defaultValue(),
                "commas inside the quoted field must not be counted as delimiters");

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);
        assertEquals(3, table.getColumnCount());
        assertEquals("a,b,c,d", table.getMetaData().getColumn(0).getName());
    }


    @Test
    void findDelimChar_commaWinsATrueThreeWayTie() throws Exception
    {
        // Exactly one comma, one semicolon and one tab: comma must win the tie by preference.
        URI uri = writeTempCsv("A,B;C\tD\n");

        List<Property> properties = provider.getProviderProperties(uri, CsvProviderSupplier.FI_CSV);
        Property delim = properties.get(1);
        assertEquals(",", delim.defaultValue(), "comma must win a three-way tie");
    }


    @Test
    void findDelimChar_tabWinsOverSemicolonOnATie() throws Exception
    {
        // No commas; tab and semicolon tie 1-1. Tab must win (preferred over semicolon).
        URI uri = writeTempCsv("A;B\tC\n");

        List<Property> properties = provider.getProviderProperties(uri, CsvProviderSupplier.FI_CSV);
        Property delim = properties.get(1);
        assertEquals("\t", delim.defaultValue(), "tab must win its tie with semicolon");
    }

    // --- provide()/provideMetaData(): the guessingRowCount sampling boundary. A boundary mutant
    // (>= -> >) samples one row too many, so a non-numeric row placed exactly one past the sample
    // window must NOT influence the detected type. ---


    @Test
    void provideSamplesExactlyGuessingRowCountRows() throws Exception
    {
        String csv = "VAL\n1.0\n2.0\nfoo\n3.0\n";
        URI uri = writeTempCsv(csv);
        provider.setGuessingRowCount(2);

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertSame(DataValueType.DOUBLE, table.getMetaData().getColumn(0).getType(),
                "only the first 2 rows may be sampled; the non-numeric 3rd row must not flip the type");
        assertInstanceOf(MissingValue.class, table.getValue(2, 0),
                "the un-parseable 3rd row must read back as missing, not retype the column");
    }


    @Test
    void provideMetaDataSamplesExactlyGuessingRowCountRows() throws Exception
    {
        // Row 0 is blank (no numeric evidence either way); row 1 is the only real evidence. A
        // NEGATED boundary breaks the sampling loop after just ONE row (row 0 only), which would
        // see no evidence at all and wrongly stay STRING instead of the correct DOUBLE.
        String csv = "VAL,OTHER\n,a\n5.0,b\n7.0,c\n";
        URI uri = writeTempCsv(csv);
        provider.setGuessingRowCount(2);

        DataTableMeta meta = provider.provideMetaData(uri, CsvProviderSupplier.FI_CSV);

        assertSame(DataValueType.DOUBLE, meta.getColumn(0).getType(),
                "both of the first 2 rows must be sampled on the metadata-only path too");
    }


    @Test
    void provideMetaDataDoesNotSampleOneRowPastGuessingRowCount() throws Exception
    {
        // The opposite boundary direction from the test above: a BOUNDARY mutant (>= -> >)
        // samples one row too MANY. Two real-evidence numeric rows are correctly sampled and give
        // DOUBLE; a 3rd non-numeric row must not be pulled in and flip the type to STRING.
        String csv = "VAL\n1.0\n2.0\nfoo\n3.0\n";
        URI uri = writeTempCsv(csv);
        provider.setGuessingRowCount(2);

        DataTableMeta meta = provider.provideMetaData(uri, CsvProviderSupplier.FI_CSV);

        assertSame(DataValueType.DOUBLE, meta.getColumn(0).getType(),
                "the non-numeric 3rd row must not be sampled on the metadata-only path either");
    }

    // --- setFileFormat / setDatasetSize: both provide() and provideMetaData() must record these
    // table-metadata keys; PMDA-SD1142 depends on dataset_size, and extract_metadata("file_format")
    // depends on file_format. ---


    @Test
    void provideRecordsFileFormatAndDatasetSize() throws Exception
    {
        String csv = "A,B\n1,2\n";
        URI uri = writeTempCsv(csv);
        long expectedSize = new File(uri).length();

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertEquals("CSV",
                table.getMetaData().getMetaData(DataTableMetaSupport.META_KEY_FILE_FORMAT));
        assertEquals(expectedSize,
                table.getMetaData().getMetaData(DataTableMetaSupport.META_KEY_DATASET_SIZE));
    }


    @Test
    void provideMetaDataRecordsFileFormatAndDatasetSize() throws Exception
    {
        String csv = "A,B\n1,2\n";
        URI uri = writeTempCsv(csv);
        long expectedSize = new File(uri).length();

        DataTableMeta meta = provider.provideMetaData(uri, CsvProviderSupplier.FI_CSV);

        assertEquals("CSV", meta.getMetaData(DataTableMetaSupport.META_KEY_FILE_FORMAT));
        assertEquals(expectedSize, meta.getMetaData(DataTableMetaSupport.META_KEY_DATASET_SIZE));
    }

    // --- fireProgressEventOrAbort / parser.stopParsing() removal on the provide() path. ---

    // --- provide(ILibraryMember, FileInfo, Properties) / provideMetaData(ILibraryMember,
    // FileInfo, Properties): NO_COVERAGE -- no existing test calls these overloads at all. ---


    @Test
    void provideWithLibraryMemberAndPropertiesAppliesProperties() throws Exception
    {
        String csv = "NAME;AGE\nAlice;30\n";
        URI uri = writeTempCsv(csv);
        ILibraryMember member = mock(ILibraryMember.class);
        when(member.getUri()).thenReturn(uri);

        Map<Property, String> props = new HashMap<>();
        props.put(CsvTableProvider.PROP_DELIMITER, ";");

        IDataTable table = provider.provide(member, CsvProviderSupplier.FI_CSV, props);

        assertNotNull(table);
        assertEquals(1, table.getRowCount());
        assertEquals("Alice", table.getValue(0, 0));
        assertEquals(";", provider.getDelimiter(), "applyProperties must run before delegating");
    }


    @Test
    void provideMetaDataWithLibraryMemberAndPropertiesAppliesProperties() throws Exception
    {
        String csv = "NAME;AGE\nAlice;30\n";
        URI uri = writeTempCsv(csv);
        ILibraryMember member = mock(ILibraryMember.class);
        when(member.getUri()).thenReturn(uri);

        Map<Property, String> props = new HashMap<>();
        props.put(CsvTableProvider.PROP_DELIMITER, ";");

        DataTableMeta meta = provider.provideMetaData(member, CsvProviderSupplier.FI_CSV, props);

        assertNotNull(meta);
        assertEquals(2, meta.getColumnCount());
        assertEquals("NAME", meta.getColumn(0).getName());
        assertEquals(";", provider.getDelimiter(), "applyProperties must run before delegating");
    }

    // --- applyProperties: quote / quoteEscape / guessingRowCount must reset to their class
    // defaults on a REUSED provider when a later call's map omits them -- a fresh provider is
    // already at the default, so only a reused one can tell a working reset from a removed one. ---


    @Test
    void applyPropertiesResetsQuoteQuoteEscapeAndGuessRowCountOnAReusedProvider() throws Exception
    {
        Map<Property, String> first = new HashMap<>();
        first.put(CsvTableProvider.PROP_QUOTE, "'");
        first.put(CsvTableProvider.PROP_QUOTE_ESCAPE, "\\");
        first.put(CsvTableProvider.GUESS_ROW_COUNT, "50");
        provider.applyProperties(first);
        assertEquals('\'', provider.getQuote());
        assertEquals('\\', provider.getQuoteEscape());
        assertEquals(50, provider.getGuessingRowCount());

        provider.applyProperties(new HashMap<>());

        assertEquals('"', provider.getQuote(), "an omitted quote must reset to the class default");
        assertEquals('"', provider.getQuoteEscape(),
                "an omitted quote-escape must reset to the class default");
        assertEquals(CsvTableProvider.DEFAULT_GUESS_ROW_COUNT, provider.getGuessingRowCount(),
                "an omitted guess-row-count must reset to the class default");
    }

    // --- resolveColumnName: three columns sharing one name need TWO successive suffix bumps
    // (_2, then _3) -- a single collision only exercises the loop once, which is why the earlier
    // duplicate-header tests did not kill a broken suffix increment. ---


    @Test
    void tripleDuplicateHeaderNamesGetSequentialSuffixes() throws Exception
    {
        String csv = "NAME,NAME,NAME\nA,B,C\n";
        URI uri = writeTempCsv(csv);

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertEquals(3, table.getColumnCount());
        assertEquals("NAME", table.getMetaData().getColumn(0).getName());
        assertEquals("NAME_2", table.getMetaData().getColumn(1).getName());
        assertEquals("NAME_3", table.getMetaData().getColumn(2).getName());
    }

    // --- resolveColumnName: the blank-header log message's column number must be the same
    // 1-based number as the fallback name it reports alongside. ---


    @Test
    void blankHeaderLogMessageUsesTheSameOneBasedNumberAsTheFallbackName() throws Exception
    {
        String csv = "NAME,,SCORE\nAlice,1,95.5\n";
        URI uri = writeTempCsv(csv);

        try (net.cumba.datatable.provider.csv.testsupport.LoggerCapture log = net.cumba.datatable.provider.csv.testsupport.LoggerCapture
                .attach(CsvTableProvider.class.getName()))
        {
            provider.provide(uri, CsvProviderSupplier.FI_CSV);
            assertTrue(log.containsMessageContaining("CSV column 2 has blank header"),
                    "the logged column number must match the fallback name 'V2' it reports: "
                            + log.messages());
        }
    }
}
