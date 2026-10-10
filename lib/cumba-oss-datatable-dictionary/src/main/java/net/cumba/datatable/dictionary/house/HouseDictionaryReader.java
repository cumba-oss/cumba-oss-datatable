package net.cumba.datatable.dictionary.house;

// OSS-IDENTITY datatable-dictionary: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import org.jspecify.annotations.Nullable;

/**
 * Reads a house-format dictionary file (house format 1 or 2) into a {@link ValueMapDictionary} of
 * the {@link ValueMapDictionary.Profile#FORMAT FORMAT} profile with the streaming JSON parser: a
 * multi-MB MedDRA file is never held as a tree, and every term is interned through one map while it
 * streams past (memory first). A file whose name ends {@code .gz} is decompressed on the way in.
 * <p>
 * Like the engine's reader, an absent, {@code null} or mis-shaped section degrades to empty
 * <b>silently</b>; the file's emptiness is then {@link ValueMapDictionary#hasContent()}'s business.
 * Unknown top-level keys are skipped, so a file written by a newer installer still loads its known
 * sections.
 * </p>
 * <p>
 * <b>The format subset</b> (DF25 as amended by owner ruling OQ2, 2026-10-10): only what the format
 * path reads is held — {@code levels}, the {@code pairs} registries of the house-format-1 decode
 * table ({@code ValueMapDictionary.V1_REGISTRY_NAMES}) and {@code decodes}. The bare-name
 * {@code hierarchy}, {@code hierarchy_by_level}, {@code attributes} and every other {@code pairs}
 * registry ({@code whodrug}, {@code medrt-base}, …) are walked token by token while streaming and
 * reduced to one boolean each — whether the engine's reader would have kept an entry — so
 * {@link ValueMapDictionary#hasContent()} stays the full load's without holding a single term of
 * them. A {@link ValueMapDictionary.Profile#FULL FULL} load is {@link ValueMapDictionary#load}'s
 * (the engine's tree parse); the two answer the format-path read API identically (pinned by
 * {@code ProfileEquivalenceTest}).
 * </p>
 */
public final class HouseDictionaryReader
{

    private static final JsonFactory FACTORY = new JsonFactory();

    private static final int BUFFER = 1 << 16;

    /** The header fields the installer writes before the sections. */
    private static final Set<String> HEADER_FIELD_NAMES = Set.of("type", "version", "source",
            "retrieved", ValueMapDictionary.HOUSE_FORMAT);

    private static final int HEADER_FIELDS = HEADER_FIELD_NAMES.size();

    /**
     * The header fields of a house file, as {@link #read} reads them.
     *
     * @param type
     *            the declared type, empty when none.
     * @param version
     *            the declared version, or {@code null}.
     * @param retrieved
     *            the installer's {@code retrieved} date, or {@code null}.
     * @param houseFormat
     *            the house-format version ({@code ValueMapDictionary.HOUSE_FORMAT_1} without the
     *            field).
     * @param source
     *            the raw distribution's provenance the installer recorded, or {@code null}.
     */
    public record Header(String type, @Nullable String version, @Nullable String retrieved,
            int houseFormat, @Nullable String source)
    {
    }

    private HouseDictionaryReader()
    {
    }


    /**
     * Reads the file.
     *
     * @param aFile
     *            the {@code <type>.json} or {@code <type>.json.gz} file.
     * @return the dictionary.
     * @throws IOException
     *             when the file cannot be read or is not a JSON object.
     */
    public static ValueMapDictionary read(Path aFile) throws IOException
    {
        try (InputStream in = open(aFile); JsonParser p = FACTORY.createParser(in))
        {
            return read(p, aFile);
        }
    }


    /**
     * Reads only the header fields of the file: every section is skipped unread (no model, no
     * interning).
     *
     * @param aFile
     *            the {@code <type>.json} or {@code <type>.json.gz} file.
     * @return the header; the same values {@link #read} would give the dictionary.
     * @throws IOException
     *             when the file cannot be read or is not a JSON object.
     */
    public static Header readHeader(Path aFile) throws IOException
    {
        try (InputStream in = open(aFile); JsonParser p = FACTORY.createParser(in))
        {
            if (p.nextToken() != JsonToken.START_OBJECT)
            {
                throw new IOException(aFile + " is not a house-format dictionary (no JSON object)");
            }
            String type = null;
            String version = null;
            String retrieved = null;
            String source = null;
            int houseFormat = ValueMapDictionary.HOUSE_FORMAT_1;
            // Since the B-T fold the installer writes the five header fields FIRST, so the read
            // stops once all five have been seen (B-T review lane 2 MED-2: a whole-file walk took
            // 0.7 s for MedDRA, 1.4 s for WHODrug). A file of house format 1 has no house_format
            // field, and an earlier format-2 install appended the header after the sections: those
            // are read to the end, skipping every section unread — the same values either way.
            Set<String> seen = new HashSet<>();
            while (seen.size() < HEADER_FIELDS && p.nextToken() == JsonToken.FIELD_NAME)
            {
                String field = p.currentName();
                JsonToken value = p.nextToken();
                switch (field)
                {
                case "type" -> type = textOrEmpty(p, value);
                case "version" -> version = textOrNull(p, value);
                case "retrieved" -> retrieved = textOrNull(p, value);
                case "source" -> source = textOrNull(p, value);
                case ValueMapDictionary.HOUSE_FORMAT -> houseFormat = houseFormat(p, value);
                default -> p.skipChildren();
                }
                if (HEADER_FIELD_NAMES.contains(field))
                {
                    seen.add(field);
                }
            }
            return new Header(type != null ? type : "", version, retrieved, houseFormat, source);
        }
    }


    private static InputStream open(Path aFile) throws IOException
    {
        InputStream raw = new BufferedInputStream(Files.newInputStream(aFile), BUFFER);
        Path name = aFile.getFileName();
        if (name == null || !name.toString().endsWith(".gz"))
        {
            return raw;
        }
        try
        {
            return new GZIPInputStream(raw, BUFFER);
        }
        catch (IOException e)
        {
            raw.close();
            throw e;
        }
    }


    private static ValueMapDictionary read(JsonParser aParser, Path aFile) throws IOException
    {
        if (aParser.nextToken() != JsonToken.START_OBJECT)
        {
            throw new IOException(aFile + " is not a house-format dictionary (no JSON object)");
        }
        String type = "";
        String version = null;
        String retrieved = null;
        String source = null;
        int houseFormat = ValueMapDictionary.HOUSE_FORMAT_1;
        Map<String, Map<String, String>> levels = new LinkedHashMap<>();
        Map<String, Map<String, String>> pairs = new LinkedHashMap<>();
        Map<String, Map<String, String>> decodes = new LinkedHashMap<>();
        Map<String, String> canonical = new HashMap<>();
        // the skipped sections, reduced to "would the engine's reader have kept an entry"
        boolean skippedContent = false;
        while (aParser.nextToken() == JsonToken.FIELD_NAME)
        {
            String field = aParser.currentName();
            JsonToken value = aParser.nextToken();
            switch (field)
            {
            case "type" -> type = textOrEmpty(aParser, value);
            case "version" -> version = textOrNull(aParser, value);
            case "retrieved" -> retrieved = textOrNull(aParser, value);
            case "source" -> source = textOrNull(aParser, value);
            case ValueMapDictionary.HOUSE_FORMAT -> houseFormat = houseFormat(aParser, value);
            case "levels" -> readNestedMap(aParser, value, levels, canonical, true);
            case "hierarchy" -> skippedContent |= skipStringLists(aParser, value);
            case ValueMapDictionary.HIERARCHY_BY_LEVEL -> skippedContent |= skipByLevel(aParser,
                    value);
            case "pairs" -> skippedContent |= readPairs(aParser, value, pairs, canonical);
            case "attributes" -> skippedContent |= skipNestedMap(aParser, value);
            case ValueMapDictionary.DECODES -> readNestedMap(aParser, value, decodes, canonical,
                    false);
            default -> aParser.skipChildren();
            }
        }
        ValueMapDictionary.Provenance provenance = version == null
                ? ValueMapDictionary.Provenance.NONE
                : new ValueMapDictionary.Provenance(version);
        return ValueMapDictionary.formatSubset(
                new ValueMapDictionary.Header(type, type.isEmpty() ? fileStem(aFile) : type,
                        provenance, source, retrieved, houseFormat),
                levels, pairs, decodes, skippedContent);
    }


    /**
     * The type a file declaring none is keyed by — its file stem ({@code loinc.json.gz} →
     * {@code loinc}), as the engine keys it; the store reader only ever binds
     * {@code <type>.json[.gz]}, so the stem is the type it bound the file as.
     */
    static String fileStem(Path aFile)
    {
        Path name = aFile.getFileName();
        String n = name == null ? "" : name.toString().toLowerCase(Locale.ROOT);
        if (n.endsWith(".gz"))
        {
            n = n.substring(0, n.length() - ".gz".length());
        }
        return n.endsWith(".json") ? n.substring(0, n.length() - ".json".length()) : n;
    }


    private static String textOrEmpty(JsonParser aParser, JsonToken aToken) throws IOException
    {
        String s = textOrNull(aParser, aToken);
        return s != null ? s : "";
    }


    private static @Nullable String textOrNull(JsonParser aParser, JsonToken aToken)
        throws IOException
    {
        if (aToken == JsonToken.VALUE_STRING || aToken == JsonToken.VALUE_NUMBER_INT
                || aToken == JsonToken.VALUE_NUMBER_FLOAT)
        {
            String s = aParser.getText();
            return s.isBlank() ? null : s;
        }
        if (aToken.isStructStart())
        {
            aParser.skipChildren();
        }
        return null;
    }


    private static int houseFormat(JsonParser aParser, JsonToken aToken) throws IOException
    {
        if (aToken == JsonToken.VALUE_NUMBER_INT)
        {
            return aParser.getIntValue();
        }
        if (aToken == JsonToken.VALUE_STRING)
        {
            try
            {
                return Integer.parseInt(aParser.getText().trim());
            }
            catch (NumberFormatException _)
            {
                return ValueMapDictionary.HOUSE_FORMAT_1;
            }
        }
        if (aToken.isStructStart())
        {
            aParser.skipChildren();
        }
        return ValueMapDictionary.HOUSE_FORMAT_1;
    }


    /** The term, interned: one instance per spelling for the whole file. */
    static String canonical(Map<String, String> aCanonical, String aText)
    {
        String prev = aCanonical.putIfAbsent(aText, aText);
        return prev != null ? prev : aText;
    }


    /**
     * {@code outer → inner key → value}: {@code levels} with the inner keys upper-cased (the
     * engine's folded membership index), every other section verbatim.
     */
    private static void readNestedMap(JsonParser aParser, JsonToken aToken,
            Map<String, Map<String, String>> aOut, Map<String, String> aCanonical,
            boolean aUpperKeys)
        throws IOException
    {
        if (aToken != JsonToken.START_OBJECT)
        {
            skipValue(aParser, aToken);
            return;
        }
        while (aParser.nextToken() == JsonToken.FIELD_NAME)
        {
            String outer = aParser.currentName();
            Map<String, String> inner = new HashMap<>();
            JsonToken innerToken = aParser.nextToken();
            if (innerToken == JsonToken.START_OBJECT)
            {
                while (aParser.nextToken() == JsonToken.FIELD_NAME)
                {
                    String key = aParser.currentName();
                    JsonToken valueToken = aParser.nextToken();
                    if (valueToken.isStructStart())
                    {
                        aParser.skipChildren();
                        continue;
                    }
                    String value = aParser.getText();
                    inner.put(
                            canonical(aCanonical, aUpperKeys ? key.toUpperCase(Locale.ROOT) : key),
                            canonical(aCanonical, value));
                }
            }
            else
            {
                skipValue(aParser, innerToken);
            }
            aOut.put(outer, inner);
        }
    }


    /**
     * {@code pairs}: a registry of the house-format-1 decode table is read as
     * {@link #readNestedMap} reads it; every other registry is skipped unread.
     *
     * @return whether a skipped registry held an entry (the engine's {@code anyEntry}).
     */
    private static boolean readPairs(JsonParser aParser, JsonToken aToken,
            Map<String, Map<String, String>> aOut, Map<String, String> aCanonical)
        throws IOException
    {
        if (aToken != JsonToken.START_OBJECT)
        {
            skipValue(aParser, aToken);
            return false;
        }
        boolean skippedContent = false;
        while (aParser.nextToken() == JsonToken.FIELD_NAME)
        {
            String registry = aParser.currentName();
            JsonToken innerToken = aParser.nextToken();
            if (!ValueMapDictionary.V1_REGISTRY_NAMES.contains(registry))
            {
                skippedContent |= skipInner(aParser, innerToken);
                continue;
            }
            Map<String, String> inner = new HashMap<>();
            if (innerToken == JsonToken.START_OBJECT)
            {
                while (aParser.nextToken() == JsonToken.FIELD_NAME)
                {
                    String key = aParser.currentName();
                    JsonToken valueToken = aParser.nextToken();
                    if (valueToken.isStructStart())
                    {
                        aParser.skipChildren();
                        continue;
                    }
                    inner.put(canonical(aCanonical, key), canonical(aCanonical, aParser.getText()));
                }
            }
            else
            {
                skipValue(aParser, innerToken);
            }
            aOut.put(registry, inner);
        }
        return skippedContent;
    }


    /**
     * Skips an {@code outer → inner key → scalar} section unread.
     *
     * @return whether {@link #readNestedMap} would have kept an entry: some inner object holds a
     *         scalar value.
     */
    private static boolean skipNestedMap(JsonParser aParser, JsonToken aToken) throws IOException
    {
        if (aToken != JsonToken.START_OBJECT)
        {
            skipValue(aParser, aToken);
            return false;
        }
        boolean content = false;
        while (aParser.nextToken() == JsonToken.FIELD_NAME)
        {
            content |= skipInner(aParser, aParser.nextToken());
        }
        return content;
    }


    /** Skips one inner object; whether it holds a scalar value (a nested value is no entry). */
    private static boolean skipInner(JsonParser aParser, JsonToken aToken) throws IOException
    {
        if (aToken != JsonToken.START_OBJECT)
        {
            skipValue(aParser, aToken);
            return false;
        }
        boolean content = false;
        while (aParser.nextToken() == JsonToken.FIELD_NAME)
        {
            JsonToken valueToken = aParser.nextToken();
            if (valueToken.isStructStart())
            {
                aParser.skipChildren();
            }
            else
            {
                content = true;
            }
        }
        return content;
    }


    /**
     * Skips the bare-name {@code term → [ancestor, …]} hierarchy unread.
     *
     * @return whether some term has an ancestor list with a scalar element (the engine's non-empty
     *         list).
     */
    private static boolean skipStringLists(JsonParser aParser, JsonToken aToken) throws IOException
    {
        if (aToken != JsonToken.START_OBJECT)
        {
            skipValue(aParser, aToken);
            return false;
        }
        boolean content = false;
        while (aParser.nextToken() == JsonToken.FIELD_NAME)
        {
            JsonToken listToken = aParser.nextToken();
            if (listToken != JsonToken.START_ARRAY)
            {
                skipValue(aParser, listToken);
                continue;
            }
            JsonToken t;
            while ((t = aParser.nextToken()) != JsonToken.END_ARRAY)
            {
                if (t.isStructStart())
                {
                    aParser.skipChildren();
                }
                else
                {
                    content = true;
                }
            }
        }
        return content;
    }


    /**
     * Skips {@code level → term → ancestor level → [ancestor, …]} unread.
     *
     * @return whether some term names an ancestor level with at least one scalar ancestor — the
     *         entry {@code LevelScopedHierarchy.read} keeps (B-T lane 1 LOW-4: a term mapping to an
     *         empty object, or to lists of containers only, is no entry in either profile).
     */
    private static boolean skipByLevel(JsonParser aParser, JsonToken aToken) throws IOException
    {
        if (aToken != JsonToken.START_OBJECT)
        {
            skipValue(aParser, aToken);
            return false;
        }
        boolean content = false;
        while (aParser.nextToken() == JsonToken.FIELD_NAME)
        {
            JsonToken levelToken = aParser.nextToken();
            if (levelToken != JsonToken.START_OBJECT)
            {
                skipValue(aParser, levelToken);
                continue;
            }
            while (aParser.nextToken() == JsonToken.FIELD_NAME)
            {
                JsonToken termToken = aParser.nextToken();
                if (termToken != JsonToken.START_OBJECT)
                {
                    skipValue(aParser, termToken);
                    continue;
                }
                while (aParser.nextToken() == JsonToken.FIELD_NAME)
                {
                    content |= skipScalarList(aParser, aParser.nextToken());
                }
            }
        }
        return content;
    }


    /** Skips one value; whether it is an array holding a scalar element. */
    private static boolean skipScalarList(JsonParser aParser, JsonToken aToken) throws IOException
    {
        if (aToken != JsonToken.START_ARRAY)
        {
            skipValue(aParser, aToken);
            return false;
        }
        boolean content = false;
        JsonToken t;
        while ((t = aParser.nextToken()) != JsonToken.END_ARRAY)
        {
            if (t.isStructStart())
            {
                aParser.skipChildren();
            }
            else
            {
                content = true;
            }
        }
        return content;
    }


    private static void skipValue(JsonParser aParser, JsonToken aToken) throws IOException
    {
        if (aToken.isStructStart())
        {
            aParser.skipChildren();
        }
    }
}
