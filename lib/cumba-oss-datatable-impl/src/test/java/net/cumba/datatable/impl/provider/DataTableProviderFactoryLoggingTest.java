package net.cumba.datatable.impl.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Map;
import net.cumba.datatable.impl.provider.S3ProbeProviderSupplier.FailMode;
import net.cumba.datatable.impl.testsupport.LoggerCapture;
import net.cumba.datatable.metadata.IMetadataLibrary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Two failures inside {@link DataTableProviderFactory} are deliberately swallowed - a probe that
 * threw while another supplier may still succeed, and a provider that refuses to be reset. Each is
 * recorded at DEBUG, and that record is the only trace they leave, so this pins what the records
 * say rather than that they exist.
 */
class DataTableProviderFactoryLoggingTest
{

    private static final URI PROBE_URI = URI.create("file:///tmp/log-probe.s3a9probe");

    private final DataTableProviderFactory factory = DataTableProviderFactory.getFactory();

    @BeforeEach
    void resetProbe()
    {
        S3ProbeProviderSupplier.reset();
    }


    @AfterEach
    void resetAfter()
    {
        S3ProbeProviderSupplier.reset();
    }


    @Test
    void aFailedMetadataProbeIsRecordedWithItsOwnMessageAndThrowable()
    {
        S3ProbeProviderSupplier.failNext = true;
        S3ProbeProviderSupplier.failMode = FailMode.IO_EXCEPTION;
        IMetadataLibrary metadata = Mockito.mock(IMetadataLibrary.class);

        try (LoggerCapture log = LoggerCapture.attach(DataTableProviderFactory.class.getName()))
        {
            assertThrows(IOException.class, () -> factory.provideMetaData(PROBE_URI,
                    S3ProbeProviderSupplier.PROBE_INFO, metadata, Collections.emptyMap()));

            int i = log.indexOfMessageContaining("probe forced failure at provideMetaData");
            assertTrue(i >= 0, "the suppressed probe failure must be recorded: " + log.messages());
            Throwable thrown = log.records().get(i).getThrown();
            assertInstanceOf(IOException.class, thrown,
                    "the throwable itself must travel with the record, not just its text");
            assertEquals(thrown.getMessage(), log.messages().get(i));
        }
    }


    @Test
    void aProviderThatRefusesToBeResetIsRecordedWithTheResetFailure()
    {
        S3ProbeProviderSupplier.failNext = true;
        S3ProbeProviderSupplier.failMode = FailMode.IO_EXCEPTION;
        // The tripwire fires only on null writes, so the initial wiring succeeds and only
        // clearProviderState's reset throws.
        S3ProbeProviderSupplier.throwOnSetters = true;
        IMetadataLibrary metadata = Mockito.mock(IMetadataLibrary.class);

        try (LoggerCapture log = LoggerCapture.attach(DataTableProviderFactory.class.getName()))
        {
            assertThrows(IOException.class, () -> factory.provide(PROBE_URI,
                    S3ProbeProviderSupplier.PROBE_INFO, metadata, Map.of()));

            int i = log.indexOfMessageContaining("Provider state clear failed");
            assertTrue(i >= 0, "the failed reset must be recorded: " + log.messages());
            assertEquals("Provider state clear failed: setMetadata(null) boom",
                    log.messages().get(i));
            assertInstanceOf(IllegalStateException.class, log.records().get(i).getThrown());
        }
    }
}
