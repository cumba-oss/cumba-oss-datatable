package net.cumba.cdisc.define;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class OidMinterTest
{

    @Test
    void mintsSequentialPerPrefix()
    {
        OidMinter m = new OidMinter(Set.of());
        assertEquals("WC.1", m.mint("WC"));
        assertEquals("WC.2", m.mint("WC"));
        assertEquals("STD.1", m.mint("STD"));
        assertEquals("WC.3", m.mint("WC"));
    }


    @Test
    void skipsExistingOids()
    {
        OidMinter m = new OidMinter(Set.of("WC.1", "WC.2"));
        assertEquals("WC.3", m.mint("WC"));
    }


    @Test
    void mintedOidsNeverCollideAcrossInterleaving()
    {
        OidMinter m = new OidMinter(Set.of("STD.2"));
        assertEquals("STD.1", m.mint("STD"));
        assertEquals("STD.3", m.mint("STD")); // STD.2 reserved -> skipped
    }


    @Test
    void reserveBlocksSubsequentMint()
    {
        OidMinter m = new OidMinter(Set.of());
        m.reserve("WC.1");
        assertTrue(m.isUsed("WC.1"));
        assertEquals("WC.2", m.mint("WC"));
    }


    @Test
    void reserveIgnoresBlank()
    {
        OidMinter m = new OidMinter(Set.of());
        m.reserve("");
        m.reserve(null);
        assertFalse(m.isUsed(""));
        assertEquals("WC.1", m.mint("WC"));
    }

}
