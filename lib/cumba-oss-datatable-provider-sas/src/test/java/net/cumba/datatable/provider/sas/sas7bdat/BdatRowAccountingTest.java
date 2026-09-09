package net.cumba.datatable.provider.sas.sas7bdat;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * F-prov-07 pin for {@link BdatTableProvider#checkAllRowsRead}: the expected row count must be
 * derived from the header's <em>declared</em> deleted count. The old code subtracted the count the
 * reader itself found, which made the guard tautological for exactly the failure it exists to catch
 * — a deleted-row misdetection moves the found count and the delivered row count in lockstep.
 */
class BdatRowAccountingTest
{

    @Test
    void misdetectedDeletedRowsAreNotAbsorbed()
    {
        // header: 10 rows, 0 deleted; the reader wrongly classified 4 live rows as deleted and
        // delivered 6. The old guard computed expected = 10 - 4 = 6 and passed silently.
        IOException ex = assertThrows(IOException.class,
                () -> BdatTableProvider.checkAllRowsRead(10L, 0, 4, 6));
        assertTrue(ex.getMessage().contains("Expected=10, found=6"), ex.getMessage());
    }


    @Test
    void undetectedDeclaredDeletionsFail()
    {
        // header declares 2 deleted; the reader found none and delivered all 10
        assertThrows(IOException.class, () -> BdatTableProvider.checkAllRowsRead(10L, 2, 0, 10));
    }


    @Test
    void agreementPasses() throws IOException
    {
        BdatTableProvider.checkAllRowsRead(10L, 2, 2, 8);
        BdatTableProvider.checkAllRowsRead(10L, 0, 0, 10);
    }


    @Test
    void corruptHeaderDeclaringMoreDeletedThanRowsIsStillClamped() throws IOException
    {
        // F-D17: declared deleted > row count clamps to an expectation of 0, not a negative
        BdatTableProvider.checkAllRowsRead(3L, 5, 5, 0);
    }
}
