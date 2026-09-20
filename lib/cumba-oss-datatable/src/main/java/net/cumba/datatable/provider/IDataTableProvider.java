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

    // ⭐⭐ THE PROVIDER-PROPERTY OVERLOADS, ported 2026-09-20 under the FEATURE-LEVEL reduction
    // rule (owner). These are NOT new API on a withheld feature: "provider properties" is a
    // PORTED feature here -- net.cumba.datatable.io.Property ships in full, ILibraryProvider
    // already declares getProviderProperties(URI, FileInfo) and provide(URI, FileInfo,
    // Map<Property,String>), and five library providers in this repository already override
    // them. Only the TABLE-provider side had been cut, which left one ported feature internally
    // inconsistent: XptLibraryProvider.provide RECEIVED a property map and threw it away, so
    // every member was stamped with the default charset.
    //
    // ⚑ All are `default`, so no implementor in this repository breaks.


    /**
     * Retrieve the list of supported properties for loading the given URI.
     *
     * @param aUri
     *            the URI to retrieve the properties for.
     * @param aFileInfo
     *            the optional FileInfo how to interpret the file.
     * @return a list of all supported properties by the provider. This might be empty but never
     *         null.
     */
    default List<Property> getProviderProperties(URI aUri, @Nullable FileInfo aFileInfo)
    {
        return Collections.emptyList();
    }


    /**
     * Retrieve the list of supported properties for loading the given library member.
     *
     * @param aMember
     *            the library member to retrieve the properties for.
     * @param aFileInfo
     *            the optional FileInfo how to interpret the file.
     * @return a list of all supported properties by the provider. This might be empty but never
     *         null.
     */
    default List<Property> getProviderProperties(ILibraryMember aMember,
            @Nullable FileInfo aFileInfo)
    {
        return aMember != null ? getProviderProperties(aMember.getUri(), aFileInfo)
                : Collections.emptyList();
    }


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
