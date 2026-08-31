package net.cumba.datatable.values;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Interface for data values.
 *
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
{
        @JsonSubTypes.Type(value = DataValueString.class, name = "String"),
        @JsonSubTypes.Type(value = DataValueLong.class, name = "long"),
        @JsonSubTypes.Type(value = DataValueDouble.class, name = "double"),
        @JsonSubTypes.Type(value = DataValueBoolean.class, name = "boolean"),
        @JsonSubTypes.Type(value = DataValueMissing.class, name = "missing"),
        @JsonSubTypes.Type(value = DataValueOther.class, name = "other")
})
public interface IDataValue
{

    /**
     * Returns an object that is the internal value of this data value.
     *
     * @return an object that is the internal value of this data value.<br/>
     *         <b>This is NOT allowed to be null.</b> Null values must be mapped to
     *         {@link MissingValue}, {@code Double.NaN} or anything else.
     */
    Object getValue();


    /**
     * Returns the type of the internal value and how to interpret it.
     *
     * @return the type of the internal value and how to interpret it.
     */
    @JsonIgnore
    DataValueType getType();


    /**
     * Returns true if the internal value is either a {@link MissingValue} or an invalid value like
     * {@link Double#NaN}.
     * <p>
     * ⚠ <b>This asks only whether the cell carries a missing marker.</b> It answers {@code false}
     * for a zero-length string, so a blank <em>character</em> cell is <b>not</b> missing by this
     * predicate. For blankness in rule evaluation you almost certainly want
     * {@link #isEmptyOrMissing()} — or, better, the allocation-free
     * {@link net.cumba.datatable.IDataTableColumn#isEmptyOrMissing(long)}. The two names look
     * alike, have opposite blast radius, and choosing wrongly fails <em>silently</em>.
     * </p>
     *
     * @return true if the internal value is either a {@link MissingValue} or an invalid value like
     *         {@link Double#NaN}.<br/>
     *         The default implementation only checks for {@link MissingValue}'s, but this might be
     *         overwritten by special implementations.
     */
    @JsonIgnore
    default boolean isMissingOrInvalid()
    {
        return getValue() instanceof MissingValue;
    }


    /**
     * Returns true when this cell carries <b>no usable value</b>: it is missing/invalid, or its
     * string form is empty.
     * <p>
     * This is the blankness notion rule evaluation needs — a source {@code null} in a character
     * column (represented as a {@link MissingValue}) and an empty string the file genuinely
     * contains are <b>both</b> blank, so no rule can tell the two apart.
     * </p>
     * <p>
     * ⚠ <b>Only use this overload when you already hold the {@link IDataValue}.</b> Obtaining one
     * just to ask the question allocates — prefer
     * {@link net.cumba.datatable.IDataTableColumn#isEmptyOrMissing(long)} or
     * {@link net.cumba.datatable.IDataTable#isEmptyOrMissing(long, int)}, which answer from the raw
     * stored value.
     * </p>
     *
     * @return true if this cell is missing, invalid, or an empty string.
     */
    @JsonIgnore
    default boolean isEmptyOrMissing()
    {
        return DataValueSupport.isEmptyOrMissing(this);
    }


    /**
     * Returns a String representation of {@link #getValue()}.
     *
     * @return a String representation of {@link #getValue()}. If the value is null the String
     *         <b><code>null</code></b> is returned.
     */
    @JsonIgnore
    default String getValueAsString()
    {
        Object val = getValue();
        return val != null ? val.toString() : "null";
    }


    /**
     * Try to retrieve the value as a number.
     *
     * @return the value as Number, if {@link #getValue()} returns a Number, {@link Double#NaN}
     *         otherwise.
     */
    @JsonIgnore
    default Number getValueAsNumber()
    {
        Object val = getValue();
        if (val instanceof Number n)
        {
            return n;
        }
        return Double.NaN;
    }


    /**
     * Returns the value as a double value if the value is numeric, {@link Double#NaN} otherwise.
     *
     * @return the value as a double value if the value is numeric, {@link Double#NaN} otherwise.
     */
    @JsonIgnore
    default double getValueAsDouble()
    {
        Object val = getValue();
        if (val instanceof Number n)
        {
            return n.doubleValue();
        }
        return Double.NaN;
    }

}
