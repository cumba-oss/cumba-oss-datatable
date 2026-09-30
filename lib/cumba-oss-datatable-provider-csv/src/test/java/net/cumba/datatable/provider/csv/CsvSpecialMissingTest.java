package net.cumba.datatable.provider.csv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.MissingValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Owner ruling K7b (2026-09-30): <i>"CSV: Agree to special missings as long as they are not in
 * quotation marks."</i> An <b>unquoted</b> {@code ._} or {@code .A}..{@code .Z} is that SAS special
 * missing -- its own identity, the {@code .cdt} grammar's mapping -- in every column type, and like
 * the plain {@code .} it is no type evidence. A <b>quoted</b> one is text, and a lower-case
 * {@code .a} (not a sentinel in that grammar) is text either way.
 */
class CsvSpecialMissingTest
{

    @TempDir
    Path tempDir;

    private CsvTableProvider provider;

    @BeforeEach
    void setUp()
    {
        provider = new CsvTableProvider();
    }


    private IDataTable load(String aContent) throws IOException
    {
        Path file = Files.createTempFile(tempDir, "k7b", ".csv");
        Files.writeString(file, aContent, StandardCharsets.UTF_8);
        URI uri = file.toUri();
        return provider.provide(uri, CsvProviderSupplier.FI_CSV);
    }


    /** In a character column each special keeps its own identity -- never collapsed to MIS. */
    @Test
    void specialsInACharacterColumnKeepTheirIdentity() throws Exception
    {
        IDataTable table = load("ID,C\n1,abc\n2,.A\n3,._\n4,.Z\n5,.\n");

        assertSame(DataValueType.STRING, table.getMetaData().getColumn(1).getType());
        assertEquals("abc", table.getValue(0, 1));
        assertSame(MissingValue.MIS_A, table.getValue(1, 1));
        assertSame(MissingValue.MIS__, table.getValue(2, 1));
        assertSame(MissingValue.MIS_Z, table.getValue(3, 1));
        assertSame(MissingValue.MIS, table.getValue(4, 1));
    }


    /**
     * In a numeric column an unquoted special is no evidence against DOUBLE, and the DOUBLE buffer
     * keeps its identity: {@code .A} reads {@code MIS_A}, not {@code MIS}.
     */
    @Test
    void specialsInANumericColumnKeepTheTypeAndTheirIdentity() throws Exception
    {
        IDataTable table = load("V\n1.5\n.A\n._\n.Z\n.\n3\n");

        assertSame(DataValueType.DOUBLE, table.getMetaData().getColumn(0).getType());
        assertEquals(1.5, (double) table.getValue(0, 0), 0.0);
        assertSame(MissingValue.MIS_A, table.getValue(1, 0));
        assertSame(MissingValue.MIS__, table.getValue(2, 0));
        assertSame(MissingValue.MIS_Z, table.getValue(3, 0));
        assertSame(MissingValue.MIS, table.getValue(4, 0));
        assertEquals(3.0, (double) table.getValue(5, 0), 0.0);
    }


    /**
     * A column of specials only carries no evidence at all: it stays STRING, as a {@code .} one.
     */
    @Test
    void anAllSpecialsColumnBesideNumbersStaysCharacter() throws Exception
    {
        IDataTable table = load("N,S\n1,.B\n2,._\n");

        assertSame(DataValueType.DOUBLE, table.getMetaData().getColumn(0).getType());
        assertSame(DataValueType.STRING, table.getMetaData().getColumn(1).getType());
        assertSame(MissingValue.MIS_B, table.getValue(0, 1));
        assertSame(MissingValue.MIS__, table.getValue(1, 1));
    }


    /** A quoted special is the text, and text vetoes a numeric type. */
    @Test
    void aQuotedSpecialIsText() throws Exception
    {
        IDataTable table = load("V,C\n1,\".A\"\n\".A\",\"._\"\n");

        assertSame(DataValueType.STRING, table.getMetaData().getColumn(0).getType());
        assertSame(DataValueType.STRING, table.getMetaData().getColumn(1).getType());
        assertEquals("1", table.getValue(0, 0));
        assertEquals(".A", table.getValue(1, 0));
        assertEquals(".A", table.getValue(0, 1));
        assertEquals("._", table.getValue(1, 1));
    }


    /**
     * Only the 28 sentinels of the {@code .cdt} grammar ({@code \.[_A-Z]?}) are missings: a
     * lower-case {@code .a}, a two-letter {@code .AB} and a {@code ..} are ordinary text -- and
     * text vetoes DOUBLE. (Unchanged: all three were text before K7b too.)
     */
    @Test
    void lookalikesAreText() throws Exception
    {
        IDataTable table = load("V\n1\n.a\n.AB\n..\n");

        assertSame(DataValueType.STRING, table.getMetaData().getColumn(0).getType());
        assertEquals("1", table.getValue(0, 0));
        assertEquals(".a", table.getValue(1, 0));
        assertEquals(".AB", table.getValue(2, 0));
        assertEquals("..", table.getValue(3, 0));
    }


    /** Rows past the type-guessing sample go through the same mapping, in both column types. */
    @Test
    void specialsBeyondTheGuessSample() throws Exception
    {
        provider.setGuessingRowCount(1);

        IDataTable table = load("N,S\n1,x\n.C,.D\n\".C\",\".D\"\n");

        assertSame(DataValueType.DOUBLE, table.getMetaData().getColumn(0).getType());
        assertSame(DataValueType.STRING, table.getMetaData().getColumn(1).getType());
        assertSame(MissingValue.MIS_C, table.getValue(1, 0));
        assertSame(MissingValue.MIS_D, table.getValue(1, 1));
        assertSame(MissingValue.MIS, table.getValue(2, 0),
                "a quoted special past the sample cannot be text in a DOUBLE column: MIS, as a"
                        + " quoted '.' there always was");
        assertEquals(".D", table.getValue(2, 1));
    }
}
