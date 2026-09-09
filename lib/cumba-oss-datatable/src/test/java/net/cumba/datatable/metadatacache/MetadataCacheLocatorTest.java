package net.cumba.datatable.metadatacache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link MetadataCacheLocator}: the user-directory default, the external-configuration cascade,
 * plain directory creation, and the pin's agreement with the root pom.
 */
class MetadataCacheLocatorTest
{

    /**
     * The root pom property the pin must match — see
     * {@link MetadataCacheLocator#DEFAULT_CACHE_REF}'s javadoc.
     */
    private static final Pattern POM_CACHE_TAG = Pattern
            .compile("<dependency\\.cdisc-rules-engine-cache\\.tag>([^<]+)"
                    + "</dependency\\.cdisc-rules-engine-cache\\.tag>");

    /** Environment lookup that reports nothing set — a JVM cannot change its own environment. */
    private static @Nullable String noEnv(String aName)
    {
        return null;
    }


    @Test
    void defaultStore_isTheStoreZipUnderTheUserHomeApplicationDirectory()
    {
        Path store = MetadataCacheLocator.defaultStore();
        assertEquals("metadata-cache.zip", store.getFileName().toString());
        Path parent = store.getParent();
        assertNotNull(parent);
        assertEquals(".cumbaDataBrowser", parent.getFileName().toString());
        assertEquals(Path.of(System.getProperty("user.home", ".")), parent.getParent());
    }


    @Test
    void defaultStore_doesNotConsultTheSystemProperty()
    {
        // The app's own default is the LAST tier, nothing more.
        String saved = System.getProperty(MetadataCacheLocator.SP_METADATA_STORE);
        try
        {
            System.setProperty(MetadataCacheLocator.SP_METADATA_STORE, "/nowhere/at/all.zip");
            Path store = MetadataCacheLocator.defaultStore();
            assertEquals("metadata-cache.zip", store.getFileName().toString());
        }
        finally
        {
            restoreStore(saved);
        }
    }


    @Test
    void configuredStore_environmentVariableOutranksTheSystemProperty(@TempDir Path aTmp)
    {
        String saved = System.getProperty(MetadataCacheLocator.SP_METADATA_STORE);
        try
        {
            System.setProperty(MetadataCacheLocator.SP_METADATA_STORE,
                    aTmp.resolve("sysprop.zip").toString());
            UnaryOperator<String> env = name -> MetadataCacheLocator.ENV_METADATA_STORE.equals(name)
                    ? aTmp.resolve("env.zip").toString()
                    : null;
            assertEquals(aTmp.resolve("env.zip"), MetadataCacheLocator.configuredStore(env));
        }
        finally
        {
            restoreStore(saved);
        }
    }


    @Test
    void configuredStore_nothingSet_isNull()
    {
        String saved = System.getProperty(MetadataCacheLocator.SP_METADATA_STORE);
        try
        {
            System.clearProperty(MetadataCacheLocator.SP_METADATA_STORE);
            assertNull(MetadataCacheLocator.configuredStore(MetadataCacheLocatorTest::noEnv));
        }
        finally
        {
            restoreStore(saved);
        }
    }


    @Test
    void configuredStore_blankSystemProperty_isNull()
    {
        String saved = System.getProperty(MetadataCacheLocator.SP_METADATA_STORE);
        try
        {
            System.setProperty(MetadataCacheLocator.SP_METADATA_STORE, "   ");
            assertNull(MetadataCacheLocator.configuredStore(MetadataCacheLocatorTest::noEnv));
        }
        finally
        {
            restoreStore(saved);
        }
    }


    @Test
    void configuredStore_blankEnvironmentVariableFallsThroughToTheSystemProperty(@TempDir Path aTmp)
    {
        String saved = System.getProperty(MetadataCacheLocator.SP_METADATA_STORE);
        try
        {
            System.setProperty(MetadataCacheLocator.SP_METADATA_STORE,
                    aTmp.resolve("sysprop.zip").toString());
            assertEquals(aTmp.resolve("sysprop.zip"),
                    MetadataCacheLocator.configuredStore(_ -> "  "));
        }
        finally
        {
            restoreStore(saved);
        }
    }


    @Test
    void configuredStore_noArgOverload_readsTheRealEnvironmentAndTheSystemProperty(
            @TempDir Path aTmp)
    {
        String saved = System.getProperty(MetadataCacheLocator.SP_METADATA_STORE);
        try
        {
            System.setProperty(MetadataCacheLocator.SP_METADATA_STORE, aTmp.toString());
            // ⚠ The environment variable outranks the property, so the assertion is only
            // meaningful when it is unset — and this JVM cannot unset it. Assert whichever of the
            // two actually wins rather than skipping, so the no-arg overload is genuinely covered.
            String env = System.getenv(MetadataCacheLocator.ENV_METADATA_STORE);
            Path expected = env != null && !env.isBlank() ? Path.of(env) : aTmp;
            assertEquals(expected, MetadataCacheLocator.configuredStore());
        }
        finally
        {
            restoreStore(saved);
        }
    }


    /**
     * ⚠ Review finding 3: {@code configuredStore} runs during startup before any frame exists and
     * inside {@code CoreCheckProperties.buildFor} (which also answers JSON-RPC on a server), so an
     * unusable configured value must be logged and treated as unset — never thrown.
     */
    @Test
    void configuredStore_unusableSystemPropertyIsIgnoredNotThrown()
    {
        String saved = System.getProperty(MetadataCacheLocator.SP_METADATA_STORE);
        try
        {
            System.setProperty(MetadataCacheLocator.SP_METADATA_STORE, "bad\0path");
            assertNull(MetadataCacheLocator.configuredStore(MetadataCacheLocatorTest::noEnv));
        }
        finally
        {
            restoreStore(saved);
        }
    }


    @Test
    void configuredStore_unusableEnvironmentValueFallsThroughToTheSystemProperty(@TempDir Path aTmp)
    {
        String saved = System.getProperty(MetadataCacheLocator.SP_METADATA_STORE);
        try
        {
            System.setProperty(MetadataCacheLocator.SP_METADATA_STORE, aTmp.toString());
            assertEquals(aTmp, MetadataCacheLocator.configuredStore(_ -> "bad\0path"));
        }
        finally
        {
            restoreStore(saved);
        }
    }


    /**
     * ⚠ The sibling of {@code DictionaryStoreLocatorTest.userStore_unusableUserHome…}:
     * {@code user.home} is operator-settable through {@code databrowser.properties}, applied before
     * the startup call that lands here, so an unparseable value must degrade rather than throw.
     */
    @Test
    void defaultStore_unusableUserHomeFallsBackToTheWorkingDirectory()
    {
        String saved = System.getProperty("user.home");
        try
        {
            System.setProperty("user.home", "bad\0home");
            assertEquals(Path.of(".").resolve(".cumbaDataBrowser").resolve("metadata-cache.zip"),
                    MetadataCacheLocator.defaultStore());
        }
        finally
        {
            if (saved == null)
            {
                System.clearProperty("user.home");
            }
            else
            {
                System.setProperty("user.home", saved);
            }
        }
    }


    @Test
    void createDirectories_createsNestedDirectories(@TempDir Path aTmp) throws IOException
    {
        Path cache = aTmp.resolve("nested").resolve("library-cache");
        MetadataCacheLocator.createDirectories(cache);
        assertTrue(Files.isDirectory(cache));
    }


    @Test
    void createDirectories_existingDirectoryIsLeftExactlyAsItIs(@TempDir Path aTmp)
        throws IOException
    {
        Path cache = aTmp.resolve("library-cache");
        Files.createDirectory(cache);
        Files.writeString(cache.resolve("keep.txt"), "untouched");
        MetadataCacheLocator.createDirectories(cache);
        assertTrue(Files.isRegularFile(cache.resolve("keep.txt")));
    }


    @Test
    void createDirectories_pathOccupiedByARegularFile_throws(@TempDir Path aTmp) throws IOException
    {
        Path cache = aTmp.resolve("library-cache");
        Files.writeString(cache, "a file, not a cache");
        assertThrows(IOException.class, () -> MetadataCacheLocator.createDirectories(cache));
        assertFalse(Files.isDirectory(cache));
    }


    /**
     * ⚠ The pin exists in two places on purpose — a build-time record beside the rules-corpus pin
     * in the root pom, and the runtime literal the dialog defaults to — and this is the guard that
     * keeps them from drifting apart (plans/PLAN-core-check-followups.md §3.5).
     *
     * <p>
     * Read straight from the pom file rather than a filtered test resource, because this module's
     * test resources are not filtered and enabling filtering for one guard would be a heavier
     * change than the guard itself.
     * </p>
     *
     * <p>
     * ⚠ The repo root is taken from the {@code repoRoot} system property
     * ({@code maven.multiModuleProjectDirectory}), which surefire exports in this tree <em>and</em>
     * in the split repositories — <b>not</b> from a CWD-relative path. Surefire's
     * {@code workingDirectory} is not the module directory everywhere: the split roots redirect it
     * to {@code target/test-cwd}, where {@code ../../pom.xml} silently resolves to the module pom
     * instead of the repo root and the guard then checks the wrong file. Outside surefire (a bare
     * IDE run) the property is unset and the guard is skipped, not failed.
     * </p>
     */
    @Test
    void defaultCacheRef_matchesTheRootPomPin() throws IOException
    {
        String repoRoot = System.getProperty("repoRoot");
        assumeTrue(repoRoot != null && !repoRoot.isBlank(),
                "repoRoot system property not set — not a surefire run, guard not applicable");
        Path rootPom = Path.of(repoRoot, "pom.xml");
        assertTrue(Files.isRegularFile(rootPom),
                "the repo root pom must exist at " + rootPom.toAbsolutePath());
        Matcher matcher = POM_CACHE_TAG.matcher(Files.readString(rootPom));
        assertTrue(matcher.find(), "the repo root pom (" + rootPom.toAbsolutePath()
                + ") must record the cache pin beside <dependency.corej-rules.tag>");
        assertEquals(matcher.group(1), MetadataCacheLocator.DEFAULT_CACHE_REF,
                "bump MetadataCacheLocator.DEFAULT_CACHE_REF and the root pom's "
                        + "<dependency.cdisc-rules-engine-cache.tag> together");
    }


    private static void restoreStore(@Nullable String aSaved)
    {
        if (aSaved == null)
        {
            System.clearProperty(MetadataCacheLocator.SP_METADATA_STORE);
        }
        else
        {
            System.setProperty(MetadataCacheLocator.SP_METADATA_STORE, aSaved);
        }
    }

}
