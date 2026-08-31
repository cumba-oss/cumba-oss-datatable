package net.cumba.datatable.values;

/**
 * Interface for all numeric {@link IDataValue}'s.
 *
 * @see DataValueDouble
 * @see DataValueLong
 */
public interface IDataValueNumber extends IDataValue
{

    /**
     *
     * @return the data value as a number.
     */
    @Override
    Number getValueAsNumber();


    /**
     *
     * @return the value as a double. If {@link #getValueAsNumber()} returns null, this function
     *         returns {@link Double#NaN}.
     */
    @Override
    default double getValueAsDouble()
    {
        Number num = getValueAsNumber();
        return num != null ? num.doubleValue() : Double.NaN;
    }


    /**
     * Returns the value as long.
     *
     * @return the value as long. If {@link #getValueAsNumber()} returns null, this function returns
     *         {@link Long#MIN_VALUE}.
     */
    default long getValueAsLong()
    {
        Number num = getValueAsNumber();
        return num != null ? num.longValue() : Long.MIN_VALUE;
    }
}
