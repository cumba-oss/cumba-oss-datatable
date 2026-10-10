package net.cumba.datatable.dictionary.house;

// OSS-IDENTITY datatable-dictionary: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import org.jspecify.annotations.Nullable;

/**
 * The variable → dictionary level table of the format path (DF3 (a), DF22), read from the resource
 * {@code dictionary-levels.properties} beside this class: per type, the suffix rows (a column named
 * {@code <2-character domain prefix><SUFFIX>}, matched case-insensitively, longest suffix first),
 * the explicit full-name rows, and the default level of a single-level type.
 * <p>
 * ⚠ The resource is read through {@code DictionaryLevels.class.getResourceAsStream} — this class's
 * own loader — never the thread's context class loader: the unmapped-value scan runs the resolution
 * on common-pool workers, whose context loader can be the platform loader or {@code null} under a
 * launcher (plan D4; the engine's {@code DictionaryLicences} reads its resources the same way).
 * </p>
 */
public final class DictionaryLevels
{

    /** The resource holding the table, beside this class. */
    public static final String RESOURCE = "dictionary-levels.properties";

    private static final int DOMAIN_PREFIX_LENGTH = 2;

    /** One type's rows. */
    private static final class TypeRows
    {

        /** Suffix (upper-cased) → level, iterated longest first. */
        final List<Map.Entry<String, String>> suffixes = new ArrayList<>();

        /** Full column name (upper-cased) → level. */
        final Map<String, String> names = new HashMap<>();

        @Nullable
        String defaultLevel;
    }

    private static final Map<String, TypeRows> ROWS = load();

    private DictionaryLevels()
    {
    }


    private static Map<String, TypeRows> load()
    {
        Properties props = new Properties();
        try (InputStream in = DictionaryLevels.class.getResourceAsStream(RESOURCE))
        {
            if (in == null)
            {
                throw new IllegalStateException(
                        RESOURCE + " missing beside " + DictionaryLevels.class);
            }
            props.load(in);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
        Map<String, TypeRows> rows = new HashMap<>();
        for (String key : props.stringPropertyNames())
        {
            String level = props.getProperty(key).trim();
            String[] parts = key.split("\\.", 3);
            TypeRows tr = rows.computeIfAbsent(parts[0].toLowerCase(Locale.ROOT),
                    _ -> new TypeRows());
            if (parts.length == 2 && "default".equals(parts[1]))
            {
                tr.defaultLevel = level;
            }
            else if (parts.length == 3 && "suffix".equals(parts[1]))
            {
                tr.suffixes.add(Map.entry(parts[2].toUpperCase(Locale.ROOT), level));
            }
            else if (parts.length == 3 && "name".equals(parts[1]))
            {
                tr.names.put(parts[2].toUpperCase(Locale.ROOT), level);
            }
            else
            {
                throw new IllegalStateException(RESOURCE + ": unknown row " + key);
            }
        }
        for (TypeRows tr : rows.values())
        {
            tr.suffixes.sort(
                    Comparator.comparingInt((Map.Entry<String, String> e) -> -e.getKey().length())
                            .thenComparing(Map.Entry::getKey));
        }
        return Collections.unmodifiableMap(rows);
    }


    /**
     * The dictionary level a column reads.
     *
     * @param aType
     *            the house type ({@code meddra}, …).
     * @param aColumnName
     *            the column's name.
     * @return the level, or {@code null} when the table has no row for the column (no guess).
     */
    public static @Nullable String levelOf(String aType, @Nullable String aColumnName)
    {
        TypeRows tr = ROWS.get(aType.toLowerCase(Locale.ROOT));
        if (tr == null || aColumnName == null)
        {
            return null;
        }
        String name = aColumnName.trim().toUpperCase(Locale.ROOT);
        String explicit = tr.names.get(name);
        if (explicit != null)
        {
            return explicit;
        }
        for (Map.Entry<String, String> row : tr.suffixes)
        {
            String suffix = row.getKey();
            if (name.length() == DOMAIN_PREFIX_LENGTH + suffix.length() && name.endsWith(suffix))
            {
                return row.getValue();
            }
        }
        return tr.defaultLevel;
    }


    /**
     * The suffix rows of a type, longest suffix first ({@code AEHLGTCD} is {@code HLGTCD}, never
     * {@code HLGT}): suffix &rarr; level. The conformance engine's hierarchy function reads the
     * MedDRA rows from here (PLAN-dictionary-backed-formats B3), minus the rows it deliberately
     * excludes ({@code --BODSYS} may carry a secondary SOC — a hierarchy-only exclusion).
     *
     * @param aType
     *            the house type.
     * @return the rows, empty for a type without suffix rows.
     */
    public static List<Map.Entry<String, String>> suffixRowsOf(String aType)
    {
        TypeRows tr = ROWS.get(aType.toLowerCase(Locale.ROOT));
        return tr == null ? List.of() : List.copyOf(tr.suffixes);
    }


    /**
     * The level every column of a single-level type resolves to (DF22), or {@code null}.
     *
     * @param aType
     *            the house type.
     * @return the default level, or {@code null}.
     */
    public static @Nullable String defaultLevelOf(String aType)
    {
        TypeRows tr = ROWS.get(aType.toLowerCase(Locale.ROOT));
        return tr != null ? tr.defaultLevel : null;
    }


    /**
     * Every level the table can resolve a column of the type to (the roster a test pins; never a
     * dictionary's content).
     *
     * @param aType
     *            the house type.
     * @return the distinct levels, in table order.
     */
    public static List<String> levelsOf(String aType)
    {
        TypeRows tr = ROWS.get(aType.toLowerCase(Locale.ROOT));
        if (tr == null)
        {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> row : tr.suffixes)
        {
            if (!out.contains(row.getValue()))
            {
                out.add(row.getValue());
            }
        }
        for (String level : tr.names.values())
        {
            if (!out.contains(level))
            {
                out.add(level);
            }
        }
        if (tr.defaultLevel != null && !out.contains(tr.defaultLevel))
        {
            out.add(tr.defaultLevel);
        }
        return out;
    }
}
