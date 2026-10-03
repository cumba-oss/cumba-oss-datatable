package net.cumba.datatable.impl.library.dblib;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.cumba.datatable.impl.library.dblib.beans.DataBrowserColumnMetaBean;
import net.cumba.datatable.impl.library.dblib.beans.DataBrowserLibraryBean;
import net.cumba.datatable.impl.library.dblib.beans.DataBrowserMemberMetaBean;
import net.cumba.datatable.impl.library.dblib.beans.DataBrowserSourceBean;
import net.cumba.datatable.impl.library.dblib.beans.DataBrowserValueFormatBean;

/**
 * Writes a complete Data Browser library — the {@code .dblib} file, its member data files and any
 * external column-metadata tables it references — into a directory, so a test can drive
 * {@link DataBrowserLibraryProvider} against a real library on disk instead of hand-assembling
 * beans.
 *
 * <p>
 * Every file is written below the directory handed to {@link #in(Path)} (normally a JUnit
 * {@code @TempDir}), and members and metadata tables are referenced by absolute {@code file:} URIs
 * into that directory, so two fixtures never see each other's files and a test can vary a single
 * field without disturbing any shared resource.
 *
 * <p>
 * Usage:
 *
 * <pre>
 *
 * Path dblib = L2LibraryFixture.in(tempDir)//
 *         .name("STUDY1")//
 *         .member("DM", "dm.data", "irrelevant")//
 *         .columnMetaTable("cols.csv", "uri,name,label\n...")//
 *         .write();
 * </pre>
 */
public final class L2LibraryFixture
{

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path dir;

    private final List<DataBrowserSourceBean> sources = new ArrayList<>();

    private final List<DataBrowserColumnMetaBean> columnMeta = new ArrayList<>();

    private final List<DataBrowserMemberMetaBean> memberMeta = new ArrayList<>();

    private final List<DataBrowserValueFormatBean> formats = new ArrayList<>();

    private final List<String> columnMetaTableUris = new ArrayList<>();

    private final List<String> formatCatalogUris = new ArrayList<>();

    private final Map<String, String> attributes = new LinkedHashMap<>();

    private String libraryFileName = "lib.dblib";

    private String name = "L2LIB";

    private String label;

    private L2LibraryFixture(Path aDir)
    {
        dir = aDir;
    }


    /**
     * Start a fixture that writes into the given directory.
     */
    public static L2LibraryFixture in(Path aDir)
    {
        return new L2LibraryFixture(aDir);
    }


    /**
     * The name stored inside the {@code .dblib} file.
     */
    public L2LibraryFixture name(String aName)
    {
        name = aName;
        return this;
    }


    /**
     * The label stored inside the {@code .dblib} file.
     */
    public L2LibraryFixture label(String aLabel)
    {
        label = aLabel;
        return this;
    }


    /**
     * The file name of the library file itself. Defaults to {@code lib.dblib}.
     */
    public L2LibraryFixture libraryFileName(String aFileName)
    {
        libraryFileName = aFileName;
        return this;
    }


    /**
     * Write a member data file and register it as a library source.
     *
     * @param aMemberName
     *            the member name the library should report.
     * @param aFileName
     *            the file name to write below the fixture directory.
     * @param aContent
     *            the file content. The content is never parsed by the library itself — only its
     *            existence matters — so any placeholder will do.
     */
    public L2LibraryFixture member(String aMemberName, String aFileName, String aContent)
        throws IOException
    {
        URI uri = writeFile(aFileName, aContent);
        sources.add(DataBrowserSourceBean.builder().uri(uri.toString()).name(aMemberName).build());
        return this;
    }


    /**
     * Register inline column metadata.
     */
    public L2LibraryFixture columnMeta(DataBrowserColumnMetaBean aColumnMeta)
    {
        columnMeta.add(aColumnMeta);
        return this;
    }


    /**
     * Register inline member metadata.
     */
    public L2LibraryFixture memberMeta(DataBrowserMemberMetaBean aMemberMeta)
    {
        memberMeta.add(aMemberMeta);
        return this;
    }


    /**
     * Register an inline value format.
     */
    public L2LibraryFixture format(String aName, String aLabel, Map<String, String> aValueMap)
    {
        formats.add(DataBrowserValueFormatBean.builder().name(aName).label(aLabel)
                .valueMap(new LinkedHashMap<>(aValueMap)).build());
        return this;
    }


    /**
     * Write an external column-metadata table as CSV and register its URI with the library.
     *
     * @param aFileName
     *            the file name to write below the fixture directory.
     * @param aCsv
     *            the table content; the first line holds the column names.
     */
    public L2LibraryFixture columnMetaTable(String aFileName, String aCsv) throws IOException
    {
        columnMetaTableUris.add(writeFile(aFileName, aCsv).toString());
        return this;
    }


    /**
     * Register a column-metadata table URI without writing a file, for the cases where the table is
     * expected to be unreadable or the URI itself is the subject of the test.
     */
    public L2LibraryFixture columnMetaTableUri(String aUri)
    {
        columnMetaTableUris.add(aUri);
        return this;
    }


    /**
     * Register an external format-catalog URI.
     */
    public L2LibraryFixture formatCatalogUri(String aUri)
    {
        formatCatalogUris.add(aUri);
        return this;
    }


    /**
     * Register a library attribute.
     */
    public L2LibraryFixture attribute(String aKey, String aValue)
    {
        attributes.put(aKey, aValue);
        return this;
    }


    /**
     * Write an arbitrary file below the fixture directory.
     *
     * @return the absolute URI of the written file.
     */
    public URI writeFile(String aFileName, String aContent) throws IOException
    {
        Path file = dir.resolve(aFileName);
        Files.createDirectories(file.getParent());
        Files.writeString(file, aContent, StandardCharsets.UTF_8);
        return file.toUri();
    }


    /**
     * The absolute URI a file of the given name has (or would have) below the fixture directory.
     */
    public URI uriOf(String aFileName)
    {
        return dir.resolve(aFileName).toUri();
    }


    /**
     * Assemble the library bean without writing anything.
     */
    public DataBrowserLibraryBean bean()
    {
        DataBrowserLibraryBean.DataBrowserLibraryBeanBuilder b = DataBrowserLibraryBean.builder()//
                .name(name)//
                .label(label);
        if (!sources.isEmpty())
        {
            b.sources(sources.toArray(new DataBrowserSourceBean[0]));
        }
        if (!columnMeta.isEmpty())
        {
            b.columnMeta(columnMeta.toArray(new DataBrowserColumnMetaBean[0]));
        }
        if (!memberMeta.isEmpty())
        {
            b.memberMeta(memberMeta.toArray(new DataBrowserMemberMetaBean[0]));
        }
        if (!formats.isEmpty())
        {
            b.formats(formats.toArray(new DataBrowserValueFormatBean[0]));
        }
        if (!columnMetaTableUris.isEmpty())
        {
            b.columnMetaTableUris(columnMetaTableUris.toArray(new String[0]));
        }
        if (!formatCatalogUris.isEmpty())
        {
            b.formatCatalogUris(formatCatalogUris.toArray(new String[0]));
        }
        if (!attributes.isEmpty())
        {
            b.attributes(new LinkedHashMap<>(attributes));
        }
        return b.build();
    }


    /**
     * Write the {@code .dblib} file and return its path.
     */
    public Path write() throws IOException
    {
        Path file = dir.resolve(libraryFileName);
        Files.createDirectories(file.getParent());
        MAPPER.writeValue(file.toFile(), bean());
        return file;
    }


    /**
     * Write the {@code .dblib} file and return its URI.
     */
    public URI writeUri() throws IOException
    {
        return write().toUri();
    }
}
