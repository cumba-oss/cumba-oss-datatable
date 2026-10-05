package net.cumba.datatable.impl.view;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.CachedDataTableColumn;
import net.cumba.datatable.impl.ColumnCachedDataTable;
import net.cumba.datatable.impl.databuffer.DataBufferFactory;
import net.cumba.datatable.impl.databuffer.IDataBufferNumeric;
import net.cumba.datatable.values.DataValueString;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.IDataValue;
import net.cumba.datatable.values.MissingValue;
import org.junit.jupiter.api.Test;

/**
 * The reduced {@code ViewDataTable} this twin carries: the row map, the column map, the unavailable
 * values, provenance and the supplied marker — the surface the merged evaluation table uses,
 * identical in both twins.
 */
class ViewDataTableTest
{

    private static IDataTable table()
    {
        CachedDataTableColumn id = new CachedDataTableColumn(0, DataValueType.STRING);
        CachedDataTableColumn n = new CachedDataTableColumn(1, DataValueType.DOUBLE);
        for (int i = 0; i < 3; i++)
        {
            id.addElement("R" + i);
            n.addElement((double) i);
        }
        id.complete();
        n.complete();
        DataTableMeta meta = DataTableMeta.builder().name("T").columns(new DataTableColumnMeta[]
        {
                DataTableColumnMeta.builder().index(0).name("ID").type(DataValueType.STRING)
                        .build(),
                DataTableColumnMeta.builder().index(1).name("N").type(DataValueType.DOUBLE).build()
        }).rowCount(3).totalRowCount(3).build();
        return new ColumnCachedDataTable(meta, id, n);
    }


    private static DataTableViewBuffer map(long... aRows)
    {
        IDataBufferNumeric buf = DataBufferFactory.get().createForRange(-1, 2);
        for (long r : aRows)
        {
            buf.addValue(r);
        }
        return new DataTableViewBuffer(buf);
    }


    @Test
    void nativeOrderWithoutAView_everyColumnKept()
    {
        IDataTable base = table();
        ViewDataTable v = new ViewDataTable(base, null, null);
        assertSame(base, v.getTable());
        assertSame(NativeOrderView.INSTANCE, v.getView());
        assertEquals(3, v.getRowCount());
        assertEquals(2, v.getColumnCount());
        assertFalse(v.usesColumnMapping());
        assertNull(v.getBaseViewColumns());
        assertEquals("R1", v.getValue(1, 0));
        assertEquals("R1", v.getDataValue(1, 0).getValueAsString());
        assertEquals(1, v.getBaseColumnIndex(1));
        assertEquals(2, v.getBaseRowIndex(2));
        assertEquals(2, v.getRealRowIndex(2));
        assertEquals(2, v.getDisplayRowIndex(2));
        assertEquals(-1, v.getDisplayRowIndex(5));
        assertTrue(v.isSupplied(0, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> v.getValue(0, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> v.getBaseColumnIndex(-1));
    }


    @Test
    void rowMapAndColumnMap_unmappedCellsReadTheTypeDefaultAndAreNotSupplied()
    {
        IDataTable base = table();
        DataTableColumnMeta n = base.getMetaData().getColumn(1);
        DataTableColumnMeta id = base.getMetaData().getColumn(0);
        ViewDataTable v = new ViewDataTable(base, map(2, -1, 0), Stream.of(n, id));
        assertTrue(v.usesColumnMapping());
        assertEquals(3, v.getRowCount());
        assertEquals("N", v.getMetaData().getColumn(0).getName());
        assertEquals(0, v.getMetaData().getColumn(0).getIndex(), "re-indexed");
        assertEquals(1, v.getBaseColumnIndex(0));
        assertEquals(0, v.getBaseColumnIndex(1));
        assertEquals(2.0, v.getValue(0, 0));
        assertEquals("R2", v.getValue(0, 1));
        assertEquals(2, v.getRealRowIndex(0));
        assertEquals(0, v.getRealRowIndex(2));
        assertEquals(2, v.getDisplayRowIndex(0));
        // the unmapped row: the type default, not supplied
        assertTrue(v.getDataValue(1, 0).isMissingOrInvalid());
        assertEquals("", v.getValue(1, 1));
        assertEquals("", v.getDataValue(1, 1).getValueAsString());
        assertFalse(v.isSupplied(1, 0));
        assertFalse(v.isSupplied(1, 1));
        assertTrue(v.isSupplied(0, 0));
        DataTableColumnMeta[] copy = v.getBaseViewColumns();
        assertArrayEquals(new DataTableColumnMeta[]
        {
                n, id
        }, copy);
        assertNotSame(copy, v.getBaseViewColumns(), "a copy each time");
    }


    @Test
    void unavailableValuesCanBeOverridden()
    {
        IDataTable base = table();
        ViewDataTable v = new ViewDataTable(base, map(-1), null);
        assertEquals(MissingValue.MIS, v.getValueUnavailable());
        assertEquals("", v.getColumnUnavailable(0).getValueAsString());
        assertEquals(MissingValue.MIS, v.getColumnUnavailable(1).getValue());
        v.setValueUnavailable(MissingValue.MIS_A);
        assertEquals(MissingValue.MIS_A, v.getColumnUnavailable(1).getValue());
        assertEquals(MissingValue.MIS_A, v.getValue(0, 1));
        IDataValue na = new DataValueString("N/A");
        v.setColumnUnavailable(0, na);
        assertSame(na, v.getDataValue(0, 0));
        assertEquals("N/A", v.getValue(0, 0));
        v.setValueUnavailable(MissingValue.MIS);
        assertEquals("", v.getValue(0, 0), "recomputed on setValueUnavailable");
        assertThrows(IndexOutOfBoundsException.class, () -> v.getColumnUnavailable(2));
        assertThrows(IndexOutOfBoundsException.class, () -> v.setColumnUnavailable(2, na));
    }


    @Test
    void aColumnMappedToNoBaseColumn_isNotSupplied()
    {
        IDataTable base = table();
        DataTableColumnMeta ghost = DataTableColumnMeta.builder().index(0).name("GHOST")
                .type(DataValueType.STRING).build();
        // a view over a view: the inner view's column 1 is mapped to the outer's column 0
        ViewDataTable inner = new ViewDataTable(base, map(0, -1), null);
        ViewDataTable outer = new ViewDataTable(inner, null,
                Stream.of(inner.getMetaData().getColumn(1), ghost.withIndex(5)));
        assertEquals(0.0, outer.getValue(0, 0));
        assertTrue(outer.isSupplied(0, 0));
        assertFalse(outer.isSupplied(1, 0), "the inner view's marker survives");
        assertThrows(IndexOutOfBoundsException.class, () -> outer.getValue(0, 1),
                "a ghost column maps outside the inner view");
    }
}
