package net.cumba.datatable.provider.sas.xpt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import net.cumba.sasutils.xpt.VariableXpt;
import org.junit.jupiter.api.Test;

class XptObservationTest
{

    @Test
    void testGetValueString()
    {
        VariableXpt strVar = createVar("NAME", (short) 2, (short) 4);
        XptVarParser parser = new XptVarParser(strVar, 0, StandardCharsets.UTF_8);
        List<XptVarParser> parsers = Arrays.asList(parser);

        byte[] buf = "Test".getBytes(StandardCharsets.UTF_8);
        XptObservation obs = new XptObservation(parsers, buf);

        Object val = obs.getValue(0);
        assertTrue(val instanceof String);
        assertEquals("Test", val);
    }


    @Test
    void testGetValueNumeric()
    {
        VariableXpt numVar = createVar("AGE", (short) 1, (short) 8);
        XptVarParser parser = new XptVarParser(numVar, 0, StandardCharsets.UTF_8);
        List<XptVarParser> parsers = Arrays.asList(parser);

        // IBM 1.0
        byte[] buf =
        {
                0x41, 0x10, 0, 0, 0, 0, 0, 0
        };
        XptObservation obs = new XptObservation(parsers, buf);

        Object val = obs.getValue(0);
        assertTrue(val instanceof Double);
        assertEquals(1.0, (Double) val, 1e-10);
    }


    @Test
    void testGetParsers()
    {
        VariableXpt vxpt = createVar("X", (short) 2, (short) 4);
        XptVarParser parser = new XptVarParser(vxpt, 0, StandardCharsets.UTF_8);
        List<XptVarParser> parsers = Arrays.asList(parser);

        XptObservation obs = new XptObservation(parsers, new byte[4]);
        assertNotNull(obs.getParsers());
        assertEquals(1, obs.getParsers().size());
    }


    @Test
    void testMultipleColumns()
    {
        // String column at offset 0, length 4 + Numeric column at offset 4, length 8
        VariableXpt strVar = createVar("NAME", (short) 2, (short) 4);
        VariableXpt numVar = createVar("AGE", (short) 1, (short) 8);
        XptVarParser strParser = new XptVarParser(strVar, 0, StandardCharsets.UTF_8);
        XptVarParser numParser = new XptVarParser(numVar, 4, StandardCharsets.UTF_8);
        List<XptVarParser> parsers = Arrays.asList(strParser, numParser);

        byte[] buf = new byte[12];
        System.arraycopy("Test".getBytes(StandardCharsets.UTF_8), 0, buf, 0, 4);
        // IBM 1.0 at offset 4
        buf[4] = 0x41;
        buf[5] = 0x10;

        XptObservation obs = new XptObservation(parsers, buf);

        assertEquals("Test", obs.getValue(0));
        assertEquals(1.0, (Double) obs.getValue(1), 1e-10);
    }


    private VariableXpt createVar(String name, short typeId, short length)
    {
        VariableXpt vxpt = new VariableXpt();
        vxpt.name = name;
        vxpt.variableTypeId = typeId;
        vxpt.length = length;
        return vxpt;
    }
}
