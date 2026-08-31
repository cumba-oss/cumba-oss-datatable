package net.cumba.datatable.impl.databuffer;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DataBufferFactoryTest
{

    private DataBufferFactory previous;

    @AfterEach
    void restore()
    {
        if (previous != null)
        {
            DataBufferFactory.set(previous);
            previous = null;
        }
    }


    @Test
    void testGetReturnsNonNullSingleton()
    {
        assertNotNull(DataBufferFactory.get());
    }


    @Test
    void testSetSwapsSingleton()
    {
        previous = DataBufferFactory.get();
        StubFactory stub = new StubFactory();
        DataBufferFactory.set(stub);
        assertSame(stub, DataBufferFactory.get());
    }


    @Test
    void testLoadFromPropertyNullUsesDefault()
    {
        assertSame(DefaultDataBufferFactory.INSTANCE,
                DataBufferFactory.Holder.loadFromProperty(null));
    }


    @Test
    void testLoadFromPropertyBlankUsesDefault()
    {
        assertSame(DefaultDataBufferFactory.INSTANCE,
                DataBufferFactory.Holder.loadFromProperty("   "));
    }


    @Test
    void testLoadFromPropertyValidFactoryClass()
    {
        DataBufferFactory loaded = DataBufferFactory.Holder
                .loadFromProperty(StubFactory.class.getName());
        assertTrue(loaded instanceof StubFactory);
    }


    @Test
    void testLoadFromPropertyNonFactoryClassFallsBack()
    {
        // String is loadable but not a DataBufferFactory -> default returned.
        assertSame(DefaultDataBufferFactory.INSTANCE,
                DataBufferFactory.Holder.loadFromProperty("java.lang.String"));
    }


    @Test
    void testLoadFromPropertyMissingClassFallsBack()
    {
        // Class.forName fails -> ReflectiveOperationException path -> default returned.
        assertSame(DefaultDataBufferFactory.INSTANCE,
                DataBufferFactory.Holder.loadFromProperty("no.such.Class$Nope"));
    }

    /**
     * Public, system-classloader-visible factory with a public no-arg constructor so reflection in
     * {@link DataBufferFactory.Holder#loadFromProperty(String)} can instantiate it.
     */
    public static final class StubFactory implements DataBufferFactory
    {

        public StubFactory()
        {
        }


        @Override
        public IDataBuffer createColumnBuffer(DataValueType aType)
        {
            return new DataBufferObject();
        }


        @Override
        public IDataBufferNumeric createForRange(long aMin, long aMax)
        {
            return new DataBufferLong();
        }
    }
}
