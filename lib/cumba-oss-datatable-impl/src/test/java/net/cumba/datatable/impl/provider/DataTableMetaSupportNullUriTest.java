package net.cumba.datatable.impl.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import org.junit.jupiter.api.Test;

/**
 * Tests for DataTableMetaSupport null-safety in getTableNameFor and setTable.
 */
class DataTableMetaSupportNullUriTest
{

    @Test
    void testSetTableWithNullUri()
    {
        DataTableMetaSupport support = new DataTableMetaSupport(null);
        support.setTable(null, "TestName");
        // Should not NPE; should use provided name
        var builder = support.getTableMeta();
        assertNotNull(builder);
    }


    @Test
    void testSetTableWithNullUriAndNullName()
    {
        DataTableMetaSupport support = new DataTableMetaSupport(null);
        // When both URI and name are null, getTableNameFor should return ""
        support.setTable(null, null);
        var builder = support.getTableMeta();
        assertNotNull(builder);
    }


    @Test
    void testSetTableWithFragmentUri()
    {
        DataTableMetaSupport support = new DataTableMetaSupport(null);
        URI uri = URI.create("file:///path/to/file.xpt#DATASET1");
        support.setTable(uri, null);
        var builder = support.getTableMeta();
        assertNotNull(builder);
    }


    @Test
    void testSetTableWithOpaqueUri()
    {
        DataTableMetaSupport support = new DataTableMetaSupport(null);
        URI uri = URI.create("file:///test.csv");
        support.setTable(uri, null);
        var builder = support.getTableMeta();
        assertNotNull(builder);
    }


    @Test
    void testSetTablePreservesProvidedName()
    {
        DataTableMetaSupport support = new DataTableMetaSupport(null);
        support.setTable(URI.create("file:///test.csv"), "MYNAME");
        support.addColumn("COL1", net.cumba.datatable.values.DataValueType.STRING);
        var meta = support.getTableMeta().build();
        assertEquals("MYNAME", meta.getName());
    }


    @Test
    void testGetTableColumnCount()
    {
        DataTableMetaSupport support = new DataTableMetaSupport(null);
        support.setTable(URI.create("file:///test.csv"), "T");
        assertEquals(0, support.getTableColumnCount());
        support.addColumn("A", net.cumba.datatable.values.DataValueType.STRING);
        assertEquals(1, support.getTableColumnCount());
        support.addColumn("B", net.cumba.datatable.values.DataValueType.DOUBLE);
        assertEquals(2, support.getTableColumnCount());
    }


    @Test
    void testDuplicateColumnNameThrows()
    {
        DataTableMetaSupport support = new DataTableMetaSupport(null);
        support.setTable(URI.create("file:///test.csv"), "T");
        support.addColumn("COL1", net.cumba.datatable.values.DataValueType.STRING);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> support.addColumn("COL1", net.cumba.datatable.values.DataValueType.STRING));
    }


    @Test
    void testDuplicateColumnNameCaseInsensitive()
    {
        DataTableMetaSupport support = new DataTableMetaSupport(null, false);
        support.setTable(URI.create("file:///test.csv"), "T");
        support.addColumn("Col1", net.cumba.datatable.values.DataValueType.STRING);

        // Case-insensitive mode: "col1" should clash with "Col1"
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> support.addColumn("col1", net.cumba.datatable.values.DataValueType.STRING));
    }


    @Test
    void testDuplicateColumnNameCaseSensitive()
    {
        DataTableMetaSupport support = new DataTableMetaSupport(null, true);
        support.setTable(URI.create("file:///test.csv"), "T");
        support.addColumn("Col1", net.cumba.datatable.values.DataValueType.STRING);

        // Case-sensitive mode: "col1" should NOT clash with "Col1"
        support.addColumn("col1", net.cumba.datatable.values.DataValueType.STRING);
        assertEquals(2, support.getTableColumnCount());
    }


    @Test
    void testApplyColumnsWithoutTableThrows()
    {
        DataTableMetaSupport support = new DataTableMetaSupport(null);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                support::applyColumns);
    }


    @Test
    void testSetTableTwiceThrows()
    {
        DataTableMetaSupport support = new DataTableMetaSupport(null);
        support.setTable(URI.create("file:///test.csv"), "T");

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> support.setTable(URI.create("file:///test2.csv"), "T2"));
    }
}
