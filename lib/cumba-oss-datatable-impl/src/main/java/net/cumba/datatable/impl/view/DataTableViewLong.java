package net.cumba.datatable.impl.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import net.cumba.datatable.ExMsgs;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.view.IDataTableView;

/**
 * A default implementation for the {@link IDataTableView} interface that uses an array to map row
 * index values.
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class DataTableViewLong implements IDataTableView
{

    /**
     * The array that stores the real row index values.
     */
    @NonNull
    private final long[] view;

    @Override
    public long getRowCount(IDataTable aTable)
    {
        return view.length;
    }


    @Override
    public long getRealRow(IDataTable aTable, long aRow) throws IndexOutOfBoundsException
    {
        if (aRow < 0 || aRow >= view.length)
        {
            throw new IndexOutOfBoundsException(
                    ExMsgs.indexOutOfBounds("row", aRow, 0, view.length));
        }
        // Safe cast: bounds check above ensures aRow < view.length <= Integer.MAX_VALUE
        // (Java arrays are limited to Integer.MAX_VALUE elements)
        return view[(int) aRow];
    }


    @Override
    public long getDisplayRow(IDataTable aTable, long aRealRow)
    {
        for (int i = 0; i < view.length; i++)
        {
            if (view[i] == aRealRow)
            {
                return i;
            }
        }
        return -1;
    }
}
