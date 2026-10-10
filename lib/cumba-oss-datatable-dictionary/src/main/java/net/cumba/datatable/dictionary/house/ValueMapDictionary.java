package net.cumba.datatable.dictionary.house;

// OSS-IDENTITY datatable-dictionary: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import org.jspecify.annotations.Nullable;

/**
 * T1 — an external medical-dictionary modelled as a simple <b>value-map</b>, loaded from one
 * house-format JSON file ({@code <type>.json[.gz]}). A dictionary reduces every dictionary
 * conformance check to a lookup:
 *
 * <ul>
 * <li><b>membership</b> — the term (upper-cased) is a key of {@code levels[termType]};</li>
 * <li><b>case</b> — {@code levels[termType][upper(term)]} equals the term verbatim (the value holds
 * the preferred case);</li>
 * <li><b>hierarchy-path</b> — keyed by the levels of the two variables read, the parent is in
 * {@code hierarchy_by_level[termLevel][term][parentLevel]} (runbook W2 DI1,
 * {@link LevelScopedHierarchy}); since runbook W4 DI2 the bare-name {@code hierarchy[term]} map is
 * no MedDRA lookup any more (a MedDRA file without the level-scoped section answers no hierarchy);
 * every other type answers from it as before W4 ({@link #onHierarchyPathAsBefore}, W4 fold R5
 * P3-L3);</li>
 * <li><b>code&harr;decode pair</b> — some registry / attribute map carries {@code map[code] ==
 * decode};</li>
 * <li><b>per-term attribute</b> — {@code attributes[attr][upper(term)]}.</li>
 * </ul>
 *
 * <p>
 * Small dummy maps are checked into the conformance engine's repository
 * ({@code cumba-corej-core/dictionaries/}) so the dictionary conformance rules execute and the
 * {@code .cdt} scenarios discriminate without any licensed dictionary data present; real dictionary
 * data is installed by the operator behind the same file format
 * ({@code plans/done/PLAN-dictionary-seeder.md}).
 * </p>
 *
 * <p>
 * <b>Readers of this format.</b> This class — moved here from the conformance engine
 * ({@code cumba-corej-core}, PLAN-dictionary-backed-formats B1, DF26) and shared by the engine and
 * the dictionary formats of the data browser — in two load {@link Profile profiles}:
 * {@link Profile#FULL} parses the whole file as a tree ({@link #load}, {@link #parse});
 * {@link Profile#FORMAT} is streamed by {@link HouseDictionaryReader} and holds only what the
 * format path reads (DF25 as amended by owner ruling OQ2, 2026-10-10). Besides, the engine's
 * {@code DictionaryValidationTest.caseContractViolations} walks the same four sections
 * independently to audit the preferred-case contract of the engine's dummy files.
 * </p>
 *
 * <p>
 * <b>Format-path read API</b> (Phase A's, taken over by this class in B1 — D1: the engine's class
 * is the survivor): {@link #isMember}, {@link #decode}, {@link #hasLevel}, {@link #decodes},
 * {@link #houseFormat}, {@link #getRetrieved} — answered identically in both profiles. A code level
 * decodes from its own source only: house format 1 a fixed level → {@code pairs} registry table
 * ({@code LOINC → pairs.loinc}, {@code UNII → pairs.unii}, {@code SNOMEDCD → pairs.snomed},
 * {@code MEDRTCD → pairs.medrt}), house format 2 the {@code decodes} section — <b>never</b> the
 * fall-through scan of {@link #codeDecodePair} / {@link #hasDecode}, never {@code medrt-base}; no
 * section is enumerated (DF11).
 * </p>
 *
 * <p>
 * Instances are immutable. Since D-TA-3 / Fix #266 the hierarchy, pair and decode lookups are
 * <b>flag-aware</b>: each keeps a case-sensitive primary path (comparing the dictionary's
 * as-authored entries verbatim) and a case-folded fallback path serving
 * {@code case_sensitive: false}, backed by dual pre-folded indexes so both stay O(1) per probe.
 * Term membership keeps its two established primitives: {@link #isValidTerm} (folded probe over the
 * folded {@code levels} index) and {@link #caseMatches} (folded probe, verdict compared against the
 * stored preferred-case value verbatim — the sensitive path).
 * </p>
 */
public final class ValueMapDictionary
{

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** The house format's level-scoped hierarchy section (runbook W2 DI1). */
    public static final String HIERARCHY_BY_LEVEL = "hierarchy_by_level";

    /**
     * The house-format-2 section of code &rarr; decode maps per code level (DF23): out of reach of
     * the {@link #codeDecodePair} / {@link #hasDecode} fall-through scan by construction.
     */
    public static final String DECODES = "decodes";

    /**
     * The top-level field naming the house-format version; absent means {@value #HOUSE_FORMAT_1}.
     */
    public static final String HOUSE_FORMAT = "house_format";

    /** The house-format version a file without a {@value #HOUSE_FORMAT} field has. */
    public static final int HOUSE_FORMAT_1 = 1;

    /** The house-format version that carries the {@value #DECODES} section. */
    public static final int HOUSE_FORMAT_2 = 2;

    /**
     * House format 1: the level whose codes decode through a {@code pairs} registry, keyed as
     * {@code <type>/<level>} — the registry the converter of that type writes for exactly that
     * level, and nothing else (never the fall-through scan, never {@code medrt-base}).
     */
    private static final Map<String, String> V1_DECODE_REGISTRIES = Map.of("loinc/LOINC", "loinc",
            "unii/UNII", "unii", "snomed/SNOMEDCD", "snomed", "medrt/MEDRTCD", "medrt");

    /**
     * The {@code pairs} registries a {@link Profile#FORMAT} load keeps — the registries of the
     * house-format-1 decode table; every other registry is skipped while streaming.
     */
    static final Set<String> V1_REGISTRY_NAMES = Set.copyOf(V1_DECODE_REGISTRIES.values());

    /**
     * What a load holds (plan D-B0-2: one model class, two load profiles, one cache).
     */
    public enum Profile
    {
        /**
         * Every section, with the case-folded twins the rule functions' {@code case_sensitive:
         * false} needs — the conformance engine's load.
         */
        FULL,

        /**
         * The format subset (DF25 as amended by owner ruling OQ2): {@code levels}, the
         * house-format-1 decode registries and {@code decodes}; no folded twins; every other
         * section reduced to the one boolean {@link #hasContent} needs. Answers the format-path
         * read API exactly as a {@link #FULL} load does; the engine-only lookups refuse.
         */
        FORMAT
    }


    /**
     * Where a dictionary came from and which release it is — written by the dictionary installer,
     * read back for the report's {@code *_Version} conformance fields.
     *
     * <p>
     * The version is {@code null} for a file that declares none (every hand-authored fixture, and
     * every file predating the installer). ⚑ The {@code source} / {@code retrieved} components the
     * installer also writes were never read and left the record with
     * PLAN-retire-dead-multi-match-lookup U12 (D-B9). Provenance is <b>reported, never checked</b>:
     * no conformance finding compares a declared define.xml {@code ExternalCodeList/@Version}
     * against it.
     * </p>
     *
     * @param version
     *            the vendor's own release identifier, verbatim ({@code "27.0"},
     *            {@code "2026.07.06"}, {@code "SEP_2020"}, {@code "4Aug2026"}) — never parsed,
     *            ordered or normalised, only compared for equality
     */
    public record Provenance(@Nullable String version)
    {

        /** The provenance of a file that declares none. */
        public static final Provenance NONE = new Provenance(null);
    }

    private final String type;

    private final Profile profile;

    /**
     * The type whose house-format-1 {@code pairs} registry decodes a code level: the declared type,
     * or for a file declaring none its file stem (the type the store bound it as).
     */
    private final String registryType;

    private final Provenance provenance;

    private final @Nullable String source;

    private final @Nullable String retrieved;

    private final int houseFormat;

    /** House format 2: code level &rarr; code (verbatim) &rarr; decode (verbatim), interned. */
    private final Map<String, Map<String, String>> decodes;

    /**
     * {@link Profile#FORMAT} only: whether a section the load skipped held an entry the full load
     * keeps (read for {@link #hasContent}); always {@code false} for a full load.
     */
    private final boolean skippedContent;

    private final Map<String, Map<String, String>> levels;

    /**
     * Whether the bare-name {@code hierarchy} section carries an ancestor list — read for
     * {@link #hasContent}: since runbook W4 DI2 no MedDRA lookup reads the bare-name map (the
     * converter keeps writing it for older engine releases sharing a dictionary directory), so a
     * MedDRA file does not hold it in memory ({@link #hierarchy} is held for the other types).
     */
    private final boolean bareHierarchyContent;

    /**
     * The bare-name hierarchy exactly as authored (term &rarr; ancestor terms) — held for a
     * NON-MedDRA file only (W4 fold, R5 P3-L3: those types answer from it as before W4); a MedDRA
     * file keeps just {@link #bareHierarchyContent}, so its memory stays as runbook W4 DI2 built
     * it.
     */
    private final Map<String, List<String>> hierarchy;

    /** The case-folded twin of {@link #hierarchy} (keys and ancestor values upper-cased). */
    private final Map<String, List<String>> hierarchyFolded;

    /**
     * The level-scoped hierarchy ({@code hierarchy_by_level}, runbook W2 DI1), or
     * {@link LevelScopedHierarchy#EMPTY} for a file without it — a file converted before DI1, which
     * answers no hierarchy since runbook W4 DI2.
     */
    private final LevelScopedHierarchy hierarchyByLevel;

    /** Pair registries exactly as authored: registry &rarr; code &rarr; decode. */
    private final Map<String, Map<String, String>> pairs;

    /** Pair registries with the code keys upper-cased (decodes verbatim). */
    private final Map<String, Map<String, String>> pairsFolded;

    /** Attribute maps exactly as authored: attribute &rarr; term/code &rarr; value. */
    private final Map<String, Map<String, String>> attributes;

    /** Attribute maps with the term/code keys upper-cased (values verbatim). */
    private final Map<String, Map<String, String>> attributesFolded;

    /** The provenance header of a file: what both profiles read before the sections. */
    record Header(String type, String registryType, Provenance provenance, @Nullable String source,
            @Nullable String retrieved, int houseFormat)
    {
    }

    /** A {@link Profile#FULL} load. */
    private ValueMapDictionary(Header header, Map<String, Map<String, String>> levels,
            Map<String, List<String>> hierarchy, LevelScopedHierarchy hierarchyByLevel,
            Map<String, Map<String, String>> pairs, Map<String, Map<String, String>> attributes,
            Map<String, Map<String, String>> decodes)
    {
        this.type = header.type();
        this.profile = Profile.FULL;
        this.registryType = header.registryType().toLowerCase(Locale.ROOT);
        this.provenance = header.provenance();
        this.source = header.source();
        this.retrieved = header.retrieved();
        this.houseFormat = header.houseFormat();
        this.levels = levels;
        this.bareHierarchyContent = hierarchy.values().stream().anyMatch(l -> !l.isEmpty());
        boolean meddra = "meddra".equalsIgnoreCase(type);
        this.hierarchy = meddra ? Map.of() : hierarchy;
        this.hierarchyFolded = meddra ? Map.of() : foldHierarchy(hierarchy);
        this.hierarchyByLevel = hierarchyByLevel;
        this.pairs = pairs;
        this.pairsFolded = foldKeys(pairs);
        this.attributes = attributes;
        this.attributesFolded = foldKeys(attributes);
        this.decodes = decodes;
        this.skippedContent = false;
    }


    /** A {@link Profile#FORMAT} load: no hierarchy, no attributes, no folded twins. */
    private ValueMapDictionary(Header header, Map<String, Map<String, String>> levels,
            Map<String, Map<String, String>> decodeRegistries,
            Map<String, Map<String, String>> decodes, boolean skippedContent)
    {
        this.type = header.type();
        this.profile = Profile.FORMAT;
        this.registryType = header.registryType().toLowerCase(Locale.ROOT);
        this.provenance = header.provenance();
        this.source = header.source();
        this.retrieved = header.retrieved();
        this.houseFormat = header.houseFormat();
        this.levels = levels;
        this.bareHierarchyContent = false;
        this.hierarchy = Map.of();
        this.hierarchyFolded = Map.of();
        this.hierarchyByLevel = LevelScopedHierarchy.EMPTY;
        this.pairs = decodeRegistries;
        this.pairsFolded = Map.of();
        this.attributes = Map.of();
        this.attributesFolded = Map.of();
        this.decodes = decodes;
        this.skippedContent = skippedContent;
    }


    /**
     * A {@link Profile#FORMAT} dictionary, as {@link HouseDictionaryReader} streams it.
     *
     * @param header
     *            the provenance header.
     * @param levels
     *            {@code levels}: level &rarr; upper-cased term &rarr; preferred form.
     * @param decodeRegistries
     *            the {@code pairs} registries of {@link #V1_REGISTRY_NAMES} only.
     * @param decodes
     *            the {@value #DECODES} section.
     * @param skippedContent
     *            whether a skipped section held an entry the full load keeps.
     * @return the dictionary.
     */
    static ValueMapDictionary formatSubset(Header header, Map<String, Map<String, String>> levels,
            Map<String, Map<String, String>> decodeRegistries,
            Map<String, Map<String, String>> decodes, boolean skippedContent)
    {
        return new ValueMapDictionary(header, levels, decodeRegistries, decodes, skippedContent);
    }


    /** The dictionary type (e.g. {@code "meddra"}). */
    public String getType()
    {
        return type;
    }


    /** The vendor release identifier this dictionary was built from, or {@code null}. */
    public @Nullable String getVersion()
    {
        return provenance.version();
    }


    /**
     * Where the installer converted the file from — the raw distribution's provenance it recorded
     * ({@code source}: a local directory's absolute path, or a download's description), or
     * {@code null} for a file that records none.
     */
    public @Nullable String getSource()
    {
        return source;
    }


    /** The installer's {@code retrieved} date ({@code YYYY-MM-DD}, UTC), or {@code null}. */
    public @Nullable String getRetrieved()
    {
        return retrieved;
    }


    /** The house-format version: {@value #HOUSE_FORMAT_1} when the file carries no field. */
    public int houseFormat()
    {
        return houseFormat;
    }


    /** The profile this instance was loaded with. */
    public Profile getProfile()
    {
        return profile;
    }


    /**
     * Whether this dictionary carries any usable content at all — at least one term, ancestor list,
     * pair or attribute (a {@value #DECODES} entry is none: it decodes only a member of a level).
     *
     * <p>
     * <b>Why this exists.</b> Every reader below degrades a missing, {@code null}, array-shaped or
     * misspelled section to an empty map <em>silently</em>, so a truncated download, a partly
     * written file or a converter bug parses cleanly and answers nothing. Such a file would
     * otherwise still make {@link RuntimeDictionaryProvider#isAvailable} return {@code true},
     * bypassing the engine's eager SKIP arm entirely: the membership rules would then find every
     * term invalid and fire on <em>every row</em>, while the pair and decode rules would report
     * {@code noViolation} vacuously. {@link RuntimeDictionaryProvider#loadDirectory} therefore
     * treats a dictionary that fails this test as <b>not loaded</b>, so the established
     * "unavailable ⇒ SKIP, never false-pass" contract catches it whatever its provenance.
     * </p>
     */
    public boolean hasContent()
    {
        // The hierarchy check mirrors anyEntry: a key mapping to an empty ancestor list answers
        // no hierarchy probe, so {"hierarchy":{"X":[]}} is no more content than {"levels":{"X":{}}}
        // — counting it would register the dictionary as available and bypass the SKIP arm.
        // ⚠ decodes do NOT count (B-T review lane 1 MED-1): decode() asks isMember first and no
        // engine lookup reads them, so a decodes-only file answers nothing in either profile.
        return anyEntry(levels) || bareHierarchyContent || !hierarchyByLevel.isEmpty()
                || anyEntry(pairs) || anyEntry(attributes) || skippedContent;
    }


    /** Whether a nested section holds at least one inner entry (an empty inner map is not one). */
    private static boolean anyEntry(Map<String, Map<String, String>> section)
    {
        return section.values().stream().anyMatch(inner -> !inner.isEmpty());
    }


    /**
     * Loads a dictionary from a house-format JSON file on disk. A file whose name ends {@code .gz}
     * is decompressed on the way in, so a large bundle can ship as {@code <type>.json.gz} without a
     * separate unpack step.
     */
    public static ValueMapDictionary load(Path file) throws IOException
    {
        try (InputStream in = open(file))
        {
            return parse(MAPPER.readTree(in), HouseDictionaryReader.fileStem(file));
        }
    }


    /** Opens {@code file}, wrapping it in a GZIP stream when its name ends {@code .gz}. */
    private static InputStream open(Path file) throws IOException
    {
        Path name = file.getFileName();
        InputStream raw = Files.newInputStream(file);
        if (name == null || !name.toString().endsWith(".gz"))
        {
            return raw;
        }
        try
        {
            return new GZIPInputStream(raw);
        }
        catch (IOException e)
        {
            // The wrapper never took ownership, so this stream would otherwise leak on a file
            // named .gz that is not gzip.
            raw.close();
            throw e;
        }
    }


    /**
     * Parses a dictionary from an already-read house-format JSON document ({@link Profile#FULL}).
     */
    public static ValueMapDictionary parse(JsonNode root)
    {
        return parse(root, "");
    }


    private static ValueMapDictionary parse(JsonNode root, String fileStem)
    {
        // a blank type is no type, as the streaming reader reads it (B-T lane 1 LOW-4)
        String t = root.hasNonNull("type") && !root.get("type").isContainerNode()
                && !root.get("type").asText().isBlank() ? root.get("type").asText() : "";
        // R5 PB-M1: ONE intern map for the term-bearing sections — a PT name is a `levels` value
        // and a hierarchy_by_level key, a SOC name recurs on every PT row; the decodes of house
        // format 2 share it (a PT name in levels.PT and decodes.PTCD is held once), and so do pairs
        // and attributes (B-T lane 1 MED-3: a WHODrug trade name is a pairs.whodrug key AND a
        // decodes.DRUGCD value, a preferred name a levels.PT AND a pairs.whodrug value)
        Map<String, String> canonical = new HashMap<>();
        Header header = new Header(t, t.isEmpty() ? fileStem : t, readProvenance(root),
                text(root, "source"), text(root, "retrieved"),
                readHouseFormat(root.get(HOUSE_FORMAT)));
        return new ValueMapDictionary(header, readLevels(root.get("levels"), canonical),
                readNestedStringList(root.get("hierarchy")),
                LevelScopedHierarchy.read(root.get(HIERARCHY_BY_LEVEL), canonical),
                readNestedStringMap(root.get("pairs"), canonical),
                readNestedStringMap(root.get("attributes"), canonical),
                readDecodes(root.get(DECODES), canonical));
    }


    /**
     * The {@value #HOUSE_FORMAT} field: an integer (or a numeric string); anything else — absent,
     * malformed — is {@value #HOUSE_FORMAT_1}, the version of every file written before the field.
     */
    static int readHouseFormat(@Nullable JsonNode node)
    {
        if (node == null || node.isNull())
        {
            return HOUSE_FORMAT_1;
        }
        if (node.isIntegralNumber())
        {
            return node.asInt();
        }
        if (node.isTextual())
        {
            try
            {
                return Integer.parseInt(node.asText().trim());
            }
            catch (NumberFormatException _)
            {
                return HOUSE_FORMAT_1;
            }
        }
        return HOUSE_FORMAT_1;
    }


    /** The {@value #DECODES} section, verbatim, interned through {@code canonical}. */
    private static Map<String, Map<String, String>> readDecodes(@Nullable JsonNode node,
            Map<String, String> canonical)
    {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        if (node == null || !node.isObject())
        {
            return out;
        }
        for (Map.Entry<String, JsonNode> outer : node.properties())
        {
            Map<String, String> inner = new HashMap<>();
            JsonNode innerNode = outer.getValue();
            if (innerNode.isObject())
            {
                for (Map.Entry<String, JsonNode> e : innerNode.properties())
                {
                    if (!e.getValue().isContainerNode())
                    {
                        inner.put(LevelScopedHierarchy.canonical(canonical, e.getKey()),
                                LevelScopedHierarchy.canonical(canonical, e.getValue().asText()));
                    }
                }
            }
            out.put(outer.getKey(), inner);
        }
        return out;
    }


    /** Reads the optional {@code version} key. */
    private static Provenance readProvenance(JsonNode root)
    {
        String version = text(root, "version");
        return version == null ? Provenance.NONE : new Provenance(version);
    }


    /** A non-blank string field, or {@code null}. */
    private static @Nullable String text(JsonNode root, String field)
    {
        if (!root.hasNonNull(field))
        {
            return null;
        }
        String value = root.get(field).asText();
        return value.isBlank() ? null : value;
    }


    /**
     * The {@code levels} index: term keys upper-cased on load (the folded membership index), the
     * values kept verbatim (each value holds the term's preferred case) — both canonicalised
     * through {@code canonical}, the intern map the level-scoped hierarchy shares (R5 PB-M1).
     */
    private static Map<String, Map<String, String>> readLevels(@Nullable JsonNode node,
            Map<String, String> canonical)
    {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        if (node == null || !node.isObject())
        {
            return out;
        }
        for (Map.Entry<String, JsonNode> outer : node.properties())
        {
            Map<String, String> inner = new LinkedHashMap<>();
            JsonNode innerNode = outer.getValue();
            if (innerNode.isObject())
            {
                for (Map.Entry<String, JsonNode> e : innerNode.properties())
                {
                    // a container is no term (B-T lane 1 LOW-4: the streaming reader skips it too)
                    if (!e.getValue().isContainerNode())
                    {
                        inner.put(
                                LevelScopedHierarchy.canonical(canonical,
                                        e.getKey().toUpperCase(Locale.ROOT)),
                                LevelScopedHierarchy.canonical(canonical, e.getValue().asText()));
                    }
                }
            }
            out.put(outer.getKey(), inner);
        }
        return out;
    }


    /**
     * Nested string map read verbatim — keys and values exactly as authored in the file, interned
     * through {@code canonical}; a container value is no entry (the streaming reader skips it too).
     */
    private static Map<String, Map<String, String>> readNestedStringMap(@Nullable JsonNode node,
            Map<String, String> canonical)
    {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        if (node == null || !node.isObject())
        {
            return out;
        }
        for (Map.Entry<String, JsonNode> outer : node.properties())
        {
            Map<String, String> inner = new LinkedHashMap<>();
            JsonNode innerNode = outer.getValue();
            if (innerNode.isObject())
            {
                for (Map.Entry<String, JsonNode> e : innerNode.properties())
                {
                    if (!e.getValue().isContainerNode())
                    {
                        inner.put(LevelScopedHierarchy.canonical(canonical, e.getKey()),
                                LevelScopedHierarchy.canonical(canonical, e.getValue().asText()));
                    }
                }
            }
            out.put(outer.getKey(), inner);
        }
        return out;
    }


    /** Hierarchy read verbatim — term keys and ancestor values exactly as authored. */
    private static Map<String, List<String>> readNestedStringList(@Nullable JsonNode node)
    {
        Map<String, List<String>> out = new LinkedHashMap<>();
        if (node == null || !node.isObject())
        {
            return out;
        }
        for (Map.Entry<String, JsonNode> outer : node.properties())
        {
            List<String> values = new java.util.ArrayList<>();
            if (outer.getValue().isArray())
            {
                for (JsonNode item : outer.getValue())
                {
                    // a container is no ancestor (the streaming reader skips it too)
                    if (!item.isContainerNode())
                    {
                        values.add(item.asText());
                    }
                }
            }
            out.put(outer.getKey(), values);
        }
        return out;
    }


    /**
     * The folded twin of a pair/attribute structure: inner keys upper-cased, values verbatim. On a
     * folded-key collision the last entry wins — the same disposition the pre-#266 load-time
     * folding produced.
     */
    private static Map<String, Map<String, String>> foldKeys(Map<String, Map<String, String>> exact)
    {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> outer : exact.entrySet())
        {
            Map<String, String> inner = new LinkedHashMap<>();
            for (Map.Entry<String, String> e : outer.getValue().entrySet())
            {
                inner.put(upper(e.getKey()), e.getValue());
            }
            out.put(outer.getKey(), inner);
        }
        return out;
    }


    /** The folded twin of the hierarchy: term keys and ancestor values upper-cased. */
    private static Map<String, List<String>> foldHierarchy(Map<String, List<String>> exact)
    {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : exact.entrySet())
        {
            out.put(upper(e.getKey()),
                    e.getValue().stream().map(ValueMapDictionary::upper).toList());
        }
        return out;
    }


    private static String upper(String s)
    {
        return s.toUpperCase(Locale.ROOT);
    }


    /**
     * The stored preferred form of a term at a level — the instance the model holds (tests pin the
     * interning with it).
     *
     * @param aLevel
     *            the level.
     * @param aTerm
     *            the term, any case.
     * @return the stored value, or {@code null}.
     */
    @Nullable
    String preferredForm(String aLevel, String aTerm)
    {
        Map<String, String> m = levels.get(aLevel);
        return m != null ? m.get(upper(aTerm)) : null;
    }


    /** A {@code pairs} registry as held (read-only view; tests pin the interning with it). */
    Map<String, String> pairRegistry(String aRegistry)
    {
        return java.util.Collections.unmodifiableMap(pairs.getOrDefault(aRegistry, Map.of()));
    }


    /** An {@code attributes} map as held (read-only view; tests pin the interning with it). */
    Map<String, String> attributeMap(String aName)
    {
        return java.util.Collections.unmodifiableMap(attributes.getOrDefault(aName, Map.of()));
    }


    /**
     * Whether the file carries the level with at least one term.
     *
     * @param aLevel
     *            the level ({@code PT}, {@code LOINC}, …).
     * @return {@code false} for an absent or empty level — a stale or partial install, which is a
     *         status, not "every value unmapped".
     */
    public boolean hasLevel(String aLevel)
    {
        Map<String, String> m = levels.get(aLevel);
        return m != null && !m.isEmpty();
    }


    /**
     * Membership in exactly the stored spelling (DF4 ruled exact): the level holds the value under
     * its upper-cased key <b>and</b> the stored preferred form equals the value verbatim —
     * {@link #caseMatches} for a named level, without its any-level fallback.
     *
     * @param aLevel
     *            the level.
     * @param aValue
     *            the value as it appears in the data.
     * @return whether the value is a member in the stored spelling.
     */
    public boolean isMember(String aLevel, String aValue)
    {
        Map<String, String> m = levels.get(aLevel);
        if (m == null)
        {
            return false;
        }
        String preferred = m.get(upper(aValue));
        return preferred != null && preferred.equals(aValue);
    }


    /**
     * Whether the level has a decode source: the {@value #DECODES} section holds the level (house
     * format 2), or the level is one a house-format-1 {@code pairs} registry decodes and that
     * registry is present.
     *
     * @param aLevel
     *            the level.
     * @return whether {@link #decode} can answer for the level at all.
     */
    public boolean decodes(String aLevel)
    {
        Map<String, String> v2 = decodes.get(aLevel);
        if (v2 != null && !v2.isEmpty())
        {
            return true;
        }
        Map<String, String> registry = v1Registry(aLevel);
        return registry != null && !registry.isEmpty();
    }


    /**
     * The decode of a code at a code level, exactly as stored, or {@code null} when the code is not
     * a member of the level in the stored spelling or the level's decode source has none for it.
     * The code is taken verbatim: a lower-cased UNII code is not a member and does not decode.
     *
     * @param aLevel
     *            the code level.
     * @param aCode
     *            the code as it appears in the data.
     * @return the decode, or {@code null}.
     */
    public @Nullable String decode(String aLevel, String aCode)
    {
        if (!isMember(aLevel, aCode))
        {
            return null;
        }
        Map<String, String> v2 = decodes.get(aLevel);
        if (v2 != null && !v2.isEmpty())
        {
            return v2.get(aCode);
        }
        Map<String, String> registry = v1Registry(aLevel);
        return registry != null ? registry.get(aCode) : null;
    }


    /**
     * The house-format-1 registry decoding the level, or {@code null} when the table names none.
     */
    private @Nullable Map<String, String> v1Registry(String aLevel)
    {
        String registry = V1_DECODE_REGISTRIES.get(registryType + "/" + aLevel);
        return registry != null ? pairs.get(registry) : null;
    }


    /**
     * Refuses an engine-only lookup on a {@link Profile#FORMAT} load: its sections are not held,
     * and an empty answer would read as "not on the path" / "no decode" — a silent false verdict.
     */
    private void requireFull(String aLookup)
    {
        if (profile != Profile.FULL)
        {
            throw new IllegalStateException(aLookup + " needs a FULL load; " + this
                    + " was loaded with the FORMAT profile (no hierarchy, attributes or other pairs)");
        }
    }


    @Override
    public String toString()
    {
        return "ValueMapDictionary(" + (type.isEmpty() ? registryType : type)
                + (getVersion() != null ? " " + getVersion() : "") + ", house format " + houseFormat
                + ", " + profile + ")";
    }


    /** Membership: the term (case-folded) is present in the given level. */
    public boolean isValidTerm(@Nullable String level, @Nullable String term)
    {
        if (term == null)
        {
            return false;
        }
        if (level != null && !level.isEmpty())
        {
            Map<String, String> m = levels.get(level);
            return m != null && m.containsKey(upper(term));
        }
        // No level specified: valid if present in any level.
        for (Map<String, String> m : levels.values())
        {
            if (m.containsKey(upper(term)))
            {
                return true;
            }
        }
        return false;
    }


    /**
     * Case check: the term exactly matches the stored preferred case for its level — the
     * case-SENSITIVE membership primitive (the D-TA-3 default for the membership operations).
     */
    public boolean caseMatches(@Nullable String level, @Nullable String term)
    {
        if (term == null)
        {
            return false;
        }
        Map<String, String> m = level != null && !level.isEmpty() ? levels.get(level) : anyLevel();
        if (m == null)
        {
            return false;
        }
        String preferred = m.get(upper(term));
        return preferred != null && preferred.equals(term);
    }


    private @Nullable Map<String, String> anyLevel()
    {
        return levels.values().stream().findFirst().orElse(null);
    }


    /**
     * Whether this file carries the level-scoped hierarchy ({@code hierarchy_by_level}, runbook W2
     * DI1) at all — {@code false} for a file converted before DI1, which answers no hierarchy since
     * runbook W4 DI2 (the file stays loaded for every other lookup).
     *
     * @return whether the section is present and non-empty
     */
    public boolean hasHierarchyByLevel()
    {
        requireFull("hasHierarchyByLevel");
        return !hierarchyByLevel.isEmpty();
    }


    /**
     * Whether a hierarchy lookup whose CHILD column reads {@code childLevel} can be answered
     * (runbook W4 DI2): the level-scoped section carries that level. A file without the section
     * answers no level; a converted file answers PT, HLT, HLGT and their codes, never LLT / LLTCD
     * (PB-M1). The rule runner SKIPs a hierarchy rule this answers {@code false} for, with the
     * reason, before any row is read.
     *
     * @param childLevel
     *            the house-format level the child column reads ({@code PT}, {@code HLTCD}, ...)
     * @return whether {@link #onHierarchyPath} can answer at that level
     */
    public boolean answersHierarchyAt(String childLevel)
    {
        requireFull("answersHierarchyAt");
        return hierarchyByLevel.hasLevel(childLevel);
    }


    /**
     * Hierarchy-path of a NON-MedDRA type, exactly as before runbook W4 (W4 fold, R5 P3-L3): the
     * level-scoped section where it carries the child's level (no converter but MedDRA's writes
     * one), else the bare-name map — {@code parent} an ancestor of {@code term}, verbatim when
     * {@code caseSensitive} (the D-TA-3 default), both sides folded otherwise. A MedDRA file holds
     * no bare map ({@code RuntimeDictionaryProvider.levelScopedHierarchy} routes it to
     * {@link #onHierarchyPath(String, String, String, String, boolean)}).
     *
     * @param termLevel
     *            the child column's level, or {@code null}
     * @param term
     *            the child value
     * @param parentLevel
     *            the parent column's level, or {@code null}
     * @param parent
     *            the parent value
     * @param caseSensitive
     *            verbatim (default) or folded
     * @return whether {@code parent} lies on {@code term}'s path
     */
    public boolean onHierarchyPathAsBefore(@Nullable String termLevel, @Nullable String term,
            @Nullable String parentLevel, @Nullable String parent, boolean caseSensitive)
    {
        requireFull("onHierarchyPathAsBefore");
        if (termLevel != null && parentLevel != null && hierarchyByLevel.hasLevel(termLevel))
        {
            return onHierarchyPath(termLevel, term, parentLevel, parent, caseSensitive);
        }
        if (term == null || parent == null)
        {
            return false;
        }
        List<String> parents = caseSensitive ? hierarchy.get(term)
                : hierarchyFolded.get(upper(term));
        return parents != null && parents.contains(caseSensitive ? parent : upper(parent));
    }


    /**
     * Hierarchy-path keyed by the <b>levels of the two variables read</b> (runbook W2 DI1,
     * {@code PLAN-engine-gap-dict-unsupported} §4.1): answered at {@code termLevel} /
     * {@code parentLevel} only ({@link LevelScopedHierarchy}) — a PT name that is also an HLT or
     * LLT name reads its own level's entry. ⛔ <b>No bare-name fallback</b> (runbook W4 DI2, Q3): a
     * file without the section, or a section without the child's level, is not on the path for any
     * row — reaching here for one is a gate hole ({@link #answersHierarchyAt} is the gate the
     * runner reads, and {@code DictionaryFunctions.hierarchy} refuses loudly before asking).
     */
    public boolean onHierarchyPath(@Nullable String termLevel, @Nullable String term,
            @Nullable String parentLevel, @Nullable String parent, boolean caseSensitive)
    {
        requireFull("onHierarchyPath");
        return termLevel != null && parentLevel != null && term != null && parent != null
                && hierarchyByLevel.onPath(termLevel, term, parentLevel, parent, caseSensitive);
    }


    /**
     * code&harr;decode pairing: whether some registry (in {@code pairs}) or attribute map (in
     * {@code attributes}) maps {@code code} to {@code decode}. When {@code reg} names a registry it
     * is consulted first; otherwise every pair / attribute map is searched. When
     * {@code caseSensitive} (the D-TA-3 default) code and decode both compare verbatim; with
     * {@code case_sensitive: false} both compare case-folded (pre-#266 the code folded while the
     * decode compared verbatim — an asymmetry no rule authored and no flag could express).
     */
    public boolean codeDecodePair(@Nullable String reg, @Nullable String code,
            @Nullable String decode, boolean caseSensitive)
    {
        requireFull("codeDecodePair");
        if (code == null || decode == null)
        {
            return false;
        }
        Map<String, Map<String, String>> p = caseSensitive ? pairs : pairsFolded;
        Map<String, Map<String, String>> a = caseSensitive ? attributes : attributesFolded;
        String key = caseSensitive ? code : upper(code);
        // The named registry is a *preference*: consult it first, but on a miss fall through to
        // scanning every other pairs registry AND the attributes maps (M1). Mirrors Python
        // is_valid_code_term_pair, which always iterates `pairs` + `attributes`.
        if (reg != null && p.containsKey(reg)
                && decodeMatches(decode, p.get(reg).get(key), caseSensitive))
        {
            return true;
        }
        for (Map<String, String> m : p.values())
        {
            if (decodeMatches(decode, m.get(key), caseSensitive))
            {
                return true;
            }
        }
        for (Map<String, String> m : a.values())
        {
            if (decodeMatches(decode, m.get(key), caseSensitive))
            {
                return true;
            }
        }
        return false;
    }


    private static boolean decodeMatches(String decode, @Nullable String stored,
            boolean caseSensitive)
    {
        return stored != null
                && (caseSensitive ? stored.equals(decode) : stored.equalsIgnoreCase(decode));
    }


    /**
     * E8 — decode-presence: whether some registry (in {@code pairs}) or attribute map (in
     * {@code attributes}) holds <em>any</em> decode for {@code code} — the {@code containsKey}
     * counterpart of {@link #codeDecodePair} minus the decode-equality. When {@code reg} names a
     * {@code pairs} registry it is consulted first; otherwise every {@code pairs} / {@code
     * attributes} map is searched. When {@code caseSensitive} (the D-TA-3 default) the code
     * compares verbatim; {@code case_sensitive: false} folds it. A dummy dictionary with no
     * {@code pairs}/{@code attributes} always returns {@code false} (parity holds; real data drops
     * in behind the same format).
     */
    public boolean hasDecode(@Nullable String reg, @Nullable String code, boolean caseSensitive)
    {
        requireFull("hasDecode");
        if (code == null)
        {
            return false;
        }
        Map<String, Map<String, String>> p = caseSensitive ? pairs : pairsFolded;
        Map<String, Map<String, String>> a = caseSensitive ? attributes : attributesFolded;
        String key = caseSensitive ? code : upper(code);
        if (reg != null && p.containsKey(reg) && p.get(reg).containsKey(key))
        {
            return true;
        }
        for (Map<String, String> m : p.values())
        {
            if (m.containsKey(key))
            {
                return true;
            }
        }
        for (Map<String, String> m : a.values())
        {
            if (m.containsKey(key))
            {
                return true;
            }
        }
        return false;
    }

}
