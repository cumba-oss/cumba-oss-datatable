package net.cumba.datatable.provider.sas.sas7bdat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.cumba.datatable.impl.CachedDataTableColumn;
import net.cumba.datatable.provider.sas.testsupport.LoggerCapture;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.MissingValue;
import net.cumba.sasutils.PositionAwareInputStream;
import net.cumba.sasutils.VariableType;
import net.cumba.sasutils.bdat.DatasetBdat;
import net.cumba.sasutils.bdat.Page;
import net.cumba.sasutils.bdat.ParserBdat;
import net.cumba.sasutils.bdat.VariableBdat;
import org.junit.jupiter.api.Test;

/**
 * Internals of {@link BdatParallelLoader} that the parity tests in {@link BdatParallelLoaderTest}
 * cannot reach: the degenerate-argument guards, the thread roles, the page-source iterator's
 * bounds, the deleted/type-mismatch accounting and the drain diagnostics.
 *
 * <p>
 * Everything here is pinned with latches, injected iterators and direct calls — never with a sleep
 * or a timing assumption. Where a behaviour genuinely can only be observed by winning a race it is
 * left to the residual classification in the module pom instead of being pinned by a flaky test.
 * </p>
 */
class BdatParallelLoaderInternalsTest
{

    private static final String LOGGER_NAME = BdatParallelLoader.class.getName();

    // ==================== defaultParallelism / defaultMaxInFlight ====================

    @Test
    void defaultParallelismLeavesOneCoreForTheConsumer()
    {
        String old = System.getProperty("bdat.parallelism");
        try
        {
            System.clearProperty("bdat.parallelism");
            int expected = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
            assertEquals(expected, BdatParallelLoader.defaultParallelism(),
                    "the consumer/assembler thread must keep a core of its own");
        }
        finally
        {
            restore("bdat.parallelism", old);
        }
    }


    @Test
    void defaultParallelismRejectsNonPositiveOverrides()
    {
        String old = System.getProperty("bdat.parallelism");
        int fallback = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        try
        {
            // 0 and -1 must NOT be honoured: newFixedThreadPool(0) throws and a negative one is
            // meaningless, so the property falls back instead of failing the load.
            System.setProperty("bdat.parallelism", "0");
            assertEquals(fallback, BdatParallelLoader.defaultParallelism());
            System.setProperty("bdat.parallelism", "-1");
            assertEquals(fallback, BdatParallelLoader.defaultParallelism());
            // 1 is the smallest legal override and must be honoured verbatim.
            System.setProperty("bdat.parallelism", "1");
            assertEquals(1, BdatParallelLoader.defaultParallelism());
        }
        finally
        {
            restore("bdat.parallelism", old);
        }
    }


    @Test
    void defaultParallelismLogsAndIgnoresAnUnparseableOverride()
    {
        String old = System.getProperty("bdat.parallelism");
        try (LoggerCapture log = LoggerCapture.attach(LOGGER_NAME))
        {
            System.setProperty("bdat.parallelism", "plenty");
            assertEquals(Math.max(1, Runtime.getRuntime().availableProcessors() - 1),
                    BdatParallelLoader.defaultParallelism());
            assertTrue(log.containsMessageContaining("Ignoring invalid bdat.parallelism value"),
                    "an unusable override must say so; silently using a different parallelism is "
                            + "how a misspelled property goes unnoticed for ever");
        }
        finally
        {
            restore("bdat.parallelism", old);
        }
    }


    @Test
    void defaultMaxInFlightScalesWithParallelismAndCannotOverflow()
    {
        assertEquals(12, BdatParallelLoader.defaultMaxInFlight(4), "3 pages per worker");
        assertEquals(2, BdatParallelLoader.defaultMaxInFlight(0), "floor of 2");
        // F-prov-17: 3 * 1_500_000_000 overflows int to a POSITIVE 205_032_704, which Math.max
        // cannot catch — the old code would have sized an ArrayBlockingQueue with it.
        assertEquals(BdatParallelLoader.MAX_IN_FLIGHT,
                BdatParallelLoader.defaultMaxInFlight(1_500_000_000));
        assertEquals(BdatParallelLoader.MAX_IN_FLIGHT,
                BdatParallelLoader.defaultMaxInFlight(Integer.MAX_VALUE));
    }

    // ==================== degenerate constructor arguments (F-prov-17) ====================


    @Test
    void aZeroInFlightCapIsRejectedRatherThanDeadlocking() throws IOException
    {
        LoaderFixture f = new LoaderFixture("doubles_12x25");
        // maxInFlight == 0 used to be an unconditional SILENT hang: a permit-less semaphore stops
        // the producer before it enqueues anything and the consumer waits on an empty queue for
        // ever, so not even the 10s drain warnings are reached.
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new BdatParallelLoader(f.dataset, f.stream(), f.parsers, f.columns(), f.types,
                        _ ->
                        {
                        }, 1, 0));
        assertTrue(ex.getMessage().contains("maxInFlight"), ex.getMessage());
    }


    @Test
    void nonPositiveParallelismIsRejected() throws IOException
    {
        LoaderFixture f = new LoaderFixture("doubles_12x25");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new BdatParallelLoader(f.dataset, f.stream(), f.parsers, f.columns(), f.types,
                        _ ->
                        {
                        }, 0, 4));
        assertTrue(ex.getMessage().contains("parallelism"), ex.getMessage());
    }


    @Test
    void aColumnArrayShorterThanTheParsersIsRejectedRatherThanSilentlyDroppingColumns()
        throws IOException
    {
        // extractAndParse sizes the chunks by parsers.length while appendChunks walks
        // columns.length, so a short columns array used to drop the surplus columns with no
        // exception and no log — a table with fewer columns than the file has variables.
        LoaderFixture f = new LoaderFixture("doubles_12x25");
        CachedDataTableColumn[] tooFew = Arrays.copyOf(f.columns(), f.types.length - 1);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new BdatParallelLoader(f.dataset, f.stream(), f.parsers, tooFew, f.types, _ ->
                {
                }, 2, 4));
        assertTrue(ex.getMessage().contains("same columns"), ex.getMessage());
    }


    @Test
    void anInFlightCapOfOneStillLoadsEveryRow() throws IOException
    {
        // The queue is sized maxInFlight + 1 so the sentinel always has a slot; with the off-by-one
        // wrong, maxInFlight == 1 means ArrayBlockingQueue(0), which cannot even be constructed.
        LoaderFixture f = new LoaderFixture("mix_data_misc_14x12288");
        BdatParallelLoader loader = new BdatParallelLoader(f.dataset, f.stream(), f.parsers,
                f.columns(), f.types, _ ->
                {
                }, 1, 1);
        assertEquals(12288, loader.load().rowCount());
    }

    // ==================== thread roles ====================


    @Test
    void pagesAreReadOnADaemonProducerAndParsedOnDaemonWorkers() throws IOException
    {
        // Both the producer thread and the pool's workers must be daemons: a non-daemon loader
        // thread left behind by a failed load keeps the whole JVM (the Swing app) alive.
        LoaderFixture f = new LoaderFixture("doubles_12x25");
        List<String> producerRoles = new CopyOnWriteArrayList<>();
        List<String> workerRoles = new CopyOnWriteArrayList<>();

        Iterator<BdatParallelLoader.PageSupplier> source = BdatParallelLoader.mmapSource(f.dataset,
                MemorySegment.ofArray(f.bytes));
        Iterator<BdatParallelLoader.PageSupplier> observed = new Iterator<>()
        {

            @Override
            public boolean hasNext()
            {
                return source.hasNext();
            }


            @Override
            public BdatParallelLoader.PageSupplier next()
            {
                producerRoles.add(role());
                BdatParallelLoader.PageSupplier delegate = source.next();
                return () ->
                {
                    workerRoles.add(role());
                    return delegate.get();
                };
            }
        };

        BdatParallelLoader loader = new BdatParallelLoader(f.dataset, observed, f.parsers,
                f.columns(), f.types, _ ->
                {
                }, 2, 4);
        assertEquals(25, loader.load().rowCount());

        assertFalse(producerRoles.isEmpty(), "no page was ever requested");
        assertFalse(workerRoles.isEmpty(), "no page was ever parsed");
        assertTrue(producerRoles.stream().allMatch(r -> r.equals("bdat-loader-producer/daemon")),
                "pages must be requested on the daemon producer thread, was " + producerRoles);
        assertTrue(workerRoles.stream().allMatch(r -> r.endsWith("/daemon")),
                "pages must be parsed on daemon pool threads, was " + workerRoles);
        assertTrue(workerRoles.stream().noneMatch(r -> r.startsWith("bdat-loader-producer/")),
                "the mmap source must decode IN THE WORKER, not on the producer: " + workerRoles);
    }


    private static String role()
    {
        Thread t = Thread.currentThread();
        return t.getName() + (t.isDaemon() ? "/daemon" : "/user");
    }

    // ==================== in-flight bound ====================


    @Test
    void theProducerCannotRunMoreThanTheCapAheadOfTheConsumer()
        throws IOException, InterruptedException
    {
        // Each in-flight slot holds a whole PARSED page (a String per character cell, a double per
        // numeric cell), so the semaphore is what bounds live heap on this path. Without it the
        // only remaining bound is the queue's own capacity, which is one slot larger.
        //
        // Deterministic by construction, with no sleep standing in for a result: the first page's
        // supplier blocks, so the consumer is stuck in Future.get() and NOTHING is ever released
        // while the probe runs. The correct loader therefore CANNOT reach cap + 2 page requests —
        // the await below always runs to its timeout and reports false, it cannot fail by luck.
        // A loader that never acquired a permit fills the queue (capacity cap + 1) instead and
        // reaches cap + 2 within microseconds.
        LoaderFixture f = new LoaderFixture("mix_data_misc_14x12288");
        int cap = 3;
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch ranAhead = new CountDownLatch(cap + 2);
        AtomicInteger requested = new AtomicInteger();

        Iterator<BdatParallelLoader.PageSupplier> source = BdatParallelLoader.mmapSource(f.dataset,
                MemorySegment.ofArray(f.bytes));
        Iterator<BdatParallelLoader.PageSupplier> gated = new Iterator<>()
        {

            @Override
            public boolean hasNext()
            {
                return source.hasNext();
            }


            @Override
            public BdatParallelLoader.PageSupplier next()
            {
                BdatParallelLoader.PageSupplier delegate = source.next();
                boolean first = requested.getAndIncrement() == 0;
                ranAhead.countDown();
                if (!first)
                {
                    return delegate;
                }
                return () ->
                {
                    try
                    {
                        assertTrue(release.await(30, TimeUnit.SECONDS), "probe never released");
                    }
                    catch (InterruptedException e)
                    {
                        Thread.currentThread().interrupt();
                        throw new IOException("interrupted", e);
                    }
                    return delegate.get();
                };
            }
        };

        BdatParallelLoader loader = new BdatParallelLoader(f.dataset, gated, f.parsers, f.columns(),
                f.types, _ ->
                {
                }, 2, cap);

        Thread loading = new Thread(() ->
        {
            try
            {
                assertEquals(12288, loader.load().rowCount());
            }
            catch (IOException e)
            {
                throw new AssertionError(e);
            }
        }, "probe-load");
        loading.setDaemon(true);
        loading.start();
        try
        {
            assertFalse(ranAhead.await(500, TimeUnit.MILLISECONDS),
                    "with nothing released the producer must not get " + (cap + 2)
                            + " pages ahead; it requested " + requested.get());
            assertTrue(requested.get() >= 2, "the pipeline never ran ahead at all — test vacuous");
        }
        finally
        {
            release.countDown();
        }
        loading.join(60_000);
        assertFalse(loading.isAlive(), "the load must complete once the probe is released");
    }

    // ==================== mmapSource bounds ====================


    @Test
    void mmapSourceYieldsEveryPageExactlyOnceAndThenStops() throws IOException
    {
        LoaderFixture f = new LoaderFixture("doubles_12x25");
        Iterator<BdatParallelLoader.PageSupplier> it = BdatParallelLoader.mmapSource(f.dataset,
                MemorySegment.ofArray(f.bytes));

        int count = 0;
        while (it.hasNext())
        {
            BdatParallelLoader.PageSupplier supplier = it.next();
            Page page = supplier.get();
            assertNotNull(page, "a page inside the mapped file must not be skipped (page " + count
                    + "); the last page ends exactly AT the mapped size, which is in bounds");
            count++;
        }
        assertTrue(count > 0, "no page supplied at all");
        // hasNext() must become false rather than walking past the pre-parsed list.
        assertThrows(NoSuchElementException.class, it::next);
    }


    @Test
    void mmapSourceSkipsAPageBeyondTheMappedFileRatherThanThrowing() throws IOException
    {
        // A truncated or corrupt header declares more pages than the file holds. Slicing past the
        // segment would throw IndexOutOfBoundsException out of a worker; the loader skips the page
        // and says so, and the row-count reconciliation then reports the real shortfall.
        LoaderFixture f = new LoaderFixture("doubles_12x25");
        byte[] truncated = Arrays.copyOf(f.bytes, f.bytes.length - 1);

        List<Page> supplied = new ArrayList<>();
        try (LoggerCapture log = LoggerCapture.attach(LOGGER_NAME))
        {
            Iterator<BdatParallelLoader.PageSupplier> it = BdatParallelLoader.mmapSource(f.dataset,
                    MemorySegment.ofArray(truncated));
            while (it.hasNext())
            {
                supplied.add(it.next().get());
            }
            assertTrue(supplied.contains(null),
                    "the page that ends one byte past the mapped size must be skipped");
            assertTrue(log.containsMessageContaining("beyond mapped size"),
                    "a skipped page must be logged; silently dropping rows is the failure this "
                            + "guard exists to avoid");
        }
    }

    // ==================== deleted-row and type-mismatch accounting ====================


    @Test
    void deletedRowsAreCountedOnTheParallelPath() throws IOException
    {
        // The page result's deleted count has to reach the caller: BdatTableProvider reconciles it
        // against the header's DECLARED count, so a dropped count reads as "can't read all rows".
        LoaderFixture f = new LoaderFixture("data_page_with_deleted_3x997");
        BdatParallelLoader loader = new BdatParallelLoader(f.dataset, f.stream(), f.parsers,
                f.columns(), f.types, _ ->
                {
                }, 2, 4);

        BdatParallelLoader.LoadResult result = loader.load();
        assertEquals(1, result.deletedCount(),
                "the fixture's header declares exactly 1 deleted row");
        assertEquals(997, result.rowCount());
        assertEquals(0, result.typeMismatchCount());
    }


    @Test
    void aNumericCellInAStringColumnIsCountedAndTheFirstColumnReported() throws IOException
    {
        // CHARACTER-column arm of the mismatch count. Declaring an all-numeric fixture's columns
        // STRING makes every cell contradict its column; the WARNING must name the FIRST column.
        LoaderFixture f = new LoaderFixture("doubles_12x25");
        DataValueType[] allStrings = new DataValueType[f.types.length];
        Arrays.fill(allStrings, DataValueType.STRING);

        try (LoggerCapture log = LoggerCapture.attach(LOGGER_NAME))
        {
            BdatParallelLoader.LoadResult result = load(f, allStrings);
            assertEquals(25L * f.types.length, result.typeMismatchCount());
            assertTrue(reportedMismatch(log, 25L * f.types.length, 0),
                    "the report must name column 0 — the first disagreement in file order, not "
                            + "the last one seen: " + parameters(log));
        }
    }


    @Test
    void aStringCellInANumericColumnIsCountedAndTheFirstColumnReported() throws IOException
    {
        // NUMERIC-column arm — the mirror image, and the one with no coverage at all before this
        // test. All three variables of the fixture are CHARACTER, so declaring them DOUBLE makes
        // every cell contradict its column and stores MIS_UNKNOWN rather than an ordinary '.'.
        LoaderFixture f = new LoaderFixture("data_page_with_deleted_3x997");
        DataValueType[] allDoubles = new DataValueType[f.types.length];
        Arrays.fill(allDoubles, DataValueType.DOUBLE);

        try (LoggerCapture log = LoggerCapture.attach(LOGGER_NAME))
        {
            BdatParallelLoader.LoadResult result = load(f, allDoubles);
            assertEquals(997L * f.types.length, result.typeMismatchCount());
            assertTrue(reportedMismatch(log, 997L * f.types.length, 0), parameters(log).toString());
        }
    }


    @Test
    void theReportedFirstColumnIsTheOneThatDisagreedNotSimplyZero() throws IOException
    {
        // All three variables of the fixture are CHARACTER. Declaring only the SECOND and THIRD
        // numeric leaves column 0 in agreement, so the first affected index is 1 — a report that
        // always said "column 0" would look right in every test where the first column happens to
        // be the broken one, and send the reader to the wrong variable in every other.
        LoaderFixture f = new LoaderFixture("data_page_with_deleted_3x997");
        DataValueType[] types = new DataValueType[f.types.length];
        Arrays.fill(types, DataValueType.DOUBLE);
        types[0] = DataValueType.STRING;

        try (LoggerCapture log = LoggerCapture.attach(LOGGER_NAME))
        {
            BdatParallelLoader.LoadResult result = load(f, types);
            assertEquals(997L * (f.types.length - 1), result.typeMismatchCount());
            assertTrue(reportedMismatch(log, 997L * (f.types.length - 1), 1),
                    parameters(log).toString());
        }
    }


    @Test
    void agreeingTypesAreNotReported() throws IOException
    {
        // Guards the report against firing on the normal path: a WARNING on every load would
        // train the reader to ignore it.
        LoaderFixture f = new LoaderFixture("doubles_12x25");
        try (LoggerCapture log = LoggerCapture.attach(LOGGER_NAME))
        {
            assertEquals(0, load(f, f.types).typeMismatchCount());
            assertFalse(log.containsMessageContaining("did not match their declared column type"),
                    "nothing disagreed, so nothing may be reported: " + log.messages());
        }
    }


    /** Whether the mismatch WARNING fired with exactly this count and first-column index. */
    private static boolean reportedMismatch(LoggerCapture aLog, long aCount, int aColumn)
    {
        return aLog.records().stream()
                .filter(r -> r.getMessage() != null
                        && r.getMessage().contains("did not match their declared column type"))
                .anyMatch(r -> r.getParameters() != null && r.getParameters().length == 2
                        && Objects.equals(r.getParameters()[0], aCount)
                        && Objects.equals(r.getParameters()[1], aColumn));
    }


    private static List<String> parameters(LoggerCapture aLog)
    {
        return aLog.records().stream().map(r -> Arrays.toString(r.getParameters())).toList();
    }


    private static BdatParallelLoader.LoadResult load(LoaderFixture aFixture,
            DataValueType[] aTypes)
        throws IOException
    {
        CachedDataTableColumn[] columns = new CachedDataTableColumn[aTypes.length];
        for (int i = 0; i < columns.length; i++)
        {
            columns[i] = new CachedDataTableColumn(i, aTypes[i]);
        }
        return new BdatParallelLoader(aFixture.dataset, aFixture.stream(), aFixture.parsers,
                columns, aTypes, _ ->
                {
                }, 4, 12).load();
    }

    // ==================== NaN decoding in the assembler ====================


    /**
     * ⭐ The assembler decodes a NaN double with {@code MissingValue.forNaN}, as the sequential
     * {@code addData2Column} does (PLAN-bare-nan-is-mis, owner rulings E5 and N1): a bare NaN of
     * either sign is {@code MIS}, a coded payload keeps its marker, an unrecognised payload is
     * {@code MIS_UNKNOWN}. ⚑ Inert for a real file &mdash; every double reaching the assembler
     * comes from {@code BdatVarParser.parseDouble}, which encodes every NaN as a payload, or from
     * the defensive arm that stores {@code MIS_UNKNOWN}'s &mdash; so it is pinned through injected
     * parsers. ⚠ Sensitivity: reverting the arm to {@code forValue(d, MIS_UNKNOWN)} reds the two
     * bare rows.
     */
    @Test
    void theAssemblerDecodesANaNWithForNaN() throws IOException
    {
        LoaderFixture f = new LoaderFixture("doubles_12x25");
        double[] injected =
        {
                Double.longBitsToDouble(0x7FF8_0000_0000_0000L),
                Double.longBitsToDouble(0xFFF8_0000_0000_0000L),
                Double.longBitsToDouble(0xFFFF_0000_0000_0000L), MissingValue.MIS_A.asDouble()
        };
        MissingValue[] expected =
        {
                MissingValue.MIS, MissingValue.MIS, MissingValue.MIS_UNKNOWN, MissingValue.MIS_A
        };

        List<BdatVarParser> parsers = new ArrayList<>(f.parsers);
        List<Integer> targets = new ArrayList<>();
        for (int c = 0; c < f.types.length && targets.size() < injected.length; c++)
        {
            if (f.types[c] == DataValueType.DOUBLE)
            {
                targets.add(c);
            }
        }
        assertEquals(injected.length, targets.size(), "the fixture needs four numeric columns");
        ByteOrder byteOrder = f.dataset.getByteOrder().getByteOrder();
        for (int k = 0; k < injected.length; k++)
        {
            int c = targets.get(k);
            Double cell = injected[k];
            parsers.set(c, new BdatVarParser(f.dataset.getVariables().get(c),
                    StandardCharsets.ISO_8859_1, byteOrder)
            {

                @Override
                public Object getValue(byte[] aBuffer)
                {
                    return cell;
                }
            });
        }

        CachedDataTableColumn[] columns = f.columns();
        BdatParallelLoader loader = new BdatParallelLoader(f.dataset, f.stream(), parsers, columns,
                f.types, _ ->
                {
                }, 2, 4);
        long rows = loader.load().rowCount();
        assertTrue(rows > 0, "the fixture has rows");
        for (int k = 0; k < injected.length; k++)
        {
            CachedDataTableColumn col = columns[targets.get(k)];
            for (long r = 0; r < rows; r++)
            {
                assertSame(expected[k], col.getValue(r), "column " + targets.get(k) + " row " + r);
            }
        }
    }

    // ==================== failure propagation ====================


    @Test
    void anIoExceptionFromAPageSupplierReachesTheCallerUnwrapped() throws IOException
    {
        // asIoException must hand back the SAME IOException, not a wrapper: the message the user
        // sees is the reader's own ("Truncated…", "Unsupported compression…"), not "java.io…".
        LoaderFixture f = new LoaderFixture("doubles_12x25");
        IOException boom = new IOException("page 3 is unreadable");
        BdatParallelLoader loader = new BdatParallelLoader(f.dataset, singleSupplier(() ->
        {
            throw boom;
        }), f.parsers, f.columns(), f.types, _ ->
        {
        }, 2, 4);

        IOException ex = assertThrows(IOException.class, loader::load);
        assertSame(boom, ex, "the reader's own IOException must not be re-wrapped");
    }


    @Test
    void aRuntimeFailureInAWorkerKeepsItsOriginalCause() throws IOException
    {
        LoaderFixture f = new LoaderFixture("doubles_12x25");
        IllegalStateException boom = new IllegalStateException("malformed subheader pointer");
        BdatParallelLoader loader = new BdatParallelLoader(f.dataset, singleSupplier(() ->
        {
            throw boom;
        }), f.parsers, f.columns(), f.types, _ ->
        {
        }, 2, 4);

        IOException ex = assertThrows(IOException.class, loader::load);
        assertSame(boom, ex.getCause(),
                "the worker's own cause must reach the caller, not the ExecutionException wrapper");
    }


    @Test
    void anAlreadyInterruptedCallerFailsFastAndKeepsTheInterruptFlag() throws IOException
    {
        // The consumer blocks in queue.take(), which throws at once when the calling thread is
        // already interrupted. Swallowing the flag there would leave a cancelled load looking
        // like a completed one to the caller's own interrupt handling.
        LoaderFixture f = new LoaderFixture("mix_data_misc_14x12288");
        BdatParallelLoader loader = new BdatParallelLoader(f.dataset, f.stream(), f.parsers,
                f.columns(), f.types, _ ->
                {
                }, 2, 4);

        Thread.currentThread().interrupt();
        try
        {
            IOException ex = assertThrows(IOException.class, loader::load);
            assertEquals("Interrupted while loading BDAT data", ex.getMessage());
            assertTrue(Thread.currentThread().isInterrupted(),
                    "the interrupt status must survive the load");
        }
        finally
        {
            Thread.interrupted();
        }
    }


    private static Iterator<BdatParallelLoader.PageSupplier> singleSupplier(
            BdatParallelLoader.PageSupplier aSupplier)
    {
        return List.of(aSupplier).iterator();
    }

    // ==================== drain diagnostics ====================


    @Test
    void theDrainWarnsAsSoonAsTheThresholdIsReached()
    {
        // The threshold is "has the drain outlasted N seconds", so exactly N must warn — a load
        // wedged on a malformed page is the case this exists for, and it must not need N+1.
        try (ExecutorService pool = Executors.newFixedThreadPool(1);
                LoggerCapture log = LoggerCapture.attach(LOGGER_NAME))
        {
            long tenSecondsAgo = System.nanoTime() - TimeUnit.SECONDS.toNanos(10);
            long next = BdatParallelLoader.warnIfStillDraining(tenSecondsAgo, 10L, "worker pool",
                    pool);
            assertTrue(log.containsMessageContaining("Still waiting for the SAS7BDAT"),
                    "reaching the threshold exactly must warn: " + log.messages());
            assertEquals(20L, next, "the next threshold is 10s after this one");
        }
    }


    @Test
    void theDrainReportsALiveTaskCountOnlyWhenThePoolCanSupplyOne()
    {
        try (ExecutorService pool = Executors.newFixedThreadPool(1))
        {
            long longAgo = System.nanoTime() - TimeUnit.SECONDS.toNanos(25);
            try (LoggerCapture log = LoggerCapture.attach(LOGGER_NAME))
            {
                BdatParallelLoader.warnIfStillDraining(longAgo, 10L, "worker pool", pool);
                assertTrue(
                        log.records().stream()
                                .anyMatch(r -> Arrays.toString(r.getParameters())
                                        .contains("task(s) still running")),
                        "a ThreadPoolExecutor can report its live tasks, so the message must: "
                                + log.records().stream()
                                        .map(r -> Arrays.toString(r.getParameters())).toList());
            }
            // The producer-thread wait passes no pool: the pool has already terminated by then, so
            // appending "0 task(s) still running" would assert something about the wrong thing.
            try (LoggerCapture log = LoggerCapture.attach(LOGGER_NAME))
            {
                BdatParallelLoader.warnIfStillDraining(longAgo, 10L, "producer thread", null);
                assertTrue(
                        log.records().stream()
                                .anyMatch(r -> Arrays.toString(r.getParameters())
                                        .contains("producer thread")),
                        "the warning must still fire");
                assertTrue(
                        log.records().stream()
                                .noneMatch(r -> Arrays.toString(r.getParameters())
                                        .contains("task(s) still running")),
                        "with no pool to consult there is no task count to report");
            }
        }
    }


    @Test
    void theDrainStaysQuietBelowTheThreshold()
    {
        try (ExecutorService pool = Executors.newFixedThreadPool(1);
                LoggerCapture log = LoggerCapture.attach(LOGGER_NAME))
        {
            long justStarted = System.nanoTime();
            assertEquals(10L,
                    BdatParallelLoader.warnIfStillDraining(justStarted, 10L, "worker pool", pool),
                    "below the threshold the threshold must not move");
            assertFalse(log.containsMessageContaining("Still waiting"),
                    "a one-second poll must not log once a second");
        }
    }


    private static void restore(String aKey, String aValue)
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

    /** Builds the loader inputs (parsers, columns, types) directly from a committed fixture. */
    private static final class LoaderFixture
    {

        private final byte[] bytes;

        private final DatasetBdat dataset;

        private final List<BdatVarParser> parsers = new ArrayList<>();

        private final DataValueType[] types;

        LoaderFixture(String aName) throws IOException
        {
            try (var in = BdatParallelLoaderInternalsTest.class
                    .getResourceAsStream(aName + ".sas7bdat"))
            {
                assertNotNull(in, "fixture missing: " + aName);
                this.bytes = in.readAllBytes();
            }
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
