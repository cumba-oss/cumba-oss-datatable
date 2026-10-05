package net.cumba.datatable.impl.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.cumba.datatable.ExMsgs;
import org.junit.jupiter.api.Test;

/**
 * Row mapping and its bounds for {@link DataTableViewLong}.
 *
 * <p>
 * {@code getRealRow} indexes a {@code long[]} straight after the check, so a bounds check that is
 * one slot too wide still throws — but as an array-index error about an internal buffer rather than
 * about this view's row range, and only by luck of the array ending there. Pinning the message is
 * what keeps the check itself load-bearing.
 * </p>
 */
class DataTableViewLongMutationTest
{

    private static DataTableViewLong view()
    {
        return DataTableViewLong.builder().view(new long[]
        {
                5L, 7L, 9L
        }).build();
    }


    @Test
    void realRowAndDisplayRowAreInverses()
    {
        DataTableViewLong v = view();

        assertEquals(3, v.getRowCount(null));
        for (int display = 0; display < 3; display++)
        {
            long real = v.getRealRow(null, display);
            assertEquals(display, v.getDisplayRow(null, real),
                    "getDisplayRow must invert getRealRow");
        }
        assertEquals(5, v.getRealRow(null, 0));
        assertEquals(9, v.getRealRow(null, 2));
        assertEquals(-1, v.getDisplayRow(null, 6),
                "a real row outside the view has no display row");
    }


    @Test
    void aRowAtTheEndOfTheViewIsReportedAsOutOfRange()
    {
        DataTableViewLong v = view();

        IndexOutOfBoundsException ex = assertThrows(IndexOutOfBoundsException.class,
                () -> v.getRealRow(null, 3));
        assertEquals(ExMsgs.indexOutOfBounds("row", 3L, 0L, 3L), ex.getMessage());

        IndexOutOfBoundsException neg = assertThrows(IndexOutOfBoundsException.class,
                () -> v.getRealRow(null, -1));
        assertEquals(ExMsgs.indexOutOfBounds("row", -1L, 0L, 3L), neg.getMessage());
    }
}
