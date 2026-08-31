package net.cumba.datatable.provider.sas.xpt.library;

import java.net.URI;
import java.util.List;
import java.util.stream.Stream;

import lombok.Setter;
import net.cumba.datatable.impl.library.AbstractDataTableLibrary;
import net.cumba.datatable.provider.sas.xpt.XptProviderSupplier;
import org.jspecify.annotations.Nullable;

/**
 * Library representation for SAS Transport (XPT) files containing multiple datasets.
 */
public class XptLibrary extends AbstractDataTableLibrary
{

    @Setter
    private @Nullable List<XptLibraryMember> members;

    public XptLibrary(String aName, @Nullable String aLabel, URI aUri)
    {
        super(aName, aLabel, aUri, XptProviderSupplier.FI_XPT);
    }


    @Override
    public String getType()
    {
        return "Xport";
    }


    public Stream<XptLibraryMember> getMembers()
    {
        return members != null ? members.stream() : Stream.empty();
    }

}
