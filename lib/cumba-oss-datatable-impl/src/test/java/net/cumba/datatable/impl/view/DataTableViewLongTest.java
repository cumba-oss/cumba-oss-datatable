package net.cumba.datatable.impl.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DataTableViewLongTest
{

    @Test
    void testGetRowCount()
    {
        long[] view =
        {
                5L, 3L, 1L, 4L, 2L
        };
        DataTableViewLong dtv = DataTableViewLong.builder().view(view).build();

        assertEquals(5, dtv.getRowCount(null));
    }


    @Test
    void testGetRowCountEmpty()
    {
        long[] view = {};
        DataTableViewLong dtv = DataTableViewLong.builder().view(view).build();

        assertEquals(0, dtv.getRowCount(null));
    }


    @Test
    void testGetRealRow()
    {
        long[] view =
        {
                5L, 3L, 1L, 4L, 2L
        };
        DataTableViewLong dtv = DataTableViewLong.builder().view(view).build();

        assertEquals(5L, dtv.getRealRow(null, 0));
        assertEquals(3L, dtv.getRealRow(null, 1));
        assertEquals(1L, dtv.getRealRow(null, 2));
        assertEquals(4L, dtv.getRealRow(null, 3));
        assertEquals(2L, dtv.getRealRow(null, 4));
    }


    @Test
    void testGetRealRowNegativeIndex()
    {
        long[] view =
        {
                5L, 3L, 1L
        };
        DataTableViewLong dtv = DataTableViewLong.builder().view(view).build();

        assertThrows(IndexOutOfBoundsException.class, () -> dtv.getRealRow(null, -1));
    }


    @Test
    void testGetRealRowIndexTooLarge()
    {
        long[] view =
        {
                5L, 3L, 1L
        };
        DataTableViewLong dtv = DataTableViewLong.builder().view(view).build();

        assertThrows(IndexOutOfBoundsException.class, () -> dtv.getRealRow(null, 3));
        assertThrows(IndexOutOfBoundsException.class, () -> dtv.getRealRow(null, 100));
    }


    @Test
    void testGetRealRowEmptyView()
    {
        long[] view = {};
        DataTableViewLong dtv = DataTableViewLong.builder().view(view).build();

        assertThrows(IndexOutOfBoundsException.class, () -> dtv.getRealRow(null, 0));
    }


    @Test
    void testLargeValues()
    {
        // Test with values larger than Integer.MAX_VALUE
        long[] view =
        {
                Long.MAX_VALUE, Long.MAX_VALUE - 1, Integer.MAX_VALUE + 1L
        };
        DataTableViewLong dtv = DataTableViewLong.builder().view(view).build();

        assertEquals(Long.MAX_VALUE, dtv.getRealRow(null, 0));
        assertEquals(Long.MAX_VALUE - 1, dtv.getRealRow(null, 1));
        assertEquals(Integer.MAX_VALUE + 1L, dtv.getRealRow(null, 2));
    }


    @Test
    void testBoundaryRowAccess()
    {
        long[] view =
        {
                10L, 20L, 30L
        };
        DataTableViewLong dtv = DataTableViewLong.builder().view(view).build();

        // First valid index
        assertEquals(10L, dtv.getRealRow(null, 0));
        // Last valid index
        assertEquals(30L, dtv.getRealRow(null, 2));
        // First invalid index
        assertThrows(IndexOutOfBoundsException.class, () -> dtv.getRealRow(null, 3));
    }


    @Test
    void testGetDisplayRow()
    {
        long[] view =
        {
                5L, 3L, 1L, 4L, 2L
        };
        DataTableViewLong dtv = DataTableViewLong.builder().view(view).build();

        assertEquals(0, dtv.getDisplayRow(null, 5L));
        assertEquals(1, dtv.getDisplayRow(null, 3L));
        assertEquals(2, dtv.getDisplayRow(null, 1L));
        assertEquals(3, dtv.getDisplayRow(null, 4L));
        assertEquals(4, dtv.getDisplayRow(null, 2L));
    }


    @Test
    void testGetDisplayRowNotFound()
    {
        long[] view =
        {
                5L, 3L, 1L
        };
        DataTableViewLong dtv = DataTableViewLong.builder().view(view).build();

        assertEquals(-1, dtv.getDisplayRow(null, 0L));
        assertEquals(-1, dtv.getDisplayRow(null, 99L));
    }


    @Test
    void testGetDisplayRowEmptyView()
    {
        long[] view = {};
        DataTableViewLong dtv = DataTableViewLong.builder().view(view).build();

        assertEquals(-1, dtv.getDisplayRow(null, 0L));
    }
}
