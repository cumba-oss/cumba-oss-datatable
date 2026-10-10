package net.cumba.datatable.dictionary.house;

// OSS-IDENTITY datatable-dictionary: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The per-type ordering of installed version tokens (DF1a, owner 2026-10-09): a blank define
 * {@code @Version} binds the <b>most recent</b> installed version of the type, so the tokens the
 * converters write must be ordered — never parsed anywhere else (the store binds by equality).
 * <table>
 * <caption>version tokens per type</caption>
 * <tr>
 * <th>type</th>
 * <th>token the converter writes</th>
 * <th>ordered as</th>
 * </tr>
 * <tr>
 * <td>meddra, loinc</td>
 * <td>{@code 27.0}, {@code 2.80}</td>
 * <td>numeric {@code major.minor}</td>
 * </tr>
 * <tr>
 * <td>whodrug</td>
 * <td>{@code SEP_2020}</td>
 * <td>year, then month</td>
 * </tr>
 * <tr>
 * <td>snomed</td>
 * <td>{@code 20240901}</td>
 * <td>date</td>
 * </tr>
 * <tr>
 * <td>neoplasm</td>
 * <td>{@code 2024-03-29}</td>
 * <td>date</td>
 * </tr>
 * <tr>
 * <td>medrt</td>
 * <td>{@code 2026.07.06} (or the DTS version text)</td>
 * <td>date, else dotted number</td>
 * </tr>
 * <tr>
 * <td>unii</td>
 * <td>{@code 4Aug2026}</td>
 * <td>date</td>
 * </tr>
 * </table>
 * <p>
 * A token the type's rule cannot parse ranks <b>below</b> every token it can (a directory the
 * installer did not name in the type's vocabulary is not a release of it), and among themselves
 * such tokens are ordered by the install date — the {@code retrieved} field the installer writes
 * into every house file (per version; {@code SOURCES.md} keeps one section per type, so it is not
 * the source) else the directory's modification time — then by token text; so the answer is always
 * defined. Ties of parsable tokens are broken the same way.
 * </p>
 */
public final class DictionaryVersions
{

    /** One installed version: its token and the install date, read only when the order needs it. */
    public record Installed(String token, Function<String, @Nullable LocalDate> installDate)
    {
    }

    /**
     * The key of a token the type's rule cannot parse (a zero-length array, never {@code null}).
     */
    static final long[] UNPARSEABLE = new long[0];

    private static final Pattern DOTTED = Pattern.compile("\\d{1,9}(\\.\\d{1,9})*");

    private static final Pattern DOT = Pattern.compile("\\.");

    private static final Pattern MON_YYYY = Pattern.compile("([A-Za-z]{3})_(\\d{4})");

    private static final Pattern YYYYMMDD = Pattern.compile("(\\d{4})(\\d{2})(\\d{2})");

    private static final Pattern ISO_DATE = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})");

    private static final Pattern DOTTED_DATE = Pattern.compile("(\\d{4})\\.(\\d{2})\\.(\\d{2})");

    private static final Pattern D_MMM_YYYY = Pattern.compile("(\\d{1,2})([A-Za-z]{3})(\\d{4})");

    private DictionaryVersions()
    {
    }


    /**
     * The most recent of the installed versions of a type.
     *
     * @param aType
     *            the house type.
     * @param aInstalled
     *            the installed versions; empty ⇒ {@code null}.
     * @return the most recent token, or {@code null} when nothing is installed.
     */
    public static @Nullable String mostRecent(String aType, List<Installed> aInstalled)
    {
        List<String> ordered = order(aType, aInstalled);
        return ordered.isEmpty() ? null : ordered.get(0);
    }


    /**
     * The installed versions of a type, most recent first.
     *
     * @param aType
     *            the house type.
     * @param aInstalled
     *            the installed versions.
     * @return the tokens, most recent first.
     */
    public static List<String> order(String aType, List<Installed> aInstalled)
    {
        List<Keyed> keyed = new ArrayList<>(aInstalled.size());
        for (Installed i : aInstalled)
        {
            keyed.add(new Keyed(i.token(), parse(aType, i.token()), i));
        }
        keyed.sort(Comparator.<Keyed> naturalOrder().reversed());
        List<String> out = new ArrayList<>(keyed.size());
        for (Keyed k : keyed)
        {
            out.add(k.token);
        }
        return out;
    }


    /**
     * The sortable key of a token in the type's vocabulary: the numeric parts of a dotted number,
     * or a date as its epoch day; {@link #UNPARSEABLE} (zero-length) when the token is not in the
     * vocabulary.
     *
     * @param aType
     *            the house type.
     * @param aToken
     *            the version token.
     * @return the key, or {@link #UNPARSEABLE}.
     */
    static long[] parse(String aType, String aToken)
    {
        String token = aToken.trim();
        return switch (aType.toLowerCase(Locale.ROOT))
        {
        case "meddra", "loinc" -> dotted(token);
        case "whodrug" -> monYear(token);
        case "snomed" -> date(YYYYMMDD, token);
        case "neoplasm" -> date(ISO_DATE, token);
        case "medrt" ->
        {
            long[] d = date(DOTTED_DATE, token);
            yield d.length > 0 ? d : dotted(token);
        }
        case "unii" -> dayMonYear(token);
        default -> UNPARSEABLE;
        };
    }


    private static long[] monYear(String aToken)
    {
        Matcher m = MON_YYYY.matcher(aToken);
        if (!m.matches())
        {
            return UNPARSEABLE;
        }
        Month month = month(m.group(1));
        return month == null ? UNPARSEABLE : new long[]
        {
                Long.parseLong(m.group(2)), month.getValue()
        };
    }


    private static long[] dayMonYear(String aToken)
    {
        Matcher m = D_MMM_YYYY.matcher(aToken);
        if (!m.matches())
        {
            return UNPARSEABLE;
        }
        Month month = month(m.group(2));
        return month == null ? UNPARSEABLE
                : epochDay(Integer.parseInt(m.group(3)), month.getValue(),
                        Integer.parseInt(m.group(1)));
    }


    private static long[] dotted(String aToken)
    {
        if (!DOTTED.matcher(aToken).matches())
        {
            return UNPARSEABLE;
        }
        String[] parts = DOT.split(aToken, -1);
        long[] out = new long[parts.length];
        for (int i = 0; i < parts.length; i++)
        {
            out[i] = Long.parseLong(parts[i]);
        }
        return out;
    }


    private static long[] date(Pattern aPattern, String aToken)
    {
        Matcher m = aPattern.matcher(aToken);
        if (!m.matches())
        {
            return UNPARSEABLE;
        }
        return epochDay(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                Integer.parseInt(m.group(3)));
    }


    private static long[] epochDay(int aYear, int aMonth, int aDay)
    {
        try
        {
            return new long[]
            {
                    LocalDate.of(aYear, aMonth, aDay).toEpochDay()
            };
        }
        catch (java.time.DateTimeException _)
        {
            return UNPARSEABLE;
        }
    }


    private static @Nullable Month month(String aMon)
    {
        for (Month m : Month.values())
        {
            if (m.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).equalsIgnoreCase(aMon))
            {
                return m;
            }
        }
        return null;
    }

    /** A token with its parsed key; parsable above unparsable, then key, then date, then text. */
    private static final class Keyed implements Comparable<Keyed>
    {

        private final String token;

        private final long[] key;

        private final Installed installed;

        /** The install date, read at most once per order (it can cost a file read). */
        private @Nullable LocalDate date;

        private boolean dateRead;

        Keyed(String aToken, long[] aKey, Installed aInstalled)
        {
            token = aToken;
            key = aKey;
            installed = aInstalled;
        }


        private @Nullable LocalDate date()
        {
            if (!dateRead)
            {
                date = installed.installDate().apply(token);
                dateRead = true;
            }
            return date;
        }


        @Override
        public int compareTo(Keyed aOther)
        {
            boolean mineParsed = key.length > 0;
            boolean theirsParsed = aOther.key.length > 0;
            if (mineParsed != theirsParsed)
            {
                return mineParsed ? 1 : -1;
            }
            if (mineParsed)
            {
                int c = Arrays.compare(key, aOther.key);
                if (c != 0)
                {
                    return c;
                }
            }
            LocalDate mine = date();
            LocalDate theirs = aOther.date();
            if (mine != null && theirs != null)
            {
                int c = mine.compareTo(theirs);
                if (c != 0)
                {
                    return c;
                }
            }
            else if (mine != null || theirs != null)
            {
                return mine != null ? 1 : -1;
            }
            return token.compareTo(aOther.token);
        }


        @Override
        public boolean equals(@Nullable Object aOther)
        {
            return aOther instanceof Keyed k && compareTo(k) == 0;
        }


        @Override
        public int hashCode()
        {
            return token.hashCode();
        }
    }
}
