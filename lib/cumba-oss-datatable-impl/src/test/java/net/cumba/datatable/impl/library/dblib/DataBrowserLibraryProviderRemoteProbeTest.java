package net.cumba.datatable.impl.library.dblib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.cumba.datatable.io.Property;
import org.junit.jupiter.api.Test;

/**
 * {@link DataBrowserLibraryProvider#getProviderProperties}: a remote ({@code http} / {@code https})
 * {@code .dblib} the probe cannot read aborts the open (thrown, unchecked, with the UI's headline)
 * — a download that does not parse included, it would not load either; a readable one supplies its
 * stored name; a local failure keeps the URI-based default
 * ({@code DataBrowserLibraryProviderEdgeCasesTest}).
 */
class DataBrowserLibraryProviderRemoteProbeTest
{

    @Test
    void aRemote404AbortsTheOpen() throws IOException
    {
        HttpServer server = serve(404, "");
        try
        {
            int port = server.getAddress().getPort();
            URI uri = URI.create("http://localhost:" + port + "/missing.dblib");

            UncheckedIOException ex = assertThrows(UncheckedIOException.class,
                    () -> new DataBrowserLibraryProvider().getProviderProperties(uri, null));

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
        URI uri = URI.create("http://localhost:" + port + "/x.dblib");

        UncheckedIOException ex = assertThrows(UncheckedIOException.class,
                () -> new DataBrowserLibraryProvider().getProviderProperties(uri, null));

        assertTrue(ex.getMessage().startsWith("Opening aborted: localhost:" + port),
                ex.getMessage());
    }


    @Test
    void aRemoteDownloadThatDoesNotParseAbortsTheOpen() throws IOException
    {
        HttpServer server = serve(200, "{not even json");
        try
        {
            int port = server.getAddress().getPort();
            URI uri = URI.create("http://localhost:" + port + "/bad.dblib");

            UncheckedIOException ex = assertThrows(UncheckedIOException.class,
                    () -> new DataBrowserLibraryProvider().getProviderProperties(uri, null));

            assertTrue(
                    ex.getMessage().startsWith(
                            "Opening aborted: localhost:" + port + " could not be read ("),
                    ex.getMessage());
        }
        finally
        {
            server.stop(0);
        }
    }


    @Test
    void aReadableRemoteDblibSuppliesItsStoredName() throws IOException
    {
        HttpServer server = serve(200, "{\"name\":\"RemoteLib\"}");
        try
        {
            URI uri = URI
                    .create("http://localhost:" + server.getAddress().getPort() + "/lib.dblib");

            List<Property> props = new DataBrowserLibraryProvider().getProviderProperties(uri,
                    null);

            assertEquals(1, props.size());
            assertEquals("RemoteLib", props.get(0).defaultValue());
        }
        finally
        {
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
}
