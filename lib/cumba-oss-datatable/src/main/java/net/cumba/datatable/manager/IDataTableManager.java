package net.cumba.datatable.manager;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.stream.Stream;

import lombok.NonNull;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.library.IDataTableLibrary;
import net.cumba.datatable.metadata.IMetadataLibrary;
import org.jspecify.annotations.Nullable;

/**
 * An IDataTableManager manages the access to {@link IDataTable}'s and all related elements like
 * {@link IDataTableLibrary}.<br/>
 * The reason for this is to support remote tables through special data table managers.
 */
public interface IDataTableManager
{

    /**
     * The name of the column that provides the original columns name.<br/>
     * This is used in descriptive statistics table.
     */
    String COLUMN_NAME_COLUMN = "Column";

    /**
     * The name of the column that provides the number of occurrences.<br/>
     * This is used in frequency statistics table.
     */
    String COLUMN_NAME_COUNT = "Count";

    /**
     * The name of the column that provides the percentage of occurrences inside a by group.<br/>
     * This is used in frequency statistics table.
     */
    String COLUMN_NAME_PERCENT = "Percent";

    /**
     * Returns a list of all {@link FileInfo}s that can be used to load a data table.
     *
     * @return a list of all {@link FileInfo}s that can be used to load a data table.
     */
    List<FileInfo> getSupportedDataTableInfos();


    /**
     * Returns a list of all {@link FileInfo}s that can be used to load a library.
     *
     * @return a list of all {@link FileInfo}s that can be used to load a library.
     */
    List<FileInfo> getSupportedDataLibraryInfos();


    /**
     * Test if the given URI is supported to be opened as a table with the supplied FileInfo.
     *
     * @param aUri
     *            the URI to be tested.
     * @param aFileInfo
     *            the optional FileInfo to be used when testing.
     * @return true if the URI can be opened as a table, false otherwise.
     */
    boolean isSupportedAsTable(@NonNull URI aUri, @Nullable FileInfo aFileInfo);


    /**
     * Test if the given URI is supported to be opened as a library with the supplied FileInfo.
     *
     * @param aUri
     *            the URI to be tested.
     * @param aFileInfo
     *            the optional FileInfo to be used when testing.
     * @return true if the URI can be opened as a library, false otherwise.
     */
    boolean isSupportedAsLibrary(@NonNull URI aUri, @Nullable FileInfo aFileInfo);


    /**
     * Retrieve a {@link IDataTableRef} from a given URI.
     *
     * @param aUri
     *            the URI to retrieve the data table for.
     * @param aFileInfo
     *            the optional file info that describes the format to be used to load the URI as.
     * @return the data table reference for the given URI.
     * @throws IOException
     *             in case the given URI can not be accessed as data table.
     */
    IDataTableRef getDataTableRef(@NonNull URI aUri, @Nullable FileInfo aFileInfo)
        throws IOException;


    /**
     * Retrieve a library member as a data table.
     *
     * @param aMember
     *            the library member to retrieve as a data table.
     * @param aFileInfo
     *            an optional file info to be used when loading the member. If this is null, the
     *            default file info for the member is used.
     * @return the data table for the given library member.
     * @throws IOException
     *             in case the library member can not be accessed as a data table.
     */
    IDataTableRef getDataTableRef(@NonNull ILibraryMemberRef aMember, @Nullable FileInfo aFileInfo)
        throws IOException;


    /**
     * Make a data table reference accessible as {@link IDataTable}.<br/>
     * This method blocks until the complete table is accessible as a local IDataTable.
     *
     * @param aRef
     *            the data table reference.
     * @return the local IDataTable that is referenced by the given IDataTableRef.
     * @throws IOException
     *             in case of any error when transferring.
     */
    IDataTable getDataTable(@NonNull IDataTableRef aRef) throws IOException;


    /**
     * Retrieve a {@link IDataTableLibrary} for the given URI.
     *
     * @param aUri
     *            the URI to retrieve the library for.
     * @param aFileInfo
     *            the optional file info that describes the format to be used to load the URI as.
     * @return the library for the given URI.
     * @throws IOException
     *             in case the given URI can not be accessed as library.
     */
    IDataTableLibraryRef getLibraryRef(@NonNull URI aUri, @Nullable FileInfo aFileInfo)
        throws IOException;


    /**
     * Retrieve the metadata library attached to the given library. Returns {@code null} if no
     * metadata has been attached.
     *
     * @param aLibrary
     *            the library to retrieve the metadata for.
     * @return the metadata library, or {@code null} if none is attached.
     * @throws IOException
     *             in case of any error.
     */
    default @Nullable IMetadataLibrary getMetadataLibrary(@NonNull IDataTableLibraryRef aLibrary)
        throws IOException
    {
        return null;
    }


    /**
     * Retrieve all library members from the given library.
     *
     * @param aLibrary
     *            the library to retrieve the members from.
     * @return all members of the given library.
     * @throws IOException
     *             in case of any errors while retrieving the library members.
     */
    Stream<ILibraryMemberRef> getLibraryMembers(@NonNull IDataTableLibraryRef aLibrary)
        throws IOException;

}
