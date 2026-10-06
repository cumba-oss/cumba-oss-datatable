package net.cumba.datatable.provider.csv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import net.cumba.datatable.io.Property;
import org.junit.jupiter.api.Test;

/**
 * {@link CsvTableProvider#getProviderProperties}: a remote ({@code http} / {@code https}) source
 * whose first line cannot be read aborts the open (thrown, unchecked, with the UI's headline); a
 * remote source that can be read keeps the best-effort defaults whatever its first line; a local
 * failure keeps today's defaults ({@link CsvTableProviderPropertiesTest}).
 */
class CsvTableProviderRemoteProbeTest
{

    private static final char[] PASSWORD = "changeit".toCharArray();

    @Test
    void aRemote404AbortsTheOpen() throws IOException
    {
        HttpServer server = serve(404, "");
        try
        {
            int port = server.getAddress().getPort();
            URI uri = URI.create("http://localhost:" + port + "/missing.csv");

            UncheckedIOException ex = assertThrows(UncheckedIOException.class,
                    () -> new CsvTableProvider().getProviderProperties(uri,
                            CsvProviderSupplier.FI_CSV));

            assertEquals("Opening aborted: localhost:" + port + " could not be read (not found).",
                    ex.getMessage());
        }
        finally
        {
            server.stop(0);
        }
    }


    @Test
    void aRefusedRemoteConnectionAbortsTheOpen() throws IOException
    {
        int port;
        try (ServerSocket closed = new ServerSocket(0, 1, InetAddress.getLoopbackAddress()))
        {
            port = closed.getLocalPort();
        }
        URI uri = URI.create("http://localhost:" + port + "/x.csv");

        UncheckedIOException ex = assertThrows(UncheckedIOException.class,
                () -> new CsvTableProvider().getProviderProperties(uri,
                        CsvProviderSupplier.FI_CSV));

        assertTrue(ex.getMessage().startsWith("Opening aborted: localhost:" + port),
                ex.getMessage());
    }


    /** A readable remote source probes as a local one does: the delimiter is detected. */
    @Test
    void aReadableRemoteSourceIsProbed() throws IOException
    {
        HttpServer server = serve(200, "A;B;C\n1;2;3\n");
        try
        {
            URI uri = URI.create("http://localhost:" + server.getAddress().getPort() + "/x.csv");

            List<Property> props = new CsvTableProvider().getProviderProperties(uri,
                    CsvProviderSupplier.FI_CSV);

            assertEquals(6, props.size());
            assertEquals(";", props.get(1).defaultValue());
        }
        finally
        {
            server.stop(0);
        }
    }


    /**
     * Reachable, but the first line tells nothing (an empty body): the read succeeded, so the
     * dialog's defaults are kept — only a failed read aborts.
     */
    @Test
    void aReadableRemoteSourceWithNoFirstLineKeepsTheDefaults() throws IOException
    {
        HttpServer server = serve(200, "");
        try
        {
            URI uri = URI.create("http://localhost:" + server.getAddress().getPort() + "/x.csv");

            List<Property> props = new CsvTableProvider().getProviderProperties(uri,
                    CsvProviderSupplier.FI_CSV);

            assertEquals(6, props.size());
            assertEquals(CsvTableProvider.PROP_DELIMITER.name(), props.get(1).name());
            assertEquals("", props.get(1).defaultValue());
        }
        finally
        {
            server.stop(0);
        }
    }


    /**
     * The owner's smoke: the user rejects the server's certificate in the trust dialog. The
     * handshake fails inside the probe, the open aborts with the trust manager's own text as the
     * reason, and the probe asked exactly once.
     */
    @Test
    void aRejectedCertificateAbortsTheOpenWithTheRefusalText() throws Exception
    {
        HttpsServer server = serveTls();
        SSLSocketFactory savedFactory = HttpsURLConnection.getDefaultSSLSocketFactory();
        try
        {
            int port = server.getAddress().getPort();
            String refusal = "The user rejected the certificate of localhost:" + port + ".";
            AtomicInteger asked = new AtomicInteger();
            SSLContext client = SSLContext.getInstance("TLS");
            client.init(null, new TrustManager[]
            {
                    new RejectingTrustManager(refusal, asked)
            }, null);
            HttpsURLConnection.setDefaultSSLSocketFactory(client.getSocketFactory());
            URI uri = URI.create("https://localhost:" + port + "/x.csv");

            UncheckedIOException ex = assertThrows(UncheckedIOException.class,
                    () -> new CsvTableProvider().getProviderProperties(uri,
                            CsvProviderSupplier.FI_CSV));

            assertEquals(
                    "Opening aborted: localhost:" + port + " could not be read (" + refusal + ").",
                    ex.getMessage());
            assertTrue(hasCause(ex, SSLHandshakeException.class), "the handshake failed: " + ex);
            assertEquals(1, asked.get(), "the probe asks once");
        }
        finally
        {
            HttpsURLConnection.setDefaultSSLSocketFactory(savedFactory);
            server.stop(0);
        }
    }


    private static HttpServer serve(int aStatus, String aBody) throws IOException
    {
        HttpServer server = HttpServer
                .create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange ->
        {
            try (exchange)
            {
                byte[] bytes = aBody.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(aStatus, bytes.length == 0 ? -1 : bytes.length);
                if (bytes.length > 0)
                {
                    try (OutputStream os = exchange.getResponseBody())
                    {
                        os.write(bytes);
                    }
                }
            }
        });
        server.start();
        return server;
    }


    private static HttpsServer serveTls() throws IOException, GeneralSecurityException
    {
        KeyManagerFactory kmf = KeyManagerFactory
                .getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(selfSignedLocalhost(), PASSWORD);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(kmf.getKeyManagers(), null, null);
        HttpsServer server = HttpsServer
                .create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(context));
        server.createContext("/", exchange ->
        {
            try (exchange)
            {
                byte[] bytes = "A,B\n1,2\n".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody())
                {
                    os.write(bytes);
                }
            }
        });
        server.start();
        return server;
    }


    private static KeyStore selfSignedLocalhost() throws IOException, GeneralSecurityException
    {
        Path dir = Files.createTempDirectory("csv-probe-tls");
        Path file = dir.resolve("self.p12");
        try
        {
            Path keytool = Path.of(System.getProperty("java.home"), "bin", "keytool");
            Process p = new ProcessBuilder(keytool.toString(), "-genkeypair", "-alias", "self",
                    "-dname", "CN=localhost", "-ext", "san=dns:localhost", "-keyalg", "EC",
                    "-groupname", "secp256r1", "-validity", "2", "-storetype", "PKCS12",
                    "-keystore", file.toString(), "-storepass", new String(PASSWORD), "-keypass",
                    new String(PASSWORD)).redirectErrorStream(true).start();
            String log;
            try (InputStream out = p.getInputStream())
            {
                log = new String(out.readAllBytes(), StandardCharsets.UTF_8);
            }
            assertTrue(p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0, "keytool: " + log);
            KeyStore ks = KeyStore.getInstance("PKCS12");
            try (InputStream in = Files.newInputStream(file))
            {
                ks.load(in, PASSWORD);
            }
            return ks;
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
        finally
        {
            Files.deleteIfExists(file);
            Files.deleteIfExists(dir);
        }
    }


    private static boolean hasCause(Throwable aThrown, Class<? extends Throwable> aType)
    {
        for (Throwable t = aThrown; t != null; t = t.getCause())
        {
            if (aType.isInstance(t))
            {
                return true;
            }
        }
        return false;
    }

    /** Refuses every server, as the data browser's trust manager does on Reject. */
    private static final class RejectingTrustManager implements X509TrustManager
    {

        private final String refusal;

        private final AtomicInteger asked;

        RejectingTrustManager(String aRefusal, AtomicInteger aAsked)
        {
            refusal = aRefusal;
            asked = aAsked;
        }


        @Override
        public void checkServerTrusted(X509Certificate[] aChain, String aAuthType)
            throws CertificateException
        {
            asked.incrementAndGet();
            throw new CertificateException(refusal);
        }


        @Override
        public void checkClientTrusted(X509Certificate[] aChain, String aAuthType)
            throws CertificateException
        {
            throw new CertificateException("no client certificates");
        }


        @Override
        public X509Certificate[] getAcceptedIssuers()
        {
            return new X509Certificate[0];
        }
    }
}
