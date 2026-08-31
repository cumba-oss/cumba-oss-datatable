package net.cumba.datatable.values;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/**
 * A data value that is a long value.
 */
@Getter
@Builder
@AllArgsConstructor
@EqualsAndHashCode
@Jacksonized
public class DataValueLong implements IDataValueNumber
{

    /**
     * The long that is the value of this condition variable.
     */
    private final long value;

    @Override
    public Long getValue()
    {
        return value;
    }


    /**
     * Returns the value as long without wrapping into an object.
     *
     * @return the value as long without wrapping into an object.
     */
    @JsonIgnore
    public long getLong()
    {
        return value;
    }


    @Override
    @JsonIgnore
    public Number getValueAsNumber()
    {
        return value;
    }


    @Override
    @JsonIgnore
    public long getValueAsLong()
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
    public DataValueType getType()
    {
        return DataValueType.LONG;
    }


    @Override
    public String toString()
    {
        return Long.toString(value);
    }
}
