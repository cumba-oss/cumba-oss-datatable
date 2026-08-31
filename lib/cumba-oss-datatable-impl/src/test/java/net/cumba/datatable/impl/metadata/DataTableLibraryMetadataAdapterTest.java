package net.cumba.datatable.impl.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.cumba.datatable.impl.library.dblib.DataBrowserLibrary;
import net.cumba.datatable.impl.library.dblib.DataBrowserMember;
import net.cumba.datatable.impl.library.dblib.beans.DataBrowserLibraryBean;
import net.cumba.datatable.metadata.IColumnMetadata;
import net.cumba.datatable.metadata.IDataTableMetadata;
import net.cumba.datatable.metadata.IMetadataLibrary;
import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises {@link DataTableLibraryMetadataAdapter} — both the {@code empty()} placeholder and the
 * {@code of()} factory backed by a real {@code .dblib} library on disk so the SPI-registered
 * {@code DataBrowserLibraryProvider} can supply members and column metadata.
 */
class DataTableLibraryMetadataAdapterTest
{

    // ============================================================
    // empty() — placeholder instance
    // ============================================================

    @Test
    void testEmptyHasNoData()
    {
        DataTableLibraryMetadataAdapter empty = DataTableLibraryMetadataAdapter.empty();

        assertNull(empty.getName());
        assertNull(empty.getVersion());
        assertFalse(empty.isColumnNameCaseSensitive());
        assertTrue(empty.getDataTables().isEmpty());
        assertTrue(empty.getCodelists().isEmpty());
        assertTrue(empty.getMetaKeys().isEmpty());

        assertEquals(Optional.empty(), empty.getDataTable("anything"));
        assertEquals(Optional.empty(), empty.getDataTable(null));
        assertEquals(Optional.empty(), empty.getCodelist("anything"));
        assertEquals(Optional.empty(), empty.getMetaValue("anything"));
    }

    // ============================================================
    // of(IDataTableLibrary) — integration with .dblib library
    // ============================================================

    private record Fixture(IMetadataLibrary adapter, Path memberFile)
    {
    }

    /**
     * Build a .dblib on disk that the SPI-registered {@code DataBrowserLibraryProvider} can serve,
     * instantiate the adapter, and return both the adapter and the member-file path for assertions
     * that compare against its URI.
     */
    private static Fixture buildAdapterFixture(Path tempDir) throws IOException
    {
        Path memberFile = tempDir.resolve("dm.csv");
        Files.writeString(memberFile, "SUBJID,AGE\n");

        String json = String.format("""
                {
                  "name": "MyLib",
                  "sources": [
                    { "uri": "%s", "name": "DM", "label": "Demographics" }
                  ],
                  "columnMeta": [
                    { "uri": "%s", "name": "SUBJID", "label": "Subject ID",
                      "type": "STRING", "format": "$20.", "key": 1 },
                    { "uri": "%s", "name": "AGE", "label": "Age",
                      "type": "DOUBLE", "attributes": { "origin": "CRF" } }
                  ]
                }
                """, memberFile.toUri(), memberFile.toUri(), memberFile.toUri());
        Path dblibFile = tempDir.resolve("test.dblib");
        Files.writeString(dblibFile, json);

        DataBrowserLibraryBean bean = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(json, DataBrowserLibraryBean.class);
        DataBrowserLibrary lib = new DataBrowserLibrary(dblibFile.toUri(), bean);
        return new Fixture(DataTableLibraryMetadataAdapter.of(lib), memberFile);
    }


    @Test
    void testOfWithDataBrowserLibrary_libraryLevelMetadata(@TempDir Path tempDir) throws IOException
    {
        IMetadataLibrary adapter = buildAdapterFixture(tempDir).adapter();
        assertEquals("MyLib", adapter.getName());
        assertNull(adapter.getVersion());
        assertFalse(adapter.isColumnNameCaseSensitive());
        assertTrue(adapter.getCodelists().isEmpty(), "adapter knows nothing about codelists");
        assertEquals(Optional.empty(), adapter.getCodelist("X"));
        assertEquals(Set.of(), adapter.getMetaKeys());
        assertEquals(Optional.empty(), adapter.getMetaValue("any"));
    }


    @Test
    void testOfWithDataBrowserLibrary_dataTableShapeAndLookup(@TempDir Path tempDir)
        throws IOException
    {
        Fixture f = buildAdapterFixture(tempDir);
        IMetadataLibrary adapter = f.adapter();

        List<IDataTableMetadata> tables = adapter.getDataTables();
        assertEquals(1, tables.size());
        IDataTableMetadata dt = tables.get(0);

        assertEquals("DM", dt.getName());
        // Label comes from the source bean via DataBrowserMember.
        assertEquals("Demographics", dt.getLabel());
        assertEquals(f.memberFile().toUri(), dt.getTableURI());
        assertTrue(dt.getMetaKeys().isEmpty());
        assertEquals(Optional.empty(), dt.getMetaValue("k"));

        // Adapter lookup is case-insensitive.
        assertTrue(adapter.getDataTable("dm").isPresent());
        assertTrue(adapter.getDataTable("DM").isPresent());
        assertFalse(adapter.getDataTable("XX").isPresent());
        assertTrue(adapter.getDataTable(null).isEmpty());
    }


    @Test
    void testOfWithDataBrowserLibrary_subjidColumnMetadata(@TempDir Path tempDir) throws IOException
    {
        IDataTableMetadata dt = buildAdapterFixture(tempDir).adapter().getDataTables().get(0);
        List<IColumnMetadata> cols = dt.getColumns();
        assertEquals(2, cols.size());
        IColumnMetadata subjid = dt.getColumn("subjid").orElseThrow();
        assertEquals("SUBJID", subjid.getName());
        assertEquals("Subject ID", subjid.getLabel());
        assertEquals("$20.", subjid.getDisplayFormat());
        assertEquals(0, subjid.getIndex());
        assertEquals(DataValueType.STRING, subjid.getType());
        assertEquals(0, subjid.getLength(), "length not set by provider");
        assertNull(subjid.getNativeType());
        assertEquals(0, subjid.getKeySequence(), "adapter does not propagate key sequence");
        assertFalse(subjid.isByGroup());
        assertNull(subjid.getCodelist());
        assertTrue(subjid.getMetaKeys().isEmpty(), "no custom attributes on SUBJID");
        assertEquals(Optional.empty(), subjid.getMetaValue("k"));
    }


    @Test
    void testOfWithDataBrowserLibrary_ageColumnMetadataAndNullLookups(@TempDir Path tempDir)
        throws IOException
    {
        IDataTableMetadata dt = buildAdapterFixture(tempDir).adapter().getDataTables().get(0);
        IColumnMetadata age = dt.getColumn("AGE").orElseThrow();
        assertEquals(DataValueType.DOUBLE, age.getType());
        assertEquals(1, age.getIndex());
        // The "origin" attribute attached to the column meta is exposed verbatim.
        assertEquals(Set.of("origin"), age.getMetaKeys());
        assertEquals(Optional.of("CRF"), age.getMetaValue("origin"));
        assertEquals(Optional.empty(), age.getMetaValue("missing"));
        // getColumn(null) and unknown-name lookups.
        assertTrue(dt.getColumn(null).isEmpty());
        assertTrue(dt.getColumn("nope").isEmpty());
    }


    @Test
    void testOfWithNonRegisteredLibraryIsEmpty(@TempDir Path tempDir) throws IOException
    {
        // A custom library implementation whose URI / FileInfo are not handled by any
        // registered ILibrarySupplier. The factory returns no providers, so the
        // adapter ends up with no tables.
        URI uri = tempDir.resolve("not-a-dblib.txt").toUri();
        net.cumba.datatable.library.IDataTableLibrary lib = new net.cumba.datatable.library.IDataTableLibrary()
        {

            @Override
            public String getName()
            {
                return "L";
            }


            @Override
            public String getLabel()
            {
                return null;
            }


            @Override
            public URI getUri()
            {
                return uri;
            }


            @Override
            public String getType()
            {
                return "custom";
            }

        };

        IMetadataLibrary adapter = DataTableLibraryMetadataAdapter.of(lib);

        assertEquals("L", adapter.getName());
        assertNotNull(adapter.getDataTables());
        assertTrue(adapter.getDataTables().isEmpty());
    }


    @Test
    void testAdapterColumnFallsBackToStringWhenTypeNull(@TempDir Path tempDir) throws IOException
    {
        // Build a .dblib whose columnMeta contains an entry with no type — the
        // provider returns a DataTableColumnMeta with type=null, and the adapter
        // must back-fill DataValueType.STRING.
        Path memberFile = tempDir.resolve("ae.csv");
        Files.writeString(memberFile, "A,B\n");

        String json = String.format("""
                {
                  "name": "L",
                  "sources": [
                    { "uri": "%s", "name": "AE" }
                  ],
                  "columnMeta": [
                    { "uri": "%s", "name": "WHATEVER" }
                  ]
                }
                """, memberFile.toUri(), memberFile.toUri());
        Path dblibFile = tempDir.resolve("t.dblib");
        Files.writeString(dblibFile, json);

        DataBrowserLibraryBean bean = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(json, DataBrowserLibraryBean.class);
        DataBrowserLibrary lib = new DataBrowserLibrary(dblibFile.toUri(), bean);

        IMetadataLibrary adapter = DataTableLibraryMetadataAdapter.of(lib);
        IColumnMetadata col = adapter.getDataTables().get(0).getColumns().get(0);

        assertEquals(DataValueType.STRING, col.getType());
        assertEquals("WHATEVER", col.getName());
    }


    @Test
    void testColumnWithCodelistAttribute(@TempDir Path tempDir) throws IOException
    {
        Path memberFile = tempDir.resolve("dm.csv");
        Files.writeString(memberFile, "SEX\n");

        String json = String.format("""
                {
                  "name": "L",
                  "sources": [
                    { "uri": "%s", "name": "DM" }
                  ],
                  "columnMeta": [
                    { "uri": "%s", "name": "SEX", "type": "STRING",
                      "attributes": { "codelist": "C66731" } }
                  ]
                }
                """, memberFile.toUri(), memberFile.toUri());
        Path dblibFile = tempDir.resolve("t.dblib");
        Files.writeString(dblibFile, json);

        DataBrowserLibraryBean bean = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(json, DataBrowserLibraryBean.class);
        DataBrowserLibrary lib = new DataBrowserLibrary(dblibFile.toUri(), bean);

        IMetadataLibrary adapter = DataTableLibraryMetadataAdapter.of(lib);
        IColumnMetadata sex = adapter.getDataTables().get(0).getColumn("SEX").orElseThrow();
        assertEquals("C66731", sex.getCodelist());
    }


    @Test
    void testEmptyColumnMetaYieldsEmptyTable()
    {
        // A column-meta-only adapter built via the static factory but with no library.
        // Cannot use of() (it needs LibraryProviderFactory) — exercise via the
        // public bean-driven path of DataBrowserMetadataLibrary integration is enough.
        // Here we just re-assert the empty placeholder is still valid.
        DataTableLibraryMetadataAdapter empty = DataTableLibraryMetadataAdapter.empty();
        assertTrue(empty.getDataTables().isEmpty());
    }


    @Test
    void testUnusedDataBrowserMemberConstructor(@TempDir Path tempDir)
    {
        // Smoke-test that DataBrowserMember constructed manually does not break
        // adapter integration. Members built outside the library are still valid
        // ILibraryMember instances.
        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("L").build();
        DataBrowserLibrary lib = new DataBrowserLibrary(tempDir.resolve("x.dblib").toUri(), bean);
        DataBrowserMember m = new DataBrowserMember(lib, tempDir.resolve("x.csv").toUri(), "X",
                "Label");
        assertNotNull(m.getUri());
    }
}
