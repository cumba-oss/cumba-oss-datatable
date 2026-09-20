package net.cumba.cdisc.define;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The seams {@link DefineCache} hangs its freshness decision on: the wall clock, the file
 * modification time, and the entry revalidation that uses both.
 *
 * <p>
 * The cache hands out a parsed Define-XML. Deciding wrongly that an entry is still fresh means a
 * user goes on reading metadata from a file that has since changed on disk — the stale answer is a
 * complete, plausible Define-XML, so nothing downstream can notice. The sibling suite covers this
 * through {@code getOrLoad} with both seams stubbed, which leaves the seams' own default
 * implementations — the ones that actually run in production — untested. These tests exercise them
 * directly.
 * </p>
 */
class DefineCacheSeamTest
{

    /** Stubs the two clocks so entry ages are exact rather than wall-clock dependent. */
    private static final class StubClockCache extends DefineCache
    {

        private final Map<URI, Long> lastModified = new HashMap<>();

        private long nowMs = 5_000_000L;

        @Override
        protected long clock()
        {
            return nowMs;
        }


        @Override
        protected long getLastModified(URI aUri)
        {
            return lastModified.getOrDefault(aUri, 0L);
        }
    }

    private static long cacheTimeout(DefineCache aCache) throws Exception
    {
        Field f = DefineCache.class.getDeclaredField("cacheTimeout1");
        f.setAccessible(true);
        return f.getLong(aCache);
    }


    private static DefineSupport support(URI aUri)
    {
        return new DefineSupport(aUri, ODM.builder().fileOID("F").build());
    }

    // ---------- the default seams ----------


    /**
     * {@code clock()} is documented to be the current time. A clock stuck at zero makes every
     * entry's age negative, so the timeout window never elapses and nothing is ever revalidated —
     * the cache would simply never notice a changed file again.
     */
    @Test
    void theDefaultClockIsTheWallClock()
    {
        long before = System.currentTimeMillis();
        long observed = new DefineCache().clock();
        long after = System.currentTimeMillis();

        assertTrue(observed >= before && observed <= after, "clock() must be the current time, was "
                + observed + " outside [" + before + ", " + after + "]");
    }


    /**
     * The default {@code getLastModified} is the file's modification time, and 0 for anything that
     * is not a local file. Answering 0 for a real file would make every revalidation compare 0 to 0
     * and conclude "unchanged" — a cache that can never go stale, which is the same defect as a
     * stopped clock arriving by a different route.
     */
    @Test
    void theDefaultLastModifiedReadsTheFileAndFallsBackToZero(@TempDir Path aTempDir)
        throws Exception
    {
        File f = aTempDir.resolve("define.xml").toFile();
        Files.writeString(f.toPath(), "<ODM/>");
        DefineCache cache = new DefineCache();

        assertEquals(f.lastModified(), cache.getLastModified(f.toURI()));
        assertTrue(cache.getLastModified(f.toURI()) > 0, "a real file has a real mtime");

        assertEquals(0L, cache.getLastModified(URI.create("http://example.org/define.xml")),
                "a non-file URI has no modification time to read");
        assertEquals(0L, cache.getLastModified(aTempDir.resolve("absent.xml").toUri()),
                "a file that does not exist reports 0, not a failure");
    }


    /**
     * The CacheEntry overload is a delegation, and a delegation that answers 0 is a stopped clock.
     */
    @Test
    void theEntryOverloadDelegatesToTheUriOverload()
    {
        StubClockCache cache = new StubClockCache();
        URI uri = URI.create("test://delegation.xml");
        cache.lastModified.put(uri, 4242L);

        DefineCache.CacheEntry entry = new DefineCache.CacheEntry(uri, support(uri), 4242L,
                cache.clock());

        assertEquals(4242L, cache.getLastModified(entry));
    }

    // ---------- the revalidation window ----------


    /**
     * The window is half-open: an age <em>equal</em> to the timeout has elapsed and must
     * revalidate. Getting that boundary wrong by one comparison extends every entry's trusted
     * window indefinitely, because the fast path also declines to refresh the timestamp — the entry
     * would be handed back unchecked, then checked against an age that keeps growing but from a
     * timestamp that never moves.
     */
    @Test
    void anAgeExactlyAtTheTimeoutRevalidatesAndRefreshesTheWindow() throws Exception
    {
        StubClockCache cache = new StubClockCache();
        URI uri = URI.create("test://boundary.xml");
        cache.lastModified.put(uri, 100L);
        DefineSupport ds = support(uri);
        long now = cache.clock();

        DefineCache.CacheEntry entry = new DefineCache.CacheEntry(uri, ds, 100L,
                now - cacheTimeout(cache));

        assertSame(ds, cache.getFromEntry(entry),
                "the file has not changed, so the entry is still valid");
        assertEquals(now, entry.getTimestamp(),
                "a revalidated entry restarts its window; leaving the timestamp behind means the"
                        + " next call revalidates again from a stale base");
    }


    /** Inside the window the entry is trusted without touching the filesystem at all. */
    @Test
    void insideTheWindowTheEntryIsReturnedWithoutRevalidation() throws Exception
    {
        StubClockCache cache = new StubClockCache();
        URI uri = URI.create("test://fresh.xml");
        cache.lastModified.put(uri, 999L);
        DefineSupport ds = support(uri);
        long now = cache.clock();

        DefineCache.CacheEntry entry = new DefineCache.CacheEntry(uri, ds, 100L,
                now - cacheTimeout(cache) + 1);

        assertSame(ds, cache.getFromEntry(entry),
                "within the window a changed mtime is deliberately not consulted");
        assertEquals(now - cacheTimeout(cache) + 1, entry.getTimestamp(),
                "and the window is not restarted by a fast-path hit");
    }


    /** Past the window a changed modification time invalidates the entry. */
    @Test
    void pastTheWindowAChangedFileInvalidatesTheEntry() throws Exception
    {
        StubClockCache cache = new StubClockCache();
        URI uri = URI.create("test://stale.xml");
        cache.lastModified.put(uri, 777L);
        long now = cache.clock();

        DefineCache.CacheEntry entry = new DefineCache.CacheEntry(uri, support(uri), 100L,
                now - cacheTimeout(cache));

        assertNull(cache.getFromEntry(entry), "the file changed under us; the entry is dead");
    }

    // ---------- the shared instance ----------


    /**
     * {@code sharedInstance()} is a double-checked lock, and both of its null checks are only ever
     * exercised on the very first call in a JVM — by the time any other test looks, the field is
     * populated and a broken check is invisible. Clearing the field first is what makes the first
     * call observable; the original is put back so no other test sees a different cache.
     */
    @Test
    void theSharedInstanceIsCreatedOnFirstUseAndThenReused() throws Exception
    {
        Field field = DefineCache.class.getDeclaredField("sharedInstance");
        field.setAccessible(true);
        Object original = field.get(null);
        try
        {
            field.set(null, null);

            DefineCache first = DefineCache.sharedInstance();
            assertNotNull(first, "the first call must create the instance, not answer null");
            assertSame(first, DefineCache.sharedInstance(), "and every later call reuses it");
        }
        finally
        {
            field.set(null, original);
        }
    }
}
