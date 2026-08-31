package net.cumba.datatable.provider.parquet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import net.cumba.datatable.DataTableColumnMeta.DataTableColumnMetaBuilder;
import net.cumba.datatable.DataTableMeta.DataTableMetaBuilder;
import org.jspecify.annotations.Nullable;

/**
 * Decodes Cumba-specific table and column metadata from a JSON blob stored in Parquet key-value
 * metadata under the key {@value #META_KEY}. This lets the provider recover the
 * {@link net.cumba.datatable.DataTableMeta} fields (table label, table custom metadata, column
 * label, displayFormat, nativeType, length, column custom metadata) that Parquet has no native slot
 * for.
 *
 * <p>
 * Read-only: no exporter and no R-blob ({@code "r"} key) encoder are included in this project (the
 * latter depended on {@code net.cumba.jrdata}). R-authored column labels are therefore not
 * surfaced; Cumba-native labels from {@value #META_KEY} still are.
 */
final class ParquetMetadataCodec
{

    static final String META_KEY = "cumba:meta";

    private static final String KEY_COLUMNS = "columns";

    private static final String KEY_LABEL = "label";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ParquetMetadataCodec()
    {
    }


    /**
     * Parse a JSON blob previously produced by the Cumba parquet exporter. Returns {@code null} on
     * parse failure or when {@code aJson} is null or blank.
     */
    static @Nullable JsonNode parse(@Nullable String aJson)
    {
        if (aJson == null || aJson.isEmpty())
        {
            return null;
        }
        try
        {
            return MAPPER.readTree(aJson);
        }
        catch (Exception _)
        {
            return null;
        }
    }


    /**
     * Apply the table-level fields (label, custom metadata) from a parsed blob.
     */
    static void applyTable(@Nullable JsonNode aRoot, DataTableMetaBuilder aBuilder)
    {
        if (aRoot == null)
        {
            return;
        }
        JsonNode label = aRoot.get("tableLabel");
        if (label != null && label.isTextual())
        {
            aBuilder.label(label.asText());
        }
        JsonNode tableMeta = aRoot.get("tableMeta");
        if (tableMeta != null && tableMeta.isObject())
        {
            for (Map.Entry<String, JsonNode> e : tableMeta.properties())
            {
                if (e.getValue().isValueNode())
                {
                    aBuilder.addMetaData(e.getKey(), e.getValue().asText());
                }
            }
        }
    }


    /**
     * Apply the column-level slice for one column by looking up {@code aColName} under
     * {@code "columns"} in the parsed root.
     */
    static void applyColumn(@Nullable JsonNode aRoot, String aColName,
            DataTableColumnMetaBuilder aBuilder)
    {
        if (aRoot == null)
        {
            return;
        }
        JsonNode columns = aRoot.get(KEY_COLUMNS);
        if (columns == null || !columns.isObject())
        {
            return;
        }
        JsonNode entry = columns.get(aColName);
        if (entry == null || !entry.isObject())
        {
            return;
        }

        JsonNode label = entry.get(KEY_LABEL);
        if (label != null && label.isTextual())
        {
            aBuilder.label(label.asText());
        }
        JsonNode fmt = entry.get("displayFormat");
        if (fmt != null && fmt.isTextual())
        {
            aBuilder.displayFormat(fmt.asText());
        }
        JsonNode nt = entry.get("nativeType");
        if (nt != null && nt.isTextual())
        {
            aBuilder.nativeType(nt.asText());
        }
        JsonNode length = entry.get("length");
        if (length != null && length.canConvertToInt())
        {
            aBuilder.length(length.asInt());
        }
        JsonNode meta = entry.get("meta");
        if (meta != null && meta.isObject())
        {
            for (Map.Entry<String, JsonNode> e : meta.properties())
            {
                if (e.getValue().isValueNode())
                {
                    aBuilder.addMetaData(e.getKey(), e.getValue().asText());
                }
            }
        }
    }
}
