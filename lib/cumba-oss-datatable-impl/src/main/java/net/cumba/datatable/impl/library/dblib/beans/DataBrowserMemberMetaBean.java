package net.cumba.datatable.impl.library.dblib.beans;

import java.util.Map;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * A bean that stores inline member metadata in a Data Browser Library. The URI is used to match
 * this metadata to a library member. Relative URIs are resolved against the library file URI.
 */
@Value
@Builder
@EqualsAndHashCode
@Jacksonized
public class DataBrowserMemberMetaBean
{

    /**
     * The URI of the member this metadata applies to. Can be absolute or relative to the library
     * file.
     */
    @NonNull
    private final String uri;

    /**
     * Optional label for the member.
     */
    private final String label;

    /**
     * Arbitrary key-value metadata attributes for the member.
     */
    private final Map<String, String> attributes;

}
