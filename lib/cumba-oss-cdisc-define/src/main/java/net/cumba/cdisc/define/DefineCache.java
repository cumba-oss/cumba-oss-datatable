package net.cumba.cdisc.define;

import java.io.File;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.cumba.datatable.help.URIHelper;
import org.jspecify.annotations.Nullable;

/**
 * The define cache is a cache for the define.xml. This cache uses {@link SoftReference} to keep the
 * define.xml longer than with {@link WeakReference}.
 */
public class DefineCache
{

    private static final Logger LOGGER = System.getLogger(DefineCache.class.getName());

    /**
     * The globally shared instance.
     */
    private static volatile @Nullable DefineCache sharedInstance;

    /**
     * Returns the globally shared instance.
     *
     * @return the globally shared instance.
     */
    public static DefineCache sharedInstance()
    {
        DefineCache res = sharedInstance;
        if (res == null)
        {
            synchronized (DefineCache.class)
            {
                res = sharedInstance;
                if (res == null)
                {
                    sharedInstance = res = new DefineCache();
                }
            }
        }
        return res;
    }

    /**
     * The cache map that maps from a URI to the CacheEntry.
     */
    private final Map<URI, CacheEntry> cacheMap = new HashMap<>();

    @Getter
    @Setter
    @NonNull
    private IDefineLoader defineLoader = DefineSupport::new;

    /**
     * The first cache timeout in milli seconds
     */
    private long cacheTimeout1 = 2_000L;

    /**
     * Retrieve the define for the given URI from the cache or load it.
     *
     * @param aUri
     *            the URI to retrieve the define for.
     * @return the define for the given URI.
     * @throws IOException
     *             in case loading the define from the given URI fails.
     */
    public DefineSupport getOrLoad(URI aUri) throws IOException
    {
        // clean up cache before each access
        cleanUpCache();

        CacheEntry ce;

        synchronized (cacheMap)
        {
            ce = cacheMap.get(aUri);

            if (ce != null)
            {
                DefineSupport define = getFromEntry(ce);
                if (define != null)
                {
                    return define;
                }
            }

            // we use the last modified date before the load to avoid race conditions
            long lm = getLastModified(aUri);
            DefineSupport res = loadDefine(aUri);
            cacheMap.put(aUri, new CacheEntry(aUri, res, lm, clock()));
            return res;
        }
    }


    /**
     * Returns the current time in milliseconds. Test subclasses may override this to drive the
     * cache against a virtual clock instead of wall time — avoiding {@code Thread.sleep} in the
     * cache-window tests, which made them timing-dependent and flaky under JIT / mocking-agent
     * load.
     *
     * @return current time in milliseconds (default: {@link System#currentTimeMillis()}).
     */
    protected long clock()
    {
        return System.currentTimeMillis();
    }


    protected long getLastModified(CacheEntry aEntry)
    {
        return getLastModified(aEntry.getUri());
    }


    protected long getLastModified(URI aUri)
    {
        try
        {
            File f = URIHelper.asFile(aUri);
            return f != null ? f.lastModified() : 0;
        }
        catch (Exception ex)
        {
            LOGGER.log(Level.ERROR, ex.getMessage(), ex);
            return 0;
        }
    }


    protected @Nullable DefineSupport getFromEntry(CacheEntry aEntry)
    {
        DefineSupport res = aEntry.getDefine();
        if (res == null)
        {
            return null;
        }
        long now = clock();
        long age = now - aEntry.getTimestamp();
        if (age < cacheTimeout1)
        {
            // we do no further checks within cacheTimeout1 period
            return res;
        }

        if (aEntry.getLastModified() != getLastModified(aEntry))
        {
            return null;
        }

        // entry is still valid --> update cache timestamp
        aEntry.timestamp = now;

        return res;
    }


    /**
     * Load a define from the given URI. This is called from {@link #getOrLoad(URI)} in case the
     * define is not in cache.
     *
     * @param aUri
     *            the URI to load the define from.
     * @return the define that was loaded from the given URI.
     * @throws IOException
     *             in case loading the define from the given URI fails.
     */
    protected DefineSupport loadDefine(URI aUri) throws IOException
    {
        return defineLoader.load(aUri);
    }


    /**
     * Internal helper method to clean up the cache.
     */
    private void cleanUpCache()
    {
        synchronized (cacheMap)
        {
            // collect all URI keys for cache entries with a cleared reference.
            List<URI> outdated = cacheMap.entrySet().stream()
                    .filter(e -> e.getValue().getDefine() == null)//
                    .map(Entry::getKey).toList();

            // remove all collected keys from map
            outdated.forEach(cacheMap::remove);
        }
    }

    /**
     * The cache entry
     */
    private static class CacheEntry
    {

        @Getter
        private final URI uri;

        private final Reference<DefineSupport> ref;

        /**
         * The cache timestamp. This is the timestamp the cache was last identified to be valid.
         */
        @Getter
        private long timestamp;

        /**
         * The last modified date of the element that can be accessed by {@link #uri}. This might be
         * 0 if not available.
         */
        @Getter
        @Setter
        private long lastModified;

        CacheEntry(URI aUri, DefineSupport aDefine, long aLastModified, long aNow)
        {
            uri = aUri;
            ref = new SoftReference<>(aDefine);
            lastModified = aLastModified;
            timestamp = aNow;
        }


        @Nullable
        DefineSupport getDefine()
        {
            return ref.get();
        }
    }


    /**
     * A custom loader that loads a define.xml from the given URI.
     */
    @FunctionalInterface
    public interface IDefineLoader
    {

        DefineSupport load(URI aUri) throws IOException;
    }
}
