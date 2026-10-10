package net.cumba.datatable.dictionary.house;

// OSS-IDENTITY datatable-dictionary: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * Reads the on-disk dictionary store the engine's installer writes, with the engine's
 * {@code DictionaryStore} rules (plan §3.4 step 3, round-1 L3 / L4):
 * <ul>
 * <li><b>versioned</b> — {@code <store>/<type>/<version>/<type>.json[.gz]}; a requested version
 * binds only its own directory, and only the requested type's file is read (never another
 * type's);</li>
 * <li><b>flat</b> — {@code <store>/<type>.json[.gz]} at the root (the engine's dummy fixtures): the
 * file binds when its declared version equals the requested one or none is requested, else it is
 * not installed and the status names the file's version;</li>
 * <li><b>mixed</b> — a versioned tree plus stray flat files: the versioned tree only;</li>
 * <li>⛔ <b>nothing is inferred</b> about a <em>present</em> version: equality only, no fallback to
 * another installed version. A <em>blank</em> version binds the most recent installed version of
 * the type (DF1a, owner 2026-10-09 — never the store's {@value #MANIFEST} selection, which a user
 * who never ran a CORE check never made).</li>
 * </ul>
 * <p>
 * The version token is checked before any path is built: a define's {@code @Version} is study data
 * and must never reach {@code resolve()} as {@code ..} or a separator (the installer's own
 * write-side test, {@link #isValidVersionToken}).
 * </p>
 * <p>
 * {@value #MANIFEST} is the installer's selection manifest; this reader only needs its name, to
 * tell it from a flat dictionary file. The literal agreement with the engine's
 * {@code DictionaryStore.MANIFEST} and {@code DictionaryStoreLocator.MANIFEST} is pinned by
 * {@code DictionaryInstallServiceTest.storeLocatorLiterals_stillMatchTheEngine} and by this
 * module's cross-reader test in {@code cumba-datatable-manager-local-core}.
 * </p>
 */
public final class DictionaryStoreReader
{

    /**
     * The installer-written selection manifest at the store root (excluded from the flat files).
     */
    public static final String MANIFEST = "selected-versions.json";

    /** JDK-only on purpose: this module carries no logging framework. */
    private static final System.Logger LOGGER = System
            .getLogger(DictionaryStoreReader.class.getName());

    /**
     * The shape of a usable store version token — the installer's {@code VERSION_TOKEN}: the six
     * vendor vocabularies fit, and nothing that could traverse or break a path does.
     */
    public static final String VERSION_TOKEN_SHAPE = "[A-Za-z0-9._-]{1,64}";

    /**
     * {@link #VERSION_TOKEN_SHAPE}, compiled: the one definition — the installer delegates here
     * (B-T review lane 1 LOW-3: {@code house} no longer imports {@code convert}).
     */
    private static final Pattern VERSION_TOKEN = Pattern.compile(VERSION_TOKEN_SHAPE);

    /** Why a type / version did not resolve, or that it did. */
    public enum Outcome
    {
        /** A dictionary with content was loaded. */
        RESOLVED,
        /** The type, or the requested version of it, is not installed. */
        NOT_INSTALLED,
        /** The requested version is not a usable store version token. */
        INVALID_TOKEN,
        /** The version directory holds no file of the type (or one declaring another type). */
        NO_FILE,
        /** The file could not be read or parsed. */
        UNREADABLE,
        /** The file parsed but carries no usable content. */
        NO_CONTENT
    }


    /**
     * The answer of {@link #resolve}.
     *
     * @param outcome
     *            what happened.
     * @param dictionary
     *            the loaded dictionary, for {@link Outcome#RESOLVED} only.
     * @param version
     *            the version that was bound ({@code RESOLVED}: the directory name, or the flat
     *            file's declared version, may be {@code null} for a flat file declaring none), else
     *            the version that was asked for.
     * @param mostRecent
     *            {@code true} when a blank request bound the most recent installed version (DF1a).
     * @param installed
     *            the installed version tokens of the type, most recent first (a flat store: the
     *            file's declared version, or empty).
     * @param detail
     *            a reason for a non-resolved outcome, in words.
     */
    public record Resolution(Outcome outcome, @Nullable ValueMapDictionary dictionary,
            @Nullable String version, boolean mostRecent, List<String> installed,
            @Nullable String detail)
    {

        public Resolution
        {
            installed = List.copyOf(installed);
        }


        /** Whether a dictionary was bound. */
        public boolean isResolved()
        {
            return outcome == Outcome.RESOLVED;
        }
    }

    private final Path store;

    /**
     * Creates a reader of the store.
     *
     * @param aStore
     *            the store root; may not exist (then nothing is installed).
     */
    public DictionaryStoreReader(Path aStore)
    {
        store = aStore;
    }


    /** The store root this reader reads. */
    public Path getStore()
    {
        return store;
    }


    /**
     * The installer's write-side test for a version token, applied here before a define's version
     * becomes a path segment.
     *
     * @param aVersion
     *            the token.
     * @return whether it may become a store directory name.
     */
    public static boolean isValidVersionToken(String aVersion)
    {
        return VERSION_TOKEN.matcher(aVersion).matches() && !".".equals(aVersion)
                && !"..".equals(aVersion);
    }


    /**
     * The installed versions of a type, most recent first ({@link DictionaryVersions}); a flat
     * store answers the declared version of the type's root file.
     *
     * @param aType
     *            the house type.
     * @return the tokens, most recent first; empty when the type is not installed.
     */
    public List<String> installedVersions(String aType)
    {
        if (isVersioned())
        {
            return DictionaryVersions.order(aType, installed(aType));
        }
        Path flat = flatFile(store, aType);
        if (flat == null)
        {
            return List.of();
        }
        try
        {
            String declared = DictionaryFileCache.header(flat).version();
            return declared != null ? List.of(declared) : List.of();
        }
        catch (IOException _)
        {
            return List.of();
        }
    }


    /**
     * Whether {@link #resolve} would find a file to bind for the type and version, by the same
     * directory / flat rule and without loading the file (a library tree's status lists every
     * dictionary codelist): versioned — the version's directory holds the type's file (a blank
     * version: any version directory does); flat — the root file exists and declares the requested
     * version (a blank request: the file exists). An unusable version token is never installed.
     *
     * @param aType
     *            the house type.
     * @param aVersion
     *            the requested version, or {@code null} / blank for the most recent installed one.
     * @return whether the store holds a file that version binds to.
     */
    public boolean isInstalled(String aType, @Nullable String aVersion)
    {
        String type = aType.toLowerCase(Locale.ROOT);
        String requested = aVersion == null || aVersion.isBlank() ? null : aVersion;
        if (!Files.isDirectory(store) || (requested != null && !isValidVersionToken(requested)))
        {
            return false;
        }
        if (isVersioned())
        {
            if (requested == null)
            {
                return !installed(type).isEmpty();
            }
            return versionedFile(store.resolve(type).resolve(requested), type) != null;
        }
        Path flat = flatFile(store, type);
        if (flat == null)
        {
            return false;
        }
        if (requested == null)
        {
            return true;
        }
        try
        {
            return requested.equals(DictionaryFileCache.header(flat).version());
        }
        catch (IOException _)
        {
            return false;
        }
    }


    /**
     * Binds one type at one version.
     *
     * @param aType
     *            the house type (lower-case id).
     * @param aVersion
     *            the version the define declares, or {@code null} / blank for the most recent
     *            installed one (DF1a).
     * @return the resolution; never {@code null}.
     */
    public Resolution resolve(String aType, @Nullable String aVersion)
    {
        String type = aType.toLowerCase(Locale.ROOT);
        String requested = aVersion == null || aVersion.isBlank() ? null : aVersion;
        if (!Files.isDirectory(store))
        {
            return new Resolution(Outcome.NOT_INSTALLED, null, requested, false, List.of(),
                    "no dictionary store at " + store);
        }
        if (requested != null && !isValidVersionToken(requested))
        {
            return new Resolution(Outcome.INVALID_TOKEN, null, requested, false, List.of(),
                    "'" + requested + "' is not a usable store version token");
        }
        return isVersioned() ? resolveVersioned(type, requested) : resolveFlat(type, requested);
    }


    private Resolution resolveVersioned(String aType, @Nullable String aRequested)
    {
        Path typeDir = store.resolve(aType);
        List<String> installed = Files.isDirectory(typeDir)
                ? DictionaryVersions.order(aType, installed(aType))
                : List.<String> of();
        if (installed.isEmpty())
        {
            return new Resolution(Outcome.NOT_INSTALLED, null, aRequested, false, installed,
                    "no version of " + aType + " is installed");
        }
        String version = aRequested;
        boolean mostRecent = false;
        if (version == null)
        {
            version = installed.get(0);
            mostRecent = true;
        }
        Path versionDir = typeDir.resolve(version);
        if (!Files.isDirectory(versionDir))
        {
            return new Resolution(Outcome.NOT_INSTALLED, null, version, false, installed,
                    "version " + version + " of " + aType + " is not installed");
        }
        Path file = versionedFile(versionDir, aType);
        if (file == null)
        {
            return new Resolution(Outcome.NO_FILE, null, version, mostRecent, installed,
                    "version " + version + " of " + aType + " carries no " + aType + ".json file");
        }
        return load(file, aType, version, mostRecent, installed);
    }


    private Resolution resolveFlat(String aType, @Nullable String aRequested)
    {
        Path file = flatFile(store, aType);
        if (file == null)
        {
            return new Resolution(Outcome.NOT_INSTALLED, null, aRequested, false, List.of(),
                    aType + " is not installed (flat store)");
        }
        ValueMapDictionary dict;
        try
        {
            dict = DictionaryFileCache.load(file, ValueMapDictionary.Profile.FORMAT);
        }
        catch (IOException e)
        {
            return new Resolution(Outcome.UNREADABLE, null, aRequested, false, List.of(),
                    "the " + aType + " file could not be read (" + e.getMessage() + ")");
        }
        String declared = dict.getVersion();
        List<String> installed = declared != null ? List.of(declared) : List.of();
        if (aRequested != null && !aRequested.equals(declared))
        {
            return new Resolution(Outcome.NOT_INSTALLED, null, aRequested, false, installed,
                    "version " + aRequested + " of " + aType
                            + " is not installed — the flat store holds "
                            + (declared != null ? declared : "a file declaring no version"));
        }
        if (!dict.getType().isEmpty() && !dict.getType().equalsIgnoreCase(aType))
        {
            return new Resolution(Outcome.NO_FILE, null, aRequested, false, List.of(),
                    "the " + aType + " file declares type " + dict.getType());
        }
        if (!dict.hasContent())
        {
            return new Resolution(Outcome.NO_CONTENT, null, declared, false, installed,
                    "the " + aType + " file carries no usable terms (empty or malformed)");
        }
        return new Resolution(Outcome.RESOLVED, dict, declared,
                aRequested == null && declared != null, installed, null);
    }


    private static Resolution load(Path aFile, String aType, String aVersion, boolean aMostRecent,
            List<String> aInstalled)
    {
        ValueMapDictionary dict;
        try
        {
            dict = DictionaryFileCache.load(aFile, ValueMapDictionary.Profile.FORMAT);
        }
        catch (IOException e)
        {
            return new Resolution(Outcome.UNREADABLE, null, aVersion, aMostRecent, aInstalled,
                    "version " + aVersion + " of " + aType + " could not be read (" + e.getMessage()
                            + ")");
        }
        if (!dict.getType().isEmpty() && !dict.getType().equalsIgnoreCase(aType))
        {
            return new Resolution(Outcome.NO_FILE, null, aVersion, aMostRecent, aInstalled,
                    "version " + aVersion + " of " + aType + " holds a file declaring type "
                            + dict.getType());
        }
        if (!dict.hasContent())
        {
            return new Resolution(Outcome.NO_CONTENT, null, aVersion, aMostRecent, aInstalled,
                    "version " + aVersion + " of " + aType
                            + " carries no usable terms (empty or malformed)");
        }
        return new Resolution(Outcome.RESOLVED, dict, aVersion, aMostRecent, aInstalled, null);
    }


    /**
     * Whether the store has the versioned layout: at least one subdirectory (mixed ⇒ versioned).
     */
    boolean isVersioned()
    {
        try (Stream<Path> entries = Files.list(store))
        {
            return entries.anyMatch(Files::isDirectory);
        }
        catch (IOException _)
        {
            return false;
        }
    }


    /** The installed version directories of a type, with their install date read on demand. */
    private List<DictionaryVersions.Installed> installed(String aType)
    {
        Path typeDir = store.resolve(aType);
        List<DictionaryVersions.Installed> out = new ArrayList<>();
        if (!Files.isDirectory(typeDir))
        {
            return out;
        }
        try (Stream<Path> entries = Files.list(typeDir))
        {
            for (Path dir : entries.filter(Files::isDirectory).sorted().toList())
            {
                Path name = dir.getFileName();
                if (name != null)
                {
                    out.add(new DictionaryVersions.Installed(name.toString(),
                            _ -> installDate(dir, aType)));
                }
            }
        }
        catch (IOException e)
        {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Dictionary store {0}: type directory {1} could not be listed ({2})", store,
                    typeDir, e.getMessage());
        }
        return out;
    }


    /**
     * The install date of a version directory: the house file's {@code retrieved} field (its header
     * only, never a full parse), else the directory's modification time (UTC); only read when the
     * order needs it, once per order.
     */
    private static @Nullable LocalDate installDate(Path aVersionDir, String aType)
    {
        Path file = versionedFile(aVersionDir, aType);
        if (file != null)
        {
            try
            {
                String retrieved = DictionaryFileCache.header(file).retrieved();
                if (retrieved != null)
                {
                    return LocalDate.parse(retrieved);
                }
            }
            catch (IOException | DateTimeParseException e)
            {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Dictionary {0}: no usable retrieved date in {1} ({2}); the directory time orders it",
                        aType, aVersionDir, e.getMessage());
            }
        }
        try
        {
            BasicFileAttributes attrs = Files.readAttributes(aVersionDir,
                    BasicFileAttributes.class);
            return LocalDate.ofInstant(attrs.lastModifiedTime().toInstant(), ZoneOffset.UTC);
        }
        catch (IOException _)
        {
            return null;
        }
    }


    /**
     * {@code
     *
    <dir>
     * /<type>.json}, else {@code
     *
    <dir>
     * /<type>.json.gz}, else {@code null}.
     */
    private static @Nullable Path versionedFile(Path aDir, String aType)
    {
        Path plain = aDir.resolve(aType + ".json");
        if (Files.isRegularFile(plain))
        {
            return plain;
        }
        Path gz = aDir.resolve(aType + ".json.gz");
        return Files.isRegularFile(gz) ? gz : null;
    }


    /** The flat file of a type at the store root (never the manifest), or {@code null}. */
    private static @Nullable Path flatFile(Path aStore, String aType)
    {
        Path file = versionedFile(aStore, aType);
        if (file == null)
        {
            return null;
        }
        Path name = file.getFileName();
        return name != null && MANIFEST.equals(name.toString()) ? null : file;
    }
}
