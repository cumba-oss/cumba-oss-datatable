package net.cumba.datatable.provider.define.library;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.library.ILibraryProvider;
import net.cumba.datatable.library.ILibrarySupplier;
import org.jspecify.annotations.Nullable;

public class DefineXmlLibrarySupplier implements ILibrarySupplier
{

    private static final String ID_FI_DEFINE_XML = "3c901eb0-f7c4-489d-94b4-0136ee99b97a";

    public static final FileInfo FI_DEFINE_XML = FileInfo.create("xml", "(?i)define\\.xml",
            "Define XML (define.xml)", ID_FI_DEFINE_XML);

    @Override
    public List<FileInfo> getSupportedFileInfos()
    {
        return Arrays.asList(FI_DEFINE_XML);
    }


    @Override
    public boolean canProvideFor(URI aUri, @Nullable FileInfo aFileInfo)
    {
        if (aFileInfo != null)
        {
            return aFileInfo.equals(FI_DEFINE_XML);
        }

        String path = aUri.getPath();
        if (path == null)
        {
            return false;
        }
        return path.toLowerCase(Locale.ROOT).endsWith("/define.xml");
    }


    @Override
    public @Nullable ILibraryProvider getProvider(URI aUri, @Nullable FileInfo aFileInfo)
    {
        if (canProvideFor(aUri, aFileInfo))
        {
            return new DefineXmlLibraryProvider();
        }
        return null;
    }

}
