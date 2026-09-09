package net.cumba.datatable.provider.define.metadata;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.NonNull;
import net.cumba.cdisc.define.CodeList;
import net.cumba.cdisc.define.CodeListItem;
import net.cumba.cdisc.define.CodeListRef;
import net.cumba.cdisc.define.CommentDef;
import net.cumba.cdisc.define.Decode;
import net.cumba.cdisc.define.DefineSupport;
import net.cumba.cdisc.define.EnumeratedItem;
import net.cumba.cdisc.define.GlobalVariables;
import net.cumba.cdisc.define.ItemDef;
import net.cumba.cdisc.define.ItemGroupDef;
import net.cumba.cdisc.define.ItemRef;
import net.cumba.cdisc.define.Leaf;
import net.cumba.cdisc.define.MetaDataVersion;
import net.cumba.cdisc.define.MethodDef;
import net.cumba.cdisc.define.Standard;
import net.cumba.cdisc.define.Study;
import net.cumba.cdisc.define.TranslatedText;
import net.cumba.datatable.help.CDT;
import net.cumba.datatable.impl.provider.DataTableMetaSupport;
import net.cumba.datatable.metadata.ICodeList;
import net.cumba.datatable.metadata.ICodelistEntry;
import net.cumba.datatable.metadata.IColumnMetadata;
import net.cumba.datatable.metadata.IDataTableMetadata;
import net.cumba.datatable.metadata.IMetadataLibrary;
import net.cumba.datatable.values.DataValueType;
import org.jspecify.annotations.Nullable;

/**
 * An {@link IMetadataLibrary} implementation that wraps a {@link DefineSupport} instance. This
 * adapter translates Define-XML metadata (ODM model) into the generic metadata API.
 */
public class DefineMetadataLibrary implements IMetadataLibrary
{

    private final DefineSupport define;

    private final String name;

    private final @Nullable String version;

    private final @Nullable String standardName;

    private final @Nullable String standardVersion;

    private final @Nullable String defineVersion;

    private final @Nullable String ctPackages;

    private @Nullable List<IDataTableMetadata> dataTables;

    private @Nullable List<ICodeList> codelists;

    private DefineMetadataLibrary(@NonNull DefineSupport aDefine)
    {
        define = aDefine;

        // Build name from study/global variables
        String studyName = null;
        String mdvStdName = null;
        String mdvStdVersion = null;
        String mdvDefineVersion = null;
        List<Study> studies = aDefine.getOdm().getStudies();
        if (!CDT.isEmptyOrNull(studies))
        {
            Study s = studies.get(0);
            GlobalVariables gv = s.getGlobalVariables();
            if (gv != null)
            {
                studyName = gv.getStudyName();
            }
            List<MetaDataVersion> mdvs = s.getMetaDataVersions();
            if (!CDT.isEmptyOrNull(mdvs))
            {
                MetaDataVersion mdv = mdvs.get(0);
                mdvStdName = mdv.getStandardName();
                mdvStdVersion = mdv.getStandardVersion();
                mdvDefineVersion = mdv.getDefineVersion();
            }
        }
        name = studyName != null ? studyName : "<unknown>";
        version = mdvStdName;
        standardName = mdvStdName;
        standardVersion = mdvStdVersion;
        defineVersion = mdvDefineVersion;
        ctPackages = collectCtPackageIds(aDefine);
    }


    /**
     * Build a comma-separated list of CT-package ids from the Define-XML {@code <Standard>}
     * elements of type "CT". Package id format follows the CDISC Library convention
     * {@code <publishingSet>ct-<version>}, e.g. {@code sdtmct-2022-03-25}. Returns {@code null}
     * when no such Standards exist.
     */
    private static @Nullable String collectCtPackageIds(DefineSupport aDefine)
    {
        List<Standard> stds = aDefine.getStandards().toList();
        if (stds.isEmpty())
        {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Standard s : stds)
        {
            if (s == null || !"CT".equalsIgnoreCase(s.getType()))
            {
                continue;
            }
            String pubSet = s.getPublishingSet();
            String sVer = s.getVersion();
            if (CDT.isBlankOrNull(pubSet) || CDT.isBlankOrNull(sVer))
            {
                continue;
            }
            if (!sb.isEmpty())
            {
                sb.append(", ");
            }
            sb.append(pubSet.toLowerCase(Locale.ROOT)).append("ct-").append(sVer);
        }
        return sb.isEmpty() ? null : sb.toString();
    }


    /**
     * Create an {@link IMetadataLibrary} from the given {@link DefineSupport}.
     *
     * @param aDefine
     *            the define support to wrap.
     * @return the metadata library.
     */
    public static IMetadataLibrary from(@NonNull DefineSupport aDefine)
    {
        return new DefineMetadataLibrary(aDefine);
    }


    @Override
    public String getName()
    {
        return name;
    }


    @Override
    public @Nullable String getVersion()
    {
        return version;
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
            dataTables = define.getItemGroupDefs()//
                    .map(ig -> (IDataTableMetadata) new DefineDataTableMetadata(ig))//
                    .toList();
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
            codelists = define.getCodeLists()//
                    .map(cl -> (ICodeList) new DefineCodeList(cl))//
                    .toList();
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
        Set<String> keys = new java.util.HashSet<>();
        if (standardName != null)
        {
            keys.add(META_KEY_STANDARD_NAME);
        }
        if (standardVersion != null)
        {
            keys.add(META_KEY_STANDARD_VERSION);
        }
        if (defineVersion != null)
        {
            keys.add(META_KEY_DEFINE_VERSION);
        }
        if (ctPackages != null)
        {
            keys.add(META_KEY_CT_PACKAGES);
        }
        keys.add(META_KEY_SOURCE_URI);
        return keys;
    }


    @Override
    public Optional<Object> getMetaValue(String aKey)
    {
        return switch (aKey)
        {
        case META_KEY_STANDARD_NAME -> Optional.ofNullable(standardName);
        case META_KEY_STANDARD_VERSION -> Optional.ofNullable(standardVersion);
        case META_KEY_DEFINE_VERSION -> Optional.ofNullable(defineVersion);
        case META_KEY_CT_PACKAGES -> Optional.ofNullable(ctPackages);
        case META_KEY_SOURCE_URI -> Optional.ofNullable(define.getUri().toString());
        default -> Optional.empty();
        };
    }

    // ==================== Inner class: DefineDataTableMetadata ====================

    /**
     * Wraps an {@link ItemGroupDef} as {@link IDataTableMetadata}.
     */
    private class DefineDataTableMetadata implements IDataTableMetadata
    {

        private final ItemGroupDef itemGroup;

        private final @Nullable String label;

        private final @Nullable URI tableURI;

        private final Map<String, Integer> domainKeyPositions;

        private @Nullable List<IColumnMetadata> columns;

        private @Nullable Map<String, Object> metaMap;

        DefineDataTableMetadata(ItemGroupDef aItemGroup)
        {
            itemGroup = Objects.requireNonNull(aItemGroup, "itemGroup");
            domainKeyPositions = parseDomainKeys(aItemGroup.getDomainKeys());

            String lbl = aItemGroup.getLabel();
            if (CDT.isBlankOrNull(lbl))
            {
                lbl = DefineSupport.getDescriptionString(aItemGroup);
            }
            label = lbl;

            Leaf leaf = aItemGroup.getLeaf();
            if (leaf != null && leaf.getHref() != null)
            {
                tableURI = define.getUri().resolve(leaf.getHref());
            }
            else
            {
                tableURI = null;
            }
        }


        @Override
        public String getName()
        {
            return itemGroup.getName();
        }


        @Override
        public @Nullable String getLabel()
        {
            return label;
        }


        @Override
        public @Nullable URI getTableURI()
        {
            return tableURI;
        }


        @Override
        public List<IColumnMetadata> getColumns()
        {
            if (columns == null)
            {
                // Wrapped so the lazily-built list cannot be mutated through the accessor
                // (SpotBugs EI_EXPOSE_REP).
                columns = Collections.unmodifiableList(buildColumns());
            }
            return columns;
        }


        @Override
        public Optional<IColumnMetadata> getColumn(String aColumnName)
        {
            return getColumns().stream()//
                    .filter(c -> isColumnNameCaseSensitive()
                            ? Objects.equals(c.getName(), aColumnName)
                            : CDT.equalsIgnoreCase(c.getName(), aColumnName))//
                    .findFirst();
        }


        @Override
        public @Nullable String getClassName()
        {
            // Effective form: the Define-XML 2.1 <def:Class Name="…"> element when present, else
            // the 2.0 def:Class attribute — before this unification a native-2.1 define yielded a
            // null class for every dataset (the element form was silently dropped by the
            // attribute-only binding).
            //
            // @Nullable matches both sides: IDataTableMetadata.getClassName() declares it, and a
            // define.xml need not state a class at all.
            return itemGroup.getEffectiveClassName();
        }


        @Override
        public List<String> getSubClassNames()
        {
            return itemGroup.getSubClassNames();
        }


        @Override
        public String getStructure()
        {
            return itemGroup.getStructure();
        }


        @Override
        public Set<String> getMetaKeys()
        {
            return getMetaMap().keySet();
        }


        @Override
        public Optional<Object> getMetaValue(String aKey)
        {
            return Optional.ofNullable(getMetaMap().get(aKey));
        }


        private Map<String, Object> getMetaMap()
        {
            if (metaMap == null)
            {
                // Capture the freshly-created map in a non-null local so the lambda below does not
                // dereference the @Nullable field.
                Map<String, Object> m = new HashMap<>();
                metaMap = m;
                putIfNotBlank(m, DataTableMetaSupport.META_KEY_COMMENT, getComment(itemGroup));
                putIfNotBlank(m, DataTableMetaSupport.META_KEY_STRUCTURE, itemGroup.getStructure());
                putIfNotBlank(m, DataTableMetaSupport.META_KEY_REPEATING, itemGroup.getRepeating());
                putIfNotBlank(m, DataTableMetaSupport.META_KEY_PURPOSE, itemGroup.getPurpose());
                putIfNotBlank(m, DataTableMetaSupport.META_KEY_SAS_DATASET_NAME,
                        itemGroup.getSasDatasetName());
                putIfNotBlank(m, DataTableMetaSupport.META_KEY_ITEM_NO_DATA,
                        itemGroup.getHasNoData());
                define.getStandardByOID(itemGroup.getStandardOID())//
                        .map(s -> "%s-%s (%s)".formatted(s.getName(), s.getVersion(), s.getType()))//
                        .ifPresent(s -> m.put(DataTableMetaSupport.META_KEY_STANDARD, s));
            }
            return metaMap;
        }


        private List<IColumnMetadata> buildColumns()
        {
            List<ItemRef> itemRefs = itemGroup.getItemRefs();
            if (CDT.isEmptyOrNull(itemRefs))
            {
                return Collections.emptyList();
            }

            List<IColumnMetadata> result = new ArrayList<>(itemRefs.size());
            int idx = 0;
            for (ItemRef ir : itemRefs)
            {
                Optional<ItemDef> optDef = define.getItemDefByOid(ir.getItemOID());
                if (optDef.isEmpty())
                {
                    continue;
                }
                ItemDef def = optDef.get();
                int derivedKs = domainKeyPositions.getOrDefault(
                        def.getName() != null ? def.getName().toLowerCase(Locale.ROOT) : "", 0);
                result.add(new DefineColumnMetadata(def, ir, idx++, derivedKs));
            }
            return Collections.unmodifiableList(result);
        }


        private static Map<String, Integer> parseDomainKeys(String aDomainKeys)
        {
            if (CDT.isBlankOrNull(aDomainKeys))
            {
                return Collections.emptyMap();
            }
            Map<String, Integer> result = new HashMap<>();
            int pos = 1;
            for (String token : aDomainKeys.split(",", 0))
            {
                String key = token.trim();
                if (key.isEmpty())
                {
                    continue;
                }
                result.putIfAbsent(key.toLowerCase(Locale.ROOT), pos++);
            }
            return Map.copyOf(result);
        }


        private @Nullable String getComment(ItemGroupDef aItemGrp)
        {
            if (aItemGrp == null)
            {
                return null;
            }
            String comment = aItemGrp.getComment();
            if (comment != null)
            {
                return comment;
            }
            CommentDef cdef = define.getCommentDefByOID(aItemGrp.getCommentOID()).orElse(null);
            return DefineSupport.getDescriptionString(cdef);
        }
    }

    // ==================== Inner class: DefineColumnMetadata ====================


    /**
     * Wraps an {@link ItemDef} and its {@link ItemRef} as {@link IColumnMetadata}.
     */
    private class DefineColumnMetadata implements IColumnMetadata
    {

        private final ItemDef itemDef;

        private final ItemRef itemRef;

        private final int index;

        private final int derivedKeySequence;

        private final @Nullable String label;

        private final @Nullable String displayFormat;

        private final @Nullable String codelistName;

        private @Nullable Map<String, Object> metaMap;

        DefineColumnMetadata(ItemDef aItemDef, ItemRef aItemRef, int aIndex,
                int aDerivedKeySequence)
        {
            itemDef = Objects.requireNonNull(aItemDef, "itemDef");
            itemRef = aItemRef;
            index = aIndex;
            derivedKeySequence = aDerivedKeySequence;

            String lbl = aItemDef.getLabel();
            if (CDT.isBlankOrNull(lbl))
            {
                lbl = DefineSupport.getDescriptionString(aItemDef);
            }
            label = lbl;

            codelistName = resolveCodelistName();
            displayFormat = resolveDisplayFormat();
        }


        @Override
        public String getName()
        {
            return itemDef.getName();
        }


        @Override
        public @Nullable String getLabel()
        {
            return label;
        }


        @Override
        public @Nullable String getDisplayFormat()
        {
            return displayFormat;
        }


        @Override
        public int getIndex()
        {
            return index;
        }


        @Override
        public DataValueType getType()
        {
            return mapDataType(itemDef.getDataType());
        }


        @Override
        public int getLength()
        {
            Integer len = itemDef.getLength();
            return len != null ? len : 0;
        }


        @Override
        public String getNativeType()
        {
            return itemDef.getDataType();
        }


        @Override
        public int getKeySequence()
        {
            if (itemRef != null)
            {
                Integer ks = itemRef.getKeySequence();
                if (ks != null)
                {
                    return ks;
                }
            }
            return derivedKeySequence;
        }


        @Override
        public boolean isByGroup()
        {
            return false;
        }


        @Override
        public @Nullable String getCore()
        {
            if (itemRef == null)
            {
                return null;
            }
            String mandatory = itemRef.getMandatory();
            if ("Yes".equalsIgnoreCase(mandatory))
            {
                return "Req";
            }
            if ("No".equalsIgnoreCase(mandatory))
            {
                return "Exp";
            }
            return null;
        }


        @Override
        public @Nullable String getRole()
        {
            return itemRef != null ? itemRef.getRole() : null;
        }


        @Override
        public @Nullable String getCodelist()
        {
            return codelistName;
        }


        @Override
        public Set<String> getMetaKeys()
        {
            return getMetaMap().keySet();
        }


        @Override
        public Optional<Object> getMetaValue(String aKey)
        {
            return Optional.ofNullable(getMetaMap().get(aKey));
        }


        private Map<String, Object> getMetaMap()
        {
            if (metaMap == null)
            {
                metaMap = new HashMap<>();

                // from ItemDef
                putIfNotBlank(metaMap, DataTableMetaSupport.META_KEY_COMMENT, getComment(itemDef));
                putIfNotBlank(metaMap, DataTableMetaSupport.META_KEY_ITEM_ORIGIN,
                        getOrigin(itemDef));
                putIfNotNull(metaMap, DataTableMetaSupport.META_KEY_ITEM_SIGNIFICANT_DIGITS,
                        itemDef.getSignificantDigits());
                putIfNotBlank(metaMap, DataTableMetaSupport.META_KEY_SAS_FIELD_NAME,
                        itemDef.getSasFieldName());

                // from ItemRef
                if (itemRef != null)
                {
                    MethodDef md = define.getMethodDefByOID(itemRef.getMethodOID()).orElse(null);
                    putIfNotBlank(metaMap, DataTableMetaSupport.META_KEY_ITEM_METHOD,
                            DefineSupport.getDescriptionString(md));
                    putIfNotNull(metaMap, DataTableMetaSupport.META_KEY_ITEM_KEY_SEQUENCE,
                            itemRef.getKeySequence());
                    putIfNotBlank(metaMap, DataTableMetaSupport.META_KEY_ITEM_MANDATORY,
                            itemRef.getMandatory());
                    putIfNotNull(metaMap, DataTableMetaSupport.META_KEY_ITEM_ORDER_NUMBER,
                            itemRef.getOrderNumber());
                    putIfNotBlank(metaMap, DataTableMetaSupport.META_KEY_ITEM_NO_DATA,
                            itemRef.getHasNoData());
                    putIfNotBlank(metaMap, DataTableMetaSupport.META_KEY_ITEM_ROLE,
                            itemRef.getRole());
                }
                if (!metaMap.containsKey(DataTableMetaSupport.META_KEY_ITEM_KEY_SEQUENCE)
                        && derivedKeySequence > 0)
                {
                    metaMap.put(DataTableMetaSupport.META_KEY_ITEM_KEY_SEQUENCE,
                            derivedKeySequence);
                }
            }
            return metaMap;
        }


        private @Nullable String resolveCodelistName()
        {
            CodeListRef clr = itemDef.getCodeListRef();
            if (clr != null && clr.getCodeListOID() != null)
            {
                CodeList cdl = define.getCodeListByOid(clr.getCodeListOID()).orElse(null);
                if (cdl != null)
                {
                    return cdl.getName();
                }
            }
            return null;
        }


        private String resolveDisplayFormat()
        {
            CodeListRef clr = itemDef.getCodeListRef();
            if (clr != null && clr.getCodeListOID() != null)
            {
                CodeList cdl = define.getCodeListByOid(clr.getCodeListOID()).orElse(null);
                if (cdl != null)
                {
                    DataValueType type = getType();
                    if (type == DataValueType.STRING)
                    {
                        return "$" + cdl.getName() + ".";
                    }
                    else
                    {
                        return cdl.getName() + ".";
                    }
                }
            }
            return itemDef.getDisplayFormat();
        }


        private @Nullable String getComment(ItemDef aItem)
        {
            if (aItem == null)
            {
                return null;
            }
            String comment = aItem.getComment();
            if (comment != null)
            {
                return comment;
            }
            CommentDef cdef = define.getCommentDefByOID(aItem.getCommentOID()).orElse(null);
            return DefineSupport.getDescriptionString(cdef);
        }


        private @Nullable String getOrigin(ItemDef aItem)
        {
            if (aItem == null)
            {
                return null;
            }
            String res = aItem.getOrigin();
            if (!CDT.isBlankOrNull(res))
            {
                return res;
            }
            return DefineSupport.getDescriptionString(aItem.getOriginElement());
        }
    }

    // ==================== Inner class: DefineCodeList ====================


    /**
     * Wraps a {@link CodeList} as {@link ICodeList}.
     */
    private static class DefineCodeList implements ICodeList
    {

        private final CodeList codelist;

        private final String name;

        private @Nullable List<ICodelistEntry> entries;

        DefineCodeList(CodeList aCodelist)
        {
            codelist = aCodelist;

            String n = aCodelist.getName();
            if ("text".equals(aCodelist.getDataType()))
            {
                n = "$" + n;
            }
            name = n;
        }


        @Override
        public String getName()
        {
            return name;
        }


        @Override
        public DataValueType getValueType()
        {
            if ("text".equals(codelist.getDataType()))
            {
                return DataValueType.STRING;
            }
            if ("integer".equals(codelist.getDataType()))
            {
                return DataValueType.LONG;
            }
            if ("float".equals(codelist.getDataType()))
            {
                return DataValueType.DOUBLE;
            }
            return DataValueType.STRING;
        }


        @Override
        public List<ICodelistEntry> getEntries()
        {
            if (entries == null)
            {
                // Wrapped so the lazily-built list cannot be mutated through the accessor
                // (SpotBugs EI_EXPOSE_REP).
                entries = Collections.unmodifiableList(buildEntries());
            }
            return entries;
        }


        @Override
        public Boolean isExtensible()
        {
            return Boolean.FALSE;
        }


        @Override
        public Set<String> getMetaKeys()
        {
            String desc = DefineSupport.getDescriptionString(codelist);
            if (desc != null)
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
                return Optional.ofNullable(DefineSupport.getDescriptionString(codelist));
            }
            return Optional.empty();
        }


        private List<ICodelistEntry> buildEntries()
        {
            List<CodeListItem> items = codelist.getCodeListItems();
            if (!CDT.isEmptyOrNull(items))
            {
                return buildFromCodeListItems(items);
            }
            List<EnumeratedItem> enumItems = codelist.getEnumeratedItems();
            if (!CDT.isEmptyOrNull(enumItems))
            {
                return buildFromEnumeratedItems(enumItems);
            }
            return Collections.emptyList();
        }


        private List<ICodelistEntry> buildFromCodeListItems(List<CodeListItem> aItems)
        {
            List<ICodelistEntry> result = new ArrayList<>(aItems.size());
            for (CodeListItem item : aItems)
            {
                String code = item.getCodedValue();
                if (code == null)
                {
                    continue;
                }
                Decode dc = item.getDecode();
                if (dc == null)
                {
                    continue;
                }
                List<TranslatedText> trtxts = dc.getTranslatedTexts();
                // Explicit null/empty check (not CDT.isEmptyOrNull) so NullAway narrows trtxts
                // to non-null for the get(0) below.
                if (trtxts == null || trtxts.isEmpty())
                {
                    continue;
                }
                String decode = trtxts.get(0).getValue();
                if (decode != null)
                {
                    // CDT.tri is poly-null (null in → null out); code and decode are non-null
                    // here, so the trimmed values are too, but NullAway sees @Nullable — guard.
                    String tcode = CDT.tri(code);
                    String tdecode = CDT.tri(decode);
                    if (tcode != null && tdecode != null)
                    {
                        result.add(new DefineCodelistEntry(tcode, tdecode));
                    }
                }
            }
            return Collections.unmodifiableList(result);
        }


        private List<ICodelistEntry> buildFromEnumeratedItems(List<EnumeratedItem> aItems)
        {
            List<ICodelistEntry> result = new ArrayList<>(aItems.size());
            for (EnumeratedItem item : aItems)
            {
                String code = item.getCodedValue();
                if (code != null)
                {
                    // CDT.tri is poly-null; code is non-null here so the trimmed value is too.
                    String tcode = CDT.tri(code);
                    if (tcode != null)
                    {
                        result.add(new DefineCodelistEntry(tcode, tcode));
                    }
                }
            }
            return Collections.unmodifiableList(result);
        }
    }

    // ==================== Inner class: DefineCodelistEntry ====================


    private record DefineCodelistEntry(String codeValue,
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

    // ==================== Helpers ====================

    private static DataValueType mapDataType(String aDataType)
    {
        if (aDataType == null)
        {
            return DataValueType.OTHER;
        }
        return switch (aDataType)
        {
        case "text" -> DataValueType.STRING;
        case "integer" -> DataValueType.LONG;
        case "float" -> DataValueType.DOUBLE;
        case "datetime", "date", "partialDate", "partialDatetime", "durationDatetime" -> DataValueType.STRING;
        default -> DataValueType.OTHER;
        };
    }


    private static void putIfNotBlank(Map<String, Object> aMap, String aKey,
            @Nullable String aValue)
    {
        if (!CDT.isBlankOrNull(aValue))
        {
            aMap.put(aKey, aValue);
        }
    }


    private static void putIfNotNull(Map<String, Object> aMap, String aKey, @Nullable Object aValue)
    {
        if (aValue != null)
        {
            aMap.put(aKey, aValue);
        }
    }
}
