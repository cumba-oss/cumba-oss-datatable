package net.cumba.datatable.impl.metadata;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.help.CDT;
import net.cumba.datatable.impl.library.LibraryProviderFactory;
import net.cumba.datatable.library.IDataTableLibrary;
import net.cumba.datatable.library.ILibraryMember;
import net.cumba.datatable.library.ILibraryProvider;
import net.cumba.datatable.metadata.ICodeList;
import net.cumba.datatable.metadata.IColumnMetadata;
import net.cumba.datatable.metadata.IDataTableMetadata;
import net.cumba.datatable.metadata.IMetadataLibrary;
import net.cumba.datatable.values.DataValueType;
import org.jspecify.annotations.Nullable;

/**
 * Fallback {@link IMetadataLibrary} derived directly from an {@link IDataTableLibrary}. Exposes one
 * {@link IDataTableMetadata} per library member and one {@link IColumnMetadata} per column, pulling
 * name / label / type / length / native type / display format / codelist reference from the
 * member's {@link DataTableColumnMeta} entries.
 *
 * <p>
 * Used when no Define-XML or other external metadata is attached to the library (e.g. a
 * {@code .cdt} file opened as a library): this makes simple column-presence and datatype rules
 * executable without forcing the user to attach a Define. The adapter knows nothing about codelists
 * (no entries), controlled terminology, standard name or standard version — these are only
 * available from proper metadata sources.
 * </p>
 */
public final class DataTableLibraryMetadataAdapter implements IMetadataLibrary
{

    // Matches the key used by providers (e.g. CDT) to record the codelist reference on a
    // DataTableColumnMeta via DataTableColumnMeta#addMetaData.
    private static final String COLUMN_META_CODELIST = "codelist";

    /**
     * An adapter instance exposing no data tables, no codelists and no meta keys. Useful as a
     * last-resort non-null placeholder when a caller cannot tolerate a {@code null}
     * {@link IMetadataLibrary} and no library is available to derive one from.
     */
    public static DataTableLibraryMetadataAdapter empty()
    {
        return new DataTableLibraryMetadataAdapter(null, List.of());
    }


    /**
     * Build an adapter by enumerating the library's members via the registered
     * {@link ILibraryProvider}s and collecting each member's column metadata.
     */
    public static DataTableLibraryMetadataAdapter of(IDataTableLibrary aLibrary) throws IOException
    {
        List<AdapterTable> tables = new ArrayList<>();
        LibraryProviderFactory lpf = LibraryProviderFactory.getInstance();
        List<ILibraryProvider> providers = lpf.getAllProvidersFor(aLibrary.getUri(),
                aLibrary.getFileInfo());
        for (ILibraryProvider p : providers)
        {
            try (Stream<? extends ILibraryMember> s = p.provideLibraryMembers(aLibrary))
            {
                List<? extends ILibraryMember> members = s.toList();
                for (ILibraryMember m : members)
                {
                    try (Stream<? extends DataTableColumnMeta> cs = p
                            .provideLibraryMemberColumns(m))
                    {
                        List<DataTableColumnMeta> cols = new ArrayList<>(cs.toList());
                        tables.add(new AdapterTable(m.getName(), m.getLabel(), m.getUri(), cols));
                    }
                }
            }
        }
        return new DataTableLibraryMetadataAdapter(aLibrary.getName(), tables);
    }

    // The empty() factory builds a placeholder with no derivable library name (passes null), so the
    // name is genuinely nullable here, matching the @Nullable IMetadataLibrary.getName() contract.
    // Behaviour is asserted by DataTableLibraryMetadataAdapterTest.
    private final @Nullable String name;

    private final List<IDataTableMetadata> dataTables;

    private DataTableLibraryMetadataAdapter(@Nullable String aName, List<AdapterTable> aTables)
    {
        name = aName;
        List<IDataTableMetadata> list = new ArrayList<>(aTables.size());
        for (AdapterTable t : aTables)
        {
            list.add(new AdapterDataTable(t));
        }
        dataTables = Collections.unmodifiableList(list);
    }


    // Returns the nullable placeholder name; see the field comment above. empty() legitimately has
    // none, matching the @Nullable IMetadataLibrary.getName() contract.
    @Override
    public @Nullable String getName()
    {
        return name;
    }


    @Override
    public @Nullable String getVersion()
    {
        return null;
    }


    @Override
    public boolean isColumnNameCaseSensitive()
    {
        return false;
    }


    @Override
    public List<IDataTableMetadata> getDataTables()
    {
        return dataTables;
    }


    @Override
    public Optional<IDataTableMetadata> getDataTable(String aDomainName)
    {
        if (aDomainName == null)
        {
            return Optional.empty();
        }
        for (IDataTableMetadata dt : dataTables)
        {
            if (CDT.equalsIgnoreCase(dt.getName(), aDomainName))
            {
                return Optional.of(dt);
            }
        }
        return Optional.empty();
    }


    @Override
    public List<ICodeList> getCodelists()
    {
        return List.of();
    }


    @Override
    public Optional<ICodeList> getCodelist(String aName)
    {
        return Optional.empty();
    }


    @Override
    public Set<String> getMetaKeys()
    {
        return Set.of();
    }


    @Override
    public Optional<Object> getMetaValue(String aKey)
    {
        return Optional.empty();
    }

    // ------------------------------------------------------------------
    // Internal types
    // ------------------------------------------------------------------

    private record AdapterTable(String name, @Nullable String label, URI uri,
            List<DataTableColumnMeta> columns)
    {
    }


    private static final class AdapterDataTable implements IDataTableMetadata
    {

        private final AdapterTable src;

        private final List<IColumnMetadata> columns;

        private final Map<String, IColumnMetadata> byLowerName;

        AdapterDataTable(AdapterTable aSrc)
        {
            src = aSrc;
            List<IColumnMetadata> list = new ArrayList<>(aSrc.columns().size());
            Map<String, IColumnMetadata> lookup = new LinkedHashMap<>();
            for (DataTableColumnMeta dcm : aSrc.columns())
            {
                AdapterColumn col = new AdapterColumn(dcm);
                list.add(col);
                lookup.putIfAbsent(dcm.getName().toLowerCase(Locale.ROOT), col);
            }
            columns = Collections.unmodifiableList(list);
            byLowerName = Collections.unmodifiableMap(lookup);
        }


        @Override
        public String getName()
        {
            return src.name();
        }


        @Override
        public @Nullable String getLabel()
        {
            return src.label();
        }


        @Override
        public URI getTableURI()
        {
            return src.uri();
        }


        @Override
        public List<IColumnMetadata> getColumns()
        {
            return columns;
        }


        @Override
        public Optional<IColumnMetadata> getColumn(String aColumnName)
        {
            if (aColumnName == null)
            {
                return Optional.empty();
            }
            return Optional.ofNullable(byLowerName.get(aColumnName.toLowerCase(Locale.ROOT)));
        }


        @Override
        public Set<String> getMetaKeys()
        {
            return Set.of();
        }


        @Override
        public Optional<Object> getMetaValue(String aKey)
        {
            return Optional.empty();
        }
    }


    private static final class AdapterColumn implements IColumnMetadata
    {

        private final DataTableColumnMeta src;

        AdapterColumn(DataTableColumnMeta aSrc)
        {
            src = aSrc;
        }


        @Override
        public String getName()
        {
            return src.getName();
        }


        @Override
        public @Nullable String getLabel()
        {
            return src.getLabel();
        }


        @Override
        public @Nullable String getDisplayFormat()
        {
            return src.getDisplayFormat();
        }


        @Override
        public int getIndex()
        {
            return src.getIndex();
        }


        @Override
        public DataValueType getType()
        {
            DataValueType t = src.getType();
            return t != null ? t : DataValueType.STRING;
        }


        @Override
        public int getLength()
        {
            return src.getLength();
        }


        @Override
        public String getNativeType()
        {
            return src.getNativeType();
        }


        @Override
        public int getKeySequence()
        {
            return 0;
        }


        @Override
        public boolean isByGroup()
        {
            return false;
        }


        @Override
        public @Nullable String getCodelist()
        {
            Object v = src.getMetaData(COLUMN_META_CODELIST);
            return v != null ? v.toString() : null;
        }


        @Override
        public Set<String> getMetaKeys()
        {
            return Set.copyOf(src.getMetaDataKeys());
        }


        @Override
        public Optional<Object> getMetaValue(String aKey)
        {
            return Optional.ofNullable(src.getMetaData(aKey));
        }
    }
}
