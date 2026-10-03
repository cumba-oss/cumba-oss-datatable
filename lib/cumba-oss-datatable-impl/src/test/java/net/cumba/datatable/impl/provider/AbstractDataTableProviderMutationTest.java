package net.cumba.datatable.impl.provider;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.stream.Stream;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.IDataTableColumn;
import net.cumba.datatable.impl.CachedDataTableColumn;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.Test;

/**
 * Mutation-hardening tests for {@link AbstractDataTableProvider}: the column-completion join must
 * surface async failures, and the debug size calculation must skip non-cached columns.
 */
class AbstractDataTableProviderMutationTest
{

    private static final class TestProvider extends AbstractDataTableProvider
    {

        @Override
        public List<FileInfo> getSupportedFileInfos()
        {
            return List.of();
        }


        @Override
        public IDataTable provide(URI aUri, FileInfo aFileInfo) throws IOException
        {
            return null;
        }


        @Override
        public DataTableMeta provideMetaData(URI aUri, FileInfo aFileInfo) throws IOException
        {
            return null;
        }
    }

    @Test
    void completeParsedColumnsSurfacesACompletionFailure()
    {
        CachedDataTableColumn bad = new CachedDataTableColumn(0, DataValueType.STRING)
        {

            @Override
            public void complete()
            {
                throw new IllegalStateException("complete() boom");
            }
        };

        TestProvider provider = new TestProvider();
        // Without the join the async failure would be swallowed and the caller would proceed
        // with an uncompleted column.
        assertThrows(Exception.class, () -> provider.completeParsedColumns(Stream.of(bad)));
    }


    @Test
    void debugCalcDataSizeSkipsNonCachedColumns()
    {
        DataTableColumnMeta cm0 = DataTableColumnMeta.builder().index(0).name("PLAIN")
                .type(DataValueType.STRING).build();
        DataTableColumnMeta cm1 = DataTableColumnMeta.builder().index(1).name("CACHED")
                .type(DataValueType.STRING).build();
        DataTableMeta meta = DataTableMeta.builder().name("t").setColumns(cm0, cm1).rowCount(1)
                .totalRowCount(1).build();

        CachedDataTableColumn cached = new CachedDataTableColumn(1, DataValueType.STRING);
        cached.addElement("x");
        cached.complete();

        IDataTable table = mock(IDataTable.class);
        when(table.getMetaData()).thenReturn(meta);
        when(table.getColumnCount()).thenReturn(2);
        when(table.getColumn(0)).thenReturn(mock(IDataTableColumn.class));
        when(table.getColumn(1)).thenReturn(cached);

        TestProvider provider = new TestProvider();
        assertDoesNotThrow(() -> provider.debugCalcDataSize(table));
    }
}
