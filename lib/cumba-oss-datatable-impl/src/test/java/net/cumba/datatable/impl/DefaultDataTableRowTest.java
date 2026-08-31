package net.cumba.datatable.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.DefaultDataTableRow;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.IDataValue;
import org.junit.jupiter.api.Test;

class DefaultDataTableRowTest
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
    void testGetIndex()
    {
        ColumnCachedDataTable table = createTestTable();
        DefaultDataTableRow row = DefaultDataTableRow.builder().table(table).index(1).build();

        assertEquals(1, row.getIndex());
    }


    @Test
    void testGetColumnCount()
    {
        ColumnCachedDataTable table = createTestTable();
        DefaultDataTableRow row = DefaultDataTableRow.builder().table(table).index(0).build();

        assertEquals(2, row.getColumnCount());
    }


    @Test
    void testGetValueByIndex()
    {
        ColumnCachedDataTable table = createTestTable();
        DefaultDataTableRow row = DefaultDataTableRow.builder().table(table).index(1).build();

        assertEquals("b", row.getValue(0));
        assertEquals(2.0, row.getValue(1));
    }


    @Test
    void testGetValueByName()
    {
        ColumnCachedDataTable table = createTestTable();
        DefaultDataTableRow row = DefaultDataTableRow.builder().table(table).index(2).build();

        assertEquals("c", row.getValue("col0"));
        assertEquals(3.0, row.getValue("col1"));
    }


    @Test
    void testGetDataValueByIndex()
    {
        ColumnCachedDataTable table = createTestTable();
        DefaultDataTableRow row = DefaultDataTableRow.builder().table(table).index(0).build();

        IDataValue dv0 = row.getDataValue(0);
        IDataValue dv1 = row.getDataValue(1);

        assertNotNull(dv0);
        assertNotNull(dv1);
        assertEquals("a", dv0.getValue());
        assertEquals(1.0, dv1.getValue());
    }


    @Test
    void testGetDataValueByName()
    {
        ColumnCachedDataTable table = createTestTable();
        DefaultDataTableRow row = DefaultDataTableRow.builder().table(table).index(0).build();

        IDataValue dv0 = row.getDataValue("col0");
        IDataValue dv1 = row.getDataValue("col1");

        assertNotNull(dv0);
        assertNotNull(dv1);
        assertEquals("a", dv0.getValue());
        assertEquals(1.0, dv1.getValue());
    }


    @Test
    void testGetValuesForColumns()
    {
        ColumnCachedDataTable table = createTestTable();
        DefaultDataTableRow row = DefaultDataTableRow.builder().table(table).index(0).build();

        List<?> values = row.getValues(0, 1).toList();

        assertEquals(2, values.size());
        assertEquals("a", values.get(0));
        assertEquals(1.0, values.get(1));
    }


    @Test
    void testGetValuesForColumnNames()
    {
        ColumnCachedDataTable table = createTestTable();
        DefaultDataTableRow row = DefaultDataTableRow.builder().table(table).index(2).build();

        List<?> values = row.getValues("col0", "col1").toList();

        assertEquals(2, values.size());
        assertEquals("c", values.get(0));
        assertEquals(3.0, values.get(1));
    }


    @Test
    void testGetDataValuesForColumns()
    {
        ColumnCachedDataTable table = createTestTable();
        DefaultDataTableRow row = DefaultDataTableRow.builder().table(table).index(0).build();

        List<IDataValue> dataValues = row.getDataValues(0, 1).toList();

        assertEquals(2, dataValues.size());
        assertEquals("a", dataValues.get(0).getValue());
        assertEquals(1.0, dataValues.get(1).getValue());
    }


    @Test
    void testGetDataValuesForColumnNames()
    {
        ColumnCachedDataTable table = createTestTable();
        DefaultDataTableRow row = DefaultDataTableRow.builder().table(table).index(2).build();

        List<IDataValue> dataValues = row.getDataValues("col0", "col1").toList();

        assertEquals(2, dataValues.size());
        assertEquals("c", dataValues.get(0).getValue());
        assertEquals(3.0, dataValues.get(1).getValue());
    }


    @Test
    void testEquals()
    {
        ColumnCachedDataTable table = createTestTable();
        DefaultDataTableRow row1 = DefaultDataTableRow.builder().table(table).index(1).build();
        DefaultDataTableRow row2 = DefaultDataTableRow.builder().table(table).index(1).build();
        DefaultDataTableRow row3 = DefaultDataTableRow.builder().table(table).index(2).build();

        assertEquals(row1, row2);
        assertNotEquals(row1, row3);
        assertEquals(row1.hashCode(), row2.hashCode());
    }


    @Test
    void testBuilder()
    {
        ColumnCachedDataTable table = createTestTable();
        DefaultDataTableRow row = DefaultDataTableRow.builder().table(table).index(0).build();

        assertNotNull(row);
        assertEquals(table, row.getTable());
        assertEquals(0, row.getIndex());
    }
}
