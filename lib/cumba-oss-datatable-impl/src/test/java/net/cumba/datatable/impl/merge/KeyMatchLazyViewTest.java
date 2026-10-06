package net.cumba.datatable.impl.merge;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import static net.cumba.datatable.impl.merge.MergeFixtures.table;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.BitSet;
import java.util.concurrent.atomic.AtomicLong;
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


    /**
     * M1 (T2 review): the lazy view is read from whichever thread reads the table (the EDT beside
     * an export, the remote backend), and the matcher's composite-key probe filled ONE scratch
     * array — two readers interleaving their fills read each other's key halves (the reviewer's
     * repro: 1.18 M of 8 M mappings wrong). Every probe of the view now carries its own key. The
     * planted control is the single-threaded answer: every base row maps to its own key row, so a
     * wrong mapping under two readers can only be the race.
     */
    @Test
    void m1_twoThreadsReadingOneLazyView_everyMappingIsRight() throws InterruptedException
    {
        int n = 2000;
        String[] base = new String[n];
        String[] key = new String[n];
        for (int i = 0; i < n; i++)
        {
            base[i] = "S1,U" + i;
            key[i] = "S1,U" + i + ",V" + i;
        }
        IDataTable b = table("AE", "STUDYID,USUBJID", base);
        IDataTable k = table("DM", "STUDYID,USUBJID,AGE", key);
        KeyedMatcher m = KeyedMatcher.of(b, k,
                KeyedMergeSpec.builder().qualifier("DM").key(KeyColumn.of("STUDYID"))
                        .key(KeyColumn.of("USUBJID")).joinType(JoinType.LEFT).build());
        for (int i = 0; i < n; i++)
        {
            assertEquals(i, m.firstMatchOf(i), "the control: confined, every row maps to itself");
        }
        KeyMatchLazyView view = new KeyMatchLazyView(m, n);
        AtomicLong wrong = new AtomicLong();
        Runnable reader = () ->
        {
            for (int it = 0; it < 1000; it++)
            {
                for (int i = 0; i < n; i++)
                {
                    if (view.getRealRow(b, i) != i)
                    {
                        wrong.incrementAndGet();
                    }
                }
            }
        };
        Thread t1 = new Thread(reader);
        Thread t2 = new Thread(reader);
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        assertEquals(0, wrong.get(), "mappings answered wrong under two concurrent readers");
    }
}
