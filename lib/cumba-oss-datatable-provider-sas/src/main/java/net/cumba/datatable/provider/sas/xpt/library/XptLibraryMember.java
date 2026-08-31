package net.cumba.datatable.provider.sas.xpt.library;

import java.net.URI;
import java.nio.charset.Charset;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.library.ILibraryMember;

/**
 * A single dataset member within an {@link XptLibrary}, representing one dataset in a SAS Transport
 * (XPT) file.
 */
@Getter
@Builder
@AllArgsConstructor
public class XptLibraryMember implements ILibraryMember
{

    private final String name;

    private final String label;

    private final URI uri;

    private final XptLibrary library;

    private DataTableColumnMeta[] columns;

    /**
     * Charset chosen when the parent XPT library was opened. Used by {@code XptTableProvider} to
     * decode character values without re-prompting the user.
     */
    private final Charset charset;

}
