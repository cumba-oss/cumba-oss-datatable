package net.cumba.datatable.impl.library.dblib;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import lombok.CustomLog;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.help.CDT;
import net.cumba.datatable.help.URIHelper;
import net.cumba.datatable.impl.library.AbstractLibraryProvider;
import net.cumba.datatable.impl.library.dblib.beans.DataBrowserColumnMetaBean;
import net.cumba.datatable.impl.library.dblib.beans.DataBrowserLibraryBean;
import net.cumba.datatable.impl.metadata.dblib.DataBrowserMetadataLibrary;
import net.cumba.datatable.impl.provider.DataTableMetaSupport;
import net.cumba.datatable.impl.provider.DataTableProviderFactory;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.io.ProbeFailures;
import net.cumba.datatable.io.Property;
import net.cumba.datatable.library.IDataTableLibrary;
import net.cumba.datatable.library.ILibraryMember;
import net.cumba.datatable.library.ILibraryProvider;
import net.cumba.datatable.metadata.IColumnMetadata;
import net.cumba.datatable.metadata.IMetadataLibrary;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.IDataValue;
import org.jspecify.annotations.Nullable;

/**
 * Provider that reads a {@code .dblib} JSON file and creates a {@link DataBrowserLibrary} instance.
 * Handles member listing delegation and column metadata resolution (including
 * {@code displayFormat}) from both internal and external sources.
 */
@CustomLog
public class DataBrowserLibraryProvider extends AbstractLibraryProvider
{

    @Override
    public List<FileInfo> getSupportedFileInfos()
    {
        return DataBrowserLibrarySupplier.FIS;
    }


    @Override
    public List<Property> getProviderProperties(URI aUri, @Nullable FileInfo aFileInfo)
    {
        String beanDefault = computeBeanDefaultName(aUri);
        return List.of(ILibraryProvider.libraryNameProperty(aUri, aFileInfo, beanDefault));
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
        try (InputStream in = URIHelper.openStream(aUri))
        {
            DataBrowserLibraryBean bean = new ObjectMapper().readValue(in,
                    DataBrowserLibraryBean.class);

            String resolvedName = ILibraryProvider.resolveLibraryName(aUri, aFileInfo, aProperties,
                    bean.getName());
            if (!CDT.isBlankOrNull(resolvedName))
            {
                bean = bean.withName(resolvedName);
            }

            DataBrowserLibrary lib = new DataBrowserLibrary(aUri, bean);
            lib.setMetadata(DataBrowserMetadataLibrary.from(bean, aUri));
            return lib;
        }
    }


    /**
     * Reads the {@code .dblib} JSON file just to extract the stored library name for use as default
     * in the library-name property. Returns {@code null} on any failure reading a local file so
     * callers fall back to the URI-based default; a remote ({@code http} / {@code https})
     * {@code .dblib} that cannot be read — or does not parse — aborts the open instead
     * ({@link ProbeFailures}).
     *
     * @throws java.io.UncheckedIOException
     *             if a remote {@code .dblib} cannot be read.
     */
    private static @Nullable String computeBeanDefaultName(URI aUri)
    {
        try
        {
            try (InputStream in = URIHelper.openStream(aUri))
            {
                DataBrowserLibraryBean bean = new ObjectMapper().readValue(in,
                        DataBrowserLibraryBean.class);
                return bean.getName();
            }
        }
        catch (Exception ex)
        {
            ProbeFailures.rethrowIfRemote(aUri, ex);
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Failed to pre-load .dblib for default library name: {0}", aUri, ex);
            return null;
        }
    }


    @Override
    public Stream<? extends ILibraryMember> provideLibraryMembers(IDataTableLibrary aLibrary)
        throws IOException
    {
        if (aLibrary instanceof DataBrowserLibrary dbLib)
        {
            return dbLib.getMembers();
        }
        return Stream.empty();
    }


    @Override
    public Stream<? extends DataTableColumnMeta> provideLibraryMemberColumns(ILibraryMember aMember)
        throws IOException
    {
        if (!(aMember instanceof DataBrowserMember dbm))
        {
            return Stream.empty();
        }

        DataBrowserLibrary lib = dbm.getLibrary();
        DataBrowserLibraryBean bean = lib.getBean();
        URI libraryUri = lib.getUri();
        URI memberUri = dbm.getUri();

        // Collect column metadata: external tables first (lower priority), then internal (higher)
        Map<String, DataTableColumnMeta.DataTableColumnMetaBuilder> columnMap = new LinkedHashMap<>();
        // The declared key of each column, by column name. Written once after both passes: the
        // builder appends meta entries and the first one of a key wins on read, so the internal
        // key can only override the external one if neither has been written yet.
        Map<String, Integer> keySequences = new LinkedHashMap<>();
        loadExternalColumnMeta(bean.getColumnMetaTableUris(), libraryUri, memberUri, columnMap,
                keySequences);
        applyInternalColumnMeta(bean.getColumnMeta(), libraryUri, memberUri, columnMap,
                keySequences);
        for (Map.Entry<String, DataTableColumnMeta.DataTableColumnMetaBuilder> e : columnMap
                .entrySet())
        {
            Integer seq = keySequences.get(e.getKey());
            if (seq != null)
            {
                e.getValue().addMetaData(DataTableMetaSupport.META_KEY_ITEM_KEY_SEQUENCE, seq);
            }
        }

        if (!columnMap.isEmpty())
        {
            AtomicInteger idx = new AtomicInteger(0);
            return columnMap.values().stream()//
                    .map(b -> b.index(idx.getAndIncrement()).build());
        }

        // Fall back to metadata library for column information
        return getColumnsFromMetadata(lib, dbm.getName());
    }


    /**
     * Retrieves column metadata from the library's {@link IMetadataLibrary} if available.
     */
    private Stream<DataTableColumnMeta> getColumnsFromMetadata(DataBrowserLibrary aLib,
            String aMemberName)
    {
        IMetadataLibrary metadata = aLib.getMetadata();
        if (metadata == null)
        {
            return Stream.empty();
        }
        return metadata.getDataTable(aMemberName)//
                .map(dt -> dt.getColumns().stream()//
                        .map(this::mapColumnMetadata))//
                .orElse(Stream.empty());
    }


    /**
     * Maps an {@link IColumnMetadata} to a {@link DataTableColumnMeta}.
     */
    private DataTableColumnMeta mapColumnMetadata(IColumnMetadata aCol)
    {
        DataTableColumnMeta.DataTableColumnMetaBuilder b = DataTableColumnMeta.builder()//
                .name(aCol.getName())//
                .label(aCol.getLabel())//
                .type(aCol.getType())//
                .displayFormat(aCol.getDisplayFormat())//
                .index(aCol.getIndex());
        if (aCol.getKeySequence() > 0)
        {
            b.addMetaData(DataTableMetaSupport.META_KEY_ITEM_KEY_SEQUENCE, aCol.getKeySequence());
        }
        // IColumnMetadata.getNativeType is @Nullable, but the DataTableColumnMeta builder's
        // nativeType setter is @NonNull (see report: DataTableColumnMeta.nativeType contract gap);
        // only set it when present.
        String nativeType = aCol.getNativeType();
        if (nativeType != null)
        {
            b.nativeType(nativeType);
        }
        if (aCol.getLength() > 0)
        {
            b.length(aCol.getLength());
        }
        return b.build();
    }


    @Override
    public @Nullable Object getLibraryAttribute(IDataTableLibrary aLibrary,
            @Nullable String aAttributeKey)
    {
        if (aLibrary instanceof DataBrowserLibrary dbLib)
        {
            Map<String, String> attributes = dbLib.getBean().getAttributes();
            if (attributes != null)
            {
                return attributes.get(aAttributeKey);
            }
        }
        return null;
    }


    /**
     * Loads column metadata from external metadata tables. Rows are matched by the {@code uri} and
     * {@code name} columns as composite key. A row's declared key goes to {@code aKeySequences}.
     */
    private void loadExternalColumnMeta(String[] aTableUris, URI aLibraryUri, URI aMemberUri,
            Map<String, DataTableColumnMeta.DataTableColumnMetaBuilder> aColumnMap,
            Map<String, Integer> aKeySequences)
        throws IOException
    {
        if (CDT.isEmptyOrNull(aTableUris))
        {
            return;
        }
        for (String tableUriStr : aTableUris)
        {
            URI tableUri;
            try
            {
                tableUri = aLibraryUri.resolve(tableUriStr);
            }
            catch (Exception ex)
            {
                LOGGER.log(System.Logger.Level.WARNING, "Invalid column metadata table URI: {0}",
                        tableUriStr, ex);
                continue;
            }

            IDataTable table;
            try
            {
                table = loadMetaTable(tableUri);
            }
            catch (Exception ex)
            {
                LOGGER.log(System.Logger.Level.WARNING, "Failed to load column metadata table: {0}",
                        tableUri, ex);
                continue;
            }
            if (table == null)
            {
                continue;
            }

            int uriCol = table.getColumnIndex("uri");
            int nameCol = table.getColumnIndex("name");
            if (uriCol < 0 || nameCol < 0)
            {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Column metadata table missing required columns (uri, name): {0}",
                        tableUri);
                continue;
            }

            // The live row count, not DataTableMeta.rowCount: the latter is a snapshot that
            // defaults to 0 and is documented as "might be -1". A provider that leaves it unset
            // would make this loop read no rows at all and silently drop every external column
            // label/type/format; a table view whose meta still carries the *source* count would
            // read past the end and throw IndexOutOfBoundsException out of this method.
            long rowCount = table.getRowCount();
            for (long row = 0; row < rowCount; row++)
            {
                String rowUriStr = table.getDataValue(row, uriCol).getValueAsString();
                if (CDT.isBlankOrNull(rowUriStr))
                {
                    continue;
                }

                URI resolvedUri = resolveMetaUri(tableUri, rowUriStr);
                if (resolvedUri == null || !uriMatches(resolvedUri, aMemberUri))
                {
                    continue;
                }

                String colName = table.getDataValue(row, nameCol).getValueAsString();
                if (CDT.isBlankOrNull(colName))
                {
                    continue;
                }

                DataTableColumnMeta.DataTableColumnMetaBuilder builder = aColumnMap
                        .computeIfAbsent(colName, k -> DataTableColumnMeta.builder().name(k));
                applyTableRowToColumnBuilder(table, tableUri, row, uriCol, nameCol, builder,
                        aKeySequences);
            }
        }
    }


    /**
     * Applies column metadata from the internal {@code aColumnMeta} array to the column map. Values
     * from internal metadata override those from external tables.
     * <p>
     * A bean's {@code key} &gt; 0 is the column's declared key: it replaces an external table's key
     * in {@code aKeySequences}, and it wins over a user attribute that is also named
     * {@value DataTableMetaSupport#META_KEY_ITEM_KEY_SEQUENCE}, which is then dropped. {@code 0}
     * means "not a key" and declares nothing.
     */
    private void applyInternalColumnMeta(DataBrowserColumnMetaBean[] aColumnMeta, URI aLibraryUri,
            URI aMemberUri, Map<String, DataTableColumnMeta.DataTableColumnMetaBuilder> aColumnMap,
            Map<String, Integer> aKeySequences)
        throws IOException
    {
        if (CDT.isEmptyOrNull(aColumnMeta))
        {
            return;
        }

        for (DataBrowserColumnMetaBean meta : aColumnMeta)
        {
            if (meta == null)
            {
                continue;
            }
            URI resolvedUri = resolveMetaUri(aLibraryUri, meta.getUri());
            if (resolvedUri == null || !uriMatches(resolvedUri, aMemberUri))
            {
                continue;
            }

            DataTableColumnMeta.DataTableColumnMetaBuilder builder = aColumnMap
                    .computeIfAbsent(meta.getName(), k -> DataTableColumnMeta.builder().name(k));

            if (!CDT.isBlankOrNull(meta.getLabel()))
            {
                builder.label(meta.getLabel());
            }
            if (!CDT.isBlankOrNull(meta.getFormat()))
            {
                builder.displayFormat(meta.getFormat());
            }
            if (!CDT.isBlankOrNull(meta.getType()))
            {
                builder.type(parseColumnType(meta.getType(), meta.getName(), aLibraryUri));
            }

            if (meta.getKey() > 0)
            {
                aKeySequences.put(meta.getName(), meta.getKey());
            }

            Map<String, String> attrs = meta.getAttributes();
            if (attrs != null)
            {
                for (Map.Entry<String, String> entry : attrs.entrySet())
                {
                    if (DataTableMetaSupport.META_KEY_ITEM_KEY_SEQUENCE.equals(entry.getKey())
                            && aKeySequences.containsKey(meta.getName()))
                    {
                        // the declared key wins over a same-named attribute
                        continue;
                    }
                    builder.addMetaData(entry.getKey(), entry.getValue());
                }
            }
        }
    }


    /**
     * Resolves a column type string from a {@code .dblib} (or from a metadata table it points at)
     * to a {@link DataValueType}.
     * <p>
     * ⚠ An unrecognised value RAISES rather than falling back. It used to log a WARNING and leave
     * the builder's {@code type} unset, which produced a column whose {@code getType()} is
     * {@code null} - silently wrong data for every consumer downstream, announced only in a log
     * nobody reads. Owner ruling 2026-09-14 (finding Q42): an unrecognised type string is a corrupt
     * file and must say so; no default is substituted. This is a behaviour change for files that
     * already exist, so the message names the offending value, the column and the source that
     * carried it.
     *
     * @throws IOException
     *             if {@code aType} does not name a {@link DataValueType}.
     */
    private static DataValueType parseColumnType(String aType, @Nullable String aColumnName,
            URI aSource)
        throws IOException
    {
        try
        {
            return DataValueType.valueOf(aType);
        }
        catch (IllegalArgumentException ex)
        {
            throw new IOException("Unknown data value type '" + aType + "' for column '"
                    + aColumnName + "' in " + aSource, ex);
        }
    }


    /**
     * Applies column attribute values from a metadata table row to a column builder. Handles known
     * columns (label, format, type, key) explicitly and stores all others as custom metadata.
     * <p>
     * The {@code key} cell is stored twice: as the raw custom entry {@code key} it always was, and,
     * when it declares a key, as the column's key sequence in {@code aKeySequences} (see
     * {@link #parseKeySequence}).
     *
     * @throws IOException
     *             if the {@code type} cell names no {@link DataValueType} or the {@code key} cell
     *             is not a whole number &gt;= 0.
     */
    private void applyTableRowToColumnBuilder(IDataTable aTable, URI aTableUri, long aRow,
            int aUriCol, int aNameCol, DataTableColumnMeta.DataTableColumnMetaBuilder aBuilder,
            Map<String, Integer> aKeySequences)
        throws IOException
    {
        String columnName = aTable.getDataValue(aRow, aNameCol).getValueAsString();
        int colCount = aTable.getMetaData().getColumnCount();
        for (int col = 0; col < colCount; col++)
        {
            if (col == aUriCol || col == aNameCol)
            {
                continue;
            }

            String colName = aTable.getMetaData().getColumn(col).getName().toLowerCase(Locale.ROOT);
            IDataValue cell = aTable.getDataValue(aRow, col);
            String value = cell.getValueAsString();
            if (CDT.isBlankOrNull(value))
            {
                continue;
            }

            switch (colName)
            {
            case "label" -> aBuilder.label(value);
            case "format" -> aBuilder.displayFormat(value);
            case "type" -> aBuilder.type(parseColumnType(value, columnName, aTableUri));
            case "key" ->
            {
                // the raw entry, as before
                aBuilder.addMetaData(colName, value);
                Integer seq = parseKeySequence(cell, value, columnName, aTableUri);
                if (seq != null)
                {
                    aKeySequences.put(columnName, seq);
                }
            }
            default -> aBuilder.addMetaData(colName, value);
            }
        }
    }


    /**
     * Reads the key sequence an external column-metadata table's {@code key} cell declares.
     * <p>
     * A missing or empty cell (tested on the cell, before it is turned into text: a missing numeric
     * cell reads as {@code "."}) and {@code 0} mean "not a key" and return {@code null}. Any other
     * value must be a whole number &gt;= 0 ({@code 2}, {@code 2.0}); anything else ({@code Y},
     * {@code 1.5}, {@code -1}) is a corrupt file and RAISES, naming the value, the column and the
     * source - as an unknown {@code type} does ({@link #parseColumnType}, owner ruling Q42
     * 2026-09-14). PLAN-compare-type-change-and-column-key D-b.
     *
     * @throws IOException
     *             if the cell is not a whole number &gt;= 0.
     */
    private static @Nullable Integer parseKeySequence(IDataValue aCell, String aText,
            @Nullable String aColumnName, URI aSource)
        throws IOException
    {
        if (aCell.isEmptyOrMissing())
        {
            return null;
        }
        int seq;
        try
        {
            seq = new BigDecimal(aText.trim()).intValueExact();
        }
        catch (NumberFormatException | ArithmeticException ex)
        {
            throw malformedKey(aText, aColumnName, aSource, ex);
        }
        if (seq < 0)
        {
            throw malformedKey(aText, aColumnName, aSource, null);
        }
        return seq == 0 ? null : seq;
    }


    private static IOException malformedKey(String aText, @Nullable String aColumnName, URI aSource,
            @Nullable Exception aCause)
    {
        return new IOException("Malformed key '" + aText + "' for column '" + aColumnName + "' in "
                + aSource + ": expected a whole number >= 0 (0 = not a key)", aCause);
    }


    /**
     * Loads an external column-metadata table through the registered data-table providers.
     *
     * @param aTableUri
     *            the absolute URI of the metadata table.
     * @return the loaded table, or {@code null} when no provider can read the URI.
     * @throws IOException
     *             if a provider failed to read the table.
     */
    @Nullable
    IDataTable loadMetaTable(URI aTableUri) throws IOException
    {
        return DataTableProviderFactory.getFactory().provide(aTableUri, null);
    }


    /**
     * Resolves a URI string from a metadata source against a base URI. If the URI string is already
     * absolute, it is returned as-is. Otherwise it is resolved relative to the base URI.
     */
    private @Nullable URI resolveMetaUri(URI aBaseUri, String aUriStr)
    {
        try
        {
            URI uri = new URI(aUriStr);
            if (uri.isAbsolute())
            {
                return uri.normalize();
            }
            return aBaseUri.resolve(uri).normalize();
        }
        catch (URISyntaxException ex)
        {
            LOGGER.log(System.Logger.Level.WARNING, "Invalid metadata URI: {0}", aUriStr, ex);
            return null;
        }
    }


    /**
     * Checks whether two URIs reference the same resource by comparing their normalized forms.
     */
    private boolean uriMatches(URI aResolvedUri, URI aMemberUri)
    {
        return aResolvedUri.normalize().equals(aMemberUri.normalize());
    }

}
