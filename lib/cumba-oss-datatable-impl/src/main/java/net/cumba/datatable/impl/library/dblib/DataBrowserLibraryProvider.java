package net.cumba.datatable.impl.library.dblib;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
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
import net.cumba.datatable.impl.library.AbstractLibraryProvider;
import net.cumba.datatable.impl.library.dblib.beans.DataBrowserColumnMetaBean;
import net.cumba.datatable.impl.library.dblib.beans.DataBrowserLibraryBean;
import net.cumba.datatable.impl.metadata.dblib.DataBrowserMetadataLibrary;
import net.cumba.datatable.impl.provider.DataTableProviderFactory;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.io.Property;
import net.cumba.datatable.library.IDataTableLibrary;
import net.cumba.datatable.library.ILibraryMember;
import net.cumba.datatable.library.ILibraryProvider;
import net.cumba.datatable.metadata.IColumnMetadata;
import net.cumba.datatable.metadata.IMetadataLibrary;
import net.cumba.datatable.values.DataValueType;
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
        URL url = aUri.toURL();
        try (InputStream in = url.openStream())
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
     * in the library-name property. Returns {@code null} on any failure so callers fall back to the
     * URI-based default.
     */
    private static @Nullable String computeBeanDefaultName(URI aUri)
    {
        try
        {
            URL url = aUri.toURL();
            try (InputStream in = url.openStream())
            {
                DataBrowserLibraryBean bean = new ObjectMapper().readValue(in,
                        DataBrowserLibraryBean.class);
                return bean.getName();
            }
        }
        catch (Exception ex)
        {
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
        loadExternalColumnMeta(bean.getColumnMetaTableUris(), libraryUri, memberUri, columnMap);
        applyInternalColumnMeta(bean.getColumnMeta(), libraryUri, memberUri, columnMap);

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
        // IColumnMetadata.getNativeType() is optional (e.g. the .dblib format carries none), but
        // DataTableColumnMeta.nativeType is required; fall back to the column type's token.
        String nativeType = aCol.getNativeType();
        DataTableColumnMeta.DataTableColumnMetaBuilder b = DataTableColumnMeta.builder()//
                .name(aCol.getName())//
                .label(aCol.getLabel())//
                .type(aCol.getType())//
                .displayFormat(aCol.getDisplayFormat())//
                .nativeType(nativeType != null ? nativeType : aCol.getType().name())//
                .index(aCol.getIndex());
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
     * {@code name} columns as composite key.
     */
    private void loadExternalColumnMeta(String[] aTableUris, URI aLibraryUri, URI aMemberUri,
            Map<String, DataTableColumnMeta.DataTableColumnMetaBuilder> aColumnMap)
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
                applyTableRowToColumnBuilder(table, tableUri, row, uriCol, nameCol, builder);
            }
        }
    }


    /**
     * Applies column metadata from the internal {@code aColumnMeta} array to the column map. Values
     * from internal metadata override those from external tables.
     */
    private void applyInternalColumnMeta(DataBrowserColumnMetaBean[] aColumnMeta, URI aLibraryUri,
            URI aMemberUri, Map<String, DataTableColumnMeta.DataTableColumnMetaBuilder> aColumnMap)
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

            Map<String, String> attrs = meta.getAttributes();
            if (attrs != null)
            {
                for (Map.Entry<String, String> entry : attrs.entrySet())
                {
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
     */
    private void applyTableRowToColumnBuilder(IDataTable aTable, URI aTableUri, long aRow,
            int aUriCol, int aNameCol, DataTableColumnMeta.DataTableColumnMetaBuilder aBuilder)
        throws IOException
    {
        int colCount = aTable.getMetaData().getColumnCount();
        for (int col = 0; col < colCount; col++)
        {
            if (col == aUriCol || col == aNameCol)
            {
                continue;
            }

            String colName = aTable.getMetaData().getColumn(col).getName().toLowerCase(Locale.ROOT);
            String value = aTable.getDataValue(aRow, col).getValueAsString();
            if (CDT.isBlankOrNull(value))
            {
                continue;
            }

            switch (colName)
            {
            case "label" -> aBuilder.label(value);
            case "format" -> aBuilder.displayFormat(value);
            case "type" -> aBuilder.type(parseColumnType(value,
                    aTable.getDataValue(aRow, aNameCol).getValueAsString(), aTableUri));
            // "key" handled as custom metadata, same as default
            default -> aBuilder.addMetaData(colName, value);
            }
        }
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

}
