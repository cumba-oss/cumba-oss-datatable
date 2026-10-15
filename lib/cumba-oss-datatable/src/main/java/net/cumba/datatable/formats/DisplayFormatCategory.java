package net.cumba.datatable.formats;

// OSS-IDENTITY datatable-temporal-classifier: byte-identical in cumba-datatable and
// cumba-oss-datatable. Edit in cumba-datatable, then copy; check_oss_identity.py fails on any
// divergence.

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.values.DataValueType;

import org.jspecify.annotations.Nullable;

/**
 * The stack's one classifier of a column display format (SAS or R style) into a date / time /
 * datetime kind plus the epoch the stored number is measured against. The define.xml generator, the
 * Dataset-JSON, Excel and R exporters and the CDT writer all ask it, so a converted library's
 * define and data files agree on which column is a date (PLAN-date-format-classifier-agreement,
 * SC2); change it here for all of them.
 *
 * <p>
 * <b>SAS formats are classified by the value kind the SAS documentation says the format writes</b>
 * (<i>SAS 9.4 Formats and Informats: Reference</i>, SC1), whatever part of the value is displayed:
 * {@code MONNAME}, {@code MONTH}, {@code JULIAN}, {@code YEAR} <i>"write date values"</i> and are
 * dates; {@code DTDATE}, {@code E8601DN}, {@code DTYEAR} expect a datetime value and are datetimes;
 * {@code HOUR}, {@code MMSS}, {@code E8601TZ} <i>"write time values"</i> and are times. The roster
 * is SAS's own: every built-in numeric format whose {@code FMTINFO(name, 'cat')} is {@code date},
 * {@code datetime} or {@code time} (owner SAS 9.4M9 FMTINFO run, 2026-10-15: 302 date, 75 datetime
 * and 27 time names, the separator families, the {@code IS8601*} aliases and the language-prefixed
 * NLS forms included — tested as a bijection against the roster test data; SC14: FMTINFO's category
 * wins where the reference reads differently, e.g. {@code E8601LX} / {@code B8601LX} are times and
 * {@code PDJULG} / {@code PDJULI} are {@code binary}, not dates). Only a documented name
 * classifies: the <b>exact base name</b> after {@link FormatSpec} strips width and decimals,
 * case-insensitively; a {@code $} character format never; a user format ({@code MYDATE.},
 * {@code DATEFMT.}) never, whatever its name contains (SC8). The four formats SAS documents for
 * time <b>and</b> datetime values — {@code TOD}, {@code TIMEAMPM}, {@code E8601TX}, {@code B8601TX}
 * — are times (owner ruling SC4, 2026-10-15: the writers show the time of day; FMTINFO agrees).
 * </p>
 *
 * <p>
 * Two epochs are recognised:
 * </p>
 * <ul>
 * <li><b>SAS</b> — 1960-01-01: numeric values are days (date), seconds since 1960-01-01 UTC
 * (datetime), or seconds since midnight (time).</li>
 * <li><b>UNIX</b> — 1970-01-01: numeric values are days (R {@code Date}) or seconds since
 * 1970-01-01 UTC (R {@code POSIXct} / {@code POSIXt}).</li>
 * </ul>
 *
 * <p>
 * The classifier is purely metadata-driven; it does not touch the data. {@link #classify(String)}
 * answers for a format string, {@link #classify(DataTableColumnMeta)} for a column (numeric-first:
 * a preserved Dataset-JSON {@code dataType} wins over the display format, SC10),
 * {@link #preservedKind(DataTableColumnMeta)} reads that preserved type alone, and
 * {@link #isSasDuration(String)} names the time formats SAS documents for durations (values of 24
 * hours or more, or negative), which a writer of times of day falls back from (SC11).
 * </p>
 */
public enum DisplayFormatCategory
{

    DATE_SAS, DATETIME_SAS, TIME_SAS, DATE_UNIX, DATETIME_UNIX, OTHER;

    /**
     * The column-metadata key under which the Dataset-JSON provider preserves a column's
     * {@code dataType}; the only source of a preserved kind (never {@code nativeType}, which a JDBC
     * source fills with {@code DATE} for a database date, not a Dataset-JSON token).
     */
    private static final String META_KEY_DATA_TYPE = "dataType";

    /** The metadata fallback a define import stores the display format under. */
    private static final String META_KEY_DISPLAY_FORMAT = "displayFormat";

    /**
     * The date formats ({@code FMTINFO} category {@code date}: <i>"Writes date values …"</i> in the
     * reference, or <i>"calculates the SAS date value"</i> for the week formats), the separator
     * families expanded below.
     */
    private static final Set<String> SAS_DATE_NAMES = roster(List.of("AFRDFDD", "AFRDFDE",
            "AFRDFDN", "AFRDFDWN", "AFRDFMN", "AFRDFMY", "AFRDFWDX", "AFRDFWKX", "B8601DA",
            "CATDFDD", "CATDFDE", "CATDFDN", "CATDFDWN", "CATDFMN", "CATDFMY", "CATDFWDX",
            "CATDFWKX", "CRODFDD", "CRODFDE", "CRODFDN", "CRODFDWN", "CRODFMN", "CRODFMY",
            "CRODFWDX", "CRODFWKX", "CSYDFDD", "CSYDFDE", "CSYDFDN", "CSYDFDWN", "CSYDFMN",
            "CSYDFMY", "CSYDFWDX", "CSYDFWKX", "DANDFDD", "DANDFDE", "DANDFDN", "DANDFDWN",
            "DANDFMN", "DANDFMY", "DANDFWDX", "DANDFWKX", "DATE", "DAY", "DDMMYY", "DESDFDD",
            "DESDFDE", "DESDFDN", "DESDFDWN", "DESDFMN", "DESDFMY", "DESDFWDX", "DESDFWKX",
            "DEUDFDD", "DEUDFDE", "DEUDFDN", "DEUDFDWN", "DEUDFMN", "DEUDFMY", "DEUDFWDX",
            "DEUDFWKX", "DOWNAME", "E8601DA", "ENGDFDD", "ENGDFDE", "ENGDFDN", "ENGDFDWN",
            "ENGDFMN", "ENGDFMY", "ENGDFWDX", "ENGDFWKX", "ESPDFDD", "ESPDFDE", "ESPDFDN",
            "ESPDFDWN", "ESPDFMN", "ESPDFMY", "ESPDFWDX", "ESPDFWKX", "EURDFDD", "EURDFDE",
            "EURDFDN", "EURDFDWN", "EURDFMN", "EURDFMY", "EURDFWDX", "EURDFWKX", "FINDFDD",
            "FINDFDE", "FINDFDN", "FINDFDWN", "FINDFMN", "FINDFMY", "FINDFWDX", "FINDFWKX",
            "FRADFDD", "FRADFDE", "FRADFDN", "FRADFDWN", "FRADFMN", "FRADFMY", "FRADFWDX",
            "FRADFWKX", "FRSDFDD", "FRSDFDE", "FRSDFDN", "FRSDFDWN", "FRSDFMN", "FRSDFMY",
            "FRSDFWDX", "FRSDFWKX", "HDATE", "HEBDATE", "HUNDFDD", "HUNDFDE", "HUNDFDN", "HUNDFDWN",
            "HUNDFMN", "HUNDFMY", "HUNDFWDX", "HUNDFWKX", "IS8601DA", "ITADFDD", "ITADFDE",
            "ITADFDN", "ITADFDWN", "ITADFMN", "ITADFMY", "ITADFWDX", "ITADFWKX", "JDATEMD",
            "JDATEMDW", "JDATEMNW", "JDATEMON", "JDATEQRW", "JDATEQTR", "JDATESEM", "JDATESMW",
            "JDATEWK", "JDATEYDW", "JDATEYM", "JDATEYMD", "JDATEYMW", "JNENGO", "JNENGOW",
            "JULDATE", "JULDAY", "JULIAN", "MACDFDD", "MACDFDE", "MACDFDN", "MACDFDWN", "MACDFMN",
            "MACDFMY", "MACDFWDX", "MACDFWKX", "MINGUO", "MMDDYY", "MMYY", "MONNAME", "MONTH",
            "MONYY", "NENGO", "NLDATE", "NLDATEL", "NLDATEM", "NLDATEMD", "NLDATEMDL", "NLDATEMDM",
            "NLDATEMDS", "NLDATEMN", "NLDATES", "NLDATEW", "NLDATEWN", "NLDATEYM", "NLDATEYML",
            "NLDATEYMM", "NLDATEYMS", "NLDATEYQ", "NLDATEYQL", "NLDATEYQM", "NLDATEYQS", "NLDATEYR",
            "NLDATEYW", "NLDDFDD", "NLDDFDE", "NLDDFDN", "NLDDFDWN", "NLDDFMN", "NLDDFMY",
            "NLDDFWDX", "NLDDFWKX", "NORDFDD", "NORDFDE", "NORDFDN", "NORDFDWN", "NORDFMN",
            "NORDFMY", "NORDFWDX", "NORDFWKX", "POLDFDD", "POLDFDE", "POLDFDN", "POLDFDWN",
            "POLDFMN", "POLDFMY", "POLDFWDX", "POLDFWKX", "PTGDFDD", "PTGDFDE", "PTGDFDN",
            "PTGDFDWN", "PTGDFMN", "PTGDFMY", "PTGDFWDX", "PTGDFWKX", "QTR", "QTRR", "RSTDODB",
            "RSTDODS", "RSTDOMN", "RSTDOMY", "RUSDFDD", "RUSDFDE", "RUSDFDN", "RUSDFDWN", "RUSDFMN",
            "RUSDFMY", "RUSDFWDX", "RUSDFWKX", "SLODFDD", "SLODFDE", "SLODFDN", "SLODFDWN",
            "SLODFMN", "SLODFMY", "SLODFWDX", "SLODFWKX", "SVEDFDD", "SVEDFDE", "SVEDFDN",
            "SVEDFDWN", "SVEDFMN", "SVEDFMY", "SVEDFWDX", "SVEDFWKX", "WEEKDATE", "WEEKDATX",
            "WEEKDAY", "WEEKU", "WEEKV", "WEEKW", "WORDDATE", "WORDDATX", "XYYMMDD", "YEAR", "YYMM",
            "YYMMDD", "YYMON", "YYQ", "YYQR", "YYQZ", "YYWEEKU", "YYWEEKV", "YYWEEKW"),
            // DDMMYYx / MMDDYYx / YYMMDDx admit a blank separator (B), MMYYx / YYMMx / YYQx /
            // YYQRx do not (their entries list C D N P S only).
            List.of("DDMMYY", "MMDDYY", "YYMMDD"), "BCDNPS", List.of("MMYY", "YYMM", "YYQ", "YYQR"),
            "CDNPS");

    /**
     * The datetime formats ({@code FMTINFO} category {@code datetime}: <i>"Writes datetime values
     * …"</i>, <i>"Expects a datetime value …"</i>, <i>"Writes the date part / the time portion of a
     * datetime value …"</i>).
     */
    private static final Set<String> SAS_DATETIME_NAMES = Set.of("AFRDFDT", "B8601DN", "B8601DT",
            "B8601DX", "B8601DZ", "CATDFDT", "CRODFDT", "CSYDFDT", "DANDFDT", "DATEAMPM",
            "DATETIME", "DESDFDT", "DEUDFDT", "DTDATE", "DTMONYY", "DTWKDATX", "DTYEAR", "DTYYQC",
            "E8601DN", "E8601DT", "E8601DX", "E8601DZ", "ENGDFDT", "ESPDFDT", "EURDFDT", "FINDFDT",
            "FRADFDT", "FRSDFDT", "HUNDFDT", "IS8601DN", "IS8601DT", "IS8601DZ", "ITADFDT",
            "JDATEYT", "JDATEYTW", "JNENGOT", "JNENGOTW", "MACDFDT", "MDYAMPM", "NLDATM",
            "NLDATMAP", "NLDATMDT", "NLDATML", "NLDATMM", "NLDATMMD", "NLDATMMDL", "NLDATMMDM",
            "NLDATMMDS", "NLDATMMN", "NLDATMS", "NLDATMTM", "NLDATMTZ", "NLDATMW", "NLDATMWN",
            "NLDATMWZ", "NLDATMYM", "NLDATMYML", "NLDATMYMM", "NLDATMYMS", "NLDATMYQ", "NLDATMYQL",
            "NLDATMYQM", "NLDATMYQS", "NLDATMYR", "NLDATMYW", "NLDATMZ", "NLDDFDT", "NORDFDT",
            "POLDFDT", "PTGDFDT", "RUSDFDT", "SLODFDT", "SVEDFDT", "TWMDY", "UNXEPOCH");

    /**
     * The time formats ({@code FMTINFO} category {@code time}: <i>"Writes time values …"</i>),
     * including the four SAS documents for time and datetime values ({@code TOD}, {@code TIMEAMPM},
     * {@code E8601TX}, {@code B8601TX}; SC4) and {@code E8601LX} / {@code B8601LX} (FMTINFO: time;
     * SC14).
     */
    private static final Set<String> SAS_TIME_NAMES = Set.of("B8601LX", "B8601LZ", "B8601TM",
            "B8601TX", "B8601TZ", "E8601LX", "E8601LZ", "E8601TM", "E8601TX", "E8601TZ", "HHMM",
            "HOUR", "IS8601LZ", "IS8601TM", "IS8601TZ", "JTIMEH", "JTIMEHM", "JTIMEHMS", "JTIMEHW",
            "JTIMEMW", "JTIMESW", "MMSS", "NLTIMAP", "NLTIME", "TIME", "TIMEAMPM", "TOD");

    /**
     * The time formats whose entry documents a <b>duration</b> (SC11): {@code TIME} <i>"can also
     * represent a time duration … can exceed 23:59:59, and it can also be a negative value"</i>;
     * {@code MMSS} (minutes and seconds, its example formats {@code '25:42:08't}); {@code HHMM} and
     * {@code HOUR} <i>"use asterisks to format values that are outside the time range 0–24
     * hours"</i>. Every other time format writes a time of day and wraps a larger value (e.g.
     * {@code TOD}: <i>"it takes the MOD of the value and 24:00:00"</i>).
     */
    private static final Set<String> SAS_DURATION_NAMES = Set.of("TIME", "MMSS", "HHMM", "HOUR");

    /**
     * Classify the given display-format string. Accepts:
     * <ul>
     * <li>SAS forms with or without width and decimals: {@code "DATE9."}, {@code "E8601DA"},
     * {@code "DATETIME20.0"}, {@code "YYMMDDN8."}.</li>
     * <li>R {@code Date} forms: {@code "Rdate"}, {@code "Rdate."}.</li>
     * <li>R {@code POSIXct}/{@code POSIXt} forms: {@code "Rtimestamp"}, {@code "Rtimestamp."},
     * {@code "Rtimestamp[Europe/Berlin]"}, {@code "Rtimestamp.[Europe/Berlin]"}.</li>
     * </ul>
     *
     * @param aDisplayFormat
     *            the display format string, may be {@code null} or blank.
     * @return the matching category, or {@link #OTHER} if the format is null, blank, or not a
     *         recognised date/time format.
     */
    public static DisplayFormatCategory classify(@Nullable String aDisplayFormat)
    {
        if (aDisplayFormat == null || aDisplayFormat.isBlank())
        {
            return OTHER;
        }
        String trimmed = aDisplayFormat.trim();
        DisplayFormatCategory r = classifyR(trimmed);
        if (r != OTHER)
        {
            return r;
        }
        return classifySas(trimmed);
    }


    /**
     * Classify a column, numeric-first (the rule the define generator, the Excel exporter and the
     * CDT writer share): a column that is not {@code LONG} or {@code DOUBLE} is {@link #OTHER} (a
     * character column with a date-ish format already holds text); else the
     * {@linkplain #preservedKind(DataTableColumnMeta) preserved Dataset-JSON type} wins when it
     * names a kind (SC10); else the display format — the column's own, falling back to a
     * {@code "displayFormat"} metadata entry — through {@link #classify(String)}.
     *
     * @param aColumn
     *            the column.
     * @return the column's kind, or {@link #OTHER}.
     */
    public static DisplayFormatCategory classify(DataTableColumnMeta aColumn)
    {
        DataValueType type = aColumn.getType();
        if (type != DataValueType.LONG && type != DataValueType.DOUBLE)
        {
            return OTHER;
        }
        DisplayFormatCategory preserved = preservedKind(aColumn);
        if (preserved != OTHER)
        {
            return preserved;
        }
        return classify(displayFormatOf(aColumn));
    }


    /**
     * The kind a preserved Dataset-JSON {@code dataType} column metadata names, whatever the
     * column's storage type: {@code date} / {@code datetime} / {@code time} (the enum constant the
     * Dataset-JSON provider stores, or its name as text, case-insensitively) give {@link #DATE_SAS}
     * / {@link #DATETIME_SAS} / {@link #TIME_SAS} — Dataset-JSON's numeric form of a date type
     * counts from the SAS epoch — and anything else {@link #OTHER}. Only the {@code "dataType"} key
     * is read, never the native type. The Dataset-JSON exporter asks this first for every column,
     * so a character date column passes its declaration through.
     *
     * @param aColumn
     *            the column.
     * @return the preserved kind, or {@link #OTHER}.
     */
    public static DisplayFormatCategory preservedKind(DataTableColumnMeta aColumn)
    {
        Object preserved = aColumn.getMetaData(META_KEY_DATA_TYPE);
        String token;
        if (preserved instanceof Enum<?> e)
        {
            token = e.name();
        }
        else if (preserved instanceof String s)
        {
            token = s.trim();
        }
        else
        {
            return OTHER;
        }
        return switch (token.toUpperCase(Locale.ROOT))
        {
        case "DATE" -> DATE_SAS;
        case "DATETIME" -> DATETIME_SAS;
        case "TIME" -> TIME_SAS;
        default -> OTHER;
        };
    }


    /**
     * Whether the format is one of the SAS time formats documented for durations ({@code TIME},
     * {@code MMSS}, {@code HHMM}, {@code HOUR}; SC11): a value of 24 hours or more, or a negative
     * one, is a duration there, not a time of day to wrap.
     *
     * @param aDisplayFormat
     *            the display format string, may be {@code null}.
     * @return {@code true} for a duration format.
     */
    public static boolean isSasDuration(@Nullable String aDisplayFormat)
    {
        String base = sasBaseName(aDisplayFormat);
        return base != null && SAS_DURATION_NAMES.contains(base);
    }


    /**
     * The SAS format names of a kind, for the roster tests: every name {@link #classify(String)}
     * files under {@code aKind} (upper case, without width and decimals).
     *
     * @param aKind
     *            {@link #DATE_SAS}, {@link #DATETIME_SAS} or {@link #TIME_SAS}.
     * @return an unmodifiable set; empty for the Unix kinds and {@link #OTHER}.
     */
    public static Set<String> sasFormatNames(DisplayFormatCategory aKind)
    {
        return switch (aKind)
        {
        case DATE_SAS -> SAS_DATE_NAMES;
        case DATETIME_SAS -> SAS_DATETIME_NAMES;
        case TIME_SAS -> SAS_TIME_NAMES;
        default -> Set.of();
        };
    }


    private static Set<String> roster(List<String> aNames, List<String> aSixLetterFamilies,
            String aSixLetters, List<String> aFiveLetterFamilies, String aFiveLetters)
    {
        Set<String> names = new HashSet<>(aNames);
        addSeparatorVariants(names, aSixLetterFamilies, aSixLetters);
        addSeparatorVariants(names, aFiveLetterFamilies, aFiveLetters);
        return Collections.unmodifiableSet(names);
    }


    private static void addSeparatorVariants(Set<String> aNames, List<String> aFamilies,
            String aLetters)
    {
        for (String family : aFamilies)
        {
            for (int i = 0; i < aLetters.length(); i++)
            {
                aNames.add(family + aLetters.charAt(i));
            }
        }
    }


    private static @Nullable String displayFormatOf(DataTableColumnMeta aColumn)
    {
        String fmt = aColumn.getDisplayFormat();
        if (fmt != null && !fmt.isBlank())
        {
            return fmt;
        }
        Object meta = aColumn.getMetaData(META_KEY_DISPLAY_FORMAT);
        return meta instanceof String s && !s.isBlank() ? s : null;
    }


    private static DisplayFormatCategory classifyR(String aFormat)
    {
        // Match Rdate / Rdate. (case-insensitive, no other suffix)
        if (aFormat.equalsIgnoreCase("Rdate") || aFormat.equalsIgnoreCase("Rdate."))
        {
            return DATE_UNIX;
        }
        // Match Rtimestamp / Rtimestamp. / Rtimestamp[<tz>] / Rtimestamp.[<tz>]
        // Case-insensitive on the literal prefix; the timezone payload is preserved as-is by the
        // caller (we only need the kind here, not the timezone).
        String lower = aFormat.toLowerCase(Locale.ROOT);
        if (lower.equals("rtimestamp") || lower.equals("rtimestamp."))
        {
            return DATETIME_UNIX;
        }
        if (lower.startsWith("rtimestamp[") && aFormat.endsWith("]"))
        {
            return DATETIME_UNIX;
        }
        if (lower.startsWith("rtimestamp.[") && aFormat.endsWith("]"))
        {
            return DATETIME_UNIX;
        }
        return OTHER;
    }


    private static DisplayFormatCategory classifySas(String aFormat)
    {
        String upper = sasBaseName(aFormat);
        if (upper == null)
        {
            return OTHER;
        }
        if (SAS_DATE_NAMES.contains(upper))
        {
            return DATE_SAS;
        }
        if (SAS_DATETIME_NAMES.contains(upper))
        {
            return DATETIME_SAS;
        }
        if (SAS_TIME_NAMES.contains(upper))
        {
            return TIME_SAS;
        }
        return OTHER;
    }


    /**
     * The upper-case SAS base name of a format string (width and decimals stripped), or
     * {@code null} for a null / blank / unparsable string or a {@code $} character format.
     */
    private static @Nullable String sasBaseName(@Nullable String aFormat)
    {
        if (aFormat == null || aFormat.isBlank())
        {
            return null;
        }
        FormatSpec spec = FormatSpec.getFormatSpec(aFormat.trim());
        if (spec == null)
        {
            return null;
        }
        String base = spec.getName();
        if (base == null || base.isEmpty() || base.charAt(0) == '$')
        {
            return null;
        }
        return base.toUpperCase(Locale.ROOT);
    }


    /**
     * Returns {@code true} if this category represents a date/datetime/time logical kind.
     *
     * @return {@code true} if this category represents a date/datetime/time logical kind.
     */
    public boolean isDateTimeKind()
    {
        return this != OTHER;
    }


    /**
     * Returns {@code true} if the underlying numeric value is measured against the SAS epoch
     * (1960-01-01).
     *
     * @return {@code true} if the underlying numeric value is measured against the SAS epoch
     *         (1960-01-01).
     */
    public boolean isSasEpoch()
    {
        return this == DATE_SAS || this == DATETIME_SAS || this == TIME_SAS;
    }


    /**
     * Returns {@code true} if the underlying numeric value is measured against the Unix epoch
     * (1970-01-01).
     *
     * @return {@code true} if the underlying numeric value is measured against the Unix epoch
     *         (1970-01-01).
     */
    public boolean isUnixEpoch()
    {
        return this == DATE_UNIX || this == DATETIME_UNIX;
    }
}
