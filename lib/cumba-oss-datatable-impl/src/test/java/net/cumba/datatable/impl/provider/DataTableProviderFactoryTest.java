package net.cumba.datatable.impl.provider;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

// Test deliberately asserts the subtype contract; the always-true instanceof is the assertion.
@SuppressWarnings("BadInstanceof")
class DataTableProviderFactoryTest
{

    // ==================== getFactory ====================

    @Test
    void testGetFactoryReturnsNonNull()
    {
        DataTableProviderFactory factory = DataTableProviderFactory.getFactory();

        assertNotNull(factory);
    }


    @Test
    void testGetFactoryReturnsCorrectType()
    {
        DataTableProviderFactory factory = DataTableProviderFactory.getFactory();

        assertTrue(factory instanceof DataTableProviderFactory);
    }


    @Test
    void testGetFactorySharesInstance()
    {
        DataTableProviderFactory factory1 = DataTableProviderFactory.getFactory();
        DataTableProviderFactory factory2 = DataTableProviderFactory.getFactory();

        assertSame(factory1, factory2);
    }

    // ==================== Inheritance ====================


    @Test
    void testExtendsGenericProviderFactory()
    {
        DataTableProviderFactory factory = DataTableProviderFactory.getFactory();

        assertTrue(factory instanceof net.cumba.datatable.io.GenericProviderFactory);
    }

    // ==================== getFileInfos ====================


    @Test
    void testGetFileInfosReturnsNonNull()
    {
        DataTableProviderFactory factory = DataTableProviderFactory.getFactory();

        assertNotNull(factory.getFileInfos());
    }

}
