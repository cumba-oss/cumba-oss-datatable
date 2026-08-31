package net.cumba.datatable.impl.library.dblib;

import java.net.URI;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import net.cumba.datatable.library.ILibraryMember;
import org.jspecify.annotations.Nullable;

/**
 * A member of a {@link DataBrowserLibrary}. Each member represents a single dataset that can be
 * loaded from its URI.
 */
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class DataBrowserMember implements ILibraryMember
{

    @Getter
    @EqualsAndHashCode.Include
    private final DataBrowserLibrary library;

    @Getter
    @EqualsAndHashCode.Include
    private final URI uri;

    @Getter
    private final String name;

    @Getter
    private final @Nullable String label;

    public DataBrowserMember(DataBrowserLibrary aLibrary, URI aUri, String aName,
            @Nullable String aLabel)
    {
        library = aLibrary;
        uri = aUri;
        name = aName;
        label = aLabel;
    }

}
