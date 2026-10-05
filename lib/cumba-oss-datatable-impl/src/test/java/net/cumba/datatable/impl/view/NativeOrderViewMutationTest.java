package net.cumba.datatable.impl.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * {@link NativeOrderView} is the identity mapping. Its "no table" short circuit must still answer
 * with the row it was given — returning 0 there would silently map every row of an unknown table
 * onto the first one.
 */
class NativeOrderViewMutationTest
{

    @Test
    void withoutATableTheIdentityMappingStillHoldsBothWays()
    {
        assertEquals(7L, NativeOrderView.INSTANCE.getRealRow(null, 7L));
        assertEquals(7L, NativeOrderView.INSTANCE.getDisplayRow(null, 7L));
        assertEquals(0L, NativeOrderView.INSTANCE.getRowCount(null));
    }
}
