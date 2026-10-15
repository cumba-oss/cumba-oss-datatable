package net.cumba.datatable.testkit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.cumba.datatable.ColumnContent;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.IDataTableRow;
import net.cumba.datatable.values.DataValueType;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The harness itself: it accepts a table that is safe to read concurrently, and it must reject the
 * shape the {@link IDataTable} contract forbids — per-call cursor state in a shared field — or a
 * green result elsewhere proves nothing. Its vacuity control must fire when the reads cannot
 * overlap. (The internal twin also rejects a format with shared scratch state; this IDataTable has
 * no formatted reads.)
 */
class IDataTableReadConcurrencyContractTest
{

    /** A spin long enough that two concurrent reads reliably meet inside it. */
    private static final long HOLD_NANOS = 20_000L;

    @Test
    void anImmutableTablePasses()
    {
        assertDoesNotThrow(() -> IDataTableReadConcurrencyContract.check(table(12)));
    }


    @Test
    void aTableWithASharedReadCursorIsRejected()
    {
        AssertionError error = assertThrows(AssertionError.class,
                () -> IDataTableReadConcurrencyContract.check(new SharedCursorTable(table(12))));
        assertTrue(error.getMessage().contains("answered differently from the serial read"),
                error.getMessage());
    }


    @Test
    void oneThreadCannotOverlapSoTheVacuityControlFires()
    {
        AssertionError error = assertThrows(AssertionError.class,
                () -> IDataTableReadConcurrencyContract.check(table(3), 1,
                        TimeUnit.MILLISECONDS.toNanos(50)));
        assertTrue(error.getMessage().startsWith("vacuous"), error.getMessage());
    }


    @Test
    void aReadThatThrowsMustThrowTheSameWayEveryTime()
    {
        assertDoesNotThrow(() -> IDataTableReadConcurrencyContract
                .check(new ThrowingHashTable(table(4), () -> "always the same")));

        int[] calls =
        {
                0
        };
        AssertionError error = assertThrows(AssertionError.class,
                () -> IDataTableReadConcurrencyContract
                        .check(new ThrowingHashTable(table(4), () -> "call " + nextCall(calls))));
        assertTrue(error.getMessage().contains("threw java.lang.IllegalStateException: call"),
                error.getMessage());
    }


    @Test
    void aThreadThatDiesFailsTheCheck()
    {
        AssertionError error = assertThrows(AssertionError.class,
                () -> IDataTableReadConcurrencyContract.check(new ErrorOnWorkerTable(table(2))));
        assertTrue(error.getMessage().startsWith("a reading thread failed or hung"),
                error.getMessage());
    }


    @Test
    void twoThreadsAreEnoughAndStopOnceTheyHaveMet()
    {
        long start = System.nanoTime();
        assertDoesNotThrow(() -> IDataTableReadConcurrencyContract.check(table(6), 2,
                TimeUnit.SECONDS.toNanos(5)));
        assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(4),
                "two threads that met must not keep reading until the overlap deadline");
    }


    @Test
    void aLoneThreadKeepsTryingUntilTheOverlapDeadline()
    {
        long start = System.nanoTime();
        assertThrows(AssertionError.class, () -> IDataTableReadConcurrencyContract.check(table(3),
                1, TimeUnit.MILLISECONDS.toNanos(300)));
        assertTrue(System.nanoTime() - start >= TimeUnit.MILLISECONDS.toNanos(250),
                "the check gave up before the overlap deadline");
    }


    @Test
    void withoutADeadlineAThreadReadsExactlyTheMinimumPasses()
    {
        RecordingTable recording = new RecordingTable(table(5));
        assertThrows(AssertionError.class,
                () -> IDataTableReadConcurrencyContract.check(recording, 1, 0));
        assertEquals(IDataTableReadConcurrencyContract.MIN_PASSES * 5,
                recording.workerRowNames.size());
        assertEquals(List.of(true), recording.workerDaemon.stream().distinct().toList(),
                "the reading threads must be daemons, so a hung read cannot keep the JVM alive");
    }


    @Test
    void aRowStreamThatDisagreesWithGetRowIsRejected()
    {
        IDataTable other = table(12);
        IDataTable table = new Delegating(
                new SyntheticDataTable("T", List.of("ID", "N"), new String[]
                {
                        "x", "y"
                }, 12))
        {

            @Override
            public Stream<IDataTableRow> getRows()
            {
                return other.getRows();
            }
        };
        AssertionError error = assertThrows(AssertionError.class,
                () -> IDataTableReadConcurrencyContract.check(table));
        assertTrue(error.getMessage().contains("getRows().parallel()"), error.getMessage());
    }


    /** Rows from {@code 7 * firstRow}, wrapping; every other thread reverses the columns. */
    @Test
    void eachThreadReadsInItsOwnOrder()
    {
        assertEquals(5, IDataTableReadConcurrencyContract.firstRow(2, 3));
        assertEquals(List.of(false, true, false, true), IntStream.range(0, 4)
                .mapToObj(IDataTableReadConcurrencyContract::reverseColumns).toList());

        RecordingTable forward = new RecordingTable(table(10));
        IDataTableReadConcurrencyContract.read(forward, 1, false);
        assertEquals(List.of(7L, 8L, 9L, 0L, 1L, 2L, 3L, 4L, 5L, 6L), forward.rowNames);
        assertEquals(List.of(0, 1), forward.contentColumns);
        assertEquals(List.of(0, 1), forward.hashColumns.subList(0, 2));

        RecordingTable reversed = new RecordingTable(table(10));
        IDataTableReadConcurrencyContract.read(reversed, 0, true);
        assertEquals(0L, reversed.rowNames.get(0));
        assertEquals(List.of(1, 0), reversed.contentColumns);
        assertEquals(List.of(1, 0), reversed.hashColumns.subList(0, 2));
    }


    /** Every answer sits at its canonical place, whatever order a thread reads in. */
    @Test
    void theReadOrderDoesNotMoveAnAnswer()
    {
        IDataTable table = table(5);
        List<String> canonical = IDataTableReadConcurrencyContract.read(table, 0, false);
        assertEquals(canonical, IDataTableReadConcurrencyContract.read(table, 3, true));
        assertEquals(canonical, IDataTableReadConcurrencyContract.read(table, 4, false));
    }


    @Test
    void theAnswersCoverEveryReadOnce()
    {
        IDataTable table = table(2);
        List<String> answers = IDataTableReadConcurrencyContract.read(table, 0, false);
        // 1 table answer + 1 per column (content) + per row 3 + 4 per cell
        assertEquals(1 + 2 + 2 * (3 + 2 * 4), answers.size());
        assertEquals("2 rows, 2 columns, [ID:STRING, N:LONG]", answers.get(0));
        assertEquals(List.of(String.valueOf(table.getColumnContent(0)),
                String.valueOf(table.getColumnContent(1))), answers.subList(1, 3));
        int row1 = 1 + 2 + 3 + 2 * 4;
        assertEquals("2", answers.get(row1));
        assertEquals("1", answers.get(row1 + 1));
        assertTrue(answers.get(row1 + 2).startsWith("1@1 ["), answers.get(row1 + 2));
        assertEquals(table.getValue(1, 0) == null ? "null" : "String:" + table.getValue(1, 0),
                answers.get(row1 + 3));
        assertTrue(answers.get(row1 + 4).startsWith("STRING="), answers.get(row1 + 4));
        assertTrue(answers.get(row1 + 5).matches("(true|false)/(true|false)/(true|false)"),
                answers.get(row1 + 5));
        assertEquals(String.valueOf(table.hashCodeAt(1, 0)), answers.get(row1 + 6));
        assertTrue(
                answers.get(row1 + 7).startsWith("Long:") || "null".equals(answers.get(row1 + 7)),
                answers.get(row1 + 7));
    }


    private static int nextCall(int[] aCalls)
    {
        synchronized (aCalls)
        {
            return ++aCalls[0];
        }
    }


    private static IDataTable table(int aRows)
    {
        Map<String, DataValueType> columns = new LinkedHashMap<>();
        columns.put("ID", DataValueType.STRING);
        columns.put("N", DataValueType.LONG);
        return new SyntheticDataTable("T", columns, aRows);
    }


    private static void hold()
    {
        long end = System.nanoTime() + HOLD_NANOS;
        while (System.nanoTime() < end)
        {
            Thread.onSpinWait();
        }
    }

    /** Delegates every read the harness makes; subclasses break one of them. */
    private abstract static class Delegating implements IDataTable
    {

        final IDataTable delegate;

        Delegating(IDataTable aDelegate)
        {
            delegate = aDelegate;
        }


        @Override
        public DataTableMeta getMetaData()
        {
            return delegate.getMetaData();
        }


        @Override
        public long getRowCount()
        {
            return delegate.getRowCount();
        }


        @Override
        public @Nullable Object getValue(long aRow, int aColumn)
        {
            return delegate.getValue(aRow, aColumn);
        }
    }


    /** Records the reads that show a thread's order, and who read on which thread. */
    private static final class RecordingTable extends Delegating
    {

        final List<Long> rowNames = new CopyOnWriteArrayList<>();

        final List<Integer> contentColumns = new CopyOnWriteArrayList<>();

        final List<Integer> hashColumns = new CopyOnWriteArrayList<>();

        final List<Long> workerRowNames = new CopyOnWriteArrayList<>();

        final List<Boolean> workerDaemon = new CopyOnWriteArrayList<>();

        private final Thread owner = Thread.currentThread();

        RecordingTable(IDataTable aDelegate)
        {
            super(aDelegate);
        }


        @Override
        public String getRowName(long aRowIndex)
        {
            if (Thread.currentThread() == owner)
            {
                rowNames.add(aRowIndex);
            }
            else
            {
                workerRowNames.add(aRowIndex);
                workerDaemon.add(Thread.currentThread().isDaemon());
            }
            return delegate.getRowName(aRowIndex);
        }


        @Override
        public ColumnContent getColumnContent(int aColumn)
        {
            if (Thread.currentThread() == owner)
            {
                contentColumns.add(aColumn);
            }
            return delegate.getColumnContent(aColumn);
        }


        @Override
        public int hashCodeAt(long aRow, int aColumn)
        {
            if (Thread.currentThread() == owner)
            {
                hashColumns.add(aColumn);
            }
            return delegate.hashCodeAt(aRow, aColumn);
        }
    }


    /** One shared cursor on the read path: a concurrent read steals another's coordinates. */
    private static final class SharedCursorTable extends Delegating
    {

        // A field on purpose: the shared cursor IS the defect this fixture models.
        @SuppressWarnings("PMD.SingularField")
        private long cursorRow = -1;

        SharedCursorTable(IDataTable aDelegate)
        {
            super(aDelegate);
        }


        @Override
        public @Nullable Object getValue(long aRow, int aColumn)
        {
            cursorRow = aRow;
            hold();
            return delegate.getValue(cursorRow, aColumn);
        }
    }


    /** A cell read that throws with a message from {@code aMessage}. */
    private static final class ThrowingHashTable extends Delegating
    {

        private final java.util.function.Supplier<String> message;

        ThrowingHashTable(IDataTable aDelegate, java.util.function.Supplier<String> aMessage)
        {
            super(aDelegate);
            message = aMessage;
        }


        @Override
        public int hashCodeAt(long aRow, int aColumn)
        {
            throw new IllegalStateException(message.get());
        }
    }


    /** Reads fine on the calling thread, but an Error kills every reading thread. */
    private static final class ErrorOnWorkerTable extends Delegating
    {

        private final Thread owner = Thread.currentThread();

        ErrorOnWorkerTable(IDataTable aDelegate)
        {
            super(aDelegate);
        }


        @Override
        public long getRowCount()
        {
            if (Thread.currentThread() != owner)
            {
                throw new LinkageError("worker");
            }
            return delegate.getRowCount();
        }
    }
}
