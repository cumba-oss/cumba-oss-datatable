package net.cumba.datatable.manager.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import net.cumba.datatable.library.IDataTableLibrary;
import net.cumba.datatable.manager.IDataTableLibraryRef;
import net.cumba.datatable.manager.local.testsupport.ClosingLibrary;
import net.cumba.datatable.manager.local.testsupport.LogCapture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Library refs under load (PACK G 17, the check-then-act interleavings F3 (a), (b), (c)): workers
 * open, hold and release one URI while a superseder plants fresh instances under it, and after
 * every round the invariants are checked at quiescence.
 * <p>
 * Statistical: the race windows are nanoseconds wide, so a green run proves little on its own. The
 * deterministic tests in {@link LocalDataTableManagerLibraryRefCountTest} and
 * {@link LocalCacheSupportTest} carry (b) and (c); the plan's sensitivity arm (widened windows) is
 * what shows this harness can red. Excluded from Pitest: its outcome is timing, not logic. Every
 * wait is bounded.
 */
class LocalDataTableManagerLibraryRefConcurrencyTest
{

    private static final int WORKERS = 4;

    private static final int ROUNDS = 30;

    private static final int OPENS_PER_ROUND = 8;

    private static final int PLANTS_PER_ROUND = 3;

    @TempDir
    File tempDir;

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void openHoldReleaseUnderSupersede_keepsEveryInvariant() throws Exception
    {
        LocalDataTableManager manager = new LocalDataTableManager();
        LocalCacheSupport cache = cacheOf(manager);
        File dir = new File(tempDir, "lib");
        assertTrue(dir.mkdirs());
        Files.writeString(new File(dir, "dm.csv").toPath(), "USUBJID,VAL\nU1,1\n",
                StandardCharsets.UTF_8);
        URI uri = dir.toURI();

        List<ClosingLibrary> planted = new CopyOnWriteArrayList<>();
        Queue<String> violations = new ConcurrentLinkedQueue<>();
        CyclicBarrier start = new CyclicBarrier(WORKERS + 1);
        CyclicBarrier end = new CyclicBarrier(WORKERS + 1,
                () -> checkQuiescent(cache, uri, planted, violations));
        // shutdownNow in the finally, never close(): close() waits for the workers without bound,
        // and a hung worker is what this test must report (PACK G fold, G17 review).
        @SuppressWarnings("PMD.CloseResource")
        ExecutorService pool = Executors.newFixedThreadPool(WORKERS + 1, r ->
        {
            Thread thread = new Thread(r, "LibraryRefConcurrency");
            thread.setDaemon(true);
            return thread;
        });
        try (LogCapture log = LogCapture.of(LocalDataTableManager.class))
        {
            List<Future<?>> futures = new ArrayList<>();
            for (int w = 0; w < WORKERS; w++)
            {
                futures.add(pool.submit(() ->
                {
                    for (int r = 0; r < ROUNDS; r++)
                    {
                        start.await(10, TimeUnit.SECONDS);
                        for (int i = 0; i < OPENS_PER_ROUND; i++)
                        {
                            openHoldRelease(manager, uri, violations);
                        }
                        end.await(10, TimeUnit.SECONDS);
                    }
                    return null;
                }));
            }
            futures.add(pool.submit(() ->
            {
                for (int r = 0; r < ROUNDS; r++)
                {
                    start.await(10, TimeUnit.SECONDS);
                    for (int p = 0; p < PLANTS_PER_ROUND; p++)
                    {
                        ClosingLibrary lib = new ClosingLibrary(uri);
                        planted.add(lib);
                        plant(cache, uri, lib);
                        for (int k = 0; k < 64; k++)
                        {
                            Thread.onSpinWait();
                        }
                    }
                    end.await(10, TimeUnit.SECONDS);
                }
                return null;
            }));
            for (Future<?> f : futures)
            {
                f.get(30, TimeUnit.SECONDS);
            }
            assertTrue(violations.isEmpty(), () -> violations.size() + " violations, e.g. "
                    + violations.stream().distinct().limit(8).toList());
            assertEquals(0, log.count(Level.WARNING),
                    () -> "every ref was released exactly once: " + log.records());
        }
        finally
        {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS), "a worker is still running");
        }
    }


    /**
     * One holder: open, check the instance is not closed, hold it a little, check again, release.
     */
    // The libraries are the manager's and the ClosingLibrary doubles' closes are what is counted.
    @SuppressWarnings("PMD.CloseResource")
    private static void openHoldRelease(LocalDataTableManager aManager, URI aUri,
            Queue<String> aViolations)
        throws Exception
    {
        IDataTableLibraryRef ref = aManager.getLibraryRef(aUri, null);
        IDataTableLibrary lib = ((LocalDataTableLibraryRef) ref).getLibrary();
        if (lib instanceof ClosingLibrary cl && cl.closeCount() != 0)
        {
            aViolations.add("I1: handed out an instance that is already closed");
        }
        for (int k = 0; k < 64; k++)
        {
            Thread.onSpinWait();
        }
        if (lib instanceof ClosingLibrary cl && cl.closeCount() != 0)
        {
            aViolations.add("I1: an instance was closed while held");
        }
        aManager.releaseLibraryRef(ref);
    }


    /**
     * At quiescence every ref of the round is released: nothing counts any more, nothing closed
     * twice, and an instance is closed exactly when it has left the cache (I2, I3, I6, I7).
     */
    // The libraries are the manager's and the ClosingLibrary doubles' closes are what is counted.
    @SuppressWarnings("PMD.CloseResource")
    private static void checkQuiescent(LocalCacheSupport aCache, URI aUri,
            List<ClosingLibrary> aPlanted, Queue<String> aViolations)
    {
        int owners = aCache.countOwners(aUri);
        if (owners != 0)
        {
            aViolations.add("I6: " + owners + " owners left after every ref was released");
        }
        List<IDataTableLibrary> cached = aCache.streamLibraries().toList();
        if (cached.size() > 1)
        {
            aViolations.add("I7: " + cached.size() + " instances cached under one URI");
        }
        // I7 proper (PACK G fold, G17 review): the index and the cache agree on the instance
        IDataTableLibrary indexed = aCache.indexedInstance(aUri);
        if (cached.size() == 1 && indexed != cached.get(0))
        {
            aViolations.add("I7: the index holds " + indexed + " but the cache " + cached.get(0));
        }
        if (cached.isEmpty() && indexed != null)
        {
            aViolations.add("I7: the index holds an instance the cache does not");
        }
        for (ClosingLibrary lib : aPlanted)
        {
            int closes = lib.closeCount();
            boolean isCached = cached.stream().anyMatch(c -> c == lib);
            if (closes > 1)
            {
                aViolations.add("I2: an instance was closed " + closes + " times");
            }
            else if (isCached && closes != 0)
            {
                aViolations.add("I7: a closed instance is still cached");
            }
            else if (!isCached && closes == 0)
            {
                aViolations.add("I2/I3: an instance left the cache and was never closed");
            }
        }
    }


    private static LocalCacheSupport cacheOf(LocalDataTableManager aManager) throws Exception
    {
        java.lang.reflect.Field f = LocalDataTableManager.class.getDeclaredField("cacheSupport");
        f.setAccessible(true);
        return (LocalCacheSupport) f.get(aManager);
    }


    /**
     * Put a library into the cache with the folder's real timestamp. The one line that differs from
     * the internal twin: {@code storeLibrary} takes no validation report here.
     */
    private static void plant(LocalCacheSupport aCache, URI aUri, IDataTableLibrary aLibrary)
    {
        aCache.storeLibrary(aUri, aLibrary, null, aCache.getLastModified(aUri));
    }
}
