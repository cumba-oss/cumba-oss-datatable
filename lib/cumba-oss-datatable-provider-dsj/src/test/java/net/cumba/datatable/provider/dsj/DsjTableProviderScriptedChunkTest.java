package net.cumba.datatable.provider.dsj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import net.cumba.cdisc.dsj.DataSetJsonTableParallelParser;
import net.cumba.cdisc.dsj.DsjTable;
import net.cumba.cdisc.dsj.DsjTableColumn;
import net.cumba.cdisc.dsj.MetadataHandler;
import net.cumba.datatable.IDataTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Deterministic chunk-merge tests driven through the {@link DsjTableProvider#createParallelParser}
 * seam (owner ruling, Q35/G8: "should the chunk boundary be injectable — YES").
 *
 * <p>
 * The real {@link DataSetJsonTableParallelParser} derives its chunk boundaries from byte offsets
 * and delivers batches from four racing workers, so batch alignment — and with it the merge order
 * and the delivery-vs-assembly row check — is not deterministically reachable through real files.
 * The {@link ScriptedParallelParser} below replays a fixed batch script through the same
 * {@code handlerMetadata} / {@code handlerChunkRows} callbacks the real parser drives, which turns
 * those behaviours into ordinary assertions.
 * </p>
 */
class DsjTableProviderScriptedChunkTest
{

    /**
     * One scripted {@code handlerChunkRows} callback: a batch of rows for one chunk index.
     *
     * <p>
     * [ArrayRecordComponent] suppressed: this is a recording carrier, never compared or hashed --
     * the grid is the row payload the parser handed the callback, and copying it defensively would
     * hide an aliasing bug rather than expose one.
     * </p>
     */
    @SuppressWarnings("ArrayRecordComponent")
    private record Batch(int chunkIdx, Object[][] rows)
    {
    }


    /**
     * Test double that replays a scripted sequence of chunk batches instead of parsing anything.
     * Deliberately does NOT run {@code verifyRecordCount}: the provider's own delivery-vs-assembly
     * check ("Can't read all rows") is unreachable through the real parser, whose own verification
     * fires first — the script is the only way to exercise it.
     */
    private static final class ScriptedParallelParser extends DataSetJsonTableParallelParser
    {

        private final DsjTable table;

        private final List<Batch> batches;

        private final List<Integer> parallelismCalls = new ArrayList<>();

        ScriptedParallelParser(DsjTable aTable, List<Batch> aBatches)
        {
            this.table = aTable;
            this.batches = aBatches;
        }


        @Override
        public void setParallelism(int aParallelism)
        {
            parallelismCalls.add(aParallelism);
            super.setParallelism(aParallelism);
        }


        @Override
        public void parseDataSet(Path aFile) throws IOException
        {
            replay();
        }


        @Override
        public void parseDataSet(InputStream aStream) throws IOException
        {
            replay();
        }


        private void replay() throws IOException
        {
            MetadataHandler mh = getHandlerMetadata();
            if (mh != null && mh.metadata(table) != 0)
            {
                throw new IOException("User aborted!");
            }
            ChunkRowsHandler handler = Objects.requireNonNull(getHandlerChunkRows(),
                    "the provider must have registered a chunk-rows handler");
            for (Batch b : batches)
            {
                if (handler.chunkRows(b.chunkIdx(), table, b.rows().length, b.rows()) != 0)
                {
                    throw new IOException("User aborted!");
                }
            }
        }
    }


    /** Provider whose parallel parser is replaced by a script via the G8 seam. */
    private static final class ScriptedProvider extends DsjTableProvider
    {

        private final ScriptedParallelParser parser;

        ScriptedProvider(ScriptedParallelParser aParser)
        {
            this.parser = aParser;
        }


        @Override
        protected DataSetJsonTableParallelParser createParallelParser()
        {
            return parser;
        }
    }

    private static DsjTable table(long aRecords, DsjTableColumn... aColumns)
    {
        return DsjTable.builder().datasetJSONCreationDateTime("2026-09-15T10:00:00")
                .itemGroupOID("IG.SCRIPT").name("SCRIPT").label("Scripted table").records(aRecords)
                .columns(aColumns).build();
    }


    private static DsjTableColumn stringColumn(int aIndex, String aName)
    {
        return DsjTableColumn.builder().index(aIndex).itemOID("IT." + aName).name(aName)
                .label(aName).dataType("string").build();
    }


    private static Object[][] stringRows(String aPrefix, int aCount)
    {
        Object[][] rows = new Object[aCount][];
        for (int i = 0; i < aCount; i++)
        {
            rows[i] = new Object[]
            {
                    aPrefix + i
            };
        }
        return rows;
    }


    private static URI scriptUri(Path aDir) throws IOException
    {
        Path f = aDir.resolve("scripted.ndjson");
        Files.writeString(f, "replaced by the scripted parser");
        return f.toUri();
    }

    // ---- provide:212 — the configured parallelism must reach the parser ---------------------


    @Test
    void configuredParallelismIsForwardedToTheParser(@TempDir Path tmp) throws IOException
    {
        ScriptedParallelParser parser = new ScriptedParallelParser(table(2, stringColumn(0, "A")),
                List.of(new Batch(0, stringRows("r", 2))));
        ScriptedProvider provider = new ScriptedProvider(parser);
        provider.setParallelism(3);

        IDataTable result = provider.provide(scriptUri(tmp), DsjProviderSupplier.FI_DSJ_NDJSON);

        assertEquals(2, result.getRowCount());
        assertEquals(List.of(3), parser.parallelismCalls,
                "provider.setParallelism(3) must be forwarded to the parser exactly once");
    }

    // ---- provide:357 — the delivery-vs-assembly row check ----------------------------------


    @Test
    void assembledRowsAgainstDeclaredZeroRecordsFailTheLoad(@TempDir Path tmp) throws IOException
    {
        // records=0 is the boundary case: `declaredRowCount >= 0` must still arm the check.
        ScriptedParallelParser parser = new ScriptedParallelParser(table(0, stringColumn(0, "A")),
                List.of(new Batch(0, stringRows("r", 2))));
        ScriptedProvider provider = new ScriptedProvider(parser);
        URI uri = scriptUri(tmp);

        IOException ex = assertThrows(IOException.class,
                () -> provider.provide(uri, DsjProviderSupplier.FI_DSJ_NDJSON));
        assertTrue(ex.getMessage().contains("Can't read all rows"),
                "expected the provider's delivery-vs-assembly check, got: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("Expected=0, found=2"), ex.getMessage());
    }


    @Test
    void assembledRowsShortOfDeclaredRecordsFailTheLoad(@TempDir Path tmp) throws IOException
    {
        ScriptedParallelParser parser = new ScriptedParallelParser(table(5, stringColumn(0, "A")),
                List.of(new Batch(0, stringRows("r", 3))));
        ScriptedProvider provider = new ScriptedProvider(parser);
        URI uri = scriptUri(tmp);

        IOException ex = assertThrows(IOException.class,
                () -> provider.provide(uri, DsjProviderSupplier.FI_DSJ_NDJSON));
        assertTrue(ex.getMessage().contains("Expected=5, found=3"), ex.getMessage());
    }

    // ---- mergeChunkParsers:476/487 — chunk-index merge order and the clamp log --------------


    @Test
    void chunksAreMergedInChunkIndexOrderNotMapIterationOrder(@TempDir Path tmp) throws IOException
    {
        // Chunk indices 16 and 1 collide with ConcurrentHashMap's bin layout in reverse: key 16
        // lands in bin 0 and key 1 in bin 1, so the UNSORTED key set iterates [16, 1]. Only the
        // explicit sort in mergeChunkParsers restores file order. Delivery order (16 first) also
        // differs from merge order on purpose.
        ScriptedParallelParser parser = new ScriptedParallelParser(table(10, stringColumn(0, "A")),
                List.of(new Batch(16, stringRows("B", 5)), new Batch(1, stringRows("A", 5))));
        ScriptedProvider provider = new ScriptedProvider(parser);

        Logger julLogger = Logger.getLogger(DsjTableProvider.class.getName());
        java.util.logging.Level oldLevel = julLogger.getLevel();
        List<LogRecord> records = new ArrayList<>();
        Handler capture = new Handler()
        {

            @Override
            public void publish(LogRecord aRecord)
            {
                records.add(aRecord);
            }


            @Override
            public void flush()
            {
                // no-op
            }


            @Override
            public void close()
            {
                // no-op
            }
        };
        julLogger.setLevel(java.util.logging.Level.ALL);
        julLogger.addHandler(capture);
        try
        {
            IDataTable result = provider.provide(scriptUri(tmp), DsjProviderSupplier.FI_DSJ_NDJSON);

            assertEquals(10, result.getRowCount());
            for (int i = 0; i < 5; i++)
            {
                assertEquals("A" + i, result.getValue(i, 0),
                        "row " + i + " must come from the LOWER chunk index (1)");
                assertEquals("B" + i, result.getValue(5 + i, 0),
                        "row " + (5 + i) + " must come from the HIGHER chunk index (16)");
            }

            // mergeChunkParsers:487 — the Integer.MAX_VALUE clamp warning must NOT fire for a
            // total that fits comfortably in an int (kills the negated conditional).
            assertFalse(
                    records.stream()
                            .anyMatch(r -> r.getMessage() != null
                                    && r.getMessage().contains("exceeds Integer.MAX_VALUE")),
                    "the expected-size clamp log must only fire for totals above "
                            + "Integer.MAX_VALUE");
            assertNotNull(result.getMetaData());
        }
        finally
        {
            julLogger.removeHandler(capture);
            julLogger.setLevel(oldLevel);
        }
    }

}
