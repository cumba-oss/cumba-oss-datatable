package net.cumba.datatable.provider.sas.sas7bdat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.CachedDataTableColumn;
import net.cumba.datatable.values.DataValueType;
import net.cumba.sasutils.PositionAwareInputStream;
import net.cumba.sasutils.VariableType;
import net.cumba.sasutils.bdat.DatasetBdat;
import net.cumba.sasutils.bdat.ParserBdat;
import net.cumba.sasutils.bdat.VariableBdat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Verifies the parallel page-pipeline loader against the sequential loader: identical tables (or
 * identical failures) per fixture, deterministic output under varying parallelism, and correct
 * error/abort propagation with pool shutdown.
 */
class BdatParallelLoaderTest
{

    private static URI fixtureUri(String aName) throws URISyntaxException
    {
        var url = BdatParallelLoaderTest.class.getResource(aName + ".sas7bdat");
        assertNotNull(url, "fixture missing: " + aName);
        return url.toURI();
    }


    private static byte[] fixtureBytes(String aName) throws IOException
    {
        try (var in = BdatParallelLoaderTest.class.getResourceAsStream(aName + ".sas7bdat"))
        {
            assertNotNull(in, "fixture missing: " + aName);
            return in.readAllBytes();
        }
    }

    /** Outcome of a load: either a table or the message of the IOException it threw. */
    private record Outcome(IDataTable table, String error)
    {
    }

    /** Load with the stream pipeline (mmap forced off) — used by the stream-path parity tests. */
    private static Outcome load(URI aUri, String aParallel, String aParallelism)
    {
        return load(aUri, aParallel, "false", aParallelism);
    }


    private static Outcome load(URI aUri, String aParallel, String aMmap, String aParallelism)
    {
        String oldParallel = System.getProperty("bdat.parallel");
        String oldMmap = System.getProperty("bdat.mmap");
        String oldParallelism = System.getProperty("bdat.parallelism");
        try
        {
            setOrClear("bdat.parallel", aParallel);
            setOrClear("bdat.mmap", aMmap);
            setOrClear("bdat.parallelism", aParallelism);
            return new Outcome(new BdatTableProvider().provide(aUri, BdatProviderSupplier.FI_BDAT),
                    null);
        }
        catch (IOException e)
        {
            return new Outcome(null, e.getMessage());
        }
        finally
        {
            setOrClear("bdat.parallel", oldParallel);
            setOrClear("bdat.mmap", oldMmap);
            setOrClear("bdat.parallelism", oldParallelism);
        }
    }


    private static void setOrClear(String aKey, String aValue)
    {
        if (aValue == null)
        {
            System.clearProperty(aKey);
        }
        else
        {
            System.setProperty(aKey, aValue);
        }
    }


    @ParameterizedTest
    @ValueSource(strings =
    {
            "doubles_12x25", "int_only_25x52", "numeric1_15x4733", "mix_data_misc_14x12288",
            "mixed4_13x15564", "data_page_with_deleted_3x997", "comp_deleted_4x3", "charset_wlt1"
    })
    void parallelMatchesSequential(String aFixture) throws URISyntaxException
    {
        URI uri = fixtureUri(aFixture);
        Outcome sequential = load(uri, "false", null);
        Outcome parallel = load(uri, "true", null);

        if (sequential.error() != null || parallel.error() != null)
        {
            assertEquals(sequential.error(), parallel.error(),
                    "parallel and sequential must fail identically for " + aFixture);
        }
        else
        {
            assertTablesEqual(sequential.table(), parallel.table(), aFixture);
        }
    }


    @ParameterizedTest
    @ValueSource(strings =
    {
            "1", "2", "8"
    })
    void deterministicAcrossParallelism(String aParallelism) throws URISyntaxException
    {
        URI uri = fixtureUri("mix_data_misc_14x12288");
        Outcome sequential = load(uri, "false", null);
        Outcome parallel = load(uri, "true", aParallelism);

        assertNotNull(sequential.table());
        assertNotNull(parallel.table(), "parallel load failed: " + parallel.error());
        assertTablesEqual(sequential.table(), parallel.table(), "parallelism=" + aParallelism);
    }


    /**
     * The memory-mapped fast path (local {@code file:} + parallel + mmap) must produce a table
     * identical to the sequential loader. Each fixture is written to a real temp file so its
     * {@code file:} URI triggers mmap.
     */
    @ParameterizedTest
    @ValueSource(strings =
    {
            "doubles_12x25", "numeric1_15x4733", "mix_data_misc_14x12288", "mixed4_13x15564",
            "data_page_with_deleted_3x997", "comp_deleted_4x3", "charset_wlt1"
    })
    void mmapMatchesSequential(String aFixture, @TempDir Path aDir) throws IOException
    {
        Path file = aDir.resolve(aFixture + ".sas7bdat");
        Files.write(file, fixtureBytes(aFixture));
        URI uri = file.toUri();

        Outcome sequential = load(uri, "false", "false", null);
        Outcome mmap = load(uri, "true", "true", null);

        if (sequential.error() != null || mmap.error() != null)
        {
            assertEquals(sequential.error(), mmap.error(),
                    "mmap and sequential must fail identically for " + aFixture);
        }
        else
        {
            assertTablesEqual(sequential.table(), mmap.table(), "mmap:" + aFixture);
        }
    }


    @Test
    void mmapCandidateSelectsLocalReadableFilesOnly(@TempDir Path aDir) throws IOException
    {
        Path file = aDir.resolve("x.sas7bdat");
        Files.write(file, new byte[]
        {
                1, 2, 3
        });

        // A real local file (mmap + parallel default-on) is a candidate.
        assertNotNull(BdatTableProvider.mmapCandidate(file.toUri()));

        // Non-file schemes are never candidates (must use the stream pipeline).
        assertNull(BdatTableProvider.mmapCandidate(URI.create("http://host/x.sas7bdat")));
        assertNull(BdatTableProvider.mmapCandidate(URI.create("jar:file:/a.jar!/x.sas7bdat")));

        // A file: URI to a non-existent path is not a candidate.
        assertNull(BdatTableProvider.mmapCandidate(aDir.resolve("missing.sas7bdat").toUri()));

        // Disabling mmap forces the stream pipeline even for a local file.
        String old = System.getProperty("bdat.mmap");
        try
        {
            System.setProperty("bdat.mmap", "false");
            assertNull(BdatTableProvider.mmapCandidate(file.toUri()));
        }
        finally
        {
            setOrClear("bdat.mmap", old);
        }
    }


    @Test
    void pageStartByteUses64BitArithmetic()
    {
        // index * pageSize overflows int but must be correct in long arithmetic (> 2 GB files).
        long headerSize = 65_536L;
        int pageSize = 1 << 20; // 1 MiB
        long index = 5_000L; // 5000 * 1 MiB ≈ 5 GB, well past Integer.MAX_VALUE
        long expected = headerSize + index * pageSize;
        assertEquals(expected, BdatParallelLoader.pageStartByte(headerSize, pageSize, index));
        assertEquals(headerSize, BdatParallelLoader.pageStartByte(headerSize, pageSize, 0));
    }


    /**
     * Full-scale parity on the real local reference file {@code /data/lb.sas7bdat} (a ~16&nbsp;GB
     * {@code COMPRESS=CHAR} dataset, &gt; 4M rows). Loads it both ways and compares every cell so a
     * row swap, off-by-one, or page-reorder bug would be caught at scale. Disabled by default
     * (needs the local file + minutes of runtime); enable with {@code -Dbdat.parity.lb=true}.
     */
    @Test
    @EnabledIfSystemProperty(named = "bdat.parity.lb", matches = "true")
    void parallelMatchesSequentialForLbReference()
    {
        Path lb = Path.of("/data/lb.sas7bdat");
        assumeTrue(Files.exists(lb), "/data/lb.sas7bdat not present — skipping");
        URI uri = lb.toUri();

        Outcome sequential = load(uri, "false", "false", null);
        Outcome mmap = load(uri, "true", "true", null);

        assertNull(sequential.error(), "sequential load failed: " + sequential.error());
        assertNull(mmap.error(), "mmap load failed: " + mmap.error());
        assertTablesEqual(sequential.table(), mmap.table(), "lb.sas7bdat (mmap)");
    }


    @Test
    void abortFromProgressSinkPropagatesAsIoException() throws IOException
    {
        LoaderFixture fixture = new LoaderFixture("mix_data_misc_14x12288");
        BdatParallelLoader loader = new BdatParallelLoader(fixture.dataset, fixture.stream(),
                fixture.parsers, fixture.columns(), fixture.types, _ ->
                {
                    throw new IOException("User aborted!");
                }, 4, 12);

        IOException ex = assertThrows(IOException.class, loader::load);
        assertEquals("User aborted!", ex.getMessage());
    }


    @Test
    void workerFailurePropagatesAsIoException() throws IOException
    {
        LoaderFixture fixture = new LoaderFixture("doubles_12x25");
        // A worker that cannot decode its page must surface as an IOException with the pool shut
        // down. This used to pass an empty type array to make the worker index out of bounds, but
        // F-prov-17 turned a parsers/columns/types length disagreement into a loud constructor
        // check (it used to SILENTLY DROP the surplus columns), so the failure is injected through
        // the page source instead — which is also closer to the real thing.
        RuntimeException boom = new IllegalStateException("page 0 is undecodable");
        Iterator<BdatParallelLoader.PageSupplier> failing = List.<BdatParallelLoader.PageSupplier> of(
                () ->
                {
                    throw boom;
                }).iterator();
        BdatParallelLoader loader = new BdatParallelLoader(fixture.dataset, failing,
                fixture.parsers, fixture.columns(), fixture.types, _ ->
                {
                }, 4, 12);

        IOException ex = assertThrows(IOException.class, loader::load);
        assertSame(boom, ex.getCause());
    }


    @Test
    void producerErrorSurfacesAsItsOwnCauseNotAsATruncatedFile() throws IOException
    {
        // An Error on the producer (OutOfMemoryError is realistic here: a page buffer per page
        // plus every in-flight chunk array) used to escape the RuntimeException-only catch. The
        // finally still queued the sentinel, so load() returned a SHORT result and the caller's
        // row-count assertion reported "Can't read all rows" — telling the user their file was
        // truncated when the JVM had actually run out of memory.
        LoaderFixture fixture = new LoaderFixture("doubles_12x25");
        OutOfMemoryError boom = new OutOfMemoryError("simulated heap exhaustion");
        Iterator<BdatParallelLoader.PageSupplier> exploding = new Iterator<>()
        {

            @Override
            public boolean hasNext()
            {
                return true;
            }


            @Override
            public BdatParallelLoader.PageSupplier next()
            {
                throw boom;
            }
        };

        BdatParallelLoader loader = new BdatParallelLoader(fixture.dataset, exploding,
                fixture.parsers, fixture.columns(), fixture.types, _ ->
                {
                }, 2, 4);

        IOException ex = assertThrows(IOException.class, loader::load);
        assertSame(boom, ex.getCause(), "the real cause must reach the caller");
    }


    @Test
    void cellsThatContradictTheirColumnTypeAreCountedNotSilentlyCoerced() throws IOException
    {
        // The parallel path resolves a cell by its COLUMN type while the sequential
        // addData2Column resolves it by the VALUE, so the two agree only while the declared type
        // and the parsed value agree. Nothing enforces that, and it used to fail silently in both
        // directions. Declaring every column STRING against a numeric fixture forces the
        // disagreement: each cell is stored as "" and counted.
        LoaderFixture fixture = new LoaderFixture("doubles_12x25");
        DataValueType[] allStrings = new DataValueType[fixture.types.length];
        Arrays.fill(allStrings, DataValueType.STRING);
        CachedDataTableColumn[] columns = new CachedDataTableColumn[allStrings.length];
        for (int i = 0; i < columns.length; i++)
        {
            columns[i] = new CachedDataTableColumn(i, DataValueType.STRING);
        }

        BdatParallelLoader loader = new BdatParallelLoader(fixture.dataset, fixture.stream(),
                fixture.parsers, columns, allStrings, _ ->
                {
                }, 4, 12);

        // Only the genuinely numeric columns contradict a STRING declaration; any CHARACTER column
        // in the fixture still yields a String and agrees.
        long numericColumns = Arrays.stream(fixture.types).filter(t -> t != DataValueType.STRING)
                .count();
        assumeTrue(numericColumns > 0, "fixture has no numeric column to contradict");

        BdatParallelLoader.LoadResult result = loader.load();
        assertEquals(result.rowCount() * numericColumns, result.typeMismatchCount(),
                "every numeric cell read into a STRING column must be counted");
    }


    @Test
    void agreeingTypesReportNoMismatches() throws IOException
    {
        // Guards the counter against false positives on the normal path.
        LoaderFixture fixture = new LoaderFixture("mix_data_misc_14x12288");
        BdatParallelLoader loader = new BdatParallelLoader(fixture.dataset, fixture.stream(),
                fixture.parsers, fixture.columns(), fixture.types, _ ->
                {
                }, 4, 12);

        assertEquals(0, loader.load().typeMismatchCount());
    }


    @Test
    void drainWarningsAreRateLimited()
    {
        // The drain polls once a second; without a threshold it would log a warning every poll and
        // bury the log it exists to make readable.
        try (ExecutorService pool = Executors.newFixedThreadPool(1))
        {
            long justStarted = System.nanoTime();
            assertEquals(10L,
                    BdatParallelLoader.warnIfStillDraining(justStarted, 10L, "worker pool", pool),
                    "below the threshold the threshold must not move");

            long longAgo = System.nanoTime() - TimeUnit.SECONDS.toNanos(25);
            long next = BdatParallelLoader.warnIfStillDraining(longAgo, 10L, "worker pool", pool);
            assertTrue(next >= 35L,
                    "after warning at 25s the next threshold must be ~35s, got " + next);
        }
    }


    private static void assertTablesEqual(IDataTable aExpected, IDataTable aActual, String aCtx)
    {
        assertEquals(aExpected.getRowCount(), aActual.getRowCount(), "rowCount " + aCtx);
        assertEquals(aExpected.getColumnCount(), aActual.getColumnCount(), "colCount " + aCtx);

        DataTableMeta expectedMeta = aExpected.getMetaData();
        DataTableMeta actualMeta = aActual.getMetaData();
        for (int c = 0; c < aExpected.getColumnCount(); c++)
        {
            DataTableColumnMeta ec = expectedMeta.getColumn(c);
            DataTableColumnMeta ac = actualMeta.getColumn(c);
            assertEquals(ec.getName(), ac.getName(), "column name " + c + " " + aCtx);
            assertEquals(ec.getType(), ac.getType(), "column type " + c + " " + aCtx);
            assertEquals(ec.getLength(), ac.getLength(), "column length " + c + " " + aCtx);
        }

        long rows = aExpected.getRowCount();
        int cols = aExpected.getColumnCount();
        for (long r = 0; r < rows; r++)
        {
            for (int c = 0; c < cols; c++)
            {
                Object ev = aExpected.getValue(r, c);
                Object av = aActual.getValue(r, c);
                if (!Objects.equals(ev, av))
                {
                    assertEquals(ev, av, "cell [" + r + "," + c + "] " + aCtx);
                }
            }
        }
    }

    /** Helper that builds the loader inputs (parsers, columns, types) directly from a fixture. */
    private static final class LoaderFixture
    {

        private final byte[] bytes;

        private final DatasetBdat dataset;

        private final List<BdatVarParser> parsers = new ArrayList<>();

        private final DataValueType[] types;

        LoaderFixture(String aName) throws IOException
        {
            this.bytes = fixtureBytes(aName);
            this.dataset = new ParserBdat().parseDataset(new ByteArrayInputStream(bytes));
            List<VariableBdat> vars = dataset.getVariables();
            this.types = new DataValueType[vars.size()];
            Charset charset = StandardCharsets.ISO_8859_1;
            ByteOrder byteOrder = dataset.getByteOrder().getByteOrder();
            for (int i = 0; i < vars.size(); i++)
            {
                VariableBdat v = vars.get(i);
                parsers.add(new BdatVarParser(v, charset, byteOrder));
                types[i] = v.getType() == VariableType.NUMERIC ? DataValueType.DOUBLE
                        : DataValueType.STRING;
            }
        }


        PositionAwareInputStream stream()
        {
            return new PositionAwareInputStream(new ByteArrayInputStream(bytes));
        }


        CachedDataTableColumn[] columns()
        {
            CachedDataTableColumn[] cols = new CachedDataTableColumn[types.length];
            for (int i = 0; i < cols.length; i++)
            {
                cols[i] = new CachedDataTableColumn(i, types[i]);
            }
            return cols;
        }
    }
}
