package net.cumba.datatable.impl;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Objects;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.values.IDataValue;
import org.jspecify.annotations.Nullable;

/**
 * Supportive functions for IDataTable's.
 */
public class DataTableSupport
{

    private static final Logger LOGGER = System.getLogger(DataTableSupport.class.getName());

    private final @Nullable IDataTable table;

    public DataTableSupport()
    {
        this(null);
    }


    public DataTableSupport(@Nullable IDataTable aTable)
    {
        table = aTable;
    }


    protected IDataTable getTableSafe() throws IllegalStateException
    {
        if (table != null)
        {
            return table;
        }
        throw new IllegalStateException("No table available!");
    }


    /**
     * Compare the data of two {@link IDataTable} instances for equality. Two tables are considered
     * data-equal if they have the same number of rows and columns, and every cell value is equal.
     * Metadata (name, label, etc.) is not compared by this method.
     *
     * @param aTable1
     *            the first table.
     * @param aTable2
     *            the second table.
     * @return true if both tables contain the same data.
     */
    public boolean isDataEqual(IDataTable aTable1, IDataTable aTable2)
    {
        if (aTable1 == aTable2)
        {
            return true;
        }
        if (aTable1 == null || aTable2 == null)
        {
            return false;
        }

        long rowCount = aTable1.getRowCount();
        int colCount = aTable1.getColumnCount();

        if (rowCount != aTable2.getRowCount())
        {
            return false;
        }
        if (colCount != aTable2.getColumnCount())
        {
            return false;
        }

        for (long r = 0; r < rowCount; r++)
        {
            for (int c = 0; c < colCount; c++)
            {
                IDataValue v1 = aTable1.getDataValue(r, c);
                IDataValue v2 = aTable2.getDataValue(r, c);
                if (!Objects.equals(v1, v2))
                {
                    LOGGER.log(Level.DEBUG, "Data differs at row {0}, column {1}: {2} != {3}.", r,
                            c, v1, v2);
                    return false;
                }
            }
        }

        return true;
    }

}
