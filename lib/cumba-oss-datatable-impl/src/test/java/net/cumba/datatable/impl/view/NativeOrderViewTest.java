package net.cumba.datatable.impl.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.CachedDataTableColumn;
import net.cumba.datatable.impl.ColumnCachedDataTable;
import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.Test;

class NativeOrderViewTest
{

    private IDataTable createTestTable(int rowCount)
    {
        CachedDataTableColumn col = new CachedDataTableColumn(0, DataValueType.LONG);
        for (int i = 0; i < rowCount; i++)
        {
            col.addElement(i);
        }
        col.complete();

        DataTableColumnMeta colMeta = DataTableColumnMeta.builder().index(0).name("col1")
                .type(DataValueType.LONG).build();

        DataTableMeta meta = DataTableMeta.builder().name("TestTable").setColumns(colMeta)
                .rowCount(rowCount).totalRowCount(rowCount).build();

        return new ColumnCachedDataTable(meta, col);
    }


    @Test
    void testGetRowCount()
    {
        IDataTable table = createTestTable(5);
        NativeOrderView view = NativeOrderView.INSTANCE;

        assertEquals(5, view.getRowCount(table));
    }


    @Test
    void testGetRowCountEmpty()
    {
        IDataTable table = createTestTable(0);
        NativeOrderView view = NativeOrderView.INSTANCE;

        assertEquals(0, view.getRowCount(table));
    }


    @Test
    void testGetRealRowReturnsIdentity()
    {
        IDataTable table = createTestTable(5);
        NativeOrderView view = NativeOrderView.INSTANCE;

        // NativeOrderView should return the same index (identity mapping)
        for (int i = 0; i < 5; i++)
        {
            assertEquals(i, view.getRealRow(table, i));
        }
    }


    @Test
    void testGetRealRowNegativeIndex()
    {
        IDataTable table = createTestTable(5);
        NativeOrderView view = NativeOrderView.INSTANCE;

        assertThrows(IndexOutOfBoundsException.class, () -> view.getRealRow(table, -1));
    }


    @Test
    void testGetRealRowIndexTooLarge()
    {
        IDataTable table = createTestTable(5);
        NativeOrderView view = NativeOrderView.INSTANCE;

        assertThrows(IndexOutOfBoundsException.class, () -> view.getRealRow(table, 5));
        assertThrows(IndexOutOfBoundsException.class, () -> view.getRealRow(table, 100));
    }


    @Test
    void testGetRealRowEmptyTable()
    {
        IDataTable table = createTestTable(0);
        NativeOrderView view = NativeOrderView.INSTANCE;

        assertThrows(IndexOutOfBoundsException.class, () -> view.getRealRow(table, 0));
    }


    @Test
    void testBoundaryRowAccess()
    {
        IDataTable table = createTestTable(3);
        NativeOrderView view = NativeOrderView.INSTANCE;

        // First valid index
        assertEquals(0, view.getRealRow(table, 0));
        // Last valid index
        assertEquals(2, view.getRealRow(table, 2));
        // First invalid index
        assertThrows(IndexOutOfBoundsException.class, () -> view.getRealRow(table, 3));
    }


    @Test
    void testNullTableGetRowCountReturnsZero()
    {
        NativeOrderView view = NativeOrderView.INSTANCE;

        assertEquals(0, view.getRowCount(null));
    }


    @Test
    void testNullTableGetRealRowPassesThrough()
    {
        NativeOrderView view = NativeOrderView.INSTANCE;

        assertEquals(0, view.getRealRow(null, 0));
        assertEquals(9, view.getRealRow(null, 9));
    }


    @Test
    void testSingleInstanceReusableAcrossTables()
    {
        IDataTable table3 = createTestTable(3);
        IDataTable table7 = createTestTable(7);
        NativeOrderView view = NativeOrderView.INSTANCE;

        assertEquals(3, view.getRowCount(table3));
        assertEquals(7, view.getRowCount(table7));

        assertEquals(2, view.getRealRow(table3, 2));
        assertEquals(6, view.getRealRow(table7, 6));

        assertThrows(IndexOutOfBoundsException.class, () -> view.getRealRow(table3, 3));
        assertThrows(IndexOutOfBoundsException.class, () -> view.getRealRow(table7, 7));
    }


    @Test
    void testGetDisplayRowReturnsIdentity()
    {
        IDataTable table = createTestTable(5);
        NativeOrderView view = NativeOrderView.INSTANCE;

        for (int i = 0; i < 5; i++)
        {
            assertEquals(i, view.getDisplayRow(table, i));
        }
    }


    @Test
    void testGetDisplayRowOutOfRange()
    {
        IDataTable table = createTestTable(3);
        NativeOrderView view = NativeOrderView.INSTANCE;

        assertEquals(-1, view.getDisplayRow(table, -1));
        assertEquals(-1, view.getDisplayRow(table, 3));
        assertEquals(-1, view.getDisplayRow(table, 99));
    }


    @Test
    void testGetDisplayRowEmptyTable()
    {
        IDataTable table = createTestTable(0);
        NativeOrderView view = NativeOrderView.INSTANCE;

        assertEquals(-1, view.getDisplayRow(table, 0));
    }

}
