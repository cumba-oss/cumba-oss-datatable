package net.cumba.datatable.library.folder;

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
 * This registration contributes a library supplier rather than a data-table provider supplier, so
 * the registry is {@link LibraryProviderFactory} instead of the provider factory. The failure mode
 * is identical: a stale fully-qualified class name in that services file is invisible to the
 * compiler and to every other test in this module — {@code GenericServiceFactory} would simply find
 * no supplier, the library would be absent at runtime, and the build would stay green. This test is
 * the only thing that fails in that case.
 * </p>
 *
 * <p>
 * {@link FolderLibrarySupplier} declares its supported formats in an overridden
 * {@code getSupportedFileInfos()} rather than in a public constant, so the expected set is taken
 * from a freshly constructed instance — which also exercises the public no-argument constructor
 * that the service loader requires.
 * </p>
 */
class FolderLibrarySpiRegistrationTest
{

    @Test
    void supplierIsDiscoverableThroughTheSpi()
    {
        List<FileInfo> expected = new FolderLibrarySupplier().getSupportedFileInfos();
        assertFalse(expected.isEmpty(), "FolderLibrarySupplier must declare at least one FileInfo, "
                + "otherwise this guard would pass vacuously");

        List<FileInfo> registered = LibraryProviderFactory.getInstance().getFileInfos();

        for (FileInfo fi : expected)
        {
            assertTrue(registered.contains(fi),
                    () -> "FolderLibrarySupplier must be registered in META-INF/services — " + fi
                            + " was not discovered through the SPI");
        }
    }

}
