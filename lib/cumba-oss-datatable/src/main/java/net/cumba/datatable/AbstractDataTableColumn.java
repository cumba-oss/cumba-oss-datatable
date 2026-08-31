package net.cumba.datatable;

import lombok.Getter;

/**
 * Abstract base class for {@link IDataTableColumn} implementations that stores the column index.
 */
public abstract class AbstractDataTableColumn implements IDataTableColumn
{

    /**
     * The column index of this column in the data table.
     */
    @Getter
    protected final int index;

    protected AbstractDataTableColumn(int aIndex)
    {
        index = aIndex;
    }

}
