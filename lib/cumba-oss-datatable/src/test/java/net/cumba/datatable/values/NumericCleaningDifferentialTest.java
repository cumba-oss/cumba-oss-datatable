package net.cumba.datatable.values;

// OSS-IDENTITY datatable-numeric-text: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * {@link DataValueSupport#getAsDoubleCleaned(double)}'s table-driven fast path against an
 * <b>independent</b> {@link BigDecimal} reference of the same rule, over about a million seeded
 * values.
 *
 * <p>
 * The reference shares no production helper: it takes the decimal exponent from the exact binary
 * expansion ({@code precision - scale - 1}), rounds with its own {@link MathContext}, and compares
 * the exact difference against the exact threshold {@code 1E-12 * 10^e}. The fast path scales by a
 * power-of-ten table, rounds with {@link Math#rint} and compares doubles; the two agreeing on every
 * class of value — around every decade from {@code 1e-13} to {@code 1e15}, the
 * {@code [1e-13, 1e-11)} band that takes the exact path, negatives, decimal text of 12 to 18
 * digits, calculated values, widened floats, values a hair inside and outside the threshold, the
 * known thirteenth-digit ties, and the fractions from {@code 1e12} to {@code 2^52} where the units
 * floor (D3) replaces the 12-digit rounding — is what makes the fast path's shortcuts (the integral
 * short-circuit, the tie return, the capped exponent, the one-step exponent correction) claims
 * rather than hopes.
 * </p>
 *
 * <p>
 * Non-vacuity: the run must both snap and keep values, and the reference must give the owner's
 * ruled answers on its own — a reference that answers {@code v} for everything would agree with a
 * fast path that does the same.
 * </p>
 */
class NumericCleaningDifferentialTest
{

    private static final MathContext TWELVE = new MathContext(12, RoundingMode.HALF_EVEN);

    private static final BigDecimal FLOOR = new BigDecimal("1E-13");

    private static final BigDecimal REL = new BigDecimal("1E-12");

    private static final int PER_CLASS = 25_000;

    private static final int MAX_LISTED = 10;

    /** The rule, evaluated exactly; shares nothing with the production path. */
    @SuppressWarnings("PMD.AvoidDecimalLiteralsInBigDecimalConstructor")
    static double reference(double aValue)
    {
        if (!Double.isFinite(aValue) || aValue == 0.0d)
        {
            return aValue;
        }
        BigDecimal exact = new BigDecimal(aValue);
        if (exact.stripTrailingZeros().scale() <= 0)
        {
            return aValue; // integral
        }
        if (exact.abs().compareTo(FLOOR) < 0)
        {
            return 0.0d;
        }
        int e = exact.precision() - exact.scale() - 1;
        // D3: never coarser than whole units -- from 1e12 the rounding step is the nearest integer
        // (half-even), not the 12-digit rounding
        double rounded = e >= 12 ? exact.setScale(0, RoundingMode.HALF_EVEN).doubleValue()
                : exact.round(TWELVE).doubleValue();
        BigDecimal diff = exact.subtract(new BigDecimal(rounded)).abs();
        return diff.compareTo(REL.scaleByPowerOfTen(e)) <= 0 ? rounded : aValue;
    }


    private static double decade(int aExponent)
    {
        return Double.parseDouble("1e" + aExponent);
    }


    /** Seeded values, class by class; the classes are named in the class javadoc. */
    private static List<Double> population()
    {
        Random rnd = new Random(20260925L);
        List<Double> out = new ArrayList<>();
        for (int e = -13; e <= 15; e++)
        {
            double base = decade(e);
            for (int i = 0; i < PER_CLASS; i++)
            {
                double mantissa = 1 + rnd.nextDouble() * 9;
                double x = base * mantissa;
                switch (i % 6)
                {
                case 0 -> out.add(x); // full precision
                case 1 -> out.add(-x); // negatives
                case 2 -> out.add(Double.parseDouble( // decimal text of 12..18 digits
                        String.format(Locale.ROOT, "%." + (rnd.nextInt(7) + 12) + "g", x)));
                case 3 -> out.add(x * 3 / 3 + x / 7 * 7 - x); // calculated
                case 4 -> out.add(Double.parseDouble(String.format(Locale.ROOT, "%.12g", x))
                        + base * 1e-12 * (rnd.nextDouble() * 2.4 - 1.2)); // around the threshold
                // 13 digits: the thirteenth-digit ties and their neighbours
                default -> out.add(Double.parseDouble(String.format(Locale.ROOT, "%.13g", x)));
                }
            }
        }
        // at and beside every decade boundary: 10^e itself, its neighbours up to 4 ulps either
        // way, both signs -- where the exponent estimate and its correction meet, and where the
        // decade table's nearest-double entries are the only ambiguous inputs
        for (int e = -13; e <= 15; e++)
        {
            double pow = decade(e);
            for (int ulps = -4; ulps <= 4; ulps++)
            {
                double v = pow;
                for (int k = 0; k < Math.abs(ulps); k++)
                {
                    v = ulps < 0 ? Math.nextDown(v) : Math.nextUp(v);
                }
                out.add(v);
                out.add(-v);
            }
        }
        for (int i = 0; i < 100_000; i++)
        {
            out.add((double) (rnd.nextFloat() * 2000 - 1000)); // widened floats
            double a = rnd.nextInt(1_000_000) / 100.0;
            int b = rnd.nextInt(999) + 1;
            out.add(a / b * b); // R-B3: calculated a / b * b
            out.add(rnd.nextInt(100_000) / 10_000.0); // R-B3: 4-decimal text data
        }
        // the units floor: half-integers and near-integers from 1e12 to 2^52, both signs
        for (int e = 12; e <= 15; e++)
        {
            double base = decade(e);
            for (int i = 0; i < 20_000; i++)
            {
                double whole = Math.floor(base * (1 + rnd.nextDouble() * 9));
                double frac = switch (i % 4)
                {
                case 0 -> 0.5;
                case 1 -> rnd.nextDouble();
                case 2 -> 0.25 * (rnd.nextInt(4) + 1);
                default -> 1.0 / (rnd.nextInt(64) + 1);
                };
                out.add(whole + frac);
                out.add(-(whole + frac));
            }
        }
        // the known thirteenth-digit ties and their neighbours (0.08190316160375 and
        // 7366226.276815 from the research; 123456789012.5 an exact one), plus a value whose
        // tail is exactly half the threshold (1.0000000000005: 13 digits, tail 5e-13 <= 1e-12,
        // snapped -- NOT a tie, it is inside the threshold)
        for (double tie : new double[]
        {
                0.08190316160375, 7366226.276815, 123456789012.5, 1.0000000000005
        })
        {
            out.add(tie);
            out.add(Math.nextUp(tie));
            out.add(Math.nextDown(tie));
        }
        return out;
    }


    @Test
    void fastPathAgreesWithTheExactReference()
    {
        List<Double> values = population();
        assertTrue(values.size() >= 1_000_000, "population floor: " + values.size());
        int snapped = 0;
        int kept = 0;
        List<String> mismatches = new ArrayList<>();
        for (double v : values)
        {
            double fast = DataValueSupport.getAsDoubleCleaned(v);
            double ref = reference(v);
            if (Double.compare(fast, ref) != 0 && mismatches.size() < MAX_LISTED)
            {
                mismatches.add(v + ": fast=" + fast + " reference=" + ref);
            }
            if (Double.compare(fast, ref) != 0)
            {
                continue;
            }
            if (Double.compare(fast, v) == 0)
            {
                kept++;
            }
            else
            {
                snapped++;
            }
        }
        assertEquals(List.of(), mismatches, "fast path vs BigDecimal reference over "
                + values.size() + " values (first " + MAX_LISTED + " listed)");
        assertEquals(values.size(), snapped + kept, "every value agreed");
        assertTrue(snapped > 1000, "non-vacuity: the run must snap values, snapped=" + snapped);
        assertTrue(kept > 1000, "non-vacuity: the run must keep values, kept=" + kept);
    }


    @Test
    void theReferenceGivesTheRuledAnswersOnItsOwn()
    {
        // the reference is the oracle above; here it is held to the owner's examples so that a
        // silently degenerate oracle (answers v for everything) cannot pass the differential
        assertEquals(5.0, reference(4.9999999999994));
        assertEquals(-5.0, reference(-4.9999999999994));
        assertEquals(50000.0, reference(49999.999999994));
        assertEquals(5.0, reference(5.0000000000001));
        assertEquals(123456.1234567, reference(123456.1234567));
        assertEquals(1234567890123.0, reference(1234567890123.0));
        assertEquals(123456789012.34, reference(123456789012.34), "e = 11: kept");
        assertEquals(1234567890123.0, reference(1234567890123.4), "e = 12: D3, the fraction goes");
        assertEquals(10000000000001.0, reference(10000000000001.4), "D3: units, not 1e13");
        assertEquals(10000000000006.0, reference(10000000000005.5), "D3: half-even at .5");
        assertEquals(0.0, reference(5.551115123125783e-17));
        assertEquals(1.0036416142611415e-13, reference(1.0036416142611415e-13));
        assertEquals(0.08190316160375, reference(0.08190316160375));
        assertEquals(1e-12, reference(1.000000000000001e-12));
    }
}
