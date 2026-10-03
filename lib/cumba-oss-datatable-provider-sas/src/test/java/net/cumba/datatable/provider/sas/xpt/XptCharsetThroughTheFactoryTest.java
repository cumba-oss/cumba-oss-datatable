package net.cumba.datatable.provider.sas.xpt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.provider.DataTableProviderFactory;
import net.cumba.datatable.metadata.IMetadataLibrary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The factory arm of {@link XptCharsetReachesTheDecoderTest}: a charset handed to
 * {@link DataTableProviderFactory} in the property map must reach the XPT decoder. This arm is a
 * separate, per-tree test because the factory's property-carrying overload has a different arity in
 * the two repositories (the internal one also takes a loading-progress listener).
 */
class XptCharsetThroughTheFactoryTest
{

    @TempDir
    Path tmp;

    @Test
    void theFactoryForwardsProperties() throws IOException
    {
        Path xpt = tmp.resolve("charset.xpt");
        Files.write(xpt, XptCharsetReachesTheDecoderTest.xptBytes());
        IDataTable t = DataTableProviderFactory.getFactory().provide(xpt.toUri(),
                XptProviderSupplier.FI_XPT, (IMetadataLibrary) null,
                Map.of(XptTableProvider.PROP_CHARSET, "windows-1252"));
        assertEquals("Café", t.getValue(0, 0));
    }
}
