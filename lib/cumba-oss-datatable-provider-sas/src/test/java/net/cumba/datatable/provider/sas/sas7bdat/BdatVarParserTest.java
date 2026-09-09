package net.cumba.datatable.provider.sas.sas7bdat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import net.cumba.sasutils.Format;
import net.cumba.sasutils.VariableType;
import net.cumba.sasutils.bdat.VariableBdat;
import org.junit.jupiter.api.Test;

class BdatVarParserTest
{

    // --- parseDouble ---

    @Test
    void testParseDoubleBigEndianOne()
    {
        BdatVarParser parser = createNumericParser(0, 8, ByteOrder.BIG_ENDIAN);
        // IEEE 754 1.0 in big-endian: 0x3FF0000000000000
        byte[] buf =
        {
                0x3F, (byte) 0xF0, 0, 0, 0, 0, 0, 0
        };
        assertEquals(1.0, parser.parseDouble(buf), 1e-10);
    }


    @Test
    void testParseDoubleLittleEndianOne()
    {
        BdatVarParser parser = createNumericParser(0, 8, ByteOrder.LITTLE_ENDIAN);
        // IEEE 754 1.0 in little-endian: 0x000000000000F03F
        byte[] buf =
        {
                0, 0, 0, 0, 0, 0, (byte) 0xF0, 0x3F
        };
        assertEquals(1.0, parser.parseDouble(buf), 1e-10);
    }


    @Test
    void testParseDoubleBigEndianNegative()
    {
        BdatVarParser parser = createNumericParser(0, 8, ByteOrder.BIG_ENDIAN);
        // IEEE 754 -1.0 in big-endian: 0xBFF0000000000000
        byte[] buf =
        {
                (byte) 0xBF, (byte) 0xF0, 0, 0, 0, 0, 0, 0
        };
        assertEquals(-1.0, parser.parseDouble(buf), 1e-10);
    }


    @Test
    void testParseDoubleZero()
    {
        BdatVarParser parser = createNumericParser(0, 8, ByteOrder.BIG_ENDIAN);
        byte[] buf = new byte[8];
        assertEquals(0.0, parser.parseDouble(buf), 1e-10);
    }


    @Test
    void testParseDoubleWithOffset()
    {
        BdatVarParser parser = createNumericParser(4, 8, ByteOrder.BIG_ENDIAN);
        // IEEE 754 1.0 at offset 4
        byte[] buf = new byte[12];
        buf[4] = 0x3F;
        buf[5] = (byte) 0xF0;
        assertEquals(1.0, parser.parseDouble(buf), 1e-10);
    }


    @Test
    void testParseDoubleNaNMissing()
    {
        BdatVarParser parser = createNumericParser(0, 8, ByteOrder.BIG_ENDIAN);
        // Standard quiet NaN: 0x7FF8000000000000
        byte[] buf =
        {
                0x7F, (byte) 0xF8, 0, 0, 0, 0, 0, 0
        };
        double result = parser.parseDouble(buf);
        assertTrue(Double.isNaN(result));
    }


    @Test
    void testParseDoubleShortLength()
    {
        // Variable length < 8: remaining bytes should be zero-padded
        BdatVarParser parser = createNumericParser(0, 4, ByteOrder.BIG_ENDIAN);
        // IEEE 754: 0x3FF00000_00000000 — only first 4 bytes
        byte[] buf =
        {
                0x3F, (byte) 0xF0, 0, 0, 0, 0, 0, 0, 0, 0
        };
        double result = parser.parseDouble(buf);
        assertEquals(1.0, result, 1e-10);
    }

    // --- parseString ---


    @Test
    void testParseString()
    {
        BdatVarParser parser = createStringParser(0, 5, ByteOrder.BIG_ENDIAN);
        byte[] buf = "Hello".getBytes(StandardCharsets.UTF_8);
        assertEquals("Hello", parser.parseString(buf));
    }


    @Test
    void testParseStringWithOffset()
    {
        BdatVarParser parser = createStringParser(3, 4, ByteOrder.BIG_ENDIAN);
        byte[] buf = new byte[7];
        System.arraycopy("Test".getBytes(StandardCharsets.UTF_8), 0, buf, 3, 4);
        assertEquals("Test", parser.parseString(buf));
    }

    // --- getValue dispatch ---


    @Test
    void testGetValueNumeric()
    {
        BdatVarParser parser = createNumericParser(0, 8, ByteOrder.BIG_ENDIAN);
        byte[] buf =
        {
                0x3F, (byte) 0xF0, 0, 0, 0, 0, 0, 0
        };
        Object val = parser.getValue(buf);
        assertTrue(val instanceof Double);
        assertEquals(1.0, (Double) val, 1e-10);
    }


    @Test
    void testGetValueCharacter()
    {
        BdatVarParser parser = createStringParser(0, 4, ByteOrder.BIG_ENDIAN);
        byte[] buf = "Test".getBytes(StandardCharsets.UTF_8);
        Object val = parser.getValue(buf);
        assertTrue(val instanceof String);
        assertEquals("Test", val);
    }

    // --- unpackRaw64 / unpackFloat64 ---


    /**
     * F-prov-06 pin: a short numeric must be promoted to the high end of the 64-bit word by
     * {@code (8 - aLength) * 8} BITS (the value is accumulated one byte at a time), matching the
     * sibling decoder {@code XptVarParser.ibmToIeee}. The old code shifted by {@code 8 - aLength},
     * i.e. by bytes-as-bits, leaving the value six orders of magnitude wrong.
     *
     * <p>
     * No production caller reaches {@code aLength < 8} — {@code parseDouble} right-pads the short
     * numeric itself and always calls with 8 — so this direct call IS the pin; do not replace it
     * with a {@code .sas7bdat} fixture.
     * </p>
     */
    @Test
    void testUnpackRaw64ShortLengthPromotesByBitsNotBytes()
    {
        BdatVarParser parser = createNumericParser(0, 8, ByteOrder.BIG_ENDIAN);
        // 0x40 0x59 0x00 promoted by 40 bits = 0x4059000000000000 = 100.0
        byte[] buf =
        {
                0x40, 0x59, 0x00
        };
        assertEquals(0x4059000000000000L, parser.unpackRaw64(buf, 0, 3));
        assertEquals(100.0, Double.longBitsToDouble(parser.unpackRaw64(buf, 0, 3)));
    }


    @Test
    void testUnpackRaw64SevenBytesPromotedByOneByte()
    {
        BdatVarParser parser = createNumericParser(0, 8, ByteOrder.BIG_ENDIAN);
        // 7-byte prefix of IEEE 1.0 (0x3FF0000000000000) promoted by 8 bits
        byte[] buf =
        {
                0x3F, (byte) 0xF0, 0, 0, 0, 0, 0
        };
        assertEquals(0x3FF0000000000000L, parser.unpackRaw64(buf, 0, 7));
        assertEquals(1.0, Double.longBitsToDouble(parser.unpackRaw64(buf, 0, 7)));
    }


    @Test
    void testUnpackRaw64bAllZeros()
    {
        BdatVarParser parser = createNumericParser(0, 8, ByteOrder.BIG_ENDIAN);
        byte[] buf = new byte[8];
        assertEquals(0L, parser.unpackRaw64(buf, 0, 8));
    }


    @Test
    void testUnpackRaw64bAllOnes()
    {
        BdatVarParser parser = createNumericParser(0, 8, ByteOrder.BIG_ENDIAN);
        byte[] buf =
        {
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                (byte) 0xFF, (byte) 0xFF
        };
        assertEquals(-1L, parser.unpackRaw64(buf, 0, 8));
    }


    @Test
    void testUnpackFloat64b()
    {
        BdatVarParser parser = createNumericParser(0, 8, ByteOrder.BIG_ENDIAN);
        // IEEE 754 2.0: 0x4000000000000000
        byte[] buf =
        {
                0x40, 0, 0, 0, 0, 0, 0, 0
        };
        assertEquals(2.0, parser.unpackFloat64(buf, 0, 8), 1e-10);
    }

    // --- getVar ---


    @Test
    void testGetVar()
    {
        VariableBdat vbdat = testVar(VariableType.NUMERIC, 8, 0L);
        BdatVarParser parser = new BdatVarParser(vbdat, StandardCharsets.UTF_8,
                ByteOrder.BIG_ENDIAN);
        assertEquals(vbdat, parser.getBdatVar());
    }

    // --- helpers ---


    private BdatVarParser createNumericParser(int offset, int length, ByteOrder bo)
    {
        VariableBdat vbdat = testVar(VariableType.NUMERIC, length, offset);
        return new BdatVarParser(vbdat, StandardCharsets.UTF_8, bo);
    }


    private BdatVarParser createStringParser(int offset, int length, ByteOrder bo)
    {
        VariableBdat vbdat = testVar(VariableType.CHARACTER, length, offset);
        return new BdatVarParser(vbdat, StandardCharsets.UTF_8, bo);
    }


    private static VariableBdat testVar(VariableType type, int length, long offset)
    {
        return new VariableBdat(null, null, null)
        {

            @Override
            public VariableType getType()
            {
                return type;
            }


            @Override
            public Integer getLength()
            {
                return length;
            }


            @Override
            public Long getOffset()
            {
                return offset;
            }


            @Override
            public String getName()
            {
                return "TEST";
            }


            @Override
            public String getLabel()
            {
                return "";
            }


            @Override
            public Format getFormat()
            {
                return null;
            }
        };
    }
}
