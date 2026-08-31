package net.cumba.datatable.provider.cdt.library;

import java.net.URI;
import java.util.List;
import java.util.stream.Stream;

import lombok.Setter;
import net.cumba.datatable.impl.library.AbstractDataTableLibrary;
import net.cumba.datatable.provider.cdt.CdtProviderSupplier;

import org.jspecify.annotations.Nullable;

/**
 * A multi-dataset library view of a single {@code .cdt} file. Each block in the file becomes one
 * {@link CdtLibraryMember}.
 */
public class CdtLibrary extends AbstractDataTableLibrary
{

    @Setter
    private @Nullable List<CdtLibraryMember> members;

    public CdtLibrary(String aName, @Nullable String aLabel, URI aUri)
    {
        super(aName, aLabel, aUri, CdtProviderSupplier.FI_CDT);
    }


    @Override
    public String getType()
    {
        return "Cdt";
    }


    public Stream<CdtLibraryMember> getMembers()
    {
        return members != null ? members.stream() : Stream.empty();
    }
}
