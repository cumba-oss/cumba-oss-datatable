package net.cumba.datatable.impl.provider;

import java.io.File;
import java.net.URI;
import java.nio.charset.Charset;
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
import net.cumba.datatable.help.URIHelper;
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

    /**
     * The dataset's observation class (e.g. {@code EVENTS}, {@code BASIC DATA STRUCTURE}).
     *
     * <p>
     * Unlike its neighbours this is <b>not</b> a free-form metadata key on the source — it is a
     * first-class accessor, {@link IDataTableMetadata#getClassName()} (from Define-XML's
     * {@code def:Class}, in either the 2.0 attribute or the 2.1 element form). It is carried here
     * so a consumer holding only a {@link DataTableMeta} can still reach it — notably a define.xml
     * generator, which must emit the class to satisfy the conformance rules that require one.
     * </p>
     */
    public static final String META_KEY_CLASS = "Class";

    public static final String META_KEY_REPEATING = "Repeating";

    public static final String META_KEY_CREATED = "Created";

    public static final String META_KEY_MODIFIED = "Modified";

    /**
     * The charset the provider decoded the table's text with: its canonical name
     * ({@code Charset.name()}), or {@value #ENCODING_MIXED} when the strings of one table decoded
     * with more than one (an RDS file whose R encoding marks differ). It describes the decode,
     * never the file's claim: a declared charset the reader could not use is reported as the
     * fallback it decoded with. Always published together with {@value #META_KEY_ENCODING_SOURCE}
     * ({@link #setEncoding(String, String)}); the rule engine reads it as
     * {@code extract_metadata("encoding")}.
     */
    public static final String META_KEY_ENCODING = "Encoding";

    /**
     * Where {@value #META_KEY_ENCODING} came from: {@value #ENCODING_SOURCE_DECLARED},
     * {@value #ENCODING_SOURCE_FORMAT}, {@value #ENCODING_SOURCE_CONFIGURED} or
     * {@value #ENCODING_SOURCE_DEFAULT}. Read by the rule engine as
     * {@code extract_metadata("encoding_source")}.
     */
    public static final String META_KEY_ENCODING_SOURCE = "EncodingSource";

    /** The {@value #META_KEY_ENCODING} of a table whose strings decoded with several charsets. */
    public static final String ENCODING_MIXED = "MIXED";

    /** The file declares the charset (a sas7bdat header byte, R's string marks or RDS header). */
    public static final String ENCODING_SOURCE_DECLARED = "DECLARED";

    /**
     * The format fixes the charset (CDT, Dataset-JSON, Parquet, XLSX, a JSON API, Delta).
     *
     * <p>
     * A Dataset-JSON table reports {@code UTF-8} / {@code FORMAT} even when the file it was read
     * from is physically UTF-16 or UTF-32 (a non-conforming file the parser still reads): for
     * Dataset-JSON the {@value #META_KEY_ENCODING} names the format's mandated interchange charset
     * (RFC 8259 §8.1), the one its byte counts are meant in &mdash; an exception to "describes the
     * decode" (owner ruling B2-Q3 ⓒ, 2026-10-16).
     * </p>
     */
    public static final String ENCODING_SOURCE_FORMAT = "FORMAT";

    /** A charset other than the default was requested through the provider's charset property. */
    public static final String ENCODING_SOURCE_CONFIGURED = "CONFIGURED";

    /**
     * The provider's default charset: nothing declared or requested one, or what was declared or
     * requested could not be used and the reader fell back.
     */
    public static final String ENCODING_SOURCE_DEFAULT = "DEFAULT";

    /**
     * The physical source format the provider parsed the table from (CX-2). Lower-case key: it is
     * read by the rule engine's {@code extract_metadata("file_format")} accessor, matching the
     * {@code dataset_size} / {@code dataset_location} accessor-key convention.
     */
    public static final String META_KEY_FILE_FORMAT = "file_format";

    /** The transport/container version for formats that define one (XPORT ⇒ {@code "5"}). */
    public static final String META_KEY_TRANSPORT_VERSION = "transport_version";

    /**
     * Size in bytes of the file this table was parsed from, as a {@link Long}. Lower-case key: it
     * backs the rule-engine {@code extract_metadata("dataset_size")} accessor
     * ({@code OperationExecutor.evalExtractMetadata}), matching the {@code file_format} /
     * {@code dataset_location} accessor-key convention.
     *
     * <p>
     * ⚠ This key is <b>not</b> decoration: {@code PMDA-SD1142} ("the submitted dataset is larger
     * than 5 GB") is a Published rule in the shipping corpus whose whole check is
     * {@code $dataset_size > 5368709120}. Until a provider populates the key the rule reads
     * {@code null} and can never fire, on a dataset of any size. Each provider therefore states the
     * size of the bytes <b>it actually parsed</b> — see {@link #setDatasetSize(java.io.File, URI)}.
     * </p>
     */
    public static final String META_KEY_DATASET_SIZE = "dataset_size";

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
    private final List<DataTableColumnMetaBuilder> columns = new ArrayList<>();

    /**
     * A set of column names of columns, already added. It is not allowed to have two columns with
     * the same name.
     */
    private final Set<String> columnNames = new HashSet<>();

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

            // The class comes from a first-class accessor rather than a metadata key, so it needs
            // its own line — copyMetaValue would look for a "Class" entry that no source sets.
            String className = metaTable.getClassName();
            if (className != null && !className.isBlank())
            {
                tableMetaBuilder.addMetaData(META_KEY_CLASS, className);
            }
        }
    }


    /**
     * Declares the physical source format the provider parsed this table from, as the
     * {@value #META_KEY_FILE_FORMAT} (and optionally {@value #META_KEY_TRANSPORT_VERSION})
     * table-metadata keys. Unlike a filename-extension heuristic this is authoritative — the
     * provider that actually parsed the bytes states its format — and backs the rule-engine
     * {@code extract_metadata("file_format")} accessor (CX-2,
     * {@code plans/done/PLAN-engine-enhancements-CX.md}). Call after
     * {@link #setTable(URI, String)}.
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


    /**
     * Records the charset the table's text was decoded with, as the {@value #META_KEY_ENCODING} and
     * {@value #META_KEY_ENCODING_SOURCE} table-metadata keys — always both. Call after
     * {@link #setTable(URI, String)}.
     *
     * @param aEncoding
     *            the canonical charset name ({@code Charset.name()}) or {@value #ENCODING_MIXED}.
     * @param aSource
     *            one of the {@code ENCODING_SOURCE_*} values.
     */
    public void setEncoding(String aEncoding, String aSource)
    {
        if (tableMetaBuilder == null)
        {
            throw new IllegalStateException("setTable() must be called before setEncoding()");
        }
        tableMetaBuilder.addMetaData(META_KEY_ENCODING, aEncoding);
        tableMetaBuilder.addMetaData(META_KEY_ENCODING_SOURCE, aSource);
    }


    /**
     * The {@value #META_KEY_ENCODING_SOURCE} of a charset taken from a provider's charset property:
     * {@value #ENCODING_SOURCE_CONFIGURED} when it differs from the property's default, else
     * {@value #ENCODING_SOURCE_DEFAULT}. Comparing with the default — not asking whether a value
     * was supplied — makes the answer a property of the request rather than of the caller (the data
     * browser fills every property with its default; the CLI supplies none); an absent, blank or
     * unusable request resolves to the default, so it reads {@value #ENCODING_SOURCE_DEFAULT}.
     *
     * @param aEffective
     *            the charset the provider decodes with.
     * @param aDefault
     *            the charset property's default.
     * @return the source constant.
     */
    public static String requestedCharsetSource(Charset aEffective, Charset aDefault)
    {
        return aEffective.equals(aDefault) ? ENCODING_SOURCE_DEFAULT : ENCODING_SOURCE_CONFIGURED;
    }


    /**
     * Adds to {@code aBuilder} the {@value #META_KEY_ENCODING} / {@value #META_KEY_ENCODING_SOURCE}
     * pair a view over several tables carries (owner ruling B2-Q4 ⓐ, 2026-10-16) — always both keys
     * or neither:
     * <ul>
     * <li>{@value #META_KEY_ENCODING}: the members' common value when every member reports the same
     * one, {@value #ENCODING_MIXED} when they differ; <b>none</b> when any member reports none (a
     * view whose rows came partly from text of unknown charset has no charset to claim).</li>
     * <li>{@value #META_KEY_ENCODING_SOURCE}: the members' common value when they agree, else the
     * least certain one present ({@value #ENCODING_SOURCE_DEFAULT} &lt;
     * {@value #ENCODING_SOURCE_CONFIGURED} &lt; {@value #ENCODING_SOURCE_FORMAT} &lt;
     * {@value #ENCODING_SOURCE_DECLARED}; a value outside that vocabulary counts as less certain
     * than all of them and is carried as it is).</li>
     * </ul>
     * A member counts as reporting an encoding only when it carries <b>both</b> keys as strings.
     * The rule engine measures byte lengths in the evaluated table's {@value #META_KEY_ENCODING}
     * ({@code extract_metadata("encoding")}), and a union is evaluated over (a split domain), so
     * the pair is what a byte count over the union reads.
     *
     * <p>
     * The builder must not already hold either key: {@code addMetaData} keeps the <b>first</b>
     * value of a duplicate key, so a copied member pair would shadow the agreed one — strip it with
     * {@link #withoutEncoding(Object[])} first.
     * </p>
     *
     * @param aBuilder
     *            the view's metadata builder.
     * @param aMembers
     *            the members' metadata, at least one.
     */
    public static void putAgreedEncoding(DataTableMetaBuilder aBuilder, DataTableMeta... aMembers)
    {
        String encoding = null;
        String source = null;
        for (DataTableMeta member : aMembers)
        {
            if (!(member.getMetaData(META_KEY_ENCODING) instanceof String memberEncoding)
                    || !(member
                            .getMetaData(META_KEY_ENCODING_SOURCE) instanceof String memberSource))
            {
                return;
            }
            if (encoding == null || source == null)
            {
                encoding = memberEncoding;
                source = memberSource;
                continue;
            }
            if (!encoding.equals(memberEncoding))
            {
                encoding = ENCODING_MIXED;
            }
            if (encodingSourceCertainty(memberSource) < encodingSourceCertainty(source))
            {
                source = memberSource;
            }
        }
        if (encoding != null && source != null)
        {
            aBuilder.addMetaData(META_KEY_ENCODING, encoding);
            aBuilder.addMetaData(META_KEY_ENCODING_SOURCE, source);
        }
    }


    /**
     * A copy of a {@code [key, value, …]} custom-metadata table without the
     * {@value #META_KEY_ENCODING} / {@value #META_KEY_ENCODING_SOURCE} entries — the input
     * {@link #putAgreedEncoding(DataTableMetaBuilder, DataTableMeta...)} needs when a view starts
     * from a member's metadata.
     *
     * @param aMetaTable
     *            the table, as {@link DataTableMeta#getMetaTable()} returns it; may be
     *            {@code null}.
     * @return the filtered copy, or {@code null} when nothing is left.
     */
    public static Object @Nullable [] withoutEncoding(Object @Nullable [] aMetaTable)
    {
        if (aMetaTable == null)
        {
            return null;
        }
        List<Object> kept = new ArrayList<>(aMetaTable.length);
        for (int i = 0; i + 1 < aMetaTable.length; i += 2)
        {
            Object key = aMetaTable[i];
            if (!META_KEY_ENCODING.equals(key) && !META_KEY_ENCODING_SOURCE.equals(key))
            {
                kept.add(key);
                kept.add(aMetaTable[i + 1]);
            }
        }
        return kept.isEmpty() ? null : kept.toArray();
    }


    /**
     * The certainty rank of an {@value #META_KEY_ENCODING_SOURCE} value: {@code DEFAULT} 0,
     * {@code CONFIGURED} 1, {@code FORMAT} 2, {@code DECLARED} 3, anything else -1.
     *
     * @param aSource
     *            the source value.
     * @return its rank; lower is less certain.
     */
    private static int encodingSourceCertainty(String aSource)
    {
        return switch (aSource)
        {
        case ENCODING_SOURCE_DEFAULT -> 0;
        case ENCODING_SOURCE_CONFIGURED -> 1;
        case ENCODING_SOURCE_FORMAT -> 2;
        case ENCODING_SOURCE_DECLARED -> 3;
        default -> -1;
        };
    }


    /**
     * Records the size of the file a {@code file:} URI points at — the stream-based providers' form
     * of {@link #setDatasetSize(File, URI)}, which hold no file of their own. A remote source
     * yields no size rather than a guessed one.
     *
     * @param aUri
     *            the table's source URI.
     */
    public void setDatasetSize(@Nullable URI aUri)
    {
        setDatasetSize(null, aUri);
    }


    /**
     * Records the size in bytes of the file this table was parsed from, as the
     * {@value #META_KEY_DATASET_SIZE} table-metadata key. Call after
     * {@link #setTable(URI, String)}.
     *
     * <p>
     * {@code aParsedFile} is preferred over {@code aUri} and is what makes the value authoritative
     * rather than a guess: a provider that materialised its input locally —
     * {@code XptTableProvider} downloads a non-{@code file} URI to a temp file before parsing,
     * {@code ParquetTableProvider} needs random access — holds the very bytes it read, and so
     * reports a real size even for an {@code http:} source, where stat'ing the URI can report
     * nothing. The URI is the fallback for the stream-based providers, which never hold a file.
     * </p>
     *
     * @param aParsedFile
     *            the local file whose bytes this table was actually parsed from, or {@code null}
     *            when the provider streamed its input.
     * @param aUri
     *            the table's source URI, used when {@code aParsedFile} is {@code null} and the URI
     *            has the {@code file} scheme; any other scheme yields no size.
     */
    public void setDatasetSize(@Nullable File aParsedFile, @Nullable URI aUri)
    {
        long size = sizeOf(aParsedFile);
        if (size < 0)
        {
            size = sizeOfUri(aUri);
        }
        setDatasetSize(size);
    }


    /**
     * Records an already-known dataset size as the {@value #META_KEY_DATASET_SIZE} table-metadata
     * key. A negative size means "unknown" and is not recorded at all — consumers distinguish an
     * absent key from a real size, and a sentinel written into the metadata would read as a real
     * one. Call after {@link #setTable(URI, String)}.
     *
     * @param aSizeInBytes
     *            the size in bytes, or a negative value when the size is not known.
     */
    public void setDatasetSize(long aSizeInBytes)
    {
        if (tableMetaBuilder == null)
        {
            throw new IllegalStateException("setTable() must be called before setDatasetSize()");
        }
        if (aSizeInBytes >= 0)
        {
            tableMetaBuilder.addMetaData(META_KEY_DATASET_SIZE, aSizeInBytes);
        }
    }


    /**
     * Size of a local file, or {@code -1} when it is null, absent or not a regular file.
     */
    private static long sizeOf(@Nullable File aFile)
    {
        if (aFile == null || !aFile.isFile())
        {
            return -1;
        }
        long len = aFile.length();
        // File.length() answers 0 both for an empty file and for one it could not stat; an empty
        // file is a legitimate 0, so only a negative result is treated as unknown.
        return len >= 0 ? len : -1;
    }


    /**
     * Size of the file a {@code file:} URI points at, or {@code -1} for any other scheme or when it
     * cannot be stat'ed. The fragment is stripped first: a member of a multi-dataset container is
     * addressed as {@code file:/x/lb.xpt#DM}, and {@code Path.of} rejects that with "URI has a
     * fragment component" — the size then reported is the container's, which is what the
     * {@code dataset_size} rules mean by the submitted dataset.
     */
    private static long sizeOfUri(@Nullable URI aUri)
    {
        if (aUri == null || !"file".equalsIgnoreCase(aUri.getScheme()))
        {
            return -1;
        }
        try
        {
            URI plain = aUri.getFragment() == null ? aUri : URIHelper.replaceFragment(aUri, null);
            return sizeOf(new File(plain));
        }
        catch (IllegalArgumentException | FileSystemNotFoundException _)
        {
            return -1;
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

        DataTableColumnMeta[] metaCols = columns.stream().map(DataTableColumnMetaBuilder::build)
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
