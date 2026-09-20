package net.cumba.datatable.provider.dsj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.provider.DataTableMetaSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Assertion-gap tests for {@link DsjTableProvider#buildMeta} / {@code handleMetadata}: the
 * {@code file_format} / {@code dataset_size} table metadata, and the metadata-only-document path
 * (records=0), which used to throw even though metadata plainly HAD been captured.
 */
class DsjTableProviderMetadataAssertionsTest
{

    private static URI writeJson(Path aDir, String aName, String aJson) throws IOException
    {
        Path f = aDir.resolve(aName);
        Files.writeString(f, aJson, StandardCharsets.UTF_8);
        return f.toUri();
    }

    private static final String BASE = """
            {
              "datasetJSONCreationDateTime": "2026-09-08T10:00:00",
              "datasetJSONVersion": "1.1.0",
              "itemGroupOID": "IG.META",
              "name": "META",
              "label": "%s",
              "records": 1,
              "columns": [
                {"itemOID": "IT.A", "name": "A", "label": "A", "dataType": "string"}
              ],
              "rows": [
                ["x"]
              ]
            }
            """;
    // ---- file_format / dataset_size metadata (lines 164-165 / 559-560) ---------------------

    @Test
    void provideSetsFileFormatAndDatasetSizeMetadata(@TempDir Path tmp) throws IOException
    {
        String json = BASE.formatted("META Table");
        URI uri = writeJson(tmp, "meta.json", json);
        long expectedSize = Files.size(Path.of(uri));

        IDataTable table = new DsjTableProvider().provide(uri, DsjProviderSupplier.FI_DSJ_JSON);
        DataTableMeta meta = table.getMetaData();

        assertEquals("DATASET-JSON", meta.getMetaData(DataTableMetaSupport.META_KEY_FILE_FORMAT),
                "provide() must record the DATASET-JSON file_format metadata key");
        assertEquals(Long.valueOf(expectedSize),
                meta.getMetaData(DataTableMetaSupport.META_KEY_DATASET_SIZE),
                "provide() must record the actual on-disk size as dataset_size metadata");
    }


    @Test
    void provideMetaDataSetsFileFormatAndDatasetSizeMetadata(@TempDir Path tmp) throws IOException
    {
        String json = BASE.formatted("META Table");
        URI uri = writeJson(tmp, "meta2.json", json);
        long expectedSize = Files.size(Path.of(uri));

        DataTableMeta meta = new DsjTableProvider().provideMetaData(uri,
                DsjProviderSupplier.FI_DSJ_JSON);

        assertEquals("DATASET-JSON", meta.getMetaData(DataTableMetaSupport.META_KEY_FILE_FORMAT),
                "provideMetaData() must record the DATASET-JSON file_format metadata key");
        assertEquals(Long.valueOf(expectedSize),
                meta.getMetaData(DataTableMetaSupport.META_KEY_DATASET_SIZE),
                "provideMetaData() must record the actual on-disk size as dataset_size metadata");
    }


    /**
     * Dataset-JSON top-level metadata attribute "records" (minimum 0): "Since 'rows' is an optional
     * object, records=0 allows for the transfer of metadata without sending data."
     * {@code DataSetJsonTableParser} already supports this -- it fires the metadata handler and
     * delivers zero rows for both an absent and an empty {@code "rows"} array -- but
     * {@code DsjTableProvider.assembleResult} used to treat "neither ingestion handler ever ran" as
     * "no metadata was found" and threw {@code IOException("No metadata found in
     * DataSet-JSON file.")} even though metadata plainly HAD been captured. Fixed: when metadata
     * was captured but no row handler ever fired, return a real zero-row table built from that
     * metadata instead.
     */
    @Test
    void metadataOnlyDocumentWithAbsentRowsLoadsAsAnEmptyTable(@TempDir Path tmp) throws IOException
    {
        String json = """
                {
                  "datasetJSONCreationDateTime": "2026-09-08T10:00:00",
                  "datasetJSONVersion": "1.1.0",
                  "itemGroupOID": "IG.EMPTY",
                  "name": "EMPTY",
                  "label": "Metadata only, no rows member at all",
                  "records": 0,
                  "columns": [
                    {"itemOID": "IT.A", "name": "A", "label": "A", "dataType": "string"},
                    {"itemOID": "IT.B", "name": "B", "label": "B", "dataType": "double"}
                  ]
                }
                """;
        URI uri = writeJson(tmp, "empty-norows.json", json);

        IDataTable table = new DsjTableProvider().provide(uri, DsjProviderSupplier.FI_DSJ_JSON);

        assertEquals(0, table.getRowCount());
        assertEquals(2, table.getColumnCount());
        assertEquals("EMPTY", table.getMetaData().getName());
    }


    @Test
    void metadataOnlyDocumentWithEmptyRowsArrayLoadsAsAnEmptyTable(@TempDir Path tmp)
        throws IOException
    {
        String json = """
                {
                  "datasetJSONCreationDateTime": "2026-09-08T10:00:00",
                  "datasetJSONVersion": "1.1.0",
                  "itemGroupOID": "IG.EMPTY2",
                  "name": "EMPTY2",
                  "label": "Metadata only, explicit empty rows array",
                  "records": 0,
                  "columns": [
                    {"itemOID": "IT.A", "name": "A", "label": "A", "dataType": "string"}
                  ],
                  "rows": []
                }
                """;
        URI uri = writeJson(tmp, "empty-rows.json", json);

        IDataTable table = new DsjTableProvider().provide(uri, DsjProviderSupplier.FI_DSJ_JSON);

        assertEquals(0, table.getRowCount());
        assertEquals(1, table.getColumnCount());
    }

    // ---- ported from the internal twin: blank-vs-populated label, blank displayFormat,
    // explicit records=0, and targetDataType resolution. The order-related tests of that file
    // are deliberately NOT ported: net.cumba.datatable.order is not part of this project.


    @Test
    void blankTableLabelIsNotSetOnTheMetadata(@TempDir Path tmp) throws IOException
    {
        String json = BASE.formatted("   ");
        URI uri = writeJson(tmp, "blanklabel.json", json);

        IDataTable table = new DsjTableProvider().provide(uri, DsjProviderSupplier.FI_DSJ_JSON);

        // A blank/whitespace-only label must be treated as absent, i.e. must not overwrite
        // whatever default DataTableMetaBuilder assigns (never the literal blank string).
        assertFalse("   ".equals(table.getMetaData().getLabel()),
                "a blank table label must not be forwarded verbatim onto the built metadata");
    }


    @Test
    void populatedTableLabelIsSetOnTheMetadata(@TempDir Path tmp) throws IOException
    {
        String json = BASE.formatted("A Real Label");
        URI uri = writeJson(tmp, "reallabel.json", json);

        IDataTable table = new DsjTableProvider().provide(uri, DsjProviderSupplier.FI_DSJ_JSON);

        assertEquals("A Real Label", table.getMetaData().getLabel());
    }


    @Test
    void explicitlyZeroRecordsLeavesRowCountAtItsDefault(@TempDir Path tmp) throws IOException
    {
        String json = """
                {
                  "datasetJSONCreationDateTime": "2026-09-08T10:00:00",
                  "datasetJSONVersion": "1.1.0",
                  "itemGroupOID": "IG.ZERO",
                  "name": "ZERO",
                  "label": "Zero records",
                  "records": 0,
                  "columns": [
                    {"itemOID": "IT.A", "name": "A", "label": "A", "dataType": "string"}
                  ],
                  "rows": []
                }
                """;
        URI uri = writeJson(tmp, "zero.json", json);

        DataTableMeta meta = new DsjTableProvider().provideMetaData(uri,
                DsjProviderSupplier.FI_DSJ_JSON);

        // DataTableMeta's own default for an unset rowCount/totalRowCount is 0, so a document
        // that explicitly declares "records": 0 must read back the same way as one that never
        // sets rowCount at all -- this pins that (documented) equivalence rather than leaving it
        // as an implicit assumption.
        assertEquals(0, meta.getRowCount());
        assertEquals(0, meta.getTotalRowCount());
    }


    @Test
    void blankColumnDisplayFormatIsNotSetOnTheColumnMetadata(@TempDir Path tmp) throws IOException
    {
        String json = """
                {
                  "datasetJSONCreationDateTime": "2026-09-08T10:00:00",
                  "datasetJSONVersion": "1.1.0",
                  "itemGroupOID": "IG.DF",
                  "name": "DF",
                  "label": "Blank displayFormat",
                  "records": 1,
                  "columns": [
                    {"itemOID": "IT.A", "name": "A", "label": "A", "dataType": "double",
                     "displayFormat": "   "},
                    {"itemOID": "IT.B", "name": "B", "label": "B", "dataType": "double",
                     "displayFormat": "8.2"}
                  ],
                  "rows": [[1.5, 2.5]]
                }
                """;
        URI uri = writeJson(tmp, "displayformat.json", json);

        IDataTable table = new DsjTableProvider().provide(uri, DsjProviderSupplier.FI_DSJ_JSON);

        assertFalse("   ".equals(table.getMetaData().getColumn(0).getDisplayFormat()),
                "a blank displayFormat must not be forwarded verbatim onto the column metadata");
        assertEquals("8.2", table.getMetaData().getColumn(1).getDisplayFormat());
    }


    @Test
    void provideMetaDataAlsoTreatsABlankLabelAsAbsent(@TempDir Path tmp) throws IOException
    {
        // provideMetaData() runs through buildMeta(), a separate (near-duplicate) copy of the
        // blank-label check exercised via provide()'s handleMetadata() above -- both copies must
        // be tested independently, since a mutation in one is invisible to a test of the other.
        String json = BASE.formatted("   ");
        URI uri = writeJson(tmp, "blanklabel-meta.json", json);

        DataTableMeta meta = new DsjTableProvider().provideMetaData(uri,
                DsjProviderSupplier.FI_DSJ_JSON);

        assertFalse("   ".equals(meta.getLabel()),
                "a blank table label must not be forwarded verbatim by provideMetaData() either");
    }


    @Test
    void targetDataTypeIntegerAndDecimalBothResolveToDoubleColumnType(@TempDir Path tmp)
        throws IOException
    {
        String json = """
                {
                  "datasetJSONCreationDateTime": "2026-09-08T10:00:00",
                  "datasetJSONVersion": "1.1.0",
                  "itemGroupOID": "IG.TT",
                  "name": "TT",
                  "label": "targetDataType resolution",
                  "records": 1,
                  "columns": [
                    {"itemOID": "IT.INT", "name": "INTCOL", "label": "Int", "dataType": "integer",
                     "targetDataType": "integer"},
                    {"itemOID": "IT.DEC", "name": "DECCOL", "label": "Dec", "dataType": "decimal",
                     "targetDataType": "decimal", "length": 16}
                  ],
                  "rows": [[7, "1.5"]]
                }
                """;
        URI uri = writeJson(tmp, "targettype.json", json);

        DataTableMeta meta = new DsjTableProvider().provideMetaData(uri,
                DsjProviderSupplier.FI_DSJ_JSON);

        assertEquals(net.cumba.datatable.values.DataValueType.DOUBLE, meta.getColumn(0).getType(),
                "targetDataType=integer must resolve the column's stored type to DOUBLE");
        assertEquals(net.cumba.datatable.values.DataValueType.DOUBLE, meta.getColumn(1).getType(),
                "targetDataType=decimal must resolve the column's stored type to DOUBLE");
    }

}
