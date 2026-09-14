package net.cumba.datatable.provider.sas.xpt;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
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
import net.cumba.sasutils.xpt.XptConstants;
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

    // --- F-prov-02: a short read carrying observation DATA is a truncated file and must fail
    // loudly (wrapped per the F-D16 Iterator contract), because XPT stores no row count and a
    // silently dropped tail is undetectable downstream. A short read of nothing but sentinel
    // padding is a clean end: records are padded to 80-byte boundaries, not observation
    // boundaries.
    //
    // NOTE: this replaces the former testPartialRead, which asserted the silent form
    // (assertFalse(hasNext) on a 2-byte tail of DATA) - i.e. it pinned exactly the bug the
    // owner ruled on in F-prov-02.


    @Test
    void testPartialReadOfObservationDataThrows()
    {
        DatasetXpt ds = createMockDataset(8);
        byte[] data = new byte[]
        {
                'A', 'B'
        }; // only 2 bytes of DATA, need 8 -> truncated
        ByteArrayInputStream in = new ByteArrayInputStream(data);

        ObservationIteratorXpt iter = new ObservationIteratorXpt(ds, in);
        IllegalStateException ex = assertThrows(IllegalStateException.class, iter::hasNext);
        IOException cause = assertInstanceOf(IOException.class, ex.getCause());
        assertTrue(cause.getMessage().contains("Truncated XPT"), cause.getMessage());
        assertTrue(cause.getMessage().contains("2 byte(s)"), cause.getMessage());
    }


    @Test
    void testTruncationAfterCompleteObservationsThrows()
    {
        DatasetXpt ds = createMockDataset(4);
        // one complete observation, then the file ends 2 bytes into the second one
        byte[] data = "AAAABB".getBytes(StandardCharsets.UTF_8);
        ObservationIteratorXpt iter = new ObservationIteratorXpt(ds,
                new ByteArrayInputStream(data));

        assertTrue(iter.hasNext());
        assertNotNull(iter.next());
        IllegalStateException ex = assertThrows(IllegalStateException.class, iter::hasNext);
        assertInstanceOf(IOException.class, ex.getCause());
    }


    @Test
    void testShortAllPaddingTailIsCleanEndOfFile()
    {
        DatasetXpt ds = createMockDataset(8);
        // one complete observation followed by a 3-byte tail of record padding (spaces)
        byte[] data = "ABCDEFGH   ".getBytes(StandardCharsets.UTF_8);
        ObservationIteratorXpt iter = new ObservationIteratorXpt(ds,
                new ByteArrayInputStream(data));

        assertTrue(iter.hasNext());
        assertNotNull(iter.next());
        assertFalse(iter.hasNext(), "an all-sentinel short tail is a clean end, not truncation");
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

    // --- F-prov-11: the trailing "HEADER RECORD*******" that ends a member's observation
    // section must never be read as observation DATA. XPT pads the data section to an 80-byte
    // RECORD boundary, not to an observation boundary, so the tag's offset inside the
    // observation-sized read buffer is (paddedDataBytes mod observationSize) - every offset is
    // reachable, including the two the predecessor bound `i < buffer.length - 20` skipped:
    // exactly-fits and split-across-reads. Both produced a garbage row built from the NEXT
    // member's descriptor records, and XPT stores no row count so nothing downstream noticed.


    @Test
    void aHeaderRecordFillingTheWholeBufferIsNotAnObservation()
    {
        // observationSize == the tag length, so `buffer.length - 20` is 0 and the old bound
        // `i < 0` could never hold: the tag was always read as data.
        DatasetXpt ds = createMockDataset(HEADER_TAG.length());
        byte[] data = concat(row(HEADER_TAG.length(), 'A'), HEADER_TAG.getBytes(ISO_8859_1));

        ObservationIteratorXpt iter = new ObservationIteratorXpt(ds,
                new ByteArrayInputStream(data));
        assertEquals(1, drain(iter), "the HEADER RECORD tag is not a row");
    }


    @Test
    void aHeaderRecordExactlyFillingTheRestOfTheBufferIsNotAnObservation()
    {
        // 25-byte observations: the tag starts at index 5 of the second buffer and all 20 of its
        // bytes are present, but the old strict `<` skipped the comparison at i == 5 == checkEnd.
        DatasetXpt ds = createMockDataset(25);
        byte[] data = concat(row(25, 'A'), row(5, ' '), HEADER_TAG.getBytes(ISO_8859_1));

        ObservationIteratorXpt iter = new ObservationIteratorXpt(ds,
                new ByteArrayInputStream(data));
        assertEquals(1, drain(iter));
    }


    @Test
    void aHeaderRecordSplitAcrossTwoReadsIsNotAnObservation()
    {
        // 30-byte observations: the second buffer holds 20 bytes of padding then only the first
        // 10 bytes of the tag. The remaining 10 are completed from the stream.
        DatasetXpt ds = createMockDataset(30);
        byte[] data = concat(row(30, 'A'), row(20, ' '), HEADER_TAG.getBytes(ISO_8859_1),
                row(60, ' '));

        ObservationIteratorXpt iter = new ObservationIteratorXpt(ds,
                new ByteArrayInputStream(data));
        assertEquals(1, drain(iter));
    }


    @Test
    void textThatMerelyStartsLikeTheHeaderTagIsStillAnObservation()
    {
        // The discriminator must stay the FULL 20-byte tag. Weakening it to a prefix match would
        // end the dataset early on ordinary character data - here a value that shares the tag's
        // first 15 bytes - and silently drop every remaining row.
        DatasetXpt ds = createMockDataset(25);
        byte[] lookalike = "HEADER RECORD**HELLO12345".getBytes(ISO_8859_1);
        assertEquals(25, lookalike.length);
        byte[] data = concat(row(25, 'A'), lookalike);

        ObservationIteratorXpt iter = new ObservationIteratorXpt(ds,
                new ByteArrayInputStream(data));
        assertEquals(2, drain(iter), "a tag look-alike is data, not a header");
    }


    @Test
    void bytesReadToCompleteTheTagAreGivenBackWhenTheyDoNotCompleteIt()
    {
        // The split-tag completion reads 10 bytes ahead. When they do not complete the tag those
        // bytes are real observation data and must reappear in the following rows - otherwise the
        // fix for the split case would itself drop data.
        DatasetXpt ds = createMockDataset(30);
        byte[] data = concat(row(30, 'A'), row(20, ' '), "HEADER REC".getBytes(ISO_8859_1),
                row(30, 'B'));

        ObservationIteratorXpt iter = new ObservationIteratorXpt(ds,
                new ByteArrayInputStream(data));
        List<String> rows = new ArrayList<>();
        while (iter.hasNext())
        {
            rows.add(new String(valueOf(iter.next()), ISO_8859_1));
        }
        assertEquals(3, rows.size());
        assertEquals("A".repeat(30), rows.get(0));
        // row 2 = 20 spaces + "HEADER REC": the look-ahead bytes are the ones after it
        assertEquals(" ".repeat(20) + "HEADER REC", rows.get(1));
        assertEquals("B".repeat(30), rows.get(2));
    }

    private static final String HEADER_TAG = XptConstants.HEADER_TAG;

    private static byte[] row(int aCount, char aFill)
    {
        byte[] b = new byte[aCount];
        java.util.Arrays.fill(b, (byte) aFill);
        return b;
    }


    private static byte[] concat(byte[]... aParts)
    {
        int len = 0;
        for (byte[] part : aParts)
        {
            len += part.length;
        }
        byte[] out = new byte[len];
        int at = 0;
        for (byte[] part : aParts)
        {
            System.arraycopy(part, 0, out, at, part.length);
            at += part.length;
        }
        return out;
    }


    @Test
    void nextReadsForItselfWhenTheCallerNeverAsksHasNext()
    {
        // Iterator contract: next() is allowed on its own. It keeps its own "needToRead" flag, so a
        // next() that relied on hasNext() having read would hand the SAME buffer back twice — every
        // row of the file duplicated, with the row count still adding up.
        DatasetXpt ds = createMockDataset(4);
        byte[] data = concat("AAAA".getBytes(ISO_8859_1), "BBBB".getBytes(ISO_8859_1));

        ObservationIteratorXpt iter = new ObservationIteratorXpt(ds,
                new ByteArrayInputStream(data));
        XptObservation first = iter.next();
        XptObservation second = iter.next();

        assertEquals("AAAA", String.valueOf(first.getValue(0)));
        assertEquals("BBBB", String.valueOf(second.getValue(0)),
                "the second next() must have read the second observation for itself");
    }


    private static byte[] valueOf(XptObservation aObservation)
    {
        return String.valueOf(aObservation.getValue(0)).getBytes(ISO_8859_1);
    }


    private static int drain(ObservationIteratorXpt aIterator)
    {
        int n = 0;
        while (aIterator.hasNext())
        {
            assertNotNull(aIterator.next());
            n++;
        }
        return n;
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
