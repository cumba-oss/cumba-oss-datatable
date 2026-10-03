package net.cumba.datatable.provider.sas.sas7bdat;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.CustomLog;
import net.cumba.datatable.impl.CachedDataTableColumn;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.MissingValue;
import net.cumba.sasutils.PositionAwareInputStream;
import net.cumba.sasutils.bdat.BdatPageProducer;
import net.cumba.sasutils.bdat.CompressionAlgorithm;
import net.cumba.sasutils.bdat.DatasetBdat;
import net.cumba.sasutils.bdat.Page;
import net.cumba.sasutils.bdat.PageDataExtractor;
import org.jspecify.annotations.Nullable;

/**
 * Parallel page-pipeline loader for SAS7BDAT data: a single producer reads pages in file order, a
 * bounded worker pool decompresses and column-parses each page into per-page {@link ColumnChunk}s,
 * and a single ordered assembler appends the chunks to the (non-thread-safe)
 * {@link CachedDataTableColumn}s strictly in page order.
 *
 * <p>
 * Per-page extraction and page sequencing are delegated to {@link PageDataExtractor} /
 * {@link BdatPageProducer}, so the rows and their order are byte-identical to the sequential
 * {@link net.cumba.sasutils.bdat.ObservationIteratorBdat2}. The per-cell value mapping reproduces
 * exactly the sequential {@code BdatTableProvider.BdatTableDataParser#addData2Column} logic.
 * </p>
 *
 * <p>
 * The loader only <em>fills</em> the column array it is handed; creating the columns and the
 * post-fill finalisation (complete, reconcile lengths, build table, post-checks) remain with
 * {@link BdatTableProvider}, which has access to the inherited {@code AbstractDataTableProvider}
 * helpers.
 * </p>
 */
@CustomLog
public final class BdatParallelLoader
{

    /** Default in-flight pages per worker before the producer blocks. */
    private static final int IN_FLIGHT_FACTOR = 3;

    /**
     * Hard ceiling on the in-flight page cap {@link #defaultMaxInFlight(int)} derives. Reached only
     * from an absurd {@code -Dbdat.parallelism} (above 1365); it keeps the queue allocation and the
     * live parsed-page set finite no matter what the property says.
     */
    static final int MAX_IN_FLIGHT = 4096;

    /** Seconds between "still draining" warnings while waiting for the pipeline to stop. */
    private static final long QUIESCENCE_WARN_INTERVAL_SECONDS = 10;

    private static final Object SENTINEL = new Object();

    private final DatasetBdat dataset;

    private final boolean compressed;

    private final @Nullable CompressionAlgorithm compressionAlgorithm;

    private final Iterator<PageSupplier> pageSuppliers;

    private final BdatVarParser[] parsers;

    private final CachedDataTableColumn[] columns;

    private final DataValueType[] types;

    private final ProgressSink progress;

    private final int parallelism;

    private final int maxInFlight;

    /**
     * Sink for progress + abort. A provider that reports loading progress wires this to its
     * listener and throws {@link IOException} when cancellation is requested; one that does not
     * passes a no-op.
     */
    @FunctionalInterface
    public interface ProgressSink
    {

        void rowsLoaded(long aTotalRows) throws IOException;
    }


    /**
     * Supplies the decoded {@link Page} for one pipeline slot. For the stream source the page is
     * already decoded (the producer read it sequentially) and {@code get()} just returns it; for
     * the mmap source {@code get()} performs the slice copy + decode <em>inside the worker</em>, so
     * the reads run in parallel. May return {@code null} for a skipped (unknown-type) page.
     */
    @FunctionalInterface
    public interface PageSupplier
    {

        @Nullable
        Page get() throws IOException;
    }


    /**
     * The result of a parallel load.
     *
     * @param rowCount
     *            rows appended to the columns.
     * @param deletedCount
     *            deleted rows skipped.
     * @param typeMismatchCount
     *            cells whose parsed value did not match their declared column type and were stored
     *            as that column type's missing value. Normally {@code 0}; anything else means the
     *            table metadata and the variable parsers disagree — see
     *            {@link #warnOnTypeMismatches(long, int)}. Exposed rather than only logged so the
     *            condition is assertable and available to callers.
     */
    public record LoadResult(long rowCount, long deletedCount, long typeMismatchCount)
    {
    }


    /**
     * Per-column, per-page parsed values. Exactly one of {@code doubles}/{@code strings} is set.
     * For numeric columns the {@code doubles} hold the raw value from
     * {@code BdatVarParser.parseDouble}, which is already NaN-encoded for SAS missing values —
     * decoded back to a {@code MissingValue} by the assembler, mirroring the sequential
     * {@code addData2Column}.
     */
    private static final class ColumnChunk
    {

        private final double @Nullable [] doubles;

        private final @Nullable String @Nullable [] strings;

        private ColumnChunk(double @Nullable [] aDoubles, @Nullable String @Nullable [] aStrings)
        {
            this.doubles = aDoubles;
            this.strings = aStrings;
        }
    }


    /**
     * One page's parsed columns plus its deleted-row count and row count. A plain class (not a
     * record) because it carries an array — a transient carrier with no value semantics.
     */
    private static final class PageResult
    {

        private final ColumnChunk[] columns;

        private final long deletedCount;

        private final int rowCount;

        private final long mismatchCount;

        private final int firstMismatchColumn;

        private PageResult(ColumnChunk[] aColumns, long aDeletedCount, int aRowCount,
                long aMismatchCount, int aFirstMismatchColumn)
        {
            this.columns = aColumns;
            this.deletedCount = aDeletedCount;
            this.rowCount = aRowCount;
            this.mismatchCount = aMismatchCount;
            this.firstMismatchColumn = aFirstMismatchColumn;
        }


        ColumnChunk[] columns()
        {
            return columns;
        }


        long deletedCount()
        {
            return deletedCount;
        }


        int rowCount()
        {
            return rowCount;
        }


        long mismatchCount()
        {
            return mismatchCount;
        }


        int firstMismatchColumn()
        {
            return firstMismatchColumn;
        }
    }

    /**
     * Stream-source constructor: pages are produced (read + decoded) sequentially on the producer
     * thread via {@link BdatPageProducer}. Required for non-seekable / remote inputs.
     */
    public BdatParallelLoader(DatasetBdat aDataset, PositionAwareInputStream aStream,
            List<BdatVarParser> aParsers, CachedDataTableColumn[] aColumns, DataValueType[] aTypes,
            ProgressSink aProgress, int aParallelism, int aMaxInFlight)
    {
        this(aDataset, streamSource(aDataset, aStream), aParsers, aColumns, aTypes, aProgress,
                aParallelism, aMaxInFlight);
    }


    /**
     * Page-source constructor: the engine drives an arbitrary {@link PageSupplier} sequence (e.g.
     * the mmap source, whose suppliers decode in the worker for parallel reads).
     */
    public BdatParallelLoader(DatasetBdat aDataset, Iterator<PageSupplier> aPageSuppliers,
            List<BdatVarParser> aParsers, CachedDataTableColumn[] aColumns, DataValueType[] aTypes,
            ProgressSink aProgress, int aParallelism, int aMaxInFlight)
    {
        // F-prov-17: validate the pipeline geometry here, where a violation is one loud
        // IllegalArgumentException, rather than downstream where each one fails differently and
        // one of them not at all. maxInFlight == 0 was an unconditional SILENT DEADLOCK: the
        // producer blocks for ever on a permit-less Semaphore having enqueued nothing and the
        // consumer blocks for ever on an empty queue, so the drain warnings that exist to make a
        // wedged load diagnosable are never even reached. Its neighbours already failed fast
        // (parallelism <= 0 from newFixedThreadPool, maxInFlight == MAX_VALUE from the
        // queue capacity overflowing), so 0 was the one degenerate value that hung.
        // And columns shorter than parsers SILENTLY DROPPED the surplus columns -- a table with
        // fewer columns than the file has variables, no exception and no log -- because
        // extractAndParse sizes the chunks by parsers.length while appendChunks walks
        // columns.length. Neither is reachable from the product's own call sites
        // (defaultMaxInFlight >= 2, defaultParallelism >= 1, and one meta column per variable),
        // but both constructors are public on a public class.
        if (aParallelism < 1)
        {
            throw new IllegalArgumentException("parallelism must be >= 1, was " + aParallelism);
        }
        if (aMaxInFlight < 1)
        {
            throw new IllegalArgumentException("maxInFlight must be >= 1, was " + aMaxInFlight);
        }
        if (aParsers.size() != aColumns.length || aParsers.size() != aTypes.length)
        {
            throw new IllegalArgumentException(
                    "parsers, columns and types must describe the same columns, were %d/%d/%d"
                            .formatted(aParsers.size(), aColumns.length, aTypes.length));
        }
        this.dataset = aDataset;
        // Resolve compression once on the calling thread; workers must not re-derive it off the
        // shared dataset (it walks getPages()/getStringSubHeaders()).
        this.compressed = aDataset.getCompressed();
        this.compressionAlgorithm = aDataset.getCompressionAlgorithm();
        this.pageSuppliers = aPageSuppliers;
        this.parsers = aParsers.toArray(new BdatVarParser[0]);
        this.columns = aColumns;
        this.types = aTypes;
        this.progress = aProgress;
        this.parallelism = aParallelism;
        this.maxInFlight = aMaxInFlight;
    }


    /**
     * Page source for the stream pipeline: each {@link PageSupplier} returns an already-decoded
     * {@link Page} (the underlying {@link BdatPageProducer} reads + decodes sequentially as the
     * producer thread advances the iterator).
     *
     * @param aDataset
     *            the parsed dataset.
     * @param aStream
     *            the row-data stream.
     * @return an iterator of page suppliers in file order.
     */
    public static Iterator<PageSupplier> streamSource(DatasetBdat aDataset,
            PositionAwareInputStream aStream)
    {
        BdatPageProducer producer = new BdatPageProducer(aDataset, aStream);
        return new Iterator<>()
        {

            @Override
            public boolean hasNext()
            {
                return producer.hasNext();
            }


            @Override
            public PageSupplier next()
            {
                Page page = producer.next();
                return () -> page;
            }
        };
    }


    /**
     * Page source for the memory-mapped fast path: pre-parsed observation pages first (already in
     * memory), then one supplier per lazy page index. Each lazy supplier copies its page slice out
     * of the shared read-only {@link MemorySegment} and decodes it <em>in the worker</em>, so the
     * reads run in parallel. An unknown-type page decodes to {@code null} (contributes no rows).
     *
     * @param aDataset
     *            the parsed dataset.
     * @param aSegment
     *            a read-only segment covering the whole file.
     * @return an iterator of page suppliers in file order.
     */
    public static Iterator<PageSupplier> mmapSource(DatasetBdat aDataset, MemorySegment aSegment)
    {
        int pageSize = aDataset.getHeader3().getPageSize();
        long headerSize = aDataset.getHeader3().getHeaderSize();
        long pageCount = aDataset.getHeader3().getPageCount();
        long segSize = aSegment.byteSize();
        List<Page> preParsed = aDataset.getPages().stream()
                .filter(p -> p.getTotalObservationCount() > 0).toList();

        return new Iterator<>()
        {

            private int preIndex;

            private long lazyIndex = aDataset.getMetadataPageCount();

            @Override
            public boolean hasNext()
            {
                return preIndex < preParsed.size() || lazyIndex < pageCount;
            }


            @Override
            public PageSupplier next()
            {
                if (!hasNext())
                {
                    throw new NoSuchElementException();
                }
                if (preIndex < preParsed.size())
                {
                    Page page = preParsed.get(preIndex++);
                    return () -> page;
                }
                long index = lazyIndex++;
                long startByte = pageStartByte(headerSize, pageSize, index);
                return () ->
                {
                    if (startByte < 0 || startByte + pageSize > segSize)
                    {
                        // Declared page lies beyond the mapped file (truncated / corrupt header):
                        // skip it. The downstream row-count assertion still flags a real shortfall.
                        LOGGER.log(Level.WARNING,
                                "Skipping page at index {0} (offset {1}+{2}) beyond mapped size {3}",
                                index, startByte, pageSize, segSize);
                        return null;
                    }
                    byte[] pageBuffer = aSegment.asSlice(startByte, pageSize)
                            .toArray(ValueLayout.JAVA_BYTE);
                    return BdatPageProducer.decodePageAt(aDataset, pageBuffer, startByte);
                };
            }
        };
    }


    /**
     * Resolve the default worker count: {@code -Dbdat.parallelism} if set and positive, otherwise
     * {@code max(1, availableProcessors() - 1)}.
     *
     * @return the parallelism to use.
     */
    public static int defaultParallelism()
    {
        String prop = System.getProperty("bdat.parallelism");
        if (prop != null)
        {
            try
            {
                int v = Integer.parseInt(prop.trim());
                if (v > 0)
                {
                    return v;
                }
            }
            catch (NumberFormatException e)
            {
                LOGGER.log(Level.DEBUG, "Ignoring invalid bdat.parallelism value '" + prop + "'",
                        e);
            }
        }
        return Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    }


    /**
     * The recommended in-flight cap for the given parallelism.
     *
     * <p>
     * Computed in 64-bit arithmetic and clamped to {@link #MAX_IN_FLIGHT}: {@code
     * IN_FLIGHT_FACTOR * aParallelism} overflows {@code int} above 715&nbsp;827&nbsp;882 and wraps
     * to a <em>positive</em> nonsense cap for part of that range ({@code 1_500_000_000} wrapped to
     * 205&nbsp;032&nbsp;704), which {@code Math.max} cannot catch. The cap is not only an overflow
     * guard: each in-flight slot holds a whole parsed page, so it bounds live heap as much as it
     * bounds the queue.
     * </p>
     *
     * @param aParallelism
     *            the worker count the cap is derived from.
     * @return the in-flight page cap, at least 2 and at most {@link #MAX_IN_FLIGHT}.
     */
    public static int defaultMaxInFlight(int aParallelism)
    {
        long scaled = (long) IN_FLIGHT_FACTOR * aParallelism;
        return Math.clamp(scaled, 2, MAX_IN_FLIGHT);
    }


    /**
     * Compute the file byte offset of a page for the mmap fast path, in 64-bit arithmetic so files
     * larger than 2&nbsp;GB (page index × page size exceeding {@link Integer#MAX_VALUE}) address
     * correctly.
     *
     * @param aHeaderSize
     *            the BDAT header size in bytes.
     * @param aPageSize
     *            the page size in bytes.
     * @param aIndex
     *            the zero-based page index.
     * @return the absolute byte offset of the page.
     */
    public static long pageStartByte(long aHeaderSize, int aPageSize, long aIndex)
    {
        return aHeaderSize + aIndex * aPageSize;
    }


    /**
     * Run the pipeline, filling the column array.
     *
     * @return the appended row count and deleted-row count.
     * @throws IOException
     *             on any worker/producer failure or user abort.
     */
    public LoadResult load() throws IOException
    {
        // Warm the struct metadata caches single-threaded: the underlying struct library caches
        // reflective layouts in an unsynchronised map (StructEntityMapping.CACHE is a plain
        // HashMap with an unguarded containsKey/put), so a worker's first concurrent decode of a
        // page header / subheader pointer could otherwise race that cache. Touch both here (on the
        // calling thread) so they are fully populated before any worker decodes a page.
        //
        // Going through the byte-count accessors rather than the struct accessors also memoizes
        // the two sizes Page#getDataAreaOffset needs, which every worker asks for once per page —
        // Struct.create allocates a fresh Struct and token list on each call.
        dataset.getPageHeaderByteCount();
        dataset.getSubHeaderPointerByteCount();

        // Not try-with-resources: the finally block below cancels with shutdownNow() and then waits
        // for quiescence, whereas ExecutorService.close() would shut down gracefully and let every
        // queued page task run to completion first.
        @SuppressWarnings("PMD.CloseResource")
        ExecutorService pool = Executors.newFixedThreadPool(parallelism, daemonThreadFactory());
        Semaphore inFlight = new Semaphore(maxInFlight);
        BlockingQueue<Object> queue = new ArrayBlockingQueue<>(maxInFlight + 1);
        ProducerState producer = new ProducerState();

        Thread producerThread = new Thread(() -> produce(pool, inFlight, queue, producer),
                "bdat-loader-producer");
        producerThread.setDaemon(true);
        producerThread.start();

        long appendedRows = 0;
        long deleted = 0;
        long typeMismatches = 0;
        int firstMismatchColumn = -1;
        try
        {
            while (true)
            {
                Object item = queue.take();
                if (item == SENTINEL)
                {
                    break;
                }
                @SuppressWarnings("unchecked")
                PageResult result = ((Future<PageResult>) item).get();
                appendChunks(result);
                appendedRows += result.rowCount();
                deleted += result.deletedCount();
                if (result.mismatchCount() > 0)
                {
                    typeMismatches += result.mismatchCount();
                    firstMismatchColumn = firstMismatchColumn < 0 ? result.firstMismatchColumn()
                            : firstMismatchColumn;
                }
                inFlight.release();
                progress.rowsLoaded(appendedRows);
            }

            if (producer.error != null)
            {
                throw asIoException(producer.error);
            }
            warnOnTypeMismatches(typeMismatches, firstMismatchColumn);
            return new LoadResult(appendedRows, deleted, typeMismatches);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while loading BDAT data", e);
        }
        catch (ExecutionException e)
        {
            // Surface the worker failure, but don't hide a concurrent producer failure (often the
            // real root cause); attach it as suppressed. The ExecutionException carries this
            // consumer thread's trace, so the unwrapped exception keeps it as a suppressed one.
            Throwable cause = e.getCause();
            IOException io;
            if (cause == null)
            {
                io = asIoException(e);
            }
            else
            {
                io = asIoException(cause);
                io.addSuppressed(e);
            }
            if (producer.error != null && producer.error != cause)
            {
                io.addSuppressed(producer.error);
            }
            throw io;
        }
        finally
        {
            producerThread.interrupt();
            pool.shutdownNow();
            // CRITICAL: do not return until every worker and the producer have actually stopped.
            // A worker mid-read could otherwise touch the caller's resources (e.g. an mmap
            // MemorySegment) after they are released — an IllegalStateException at best, a JVM
            // crash at worst. shutdownNow() only *requests* stop; we must wait for it.
            awaitQuiescence(pool, producerThread);
        }
    }


    /**
     * Block until the worker pool has terminated and the producer thread has joined. Waits through
     * interruption (deferring it) on purpose: returning early while a worker is still running could
     * let it touch resources the caller is about to release (the mmap segment).
     *
     * <p>
     * The wait is deliberately unbounded — there is no safe timeout, because abandoning a live
     * worker is exactly the JVM crash this exists to prevent. It is not, however, silent: a worker
     * wedged on a malformed page would otherwise present as a frozen application with nothing in
     * the log to go on, so progress warnings are emitted every
     * {@value #QUIESCENCE_WARN_INTERVAL_SECONDS} seconds.
     * </p>
     */
    private static void awaitQuiescence(ExecutorService aPool, Thread aProducerThread)
    {
        boolean interrupted = false;
        boolean poolTerminated = false;
        long startNanos = System.nanoTime();
        long nextWarnSeconds = QUIESCENCE_WARN_INTERVAL_SECONDS;
        while (!poolTerminated)
        {
            try
            {
                poolTerminated = aPool.awaitTermination(1, TimeUnit.SECONDS);
            }
            catch (InterruptedException e)
            {
                interrupted = true;
            }
            nextWarnSeconds = warnIfStillDraining(startNanos, nextWarnSeconds, "worker pool",
                    aPool);
        }
        while (aProducerThread.isAlive())
        {
            try
            {
                aProducerThread.join(1000);
            }
            catch (InterruptedException e)
            {
                interrupted = true;
            }
            // null, not aPool: the pool has already terminated (the loop above only exits when
            // it has), so appending its task count here would assert "0 task(s) still running"
            // in the one wait that is not about the pool at all.
            nextWarnSeconds = warnIfStillDraining(startNanos, nextWarnSeconds, "producer thread",
                    null);
        }
        if (interrupted)
        {
            Thread.currentThread().interrupt();
        }
    }


    /**
     * Emit a WARNING once the drain has outlasted the next threshold, and return the threshold for
     * the following one.
     *
     * @param aStartNanos
     *            when the drain began.
     * @param aNextWarnSeconds
     *            the elapsed-seconds threshold at which to warn.
     * @param aWhat
     *            what is still outstanding, for the message.
     * @param aPool
     *            the worker pool, consulted for a live task count when it exposes one; {@code null}
     *            when the wait is not about the pool.
     * @return the threshold for the next warning.
     */
    static long warnIfStillDraining(long aStartNanos, long aNextWarnSeconds, String aWhat,
            @Nullable ExecutorService aPool)
    {
        long elapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - aStartNanos);
        if (elapsedSeconds < aNextWarnSeconds)
        {
            return aNextWarnSeconds;
        }
        String active = aPool instanceof ThreadPoolExecutor pool
                ? " (" + pool.getActiveCount() + " task(s) still running)"
                : "";
        LOGGER.log(Level.WARNING,
                "Still waiting for the SAS7BDAT {0} to stop after {1}s{2}. The load cannot complete "
                        + "until it does; a worker is most likely stuck decoding a malformed page.",
                aWhat, elapsedSeconds, active);
        return elapsedSeconds + QUIESCENCE_WARN_INTERVAL_SECONDS;
    }


    /**
     * Report cells whose parsed value did not match the type their column was declared with.
     *
     * <p>
     * This path resolves such a cell by the <em>column</em> type (empty string for CHARACTER,
     * {@code MIS_UNKNOWN} for numeric), whereas the sequential {@code addData2Column} resolves it
     * by the <em>value</em> and would store the number. The two therefore only agree while the
     * declared type and the parsed value agree — which nothing enforces, and which used to fail
     * silently in both directions. Resolving by column type is the defensible choice (the table is
     * built from that metadata), so the disagreement is reported rather than reconciled.
     * </p>
     *
     * @param aCount
     *            how many cells disagreed.
     * @param aFirstColumn
     *            the column index of the first disagreement in FILE order, or {@code -1} when none
     *            did. Pages are consumed in file order, so a later page's lower column index does
     *            not replace an earlier page's higher one.
     */
    private void warnOnTypeMismatches(long aCount, int aFirstColumn)
    {
        if (aCount <= 0)
        {
            return;
        }
        LOGGER.log(Level.WARNING,
                "{0} cell value(s) did not match their declared column type and were stored as the "
                        + "column type''s missing value; first affected column index: {1}. This "
                        + "means the table metadata and the variable parsers disagree.",
                aCount, aFirstColumn);
    }


    /** Producer: read pages in order, submit worker tasks, enqueue their futures. */
    private void produce(ExecutorService aPool, Semaphore aInFlight, BlockingQueue<Object> aQueue,
            ProducerState aState)
    {
        try
        {
            while (pageSuppliers.hasNext())
            {
                PageSupplier supplier = pageSuppliers.next();
                aInFlight.acquire();
                Future<PageResult> future = aPool.submit(() -> extractAndParse(supplier.get()));
                aQueue.put(future);
            }
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
        catch (Throwable e)
        {
            // Throwable, not RuntimeException. The finally below puts the sentinel either way, so
            // an Error escaping here used to leave `error` null: load() returned a short result and
            // the caller's row-count assertion reported "Can't read all rows", telling the user
            // their file was truncated when the JVM had in fact run out of memory (realistic on
            // this path — a page buffer per page plus every in-flight chunk array). Recording it
            // lets the consumer rethrow the real cause instead.
            aState.error = e;
        }
        finally
        {
            putSentinelQuietly(aQueue);
        }
    }


    /** Worker: (decode if needed,) decompress + column-parse a single page into chunks. */
    private PageResult extractAndParse(@Nullable Page aPage) throws IOException
    {
        int colCount = parsers.length;
        List<byte[]> rows;
        long deleted;
        if (aPage == null)
        {
            // Unknown-type page from the mmap self-serve source: contributes no rows.
            rows = List.of();
            deleted = 0;
        }
        else
        {
            PageDataExtractor.PageRows pageRows = PageDataExtractor.extractRows(dataset, aPage,
                    compressed, compressionAlgorithm);
            rows = pageRows.rows();
            deleted = pageRows.deletedCount();
        }
        int n = rows.size();

        ColumnChunk[] chunks = new ColumnChunk[colCount];
        for (int c = 0; c < colCount; c++)
        {
            chunks[c] = types[c] == DataValueType.STRING ? new ColumnChunk(null, new String[n])
                    : new ColumnChunk(new double[n], null);
        }

        long mismatches = 0;
        int firstMismatchColumn = -1;

        for (int r = 0; r < n; r++)
        {
            byte[] row = rows.get(r);
            for (int c = 0; c < colCount; c++)
            {
                Object value = parsers[c].getValue(row);
                ColumnChunk chunk = chunks[c];
                if (chunk.strings != null)
                {
                    // CHARACTER column: String arm -> tri(); defensive null/non-String -> "".
                    if (value instanceof String s)
                    {
                        chunk.strings[r] = net.cumba.datatable.impl.provider.AbstractTableDataParser
                                .tri(s);
                    }
                    else
                    {
                        chunk.strings[r] = "";
                        mismatches++;
                        firstMismatchColumn = firstMismatchColumn < 0 ? c : firstMismatchColumn;
                    }
                }
                else
                {
                    // Invariant (see ColumnChunk creation): exactly one of strings/doubles is
                    // non-null; strings == null here means this is a NUMERIC column with doubles
                    // set.
                    double[] doubles = Objects.requireNonNull(chunk.doubles,
                            "numeric chunk doubles");
                    if (value instanceof Number num)
                    {
                        // Store the raw (possibly NaN-encoded) double; assembler decodes missings.
                        doubles[r] = num.doubleValue();
                    }
                    else
                    {
                        // NUMERIC column, defensive (unreachable for BDAT): getValue only ever
                        // returns a non-null Double or String. Mirrors BOTH the addData2Column
                        // `null` and `default` arms, which now agree on MIS_UNKNOWN for
                        // non-STRING columns — an unexpected cell should not read as an ordinary
                        // SAS '.' missing.
                        doubles[r] = MissingValue.MIS_UNKNOWN.asDouble();
                        mismatches++;
                        firstMismatchColumn = firstMismatchColumn < 0 ? c : firstMismatchColumn;
                    }
                }
            }
        }

        return new PageResult(chunks, deleted, n, mismatches, firstMismatchColumn);
    }


    /** Assembler: append one page's chunks to the global columns, in page order. */
    private void appendChunks(PageResult aResult)
    {
        ColumnChunk[] pageColumns = aResult.columns();
        int n = aResult.rowCount();
        for (int c = 0; c < columns.length; c++)
        {
            CachedDataTableColumn column = columns[c];
            ColumnChunk chunk = pageColumns[c];
            if (chunk.strings != null)
            {
                for (int r = 0; r < n; r++)
                {
                    column.addElement(chunk.strings[r]);
                }
            }
            else
            {
                // Invariant (see ColumnChunk creation): strings == null here means doubles is set.
                double[] doubles = Objects.requireNonNull(chunk.doubles, "numeric chunk doubles");
                for (int r = 0; r < n; r++)
                {
                    double d = doubles[r];
                    if (Double.isNaN(d))
                    {
                        // Mirrors addData2Column: a NaN double decodes to its SAS MissingValue,
                        // a bare NaN to MIS (owner ruling E5), anything else to MIS_UNKNOWN.
                        column.addElement(MissingValue.forNaN(d));
                    }
                    else
                    {
                        column.addElement(d);
                    }
                }
            }
        }
    }


    private static IOException asIoException(Throwable aCause)
    {
        if (aCause instanceof IOException io)
        {
            return io;
        }
        return new IOException(aCause.getMessage(), aCause);
    }


    /**
     * Enqueue the end-of-pages sentinel, the one signal that lets the consumer leave its
     * {@code take()} loop.
     *
     * <p>
     * F-prov-18: {@code offer}, not {@code put}. The queue has capacity {@code maxInFlight + 1} and
     * at most {@code maxInFlight} futures are ever physically queued (a permit is released only
     * after a future has been taken out), so the sentinel is guaranteed a free slot and a
     * non-blocking offer cannot fail. {@code put} locks <em>interruptibly</em>, so on the one path
     * that reaches here with the interrupt flag already set — the {@code InterruptedException} arm
     * of {@link #produce} — it threw immediately, enqueued nothing, and left the consumer blocked
     * on {@code take()} for ever: a frozen load with an empty log, which is the exact failure the
     * drain warnings exist to prevent. Not reachable from the product today (only {@code load()}'s
     * own {@code finally} interrupts the producer, and by then the consumer has left the loop), but
     * "the sentinel is enqueued either way" is the invariant the whole shutdown design rests on.
     * </p>
     */
    private void putSentinelQuietly(BlockingQueue<Object> aQueue)
    {
        aQueue.offer(SENTINEL);
    }


    private ThreadFactory daemonThreadFactory()
    {
        AtomicInteger counter = new AtomicInteger();
        return runnable ->
        {
            Thread t = new Thread(runnable, "bdat-loader-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    /**
     * Mutable holder for a producer-thread failure. Typed {@link Throwable}, not
     * {@link RuntimeException}: an {@link Error} on the producer must reach the consumer too, or it
     * is reported to the user as a truncated file.
     */
    private static final class ProducerState
    {

        private volatile @Nullable Throwable error;
    }
}
