package net.cumba.datatable.provider.cdt;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.MissingValue;

import org.jspecify.annotations.Nullable;

/**
 * Conversion helpers shared by the provider, the exporter, and any test-fixture loader that reads
 * CDT content. Converts raw string values from a {@link CdtDataset} into the native object
 * representation expected by the data model.
 */
public final class CdtValues
{

    /** SAS epoch for Date / DateTime columns: 1960-01-01. */
    public static final LocalDate SAS_EPOCH = LocalDate.of(1960, 1, 1);

    public static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    public static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ISO_LOCAL_TIME;

    public static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private CdtValues()
    {
    }


    /**
     * Convert a raw string value from a CDT data row to the native object representation. An empty
     * string is always missing. For non-character columns a single {@code .} is also missing (SAS
     * convention) — note that {@link CdtParser} already folds an unquoted {@code .} field to the
     * empty string for every column type, so that branch only fires for direct API callers.
     * <p>
     * <b>Missing is spelled per the column's storage type</b>, which is the house contract stated
     * in {@code AbstractDataBuffer.createDataValue}: for STRING "we map from null to empty string",
     * so {@code null} is the character column's empty cell and reads back as {@code ""}. A numeric
     * column has no such mapping — {@code DataBufferDouble.canStore} rejects {@code null} outright
     * and {@code setValue} throws {@code IllegalArgumentException: Invalid value: null} — so a
     * numeric missing must be spelled {@link MissingValue#MIS}, the value that buffer does accept.
     * See {@link #missingFor(CdtType)} for which types count as numeric.
     *
     * @param aRaw
     *            the raw field as read from the file.
     * @param aType
     *            the target column type.
     * @return a {@link String} for CHAR, a {@link Double} for NUM / DATE / TIME / DATETIME, or the
     *         type's missing representation ({@code null} for CHAR, {@link MissingValue#MIS} for
     *         the numeric types) for a missing cell.
     * @throws CdtParseException
     *             if the value does not parse as the declared type.
     */
    public static @Nullable Object parseValue(@Nullable String aRaw, CdtType aType)
    {
        if (aRaw == null || aRaw.isEmpty())
        {
            return missingFor(aType);
        }
        if (aType != CdtType.CHAR && ".".equals(aRaw))
        {
            return missingFor(aType);
        }
        try
        {
            return switch (aType)
            {
            case CHAR -> aRaw;
            case NUM -> Double.parseDouble(aRaw);
            case DATE -> dateToSasDays(aRaw);
            case TIME -> timeToSeconds(aRaw);
            case DATETIME -> datetimeToSasSeconds(aRaw);
            };
        }
        catch (NumberFormatException | DateTimeParseException ex)
        {
            throw new CdtParseException(
                    "invalid " + aType + " value '" + aRaw + "': " + ex.getMessage(), ex);
        }
    }


    /**
     * The representation of a missing cell for the given column type.
     * <p>
     * The decision is <b>derived from the storage type, not enumerated</b>: it asks
     * {@link #toDataValueType(CdtType)} — the very mapping {@code CdtTableBuilder.buildMeta} uses
     * to type the column and hence, via {@code DataBufferFactory.createColumnBuffer}, to pick the
     * buffer. Every {@link CdtType} that maps to {@link DataValueType#DOUBLE} (today NUM, DATE,
     * TIME and DATETIME — dates and times are numeric in SAS terms and share the numeric buffer)
     * lands in a {@code DataBufferDouble}, which rejects {@code null} but accepts
     * {@link MissingValue}. A new numeric {@link CdtType} is therefore handled here by
     * construction, without a second list to keep in sync.
     *
     * @param aType
     *            the target column type.
     * @return {@link MissingValue#MIS} for a numeric column, {@code null} for a character column.
     */
    public static @Nullable Object missingFor(CdtType aType)
    {
        return toDataValueType(aType) == DataValueType.DOUBLE ? MissingValue.MIS : null;
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
