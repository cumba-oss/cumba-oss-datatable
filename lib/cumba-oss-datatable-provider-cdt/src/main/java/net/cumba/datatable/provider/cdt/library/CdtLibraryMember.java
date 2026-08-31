package net.cumba.datatable.provider.cdt.library;

import java.net.URI;

import lombok.Builder;
import lombok.Getter;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.library.ILibraryMember;

import org.jspecify.annotations.Nullable;

/**
 * A single dataset member within a {@link CdtLibrary}.
 */
@Getter
@Builder
public class CdtLibraryMember implements ILibraryMember
{

    private final String name;

    private final @Nullable String label;

    private final URI uri;

    private final CdtLibrary library;

    private final DataTableColumnMeta @Nullable [] columns;
}
