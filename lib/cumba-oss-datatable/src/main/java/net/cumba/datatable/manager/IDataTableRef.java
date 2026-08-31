package net.cumba.datatable.manager;

import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import org.jspecify.annotations.Nullable;

/**
 * A DataTableRef is a reference to an {@link IDataTable} with an attached manager. The reference
 * itself is not able to provide any extended information about the data table, but it knows the
 * manager the reference is handled from.
 */
public interface IDataTableRef
{

    /**
     * Returns the manager that can be used to access the content of the related {@link IDataTable}.
     *
     * @return the manager, that can be used to a access the content of the related
     *         {@link IDataTable}.
     */
    IDataTableManager getManager();


    /**
     * Returns the name of the data table.
     *
     * @return the name of the data table.
     */
    @Nullable
    String getName();


    /**
     * Returns the metadata of the table.
     *
     * @return the metadata of the table.
     */
    DataTableMeta getMetaData();


    /**
     * Returns the URI of the data table.
     *
     * @return the URI of the data table.
     */
    @Nullable
    String getUri();

}
