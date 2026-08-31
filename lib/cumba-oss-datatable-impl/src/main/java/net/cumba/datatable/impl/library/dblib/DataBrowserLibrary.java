package net.cumba.datatable.impl.library.dblib;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import lombok.CustomLog;
import lombok.Getter;
import lombok.NonNull;
import net.cumba.datatable.help.CDT;
import net.cumba.datatable.impl.library.AbstractDataTableLibrary;
import net.cumba.datatable.impl.library.LibraryMemberComparator;
import net.cumba.datatable.impl.library.dblib.beans.DataBrowserLibraryBean;
import net.cumba.datatable.impl.library.dblib.beans.DataBrowserSourceBean;

/**
 * A configurable library implementation that supports multiple source types including local files,
 * directories, and remote URIs. The library definition is stored as a JSON file with the
 * {@code .dblib} extension.
 * <p>
 * File system sources are re-evaluated on each member listing to reflect backend changes.
 */
@CustomLog
public class DataBrowserLibrary extends AbstractDataTableLibrary
{

    @Getter
    private final DataBrowserLibraryBean bean;

    public DataBrowserLibrary(@NonNull URI aUri, @NonNull DataBrowserLibraryBean aBean)
    {
        super(aBean.getName(), aBean.getLabel(), aUri, DataBrowserLibrarySupplier.FI_DBLIB);
        bean = aBean;
    }


    @Override
    public String getType()
    {
        return "databrowser";
    }


    /**
     * Resolves all configured sources to library members. File system paths are evaluated against
     * the current file system state on each call.
     *
     * @return a stream of resolved library members, sorted by name.
     * @throws IOException
     *             if an I/O error occurs during directory listing.
     */
    public Stream<DataBrowserMember> getMembers() throws IOException
    {
        DataBrowserSourceBean[] sources = bean.getSources();
        if (CDT.isEmptyOrNull(sources))
        {
            return Stream.empty();
        }

        List<DataBrowserMember> members = new ArrayList<>();
        for (DataBrowserSourceBean source : sources)
        {
            if (source == null)
            {
                continue;
            }
            resolveSource(source, members);
        }
        members.sort(new LibraryMemberComparator());
        return members.stream();
    }


    private void resolveSource(DataBrowserSourceBean aSource, List<DataBrowserMember> aMembers)
    {
        URI sourceUri;
        try
        {
            sourceUri = new URI(aSource.getUri());
        }
        catch (URISyntaxException ex)
        {
            LOGGER.log(System.Logger.Level.WARNING, "Invalid source URI: {0}", aSource.getUri(),
                    ex);
            return;
        }

        if ("file".equalsIgnoreCase(sourceUri.getScheme()))
        {
            resolveFileSource(sourceUri, aSource, aMembers);
        }
        else
        {
            resolveSingleMember(sourceUri, aSource, aMembers);
        }
    }


    private void resolveFileSource(URI aUri, DataBrowserSourceBean aSource,
            List<DataBrowserMember> aMembers)
    {
        Path path;
        try
        {
            path = Path.of(aUri);
        }
        catch (Exception ex)
        {
            LOGGER.log(System.Logger.Level.WARNING, "Cannot resolve file path: {0}", aUri, ex);
            return;
        }

        if (!Files.exists(path))
        {
            // Silently ignore non-existing paths
            return;
        }

        if (Files.isDirectory(path))
        {
            resolveDirectorySource(path, aMembers);
        }
        else
        {
            // Single file: use source name/label, default name from filename
            String name = aSource.getName();
            if (CDT.isBlankOrNull(name))
            {
                String fileName = path.getFileName() != null ? path.getFileName().toString()
                        : path.toString();
                name = CDT.getBeforeLast(fileName, '.').toUpperCase(Locale.ROOT);
            }
            aMembers.add(new DataBrowserMember(this, aUri, name, aSource.getLabel()));
        }
    }


    private void resolveDirectorySource(Path aDirectory, List<DataBrowserMember> aMembers)
    {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(aDirectory))
        {
            for (Path entry : stream)
            {
                if (Files.isRegularFile(entry))
                {
                    String fileName = entry.getFileName() != null ? entry.getFileName().toString()
                            : entry.toString();
                    String name = CDT.getBeforeLast(fileName, '.').toUpperCase(Locale.ROOT);
                    aMembers.add(new DataBrowserMember(this, entry.toUri(), name, null));
                }
            }
        }
        catch (IOException ex)
        {
            LOGGER.log(System.Logger.Level.WARNING, "Failed to list directory: {0}", aDirectory,
                    ex);
        }
    }


    private void resolveSingleMember(URI aUri, DataBrowserSourceBean aSource,
            List<DataBrowserMember> aMembers)
    {
        String name = aSource.getName();
        if (CDT.isBlankOrNull(name))
        {
            name = aUri.toString();
        }
        aMembers.add(new DataBrowserMember(this, aUri, name, aSource.getLabel()));
    }

}
