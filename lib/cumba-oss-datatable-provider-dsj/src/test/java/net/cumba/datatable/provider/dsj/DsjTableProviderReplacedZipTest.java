package net.cumba.datatable.provider.dsj;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.cumba.datatable.IDataTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A zip replaced during the session must be read afresh. {@code URL.openStream()} on a {@code jar:}
 * URL leaves the archive in {@code JarURLConnection}'s JVM-wide cache after the stream is closed,
 * so a second read of a replaced zip returned the OLD rows (measured on Linux), and on Windows the
 * zip stayed locked and could not be replaced at all. The provider now reads through
 * {@code URIHelper.openStream}. See {@code PLAN-jar-url-stream-cache}.
 */
class DsjTableProviderReplacedZipTest
{

    private static String doc(int aRows)
    {
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < aRows; i++)
        {
            rows.append(i == 0 ? "" : ", ").append("[\"v").append(i).append("\"]");
        }
        return """
                {
                  "datasetJSONCreationDateTime": "2026-09-24T10:00:00",
                  "datasetJSONVersion": "1.1.0",
                  "itemGroupOID": "IG.ZIP",
                  "name": "ZIP",
                  "label": "Replaced zip",
                  "records": %d,
                  "columns": [
                    {"itemOID": "IT.A", "name": "A", "label": "A", "dataType": "string"}
                  ],
                  "rows": [%s]
                }
                """.formatted(aRows, rows);
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


    @Test
    void aZipReplacedDuringTheSessionIsReadAfresh(@TempDir Path tmp) throws IOException
    {
        Path zip = tmp.resolve("replaced.zip");
        writeZippedDoc(zip, doc(2));
        URI jarUri = URI.create("jar:" + zip.toUri() + "!/ds.json");
        assertEquals(2, new DsjTableProvider().provide(jarUri, DsjProviderSupplier.FI_DSJ_JSON)
                .getRowCount());

        Files.delete(zip); // on Windows this is where a cached archive handle fails the test
        writeZippedDoc(zip, doc(3));

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
        writeZippedDoc(zip, doc(2));
        URI jarUri = URI.create("jar:" + zip.toUri() + "!/ds.json");
        assertEquals(2, new DsjTableProvider()
                .provideMetaData(jarUri, DsjProviderSupplier.FI_DSJ_JSON).getRowCount());

        Files.delete(zip); // on Windows this is where a cached archive handle fails the test
        writeZippedDoc(zip, doc(3));

        assertEquals(3,
                new DsjTableProvider().provideMetaData(jarUri, DsjProviderSupplier.FI_DSJ_JSON)
                        .getRowCount(),
                "the replaced zip's header must be read, not the cached one");
    }
}
