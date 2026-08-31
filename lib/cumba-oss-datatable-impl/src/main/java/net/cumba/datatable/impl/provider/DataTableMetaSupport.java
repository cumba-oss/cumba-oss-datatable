package net.cumba.datatable.impl.provider;

import java.net.URI;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import lombok.NonNull;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableColumnMeta.DataTableColumnMetaBuilder;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.DataTableMeta.DataTableMetaBuilder;
import net.cumba.datatable.help.CDT;
import net.cumba.datatable.metadata.IColumnMetadata;
import net.cumba.datatable.metadata.IDataTableMetadata;
import net.cumba.datatable.metadata.IMetadataLibrary;
import net.cumba.datatable.values.DataValueType;
import org.jspecify.annotations.Nullable;

/**
 * A supporting class for {@link DataTableMeta} generation. This class can use a
 * {@link IMetadataLibrary} to initialize metadata from.
 */
public class DataTableMetaSupport
{

    public static final String META_KEY_SAS_DATASET_NAME = "SASDatasetName";

    public static final String META_KEY_SAS_FIELD_NAME = "SASFieldName";

    public static final String META_KEY_COMMENT = "Comment";

    public static final String META_KEY_ITEM_METHOD = "Method";

    public static final String META_KEY_ITEM_KEY_SEQUENCE = "KeySequence";

    public static final String META_KEY_ITEM_MANDATORY = "Mandatory";

    public static final String META_KEY_ITEM_ORDER_NUMBER = "OrderNumber";

    public static final String META_KEY_ITEM_NO_DATA = "NoData";

    public static final String META_KEY_ITEM_ROLE = "Role";

    public static final String META_KEY_ITEM_ORIGIN = "Origin";

    public static final String META_KEY_ITEM_SIGNIFICANT_DIGITS = "SignificantDigits";

    public static final String META_KEY_PURPOSE = "Purpose";

    public static final String META_KEY_STANDARD = "Standard";

    public static final String META_KEY_STRUCTURE = "Structure";

    public static final String META_KEY_REPEATING = "Repeating";

    public static final String META_KEY_CREATED = "Created";

    public static final String META_KEY_MODIFIED = "Modified";

    public static final String META_KEY_ENCODING = "Encoding";

    /**
     * The physical source format the provider parsed the table from (CX-2). Lower-case key: it is
     * read by the rule engine's {@code extract_metadata("file_format")} accessor, matching the
     * {@code dataset_size} / {@code dataset_location} accessor-key convention.
     */
    public static final String META_KEY_FILE_FORMAT = "file_format";

    /** The transport/container version for formats that define one (XPORT ⇒ {@code "5"}). */
    public static final String META_KEY_TRANSPORT_VERSION = "transport_version";

    /**
     * The optional metadata library.
     */
    private @Nullable IMetadataLibrary metadata;

    /**
     * An optional data table metadata from the metadata library that represents the table to work
     * on.
     */
    private @Nullable IDataTableMetadata metaTable;

    /**
     * A flag to indicate if the column names are case sensitive (true) or not (false).
     */
    private boolean caseSensitive = false;

    /**
     * The list of columns (as builders) that are already added to the table.
     */
    private List<DataTableColumnMetaBuilder> columns = new ArrayList<>();

    /**
     * A set of column names of columns, already added. It is not allowed to have two columns with
     * the same name.
     */
    private Set<String> columnNames = new HashSet<>();

    /**
     * The builder for table metadata. This is created in {@link #setTable(URI, String)}.
     */
    private @Nullable DataTableMetaBuilder tableMetaBuilder;

    /**
     * Create a support class that supports in table metadata retrieval.
     *
     * @param aMetadata
     *            an optional metadata library. This might be null.
     */
    public DataTableMetaSupport(@Nullable IMetadataLibrary aMetadata)
    {
        this(aMetadata, false);
    }


    /**
     * Create a support class that supports in table metadata retrieval.
     *
     * @param aMetadata
     *            an optional metadata library. This might be null.
     * @param aCaseSensitive
     *            a flag to indicate if column names are case sensitive (true) or not (false).
     */
    public DataTableMetaSupport(@Nullable IMetadataLibrary aMetadata, boolean aCaseSensitive)
    {
        metadata = aMetadata;
        caseSensitive = aCaseSensitive;
    }


    public void setTable(URI aUri)
    {
        setTable(aUri, null);
    }


    /**
     * Set the table to work on.
     *
     * @param aUri
     *            the URI of the table to process.
     * @param aName
     *            An optional table name of the table to process.
     */
    public void setTable(URI aUri, @Nullable String aName)
    {
        if (tableMetaBuilder != null)
        {
            throw new IllegalStateException("Table already set!");
        }

        // we set metaTable to null
        metaTable = null;
        // and try to initialize from metadata by URI
        if (metadata != null && aUri != null)
        {
            metaTable = findTableByUri(aUri);
        }

        // now ensure we have a name; a blank/null aName is replaced via getTableNameFor (which
        // never
        // returns null), so after this block the name is non-null. NullAway cannot model
        // CDT.isBlankOrNull, hence the requireNonNull to capture the established invariant.
        String resolvedName = aName;
        if (CDT.isBlankOrNull(resolvedName))
        {
            resolvedName = getTableNameFor(aUri);
        }
        String tableName = Objects.requireNonNull(resolvedName,
                "table name resolved to null despite the blank-or-null guard");

        // and if metaTable is still null try to get from metadata by name
        if (metaTable == null && metadata != null)
        {
            metaTable = metadata.getDataTable(tableName).orElse(null);
        }

        tableMetaBuilder = DataTableMeta.builder()//
                .name(tableName)//
                .tableURI(aUri)//
                .columnNameCaseSensitive(caseSensitive)//
                .label(metaTable != null ? metaTable.getLabel() : null)//
        ;
        if (metaTable != null)
        {
            copyMetaValue(metaTable, META_KEY_COMMENT);
            copyMetaValue(metaTable, META_KEY_STRUCTURE);
            copyMetaValue(metaTable, META_KEY_REPEATING);
            copyMetaValue(metaTable, META_KEY_PURPOSE);
            copyMetaValue(metaTable, META_KEY_SAS_DATASET_NAME);
            copyMetaValue(metaTable, META_KEY_STANDARD);
        }
    }


    /**
     * Declares the physical source format the provider parsed this table from, as the
     * {@value #META_KEY_FILE_FORMAT} (and optionally {@value #META_KEY_TRANSPORT_VERSION})
     * table-metadata keys. Unlike a filename-extension heuristic this is authoritative — the
     * provider that actually parsed the bytes states its format — and backs the rule-engine
     * {@code extract_metadata("file_format")} accessor (CX-2,
     * {@code plans/PLAN-engine-enhancements-CX.md}). Call after {@link #setTable(URI, String)}.
     *
     * @param aFormat
     *            the format token, e.g. {@code "XPORT"}, {@code "SAS7BDAT"},
     *            {@code "DATASET-JSON"}, {@code "PARQUET"}, {@code "XLSX"}, {@code "CSV"}
     * @param aTransportVersion
     *            the transport/container version when the format defines one (XPORT ⇒ {@code "5"}),
     *            else {@code null}
     */
    public void setFileFormat(@NonNull String aFormat, @Nullable String aTransportVersion)
    {
        if (tableMetaBuilder == null)
        {
            throw new IllegalStateException("setTable() must be called before setFileFormat()");
        }
        tableMetaBuilder.addMetaData(META_KEY_FILE_FORMAT, aFormat);
        if (aTransportVersion != null)
        {
            tableMetaBuilder.addMetaData(META_KEY_TRANSPORT_VERSION, aTransportVersion);
        }
    }


    public DataTableColumnMetaBuilder addColumn(@NonNull String aColumnName, DataValueType aType)
    {
        return addColumn(aColumnName, aType, null);
    }


    /**
     * Add a new column to the table metadata.
     *
     * @param aColumnName
     *            the name of the column to be added.
     * @param aType
     *            the column type.
     * @param aColumnMeta
     *            an optional {@link IColumnMetadata} that describes the column. This can be given
     *            if the data source defines this already. In an optimal world, this would not be
     *            needed, but for now we prefer what is in the data table in case both deviate.
     * @return the builder that can be used to further configure the column. If a
     *         {@link IMetadataLibrary} is available, this builder is pre-initialized with values
     *         from the metadata.
     */
    public DataTableColumnMetaBuilder addColumn(@NonNull String aColumnName, DataValueType aType,
            @Nullable IColumnMetadata aColumnMeta)
    {

        String key = caseSensitive ? aColumnName : aColumnName.toLowerCase(Locale.ROOT);
        if (columnNames.contains(key))
        {
            throw new IllegalStateException(
                    "A column with the name %s is already defined!".formatted(aColumnName));
        }
        columnNames.add(key);

        IColumnMetadata colMeta;
        if (aColumnMeta != null)
        {
            colMeta = aColumnMeta;
        }
        else if (metaTable != null)
        {
            colMeta = metaTable.getColumn(aColumnName).orElse(null);
        }
        else
        {
            colMeta = null;
        }

        DataTableColumnMetaBuilder b = DataTableColumnMeta.builder()//
                .index(columns.size())//
                .name(aColumnName)//
                .type(aType)//
                .label(colMeta != null ? colMeta.getLabel() : null)//
                .displayFormat(colMeta != null ? colMeta.getDisplayFormat() : null)//
        ;
        if (colMeta != null)
        {
            if (colMeta.getLength() > 0)
            {
                b.length(colMeta.getLength());
            }
            // Copy all metadata attributes from the column metadata
            for (String metaKey : colMeta.getMetaKeys())
            {
                colMeta.getMetaValue(metaKey).ifPresent(v -> b.addMetaData(metaKey, v));
            }
        }
        columns.add(b);
        return b;
    }


    /**
     * Apply the columns as they are currently configured in the corresponding builders to the table
     * metadata. This can be triggered externally to ensure the columns in the table metadata
     * builder are up to date with the current builders. Additionally this is called on
     * {@link #getTableMeta()} automatically, so it does not need to be called manually at all.
     *
     * @return the columns applied to the builder.
     */
    public DataTableColumnMeta[] applyColumns()
    {
        if (tableMetaBuilder == null)
        {
            throw new IllegalStateException("No table set!");
        }

        DataTableColumnMeta[] metaCols = columns.stream().map(b -> b.build())
                .toArray(DataTableColumnMeta[]::new);
        tableMetaBuilder.columns(metaCols);
        return metaCols;
    }


    public int getTableColumnCount()
    {
        return columns.size();
    }


    public DataTableColumnMeta[] getTableColumns()
    {
        // we generate the metaCols fresh from the columns currently in the builder
        return applyColumns();
    }


    /**
     * Retrieve the builder for table metadata as it is currently configured.
     *
     * @return the builder for table metadata as it is currently configured.
     */
    public DataTableMetaBuilder getTableMeta()
    {
        // applyColumns() throws IllegalStateException when tableMetaBuilder is null, so on return
        // it
        // is guaranteed non-null.
        applyColumns();
        return Objects.requireNonNull(tableMetaBuilder, "tableMetaBuilder must be set");
    }


    public @Nullable IMetadataLibrary getMetadata()
    {
        return metadata;
    }


    protected String getTableNameFor(@Nullable URI aUri)
    {
        if (aUri != null)
        {
            String fragment = aUri.getFragment();
            if (!CDT.isBlankOrNull(fragment))
            {
                return fragment;
            }
        }

        if (metaTable != null)
        {
            String name = metaTable.getName();
            if (!CDT.isBlankOrNull(name))
            {
                return name;
            }
        }

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


    /**
     * Find a data table metadata entry by matching the table URI.
     * <p>
     * URIs are canonicalised before comparison so trivially-equivalent forms still match —
     * {@code file:} URIs are normalised through {@link Path#toUri()} (handles trailing slashes,
     * {@code %20} vs literal space, relative path segments), other schemes via
     * {@link URI#normalize()}.
     */
    private @Nullable IDataTableMetadata findTableByUri(URI aUri)
    {
        if (metadata == null || aUri == null)
        {
            return null;
        }
        URI needle = canonicalize(aUri);
        for (IDataTableMetadata dt : metadata.getDataTables())
        {
            URI candidate = canonicalize(dt.getTableURI());
            if (Objects.equals(needle, candidate))
            {
                return dt;
            }
        }
        return null;
    }


    /**
     * Best-effort URI canonicalisation for comparing locator-equivalent URIs. {@code file:} URIs
     * are routed through {@link Path} to resolve relative segments and normalise the path syntax;
     * other schemes use {@link URI#normalize()}. Returns {@code null} for {@code null} input and
     * the original URI when canonicalisation throws.
     * <p>
     * Note: {@code Path.toAbsolutePath()} resolves relative file URIs against the current JVM
     * working directory. If a metadata library is built with a relative {@code file:} URI in one
     * cwd and consumed in another (rare — most providers normalise to absolute URIs at ingest),
     * lookups can silently miss.
     */
    @SuppressWarnings("PMD.EmptyCatchBlock")
    private static @Nullable URI canonicalize(@Nullable URI aUri)
    {
        if (aUri == null)
        {
            return null;
        }
        if ("file".equalsIgnoreCase(aUri.getScheme()))
        {
            try
            {
                return Path.of(aUri).toAbsolutePath().normalize().toUri();
            }
            catch (IllegalArgumentException | FileSystemNotFoundException ex)
            {
                // Fall through to plain URI normalisation
            }
        }
        return aUri.normalize();
    }


    /**
     * Copy a metadata value from the given element to the table meta builder if present.
     */
    private void copyMetaValue(IDataTableMetadata aMeta, String aKey)
    {
        // copyMetaValue is only invoked from setTable() after tableMetaBuilder has been assigned;
        // capture it as a non-null local so NullAway can verify the lambda capture.
        DataTableMetaBuilder builder = Objects.requireNonNull(tableMetaBuilder,
                "tableMetaBuilder must be set before copyMetaValue");
        aMeta.getMetaValue(aKey).ifPresent(v -> builder.addMetaData(aKey, v));
    }
}
