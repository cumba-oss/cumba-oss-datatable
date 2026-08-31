package net.cumba.datatable.impl.databuffer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.Test;

class DefaultDataBufferFactoryTest
{

    private final DefaultDataBufferFactory factory = DefaultDataBufferFactory.INSTANCE;

    @Test
    void testCreateColumnBufferDouble()
    {
        assertTrue(factory.createColumnBuffer(DataValueType.DOUBLE) instanceof DataBufferDouble);
    }


    @Test
    void testCreateColumnBufferLong()
    {
        assertTrue(factory.createColumnBuffer(DataValueType.LONG) instanceof DataBufferLong);
    }


    @Test
    void testCreateColumnBufferBoolean()
    {
        assertTrue(factory.createColumnBuffer(DataValueType.BOOLEAN) instanceof DataBufferInt);
    }


    @Test
    void testCreateColumnBufferString()
    {
        assertTrue(factory.createColumnBuffer(DataValueType.STRING) instanceof DataBufferObject);
    }


    @Test
    void testCreateColumnBufferOther()
    {
        assertTrue(factory.createColumnBuffer(DataValueType.OTHER) instanceof DataBufferObject);
    }


    @Test
    void testCreateColumnBufferMissing()
    {
        assertTrue(factory.createColumnBuffer(DataValueType.MISSING) instanceof DataBufferObject);
    }


    @Test
    void testCreateColumnBufferNull()
    {
        assertTrue(factory.createColumnBuffer(null) instanceof DataBufferObject);
    }


    @Test
    void testCreateForRangeFitsInInt()
    {
        // Both endpoints fit comfortably inside int range -> int-backed buffer.
        assertTrue(factory.createForRange(0, 100) instanceof DataBufferInt);
    }


    @Test
    void testCreateForRangeMinAtIntMinForcesLong()
    {
        // aMin == Integer.MIN_VALUE collides with the int missing sentinel -> long-backed.
        assertTrue(factory.createForRange(Integer.MIN_VALUE, 0) instanceof DataBufferLong);
    }


    @Test
    void testCreateForRangeJustAboveIntMinFitsInt()
    {
        assertTrue(factory.createForRange((long) Integer.MIN_VALUE + 1,
                Integer.MAX_VALUE) instanceof DataBufferInt);
    }


    @Test
    void testCreateForRangeMaxBeyondIntForcesLong()
    {
        assertTrue(
                factory.createForRange(0, (long) Integer.MAX_VALUE + 1) instanceof DataBufferLong);
    }


    @Test
    void testCreateForRangeMaxAtIntMaxFitsInt()
    {
        assertTrue(factory.createForRange(1, Integer.MAX_VALUE) instanceof DataBufferInt);
    }


    @Test
    void testCreateForRangeInvertedThrows()
    {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> factory.createForRange(10, 5));
        assertTrue(ex.getMessage().contains("10"));
        assertTrue(ex.getMessage().contains("5"));
    }
}
