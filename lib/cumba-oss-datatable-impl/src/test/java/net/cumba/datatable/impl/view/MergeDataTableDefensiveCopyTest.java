package net.cumba.datatable.impl.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.CachedDataTableColumn;
import net.cumba.datatable.impl.ColumnCachedDataTable;
import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.Test;

/**
 * Tests that MergeDataTable defensively copies its tables array.
 */
class MergeDataTableDefensiveCopyTest
{

    private IDataTable createTable(String name, String colName)
    {
        CachedDataTableColumn col = new CachedDataTableColumn(0, DataValueType.STRING);
        col.addElement("A");
        col.complete();

        DataTableColumnMeta cm = DataTableColumnMeta.builder().index(0).name(colName)
                .type(DataValueType.STRING).build();

        DataTableMeta meta = DataTableMeta.builder().name(name).setColumns(cm).rowCount(1)
                .totalRowCount(1).build();

        return new ColumnCachedDataTable(meta, col);
    }


    @Test
    void testMutatingInputArrayDoesNotAffectMergedTable()
    {
        IDataTable t1 = createTable("T1", "COL_A");
        IDataTable t2 = createTable("T2", "COL_B");

        IDataTable[] tablesArray =
        {
                t1, t2
        };
        MergeDataTable merged = new MergeDataTable(tablesArray);

        // Verify before mutation
        assertEquals(2, merged.getColumnCount());
        assertEquals("A", merged.getValue(0, 0));
        assertEquals("A", merged.getValue(0, 1));

        // Mutate the input array
        tablesArray[0] = null;
        tablesArray[1] = null;

        // MergeDataTable should still work correctly
        assertEquals(2, merged.getColumnCount());
        assertEquals("A", merged.getValue(0, 0));
        assertEquals("A", merged.getValue(0, 1));
    }
}
