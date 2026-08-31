package net.cumba.datatable.impl.library.dblib.beans;

import java.util.Map;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.With;
import lombok.extern.jackson.Jacksonized;
import org.jspecify.annotations.Nullable;

/**
 * The top-level bean for a Data Browser Library definition. Serialized as a JSON file with the
 * {@code .dblib} extension.
 */
@Value
@Builder
@EqualsAndHashCode
@Jacksonized
public class DataBrowserLibraryBean
{

    /**
     * The name of the library.
     */
    @With
    private final String name;

    /**
     * The label to be displayed for the library.
     */
    private final String label;

    /**
     * The list of source entries. Each source is a URI that references a file, directory, or remote
     * resource to be included in the library as one or more members.
     */
    private final DataBrowserSourceBean[] sources;

    /**
     * URIs to elements that can be handled as format catalog. This can be any URI pointing to a
     * supported format catalog such as sas7bcat files, define.xml formats, or format tables of any
     * supported data type.
     */
    private final String[] formatCatalogUris;

    /**
     * Internally stored value format definitions. These are stored directly in the library JSON and
     * take priority over external format catalogs.
     */
    private final DataBrowserValueFormatBean[] formats;

    /**
     * URIs of tables that contain member-based metadata. Each table must be readable as an
     * {@code IDataTable} and must contain a {@code uri} column as key. All other columns are
     * applied as member attributes. The {@code uri} column can contain absolute URIs or relative
     * URIs that are resolved against the metadata file URI.
     */
    private final String[] memberMetaTableUris;

    /**
     * URIs of tables that contain column-based metadata. Each table must be readable as an
     * {@code IDataTable} and must contain {@code uri} and {@code name} columns as composite key.
     * All other columns are applied as column attributes. The {@code uri} column can contain
     * absolute URIs or relative URIs that are resolved against the metadata file URI.
     */
    private final String[] columnMetaTableUris;

    /**
     * Internally stored member metadata. Matched to library members by URI. Relative URIs are
     * resolved against the library file URI.
     */
    private final DataBrowserMemberMetaBean[] memberMeta;

    /**
     * Internally stored column metadata. Matched to library member columns by URI and column name.
     * Relative URIs are resolved against the library file URI.
     */
    private final DataBrowserColumnMetaBean[] columnMeta;

    /**
     * Arbitrary key-value attributes for the library. These are exposed through
     * {@link net.cumba.datatable.library.ILibraryProvider#getLibraryAttribute( net.cumba.datatable.library.IDataTableLibrary, String)}.
     */
    private final @Nullable Map<String, String> attributes;

}
