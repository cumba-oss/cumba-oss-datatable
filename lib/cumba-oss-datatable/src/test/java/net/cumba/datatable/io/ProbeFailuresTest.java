package net.cumba.datatable.io;

// OSS-IDENTITY datatable-probe-failures: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import net.cumba.datatable.help.URIHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link ProbeFailures}: remote is decided by the URI scheme (never by the exception type), a local
 * failure returns, a remote one throws the abort whose message is the UI's headline.
 */
class ProbeFailuresTest
{

    @ParameterizedTest
    @ValueSource(strings =
    {
            "http://example.org/x.csv", "https://example.org/x.csv", "HTTPS://example.org/x.csv",
            "HtTp://example.org:8080/x.csv", "https://[::1]:8443/x.csv"
    })
    void httpAndHttpsAreRemote(String aUri)
    {
        assertTrue(ProbeFailures.isRemote(URI.create(aUri)));
    }


    @ParameterizedTest
    @ValueSource(strings =
    {
            "file:///tmp/x.csv", "file://server/share/x.csv",
            "jar:https://example.org/a.zip!/x.csv", "databricks://workspace/catalog/schema",
            "x.csv", "ftp://example.org/x.csv", "httpx://example.org/x.csv"
    })
    void everythingElseIsLocal(String aUri)
    {
        assertFalse(ProbeFailures.isRemote(URI.create(aUri)));
    }


    @Test
    void nullIsLocal()
    {
        assertFalse(ProbeFailures.isRemote(null));
    }


    /**
     * The sensitivity arm of "by scheme, never by exception type": a missing local file and a
     * missing file on a UNC share both throw {@link FileNotFoundException} — the type a remote 404
     * throws — and both keep the caller's default.
     */
    @ParameterizedTest
    @ValueSource(strings =
    {
            "file:///definitely/missing/nope.csv", "file://server/share/missing.csv"
    })
    void aLocalFailureReturnsWhateverItsType(String aUri)
    {
        URI uri = URI.create(aUri);
        assertDoesNotThrow(
                () -> ProbeFailures.rethrowIfRemote(uri, new FileNotFoundException(aUri)));
        assertDoesNotThrow(() -> ProbeFailures.rethrowIfRemote(uri,
                new SSLHandshakeException("(certificate_unknown) refused")));
        assertDoesNotThrow(
                () -> ProbeFailures.rethrowIfRemote(uri, new IllegalStateException("bad")));
    }


    /**
     * The re-chained shape {@code HttpsURLConnection} produces: an outer
     * {@link SSLHandshakeException} over the handshake's own, over the trust manager's
     * {@link CertificateException}. The reason is the trust manager's text, not the handshake's
     * {@code (certificate_unknown)}-prefixed copy of it.
     */
    @Test
    void aTlsRefusalCarriesTheTrustManagersText()
    {
        CertificateException refusal = new CertificateException(
                "The user rejected the certificate of example.org:443.");
        SSLHandshakeException inner = new SSLHandshakeException(
                "(certificate_unknown) The user rejected the certificate of example.org:443.");
        inner.initCause(refusal);
        SSLHandshakeException outer = new SSLHandshakeException(inner.getMessage());
        outer.initCause(inner);

        UncheckedIOException ex = assertThrows(UncheckedIOException.class, () -> ProbeFailures
                .rethrowIfRemote(URI.create("https://example.org/x.csv"), outer));

        assertEquals("Opening aborted: example.org:443 could not be read (The user rejected the "
                + "certificate of example.org:443.).", ex.getMessage());
        assertSame(outer, ex.getCause());
    }


    @Test
    void aTlsFailureWithoutACertificateRefusalCarriesTheSslText()
    {
        SSLException ssl = new SSLException("Unsupported or unrecognized SSL message");

        UncheckedIOException ex = assertThrows(UncheckedIOException.class,
                () -> ProbeFailures.rethrowIfRemote(URI.create("https://example.org:8443/x"), ssl));

        assertEquals("Opening aborted: example.org:8443 could not be read (Unsupported or "
                + "unrecognized SSL message).", ex.getMessage());
    }


    @Test
    void aRemote404AbortsWithNotFound() throws IOException
    {
        HttpServer server = HttpServer
                .create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange ->
        {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
        try
        {
            int port = server.getAddress().getPort();
            URI uri = URI.create("http://localhost:" + port + "/missing.csv");
            IOException read = assertThrows(IOException.class, () -> readAll(uri));
            assertInstanceOf(FileNotFoundException.class, read, "the JDK's 404 shape");

            UncheckedIOException ex = assertThrows(UncheckedIOException.class,
                    () -> ProbeFailures.rethrowIfRemote(uri, read));

            assertEquals("Opening aborted: localhost:" + port + " could not be read (not found).",
                    ex.getMessage());
            assertSame(read, ex.getCause());
        }
        finally
        {
            server.stop(0);
        }
    }


    @Test
    void aRefusedConnectionAbortsWithTheJdkText() throws IOException
    {
        int port;
        try (ServerSocket closed = new ServerSocket(0, 1, InetAddress.getLoopbackAddress()))
        {
            port = closed.getLocalPort();
        }
        URI uri = URI.create("http://localhost:" + port + "/x.csv");
        IOException read = assertThrows(IOException.class, () -> readAll(uri));

        UncheckedIOException ex = assertThrows(UncheckedIOException.class,
                () -> ProbeFailures.rethrowIfRemote(uri, read));

        assertTrue(
                ex.getMessage()
                        .startsWith("Opening aborted: localhost:" + port + " could not be read ("),
                ex.getMessage());
        assertSame(read, ex.getCause());
    }


    @Test
    void theDefaultPortIsFilledIn()
    {
        assertEquals("example.org:80",
                ProbeFailures.hostAndPort(URI.create("http://example.org/x.csv")));
        assertEquals("example.org:443",
                ProbeFailures.hostAndPort(URI.create("HTTPS://example.org/x.csv")));
        assertEquals("example.org:8080",
                ProbeFailures.hostAndPort(URI.create("https://example.org:8080/x.csv")));
        assertEquals("[::1]:443", ProbeFailures.hostAndPort(URI.create("https://[::1]/x.csv")));
    }


    @Test
    void aNonIoFailureIsWrappedInAnIoException()
    {
        IllegalStateException parse = new IllegalStateException("not a Define-XML document");

        UncheckedIOException ex = assertThrows(UncheckedIOException.class, () -> ProbeFailures
                .rethrowIfRemote(URI.create("https://example.org/define.xml"), parse));

        assertEquals("Opening aborted: example.org:443 could not be read (not a Define-XML "
                + "document).", ex.getMessage());
        assertSame(parse, ex.getCause().getCause());
    }


    @Test
    void anUncheckedIoFailureKeepsItsIoCause()
    {
        IOException io = new IOException("stream closed");

        UncheckedIOException ex = assertThrows(UncheckedIOException.class, () -> ProbeFailures
                .rethrowIfRemote(URI.create("http://example.org/x"), new UncheckedIOException(io)));

        assertSame(io, ex.getCause());
        assertEquals("Opening aborted: example.org:80 could not be read (stream closed).",
                ex.getMessage());
    }


    @Test
    void reasonsWithoutAUsefulMessage()
    {
        assertEquals("EOFException", ProbeFailures.reasonOf(new EOFException()));
        assertEquals("unknown host",
                ProbeFailures.reasonOf(new UnknownHostException("nohost.invalid")));
        assertEquals("not found", ProbeFailures
                .reasonOf(new UncheckedIOException(new FileNotFoundException("https://h/x"))));
    }


    private static void readAll(URI aUri) throws IOException
    {
        try (InputStream in = URIHelper.openStream(aUri))
        {
            in.readAllBytes();
        }
    }
}
