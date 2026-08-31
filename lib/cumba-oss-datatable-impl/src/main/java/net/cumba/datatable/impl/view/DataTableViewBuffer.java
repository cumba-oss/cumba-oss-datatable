package net.cumba.datatable.impl.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import net.cumba.datatable.ExMsgs;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.databuffer.IDataBufferNumeric;
import net.cumba.datatable.view.IDataTableView;

/**
 * A {@link IDataTableView} implementation backed by a {@link IDataBufferNumeric} to map row index
 * values. This is used when the row mapping needs to support a large number of rows with compact
 * storage.
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class DataTableViewBuffer implements IDataTableView
{

    /**
     * The array that stores the real row index values.
     */
    @NonNull
    private final IDataBufferNumeric buffer;

    @Override
    public long getRowCount(IDataTable aTable)
    {
        return buffer.size();
    }


    @Override
    public long getRealRow(IDataTable aTable, long aRow) throws IndexOutOfBoundsException
    {
        long rowCount = buffer.size();
        if (aRow < 0 || aRow >= rowCount)
        {
            throw new IndexOutOfBoundsException(ExMsgs.indexOutOfBounds("row", aRow, 0, rowCount));
        }
        // Safe cast: bounds check above ensures aRow < rowCount <= Integer.MAX_VALUE
        // (buffer size is limited to Integer.MAX_VALUE elements)
        return buffer.getValueAsLong((int) aRow);
    }


    @Override
    public long getDisplayRow(IDataTable aTable, long aRealRow)
    {
        for (int i = 0; i < buffer.size(); i++)
        {
            if (buffer.getValueAsLong(i) == aRealRow)
            {
                return i;
            }
        }
        return -1;
    }

}
