package net.cumba.datatable.values;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MissingValueTest
{

    @Test
    void testGetValue()
    {
        assertEquals(64, MissingValue.MIS.getValue());
    }


    @Test
    void testGetDisplayString()
    {
        assertEquals(".", MissingValue.MIS.getDisplayString());
    }


    @Test
    void testGetDescription()
    {
        assertEquals("SAS Missing .", MissingValue.MIS.getDescription());
    }


    @Test
    void testToString()
    {
        assertEquals(".", MissingValue.MIS.toString());
    }


    @Test
    void testAsDouble()
    {
        double misDouble = MissingValue.MIS.asDouble();
        assertTrue(Double.isNaN(misDouble));
    }


    @Test
    void testForValueInt()
    {
        assertSame(MissingValue.MIS, MissingValue.forValue(64));
    }


    @Test
    void testForValueIntReturnsErrorForInvalidValue()
    {
        assertSame(MissingValue.MIS_ERROR, MissingValue.forValue(999));
        assertSame(MissingValue.MIS_ERROR, MissingValue.forValue(-1));
        assertSame(MissingValue.MIS_ERROR, MissingValue.forValue(0));
    }


    @Test
    void testForValueIntWithDefault()
    {
        assertSame(MissingValue.MIS, MissingValue.forValue(64, MissingValue.MIS_UNKNOWN));
        assertSame(MissingValue.MIS_UNKNOWN, MissingValue.forValue(999, MissingValue.MIS_UNKNOWN));
        assertNull(MissingValue.forValue(999, null));
    }


    @Test
    void testForValueDoubleWithNonNaN()
    {
        // Non-NaN values should return the default
        assertSame(MissingValue.MIS_ERROR, MissingValue.forValue(1.0, MissingValue.MIS_ERROR));
        assertSame(MissingValue.MIS_ERROR, MissingValue.forValue(0.0, MissingValue.MIS_ERROR));
        assertSame(MissingValue.MIS_ERROR, MissingValue.forValue(-123.456, MissingValue.MIS_ERROR));
        assertNull(MissingValue.forValue(42.0, null));
    }


    @Test
    void testRoundTripAllMissingValues()
    {
        // Test that all missing values can be converted to double and back
        for (MissingValue mv : MissingValue.values())
        {
            double asDouble = mv.asDouble();
            assertTrue(Double.isNaN(asDouble), "asDouble() should return NaN for " + mv);

            MissingValue recovered = MissingValue.forValue(asDouble, null);
            assertSame(mv, recovered, "Round trip failed for " + mv);
        }
    }


    @Test
    void testFloatRoundTripAllMissingValues()
    {
        // The NaN payload is shifted to bits [50:43] so it survives double→float→double
        for (MissingValue mv : MissingValue.values())
        {
            double asDouble = mv.asDouble();
            float asFloat = (float) asDouble;
            double backToDouble = asFloat;

            assertTrue(Double.isNaN(backToDouble),
                    "float round-trip should preserve NaN for " + mv);

            MissingValue recovered = MissingValue.forValue(backToDouble, null);
            assertSame(mv, recovered, "float round-trip failed for " + mv);
        }
    }


    @Test
    void testAllNanEncodingsUnique()
    {
        // Each missing value should encode to a unique NaN bit pattern
        MissingValue[] all = MissingValue.values();
        for (int i = 0; i < all.length; i++)
        {
            long bitsI = Double.doubleToRawLongBits(all[i].asDouble());
            for (int j = i + 1; j < all.length; j++)
            {
                long bitsJ = Double.doubleToRawLongBits(all[j].asDouble());
                assertNotEquals(bitsI, bitsJ,
                        "NaN encodings must be unique: " + all[i] + " vs " + all[j]);
            }
        }
    }


    @Test
    void testForValueString()
    {
        assertSame(MissingValue.MIS, MissingValue.forValue("."));
    }


    @Test
    void testForValueStringNotFound()
    {
        assertSame(MissingValue.MIS_ERROR, MissingValue.forValue("UNKNOWN_VALUE"));
    }


    @Test
    void testForValueStringWithDefault()
    {
        assertSame(MissingValue.MIS, MissingValue.forValue(".", MissingValue.MIS_UNKNOWN));
        assertSame(MissingValue.MIS_UNKNOWN,
                MissingValue.forValue("nope", MissingValue.MIS_UNKNOWN));
        assertNull(MissingValue.forValue("nope", null));
    }


    @Test
    void testHashCodeStableIsNonZeroForAllConstants()
    {
        // The zero sentinel used by HASHES_BY_BYTE relies on no constant producing 0.
        for (MissingValue mv : MissingValue.values())
        {
            assertNotEquals(0, mv.hashCodeStable(), "hashCodeStable must not be 0 for " + mv);
        }
    }


    @Test
    void testHashCodeStableAllUnique()
    {
        // Distinct constants must produce distinct hashes to avoid lookup collisions.
        Set<Integer> seen = new HashSet<>();
        for (MissingValue mv : MissingValue.values())
        {
            assertTrue(seen.add(mv.hashCodeStable()),
                    "hashCodeStable must be unique per constant: collision at " + mv);
        }
    }


    @Test
    void testHashCodeForNanRoundTrip()
    {
        // asDouble -> hashCodeForNan should equal the instance's hashCodeStable.
        for (MissingValue mv : MissingValue.values())
        {
            double asDouble = mv.asDouble();
            assertEquals(mv.hashCodeStable(), MissingValue.hashCodeForNan(asDouble),
                    "hashCodeForNan must round-trip for " + mv);
        }
    }


    @Test
    void testHashCodeForNanFloatRoundTrip()
    {
        // NaN-payload double<->float round-trip preserves the encoded byte,
        // so the stable hash must also survive it.
        for (MissingValue mv : MissingValue.values())
        {
            double asDouble = mv.asDouble();
            double afterFloat = (float) asDouble;
            assertEquals(mv.hashCodeStable(), MissingValue.hashCodeForNan(afterFloat),
                    "hashCodeForNan must survive float round-trip for " + mv);
        }
    }


    @Test
    void testHashCodeForNanReturnsZeroForUnknownPayload()
    {
        // A quiet NaN with a payload byte that is not a known missing value should
        // return the 0 sentinel so callers can fall through to the numeric hash path.
        // Use a byte (e.g. 1) that is NOT assigned to any MissingValue constant.
        long unknownBits = 0x7f_f8_00_00_00_00_00_00L | (1L << 43);
        double unknownNan = Double.longBitsToDouble(unknownBits);

        assertTrue(Double.isNaN(unknownNan));
        assertEquals(0, MissingValue.hashCodeForNan(unknownNan));
    }


    @Test
    void testHashesByByteMatchesHashCodeStable()
    {
        // Every known missing has its slot in HASHES_BY_BYTE filled with hashCodeStable().
        for (MissingValue mv : MissingValue.values())
        {
            assertEquals(mv.hashCodeStable(), MissingValue.HASHES_BY_BYTE[mv.getValue() & 0xFF],
                    "HASHES_BY_BYTE slot mismatch for " + mv);
        }
    }
}
