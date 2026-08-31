package net.cumba.datatable.view;

import net.cumba.datatable.IDataTable;

/**
 * A view to an underlying data table. This is used when sorting or filtering a {@link IDataTable}.
 * The data table is not changed, but a view is generated that maps from the view index of a row to
 * its real index.
 */
public interface IDataTableView
{

    /**
     * Returns the number of rows contained in this view.
     *
     * @return the number of rows contained in this view.
     */
    long getRowCount(IDataTable aTable);


    /**
     * Map a given row index to the real index in the underlying {@link IDataTable}.
     *
     * @param aRow
     *            the 0-based view index of the row to retrieve the underlying index for.<br/>
     *            Valid range is <code>0 &lt;= aRow &lt; rowCount</code>.
     * @return the index of the row in the underlying table.
     * @throws IndexOutOfBoundsException
     *             if aRow is outside of the valid bounds.
     */
    long getRealRow(IDataTable aTable, long aRow) throws IndexOutOfBoundsException;


    /**
     * Map a given real row index in the underlying {@link IDataTable} to the display row index in
     * this view.
     *
     * @param aRealRow
     *            the 0-based real index of the row in the underlying table.
     * @return the display row index in this view, or <code>-1</code> if the real row is not present
     *         in the view.
     */
    default long getDisplayRow(IDataTable aTable, long aRealRow)
    {
        long rc = getRowCount(aTable);
        for (long i = 0; i < rc; i++)
        {
            if (getRealRow(aTable, i) == aRealRow)
            {
                return i;
            }
        }
        return -1;
    }

}
