package net.cumba.datatable.impl.databuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.cumba.datatable.values.DataValueBoolean;
import net.cumba.datatable.values.DataValueMissing;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.IDataValue;
import net.cumba.datatable.values.MissingValue;
import org.junit.jupiter.api.Test;

/**
 * The BOOLEAN read of a {@link DataBufferInt}. Kept apart from {@code DataBufferIntTest}, which is
 * byte-identical in {@code cumba-datatable} and {@code cumba-oss-datatable}: the BOOLEAN arm is
 * this repository's {@code AbstractNumericDataBuffer.createDataValue}, which the internal twin does
 * not have (there a BOOLEAN column is never backed by a {@code DataBufferInt}).
 */
class DataBufferIntBooleanReadTest
{

    @Test
    void testGetDataValueBooleanTrue()
    {
        DataBufferInt buffer = new DataBufferInt();
        buffer.setLongValue(0, 1L);
        IDataValue dv = buffer.getDataValue(0, DataValueType.BOOLEAN);
        assertTrue(dv instanceof DataValueBoolean);
        assertEquals(true, dv.getValue());
    }


    @Test
    void testGetDataValueBooleanFalse()
    {
        DataBufferInt buffer = new DataBufferInt();
        buffer.setLongValue(0, 0L);
        IDataValue dv = buffer.getDataValue(0, DataValueType.BOOLEAN);
        assertTrue(dv instanceof DataValueBoolean);
        assertEquals(false, dv.getValue());
    }


    @Test
    void testGetDataValueBooleanMissingKeepsItsIdentity()
    {
        DataBufferInt buffer = new DataBufferInt();
        buffer.setValue(0, MissingValue.MIS_B);
        buffer.setValue(1, true);
        IDataValue dv = buffer.getDataValue(0, DataValueType.BOOLEAN);
        assertTrue(dv instanceof DataValueMissing);
        assertEquals(MissingValue.MIS_B, dv.getValue());
        assertEquals(true, buffer.getDataValue(1, DataValueType.BOOLEAN).getValue());
    }
}
