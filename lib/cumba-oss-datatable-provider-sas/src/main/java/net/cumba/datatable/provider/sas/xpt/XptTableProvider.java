package net.cumba.datatable.provider.sas.xpt;

import static net.cumba.datatable.impl.provider.DataTableMetaSupport.META_KEY_CREATED;
import static net.cumba.datatable.impl.provider.DataTableMetaSupport.META_KEY_MODIFIED;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableColumnMeta.DataTableColumnMetaBuilder;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.DataTableMeta.DataTableMetaBuilder;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.help.CDT;
import net.cumba.datatable.help.URIHelper;
import net.cumba.datatable.impl.CachedDataTableColumn;
import net.cumba.datatable.impl.provider.AbstractDataTableProvider;
import net.cumba.datatable.impl.provider.AbstractTableDataParser;
import net.cumba.datatable.impl.provider.DataTableMetaSupport;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.provider.IDataTableProvider;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.MissingValue;
import net.cumba.sasutils.VariableType;
import net.cumba.sasutils.xpt.DatasetXpt;
import net.cumba.sasutils.xpt.LibraryXpt;
import net.cumba.sasutils.xpt.ParserXpt;
import net.cumba.sasutils.xpt.VariableXpt;
import org.jspecify.annotations.Nullable;

/**
 * A data table provider that reads SAS XPT (v5 transport) files using the sas-utils library.
 * Supports multi-dataset XPT libraries via URI fragments. Rows are loaded in slices of 5,000 and
 * columns are processed concurrently using {@link CompletableFuture}.
 */
public class XptTableProvider extends AbstractDataTableProvider
{

    private static final Logger LOGGER = System.getLogger(XptTableProvider.class.getName());

    /**
     * The charset to be used when parsing character values from the XPT file.<br/>
     * The SAS v5 XPORT engine officially supports only latin1 7bit chars, but SAS itself simply
     * exports the data with the session encoding. Unfortunately XPT does not store the used
     * encoding.<br/>
     * We use UTF-8 as default here as this is the standard encoding in most recent versions of SAS.
     */
    @Getter
    @Setter
    private Charset charset = ObservationIteratorXpt.DEFAULT_CHARSET;

    /**
     * Resolve a {@link Charset} from the given charset name, falling back to
     * {@link ObservationIteratorXpt#DEFAULT_CHARSET} if unset or unparseable.
     */
    public static Charset resolveCharset(@Nullable String aCharsetName)
    {
        if (CDT.isBlankOrNull(aCharsetName))
        {
            return ObservationIteratorXpt.DEFAULT_CHARSET;
        }
        // isBlankOrNull(aCharsetName) is false here, so aCharsetName is non-null.
        String name = Objects.requireNonNull(aCharsetName);
        // F-D21: pre-check via Charset.isSupported so we can name the unsupported encoding
        // in the log; previously the catch swallowed both the JVM rejection message and the
        // input name. isSupported() itself throws IllegalCharsetNameException for malformed
        // names — keep a thin catch for that so user-supplied junk doesn't crash the load.
        try
        {
            if (Charset.isSupported(name))
            {
                return Charset.forName(name);
            }
        }
        catch (IllegalArgumentException ex)
        {
            LOGGER.log(Level.WARNING,
                    "XPT charset ''{0}'' is not a legal charset name; falling back to {1}.", name,
                    ObservationIteratorXpt.DEFAULT_CHARSET);
            return ObservationIteratorXpt.DEFAULT_CHARSET;
        }
        LOGGER.log(Level.WARNING,
                "XPT charset ''{0}'' is not supported by the JVM; falling back to {1}.", name,
                ObservationIteratorXpt.DEFAULT_CHARSET);
        return ObservationIteratorXpt.DEFAULT_CHARSET;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public List<FileInfo> getSupportedFileInfos()
    {
        return XptProviderSupplier.FIS;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("PMD.EmptyCatchBlock")
    public IDataTable provide(URI aUri, @Nullable FileInfo aFileInfo) throws IOException
    {
        String fragment = aUri.getFragment();

        if ("file".equalsIgnoreCase(aUri.getScheme()))
        {
            if (CDT.isBlankOrNull(fragment))
            {
                return provide(aUri, new File(aUri));
            }
            else
            {
                URI uri = URIHelper.replaceFragment(aUri, null);
                return provide(aUri, new File(uri), fragment);
            }
        }

        File f = downloadToFile(aUri);
        f.deleteOnExit();
        try
        {
            if (CDT.isBlankOrNull(fragment))
            {
                return provide(aUri, f);
            }
            else
            {
                return provide(aUri, f, fragment);
            }
        }
        finally
        {
            try
            {
                Files.deleteIfExists(f.toPath());
            }
            catch (IOException _)
            {
                // best-effort cleanup
            }
        }
    }


    @SuppressWarnings("PMD.EmptyCatchBlock")
    @Override
    public DataTableMeta provideMetaData(URI aUri, @Nullable FileInfo aFileInfo) throws IOException
    {
        String fragment = aUri.getFragment();

        File f;
        boolean downloaded = false;
        if ("file".equalsIgnoreCase(aUri.getScheme()))
        {
            URI uri = CDT.isBlankOrNull(fragment) ? aUri : URIHelper.replaceFragment(aUri, null);
            f = new File(uri);
        }
        else
        {
            f = downloadToFile(aUri);
            f.deleteOnExit();
            downloaded = true;
        }

        try
        {
            LibraryXpt library = new ParserXpt().parseLibrary(f, true);
            if (library.getDatasets().isEmpty())
            {
                throw new IOException("XPT file contains no datasets");
            }

            DatasetXpt ds;
            if (CDT.isBlankOrNull(fragment))
            {
                ds = library.getDatasets().get(0);
            }
            else
            {
                ds = library.getDatasets().stream()//
                        .filter(d -> d.getName().equalsIgnoreCase(fragment))//
                        .findAny()//
                        .orElseThrow(() -> new IOException(
                                "XPT file does not contain dataset: " + fragment));
            }

            return buildMeta(aUri, ds);
        }
        finally
        {
            if (downloaded)
            {
                try
                {
                    Files.deleteIfExists(f.toPath());
                }
                catch (IOException _)
                {
                    // best-effort cleanup
                }
            }
        }
    }


    /**
     * Build a {@link DataTableMeta} from a pre-parsed XPT dataset descriptor without iterating any
     * observations.
     */
    protected DataTableMeta buildMeta(URI aUri, DatasetXpt aDataSet)
    {
        DataTableMetaSupport dtms = new DataTableMetaSupport(getMetadata());

        dtms.setTable(aUri, aDataSet.getName());
        dtms.setFileFormat("XPORT", "5");
        String dsLabel = aDataSet.getLabel();
        if (!CDT.isBlankOrNull(dsLabel))
        {
            dtms.getTableMeta().label(dsLabel);
        }

        List<VariableXpt> vars = aDataSet.getVariables();
        for (int i = 0; i < vars.size(); i++)
        {
            addColumn(dtms, vars.get(i), i, aUri);
        }

        applyTableMetaData(dtms, aDataSet);

        return dtms.getTableMeta().build();
    }


    /**
     * Read the first dataset from the given XPT file.
     *
     * @param aUri
     *            the original URI (used for metadata).
     * @param aFile
     *            the XPT file.
     * @return the parsed data table.
     * @throws IOException
     *             in case of any I/O error.
     */
    public IDataTable provide(URI aUri, File aFile) throws IOException
    {
        LibraryXpt library = new ParserXpt().parseLibrary(aFile, false);

        if (library.getDatasets().isEmpty())
        {
            throw new IOException("XPT file contains no datasets");
        }
        DatasetXpt ds = library.getDatasets().get(0);

        return provide(aUri, aFile, ds);
    }


    /**
     * Read a specific named dataset from the given XPT file.
     *
     * @param aFile
     *            the XPT file.
     * @param aUri
     *            the original URI (used for metadata).
     * @param aDataSetName
     *            the dataset name to look up (case-insensitive).
     * @return the parsed data table.
     * @throws IOException
     *             in case of any I/O error, or if the named dataset is not present in the file.
     */
    public IDataTable provide(URI aUri, File aFile, String aDataSetName) throws IOException
    {
        LibraryXpt library = new ParserXpt().parseLibrary(aFile, true);

        Optional<DatasetXpt> ds = library.getDatasets().stream()
                .filter(d -> d.getName().equalsIgnoreCase(aDataSetName)).findAny();

        if (ds.isPresent())
        {
            return provide(aUri, aFile, ds.get());
        }
        throw new IOException("XPT file does not contain dataset: " + aDataSetName);
    }


    /**
     * Read a specific dataset from the given XPT file into an {@link IDataTable}.
     *
     * @param aFile
     *            the XPT file.
     * @param aUri
     *            the original URI (used for metadata).
     * @param aDataSet
     *            the parsed dataset descriptor.
     * @return the parsed data table.
     * @throws IOException
     *             in case of any I/O error.
     */
    public IDataTable provide(URI aUri, File aFile, DatasetXpt aDataSet) throws IOException
    {
        DataTableMetaSupport dtms = new DataTableMetaSupport(getMetadata());

        dtms.setTable(aUri, aDataSet.getName());
        dtms.setFileFormat("XPORT", "5");
        String dsLabel = aDataSet.getLabel();
        if (!CDT.isBlankOrNull(dsLabel))
        {
            dtms.getTableMeta().label(dsLabel);
        }
        List<VariableXpt> vars = aDataSet.getVariables();

        for (int i = 0; i < vars.size(); i++)
        {
            addColumn(dtms, vars.get(i), i, aUri);
        }

        applyTableMetaData(dtms, aDataSet);

        DataTableMeta meta = dtms.getTableMeta().build();

        XptTableDataParser tableParser = new XptTableDataParser(this, meta);

        try (FileInputStream fin = new FileInputStream(aFile))
        {
            // 262_144 = 256*1024
            BufferedInputStream bin = new BufferedInputStream(fin, 262_144);
            ObservationIteratorXpt iter = new ObservationIteratorXpt(aDataSet, getCharset(), bin);

            while (iter.hasNext())
            {
                tableParser.addDataRow(iter.next());
            }
        }
        catch (IllegalStateException ex)
        {
            // F-D16: ObservationIteratorXpt wraps underlying IOExceptions as
            // IllegalStateException to honour the Iterator contract. Restore the declared
            // throws IOException by unwrapping the cause where present.
            if (ex.getCause() instanceof IOException ioe)
            {
                throw ioe;
            }
            throw ex;
        }

        return tableParser.completeTable();

    }


    /**
     * Create a new {@link CachedDataTableColumn} for the given column metadata.
     *
     * @param aMeta
     *            the column metadata.
     * @return the new column instance.
     */
    protected CachedDataTableColumn createColumnFor(DataTableColumnMeta aMeta)
    {
        return new CachedDataTableColumn(aMeta.getIndex(), aMeta.getType());
    }


    protected void addColumn(DataTableMetaSupport aSupport, VariableXpt aVariable, int aIndex,
            URI aUri)
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

        String columnName = aVariable.getName();
        if (columnName == null)
        {
            columnName = "V" + (aIndex + 1);
            LOGGER.log(Level.WARNING, "XPT column %d has no name; using fallback '%s' (uri=%s)"
                    .formatted(aIndex, columnName, aUri));
        }

        DataTableColumnMetaBuilder b = aSupport.addColumn(columnName, type)//
                .nativeType(nativeType)//
                .length(aVariable.getLength());

        String dispFmt = getFullFormatName(aVariable);

        if (!CDT.isBlankOrNull(dispFmt))
        {
            b.displayFormat(dispFmt);
        }

        if (!CDT.isBlankOrNull(aVariable.getLabel()))
        {
            b.label(aVariable.getLabel());
        }
    }


    protected void applyTableMetaData(DataTableMetaSupport aSupport, DatasetXpt aDataSet)
    {
        SimpleDateFormat sdtf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

        DataTableMetaBuilder b = aSupport.getTableMeta();

        if (aDataSet.getCreated() != null)
        {
            Date created = Date
                    .from(aDataSet.getCreated().atZone(ZoneId.systemDefault()).toInstant());
            b.addMetaData(META_KEY_CREATED, sdtf.format(created));
        }
        if (aDataSet.getModified() != null)
        {
            Date modified = Date
                    .from(aDataSet.getModified().atZone(ZoneId.systemDefault()).toInstant());
            b.addMetaData(META_KEY_MODIFIED, sdtf.format(modified));
        }
        if (!CDT.isBlankOrNull(aDataSet.getLabel()))
        {
            b.label(aDataSet.getLabel());
        }
    }


    /**
     * Build the full SAS display format name string from the XPT variable's format fields. A
     * variable without an explicit stored format returns {@code null} (no synthetic "$w." default
     * is produced for character columns), consistent with {@code XptLibraryProvider} and the BDAT
     * provider.
     *
     * @param aVariable
     *            the XPT variable descriptor.
     * @return the format string (e.g. "BEST12.2", "DATE9."), or {@code null} when the variable has
     *         no stored format.
     */
    protected @Nullable String getFullFormatName(VariableXpt aVariable)
    {
        String name = aVariable.getFormatTypeString();
        int w = aVariable.getFormatLength();
        int d = aVariable.getFormatDecimals();

        // No stored format → no display format (consistent with XptLibraryProvider and the BDAT
        // provider; a synthetic "$w." default for character columns is intentionally not produced).
        if (CDT.isBlankOrNull(name))
        {
            return null;
        }

        StringBuilder sb = new StringBuilder();

        sb.append(name.trim());

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


    /**
     * Stream a non-{@code file:} URI to a local temporary file so the random-access XPT parser can
     * read it. corej's {@code AbstractGenericProvider} does not provide a {@code downloadToFile}
     * helper (it was dropped in the OSS extraction), so the provider supplies its own.
     *
     * @param aUri
     *            the source URI.
     * @return a temporary file holding the downloaded content (caller deletes it).
     * @throws IOException
     *             on any I/O error.
     */
    protected File downloadToFile(URI aUri) throws IOException
    {
        File tmp = File.createTempFile("cumba-oss-xpt-", ".xpt");
        try (InputStream in = aUri.toURL().openStream())
        {
            Files.copy(in, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException | RuntimeException ex)
        {
            // Don't leak the temp file if the download fails before it is handed back to the
            // caller (the caller is responsible for cleanup only once we return it).
            Files.deleteIfExists(tmp.toPath());
            throw ex;
        }
        return tmp;
    }

    private static class XptTableDataParser extends AbstractTableDataParser<XptObservation>
    {

        XptTableDataParser(@NonNull IDataTableProvider aProvider, @NonNull DataTableMeta aMeta)
        {
            super(aProvider, aMeta);
        }


        @Override
        protected void addData2Column(List<XptObservation> aRowSlice, int aColumnIndex,
                DataTableColumnMeta aMetaColumn, CachedDataTableColumn aDataColumn)
        {
            int rowCount = aRowSlice.size();
            for (int ridx = 0; ridx < rowCount; ridx++)
            {
                Object val = aRowSlice.get(ridx).getValue(aColumnIndex);
                switch (val)
                {
                // CDT.tri is poly-null: a non-null argument yields a non-null result, but NullAway
                // cannot express that contract, so capture and assert non-null here.
                case String str -> aDataColumn.addElement(Objects.requireNonNull(tri(str)));
                case Number num ->
                {
                    double dbl = num.doubleValue();
                    if (Double.isNaN(dbl))
                    {
                        aDataColumn
                                .addElement(MissingValue.forValue(dbl, MissingValue.MIS_UNKNOWN));
                    }
                    else
                    {
                        aDataColumn.addElement(dbl);
                    }
                }
                default -> aDataColumn.addElement(MissingValue.MIS_UNKNOWN);
                }
            }
        }

    }
}
