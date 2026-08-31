package net.cumba.datatable.provider.parquet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.JsonNode;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Direct unit tests for {@link ParquetMetadataCodec} covering the lenient applyTable / applyColumn
 * branches and the JSON-parse error path. Most happy-path behaviour is already exercised end-to-end
 * by the exporter and provider tests, so this class only fills in the small islands that those
 * tests cannot reach.
 */
class ParquetMetadataCodecTest
{

    @Test
    void testParseReturnsNullForBlankOrNullInput()
    {
        assertNull(ParquetMetadataCodec.parse(null));
        assertNull(ParquetMetadataCodec.parse(""));
    }


    @Test
    void testParseReturnsNullForInvalidJson()
    {
        // hits the catch block in ParquetMetadataCodec#parse
        assertNull(ParquetMetadataCodec.parse("{not valid json"));
    }


    @Test
    void testParseReturnsTreeForValidJson()
    {
        JsonNode node = ParquetMetadataCodec.parse("{\"tableLabel\":\"X\"}");
        assertNotNull(node);
        assertEquals("X", node.get("tableLabel").asText());
    }


    @Test
    void testApplyTableNullRootIsNoOp()
    {
        // covers the early-return guard when the JSON root is null
        DataTableMeta.DataTableMetaBuilder b = DataTableMeta.builder().name("T");
        ParquetMetadataCodec.applyTable(null, b);
        // No exception, builder is still usable
        DataTableMeta meta = b.rowCount(0).totalRowCount(0).columns(new DataTableColumnMeta[0])
                .build();
        assertEquals("T", meta.getName());
    }


    @Test
    void testApplyColumnNullRootIsNoOp()
    {
        DataTableColumnMeta.DataTableColumnMetaBuilder b = DataTableColumnMeta.builder().index(0)
                .name("X").type(DataValueType.STRING);
        ParquetMetadataCodec.applyColumn(null, "X", b);
        DataTableColumnMeta c = b.build();
        assertEquals("X", c.getName());
        assertNull(c.getLabel());
    }


    /**
     * Exercises every branch where applyColumn should leave the builder untouched: no columns key,
     * columns not an object, the named entry is missing, and the entry is present but not an
     * object.
     */
    @ParameterizedTest
    @ValueSource(strings =
    {
            "{\"tableLabel\":\"X\"}", "{\"columns\":\"oops\"}",
            "{\"columns\":{\"Y\":{\"label\":\"L\"}}}", "{\"columns\":{\"X\":\"oops\"}}"
    })
    void testApplyColumnLeavesBuilderUntouched(String aJson)
    {
        JsonNode root = ParquetMetadataCodec.parse(aJson);
        DataTableColumnMeta.DataTableColumnMetaBuilder b = DataTableColumnMeta.builder().index(0)
                .name("X").type(DataValueType.STRING);
        ParquetMetadataCodec.applyColumn(root, "X", b);
        assertNull(b.build().getLabel());
    }


    @Test
    void testApplyColumnFullEntryAppliesAllFields()
    {
        JsonNode root = ParquetMetadataCodec.parse("{\"columns\":{\"X\":{" + "\"label\":\"Lbl\","
                + "\"displayFormat\":\"DATE9.\"," + "\"nativeType\":\"NUM\"," + "\"length\":8,"
                + "\"meta\":{\"k\":\"v\"}" + "}}}");
        DataTableColumnMeta.DataTableColumnMetaBuilder b = DataTableColumnMeta.builder().index(0)
                .name("X").type(DataValueType.DOUBLE);
        ParquetMetadataCodec.applyColumn(root, "X", b);
        DataTableColumnMeta col = b.build();
        assertEquals("Lbl", col.getLabel());
        assertEquals("DATE9.", col.getDisplayFormat());
        assertEquals("NUM", col.getNativeType());
        assertEquals(8, col.getLength());
        assertEquals("v", col.getMetaData("k"));
    }


    @Test
    void testApplyTableAddsLabelAndMetaData()
    {
        JsonNode root = ParquetMetadataCodec
                .parse("{\"tableLabel\":\"My Table\",\"tableMeta\":{\"Standard\":\"ADaM\"}}");
        DataTableMeta.DataTableMetaBuilder b = DataTableMeta.builder().name("T");
        ParquetMetadataCodec.applyTable(root, b);
        DataTableMeta meta = b.rowCount(0).totalRowCount(0).columns(new DataTableColumnMeta[0])
                .build();
        assertEquals("My Table", meta.getLabel());
        assertEquals("ADaM", meta.getMetaData("Standard"));
    }

}
