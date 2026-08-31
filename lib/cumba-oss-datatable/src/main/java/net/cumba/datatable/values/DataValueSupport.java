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

    /**
     * A magic number that defines the border where we allow rounding if
     * {@code abs(value) < EPSILON}.
     */
    public static final double EPSILON = 1e-13;

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

    /**
     * The number of significant digits to extract as a {@code long} for run detection. Must be &le;
     * 15 to stay within {@code long} range after scaling.
     */
    private static final int SIG_DIGITS = 15;

    /**
     * Pre-computed power-of-10 table for fast scaling without {@link Math#pow}.
     */
    private static final double[] POW10 =
    {
            1e0, 1e1, 1e2, 1e3, 1e4, 1e5, 1e6, 1e7, 1e8, 1e9, 1e10, 1e11, 1e12, 1e13, 1e14, 1e15,
            1e16, 1e17
    };

    /**
     * Minimum number of consecutive repeating digits (0s or 9s) required to consider a value as
     * imprecise in the fallback cleaning path.
     */
    private static final int MIN_RUN_LENGTH = 4;

    /**
     * Round the given value for 12 significant digits and if the difference is lower than a
     * calculated epsilon return the rounded version, otherwise the original value.<br/>
     * If the 12-digit rounding does not change the value (because it already has &le; 12
     * significant digits), a fallback detects trailing 9- or 0-runs using arithmetic and rounds to
     * the precision before the run, cleaning values like {@code -0.07757999999999} &rarr;
     * {@code -0.07758}.
     *
     * @param aValue
     *            the value to be cleaned.
     * @return the cleaned value. This can be the original value or a rounded version.
     */
    @SuppressWarnings("PMD.AvoidDecimalLiteralsInBigDecimalConstructor")
    public static double getAsDoubleCleaned(double aValue)
    {
        if (!Double.isFinite(aValue) || aValue == 0.0d)
        {
            return aValue;
        }

        // calculate difference between value and rounded version
        double absValue = Math.abs(aValue);

        if (absValue > 0 && absValue < EPSILON)
        {
            // for now we define all numbers where abs(value) < EPSILON to be displayed as 0
            return 0.0d;
        }

        @SuppressWarnings("java:S2111")
        BigDecimal bd = new BigDecimal(aValue);

        // round to 12 significant digits
        double rounded = bd.round(MC_RND).doubleValue();

        double diff = Math.abs(aValue - rounded);

        // calculate an epsilon based on value size
        double maxAbs = Math.max(Math.abs(aValue), Math.abs(rounded));

        double epsilon;
        if (maxAbs < 1)
        {
            epsilon = EPSILON;
        }
        else
        {
            epsilon = Math.max(EPSILON, Math.ulp(aValue) * 100) * maxAbs;
        }

        // if difference is not 0 but lower than epsilon we return the rounded version, otherwise
        // the original value
        if (diff > 0 && diff <= epsilon)
        {
            return rounded;
        }

        // Fallback: when rounding to 12 sig digits didn't change the value (the value already has
        // <= 12 significant digits), detect trailing 9- or 0-runs using arithmetic and round to a
        // reduced precision that eliminates the run.
        if (rounded == aValue)
        {
            int cleanPrecision = findCleanPrecision(absValue);
            if (cleanPrecision > 0 && cleanPrecision < MCS.length)
            {
                double cleanRounded = bd.round(MCS[cleanPrecision]).doubleValue();
                if (cleanRounded != aValue)
                {
                    return cleanRounded;
                }
            }
        }

        return aValue;
    }


    /**
     * Determine the number of significant digits before a trailing run of 9s or 0s using pure
     * arithmetic (no String conversion). The value is scaled to a {@code long} with
     * {@value #SIG_DIGITS} significant digits and trailing zeros from over-scaling are removed.
     * Then the remaining digits are checked for a trailing run of at least {@link #MIN_RUN_LENGTH}
     * identical 9s or 0s.
     *
     * @param aAbsValue
     *            the absolute value to analyze. Must be &gt; 0 and finite.
     * @return the number of significant digits before the run, or -1 if no qualifying run is found.
     */
    static int findCleanPrecision(double aAbsValue)
    {
        int exponent = (int) Math.floor(Math.log10(aAbsValue));
        int scaleExp = SIG_DIGITS - 1 - exponent;

        // scale the value so that it has SIG_DIGITS significant digits as a long
        double scale = (scaleExp >= 0 && scaleExp < POW10.length) ? POW10[scaleExp]
                : Math.pow(10, scaleExp);
        long sig = Math.round(aAbsValue * scale);
        int digits = SIG_DIGITS;

        // remove trailing zeros introduced by scaling beyond the value's actual precision
        while (sig > 0 && sig % 10 == 0)
        {
            sig /= 10;
            digits--;
        }

        if (digits < MIN_RUN_LENGTH + 1)
        {
            return -1;
        }

        // determine the run digit (must be 0 or 9)
        int lastDigit = (int) (sig % 10);
        int runDigit;

        if (lastDigit == 9 || lastDigit == 0)
        {
            runDigit = lastDigit;
        }
        else
        {
            // last digit may be a stray digit caused by float rounding (e.g., ...99998)
            if (digits < MIN_RUN_LENGTH + 2)
            {
                return -1;
            }
            sig /= 10;
            digits--;
            int prev = (int) (sig % 10);
            if (prev != 9 && prev != 0)
            {
                return -1;
            }
            runDigit = prev;
        }

        // count the run length
        int runLength = 0;
        long working = sig;
        while (working > 0 && (int) (working % 10) == runDigit)
        {
            runLength++;
            working /= 10;
        }

        if (runLength < MIN_RUN_LENGTH)
        {
            return -1;
        }

        int cleanPrecision = digits - runLength;

        // if the entire number is a run (e.g., 9.99999999999), round to 1 sig digit
        if (cleanPrecision <= 0)
        {
            return 1;
        }
        return cleanPrecision;
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
     *         {@link DataValueMissing} for {@link MissingValue#MIS}.
     */
    public static IDataValue getAsDataValue(@Nullable Object aValue, DataValueType aType)
    {
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
            if (aValue != null)
            {
                res = new DataValueOther(aValue);
            }
            break;
        }

        if (res != null)
        {
            // we wrapped it into the requested type!
            return res;
        }

        if (aValue == null)
        {
            // null is wrapped as DataValueMissing
            return new DataValueMissing(MissingValue.MIS);
        }

        if (aValue instanceof MissingValue missingvalue)
        {
            // keep it as missing value
            return new DataValueMissing(missingvalue);
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
     * Compare two data values. This can be used as a generic {@link Comparator} for
     * {@link IDataValue}s.
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
