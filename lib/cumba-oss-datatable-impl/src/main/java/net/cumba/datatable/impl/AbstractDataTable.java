package net.cumba.datatable.impl;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.ExMsgs;
import net.cumba.datatable.IDataTable;

/**
 * An abstract implementation of the {@link IDataTable} interface. This implements most of the
 * required methods.
 */
// metaData is a required (@NonNull per IDataTable.getMetaData) field set by subclasses via the
// protected Lombok setter after construction, before any accessor use; NullAway cannot see that
// deferred init, so suppress its init check here.
@SuppressWarnings("NullAway.Init")
public abstract class AbstractDataTable implements IDataTable
{

    @Getter
    @Setter(value = AccessLevel.PROTECTED)
    private DataTableMeta metaData;

    /**
     * A helper method that ensures that the given row number is a valid row index in this table.
     *
     * @param aRow
     *            the number to check as valid row number.
     * @return the given number (if it is valid).
     * @throws IndexOutOfBoundsException
     *             in case the given number is not a valid row index.
     */
    protected long ensureValidRow(long aRow) throws IndexOutOfBoundsException
    {
        long rc = getRowCount();
        if (aRow < 0 || aRow >= rc)
        {
            throw new IndexOutOfBoundsException(ExMsgs.indexOutOfBounds("row", aRow, 0, rc));
        }
        return aRow;
    }

}
