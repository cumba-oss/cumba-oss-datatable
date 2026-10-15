package net.cumba.datatable.manager.local.testsupport;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.cumba.datatable.library.IDataTableLibrary;

/**
 * A library double that counts its {@code close()} calls (PACK G 17). No library of the local
 * manager's own modules overrides the no-op {@code IDataTableLibrary.close()}, so a test that
 * watched for a closed folder library would confirm itself; this one makes every close observable.
 * <p>
 * {@link #latchGetUri(Thread)} is the seam for the release-to-zero race: the named thread parks in
 * its next {@link #getUri()} until {@link #resume()} (the manager reads the URI of the library it
 * releases while it unlocks it). Every wait is bounded.
 */
public final class ClosingLibrary implements IDataTableLibrary
{

    private final URI uri;

    private final AtomicInteger closeCount = new AtomicInteger();

    private final AtomicReference<Thread> latched = new AtomicReference<>();

    private final CountDownLatch parked = new CountDownLatch(1);

    private final CountDownLatch resumed = new CountDownLatch(1);

    /**
     * Create the double.
     *
     * @param aUri
     *            the URI it answers, normally the folder URI it is cached under.
     */
    public ClosingLibrary(URI aUri)
    {
        uri = aUri;
    }


    /**
     * Park {@code aThread} in its next {@link #getUri()} call until {@link #resume()}.
     *
     * @param aThread
     *            the thread to park, once.
     */
    public void latchGetUri(Thread aThread)
    {
        latched.set(aThread);
    }


    /**
     * Wait until the latched thread is parked.
     *
     * @return whether it parked within five seconds.
     * @throws InterruptedException
     *             if interrupted while waiting.
     */
    public boolean awaitParked() throws InterruptedException
    {
        return parked.await(5, TimeUnit.SECONDS);
    }


    /** Let the parked thread go on. */
    public void resume()
    {
        resumed.countDown();
    }


    /**
     * The number of {@code close()} calls so far.
     *
     * @return the count.
     */
    public int closeCount()
    {
        return closeCount.get();
    }


    @Override
    public String getName()
    {
        return "closing";
    }


    @Override
    public String getLabel()
    {
        return "closing";
    }


    @Override
    public URI getUri()
    {
        Thread self = Thread.currentThread();
        if (latched.compareAndSet(self, null))
        {
            parked.countDown();
            try
            {
                if (!resumed.await(10, TimeUnit.SECONDS))
                {
                    throw new IllegalStateException("the latched getUri() was never resumed");
                }
            }
            catch (InterruptedException ex)
            {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
            }
        }
        return uri;
    }


    @Override
    public String getType()
    {
        return "closing";
    }


    @Override
    public void close()
    {
        closeCount.incrementAndGet();
    }
}
