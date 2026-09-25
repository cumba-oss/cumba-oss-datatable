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
 * The rows that carry the plan's sensitivity arms: re-inserting the old magnitude scaling reddens
 * the non-integral rows beyond 12 digits and the owner's {@code 4.9999999999994}; removing the
 * integral short-circuit reddens the {@code 1234567890123 != 1234567890124} rows.
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
        // both rendered "1.23456789012E12" under the old rule: one text for two values
        assertEquals(1234567890123.4, clean(1234567890123.4));
        assertEquals(1234567890123.3, clean(1234567890123.3));
        assertEquals("1234567890123.4", text(1234567890123.4));
        assertEquals("1234567890123.3", text(1234567890123.3));
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
    void theSnapInsideTheIntegralDecadesIsStated()
    {
        // [1e11, 2^52): a fraction within 10^(e-12) snaps onto the integer, the ruling applied
        assertEquals(1e13, clean(10000000000001.4));
        assertEquals("10000000000000", text(10000000000001.4));
        assertEquals("10000000000000", text(1e13));
        assertEquals(10000000000001.0, clean(10000000000001.0), "integral: never rounded");
        assertEquals("10000000000001", text(10000000000001.0));
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
    void valuesBelow1e11TakeTheExactPath()
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
        assertEquals("1234567890123.4", DataValueSupport.toCleanText(1234567890123.4));
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
                4.9999999999994, 49999.999999994, 123456.1234567, 1234567890123.4, 0.99999999999994,
                1.000000000000001e-12, 0.08190316160375, 10000000000001.4
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
}
