package net.cumba.datatable.provider.sas.xpt.library;

import java.net.URI;
import java.util.List;
import net.cumba.datatable.help.CDT;
import net.cumba.datatable.impl.library.AbstractLibrarySupplier;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.library.ILibraryProvider;
import net.cumba.datatable.provider.sas.xpt.XptProviderSupplier;
import org.jspecify.annotations.Nullable;

/**
 * SPI supplier that registers SAS Transport (XPT) file format support for library access and
 * creates {@link XptLibraryProvider} instances.
 */
public class XptLibrarySupplier extends AbstractLibrarySupplier
{

    public static final List<FileInfo> FIS = List.of(XptProviderSupplier.FI_XPT);

    public XptLibrarySupplier()
    {
        super(FIS);
    }


    @Override
    public boolean canProvideFor(URI aUri, @Nullable FileInfo aFileInfo)
    {
        if (aUri == null || !CDT.isBlankOrNull(aUri.getFragment()))
        {
            return false;
        }

        return super.canProvideFor(aUri, aFileInfo);
    }


    @Override
    public @Nullable ILibraryProvider getProvider(URI aUri, @Nullable FileInfo aFileInfo)
    {
        if (canProvideFor(aUri, aFileInfo))
        {
            return new XptLibraryProvider();
        }
        return null;
    }

}
