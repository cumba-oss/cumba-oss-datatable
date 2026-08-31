package net.cumba.datatable.values;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/**
 * A data value that is a missing value.
 */
@Getter
@Builder
@AllArgsConstructor
@EqualsAndHashCode
@Jacksonized
public class DataValueMissing implements IDataValue
{

    /**
     * The byte that describes the missing value of this condition variable.
     */
    private final MissingValue value;

    public DataValueMissing(int aValue)
    {
        this(MissingValue.forValue(aValue));
    }


    @Override
    public MissingValue getValue()
    {
        return value;
    }


    @Override
    @JsonIgnore
    public double getValueAsDouble()
    {
        return Double.NaN;
    }


    /**
     * Returns the missing value as byte without wrapping into an object.
     *
     * @return the missing value as byte without wrapping into an object.
     */
    @JsonIgnore
    public byte getByteValue()
    {
        return value.getValue();
    }


    @Override
    @JsonIgnore
    public DataValueType getType()
    {
        return DataValueType.MISSING;
    }


    @Override
    public String toString()
    {
        return value.toString();
    }
}
