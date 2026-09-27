package net.cumba.datatable.values;

// OSS-IDENTITY datatable-numeric-text: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * ⭐ The store rule of the DOUBLE buffers, pinned as bits (PLAN-negative-zero-on-load, register
 * {@code NZL O1}; owner 2026-09-27: <i>"a -0.0 gets read as 0.0"</i>):
 * {@link DataValueDouble#dropZeroSign(double)} turns exactly one bit pattern, {@code -0.0}, into
 * {@code +0.0}, and {@link DataValueDouble#normalizeForStore(double)} composes it with the bare-NaN
 * rule ({@code BNM N2} (b)). Everything else — every NaN payload, the smallest negative subnormal,
 * the infinities — keeps its bits.
 *
 * <p>
 * Every assertion compares raw bits ({@link Double#doubleToRawLongBits(double)}):
 * {@code -0.0 == 0.0} is {@code true}, so a value comparison could not see the rule at all.
 * Byte-identical in both datatable twins, run against each twin's own {@code DataValueDouble}.
 * </p>
 */
class DataValueDoubleNormalizeForStoreTest
{

    private static final long NEGATIVE_ZERO = 0x8000_0000_0000_0000L;

    private static final long POSITIVE_ZERO = 0L;

    /** {@link Double#NaN}'s own pattern: a bare NaN. */
    private static final long BARE_POSITIVE = 0x7FF8_0000_0000_0000L;

    /** The x86 arithmetic NaN ({@code 0.0 / 0.0}): a bare NaN carrying the sign bit. */
    private static final long BARE_NEGATIVE = 0xFFF8_0000_0000_0000L;

    /** A payload no constant decodes, starting {@code 0xFFFF} like an in-file SAS code. */
    private static final long FOREIGN_PAYLOAD = 0xFFFF_0000_0000_0000L;

    private static long dropped(long aBits)
    {
        return Double
                .doubleToRawLongBits(DataValueDouble.dropZeroSign(Double.longBitsToDouble(aBits)));
    }


    private static long stored(long aBits)
    {
        return Double.doubleToRawLongBits(
                DataValueDouble.normalizeForStore(Double.longBitsToDouble(aBits)));
    }


    @Test
    void dropZeroSignTurnsANegativeZeroIntoZero()
    {
        assertEquals(POSITIVE_ZERO, dropped(NEGATIVE_ZERO));
        assertEquals(POSITIVE_ZERO, dropped(POSITIVE_ZERO));
        // a float -0.0f widened to double is the same bit pattern
        assertEquals(NEGATIVE_ZERO, Double.doubleToRawLongBits((double) -0.0f));
        assertEquals(POSITIVE_ZERO,
                Double.doubleToRawLongBits(DataValueDouble.dropZeroSign((double) -0.0f)));
    }


    @ParameterizedTest(name = "0x{0}")
    @ValueSource(longs =
    {
            // the smallest negative subnormal: one bit away from -0.0, and not a zero
            0x8000_0000_0000_0001L,
            // the smallest positive subnormal
            0x0000_0000_0000_0001L,
            // -1.5 and 2.5
            0xBFF8_0000_0000_0000L, 0x4004_0000_0000_0000L,
            // the infinities
            0x7FF0_0000_0000_0000L, 0xFFF0_0000_0000_0000L,
            // the bare NaNs: dropZeroSign does not touch them (the bare-NaN rule is separate)
            BARE_POSITIVE, BARE_NEGATIVE,
            // a foreign payload and the all-ones pattern
            FOREIGN_PAYLOAD, 0xFFFF_FFFF_FFFF_FFFFL
    })
    void dropZeroSignKeepsEveryOtherPattern(long aBits)
    {
        assertEquals(aBits, dropped(aBits));
    }


    @Test
    void dropZeroSignKeepsEveryMissingValuePayload()
    {
        for (MissingValue mv : MissingValue.values())
        {
            long bits = Double.doubleToRawLongBits(mv.asDouble());
            assertEquals(bits, dropped(bits), mv.name());
        }
    }


    @Test
    void normalizeForStoreDropsTheZeroSignAndMapsABareNaNToMis()
    {
        long mis = Double.doubleToRawLongBits(MissingValue.MIS.asDouble());
        assertEquals(POSITIVE_ZERO, stored(NEGATIVE_ZERO));
        assertEquals(POSITIVE_ZERO, stored(POSITIVE_ZERO));
        assertEquals(mis, stored(BARE_POSITIVE));
        assertEquals(mis, stored(BARE_NEGATIVE));
    }


    @Test
    void normalizeForStoreKeepsEveryPayloadAndEveryOtherNumber()
    {
        for (MissingValue mv : new MissingValue[]
        {
                MissingValue.MIS, MissingValue.MIS_A, MissingValue.MIS_UNKNOWN
        })
        {
            long bits = Double.doubleToRawLongBits(mv.asDouble());
            assertEquals(bits, stored(bits), mv.name());
        }
        for (long bits : new long[]
        {
                FOREIGN_PAYLOAD, 0xFFFF_FFFF_FFFF_FFFFL, 0x8000_0000_0000_0001L,
                0x7FF0_0000_0000_0000L, 0xFFF0_0000_0000_0000L, 0xBFF8_0000_0000_0000L
        })
        {
            assertEquals(bits, stored(bits), Long.toHexString(bits));
        }
    }
}
