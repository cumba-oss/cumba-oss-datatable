package net.cumba.datatable.io;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import net.cumba.datatable.help.CDT;
import org.jspecify.annotations.Nullable;

/**
 * A generic file info bean that can be used to build file filters in UIs or to simply understand
 * the file name conventions to be used for files to get handled correctly by a provider..
 */
@Value
@Builder(toBuilder = true, buildMethodName = "_build")
@Jacksonized
// Lombok @Value/@Builder all-args constructor trips NullAway's @NonNull-field init check
// (NullAway#917); the custom builder enforces a non-null uuid at build() time.
@SuppressWarnings("NullAway.Init")
public class FileInfo
{

    private static final Logger LOGGER = System.getLogger(FileInfo.class.getName());

    /**
     * Create a combined FileInfo that matches all elements that would match at least one of the
     * given {@link FileInfo}s.
     *
     * @param aFileInfos
     *            the list of file infos to combine.
     * @return a combined {@link FileInfo}
     */
    public static FileInfo createCombined(List<FileInfo> aFileInfos)
    {
        return createCombined(aFileInfos, null);
    }


    /**
     * Create a combined FileInfo that matches all elements that would match at least one of the
     * given {@link FileInfo}s.
     *
     * @param aFileInfos
     *            the list of file infos to combine.
     * @param aDescriptionPrefix
     *            an optional custom description prefix to be used.
     * @return a combined {@link FileInfo}
     */
    public static FileInfo createCombined(List<FileInfo> aFileInfos,
            @Nullable String aDescriptionPrefix)
    {

        String[] exts = aFileInfos.stream()//
                .map(FileInfo::getFileExtension)//
                .sorted()//
                .toArray(String[]::new);
        String pattern = "(?i).*\\.(" + String.join("|", exts) + ")";

        String[] exts2 = Arrays.stream(exts)//
                .map(s -> ".*" + s)//
                .limit(5)//
                .toArray(String[]::new);

        StringBuilder sb = new StringBuilder();
        if (CDT.isBlankOrNull(aDescriptionPrefix))
        {
            sb.append("All Supported types");
        }
        else
        {
            sb.append(aDescriptionPrefix);
        }
        sb.append(" (")//
                .append(String.join(";", exts2));

        if (exts.length > 5)
        {
            // we use only first 5 so we add a ;...
            sb.append(";...");
        }
        sb.append(")");

        return create("", pattern, sb.toString(), null);
    }


    /**
     * Create a standard implementation for the given file name extension and File Type Name.
     *
     * @param aExtension
     *            the file name extension (e.g. json or csv).
     * @param aName
     *            the file type name. This is a short explanatory name that makes this info unique.
     * @return the created file info.
     */
    public static FileInfo createFor(String aExtension, String aName)
    {
        return createFor(aExtension, aName, null);
    }


    public static FileInfo createFor(String aExtension, String aName, @Nullable String aUUID)
    {
        String pattern = "(?i).*\\." + aExtension;
        String descr = aName + " (*." + aExtension + ")";
        return create(aExtension, pattern, descr, aUUID);
    }


    /**
     * Find the first {@link FileInfo} in the given list whose extension matches the extension of
     * the given file name (case insensitive). Returns {@code null} when the file name has no
     * extension or when no matching FileInfo is found. With ambiguous extensions the first match in
     * iteration order wins.
     *
     * @param aFileName
     *            the file name to inspect (may be {@code null}).
     * @param aInfos
     *            the candidate file infos.
     * @return the matching FileInfo, or {@code null}.
     */
    public static @Nullable FileInfo findByFileName(@Nullable String aFileName,
            @Nullable List<FileInfo> aInfos)
    {
        if (aFileName == null || aInfos == null)
        {
            return null;
        }
        int dot = aFileName.lastIndexOf('.');
        if (dot < 0 || dot == aFileName.length() - 1)
        {
            return null;
        }
        String ext = aFileName.substring(dot + 1);
        for (FileInfo fi : aInfos)
        {
            if (ext.equalsIgnoreCase(fi.getFileExtension()))
            {
                return fi;
            }
        }
        return null;
    }


    /**
     * Create a new instance.
     *
     * @param aExtension
     *            the value to be used as file name extension.
     * @param aPattern
     *            the value to be used as pattern.
     * @param aDescription
     *            the value to be used as description.
     * @param aUUID
     *            the value to be used as UUID. If this is null, a random UUID will be generated on
     *            the fly. This random id will change after application restart, so it can't be used
     *            to identify a instance permanently.
     * @return the new generated FileInfo.
     */
    public static FileInfo create(String aExtension, String aPattern, String aDescription,
            @Nullable String aUUID)
    {
        String uuid;
        if (CDT.isBlankOrNull(aUUID))
        {
            uuid = UUID.randomUUID().toString();
            LOGGER.log(Level.WARNING,
                    "No UUID given for FileInfo: extension=\"{0}\" pattern=\"{1}\" description=\"{2}\". Will use random UUID=\"{3}\".",
                    aExtension, aPattern, aDescription, uuid);
        }
        else
        {
            // isBlankOrNull(aUUID) was false, so aUUID is non-null here.
            uuid = Objects.requireNonNull(aUUID);
        }

        return FileInfo.builder().fileExtension(aExtension).fileNamePattern(aPattern)
                .description(aDescription).uuid(uuid).build();
    }

    /**
     * The file name extension, this file info is about.
     */
    private final String fileExtension;

    /**
     * A file name pattern that can be used to filter for valid file names.
     */
    private final String fileNamePattern;

    /**
     * A human understandable type description.
     */
    private final String description;

    /**
     * A system wide unique ID for this instance.
     */
    @NonNull
    private final String uuid;

    @Override
    public String toString()
    {
        return description;
    }

    public static class FileInfoBuilder
    {

        public FileInfo build()
        {
            if (CDT.isBlankOrNull(uuid))
            {
                throw new IllegalStateException("UUID must not be empty or null!");
            }
            return _build();
        }
    }
}
