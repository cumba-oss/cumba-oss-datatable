package net.cumba.datatable.provider.dsj;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.cumba.datatable.impl.provider.DataTableProviderFactory;
import net.cumba.datatable.io.FileInfo;
import org.junit.jupiter.api.Test;

/**
 * Guards the SPI registration in
 * {@code META-INF/services/net.cumba.datatable.provider.IProviderSupplier}.
 *
 * <p>
 * The package was renamed from {@code ...provider.dsj2} to {@code ...provider.dsj} during the
 * migration out of corej. A stale fully-qualified class name in that services file is invisible to
 * the compiler and to every other test in this module: {@code GenericServiceFactory} would simply
 * find no supplier, the provider would be absent at runtime, and the build would stay green. This
 * test is the only thing that fails in that case.
 * </p>
 */
class DsjProviderSpiRegistrationTest
{

    @Test
    void supplierIsDiscoverableThroughTheSpi()
    {
        List<FileInfo> registered = DataTableProviderFactory.getFactory().getFileInfos();

        for (FileInfo expected : DsjProviderSupplier.FIS)
        {
            assertTrue(registered.contains(expected),
                    () -> "DsjProviderSupplier must be registered in META-INF/services — "
                            + expected + " was not discovered through the SPI");
        }
    }

}
