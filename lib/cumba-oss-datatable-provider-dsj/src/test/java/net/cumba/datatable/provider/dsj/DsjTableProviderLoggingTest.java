package net.cumba.datatable.provider.dsj;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.cumba.cdisc.dsj.ColumnDataType;
import net.cumba.cdisc.dsj.ColumnTargetDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Asserts that the several {@code LOGGER.log(WARNING, ...)} defensive-fallback calls in
 * {@link DsjTableProvider} actually fire, rather than merely executing unobserved.
 * {@code @CustomLog} (see the project's {@code lombok.config}) backs {@code System.Logger} with
 * plain {@code java.util.logging}, so a {@link Handler} attached to the class-named JUL
 * {@link Logger} captures every record.
 */
class DsjTableProviderLoggingTest
{

    private static final String LOGGER_NAME = DsjTableProvider.class.getName();

    private Logger julLogger;

    private CapturingHandler capture;

    private static class CapturingHandler extends Handler
    {

        private final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord aRecord)
        {
            records.add(aRecord);
        }


        @Override
        public void flush()
        {
            // no-op
        }


        @Override
        public void close()
        {
            // no-op
        }
    }

    @BeforeEach
    void installHandler()
    {
        julLogger = Logger.getLogger(LOGGER_NAME);
        julLogger.setLevel(Level.ALL);
        capture = new CapturingHandler();
        julLogger.addHandler(capture);
    }


    @AfterEach
    void removeHandler()
    {
        julLogger.removeHandler(capture);
    }


    private boolean anyRecordContains(String aFragment)
    {
        return capture.records.stream()
                .anyMatch(r -> r.getMessage() != null && r.getMessage().contains(aFragment));
    }


    private static URI writeJson(Path aDir, String aName, String aJson) throws IOException
    {
        Path f = aDir.resolve(aName);
        Files.writeString(f, aJson, StandardCharsets.UTF_8);
        return f.toUri();
    }

    // ---- getColumnDataTypeFor: unexpected dataType string (line ~682) ----------------------


    @Test
    void unexpectedDataTypeStringLogsAWarning()
    {
        DsjTableProvider provider = new DsjTableProvider();
        ColumnDataType result = provider.getColumnDataTypeFor("not-a-real-type");

        assertTrue(result == ColumnDataType.OTHER);
        assertTrue(anyRecordContains("Found unexpected data type: not-a-real-type"));
    }

    // ---- getColumnTargetDataTypeFor: unexpected targetDataType string (line ~708) ----------


    @Test
    void unexpectedTargetDataTypeStringLogsAWarning()
    {
        DsjTableProvider provider = new DsjTableProvider();
        ColumnTargetDataType result = provider.getColumnTargetDataTypeFor("not-a-real-target");

        assertTrue(result == ColumnTargetDataType.OTHER);
        assertTrue(anyRecordContains("Found unexpected target data type: not-a-real-target"));
    }

    // ---- getTypeFor: OTHER targetDataType falls through to a WARNING (line ~798) -----------


    @Test
    void unexpectedTargetDataTypeInADocumentLogsAWarningFromGetTypeFor(@TempDir Path tmp)
        throws IOException
    {
        String json = """
                {
                  "datasetJSONCreationDateTime": "2026-09-08T10:00:00",
                  "datasetJSONVersion": "1.1.0",
                  "itemGroupOID": "IG.BOGUS",
                  "name": "BOGUS",
                  "label": "Bogus targetDataType",
                  "records": 1,
                  "columns": [
                    {"itemOID": "IT.A", "name": "A", "label": "A", "dataType": "double",
                     "targetDataType": "not-a-real-target"}
                  ],
                  "rows": [[1.5]]
                }
                """;
        URI uri = writeJson(tmp, "bogustarget.json", json);

        new DsjTableProvider().provideMetaData(uri, DsjProviderSupplier.FI_DSJ_JSON);

        // Two independent WARNINGs fire for this one document: getColumnTargetDataTypeFor's own
        // fallback (line ~708, logs the raw STRING "not-a-real-target") and getTypeFor's separate
        // OTHER-branch fallback (line ~798, logs the resolved ENUM value "OTHER" -- a different
        // message text). Both must be asserted independently, or a mutant removing just the
        // second call is invisible behind the first.
        assertTrue(anyRecordContains("Found unexpected target data type: not-a-real-target"),
                "getColumnTargetDataTypeFor's own fallback must have logged the raw string");
        assertTrue(anyRecordContains("Found unexpected target data type: OTHER"),
                "getTypeFor's OTHER-branch fallback must have logged the resolved enum value");
    }

    // ---- addData2Column: the unexpected-VALUE branch (line ~939) --------------------------


    @Test
    void nestedObjectRowValueLogsAWarningFromAddData2Column(@TempDir Path tmp) throws IOException
    {
        String json = """
                {
                  "datasetJSONCreationDateTime": "2026-09-08T10:00:00",
                  "datasetJSONVersion": "1.1.0",
                  "itemGroupOID": "IG.NESTLOG",
                  "name": "NESTLOG",
                  "label": "Nested value logging",
                  "records": 1,
                  "columns": [
                    {"itemOID": "IT.A", "name": "A", "label": "A", "dataType": "double"}
                  ],
                  "rows": [
                    [{"nested": 1}]
                  ]
                }
                """;
        URI uri = writeJson(tmp, "nestedlog.json", json);

        new DsjTableProvider().provide(uri, DsjProviderSupplier.FI_DSJ_JSON);

        // This call uses the log(Level, String, Object...) placeholder overload, so
        // LogRecord.getMessage() carries the raw "{0}"-style pattern, not the substituted text --
        // match on the literal pattern text rather than assuming it was already formatted.
        assertTrue(anyRecordContains("Unexpected value for row={0} column={1}"));
    }

}
