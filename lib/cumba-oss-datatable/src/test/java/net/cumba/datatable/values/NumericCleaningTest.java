package net.cumba.datatable.values;

// OSS-IDENTITY datatable-numeric-text: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The numeric cleaning rule and the two renderings, pinned row by row
 * ({@code PLAN-numeric-cleaning-and-key-text}, owner rulings E7 / D1 / D2, 2026-09-25).
 *
 * <p>
 * {@link DataValueSupport#getAsDoubleCleaned(double)} snaps a value onto its 12-significant-digit
 * rounding when the tail is below a tenth of the twelfth digit's unit ({@code 1e-12 * 10^e}, the
 * <b>decade</b> anchor), never rounds an integral value, and flattens {@code |v| < 1e-13} to
 * {@code 0}. {@link DataValueSupport#toPlainNumberText(double)} is notation only — plain, no
 * {@code .0}, no scientific notation, no {@code long} saturation — and
 * {@link DataValueSupport#toCleanText(double)} is the two in sequence: the text of every DOUBLE
 * cell, and therefore of every text key built from one.
 * </p>
 *
 * <p>
 * The rows that carry the plan's sensitivity arms (both measured 2026-09-25): re-inserting the old
 * magnitude scaling reddens the non-integral rows beyond 12 digits and the owner's
 * {@code 4.9999999999994} (5 of 12); removing the integral short-circuit reddens the integral rows
 * that sit INSIDE the decade threshold — {@code 2^52 + 1} and {@code 10000000000001.0} — (2 of 12).
 * ⚠ It does not redden {@code 1234567890123 != 1234567890124}: at {@code e = 12} the threshold is
 * {@code 1} and those two are {@code 3} from any 12-digit rounding, so the threshold alone keeps
 * them; the short-circuit is what protects an integral value whose fraction-free tail is small.
 * </p>
 */
class NumericCleaningTest
{

    private static final DataValueSupport SUPPORT = new DataValueSupport();

    private static double clean(double aValue)
    {
        return DataValueSupport.getAsDoubleCleaned(aValue);
    }


    private static String text(double aValue)
    {
        return new DataValueDouble(aValue).getValueAsString();
    }

    // ------------------------------------------------------------------ the owner's examples


    @Test
    void theOwnersNoiseExamplesAreOneValue()
    {
        assertEquals(5.0, clean(4.9999999999994), "E7: 4.9999999999994 means 5");
        assertEquals(-5.0, clean(-4.9999999999994), "E7: the sign does not change the rule");
        assertEquals(5.0, clean(5.0000000000001), "E7: 5.0000000000001 means 5");
        assertEquals(50000.0, clean(49999.999999994),
                "E7: 4.9999999999994 * 10000 = 49999.999999994 means 50000 — relative to magnitude");
        assertEquals(10.0, clean(9.9999999999994), "the decade anchor: a leading 9 snaps like a 1");
        assertEquals(1.0, clean(0.99999999999994), "and so does a value just below its decade");
        assertEquals("5", text(4.9999999999994), "the text is the cleaned value, plain");
        assertEquals("5", text(5.0000000000001));
        assertEquals("50000", text(49999.999999994));
        assertEquals("5", DataValueSupport.toCleanText(4.9999999999994));
    }

    // ---------------------------------------------------------- lossless beyond 12 digits


    @Test
    void integralValuesAreNeverRounded()
    {
        // E2: these two used to render "1234567890120" both, and joined as one key
        assertEquals(1234567890123.0, clean(1234567890123.0));
        assertEquals(1234567890124.0, clean(1234567890124.0));
        assertNotEquals(text(1234567890123.0), text(1234567890124.0),
                "E2: two integral values 13 digits long are two texts");
        assertEquals("1234567890123", text(1234567890123.0));
        assertEquals("1234567890124", text(1234567890124.0));
        // at and beyond 2^52 every double is integral, and so is left alone
        assertEquals(4503599627370497.0, clean(4503599627370497.0), "2^52 + 1");
        assertEquals(9007199254740992.0, clean(9007199254740992.0), "2^53");
        assertEquals(1e20, clean(1e20));
        assertEquals("100000000000000000000", text(1e20), "R-B2: no long saturation");
        assertEquals(19999.0, clean(19999.0), "Q23: 19999 is not 20000");
    }


    @Test
    void nonIntegralValuesBeyondTwelveDigitsKeepTheirDigits()
    {
        // both rendered "123456789012" under the old rule: one text for two values (e = 11,
        // 14 digits, tails .34 / .33 against the 0.1 threshold)
        assertEquals(123456789012.34, clean(123456789012.34));
        assertEquals(123456789012.33, clean(123456789012.33));
        assertEquals("123456789012.34", text(123456789012.34));
        assertEquals("123456789012.33", text(123456789012.33));
        // from 1e12 the fraction goes by ruling D3 (never coarser than whole units): the same
        // pair one decade up is ONE text
        assertEquals("1234567890123", text(1234567890123.4));
        assertEquals("1234567890123", text(1234567890123.3));
        // 16 digits, tail 3.3e-10 against a threshold of 1e-10: kept
        assertEquals(333.3333333333333, clean(333.3333333333333));
        assertEquals("333.3333333333333", text(333.3333333333333));
        // 13 digits, tail 3e-7 against 1e-7: kept — for a leading 5 as much as for a leading 1
        assertEquals(523456.1234567, clean(523456.1234567), "R-B3: no leading-digit asymmetry");
        assertEquals(123456.1234567, clean(123456.1234567));
        assertEquals("123456.1234567", text(123456.1234567),
                "used to render \"123456.123457\" under the value-relative rule");
    }


    @Test
    void neverCoarserThanWholeUnits()
    {
        // D3 (owner: "agree to b"): from 1e12 a non-integral value snaps to its NEAREST INTEGER,
        // never to the 12-digit rounding that would carry it across integral neighbours
        assertEquals(10000000000001.0, clean(10000000000001.4),
                "was 1e13 under the uncapped 12-digit rounding");
        assertEquals("10000000000001", text(10000000000001.4));
        assertEquals(10000000000006.0, clean(10000000000005.5), "half-even at .5: 5.5 -> 6");
        assertEquals(10000000000004.0, clean(10000000000004.5), "half-even at .5: 4.5 -> 4");
        assertEquals(1000000000000.0, clean(1000000000000.4), "e = 12: the cap's first decade");
        assertEquals(1000000000002.0, clean(1000000000001.5));
        assertEquals(1000000000000999.0, clean(1000000000000999.4), "e = 15");
        assertEquals(10000000000001.0, clean(10000000000001.0), "integral: never rounded");
        assertEquals("10000000000001", text(10000000000001.0));
        // the edge below the cap: at e = 11 the twelfth digit already IS the unit (k = 0), the
        // threshold 0.1 decides -- unchanged by D3
        assertEquals(100000000000.0, clean(100000000000.04), "e = 11, tail 0.04 <= 0.1: snapped");
        assertEquals(100000000000.4, clean(100000000000.4), "e = 11, tail 0.4 > 0.1: kept");
        assertEquals(999999999999.5, clean(999999999999.5), "e = 11: a .5 is outside 0.1, kept");
    }


    @Test
    void cleaningNeverInvertsTheOrder()
    {
        // D3's reason: a cleaned value never crosses an integral neighbour, so compare stays
        // monotone. Both rows were +1 under the uncapped rounding (...005.5 -> ...000).
        assertTrue(
                SUPPORT.compare(new DataValueDouble(10000000000005.0),
                        new DataValueDouble(10000000000005.5)) < 0,
                "10000000000005.0 < 10000000000005.5");
        assertTrue(
                SUPPORT.compare(new DataValueDouble(1000000000000999.0),
                        new DataValueDouble(1000000000000999.5)) < 0,
                "1000000000000999.0 < ...999.5");
        // a sweep over consecutive doubles: 3000 ulps below each start and 3000 above, so a start
        // at a decade boundary covers both decades (at 1e12 an ulp is 2^-13, so 3000 ulps is ~0.37
        // of a unit). At 1e12 both sides are k == 0 and the threshold steps from 0.1 to the D3
        // cap; at 1e11 k steps from 1 to 0; 1e-5 is a k > 0 decade; at 1e-11 the fast path meets
        // the BigDecimal branch (its lower neighbours take it). The starts cover the units floor
        // (1e12 .. 4e15) and those seams, in both signs -- non-decreasing everywhere
        for (double magnitude : new double[]
        {
                1e12, 5e12, 1e13, 3.3e13, 1e14, 7e14, 1e15, 4e15, 1e11, 1e-5, 1e-11
        })
        {
            for (double start : new double[]
            {
                    magnitude, -magnitude
            })
            {
                double v = start;
                for (int i = 0; i < 3000; i++)
                {
                    v = Math.nextDown(v);
                }
                double previous = clean(v);
                for (int i = 0; i < 6000; i++)
                {
                    v = Math.nextUp(v);
                    double c = clean(v);
                    assertTrue(c >= previous,
                            "order inverted at " + v + ": " + previous + " > " + c);
                    previous = c;
                }
            }
        }
    }

    // ------------------------------------------------------------------------- the floor


    @Test
    void theAbsoluteFloorRemovesCancellationResidueBelow1e13Only()
    {
        double residue = 0.1 + 0.2 - 0.3;
        assertEquals(5.551115123125783e-17, residue, "the example must be the real residue");
        assertEquals(0.0, clean(residue), "limit (1): a residue below 1e-13 is noise, removed");
        assertEquals("0", text(residue));
        assertEquals(0.0, clean(9.9e-14));
        assertEquals(0.0, clean(-9.9e-14));
        double larger = 5 - 4.9999999999999;
        assertEquals(1.0036416142611415e-13, larger);
        assertEquals(larger, clean(larger),
                "limit (2): a residue of 1e-13 or more cannot be told from a real small value");
        assertEquals("0.00000000000010036416142611415", text(larger));
        assertEquals(1e-13, clean(1e-13), "exactly EPSILON is not below the floor");
    }

    // ------------------------------------------------------------------ ties and k > 22


    @Test
    void aThirteenthDigitTieIsKept()
    {
        // an exact .5 at the thirteenth digit is half a unit of the twelfth digit from either
        // neighbour — five times the threshold — so the rule keeps it whichever way it would round
        assertEquals(0.08190316160375, clean(0.08190316160375));
        assertEquals(7366226.276815, clean(7366226.276815));
        assertEquals("0.08190316160375", text(0.08190316160375));
        assertEquals("7366226.276815", text(7366226.276815));
    }


    @Test
    void valuesBelow1eMinus11TakeTheExactPath()
    {
        // k = 11 - e > 22: 10^k is inexact, so the rule is evaluated in BigDecimal
        assertEquals(1.23456789012345e-12, clean(1.23456789012345e-12), "15 digits, kept");
        assertEquals(1e-12, clean(1.000000000000001e-12), "tail 1e-27 <= 1e-24: snapped");
        assertEquals("0.000000000001", text(1.000000000000001e-12));
        assertEquals(1.5e-13, clean(1.5e-13));
        assertEquals("0.00000000000015", text(1.5e-13));
    }

    // -------------------------------------------------------------------- plain notation


    @Test
    void plainNotationNeverScientificNeverSaturated()
    {
        assertEquals("12345678.9", DataValueSupport.toPlainNumberText(12345678.9),
                "D2: Double.toString says \"1.23456789E7\"");
        assertEquals("0.0001", DataValueSupport.toPlainNumberText(0.0001),
                "D2: Double.toString says \"1.0E-4\"; the trailing zero must not survive");
        assertEquals("0.0000001", DataValueSupport.toPlainNumberText(1e-7));
        assertEquals("0.00000000000025", DataValueSupport.toPlainNumberText(2.5e-13));
        assertEquals("100000000000000000000", DataValueSupport.toPlainNumberText(1e20));
        assertEquals("12", DataValueSupport.toPlainNumberText(12.0), "no .0, as SDTM writes it");
        assertEquals("-2", DataValueSupport.toPlainNumberText(-2.0));
        assertEquals("0", DataValueSupport.toPlainNumberText(-0.0));
        assertEquals("0", DataValueSupport.toPlainNumberText(0.0));
        assertEquals("3.5", DataValueSupport.toPlainNumberText(3.5));
        assertEquals("0.001", DataValueSupport.toPlainNumberText(0.001));
        assertEquals("9007199254740992", DataValueSupport.toPlainNumberText(9007199254740992.0));
        // -2^63 is integral and a long holds it: its exact digits (PLAN-grouping-key-identity L2);
        // +2^63 is beyond every long and keeps the shortest round-trip form
        assertEquals("-9223372036854775808", DataValueSupport.toPlainNumberText(-0x1p63));
        assertEquals("-9223372036854775808", DataValueSupport.toCleanText(-0x1p63));
        assertEquals("9223372036854776000", DataValueSupport.toPlainNumberText(0x1p63));
        assertEquals("NaN", DataValueSupport.toPlainNumberText(Double.NaN));
        assertEquals("Infinity", DataValueSupport.toPlainNumberText(Double.POSITIVE_INFINITY));
        assertEquals("-Infinity", DataValueSupport.toPlainNumberText(Double.NEGATIVE_INFINITY));
        String max = DataValueSupport.toPlainNumberText(Double.MAX_VALUE);
        assertEquals(309, max.length(), "Double.MAX_VALUE has 309 integer digits");
        assertTrue(max.startsWith("17976931348623157"), max);
        assertFalse(max.contains("E"), max);
        assertNotEquals("9223372036854775807", DataValueSupport.toPlainNumberText(Double.MAX_VALUE),
                "R-B2: the (long) cast saturated here");
    }


    @Test
    void plainNotationIsNotCleaning()
    {
        assertEquals("4.9999999999994", DataValueSupport.toPlainNumberText(4.9999999999994),
                "notation only: the noise is not the renderer's business");
        assertEquals("5", DataValueSupport.toCleanText(4.9999999999994));
        assertEquals("123456789012.34", DataValueSupport.toCleanText(123456789012.34));
        assertEquals("12345678.9", text(12345678.9), "the cell text is plain too");
        assertEquals("42", text(42.0));
        assertEquals("3.14159", text(3.14159));
    }

    // ------------------------------------------------------- compare and isExactValue


    @Test
    void compareRunsOnTheCleanedValues()
    {
        DataValueDouble third = new DataValueDouble(1000.0 / 3);
        DataValueDouble twelveDigits = new DataValueDouble(333.333333333);
        assertNotEquals(0, SUPPORT.compare(third, twelveDigits),
                "1000/3 keeps its 16 digits, so it is no longer equal to its 12-digit rounding");
        assertEquals(0,
                SUPPORT.compare(new DataValueDouble(4.9999999999994), new DataValueDouble(5.0)),
                "E7: noise within the threshold is one value in a sort too");
        assertTrue(
                SUPPORT.compare(new DataValueDouble(1234567890123.0),
                        new DataValueDouble(1234567890124.0)) < 0,
                "two integral values are two values");
        assertTrue(SUPPORT.compare(new DataValueDouble(19999.0), new DataValueDouble(20000.0)) < 0);
    }


    @Test
    void isExactValueFollowsTheRule()
    {
        assertFalse(DataValueSupport.isExactValue(4.9999999999994));
        assertTrue(DataValueSupport.isExactValue(333.3333333333333));
        assertTrue(DataValueSupport.isExactValue(1234567890123.0));
        assertTrue(DataValueSupport.isExactValue(0.08190316160375), "a tie is kept");
    }


    @Test
    void theRuleIsOddInTheSign()
    {
        double[] sample =
        {
                4.9999999999994, 49999.999999994, 123456.1234567, 123456789012.34, 0.99999999999994,
                1.000000000000001e-12, 0.08190316160375, 10000000000001.4, 10000000000005.5
        };
        for (double v : sample)
        {
            assertEquals(-clean(v), clean(-v), "clean(-v) == -clean(v) for " + v);
        }
        // the one exception: the floor answers +0.0 for either sign (a residue has no sign worth
        // keeping, and "0" is the text either way)
        assertEquals(0, Double.compare(0.0, clean(5.551115123125783e-17)));
        assertEquals(0, Double.compare(0.0, clean(-5.551115123125783e-17)));
    }


    @Test
    void theTwoZerosAreOneValue()
    {
        // review round 3 LOW-2: -0.0 used to be answered unchanged while the floor answered +0.0,
        // and compare is Double.compare (which orders -0.0 below 0.0) -- so a sub-floor NEGATIVE
        // sorted above zero, and -0.0 and 0.0 split although both render "0"
        assertEquals(0, Double.compare(0.0, clean(-0.0)), "-0.0 cleans to +0.0");
        assertEquals(0, Double.compare(0.0, clean(-1e-14)), "a sub-floor negative cleans to +0.0");
        assertEquals("0", text(-0.0));
        assertEquals("0", text(-1e-14));
        assertEquals(0, SUPPORT.compare(new DataValueDouble(-0.0), new DataValueDouble(0.0)),
                "compare(-0.0, 0.0) was -1");
        assertEquals(0, SUPPORT.compare(new DataValueDouble(-1e-14), new DataValueDouble(-0.0)),
                "compare(-1e-14, -0.0) was +1: a negative value sorted above zero");
        assertEquals(0, SUPPORT.compare(new DataValueDouble(-1e-14), new DataValueDouble(0.0)));
        assertTrue(SUPPORT.compare(new DataValueDouble(-1e-14), new DataValueDouble(1e-13)) < 0);
        assertTrue(SUPPORT.compare(new DataValueDouble(-1e-13), new DataValueDouble(-0.0)) < 0,
                "a value AT the floor keeps its sign and sorts below zero");
    }
}
