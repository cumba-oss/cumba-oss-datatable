package net.cumba.datatable.provider.sas.xpt.library;

import java.io.File;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.help.CDT;
import net.cumba.datatable.help.URIHelper;
import net.cumba.datatable.impl.library.AbstractLibraryProvider;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.library.IDataTableLibrary;
import net.cumba.datatable.library.ILibraryMember;
import net.cumba.datatable.library.ILibraryProvider;
import net.cumba.datatable.provider.sas.xpt.XptTableProvider;
import net.cumba.datatable.values.DataValueType;
import net.cumba.sasutils.VariableType;
import net.cumba.sasutils.xpt.DatasetXpt;
import net.cumba.sasutils.xpt.LibraryXpt;
import net.cumba.sasutils.xpt.ParserXpt;
import net.cumba.sasutils.xpt.VariableXpt;
import org.jspecify.annotations.Nullable;

/**
 * Reads SAS Transport (XPT) files as multi-dataset libraries, parsing dataset and variable metadata
 * from the transport file headers.
 */
public class XptLibraryProvider extends AbstractLibraryProvider
{

    private static final Logger LOGGER = System.getLogger(XptLibraryProvider.class.getName());

    @SuppressWarnings("this-escape")
    public XptLibraryProvider()
    {
        setName("XptLibraryProvider");
        setDescription("Provides SAS v5 XPORT files as library");
    }


    @Override
    public List<FileInfo> getSupportedFileInfos()
    {
        return XptLibrarySupplier.FIS;
    }


    // NullAway: IGenericProvider.provide is declared @NonNull, but this provider returns null for a
    // non-file: URI (a contract the LibraryProviderFactory already null-tolerates, and which an
    // existing test pins). The interface lives in a sibling module and cannot be relaxed here, so
    // the narrowed @Nullable return is suppressed rather than the behaviour being changed.
    @Override
    @SuppressWarnings("NullAway")
    public @Nullable IDataTableLibrary provide(URI aUri, @Nullable FileInfo aFileInfo)
        throws IOException
    {
        if (!"file".equalsIgnoreCase(aUri.getScheme()))
        {
            return null;
        }

        File f = new File(aUri);
        LibraryXpt library = new ParserXpt().parseLibrary(f, true);

        List<DatasetXpt> dataSets = library.getDatasets();

        String libName = ILibraryProvider.resolveLibraryName(aUri, aFileInfo,
                getLibraryNameFor(aUri));
        XptLibrary lib = new XptLibrary(libName, null, aUri);

        Charset charset = XptTableProvider.resolveCharset(null);

        List<XptLibraryMember> mems = new ArrayList<>();

        for (DatasetXpt ds : dataSets)
        {
            URI dsUri = URIHelper.replaceFragment(aUri, ds.getName());

            int[] counter =
            {
                    0
            };
            DataTableColumnMeta[] columns = ds.getVariables().stream()//
                    .map(v -> mapToColumnMeta(v, counter[0]++, dsUri))//
                    .toArray(DataTableColumnMeta[]::new);

            XptLibraryMember mem = XptLibraryMember.builder()//
                    .library(lib)//
                    .name(ds.getName())//
                    .label(ds.getLabel())//
                    .columns(columns)//
                    .uri(dsUri)//
                    .charset(charset)//
                    .build();

            mems.add(mem);
        }

        lib.setMembers(mems);

        return lib;
    }


    protected DataTableColumnMeta mapToColumnMeta(VariableXpt aVariable, int aIndex, URI aUri)
    {
        DataValueType type;
        String nativeType = aVariable.getType().toString();
        if (aVariable.getType() == VariableType.NUMERIC)
        {
            type = DataValueType.DOUBLE;
        }
        else
        {
            type = DataValueType.STRING;
        }
        String dispFmt = getFullFormatName(aVariable);

        String columnName = aVariable.getName();
        if (columnName == null)
        {
            columnName = "V" + (aIndex + 1);
            LOGGER.log(Level.WARNING, "XPT column %d has no name; using fallback '%s' (uri=%s)"
                    .formatted(aIndex, columnName, aUri));
        }

        return DataTableColumnMeta.builder()//
                .index(aIndex)//
                .name(columnName)//
                .type(type)//
                .nativeType(nativeType)//
                .label(aVariable.getLabel())//
                .displayFormat(dispFmt)//
                .build();
    }


    protected @Nullable String getFullFormatName(VariableXpt aVariable)
    {
        String name = aVariable.getFormatTypeString();

        if (CDT.isBlankOrNull(name))
        {
            return null;
        }

        StringBuilder sb = new StringBuilder();

        sb.append(name.trim());

        int w = aVariable.getFormatLength();
        int d = aVariable.getFormatDecimals();

        if (w > 0)
        {
            sb.append(w);
        }
        sb.append('.');
        if (d > 0)
        {
            sb.append(d);
        }
        return sb.toString();
    }


    protected String getLibraryNameFor(URI aUri)
    {
        String name = aUri.getPath();
        if (name == null)
        {
            return "";
        }
        name = CDT.getAfterLast(name, '/');
        name = CDT.getBeforeLast(name, '.');
        return name.toUpperCase(Locale.ROOT);
    }


    @Override
    public Stream<? extends ILibraryMember> provideLibraryMembers(IDataTableLibrary aLibrary)
        throws IOException
    {
        if (aLibrary instanceof XptLibrary xptLib)
        {
            return xptLib.getMembers();
        }
        return Stream.empty();
    }


    @Override
    public Stream<? extends DataTableColumnMeta> provideLibraryMemberColumns(ILibraryMember aMember)
        throws IOException
    {
        if (aMember instanceof XptLibraryMember xptMember)
        {
            DataTableColumnMeta[] columns = xptMember.getColumns();
            if (columns != null)
            {
                return Stream.of(columns);
            }
        }
        return Stream.empty();
    }


    @Override
    public @Nullable Object getLibraryAttribute(IDataTableLibrary aLibrary,
            @Nullable String aAttributeKey)
    {
        // TODO Auto-generated method stub
        return null;
    }

}
