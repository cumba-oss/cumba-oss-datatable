package net.cumba.datatable.values;

// OSS-IDENTITY datatable-missing-value: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The bare-NaN predicate and its two uses, {@link MissingValue#isBareNaN(double)},
 * {@link MissingValue#forNaN(double)} and {@link MissingValue#normalizeBareNaN(double)}
 * (PLAN-bare-nan-is-mis). ⭐ Owner rulings, 2026-09-25: <b>E5</b> <i>"NaN should get mis"</i> and
 * <b>N1</b> <i>"SAS: canonical only"</i> &mdash; a bare NaN is exactly the canonical quiet NaN of
 * either sign, and every other payload that decodes to no constant keeps {@code MIS_UNKNOWN}.
 */
class MissingValueBareNaNTest
{

    /** {@link Double#NaN}'s own pattern, numpy's and pandas' {@code nan}. */
    private static final double POSITIVE_BARE = Double.longBitsToDouble(0x7FF8_0000_0000_0000L);

    /** The x86 arithmetic NaN ({@code 0.0 / 0.0}), which carries the sign bit. */
    private static final double NEGATIVE_BARE = Double.longBitsToDouble(0xFFF8_0000_0000_0000L);

    /**
     * Decodes to no constant and is not bare: it starts {@code 0xFFFF}, like an in-file SAS code.
     */
    private static final double UNRECOGNISED = Double.longBitsToDouble(0xFFFF_0000_0000_0000L);

    /** The predicate is exactly N1's two bit patterns. */
    @Test
    void isBareNaNIsTheCanonicalQuietNaNOfEitherSignAndNothingElse()
    {
        assertTrue(MissingValue.isBareNaN(Double.NaN));
        assertTrue(MissingValue.isBareNaN(0.0 / zero()), "arithmetic NaN");
        assertTrue(MissingValue.isBareNaN(POSITIVE_BARE));
        assertTrue(MissingValue.isBareNaN(NEGATIVE_BARE));
        assertTrue(MissingValue.isBareNaN(Float.NaN), "a float NaN widens to the canonical NaN");

        assertFalse(MissingValue.isBareNaN(UNRECOGNISED));
        assertFalse(MissingValue.isBareNaN(Double.longBitsToDouble(0x7FF8_0000_0000_0001L)),
                "one payload bit makes it not bare");
        assertFalse(MissingValue.isBareNaN(Double.longBitsToDouble(0x7FF0_0000_0000_0001L)),
                "a signalling NaN is not bare");
        assertFalse(MissingValue.isBareNaN(Double.longBitsToDouble(0x7FF0_0000_0000_07A2L)),
                "R's NA_real_ is not bare");
        for (MissingValue mv : MissingValue.values())
        {
            assertFalse(MissingValue.isBareNaN(mv.asDouble()), mv + "'s payload is not bare");
            assertSame(mv, MissingValue.forNaN(mv.asDouble()), mv + " decodes to itself");
        }
        assertFalse(MissingValue.isBareNaN(Double.POSITIVE_INFINITY), "infinity is not a NaN");
        assertFalse(MissingValue.isBareNaN(0.0));
    }


    @Test
    void forNaNAndNormalizeBareNaN()
    {
        assertSame(MissingValue.MIS, MissingValue.forNaN(POSITIVE_BARE));
        assertSame(MissingValue.MIS, MissingValue.forNaN(NEGATIVE_BARE));
        assertSame(MissingValue.MIS_UNKNOWN, MissingValue.forNaN(UNRECOGNISED));
        assertThrows(IllegalArgumentException.class, () -> MissingValue.forNaN(1.5));
        assertThrows(IllegalArgumentException.class,
                () -> MissingValue.forNaN(Double.NEGATIVE_INFINITY));

        long misBits = Double.doubleToRawLongBits(MissingValue.MIS.asDouble());
        assertEquals(misBits,
                Double.doubleToRawLongBits(MissingValue.normalizeBareNaN(POSITIVE_BARE)));
        assertEquals(misBits,
                Double.doubleToRawLongBits(MissingValue.normalizeBareNaN(NEGATIVE_BARE)));
        for (double kept : new double[]
        {
                UNRECOGNISED, MissingValue.MIS_UNKNOWN.asDouble(), MissingValue.MIS_A.asDouble(),
                1.5, -0.0, Double.POSITIVE_INFINITY
        })
        {
            assertEquals(Double.doubleToRawLongBits(kept),
                    Double.doubleToRawLongBits(MissingValue.normalizeBareNaN(kept)),
                    "kept as given: " + kept);
        }
        assertNotEquals(misBits, Double.doubleToRawLongBits(MissingValue.MIS_UNKNOWN.asDouble()),
                "a null's payload is not MIS's");
    }


    /** Keeps {@code 0.0 / 0.0} from being folded into the constant {@code Double.NaN}. */
    private static double zero()
    {
        return Double.parseDouble("0");
    }
}
