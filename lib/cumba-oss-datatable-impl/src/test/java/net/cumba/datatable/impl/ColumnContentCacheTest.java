package net.cumba.datatable.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.cumba.datatable.ColumnContent;
import net.cumba.datatable.ColumnContent.Fill;
import net.cumba.datatable.ColumnContent.Uniformity;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.view.UnionDataTable;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.MissingValue;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * {@code PLAN-present-empty-column-fold} row 47, the cache in this reimplementation: cached per
 * instance under the table's content generation, a wrapper above a changing table moves with its
 * source's generation, and the clear-then-late-store race (§4.1: a scan that began before an
 * invalidation must not store after it). No edit layer and no buffer overrides here: the scan is
 * the one source, and a missing is built as a {@link MissingValue} (a DOUBLE column rejects a raw
 * null).
 */
class ColumnContentCacheTest
{

    private static ColumnContent of(Fill aFill, Uniformity aUniformity)
    {
        return new ColumnContent(aFill, aUniformity);
    }


    private static DataTableMeta meta(DataValueType aType, int aRows)
    {
        DataTableColumnMeta cm = DataTableColumnMeta.builder().index(0).name("C").type(aType)
                .build();
        return DataTableMeta.builder().name("T").setColumns(cm).rowCount(aRows).totalRowCount(aRows)
                .build();
    }


    private static ColumnCachedDataTable table(DataValueType aType, Object... aCells)
    {
        CachedDataTableColumn c = new CachedDataTableColumn(0, aType);
        for (Object cell : aCells)
        {
            c.addElement(cell);
        }
        return new ColumnCachedDataTable(meta(aType, aCells.length), c);
    }


    @Test
    void theAnswerIsCachedPerInstance()
    {
        ColumnCachedDataTable t = table(DataValueType.STRING, "a", "b");
        ColumnContent first = t.getColumnContent(0);
        assertEquals(of(Fill.FILLED, Uniformity.MULTI_VALUES), first);
        assertSame(first, t.getColumnContent(0), "the second read is the cached instance");
        assertEquals(1, t.getColumnContents().length);
        ColumnCachedDataTable missing = table(DataValueType.DOUBLE, MissingValue.MIS,
                MissingValue.MIS_A);
        assertEquals(of(Fill.MISSING, Uniformity.MULTI_VALUES), missing.getColumnContent(0),
                ".A is not .");
    }


    @Test
    void twoTablesOverTheSameCellsCacheIndependently()
    {
        ColumnCachedDataTable a = table(DataValueType.STRING, "", "");
        ColumnCachedDataTable b = table(DataValueType.STRING, "", "");
        assertEquals(a.getColumnContent(0), b.getColumnContent(0));
        assertNotSame(a.getColumnContent(0), b.getColumnContent(0));
    }

    /** A table whose cells change under the test's control, and whose scan can be held. */
    private static final class MutableTable extends AbstractDataTable
    {

        final CountDownLatch scanStarted = new CountDownLatch(1);

        final CountDownLatch release = new CountDownLatch(1);

        final AtomicInteger computes = new AtomicInteger();

        volatile String cell = "v";

        MutableTable()
        {
            setMetaData(meta(DataValueType.STRING, 1));
        }


        @Override
        public long getRowCount()
        {
            return 1;
        }


        @Override
        public @Nullable Object getValue(long aRow, int aColumn) throws IndexOutOfBoundsException
        {
            return cell;
        }


        @Override
        protected ColumnContent computeColumnContent(int aColumn)
        {
            computes.incrementAndGet();
            scanStarted.countDown();
            try
            {
                release.await(10, TimeUnit.SECONDS);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
            return super.computeColumnContent(aColumn);
        }


        void set(String aCell)
        {
            cell = aCell;
            invalidateColumnContent();
        }
    }

    @Test
    void aWrapperAboveAChangingTableMovesWithTheChange()
    {
        MutableTable source = new MutableTable();
        source.release.countDown();
        IDataTable union = new UnionDataTable("U", source);
        assertEquals(of(Fill.FILLED, Uniformity.SINGLE_VALUE), union.getColumnContent(0));
        long before = union.contentGeneration();
        source.set("");
        assertTrue(union.contentGeneration() > before,
                "the union's generation includes its member's");
        assertEquals(of(Fill.EMPTY, Uniformity.SINGLE_VALUE), union.getColumnContent(0),
                "the union re-reads after the change below it");
    }


    @Test
    void aScanThatBeganBeforeAChangeDoesNotStoreAfterItsClear() throws Exception
    {
        MutableTable t = new MutableTable();
        Thread scanner = new Thread(() -> t.getColumnContent(0), "late-store");
        scanner.start();
        assertTrue(t.scanStarted.await(10, TimeUnit.SECONDS), "the scan must have started");
        t.set(""); // the change's clear lands while the scan is in flight
        t.release.countDown();
        scanner.join(10_000);
        assertEquals(1, t.computes.get(), "exactly one scan so far");
        assertEquals(of(Fill.EMPTY, Uniformity.SINGLE_VALUE), t.getColumnContent(0),
                "the in-flight scan's stale FILLED answer must not have been stored");
        assertEquals(2, t.computes.get(), "the read after the change scanned again");
        assertSame(t.getColumnContent(0), t.getColumnContent(0), "and that one is cached");
    }
}
