package net.cumba.datatable.provider.dsj;

import static net.cumba.datatable.impl.provider.DataTableMetaSupport.META_KEY_CREATED;
import static net.cumba.datatable.impl.provider.DataTableMetaSupport.META_KEY_ITEM_KEY_SEQUENCE;
import static net.cumba.datatable.impl.provider.DataTableMetaSupport.META_KEY_MODIFIED;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import lombok.CustomLog;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.cumba.cdisc.dsj.ColumnDataType;
import net.cumba.cdisc.dsj.ColumnTargetDataType;
import net.cumba.cdisc.dsj.DataSetJsonTableParallelParser;
import net.cumba.cdisc.dsj.DataSetJsonTableParser;
import net.cumba.cdisc.dsj.DataTypeMapperFactory;
import net.cumba.cdisc.dsj.DsjTable;
import net.cumba.cdisc.dsj.DsjTableColumn;
import net.cumba.cdisc.dsj.IDataTypeMapper;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableColumnMeta.DataTableColumnMetaBuilder;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.DataTableMeta.DataTableMetaBuilder;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.IDataTableColumn;
import net.cumba.datatable.help.CDT;
import net.cumba.datatable.impl.CachedDataTableColumn;
import net.cumba.datatable.impl.ColumnCachedDataTable;
import net.cumba.datatable.impl.provider.AbstractDataTableProvider;
import net.cumba.datatable.impl.provider.AbstractTableDataParser;
import net.cumba.datatable.impl.provider.DataTableMetaSupport;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.MissingValue;
import org.jspecify.annotations.Nullable;

/**
 * Parses DataSet-JSON files (JSON, NDJSON, DSJC) and converts them into {@link IDataTable}
 * instances using row-based parsing via {@link AbstractTableDataParser}. Each row is received as an
 * {@code Object[]} from the {@link DataSetJsonTableParser}'s row handler callback and fed into the
 * parser framework for parallel column population.
 *
 * <p>
 * This is the v2 implementation that replaces the custom slice-based pipeline with the standard
 * {@link AbstractTableDataParser} infrastructure.
 * </p>
 */
@CustomLog
public class DsjTableProvider extends AbstractDataTableProvider
{

    @Getter
    @Setter
    private volatile int rowSliceSize = 10000;

    /**
     * Number of parallel chunks for NDJSON loads. Defaults to whatever
     * {@link DataSetJsonTableParallelParser} chooses; setting this overrides per-provider. The
     * setting only affects local plain-NDJSON loads; compressed and remote inputs always run
     * single-threaded.
     */
    @Getter
    @Setter
    private @Nullable Integer parallelism;

    /**
     * Minimum byte size at which an NDJSON file is split into parallel chunks. Defaults to whatever
     * {@link DataSetJsonTableParallelParser} chooses ({@code 4 MB}). Tests typically override to
     * {@code 0} to force the parallel branch on small fixtures, and downstream callers can raise
     * this to suppress parallelism on memory-constrained hosts.
     */
    @Getter
    @Setter
    private @Nullable Long minBytesForParallel;

    /** {@inheritDoc} */
    @Override
    public List<FileInfo> getSupportedFileInfos()
    {
        return DsjProviderSupplier.FIS;
    }


    /**
     * Parses a DataSet-JSON file from the given URI and returns the resulting data table.
     *
     * @param aURI
     *            the URI of the DataSet-JSON file to parse.
     * @param aFileInfo
     *            the file type information.
     * @return the parsed {@link IDataTable}.
     * @throws IOException
     *             if reading or parsing fails.
     */
    @Override
    public DataTableMeta provideMetaData(URI aURI, @Nullable FileInfo aFileInfo) throws IOException
    {
        DataTableMeta[] metaHolder = new DataTableMeta[1];

        DataSetJsonTableParser dsjtp = new DataSetJsonTableParser();
        dsjtp.setHandlerMetadata(table ->
        {
            metaHolder[0] = buildMeta(table, aURI);
            // abort parsing once the metadata is available — we do not want to stream rows.
            return 1;
        });

        try (InputStream stream = aURI.toURL().openStream())
        {
            dsjtp.parseDataSet(stream);
        }
        catch (IOException ex)
        {
            // handlerMetadata returning non-zero triggers a "User aborted!" IOException — treat
            // that as success as long as we captured the metadata.
            if (metaHolder[0] == null)
            {
                throw ex;
            }
        }

        if (metaHolder[0] == null)
        {
            throw new IOException("No metadata found in DataSet-JSON file: " + aURI);
        }
        return metaHolder[0];
    }


    private DataTableMeta buildMeta(DsjTable aTable, URI aURI)
    {
        DataTableMetaSupport metaSup = new DataTableMetaSupport(getMetadata());
        metaSup.setTable(aURI, aTable.getName());
        metaSup.setFileFormat("DATASET-JSON", null);
        aTable.getColumnsStream().forEachOrdered(tc -> addColumn(metaSup, tc));

        long rowCount = aTable.getRecords();

        DataTableMetaBuilder b = metaSup.getTableMeta();

        if (!CDT.isBlankOrNull(aTable.getLabel()))
        {
            b.label(aTable.getLabel());
        }

        b.addMetaData(META_KEY_CREATED, aTable.getDatasetJSONCreationDateTime())
                .addMetaData(META_KEY_MODIFIED, aTable.getDbLastModifiedDateTime())
                .addMetaData("datasetJSONVersion", aTable.getDatasetJSONVersion())
                .addMetaData("fileOID", aTable.getFileOID())
                .addMetaData("originator", aTable.getOriginator())
                .addMetaData("studyOID", aTable.getStudyOID())
                .addMetaData("metaDataVersionOID", aTable.getMetaDataVersionOID())
                .addMetaData("itemGroupOID", aTable.getItemGroupOID())
                .addMetaData("metaDataRef", aTable.getMetaDataRef());

        if (rowCount > 0)
        {
            b.rowCount(rowCount).totalRowCount(rowCount);
        }

        return b.build();
    }


    @Override
    public IDataTable provide(URI aURI, @Nullable FileInfo aFileInfo) throws IOException
    {
        URL tblUrl = aURI.toURL();

        DataSetJsonTableParallelParser dsjtp = new DataSetJsonTableParallelParser();
        if (parallelism != null)
        {
            dsjtp.setParallelism(parallelism);
        }
        if (minBytesForParallel != null)
        {
            dsjtp.setMinBytesForParallel(minBytesForParallel);
        }

        // Lazily-populated state. The parallel parser fires `handlerChunkRows` when it can
        // split a plain NDJSON file into byte-range chunks; otherwise (compressed input,
        // small files, non-Path URIs, plain-JSON-with-rows-nested layouts) it delegates to
        // the single-threaded super-class which fires `handlerRow` instead. Both code paths
        // share the same metadata + mappers, captured once in `handlerMetadata`.
        //
        // The chunk-parser map is keyed by the parser's monotonic chunkIdx, capped at
        // {@code parallelism}. Each chunkIdx is owned by exactly one chunk worker at a time,
        // so a {@link ConcurrentHashMap}'s {@code computeIfAbsent} is the right shape for
        // lazy creation under concurrent first-batch arrivals.
        Map<Integer, DsjTableDataParser> chunkParsers = new ConcurrentHashMap<>();
        DsjTableDataParser[] singleParser = new DsjTableDataParser[1];
        DataTableMeta[] metaHolder = new DataTableMeta[1];
        IDataTypeMapper[][] mappersHolder = new IDataTypeMapper[1][];

        dsjtp.setHandlerMetadata(table -> handleMetadata(table, aURI, metaHolder, mappersHolder));

        dsjtp.setHandlerChunkRows((chunkIdx, _, count, rows) ->
        {
            try
            {
                DsjTableDataParser cp = chunkParsers.computeIfAbsent(chunkIdx,
                        _ -> createChunkParser(metaHolder[0], mappersHolder[0]));
                for (int i = 0; i < count; i++)
                {
                    cp.addDataRow(rows[i]);
                }
                return 0;
            }
            catch (IOException ex)
            {
                throw new UncheckedIOException(ex);
            }
        });

        dsjtp.setHandlerRow((_, _, values) ->
        {
            try
            {
                DsjTableDataParser sp = singleParser[0];
                if (sp == null)
                {
                    sp = new DsjTableDataParser(metaHolder[0], mappersHolder[0]);
                    singleParser[0] = sp;
                }
                sp.addDataRow(values);
            }
            catch (IOException ex)
            {
                throw new UncheckedIOException(ex);
            }
            return 0;
        });

        // Prefer Path access so the parallel path can use FileChannel positional reads.
        // Non-file URIs (http://, jar:, etc.) and non-Path-resolvable URIs go through the
        // existing stream-based path which the parallel parser internally delegates to its
        // single-threaded super-class.
        Path path = pathFromUri(aURI);
        if (path != null)
        {
            dsjtp.parseDataSet(path);
        }
        else
        {
            try (InputStream stream = tblUrl.openStream())
            {
                dsjtp.parseDataSet(stream);
            }
        }

        IDataTable table = assembleResult(chunkParsers, singleParser[0], metaHolder[0]);

        long expectedRowCount = table.getMetaData().getRowCount();
        long parsedRowCount = table.getRowCount();
        if (expectedRowCount >= 0 && parsedRowCount != expectedRowCount)
        {
            LOGGER.log(Level.WARNING,
                    "Row count mismatch: metadata declares {0} rows but {1} were parsed.",
                    expectedRowCount, parsedRowCount);
        }

        debugCalcDataSize(table);

        return table;
    }


    /**
     * Returns a {@link Path} for the given {@link URI} when it is a {@code file:} URI that resolves
     * to a local filesystem path; returns {@code null} otherwise. Used to gate the parallel path:
     * only local files have the random access required for byte-range chunking.
     */
    private static @Nullable Path pathFromUri(@Nullable URI aURI)
    {
        if (aURI == null || aURI.getScheme() == null || !"file".equalsIgnoreCase(aURI.getScheme()))
        {
            return null;
        }
        try
        {
            return Paths.get(aURI);
        }
        catch (IllegalArgumentException | FileSystemNotFoundException _)
        {
            return null;
        }
    }


    /**
     * Builds the final {@link IDataTable} from whichever of the two ingestion paths fired during
     * parsing. If any chunk parser was populated by {@code handlerChunkRows}, performs a
     * per-column-parallel concatenation. Otherwise returns the single parser's completion result.
     */
    private IDataTable assembleResult(Map<Integer, DsjTableDataParser> chunkParsers,
            DsjTableDataParser singleParser, DataTableMeta meta)
        throws IOException
    {
        if (!chunkParsers.isEmpty())
        {
            return mergeChunkParsers(chunkParsers, meta);
        }
        if (singleParser != null)
        {
            return singleParser.completeTable();
        }
        throw new IOException("No metadata found in DataSet-JSON file.");
    }


    /**
     * Completes each per-chunk {@link DsjTableDataParser} into its own {@link IDataTable}, then
     * concatenates them column-by-column in parallel into a single result. The merge is
     * single-threaded within a column but parallel across columns — at typical SDTM widths (20-30
     * columns) this saturates the available cores cleanly.
     *
     * <p>
     * Chunks are concatenated in increasing {@code chunkIdx} order, which by the parallel parser's
     * contract corresponds to file row order. String length reconciliation is rerun across the
     * merged columns: each chunk's {@code complete()} reconciles against its own
     * {@code maxValueLength}, so the merged value can exceed any per-chunk length and the
     * table-level metadata needs the cross-chunk maximum. Sort wrapping (if the table declares an
     * order) is reapplied at the end.
     * </p>
     */
    private IDataTable mergeChunkParsers(Map<Integer, DsjTableDataParser> chunkParsers,
            DataTableMeta meta)
        throws IOException
    {
        List<Integer> orderedIdx = new ArrayList<>(chunkParsers.keySet());
        orderedIdx.sort(Integer::compare);
        IDataTable[] chunkTables = new IDataTable[orderedIdx.size()];
        long totalRows = 0;
        for (int i = 0; i < orderedIdx.size(); i++)
        {
            // key originates from chunkParsers.keySet(), so the mapping is always present.
            DsjTableDataParser cp = Objects.requireNonNull(chunkParsers.get(orderedIdx.get(i)));
            chunkTables[i] = cp.completeTable();
            totalRows += chunkTables[i].getRowCount();
        }
        final long total = totalRows;
        int colCount = meta.getColumnCount();
        CachedDataTableColumn[] outCols = new CachedDataTableColumn[colCount];

        IntStream.range(0, colCount).parallel().forEach(c ->
        {
            DataValueType type = meta.getColumn(c).getType();
            CachedDataTableColumn out = new CachedDataTableColumn(c, type);
            out.setExpectedSize((int) Math.min(total, Integer.MAX_VALUE));
            for (IDataTable t : chunkTables)
            {
                if (t == null)
                {
                    continue;
                }
                IDataTableColumn src = t.getColumn(c);
                long rc = src.getRowCount();
                for (long r = 0; r < rc; r++)
                {
                    out.addElement(src.getValue(r));
                }
            }
            out.complete();
            outCols[c] = out;
        });

        DataTableColumnMeta[] reconciled = reconcileMergedLengths(meta, outCols);
        DataTableMetaBuilder mb = DataTableMeta.builderFrom(meta).rowCount(total)
                .totalRowCount(total);
        if (reconciled != null)
        {
            mb.columns(reconciled);
        }
        DataTableMeta finalMeta = mb.build();

        return new ColumnCachedDataTable(finalMeta, outCols);
    }


    /**
     * Walks each STRING column's actual {@link CachedDataTableColumn#getMaxValueLength()} against
     * the declared length on {@code aMeta} and produces an updated column array if any declared
     * length is short of the observed maximum. Returns {@code null} when no column needs
     * adjustment. Mirrors {@code AbstractTableDataParser.reconcileColumnLengths} but operates on
     * already-merged columns rather than on a single parser's columns.
     */
    private DataTableColumnMeta @Nullable [] reconcileMergedLengths(DataTableMeta aMeta,
            CachedDataTableColumn[] aCols)
    {
        int colCount = aMeta.getColumnCount();
        DataTableColumnMeta[] out = null;
        for (int i = 0; i < colCount; i++)
        {
            DataTableColumnMeta cm = aMeta.getColumn(i);
            if (cm.getType() != DataValueType.STRING)
            {
                continue;
            }
            int declared = cm.getLength();
            int observed = aCols[i].getMaxValueLength();
            if (observed <= declared)
            {
                continue;
            }
            if (out == null)
            {
                out = new DataTableColumnMeta[colCount];
                for (int j = 0; j < colCount; j++)
                {
                    out[j] = aMeta.getColumn(j);
                }
            }
            out[i] = cm.toBuilder().length(observed)._build();
        }
        return out;
    }


    /**
     * Constructs a per-chunk {@link DsjTableDataParser}.
     */
    private DsjTableDataParser createChunkParser(DataTableMeta meta, IDataTypeMapper[] mappers)
    {
        return new DsjTableDataParser(meta, mappers);
    }


    private int handleMetadata(DsjTable aTable, URI aURI, DataTableMeta[] aMetaHolder,
            IDataTypeMapper[][] aMappersHolder)
    {
        DataTableMetaSupport metaSup = new DataTableMetaSupport(getMetadata());
        metaSup.setTable(aURI, aTable.getName());
        metaSup.setFileFormat("DATASET-JSON", null);
        aTable.getColumnsStream().forEachOrdered(tc -> addColumn(metaSup, tc));

        DataTableMetaBuilder b = metaSup.getTableMeta();

        if (!CDT.isBlankOrNull(aTable.getLabel()))
        {
            b.label(aTable.getLabel());
        }

        b.addMetaData(META_KEY_CREATED, aTable.getDatasetJSONCreationDateTime())
                .addMetaData(META_KEY_MODIFIED, aTable.getDbLastModifiedDateTime())
                .addMetaData("datasetJSONVersion", aTable.getDatasetJSONVersion())
                .addMetaData("fileOID", aTable.getFileOID())
                .addMetaData("originator", aTable.getOriginator())
                .addMetaData("studyOID", aTable.getStudyOID())
                .addMetaData("metaDataVersionOID", aTable.getMetaDataVersionOID())
                .addMetaData("itemGroupOID", aTable.getItemGroupOID())
                .addMetaData("metaDataRef", aTable.getMetaDataRef());

        DataTableMeta meta = b.build();
        aMetaHolder[0] = meta;

        IDataTypeMapper[] typeMappers = Arrays.stream(meta.getColumns())
                .map(this::createDataTypeMapper).toArray(IDataTypeMapper[]::new);
        aMappersHolder[0] = typeMappers;

        return 0;
    }


    private void addColumn(DataTableMetaSupport aSupport, DsjTableColumn aColumn)
    {
        ColumnDataType dt = getColumnDataTypeFor(aColumn.getDataType());
        ColumnTargetDataType tdt = getColumnTargetDataTypeFor(aColumn.getTargetDataType());

        DataValueType type = getTypeFor(dt, tdt);
        int len = aColumn.getLength();

        DataTableColumnMetaBuilder b = aSupport.addColumn(aColumn.getName(), type);

        if (!CDT.isBlankOrNull(aColumn.getLabel()))
        {
            b.label(aColumn.getLabel());
        }

        if (!CDT.isBlankOrNull(aColumn.getDisplayFormat()))
        {
            b.displayFormat(aColumn.getDisplayFormat());
        }

        b.length(len)//
                .nativeType(dt.toString().toLowerCase(Locale.ROOT))//
                .addMetaData("dataType", dt)//
                .addMetaData("targetDataType", tdt)//
                .addMetaData(META_KEY_ITEM_KEY_SEQUENCE, aColumn.getKeySequence())//
                .addMetaData("itemOID", aColumn.getItemOID())//
        ;
    }


    /**
     * Map a String representation of the columns dataType property to the {@link ColumnDataType}.
     *
     * @param aTypeName
     *            the string value of the dataType property.
     * @return the ColumnDataType.
     */
    protected ColumnDataType getColumnDataTypeFor(String aTypeName)
    {
        if (aTypeName == null)
        {
            return ColumnDataType.OTHER;
        }

        try
        {
            return ColumnDataType.valueOf(aTypeName.toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException _)
        {
            LOGGER.log(Level.WARNING, "Found unexpected data type: " + aTypeName);
            return ColumnDataType.OTHER;
        }
    }


    /**
     * Map a String representation of the columns targetDataType property to the
     * {@link ColumnTargetDataType}.
     *
     * @param aTypeName
     *            the string value of the targetDataType property.
     * @return the ColumnTargetDataType.
     */
    protected ColumnTargetDataType getColumnTargetDataTypeFor(@Nullable String aTypeName)
    {
        if (aTypeName == null)
        {
            return ColumnTargetDataType.UNKNOWN;
        }
        try
        {
            return ColumnTargetDataType.valueOf(aTypeName.toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException _)
        {
            LOGGER.log(Level.WARNING, "Found unexpected target data type: " + aTypeName);
            return ColumnTargetDataType.OTHER;
        }
    }


    /**
     * Creates an {@link IDataTypeMapper} for the given column based on its dataType and
     * targetDataType metadata.
     *
     * @param aMeta
     *            the column metadata containing dataType and targetDataType.
     * @return a type mapper for value conversion.
     */
    protected IDataTypeMapper createDataTypeMapper(DataTableColumnMeta aMeta)
    {
        // "dataType" / "targetDataType" are always populated by addColumn(...) for every column
        // this provider builds, so the metadata lookups are guaranteed present here.
        ColumnDataType dt = Objects.requireNonNull((ColumnDataType) aMeta.getMetaData("dataType"),
                "missing 'dataType' column meta");
        ColumnTargetDataType tdt = Objects.requireNonNull(
                (ColumnTargetDataType) aMeta.getMetaData("targetDataType"),
                "missing 'targetDataType' column meta");
        return new DataTypeMapperFactory().getMapper(dt, tdt);
    }


    /**
     * Determines the {@link DataValueType} for a column based on its data type and target data
     * type. The target data type takes precedence when available; otherwise the general data type
     * is used.
     *
     * @param aType
     *            the column data type from DataSet-JSON.
     * @param aTargetType
     *            the target data type from DataSet-JSON, or {@link ColumnTargetDataType#UNKNOWN}.
     * @return the mapped data value type.
     */
    private DataValueType getTypeFor(ColumnDataType aType, ColumnTargetDataType aTargetType)
    {
        // prefer target data type
        switch (aTargetType)
        {
        case INTEGER:
            // J2: store integer-declared columns as floating point internally so a declared-integer
            // cell holding a decimal (non-conformant data) is not truncated. DataValueDouble
            // renders
            // a whole double as "3" (trims .0), and both LONG and DOUBLE collapse to "Num", so the
            // type-metadata rules are unaffected.
            return DataValueType.DOUBLE;
        case DECIMAL:
            return DataValueType.DOUBLE;
        case UNKNOWN:
            // not given
            break;
        case OTHER:
        default:
            LOGGER.log(Level.WARNING, "Found unexpected target data type: " + aTargetType);
            break;
        }

        // no target data type --> use general data type
        return switch (aType)
        {
        case STRING -> DataValueType.STRING;
        case INTEGER -> DataValueType.DOUBLE; // J2: floating point internally (see target-type
                                              // case)
        case DECIMAL, FLOAT, DOUBLE -> DataValueType.DOUBLE;
        case BOOLEAN -> DataValueType.BOOLEAN;
        case DATE, DATETIME, TIME, URI -> DataValueType.STRING;
        default -> DataValueType.STRING;
        };
    }

    /**
     * Inner table data parser that processes rows from the DataSet-JSON parser's row handler. Each
     * row is an {@code Object[]} with one element per column, containing values as their natural
     * JSON types (String, Long, Double, Boolean, null).
     */
    private class DsjTableDataParser extends AbstractTableDataParser<@Nullable Object[]>
    {

        private final IDataTypeMapper[] typeMappers;

        DsjTableDataParser(@NonNull DataTableMeta aMeta, IDataTypeMapper[] aTypeMappers)
        {
            super(DsjTableProvider.this, aMeta);
            this.typeMappers = aTypeMappers;
        }


        @Override
        protected void addData2Column(List<@Nullable Object[]> aRowSlice, int aColumnIndex,
                DataTableColumnMeta aMetaColumn, CachedDataTableColumn aDataColumn)
        {
            DataValueType dvt = aMetaColumn.getType();
            IDataTypeMapper mapper = typeMappers[aColumnIndex];
            boolean isNumeric = dvt == DataValueType.DOUBLE || dvt == DataValueType.LONG;

            int rowCount = aRowSlice.size();
            for (int ridx = 0; ridx < rowCount; ridx++)
            {
                @Nullable
                Object val = aRowSlice.get(ridx)[aColumnIndex];

                // Apply data type mapper (e.g., date/time string to SAS epoch)
                val = mapper.mapValueToTargetType(val);

                try
                {
                    if (val == null)
                    {
                        // A JSON `null` is an explicit "no value" that Dataset-JSON can express in
                        // a column of ANY type, character included, so it is loaded as
                        // MissingValue.MIS in every column type. An empty string the file
                        // genuinely contains arrives as "" through the `instanceof String` arm
                        // below and stays "" — the two are distinct in the model.
                        //
                        // This supersedes Fix #161's mechanism (which mapped a STRING null to ""
                        // here so a blank cell was format-independent at ingestion) while keeping
                        // its goal: finding-equivalence is now guaranteed at CONSUMPTION, because
                        // every blankness consumer asks isEmptyOrMissing(), for which a
                        // MissingValue and a "" are both blank. Only the grouping-key encoding
                        // keeps them apart, and there deliberately (they are distinct keys with a
                        // shared disposition).
                        //
                        // NB. for a STRING column the type mapper is the identity NoMapper, so
                        // `val == null` here means the source cell really was null; a mapper that
                        // answers null for an unparseable value (DateMapper etc.) only ever serves
                        // a numeric column, which already stored MIS.
                        aDataColumn.addElement(MissingValue.MIS);
                    }
                    else if (val instanceof String str)
                    {
                        if (isNumeric)
                        {
                            addParsedDoubleOrError(aDataColumn, str);
                        }
                        else
                        {
                            // tri() is poly-null: a non-null argument yields a non-null result,
                            // which NullAway cannot express, so assert it for the @NonNull
                            // addElement.
                            aDataColumn.addElement(Objects.requireNonNull(tri(str)));
                        }
                    }
                    else if (val instanceof Number num)
                    {
                        if (dvt == DataValueType.LONG)
                        {
                            aDataColumn.addElement(num.longValue());
                        }
                        else
                        {
                            double dbl = num.doubleValue();
                            if (Double.isNaN(dbl))
                            {
                                aDataColumn.addElement(MissingValue.MIS);
                            }
                            else
                            {
                                aDataColumn.addElement(num);
                            }
                        }
                    }
                    else if (val instanceof Boolean boolVal)
                    {
                        aDataColumn.addElement(boolVal);
                    }
                    else
                    {
                        aDataColumn.addElement(MissingValue.MIS_UNKNOWN);

                        LOGGER.log(Level.WARNING,
                                "Unexpected value for row={0} column={1} type={2} value=\"{3}\".",
                                ridx, aColumnIndex, val.getClass(), val);
                    }
                }
                catch (Exception ex)
                {
                    String msg = MessageFormat.format(
                            "Error while handling value {0} [row={1}, column={2}]: {3}", val, ridx,
                            aColumnIndex, ex.getMessage());
                    LOGGER.log(Level.WARNING, msg, ex);
                    throw new IllegalStateException(msg, ex);
                }
            }
        }


        private static void addParsedDoubleOrError(CachedDataTableColumn aColumn, String aStr)
        {
            try
            {
                aColumn.addElement(Double.parseDouble(aStr));
            }
            catch (NumberFormatException _)
            {
                aColumn.addElement(MissingValue.MIS_ERROR);
            }
        }
    }

}
