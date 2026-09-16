package net.cumba.datatable.provider.sas.sas7bdat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.io.FileInfo;
import org.junit.jupiter.api.Test;

/**
 * End-to-end read tests for {@link BdatTableProvider} against a real sas7bdat fixture
 * ({@code testdata/sas7bdat/01_plain/adsl.sas7bdat}, an ADaM ADSL data set). These exercise the
 * full streaming read path (parser, observation iterator, column population) that the in-memory
 * unit tests do not cover.
 */
class BdatTableProviderReadTest
{

    private static File fixture()
    {
        return new File(System.getProperty("repoRoot"), "testdata/sas7bdat/01_plain/adsl.sas7bdat");
    }


    @Test
    void readsFullTable() throws IOException
    {
        File f = fixture();
        assertTrue(f.isFile(), "fixture missing: " + f);

        BdatTableProvider provider = new BdatTableProvider();
        IDataTable table = provider.provide(f.toURI(), (FileInfo) null);

        assertTrue(table.getColumnCount() > 0, "should have columns");
        assertTrue(table.getRowCount() > 0, "should have rows");
        assertTrue(table.getMetaData().containsColumn("STUDYID"), "ADSL should expose STUDYID");
        // The metadata row count must agree with the streamed row count.
        assertEquals(table.getRowCount(), table.getMetaData().getRowCount());
        // Reading a value through the populated column must not throw.
        assertTrue(table.getColumnCount() == table.getMetaData().getColumnCount());
        table.getValue(0, 0);
    }


    @Test
    void provideMetaDataDoesNotStreamRows() throws IOException
    {
        File f = fixture();
        BdatTableProvider provider = new BdatTableProvider();
        DataTableMeta meta = provider.provideMetaData(f.toURI(), null);

        assertTrue(meta.getColumnCount() > 0);
        assertTrue(meta.containsColumn("STUDYID"));
    }


    /**
     * F-prov-14: the metadata-only view must report the LIVE row count, the same figure
     * {@code provide} delivers. {@code DatasetBdat.getRowCount()} counts the rows SAS has marked
     * deleted as well - which is exactly why {@code provide} has to reconcile them in
     * {@code checkAllRowsRead} - so handing it on unadjusted made the library browser's row-count
     * column over-report by the deleted count and disagree with the table the user then opened.
     */
    @Test
    void provideMetaDataReportsTheLiveRowCountNotTheHeaderCount() throws IOException
    {
        File f = new File(System.getProperty("repoRoot"),
                "testdata/sas7bdat/01_plain/with_9_deleted.sas7bdat");
        assertTrue(f.isFile(), "fixture missing: " + f);

        BdatTableProvider provider = new BdatTableProvider();
        DataTableMeta meta = provider.provideMetaData(f.toURI(), null);
        IDataTable table = provider.provide(f.toURI(), (FileInfo) null);

        // SAS itself reports 48 for this file: 57 stored observations, 9 marked deleted. The
        // explicit 48 keeps this test non-vacuous - a fixture without deleted rows would let a
        // reverted fix pass the agreement assertion below.
        assertEquals(48L, table.getRowCount());
        assertEquals(table.getRowCount(), meta.getRowCount(),
                "provideMetaData must report the live row count provide() delivers, not the"
                        + " header count");
    }

}
