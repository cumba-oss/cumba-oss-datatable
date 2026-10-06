package net.cumba.datatable.merge;

// OSS-IDENTITY datatable-merge: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** The text-carried join token (MET #11, #14) and the RELREC link spelling. */
class JoinTokenTest
{

    @Test
    void numericParent_canonicalises_characterParent_stripsOnly()
    {
        assertEquals("1", JoinToken.normalize(" 01 ", true));
        assertEquals("1", JoinToken.normalize("1.0", true));
        assertEquals("1", JoinToken.normalize("1E0", true));
        assertEquals("1", JoinToken.normalize("+1", true));
        assertEquals("1d", JoinToken.normalize("1d", true), "not a number: its stripped text");
        assertEquals("01", JoinToken.normalize(" 01", false));
        assertEquals("A1", JoinToken.normalize("  A1", false));
        assertEquals("", JoinToken.normalize("   ", false));
        assertNull(JoinToken.normalize(null, true));
        assertNull(JoinToken.normalize(null, false));
    }


    @Test
    void relrecKey_canonicalisesANumber_keepsAnyOtherValueVerbatim()
    {
        assertEquals("1", JoinToken.relrecKey(" 1.0 "));
        assertEquals(" L1 ", JoinToken.relrecKey(" L1 "),
                "the RELREC site keeps the unstripped text");
        assertEquals("9007199254740993", JoinToken.relrecKey("9007199254740993"));
        assertNull(JoinToken.relrecKey(null));
    }
}
