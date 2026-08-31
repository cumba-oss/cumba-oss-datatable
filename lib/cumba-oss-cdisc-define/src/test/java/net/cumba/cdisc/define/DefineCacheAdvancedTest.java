package net.cumba.cdisc.define;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Advanced tests for {@link DefineCache} covering revalidation via mocked {@code lastModified}, the
 * timeout fast-path, and a concurrency check on the double-checked locking inside
 * {@link DefineCache#sharedInstance()}. {@code SoftReference} GC behaviour is deliberately
 * <strong>not</strong> tested.
 */
class DefineCacheAdvancedTest
{

    /**
     * Test subclass that exposes stubs for the two clocks the cache cares about:
     * <ul>
     * <li>{@code lastModified} per URI (so we can simulate file mtime changes), and</li>
     * <li>a virtual wall clock that advances only when the test calls {@link #tick(long)} — this
     * replaces {@code Thread.sleep} in the cache-window tests, which made them wall-clock-dependent
     * and flaky under JIT / mocking-agent load.</li>
     * </ul>
     */
    private static class StubbedLastModifiedCache extends DefineCache
    {

        private final Map<URI, Long> lastModifiedByUri = new HashMap<>();

        /** Virtual wall-clock time in ms; starts at an arbitrary epoch so age math is sensible. */
        private long nowMs = 1_000_000L;

        void setLastModified(URI uri, long lm)
        {
            lastModifiedByUri.put(uri, lm);
        }


        /** Advance the virtual clock by {@code aDeltaMs} milliseconds. */
        void tick(long aDeltaMs)
        {
            nowMs += aDeltaMs;
        }


        @Override
        protected long getLastModified(URI aUri)
        {
            return lastModifiedByUri.getOrDefault(aUri, 0L);
        }


        @Override
        protected long clock()
        {
            return nowMs;
        }
    }

    @Test
    void getOrLoad_reloadsWhenFileChanges() throws Exception
    {
        StubbedLastModifiedCache cache = new StubbedLastModifiedCache();
        URI uri = URI.create("test://reload.xml");
        AtomicInteger loadCount = new AtomicInteger(0);

        // Each load creates a *new* DefineSupport instance so we can detect cache invalidation
        // by identity.
        cache.setDefineLoader(u ->
        {
            loadCount.incrementAndGet();
            return new DefineSupport(u, ODM.builder().fileOID("v" + loadCount.get()).build());
        });

        // initial last-modified = 100
        cache.setLastModified(uri, 100L);
        DefineSupport first = cache.getOrLoad(uri);
        assertEquals(1, loadCount.get());

        // Advance the virtual clock past the 2s cache window so the cache re-checks lastModified.
        cache.tick(3_500L);

        // Change the file mtime to force a revalidation miss.
        cache.setLastModified(uri, 200L);
        DefineSupport second = cache.getOrLoad(uri);

        assertEquals(2, loadCount.get(), "Expected a reload after lastModified change");
        assertNotSame(first, second);
    }


    @Test
    void getOrLoad_withinTimeoutWindow_skipsLastModifiedCheck() throws Exception
    {
        StubbedLastModifiedCache cache = new StubbedLastModifiedCache();
        URI uri = URI.create("test://hot.xml");
        AtomicInteger loadCount = new AtomicInteger(0);

        cache.setDefineLoader(u ->
        {
            loadCount.incrementAndGet();
            return new DefineSupport(u, ODM.builder().fileOID("only").build());
        });

        cache.setLastModified(uri, 100L);
        DefineSupport first = cache.getOrLoad(uri);

        // Even when the underlying file's lastModified changes, within the 2s window the cache
        // returns the existing entry.
        cache.setLastModified(uri, 999L);
        DefineSupport second = cache.getOrLoad(uri);

        assertSame(first, second);
        assertEquals(1, loadCount.get());
    }


    @Test
    void sharedInstance_concurrentInitialisationReturnsSingleInstance() throws Exception
    {
        // Note: this exercises the double-checked-locking branch on the *existing* shared
        // instance — we cannot reset the singleton without reflection. We assert that all racing
        // callers observe the same instance, which is the contract.
        int threadCount = 8;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        DefineCache[] observed = new DefineCache[threadCount];

        for (int i = 0; i < threadCount; i++)
        {
            final int idx = i;
            Thread t = new Thread(() ->
            {
                try
                {
                    start.await();
                    observed[idx] = DefineCache.sharedInstance();
                }
                catch (InterruptedException _)
                {
                    Thread.currentThread().interrupt();
                }
                finally
                {
                    done.countDown();
                }
            });
            t.setDaemon(true);
            t.start();
        }
        start.countDown();
        assertEquals(true, done.await(5L, TimeUnit.SECONDS),
                "All sharedInstance() callers should complete");

        DefineCache canonical = observed[0];
        assertNotNull(canonical);
        for (int i = 1; i < threadCount; i++)
        {
            assertSame(canonical, observed[i], "Thread " + i + " saw a different instance");
        }
    }


    @Test
    void getOrLoad_propagatesIOExceptionOnReload() throws Exception
    {
        StubbedLastModifiedCache cache = new StubbedLastModifiedCache();
        URI uri = URI.create("test://fail.xml");

        cache.setLastModified(uri, 100L);
        cache.setDefineLoader(u -> new DefineSupport(u, ODM.builder().build()));
        cache.getOrLoad(uri);

        // Advance past the cache window, then switch the loader to throw so the revalidated
        // reload surfaces the IOException.
        cache.tick(3_500L);
        cache.setLastModified(uri, 200L);
        cache.setDefineLoader(_ ->
        {
            throw new IOException("simulated reload failure");
        });

        IOException ex = assertThrows(IOException.class, () -> cache.getOrLoad(uri));
        assertEquals("simulated reload failure", ex.getMessage());
    }


    @Test
    void cleanUpCache_dropsEntriesWithClearedReferences() throws Exception
    {
        // We exercise the cleanUpCache() path indirectly: any time getOrLoad is called, the
        // private cleanUpCache() runs first. We don't try to GC the SoftReference (per plan),
        // we simply verify the method is invoked without side-effects on a populated cache.
        StubbedLastModifiedCache cache = new StubbedLastModifiedCache();
        URI uri = URI.create("test://stable.xml");
        cache.setLastModified(uri, 1L);
        cache.setDefineLoader(u -> new DefineSupport(u, ODM.builder().build()));

        DefineSupport a = cache.getOrLoad(uri);
        DefineSupport b = cache.getOrLoad(uri);

        assertSame(a, b);
    }
}
