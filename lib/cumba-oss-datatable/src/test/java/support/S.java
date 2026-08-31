package support;

import net.cumba.datatable.IDataTableRow;
import net.cumba.datatable.values.IDataValue;

public class S
{

    @SuppressWarnings("unchecked")
    public static <T> T[] ar(T... aValues)
    {
        return aValues;
    }


    public static IDataTableRow createMockRow(String column, IDataValue value)
    {
        return new MockRow(0, ar(column), ar(value));
    }


    public static IDataTableRow createMockRow(String[] columns, IDataValue... values)
    {
        return new MockRow(0, columns, values);
    }

}
