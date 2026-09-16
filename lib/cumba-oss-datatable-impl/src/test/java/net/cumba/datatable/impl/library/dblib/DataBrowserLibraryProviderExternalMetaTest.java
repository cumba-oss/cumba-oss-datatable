package net.cumba.datatable.impl.library.dblib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.CachedDataTableColumn;
import net.cumba.datatable.impl.ColumnCachedDataTable;
import net.cumba.datatable.impl.library.dblib.beans.DataBrowserColumnMetaBean;
import net.cumba.datatable.impl.library.dblib.beans.DataBrowserLibraryBean;
import net.cumba.datatable.values.DataValueType;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Guards for {@link DataBrowserLibraryProvider}'s external column-metadata-table loop, which no
 * other test in this repository enters at all. The data-table provider SPI is not on this module's
 * test classpath, so the tests reach the loop through the package-private {@code loadMetaTable}
 * seam - which is exactly why that seam is package-private - handing the provider a table built
 * directly from {@link CachedDataTableColumn}s.
 */
class DataBrowserLibraryProviderExternalMetaTest
{

    /**
     * Builds an all-STRING metadata table whose {@link DataTableMeta} snapshot may deliberately
     * disagree with the live column row count.
     */
    private static IDataTable stringTable(URI aTableUri, long aMetaRowCount, String[] aColumnNames,
            String[]... aRows)
    {
        DataTableColumnMeta[] colMetas = new DataTableColumnMeta[aColumnNames.length];
        CachedDataTableColumn[] cols = new CachedDataTableColumn[aColumnNames.length];
        for (int c = 0; c < aColumnNames.length; c++)
        {
            colMetas[c] = DataTableColumnMeta.builder().index(c).name(aColumnNames[c])
                    .type(DataValueType.STRING).build();
            cols[c] = new CachedDataTableColumn(c, DataValueType.STRING);
            for (String[] row : aRows)
            {
                cols[c].addElement(row[c]);
            }
            cols[c].complete();
        }
        DataTableMeta meta = DataTableMeta.builder().name("cols").rowCount(aMetaRowCount)
                .totalRowCount(aMetaRowCount).tableURI(aTableUri).columns(colMetas).build();
        return new ColumnCachedDataTable(meta, cols);
    }


    private static DataBrowserLibraryProvider providerReturning(IDataTable aTable)
    {
        return new DataBrowserLibraryProvider()
        {

            @Override
            @Nullable
            IDataTable loadMetaTable(URI aTableUri)
            {
                return aTable;
            }
        };
    }


    private static DataBrowserMember member(URI aLibraryUri, URI aMemberUri,
            DataBrowserLibraryBean aBean)
    {
        return new DataBrowserMember(new DataBrowserLibrary(aLibraryUri, aBean), aMemberUri, "DM",
                null);
    }


    private static DataBrowserLibraryBean beanWithColsTable()
    {
        return DataBrowserLibraryBean.builder().name("L").columnMetaTableUris(new String[]
        {
                "cols.csv"
        }).build();
    }


    @Test
    void testExternalTableRowsAreCountedFromTheTableNotItsMetadata(@TempDir Path tempDir)
        throws IOException
    {
        // DataTableMeta.rowCount is a snapshot that defaults to 0 and is documented as "might be
        // -1"; IDataTable.getRowCount() is the live count. Reading the snapshot made a metadata
        // table whose provider left it unset contribute NO column metadata at all, silently.
        URI libraryUri = tempDir.resolve("t.dblib").toUri();
        URI memberUri = tempDir.resolve("dm.csv").toUri();

        // Same columns, but the metadata snapshot claims there are no rows: the live count derives
        // from the columns (ColumnCachedDataTable leaves its own rowCount field at -1), while the
        // snapshot says 0 - the exact stale shape a provider that leaves rowCount unset produces.
        IDataTable stale = stringTable(libraryUri.resolve("cols.csv"), 0, new String[]
        {
                "uri", "name", "label"
        }, new String[]
        {
                memberUri.toString(), "AGE", "Age in Years"
        });
        assertEquals(0, stale.getMetaData().getRowCount());
        assertEquals(1, stale.getRowCount());

        DataBrowserLibraryProvider provider = providerReturning(stale);
        List<? extends DataTableColumnMeta> cols = provider
                .provideLibraryMemberColumns(member(libraryUri, memberUri, beanWithColsTable()))
                .toList();

        assertEquals(1, cols.size(), "rows must be read from the table, not from its metadata");
        assertEquals("Age in Years", cols.get(0).getLabel());
    }


    /**
     * Owner ruling Q42 (2026-09-14): an unrecognised type string in a metadata table row is a
     * corrupt file - the load fails and raises, no default is substituted, and the message names
     * the offending value, the column and the source so an operator can repair the file. The
     * pre-ruling behaviour left the column untyped with a WARNING in a log nobody reads.
     */
    @Test
    void anUnknownTypeInAMetadataTableRowFailsTheLoad(@TempDir Path tempDir) throws IOException
    {
        URI libraryUri = tempDir.resolve("t.dblib").toUri();
        URI memberUri = tempDir.resolve("dm.csv").toUri();
        IDataTable metaTable = stringTable(libraryUri.resolve("cols.csv"), 1, new String[]
        {
                "uri", "name", "type", "label"
        }, new String[]
        {
                memberUri.toString(), "AGE", "NOT_A_TYPE", "Age"
        });

        DataBrowserMember member = member(libraryUri, memberUri, beanWithColsTable());
        DataBrowserLibraryProvider provider = providerReturning(metaTable);
        IOException ex = assertThrows(IOException.class,
                () -> provider.provideLibraryMemberColumns(member).toList(),
                "an unrecognised type must fail the load rather than yield an untyped column");

        String msg = ex.getMessage();
        assertNotNull(msg);
        assertTrue(msg.contains("NOT_A_TYPE"), "the message must name the value: " + msg);
        assertTrue(msg.contains("AGE"), "the message must name the column: " + msg);
        assertTrue(msg.contains("cols.csv"), "the message must name the source: " + msg);
    }


    /**
     * The internal {@code columnMeta} twin of the test above: both routes into the builder had the
     * same lenient {@code catch}, so fixing only one would have left the defect reachable by the
     * other.
     */
    @Test
    void anUnknownTypeInInternalColumnMetaFailsTheLoad(@TempDir Path tempDir) throws IOException
    {
        URI libraryUri = tempDir.resolve("t.dblib").toUri();
        URI memberUri = tempDir.resolve("dm.csv").toUri();
        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("L")
                .columnMeta(new DataBrowserColumnMetaBean[]
                {
                        DataBrowserColumnMetaBean.builder().uri(memberUri.toString()).name("AGE")
                                .label("Age").type("NOT_A_TYPE").build()
                }).build();

        DataBrowserMember member = member(libraryUri, memberUri, bean);
        DataBrowserLibraryProvider provider = new DataBrowserLibraryProvider();
        IOException ex = assertThrows(IOException.class,
                () -> provider.provideLibraryMemberColumns(member).toList(),
                "an unrecognised type must fail the load rather than yield an untyped column");

        String msg = ex.getMessage();
        assertNotNull(msg);
        assertTrue(msg.contains("NOT_A_TYPE"), "the message must name the value: " + msg);
        assertTrue(msg.contains("AGE"), "the message must name the column: " + msg);
        assertTrue(msg.contains("t.dblib"), "the message must name the library: " + msg);
    }


    /**
     * The {@code loadMetaTable} seam itself still reaches the production factory: for a URI no
     * registered supplier can read, the default implementation answers {@code null} rather than
     * throwing.
     */
    @Test
    void testDefaultSeamsDelegateToTheRegisteredFactories(@TempDir Path tempDir) throws IOException
    {
        DataBrowserLibraryProvider provider = new DataBrowserLibraryProvider();
        URI unknown = tempDir.resolve("nothing.unknown-to-any-provider").toUri();

        // No supplier is registered for this extension, so the factory answers "nothing".
        assertNull(provider.loadMetaTable(unknown));
    }

}
