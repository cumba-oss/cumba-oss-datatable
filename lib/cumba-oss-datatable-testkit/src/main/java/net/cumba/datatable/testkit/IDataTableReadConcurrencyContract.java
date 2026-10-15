package net.cumba.datatable.testkit;

import java.util.ArrayList;
import java.util.List;
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
 * Checks one {@link IDataTable} instance against the interface's <b>concurrent reads</b> contract
 * (owner ruling 2026-10-12, §3.10): a table that is not being modified may be read from several
 * threads at once, and every read answers what a serial read answers.
 *
 * <p>
 * The check reads the whole table once on the calling thread — metadata, row count, per column
 * {@code getColumnContent}, per row its name, real index and {@code getRow} values, per cell
 * {@code getValue}, {@code getDataValue}, the blankness queries and {@code hashCodeAt} — and then
 * has {@code threads} threads, released together by a barrier, read it all again, each in its own
 * order (a different starting row, every other thread with the columns reversed), over and over,
 * plus one {@code getRows().parallel()} pass. Every answer must equal the serial one. A read that
 * throws is part of its answer and must throw the same way.
 * </p>
 *
 * <p>
 * Two controls give it teeth: the <b>vacuity control</b> fails the check when the threads never
 * read at the same time (a green comparison would then prove nothing), and its own test runs it
 * against a table with a shared read cursor, which it must reject. (The internal twin also compares
 * the formatted reads, a feature this IDataTable does not have.) It reports a breach by throwing
 * {@link AssertionError} itself (this module has JUnit at test scope only). Meant for small
 * representative tables: it keeps every answer of the serial read in memory.
 * </p>
 */
public final class IDataTableReadConcurrencyContract
{

    /** The default number of reading threads. */
    public static final int DEFAULT_THREADS = 8;

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
     * Checks {@code aTable} with {@link #DEFAULT_THREADS} threads.
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
     * Checks {@code aTable} with {@code aThreads} threads.
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
        List<String> reference = read(aTable, 0, false);
        CyclicBarrier start = new CyclicBarrier(aThreads);
        AtomicInteger reading = new AtomicInteger();
        AtomicInteger maxReading = new AtomicInteger();
        ConcurrentLinkedQueue<String> mismatches = new ConcurrentLinkedQueue<>();
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
                                || (maxReading.get() < 2 && System.nanoTime() < deadline); pass++)
                        {
                            maxReading.accumulateAndGet(reading.get(), Math::max);
                            compare(reference, read(aTable, thread + pass, thread % 2 != 0),
                                    "thread " + thread + " pass " + pass, mismatches);
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
            compare(rowsSerially(aTable), rowsInParallel(aTable), "getRows().parallel()",
                    mismatches);
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

        if (maxReading.get() < 2)
        {
            throw new AssertionError("vacuous: the " + aThreads
                    + " reading threads never read at the same time, so agreement proves nothing");
        }
        if (!mismatches.isEmpty())
        {
            throw new AssertionError(mismatches.size()
                    + " concurrent reads answered differently from the serial read; first: "
                    + mismatches.stream().limit(REPORTED_MISMATCHES).toList());
        }
    }


    /**
     * Every answer, in canonical order, read in this thread's own order: rows from
     * {@code aStartRow} (wrapping), columns reversed when {@code aReverseColumns}.
     */
    static List<String> read(IDataTable aTable, int aStartRow, boolean aReverseColumns)
    {
        int rows = (int) aTable.getRowCount();
        int cols = aTable.getColumnCount();
        int perCell = 4;
        int perRow = 3 + cols * perCell;
        String[] out = new String[1 + cols + rows * perRow];
        out[0] = answer(() -> aTable.getRowCount() + " rows, " + aTable.getColumnCount()
                + " columns, " + aTable.getMetaData().getAllColumns()
                        .map(c -> c.getName() + ":" + c.getType()).toList());
        for (int k = 0; k < cols; k++)
        {
            int c = aReverseColumns ? cols - 1 - k : k;
            out[1 + c] = answer(() -> String.valueOf(aTable.getColumnContent(c)));
        }
        for (int i = 0; i < rows; i++)
        {
            int r = Math.floorMod(aStartRow * 7 + i, rows);
            int base = 1 + cols + r * perRow;
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
        return List.of(out);
    }


    private static List<String> rowsSerially(IDataTable aTable)
    {
        List<String> out = new ArrayList<>();
        for (long r = 0; r < aTable.getRowCount(); r++)
        {
            long row = r;
            out.add(answer(() -> row(aTable.getRow(row))));
        }
        return out;
    }


    private static List<String> rowsInParallel(IDataTable aTable)
    {
        String[] out = new String[(int) aTable.getRowCount()];
        aTable.getRows().parallel()
                .forEach(row -> out[(int) row.getIndex()] = answer(() -> row(row)));
        return List.of(out);
    }


    private static void compare(List<String> aReference, List<String> aActual, String aWho,
            ConcurrentLinkedQueue<String> aMismatches)
    {
        for (int i = 0; i < aReference.size(); i++)
        {
            if (!aReference.get(i).equals(aActual.get(i)))
            {
                aMismatches.add(aWho + ", answer " + i + ": " + aActual.get(i) + " instead of "
                        + aReference.get(i));
            }
        }
    }


    /** A read's answer: its rendering, or how it threw. */
    private static String answer(Supplier<String> aRead)
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
