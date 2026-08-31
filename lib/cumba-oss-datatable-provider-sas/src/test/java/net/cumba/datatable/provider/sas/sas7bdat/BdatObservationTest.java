package net.cumba.datatable.provider.sas.sas7bdat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import net.cumba.sasutils.Format;
import net.cumba.sasutils.VariableType;
import net.cumba.sasutils.bdat.VariableBdat;
import org.junit.jupiter.api.Test;

class BdatObservationTest
{

    @Test
    void testGetValueString()
    {
        VariableBdat vbdat = testVar(VariableType.CHARACTER, 4, 0L);
        BdatVarParser parser = new BdatVarParser(vbdat, StandardCharsets.UTF_8,
                ByteOrder.BIG_ENDIAN);
        List<BdatVarParser> parsers = Arrays.asList(parser);

        byte[] buf = "Test".getBytes(StandardCharsets.UTF_8);
        BdatObservation obs = new BdatObservation(parsers, buf);

        Object val = obs.getValue(0);
        assertTrue(val instanceof String);
        assertEquals("Test", val);
    }


    @Test
    void testGetParsers()
    {
        VariableBdat vbdat = testVar(VariableType.CHARACTER, 4, 0L);
        BdatVarParser parser = new BdatVarParser(vbdat, StandardCharsets.UTF_8,
                ByteOrder.BIG_ENDIAN);
        List<BdatVarParser> parsers = Arrays.asList(parser);

        BdatObservation obs = new BdatObservation(parsers, new byte[4]);
        assertNotNull(obs.getParsers());
        assertEquals(1, obs.getParsers().size());
    }


    @Test
    void testGetValueNumeric()
    {
        VariableBdat vbdat = testVar(VariableType.NUMERIC, 8, 0L);
        BdatVarParser parser = new BdatVarParser(vbdat, StandardCharsets.UTF_8,
                ByteOrder.BIG_ENDIAN);
        List<BdatVarParser> parsers = Arrays.asList(parser);

        // IEEE 754 1.0 in big-endian: 0x3FF0000000000000
        byte[] buf =
        {
                0x3F, (byte) 0xF0, 0, 0, 0, 0, 0, 0
        };
        BdatObservation obs = new BdatObservation(parsers, buf);

        Object val = obs.getValue(0);
        assertTrue(val instanceof Double);
        assertEquals(1.0, (Double) val, 1e-10);
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
