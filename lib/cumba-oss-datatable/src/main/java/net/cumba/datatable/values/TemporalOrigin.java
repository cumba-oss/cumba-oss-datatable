package net.cumba.datatable.values;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * The data table's canonical temporal origin: <b>1960-01-01T00:00:00</b>.
 *
 * <p>
 * Every temporal value in the product is stored as a plain number counted from this instant — days
 * for DATE columns, seconds since midnight for TIME columns, seconds for DATETIME columns. That is
 * true regardless of the file format a value came from: Parquet, Excel, CDT, Dataset-JSON, SAS
 * transport and sas7bdat all re-base onto this origin at load time, and the exporters re-base back
 * out of it. The constants here exist so that origin is written down <em>once</em>: the offsets are
 * all derived from {@link #ORIGIN_DATE}, so no caller ever hand-writes {@code 3653},
 * {@code 315619200} or {@code -315619200000L} again.
 * </p>
 *
 * <p>
 * <b>The value is deliberately aligned with the SAS epoch — this is not a coincidence.</b> SAS
 * datasets are the highest-volume input this product reads, and their numeric date and datetime
 * values are already counted from 1960-01-01. Choosing the same origin means those values pass
 * through the read path verbatim, with no per-cell arithmetic on the hottest loop in the product;
 * every other format pays a conversion instead. The name of this class is format-neutral because
 * the origin now belongs to the data model rather than to SAS, but the number was picked for that
 * alignment and must keep it.
 * </p>
 *
 * <p>
 * <b>⚠ Changing this value would silently reinterpret every stored date and datetime in the
 * product.</b> Nothing is stamped with the origin it was written against: a table already in
 * memory, a {@code .cdt} file on disk and an exported dataset all hold bare numbers. Move the
 * origin and every one of them shifts by the delta, without a single error, warning or failing
 * round trip. It is fixed for the life of the format.
 * </p>
 */
public final class TemporalOrigin
{

    /** Seconds in a day, used to derive the second offset from the day offset. */
    private static final long SECONDS_PER_DAY = 86_400L;

    /** Milliseconds in a day, used to derive {@link #ORIGIN_EPOCH_MILLI}. */
    private static final long MILLIS_PER_DAY = 86_400_000L;

    /**
     * The origin itself: 1960-01-01, deliberately the SAS epoch (see the class javadoc).
     */
    public static final LocalDate ORIGIN_DATE = LocalDate.of(1960, 1, 1);

    /**
     * The origin as a local date-time: 1960-01-01T00:00:00. Convenience for the DATETIME paths,
     * derived from {@link #ORIGIN_DATE}.
     */
    public static final LocalDateTime ORIGIN_DATE_TIME = ORIGIN_DATE.atStartOfDay();

    /**
     * The origin expressed as a Unix epoch day: {@code -3653}. Negative because the origin precedes
     * 1970-01-01.
     */
    public static final long ORIGIN_EPOCH_DAY = ORIGIN_DATE.toEpochDay();

    /**
     * Day offset: {@code 3653}. Add it to a Unix epoch day to obtain a day count from this origin,
     * subtract it to go back.
     */
    public static final long OFFSET_DAYS = -ORIGIN_EPOCH_DAY;

    /**
     * Second offset: {@code 315_619_200}. Add it to a Unix epoch second to obtain a second count
     * from this origin, subtract it to go back.
     */
    public static final long OFFSET_SECONDS = OFFSET_DAYS * SECONDS_PER_DAY;

    /**
     * The origin expressed in milliseconds since the Unix epoch: {@code -315_619_200_000}. Negative
     * because the origin precedes 1970-01-01; subtract it from a Unix millisecond timestamp (as the
     * Excel path does with {@code java.util.Date#getTime()}) to obtain milliseconds from this
     * origin.
     */
    public static final long ORIGIN_EPOCH_MILLI = ORIGIN_EPOCH_DAY * MILLIS_PER_DAY;

    private TemporalOrigin()
    {
    }
}
