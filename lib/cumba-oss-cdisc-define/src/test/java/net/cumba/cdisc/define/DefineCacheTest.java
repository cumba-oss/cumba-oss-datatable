package net.cumba.cdisc.define;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefineCacheTest
{

    private DefineCache cache;

    private ODM testOdm;

    @BeforeEach
    void setUp()
    {
        cache = new DefineCache();
        testOdm = ODM.builder().fileOID("test").build();
    }


    @Test
    void testSharedInstance()
    {
        DefineCache instance1 = DefineCache.sharedInstance();
        DefineCache instance2 = DefineCache.sharedInstance();
        assertNotNull(instance1);
        assertSame(instance1, instance2);
    }


    @Test
    void testGetOrLoad_loadsOnFirstAccess() throws IOException
    {
        URI uri = URI.create("test://define.xml");
        AtomicInteger loadCount = new AtomicInteger(0);

        cache.setDefineLoader(u ->
        {
            loadCount.incrementAndGet();
            return new DefineSupport(u, testOdm);
        });

        DefineSupport result = cache.getOrLoad(uri);
        assertNotNull(result);
        assertEquals(1, loadCount.get());
        assertEquals(uri, result.getUri());
    }


    @Test
    void testGetOrLoad_returnsCachedOnSecondAccess() throws IOException
    {
        URI uri = URI.create("test://define.xml");
        AtomicInteger loadCount = new AtomicInteger(0);

        cache.setDefineLoader(u ->
        {
            loadCount.incrementAndGet();
            return new DefineSupport(u, testOdm);
        });

        DefineSupport result1 = cache.getOrLoad(uri);
        DefineSupport result2 = cache.getOrLoad(uri);

        assertNotNull(result1);
        assertSame(result1, result2);
        assertEquals(1, loadCount.get());
    }


    @Test
    void testGetOrLoad_differentUrisLoadSeparately() throws IOException
    {
        URI uri1 = URI.create("test://define1.xml");
        URI uri2 = URI.create("test://define2.xml");
        AtomicInteger loadCount = new AtomicInteger(0);

        cache.setDefineLoader(u ->
        {
            loadCount.incrementAndGet();
            return new DefineSupport(u, testOdm);
        });

        DefineSupport result1 = cache.getOrLoad(uri1);
        DefineSupport result2 = cache.getOrLoad(uri2);

        assertNotNull(result1);
        assertNotNull(result2);
        assertEquals(2, loadCount.get());
        assertEquals(uri1, result1.getUri());
        assertEquals(uri2, result2.getUri());
    }


    @Test
    void testGetOrLoad_propagatesIOException()
    {
        URI uri = URI.create("test://error.xml");

        cache.setDefineLoader(_ ->
        {
            throw new IOException("Load failed");
        });

        assertThrows(IOException.class, () -> cache.getOrLoad(uri));
    }


    @Test
    void testGetDefineLoader()
    {
        assertNotNull(cache.getDefineLoader());
    }


    @Test
    void testSetDefineLoader()
    {
        DefineCache.IDefineLoader loader = u -> new DefineSupport(u, testOdm);
        cache.setDefineLoader(loader);
        assertSame(loader, cache.getDefineLoader());
    }
}
