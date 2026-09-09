package net.cumba.datatable.provider.parquet;

import com.fasterxml.jackson.databind.JsonNode;
import com.jerolba.carpet.CarpetReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import lombok.CustomLog;
import lombok.NonNull;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableColumnMeta.DataTableColumnMetaBuilder;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.help.CDT;
import net.cumba.datatable.impl.CachedDataTableColumn;
import net.cumba.datatable.impl.provider.AbstractDataTableProvider;
import net.cumba.datatable.impl.provider.AbstractTableDataParser;
import net.cumba.datatable.impl.provider.DataTableMetaSupport;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.MissingValue;
import net.cumba.datatable.values.TemporalOrigin;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.hadoop.metadata.BlockMetaData;
import org.apache.parquet.hadoop.metadata.ParquetMetadata;
import org.apache.parquet.io.LocalInputFile;
import org.apache.parquet.schema.LogicalTypeAnnotation;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType;
import org.apache.parquet.schema.Type;
import org.jspecify.annotations.Nullable;

/**
 * Data table provider that reads Apache Parquet files using the Carpet library. Uses
 * {@link CarpetReader} with {@code Map.class} for schema-agnostic row-based reading, feeding rows
 * into an {@link AbstractTableDataParser} for parallel column population.
 *
 * <p>
 * Supports local files and remote URIs (which are downloaded to a temporary file first). Parses R
 * metadata from the Parquet schema to extract column labels when available.
 * </p>
 */
@CustomLog
public class ParquetTableProvider extends AbstractDataTableProvider
{

    /**
     * Days from the Unix epoch (1970-01-01) to the data table's temporal origin (1960-01-01): 3653.
     *
     * <p>
     * Parquet declares DATE / TIME / TIMESTAMP structurally in its schema, so temporal values are
     * re-based onto that origin at load time (F-prov-08 / F-prov-09) — the same convention the SAS,
     * XPT, CDT and XLSX paths already use — and the matching SAS display format is attached to the
     * column metadata. F-prov-14: the origin and both offsets are defined once, in
     * {@link TemporalOrigin}; nothing here hand-writes {@code 3653} or {@code 315619200} any more.
     * </p>
     */
    static final long SAS_EPOCH_OFFSET_DAYS = TemporalOrigin.OFFSET_DAYS;

    /**
     * Seconds from the Unix epoch to the temporal origin: 3653 &times; 86400 = 315,619,200. See
     * {@link TemporalOrigin#OFFSET_SECONDS}.
     */
    static final long SAS_EPOCH_OFFSET_SECONDS = TemporalOrigin.OFFSET_SECONDS;

    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    /** SAS display format attached to Parquet DATE columns (days since 1960-01-01). */
    static final String SAS_DATE_FORMAT = "E8601DA.";

    /** SAS display format attached to Parquet TIME columns (seconds since midnight). */
    static final String SAS_TIME_FORMAT = "E8601TM.";

    /** SAS display format attached to Parquet TIMESTAMP columns (seconds since 1960-01-01). */
    static final String SAS_DATETIME_FORMAT = "E8601DT.";

    /**
     * Converts an instant to SAS datetime seconds (seconds since 1960-01-01T00:00:00 UTC,
     * fractional).
     *
     * <p>
     * <b>Precision — this is NOT a lossless encoding, and it never was.</b> At today's magnitudes a
     * {@code double} holding SAS seconds has an ulp of &asymp; 477&nbsp;ns; the previous
     * epoch-nanos encoding had an ulp of &asymp; 256&nbsp;ns. Both round-trip <em>microsecond</em>
     * timestamps faithfully; <em>neither</em> preserves nanoseconds, so a Parquet
     * {@code TIMESTAMP(NANOS)} loses sub-microsecond precision either way. The SAS convention was
     * chosen for consistency across the stack (every other provider stores temporals as SAS-epoch
     * seconds/days), not for precision — do not "optimise" this back to epoch nanos in the belief
     * that it is more accurate.
     * </p>
     *
     * @param aInstant
     *            the instant to convert
     * @return fractional seconds since the SAS epoch
     */
    private static double toSasSeconds(Instant aInstant)
    {
        // Sum the whole seconds in exact long arithmetic first, then widen once and add
        // the fractional part. A local rather than parentheses: the grouping is what keeps
        // the second count exact, and PMD flags the (redundant) parentheses that showed it.
        long wholeSeconds = aInstant.getEpochSecond() + SAS_EPOCH_OFFSET_SECONDS;
        return wholeSeconds + aInstant.getNano() / NANOS_PER_SECOND;
    }


    /** {@inheritDoc} */
    @Override
    public List<FileInfo> getSupportedFileInfos()
    {
        return ParquetProviderSupplier.FIS;
    }


    /**
     * Provides an {@link IDataTable} by reading the Parquet file at the given URI. For
     * {@code file://} URIs the file is read directly; for other schemes the content is first
     * downloaded to a temporary file.
     *
     * @param aURI
     *            the URI of the Parquet file to read
     * @param aFileInfo
     *            the file info describing the format
     * @return the loaded data table
     * @throws IOException
     *             if reading fails or the user aborts loading
     */
    @SuppressWarnings("PMD.EmptyCatchBlock")
    @Override
    public IDataTable provide(URI aURI, @Nullable FileInfo aFileInfo) throws IOException
    {
        if ("file".equalsIgnoreCase(aURI.getScheme()))
        {
            return getTable(new File(aURI), aURI);
        }

        File f = downloadToFile(aURI);
        f.deleteOnExit();
        try
        {
            return getTable(f, aURI);
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
    public DataTableMeta provideMetaData(URI aURI, @Nullable FileInfo aFileInfo) throws IOException
    {
        if ("file".equalsIgnoreCase(aURI.getScheme()))
        {
            return getTableMeta(new File(aURI), aURI);
        }

        File f = downloadToFile(aURI);
        f.deleteOnExit();
        try
        {
            return getTableMeta(f, aURI);
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


    /**
     * Reads only the Parquet footer of a file and returns the derived {@link DataTableMeta},
     * without streaming any row data. The row count is taken from the footer block statistics.
     */
    public DataTableMeta getTableMeta(File aFile, URI aURI) throws IOException
    {
        DataTableMetaSupport dtms = new DataTableMetaSupport(getMetadata());
        dtms.setTable(aURI);
        dtms.setFileFormat("PARQUET", null);
        dtms.setDatasetSize(aFile, aURI);

        LocalInputFile inputFile = new LocalInputFile(aFile.toPath());
        MessageType parquetSchema;
        Map<String, String> fileKeyValueMetadata;
        long expectedRowCount;
        try (ParquetFileReader reader = ParquetFileReader.open(inputFile))
        {
            ParquetMetadata footer = reader.getFooter();
            fileKeyValueMetadata = footer.getFileMetaData().getKeyValueMetaData();
            parquetSchema = footer.getFileMetaData().getSchema();
            expectedRowCount = footer.getBlocks().stream().mapToLong(BlockMetaData::getRowCount)
                    .sum();
        }

        JsonNode cumbaRoot = fileKeyValueMetadata != null
                ? ParquetMetadataCodec
                        .parse(fileKeyValueMetadata.get(ParquetMetadataCodec.META_KEY))
                : null;

        List<Type> fields = parquetSchema.getFields();
        for (int i = 0; i < fields.size(); i++)
        {
            addMetaColumn(fields.get(i), i, cumbaRoot, dtms);
        }

        if (fileKeyValueMetadata != null)
        {
            for (Entry<String, String> e : fileKeyValueMetadata.entrySet())
            {
                String key = e.getKey();
                if (CDT.isBlankOrNull(key))
                {
                    continue;
                }
                if (CDT.isIn(key.toLowerCase(Locale.ROOT), "r", "arrow:schema"))
                {
                    continue;
                }
                if (ParquetMetadataCodec.META_KEY.equals(key))
                {
                    continue;
                }
                dtms.getTableMeta().addMetaData(key, e.getValue());
            }
        }

        ParquetMetadataCodec.applyTable(cumbaRoot, dtms.getTableMeta());

        return dtms.getTableMeta()//
                .rowCount(expectedRowCount)//
                .totalRowCount(expectedRowCount)//
                .build();
    }


    /**
     * Reads a Parquet file and returns it as an {@link IDataTable}. Uses Carpet's
     * {@link CarpetReader} for row-based reading and an {@link AbstractTableDataParser} for
     * parallel column population.
     *
     * @param aFile
     *            the local file to read
     * @param aURI
     *            the original URI (used for table naming and Define-XML lookup)
     * @return the loaded data table
     * @throws IOException
     *             if reading fails or the user aborts loading
     */
    @SuppressWarnings("unchecked")
    public IDataTable getTable(File aFile, URI aURI) throws IOException
    {
        try
        {
            DataTableMetaSupport dtms = new DataTableMetaSupport(getMetadata());
            dtms.setTable(aURI);
            dtms.setFileFormat("PARQUET", null);
            dtms.setDatasetSize(aFile, aURI);

            // Read Parquet schema metadata
            LocalInputFile inputFile = new LocalInputFile(aFile.toPath());
            MessageType parquetSchema;
            Map<String, String> fileKeyValueMetadata;

            long expectedRowCount;
            try (ParquetFileReader reader = ParquetFileReader.open(inputFile))
            {
                ParquetMetadata footer = reader.getFooter();
                fileKeyValueMetadata = footer.getFileMetaData().getKeyValueMetaData();
                parquetSchema = footer.getFileMetaData().getSchema();
                expectedRowCount = footer.getBlocks().stream().mapToLong(BlockMetaData::getRowCount)
                        .sum();
            }

            JsonNode cumbaRoot = fileKeyValueMetadata != null
                    ? ParquetMetadataCodec
                            .parse(fileKeyValueMetadata.get(ParquetMetadataCodec.META_KEY))
                    : null;

            // Build column metadata from Parquet schema
            List<Type> fields = parquetSchema.getFields();
            for (int i = 0; i < fields.size(); i++)
            {
                addMetaColumn(fields.get(i), i, cumbaRoot, dtms);
            }

            // Add file-level metadata (excluding R metadata and the Cumba blob)
            if (fileKeyValueMetadata != null)
            {
                for (Entry<String, String> e : fileKeyValueMetadata.entrySet())
                {
                    String key = e.getKey();
                    if (CDT.isBlankOrNull(key))
                    {
                        continue;
                    }
                    if (CDT.isIn(key.toLowerCase(Locale.ROOT), "r", "arrow:schema"))
                    {
                        continue;
                    }
                    if (ParquetMetadataCodec.META_KEY.equals(key))
                    {
                        continue;
                    }
                    dtms.getTableMeta().addMetaData(key, e.getValue());
                }
            }

            ParquetMetadataCodec.applyTable(cumbaRoot, dtms.getTableMeta());

            DataTableMeta meta = dtms.getTableMeta().build();

            // Create table parser and read rows via Carpet
            Parquet2TableDataParser tableParser = new Parquet2TableDataParser(meta, fields);

            @SuppressWarnings("rawtypes")
            CarpetReader<Map> carpetReader = new CarpetReader<>(aFile, Map.class);
            for (Map<String, Object> row : carpetReader)
            {
                tableParser.addDataRow(row);
            }

            IDataTable table = tableParser.completeTable();
            long parsedRowCount = table.getRowCount();
            if (parsedRowCount != expectedRowCount)
            {
                throw new IOException("Invalid number of rows read. Expected %d but found %d!"
                        .formatted(expectedRowCount, parsedRowCount));
            }

            return table;
        }
        catch (RuntimeException | IOException ex)
        {
            throw ex;
        }
        catch (Exception ex)
        {
            throw new IOException(ex);
        }
    }


    /**
     * Adds column metadata from a Parquet schema field to the metadata support.
     */
    protected void addMetaColumn(Type aField, int aIndex, @Nullable JsonNode aCumbaRoot,
            DataTableMetaSupport aSupport)
    {
        String name = aField.getName();
        if (name == null)
        {
            name = "V" + (aIndex + 1);
            LOGGER.log(Level.WARNING,
                    "Parquet field %d has no name; using fallback '%s'".formatted(aIndex, name));
        }
        DataValueType dvt = getDataValueType(aField);

        DataTableColumnMetaBuilder b = aSupport.addColumn(name, dvt);

        if (aField.isPrimitive())
        {
            b.nativeType(aField.asPrimitiveType().getPrimitiveTypeName().toString());

            // F-prov-08 / F-prov-09: temporal columns are re-based onto the SAS epoch at load
            // time (see addData2Column), so attach the matching SAS display format. Set before
            // applyColumn so explicit Cumba per-column metadata still wins.
            LogicalTypeAnnotation lta = aField.asPrimitiveType().getLogicalTypeAnnotation();
            if (lta instanceof LogicalTypeAnnotation.DateLogicalTypeAnnotation)
            {
                b.displayFormat(SAS_DATE_FORMAT);
            }
            else if (lta instanceof LogicalTypeAnnotation.TimeLogicalTypeAnnotation)
            {
                b.displayFormat(SAS_TIME_FORMAT);
            }
            else if (lta instanceof LogicalTypeAnnotation.TimestampLogicalTypeAnnotation)
            {
                b.displayFormat(SAS_DATETIME_FORMAT);
            }
        }

        // Cumba per-column metadata wins over the Parquet nativeType fallback.
        ParquetMetadataCodec.applyColumn(aCumbaRoot, name, b);
    }


    /**
     * Maps a Parquet schema type to the internal {@link DataValueType}.
     *
     * @param aField
     *            the Parquet schema field
     * @return the corresponding {@link DataValueType}
     */
    protected DataValueType getDataValueType(Type aField)
    {
        if (!aField.isPrimitive())
        {
            return DataValueType.OTHER;
        }

        PrimitiveType pt = aField.asPrimitiveType();
        LogicalTypeAnnotation lta = pt.getLogicalTypeAnnotation();

        // Check logical type annotations first
        if (lta instanceof LogicalTypeAnnotation.StringLogicalTypeAnnotation)
        {
            return DataValueType.STRING;
        }
        if (lta instanceof LogicalTypeAnnotation.EnumLogicalTypeAnnotation)
        {
            return DataValueType.STRING;
        }
        if (lta instanceof LogicalTypeAnnotation.DateLogicalTypeAnnotation
                || lta instanceof LogicalTypeAnnotation.TimeLogicalTypeAnnotation
                || lta instanceof LogicalTypeAnnotation.TimestampLogicalTypeAnnotation)
        {
            return DataValueType.DOUBLE;
        }
        if (lta instanceof LogicalTypeAnnotation.DecimalLogicalTypeAnnotation)
        {
            return DataValueType.DOUBLE;
        }
        if (lta instanceof LogicalTypeAnnotation.IntLogicalTypeAnnotation)
        {
            return DataValueType.LONG;
        }

        // Fall back to primitive type
        return switch (pt.getPrimitiveTypeName())
        {
        case BOOLEAN -> DataValueType.BOOLEAN;
        case INT32, INT64 -> DataValueType.LONG;
        case FLOAT, DOUBLE -> DataValueType.DOUBLE;
        case BINARY, FIXED_LEN_BYTE_ARRAY -> DataValueType.STRING;
        case INT96 -> DataValueType.DOUBLE;
        };
    }


    /**
     * Stream a non-{@code file:} URI to a local temporary file so the random-access Parquet reader
     * can read it. corej's {@code AbstractGenericProvider} does not provide a
     * {@code downloadToFile} helper (it was dropped in the OSS extraction), so the provider
     * supplies its own.
     *
     * @param aUri
     *            the source URI.
     * @return a temporary file holding the downloaded content (caller deletes it).
     * @throws IOException
     *             on any I/O error.
     */
    protected File downloadToFile(URI aUri) throws IOException
    {
        File tmp = File.createTempFile("cumba-oss-parquet-", ".parquet");
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

    /**
     * Inner table data parser that processes rows from Carpet's {@code Map<String, Object>} output.
     * Each row is a map of column name to value. Values are dispatched to columns based on column
     * index, with type conversion matching the Parquet schema.
     */
    private class Parquet2TableDataParser extends AbstractTableDataParser<Map<String, Object>>
    {

        private final String[] columnNames;

        Parquet2TableDataParser(@NonNull DataTableMeta aMeta, List<Type> aFields)
        {
            super(ParquetTableProvider.this, aMeta);
            columnNames = new String[aFields.size()];
            for (int i = 0; i < aFields.size(); i++)
            {
                // F-E24: null-named Parquet fields are rare but possible. Match the metadata-side
                // fallback in addMetaColumn so the column name used to look up row values lines up
                // with the synthesized metadata column name. Without this the lookup at row time
                // (aRowSlice.get(ridx).get(colName)) would always miss for the null-named column
                // and the column would still be silently all-missing — and worse, would mis-index
                // if the column order in the Carpet row map happened not to be null-keyed.
                String name = aFields.get(i).getName();
                columnNames[i] = (name != null) ? name : "V" + (i + 1);
            }
        }


        @Override
        protected void addData2Column(List<Map<String, Object>> aRowSlice, int aColumnIndex,
                DataTableColumnMeta aMetaColumn, CachedDataTableColumn aDataColumn)
        {
            String colName = columnNames[aColumnIndex];
            DataValueType type = aMetaColumn.getType();

            int rowCount = aRowSlice.size();
            for (int ridx = 0; ridx < rowCount; ridx++)
            {
                Object val = aRowSlice.get(ridx).get(colName);

                if (val == null)
                {
                    // A Parquet null is an explicit "no value" that the format can express in a
                    // column of ANY type, character included, so it is loaded as MissingValue.MIS
                    // in every column type. An empty string the file genuinely contains arrives as
                    // "" through the `instanceof String` arm below and stays "" — the two are
                    // distinct in the model.
                    //
                    // This supersedes Fix #161's mechanism (which mapped a null STRING cell to ""
                    // here so a blank cell was format-independent at ingestion) while keeping its
                    // goal: finding-equivalence is now guaranteed at CONSUMPTION, because every
                    // blankness consumer asks isEmptyOrMissing(), for which a MissingValue and a
                    // "" are both blank. Only the grouping-key encoding keeps them apart, and
                    // there deliberately (they are distinct keys with a shared disposition).
                    //
                    // F-prov-15 (owner ruling: MIS is correct). A Parquet null carries no
                    // missing-value semantics of its own — the format has exactly one kind of
                    // null — so it maps to MissingValue.MIS, the stack-wide default for any
                    // source without its own flavours of missing. CSV, XLSX and DSJ do the same.
                    // The documented exception is R: RData/RDS distinguish NA, and the RDS
                    // provider maps that to MissingValue.NA (F-prov-13). Do not "improve" this
                    // into NA here — that would claim a distinction the Parquet file never made.
                    aDataColumn.addElement(MissingValue.MIS);
                }
                else if (val instanceof String str)
                {
                    // tri(non-null) never returns null (only tri(null) does); str is non-null here.
                    aDataColumn.addElement(Objects.requireNonNull(tri(str)));
                }
                else if (val instanceof Boolean boolVal)
                {
                    aDataColumn.addElement(boolVal);
                }
                else if (val instanceof Number num)
                {
                    if (type == DataValueType.LONG)
                    {
                        aDataColumn.addElement(num.longValue());
                    }
                    else
                    {
                        double dbl = num.doubleValue();
                        if (Double.isNaN(dbl))
                        {
                            aDataColumn.addElement(
                                    MissingValue.forValue(dbl, MissingValue.MIS_UNKNOWN));
                        }
                        else
                        {
                            aDataColumn.addElement(dbl);
                        }
                    }
                }
                else if (val instanceof LocalDate ld)
                {
                    // F-prov-08: SAS days since 1960-01-01 (not Unix epoch days), matching the
                    // SAS/XPT/CDT/XLSX providers; addMetaColumn attaches the SAS DATE format.
                    aDataColumn.addElement((double) (ld.toEpochDay() + SAS_EPOCH_OFFSET_DAYS));
                }
                else if (val instanceof LocalTime lt)
                {
                    // F-prov-08: SAS time — (fractional) seconds since midnight, not nanos;
                    // addMetaColumn attaches the SAS TIME format.
                    aDataColumn.addElement(lt.toNanoOfDay() / NANOS_PER_SECOND);
                }
                else if (val instanceof LocalDateTime ldt)
                {
                    // F-prov-09: SAS datetime — seconds since 1960-01-01 (UTC), matching the
                    // Instant branch so the two paths stay consistent. History: the original
                    // expression mixed microseconds-per-day with nanoseconds-of-day, producing
                    // meaningless numbers and overflowing for dates past ~2261; an interim fix
                    // encoded epoch nanos, which no format in the stack could render. See
                    // toSasSeconds for the precision trade-off.
                    aDataColumn.addElement(toSasSeconds(ldt.toInstant(ZoneOffset.UTC)));
                }
                else if (val instanceof Instant inst)
                {
                    // F-prov-09: SAS datetime seconds (previously epoch nanos); see toSasSeconds
                    // for the precision trade-off.
                    aDataColumn.addElement(toSasSeconds(inst));
                }
                else if (val instanceof BigDecimal bd)
                {
                    // F-E15: Parquet DECIMAL columns can carry values that exceed double
                    // precision. Lossy conversion silently corrupts identifiers / financial
                    // numbers. Verify round-trip and fail explicitly so the user knows to
                    // re-export as STRING.
                    double d = bd.doubleValue();
                    if (BigDecimal.valueOf(d).compareTo(bd) != 0)
                    {
                        throw new UncheckedIOException(new IOException("DECIMAL column value " + bd
                                + " (column " + colName + " row " + ridx
                                + ") exceeds double precision and cannot be represented "
                                + "without loss. Convert column to STRING in source if precision "
                                + "must be preserved."));
                    }
                    aDataColumn.addElement(d);
                }
                else if (val instanceof Enum<?> enumVal)
                {
                    // tri(non-null) never returns null; Enum.name() is non-null.
                    aDataColumn.addElement(Objects.requireNonNull(tri(enumVal.name())));
                }
                else
                {
                    // tri(non-null) never returns null; val.toString() is non-null.
                    aDataColumn.addElement(Objects.requireNonNull(tri(val.toString())));
                }
            }
        }
    }

}
