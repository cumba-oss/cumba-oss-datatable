package net.cumba.datatable.impl.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.IDataTableColumn;
import net.cumba.datatable.impl.CachedDataTableColumn;
import net.cumba.datatable.impl.testsupport.LoggerCapture;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.Test;

/**
 * {@code debugCalcDataSize} exists only to emit its two DEBUG lines - it has no return value and no
 * side effect - so the log content is the whole of its behaviour. This pins both: the per-column
 * line (name, type and that column's estimated bytes) and the closing total, which must be the sum
 * over the cached columns rather than the last column's size.
 */
class AbstractDataTableProviderLoggingTest
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
    void everyCachedColumnIsReportedAndTheTotalIsTheirSum()
    {
        CachedDataTableColumn c0 = new CachedDataTableColumn(0, DataValueType.STRING);
        c0.addElement("x");
        c0.complete();
        CachedDataTableColumn c1 = new CachedDataTableColumn(1, DataValueType.STRING);
        for (int i = 0; i < 50; i++)
        {
            c1.addElement("a-much-longer-value-" + i);
        }
        c1.complete();

        DataTableColumnMeta cm0 = DataTableColumnMeta.builder().index(0).name("SHORT")
                .type(DataValueType.STRING).build();
        DataTableColumnMeta cm1 = DataTableColumnMeta.builder().index(1).name("LONG")
                .type(DataValueType.STRING).build();
        DataTableMeta meta = DataTableMeta.builder().name("t").setColumns(cm0, cm1).rowCount(50)
                .totalRowCount(50).build();

        IDataTable table = mock(IDataTable.class);
        when(table.getMetaData()).thenReturn(meta);
        when(table.getColumnCount()).thenReturn(2);
        when(table.getColumn(0)).thenReturn(c0);
        when(table.getColumn(1)).thenReturn(c1);

        long b0 = c0.getDataBuffer().getEstimatedMemoryBytes();
        long b1 = c1.getDataBuffer().getEstimatedMemoryBytes();

        try (LoggerCapture log = LoggerCapture.attach(AbstractDataTableProvider.class.getName()))
        {
            new TestProvider().debugCalcDataSize(table);

            assertEquals(
                    List.of("SHORT[STRING]: " + b0 + " bytes", "LONG[STRING]: " + b1 + " bytes",
                            "Calculated a total data size of: " + (b0 + b1) + " bytes!"),
                    log.messages());
        }
    }


    @Test
    void aTableWithoutCachedColumnsStillReportsATotalOfZero()
    {
        DataTableColumnMeta cm = DataTableColumnMeta.builder().index(0).name("PLAIN")
                .type(DataValueType.STRING).build();
        DataTableMeta meta = DataTableMeta.builder().name("t").setColumns(cm).rowCount(1)
                .totalRowCount(1).build();

        IDataTable table = mock(IDataTable.class);
        when(table.getMetaData()).thenReturn(meta);
        when(table.getColumnCount()).thenReturn(1);
        when(table.getColumn(0)).thenReturn(mock(IDataTableColumn.class));

        try (LoggerCapture log = LoggerCapture.attach(AbstractDataTableProvider.class.getName()))
        {
            new TestProvider().debugCalcDataSize(table);

            assertEquals(List.of("Calculated a total data size of: 0 bytes!"), log.messages(),
                    "a non-cached column contributes no per-column line and no bytes");
        }
    }
}
