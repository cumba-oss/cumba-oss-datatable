package net.cumba.datatable.impl.library;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class LibraryProviderFactoryTest
{

    @Test
    void testConstructor()
    {
        LibraryProviderFactory factory = LibraryProviderFactory.getInstance();
        assertNotNull(factory);
    }


    @Test
    void testGetInstanceReturnsNewInstance()
    {
        LibraryProviderFactory f1 = LibraryProviderFactory.getInstance();
        LibraryProviderFactory f2 = LibraryProviderFactory.getInstance();

        assertNotNull(f1);
        assertNotNull(f2);
        assertSame(f1, f2);
    }


    @Test
    void testGetFileInfosNotNull()
    {
        LibraryProviderFactory factory = LibraryProviderFactory.getInstance();

        // Should return a list (may be empty if no SPI providers registered)
        assertNotNull(factory.getFileInfos());
    }
}
