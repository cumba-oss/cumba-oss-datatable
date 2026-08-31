package net.cumba.datatable.impl.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.cumba.datatable.impl.databuffer.DataBufferFactory;
import net.cumba.datatable.impl.databuffer.IDataBufferNumeric;
import org.junit.jupiter.api.Test;

class DataTableViewBufferTest
{

    @Test
    void testGetRowCount()
    {
        IDataBufferNumeric buffer = DataBufferFactory.get().createForRange(0, 1_000_000L);
        buffer.setValue(0, 5);
        buffer.setValue(1, 3);
        buffer.setValue(2, 1);

        DataTableViewBuffer view = DataTableViewBuffer.builder().buffer(buffer).build();

        assertEquals(3, view.getRowCount(null));
    }


    @Test
    void testGetRowCountEmpty()
    {
        IDataBufferNumeric buffer = DataBufferFactory.get().createForRange(0, 1_000_000L);

        DataTableViewBuffer view = DataTableViewBuffer.builder().buffer(buffer).build();

        assertEquals(0, view.getRowCount(null));
    }


    @Test
    void testGetRealRow()
    {
        IDataBufferNumeric buffer = DataBufferFactory.get().createForRange(0, 1_000_000L);
        buffer.setValue(0, 10);
        buffer.setValue(1, 20);
        buffer.setValue(2, 30);

        DataTableViewBuffer view = DataTableViewBuffer.builder().buffer(buffer).build();

        assertEquals(10, view.getRealRow(null, 0));
        assertEquals(20, view.getRealRow(null, 1));
        assertEquals(30, view.getRealRow(null, 2));
    }


    @Test
    void testGetRealRowNegativeIndex()
    {
        IDataBufferNumeric buffer = DataBufferFactory.get().createForRange(0, 1_000_000L);
        buffer.setValue(0, 10);

        DataTableViewBuffer view = DataTableViewBuffer.builder().buffer(buffer).build();

        assertThrows(IndexOutOfBoundsException.class, () -> view.getRealRow(null, -1));
    }


    @Test
    void testGetRealRowIndexTooLarge()
    {
        IDataBufferNumeric buffer = DataBufferFactory.get().createForRange(0, 1_000_000L);
        buffer.setValue(0, 10);
        buffer.setValue(1, 20);

        DataTableViewBuffer view = DataTableViewBuffer.builder().buffer(buffer).build();

        assertThrows(IndexOutOfBoundsException.class, () -> view.getRealRow(null, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> view.getRealRow(null, 100));
    }


    @Test
    void testGetRealRowEmptyBuffer()
    {
        IDataBufferNumeric buffer = DataBufferFactory.get().createForRange(0, 1_000_000L);

        DataTableViewBuffer view = DataTableViewBuffer.builder().buffer(buffer).build();

        assertThrows(IndexOutOfBoundsException.class, () -> view.getRealRow(null, 0));
    }


    @Test
    void testGetBuffer()
    {
        IDataBufferNumeric buffer = DataBufferFactory.get().createForRange(0, 1_000_000L);
        buffer.setValue(0, 42);

        DataTableViewBuffer view = DataTableViewBuffer.builder().buffer(buffer).build();

        assertEquals(buffer, view.getBuffer());
    }


    @Test
    void testLargeBuffer()
    {
        IDataBufferNumeric buffer = DataBufferFactory.get().createForRange(0, 1_000_000L);
        int size = 1000;
        for (int i = 0; i < size; i++)
        {
            buffer.setValue(i, size - i - 1); // reverse order
        }

        DataTableViewBuffer view = DataTableViewBuffer.builder().buffer(buffer).build();

        assertEquals(size, view.getRowCount(null));
        assertEquals(size - 1, view.getRealRow(null, 0));
        assertEquals(0, view.getRealRow(null, size - 1));
    }


    @Test
    void testBoundaryRowAccess()
    {
        IDataBufferNumeric buffer = DataBufferFactory.get().createForRange(0, 1_000_000L);
        buffer.setValue(0, 100);
        buffer.setValue(1, 200);
        buffer.setValue(2, 300);

        DataTableViewBuffer view = DataTableViewBuffer.builder().buffer(buffer).build();

        // First valid index
        assertEquals(100, view.getRealRow(null, 0));
        // Last valid index
        assertEquals(300, view.getRealRow(null, 2));
        // First invalid index
        assertThrows(IndexOutOfBoundsException.class, () -> view.getRealRow(null, 3));
    }


    @Test
    void testGetDisplayRow()
    {
        IDataBufferNumeric buffer = DataBufferFactory.get().createForRange(0, 1_000_000L);
        buffer.setValue(0, 10);
        buffer.setValue(1, 20);
        buffer.setValue(2, 30);

        DataTableViewBuffer view = DataTableViewBuffer.builder().buffer(buffer).build();

        assertEquals(0, view.getDisplayRow(null, 10));
        assertEquals(1, view.getDisplayRow(null, 20));
        assertEquals(2, view.getDisplayRow(null, 30));
    }


    @Test
    void testGetDisplayRowNotFound()
    {
        IDataBufferNumeric buffer = DataBufferFactory.get().createForRange(0, 1_000_000L);
        buffer.setValue(0, 10);
        buffer.setValue(1, 20);

        DataTableViewBuffer view = DataTableViewBuffer.builder().buffer(buffer).build();

        assertEquals(-1, view.getDisplayRow(null, 0));
        assertEquals(-1, view.getDisplayRow(null, 99));
    }


    @Test
    void testGetDisplayRowEmptyBuffer()
    {
        IDataBufferNumeric buffer = DataBufferFactory.get().createForRange(0, 1_000_000L);

        DataTableViewBuffer view = DataTableViewBuffer.builder().buffer(buffer).build();

        assertEquals(-1, view.getDisplayRow(null, 0));
    }
}
