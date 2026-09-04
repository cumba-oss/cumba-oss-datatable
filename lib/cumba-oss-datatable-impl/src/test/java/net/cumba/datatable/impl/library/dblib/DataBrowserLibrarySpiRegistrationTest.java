package net.cumba.datatable.impl.library.dblib;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.cumba.datatable.impl.library.LibraryProviderFactory;
import net.cumba.datatable.io.FileInfo;
import org.junit.jupiter.api.Test;

/**
 * Guards the SPI registration in
 * {@code META-INF/services/net.cumba.datatable.library.ILibrarySupplier}.
 *
 * <p>
 * A stale fully-qualified class name in that services file is invisible to the compiler and to
 * every other test in this module — {@code GenericServiceFactory} would simply find no supplier,
 * the Data Browser library would be absent at runtime, and the build would stay green. This test is
 * the only thing that fails in that case.
 * </p>
 *
 * <p>
 * ⚠ This module owns {@link LibraryProviderFactory} itself, which makes the registration easy to
 * assume rather than verify: the factory being present says nothing about whether this supplier is
 * reachable through it.
 * </p>
 */
class DataBrowserLibrarySpiRegistrationTest
{

    @Test
    void theSupplierIsDiscoverableThroughTheServiceLoader()
    {
        List<FileInfo> expected = DataBrowserLibrarySupplier.FIS;
        assertFalse(expected.isEmpty(),
                "DataBrowserLibrarySupplier declares no FileInfo, so this guard would pass"
                        + " vacuously — the assertion below could never fail");

        List<FileInfo> registered = LibraryProviderFactory.getInstance().getFileInfos();
        for (FileInfo fi : expected)
        {
            assertTrue(registered.contains(fi),
                    () -> "DataBrowserLibrarySupplier must be registered in META-INF/services"
                            + " — " + fi + " was not discovered through the SPI");
        }
    }
}
