package net.cumba.datatable.impl.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileNotFoundException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Map;
import net.cumba.datatable.impl.testsupport.LoggerCapture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link LibraryProviderFactory#provide} keeps probing after a supplier throws, and only the first
 * failure is ever rethrown - every later one survives solely as a suppressed exception plus this
 * DEBUG record. Pinning the record's content keeps a probe failure from becoming invisible.
 */
class LibraryProviderFactoryLoggingTest
{

    @Test
    void aProviderFailureIsRecordedWithItsOwnMessageBeforeBeingRethrown(@TempDir Path tempDir)
    {
        URI missing = tempDir.resolve("absent.dblib").toUri();

        try (LoggerCapture log = LoggerCapture.attach(LibraryProviderFactory.class.getName()))
        {
            assertThrows(FileNotFoundException.class,
                    () -> LibraryProviderFactory.getInstance().provide(missing, null, Map.of()));

            assertEquals(1, log.records().size(), "one record: " + log.messages());
            Throwable thrown = log.records().get(0).getThrown();
            assertInstanceOf(FileNotFoundException.class, thrown,
                    "the throwable itself must travel with the record");
            assertEquals(thrown.getMessage(), log.messages().get(0));
            assertTrue(log.containsMessageContaining("absent.dblib"),
                    "the record must name the library that failed: " + log.messages());
        }
    }
}
