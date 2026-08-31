package net.cumba.datatable.library;

import java.io.IOException;
import java.net.URI;
import java.util.Objects;
import java.util.stream.Stream;

import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.help.CDT;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.io.IGenericProvider;
import org.jspecify.annotations.Nullable;

public interface ILibraryProvider extends IGenericProvider<IDataTableLibrary>
{

    String ATTRIBUTE_VALIDATION_REPORT = "validation-report";

    String ATTRIBUTE_META_DATA = "metadata";

    String ATTRIBUTE_META_DATA_URI = "metadata-uri";

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
     * Resolves the effective library name using a provider-supplied default. Returns the custom
     * default when it is not blank, otherwise the URI-based default.
     *
     * @param aUri
     *            the URI the library is loaded from.
     * @param aFileInfo
     *            the optional file info.
     * @param aCustomDefault
     *            the provider-supplied default name. May be null or blank to fall back to the
     *            URI-based default.
     * @return the resolved library name.
     */
    static String resolveLibraryName(URI aUri, @Nullable FileInfo aFileInfo,
            @Nullable String aCustomDefault)
    {
        // when not blank, aCustomDefault is non-null.
        return !CDT.isBlankOrNull(aCustomDefault) ? Objects.requireNonNull(aCustomDefault)
                : defaultLibraryName(aUri, aFileInfo);
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
