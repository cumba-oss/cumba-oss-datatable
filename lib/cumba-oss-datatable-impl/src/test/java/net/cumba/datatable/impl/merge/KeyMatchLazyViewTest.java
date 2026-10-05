package net.cumba.datatable.impl.merge;

import static net.cumba.datatable.impl.merge.MergeFixtures.table;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.BitSet;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.merge.Cardinality;
import net.cumba.datatable.merge.JoinType;
import net.cumba.datatable.merge.KeyColumn;
import net.cumba.datatable.merge.KeyedMergeSpec;
import org.junit.jupiter.api.Test;

/** The lazy key-table alignment strategy over the shared matching (MET #10, P-12). */
class KeyMatchLazyViewTest
{

    private static KeyedMatcher matcher(IDataTable aBase, IDataTable aKey, BitSet aMask)
    {
        return KeyedMatcher.of(aBase, aKey,
                KeyedMergeSpec.builder().qualifier("KEY").key(KeyColumn.of("ID"))
                        .joinType(JoinType.LEFT).cardinality(Cardinality.MANY_TO_ONE)
                        .partnerMask(aMask).build());
    }


    @Test
    void probesEachBaseRowOnDemand_cachesTheLastRow_andBoundsChecks()
    {
        IDataTable base = table("BASE", "ID,X", "K2,a", "K1,b", "K9,c", "K1,d");
        IDataTable key = table("KEY", "ID,V", "K1,one", "K2,two", "K3,three");
        KeyMatchLazyView view = new KeyMatchLazyView(matcher(base, key, null), base.getRowCount());
        assertEquals(4, view.getRowCount(base));
        assertEquals(1, view.getRealRow(base, 0));
        assertEquals(1, view.getRealRow(base, 0), "the cached answer is the same answer");
        assertEquals(0, view.getRealRow(base, 1));
        assertEquals(-1, view.getRealRow(base, 2), "no key row: -1");
        assertEquals(0, view.getRealRow(base, 3));
        assertEquals(1, view.getDisplayRow(base, 0), "the first base row mapped to key row 0");
        assertThrows(IndexOutOfBoundsException.class, () -> view.getRealRow(base, 4));
        assertThrows(IndexOutOfBoundsException.class, () -> view.getRealRow(base, -1));
    }


    @Test
    void aMaskedKeyRowIsNoPartner()
    {
        IDataTable base = table("BASE", "ID", "K1");
        IDataTable key = table("KEY", "ID,V", "K1,one");
        BitSet none = new BitSet();
        KeyMatchLazyView view = new KeyMatchLazyView(matcher(base, key, none), 1);
        assertEquals(-1, view.getRealRow(base, 0));
        KeyedMatcher m = matcher(base, key, null);
        assertEquals(0, m.firstMatchOf(0));
        assertEquals(-1, matcher(base, key, none).firstMatchOf(0));
    }
}
