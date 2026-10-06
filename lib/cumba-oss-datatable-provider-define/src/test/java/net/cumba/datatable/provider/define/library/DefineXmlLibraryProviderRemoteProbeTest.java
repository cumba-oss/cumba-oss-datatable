package net.cumba.datatable.provider.define.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import net.cumba.cdisc.define.DefineCache;
import net.cumba.cdisc.define.DefineSupport;
import net.cumba.datatable.io.Property;
import org.junit.jupiter.api.Test;

/**
 * {@link DefineXmlLibraryProvider#getProviderProperties}: a remote ({@code http} / {@code https})
 * Define-XML the probe cannot read aborts the open (thrown, unchecked, with the UI's headline) — a
 * download the loader cannot interpret included, it would not load either; a readable one supplies
 * the study name; a local failure keeps the URI-based default
 * ({@link DefineXmlLibraryProviderTest}).
 */
class DefineXmlLibraryProviderRemoteProbeTest
{

    private static final String FIXTURE = "/net/cumba/datatable/provider/define/metadata/"
            + "pilot-clean-21.xml";

    @Test
    void aRemote404AbortsTheOpen() throws IOException
    {
        HttpServer server = serve(404, new byte[0]);
        try
        {
            int port = server.getAddress().getPort();
            URI uri = URI.create("http://localhost:" + port + "/missing/define.xml");

            UncheckedIOException ex = assertThrows(UncheckedIOException.class,
                    () -> new DefineXmlLibraryProvider().getProviderProperties(uri, null));

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
        URI uri = URI.create("http://localhost:" + port + "/define.xml");

        UncheckedIOException ex = assertThrows(UncheckedIOException.class,
                () -> new DefineXmlLibraryProvider().getProviderProperties(uri, null));

        assertTrue(ex.getMessage().startsWith("Opening aborted: localhost:" + port),
                ex.getMessage());
    }


    /**
     * The sibling of {@link DefineXmlLibraryProviderTest}'s unchecked fallback: the same unchecked
     * load failure on a remote URI aborts the open.
     */
    @Test
    void anUncheckedRemoteLoadFailureAbortsTheOpen() throws Exception
    {
        Field field = DefineCache.class.getDeclaredField("sharedInstance");
        field.setAccessible(true);
        Object previous = field.get(null);
        field.set(null, new DefineCache()
        {

            @Override
            protected DefineSupport loadDefine(URI aUri)
            {
                throw new IllegalStateException("structurally odd Define-XML");
            }
        });
        try
        {
            URI uri = URI.create("https://example.org/study/define.xml");

            UncheckedIOException ex = assertThrows(UncheckedIOException.class,
                    () -> new DefineXmlLibraryProvider().getProviderProperties(uri, null));

            assertEquals("Opening aborted: example.org:443 could not be read (structurally odd "
                    + "Define-XML).", ex.getMessage());
        }
        finally
        {
            field.set(null, previous);
        }
    }


    @Test
    void aReadableRemoteDefineSuppliesTheStudyName() throws IOException, URISyntaxException
    {
        byte[] define;
        try (InputStream in = DefineXmlLibraryProviderRemoteProbeTest.class
                .getResourceAsStream(FIXTURE))
        {
            assertNotNull(in, FIXTURE);
            define = in.readAllBytes();
        }
        URI local = DefineXmlLibraryProviderRemoteProbeTest.class.getResource(FIXTURE).toURI();
        String expected = new DefineXmlLibraryProvider().getProviderProperties(local, null).get(0)
                .defaultValue();
        HttpServer server = serve(200, define);
        try
        {
            URI uri = URI
                    .create("http://localhost:" + server.getAddress().getPort() + "/s/define.xml");

            List<Property> props = new DefineXmlLibraryProvider().getProviderProperties(uri, null);

            assertEquals(1, props.size());
            assertEquals(expected, props.get(0).defaultValue());
            assertNotEquals("define", expected, "the fixture yields a study name");
        }
        finally
        {
            server.stop(0);
        }
    }


    private static HttpServer serve(int aStatus, byte[] aBody) throws IOException
    {
        HttpServer server = HttpServer
                .create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange ->
        {
            try (exchange)
            {
                exchange.sendResponseHeaders(aStatus, aBody.length == 0 ? -1 : aBody.length);
                if (aBody.length > 0)
                {
                    try (OutputStream os = exchange.getResponseBody())
                    {
                        os.write(aBody);
                    }
                }
            }
        });
        server.start();
        return server;
    }
}
