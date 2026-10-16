package net.cumba.datatable.impl.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.cumba.datatable.impl.library.dblib.DataBrowserLibrarySupplier;
import net.cumba.datatable.impl.library.dblib.L2LibraryFixture;
import net.cumba.datatable.io.Property;
import net.cumba.datatable.library.IDataTableLibrary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pins {@link LibraryProviderFactory}: the lazily created singleton, the file-name lookup, the
 * property pass-through and every exit of {@link LibraryProviderFactory#provide}.
 */
class LibraryProviderFactoryMutationTest
{

    @Test
    void testTheSingletonIsCreatedOnFirstUseAndThenReused() throws Exception
    {
        Field field = LibraryProviderFactory.class.getDeclaredField("instance");
        field.setAccessible(true);
        Object saved = field.get(null);
        try
        {
            field.set(null, null);

            LibraryProviderFactory first = LibraryProviderFactory.getInstance();
            assertNotNull(first, "the first call must create the instance");
            assertSame(first, LibraryProviderFactory.getInstance());
        }
        finally
        {
            field.set(null, saved);
        }
    }


    @Test
    void testResolveFormatFromFileName()
    {
        LibraryProviderFactory factory = LibraryProviderFactory.getInstance();

        assertSame(DataBrowserLibrarySupplier.FI_DBLIB,
                factory.resolveFormatFromFileName("study.dblib"));
        assertNull(factory.resolveFormatFromFileName("study.csv"));
        assertNull(factory.resolveFormatFromFileName(null));
    }


    @Test
    void testProviderPropertiesArePassedThroughFromTheMatchingProvider(@TempDir Path tempDir)
        throws IOException
    {
        URI libUri = L2LibraryFixture.in(tempDir).name("FromFactory").writeUri();

        List<Property> props = LibraryProviderFactory.getInstance().getProviderProperties(libUri,
                null);

        assertEquals(1, props.size(), "the dblib provider's single property must survive");
        assertEquals("FromFactory", props.get(0).defaultValue());
    }


    @Test
    void testNoMatchingSupplierYieldsNoProperties(@TempDir Path tempDir)
    {
        URI other = tempDir.resolve("x.not-a-library").toUri();

        assertEquals(List.of(),
                LibraryProviderFactory.getInstance().getProviderProperties(other, null));
    }


    @Test
    void testProvideReturnsTheLibraryOfTheFirstSupplierThatCanRead(@TempDir Path tempDir)
        throws IOException
    {
        URI libUri = L2LibraryFixture.in(tempDir).name("FromFactory").writeUri();

        try (IDataTableLibrary lib = LibraryProviderFactory.getInstance().provide(libUri, null,
                Map.of()))
        {
            assertNotNull(lib);
            assertEquals("FromFactory", lib.getName());
        }
    }


    @Test
    void testProvideReturnsNullWhenNoSupplierMatches(@TempDir Path tempDir) throws IOException
    {
        Path file = tempDir.resolve("x.not-a-library");
        Files.writeString(file, "irrelevant");

        assertNull(LibraryProviderFactory.getInstance().provide(file.toUri(), null, Map.of()));
    }


    @Test
    void testProvideRethrowsTheProviderIoExceptionUnwrapped(@TempDir Path tempDir)
    {
        URI missing = tempDir.resolve("absent.dblib").toUri();

        // The provider's own FileNotFoundException must reach the caller as-is rather than
        // wrapped in a fresh IOException.
        assertThrows(FileNotFoundException.class,
                () -> LibraryProviderFactory.getInstance().provide(missing, null, Map.of()));
    }


    @Test
    void testProvideRethrowsTheProviderRuntimeExceptionUnwrapped()
    {
        // A relative URI reaches the provider (the extension matches) and fails in URI.toURL().
        assertThrows(IllegalArgumentException.class, () -> LibraryProviderFactory.getInstance()
                .provide(URI.create("relative.dblib"), null, Map.of()));
    }


    /**
     * B2-C4 (PLAN-bugfix-datatable-readers-views U12): when two suppliers' providers both throw,
     * {@code provide} surfaces the first failure with the second added as suppressed &mdash; the
     * {@code addSuppressed} arm was {@code NO_COVERAGE}. The two test-only suppliers
     * ({@link B2ThrowingFirstLibrarySupplier}, {@link B2ThrowingSecondLibrarySupplier}) answer only
     * their own scheme and are registered through {@code src/test/resources/META-INF/services}; no
     * production seam.
     */
    @Test
    void testTwoFailingProvidersSurfaceTheFirstWithTheSecondSuppressed()
    {
        IOException ex = assertThrows(IOException.class,
                () -> LibraryProviderFactory.getInstance().provide(
                        URI.create(B2ThrowingFirstLibrarySupplier.SCHEME + ":study"), null,
                        Map.of()));

        List<String> both = List.of(B2ThrowingFirstLibrarySupplier.MESSAGE,
                B2ThrowingSecondLibrarySupplier.MESSAGE);
        assertTrue(both.contains(ex.getMessage()), ex::toString);
        assertEquals(1, ex.getSuppressed().length, () -> List.of(ex.getSuppressed()).toString());
        Throwable suppressed = ex.getSuppressed()[0];
        assertTrue(both.contains(suppressed.getMessage()), suppressed::toString);
        assertNotEquals(ex.getMessage(), suppressed.getMessage(),
                "the suppressed failure is the OTHER provider's");
    }
}
