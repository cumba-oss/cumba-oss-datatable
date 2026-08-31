package net.cumba.datatable.values;

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


    @Override
    @JsonIgnore
    public String getValueAsString()
    {
        double cleaned = DataValueSupport.getAsDoubleCleaned(value);

        // for double values that are integer / long numbers, we use the String.valueOf((long)...)
        // to avoid the .0
        if (cleaned == Math.floor(cleaned) && !Double.isInfinite(cleaned))
        {
            return String.valueOf((long) cleaned);
        }

        return String.valueOf(cleaned);
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
