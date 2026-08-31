package net.cumba.datatable.impl.library.dblib.beans;

import java.util.Map;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * A bean that represents a value format definition stored inline in a Data Browser Library.
 */
@Value
@Builder
@EqualsAndHashCode
@Jacksonized
public class DataBrowserValueFormatBean
{

    /**
     * The name of the value format.
     */
    @NonNull
    private final String name;

    /**
     * Optional label describing the value format.
     */
    private final String label;

    /**
     * The value-to-formatted-value mapping.
     */
    @NonNull
    private final Map<String, String> valueMap;

}
