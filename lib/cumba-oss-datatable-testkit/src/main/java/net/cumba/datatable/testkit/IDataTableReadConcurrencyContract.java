package net.cumba.datatable.testkit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.IDataTableRow;
import net.cumba.datatable.values.IDataValue;
import org.jspecify.annotations.Nullable;

/**
 * Checks an {@link IDataTable} implementation against the interface's <b>concurrent reads</b>
 * contract (owner ruling 2026-10-12, §3.10): a table that is not being modified may be read from
 * several threads at once, and every read answers what a serial read answers.
 *
 * <p>
 * The check reads the whole table once on the calling thread — the full {@code DataTableMeta}, row
 * count, per column {@code getColumnContent}, per row its name, real index and {@code getRow}
 * values, per cell {@code getValue}, {@code getDataValue}, the blankness queries and
 * {@code hashCodeAt} — and then has {@code threads} threads, released together by a barrier, read
 * it all again, each in its own order (a different starting row, every other thread with the
 * columns reversed), over and over, plus one {@code getRows().parallel()} pass. Every answer must
 * equal the serial one. A read that throws is part of its answer and must throw the same way.
 * </p>
 *
 * <p>
 * <b>Two arms.</b> The <b>warm arm</b> ({@link #check(IDataTable)}) reads the instance it is
 * handed: the serial reference read warms every lazy cache first, so it proves that reads of an
 * already touched table agree. It is blind to <b>first-touch</b> state — a cursor, a buffer or a
 * map filled on first use and published without synchronisation — because by the time the threads
 * start, nothing is touched for the first time any more (PACK G fold, G4 review). The <b>cold
 * arm</b> ({@link #checkCold(Supplier)}) takes a supplier of fresh instances: the reference is read
 * serially from one instance, and each of {@code rounds} rounds hands the barrier-released threads
 * another instance nobody has touched, so the first touch of every lazy structure happens under
 * contention; the parallel rows pass runs on yet another untouched instance. An implementation with
 * lazy read-path state belongs in the cold arm.
 * </p>
 *
 * <p>
 * Controls give it teeth: the <b>vacuity control</b> fails the check when the threads never read at
 * the same time (a green comparison would then prove nothing), the cold arm refuses a supplier that
 * hands out an instance twice, and its own test runs it against a table with a shared read cursor
 * and a table with a check-then-act first-touch cache — the latter accepted by the warm arm and
 * rejected by the cold one. (The internal twin also compares the formatted reads, a feature this
 * IDataTable does not have.) It reports a breach by throwing {@link AssertionError} itself (this
 * module has JUnit at test scope only). Meant for small representative tables: it keeps every
 * answer of the serial read in memory.
 * </p>
 */
public final class IDataTableReadConcurrencyContract
{

    /** The default number of reading threads. */
    public static final int DEFAULT_THREADS = 8;

    /** The default number of fresh instances the cold arm reads under contention. */
    public static final int DEFAULT_ROUNDS = 3;

    /** Each thread reads the whole table at least this often. */
    static final int MIN_PASSES = 4;

    /** A thread keeps reading, past {@link #MIN_PASSES}, until it has seen another read with it. */
    static final long OVERLAP_DEADLINE_NANOS = TimeUnit.SECONDS.toNanos(10);

    private static final long TIMEOUT_SECONDS = 120;

    private static final int REPORTED_MISMATCHES = 10;

    private IDataTableReadConcurrencyContract()
    {
    }


    /**
     * Checks {@code aTable} with {@link #DEFAULT_THREADS} threads (the warm arm).
     *
     * @param aTable
     *            the table; nobody may modify it during the check
     * @throws AssertionError
     *             if a concurrent read answered differently from the serial one, or the reads never
     *             overlapped
     */
    public static void check(IDataTable aTable)
    {
        check(aTable, DEFAULT_THREADS);
    }


    /**
     * Checks {@code aTable} with {@code aThreads} threads (the warm arm).
     *
     * @param aTable
     *            the table; nobody may modify it during the check
     * @param aThreads
     *            the number of threads reading at once
     * @throws AssertionError
     *             if a concurrent read answered differently from the serial one, or the reads never
     *             overlapped (always the case for fewer than two threads)
     */
    public static void check(IDataTable aTable, int aThreads)
    {
        check(aTable, aThreads, OVERLAP_DEADLINE_NANOS);
    }


    static void check(IDataTable aTable, int aThreads, long aOverlapDeadlineNanos)
    {
        List<Object> reference = read(aTable, 0, false);
        AtomicInteger maxReading = new AtomicInteger();
        ConcurrentLinkedQueue<String> mismatches = new ConcurrentLinkedQueue<>();
        readConcurrently(aTable, reference, aThreads, aOverlapDeadlineNanos, maxReading, mismatches,
                "");
        compare(rowsSerially(aTable), rowsInParallel(aTable), "getRows().parallel()", mismatches);
        verdict(aThreads, maxReading, mismatches);
    }


    /**
     * Checks fresh instances from {@code aTables} with {@link #DEFAULT_THREADS} threads over
     * {@link #DEFAULT_ROUNDS} rounds (the cold arm: every round's instance is first touched by the
     * concurrent readers).
     *
     * @param aTables
     *            hands out a new, untouched instance of the same table on every call; nobody may
     *            modify one during the check
     * @throws AssertionError
     *             if a concurrent read answered differently from the serial read of a separate
     *             instance, if the reads never overlapped, or if the supplier handed out an
     *             instance twice
     */
    public static void checkCold(Supplier<? extends IDataTable> aTables)
    {
        checkCold(aTables, DEFAULT_THREADS, DEFAULT_ROUNDS);
    }


    /**
     * Checks fresh instances from {@code aTables} with {@code aThreads} threads over
     * {@code aRounds} rounds (the cold arm).
     *
     * @param aTables
     *            hands out a new, untouched instance of the same table on every call
     * @param aThreads
     *            the number of threads reading at once
     * @param aRounds
     *            how many untouched instances the threads read; at least one
     * @throws AssertionError
     *             as {@link #checkCold(Supplier)}
     */
    public static void checkCold(Supplier<? extends IDataTable> aTables, int aThreads, int aRounds)
    {
        checkCold(aTables, aThreads, aRounds, OVERLAP_DEADLINE_NANOS);
    }


    static void checkCold(Supplier<? extends IDataTable> aTables, int aThreads, int aRounds,
            long aOverlapDeadlineNanos)
    {
        if (aRounds < 1)
        {
            throw new IllegalArgumentException("at least one round");
        }
        List<IDataTable> seen = new ArrayList<>();
        // the reference comes from its own instance, read serially: that one may warm up
        IDataTable referenceTable = fresh(aTables, seen);
        List<Object> reference = read(referenceTable, 0, false);
        AtomicInteger maxReading = new AtomicInteger();
        ConcurrentLinkedQueue<String> mismatches = new ConcurrentLinkedQueue<>();
        for (int round = 0; round < aRounds; round++)
        {
            readConcurrently(fresh(aTables, seen), reference, aThreads, aOverlapDeadlineNanos,
                    maxReading, mismatches, "round " + round + " ");
        }
        // the parallel rows pass on an instance nobody touched before it either
        compare(rowsSerially(referenceTable), rowsInParallel(fresh(aTables, seen)),
                "getRows().parallel() on an untouched instance", mismatches);
        verdict(aThreads, maxReading, mismatches);
    }


    private static IDataTable fresh(Supplier<? extends IDataTable> aTables, List<IDataTable> aSeen)
    {
        IDataTable table = Objects.requireNonNull(aTables.get(), "the supplier returned null");
        for (IDataTable earlier : aSeen)
        {
            if (earlier == table)
            {
                throw new AssertionError("the supplier handed out the same instance twice; the cold"
                        + " arm needs an untouched instance per call, or it proves nothing");
            }
        }
        aSeen.add(table);
        return table;
    }


    /**
     * {@code aThreads} threads, released together, read {@code aTable} in their own orders until
     * each has read it {@link #MIN_PASSES} times and seen another thread read with it.
     */
    private static void readConcurrently(IDataTable aTable, List<Object> aReference, int aThreads,
            long aOverlapDeadlineNanos, AtomicInteger aMaxReading,
            ConcurrentLinkedQueue<String> aMismatches, String aWho)
    {
        CyclicBarrier start = new CyclicBarrier(aThreads);
        AtomicInteger reading = new AtomicInteger();
        // shutdownNow in the finally below, never close(): close() waits for the workers, and a
        // reader that hangs is exactly what this check must report rather than wait for.
        @SuppressWarnings("PMD.CloseResource")
        ExecutorService pool = Executors.newFixedThreadPool(aThreads, r ->
        {
            Thread thread = new Thread(r, "IDataTableReadConcurrencyContract");
            thread.setDaemon(true);
            return thread;
        });
        try
        {
            List<Future<?>> workers = new ArrayList<>(aThreads);
            for (int t = 0; t < aThreads; t++)
            {
                int thread = t;
                workers.add(pool.submit(() ->
                {
                    start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    reading.incrementAndGet();
                    try
                    {
                        long deadline = System.nanoTime() + aOverlapDeadlineNanos;
                        for (int pass = 0; pass < MIN_PASSES
                                || (aMaxReading.get() < 2 && System.nanoTime() < deadline); pass++)
                        {
                            aMaxReading.accumulateAndGet(reading.get(), Math::max);
                            compare(aReference,
                                    read(aTable, firstRow(thread, pass), reverseColumns(thread)),
                                    aWho + "thread " + thread + " pass " + pass, aMismatches);
                        }
                    }
                    finally
                    {
                        reading.decrementAndGet();
                    }
                    return null;
                }));
            }
            for (Future<?> worker : workers)
            {
                worker.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
        }
        catch (ExecutionException | TimeoutException ex)
        {
            throw new AssertionError("a reading thread failed or hung: " + ex, ex);
        }
        catch (InterruptedException ex)
        {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted", ex);
        }
        finally
        {
            pool.shutdownNow();
        }
    }


    private static void verdict(int aThreads, AtomicInteger aMaxReading,
            ConcurrentLinkedQueue<String> aMismatches)
    {
        if (aMaxReading.get() < 2)
        {
            throw new AssertionError("vacuous: the " + aThreads
                    + " reading threads never read at the same time, so agreement proves nothing");
        }
        if (!aMismatches.isEmpty())
        {
            throw new AssertionError(aMismatches.size()
                    + " concurrent reads answered differently from the serial read; first: "
                    + aMismatches.stream().limit(REPORTED_MISMATCHES).toList());
        }
    }


    /** The row a thread starts a pass at (scaled by 7 in {@link #read}): each pass elsewhere. */
    static int firstRow(int aThread, int aPass)
    {
        return aThread + aPass;
    }


    /** Every other thread reads the columns right to left. */
    static boolean reverseColumns(int aThread)
    {
        return aThread % 2 != 0;
    }


    /**
     * Every answer, in canonical order, read in this thread's own order: rows from
     * {@code aStartRow} (wrapping), columns reversed when {@code aReverseColumns}. The first answer
     * is the whole {@code DataTableMeta} (compared by {@code equals}, every column's metadata
     * included), the others are strings.
     */
    static List<Object> read(IDataTable aTable, int aStartRow, boolean aReverseColumns)
    {
        int rows = (int) aTable.getRowCount();
        int cols = aTable.getColumnCount();
        int perCell = 4;
        int perRow = 3 + cols * perCell;
        Object[] out = new Object[2 + cols + rows * perRow];
        out[0] = answer(aTable::getMetaData);
        out[1] = answer(
                () -> aTable.getRowCount() + " rows, " + aTable.getColumnCount() + " columns");
        for (int k = 0; k < cols; k++)
        {
            int c = aReverseColumns ? cols - 1 - k : k;
            out[2 + c] = answer(() -> String.valueOf(aTable.getColumnContent(c)));
        }
        for (int i = 0; i < rows; i++)
        {
            int r = Math.floorMod(aStartRow * 7 + i, rows);
            int base = 2 + cols + r * perRow;
            out[base] = answer(() -> aTable.getRowName(r));
            out[base + 1] = answer(() -> String.valueOf(aTable.getRealRowIndex(r)));
            out[base + 2] = answer(() -> row(aTable.getRow(r)));
            for (int k = 0; k < cols; k++)
            {
                int c = aReverseColumns ? cols - 1 - k : k;
                int at = base + 3 + c * perCell;
                out[at] = answer(() -> value(aTable.getValue(r, c)));
                out[at + 1] = answer(() -> dataValue(aTable.getDataValue(r, c)));
                out[at + 2] = answer(() -> aTable.isMissingOrNull(r, c) + "/"
                        + aTable.isEmptyOrMissing(r, c) + "/" + aTable.isSupplied(r, c));
                out[at + 3] = answer(() -> String.valueOf(aTable.hashCodeAt(r, c)));
            }
        }
        return Arrays.asList(out);
    }


    private static List<Object> rowsSerially(IDataTable aTable)
    {
        List<Object> out = new ArrayList<>();
        for (long r = 0; r < aTable.getRowCount(); r++)
        {
            long row = r;
            out.add(answer(() -> row(aTable.getRow(row))));
        }
        return out;
    }


    private static List<Object> rowsInParallel(IDataTable aTable)
    {
        Object[] out = new Object[(int) aTable.getRowCount()];
        aTable.getRows().parallel()
                .forEach(row -> out[(int) row.getIndex()] = answer(() -> row(row)));
        return Arrays.asList(out);
    }


    private static void compare(List<Object> aReference, List<Object> aActual, String aWho,
            ConcurrentLinkedQueue<String> aMismatches)
    {
        if (aReference.size() != aActual.size())
        {
            aMismatches
                    .add(aWho + ": " + aActual.size() + " answers instead of " + aReference.size());
            return;
        }
        for (int i = 0; i < aReference.size(); i++)
        {
            if (!Objects.equals(aReference.get(i), aActual.get(i)))
            {
                aMismatches.add(aWho + ", answer " + i + ": " + aActual.get(i) + " instead of "
                        + aReference.get(i));
            }
        }
    }


    /** A read's answer: its rendering (or the object itself), or how it threw. */
    private static Object answer(Supplier<?> aRead)
    {
        try
        {
            return aRead.get();
        }
        catch (RuntimeException ex)
        {
            return "threw " + ex.getClass().getName() + ": " + ex.getMessage();
        }
    }


    private static String row(IDataTableRow aRow)
    {
        return aRow.getIndex() + "@" + aRow.getRealRowIndex() + " "
                + aRow.getValues().stream().map(IDataTableReadConcurrencyContract::value).toList();
    }


    private static String value(@Nullable Object aValue)
    {
        return aValue == null ? "null" : aValue.getClass().getSimpleName() + ":" + aValue;
    }


    private static String dataValue(IDataValue aValue)
    {
        return aValue.getType() + "=" + value(aValue.getValue());
    }
}
