package net.cumba.datatable.provider.cdt.library;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableColumnMeta.DataTableColumnMetaBuilder;
import net.cumba.datatable.help.CDT;
import net.cumba.datatable.help.URIHelper;
import net.cumba.datatable.impl.library.AbstractLibraryProvider;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.io.Property;
import net.cumba.datatable.library.IDataTableLibrary;
import net.cumba.datatable.library.ILibraryMember;
import net.cumba.datatable.library.ILibraryProvider;
import net.cumba.datatable.provider.cdt.CdtColumn;
import net.cumba.datatable.provider.cdt.CdtDataset;
import net.cumba.datatable.provider.cdt.CdtParser;
import net.cumba.datatable.provider.cdt.CdtTableBuilder;
import net.cumba.datatable.provider.cdt.CdtTableProvider;
import net.cumba.datatable.provider.cdt.CdtValues;

import org.jspecify.annotations.Nullable;

/**
 * Reads a {@code .cdt} file as a multi-dataset library. Each dataset block in the file becomes a
 * {@link CdtLibraryMember}; {@link net.cumba.datatable.provider.cdt.CdtTableProvider} is used to
 * materialise a member's rows.
 */
public class CdtLibraryProvider extends AbstractLibraryProvider
{

    @SuppressWarnings("this-escape")
    public CdtLibraryProvider()
    {
        setName("CdtLibraryProvider");
        setDescription("Provides CDT (Cumba Data Table) text-format files as libraries.");
    }


    @Override
    public List<FileInfo> getSupportedFileInfos()
    {
        return CdtLibrarySupplier.FIS;
    }


    @Override
    public List<Property> getProviderProperties(URI aUri, @Nullable FileInfo aFileInfo)
    {
        return List
                .of(ILibraryProvider.libraryNameProperty(aUri, aFileInfo, getLibraryNameFor(aUri)));
    }


    @Override
    public IDataTableLibrary provide(URI aUri, @Nullable FileInfo aFileInfo) throws IOException
    {
        return provide(aUri, aFileInfo, Map.of());
    }


    @Override
    public IDataTableLibrary provide(URI aUri, @Nullable FileInfo aFileInfo,
            Map<Property, String> aProperties)
        throws IOException
    {
        String content = readContent(aUri);
        List<CdtDataset> datasets = CdtParser.parseAll(content, aUri.toString());

        String libName = ILibraryProvider.resolveLibraryName(aUri, aFileInfo, aProperties,
                getLibraryNameFor(aUri));
        CdtLibrary lib = new CdtLibrary(libName, null, aUri);

        List<CdtLibraryMember> members = new ArrayList<>(datasets.size());
        for (CdtDataset ds : datasets)
        {
            URI memberUri = URIHelper.replaceFragment(aUri, ds.getName());
            DataTableColumnMeta[] cols = mapColumns(ds);
            members.add(CdtLibraryMember.builder()//
                    .library(lib)//
                    .name(ds.getName())//
                    .label(ds.getLabel())//
                    .uri(memberUri)//
                    .columns(cols)//
                    .build());
        }
        lib.setMembers(members);
        return lib;
    }


    @Override
    public Stream<? extends ILibraryMember> provideLibraryMembers(IDataTableLibrary aLibrary)
        throws IOException
    {
        if (aLibrary instanceof CdtLibrary lib)
        {
            return lib.getMembers();
        }
        return Stream.empty();
    }


    @Override
    public Stream<? extends DataTableColumnMeta> provideLibraryMemberColumns(ILibraryMember aMember)
        throws IOException
    {
        if (aMember instanceof CdtLibraryMember m && m.getColumns() != null)
        {
            return Stream.of(m.getColumns());
        }
        return Stream.empty();
    }


    private static DataTableColumnMeta[] mapColumns(CdtDataset aDataset)
    {
        List<CdtColumn> src = aDataset.getColumns();
        DataTableColumnMeta[] cols = new DataTableColumnMeta[src.size()];
        for (int i = 0; i < src.size(); i++)
        {
            CdtColumn c = src.get(i);
            DataTableColumnMetaBuilder b = DataTableColumnMeta.builder()//
                    .index(i)//
                    .name(c.getName())//
                    .type(CdtValues.toDataValueType(c.getType()))//
                    .nativeType(c.getType().token())//
                    .label(c.getLabel() != null ? c.getLabel() : c.getName())//
                    .displayFormat(c.getFormat());
            // Read once into a local: see CdtTableBuilder.buildMeta - length is a
            // primitive int on the target, so a second getLength() call unboxes unchecked.
            Integer len = c.getLength();
            if (len != null)
            {
                b.length(len);
            }
            if (c.getCodelist() != null)
            {
                b.addMetaData(CdtTableBuilder.COLUMN_META_CODELIST, c.getCodelist());
            }
            cols[i] = b.build();
        }
        return cols;
    }


    private static String getLibraryNameFor(URI aUri)
    {
        if (aUri == null)
        {
            return "";
        }
        String name = aUri.getPath();
        if (name == null)
        {
            return "";
        }
        name = CDT.getAfterLast(name, '/');
        name = CDT.getBeforeLast(name, '.');
        return name.toUpperCase(Locale.ROOT);
    }


    private String readContent(URI aUri) throws IOException
    {
        if ("file".equalsIgnoreCase(aUri.getScheme()))
        {
            URI fileUri = CDT.isBlankOrNull(aUri.getFragment()) ? aUri
                    : URIHelper.replaceFragment(aUri, null);
            return CdtTableProvider
                    .stripBom(Files.readString(Path.of(fileUri), StandardCharsets.UTF_8));
        }
        // Non-file URIs: stream the bytes straight into memory. `.cdt` library files
        // are small (typically a few hundred KB at most), so buffering the whole payload
        // beats round-tripping through a temp file.
        try (var in = aUri.toURL().openStream())
        {
            return CdtTableProvider.stripBom(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
