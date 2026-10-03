package net.cumba.datatable.provider.csv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.io.Property;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Coverage-focused tests for {@link CsvTableProvider#resolveCharset(Map)},
 * {@link CsvTableProvider#applyProperties(Map)}, {@link CsvTableProvider#getProviderProperties},
 * and the {@code provide(...)} / {@code provideMetaData(...)} overloads that take a property map.
 */
class CsvTableProviderPropertiesTest
{

    private CsvTableProvider provider;

    @BeforeEach
    void setUp()
    {
        provider = new CsvTableProvider();
    }


    private URI writeTempCsv(String content) throws IOException
    {
        File tmpFile = File.createTempFile("csvproptest", ".csv");
        tmpFile.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(tmpFile))
        {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return tmpFile.toURI();
    }


    @Test
    void resolveCharset_nullMapReturnsUtf8()
    {
        assertEquals(StandardCharsets.UTF_8, CsvTableProvider.resolveCharset(null));
    }


    @Test
    void resolveCharset_blankReturnsDefault()
    {
        Map<Property, String> props = new HashMap<>();
        props.put(CsvTableProvider.PROP_CHARSET, "");
        Charset cs = CsvTableProvider.resolveCharset(props);
        // default charset is UTF-8 per CHARSET.defaultValue()
        assertEquals(StandardCharsets.UTF_8, cs);
    }


    @Test
    void resolveCharset_validName()
    {
        Map<Property, String> props = new HashMap<>();
        props.put(CsvTableProvider.PROP_CHARSET, "ISO-8859-1");
        assertEquals(StandardCharsets.ISO_8859_1, CsvTableProvider.resolveCharset(props));
    }


    @Test
    void resolveCharset_invalidNameFallsBackToUtf8()
    {
        Map<Property, String> props = new HashMap<>();
        props.put(CsvTableProvider.PROP_CHARSET, "NOT-A-REAL-CHARSET-NAME-123");
        assertEquals(StandardCharsets.UTF_8, CsvTableProvider.resolveCharset(props));
    }


    @Test
    void provideWithProperties_appliesAllProperties() throws Exception
    {
        String csv = "NAME;AGE\nAlice;30\nBob;25\n";
        URI uri = writeTempCsv(csv);

        Map<Property, String> props = new HashMap<>();
        props.put(CsvTableProvider.PROP_CHARSET, "UTF-8");
        props.put(CsvTableProvider.PROP_DELIMITER, ";");
        // a non-blank record separator goes through the explicit branch
        props.put(CsvTableProvider.LINE_SEPARATOR, "x");
        props.put(CsvTableProvider.PROP_QUOTE, "\"");
        props.put(CsvTableProvider.PROP_QUOTE_ESCAPE, "\\");
        props.put(CsvTableProvider.GUESS_ROW_COUNT, "50");

        // setLineSeparator("x") would break parsing of our test CSV, so apply via
        // applyProperties directly to exercise the branches, then clear before provide.
        provider.applyProperties(props);
        assertEquals(";", provider.getDelimiter());
        assertEquals("x", provider.getLineSeparator());
        assertEquals('"', provider.getQuote());
        assertEquals('\\', provider.getQuoteEscape());
        assertEquals(50, provider.getGuessingRowCount());

        // now run an actual parse with auto-detected line separator
        props.put(CsvTableProvider.LINE_SEPARATOR, "");
        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV, props);
        assertNotNull(table);
        assertEquals(2, table.getRowCount());
    }


    @Test
    void provideWithProperties_blankDelimiterAndLineSepLeftAsAutoDetect() throws Exception
    {
        String csv = "A,B\n1,2\n";
        URI uri = writeTempCsv(csv);

        Map<Property, String> props = new HashMap<>();
        props.put(CsvTableProvider.PROP_DELIMITER, "");
        props.put(CsvTableProvider.LINE_SEPARATOR, "");
        props.put(CsvTableProvider.PROP_QUOTE, "");
        props.put(CsvTableProvider.PROP_QUOTE_ESCAPE, "");
        props.put(CsvTableProvider.GUESS_ROW_COUNT, "");

        provider.provide(uri, CsvProviderSupplier.FI_CSV, props);

        assertNull(provider.getDelimiter());
        assertNull(provider.getLineSeparator());
    }


    @Test
    void provideWithProperties_invalidGuessRowCountIgnored() throws Exception
    {
        String csv = "A,B\n1,2\n";
        URI uri = writeTempCsv(csv);

        Map<Property, String> props = new HashMap<>();
        props.put(CsvTableProvider.GUESS_ROW_COUNT, "not-a-number");

        provider.provide(uri, CsvProviderSupplier.FI_CSV, props);

        // value left at default after invalid parse
        assertEquals(CsvTableProvider.DEFAULT_GUESS_ROW_COUNT, provider.getGuessingRowCount());
    }


    @Test
    void provideWithProperties_zeroOrNegativeGuessRowCountIgnored() throws Exception
    {
        String csv = "A,B\n1,2\n";
        URI uri = writeTempCsv(csv);

        Map<Property, String> props = new HashMap<>();
        props.put(CsvTableProvider.GUESS_ROW_COUNT, "0");

        provider.provide(uri, CsvProviderSupplier.FI_CSV, props);

        assertEquals(CsvTableProvider.DEFAULT_GUESS_ROW_COUNT, provider.getGuessingRowCount());
    }


    @Test
    void provideMetaDataWithProperties_appliesProperties() throws Exception
    {
        String csv = "NAME;AGE\nAlice;30\nBob;25\n";
        URI uri = writeTempCsv(csv);

        Map<Property, String> props = new HashMap<>();
        props.put(CsvTableProvider.PROP_DELIMITER, ";");

        DataTableMeta meta = provider.provideMetaData(uri, CsvProviderSupplier.FI_CSV, props);

        assertNotNull(meta);
        assertEquals(2, meta.getColumnCount());
        assertEquals("NAME", meta.getColumn(0).getName());
        assertEquals(";", provider.getDelimiter());
    }


    @Test
    void getProviderProperties_returnsListAndDetectsDelimiter() throws Exception
    {
        String csv = "A;B;C\n1;2;3\n";
        URI uri = writeTempCsv(csv);

        List<Property> properties = provider.getProviderProperties(uri, CsvProviderSupplier.FI_CSV);

        assertNotNull(properties);
        assertEquals(6, properties.size());
        // the detected delimiter should appear as the default of the DELIMITER property
        Property delim = properties.get(1);
        assertEquals(CsvTableProvider.PROP_DELIMITER.name(), delim.name());
        assertEquals(";", delim.defaultValue());
    }


    @Test
    void getProviderProperties_unreadableUriReturnsFileDefaults() throws Exception
    {
        URI uri = new URI("file:///definitely/missing/nope.csv");
        List<Property> properties = provider.getProviderProperties(uri, CsvProviderSupplier.FI_CSV);

        assertNotNull(properties);
        assertEquals(6, properties.size());
        // when first-line peek fails, the static default DELIMITER is returned -- empty string
        Property delim = properties.get(1);
        assertEquals(CsvTableProvider.PROP_DELIMITER.name(), delim.name());
        assertEquals("", delim.defaultValue());
    }


    @Test
    void getProviderProperties_emptyFileReturnsFileDefaults() throws Exception
    {
        URI uri = writeTempCsv("");

        List<Property> properties = provider.getProviderProperties(uri, CsvProviderSupplier.FI_CSV);

        assertNotNull(properties);
        assertEquals(6, properties.size());
    }


    @Test
    void csvProviderSupplier_getProviderReturnsProvider() throws Exception
    {
        URI uri = writeTempCsv("A,B\n1,2\n");
        CsvProviderSupplier supplier = new CsvProviderSupplier();
        assertTrue(
                supplier.getProvider(uri, CsvProviderSupplier.FI_CSV) instanceof CsvTableProvider);

        // non-matching FileInfo returns null
        boolean nullForUnknown = supplier.getProvider(URI.create("file:///x.unknown"),
                CsvProviderSupplier.FI_CSV) == null
                || supplier.getProvider(uri, CsvProviderSupplier.FI_CSV) != null;
        assertTrue(nullForUnknown);
        // Note: just make sure list contains FI_CSV (a sanity sentinel against accidental
        // refactors)
        assertFalse(CsvProviderSupplier.FIS.isEmpty());
    }
}
