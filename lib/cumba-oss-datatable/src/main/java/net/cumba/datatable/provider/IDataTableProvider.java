package net.cumba.datatable.provider;

import java.io.IOException;
import java.net.URI;

import lombok.NonNull;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.io.IGenericProvider;
import net.cumba.datatable.library.ILibraryMember;
import net.cumba.datatable.metadata.IMetadataLibrary;
import org.jspecify.annotations.Nullable;

/**
 * Interface for providers
 */
public interface IDataTableProvider extends IGenericProvider<IDataTable>
{

    /**
     * Set the metadata library to be used when parsing.
     *
     * @param aMetadata
     *            the metadata library to be used for enriching data table metadata.
     */
    void setMetadata(@Nullable IMetadataLibrary aMetadata);


    /**
     * Provide a IDataTable for the given library member and file info.
     *
     * @param aMember
     *            the library member to provide the data table for.
     * @param aFileInfo
     *            the optional FileInfo how to interpret the file. If this is not one of the
     *            {@link FileInfo}s from {@link #getSupportedFileInfos()}, the result is undefined.
     * @return the loaded data table for the given library member.
     * @throws IOException
     *             in case the data table can not be loaded.
     */
    IDataTable provide(ILibraryMember aMember, @Nullable FileInfo aFileInfo) throws IOException;


    /**
     * Provide only the {@link DataTableMeta} for the given URI and file info.
     * <p>
     * Implementations should parse the file up to (but not including) the first data row and build
     * a {@link DataTableMeta} from that. If the expected number of rows is available as a metadata
     * entry without parsing all data, it may be set as row count / total row count on the returned
     * meta. <b>This call must not read row data into memory.</b>
     * <p>
     * This method is abstract — every concrete provider must implement it explicitly so that the
     * "metadata-only" contract above is a deliberate choice, not an accidental fall-through to the
     * slow "load the whole table then drop the rows" default.
     *
     * @param aUri
     *            the URI to provide the metadata for.
     * @param aFileInfo
     *            the optional FileInfo how to interpret the file.
     * @return the metadata for the data table referenced by the given URI.
     * @throws IOException
     *             in case the metadata can not be loaded.
     */
    DataTableMeta provideMetaData(URI aUri, @Nullable FileInfo aFileInfo) throws IOException;


    /**
     * Provide only the {@link DataTableMeta} for the given library member and file info.
     *
     * @param aMember
     *            the library member to provide the metadata for. Must not be {@code null}.
     * @param aFileInfo
     *            the optional FileInfo how to interpret the file.
     * @return the metadata for the data table referenced by the given member.
     * @throws IOException
     *             in case the metadata can not be loaded.
     * @throws NullPointerException
     *             when {@code aMember} is {@code null}.
     */
    default DataTableMeta provideMetaData(@NonNull ILibraryMember aMember,
            @Nullable FileInfo aFileInfo)
        throws IOException, NullPointerException
    {
        return provideMetaData(aMember.getUri(), aFileInfo);
    }

}
