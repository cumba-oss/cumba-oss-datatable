package net.cumba.datatable.provider.define.metadata;

// OSS-IDENTITY provider-define: byte-identical in cumba-datatable-cdisc and cumba-oss-datatable.

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import net.cumba.cdisc.define.Alias;
import net.cumba.cdisc.define.CodeList;
import net.cumba.cdisc.define.CodeListItem;
import net.cumba.cdisc.define.CommentDef;
import net.cumba.cdisc.define.DefineSupport;
import net.cumba.cdisc.define.EnumeratedItem;
import net.cumba.cdisc.define.ExternalCodeList;
import net.cumba.cdisc.define.Standard;
import net.cumba.datatable.help.CDT;
import org.jspecify.annotations.Nullable;

/**
 * The format-level metadata of a Define-XML codelist (PLAN-define-format-metadata): what the define
 * says about the codelist beyond its name and items -- display name, OID, data type,
 * {@code SASFormatName}, NCI code ({@code Alias Context="nci:ExtCodeID"}), the resolved
 * {@code def:Standard}, {@code def:IsNonStandard}, the number of extended values, the resolved
 * {@code def:CommentDef}, and the dictionary and version of an {@code ExternalCodeList}. One
 * builder serves both the format catalog of a define and the define opened as a library (FM3 (a)):
 * the keys are string literals equal to the {@code IDataFormat.META_*} constants (this class has no
 * format dependency, so the OSS twin carries it byte-identical; an internal test compares the
 * rosters), and {@link #prefixed} is the {@code format.}-prefixed form a codelist exposes as
 * metadata.
 * <p>
 * Built once per define: the standards and comment definitions are indexed by OID up front
 * ({@code DefineSupport.getStandardByOID} scans per call).
 * </p>
 */
public final class DefineCodelistMetadata
{

    /** The prefix of a codelist's format-level metadata keys ({@link #prefixed}). */
    public static final String PREFIX = "format.";

    /** The codelist's {@code Name} (the display name; the format's lookup name may differ). */
    public static final String DISPLAY_NAME = "displayName";

    /** The codelist's {@code OID}. */
    public static final String OID = "oid";

    /** The codelist's {@code DataType}. */
    public static final String DATA_TYPE = "dataType";

    /** The raw {@code SASFormatName}. */
    public static final String SAS_FORMAT_NAME = "sasFormatName";

    /** The NCI code: the first {@code Alias} with {@code Context="nci:ExtCodeID"}. */
    public static final String NCI_CODE = "nciCode";

    /** The resolved {@code def:Standard}, rendered; the raw OID when unresolved. */
    public static final String STANDARD = "standard";

    /** The raw {@code def:StandardOID}. */
    public static final String STANDARD_OID = "standardOid";

    /** {@code Yes} when the codelist carries {@code def:IsNonStandard="Yes"}. */
    public static final String NON_STANDARD = "nonStandard";

    /** The number of items with {@code def:ExtendedValue="Yes"}, when any (FM4 (a)). */
    public static final String EXTENDED_VALUES = "extendedValues";

    /** The description of the resolved {@code def:CommentDef}. */
    public static final String COMMENT = "comment";

    /** The {@code Dictionary} of an {@code ExternalCodeList}. */
    public static final String DICTIONARY = "dictionary";

    /** The {@code Version} of an {@code ExternalCodeList} (kept apart from the name, FM2). */
    public static final String DICTIONARY_VERSION = "dictionaryVersion";

    /** The {@code Alias} context that carries the NCI code of a codelist. */
    static final String NCI_CODE_CONTEXT = "nci:ExtCodeID";

    /** Every key this builder can emit, in display order. */
    private static final List<String> KEYS = List.of(DISPLAY_NAME, OID, DATA_TYPE, SAS_FORMAT_NAME,
            NCI_CODE, STANDARD, STANDARD_OID, NON_STANDARD, EXTENDED_VALUES, COMMENT, DICTIONARY,
            DICTIONARY_VERSION);

    /** How many codelist maps were built, for the tests of the lazy, once-per-codelist build. */
    private static final AtomicLong BUILDS = new AtomicLong();

    private final Map<String, Standard> standardsByOid;

    private final Map<String, CommentDef> commentsByOid;

    private DefineCodelistMetadata(Map<String, Standard> aStandards,
            Map<String, CommentDef> aComments)
    {
        standardsByOid = aStandards;
        commentsByOid = aComments;
    }


    /**
     * The builder of a define, its standards and comment definitions indexed once.
     *
     * @param aDefine
     *            the define.
     * @return the builder.
     */
    public static DefineCodelistMetadata of(DefineSupport aDefine)
    {
        Map<String, Standard> standards = new HashMap<>();
        aDefine.getStandards().forEach(std ->
        {
            if (std.getOid() != null)
            {
                standards.putIfAbsent(std.getOid(), std);
            }
        });
        Map<String, CommentDef> comments = new HashMap<>();
        aDefine.getCommentDefs().forEach(cd ->
        {
            if (cd.getOid() != null)
            {
                comments.putIfAbsent(cd.getOid(), cd);
            }
        });
        return new DefineCodelistMetadata(standards, comments);
    }


    /**
     * Every key this builder can emit, in display order.
     *
     * @return the keys.
     */
    public static List<String> keys()
    {
        return KEYS;
    }


    /**
     * The number of codelist maps built so far by any instance (a test instrument: a build that
     * must not happen shows here).
     *
     * @return the count.
     */
    static long builds()
    {
        return BUILDS.get();
    }


    /**
     * The format-level metadata of a codelist, keyed by the unprefixed keys in display order. Blank
     * values are left out; a codelist carrying nothing beyond its items still has its display name,
     * OID and data type.
     *
     * @param aCodelist
     *            the codelist.
     * @return the ordered key-value map (unmodifiable).
     */
    public Map<String, String> of(CodeList aCodelist)
    {
        BUILDS.incrementAndGet();
        Map<String, String> res = new LinkedHashMap<>();
        put(res, DISPLAY_NAME, aCodelist.getName());
        put(res, OID, aCodelist.getOid());
        put(res, DATA_TYPE, aCodelist.getDataType());
        put(res, SAS_FORMAT_NAME, aCodelist.getSasFormatName());
        put(res, NCI_CODE, nciCode(aCodelist));
        String stdOid = aCodelist.getStandardOID();
        if (!CDT.isBlankOrNull(stdOid))
        {
            Standard std = standardsByOid.get(stdOid);
            put(res, STANDARD, std != null ? renderStandard(std) : stdOid);
            put(res, STANDARD_OID, stdOid);
        }
        if ("Yes".equals(aCodelist.getIsNonStandard()))
        {
            res.put(NON_STANDARD, "Yes");
        }
        int extended = extendedValues(aCodelist);
        if (extended > 0)
        {
            res.put(EXTENDED_VALUES, Integer.toString(extended));
        }
        String commentOid = aCodelist.getCommentOID();
        if (!CDT.isBlankOrNull(commentOid))
        {
            put(res, COMMENT, DefineSupport.getDescriptionString(commentsByOid.get(commentOid)));
        }
        ExternalCodeList ext = aCodelist.getExternalCodeList();
        if (ext != null)
        {
            put(res, DICTIONARY, ext.getDictionary());
            put(res, DICTIONARY_VERSION, ext.getVersion());
        }
        return Collections.unmodifiableMap(res);
    }


    /**
     * The same map with every key prefixed by {@link #PREFIX}: the metadata a codelist exposes.
     *
     * @param aCodelist
     *            the codelist.
     * @return the ordered, prefixed map (unmodifiable).
     */
    public Map<String, String> prefixed(CodeList aCodelist)
    {
        Map<String, String> res = new LinkedHashMap<>();
        of(aCodelist).forEach((k, v) -> res.put(PREFIX + k, v));
        return Collections.unmodifiableMap(res);
    }


    /**
     * A {@code def:Standard} rendered for display: {@code Name}, {@code PublishingSet},
     * {@code Type} and {@code Version}, blank parts skipped, e.g.
     * {@code CDISC/NCI SDTM CT 2023-12-15}.
     *
     * @param aStandard
     *            the standard.
     * @return the rendering; the OID when every part is blank.
     */
    static String renderStandard(Standard aStandard)
    {
        StringBuilder sb = new StringBuilder();
        for (String part : new String[]
        {
                aStandard.getName(), aStandard.getPublishingSet(), aStandard.getType(),
                aStandard.getVersion()
        })
        {
            if (part != null && !part.isBlank())
            {
                if (sb.length() > 0)
                {
                    sb.append(' ');
                }
                sb.append(part.trim());
            }
        }
        return sb.length() > 0 ? sb.toString() : String.valueOf(aStandard.getOid());
    }


    private static @Nullable String nciCode(CodeList aCodelist)
    {
        List<Alias> aliases = aCodelist.getAliases();
        if (aliases == null)
        {
            return null;
        }
        for (Alias alias : aliases)
        {
            if (NCI_CODE_CONTEXT.equals(alias.getContext()) && !CDT.isBlankOrNull(alias.getName()))
            {
                return alias.getName();
            }
        }
        return null;
    }


    private static int extendedValues(CodeList aCodelist)
    {
        int n = 0;
        List<CodeListItem> items = aCodelist.getCodeListItems();
        if (items != null)
        {
            for (CodeListItem item : items)
            {
                if ("Yes".equals(item.getExtendedValue()))
                {
                    n++;
                }
            }
        }
        List<EnumeratedItem> enumItems = aCodelist.getEnumeratedItems();
        if (enumItems != null)
        {
            for (EnumeratedItem item : enumItems)
            {
                if ("Yes".equals(item.getExtendedValue()))
                {
                    n++;
                }
            }
        }
        return n;
    }


    private static void put(Map<String, String> aMap, String aKey, @Nullable String aValue)
    {
        // explicit null + blank check so NullAway narrows aValue for the trim
        if (aValue != null && !aValue.isBlank())
        {
            aMap.put(aKey, aValue.trim());
        }
    }
}
