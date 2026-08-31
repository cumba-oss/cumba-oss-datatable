package net.cumba.datatable.provider.cdt;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import org.jspecify.annotations.Nullable;

/**
 * A parsed dataset block from a CDT file. Carries the header, columns, and raw data rows as
 * strings. Conversion to typed values happens in the provider layer.
 */
@Value
@Builder(toBuilder = true)
@SuppressWarnings("cast")
public class CdtDataset
{

    String name;

    /** The optional label from the dataset line. {@code null} when not declared. */
    @Nullable
    String label;

    /**
     * Additional {@code key=value} attributes on the dataset line other than {@code label}. Keys
     * appear in their declared order.
     */
    @Singular("attr")
    Map<String, String> attrs;

    /** Column declarations in the order they appear in the file. */
    @Singular
    List<CdtColumn> columns;

    /**
     * Raw data rows as strings. Each row has one entry per declared column; empty strings represent
     * null fields.
     */
    @Singular
    List<List<String>> dataRows;

    /**
     * The fence line that opened the data block, e.g. {@code "---"} or {@code "-----"}. Preserved
     * so writers can round-trip the same fence length.
     */
    String fence;

    public Map<String, String> getAttrs()
    {
        return attrs != null ? attrs : Collections.emptyMap();
    }


    /**
     * Builds a copy of the attribute map that preserves insertion order. Convenient for callers
     * that want to mutate it locally without depending on Lombok's @Singular unmodifiable wrapper.
     */
    public Map<String, String> copyAttrs()
    {
        return new LinkedHashMap<>(getAttrs());
    }
}
