package net.cumba.datatable;

import java.util.NoSuchElementException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import net.cumba.datatable.values.IDataValue;
import org.jspecify.annotations.Nullable;

/**
 * A default implementation of the IDataTableRow interface that is a view to the data table.
 */
@Getter
@Builder
@EqualsAndHashCode
@AllArgsConstructor
public class DefaultDataTableRow implements IDataTableRow
{

    /**
     * The data table, this row is from.
     */
    private final IDataTable table;

    /**
     * The index of the row.
     */
    private final long index;

    @Override
    public long getRealRowIndex()
    {
        return table.getRealRowIndex(index);
    }


    @Override
    public int getColumnCount()
    {
        return table.getMetaData().getColumnCount();
    }


    @Override
    public @Nullable Object getValue(int aColumn) throws IndexOutOfBoundsException
    {
        return table.getValue(index, aColumn);
    }


    @Override
    public @Nullable Object getValue(String aColumn) throws NoSuchElementException
    {
        return table.getValue(index, aColumn);
    }


    @Override
    public IDataValue getDataValue(int aColumn)
    {
        return table.getDataValue(index, aColumn);
    }


    @Override
    public IDataValue getDataValue(String aColumn) throws IllegalArgumentException
    {
        return table.getDataValue(index, aColumn);
    }
}
