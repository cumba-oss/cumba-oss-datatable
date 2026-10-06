package net.cumba.datatable.provider;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.NonNull;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.io.IGenericProvider;
import net.cumba.datatable.io.Property;
import net.cumba.datatable.library.ILibraryMember;
import net.cumba.datatable.metadata.IMetadataLibrary;
import org.jspecify.annotations.Nullable;

/**
 * Interface for providers
 */
public interface IDataTableProvider extends IGenericProvider<IDataTable>
{

    /**
     * Retrieve the list of supported properties for loading the given URI.
     *
     * <p>
     * A provider that reads its source here to propose defaults (a <i>probe</i>) keeps its
     * best-effort defaults when a local source cannot be read, but a remote ({@code http} /
     * {@code https}) source it cannot read aborts the open: the failure is thrown, unchecked, never
     * swallowed ({@link net.cumba.datatable.io.ProbeFailures}).
     * </p>
     *
     * @param aUri
     *            the URI to retrieve the properties for.
     * @param aFileInfo
     *            the optional FileInfo how to interpret the file.
     * @return a list of all supported properties by the provider. This might be empty but never
     *         null.
     * @throws java.io.UncheckedIOException
     *             if the probe cannot read a remote source; the message is the UI's headline.
     */
    default List<Property> getProviderProperties(URI aUri, @Nullable FileInfo aFileInfo)
    {
        return Collections.emptyList();
    }


    /**
     * Retrieve the list of supported properties for loading the given library member. A remote
     * member source the probe cannot read aborts as for
     * {@link #getProviderProperties(URI, FileInfo)}.
     *
     * @param aMember
     *            the library member to retrieve the properties for.
     * @param aFileInfo
     *            the optional FileInfo how to interpret the file.
     * @return a list of all supported properties by the provider. This might be empty but never
     *         null.
     * @throws java.io.UncheckedIOException
     *             if the probe cannot read a remote source; the message is the UI's headline.
     */
    default List<Property> getProviderProperties(ILibraryMember aMember,
            @Nullable FileInfo aFileInfo)
    {
        return aMember != null ? getProviderProperties(aMember.getUri(), aFileInfo)
                : Collections.emptyList();
    }


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
    @Nullable
    IDataTable provide(ILibraryMember aMember, @Nullable FileInfo aFileInfo) throws IOException;


    /**
     * Provide a IDataTable for the given URI and file info with the given properties.
     *
     * @param aUri
     *            the URI to provide the data table for.
     * @param aFileInfo
     *            the optional FileInfo how to interpret the file.
     * @param aProperties
     *            a map of properties with values to be considered for loading.
     * @return the loaded data table for the given URI.
     * @throws IOException
     *             in case the data table can not be loaded.
     */
    default @Nullable IDataTable provide(URI aUri, @Nullable FileInfo aFileInfo,
            Map<Property, String> aProperties)
        throws IOException
    {
        return provide(aUri, aFileInfo);
    }


    /**
     * Provide a IDataTable for the given library member and file info with the given properties.
     *
     * @param aMember
     *            the library member to provide the data table for.
     * @param aFileInfo
     *            the optional FileInfo how to interpret the file.
     * @param aProperties
     *            a map of properties with values to be considered for loading.
     * @return the loaded data table for the given library member.
     * @throws IOException
     *             in case the data table can not be loaded.
     */
    default @Nullable IDataTable provide(ILibraryMember aMember, @Nullable FileInfo aFileInfo,
            Map<Property, String> aProperties)
        throws IOException
    {
        return provide(aMember, aFileInfo);
    }


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
     * <p>
     * Metadata reads complete in milliseconds (a header-only scan) and emit no loading-progress
     * events.
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
     * Provide only the {@link DataTableMeta} for the given URI and file info with the given
     * properties.
     *
     * @param aUri
     *            the URI to provide the metadata for.
     * @param aFileInfo
     *            the optional FileInfo how to interpret the file.
     * @param aProperties
     *            a map of properties with values to be considered for loading.
     * @return the metadata for the data table referenced by the given URI.
     * @throws IOException
     *             in case the metadata can not be loaded.
     */
    default DataTableMeta provideMetaData(URI aUri, @Nullable FileInfo aFileInfo,
            Map<Property, String> aProperties)
        throws IOException
    {
        return provideMetaData(aUri, aFileInfo);
    }


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


    /**
     * Provide only the {@link DataTableMeta} for the given library member and file info with the
     * given properties.
     *
     * @param aMember
     *            the library member to provide the metadata for.
     * @param aFileInfo
     *            the optional FileInfo how to interpret the file.
     * @param aProperties
     *            a map of properties with values to be considered for loading.
     * @return the metadata for the data table referenced by the given member.
     * @throws IOException
     *             in case the metadata can not be loaded.
     * @throws NullPointerException
     *             when {@code aMember} is {@code null}.
     */
    default DataTableMeta provideMetaData(ILibraryMember aMember, @Nullable FileInfo aFileInfo,
            Map<Property, String> aProperties)
        throws IOException
    {
        return provideMetaData(aMember, aFileInfo);
    }

}
