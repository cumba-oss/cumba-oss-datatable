package net.cumba.datatable.values;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.jackson.Jacksonized;

/**
 * A data value that is an other value.
 */
@Getter
@Builder
@AllArgsConstructor
@EqualsAndHashCode
@Jacksonized
public class DataValueOther implements IDataValue
{

    /**
     * The object that is the value of this condition variable.
     */
    @NonNull
    private final Object value;

    @Override
    @JsonIgnore
    public DataValueType getType()
    {
        return DataValueType.OTHER;
    }


    @Override
    public String toString()
    {
        return value.toString();
    }
}
