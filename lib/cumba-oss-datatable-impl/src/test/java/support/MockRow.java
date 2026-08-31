package support;

import java.util.NoSuchElementException;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import net.cumba.datatable.IDataTableRow;
import net.cumba.datatable.values.IDataValue;

@Getter
@Builder
@AllArgsConstructor
public class MockRow implements IDataTableRow
{

    private final long index;

    private final String[] columnNames;

    private final IDataValue[] columnValues;

    @Override
    public int getColumnCount()
    {
        return Math.min(columnNames.length, columnValues.length);
    }


    @Override
    public Object getValue(int aColumn) throws IndexOutOfBoundsException
    {
        return getDataValue(aColumn).getValue();
    }


    @Override
    public Object getValue(String aColumn) throws NoSuchElementException
    {
        return getDataValue(aColumn).getValue();
    }


    @Override
    public IDataValue getDataValue(int aColumn) throws IndexOutOfBoundsException
    {
        if (aColumn >= 0 && aColumn <= getColumnCount())
        {
            return columnValues[aColumn];
        }
        throw new IndexOutOfBoundsException();
    }


    @Override
    public IDataValue getDataValue(String aColumn) throws NoSuchElementException
    {
        for (int i = 0; i < getColumnCount(); i++)
        {
            if (aColumn.equalsIgnoreCase(columnNames[i]))
            {
                return columnValues[i];
            }
        }
        throw new NoSuchElementException();
    }


    @Override
    public long getRealRowIndex()
    {
        return index;
    }

}
