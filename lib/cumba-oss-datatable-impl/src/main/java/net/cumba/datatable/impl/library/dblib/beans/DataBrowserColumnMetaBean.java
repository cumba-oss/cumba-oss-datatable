package net.cumba.datatable.impl.library.dblib.beans;

import java.util.Map;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * A bean that stores inline column metadata in a Data Browser Library. The URI and column name are
 * used as composite key to match this metadata to a library member column. Relative URIs are
 * resolved against the library file URI.
 */
@Value
@Builder
@EqualsAndHashCode
@Jacksonized
public class DataBrowserColumnMetaBean
{

    /**
     * The URI of the member this column belongs to. Can be absolute or relative to the library
     * file.
     */
    @NonNull
    private final String uri;

    /**
     * The name of the column.
     */
    @NonNull
    private final String name;

    /**
     * Optional label for the column.
     */
    private final String label;

    /**
     * The data value type as string (e.g., "STRING", "DOUBLE", "LONG").
     */
    private final String type;

    /**
     * Optional display format for the column.
     */
    private final String format;

    /**
     * Key order indicator. 0 means not a key.
     */
    private final int key;

    /**
     * Arbitrary key-value metadata attributes for the column.
     */
    private final Map<String, String> attributes;

}
