package net.cumba.datatable.provider.xlsx.library;

import java.net.URI;
import java.util.List;
import java.util.stream.Stream;
import lombok.Setter;
import net.cumba.datatable.impl.library.AbstractDataTableLibrary;
import net.cumba.datatable.io.FileInfo;
import org.jspecify.annotations.Nullable;

public class ExcelLibrary extends AbstractDataTableLibrary
{

    @Setter
    private @Nullable List<ExcelLibraryMember> members;

    public ExcelLibrary(String aName, String aLabel, URI aUri, FileInfo aFileInfo)
    {
        super(aName, aLabel, aUri, aFileInfo);
    }


    @Override
    public String getType()
    {
        return "Excel";
    }


    public Stream<ExcelLibraryMember> getMembers()
    {
        return members != null ? members.stream() : Stream.empty();
    }

}
