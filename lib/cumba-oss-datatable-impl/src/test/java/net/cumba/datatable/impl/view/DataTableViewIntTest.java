package net.cumba.datatable.impl.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DataTableViewIntTest
{

    @Test
    void testGetRowCount()
    {
        int[] view =
        {
                5, 3, 1, 4, 2
        };
        DataTableViewInt dtv = DataTableViewInt.builder().view(view).build();

        assertEquals(5, dtv.getRowCount(null));
    }


    @Test
    void testGetRowCountEmpty()
    {
        int[] view = {};
        DataTableViewInt dtv = DataTableViewInt.builder().view(view).build();

        assertEquals(0, dtv.getRowCount(null));
    }


    @Test
    void testGetRealRow()
    {
        int[] view =
        {
                5, 3, 1, 4, 2
        };
        DataTableViewInt dtv = DataTableViewInt.builder().view(view).build();

        assertEquals(5, dtv.getRealRow(null, 0));
        assertEquals(3, dtv.getRealRow(null, 1));
        assertEquals(1, dtv.getRealRow(null, 2));
        assertEquals(4, dtv.getRealRow(null, 3));
        assertEquals(2, dtv.getRealRow(null, 4));
    }


    @Test
    void testGetRealRowNegativeIndex()
    {
        int[] view =
        {
                5, 3, 1
        };
        DataTableViewInt dtv = DataTableViewInt.builder().view(view).build();

        assertThrows(IndexOutOfBoundsException.class, () -> dtv.getRealRow(null, -1));
    }


    @Test
    void testGetRealRowIndexTooLarge()
    {
        int[] view =
        {
                5, 3, 1
        };
        DataTableViewInt dtv = DataTableViewInt.builder().view(view).build();

        assertThrows(IndexOutOfBoundsException.class, () -> dtv.getRealRow(null, 3));
        assertThrows(IndexOutOfBoundsException.class, () -> dtv.getRealRow(null, 100));
    }


    @Test
    void testGetRealRowEmptyView()
    {
        int[] view = {};
        DataTableViewInt dtv = DataTableViewInt.builder().view(view).build();

        assertThrows(IndexOutOfBoundsException.class, () -> dtv.getRealRow(null, 0));
    }


    @Test
    void testLargeViewArray()
    {
        int size = 10000;
        int[] view = new int[size];
        for (int i = 0; i < size; i++)
        {
            view[i] = size - i - 1; // reverse order
        }

        DataTableViewInt dtv = DataTableViewInt.builder().view(view).build();

        assertEquals(size, dtv.getRowCount(null));
        assertEquals(size - 1, dtv.getRealRow(null, 0));
        assertEquals(0, dtv.getRealRow(null, size - 1));
    }


    @Test
    void testBoundaryRowAccess()
    {
        int[] view =
        {
                10, 20, 30
        };
        DataTableViewInt dtv = DataTableViewInt.builder().view(view).build();

        // First valid index
        assertEquals(10, dtv.getRealRow(null, 0));
        // Last valid index
        assertEquals(30, dtv.getRealRow(null, 2));
        // First invalid index
        assertThrows(IndexOutOfBoundsException.class, () -> dtv.getRealRow(null, 3));
    }


    @Test
    void testGetDisplayRow()
    {
        int[] view =
        {
                5, 3, 1, 4, 2
        };
        DataTableViewInt dtv = DataTableViewInt.builder().view(view).build();

        assertEquals(0, dtv.getDisplayRow(null, 5));
        assertEquals(1, dtv.getDisplayRow(null, 3));
        assertEquals(2, dtv.getDisplayRow(null, 1));
        assertEquals(3, dtv.getDisplayRow(null, 4));
        assertEquals(4, dtv.getDisplayRow(null, 2));
    }


    @Test
    void testGetDisplayRowNotFound()
    {
        int[] view =
        {
                5, 3, 1
        };
        DataTableViewInt dtv = DataTableViewInt.builder().view(view).build();

        assertEquals(-1, dtv.getDisplayRow(null, 0));
        assertEquals(-1, dtv.getDisplayRow(null, 99));
    }


    @Test
    void testGetDisplayRowEmptyView()
    {
        int[] view = {};
        DataTableViewInt dtv = DataTableViewInt.builder().view(view).build();

        assertEquals(-1, dtv.getDisplayRow(null, 0));
    }
}
