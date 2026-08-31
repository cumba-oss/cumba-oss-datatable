package net.cumba.datatable.values;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/**
 * A data value that is a boolean value.
 */
@Getter
@Builder
@AllArgsConstructor
@EqualsAndHashCode
@Jacksonized
public class DataValueBoolean implements IDataValue
{

    /**
     * The boolean that is the value of this condition variable.
     */
    private final boolean value;

    @Override
    public Boolean getValue()
    {
        return value;
    }


    /**
     * Returns the value as boolean without wrapping into an object.
     *
     * @return the value as boolean without wrapping into an object.
     */
    @JsonIgnore
    public boolean getBoolean()
    {
        return value;
    }


    @Override
    @JsonIgnore
    public Number getValueAsNumber()
    {
        return value ? 1 : 0;
    }


    @JsonIgnore
    @Override
    public DataValueType getType()
    {
        return DataValueType.BOOLEAN;
    }


    @Override
    public String toString()
    {
        return Boolean.toString(value);
    }
}
