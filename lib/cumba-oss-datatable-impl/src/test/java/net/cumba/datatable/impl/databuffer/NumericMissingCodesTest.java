package net.cumba.datatable.impl.databuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.cumba.datatable.values.MissingValue;
import org.junit.jupiter.api.Test;

class NumericMissingCodesTest
{

    @Test
    void testEveryMissingValueRoundTripsThroughItsCode()
    {
        for (MissingValue mv : MissingValue.values())
        {
            assertSame(mv, NumericMissingCodes.missingFor(NumericMissingCodes.codeFor(mv)),
                    mv.name());
        }
    }


    @Test
    void testOnlyNullAndMisMapToTheEmptyCode()
    {
        assertEquals(NumericMissingCodes.MIS_CODE, NumericMissingCodes.codeFor(null));
        assertEquals(NumericMissingCodes.MIS_CODE, NumericMissingCodes.codeFor(MissingValue.MIS));
        for (MissingValue mv : MissingValue.values())
        {
            assertNotEquals(NumericMissingCodes.PRESENT_MIN, NumericMissingCodes.codeFor(mv),
                    mv.name());
            if (mv != MissingValue.MIS)
            {
                assertNotEquals(NumericMissingCodes.MIS_CODE, NumericMissingCodes.codeFor(mv),
                        mv.name());
            }
        }
    }


    @Test
    void testMisCodeNeverAllocates()
    {
        NumericMissingCodes codes = new NumericMissingCodes();
        codes.set(3, NumericMissingCodes.MIS_CODE, 16);
        codes.clear(3);
        codes.resize(32);
        assertFalse(codes.isAllocated());
        assertEquals(NumericMissingCodes.MIS_CODE, codes.get(3));
        assertEquals(0L, codes.estimatedBytes(10));
    }


    @Test
    void testFirstRealCodeAllocatesAndIsReadBack()
    {
        NumericMissingCodes codes = new NumericMissingCodes();
        byte a = NumericMissingCodes.codeFor(MissingValue.MIS_A);
        codes.set(2, a, 8);
        assertTrue(codes.isAllocated());
        assertEquals(a, codes.get(2));
        assertEquals(NumericMissingCodes.MIS_CODE, codes.get(1));
        assertEquals(10L, codes.estimatedBytes(10));
    }


    @Test
    void testClearResetsASlot()
    {
        NumericMissingCodes codes = new NumericMissingCodes();
        codes.set(0, NumericMissingCodes.PRESENT_MIN, 4);
        codes.clear(0);
        assertEquals(NumericMissingCodes.MIS_CODE, codes.get(0));
        // Beyond the table: a no-op, and a read answers the empty code.
        codes.clear(100);
        assertEquals(NumericMissingCodes.MIS_CODE, codes.get(100));
    }


    @Test
    void testSetBeyondTheTableGrowsIt()
    {
        NumericMissingCodes codes = new NumericMissingCodes();
        codes.set(0, NumericMissingCodes.PRESENT_MIN, 2);
        codes.set(9, NumericMissingCodes.codeFor(MissingValue.MIS_ERROR), 16);
        assertEquals(NumericMissingCodes.PRESENT_MIN, codes.get(0));
        assertEquals(NumericMissingCodes.codeFor(MissingValue.MIS_ERROR), codes.get(9));
    }


    @Test
    void testResizeKeepsAndTruncates()
    {
        NumericMissingCodes codes = new NumericMissingCodes();
        byte z = NumericMissingCodes.codeFor(MissingValue.MIS_Z);
        codes.set(1, z, 4);
        codes.set(3, z, 4);
        codes.resize(2);
        assertEquals(z, codes.get(1));
        assertEquals(NumericMissingCodes.MIS_CODE, codes.get(3));
        codes.resize(8);
        assertEquals(z, codes.get(1));
        assertEquals(NumericMissingCodes.MIS_CODE, codes.get(3));
    }
}
