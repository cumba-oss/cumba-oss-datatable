package net.cumba.datatable.provider.cdt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CdtFenceTest
{

    @Test
    void threeDashesIsFence()
    {
        assertTrue(CdtFence.isFence("---"));
    }


    @Test
    void longerDashRunIsFence()
    {
        assertTrue(CdtFence.isFence("----------"));
    }


    @Test
    void twoDashesIsNotFence()
    {
        assertFalse(CdtFence.isFence("--"));
    }


    @Test
    void dashesWithSuffixIsNotFence()
    {
        assertFalse(CdtFence.isFence("--- foo"));
    }


    @Test
    void blankIsNotFence()
    {
        assertFalse(CdtFence.isFence(""));
    }


    @Test
    void nullIsNotFence()
    {
        assertFalse(CdtFence.isFence(null));
    }


    @Test
    void matchingFencesMatch()
    {
        assertTrue(CdtFence.matches("---", "---"));
        assertTrue(CdtFence.matches("-----", "-----"));
    }


    @Test
    void differingLengthFencesDoNotMatch()
    {
        assertFalse(CdtFence.matches("---", "----"));
        assertFalse(CdtFence.matches("-----", "---"));
    }
}
