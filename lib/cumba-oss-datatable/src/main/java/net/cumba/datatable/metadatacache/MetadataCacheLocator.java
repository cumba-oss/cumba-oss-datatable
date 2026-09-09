package net.cumba.datatable.metadatacache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;

/**
 * Where an application keeps its unified CDISC metadata store — the single zip that a seeding tool
 * populates and the coreJ engine reads.
 *
 * <p>
 * Deliberately JDK-only and in the contract module: every caller that must resolve this path has to
 * agree on it, and they may not reach one another.
 * </p>
 *
 * <p>
 * ⚠ <b>This javadoc deliberately differs from the internal copy</b>, which names
 * {@code net.cumba.datatable.dictionary.DictionaryStoreLocator}, {@code ViewerMain} and a
 * {@code Tools ▸ …} menu. The first is in {@code dictionary}, one of the packages this distribution
 * does not carry, and a {@code @link} to it fails the javadoc gate; the other two are
 * desktop-application concerns that do not exist here. A sync copying the internal text back will
 * re-break the build — see the deliberate-divergence register in the OSS blueprint.
 * </p>
 *
 * <p>
 * ⚠⚠ {@link #defaultStore()} is the app's OWN default — the LAST tier of the cascade. It does not
 * consult {@link #ENV_METADATA_STORE} or {@link #SP_METADATA_STORE}; callers check those first (see
 * {@link #configuredStore()}). Duplicating the check here would make "which one wins" unanswerable
 * — and moving this default without the cascade would silently orphan every store an operator
 * explicitly placed: the engine's own resolution reads the environment variable and system property
 * first, and anyone who set them is unaffected by this class entirely.
 * </p>
 *
 * <h2>Two deliberate divergences from {@code DictionaryStoreLocator}</h2>
 *
 * <ul>
 * <li><b>No install-directory fallback.</b> {@link #defaultStore()} takes no install-directory
 * argument and always answers under the user's home. The store is <em>written</em> by the seeder,
 * and a location beside the application is exactly the unwritable {@code Program Files} / signed
 * {@code .app} place the per-user default exists to avoid.</li>
 * <li><b>Not owner-only.</b> {@link #createDirectories(Path)} is a plain
 * {@code Files.createDirectories}: the {@code rwx------} in the dictionary locator exists for
 * licensed third-party terminology, and public CDISC metadata carries no such constraint.</li>
 * </ul>
 *
 * <p>
 * ⚠ The retired per-request web-api cache surface ({@code ENV_API_CACHE} / {@code SP_API_CACHE} /
 * {@code defaultCache()} / {@code configuredCache()}, naming
 * {@code ~/.cumbaDataBrowser/library-cache}) was removed by the 8b-2 GUI migration: the unified
 * store replaced that cache, and nothing read the surface any more. The CDISC Library client's own
 * {@code CDISC_API_CACHE} / {@code cdisc.library.api.cache} channels still exist <em>on the
 * client</em> for live API browsing — this locator just no longer fronts them.
 * </p>
 */
public final class MetadataCacheLocator
{

    /**
     * The coreJ engine's environment variable naming the unified metadata store file, outranking
     * its system property.
     *
     * <p>
     * ⚠ Duplicated as a literal because this module deliberately does not depend on
     * {@code cumba-corej-core}. {@code MetadataCacheSeedServiceTest} in
     * {@code cumba-datatable-manager-local-core} — the nearest module that <em>can</em> see the
     * engine — asserts it still equals {@code StoreMetadataProviderFactory.STORE_ENV}.
     * </p>
     */
    public static final String ENV_METADATA_STORE = "CDISC_METADATA_STORE";

    /** The engine's own system property naming the store file. See {@link #ENV_METADATA_STORE}. */
    public static final String SP_METADATA_STORE = "cdisc.metadata.store";

    /**
     * The pinned tag of {@code cdisc-org/cdisc-rules-engine} whose {@code resources/cache} seeds
     * the cache — the maintenance dialog's default, editable there so a user can move ahead of a
     * release.
     *
     * <p>
     * ⚠ The same pin is recorded as {@code <dependency.cdisc-rules-engine-cache.tag>} in the root
     * {@code pom.xml}, beside the rules-corpus pin it must be checked against on every corpus bump.
     * {@code MetadataCacheLocatorTest} guards the two against drifting apart.
     * </p>
     */
    public static final String DEFAULT_CACHE_REF = "v0.17.1";

    /** JDK-only on purpose — this module carries no logging framework. */
    private static final System.Logger LOGGER = System
            .getLogger(MetadataCacheLocator.class.getName());

    /** The per-user application directory the cache lives under. */
    private static final String APP_DIR_NAME = ".cumbaDataBrowser";

    /** The unified metadata store's file name — one zip, versioned and replaced as a unit. */
    private static final String STORE_FILE_NAME = "metadata-cache.zip";

    private MetadataCacheLocator()
    {
    }


    /**
     * The store this app writes to and reads from when nothing outside has said where it is.
     *
     * <p>
     * ⚠ This is the app's OWN default — the last tier of the cascade, nothing more. It does
     * <b>not</b> consult {@link #SP_METADATA_STORE} or {@link #ENV_METADATA_STORE}; callers check
     * those first (see {@link #configuredStore()}), and duplicating the check here would make
     * "which one wins" unanswerable.
     * </p>
     *
     * @return {@code ~/.cumbaDataBrowser/metadata-cache.zip}, whether or not it exists.
     */
    public static Path defaultStore()
    {
        return userHome().resolve(APP_DIR_NAME).resolve(STORE_FILE_NAME);
    }


    /**
     * The store an operator configured from outside this application: the engine's own environment
     * variable first (it outranks the system property inside the engine), then its system property.
     *
     * <p>
     * Separate from {@link #defaultStore()} on purpose — the two answer different questions, and a
     * single method answering both is what makes "which one wins" unanswerable.
     * </p>
     *
     * @return the configured path, or {@code null} when neither is set to a non-blank, usable value
     *         — an unusable one is logged and treated as unset, never thrown, because this runs
     *         during startup before any frame exists.
     */
    public static @Nullable Path configuredStore()
    {
        return configuredStore(System::getenv);
    }


    /**
     * {@link #configuredStore()} with the environment lookup injected, because a JVM cannot set its
     * own environment and a test that pretends to cover that branch is worse than none.
     *
     * @param aEnv
     *            the environment-variable lookup.
     * @return the configured path, or {@code null}.
     */
    static @Nullable Path configuredStore(UnaryOperator<String> aEnv)
    {
        Path env = parseTier(aEnv.apply(ENV_METADATA_STORE), ENV_METADATA_STORE);
        if (env != null)
        {
            return env;
        }
        return parseTier(System.getProperty(SP_METADATA_STORE), SP_METADATA_STORE);
    }


    /**
     * The user's home directory, falling back to the working directory when {@code user.home} is
     * absent <b>or unparseable</b>. Mirrors {@code DictionaryStoreLocator.userHome()}, whose
     * javadoc records which processes this actually protects — the headless backend and CLI, where
     * {@code AppConfig} never initialises, plus {@code -Duser.home}; <b>not</b> the desktop, where
     * {@code AppConfig.initialize()} dies on the same value first.
     *
     * @return the home directory, never {@code null}.
     */
    private static Path userHome()
    {
        String home = System.getProperty("user.home", ".");
        try
        {
            return Path.of(home);
        }
        catch (InvalidPathException ex)
        {
            LOGGER.log(System.Logger.Level.WARNING, "Ignoring unusable user.home \"" + home + "\": "
                    + ex.getMessage() + " Falling back to the working directory.");
            return Path.of(".");
        }
    }


    /**
     * One cascade tier's value as a path, or {@code null} when it is unset, blank, or not a usable
     * path — logged, never thrown: this runs during startup before any frame exists, and inside
     * {@code CoreCheckProperties.buildFor}, which also answers JSON-RPC on a server.
     */
    private static @Nullable Path parseTier(@Nullable String aValue, String aTierName)
    {
        if (aValue == null || aValue.isBlank())
        {
            return null;
        }
        try
        {
            // No trim, deliberately: CdiscLibraryClient uses the raw value, and this locator
            // must resolve the same location the client reads.
            return Path.of(aValue);
        }
        catch (InvalidPathException ex)
        {
            LOGGER.log(System.Logger.Level.WARNING, "Ignoring unusable metadata-cache location \""
                    + aValue + "\" from " + aTierName + ": " + ex.getMessage());
            return null;
        }
    }


    /**
     * Creates the cache directory, with default permissions.
     *
     * <p>
     * ⚠ Deliberately <b>not</b> owner-only — see the class comment. An existing directory is left
     * exactly as it is.
     * </p>
     *
     * @param aCache
     *            the cache directory.
     * @throws IOException
     *             when the directory does not exist and cannot be created — a regular file already
     *             sitting at the path included. The caller must then leave the configuration
     *             unchanged rather than pointing the client at a path that is not a directory.
     */
    public static void createDirectories(Path aCache) throws IOException
    {
        Files.createDirectories(aCache);
    }
}
