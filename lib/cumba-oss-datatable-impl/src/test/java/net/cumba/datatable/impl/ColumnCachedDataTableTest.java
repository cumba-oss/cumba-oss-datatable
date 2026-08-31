package net.cumba.datatable.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTableColumn;
import net.cumba.datatable.IDataTableRow;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.IDataValue;
import org.junit.jupiter.api.Test;

class ColumnCachedDataTableTest
{
    // ---------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------

    private ColumnCachedDataTable createTestTable()
    {
        DataTableColumnMeta cm0 = DataTableColumnMeta.builder().index(0).name("col0")
                .type(DataValueType.STRING).build();
        DataTableColumnMeta cm1 = DataTableColumnMeta.builder().index(1).name("col1")
                .type(DataValueType.DOUBLE).build();
        DataTableMeta meta = DataTableMeta.builder().name("test").setColumns(cm0, cm1).rowCount(3)
                .totalRowCount(3).build();

        CachedDataTableColumn c0 = new CachedDataTableColumn(0, DataValueType.STRING);
        c0.addElement("a");
        c0.addElement("b");
        c0.addElement("c");

        CachedDataTableColumn c1 = new CachedDataTableColumn(1, DataValueType.DOUBLE);
        c1.addElement(1.0);
        c1.addElement(2.0);
        c1.addElement(3.0);

        return new ColumnCachedDataTable(meta, c0, c1);
    }


    private ColumnCachedDataTable createEmptyTable()
    {
        DataTableColumnMeta cm0 = DataTableColumnMeta.builder().index(0).name("col0")
                .type(DataValueType.STRING).build();
        DataTableMeta meta = DataTableMeta.builder().name("empty").setColumns(cm0).rowCount(0)
                .build();

        CachedDataTableColumn c0 = new CachedDataTableColumn(0, DataValueType.STRING);

        return new ColumnCachedDataTable(meta, c0);
    }

    // ---------------------------------------------------------------
    // ColumnCachedDataTable tests
    // ---------------------------------------------------------------


    @Test
    void testGetValue()
    {
        ColumnCachedDataTable table = createTestTable();

        assertEquals("a", table.getValue(0, 0));
        assertEquals("b", table.getValue(1, 0));
        assertEquals("c", table.getValue(2, 0));

        assertEquals(1.0, table.getValue(0, 1));
        assertEquals(2.0, table.getValue(1, 1));
        assertEquals(3.0, table.getValue(2, 1));
    }


    @Test
    void testGetDataValue()
    {
        ColumnCachedDataTable table = createTestTable();

        IDataValue dv0 = table.getDataValue(0, 0);
        assertNotNull(dv0);
        assertEquals("a", dv0.getValue());

        IDataValue dv1 = table.getDataValue(1, 1);
        assertNotNull(dv1);
        assertEquals(2.0, dv1.getValue());
    }


    @Test
    void testGetColumn()
    {
        ColumnCachedDataTable table = createTestTable();

        IDataTableColumn col0 = table.getColumn(0);
        assertNotNull(col0);
        assertEquals("a", col0.getValue(0));

        IDataTableColumn col1 = table.getColumn(1);
        assertNotNull(col1);
        assertEquals(1.0, col1.getValue(0));
    }


    @Test
    void testGetColumnOutOfBounds()
    {
        ColumnCachedDataTable table = createTestTable();

        assertThrows(IndexOutOfBoundsException.class, () -> table.getColumn(2));
        assertThrows(IndexOutOfBoundsException.class, () -> table.getColumn(100));
    }


    @Test
    void testGetColumnNegativeIndex()
    {
        ColumnCachedDataTable table = createTestTable();

        // Should throw IndexOutOfBoundsException (actually throws
        // ArrayIndexOutOfBoundsException due to missing negative check)
        assertThrows(IndexOutOfBoundsException.class, () -> table.getColumn(-1));
    }


    @Test
    void testGetValueRowOutOfBounds()
    {
        ColumnCachedDataTable table = createTestTable();

        assertThrows(IndexOutOfBoundsException.class, () -> table.getValue(3, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> table.getValue(100, 0));
    }


    @Test
    void testGetValueNegativeRow()
    {
        ColumnCachedDataTable table = createTestTable();

        // Negative row is not caught by the table-level check but is eventually
        // caught by the column-level ensureValidRow, so an exception is still thrown.
        assertThrows(IndexOutOfBoundsException.class, () -> table.getValue(-1, 0));
    }


    @Test
    void testGetRowCount()
    {
        // When rowCount is set explicitly via meta, it should be returned
        ColumnCachedDataTable table = createTestTable();
        assertEquals(3, table.getRowCount());
    }


    @Test
    void testGetRowCountFromColumns()
    {
        // When rowCount is not explicitly set (defaults to -1), the table should
        // derive the row count from the first column.
        DataTableColumnMeta cm0 = DataTableColumnMeta.builder().index(0).name("col0")
                .type(DataValueType.STRING).build();
        DataTableMeta meta = DataTableMeta.builder().name("test").setColumns(cm0).build();

        CachedDataTableColumn c0 = new CachedDataTableColumn(0, DataValueType.STRING);
        c0.addElement("x");
        c0.addElement("y");

        ColumnCachedDataTable table = new ColumnCachedDataTable(meta, c0);
        assertEquals(2, table.getRowCount());
    }


    @Test
    void testGetRowCountNoColumns()
    {
        DataTableMeta meta = DataTableMeta.builder().name("empty")//
                .setColumns(DataTableColumnMeta.builder().name("col1").type(DataValueType.STRING)
                        .build())
                .build();

        ColumnCachedDataTable table = new ColumnCachedDataTable(meta);
        assertEquals(0, table.getRowCount());
    }


    @Test
    void testGetMetaData()
    {
        ColumnCachedDataTable table = createTestTable();

        DataTableMeta meta = table.getMetaData();
        assertNotNull(meta);
        assertEquals("test", meta.getName());
    }


    @Test
    void testGetRow()
    {
        ColumnCachedDataTable table = createTestTable();

        IDataTableRow row = table.getRow(0);
        assertNotNull(row);
        assertEquals("a", row.getValue(0));
        assertEquals(1.0, row.getValue(1));

        IDataTableRow row2 = table.getRow(2);
        assertNotNull(row2);
        assertEquals("c", row2.getValue(0));
        assertEquals(3.0, row2.getValue(1));
    }


    @Test
    void testGetRowOutOfBounds()
    {
        ColumnCachedDataTable table = createTestTable();

        assertThrows(IndexOutOfBoundsException.class, () -> table.getRow(3));
        assertThrows(IndexOutOfBoundsException.class, () -> table.getRow(100));
    }


    @Test
    void testGetDataValueNegativeRow()
    {
        ColumnCachedDataTable table = createTestTable();

        assertThrows(IndexOutOfBoundsException.class, () -> table.getDataValue(-1, 0));
    }


    @Test
    void testGetDataValueOutOfBoundsRow()
    {
        ColumnCachedDataTable table = createTestTable();

        assertThrows(IndexOutOfBoundsException.class, () -> table.getDataValue(3, 0));
    }


    @Test
    void testEmptyTable()
    {
        ColumnCachedDataTable table = createEmptyTable();

        assertEquals(0, table.getRowCount());
        assertNotNull(table.getMetaData());
        assertNotNull(table.getColumn(0));
    }

    // ---------------------------------------------------------------
    // CachedDataTableColumn tests
    // ---------------------------------------------------------------


    @Test
    void testCachedColumnAddElement()
    {
        CachedDataTableColumn col = new CachedDataTableColumn(0, DataValueType.STRING);
        col.addElement("hello");
        col.addElement("world");

        assertEquals(2, col.getRowCount());
        assertEquals("hello", col.getValue(0));
        assertEquals("world", col.getValue(1));
    }


    @Test
    void testCachedColumnSetElement()
    {
        CachedDataTableColumn col = new CachedDataTableColumn(0, DataValueType.STRING);
        col.addElement("original");
        col.addElement("other");

        col.setElement(0, "replaced");
        assertEquals("replaced", col.getValue(0));
        assertEquals("other", col.getValue(1));
    }


    @Test
    void testCachedColumnRowCount()
    {
        CachedDataTableColumn col = new CachedDataTableColumn(0, DataValueType.DOUBLE);
        assertEquals(0, col.getRowCount());

        col.addElement(1.0);
        assertEquals(1, col.getRowCount());

        col.addElement(2.0);
        col.addElement(3.0);
        assertEquals(3, col.getRowCount());
    }


    @Test
    void testCachedColumnComplete()
    {
        CachedDataTableColumn col = new CachedDataTableColumn(0, DataValueType.DOUBLE);
        col.addElement(10.0);
        col.addElement(20.0);

        col.complete();

        // After complete the column should still function correctly
        assertEquals(2, col.getRowCount());
        assertEquals(10.0, col.getValue(0));
        assertEquals(20.0, col.getValue(1));
    }


    @Test
    void testCachedColumnEnsureValidRow()
    {
        CachedDataTableColumn col = new CachedDataTableColumn(0, DataValueType.STRING);
        col.addElement("only");

        // Valid row
        assertDoesNotThrow(() -> col.getValue(0));

        // Out of bounds: too large
        assertThrows(IndexOutOfBoundsException.class, () -> col.getValue(1));

        // Out of bounds: negative
        assertThrows(IndexOutOfBoundsException.class, () -> col.getValue(-1));
    }

    // ---------------------------------------------------------------
    // withMetadata tests
    // ---------------------------------------------------------------


    @Test
    void testHasUnmappedFormatValuesDefaultFalse()
    {
        ColumnCachedDataTable orig = createTestTable();
        // Without scanner annotation, the flag must default to false on every column.
        for (int i = 0; i < orig.getMetaData().getColumnCount(); i++)
        {
            assertTrue(!orig.getMetaData().getColumn(i).hasUnmappedFormatValues());
        }
    }
}
