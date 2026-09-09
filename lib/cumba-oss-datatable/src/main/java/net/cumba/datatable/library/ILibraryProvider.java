package net.cumba.datatable.library;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.help.CDT;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.io.IGenericProvider;
import net.cumba.datatable.io.Property;
import org.jspecify.annotations.Nullable;

public interface ILibraryProvider extends IGenericProvider<IDataTableLibrary>
{

    String ATTRIBUTE_VALIDATION_REPORT = "validation-report";

    String ATTRIBUTE_META_DATA = "metadata";

    String ATTRIBUTE_META_DATA_URI = "metadata-uri";

    /**
     * Name of the common library property that carries the display name of the loaded library.
     * Every provider should expose this property from {@link #getProviderProperties(URI, FileInfo)}
     * with a sensible URI-based default and honor the user-chosen value in
     * {@link #provide(URI, FileInfo, Map)}.
     */
    String LIBRARY_NAME_PROPERTY_NAME = "Library Name";

    /**
     * Derives a human-readable default library name from the given URI. Strips any path and
     * trailing file extension. Falls back to the host or the full URI string if no usable file name
     * is available.
     *
     * @param aUri
     *            the URI to derive the default from.
     * @param aFileInfo
     *            optional file info. Currently unused but reserved for providers that need it.
     * @return the default library name. Never null or empty.
     */
    static String defaultLibraryName(URI aUri, @Nullable FileInfo aFileInfo)
    {
        if (aUri == null)
        {
            return "library";
        }
        String path = aUri.getPath();
        if (!CDT.isBlankOrNull(path))
        {
            while (path.length() > 1 && path.endsWith("/"))
            {
                path = path.substring(0, path.length() - 1);
            }
            int lastSlash = path.lastIndexOf('/');
            String fileName = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
            if (!CDT.isBlankOrNull(fileName))
            {
                return CDT.getBeforeLast(fileName, '.');
            }
        }
        String host = aUri.getHost();
        if (!CDT.isBlankOrNull(host))
        {
            return host;
        }
        return aUri.toString();
    }


    /**
     * Builds the common {@link Property} that allows the user to choose a library display name. The
     * default value is derived from the URI via {@link #defaultLibraryName(URI, FileInfo)}.
     *
     * @param aUri
     *            the URI the library will be loaded from.
     * @param aFileInfo
     *            the optional file info.
     * @return the library name property with a URI-based default.
     */
    static Property libraryNameProperty(URI aUri, @Nullable FileInfo aFileInfo)
    {
        return libraryNameProperty(aUri, aFileInfo, null);
    }


    /**
     * Builds the common {@link Property} that allows the user to choose a library display name,
     * with a provider-supplied default. Falls back to the URI-based default if the given custom
     * default is blank or null.
     *
     * @param aUri
     *            the URI the library will be loaded from.
     * @param aFileInfo
     *            the optional file info.
     * @param aCustomDefault
     *            the provider-supplied default name, e.g. extracted from the file content itself.
     *            If blank or null the URI-based default is used.
     * @return the library name property.
     */
    static Property libraryNameProperty(URI aUri, @Nullable FileInfo aFileInfo,
            @Nullable String aCustomDefault)
    {
        // When aCustomDefault is non-blank it is non-null (NullAway can't track isBlankOrNull);
        // otherwise fall back to the never-null URI-based default.
        String def = !CDT.isBlankOrNull(aCustomDefault) ? Objects.requireNonNull(aCustomDefault)
                : defaultLibraryName(aUri, aFileInfo);
        return Property.forString(LIBRARY_NAME_PROPERTY_NAME,
                "The display name of the library as shown in the UI.", def);
    }


    /**
     * Resolves the effective library name from the given property map. Returns the user-provided
     * value if present in the map, otherwise the URI-based default.
     *
     * @param aUri
     *            the URI the library is loaded from.
     * @param aFileInfo
     *            the optional file info.
     * @param aProperties
     *            the property map as passed to {@link #provide(URI, FileInfo, Map)}.
     * @return the resolved library name.
     */
    static String resolveLibraryName(URI aUri, @Nullable FileInfo aFileInfo,
            @Nullable Map<Property, String> aProperties)
    {
        return resolveLibraryName(aUri, aFileInfo, aProperties, null);
    }


    /**
     * Resolves the effective library name using a provider-supplied default. Returns the
     * user-provided value from the property map if present, otherwise the custom default, otherwise
     * the URI-based default.
     *
     * @param aUri
     *            the URI the library is loaded from.
     * @param aFileInfo
     *            the optional file info.
     * @param aProperties
     *            the property map as passed to {@link #provide(URI, FileInfo, Map)}.
     * @param aCustomDefault
     *            the provider-supplied default name. Must match the default used in
     *            {@link #getProviderProperties(URI, FileInfo)} so the map lookup succeeds. May be
     *            null or blank to fall back to the URI-based default.
     * @return the resolved library name.
     */
    static String resolveLibraryName(URI aUri, @Nullable FileInfo aFileInfo,
            @Nullable Map<Property, String> aProperties, @Nullable String aCustomDefault)
    {
        Property prop = libraryNameProperty(aUri, aFileInfo, aCustomDefault);
        if (aProperties != null)
        {
            String value = aProperties.get(prop);
            if (!CDT.isBlankOrNull(value))
            {
                // isBlankOrNull is false here, so value is non-null (NullAway can't track this).
                return Objects.requireNonNull(value);
            }
        }
        return prop.defaultValue();
    }


    /**
     * Retrieve the list of supported properties for loading the given URI as a library.
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
     * Provide a library for the given URI and file info with the given properties.
     *
     * @param aUri
     *            the URI to provide the library for.
     * @param aFileInfo
     *            the optional FileInfo how to interpret the file.
     * @param aProperties
     *            a map of properties with values to be considered for loading.
     * @return the loaded library for the given URI.
     * @throws IOException
     *             in case the library can not be loaded.
     */
    default @Nullable IDataTableLibrary provide(URI aUri, @Nullable FileInfo aFileInfo,
            Map<Property, String> aProperties)
        throws IOException
    {
        return provide(aUri, aFileInfo);
    }


    Stream<? extends ILibraryMember> provideLibraryMembers(IDataTableLibrary aLibrary)
        throws IOException;


    Stream<? extends DataTableColumnMeta> provideLibraryMemberColumns(ILibraryMember aMember)
        throws IOException;


    default @Nullable Object getLibraryAttribute(IDataTableLibrary aLibrary,
            @Nullable String aAttributeKey)
    {
        if (Objects.equals(aAttributeKey, ATTRIBUTE_META_DATA))
        {
            return aLibrary.getMetadata();
        }
        return null;
    }
}
