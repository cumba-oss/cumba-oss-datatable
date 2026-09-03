package net.cumba.datatable.provider.dsj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import net.cumba.cdisc.dsj.DataSetJsonTableParallelParser;
import net.cumba.cdisc.dsj.DataSetJsonTableParser;
import net.cumba.datatable.IDataTable;
import org.junit.jupiter.api.Test;

/**
 * Parity tests for the parallel NDJSON load path. The parallel parser splits an NDJSON byte stream
 * into per-chunk byte ranges, parses each in its own thread, and the {@link DsjTableProvider}
 * merges per-chunk column buffers. These tests assert that the result is byte-equivalent to what
 * the single-threaded path produces from the same input.
 */
class DsjTableProviderParallelTest
{

    /**
     * The parser's parallel branch only triggers when the file is at least this many bytes (and is
     * plain NDJSON, and is accessed via a {@link Path}). Tests that want to force parallel on small
     * data set this to {@code 0}.
     */
    private static final long FORCE_PARALLEL = 0L;

    /**
     * Copies a classpath fixture to a temp file and returns its {@link Path}.
     */
    private Path materialiseFixture(String fixtureName, String suffix) throws IOException
    {
        File tmp = File.createTempFile("dsj2parallel-", suffix);
        tmp.deleteOnExit();
        try (InputStream in = getClass().getResourceAsStream("/fixtures/dsj2/" + fixtureName))
        {
            assertNotNull(in, "fixture not found: " + fixtureName);
            Files.copy(in, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        return tmp.toPath();
    }


    /**
     * Parser-level parity: the rows emitted by the parallel parser (via
     * {@link DataSetJsonTableParallelParser#getHandlerChunkRows()}) must match the rows emitted by
     * the parent {@link DataSetJsonTableParser} (via
     * {@link DataSetJsonTableParser#getHandlerRow()}) when both consume the same NDJSON file.
     */
    @Test
    void parallelParserMatchesSingleThreadedParserRowByRow() throws IOException
    {
        Path file = materialiseFixture("parallel-mixed-2500.ndjson", ".ndjson");

        // Reference: single-threaded parser via row handler.
        List<Object[]> reference = new ArrayList<>();
        DataSetJsonTableParser ref = new DataSetJsonTableParser();
        ref.setHandlerRow((_, _, values) ->
        {
            reference.add(values.clone());
            return 0;
        });
        ref.parseDataSet(file);

        int parCnt = 4;

        // Parallel: chunk handler aggregates into per-chunk lists, then we concatenate in chunk
        // order. Each chunk's rows are appended in the order parsed within the chunk; chunks
        // themselves are stitched by chunkIdx so the final sequence matches the on-disk row order.
        DataSetJsonTableParallelParser par = new DataSetJsonTableParallelParser();
        par.setMinBytesForParallel(FORCE_PARALLEL);
        par.setParallelism(parCnt);

        List<List<Object[]>> perChunk = new ArrayList<>(parCnt);
        for (int i = 0; i < parCnt; i++)
        {
            perChunk.add(new ArrayList<>());
        }
        par.setHandlerChunkRows((chunkIdx, _, count, rs) ->
        {
            List<Object[]> bucket = perChunk.get(chunkIdx);
            for (int i = 0; i < count; i++)
            {
                bucket.add(rs[i].clone());
            }
            return 0;
        });
        par.parseDataSet(file);

        List<Object[]> parallel = new ArrayList<>();
        for (List<Object[]> b : perChunk)
        {
            parallel.addAll(b);
        }

        assertEquals(reference.size(), parallel.size(), "parsed row counts diverge");
        for (int i = 0; i < reference.size(); i++)
        {
            Object[] r = reference.get(i);
            Object[] p = parallel.get(i);
            assertEquals(r.length, p.length, "column count mismatch at row " + i);
            for (int c = 0; c < r.length; c++)
            {
                assertEquals(r[c], p[c], "value mismatch at row " + i + " col " + c + " (ref="
                        + r[c] + ", par=" + p[c] + ")");
            }
        }
    }


    /**
     * Provider-level parity: load the same NDJSON file through the provider with the parallel
     * branch forced and with it suppressed (large minBytesForParallel). The resulting tables must
     * compare equal cell-for-cell.
     */
    @Test
    void providerParallelTableMatchesSingleThreadedTable() throws IOException
    {
        Path file = materialiseFixture("parallel-mixed-2500.ndjson", ".ndjson");
        URI uri = file.toUri();

        DsjTableProvider providerRef = new DsjTableProvider();
        providerRef.setMinBytesForParallel(Long.MAX_VALUE); // never parallel
        IDataTable reference = providerRef.provide(uri, DsjProviderSupplier.FI_DSJ_NDJSON);

        DsjTableProvider providerPar = new DsjTableProvider();
        providerPar.setMinBytesForParallel(FORCE_PARALLEL); // always parallel
        providerPar.setParallelism(4);
        IDataTable parallel = providerPar.provide(uri, DsjProviderSupplier.FI_DSJ_NDJSON);

        assertNotNull(reference);
        assertNotNull(parallel);
        assertEquals(reference.getRowCount(), parallel.getRowCount(),
                "row counts diverge between paths");
        assertEquals(reference.getColumnCount(), parallel.getColumnCount(),
                "column counts diverge between paths");
        long rc = reference.getRowCount();
        int cc = reference.getColumnCount();
        for (long r = 0; r < rc; r++)
        {
            for (int c = 0; c < cc; c++)
            {
                Object refVal = reference.getValue(r, c);
                Object parVal = parallel.getValue(r, c);
                assertEquals(refVal, parVal, "cell mismatch at row " + r + " col " + c);
            }
        }
    }


    /**
     * Parallel-merge native-row-order invariant. Providers do not sort on load, so both the
     * single-threaded and parallel paths return their underlying
     * {@link net.cumba.datatable.impl.ColumnCachedDataTable} directly. The invariant exercised here
     * is that row {@code N} of the buffer corresponds to the {@code N}-th NDJSON line on both paths
     * — i.e. the parallel chunked load stitches chunks back together in file order.
     *
     * <p>
     * Exposed through two checks: (1) the {@code FILEORDER} witness column carries
     * monotonically-increasing values matching the file row position, so {@code getValue(real,
     * FILEORDER) == real} must hold on both tables; (2) {@code getRealRowIndex(display)} must agree
     * between the two paths for every row (here a trivial identity check, since neither path
     * applies a sort, but the test still catches a chunk-merge regression that produced a
     * non-trivial reorder).
     * </p>
     *
     * <p>
     * The fixture is deliberately tie-heavy: rows cycle through three GROUP values, so any future
     * re-introduction of sort on this path would be dominated by tie-breaking — and tie-breaking is
     * what surfaces native-order mismatches. A regression that parsed chunk slices in some other
     * order than file order would break the FILEORDER witness check.
     * </p>
     */
    @Test
    void parallelMergeMatchesSingleThreadedFileRowOrder() throws IOException
    {
        Path file = materialiseFixture("parallel-grouped-5000.ndjson", ".ndjson");
        URI uri = file.toUri();

        DsjTableProvider providerRef = new DsjTableProvider();
        providerRef.setMinBytesForParallel(Long.MAX_VALUE); // single-threaded
        IDataTable reference = providerRef.provide(uri, DsjProviderSupplier.FI_DSJ_NDJSON);

        DsjTableProvider providerPar = new DsjTableProvider();
        providerPar.setMinBytesForParallel(FORCE_PARALLEL); // parallel
        providerPar.setParallelism(4);
        IDataTable parallel = providerPar.provide(uri, DsjProviderSupplier.FI_DSJ_NDJSON);

        // Sanity: both tables are returned in native file order, with identical shape.
        long rc = reference.getRowCount();
        assertEquals(rc, parallel.getRowCount(), "row counts diverge");

        // The witness column ("FILEORDER") was written with monotonically increasing values
        // matching the file row position. If the parallel path preserves native file order in
        // its underlying buffer, then the value at real row N is N — same as the single-threaded
        // path. We assert this independently of sort behaviour.
        int fileOrderColIdx = reference.getMetaData().getColumnIndex("FILEORDER");
        for (long real = 0; real < rc; real++)
        {
            // Look up the display index for this real index, then compare values. Both paths
            // must agree.
            long refDisplay = reference.getDisplayRowIndex(real);
            long parDisplay = parallel.getDisplayRowIndex(real);
            assertEquals(refDisplay, parDisplay, "display index diverges for real row " + real);
            // J2: an integer-declared column (FILEORDER) is stored as a floating-point value
            // (getTypeFor(INTEGER) -> DOUBLE), so getValue returns a Double, not a Long.
            assertEquals(Double.valueOf((double) real),
                    reference.getValue(refDisplay, fileOrderColIdx),
                    "reference: FILEORDER@real=" + real + " is not " + real);
            assertEquals(Double.valueOf((double) real),
                    parallel.getValue(parDisplay, fileOrderColIdx),
                    "parallel: FILEORDER@real=" + real + " is not " + real);
        }

        // The strong invariant: walk the visible (sorted) tables and verify the display→real
        // mapping is identical between the two paths. This catches any divergence in either the
        // underlying native order or in the sort itself.
        for (long display = 0; display < rc; display++)
        {
            long refReal = reference.getRealRowIndex(display);
            long parReal = parallel.getRealRowIndex(display);
            assertEquals(refReal, parReal, "getRealRowIndex(" + display + ") diverges (ref="
                    + refReal + ", par=" + parReal + ")");
        }
    }


    /**
     * Parallel branch must be skipped for non-NDJSON layouts. Compressed (.dsjc) input has a zlib
     * header that the parser detects via the two-byte peek; rows-inside-metadata layouts (plain
     * .json) are detected when the metadata locator hits the {@code "rows"} field. Both cases fall
     * through to the single-threaded super-class path, which the {@link DsjTableProvider} services
     * via the row-handler branch — meaning chunk parsers are never created.
     */
    @Test
    void plainJsonLayoutBypassesParallelBranch() throws IOException
    {
        Path file = materialiseFixture("parallel-mixed-500.json", ".json");
        URI uri = file.toUri();

        DsjTableProvider provider = new DsjTableProvider();
        provider.setMinBytesForParallel(FORCE_PARALLEL); // would parallelise...
        provider.setParallelism(4);
        IDataTable table = provider.provide(uri, DsjProviderSupplier.FI_DSJ_JSON);
        // ... but plain JSON has rows inside the metadata object — the locator returns
        // notParallelisable and the parser falls back to its super-class path. The provider then
        // returns the single-parser result. Successful load with the right row count is the
        // visible signal.
        assertEquals(500, table.getRowCount());
    }

}
