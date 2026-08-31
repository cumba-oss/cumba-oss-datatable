package net.cumba.datatable.values;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.jackson.Jacksonized;

/**
 * A data value that is a String value.
 */
@Getter
@Builder
@AllArgsConstructor
@EqualsAndHashCode
@Jacksonized
public class DataValueString implements IDataValue
{

    /**
     * The string that is the value of this condition variable.
     */
    @NonNull
    private final String value;

    @JsonIgnore
    @Override
    public DataValueType getType()
    {
        return DataValueType.STRING;
    }


    @Override
    @JsonIgnore
    public double getValueAsDouble()
    {
        return Double.NaN;
    }


    @Override
    public String toString()
    {
        return "\"" + value + "\"";
    }
}
