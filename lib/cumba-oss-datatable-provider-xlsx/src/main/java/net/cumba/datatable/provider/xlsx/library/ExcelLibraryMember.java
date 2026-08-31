package net.cumba.datatable.provider.xlsx.library;

import java.net.URI;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.library.ILibraryMember;
import org.jspecify.annotations.Nullable;

@Getter
@Builder
@AllArgsConstructor
public class ExcelLibraryMember implements ILibraryMember
{

    private final String name;

    private final String label;

    private final URI uri;

    private final ExcelLibrary library;

    /**
     * Cached column metadata sampled at library-open time. May be {@code null} if the library was
     * built by a path that does not pre-infer columns (e.g. tests). When present, the library tree
     * surfaces these without re-opening the workbook. F-B16.
     */
    private final DataTableColumnMeta @Nullable [] columns;
}
