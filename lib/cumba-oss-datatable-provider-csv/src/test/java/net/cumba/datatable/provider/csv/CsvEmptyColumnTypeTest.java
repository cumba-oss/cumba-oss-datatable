package net.cumba.datatable.provider.csv;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.Test;

/**
 * The default type of a column with no value is Char (owner, 2026-10-16: <i>"agree to Char as
 * default type."</i>; {@code PLAN-bugfix-empty-column-type} U5). CSV has typed such a column Char
 * since its K-rulings; these pins name the ruling, through {@code provide} and
 * {@code provideMetaData}: a column blank in every row, a column no row reaches (ragged rows), and
 * every column of a header-only file. A column with one number among blanks stays {@code DOUBLE}
 * (the evidence sibling).
 */
class CsvEmptyColumnTypeTest
{

    private static URI writeCsv(String aContent) throws IOException
    {
        File file = File.createTempFile("csvemptycol", ".csv");
        file.deleteOnExit();
        Files.writeString(file.toPath(), aContent, StandardCharsets.UTF_8);
        return file.toURI();
    }


    @Test
    void aColumnWithNoValueIsCharBesideAOneNumberColumnThatStaysDouble() throws IOException
    {
        URI uri = writeCsv("ID,BLANK,ONENUM,ABSENT\nr1,,\nr2,,4\nr3,\n");
        CsvTableProvider provider = new CsvTableProvider();

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);
        for (DataTableColumnMeta[] cols : List.of(table.getMetaData().getColumns(),
                provider.provideMetaData(uri, CsvProviderSupplier.FI_CSV).getColumns()))
        {
            assertEquals(DataValueType.STRING, cols[1].getType(), "BLANK");
            assertEquals(DataValueType.DOUBLE, cols[2].getType(), "ONENUM: one number is evidence");
            assertEquals(DataValueType.STRING, cols[3].getType(), "ABSENT");
        }
        for (int r = 0; r < 3; r++)
        {
            assertEquals("", table.getValue(r, 1), "BLANK row " + r);
        }
        assertEquals(4.0, (double) table.getValue(1, 2), 0.0);
    }


    @Test
    void everyColumnOfAHeaderOnlyFileIsChar() throws IOException
    {
        URI uri = writeCsv("A,B\n");
        CsvTableProvider provider = new CsvTableProvider();

        IDataTable table = provider.provide(uri, CsvProviderSupplier.FI_CSV);
        assertEquals(0, table.getRowCount());
        for (DataTableColumnMeta[] cols : List.of(table.getMetaData().getColumns(),
                provider.provideMetaData(uri, CsvProviderSupplier.FI_CSV).getColumns()))
        {
            assertEquals(2, cols.length);
            assertEquals(DataValueType.STRING, cols[0].getType(), "A");
            assertEquals(DataValueType.STRING, cols[1].getType(), "B");
        }
    }
}
