package net.cumba.datatable.provider.cdt;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.help.CDT;
import net.cumba.datatable.help.URIHelper;
import net.cumba.datatable.impl.provider.AbstractDataTableProvider;
import net.cumba.datatable.io.FileInfo;

import org.jspecify.annotations.Nullable;

/**
 * Reads a single dataset from a CDT file. When the URI carries a fragment, the dataset with that
 * name is returned (case-insensitive); otherwise the first dataset in the file is returned.
 */
public class CdtTableProvider extends AbstractDataTableProvider
{

    @SuppressWarnings("this-escape")
    public CdtTableProvider()
    {
        setName("CdtTableProvider");
        setDescription("Provides CDT (Cumba Data Table) text-format files as data tables.");
    }


    @Override
    public List<FileInfo> getSupportedFileInfos()
    {
        return CdtProviderSupplier.FIS;
    }


    @Override
    public IDataTable provide(URI aUri, @Nullable FileInfo aFileInfo) throws IOException
    {
        String content = readContent(aUri);
        String fragment = aUri.getFragment();
        String source = aUri.toString();

        CdtDataset ds;
        if (CDT.isBlankOrNull(fragment))
        {
            List<CdtDataset> all = CdtParser.parseAll(content, source);
            ds = all.get(0);
        }
        else
        {
            ds = CdtParser.parseNamed(content, source, fragment);
            if (ds == null)
            {
                throw new IOException("CDT file does not contain dataset: " + fragment);
            }
        }

        URI tableUri = CDT.isBlankOrNull(fragment) ? URIHelper.replaceFragment(aUri, ds.getName())
                : aUri;
        return CdtTableBuilder.build(ds, tableUri);
    }


    @Override
    public DataTableMeta provideMetaData(URI aUri, @Nullable FileInfo aFileInfo) throws IOException
    {
        String content = readContent(aUri);
        String fragment = aUri.getFragment();
        String source = aUri.toString();

        CdtDataset ds;
        if (CDT.isBlankOrNull(fragment))
        {
            List<CdtDataset> all = CdtParser.parseAll(content, source);
            ds = all.get(0);
        }
        else
        {
            ds = CdtParser.parseNamed(content, source, fragment);
            if (ds == null)
            {
                throw new IOException("CDT file does not contain dataset: " + fragment);
            }
        }
        URI tableUri = CDT.isBlankOrNull(fragment) ? URIHelper.replaceFragment(aUri, ds.getName())
                : aUri;
        return CdtTableBuilder.buildMeta(ds, tableUri);
    }


    private String readContent(URI aUri) throws IOException
    {
        if ("file".equalsIgnoreCase(aUri.getScheme()))
        {
            URI fileUri = CDT.isBlankOrNull(aUri.getFragment()) ? aUri
                    : URIHelper.replaceFragment(aUri, null);
            return stripBom(Files.readString(Path.of(fileUri), StandardCharsets.UTF_8));
        }
        try (InputStream in = aUri.toURL().openStream())
        {
            return stripBom(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    /** Unicode BOM ({@code U+FEFF}). UTF-8 encoded files may have this as their first character. */
    private static final char UTF8_BOM = '\uFEFF';

    /**
     * Strips a leading UTF-8 BOM ({@code U+FEFF}) from the given text if present. Java's
     * {@link Files#readString(Path, java.nio.charset.Charset)} decodes the BOM into a literal
     * {@code U+FEFF} character on the first line, which would otherwise leak into the first parsed
     * token.
     */
    public static String stripBom(String aContent)
    {
        if (aContent != null && !aContent.isEmpty() && aContent.charAt(0) == UTF8_BOM)
        {
            return aContent.substring(1);
        }
        return aContent;
    }
}
