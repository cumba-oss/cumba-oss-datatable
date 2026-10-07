package net.cumba.datatable.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;
import net.cumba.datatable.ColumnContent;
import net.cumba.datatable.ColumnContent.Fill;
import net.cumba.datatable.ColumnContent.Uniformity;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.impl.support.OverlayDataTable;
import net.cumba.datatable.impl.view.ViewDataTable;
import net.cumba.datatable.values.DataValueSupport;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.MissingValue;
import org.junit.jupiter.api.Test;

/**
 * PACK 4 review of {@code PLAN-present-empty-column-fold} — the mutators that must drop the cached
 * column content: {@link OverlayDataTable}'s cell and structure mutators (H1),
 * {@link ViewDataTable}'s unavailable-value setters (L2), and {@link EditableDataTable}'s event
 * handler invalidating AFTER the rebuild as well as before it (M1). Each case carries its control:
 * a re-read with no mutation in between returns the cached instance, so a test that passed against
 * a mutator which does not invalidate would have failed its control.
 */
class ColumnContentInvalidationTest
{

    private static ColumnContent of(Fill aFill, Uniformity aUniformity)
    {
        return new ColumnContent(aFill, aUniformity);
    }


    private static OverlayDataTable overlay()
    {
        OverlayDataTable t = OverlayDataTable.empty("T", "T", 2);
        t.addColumn("C");
        t.addColumn("D");
        t.setValue(0, "C", "a");
        t.setValue(1, "C", "b");
        t.setValue(0, "D", "");
        t.setValue(1, "D", "");
        return t;
    }

    // ---- H1: OverlayDataTable -----------------------------------------------------------------


    @Test
    void anOverlayCellEditAfterAReadIsSeen()
    {
        OverlayDataTable t = overlay();
        ColumnContent before = t.getColumnContent(0);
        assertEquals(of(Fill.FILLED, Uniformity.MULTI_VALUES), before);
        assertSame(before, t.getColumnContent(0), "control: no mutation, the cached instance");
        t.setValue(1, 0, "a");
        assertEquals(of(Fill.FILLED, Uniformity.SINGLE_VALUE), t.getColumnContent(0),
                "setValue(row, column) drops the cache");
        t.setDataValue(1, 0, DataValueSupport.getAsDataValue("", DataValueType.STRING));
        assertEquals(of(Fill.FILLED, Uniformity.MULTI_VALUES), t.getColumnContent(0),
                "setDataValue drops the cache");
        t.setValue(0, "C", "");
        assertEquals(of(Fill.EMPTY, Uniformity.SINGLE_VALUE), t.getColumnContent(0),
                "setValue(row, name) drops the cache");
    }


    @Test
    void anOverlayColumnRemovalAfterAReadIsSeen()
    {
        OverlayDataTable t = overlay();
        ColumnContent c = t.getColumnContent(0);
        ColumnContent d = t.getColumnContent(1);
        assertEquals(of(Fill.FILLED, Uniformity.MULTI_VALUES), c);
        assertEquals(of(Fill.EMPTY, Uniformity.SINGLE_VALUE), d);
        assertSame(c, t.getColumnContent(0), "control: the cached instance before the removal");
        assertTrue(t.removeColumn("C"));
        assertEquals(1, t.getColumnCount());
        assertEquals(of(Fill.EMPTY, Uniformity.SINGLE_VALUE), t.getColumnContent(0),
                "column 0 is now D: the stale answer for C must not be served");
        assertEquals(1, t.getColumnContents().length);
    }


    @Test
    void anOverlayStructureChangeAfterAReadIsSeen()
    {
        OverlayDataTable t = overlay();
        ColumnContent c = t.getColumnContent(0);
        assertSame(c, t.getColumnContent(0), "control");
        long before = t.contentGeneration();
        t.renameColumn("C", "C2");
        assertTrue(t.contentGeneration() > before, "renameColumn invalidates");
        before = t.contentGeneration();
        int idx = t.addColumn("E");
        assertTrue(t.contentGeneration() > before, "addColumn invalidates");
        assertEquals(ColumnContent.Fill.MISSING, t.getColumnContent(idx).fill(),
                "the new column reads as all-missing");
        before = t.contentGeneration();
        t.setColumnType("C2", DataValueType.DOUBLE);
        assertTrue(t.contentGeneration() > before, "setColumnType invalidates");
    }

    // ---- L2: ViewDataTable's unavailable values ---------------------------------------------


    @Test
    void aViewsUnavailableValueSettersInvalidate()
    {
        OverlayDataTable source = overlay();
        ViewDataTable view = new ViewDataTable(source, null, (Stream<DataTableColumnMeta>) null);
        ColumnContent c = view.getColumnContent(0);
        assertSame(c, view.getColumnContent(0), "control");
        long before = view.contentGeneration();
        view.setValueUnavailable(MissingValue.MIS_A);
        assertTrue(view.contentGeneration() > before, "setValueUnavailable invalidates");
        before = view.contentGeneration();
        view.setColumnUnavailable(0, DataValueSupport.getAsDataValue("", DataValueType.STRING));
        assertTrue(view.contentGeneration() > before, "setColumnUnavailable invalidates");
    }
}
