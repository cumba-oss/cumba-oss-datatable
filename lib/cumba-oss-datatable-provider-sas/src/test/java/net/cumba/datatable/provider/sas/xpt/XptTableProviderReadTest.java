package net.cumba.datatable.provider.sas.xpt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.io.FileInfo;
import org.junit.jupiter.api.Test;

/**
 * End-to-end read tests for {@link XptTableProvider} against real XPT fixtures:
 * {@code testdata/xpt/01_plain/adsl.xpt} (single dataset) and
 * {@code testdata/xpt/05_multiple/sdtm.xpt} (multi-dataset library, addressed via URI fragment).
 * These cover the full streaming read path (library parse, observation iterator, column population)
 * that the in-memory unit tests do not.
 */
class XptTableProviderReadTest
{

    private static File testdata(String aRelPath)
    {
        return new File(System.getProperty("repoRoot"), aRelPath);
    }


    @Test
    void readsSingleDatasetXpt() throws IOException
    {
        File f = testdata("testdata/xpt/01_plain/adsl.xpt");
        assertTrue(f.isFile(), "fixture missing: " + f);

        XptTableProvider provider = new XptTableProvider();
        IDataTable table = provider.provide(f.toURI(), (FileInfo) null);

        assertTrue(table.getColumnCount() > 0);
        assertTrue(table.getRowCount() > 0);
        assertTrue(table.getMetaData().containsColumn("STUDYID"), "ADSL should expose STUDYID");
        assertEquals(table.getRowCount(), table.getMetaData().getRowCount());
        table.getValue(0, 0);
    }


    @Test
    void provideMetaDataSingleDataset() throws IOException
    {
        File f = testdata("testdata/xpt/01_plain/adsl.xpt");
        XptTableProvider provider = new XptTableProvider();
        DataTableMeta meta = provider.provideMetaData(f.toURI(), null);

        assertTrue(meta.getColumnCount() > 0);
        assertTrue(meta.containsColumn("STUDYID"));
    }


    @Test
    void readsNamedDatasetFromMultiDatasetLibraryViaFragment() throws IOException
    {
        File f = testdata("testdata/xpt/05_multiple/sdtm.xpt");
        assertTrue(f.isFile(), "fixture missing: " + f);

        // Address the DM (Demographics) member of the multi-dataset library via a URI fragment.
        URI dmUri = URI.create(f.toURI() + "#DM");

        XptTableProvider provider = new XptTableProvider();
        DataTableMeta meta = provider.provideMetaData(dmUri, null);
        assertEquals("DM", meta.getName());
        assertTrue(meta.getColumnCount() > 0);

        IDataTable table = provider.provide(dmUri, (FileInfo) null);
        assertEquals("DM", table.getMetaData().getName());
        assertTrue(table.getColumnCount() > 0);
    }
}
