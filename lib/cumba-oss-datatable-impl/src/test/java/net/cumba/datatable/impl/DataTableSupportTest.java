package net.cumba.datatable.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.Test;

class DataTableSupportTest
{

    private ColumnCachedDataTable createTable(String... col0Values)
    {
        DataTableColumnMeta cm = DataTableColumnMeta.builder().index(0).name("NAME")
                .type(DataValueType.STRING).build();
        DataTableMeta meta = DataTableMeta.builder().name("test").setColumns(cm)
                .rowCount(col0Values.length).totalRowCount(col0Values.length).build();
        CachedDataTableColumn col = new CachedDataTableColumn(0, DataValueType.STRING);
        for (String v : col0Values)
        {
            col.addElement(v);
        }
        return new ColumnCachedDataTable(meta, col);
    }


    private ColumnCachedDataTable createTwoColumnTable()
    {
        DataTableColumnMeta cm0 = DataTableColumnMeta.builder().index(0).name("GROUP")
                .type(DataValueType.STRING).build();
        DataTableColumnMeta cm1 = DataTableColumnMeta.builder().index(1).name("VALUE")
                .type(DataValueType.DOUBLE).build();
        DataTableMeta meta = DataTableMeta.builder().name("test").setColumns(cm0, cm1).rowCount(5)
                .totalRowCount(5).build();
        CachedDataTableColumn c0 = new CachedDataTableColumn(0, DataValueType.STRING);
        c0.addElement("A");
        c0.addElement("B");
        c0.addElement("A");
        c0.addElement("B");
        c0.addElement("A");
        CachedDataTableColumn c1 = new CachedDataTableColumn(1, DataValueType.DOUBLE);
        c1.addElement(1.0);
        c1.addElement(2.0);
        c1.addElement(3.0);
        c1.addElement(4.0);
        c1.addElement(5.0);
        return new ColumnCachedDataTable(meta, c0, c1);
    }

    // --- isDataEqual ---


    @Test
    void isDataEqual_sameTables_returnsTrue()
    {
        ColumnCachedDataTable table = createTwoColumnTable();
        DataTableSupport support = new DataTableSupport();
        assertTrue(support.isDataEqual(table, table));
    }


    @Test
    void isDataEqual_identicalData_returnsTrue()
    {
        ColumnCachedDataTable t1 = createTwoColumnTable();
        ColumnCachedDataTable t2 = createTwoColumnTable();
        DataTableSupport support = new DataTableSupport();
        assertTrue(support.isDataEqual(t1, t2));
    }


    @Test
    void isDataEqual_differentRowCount_returnsFalse()
    {
        ColumnCachedDataTable t1 = createTable("A", "B");
        ColumnCachedDataTable t2 = createTable("A", "B", "C");
        DataTableSupport support = new DataTableSupport();
        assertFalse(support.isDataEqual(t1, t2));
    }


    @Test
    void isDataEqual_differentColumnCount_returnsFalse()
    {
        ColumnCachedDataTable t1 = createTable("A");
        ColumnCachedDataTable t2 = createTwoColumnTable();
        DataTableSupport support = new DataTableSupport();
        assertFalse(support.isDataEqual(t1, t2));
    }


    @Test
    void isDataEqual_differentValues_returnsFalse()
    {
        ColumnCachedDataTable t1 = createTable("A", "B");
        ColumnCachedDataTable t2 = createTable("A", "C");
        DataTableSupport support = new DataTableSupport();
        assertFalse(support.isDataEqual(t1, t2));
    }


    @Test
    void isDataEqual_nullFirst_returnsFalse()
    {
        DataTableSupport support = new DataTableSupport();
        assertFalse(support.isDataEqual(null, createTable("A")));
    }


    @Test
    void isDataEqual_nullSecond_returnsFalse()
    {
        DataTableSupport support = new DataTableSupport();
        assertFalse(support.isDataEqual(createTable("A"), null));
    }


    @Test
    void isDataEqual_bothNull_returnsTrue()
    {
        DataTableSupport support = new DataTableSupport();
        assertTrue(support.isDataEqual(null, null));
    }

}
