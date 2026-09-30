package net.cumba.datatable.provider.csv;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

/**
 * The quote-marking step of {@link CsvRecordReader} on its own: which cells of a record are a
 * quoted {@code "."} (owner ruling K7), given the same record parsed with {@code keepQuotes}.
 */
class CsvRecordReaderTest
{

    @Test
    void marksOnlyQuotedDots()
    {
        String[] row =
        {
                ".", ".", "x", null, "."
        };
        String[] kept =
        {
                "\".\"", ".", "\"x\"", null, "'.'"
        };

        assertArrayEquals(new boolean[]
        {
                true, false, false, false, false
        }, CsvRecordReader.quotedDots(row, kept, '"'));
        assertArrayEquals(new boolean[]
        {
                false, false, false, false, true
        }, CsvRecordReader.quotedDots(row, kept, '\''));
    }


    /**
     * A twin parse that did not reach a cell leaves it unquoted -- the ruling's default reading.
     */
    @Test
    void cellsTheKeptParseDoesNotReachAreUnquoted()
    {
        String[] row =
        {
                ".", "."
        };

        assertArrayEquals(new boolean[]
        {
                true, false
        }, CsvRecordReader.quotedDots(row, new String[]
        {
                "\".\""
        }, '"'));
        assertArrayEquals(new boolean[]
        {
                false, false
        }, CsvRecordReader.quotedDots(row, null, '"'));
        assertArrayEquals(new boolean[]
        {
                true
        }, CsvRecordReader.quotedDots(new String[]
        {
                "."
        }, new String[]
        {
                "\".\"", "\".\""
        }, '"'));
    }
}
