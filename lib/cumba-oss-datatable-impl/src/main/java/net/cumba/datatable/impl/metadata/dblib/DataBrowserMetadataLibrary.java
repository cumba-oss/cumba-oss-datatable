package net.cumba.datatable.impl.metadata.dblib;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.NonNull;
import net.cumba.datatable.help.CDT;
import net.cumba.datatable.impl.library.dblib.beans.DataBrowserColumnMetaBean;
import net.cumba.datatable.impl.library.dblib.beans.DataBrowserLibraryBean;
import net.cumba.datatable.impl.library.dblib.beans.DataBrowserMemberMetaBean;
import net.cumba.datatable.impl.library.dblib.beans.DataBrowserValueFormatBean;
import net.cumba.datatable.metadata.ICodeList;
import net.cumba.datatable.metadata.ICodelistEntry;
import net.cumba.datatable.metadata.IColumnMetadata;
import net.cumba.datatable.metadata.IDataTableMetadata;
import net.cumba.datatable.metadata.IMetadataLibrary;
import net.cumba.datatable.values.DataValueType;
import org.jspecify.annotations.Nullable;

/**
 * An {@link IMetadataLibrary} implementation that wraps a {@link DataBrowserLibraryBean}. This
 * adapter exposes the metadata defined in a {@code .dblib} JSON file through the generic metadata
 * API.
 */
public class DataBrowserMetadataLibrary implements IMetadataLibrary
{

    private final DataBrowserLibraryBean bean;

    private final URI libraryUri;

    private @Nullable List<IDataTableMetadata> dataTables;

    private @Nullable List<ICodeList> codelists;

    private DataBrowserMetadataLibrary(@NonNull DataBrowserLibraryBean aBean, @NonNull URI aUri)
    {
        bean = aBean;
        libraryUri = aUri;
    }


    /**
     * Create an {@link IMetadataLibrary} from the given {@link DataBrowserLibraryBean} and library
     * URI.
     *
     * @param aBean
     *            the library bean to wrap.
     * @param aLibraryUri
     *            the URI of the library file, used to resolve relative URIs.
     * @return the metadata library.
     */
    public static IMetadataLibrary from(@NonNull DataBrowserLibraryBean aBean,
            @NonNull URI aLibraryUri)
    {
        return new DataBrowserMetadataLibrary(aBean, aLibraryUri);
    }


    @Override
    public String getName()
    {
        return bean.getName();
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
        if (dataTables == null)
        {
            dataTables = buildDataTables();
        }
        return dataTables;
    }


    @Override
    public Optional<IDataTableMetadata> getDataTable(String aDomainName)
    {
        return getDataTables().stream()//
                .filter(dt -> CDT.equalsIgnoreCase(dt.getName(), aDomainName))//
                .findFirst();
    }


    @Override
    public List<ICodeList> getCodelists()
    {
        if (codelists == null)
        {
            codelists = buildCodelists();
        }
        return codelists;
    }


    @Override
    public Optional<ICodeList> getCodelist(String aName)
    {
        return getCodelists().stream()//
                .filter(cl -> Objects.equals(cl.getName(), aName))//
                .findFirst();
    }


    @Override
    public Set<String> getMetaKeys()
    {
        Map<String, String> attrs = bean.getAttributes();
        if (attrs != null)
        {
            return attrs.keySet();
        }
        return Collections.emptySet();
    }


    @Override
    public Optional<Object> getMetaValue(String aKey)
    {
        Map<String, String> attrs = bean.getAttributes();
        if (attrs != null)
        {
            return Optional.ofNullable(attrs.get(aKey));
        }
        return Optional.empty();
    }

    // ==================== Build data tables from member/column meta ====================


    private List<IDataTableMetadata> buildDataTables()
    {
        // Group column metadata by member URI
        Map<String, List<DataBrowserColumnMetaBean>> columnsByUri = new LinkedHashMap<>();

        DataBrowserColumnMetaBean[] columnMeta = bean.getColumnMeta();
        if (!CDT.isEmptyOrNull(columnMeta))
        {
            for (DataBrowserColumnMetaBean cm : columnMeta)
            {
                if (cm == null)
                {
                    continue;
                }
                String uriKey = resolveUri(cm.getUri());
                columnsByUri.computeIfAbsent(uriKey, _ -> new ArrayList<>()).add(cm);
            }
        }

        // Group member metadata by URI
        Map<String, DataBrowserMemberMetaBean> memberByUri = new LinkedHashMap<>();
        DataBrowserMemberMetaBean[] memberMeta = bean.getMemberMeta();
        if (!CDT.isEmptyOrNull(memberMeta))
        {
            for (DataBrowserMemberMetaBean mm : memberMeta)
            {
                if (mm == null)
                {
                    continue;
                }
                memberByUri.put(resolveUri(mm.getUri()), mm);
            }
        }

        // Build a data table for each URI that has either member or column metadata
        Set<String> allUris = new LinkedHashSet<>();
        allUris.addAll(memberByUri.keySet());
        allUris.addAll(columnsByUri.keySet());

        List<IDataTableMetadata> result = new ArrayList<>(allUris.size());
        for (String uri : allUris)
        {
            DataBrowserMemberMetaBean mm = memberByUri.get(uri);
            List<DataBrowserColumnMetaBean> cols = columnsByUri.getOrDefault(uri,
                    Collections.emptyList());
            result.add(new DbLibDataTableMetadata(this, uri, mm, cols));
        }
        return Collections.unmodifiableList(result);
    }


    private List<ICodeList> buildCodelists()
    {
        DataBrowserValueFormatBean[] formats = bean.getFormats();
        if (CDT.isEmptyOrNull(formats))
        {
            return Collections.emptyList();
        }

        List<ICodeList> result = new ArrayList<>(formats.length);
        for (DataBrowserValueFormatBean fmt : formats)
        {
            if (fmt == null)
            {
                continue;
            }
            result.add(new DbLibCodeList(fmt));
        }
        return Collections.unmodifiableList(result);
    }


    private @Nullable String resolveUri(@Nullable String aUriStr)
    {
        if (aUriStr == null)
        {
            return null;
        }
        try
        {
            URI uri = new URI(aUriStr);
            if (uri.isAbsolute())
            {
                return uri.normalize().toString();
            }
            return libraryUri.resolve(uri).normalize().toString();
        }
        catch (URISyntaxException _)
        {
            return aUriStr;
        }
    }

    // ==================== Inner class: DbLibDataTableMetadata ====================

    // static, like its sibling nested classes, with the owning library passed EXPLICITLY.
    //
    // As an inner class it carried a synthetic reference to the enclosing library. SpotBugs
    // 4.10.4.0 reports NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE against that
    // synthetic constructor parameter, which no annotation in this file can address because
    // the parameter does not appear in the source. Passing the owner as a declared, non-null
    // field says the same thing explicitly. It still needs the owner - getColumn() consults
    // isColumnNameCaseSensitive() - so the reference is kept rather than the value copied,
    // preserving late binding for any subclass that overrides it.
    private static class DbLibDataTableMetadata implements IDataTableMetadata
    {

        private final @Nullable String uriKey;

        private final @Nullable DataBrowserMemberMetaBean memberMeta;

        private final List<IColumnMetadata> columns;

        private final DataBrowserMetadataLibrary owner;

        DbLibDataTableMetadata(DataBrowserMetadataLibrary aOwner, @Nullable String aUriKey,
                @Nullable DataBrowserMemberMetaBean aMemberMeta,
                List<DataBrowserColumnMetaBean> aColumnBeans)
        {
            owner = aOwner;
            uriKey = aUriKey;
            memberMeta = aMemberMeta;

            List<IColumnMetadata> cols = new ArrayList<>(aColumnBeans.size());
            for (int i = 0; i < aColumnBeans.size(); i++)
            {
                cols.add(new DbLibColumnMetadata(aColumnBeans.get(i), i));
            }
            columns = Collections.unmodifiableList(cols);
        }


        @SuppressWarnings("PMD.EmptyCatchBlock")
        @Override
        public String getName()
        {
            // Derive name from URI: use fragment or filename without extension
            // Read once into a local so the non-nullness established by this guard survives
            // the try/catch below; the trailing `return key` is otherwise seen as returning a
            // @Nullable field from a non-null method.
            String key = uriKey;
            if (key == null)
            {
                return "";
            }
            try
            {
                URI uri = new URI(key);
                String fragment = uri.getFragment();
                if (!CDT.isBlankOrNull(fragment))
                {
                    return fragment;
                }
                String path = uri.getPath();
                if (path != null)
                {
                    String name = CDT.getAfterLast(path, '/');
                    return CDT.getBeforeLast(name, '.').toUpperCase(Locale.ROOT);
                }
            }
            catch (URISyntaxException _)
            {
                // fall through
            }
            return key;
        }


        @Override
        public @Nullable String getLabel()
        {
            return memberMeta != null ? memberMeta.getLabel() : null;
        }


        @Override
        public @Nullable URI getTableURI()
        {
            if (uriKey == null)
            {
                return null;
            }
            try
            {
                return new URI(uriKey);
            }
            catch (URISyntaxException _)
            {
                return null;
            }
        }


        @Override
        public List<IColumnMetadata> getColumns()
        {
            return columns;
        }


        @Override
        public Optional<IColumnMetadata> getColumn(String aColumnName)
        {
            return columns.stream()//
                    .filter(c -> owner.isColumnNameCaseSensitive()
                            ? Objects.equals(c.getName(), aColumnName)
                            : CDT.equalsIgnoreCase(c.getName(), aColumnName))//
                    .findFirst();
        }


        @Override
        public Set<String> getMetaKeys()
        {
            if (memberMeta != null && memberMeta.getAttributes() != null)
            {
                return memberMeta.getAttributes().keySet();
            }
            return Collections.emptySet();
        }


        @Override
        public Optional<Object> getMetaValue(String aKey)
        {
            if (memberMeta != null && memberMeta.getAttributes() != null)
            {
                return Optional.ofNullable(memberMeta.getAttributes().get(aKey));
            }
            return Optional.empty();
        }
    }

    // ==================== Inner class: DbLibColumnMetadata ====================


    private static class DbLibColumnMetadata implements IColumnMetadata
    {

        private final DataBrowserColumnMetaBean bean;

        private final int index;

        DbLibColumnMetadata(DataBrowserColumnMetaBean aBean, int aIndex)
        {
            bean = aBean;
            index = aIndex;
        }


        @Override
        public String getName()
        {
            return bean.getName();
        }


        @Override
        public @Nullable String getLabel()
        {
            return bean.getLabel();
        }


        @Override
        public @Nullable String getDisplayFormat()
        {
            return bean.getFormat();
        }


        @Override
        public int getIndex()
        {
            return index;
        }


        @SuppressWarnings("PMD.EmptyCatchBlock")
        @Override
        public DataValueType getType()
        {
            if (!CDT.isBlankOrNull(bean.getType()))
            {
                try
                {
                    return DataValueType.valueOf(bean.getType());
                }
                catch (IllegalArgumentException _)
                {
                    // fall through
                }
            }
            return DataValueType.STRING;
        }


        @Override
        public int getLength()
        {
            return 0;
        }


        // The .dblib format carries no native type, so this genuinely returns null. Behaviour is
        // asserted by DataBrowserMetadataLibraryTest.
        @Override
        public @Nullable String getNativeType()
        {
            return null;
        }


        @Override
        public int getKeySequence()
        {
            return bean.getKey();
        }


        @Override
        public boolean isByGroup()
        {
            return false;
        }


        @Override
        public Set<String> getMetaKeys()
        {
            Map<String, String> attrs = bean.getAttributes();
            if (attrs != null)
            {
                return attrs.keySet();
            }
            return Collections.emptySet();
        }


        @Override
        public Optional<Object> getMetaValue(String aKey)
        {
            Map<String, String> attrs = bean.getAttributes();
            if (attrs != null)
            {
                return Optional.ofNullable(attrs.get(aKey));
            }
            return Optional.empty();
        }
    }

    // ==================== Inner class: DbLibCodeList ====================


    private static class DbLibCodeList implements ICodeList
    {

        private final DataBrowserValueFormatBean formatBean;

        private @Nullable List<ICodelistEntry> entries;

        DbLibCodeList(DataBrowserValueFormatBean aFormatBean)
        {
            formatBean = aFormatBean;
        }


        @Override
        public String getName()
        {
            return formatBean.getName();
        }


        @Override
        public DataValueType getValueType()
        {
            return DataValueType.STRING;
        }


        @Override
        public List<ICodelistEntry> getEntries()
        {
            if (entries == null)
            {
                Map<String, String> valueMap = formatBean.getValueMap();
                if (valueMap.isEmpty())
                {
                    entries = Collections.emptyList();
                }
                else
                {
                    List<ICodelistEntry> result = new ArrayList<>(valueMap.size());
                    for (Map.Entry<String, String> e : valueMap.entrySet())
                    {
                        result.add(new DbLibCodelistEntry(e.getKey(), e.getValue()));
                    }
                    entries = Collections.unmodifiableList(result);
                }
            }
            return entries;
        }


        @Override
        public Set<String> getMetaKeys()
        {
            if (formatBean.getLabel() != null)
            {
                return Set.of("label");
            }
            return Collections.emptySet();
        }


        @Override
        public Optional<Object> getMetaValue(String aKey)
        {
            if ("label".equals(aKey))
            {
                return Optional.ofNullable(formatBean.getLabel());
            }
            return Optional.empty();
        }
    }

    // ==================== Inner class: DbLibCodelistEntry ====================


    private record DbLibCodelistEntry(String codeValue,
            String decodeValue) implements ICodelistEntry
    {

        @Override
        public String getCodeValue()
        {
            return codeValue;
        }


        @Override
        public String getDecodeValue()
        {
            return decodeValue;
        }


        @Override
        public Set<String> getMetaKeys()
        {
            return Collections.emptySet();
        }


        @Override
        public Optional<Object> getMetaValue(String aKey)
        {
            return Optional.empty();
        }
    }
}
