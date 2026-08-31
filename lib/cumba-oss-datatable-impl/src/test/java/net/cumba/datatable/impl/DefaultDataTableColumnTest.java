package net.cumba.datatable.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.DefaultDataTableColumn;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.IDataValue;
import org.junit.jupiter.api.Test;

class DefaultDataTableColumnTest
{

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


    @Test
    void testGetRowCount()
    {
        ColumnCachedDataTable table = createTestTable();
        DefaultDataTableColumn column = new DefaultDataTableColumn(table, 0);
        assertEquals(3, column.getRowCount());
    }


    @Test
    void testGetValue()
    {
        ColumnCachedDataTable table = createTestTable();
        DefaultDataTableColumn col0 = new DefaultDataTableColumn(table, 0);
        assertEquals("a", col0.getValue(0));
        assertEquals("b", col0.getValue(1));
        assertEquals("c", col0.getValue(2));

        DefaultDataTableColumn col1 = new DefaultDataTableColumn(table, 1);
        assertEquals(1.0, col1.getValue(0));
        assertEquals(2.0, col1.getValue(1));
        assertEquals(3.0, col1.getValue(2));
    }


    @Test
    void testGetDataValue()
    {
        ColumnCachedDataTable table = createTestTable();
        DefaultDataTableColumn col0 = new DefaultDataTableColumn(table, 0);
        IDataValue dv = col0.getDataValue(0);
        assertNotNull(dv);
        assertEquals("a", dv.getValue());

        DefaultDataTableColumn col1 = new DefaultDataTableColumn(table, 1);
        IDataValue dv1 = col1.getDataValue(1);
        assertNotNull(dv1);
        assertEquals(2.0, dv1.getValue());
    }


    @Test
    void testGetIndex()
    {
        ColumnCachedDataTable table = createTestTable();
        DefaultDataTableColumn col0 = new DefaultDataTableColumn(table, 0);
        assertEquals(0, col0.getIndex());

        DefaultDataTableColumn col1 = new DefaultDataTableColumn(table, 1);
        assertEquals(1, col1.getIndex());
    }


    @Test
    void testGetValues()
    {
        ColumnCachedDataTable table = createTestTable();
        DefaultDataTableColumn col0 = new DefaultDataTableColumn(table, 0);
        List<Object> values = col0.getValues().toList();
        assertEquals(3, values.size());
        assertEquals("a", values.get(0));
        assertEquals("b", values.get(1));
        assertEquals("c", values.get(2));
    }


    @Test
    void testGetValuesRange()
    {
        ColumnCachedDataTable table = createTestTable();
        DefaultDataTableColumn col0 = new DefaultDataTableColumn(table, 0);
        List<Object> values = col0.getValues(1, 3).toList();
        assertEquals(2, values.size());
        assertEquals("b", values.get(0));
        assertEquals("c", values.get(1));
    }


    @Test
    void testGetValuesNegativeEnd()
    {
        ColumnCachedDataTable table = createTestTable();
        DefaultDataTableColumn col0 = new DefaultDataTableColumn(table, 0);
        List<Object> values = col0.getValues(1, -1).toList();
        assertEquals(2, values.size());
        assertEquals("b", values.get(0));
        assertEquals("c", values.get(1));
    }


    @Test
    void testGetDataValues()
    {
        ColumnCachedDataTable table = createTestTable();
        DefaultDataTableColumn col1 = new DefaultDataTableColumn(table, 1);
        List<IDataValue> dataValues = col1.getDataValues().toList();
        assertEquals(3, dataValues.size());
        assertEquals(1.0, dataValues.get(0).getValue());
        assertEquals(2.0, dataValues.get(1).getValue());
        assertEquals(3.0, dataValues.get(2).getValue());
    }


    @Test
    void testGetDataValuesRange()
    {
        ColumnCachedDataTable table = createTestTable();
        DefaultDataTableColumn col1 = new DefaultDataTableColumn(table, 1);
        List<IDataValue> dataValues = col1.getDataValues(0, 2).toList();
        assertEquals(2, dataValues.size());
        assertEquals(1.0, dataValues.get(0).getValue());
        assertEquals(2.0, dataValues.get(1).getValue());
    }
}
