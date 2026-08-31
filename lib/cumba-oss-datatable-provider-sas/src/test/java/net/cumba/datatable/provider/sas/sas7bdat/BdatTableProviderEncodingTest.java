package net.cumba.datatable.provider.sas.sas7bdat;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BdatTableProviderEncodingTest
{

    // ==================== getEncodingName ====================

    @ParameterizedTest
    @CsvSource(
    {
            "0x14, UTF-8", "0x1C, US-ASCII", "0x1D, ISO-8859-1", "0x3E, windows-1252",
            "0x86, EUC-JP", "0x8A, Shift_JIS", "0x8C, EUC-KR", "0xCD, GB18030", "0xF2, ISO-8859-13"
    })
    void testGetEncodingNameKnown(String hex, String expected)
    {
        int encodingByte = Integer.decode(hex);
        assertEquals(expected, BdatTableProvider.getEncodingName(encodingByte));
    }


    @Test
    void testGetEncodingNameUnmappedReturnsNull()
    {
        // F-D21: unmapped bytes return null so callers can log an ERROR naming the raw byte
        // before falling back to a safe charset. (Previous behaviour was a silent
        // "windows-1252" fallback that hid encoding diagnostic information.)
        assertNull(BdatTableProvider.getEncodingName(0x00));
    }


    @Test
    void testGetEncodingNameZeroReturnsNull()
    {
        assertNull(BdatTableProvider.getEncodingName(0));
    }


    @Test
    void testGetEncodingNameUnmappedHighByteReturnsNull()
    {
        // 0xFF is not in the SAS encoding table — must surface as null.
        assertNull(BdatTableProvider.getEncodingName(0xFF));
    }

    // ==================== SAS_CHARACTER_ENCODINGS Map ====================


    @Test
    void testSasCharacterEncodingsNotNull()
    {
        assertNotNull(BdatTableProvider.SAS_CHARACTER_ENCODINGS);
    }


    @Test
    void testSasCharacterEncodingsImmutable()
    {
        assertThrows(UnsupportedOperationException.class, () ->
        {
            BdatTableProvider.SAS_CHARACTER_ENCODINGS.put((byte) 0x01, "TEST");
        });
    }


    @Test
    void testSasCharacterEncodingsContainsUtf8()
    {
        assertEquals("UTF-8", BdatTableProvider.SAS_CHARACTER_ENCODINGS.get((byte) 0x14));
    }


    @Test
    void testSasCharacterEncodingsSize()
    {
        // Should have a significant number of encodings
        assertTrue(BdatTableProvider.SAS_CHARACTER_ENCODINGS.size() > 80);
    }

    // ==================== resolveCharset (F-D21) ====================


    @Test
    void resolveCharset_knownAndSupportedReturnsThatCharset()
    {
        // UTF-8 → known and JVM-supported
        java.nio.charset.Charset cs = BdatTableProvider.resolveCharset(0x14);
        assertEquals(java.nio.charset.StandardCharsets.UTF_8, cs);
    }


    @Test
    void resolveCharset_unmappedFallsBackToIso8859_1()
    {
        // F-D21: unmapped byte → ERROR log + ISO-8859-1 fallback
        java.nio.charset.Charset cs = BdatTableProvider.resolveCharset(0x00);
        assertEquals(java.nio.charset.StandardCharsets.ISO_8859_1, cs);
    }


    @Test
    void resolveCharset_iso88591IsItselfWhenRequested()
    {
        // 0x1D maps to ISO-8859-1; should round-trip.
        java.nio.charset.Charset cs = BdatTableProvider.resolveCharset(0x1D);
        assertEquals(java.nio.charset.StandardCharsets.ISO_8859_1, cs);
    }
}
