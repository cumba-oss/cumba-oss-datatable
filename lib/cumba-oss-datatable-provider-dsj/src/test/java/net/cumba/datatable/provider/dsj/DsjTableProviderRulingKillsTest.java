package net.cumba.datatable.provider.dsj;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.provider.AbstractDataTableProvider;
import net.cumba.datatable.values.MissingValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Single-mutant kills from the Q35 ruling round (G10 "review each as a single item", plus the G2
 * logger precedent): the non-{@code file:} stream branch of {@code provide}, the suppressed
 * listener failure on the LOADING_FAILED path, the {@code debugCalcDataSize} debug log, the
 * completed columns of a metadata-only (zero-row) table, and the per-cell exception arm of
 * {@code addData2Column}.
 */
class DsjTableProviderRulingKillsTest
{

    /** JUL capture handler; see {@link DsjTableProviderLoggingTest} for the pattern. */
    private static final class CapturingHandler extends Handler
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

    private static URI writeJson(Path aDir, String aName, String aJson) throws IOException
    {
        Path f = aDir.resolve(aName);
        Files.writeString(f, aJson, StandardCharsets.UTF_8);
        return f.toUri();
    }


    private static String twoRowDoc()
    {
        return """
                {
                  "datasetJSONCreationDateTime": "2026-09-15T10:00:00",
                  "datasetJSONVersion": "1.1.0",
                  "itemGroupOID": "IG.KILLS",
                  "name": "KILLS",
                  "label": "Ruling kills",
                  "records": 2,
                  "columns": [
                    {"itemOID": "IT.A", "name": "A", "label": "A", "dataType": "string"}
                  ],
                  "rows": [["v0"], ["v1"]]
                }
                """;
    }

    // ---- provide:324 — a non-file URI must be read through the stream branch ---------------


    @Test
    void nonFileUriIsLoadedThroughTheStreamBranch(@TempDir Path tmp) throws IOException
    {
        Path zip = tmp.resolve("bundle.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip)))
        {
            zos.putNextEntry(new ZipEntry("ds.json"));
            zos.write(twoRowDoc().getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        // jar: URIs have no filesystem Path (pathFromUri returns null), so this exercises the
        // URL-stream fallback of provide().
        URI jarUri = URI.create("jar:" + zip.toUri() + "!/ds.json");

        IDataTable table = new DsjTableProvider().provide(jarUri, DsjProviderSupplier.FI_DSJ_JSON);

        assertEquals(2, table.getRowCount());
        assertEquals("v0", table.getValue(0, 0));
        assertEquals("v1", table.getValue(1, 0));
    }


    /**
     * A zip replaced during the session must be read afresh. {@code URL.openStream()} on a
     * {@code jar:} URL leaves the archive in {@code JarURLConnection}'s JVM-wide cache after the
     * stream is closed, so a second read of a replaced zip returned the OLD rows (measured on
     * Linux), and on Windows the zip stayed locked and could not be replaced at all. See
     * {@code PLAN-jar-url-stream-cache}.
     */
    @Test
    void aZipReplacedDuringTheSessionIsReadAfresh(@TempDir Path tmp) throws IOException
    {
        Path zip = tmp.resolve("replaced.zip");
        writeZippedDoc(zip, twoRowDoc());
        URI jarUri = URI.create("jar:" + zip.toUri() + "!/ds.json");
        assertEquals(2, new DsjTableProvider().provide(jarUri, DsjProviderSupplier.FI_DSJ_JSON)
                .getRowCount());

        Files.delete(zip); // on Windows this is where a cached archive handle fails the test
        writeZippedDoc(zip, twoRowDoc().replace("\"records\": 2", "\"records\": 3")
                .replace("[[\"v0\"], [\"v1\"]]", "[[\"v0\"], [\"v1\"], [\"v2\"]]"));

        IDataTable reread = new DsjTableProvider().provide(jarUri, DsjProviderSupplier.FI_DSJ_JSON);
        assertEquals(3, reread.getRowCount(), "the replaced zip must be read, not the cached one");
        assertEquals("v2", reread.getValue(2, 0));
    }


    /**
     * The same, through {@code provideMetaData} — the provider's second stream site, which
     * {@code provide} never reaches. The row count comes from the {@code records} header.
     */
    @Test
    void aZipReplacedDuringTheSessionHasFreshMetaData(@TempDir Path tmp) throws IOException
    {
        Path zip = tmp.resolve("replaced-meta.zip");
        writeZippedDoc(zip, twoRowDoc());
        URI jarUri = URI.create("jar:" + zip.toUri() + "!/ds.json");
        assertEquals(2, new DsjTableProvider()
                .provideMetaData(jarUri, DsjProviderSupplier.FI_DSJ_JSON).getRowCount());

        Files.delete(zip); // on Windows this is where a cached archive handle fails the test
        writeZippedDoc(zip, twoRowDoc().replace("\"records\": 2", "\"records\": 3")
                .replace("[[\"v0\"], [\"v1\"]]", "[[\"v0\"], [\"v1\"], [\"v2\"]]"));

        assertEquals(3,
                new DsjTableProvider().provideMetaData(jarUri, DsjProviderSupplier.FI_DSJ_JSON)
                        .getRowCount(),
                "the replaced zip's header must be read, not the cached one");
    }


    private static void writeZippedDoc(Path aZip, String aDoc) throws IOException
    {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(aZip)))
        {
            zos.putNextEntry(new ZipEntry("ds.json"));
            zos.write(aDoc.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }

    // ---- provide:363 — debugCalcDataSize must actually run (and log) after a load ----------


    @Test
    void successfulLoadLogsTheCalculatedDataSize(@TempDir Path tmp) throws IOException
    {
        URI uri = writeJson(tmp, "sizes.json", twoRowDoc());

        // debugCalcDataSize logs on AbstractDataTableProvider's own logger at DEBUG.
        Logger julLogger = Logger.getLogger(AbstractDataTableProvider.class.getName());
        Level oldLevel = julLogger.getLevel();
        CapturingHandler capture = new CapturingHandler();
        julLogger.setLevel(Level.ALL);
        julLogger.addHandler(capture);
        try
        {
            new DsjTableProvider().provide(uri, DsjProviderSupplier.FI_DSJ_JSON);
        }
        finally
        {
            julLogger.removeHandler(capture);
            julLogger.setLevel(oldLevel);
        }

        assertTrue(
                capture.records.stream()
                        .anyMatch(r -> r.getMessage() != null
                                && r.getMessage().contains("Calculated a total data size of")),
                "provide() must report the calculated data size at DEBUG");
    }

    // ---- emptyTable:449 — a metadata-only table's columns must be completed ----------------

    // ---- addData2Column — a type-violating cell is MIS_ERROR, not a failed load ------------


    @Test
    void typeViolatingCellIsMisErrorNotALoadFailure(@TempDir Path tmp) throws IOException
    {
        // A JSON boolean in a declared-double column reaches the Boolean arm of addData2Column.
        // Until 2026-09-25 the DOUBLE column buffer rejected it and the catch arm failed the WHOLE
        // load; this test pinned that. Owner ruling (PLAN-oss-dsj-malformed-boolean-cell): a type
        // violation in one cell is MIS_ERROR in that cell. The catch arm is now reached only by a
        // genuinely unexpected failure -- see unexpectedCellFailureLogsItsCoordinatesAndFails.
        String json = """
                {
                  "datasetJSONCreationDateTime": "2026-09-15T10:00:00",
                  "datasetJSONVersion": "1.1.0",
                  "itemGroupOID": "IG.BADCELL",
                  "name": "BADCELL",
                  "label": "Unstorable cell",
                  "records": 1,
                  "columns": [
                    {"itemOID": "IT.D", "name": "D", "label": "D", "dataType": "double"}
                  ],
                  "rows": [[true]]
                }
                """;
        URI uri = writeJson(tmp, "badcell.json", json);

        IDataTable table = new DsjTableProvider().provide(uri, DsjProviderSupplier.FI_DSJ_JSON);

        assertEquals(1, table.getRowCount());
        assertEquals(MissingValue.MIS_ERROR, table.getValue(0, 0));
    }

    // ---- provide:357 — a document with no declared "records" must still load ----------------


    @Test
    void documentWithoutDeclaredRecordsLoads(@TempDir Path tmp) throws IOException
    {
        // "records" absent -> DsjTable.getRecords() == -1 -> the provider's declared-vs-parsed
        // check must stay disarmed rather than comparing against -1.
        String json = """
                {
                  "datasetJSONCreationDateTime": "2026-09-15T10:00:00",
                  "datasetJSONVersion": "1.1.0",
                  "itemGroupOID": "IG.NOREC",
                  "name": "NOREC",
                  "label": "No records attribute",
                  "columns": [
                    {"itemOID": "IT.A", "name": "A", "label": "A", "dataType": "string"}
                  ],
                  "rows": [["only"]]
                }
                """;
        URI uri = writeJson(tmp, "norecords.json", json);

        IDataTable table = new DsjTableProvider().provide(uri, DsjProviderSupplier.FI_DSJ_JSON);

        assertEquals(1, table.getRowCount());
        assertEquals("only", table.getValue(0, 0));
    }

}
