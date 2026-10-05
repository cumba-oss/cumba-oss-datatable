package net.cumba.datatable.impl.merge;

import static net.cumba.datatable.impl.merge.MergeFixtures.request;
import static net.cumba.datatable.impl.merge.MergeFixtures.table;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.ServiceLoader;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.merge.MergedTable;
import net.cumba.datatable.merge.MergedTableFactory;
import net.cumba.datatable.merge.MergedTables;
import org.junit.jupiter.api.Test;

/**
 * The factory is reachable through the API module alone (MQ13): the service registration resolves
 * to this module's factory, and a request with no merges at all is the primary, unchanged.
 */
class MergedTablesServiceTest
{

    @Test
    void theServiceLoaderFindsThisModulesFactory()
    {
        List<MergedTableFactory> found = ServiceLoader.load(MergedTableFactory.class).stream()
                .map(ServiceLoader.Provider::get).toList();
        assertEquals(1, found.size());
        assertEquals(DefaultMergedTableFactory.class, found.get(0).getClass());
    }


    @Test
    void aRequestWithoutMerges_isThePrimaryAlone()
    {
        IDataTable ae = table("AE", "USUBJID,AETERM", "01-001,HEADACHE");
        MergedTable t = MergedTables.build(request(ae).build());
        assertEquals(1, t.parts().size());
        assertSame(ae, t.primary());
        assertEquals(2, t.getColumnCount());
        assertEquals("HEADACHE", t.getValue(0, 1));
        assertFalse(t.isExpanded());
        assertTrue(t.issues().isEmpty());
        assertTrue(t.unresolvedDatasets().isEmpty());
        assertTrue(t.suppQnams().isEmpty());
        assertEquals(0, t.getRealRowIndex(0));
        assertEquals("AE+", t.getMetaData().getName());
        assertThrows(IndexOutOfBoundsException.class, () -> t.getRealRowIndex(1));
    }


    @Test
    void anEmptyPrimary_buildsAnEmptyTable()
    {
        IDataTable ae = table("AE", "USUBJID,AETERM");
        IDataTable dm = table("DM", "USUBJID,SEX", "01-001,M");
        MergedTable t = MergedTables.build(request(ae, dm)
                .keyed(net.cumba.datatable.merge.KeyedMergeSpec.builder().qualifier("DM")
                        .key(net.cumba.datatable.merge.KeyColumn.of("USUBJID")).build())
                .supp(net.cumba.datatable.merge.SuppMergeSpec.builder().build()).build());
        assertEquals(0, t.getRowCount());
        assertEquals(4, t.getColumnCount());
        assertEquals(List.of("SUPPAE"), t.unresolvedDatasets());
    }
}
