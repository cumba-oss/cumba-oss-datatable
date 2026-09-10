package net.cumba.datatable.provider.dsj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.cumba.datatable.IDataTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pin for an authorised fix in {@link DsjTableProvider}: F-prov-01 — a declared-vs-parsed row-count
 * mismatch must raise an {@link IOException} instead of returning the truncated table as a
 * successful load. (F-prov-10, the keySequence half of the internal twin of this test, has no
 * surface here: this variant of the provider does not derive a proposed key order.)
 */
class DsjRowCountTest
{

    private static java.net.URI writeJson(Path aDir, String aName, String aJson) throws IOException
    {
        Path f = aDir.resolve(aName);
        Files.writeString(f, aJson, StandardCharsets.UTF_8);
        return f.toUri();
    }

    // ---- F-prov-01 ----------------------------------------------------------------------------

    private static final String TRUNCATED = """
            {
              "datasetJSONCreationDateTime": "2026-09-08T10:00:00",
              "datasetJSONVersion": "1.1.0",
              "itemGroupOID": "IG.TRUNC",
              "name": "TRUNC",
              "label": "Declares 3 records but carries only 2 rows",
              "records": 3,
              "columns": [
                {"itemOID": "IT.A", "name": "A", "label": "A", "dataType": "string"}
              ],
              "rows": [
                ["r1"],
                ["r2"]
              ]
            }
            """;

    @Test
    void rowCountMismatchThrowsInsteadOfReturningShortTable(@TempDir Path tmp) throws IOException
    {
        java.net.URI uri = writeJson(tmp, "trunc.json", TRUNCATED);
        DsjTableProvider provider = new DsjTableProvider();
        IOException ex = assertThrows(IOException.class,
                () -> provider.provide(uri, DsjProviderSupplier.FI_DSJ_JSON));
        // ⚠ TWO layers answer this, and which one wins depends on the cdisc-dsj pin. Since
        // cumba-oss-formats 0.4.0 the PARSER throws first ("Declared \"records\" (3) does not match
        // the number of rows parsed (2)."); before it the parser returned a short table and THIS
        // provider's own guard threw ("Can't read all rows. Expected=3, found=2"). Both are correct
        // answers to the same question, so assert the contract - an IOException naming both counts
        // -
        // rather than one layer's wording, which is what pinned this test to the old pin.
        String msg = ex.getMessage();
        assertTrue(msg.contains("3") && msg.contains("2")
                && (msg.contains("Expected=") || msg.contains("does not match")), msg);
    }


    @Test
    void matchingRowCountStillLoads(@TempDir Path tmp) throws IOException
    {
        java.net.URI uri = writeJson(tmp, "ok.json",
                TRUNCATED.replace("\"records\": 3", "\"records\": 2"));
        IDataTable table = new DsjTableProvider().provide(uri, DsjProviderSupplier.FI_DSJ_JSON);
        assertEquals(2L, table.getRowCount());
        assertEquals("r2", table.getValue(1, 0));
    }

}
