package net.cumba.datatable.impl.support;

import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.CachedDataTableColumn;
import net.cumba.datatable.impl.ColumnCachedDataTable;
import net.cumba.datatable.testkit.IDataTableReadConcurrencyContract;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.MissingValue;
import org.junit.jupiter.api.Test;

/**
 * {@code IDataTable}'s concurrent-reads contract on {@link OverlayDataTable}. Its
 * {@code getMetaData} read the racy cache field twice (a JMM-permitted {@code null} on the second
 * read); it now reads it once. That fix is red-first infeasible on HotSpot, so these cases are a
 * regression guard: the overrides are set before the reads start (a mutation during the reads is
 * outside the contract).
 */
class OverlayDataTableReadConcurrencyTest
{

    private static final int ROWS = 30;

    @Test
    void anOverlayOverAColumnCachedTable()
    {
        OverlayDataTable overlay = new OverlayDataTable(base());
        overlay.setValue(3, "ID", "overridden");
        overlay.addColumn("EXTRA", DataValueType.STRING, "Extra");
        overlay.setValue(5, "EXTRA", "x");
        IDataTableReadConcurrencyContract.check(overlay);
    }


    @Test
    void anEmptyOverlay()
    {
        OverlayDataTable overlay = OverlayDataTable.empty("SS", "Subject status", 6);
        overlay.addColumn("SSSTRESC");
        overlay.setValue(2, "SSSTRESC", "ALIVE");
        IDataTableReadConcurrencyContract.check(overlay);
    }


    private static IDataTable base()
    {
        CachedDataTableColumn id = new CachedDataTableColumn(0, DataValueType.STRING);
        CachedDataTableColumn aval = new CachedDataTableColumn(1, DataValueType.DOUBLE);
        for (int r = 0; r < ROWS; r++)
        {
            id.addElement("S" + r);
            aval.addElement(r == 7 ? MissingValue.MIS_A : (Object) (r * 1.5));
        }
        id.complete();
        aval.complete();
        DataTableMeta meta = DataTableMeta.builder().name("ADLB").columns(new DataTableColumnMeta[]
        {
                DataTableColumnMeta.builder().index(0).name("ID").type(DataValueType.STRING)
                        .build(),
                DataTableColumnMeta.builder().index(1).name("AVAL").type(DataValueType.DOUBLE)
                        .build()
        }).rowCount(ROWS).totalRowCount(ROWS).build();
        return new ColumnCachedDataTable(meta, id, aval);
    }
}
