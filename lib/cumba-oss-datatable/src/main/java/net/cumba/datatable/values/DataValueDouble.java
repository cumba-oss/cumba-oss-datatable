package net.cumba.datatable.values;

// OSS-IDENTITY datatable-numeric-text: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/**
 * A data value that is a double value.
 */
@Getter
@Builder
@AllArgsConstructor
@EqualsAndHashCode
@Jacksonized
public class DataValueDouble implements IDataValueNumber
{

    public static final double EPSILON = 1E-14;

    /**
     * A double with its zero sign dropped: {@code -0.0} answers {@code 0.0}; every other value
     * &mdash; every NaN payload included &mdash; is answered as given. ⭐ Owner direction 2026-09-27
     * (register {@code NZL O1}, PLAN-negative-zero-on-load): <i>"a -0.0 gets read as 0.0"</i>. One
     * raw-bits compare, true only for {@code -0.0}, so on real data the branch is never taken; a
     * NaN's exponent bits are all ones, so no NaN payload can match.
     *
     * @param aValue
     *            the value.
     * @return {@code 0.0} for {@code -0.0}, else {@code aValue} itself.
     */
    public static double dropZeroSign(double aValue)
    {
        return Double.doubleToRawLongBits(aValue) == Long.MIN_VALUE ? 0.0d : aValue;
    }


    /**
     * The double a DOUBLE buffer stores for a value it is given: {@link #dropZeroSign(double)}
     * (register {@code NZL O1}: no loaded table holds a {@code -0.0}), then a bare NaN as
     * {@link MissingValue#MIS}'s payload ({@link MissingValue#normalizeBareNaN(double)}, owner
     * ruling {@code BNM N2} (b)); every other value as given. The default
     * {@code DataBufferFactory}'s DOUBLE buffers call this on every store, in both datatable twins.
     *
     * @param aValue
     *            the value about to be stored.
     * @return the value the buffer stores.
     */
    public static double normalizeForStore(double aValue)
    {
        return MissingValue.normalizeBareNaN(dropZeroSign(aValue));
    }


    /**
     * A check if the given double value is an exact float value. The check handles Double.NaN with
     * custom mantissa values (e.g encoded missing values).
     *
     * @param aValue
     *            the value to check.
     * @return true if the given value keeps its exact value after being round trip converted to
     *         float.
     */
    public static boolean isFloatExact(double aValue)
    {
        float f = (float) aValue;
        return Double.doubleToRawLongBits(aValue) == Double.doubleToRawLongBits(f);
    }


    /**
     * A check if the given double represents the same value when converted to float. This check is
     * a toString check. This allows to store values like 0.1d as float, where the value is not
     * round trip compatible, but a conversion is still possible. When these values are stored and
     * accessed as double a simple cast will bring the wrong result and a method like
     * {@link #fromFloatRepresentable(float)} must be used to retrieve the correct double value.
     *
     * @param aValue
     *            the value to be tested.
     * @return true if the value can be converted.
     */
    public static boolean isFloatRepresentable(double aValue)
    {
        float f = (float) aValue;
        return Double.doubleToRawLongBits(aValue) == Double.doubleToRawLongBits(f)
                || Double.toString(aValue).equals(Float.toString(f));
    }


    /**
     * Retrieve the given double as a float by converting it to a String and parsing the String
     * back. This is slow, but ensures the value is converted in its meaning.
     *
     * @param aValue
     *            the value to convert.
     * @return the float that represents the same value as the double, at least if this is possible
     *         in float.
     */
    public static float asFloatRepresentable(double aValue)
    {
        return Float.parseFloat(Double.toString(aValue));
    }


    /**
     * Retrieve the given float as a double by converting it to a String and parsing the String
     * back. This is slow, but ensures the value is converted in its meaning.
     *
     * @param aValue
     *            the value to convert.
     * @return the double that represents the same value as the given float.
     */
    public static double fromFloatRepresentable(float aValue)
    {
        return Double.parseDouble(Float.toString(aValue));
    }

    /**
     * The double that is the value of this condition variable.
     */
    private final double value;

    @Override
    public Double getValue()
    {
        return value;
    }


    @Override
    @JsonIgnore
    public boolean isMissingOrInvalid()
    {
        return Double.isNaN(value);
    }


    @Override
    @JsonIgnore
    public DataValueType getType()
    {
        return DataValueType.DOUBLE;
    }


    /**
     * The cell's text: the value cleaned of floating-point noise
     * ({@link DataValueSupport#getAsDoubleCleaned(double)}) and rendered in plain notation
     * ({@link DataValueSupport#toPlainNumberText(double)}) — an integral value without {@code .0},
     * never scientific notation, never saturated. This is the text of every display, report and
     * text-keyed join or grouping of a DOUBLE cell, so it is lossless outside the ruled noise:
     * {@code 1234567890123} and {@code 1234567890124} are two texts.
     */
    @Override
    @JsonIgnore
    public String getValueAsString()
    {
        return DataValueSupport.toCleanText(value);
    }


    @Override
    @JsonIgnore
    public Number getValueAsNumber()
    {
        return value;
    }


    @Override
    @JsonIgnore
    public double getValueAsDouble()
    {
        return value;
    }


    @Override
    @JsonIgnore
    public long getValueAsLong()
    {
        return (long) value;
    }


    @Override
    public String toString()
    {
        return getValueAsString();
    }
}
