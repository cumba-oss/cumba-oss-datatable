package net.cumba.datatable.provider.dsj;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Direct tests for the private static helper {@code DsjTableProvider.pathFromUri(URI)}, which gates
 * the parallel-parse path: only a local {@code file:} URI that {@link java.nio.file.Paths} can
 * resolve returns non-{@code null}; everything else (no URI, no scheme, a non-file scheme, or a
 * file URI {@code Paths.get} rejects) must fall back to the stream-based path by returning
 * {@code null}.
 *
 * <p>
 * Reached through reflection because {@code pathFromUri} has no public entry point through which a
 * {@code null} URI or a scheme-less relative URI could ever arrive — {@code provide(URI, ...)}
 * already dereferences its {@code aURI} argument (via {@code aURI.toURL()}) before calling it, so
 * the {@code aURI == null} branch is unreachable from the public API. Testing the pure helper
 * directly is the only way to exercise every branch of its guard.
 * </p>
 */
class DsjTableProviderPathFromUriTest
{

    private static Path invoke(URI aUri) throws Exception
    {
        Method m = DsjTableProvider.class.getDeclaredMethod("pathFromUri", URI.class);
        m.setAccessible(true);
        try
        {
            return (Path) m.invoke(null, aUri);
        }
        catch (InvocationTargetException ex)
        {
            throw (Exception) ex.getCause();
        }
    }


    @Test
    void nullUriReturnsNull() throws Exception
    {
        assertNull(invoke(null));
    }


    @Test
    void schemelessUriReturnsNull() throws Exception
    {
        assertNull(invoke(URI.create("relative/path.json")));
    }


    @Test
    void nonFileSchemeReturnsNull() throws Exception
    {
        assertNull(invoke(URI.create("http://example.com/data.json")));
    }


    @Test
    void fileSchemeIsCaseInsensitive() throws Exception
    {
        assertNotNull(invoke(URI.create("FILE:///tmp/does-not-need-to-exist.json")));
    }


    @Test
    void fileUriThatPathsGetRejectsYieldsNull() throws Exception
    {
        // Paths.get(URI) throws IllegalArgumentException for a "file:" URI it cannot map --
        // pathFromUri must catch that and return null rather than propagating it, so the caller
        // falls back to the stream-based path. A QUERY component is rejected by both the Unix and
        // the Windows provider (UnixUriUtils, WindowsUriSupport). An AUTHORITY is not portable:
        // on Windows file://host/tmp/x.json is the UNC path \\host\tmp\x.json and resolves --
        // which is what this test used to assume could not happen, and why it failed there.
        assertNull(invoke(URI.create("file:///tmp/x.json?q=1")));
    }


    @Test
    void plainLocalFileUriResolves() throws Exception
    {
        Path p = invoke(URI.create("file:///tmp/some-file.json"));
        assertNotNull(p);
    }

}
