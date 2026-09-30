package net.cumba.datatable.provider.csv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.MissingValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Owner ruling K7c (2026-09-30): <i>"yes any quoted value is always a string."</i> A cell enclosed
 * in quotation marks anywhere in the type-guessing sample is evidence of TEXT, so its column types
 * STRING and every cell keeps its text -- a quoted number included. Unquoted cells keep today's
 * inference. Types are fixed by the sample: a quoted number in a row PAST it, in a column the
 * sample typed DOUBLE, is parsed as a number, as it always was.
 */
class CsvQuotedValueIsStringTest
{

    @TempDir
    Path tempDir;

    private CsvTableProvider provider;

    @BeforeEach
    void setUp()
    {
        provider = new CsvTableProvider();
    }


    private URI write(String aContent) throws IOException
    {
        Path file = Files.createTempFile(tempDir, "k7c", ".csv");
        Files.writeString(file, aContent, StandardCharsets.UTF_8);
        return file.toUri();
    }


    /** One quoted number among unquoted ones: the column is character and keeps every text. */
    @Test
    void aQuotedNumberMakesTheColumnCharacter() throws Exception
    {
        URI uri = write("ID,V\n1,\"2\"\n2,3\n3,.\n");

        DataTableMeta meta = provider.provideMetaData(uri, CsvProviderSupplier.FI_CSV);
        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertSame(DataValueType.DOUBLE, meta.getColumn(0).getType());
        assertSame(DataValueType.STRING, meta.getColumn(1).getType());
        assertSame(DataValueType.DOUBLE, table.getMetaData().getColumn(0).getType());
        assertSame(DataValueType.STRING, table.getMetaData().getColumn(1).getType());
        assertEquals("2", table.getValue(0, 1));
        assertEquals("3", table.getValue(1, 1));
        assertSame(MissingValue.MIS, table.getValue(2, 1), "an unquoted '.' is still the missing");
    }


    /** A quote-everything export: every column is character, the numbers keep their text. */
    @Test
    void aQuoteAllExportIsAllCharacter() throws Exception
    {
        URI uri = write("\"A\",\"B\"\n\"1\",\"x\"\n\"2.5\",\"y\"\n");

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertSame(DataValueType.STRING, table.getMetaData().getColumn(0).getType());
        assertSame(DataValueType.STRING, table.getMetaData().getColumn(1).getType());
        assertEquals("1", table.getValue(0, 0));
        assertEquals("2.5", table.getValue(1, 0));
    }


    /**
     * A quoted EMPTY value is a quoted value too: {@code ""} is text evidence where an unquoted
     * blank is none. It reads as the empty string.
     */
    @Test
    void aQuotedEmptyValueIsText() throws Exception
    {
        URI uri = write("A,B\n1,1\n\"\",\n");

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertSame(DataValueType.STRING, table.getMetaData().getColumn(0).getType());
        assertSame(DataValueType.DOUBLE, table.getMetaData().getColumn(1).getType(),
                "an unquoted blank is still no evidence");
        assertEquals("1", table.getValue(0, 0));
        assertEquals("", table.getValue(1, 0));
        assertSame(MissingValue.MIS, table.getValue(1, 1));
    }


    /** Whitespace outside the quotes does not unquote a value. */
    @Test
    void aPaddedQuotedNumberIsText() throws Exception
    {
        URI uri = write("A\n1\n  \"5\"  \n");

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertSame(DataValueType.STRING, table.getMetaData().getColumn(0).getType());
        assertEquals("5", table.getValue(1, 0));
    }


    /** The configured quote character is the one that counts. */
    @Test
    void theConfiguredQuoteCharacterCounts() throws Exception
    {
        URI uri = write("A,B\n'1',\"2\"\n3,4\n");
        provider.setQuote('\'');
        provider.setQuoteEscape('\'');

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertSame(DataValueType.STRING, table.getMetaData().getColumn(0).getType());
        assertEquals("1", table.getValue(0, 0));
        assertSame(DataValueType.STRING, table.getMetaData().getColumn(1).getType(),
                "\"2\" is the three-character text here, not a number");
        assertEquals("\"2\"", table.getValue(0, 1));
    }


    /** Unquoted cells keep today's inference. (Unchanged by K7c.) */
    @Test
    void unquotedInferenceIsUnchanged() throws Exception
    {
        URI uri = write("A,B,C\n1,x,\n2.5,y,.\n");

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertSame(DataValueType.DOUBLE, table.getMetaData().getColumn(0).getType());
        assertSame(DataValueType.STRING, table.getMetaData().getColumn(1).getType());
        assertSame(DataValueType.STRING, table.getMetaData().getColumn(2).getType(),
                "no evidence at all: the STRING default");
    }


    /**
     * The sample decides the type; past it a quoted number in a DOUBLE column is parsed as the
     * number it spells, as it always was -- the column cannot hold text, and types are never
     * revised. A quoted value past the sample in a character column is its text. (Unchanged by K7c;
     * pinned.)
     */
    @Test
    void aQuotedNumberPastTheSampleInANumericColumnIsParsed() throws Exception
    {
        provider.setGuessingRowCount(2);
        URI uri = write("N,S\n1,a\n2,b\n\"3\",\"4\"\n\"x\",\"\"\n");

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertSame(DataValueType.DOUBLE, table.getMetaData().getColumn(0).getType());
        assertEquals(3.0, (double) table.getValue(2, 0), 0.0);
        assertSame(MissingValue.MIS, table.getValue(3, 0), "quoted text in a numeric column");
        assertEquals("4", table.getValue(2, 1));
        assertEquals("", table.getValue(3, 1));
    }


    /** A quoted cell that is the LAST row of the sample still counts; one row later it does not. */
    @Test
    void theSampleBoundaryIsExact() throws Exception
    {
        provider.setGuessingRowCount(2);
        URI inSample = write("V\n1\n\"2\"\n3\n");
        URI pastSample = write("V\n1\n2\n\"3\"\n");

        assertSame(DataValueType.STRING, provider
                .provideMetaData(inSample, CsvProviderSupplier.FI_CSV).getColumn(0).getType());
        assertSame(DataValueType.DOUBLE, provider
                .provideMetaData(pastSample, CsvProviderSupplier.FI_CSV).getColumn(0).getType());
    }


    /** A quoted value that spans lines and holds the delimiter is text evidence like any other. */
    @Test
    void aMultiLineQuotedValueCounts() throws Exception
    {
        URI uri = write("A,B\n1,2\n\"3\n4\",\"5,6\"\n");

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);

        assertSame(DataValueType.STRING, table.getMetaData().getColumn(0).getType());
        assertSame(DataValueType.STRING, table.getMetaData().getColumn(1).getType());
        assertEquals("1", table.getValue(0, 0));
        assertEquals("3\n4", table.getValue(1, 0));
        assertEquals("5,6", table.getValue(1, 1));
    }
}
