package net.cumba.datatable.provider.sas;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;
import net.cumba.datatable.impl.provider.DataTableProviderFactory;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.provider.sas.sas7bdat.BdatProviderSupplier;
import net.cumba.datatable.provider.sas.xpt.XptProviderSupplier;
import org.junit.jupiter.api.Test;

/**
 * Guards the SPI registration in
 * {@code META-INF/services/net.cumba.datatable.provider.IProviderSupplier}.
 *
 * <p>
 * A stale fully-qualified class name in that services file is invisible to the compiler and to
 * every other test in this module: {@code GenericServiceFactory} would simply find no supplier, the
 * provider would be absent at runtime, and the build would stay green. This test is the only thing
 * that fails in that case.
 * </p>
 *
 * <p>
 * This module registers two provider suppliers in a single services file, so both are checked here.
 * </p>
 */
class SasProviderSpiRegistrationTest
{

    @Test
    void suppliersAreDiscoverableThroughTheSpi()
    {
        List<FileInfo> expected = Stream
                .concat(BdatProviderSupplier.FIS.stream(), XptProviderSupplier.FIS.stream())
                .toList();
        assertFalse(expected.isEmpty(),
                "BdatProviderSupplier and XptProviderSupplier must declare at least one "
                        + "FileInfo, otherwise this guard would pass vacuously");

        List<FileInfo> registered = DataTableProviderFactory.getFactory().getFileInfos();

        for (FileInfo fi : expected)
        {
            assertTrue(registered.contains(fi),
                    () -> "BdatProviderSupplier and XptProviderSupplier must be registered "
                            + "in META-INF/services — " + fi
                            + " was not discovered through the SPI");
        }
    }

}
