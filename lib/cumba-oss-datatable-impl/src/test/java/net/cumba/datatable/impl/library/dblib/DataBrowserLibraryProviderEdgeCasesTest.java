package net.cumba.datatable.impl.library.dblib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.impl.library.dblib.beans.DataBrowserLibraryBean;
import net.cumba.datatable.impl.library.dblib.beans.DataBrowserValueFormatBean;
import net.cumba.datatable.library.IDataTableLibrary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Edge-case tests for {@link DataBrowserLibraryProvider} focused on
 * {@code DataBrowserValueFormatBean} JSON round-tripping and corner cases not already covered by
 * {@code DataBrowserLibraryProviderTest}.
 */
class DataBrowserLibraryProviderEdgeCasesTest
{

    private final DataBrowserLibraryProvider provider = new DataBrowserLibraryProvider();

    // ============================================================
    // Format-catalog assembly: internal formats are wired into the library
    // ============================================================

    @Test
    void testProvideAssemblesInternalFormatsIntoCatalog(@TempDir Path tempDir) throws IOException
    {
        String json = """
                {
                  "name": "L",
                  "formats": [
                    {
                      "name": "SEX",
                      "label": "Sex codelist",
                      "valueMap": { "M": "Male", "F": "Female" }
                    }
                  ]
                }
                """;
        Path dblibFile = tempDir.resolve("t.dblib");
        Files.writeString(dblibFile, json);

        IDataTableLibrary lib = provider.provide(dblibFile.toUri(), null);
        assertNotNull(lib);
    }

    // ============================================================
    // External column-meta tables: warnings and skips
    // ============================================================


    @Test
    void testProvideLibraryMemberColumnsSkipsMissingExternalTable(@TempDir Path tempDir)
        throws IOException
    {
        URI memberUri = tempDir.resolve("dm.csv").toUri();
        Files.writeString(tempDir.resolve("dm.csv"), "X\n");

        URI libraryUri = tempDir.resolve("t.dblib").toUri();
        // Reference an external column-meta table that doesn't exist
        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("L")
                .columnMetaTableUris(new String[]
                {
                        "missing.csv"
                }).build();

        DataBrowserLibrary lib = new DataBrowserLibrary(libraryUri, bean);
        DataBrowserMember member = new DataBrowserMember(lib, memberUri, "DM", null);

        // No internal column meta and the external table is missing → empty stream
        List<? extends DataTableColumnMeta> cols = provider.provideLibraryMemberColumns(member)
                .toList();
        assertTrue(cols.isEmpty());
    }

    // ============================================================
    // provide() — the stored library name is used as the default
    // ============================================================


    @Test
    void testProvideUsesStoredName(@TempDir Path tempDir) throws IOException
    {
        String json = "{\"name\":\"StoredName\"}";
        Path dblibFile = tempDir.resolve("t.dblib");
        Files.writeString(dblibFile, json);

        IDataTableLibrary lib = provider.provide(dblibFile.toUri(), null);

        // The stored name is resolved as the default library name
        assertEquals("StoredName", lib.getName());
    }

    // ============================================================
    // Bean round-trip for the format bean (covers Lombok @Value getters)
    // ============================================================


    @Test
    void testValueFormatBeanJsonRoundTrip() throws IOException
    {
        DataBrowserValueFormatBean fmt = DataBrowserValueFormatBean.builder().name("X")
                .label("X label").valueMap(Map.of("a", "A", "b", "B")).build();

        ObjectMapper m = new ObjectMapper();
        String json = m.writeValueAsString(fmt);
        DataBrowserValueFormatBean parsed = m.readValue(json, DataBrowserValueFormatBean.class);

        assertEquals(fmt.getName(), parsed.getName());
        assertEquals(fmt.getLabel(), parsed.getLabel());
        assertEquals(fmt.getValueMap(), parsed.getValueMap());
    }

    // ============================================================
    // computeBeanDefaultName: failure path during provide
    // ============================================================


    @Test
    void testProvideOnFileWithBadJson(@TempDir Path tempDir) throws IOException
    {
        Path dblibFile = tempDir.resolve("bad.dblib");
        Files.writeString(dblibFile, "{not even json");

        // ObjectMapper.readValue throws → wrapped in IOException by openStream user
        org.junit.jupiter.api.Assertions.assertThrows(IOException.class,
                () -> provider.provide(dblibFile.toUri(), null));
    }

    // ============================================================
    // Smoke test for the library's getType()
    // ============================================================


    @Test
    void testDataBrowserLibraryGetType() throws IOException
    {
        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("L").build();
        try (DataBrowserLibrary lib = new DataBrowserLibrary(URI.create("file:///x.dblib"), bean))
        {
            assertEquals("databrowser", lib.getType());
            assertNull(lib.getLabel());
        }
    }
}
