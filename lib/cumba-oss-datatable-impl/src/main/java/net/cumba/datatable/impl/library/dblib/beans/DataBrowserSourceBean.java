package net.cumba.datatable.impl.library.dblib.beans;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jspecify.annotations.Nullable;

/**
 * A bean that represents a single source entry in a Data Browser Library. A source is a URI that
 * references a file, directory, or remote resource.
 */
@Value
@Builder
@EqualsAndHashCode
@Jacksonized
public class DataBrowserSourceBean
{

    /**
     * The URI of the source. Required. If the URI uses the file scheme, it is resolved as a local
     * path and may point to a file or directory.
     */
    @NonNull
    private final String uri;

    /**
     * Optional name override for the member. Used when the source resolves to a single member (file
     * or non-file URI). Ignored for directory sources.
     */
    private final String name;

    /**
     * Optional label override for the member. Used when the source resolves to a single member.
     * Ignored for directory sources.
     */
    private final @Nullable String label;

}
