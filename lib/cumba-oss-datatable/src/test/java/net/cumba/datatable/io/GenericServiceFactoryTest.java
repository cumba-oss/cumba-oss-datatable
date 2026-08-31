package net.cumba.datatable.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GenericServiceFactoryTest
{

    // ==================== Test Stubs ====================

    /**
     * A simple interface to use as the supplier type for testing.
     */
    public interface TestSupplier
    {

        String getId();
    }


    /**
     * A concrete supplier with a public no-arg constructor.
     */
    public static class ValidTestSupplier implements TestSupplier
    {

        @Override
        public String getId()
        {
            return "valid";
        }
    }


    /**
     * A supplier with no public no-arg constructor.
     */
    public static class NoDefaultConstructorSupplier implements TestSupplier
    {

        private final String id;

        public NoDefaultConstructorSupplier(String id)
        {
            this.id = id;
        }


        @Override
        public String getId()
        {
            return id;
        }
    }


    /**
     * A test subclass that exposes protected methods for testing.
     */
    private static class TestableServiceFactory extends GenericServiceFactory<TestSupplier, Object>
    {

        private int loadCount = 0;

        private TestableServiceFactory(Class<TestSupplier> supplierClass)
        {
            super(supplierClass);
        }


        @Override
        public List<TestSupplier> getSuppliers()
        {
            return super.getSuppliers();
        }


        @Override
        protected List<TestSupplier> loadSuppliers()
        {
            loadCount++;
            return super.loadSuppliers();
        }


        private int getLoadCount()
        {
            return loadCount;
        }


        private Optional<Class<? extends TestSupplier>> testLoadClass(String className)
        {
            return loadSupplierClassForClassName(className);
        }


        private Optional<? extends TestSupplier> testCreateSupplier(
                Class<? extends TestSupplier> clazz)
        {
            return createSupplier(clazz);
        }
    }

    // ==================== Constructor ====================

    @Test
    void testConstructorNonNull()
    {
        GenericServiceFactory<TestSupplier, Object> factory = new GenericServiceFactory<>(
                TestSupplier.class);
        assertNotNull(factory);
    }


    @Test
    void testConstructorNullThrows()
    {
        assertThrows(NullPointerException.class, () ->
        {
            new GenericServiceFactory<>(null);
        });
    }

    // ==================== loadSupplierClassForClassName ====================


    @Test
    void testLoadSupplierClassForValidName()
    {
        TestableServiceFactory factory = new TestableServiceFactory(TestSupplier.class);
        Optional<Class<? extends TestSupplier>> result = factory
                .testLoadClass(ValidTestSupplier.class.getName());

        assertTrue(result.isPresent());
        assertEquals(ValidTestSupplier.class, result.get());
    }


    @Test
    void testLoadSupplierClassForInvalidName()
    {
        TestableServiceFactory factory = new TestableServiceFactory(TestSupplier.class);
        Optional<Class<? extends TestSupplier>> result = factory
                .testLoadClass("com.nonexistent.FakeClass");

        assertFalse(result.isPresent());
    }


    @Test
    void testLoadSupplierClassForUnrelatedClass()
    {
        TestableServiceFactory factory = new TestableServiceFactory(TestSupplier.class);
        // String.class is not assignable to TestSupplier
        Optional<Class<? extends TestSupplier>> result = factory
                .testLoadClass(String.class.getName());

        assertFalse(result.isPresent());
    }

    // ==================== createSupplier ====================


    @Test
    void testCreateSupplierValid()
    {
        TestableServiceFactory factory = new TestableServiceFactory(TestSupplier.class);
        Optional<? extends TestSupplier> result = factory
                .testCreateSupplier(ValidTestSupplier.class);

        assertTrue(result.isPresent());
        assertEquals("valid", result.get().getId());
    }


    @Test
    void testCreateSupplierNoDefaultConstructor()
    {
        TestableServiceFactory factory = new TestableServiceFactory(TestSupplier.class);
        Optional<? extends TestSupplier> result = factory
                .testCreateSupplier(NoDefaultConstructorSupplier.class);

        assertFalse(result.isPresent(),
                "Should return empty Optional when class has no default constructor");
    }

    // ==================== getSuppliers ====================


    @Test
    void testGetSuppliersNoServicesRegistered()
    {
        // No META-INF/services file for TestSupplier, so should return empty list
        GenericServiceFactory<TestSupplier, Object> factory = new GenericServiceFactory<>(
                TestSupplier.class);
        List<TestSupplier> suppliers = factory.getSuppliers();

        assertNotNull(suppliers);
        assertTrue(suppliers.isEmpty());
    }


    @Test
    void testGetSuppliersCaching()
    {
        TestableServiceFactory factory = new TestableServiceFactory(TestSupplier.class);

        factory.getSuppliers();
        factory.getSuppliers();
        factory.getSuppliers();

        assertEquals(1, factory.getLoadCount(), "loadSuppliers() is called only once");
    }
}
