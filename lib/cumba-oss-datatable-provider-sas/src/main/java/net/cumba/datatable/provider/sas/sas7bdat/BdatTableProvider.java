package net.cumba.datatable.provider.sas.sas7bdat;

import static net.cumba.datatable.impl.provider.DataTableMetaSupport.META_KEY_CREATED;
import static net.cumba.datatable.impl.provider.DataTableMetaSupport.META_KEY_ENCODING;
import static net.cumba.datatable.impl.provider.DataTableMetaSupport.META_KEY_MODIFIED;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.net.URI;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import lombok.NonNull;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableColumnMeta.DataTableColumnMetaBuilder;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.DataTableMeta.DataTableMetaBuilder;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.help.CDT;
import net.cumba.datatable.help.URIHelper;
import net.cumba.datatable.impl.CachedDataTableColumn;
import net.cumba.datatable.impl.ColumnCachedDataTable;
import net.cumba.datatable.impl.databuffer.AbstractDataBuffer;
import net.cumba.datatable.impl.provider.AbstractDataTableProvider;
import net.cumba.datatable.impl.provider.AbstractTableDataParser;
import net.cumba.datatable.impl.provider.DataTableMetaSupport;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.MissingValue;
import net.cumba.sasutils.PositionAwareInputStream;
import net.cumba.sasutils.VariableType;
import net.cumba.sasutils.bdat.DatasetBdat;
import net.cumba.sasutils.bdat.FormatAndLabelSubHeader;
import net.cumba.sasutils.bdat.ObservationIteratorBdat2;
import net.cumba.sasutils.bdat.ParserBdat;
import net.cumba.sasutils.bdat.VariableBdat;
import org.jspecify.annotations.Nullable;

/**
 * A data table provider that reads SAS sas7bdat (BDAT) files using the sas-utils library. Rows are
 * loaded in slices of 5,000 and columns are processed concurrently using {@link CompletableFuture}.
 */
public class BdatTableProvider extends AbstractDataTableProvider
{

    private static final Logger LOGGER = System.getLogger(BdatTableProvider.class.getName());

    /**
     * Renders the header's created/modified stamps. SAS stores them without a time zone, so the
     * wall-clock value is formatted as is; no zone conversion is applied.
     */
    private static final DateTimeFormatter META_TIMESTAMP = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT);

    /**
     * The integer (one or two bytes) at the {@code SasFileConstants.ENCODING_OFFSET} indicates the
     * character encoding of string data. The SAS_CHARACTER_ENCODINGS map links the values that are
     * known to occur and the associated encoding. This list excludes encodings present in SAS but
     * missing support in {@link java.nio.charset} <br/>
     * This list is copied from parso library (https://github.com/epam/parso)
     */
    public static final Map<Byte, String> SAS_CHARACTER_ENCODINGS;

    static
    {
        Map<Byte, String> map = new HashMap<>();

        put(map, 0x14, "UTF-8");
        put(map, 0x1C, "US-ASCII");
        put(map, 0x1D, "ISO-8859-1");
        put(map, 0x1E, "ISO-8859-2");
        put(map, 0x1F, "ISO-8859-3");

        put(map, 0x20, "ISO-8859-4");
        put(map, 0x21, "ISO-8859-5");
        put(map, 0x22, "ISO-8859-6");
        put(map, 0x23, "ISO-8859-7");
        put(map, 0x24, "ISO-8859-8");
        put(map, 0x25, "ISO-8859-9");
        put(map, 0x27, "x-iso-8859-11");
        put(map, 0x28, "ISO-8859-15");
        put(map, 0x2B, "IBM437");
        put(map, 0x2C, "IBM850");
        put(map, 0x2D, "IBM852");
        put(map, 0x2E, "IBM00858");
        put(map, 0x2F, "IBM862");

        put(map, 0x33, "IBM866");
        put(map, 0x3A, "IBM857");
        put(map, 0x3C, "windows-1250");
        put(map, 0x3D, "windows-1251");
        put(map, 0x3E, "windows-1252");
        put(map, 0x3F, "windows-1253");

        put(map, 0x40, "windows-1254");
        put(map, 0x41, "windows-1255");
        put(map, 0x42, "windows-1256");
        put(map, 0x43, "windows-1257");
        put(map, 0x44, "windows-1258");
        put(map, 0x45, "x-MacRoman");
        put(map, 0x46, "x-MacArabic");
        put(map, 0x47, "x-MacHebrew");
        put(map, 0x48, "x-MacGreek");
        put(map, 0x49, "x-MacThai");
        put(map, 0x4B, "x-MacTurkish");
        put(map, 0x4C, "x-MacUkraine");
        put(map, 0x4E, "IBM037");

        put(map, 0x57, "IBM424");
        put(map, 0x58, "IBM500");
        put(map, 0x59, "IBM-Thai");
        put(map, 0x5A, "IBM870");
        put(map, 0x5B, "x-IBM875");
        put(map, 0x5F, "x-IBM1025");

        put(map, 0x62, "x-IBM1112");
        put(map, 0x63, "x-IBM1122");
        put(map, 0x66, "IBM424");
        put(map, 0x67, "IBM-Thai");
        put(map, 0x68, "IBM870");
        put(map, 0x69, "x-IBM875");
        put(map, 0x6C, "x-IBM1025");
        put(map, 0x6D, "IBM1026");
        put(map, 0x6E, "IBM1047");
        put(map, 0x6F, "x-IBM1112");

        put(map, 0x70, "x-IBM1122");
        put(map, 0x75, "x-IBM937");
        put(map, 0x76, "x-windows-950");
        put(map, 0x77, "x-EUC-TW");
        put(map, 0x7B, "Big5");
        put(map, 0x7C, "x-IBM935");
        put(map, 0x7D, "GBK");
        put(map, 0x7E, "x-mswin-936");

        put(map, 0x80, "x-IBM1381");
        put(map, 0x81, "x-IBM939");
        put(map, 0x82, "x-IBM930");
        put(map, 0x86, "EUC-JP");
        put(map, 0x88, "x-windows-iso2022jp");
        put(map, 0x89, "x-IBM942");
        put(map, 0x8A, "Shift_JIS");
        put(map, 0x8B, "x-IBM933");
        put(map, 0x8C, "EUC-KR");
        put(map, 0x8D, "x-windows-949");
        put(map, 0x8E, "x-IBM949");

        put(map, 0xA3, "x-MacIceland");
        put(map, 0xA7, "ISO-2022-JP");
        put(map, 0xA8, "ISO-2022-KR");
        put(map, 0xA9, "x-ISO2022-CN-GB");
        put(map, 0xAC, "x-ISO2022-CN-CNS");
        put(map, 0xAD, "IBM037");

        put(map, 0xB7, "IBM01140");
        put(map, 0xB8, "IBM01141");
        put(map, 0xB9, "IBM01142");
        put(map, 0xBA, "IBM01143");
        put(map, 0xBB, "IBM01144");
        put(map, 0xBC, "IBM01145");
        put(map, 0xBD, "IBM01146");
        put(map, 0xBE, "IBM01147");
        put(map, 0xBF, "IBM01148");

        put(map, 0xC0, "IBM01140");
        put(map, 0xC1, "IBM01141");
        put(map, 0xC2, "IBM01142");
        put(map, 0xC3, "IBM01143");
        put(map, 0xC4, "IBM01144");
        put(map, 0xC5, "IBM01145");
        put(map, 0xC6, "IBM01146");
        put(map, 0xC7, "IBM01147");
        put(map, 0xC8, "IBM01148");
        put(map, 0xCD, "GB18030");
        put(map, 0xCF, "x-IBM1097");

        put(map, 0xD0, "x-IBM1097");
        put(map, 0xD3, "IBM01149");
        put(map, 0xD4, "IBM01149");

        put(map, 0xEA, "x-IBM930");
        put(map, 0xEB, "x-IBM933");
        put(map, 0xEC, "x-IBM935");
        put(map, 0xED, "x-IBM937");
        put(map, 0xEE, "x-IBM939");

        put(map, 0xF2, "ISO-8859-13");
        put(map, 0xF5, "x-MacCroatian");
        put(map, 0xF6, "x-MacCyrillic");
        put(map, 0xF7, "x-MacRomania");
        put(map, 0xF8, "JIS_X0201");

        SAS_CHARACTER_ENCODINGS = Collections.unmodifiableMap(map);
    }

    /**
     * Adds an encoding-id → charset-name entry to the given map, failing fast with an
     * {@link IllegalStateException} when the id was already mapped. The current entries contain no
     * duplicates — the guard protects future edits of the table above.
     */
    private static void put(Map<Byte, String> aMap, int aId, String aName)
    {
        String previous = aMap.putIfAbsent((byte) aId, aName);
        if (previous != null)
        {
            throw new IllegalStateException("Duplicate SAS encoding id 0x"
                    + Integer.toHexString(aId) + ": " + previous + " vs " + aName);
        }
    }


    /**
     * Map a SAS encoding byte from the BDAT file header to the corresponding Java charset name.
     *
     * @param encodingByte
     *            the encoding byte (low 8 bits used).
     * @return the Java charset name when the byte is known, or {@code null} when the byte does not
     *         appear in the SAS encoding table. Callers are expected to log the raw byte at ERROR
     *         and fall back to a safe charset (typically {@code ISO-8859-1}).
     */
    public static @Nullable String getEncodingName(int encodingByte)
    {
        return SAS_CHARACTER_ENCODINGS.get((byte) encodingByte);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public List<FileInfo> getSupportedFileInfos()
    {
        return BdatProviderSupplier.FIS;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public IDataTable provide(URI aUri, @Nullable FileInfo aFileInfo) throws IOException
    {
        try (InputStream in = URIHelper.openStream(aUri))
        {
            try (PositionAwareInputStream pais = new PositionAwareInputStream(in))
            {
                DatasetBdat bdat = new ParserBdat().parseDataset(pais);
                return provide(aUri, pais, bdat, mmapCandidate(aUri));
            }
        }
    }


    @Override
    public DataTableMeta provideMetaData(URI aUri, @Nullable FileInfo aFileInfo) throws IOException
    {
        try (InputStream in = URIHelper.openStream(aUri))
        {
            try (PositionAwareInputStream pais = new PositionAwareInputStream(in))
            {
                DatasetBdat bdat = new ParserBdat().parseDataset(pais);
                return buildMeta(aUri, bdat);
            }
        }
    }


    /**
     * Resolve the BDAT-header encoding byte to a {@link Charset}. F-D21:
     * <ul>
     * <li>known byte + JVM-supported → that charset</li>
     * <li>known byte but unsupported by the JVM → WARN naming both, fall back to ISO-8859-1</li>
     * <li>unmapped byte → ERROR naming the raw byte, fall back to ISO-8859-1</li>
     * </ul>
     * The user can then diagnose round-trip mojibake by inspecting the log.
     */
    static Charset resolveCharset(int aEncodingByte)
    {
        String encodingName = getEncodingName(aEncodingByte);
        if (encodingName == null)
        {
            LOGGER.log(Level.ERROR,
                    "BDAT encoding byte 0x{0} is not in the SAS encoding table; "
                            + "falling back to ISO-8859-1. Character columns may not "
                            + "decode correctly.",
                    Integer.toHexString(aEncodingByte));
            return StandardCharsets.ISO_8859_1;
        }
        if (!Charset.isSupported(encodingName))
        {
            LOGGER.log(Level.WARNING,
                    "BDAT encoding {0} (id=0x{1}) not supported by the JVM; falling back to"
                            + " ISO-8859-1. Character columns may not decode correctly.",
                    encodingName, Integer.toHexString(aEncodingByte));
            return StandardCharsets.ISO_8859_1;
        }
        return Charset.forName(encodingName);
    }


    /**
     * Build a {@link DataTableMeta} from a pre-parsed BDAT dataset descriptor without touching any
     * observation data.
     */
    protected DataTableMeta buildMeta(URI aUri, DatasetBdat aDataSet)
    {
        int encoding = aDataSet.getHeader2().encoding & 0xFF;
        Charset charset = resolveCharset(encoding);

        DataTableMetaSupport dtms = new DataTableMetaSupport(getMetadata());
        dtms.setTable(aUri);
        dtms.setFileFormat("SAS7BDAT", null);
        dtms.setDatasetSize(aUri);

        List<VariableBdat> vars = aDataSet.getVariables();
        for (int i = 0; i < vars.size(); i++)
        {
            addColumn(dtms, vars.get(i), i, aUri);
        }

        // F-prov-14: the LIVE row count, not the header's. DatasetBdat.getRowCount() counts the
        // rows SAS has marked deleted as well -- which is why provide() has to reconcile them in
        // checkAllRowsRead and why BdatRowAccountingTest pins header 10 / declared 2 / delivered 8
        // as agreement. buildMeta() reads no observations, so it cannot count them; it can however
        // subtract the count the header itself declares, which is exactly the figure provide()
        // then delivers. Before this, provideMetaData() over-reported by the deleted-row count and
        // a metadata-only consumer (the library browser's row-count column, a preview, the P21
        // metadata) disagreed with the table the user went on to open.
        long liveRows = clampExpectedRows(requireRowCount(aDataSet),
                deletedObservationCount(aDataSet));

        applyTableMetaData(dtms, aDataSet, liveRows, charset);

        return dtms.getTableMeta().build();
    }


    /**
     * Parse and provide a data table from the given BDAT file.
     *
     * @param aUri
     *            the original URI (used for metadata).
     * @param aFile
     *            the local file.
     * @return the parsed data table.
     * @throws IOException
     *             in case of any I/O error.
     */
    public IDataTable provide(URI aUri, File aFile) throws IOException
    {
        DatasetBdat bdat = new ParserBdat().parseDataset(aFile);
        try (InputStream in = new FileInputStream(aFile))
        {
            return provide(aUri, in, bdat, isMmapEnabled() ? aFile : null);
        }
    }


    /**
     * Parse and provide a data table from a pre-parsed BDAT dataset (stream path; no mmap).
     *
     * @param aUri
     *            the original URI (used for metadata).
     * @param aStream
     *            the input stream to read the dataset rows from.
     * @param aDataSet
     *            the pre-parsed BDAT dataset descriptor.
     * @return the parsed data table.
     * @throws IOException
     *             in case of any I/O error.
     */
    public IDataTable provide(URI aUri, InputStream aStream, DatasetBdat aDataSet)
        throws IOException
    {
        return provide(aUri, aStream, aDataSet, null);
    }


    /**
     * Parse and provide a data table from a pre-parsed BDAT dataset.
     *
     * @param aUri
     *            the original URI (used for metadata).
     * @param aStream
     *            the input stream to read the dataset rows from (used unless mmap is taken).
     * @param aDataSet
     *            the pre-parsed BDAT dataset descriptor.
     * @param aMmapFile
     *            when non-{@code null} (a local file) and the parallel + mmap fast path is enabled,
     *            rows are read via a memory-mapped segment with parallel per-page reads instead of
     *            the stream; otherwise this is ignored.
     * @return the parsed data table.
     * @throws IOException
     *             in case of any I/O error.
     */
    public IDataTable provide(URI aUri, InputStream aStream, DatasetBdat aDataSet,
            @Nullable File aMmapFile)
        throws IOException
    {
        long totalRows = requireRowCount(aDataSet);
        if (totalRows > AbstractDataBuffer.SOFT_MAX_ARRAY_LENGTH)
        {
            throw new OutOfMemoryError("Too many rows!");
        }

        int encoding = aDataSet.getHeader2().encoding & 0xFF;
        Charset charset = resolveCharset(encoding);

        DataTableMetaSupport dtms = new DataTableMetaSupport(getMetadata());

        dtms.setTable(aUri);
        dtms.setFileFormat("SAS7BDAT", null);
        dtms.setDatasetSize(aMmapFile, aUri);

        List<VariableBdat> vars = aDataSet.getVariables();

        for (int i = 0; i < vars.size(); i++)
        {
            addColumn(dtms, vars.get(i), i, aUri);
        }

        applyTableMetaData(dtms, aDataSet, totalRows, charset);

        List<BdatVarParser> parsers = new ArrayList<>();

        ByteOrder bo = aDataSet.getByteOrder().getByteOrder();

        for (VariableBdat variable : aDataSet.getVariables())
        {
            parsers.add(new BdatVarParser(variable, charset, bo));
        }

        DataTableMeta builtMeta = dtms.getTableMeta().build();

        long identifiedDeletedRecords;
        IDataTable table;

        if (isParallelEnabled())
        {
            long[] deletedOut = new long[1];
            if (aMmapFile != null)
            {
                table = loadMmap(aDataSet, aMmapFile, builtMeta, parsers, deletedOut);
            }
            else
            {
                table = loadParallel(aDataSet, aStream, builtMeta, parsers, deletedOut);
            }
            identifiedDeletedRecords = deletedOut[0];
        }
        else
        {
            BdatTableDataParser tableParser = new BdatTableDataParser(builtMeta);

            ObservationIteratorBdat2 iter = new ObservationIteratorBdat2(aDataSet, aStream);
            while (iter.hasNext())
            {
                tableParser.addDataRow(new BdatObservation(parsers, iter.next()));
            }

            identifiedDeletedRecords = iter.getParsedDeletedRowCount();
            table = tableParser.completeTable();
        }

        checkAllRowsRead(aDataSet.getRowCount(), aDataSet.getDeletedObservationCount(),
                identifiedDeletedRecords, table.getRowCount());
        return table;

    }


    /**
     * Row-accounting guard: every row the header declares must either have been delivered or be
     * accounted for by the header's declared deleted-row count.
     *
     * <p>
     * F-prov-07: the expected row count is derived from the header's <em>declared</em> deleted
     * count, never from the count the reader itself found. Subtracting the found count would make
     * this guard tautological for exactly the failure it exists to catch: a deleted-row
     * misdetection changes the found count and the delivered row count in lockstep, so the two
     * sides of the comparison could never disagree.
     * </p>
     *
     * @param aHeaderRowCount
     *            the total row count the file header declares ({@code null} reads as 0).
     * @param aDeclaredDeleted
     *            the deleted-row count the file header declares.
     * @param aFoundDeleted
     *            the deleted-row count the reader actually identified while reading.
     * @param aActualRows
     *            the number of rows delivered into the table.
     * @throws IOException
     *             if the delivered row count does not match the header's declaration.
     */
    static void checkAllRowsRead(@Nullable Long aHeaderRowCount, long aDeclaredDeleted,
            long aFoundDeleted, long aActualRows)
        throws IOException
    {
        if (aFoundDeleted != aDeclaredDeleted)
        {
            LOGGER.log(Level.WARNING,
                    "Found unexpected number of deleted records. Found {0} but expected {1}.",
                    aFoundDeleted, aDeclaredDeleted);
        }
        else if (aFoundDeleted > 0)
        {
            LOGGER.log(Level.DEBUG, "Found {0} deleted records (as expected).", aFoundDeleted);
        }

        // F-D17: clamp to >= 0 - if a corrupt header declares more deleted records than rows
        // we must not produce a negative expected count (which would always trip the error
        // path below and mislead the user about the actual problem).
        long expectedRows = clampExpectedRows(aHeaderRowCount, aDeclaredDeleted);
        if (expectedRows != aActualRows)
        {
            throw new IOException("Can't read all rows. Expected=%d, found=%d"
                    .formatted(expectedRows, aActualRows));
        }
    }


    /**
     * Whether the parallel page-pipeline loader is enabled. Defaults to {@code true}; set the
     * system property {@code -Dbdat.parallel=false} to fall back to the single-threaded loop
     * (safety net / A-B benchmarking).
     *
     * @return {@code true} unless {@code bdat.parallel} is set to {@code false}.
     */
    static boolean isParallelEnabled()
    {
        return !"false".equalsIgnoreCase(System.getProperty("bdat.parallel"));
    }


    /**
     * Load the observation rows with the parallel page pipeline, then run the same finalisation
     * tail as the sequential {@code AbstractTableDataParser#completeTable()}.
     *
     * @param aDataSet
     *            the parsed dataset metadata.
     * @param aStream
     *            the row data stream.
     * @param aMeta
     *            the built table metadata.
     * @param aParsers
     *            the per-variable value parsers.
     * @param aDeletedOut
     *            single-element out-parameter receiving the identified deleted-row count.
     * @return the assembled data table.
     * @throws IOException
     *             on any worker/producer failure or user abort.
     */
    private IDataTable loadParallel(DatasetBdat aDataSet, InputStream aStream, DataTableMeta aMeta,
            List<BdatVarParser> aParsers, long[] aDeletedOut)
        throws IOException
    {
        CachedDataTableColumn[] columns = createCachedColumns(aMeta);

        DataValueType[] columnTypes = new DataValueType[aMeta.getColumnCount()];
        for (int i = 0; i < columnTypes.length; i++)
        {
            columnTypes[i] = aMeta.getColumn(i).getType();
        }

        PositionAwareInputStream pais = aStream instanceof PositionAwareInputStream existing
                ? existing
                : new PositionAwareInputStream(aStream);

        int parallelism = BdatParallelLoader.defaultParallelism();
        BdatParallelLoader loader = new BdatParallelLoader(aDataSet, pais, aParsers, columns,
                columnTypes, _ ->
                {
                }, parallelism, BdatParallelLoader.defaultMaxInFlight(parallelism));

        BdatParallelLoader.LoadResult result = loader.load();
        aDeletedOut[0] = result.deletedCount();
        return finalizeParallelTable(aMeta, columns, result.rowCount());
    }


    /**
     * Whether the memory-mapped fast path is enabled for local {@code file:} inputs. Defaults to
     * {@code true}; set {@code -Dbdat.mmap=false} to force the stream pipeline.
     *
     * @return {@code true} unless {@code bdat.mmap} is set to {@code false}.
     */
    static boolean isMmapEnabled()
    {
        return !"false".equalsIgnoreCase(System.getProperty("bdat.mmap"));
    }


    /**
     * Resolve a URI to a local file eligible for the mmap fast path: only when mmap + parallel are
     * enabled, the scheme is {@code file}, and the target is a regular readable file. Returns
     * {@code null} otherwise (the caller then uses the stream pipeline).
     *
     * @param aUri
     *            the source URI.
     * @return the local {@link File}, or {@code null} when mmap should not be used.
     */
    static @Nullable File mmapCandidate(URI aUri)
    {
        if (!isMmapEnabled() || !isParallelEnabled() || aUri == null
                || !"file".equalsIgnoreCase(aUri.getScheme()))
        {
            return null;
        }
        try
        {
            File file = new File(aUri);
            return file.isFile() && file.canRead() ? file : null;
        }
        catch (IllegalArgumentException _)
        {
            // Not an absolute hierarchical file: URI — fall back to the stream pipeline.
            return null;
        }
    }


    /**
     * Load rows via a memory-mapped segment with parallel per-page reads: workers self-serve their
     * page slice from the shared read-only {@link MemorySegment}, removing the single-reader
     * serialisation of the stream pipeline. Falls through the same finalisation tail and
     * post-checks as {@link #loadParallel}.
     *
     * @param aDataSet
     *            the parsed dataset metadata.
     * @param aFile
     *            the local file to map.
     * @param aMeta
     *            the built table metadata.
     * @param aParsers
     *            the per-variable value parsers.
     * @param aDeletedOut
     *            single-element out-parameter receiving the identified deleted-row count.
     * @return the assembled data table.
     * @throws IOException
     *             on any worker failure, mapping failure, or user abort.
     */
    private IDataTable loadMmap(DatasetBdat aDataSet, File aFile, DataTableMeta aMeta,
            List<BdatVarParser> aParsers, long[] aDeletedOut)
        throws IOException
    {
        CachedDataTableColumn[] columns = createCachedColumns(aMeta);

        DataValueType[] columnTypes = new DataValueType[aMeta.getColumnCount()];
        for (int i = 0; i < columnTypes.length; i++)
        {
            columnTypes[i] = aMeta.getColumn(i).getType();
        }

        int parallelism = BdatParallelLoader.defaultParallelism();
        try (Arena arena = Arena.ofShared();
                FileChannel channel = FileChannel.open(aFile.toPath(), StandardOpenOption.READ))
        {
            MemorySegment segment = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size(),
                    arena);
            BdatParallelLoader loader = new BdatParallelLoader(aDataSet,
                    BdatParallelLoader.mmapSource(aDataSet, segment), aParsers, columns,
                    columnTypes, _ ->
                    {
                    }, parallelism, BdatParallelLoader.defaultMaxInFlight(parallelism));

            BdatParallelLoader.LoadResult result = loader.load();
            aDeletedOut[0] = result.deletedCount();
            return finalizeParallelTable(aMeta, columns, result.rowCount());
        }
    }


    /**
     * Reproduce the sequential {@code completeTable()} tail for the parallel path: complete
     * columns, reconcile STRING lengths, set the actual row count and build the table.
     *
     * @param aMeta
     *            the table metadata before reconciliation.
     * @param aColumns
     *            the filled columns.
     * @param aRowCount
     *            the actual appended row count.
     * @return the assembled table.
     * @throws IOException
     *             if column completion fails.
     */
    private IDataTable finalizeParallelTable(DataTableMeta aMeta, CachedDataTableColumn[] aColumns,
            long aRowCount)
        throws IOException
    {
        completeParsedColumns(Arrays.stream(aColumns));

        DataTableColumnMeta[] reconciled = AbstractTableDataParser.reconcileColumnLengths(aMeta,
                aColumns);

        DataTableMetaBuilder builder = DataTableMeta.builderFrom(aMeta).rowCount(aRowCount)
                .totalRowCount(aRowCount);
        if (reconciled != null)
        {
            builder.columns(reconciled);
        }
        DataTableMeta finalMeta = builder.build();

        return new ColumnCachedDataTable(finalMeta, aColumns);
    }


    /**
     * Compute the expected non-deleted row count, clamped to {@code >= 0}.
     * <p>
     * F-D17: a corrupt BDAT header may report more deleted observations than declared rows. Without
     * clamping, the subtraction underflows into a negative number that always trips the "Can't read
     * all rows" error path and obscures the real corruption signal. We surface the count
     * consistently as a small non-negative number; the row-count assertion downstream will still
     * detect actual mismatches.
     *
     * @param aRowCount
     *            the declared row count from the BDAT header; may be {@code null} (treated as 0).
     * @param aDeleted
     *            the number of deleted observations identified during the scan.
     * @return {@code max(0, rowCount - deleted)}.
     */
    static long clampExpectedRows(@Nullable Long aRowCount, long aDeleted)
    {
        long rows = aRowCount == null ? 0L : aRowCount;
        return Math.max(0L, rows - aDeleted);
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


    /**
     * Convert a single BDAT variable descriptor into column metadata and add it to the support.
     *
     * @param aSupport
     *            the metadata support to add the column to.
     * @param aVariable
     *            the BDAT variable descriptor.
     * @param aIndex
     *            the zero-based column index (used for the fallback name and the warning message).
     * @param aUri
     *            the source URI (used for the warning message); may be {@code null}.
     */
    protected void addColumn(DataTableMetaSupport aSupport, VariableBdat aVariable, int aIndex,
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
            LOGGER.log(Level.WARNING, "BDAT column %d has no name; using fallback '%s' (uri=%s)"
                    .formatted(aIndex, columnName, aUri));
        }

        DataTableColumnMetaBuilder b = aSupport.addColumn(columnName, type)//
                .nativeType(nativeType);

        Integer varLen = aVariable.getLength();
        if (varLen != null)
        {
            b.length(varLen);
        }
        else if (type == DataValueType.DOUBLE)
        {
            b.length(8);
        }

        FormatAndLabelSubHeader flsh = aVariable.getFormatAndLabelSubHeader();

        String dispFmt = flsh != null ? flsh.getFormat() : null;
        // isBlankOrNull, not != null: an all-blank format slice of non-zero length right-trims to
        // "" and used to be assembled into a bare "." (or "8."), which is not a format SAS has.
        // The XPT twin already guarded it this way.
        if (!CDT.isBlankOrNull(dispFmt))
        {
            // formatDigits/formatDecimals are nullable Short on the wire model; a format without a
            // digit/decimal count is valid and must not NPE the load.
            if (flsh.formatDigits != null && flsh.formatDigits > 0)
            {
                dispFmt = dispFmt + flsh.formatDigits;
            }
            dispFmt = dispFmt + ".";

            if (type == DataValueType.DOUBLE && flsh.formatDecimals != null
                    && flsh.formatDecimals > 0)
            {
                dispFmt += flsh.formatDecimals;
            }

            b.displayFormat(dispFmt);
        }

        if (!CDT.isBlankOrNull(aVariable.getLabel()))
        {
            b.label(aVariable.getLabel());
        }

    }


    /**
     * Read the row count once and fail loudly if it is absent.
     *
     * <p>
     * {@code DatasetBdat.getRowCount()} returns a boxed {@link Long}, and every use here feeds a
     * primitive {@code long}. Calling it inline therefore unboxes a value nothing has checked - an
     * absent row count would surface as a bare {@link NullPointerException} from deep inside the
     * metadata build rather than as a statement about the file. Reading it once into a local and
     * checking it turns that into a diagnosable error, and is what SpotBugs'
     * NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE is pointing at.
     * </p>
     *
     * @param aDataSet
     *            the parsed dataset descriptor.
     * @return the row count.
     */
    private static long requireRowCount(DatasetBdat aDataSet)
    {
        Long rowCount = aDataSet.getRowCount();
        if (rowCount == null)
        {
            throw new IllegalStateException(
                    "SAS7BDAT dataset has no row count in its row-size subheader");
        }
        return rowCount;
    }


    /**
     * The deleted-observation count the BDAT header declares, reading an absent value as 0.
     *
     * @param aDataSet
     *            the parsed dataset descriptor.
     * @return the declared deleted-row count, never negative-by-absence.
     */
    private static long deletedObservationCount(DatasetBdat aDataSet)
    {
        Long deleted = aDataSet.getDeletedObservationCount();
        return deleted == null ? 0L : deleted;
    }


    /**
     * Apply BDAT dataset properties as table metadata to the support.
     *
     * @param aSupport
     *            the metadata support to update.
     * @param aDataSet
     *            the BDAT dataset descriptor.
     * @param aRowCount
     *            the actual number of rows loaded.
     * @param aCharset
     *            the character encoding used.
     */
    protected void applyTableMetaData(DataTableMetaSupport aSupport, DatasetBdat aDataSet,
            long aRowCount, Charset aCharset)
    {
        String rawCompr = aDataSet.getCompression().orElse(null);

        String compr;
        if (CDT.isBlankOrNull(rawCompr))
        {
            compr = "NO";
        }
        else
        {
            // isBlankOrNull(rawCompr) is false here, so rawCompr is non-null.
            String present = Objects.requireNonNull(rawCompr);
            if (present.equals("SASYZCR2"))
            {
                compr = "BINARY";
            }
            else if (present.equals("SASYZCRL"))
            {
                compr = "CHAR";
            }
            else
            {
                compr = present;
            }
        }

        DataTableMetaBuilder b = aSupport.getTableMeta()//
                .rowCount(aRowCount)//
                .totalRowCount(aRowCount)//
                .addMetaData("Compression", compr)//
                .addMetaData(META_KEY_ENCODING, aCharset.toString())//
                .addMetaData("Row Length", aDataSet.getRowLength())//
        ;

        if (aDataSet.getCreated() != null)
        {
            b.addMetaData(META_KEY_CREATED, META_TIMESTAMP.format(aDataSet.getCreated()));
        }
        if (aDataSet.getModified() != null)
        {
            b.addMetaData(META_KEY_MODIFIED, META_TIMESTAMP.format(aDataSet.getModified()));
        }

        aDataSet.getDataSetLabel().ifPresent(b::label);
    }

    private class BdatTableDataParser extends AbstractTableDataParser<BdatObservation>
    {

        BdatTableDataParser(@NonNull DataTableMeta aMeta)
        {
            super(BdatTableProvider.this, aMeta);
        }


        @Override
        protected void addData2Column(List<BdatObservation> aRowSlice, int aColumnIndex,
                DataTableColumnMeta aMetaColumn, CachedDataTableColumn aDataColumn)
        {
            int rowCount = aRowSlice.size();
            for (int ridx = 0; ridx < rowCount; ridx++)
            {
                Object val = aRowSlice.get(ridx).getValue(aColumnIndex);
                switch (val)
                {
                // F-RS9: explicit null arm so the pattern switch doesn't throw NPE on a null
                // cell value. Defensive on both paths — BdatVarParser.getValue cannot return
                // null today (it yields a boxed Double or a String, or throws), which corrects
                // the earlier note here claiming sparse SAS rows produce one.
                //
                // Non-STRING columns resolve to MIS_UNKNOWN, not MIS: a null cell is an
                // unexpected state and should read as one, rather than as an ordinary SAS '.'
                // missing that the file deliberately contained. This also aligns the arm with
                // BdatParallelLoader, which already used MIS_UNKNOWN — the two silently
                // disagreed here, which would have become a real divergence on a default-on
                // path the moment the case turned reachable.
                case null -> aDataColumn
                        .addElement(aMetaColumn.getType() == DataValueType.STRING ? ""
                                : MissingValue.MIS_UNKNOWN);
                case String str -> aDataColumn.addElement(tri(str));
                case Number num ->
                {
                    double dbl = num.doubleValue();
                    if (Double.isNaN(dbl))
                    {
                        // forNaN: the SAS code BdatVarParser encoded, else MIS for a bare NaN
                        // (owner ruling E5, "NaN should get mis"; BdatVarParser already turns one
                        // into MIS's payload, ruling N1), else MIS_UNKNOWN.
                        aDataColumn.addElement(MissingValue.forNaN(dbl));
                    }
                    else
                    {
                        aDataColumn.addElement(dbl);
                    }
                }
                default ->
                {
                    // Type-aware fallback: STRING columns get an empty string (matches the parso
                    // provider behaviour for unrecognised cell values), other types get
                    // MIS_UNKNOWN so that downstream code can still detect the cell as missing.
                    if (aMetaColumn.getType() == DataValueType.STRING)
                    {
                        aDataColumn.addElement("");
                    }
                    else
                    {
                        aDataColumn.addElement(MissingValue.MIS_UNKNOWN);
                    }
                }
                }
            }
        }
    }

}
