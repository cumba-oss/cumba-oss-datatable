package net.cumba.datatable.provider.dsj;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.cumba.datatable.impl.databuffer.DataBufferFactory;
import net.cumba.datatable.impl.databuffer.DataBufferObject;
import net.cumba.datatable.impl.databuffer.IDataBuffer;
import net.cumba.datatable.impl.databuffer.IDataBufferNumeric;
import net.cumba.datatable.values.DataValueType;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The catch arm of {@code addData2Column} is a net for failures no document should cause (a column
 * buffer breaking its contract). Since PLAN-oss-dsj-malformed-boolean-cell every value a document
 * can carry is handled per cell, so a buffer that refuses every value is injected through the
 * {@link DataBufferFactory} seam: the load must fail, and the cell must be logged with its
 * coordinates -- as text, so the message does not depend on the host locale.
 */
class DsjTableProviderUnexpectedFailureTest
{

    private static final class CapturingHandler extends Handler
    {

        private final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord aRecord)
        {
            records.add(aRecord);
        }


        @Override
        public void flush()
        {
            // nothing buffered
        }


        @Override
        public void close()
        {
            // nothing to release
        }
    }


    /** Delegates everything, but hands a DOUBLE column a buffer that refuses every value. */
    private static final class RefusingDoubleColumnFactory implements DataBufferFactory
    {

        private final DataBufferFactory delegate;

        RefusingDoubleColumnFactory(DataBufferFactory aDelegate)
        {
            delegate = aDelegate;
        }


        @Override
        public IDataBuffer createColumnBuffer(DataValueType aType)
        {
            if (aType != DataValueType.DOUBLE)
            {
                return delegate.createColumnBuffer(aType);
            }
            return new DataBufferObject()
            {

                @Override
                public void setValue(int aIndex, @Nullable Object aElement)
                {
                    throw new IllegalStateException("refusing buffer");
                }
            };
        }


        @Override
        public IDataBufferNumeric createForRange(long aMin, long aMax)
        {
            return delegate.createForRange(aMin, aMax);
        }
    }

    @Test
    void unexpectedCellFailureLogsItsCoordinatesAndFails(@TempDir Path tmp) throws IOException
    {
        String json = """
                {
                  "datasetJSONCreationDateTime": "2026-09-25T10:00:00",
                  "datasetJSONVersion": "1.1.0",
                  "itemGroupOID": "IG.BROKEN",
                  "name": "BROKEN",
                  "label": "Broken buffer",
                  "records": 1,
                  "columns": [
                    {"itemOID": "IT.D", "name": "D", "label": "D", "dataType": "double"}
                  ],
                  "rows": [[1.5]]
                }
                """;
        Path file = tmp.resolve("broken.json");
        Files.writeString(file, json, StandardCharsets.UTF_8);
        URI uri = file.toUri();

        DataBufferFactory previous = DataBufferFactory.get();
        Logger julLogger = Logger.getLogger(DsjTableProvider.class.getName());
        Level oldLevel = julLogger.getLevel();
        CapturingHandler capture = new CapturingHandler();
        Exception thrown;
        try
        {
            DataBufferFactory.set(new RefusingDoubleColumnFactory(previous));
            julLogger.setLevel(Level.ALL);
            julLogger.addHandler(capture);
            DsjTableProvider provider = new DsjTableProvider();
            thrown = assertThrows(Exception.class,
                    () -> provider.provide(uri, DsjProviderSupplier.FI_DSJ_JSON),
                    "a failure the provider does not expect must fail the load, not be dropped");
        }
        finally
        {
            DataBufferFactory.set(previous);
            julLogger.removeHandler(capture);
            julLogger.setLevel(oldLevel);
        }

        assertTrue(
                capture.records.stream()
                        .anyMatch(r -> r.getMessage() != null && r.getMessage()
                                .contains("Error while handling value 1.5 [row=0, column=0]")),
                "the failing cell must be logged with its coordinates; thrown=" + thrown);
    }
}
