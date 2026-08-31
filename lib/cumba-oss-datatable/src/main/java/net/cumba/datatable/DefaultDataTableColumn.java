package net.cumba.datatable;

import net.cumba.datatable.values.IDataValue;
import org.jspecify.annotations.Nullable;

/**
 * A default implementation of a {@link IDataTableColumn} that does not store any value, but always
 * references back to the table.
 */
public class DefaultDataTableColumn extends AbstractDataTableColumn
{

    /**
     * The table, this column belongs to.
     */
    private final IDataTable table;

    /**
     * Create a new instance that references the given table and is used for the given index.
     *
     * @param aTable
     *            the table to reference.
     * @param aIndex
     *            the column index to be used.
     */
    public DefaultDataTableColumn(IDataTable aTable, int aIndex)
    {
        super(aIndex);
        table = aTable;
    }


    /**
     * {@inheritDoc}
     *
     * This implementation calls {@link IDataTable#getRowCount()} to retrieve the row count.
     */
    @Override
    public long getRowCount()
    {
        return table.getRowCount();
    }


    /**
     * {@inheritDoc}
     *
     * This implementation calls {@link IDataTable#getValue(long, int)} to retrieve the value.
     */
    @Override
    public @Nullable Object getValue(long aRow)
    {
        return table.getValue(aRow, index);
    }


    /**
     * {@inheritDoc}
     *
     * This implementation calls {@link IDataTable#getDataValue(long, int)} to retrieve the data
     * value.
     */
    @Override
    public IDataValue getDataValue(long aRow)
    {
        return table.getDataValue(aRow, index);
    }
}
