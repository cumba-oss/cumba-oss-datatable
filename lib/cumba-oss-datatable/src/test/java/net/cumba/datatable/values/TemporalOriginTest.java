package net.cumba.datatable.values;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * Equivalence pin for F-prov-14. {@link TemporalOrigin} replaced four independently-written
 * definitions of the same instant, in three different units:
 *
 * <ul>
 * <li>{@code SASFormatFactory.SAS_DATE_EPOCH} / {@code SAS_DATETIME_EPOCH} — {@code LocalDate} /
 * {@code LocalDateTime} {@code 1960-01-01}</li>
 * <li>{@code CdtValues.SAS_EPOCH} — {@code LocalDate} {@code 1960-01-01}</li>
 * <li>{@code ExcelTableProvider.ExcelRow.SAS_DATETIME_EPOCH_MILLIS} — {@code -315619200000L}</li>
 * <li>{@code ParquetTableProvider.SAS_EPOCH_OFFSET_DAYS} / {@code _SECONDS} — {@code 3653} and
 * {@code 315619200}</li>
 * </ul>
 *
 * <p>
 * The move was a pure refactor, so there is no behaviour change to assert. This test asserts the
 * equivalence instead: each literal the constants replaced, written out here, still equals what
 * {@link TemporalOrigin} derives. If someone ever moves the origin, this test — not a silently
 * reinterpreted dataset — is what fails first.
 * </p>
 */
class TemporalOriginTest
{

    @Test
    void originIsNineteenSixtyJanuaryFirst()
    {
        assertEquals(LocalDate.of(1960, 1, 1), TemporalOrigin.ORIGIN_DATE);
        assertEquals(LocalDateTime.of(1960, 1, 1, 0, 0, 0), TemporalOrigin.ORIGIN_DATE_TIME);
    }


    @Test
    void dayOffsetIs3653()
    {
        assertEquals(3653L, TemporalOrigin.OFFSET_DAYS);
        assertEquals(-3653L, TemporalOrigin.ORIGIN_EPOCH_DAY);
    }


    @Test
    void secondOffsetIs315619200()
    {
        assertEquals(315_619_200L, TemporalOrigin.OFFSET_SECONDS);
    }


    @Test
    void milliOffsetIsMinus315619200000()
    {
        assertEquals(-315_619_200_000L, TemporalOrigin.ORIGIN_EPOCH_MILLI);
    }


    /**
     * The three units must describe the <em>same</em> instant — the whole point of deriving them
     * from one {@link LocalDate} rather than writing three literals.
     */
    @Test
    void theThreeUnitsAgreeWithEachOther()
    {
        assertEquals(TemporalOrigin.OFFSET_DAYS * 86_400L, TemporalOrigin.OFFSET_SECONDS);
        assertEquals(-TemporalOrigin.OFFSET_SECONDS * 1_000L, TemporalOrigin.ORIGIN_EPOCH_MILLI);
        assertEquals(TemporalOrigin.ORIGIN_DATE_TIME.toInstant(ZoneOffset.UTC).toEpochMilli(),
                TemporalOrigin.ORIGIN_EPOCH_MILLI);
    }
}
