package net.cumba.datatable.impl;

import lombok.AccessLevel;
import lombok.CustomLog;
import lombok.Setter;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.ExMsgs;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.IDataTableColumn;
import net.cumba.datatable.values.IDataValue;
import org.jspecify.annotations.Nullable;

/**
 * A {@link IDataTable} that only consists of independent {@link IDataTableColumn}'s. This table
 * implementation finally retrieves the data from these columns, so the columns must NOT request the
 * data from this table.
 */
@CustomLog
public class ColumnCachedDataTable extends AbstractDataTable
{

    private IDataTableColumn[] columns;

    @Setter(value = AccessLevel.PROTECTED)
    private long rowCount = -1;

    @SuppressWarnings("this-escape")
    public ColumnCachedDataTable(DataTableMeta aMeta, IDataTableColumn... aColumns)
    {
        columns = aColumns;
        setMetaData(aMeta);
    }


    @Override
    public IDataTableColumn getColumn(int aColumn) throws IndexOutOfBoundsException
    {
        if (aColumn < 0 || columns.length <= aColumn)
        {
            String msg = ExMsgs.indexOutOfBounds("column", aColumn, 0, columns.length);
            throw new IndexOutOfBoundsException(msg);
        }
        return columns[aColumn];
    }


    @Override
    public @Nullable Object getValue(long aRow, int aColumn) throws IndexOutOfBoundsException
    {
        long rc = getRowCount();
        if (aRow < 0 || rc <= aRow)
        {
            String msg = ExMsgs.indexOutOfBounds("row", aRow, 0, rc);
            throw new IndexOutOfBoundsException(msg);
        }
        return getColumn(aColumn).getValue(aRow);
    }


    @Override
    public boolean isMissingOrNull(long aRow, int aColumn) throws IndexOutOfBoundsException
    {
        long rc = getRowCount();
        if (aRow < 0 || rc <= aRow)
        {
            String msg = ExMsgs.indexOutOfBounds("row", aRow, 0, rc);
            throw new IndexOutOfBoundsException(msg);
        }
        return getColumn(aColumn).isMissingOrNull(aRow);
    }


    @Override
    public boolean isEmptyOrMissing(long aRow, int aColumn) throws IndexOutOfBoundsException
    {
        long rc = getRowCount();
        if (aRow < 0 || rc <= aRow)
        {
            String msg = ExMsgs.indexOutOfBounds("row", aRow, 0, rc);
            throw new IndexOutOfBoundsException(msg);
        }
        return getColumn(aColumn).isEmptyOrMissing(aRow);
    }


    @Override
    public int hashCodeAt(long aRow, int aColumn) throws IndexOutOfBoundsException
    {
        long rc = getRowCount();
        if (aRow < 0 || rc <= aRow)
        {
            String msg = ExMsgs.indexOutOfBounds("row", aRow, 0, rc);
            throw new IndexOutOfBoundsException(msg);
        }
        return getColumn(aColumn).hashCodeAt(aRow);
    }


    @Override
    public IDataValue getDataValue(long aRow, int aColumn)
    {
        long rc = getRowCount();
        if (aRow < 0 || aRow >= rc)
        {
            String msg = ExMsgs.indexOutOfBounds("row", aRow, 0, rc);
            throw new IndexOutOfBoundsException(msg);
        }
        return getColumn(aColumn).getDataValue(aRow);
    }


    @Override
    public long getRowCount()
    {
        if (rowCount >= 0)
        {
            return rowCount;
        }
        return columns.length > 0 ? columns[0].getRowCount() : 0;
    }
}
