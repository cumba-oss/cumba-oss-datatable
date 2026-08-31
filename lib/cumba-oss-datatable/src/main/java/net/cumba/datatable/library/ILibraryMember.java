package net.cumba.datatable.library;

import java.net.URI;

import net.cumba.datatable.io.FileInfo;
import org.jspecify.annotations.Nullable;

/**
 * Interface for elements that are library members.
 */
public interface ILibraryMember
{

    /**
     * Returns the name of the member.
     *
     * @return the name of the member.
     */
    String getName();


    /**
     * Returns the optional label of the member.
     *
     * @return the optional label of the member.
     */
    @Nullable
    String getLabel();


    /**
     * Returns a URI that is referenced by the member.
     *
     * @return a URI that is referenced by the member.
     */
    URI getUri();


    default @Nullable FileInfo getFileInfo()
    {
        return null;
    }


    /**
     * Returns the library, this member is from.
     *
     * @return the library, this member is from.
     */
    IDataTableLibrary getLibrary();
}
