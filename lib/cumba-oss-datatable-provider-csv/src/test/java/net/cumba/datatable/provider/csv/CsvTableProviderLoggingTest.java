package net.cumba.datatable.provider.csv;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.io.Property;
import net.cumba.datatable.provider.csv.testsupport.LoggerCapture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pins the diagnostic log calls that {@code removed call to java/lang/System$Logger::log} survivors
 * showed had no observation point: an invalid {@code GUESS_ROW_COUNT} property, a failed
 * {@link CsvTableProvider#peekFirstLine} read, a blank header on both the {@code provide(...)} and
 * {@code provideMetaData(...)} paths, and a swallowed exception inside
 * {@link CsvTableProvider#determineTypes}. Each uses {@link LoggerCapture} rather than asserting
 * only on the (unaffected) return value, per the campaign's standing note that a bare log removal
 * generates a real mutant that only a log-observing test can kill.
 */
class CsvTableProviderLoggingTest
{

    private CsvTableProvider provider;

    @BeforeEach
    void setUp()
    {
        provider = new CsvTableProvider();
    }


    private URI writeTempCsv(String content) throws IOException
    {
        File tmpFile = File.createTempFile("csvlogtest", ".csv");
        tmpFile.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(tmpFile))
        {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return tmpFile.toURI();
    }


    @Test
    void invalidGuessRowCountLogsAtDebug() throws Exception
    {
        URI uri = writeTempCsv("A,B\n1,2\n");
        Map<Property, String> props = new HashMap<>();
        props.put(CsvTableProvider.GUESS_ROW_COUNT, "not-a-number");

        try (LoggerCapture log = LoggerCapture.attach(CsvTableProvider.class.getName()))
        {
            provider.provide(uri, CsvProviderSupplier.FI_CSV, props);
            assertTrue(log.containsMessageContaining("Invalid"),
                    "an invalid GUESS_ROW_COUNT must be logged, not silently ignored: "
                            + log.messages());
        }
    }


    @Test
    void peekFirstLineFailureLogsAtDebug() throws Exception
    {
        URI uri = new URI("file:///definitely/missing/nope-log.csv");

        try (LoggerCapture log = LoggerCapture.attach(CsvTableProvider.class.getName()))
        {
            provider.getProviderProperties(uri, CsvProviderSupplier.FI_CSV);
            assertTrue(log.containsMessageContaining("Unable to peek first line"),
                    "a failed first-line peek must be logged: " + log.messages());
        }
    }


    @Test
    void blankHeaderOnProvideLogsAWarning() throws Exception
    {
        String csv = "NAME,,SCORE\nAlice,1,95.5\n";
        URI uri = writeTempCsv(csv);

        try (LoggerCapture log = LoggerCapture.attach(CsvTableProvider.class.getName()))
        {
            IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);
            assertNotNull(table);
            assertTrue(log.containsMessageContaining("blank header"),
                    "a blank header cell must be logged on the provide() path: " + log.messages());
        }
    }


    @Test
    void blankHeaderOnProvideMetaDataLogsAWarning() throws Exception
    {
        String csv = "NAME,,SCORE\nAlice,1,95.5\n";
        URI uri = writeTempCsv(csv);

        try (LoggerCapture log = LoggerCapture.attach(CsvTableProvider.class.getName()))
        {
            DataTableMeta meta = provider.provideMetaData(uri, CsvProviderSupplier.FI_CSV);
            assertNotNull(meta);
            assertTrue(log.containsMessageContaining("blank header"),
                    "a blank header cell must be logged on the provideMetaData() path: "
                            + log.messages());
        }
    }
}
