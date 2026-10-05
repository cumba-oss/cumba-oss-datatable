package net.cumba.datatable.impl.view;

import net.cumba.datatable.ExMsgs;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.view.IDataTableView;

/**
 * A IDataTableView that really does nothing. It can be used if the view should not alter the rows.
 * For example views that only change columns.
 */
public final class NativeOrderView implements IDataTableView
{

    public static final NativeOrderView INSTANCE = new NativeOrderView();

    /**
     * Create a new instance.
     */
    private NativeOrderView()
    {
    }


    @Override
    public long getRowCount(IDataTable aTable)
    {
        return aTable != null ? aTable.getRowCount() : 0;
    }


    @Override
    public long getRealRow(IDataTable aTable, long aRow) throws IndexOutOfBoundsException
    {
        if (aTable == null)
        {
            return aRow;
        }
        long rc = getRowCount(aTable);
        if (aRow < 0 || aRow >= rc)
        {
            throw new IndexOutOfBoundsException(ExMsgs.indexOutOfBounds("row", aRow, 0, rc));
        }
        return aRow;
    }


    @Override
    public long getDisplayRow(IDataTable aTable, long aRealRow)
    {
        if (aTable == null)
        {
            return aRealRow;
        }
        long rc = getRowCount(aTable);
        if (aRealRow < 0 || aRealRow >= rc)
        {
            return -1;
        }
        return aRealRow;
    }
}
