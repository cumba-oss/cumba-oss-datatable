package net.cumba.datatable.index;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.cumba.datatable.IDataTable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DataTableIndexFactoryTest
{

    /** System property that selects the factory implementation class. */
    private static final String PROP = DataTableIndexFactory.class.getName();

    @AfterEach
    void clearProperty()
    {
        System.clearProperty(PROP);
    }

    // ==================== Test Stubs ====================

    /** A valid factory with a public no-arg constructor. */
    public static class GoodFactory extends DataTableIndexFactory
    {

        @Override
        public IDataTableIndex createIndex(IDataTable aTable, String... aColumns)
        {
            return null;
        }
    }


    /** A factory subclass without a no-arg constructor. */
    public static class NoDefaultConstructorFactory extends DataTableIndexFactory
    {

        public NoDefaultConstructorFactory(int unused)
        {
            // no no-arg constructor on purpose
        }


        @Override
        public IDataTableIndex createIndex(IDataTable aTable, String... aColumns)
        {
            return null;
        }
    }


    /** An abstract subclass: cannot be instantiated (InstantiationException). */
    public abstract static class AbstractFactory extends DataTableIndexFactory
    {
        // intentionally abstract
    }


    /** A factory whose constructor throws (InvocationTargetException). */
    public static class ThrowingFactory extends DataTableIndexFactory
    {

        public ThrowingFactory()
        {
            throw new IllegalArgumentException("boom");
        }


        @Override
        public IDataTableIndex createIndex(IDataTable aTable, String... aColumns)
        {
            return null;
        }
    }

    // ==================== createInstance ====================

    @Test
    void testCreateInstanceWithValidFactory()
    {
        System.setProperty(PROP, GoodFactory.class.getName());

        DataTableIndexFactory factory = DataTableIndexFactory.createInstance();

        assertNotNull(factory);
        assertEquals(GoodFactory.class, factory.getClass());
    }


    @Test
    void testCreateInstanceUnsetUsesDefaultClassWhichIsAbsent()
    {
        // Property unset -> blank-or-null -> the default impl class name is used. That
        // implementation lives in cumba-oss-datatable-impl, which is not on this module's
        // classpath, so the load fails with an IllegalStateException.
        System.clearProperty(PROP);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                DataTableIndexFactory::createInstance);
        assertTrue(ex.getMessage().contains("can not be found"), ex.getMessage());
    }


    @Test
    void testCreateInstanceClassNotFound()
    {
        System.setProperty(PROP, "com.nonexistent.FakeFactory");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                DataTableIndexFactory::createInstance);
        assertTrue(ex.getMessage().contains("can not be found"), ex.getMessage());
    }


    @Test
    void testCreateInstanceNotAFactory()
    {
        System.setProperty(PROP, String.class.getName());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                DataTableIndexFactory::createInstance);
        assertTrue(ex.getMessage().contains("not a valid"), ex.getMessage());
    }


    @Test
    void testCreateInstanceNoDefaultConstructor()
    {
        System.setProperty(PROP, NoDefaultConstructorFactory.class.getName());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                DataTableIndexFactory::createInstance);
        assertTrue(ex.getMessage().contains("default (no args) constructor"), ex.getMessage());
    }


    @Test
    void testCreateInstanceAbstractClass()
    {
        System.setProperty(PROP, AbstractFactory.class.getName());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                DataTableIndexFactory::createInstance);
        assertTrue(ex.getMessage().contains("Can not call default constructor"), ex.getMessage());
    }


    @Test
    void testCreateInstanceConstructorThrows()
    {
        System.setProperty(PROP, ThrowingFactory.class.getName());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                DataTableIndexFactory::createInstance);
        assertTrue(ex.getMessage().contains("Can not call default constructor"), ex.getMessage());
    }
}
