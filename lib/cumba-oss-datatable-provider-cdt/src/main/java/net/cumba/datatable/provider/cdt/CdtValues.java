package net.cumba.datatable.provider.cdt;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.MissingValue;
import net.cumba.datatable.values.TemporalOrigin;

import org.jspecify.annotations.Nullable;

/**
 * Conversion helpers shared by the provider, the exporter, and any test-fixture loader that reads
 * CDT content. Converts raw string values from a {@link CdtDataset} into the native object
 * representation expected by the data model.
 */
public final class CdtValues
{

    /**
     * Epoch for Date / DateTime columns: 1960-01-01.
     *
     * <p>
     * F-prov-14: retained as public API, but no longer an independent definition — it delegates to
     * the data table's canonical temporal origin, {@link TemporalOrigin#ORIGIN_DATE}. New code
     * should use that constant directly; read its javadoc before changing anything about the value.
     * </p>
     */
    public static final LocalDate SAS_EPOCH = TemporalOrigin.ORIGIN_DATE;

    public static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    public static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ISO_LOCAL_TIME;

    public static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private CdtValues()
    {
    }

    /**
     * The {@code .cdt} missing-value sentinel grammar — <b>and nothing else</b>: a single dot,
     * optionally followed by {@code _} or one of {@code A}..{@code Z}. These are exactly the 28 SAS
     * missing forms carried by {@link MissingValue} ({@code .} , {@code ._},
     * {@code .A}..{@code .Z}).
     *
     * <p>
     * ⛔ {@link MissingValue} also carries non-SAS display strings — {@code NA}, {@code np.nan},
     * {@code pd.NA}, {@code pd.NaT}, {@code <UKN>}, {@code <ERR>}. Those must NEVER be accepted as
     * a {@code .cdt} sentinel: {@code NA} is a legitimate CDISC null-flavour code that really does
     * occur as character data in the rule corpus (e.g. {@code TSVALNF = NA}), and treating it as
     * missing would silently destroy it. So the regex is matched <em>first</em> and the enum is
     * consulted only afterwards, via {@link MissingValue#forValue(String, MissingValue)} with a
     * {@code null} default — never the one-argument {@code forValue}, which defaults to
     * {@link MissingValue#MIS_ERROR} and would therefore accept anything.
     * </p>
     */
    private static final Pattern MISSING_SENTINEL = Pattern.compile("\\.[_A-Z]?");

    /** The character that escapes a literal whose text would otherwise read back as a sentinel. */
    private static final char ESCAPE = '\\';

    /**
     * Whether the given text is exactly one of the {@code .cdt} missing sentinels.
     *
     * @param aText
     *            the text to test.
     * @return true when {@code aText} matches {@code ^\.([_A-Z])?$}.
     */
    public static boolean isMissingSentinel(String aText)
    {
        return MISSING_SENTINEL.matcher(aText).matches();
    }


    /**
     * Resolve the sentinel text to its {@link MissingValue}, or {@code null} when the text is not a
     * sentinel at all (ordinary data).
     *
     * @param aText
     *            the field text to resolve.
     * @return the missing value, or {@code null} when {@code aText} is not a sentinel.
     */
    public static @Nullable MissingValue missingSentinel(String aText)
    {
        if (!isMissingSentinel(aText))
        {
            return null;
        }
        return MissingValue.forValue(aText, null);
    }


    /**
     * Encode one parsed field for storage in {@code CdtDataset.getDataRows()}.
     *
     * <p>
     * The row model is {@code List<List<String>>}, so the one bit the type-resolving layer needs —
     * <em>was this field quoted?</em> — has to travel inside the string. Only sentinel-shaped text
     * is ambiguous, so only sentinel-shaped text is marked: an <b>unquoted</b> sentinel is stored
     * verbatim ({@code .}, {@code ._}, {@code .A}) and means <em>missing</em>, while any literal
     * whose text would read back as a sentinel is prefixed with a backslash. The prefix is applied
     * to an already-backslash-prefixed sentinel too, so the encoding is injective: {@code "."} →
     * {@code \.}, {@code \.} → {@code \\.}, and ordinary data is untouched.
     * {@link #parseValue(String, CdtType)} is the matching decoder.
     * </p>
     *
     * @param aText
     *            the field's literal text, as read from the file.
     * @param aQuoted
     *            whether the field was quoted in the file.
     * @return the encoded field text.
     */
    public static String encodeField(String aText, boolean aQuoted)
    {
        if (!aQuoted && isMissingSentinel(aText))
        {
            return aText;
        }
        int i = 0;
        while (i < aText.length() && aText.charAt(i) == ESCAPE)
        {
            i++;
        }
        return isMissingSentinel(aText.substring(i)) ? ESCAPE + aText : aText;
    }


    /**
     * Inverse of the escape applied by {@link #encodeField(String, boolean)}: strips one leading
     * backslash from an escaped sentinel literal. Any other text is returned unchanged.
     */
    private static String decodeField(String aRaw)
    {
        if (aRaw.isEmpty() || aRaw.charAt(0) != ESCAPE)
        {
            return aRaw;
        }
        int i = 0;
        while (i < aRaw.length() && aRaw.charAt(i) == ESCAPE)
        {
            i++;
        }
        return isMissingSentinel(aRaw.substring(i)) ? aRaw.substring(1) : aRaw;
    }


    /**
     * Convert a raw string value from a CDT data row to the native object representation. Never
     * returns {@code null}.
     *
     * <p>
     * <b>A blank field keeps its historical meaning</b> — an empty {@link String} for CHAR columns
     * and {@link MissingValue#MIS} for the numeric family, exactly as
     * {@code DataValueSupport.defaultForType} spells it. All hand-authored corpora were written
     * against that reading (SAS character data cannot express a missing value, only an empty
     * string), so it must not move.
     * </p>
     *
     * <p>
     * <b>What is new is the sentinel</b> (added 2026-09-17): an unquoted {@code .}, {@code ._} or
     * {@code .A}..{@code .Z} field resolves to the corresponding {@link MissingValue} for
     * <em>every</em> column type, CHAR included — this is the only way {@code .cdt} can express a
     * missing character value, and the only way it can express a SAS <em>special</em> missing at
     * all. A literal dot-shaped character value is written quoted ({@code "."}) and arrives here
     * backslash-escaped; see {@link #encodeField(String, boolean)}.
     * </p>
     *
     * @param aRaw
     *            the raw field as encoded by {@link #encodeField(String, boolean)}.
     * @param aType
     *            the target column type.
     * @return a {@link String} for CHAR, a {@link Double} for NUM / DATE / TIME / DATETIME, or a
     *         {@link MissingValue}. Never {@code null}.
     * @throws CdtParseException
     *             if the value does not parse as the declared type.
     */
    public static Object parseValue(@Nullable String aRaw, CdtType aType)
    {
        if (aRaw == null || aRaw.isEmpty())
        {
            return missingFor(aType);
        }
        MissingValue sentinel = missingSentinel(aRaw);
        if (sentinel != null)
        {
            return sentinel;
        }
        String raw = decodeField(aRaw);
        // A quoted "." on a non-character column has no literal reading - there is no such number -
        // so it keeps answering "missing", as it did before the sentinel existed.
        if (aType != CdtType.CHAR && ".".equals(raw))
        {
            return missingFor(aType);
        }
        try
        {
            return switch (aType)
            {
            case CHAR -> raw;
            case NUM -> Double.parseDouble(raw);
            case DATE -> dateToSasDays(raw);
            case TIME -> timeToSeconds(raw);
            case DATETIME -> datetimeToSasSeconds(raw);
            };
        }
        catch (NumberFormatException | DateTimeParseException ex)
        {
            throw new CdtParseException(
                    "invalid " + aType + " value '" + raw + "': " + ex.getMessage(), ex);
        }
    }


    /**
     * The missing-value representation for the given column type. Mirrors SAS character semantics
     * as applied project-wide (see {@code DataValueSupport.defaultForType} and the DSJ / SAS7BDAT /
     * XPT / CSV providers): a character column has no missing sentinel — an empty string
     * <em>is</em> its missing value — while the numeric family uses {@link MissingValue#MIS}.
     *
     * @param aType
     *            the target column type.
     * @return {@code ""} for {@link CdtType#CHAR}, {@link MissingValue#MIS} otherwise.
     */
    public static Object missingFor(CdtType aType)
    {
        return aType == CdtType.CHAR ? "" : MissingValue.MIS;
    }


    /**
     * Map a CDT column type to the {@link DataValueType} used by the table model.
     */
    public static DataValueType toDataValueType(CdtType aType)
    {
        return switch (aType)
        {
        case CHAR -> DataValueType.STRING;
        case NUM, DATE, TIME, DATETIME -> DataValueType.DOUBLE;
        };
    }


    /**
     * Convert an ISO date string to SAS days since 1960-01-01.
     */
    public static Double dateToSasDays(String aIso)
    {
        LocalDate date = LocalDate.parse(aIso, DATE_FMT);
        return (double) (date.toEpochDay() - SAS_EPOCH.toEpochDay());
    }


    /**
     * Convert an ISO time string to seconds since midnight.
     */
    public static Double timeToSeconds(String aIso)
    {
        LocalTime time = LocalTime.parse(aIso, TIME_FMT);
        return (double) time.toSecondOfDay();
    }


    /**
     * Convert an ISO local date-time string to SAS seconds since 1960-01-01.
     */
    public static Double datetimeToSasSeconds(String aIso)
    {
        LocalDateTime dt = LocalDateTime.parse(aIso, DATETIME_FMT);
        long epochDays = dt.toLocalDate().toEpochDay() - SAS_EPOCH.toEpochDay();
        long secs = dt.toLocalTime().toSecondOfDay();
        return (double) (epochDays * 86400L + secs);
    }
}
