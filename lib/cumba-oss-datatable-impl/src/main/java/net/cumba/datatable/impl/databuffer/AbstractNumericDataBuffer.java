package net.cumba.datatable.impl.databuffer;

import net.cumba.datatable.values.DataValueBoolean;
import net.cumba.datatable.values.DataValueDouble;
import net.cumba.datatable.values.DataValueLong;
import net.cumba.datatable.values.DataValueMissing;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.IDataValue;
import net.cumba.datatable.values.MissingValue;

/**
 * Abstract base class for numeric data buffers. Provides an optimized {@link #createDataValue}
 * implementation that uses {@link IDataBufferNumeric#getValueAsDouble} and
 * {@link IDataBufferNumeric#getValueAsLong} to avoid boxing overhead.
 */
public abstract class AbstractNumericDataBuffer extends AbstractDataBuffer
        implements
        IDataBufferNumeric
{

    @Override
    protected IDataValue createDataValue(int aIndex, DataValueType aType)
    {
        if (isMissing(aIndex))
        {
            Object raw = getValue(aIndex);
            if (raw instanceof MissingValue mv)
            {
                return new DataValueMissing(mv);
            }
            return new DataValueMissing(MissingValue.MIS);
        }
        return switch (aType)
        {
        case DOUBLE -> new DataValueDouble(getValueAsDouble(aIndex));
        case LONG -> new DataValueLong(getValueAsLong(aIndex));
        case BOOLEAN -> new DataValueBoolean(getValueAsLong(aIndex) != 0);
        default -> super.createDataValue(aIndex, aType);
        };
    }
}
