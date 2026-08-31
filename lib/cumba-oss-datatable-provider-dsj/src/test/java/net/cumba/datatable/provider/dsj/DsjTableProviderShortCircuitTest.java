package net.cumba.datatable.provider.dsj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.cumba.datatable.DataTableMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Short-circuit tests for the dsj2 {@link DsjTableProvider#provideMetaData}. See the dsj v1 variant
 * for the rationale behind each assertion.
 */
class DsjTableProviderShortCircuitTest
{

    private DsjTableProvider provider;

    @BeforeEach
    void setUp()
    {
        provider = new DsjTableProvider();
    }


    private URI writeBytesToTempUri(byte[] bytes, String suffix) throws IOException
    {
        File tmp = File.createTempFile("dsj2shortcircuit", suffix);
        tmp.deleteOnExit();
        Files.write(tmp.toPath(), bytes);
        return tmp.toURI();
    }


    @Test
    void provideMetaData_skipsRowsEvenIfRowSectionIsCorrupt() throws Exception
    {
        // Load the valid 5-row fixture, then corrupt the row data section.
        Path validPath;
        try (InputStream in = getClass()
                .getResourceAsStream("/fixtures/dsj2/shortcircuit-test-5rows.json"))
        {
            assertNotNull(in, "fixture not found");
            File tmp = File.createTempFile("dsj2valid", ".json");
            tmp.deleteOnExit();
            Files.copy(in, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
            validPath = tmp.toPath();
        }
        String json = Files.readString(validPath, StandardCharsets.UTF_8);

        int rowsIdx = json.indexOf("\"rows\"");
        assertTrue(rowsIdx > 0, "fixture should contain a 'rows' array");
        int firstRowStart = json.indexOf('[', rowsIdx);
        assertTrue(firstRowStart > 0);

        String corrupted = json.substring(0, firstRowStart) + "[ @@@ not json @@@ ";
        byte[] corruptedBytes = corrupted.getBytes(StandardCharsets.UTF_8);
        URI uri = writeBytesToTempUri(corruptedBytes, ".json");

        DataTableMeta meta = provider.provideMetaData(uri, DsjProviderSupplier.FI_DSJ_JSON);
        assertNotNull(meta);
        assertEquals(2, meta.getColumnCount());
        assertEquals("TEST", meta.getName());

        assertThrows(Exception.class, () -> provider.provide(uri, DsjProviderSupplier.FI_DSJ_JSON));
    }


    @Test
    void provideMetaData_malformedHeaderThrows() throws Exception
    {
        byte[] malformed = "{\"datasetJSONVersion\":\"1.1.0\",\"name\":\"BROKEN"
                .getBytes(StandardCharsets.UTF_8);
        URI uri = writeBytesToTempUri(malformed, ".json");

        assertThrows(IOException.class,
                () -> provider.provideMetaData(uri, DsjProviderSupplier.FI_DSJ_JSON));
    }
}
