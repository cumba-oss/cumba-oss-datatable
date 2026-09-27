package net.cumba.datatable.values;

import static java.lang.System.Logger.Level.TRACE;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Comparator;
import lombok.CustomLog;
import org.jspecify.annotations.Nullable;

@CustomLog
public class DataValueSupport
{

    // OSS-IDENTITY-REGION datatable-numeric-epsilon BEGIN
    /**
     * The absolute floor of {@link #getAsDoubleCleaned(double)}: every {@code abs(value) < EPSILON}
     * cleans to {@code 0}. Why it exists and what it costs is in that method's javadoc.
     */
    public static final double EPSILON = 1e-13;
    // OSS-IDENTITY-REGION datatable-numeric-epsilon END

    /**
     * Per-type "default missing" value used when initialising new cells (synthetic added rows on
     * source columns, and added columns with empty user-supplied default). Mirrors SAS character
     * semantics: a STRING column has no missing sentinel — an empty string is its missing value —
     * while numeric / boolean / other types map to {@link MissingValue#MIS}.
     *
     * @param aType
     *            the column's data type. Must not be {@code null}.
     * @return {@code DataValueString("")} for {@link DataValueType#STRING};
     *         {@code DataValueMissing(MissingValue.MIS)} for every other type.
     */
    public static IDataValue defaultForType(DataValueType aType)
    {
        if (aType == DataValueType.STRING)
        {
            return new DataValueString("");
        }
        return new DataValueMissing(MissingValue.MIS);
    }


    /**
     * <b>The single implementation of "this cell carries no usable value"</b>: it is {@code null},
     * missing/invalid, or its string form is empty.
     *
     * <p>
     * A source {@code null} in a character column (which the Dataset-JSON and Parquet loaders
     * represent as a {@link MissingValue}) and an empty string the file genuinely contains are
     * <b>both</b> blank here, so no rule can tell them apart.
     * </p>
     *
     * <p>
     * ⚠⚠ <b>It lives on this class, not only as {@link IDataValue#isEmptyOrMissing()}, on
     * purpose.</b> {@code IDataValue} is an interface the test suite mocks in many places, and a
     * Mockito mock answers {@code false} to an <em>unstubbed default method</em> rather than
     * running it. Routing {@code ScalarSemantics.isMissing} straight at the default therefore
     * silently changed the answer for every mocked cell in the suite — measured: it reddened
     * {@code GroupKeyPolicyUnificationTest}, {@code RuleRunnerRecordKeyTest} and others, none of
     * which had anything to do with blank cells. Calling a static on a concrete class cannot be
     * intercepted that way, so the one implementation stays honest for mocks and real values alike.
     * </p>
     *
     * <p>
     * ⚠ When you have a column and a row rather than an {@link IDataValue}, prefer
     * {@link net.cumba.datatable.IDataTableColumn#isEmptyOrMissing(long)} — it answers from the raw
     * stored value and allocates nothing.
     * </p>
     *
     * @param aValue
     *            the cell to test; {@code null} counts as blank.
     * @return true if the cell is null, missing, invalid, or an empty string.
     */
    public static boolean isEmptyOrMissing(@Nullable IDataValue aValue)
    {
        return aValue == null || aValue.isMissingOrInvalid() || aValue.getValueAsString().isEmpty();
    }

    // OSS-IDENTITY-REGION datatable-numeric-mathcontext BEGIN
    /**
     * Array containing pre instantiated {@link MathContext}'s for rounding.
     */
    private static final MathContext[] MCS =
    {
            null, //
            new MathContext(1, RoundingMode.HALF_EVEN), //
            new MathContext(2, RoundingMode.HALF_EVEN), //
            new MathContext(3, RoundingMode.HALF_EVEN), //
            new MathContext(4, RoundingMode.HALF_EVEN), //
            new MathContext(5, RoundingMode.HALF_EVEN), //
            new MathContext(6, RoundingMode.HALF_EVEN), //
            new MathContext(7, RoundingMode.HALF_EVEN), //
            new MathContext(8, RoundingMode.HALF_EVEN), //
            new MathContext(9, RoundingMode.HALF_EVEN), //
            new MathContext(10, RoundingMode.HALF_EVEN), //
            new MathContext(11, RoundingMode.HALF_EVEN), //
            new MathContext(12, RoundingMode.HALF_EVEN), //
            new MathContext(13, RoundingMode.HALF_EVEN), //
            new MathContext(14, RoundingMode.HALF_EVEN)//
    };

    /**
     * Math context for rounding to 12 significant digits.
     */
    public static final MathContext MC_RND = MCS[12];
    // OSS-IDENTITY-REGION datatable-numeric-mathcontext END

    // OSS-IDENTITY-REGION datatable-numeric-cleaning BEGIN: the cleaning rule, the constants it
    // reads and the two renderings are byte-identical in cumba-datatable and cumba-oss-datatable
    // between this line and the END marker (check_oss_identity.py compares the region; the file as
    // a whole differs). EPSILON and MC_RND sit in two further regions of their own.
    /**
     * Powers of ten {@code 10^0 .. 10^22} — every one exactly representable as a double, which is
     * what makes {@link #getAsDoubleCleaned(double)}'s scaling and its {@code m / 10^k} rounding
     * correctly rounded rather than merely close. {@code 10^23} is the first inexact one; the
     * cleaner falls back to {@link BigDecimal} before it would need it.
     */
    private static final double[] POW10 =
    {
            1e0, 1e1, 1e2, 1e3, 1e4, 1e5, 1e6, 1e7, 1e8, 1e9, 1e10, 1e11, 1e12, 1e13, 1e14, 1e15,
            1e16, 1e17, 1e18, 1e19, 1e20, 1e21, 1e22
    };

    /**
     * The decade anchors {@code 10^-13 .. 10^15} used to correct the decimal exponent (index
     * {@code e + 13}); the negative entries are the nearest doubles, which classifies a value that
     * is exactly such a double as its own decade — the only ambiguous inputs, and for those the
     * cleaner answers the value itself whichever decade it is put in.
     */
    private static final double[] DECADE =
    {
            1e-13, 1e-12, 1e-11, 1e-10, 1e-9, 1e-8, 1e-7, 1e-6, 1e-5, 1e-4, 1e-3, 1e-2, 1e-1, 1e0,
            1e1, 1e2, 1e3, 1e4, 1e5, 1e6, 1e7, 1e8, 1e9, 1e10, 1e11, 1e12, 1e13, 1e14, 1e15
    };

    /**
     * The noise threshold of each decade, {@code 1e-12 * 10^e = 10^(e-12)} for {@code e} in
     * {@code [-11, 15]} (index {@code e + 11}): a tenth of the unit of the twelfth significant
     * digit.
     */
    private static final double[] THRESHOLD =
    {
            1e-23, 1e-22, 1e-21, 1e-20, 1e-19, 1e-18, 1e-17, 1e-16, 1e-15, 1e-14, 1e-13, 1e-12,
            1e-11, 1e-10, 1e-9, 1e-8, 1e-7, 1e-6, 1e-5, 1e-4, 1e-3, 1e-2, 1e-1, 1e0, 1e1, 1e2, 1e3
    };

    /** {@code log10(2)}, for the first estimate of a value's decimal exponent. */
    private static final double LOG10_2 = 0.30102999566398120d;

    /** Below this decimal exponent {@code 10^(11-e)} is no longer an exact double. */
    private static final int MIN_FAST_EXPONENT = -11;

    /**
     * Absorb floating-point noise: answer the value, or the nearest value of at most 12 significant
     * digits (from {@code 1e12}: the nearest integer, D3) when the two differ by no more than a
     * tenth of the unit of the twelfth digit.
     *
     * <p>
     * <b>Purpose</b> (owner ruling E7, 2026-09-25): the data browser works with binary data from
     * different operating systems and CPU architectures, and with calculated values, and both
     * produce floating-point noise — {@code 5.0000000000001}, {@code 5} and {@code 4.9999999999994}
     * all mean the same value, and so do {@code 49999.999999994} and {@code 50000}. Whether a value
     * is slightly off by such noise or is a very precise measurement cannot be known, so the rule
     * accepts that a genuinely precise value whose thirteenth digit onward lies within the
     * threshold is snapped as well.
     * </p>
     *
     * <p>
     * <b>The rule.</b> 12 significant digits (owner: <i>"keep it at 12"</i>). Let
     * {@code e = floor(log10 |v|)} be the value's decimal exponent and {@code r} its rounding to 12
     * significant digits. The answer is {@code r} when {@code |v - r| <= 1e-12 * 10^e}, i.e. when
     * the discarded tail is below a tenth of the twelfth digit's unit, else {@code v}. The
     * threshold is anchored on the value's <b>decade</b> (owner ruling D1), not on the value
     * itself, so that it means the same thing for every leading digit: relative to the value, a
     * value starting with 9 would have been allowed nine times the tail a value starting with 1 is.
     * </p>
     *
     * <p>
     * <b>Integral values are never rounded</b> (owner: <i>"for numbers that do not have any
     * floating point digits, the rounding does not make sense"</i>): a whole number carries no
     * noise to remove, and {@code 1234567890123} and {@code 1234567890124} are two values. That
     * also covers every double of magnitude {@code 2^52} or more, all of which are integral.
     * </p>
     *
     * <p>
     * <b>Never coarser than whole units</b> (owner ruling D3, 2026-09-25: <i>"agree to b"</i> —
     * option (b), <i>never clean coarser than whole units</i>). From {@code 1e12} upwards the
     * twelfth significant digit is a ten or more, so the 12-digit rounding would snap a value
     * across its integral neighbours — which stay exact — and invert the order
     * ({@code 10000000000005.5} would have cleaned to {@code 10000000000000}, below
     * {@code 10000000000005.0}). The rounding step is therefore capped at units: for
     * {@code e >= 12} a non-integral value snaps to its <b>nearest integer</b> ({@link Math#rint},
     * half-even at {@code .5}: {@code 10000000000005.5} cleans to {@code 10000000000006},
     * {@code 10000000000001.4} to {@code 10000000000001}), and the D1 threshold,
     * {@code 10^(e-12) >= 1} there, always accepts it. So a cleaned value never crosses an integer,
     * and {@link #compare} stays monotone. The edge: at {@code e = 11} the twelfth digit already IS
     * the unit ({@code k = 0}), so {@code [1e11, 1e12)} was unit rounding all along, with the
     * threshold {@code 0.1} deciding ({@code 100000000000.04} cleans to {@code 100000000000},
     * {@code 100000000000.4} is kept); from {@code e = 12} the cap bites and the threshold no
     * longer rejects anything.
     * </p>
     *
     * <p>
     * <b>The absolute floor: {@code |v| < 1e-13} becomes {@code 0}.</b> A relative rule measures a
     * value against itself, so the leftover of a cancellation looks perfectly precise:
     * {@code 0.1 + 0.2 - 0.3 = 5.551115123125783e-17} carries no information, but relative to
     * itself it is exact. Its noise is relative to the <em>operands</em>, and only an absolute
     * floor can remove it (kept by owner ruling, 2026-09-25). Its limits, both real: (1) a genuine
     * value below {@code 1e-13} — a base-SI femto-scale quantity, say — is lost; clinical data
     * reports such quantities in scaled units, which is why the floor was kept. (2) A cancellation
     * residue of {@code 1e-13} or more is <b>not</b> removed:
     * {@code 5 - 4.9999999999999 = 1.0036416142611415e-13} survives and renders as
     * {@code "0.00000000000010036416142611415"}, because at that size a residue cannot be told from
     * a real small value. The floor answers {@code +0.0} whatever the sign, and so does a
     * {@code -0.0} input: {@link #compare} is {@code Double.compare}, which orders {@code -0.0}
     * below {@code 0.0}, so an unnormalised {@code -0.0} sorted {@code -1e-14} <em>above</em> zero
     * and split two cells reading {@code "0"}.
     * </p>
     *
     * <p>
     * <b>Implementation.</b> Called on the hot path — once per rendered DOUBLE cell, per text key,
     * per comparison — so the common case allocates nothing: an integral check, a table-driven
     * decimal exponent, one multiply, one {@link Math#rint}, one divide. The scaled value
     * {@code v * 10^k} is correctly rounded and {@code m / 10^k} is the double nearest the 12-digit
     * decimal, both because every power of ten used is exact. For {@code k > 0} a thirteenth-digit
     * tie ({@code scaled} ending in exactly {@code .5}) lies half a unit of the twelfth digit from
     * either neighbour — five times the threshold — so it is kept without deciding which way it
     * would round; at {@code k = 0} (units) the threshold is at least {@code 0.1} of a unit and a
     * {@code .5} is decided by {@code rint}'s half-even, as ruling D3 states. Only
     * {@code |v| < 1e-11} needs {@code 10^23} or more and goes through {@link BigDecimal}, where
     * the rule is evaluated exactly.
     * </p>
     *
     * @param aValue
     *            the value to be cleaned.
     * @return the cleaned value: {@code aValue} itself, its 12-significant-digit rounding (from
     *         {@code 1e12}: its nearest integer, D3), or {@code 0.0} below the floor — and
     *         {@code 0.0} for {@code -0.0}, so that the two zeros, one value and one text, are one
     *         value to {@link #compare} as well. NaN and the infinities are answered as they are.
     */
    public static double getAsDoubleCleaned(double aValue)
    {
        if (!Double.isFinite(aValue))
        {
            return aValue;
        }
        if (aValue == 0.0d)
        {
            // -0.0 too: the floor answers +0.0 for a sub-1e-13 negative, and compare is
            // Double.compare, which orders -0.0 below 0.0 -- so an unnormalised -0.0 sorted a
            // negative value ABOVE zero and split "0" from "0". One zero, as one text.
            return 0.0d;
        }
        if (aValue == Math.rint(aValue))
        {
            // integral: never rounded (E7); every |v| >= 2^52 ends here
            return aValue;
        }
        double abs = Math.abs(aValue);
        if (abs < EPSILON)
        {
            // the absolute floor, see the javadoc
            return 0.0d;
        }
        int e = decimalExponent(abs);
        if (e < MIN_FAST_EXPONENT)
        {
            // k = 11 - e > 22: 10^k is no longer exact, evaluate the rule exactly instead
            return cleanViaBigDecimal(aValue);
        }
        // 12 significant digits, but never coarser than whole units (D3): k = 11 - e would be
        // negative from e = 12, where the twelfth digit is a ten -- capped at 0, the rounding step
        // is rint(v) there and the threshold (>= 1) always accepts; k in [0, 22]
        int k = Math.max(0, 11 - e);
        double scaled = aValue * POW10[k]; // |scaled| < 10^12 for k > 0, < 2^52 for k == 0
        if (k > 0 && scaled - Math.floor(scaled) == 0.5d)
        {
            // a thirteenth-digit tie is half a unit of the twelfth digit away from either
            // rounding, five times the threshold: kept, whichever way it would round. At k == 0
            // the threshold is >= 0.1 of a unit and rint's half-even decides (D3).
            return aValue;
        }
        double rounded = Math.rint(scaled) / POW10[k];
        return Math.abs(aValue - rounded) <= THRESHOLD[e - MIN_FAST_EXPONENT] ? rounded : aValue;
    }


    /**
     * {@code floor(log10 aAbs)} for a finite {@code aAbs} in {@code [1e-13, 2^52)}: a first
     * estimate from the binary exponent, then at most one upward correction against the decade
     * table (the estimate is never too high, and never more than one too low).
     */
    private static int decimalExponent(double aAbs)
    {
        int e = (int) Math.floor(Math.getExponent(aAbs) * LOG10_2);
        if (e < 15 && aAbs >= DECADE[e + 14])
        {
            e++;
        }
        return e;
    }


    /**
     * The cleaning rule evaluated exactly, for the values the table-driven path cannot scale
     * ({@code |v| < 1e-11}): the decimal exponent, the 12-digit rounding and the threshold
     * comparison all in {@link BigDecimal} on the value's exact binary expansion.
     */
    @SuppressWarnings("PMD.AvoidDecimalLiteralsInBigDecimalConstructor")
    private static double cleanViaBigDecimal(double aValue)
    {
        BigDecimal exact = new BigDecimal(aValue);
        double rounded = exact.round(MC_RND).doubleValue();
        int e = exact.precision() - exact.scale() - 1;
        BigDecimal diff = exact.subtract(new BigDecimal(rounded)).abs();
        return diff.compareTo(BigDecimal.ONE.scaleByPowerOfTen(e - 12)) <= 0 ? rounded : aValue;
    }


    /**
     * A number as plain decimal text — <b>notation only, no cleaning</b> (owner ruling D2,
     * 2026-09-25). An integral value renders without a fractional part ({@code 12.0} is
     * {@code "12"}, as SDTM writes it, and {@code -0.0} is {@code "0"}); any other value renders
     * its shortest round-trip digits ({@link Double#toString(double)}) in plain notation, never in
     * scientific notation: {@code 12345678.9} is {@code "12345678.9"}, {@code 0.0001} is
     * {@code "0.0001"}, {@code 1e20} is {@code "100000000000000000000"} (no {@code long}
     * saturation). NaN and the infinities render as {@link String#valueOf(double)} does. The text
     * is lossless: two different finite doubles never render the same — bar {@code 0.0} and
     * {@code -0.0}, which are one value and both {@code "0"}.
     *
     * @param aValue
     *            the value to render.
     * @return the plain decimal text.
     */
    public static String toPlainNumberText(double aValue)
    {
        if (Double.isNaN(aValue) || Double.isInfinite(aValue))
        {
            return String.valueOf(aValue);
        }
        if (aValue == 0.0d)
        {
            return "0"; // -0.0 too
        }
        if (aValue == Math.rint(aValue) && Math.abs(aValue) < 0x1p63)
        {
            return Long.toString((long) aValue); // no ".0"
        }
        String text = Double.toString(aValue); // shortest round-trip digits
        if (text.indexOf('E') < 0)
        {
            return text; // already plain
        }
        return new BigDecimal(text).stripTrailingZeros().toPlainString();
    }


    /**
     * The text of a DOUBLE cell — for display, for reports and for every join or grouping that
     * compares keys as text: the value cleaned by {@link #getAsDoubleCleaned(double)}, then
     * rendered by {@link #toPlainNumberText(double)}. The cleaning is a projection, stated per
     * value: a value within {@code 10^(e-12)} of its 12-significant-digit rounding renders as that
     * rounding (from {@code 1e12}: a non-integral value renders as its nearest integer, D3 —
     * {@code 10000000000005.5} is {@code "10000000000006"}), any other value renders its own
     * digits. So {@code 4.9999999999991} and {@code 5.0000000000009} both render {@code "5"}, while
     * {@code 5.0000000000009} and {@code 5.0000000000011} — closer to each other than either is to
     * {@code 5} — render differently: sharing a text is not a distance between two values.
     *
     * @param aValue
     *            the value to render.
     * @return the cleaned value's plain decimal text.
     */
    public static String toCleanText(double aValue)
    {
        return toPlainNumberText(getAsDoubleCleaned(aValue));
    }

    // OSS-IDENTITY-REGION datatable-numeric-cleaning END


    /**
     * A test if the given value is an exact value. This method compares the given value by the one
     * returned from {@link #getAsDoubleCleaned(double)}. If both are equal, the value is considered
     * exact.
     *
     * @param aValue
     *            the value to test.
     * @return true if the value is an exact value, false otherwise.
     */
    public static boolean isExactValue(double aValue)
    {
        return getAsDoubleCleaned(aValue) == aValue;
    }


    /**
     * Create a {@link IDataValue} instance from the given value object by aiming for the given
     * type.
     *
     * @param aValue
     *            the value to wrap into a {@link IDataValue}.
     * @param aType
     *            the type to aim for. If possible, the value will be wrapped into a data value of
     *            this type.
     * @return a IDataValue that wraps the given object. If aValue is null, this is a
     *         {@link DataValueMissing} for {@link MissingValue#MIS}; if aValue already is a
     *         {@link MissingValue} it is a {@link DataValueMissing} for that value, whatever the
     *         requested type — a missing cell stays missing on a character column too.
     */
    public static IDataValue getAsDataValue(@Nullable Object aValue, DataValueType aType)
    {
        if (aValue == null)
        {
            // null is wrapped as DataValueMissing
            return new DataValueMissing(MissingValue.MIS);
        }

        if (aValue instanceof MissingValue missingvalue)
        {
            // ⭐ A stored MissingValue keeps its identity for EVERY target type: whether a cell
            // is missing cannot depend on the column's type. Resolved BEFORE the switch on
            // purpose. getAsDataValueString wraps anything non-null through toString(), so a
            // missing cell used to become the plain string ".", ".A" or "NA" and answered
            // "not missing" — on exactly the character columns that Dataset-JSON, Parquet and
            // .cdt store a MissingValue in.
            return new DataValueMissing(missingvalue);
        }

        IDataValue res = null;
        switch (aType)
        {
        case LONG:
            res = getAsDataValueLong(aValue);
            break;

        case DOUBLE:
            res = getAsDataValueDouble(aValue);
            break;
        case STRING:
            res = getAsDataValueString(aValue);
            break;
        case BOOLEAN:
            res = getAsDataValueBoolean(aValue);
            break;
        case MISSING:
            res = getAsDataValueMissing(aValue);
            break;
        case OTHER:
        default:
            // The null guard this arm used to carry is gone: null is answered above the switch, so
            // Error Prone reports the check as AlreadyChecked (-Werror).
            res = new DataValueOther(aValue);
            break;
        }

        if (res != null)
        {
            // we wrapped it into the requested type!
            return res;
        }

        if (aValue instanceof String str)
        {
            // here this is a STRING but should be wrapped into something else (e.g. LONG or DOUBLE)

            // try to parse as missing
            try
            {
                MissingValue mv = MissingValue.valueOf(str);
                return new DataValueMissing(mv);
            }
            catch (Exception e)
            {
                LOGGER.log(TRACE, "not a missing-value sentinel; keep it as plain string", e);
            }

            // keep it as string
            return new DataValueString(str);
        }

        // last fallback is to be wrapped into OTHER.
        return new DataValueOther(aValue);
    }


    /**
     * Retrieve a IDataValue from the given object and try to convert it into a
     * {@link DataValueLong}.
     *
     * @param aValue
     *            the value to wrap into a IDataValue.
     * @return a IDataValue for the given value or null, if this
     */
    protected static @Nullable DataValueLong getAsDataValueLong(@Nullable Object aValue)
    {
        if (aValue instanceof Number num)
        {
            // number can be accessed as long
            return new DataValueLong(num.longValue());
        }

        if (aValue instanceof String str)
        {
            // try to parse as long
            try
            {
                return new DataValueLong(Long.parseLong(str));
            }
            catch (Exception e)
            {
                LOGGER.log(TRACE, "not parseable as long; fall through and return null", e);
            }
        }
        // no way to access as long
        return null;
    }


    /**
     * Retrieve a IDataValue from the given object and try to convert it into a
     * {@link DataValueDouble}.
     *
     * @param aValue
     *            the value to wrap into a IDataValue.
     * @return a IDataValue for the given value or null, if this
     */
    protected static @Nullable DataValueDouble getAsDataValueDouble(@Nullable Object aValue)
    {
        if (aValue instanceof Number num)
        {
            // number can be accessed as double
            return new DataValueDouble(num.doubleValue());
        }

        if (aValue instanceof String str)
        {
            // try to parse as long
            try
            {
                return new DataValueDouble(Double.parseDouble(str));
            }
            catch (Exception e)
            {
                LOGGER.log(TRACE, "not parseable as double; fall through and return null", e);
            }
        }
        // no way to access as double
        return null;
    }


    /**
     * Retrieve a IDataValue from the given object and try to convert it into a
     * {@link DataValueBoolean}.
     *
     * @param aValue
     *            the value to wrap into a IDataValue.
     * @return a IDataValue for the given value or null, if this
     */
    protected static @Nullable DataValueBoolean getAsDataValueBoolean(@Nullable Object aValue)
    {
        if (aValue instanceof Boolean b)
        {
            // this is a boolean
            return new DataValueBoolean(b);
        }

        if (aValue instanceof Number num)
        {
            // we can convert numbers to boolean values
            return new DataValueBoolean(num.doubleValue() != 0);
        }

        if (aValue instanceof String str)
        {
            // we can convert some strings to boolean
            if (str.equalsIgnoreCase("true"))
            {
                return new DataValueBoolean(true);
            }
            if (str.equalsIgnoreCase("false"))
            {
                return new DataValueBoolean(false);
            }

            // try to parse as number and convert from number to boolean
            try
            {
                double val = Double.parseDouble(str);
                return new DataValueBoolean(val != 0);
            }
            catch (Exception e)
            {
                LOGGER.log(TRACE, "not parseable as number; fall through and return null", e);
            }
        }
        // no way to access as boolean
        return null;
    }


    /**
     * Retrieve a IDataValue from the given object and try to convert it into a
     * {@link DataValueMissing}.
     *
     * @param aValue
     *            the value to wrap into a IDataValue.
     * @return a IDataValue for the given value or null, if this
     */
    protected static @Nullable DataValueMissing getAsDataValueMissing(@Nullable Object aValue)
    {
        if (aValue instanceof MissingValue m)
        {
            // this is a missing
            return new DataValueMissing(m);
        }

        if (aValue instanceof Number num)
        {
            // we can convert a number to a missing
            return new DataValueMissing(num.byteValue());
        }

        if (aValue instanceof String str)
        {
            // try to parse String as missing
            try
            {
                return new DataValueMissing(MissingValue.valueOf(str));
            }
            catch (Exception e)
            {
                LOGGER.log(TRACE, "not a missing-value sentinel; fall through and return null", e);
            }
        }
        // no way to access as boolean
        return null;
    }


    /**
     * Retrieve a IDataValue from the given object and try to convert it into a
     * {@link DataValueString}.
     *
     * @param aValue
     *            the value to wrap into a IDataValue.
     * @return a IDataValue for the given value or null, if this
     */
    protected static @Nullable DataValueString getAsDataValueString(@Nullable Object aValue)
    {
        if (aValue == null)
        {
            return null;
        }
        return new DataValueString(aValue.toString());
    }


    /**
     * Identify the {@link MissingValue} a data value carries, if any. Recognizes both the explicit
     * form — a value whose {@code getValue()} is a {@link MissingValue}, i.e. a
     * {@link DataValueMissing} — and the encoded form: a DOUBLE-typed value whose double is a NaN
     * carrying the missing-value byte in its mantissa payload. A DOUBLE NaN without a known payload
     * maps through {@link MissingValue#forNaN(double)}, mirroring the numeric buffers' decoding: a
     * bare NaN is {@link MissingValue#MIS} (owner ruling E5, <i>"NaN should get mis"</i>), any
     * other unrecognised payload {@link MissingValue#MIS_UNKNOWN} (ruling N1). A STRING (or any
     * other non-DOUBLE) value never counts as missing here, no matter what its numeric
     * interpretation would be.
     *
     * @param aValue
     *            the value to inspect.
     * @return the missing value the given data value carries, or null if it is not missing.
     */
    public static @Nullable MissingValue getMissingValue(IDataValue aValue)
    {
        if (aValue.getValue() instanceof MissingValue mv)
        {
            return mv;
        }
        if (aValue.getType() == DataValueType.DOUBLE)
        {
            double val = aValue.getValueAsDouble();
            if (Double.isNaN(val))
            {
                return MissingValue.forNaN(val);
            }
        }
        return null;
    }


    /**
     * Compare two data values. This can be used as a generic {@link Comparator} for
     * {@link IDataValue}s.
     *
     * <p>
     * <b>Missing values (SAS semantics, F-dt-06):</b> a missing value sorts BELOW any non-missing
     * value — numeric or string — and missings order among themselves by their missing-value byte,
     * which reproduces the SAS collating sequence ({@code ._ < . < .A < ... < .Z}). This includes
     * DOUBLE-typed values that carry a missing encoded as a NaN payload; {@code Double.compare}
     * alone would sort every NaN LAST and call all special missings equal.
     * </p>
     *
     * <p>
     * <b>DOUBLE values are compared normalised:</b> both sides pass through
     * {@link #getAsDoubleCleaned(double)} first, so sub-{@code 1e-13} magnitudes flatten to 0 and
     * noise within the ruled threshold folds onto the 12-significant-digit value (from
     * {@code 1e12}: the nearest integer, D3) before comparison — {@code 4.9999999999994} and
     * {@code 5} compare equal, {@code 1000 / 3} and {@code 333.333333333} do not, and {@code -0.0},
     * {@code 0.0} and a sub-floor negative all compare equal (the cleaning answers {@code 0.0} for
     * each).
     * </p>
     *
     * @param aValue1
     *            the first value to compare.
     * @param aValue2
     *            the second value to compate.
     * @return an integer that provides information to build a order.
     * @see Comparator#compare(Object, Object)
     */
    public int compare(@Nullable IDataValue aValue1, @Nullable IDataValue aValue2)
    {
        if (aValue1 == null)
        {
            return aValue2 == null ? 0 : 1;
        }
        else if (aValue2 == null)
        {
            return -1;
        }

        IDataValue val1 = aValue1;
        IDataValue val2 = aValue2;

        // F-dt-06: missing values sort below every non-missing value and among themselves by
        // their missing-value byte (the SAS collating sequence). Handling them here — before the
        // type routing — also keeps NaNs out of the DOUBLE arm's Double.compare, whose total
        // order would otherwise sort missings last and collapse all special missings into one.
        MissingValue mis1 = getMissingValue(val1);
        MissingValue mis2 = getMissingValue(val2);
        if (mis1 != null || mis2 != null)
        {
            if (mis1 != null && mis2 != null)
            {
                return Integer.compare(mis1.getValue() & 0xFF, mis2.getValue() & 0xFF);
            }
            return mis1 != null ? -1 : 1;
        }

        if (val1.getType() != val2.getType())
        {
            // both values have different type (this should not occur)
            if (val1 instanceof IDataValueNumber n1 && val2 instanceof IDataValueNumber n2)
            {
                // both are numeric --> compare as number
                return Double.compare(n1.getValueAsDouble(), n2.getValueAsDouble());
            }
            // fallback to compare as string
            return val1.getValueAsString().compareTo(val2.getValueAsString());
        }

        // both values have the same type, compare by type
        switch (val1.getType())
        {
        case DOUBLE:
        {
            double v1 = getAsDoubleCleaned(val1.getValueAsDouble());
            double v2 = getAsDoubleCleaned(val2.getValueAsDouble());

            return Double.compare(v1, v2);
        }
        case LONG:
        {
            long v1 = val1.getValueAsNumber().longValue();
            long v2 = val2.getValueAsNumber().longValue();
            return Long.compare(v1, v2);
        }
        case STRING:
        default:
            return val1.getValueAsString().compareTo(val2.getValueAsString());
        }

    }
}
