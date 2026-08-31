package net.cumba.datatable.provider.sas.xpt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.cumba.sasutils.xpt.DatasetXpt;
import net.cumba.sasutils.xpt.VariableXpt;
import org.junit.jupiter.api.Test;

class ObservationIteratorXptTest
{

    @Test
    void testDefaultCharset()
    {
        assertEquals(StandardCharsets.UTF_8, ObservationIteratorXpt.DEFAULT_CHARSET);
    }


    @Test
    void testSentinelValue()
    {
        assertEquals(' ', ObservationIteratorXpt.SENTINEL);
    }


    @Test
    void testNoValueConstant()
    {
        assertEquals('.', ObservationIteratorXpt.NO_VALUE);
    }


    @Test
    void testGetAndSetCharset()
    {
        DatasetXpt ds = createMockDataset(4);
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[0]);

        ObservationIteratorXpt iter = new ObservationIteratorXpt(ds, in);
        assertEquals(StandardCharsets.UTF_8, iter.getCharset());

        iter.setCharset(StandardCharsets.ISO_8859_1);
        assertEquals(StandardCharsets.ISO_8859_1, iter.getCharset());
    }


    @Test
    void testConstructorWithCharset()
    {
        DatasetXpt ds = createMockDataset(4);
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[0]);

        ObservationIteratorXpt iter = new ObservationIteratorXpt(ds, StandardCharsets.ISO_8859_1,
                in);
        assertEquals(StandardCharsets.ISO_8859_1, iter.getCharset());
    }


    @Test
    void testHasNextEmptyStream()
    {
        DatasetXpt ds = createMockDataset(4);
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[0]);

        ObservationIteratorXpt iter = new ObservationIteratorXpt(ds, in);
        assertFalse(iter.hasNext());
    }


    @Test
    void testHasNextWithAllSpaces()
    {
        // A buffer full of spaces (SENTINEL) means no real data
        DatasetXpt ds = createMockDataset(4);
        byte[] data = new byte[]
        {
                ' ', ' ', ' ', ' '
        };
        ByteArrayInputStream in = new ByteArrayInputStream(data);

        ObservationIteratorXpt iter = new ObservationIteratorXpt(ds, in);
        assertFalse(iter.hasNext());
    }


    @Test
    void testHasNextAndNextWithData()
    {
        DatasetXpt ds = createMockDataset(4);
        byte[] data = "Test".getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream in = new ByteArrayInputStream(data);

        ObservationIteratorXpt iter = new ObservationIteratorXpt(ds, in);
        assertTrue(iter.hasNext());
        XptObservation obs = iter.next();
        assertNotNull(obs);
        // After consuming the one observation, no more data
        assertFalse(iter.hasNext());
    }


    @Test
    void testMultipleObservations()
    {
        DatasetXpt ds = createMockDataset(4);
        // Two observations: "AAAA" and "BBBB"
        byte[] data = "AAAABBBB".getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream in = new ByteArrayInputStream(data);

        ObservationIteratorXpt iter = new ObservationIteratorXpt(ds, in);

        assertTrue(iter.hasNext());
        XptObservation obs1 = iter.next();
        assertNotNull(obs1);

        assertTrue(iter.hasNext());
        XptObservation obs2 = iter.next();
        assertNotNull(obs2);

        assertFalse(iter.hasNext());
    }


    @Test
    void testPartialRead()
    {
        // Data shorter than observation size
        DatasetXpt ds = createMockDataset(8);
        byte[] data = new byte[]
        {
                'A', 'B'
        }; // only 2 bytes, need 8
        ByteArrayInputStream in = new ByteArrayInputStream(data);

        ObservationIteratorXpt iter = new ObservationIteratorXpt(ds, in);
        assertFalse(iter.hasNext());
    }

    // --- F-D16: IOException in the read path is surfaced as IllegalStateException whose
    // cause is the original IOException, so the caller can unwrap it back. ---


    @Test
    void testReadIfNecessaryWrapsIoExceptionWithCausePreserved()
    {
        DatasetXpt ds = createMockDataset(4);
        // A stream whose read() throws synchronously. IOUtils.read() will propagate the
        // IOException up to readIfNecessary's catch block.
        IOException synthetic = new IOException("synthetic disk failure");
        InputStream failing = new InputStream()
        {

            @Override
            public int read() throws IOException
            {
                throw synthetic;
            }


            @Override
            public int read(byte[] b, int off, int len) throws IOException
            {
                throw synthetic;
            }
        };

        ObservationIteratorXpt iter = new ObservationIteratorXpt(ds, failing);
        IllegalStateException ex = assertThrows(IllegalStateException.class, iter::hasNext);
        assertInstanceOf(IOException.class, ex.getCause(),
                "F-D16: cause must be the original IOException so callers can unwrap it");
        assertEquals("synthetic disk failure", ex.getCause().getMessage());
    }


    private DatasetXpt createMockDataset(int varLength)
    {
        VariableXpt vxpt = new VariableXpt();
        vxpt.name = "V1";
        vxpt.variableTypeId = 2; // Character
        vxpt.length = (short) varLength;

        List<VariableXpt> vars = new ArrayList<>();
        vars.add(vxpt);

        DatasetXpt ds = mock(DatasetXpt.class);
        when(ds.getVariables()).thenReturn(vars);
        when(ds.getObservationStartByte()).thenReturn(0L);

        return ds;
    }
}
