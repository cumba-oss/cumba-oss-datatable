package net.cumba.datatable.provider.sas.xpt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import net.cumba.datatable.values.MissingValue;
import net.cumba.sasutils.xpt.VariableXpt;
import org.junit.jupiter.api.Test;

class XptVarParserTest
{

    // --- ibmToIeee ---

    @Test
    void testIbmToIeeeZero()
    {
        // IBM zero: all bytes 0x00
        byte[] buf = new byte[8];
        assertEquals(0.0, XptVarParser.ibmToIeee(buf, 0, 8));
    }


    @Test
    void testIbmToIeeeNegativeZero()
    {
        // IBM negative zero: first byte 0x80, rest 0x00
        byte[] buf =
        {
                (byte) 0x80, 0, 0, 0, 0, 0, 0, 0
        };
        assertEquals(-0.0, XptVarParser.ibmToIeee(buf, 0, 8));
    }


    @Test
    void testIbmToIeeeMissingDot()
    {
        // SAS missing '.' = 0x2E, mantissa zero
        byte[] buf =
        {
                '.', 0, 0, 0, 0, 0, 0, 0
        };
        double result = XptVarParser.ibmToIeee(buf, 0, 8);
        assertTrue(Double.isNaN(result));
        assertEquals(MissingValue.MIS, MissingValue.forValue(result, MissingValue.MIS_UNKNOWN));
    }


    @Test
    void testIbmToIeeeMissingUnderscore()
    {
        // SAS missing '_' = 0x5F, mantissa zero
        byte[] buf =
        {
                '_', 0, 0, 0, 0, 0, 0, 0
        };
        double result = XptVarParser.ibmToIeee(buf, 0, 8);
        assertTrue(Double.isNaN(result));
        // corej dropped the SAS special-missing constants, so ._ collapses to MIS_UNKNOWN.
        assertEquals(MissingValue.MIS_UNKNOWN,
                MissingValue.forValue(result, MissingValue.MIS_UNKNOWN));
    }


    @Test
    void testIbmToIeeeMissingA()
    {
        // SAS missing 'A' = 0x41, mantissa zero
        byte[] buf =
        {
                'A', 0, 0, 0, 0, 0, 0, 0
        };
        double result = XptVarParser.ibmToIeee(buf, 0, 8);
        assertTrue(Double.isNaN(result));
        // corej dropped the SAS special-missing constants, so .A collapses to MIS_UNKNOWN.
        assertEquals(MissingValue.MIS_UNKNOWN,
                MissingValue.forValue(result, MissingValue.MIS_UNKNOWN));
    }


    @Test
    void testIbmToIeeeMissingZ()
    {
        // SAS missing 'Z' = 0x5A, mantissa zero
        byte[] buf =
        {
                'Z', 0, 0, 0, 0, 0, 0, 0
        };
        double result = XptVarParser.ibmToIeee(buf, 0, 8);
        assertTrue(Double.isNaN(result));
        // corej dropped the SAS special-missing constants, so .Z collapses to MIS_UNKNOWN.
        assertEquals(MissingValue.MIS_UNKNOWN,
                MissingValue.forValue(result, MissingValue.MIS_UNKNOWN));
    }


    @Test
    void testIbmToIeeeMissingUnknown()
    {
        // Non-standard first byte with zero mantissa → MIS_UNKNOWN
        byte[] buf =
        {
                0x01, 0, 0, 0, 0, 0, 0, 0
        };
        double result = XptVarParser.ibmToIeee(buf, 0, 8);
        assertTrue(Double.isNaN(result));
        assertEquals(MissingValue.MIS_UNKNOWN,
                MissingValue.forValue(result, MissingValue.MIS_UNKNOWN));
    }


    @Test
    void testIbmToIeeeOne()
    {
        // IBM 1.0: sign=0, exponent=65 (0x41), mantissa = 0x10000000000000
        // Value: 0.0625 * 16^(65-64) = 0.0625 * 16 = 1.0
        byte[] buf =
        {
                0x41, 0x10, 0, 0, 0, 0, 0, 0
        };
        double result = XptVarParser.ibmToIeee(buf, 0, 8);
        assertEquals(1.0, result, 1e-10);
    }


    @Test
    void testIbmToIeeeNegativeOne()
    {
        // IBM -1.0: sign=1, exponent=65 (0xC1), mantissa = 0x10000000000000
        byte[] buf =
        {
                (byte) 0xC1, 0x10, 0, 0, 0, 0, 0, 0
        };
        double result = XptVarParser.ibmToIeee(buf, 0, 8);
        assertEquals(-1.0, result, 1e-10);
    }


    @Test
    void testIbmToIeeeWithOffset()
    {
        // Put the IBM 1.0 at offset 3
        byte[] buf =
        {
                0, 0, 0, 0x41, 0x10, 0, 0, 0, 0, 0, 0
        };
        double result = XptVarParser.ibmToIeee(buf, 3, 8);
        assertEquals(1.0, result, 1e-10);
    }


    @Test
    void testIbmToIeeeShortLength()
    {
        // IBM 1.0 with length=4 (mantissa in fewer bytes, should be left-shifted)
        byte[] buf =
        {
                0x41, 0x10, 0, 0
        };
        double result = XptVarParser.ibmToIeee(buf, 0, 4);
        assertEquals(1.0, result, 1e-10);
    }


    @Test
    void testIbmToIeee100()
    {
        // IBM 100.0: sign=0, exponent=66 (0x42), mantissa = 0x64000000000000
        // Value: (0x64 / 256.0) * 16^(66-64) = 0.390625 * 256 = 100.0
        byte[] buf =
        {
                0x42, 0x64, 0, 0, 0, 0, 0, 0
        };
        double result = XptVarParser.ibmToIeee(buf, 0, 8);
        assertEquals(100.0, result, 1e-10);
    }

    // --- getValue / parseString / parseDouble ---


    @Test
    void testParseStringValue()
    {
        VariableXpt vxpt = createVar("NAME", (short) 2, (short) 8);
        XptVarParser parser = new XptVarParser(vxpt, 0, StandardCharsets.UTF_8);

        byte[] buf = "TestName".getBytes(StandardCharsets.UTF_8);
        Object val = parser.getValue(buf);
        assertTrue(val instanceof String);
        assertEquals("TestName", val);
    }


    @Test
    void testParseNumericValue()
    {
        VariableXpt vxpt = createVar("AGE", (short) 1, (short) 8);
        XptVarParser parser = new XptVarParser(vxpt, 0, StandardCharsets.UTF_8);

        // IBM 1.0
        byte[] buf =
        {
                0x41, 0x10, 0, 0, 0, 0, 0, 0
        };
        Object val = parser.getValue(buf);
        assertTrue(val instanceof Double);
        assertEquals(1.0, (Double) val, 1e-10);
    }


    @Test
    void testParseStringWithOffset()
    {
        VariableXpt vxpt = createVar("NAME", (short) 2, (short) 4);
        XptVarParser parser = new XptVarParser(vxpt, 8, StandardCharsets.UTF_8);

        byte[] buf = new byte[12];
        System.arraycopy("Test".getBytes(StandardCharsets.UTF_8), 0, buf, 8, 4);
        String val = parser.parseString(buf);
        assertEquals("Test", val);
    }


    private VariableXpt createVar(String name, short typeId, short length)
    {
        VariableXpt vxpt = new VariableXpt();
        vxpt.name = name;
        vxpt.variableTypeId = typeId; // 1=Numeric, 2=Character
        vxpt.length = length;
        vxpt.label = name + " label";
        vxpt.formatTypeString = null;
        vxpt.formatLength = (short) 0;
        vxpt.formatDecimals = (short) 0;
        return vxpt;
    }
}
