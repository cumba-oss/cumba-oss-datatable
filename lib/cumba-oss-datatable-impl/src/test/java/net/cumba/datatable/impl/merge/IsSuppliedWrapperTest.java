package net.cumba.datatable.impl.merge;

import static net.cumba.datatable.impl.merge.MergeFixtures.request;
import static net.cumba.datatable.impl.merge.MergeFixtures.table;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.databuffer.DataBufferFactory;
import net.cumba.datatable.impl.databuffer.IDataBufferNumeric;
import net.cumba.datatable.impl.support.OverlayDataTable;
import net.cumba.datatable.impl.view.DataTableViewBuffer;
import net.cumba.datatable.impl.view.MergeDataTable;
import net.cumba.datatable.impl.view.UnionDataTable;
import net.cumba.datatable.impl.view.ViewDataTable;
import net.cumba.datatable.merge.JoinType;
import net.cumba.datatable.merge.KeyColumn;
import net.cumba.datatable.merge.KeyedMergeSpec;
import net.cumba.datatable.merge.MergedTable;
import net.cumba.datatable.merge.MergedTables;
import org.junit.jupiter.api.Test;

/**
 * The {@code isSupplied} marker survives every wrapper that overrides it ({@code
 * PLAN-merged-evaluation-table} §3.1: "a wrapper that does not delegate answers wrongly without
 * error, so a test wraps a view in each wrapper and asserts the marker survives") — one test per
 * override: {@code ViewDataTable}, {@code MergeDataTable}, {@code UnionDataTable},
 * {@code OverlayDataTable} and the default — the wrappers both twins carry. A twin's further
 * wrappers have their rows in a sibling test of that twin.
 */
class IsSuppliedWrapperTest
{

    /** A merged table with one supplied and one not-supplied cell in column {@code DM.SEX}. */
    private static MergedTable view()
    {
        IDataTable ae = table("AE", "USUBJID,AETERM", "01-001,HEADACHE", "01-003,RASH");
        IDataTable dm = table("DM", "USUBJID,SEX", "01-001,M");
        return MergedTables.build(request(ae, dm).keyed(KeyedMergeSpec.builder().qualifier("DM")
                .key(KeyColumn.of("USUBJID")).joinType(JoinType.LEFT).build()).build());
    }


    private static int sex(IDataTable aTable)
    {
        return aTable.getMetaData().getColumnIndex("DM.SEX");
    }


    @Test
    void aPlainTableAnswersTrueForEveryValidCell_andBoundsCheck()
    {
        IDataTable ae = table("AE", "USUBJID,AETERM", "01-001,HEADACHE");
        assertTrue(ae.isSupplied(0, 0));
        assertTrue(ae.isSupplied(0, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> ae.isSupplied(1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> ae.isSupplied(0, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> ae.isSupplied(-1, 0));
    }


    @Test
    void theMergedTableItself()
    {
        MergedTable t = view();
        assertTrue(t.isSupplied(0, sex(t)));
        assertFalse(t.isSupplied(1, sex(t)));
        assertTrue(t.isSupplied(1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> t.isSupplied(0, 99));
    }


    @Test
    void viewDataTable_delegatesThroughItsRowAndColumnMaps()
    {
        MergedTable t = view();
        // a view that shows row 1 first, then row 0, and only the DM.SEX column
        IDataBufferNumeric map = DataBufferFactory.get().createForRange(-1, 1);
        map.addValue(1);
        map.addValue(0);
        map.addValue(-1);
        DataTableColumnMeta sexMeta = t.getMetaData().getColumn(sex(t));
        ViewDataTable v = new ViewDataTable(t, new DataTableViewBuffer(map), Stream.of(sexMeta));
        assertFalse(v.isSupplied(0, 0), "row 1 of the merge is not supplied");
        assertTrue(v.isSupplied(1, 0), "row 0 of the merge is supplied");
        assertFalse(v.isSupplied(2, 0), "a view row with no backing row is not supplied");
        assertEquals("", v.getValue(2, 0), "the value at the marker is the type default");
        assertThrows(IndexOutOfBoundsException.class, () -> v.isSupplied(0, 1));
        // the 3-argument constructor keeps every column when none is given
        ViewDataTable all = new ViewDataTable(t, new DataTableViewBuffer(map), null);
        assertEquals(t.getColumnCount(), all.getColumnCount());
        assertFalse(all.isSupplied(0, sex(t)));
        assertTrue(all.isSupplied(1, sex(t)));
    }


    @Test
    void mergeDataTable_delegatesToThePartHoldingTheColumn()
    {
        MergedTable t = view();
        IDataTable extra = table("X", "XVAL", "a", "b");
        MergeDataTable m = new MergeDataTable(extra, t);
        int sexInMerge = 1 + sex(t);
        assertTrue(m.isSupplied(0, sexInMerge));
        assertFalse(m.isSupplied(1, sexInMerge));
        assertTrue(m.isSupplied(1, 0));
        assertEquals(1, m.partOfColumn(sexInMerge));
        assertEquals(0, m.partOfColumn(0));
        assertEquals(2, m.partCount());
        assertEquals(extra, m.part(0));
        assertEquals(t, m.part(1));
        assertThrows(IndexOutOfBoundsException.class, () -> m.part(2));
        assertThrows(IndexOutOfBoundsException.class, () -> m.partOfColumn(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> m.isSupplied(0, 99));
        assertThrows(IndexOutOfBoundsException.class, () -> m.getValue(0, 99));
        assertThrows(IndexOutOfBoundsException.class, () -> m.getDataValue(0, 99));
        assertEquals("M", m.getValue(0, sexInMerge));
        assertEquals("a", m.getDataValue(0, 0).getValueAsString());
    }


    @Test
    void unionDataTable_delegatesToTheMemberHoldingTheRow()
    {
        MergedTable t = view();
        IDataTable other = table("AE2", "USUBJID,AETERM", "01-009,X");
        UnionDataTable u = new UnionDataTable("AEALL", t, other);
        int sexInUnion = u.getMetaData().getColumnIndex("DM.SEX");
        assertTrue(u.isSupplied(0, sexInUnion));
        assertFalse(u.isSupplied(1, sexInUnion), "the merged member's marker survives");
        assertTrue(u.isSupplied(2, sexInUnion),
                "a column the member lacks is a stored missing value, not a marker");
        assertTrue(u.isSupplied(2, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> u.isSupplied(3, 0));
    }


    /**
     * L1 (W1 review): {@code OverlayDataTable} (both twins) did not override the marker and
     * answered {@code true} for a not-supplied cell; it now delegates through its column mapping,
     * and a cell it overrides, or a column it adds, is a stored value.
     */
    @Test
    void overlayDataTable_delegatesThroughItsColumnMapping_anOverrideIsSupplied()
    {
        MergedTable t = view();
        OverlayDataTable o = new OverlayDataTable(t);
        assertTrue(o.isSupplied(0, sex(t)));
        assertFalse(o.isSupplied(1, sex(t)), "the marker survives the overlay");
        o.setValue(1, sex(t), "F");
        assertTrue(o.isSupplied(1, sex(t)), "an overridden cell is a stored value");
        int added = o.addColumn("EXTRA");
        assertTrue(o.isSupplied(1, added), "an added column has no partner to lack");
        assertThrows(IndexOutOfBoundsException.class, () -> o.isSupplied(2, 0));
    }
}
