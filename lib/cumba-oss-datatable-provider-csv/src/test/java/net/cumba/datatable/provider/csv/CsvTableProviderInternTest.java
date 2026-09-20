package net.cumba.datatable.provider.csv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.help.CDT;
import org.junit.jupiter.api.Test;

/**
 * Verifies that CSV string cells are canonicalised against the app-global pool: equal content is
 * {@code ==}-shared within one table, across independently loaded tables, and with any other
 * subsystem going through {@link CDT#intern(String)}.
 */
class CsvTableProviderInternTest
{

    private static URI writeTempCsv(String aContent) throws IOException
    {
        File tmpFile = File.createTempFile("csvinterntest", ".csv");
        tmpFile.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(tmpFile))
        {
            fos.write(aContent.getBytes(StandardCharsets.UTF_8));
        }
        return tmpFile.toURI();
    }


    @Test
    void stringCellsAreCanonicalisedWithinAndAcrossLoads() throws Exception
    {
        String csv = "NAME,CITY\nAlice,Berlin\nBob,Berlin\n";
        URI uri1 = writeTempCsv(csv);
        URI uri2 = writeTempCsv(csv);

        CsvTableProvider provider = new CsvTableProvider();
        IDataTable t1 = provider.provide(uri1, CsvProviderSupplier.FI_CSV);
        IDataTable t2 = provider.provide(uri2, CsvProviderSupplier.FI_CSV);

        Object a = t1.getValue(0, 1);
        Object b = t1.getValue(1, 1);
        Object c = t2.getValue(0, 1);
        assertEquals("Berlin", a);
        assertSame(a, b, "repeated value within one table must share one instance");
        assertSame(a, c, "equal values across independent loads must share one instance");
        assertSame(a, CDT.intern("Berlin"), "the shared instance must be the CDT-pool one");
    }
}
