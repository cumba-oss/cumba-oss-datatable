package net.cumba.datatable.impl.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Pins the fix for F-dt-07: {@code put} must reject an insert into a full table instead of spinning
 * forever in its probe loop, and the table must always keep one EMPTY slot so that {@code get} for
 * an absent key terminates too.
 */
class HashLookupFullGuardTest
{

    @Test
    void putBeyondCapacityThrowsInsteadOfHanging()
    {
        HashLookup lookup = new HashLookup(4, 0.75f);
        assertEquals(16, lookup.capacity(), "minimum capacity");

        // capacity - 1 entries fit: one slot must stay EMPTY so the probe loops terminate
        for (int i = 0; i < 15; i++)
        {
            lookup.put(i, i);
        }
        assertEquals(15, lookup.size());

        assertThrows(IllegalStateException.class, () -> lookup.put(15, 15),
                "an insert into a full table must fail loudly, not spin");
    }


    @Test
    void getForAnAbsentKeyStillTerminatesAtMaximumFill()
    {
        HashLookup lookup = new HashLookup(4, 0.75f);
        for (int i = 0; i < 15; i++)
        {
            lookup.put(i, i);
        }

        // every stored key is found ...
        for (int i = 0; i < 15; i++)
        {
            int expected = i;
            assertEquals(expected, lookup.get(i, aRow -> aRow == expected));
        }
        // ... and an absent key answers -1 instead of probing forever
        assertEquals(-1, lookup.get(999, aRow -> false));
    }


    @Test
    void declaredExpectedEntriesAlwaysFit()
    {
        // the capacity formula guarantees room for expectedEntries entries plus the EMPTY slot,
        // so a caller that declared its count correctly never sees the guard
        HashLookup lookup = new HashLookup(100, 0.75f);
        for (int i = 0; i < 100; i++)
        {
            lookup.put(i, i);
        }
        assertEquals(100, lookup.size());
    }
}
