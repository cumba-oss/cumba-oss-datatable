package net.cumba.datatable.library;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;

import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.metadata.IMetadataLibrary;
import org.jspecify.annotations.Nullable;

/**
 * Interface for a library that contains members.
 */
public interface IDataTableLibrary extends Closeable
{

    /**
     * Returns the name of the library.
     *
     * @return the name of the library.
     */
    String getName();


    /**
     * Returns an optional label that contains a description of the library.
     *
     * @return an optional label that contains a description of the library or null if no label is
     *         available.
     */
    @Nullable
    String getLabel();


    /**
     * Returns a URI that references this library.
     *
     * @return a URI that references this library. This might be a URI to a file (e.g. define.xml),
     *         a folder or what ever else.
     */
    URI getUri();


    /**
     * Returns a string that defines the type of the library.
     *
     * @return a string that defines the type of the library.
     */
    String getType();


    default @Nullable FileInfo getFileInfo()
    {
        return null;
    }


    /**
     * Returns the optional metadata that is attached to this library.
     *
     * @return the optional metadata that is attached to this library.
     */
    default @Nullable IMetadataLibrary getMetadata()
    {
        return null;
    }


    @Override
    default void close() throws IOException
    {

    }
}
