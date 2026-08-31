package net.cumba.datatable.manager;

import net.cumba.datatable.library.IDataTableLibrary;
import org.jspecify.annotations.Nullable;

/**
 * A DataTableLibraryRef is a reference to an {@link IDataTableLibrary} with an attached manager.
 * The reference itself is not able to provide any extended information, but it knows the manager
 * the reference is handled from.
 */
public interface IDataTableLibraryRef
{

    /**
     * Returns the manager that can be used to access the content of the related
     * {@link IDataTableLibrary}.
     *
     * @return the manager, that can be used to a access the content of the related
     *         {@link IDataTableLibrary}.
     */
    IDataTableManager getManager();


    /**
     * Returns the name of the library.
     *
     * @return the name of the library.
     */
    String getName();


    /**
     * Returns the label of the library.
     *
     * @return the label of the library.
     */
    @Nullable
    String getLabel();


    /**
     * Returns the URI of the library.
     *
     * @return the URI of the library.
     */
    @Nullable
    String getUri();
}
