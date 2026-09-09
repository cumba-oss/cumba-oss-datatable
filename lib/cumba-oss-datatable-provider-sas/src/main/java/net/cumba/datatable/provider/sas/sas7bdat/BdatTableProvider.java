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
import java.net.URI;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
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
import net.cumba.datatable.impl.CachedDataTableColumn;
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

        map.put((byte) 0x14, "UTF-8");
        map.put((byte) 0x1C, "US-ASCII");
        map.put((byte) 0x1D, "ISO-8859-1");
        map.put((byte) 0x1E, "ISO-8859-2");
        map.put((byte) 0x1F, "ISO-8859-3");

        map.put((byte) 0x20, "ISO-8859-4");
        map.put((byte) 0x21, "ISO-8859-5");
        map.put((byte) 0x22, "ISO-8859-6");
        map.put((byte) 0x23, "ISO-8859-7");
        map.put((byte) 0x24, "ISO-8859-8");
        map.put((byte) 0x25, "ISO-8859-9");
        map.put((byte) 0x27, "x-iso-8859-11");
        map.put((byte) 0x28, "ISO-8859-15");
        map.put((byte) 0x2B, "IBM437");
        map.put((byte) 0x2C, "IBM850");
        map.put((byte) 0x2D, "IBM852");
        map.put((byte) 0x2E, "IBM00858");
        map.put((byte) 0x2F, "IBM862");

        map.put((byte) 0x33, "IBM866");
        map.put((byte) 0x3A, "IBM857");
        map.put((byte) 0x3C, "windows-1250");
        map.put((byte) 0x3D, "windows-1251");
        map.put((byte) 0x3E, "windows-1252");
        map.put((byte) 0x3F, "windows-1253");

        map.put((byte) 0x40, "windows-1254");
        map.put((byte) 0x41, "windows-1255");
        map.put((byte) 0x42, "windows-1256");
        map.put((byte) 0x43, "windows-1257");
        map.put((byte) 0x44, "windows-1258");
        map.put((byte) 0x45, "x-MacRoman");
        map.put((byte) 0x46, "x-MacArabic");
        map.put((byte) 0x47, "x-MacHebrew");
        map.put((byte) 0x48, "x-MacGreek");
        map.put((byte) 0x49, "x-MacThai");
        map.put((byte) 0x4B, "x-MacTurkish");
        map.put((byte) 0x4C, "x-MacUkraine");
        map.put((byte) 0x4E, "IBM037");

        map.put((byte) 0x57, "IBM424");
        map.put((byte) 0x58, "IBM500");
        map.put((byte) 0x59, "IBM-Thai");
        map.put((byte) 0x5A, "IBM870");
        map.put((byte) 0x5B, "x-IBM875");
        map.put((byte) 0x5F, "x-IBM1025");

        map.put((byte) 0x62, "x-IBM1112");
        map.put((byte) 0x63, "x-IBM1122");
        map.put((byte) 0x66, "IBM424");
        map.put((byte) 0x67, "IBM-Thai");
        map.put((byte) 0x68, "IBM870");
        map.put((byte) 0x69, "x-IBM875");
        map.put((byte) 0x6C, "x-IBM1025");
        map.put((byte) 0x6D, "IBM1026");
        map.put((byte) 0x6E, "IBM1047");
        map.put((byte) 0x6F, "x-IBM1112");

        map.put((byte) 0x70, "x-IBM1122");
        map.put((byte) 0x75, "x-IBM937");
        map.put((byte) 0x76, "x-windows-950");
        map.put((byte) 0x77, "x-EUC-TW");
        map.put((byte) 0x7B, "Big5");
        map.put((byte) 0x7C, "x-IBM935");
        map.put((byte) 0x7D, "GBK");
        map.put((byte) 0x7E, "x-mswin-936");

        map.put((byte) 0x80, "x-IBM1381");
        map.put((byte) 0x81, "x-IBM939");
        map.put((byte) 0x82, "x-IBM930");
        map.put((byte) 0x86, "EUC-JP");
        map.put((byte) 0x88, "x-windows-iso2022jp");
        map.put((byte) 0x89, "x-IBM942");
        map.put((byte) 0x8A, "Shift_JIS");
        map.put((byte) 0x8B, "x-IBM933");
        map.put((byte) 0x8C, "EUC-KR");
        map.put((byte) 0x8D, "x-windows-949");
        map.put((byte) 0x8E, "x-IBM949");

        map.put((byte) 0xA3, "x-MacIceland");
        map.put((byte) 0xA7, "ISO-2022-JP");
        map.put((byte) 0xA8, "ISO-2022-KR");
        map.put((byte) 0xA9, "x-ISO2022-CN-GB");
        map.put((byte) 0xAC, "x-ISO2022-CN-CNS");
        map.put((byte) 0xAD, "IBM037");

        map.put((byte) 0xB7, "IBM01140");
        map.put((byte) 0xB8, "IBM01141");
        map.put((byte) 0xB9, "IBM01142");
        map.put((byte) 0xBA, "IBM01143");
        map.put((byte) 0xBB, "IBM01144");
        map.put((byte) 0xBC, "IBM01145");
        map.put((byte) 0xBD, "IBM01146");
        map.put((byte) 0xBE, "IBM01147");
        map.put((byte) 0xBF, "IBM01148");

        map.put((byte) 0xC0, "IBM01140");
        map.put((byte) 0xC1, "IBM01141");
        map.put((byte) 0xC2, "IBM01142");
        map.put((byte) 0xC3, "IBM01143");
        map.put((byte) 0xC4, "IBM01144");
        map.put((byte) 0xC5, "IBM01145");
        map.put((byte) 0xC6, "IBM01146");
        map.put((byte) 0xC7, "IBM01147");
        map.put((byte) 0xC8, "IBM01148");
        map.put((byte) 0xCD, "GB18030");
        map.put((byte) 0xCF, "x-IBM1097");

        map.put((byte) 0xD0, "x-IBM1097");
        map.put((byte) 0xD3, "IBM01149");
        map.put((byte) 0xD4, "IBM01149");

        map.put((byte) 0xEA, "x-IBM930");
        map.put((byte) 0xEB, "x-IBM933");
        map.put((byte) 0xEC, "x-IBM935");
        map.put((byte) 0xED, "x-IBM937");
        map.put((byte) 0xEE, "x-IBM939");

        map.put((byte) 0xF2, "ISO-8859-13");
        map.put((byte) 0xF5, "x-MacCroatian");
        map.put((byte) 0xF6, "x-MacCyrillic");
        map.put((byte) 0xF7, "x-MacRomania");
        map.put((byte) 0xF8, "JIS_X0201");

        SAS_CHARACTER_ENCODINGS = Collections.unmodifiableMap(map);
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
        return SAS_CHARACTER_ENCODINGS.get(Byte.valueOf((byte) encodingByte));
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
        try (InputStream in = aUri.toURL().openStream())
        {
            try (PositionAwareInputStream pais = new PositionAwareInputStream(in))
            {
                DatasetBdat bdat = new ParserBdat().parseDataset(pais);
                return provide(aUri, pais, bdat);
            }
        }
    }


    @Override
    public DataTableMeta provideMetaData(URI aUri, @Nullable FileInfo aFileInfo) throws IOException
    {
        try (InputStream in = aUri.toURL().openStream())
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

        List<VariableBdat> vars = aDataSet.getVariables();
        for (int i = 0; i < vars.size(); i++)
        {
            addColumn(dtms, vars.get(i), i, aUri);
        }

        applyTableMetaData(dtms, aDataSet, requireRowCount(aDataSet), charset);

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
            return provide(aUri, in, bdat);
        }
    }


    /**
     * Parse and provide a data table from a pre-parsed BDAT dataset.
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

        BdatTableDataParser tableParser = new BdatTableDataParser(dtms.getTableMeta().build());

        long identifiedDeletedRecords;

        ObservationIteratorBdat2 iter = new ObservationIteratorBdat2(aDataSet, aStream);
        while (iter.hasNext())
        {
            tableParser.addDataRow(new BdatObservation(parsers, iter.next()));
        }

        identifiedDeletedRecords = iter.getParsedDeletedRowCount();

        IDataTable table = tableParser.completeTable();

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
    static long clampExpectedRows(Long aRowCount, long aDeleted)
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
        if (dispFmt != null)
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
        SimpleDateFormat sdtf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

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
                // cell value (which the underlying parser can legitimately produce for sparse
                // SAS rows). For STRING columns, SAS character missing is empty string by
                // convention; everything else is treated as MIS.
                case null -> aDataColumn.addElement(
                        aMetaColumn.getType() == DataValueType.STRING ? "" : MissingValue.MIS);
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
