package net.cumba.datatable.provider.sas.xpt;

import static net.cumba.datatable.impl.provider.DataTableMetaSupport.META_KEY_CREATED;
import static net.cumba.datatable.impl.provider.DataTableMetaSupport.META_KEY_FILE_FORMAT;
import static net.cumba.datatable.impl.provider.DataTableMetaSupport.META_KEY_MODIFIED;
import static net.cumba.datatable.impl.provider.DataTableMetaSupport.META_KEY_TRANSPORT_VERSION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.io.Property;
import net.cumba.datatable.provider.sas.testsupport.LoggerCapture;
import net.cumba.datatable.provider.sas.xpt.library.XptLibraryMember;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The {@link XptTableProvider} entry points the rest of the suite does not reach: the {@code file:}
 * versus download decision, the URI-fragment member selection on the metadata-only path, the
 * charset property, the table-level metadata both paths must agree on, the progress events and the
 * truncated-file failure.
 *
 * <p>
 * ⚠ Every test hands the provider a THROW-AWAY copy of a fixture, never the committed file: this
 * provider deletes the file it downloaded in a {@code finally}, and a mutation that conflates the
 * local and downloaded arms then deletes the caller's own dataset — which is how an earlier
 * revision of {@code XptTableProviderDatasetSizeTest} had {@code testdata/xpt/01_plain/adsl.xpt}
 * removed from the working tree by a Pitest pass.
 * </p>
 */
class XptTableProviderPathsTest
{

    /** Disambiguates provide(URI, FileInfo) from provide(URI, File) for a null argument. */
    private static final FileInfo NO_FILE_INFO = null;

    private static final String LOGGER_NAME = XptTableProvider.class.getName();

    private static final String SINGLE = "testdata/xpt/01_plain/adsl.xpt";

    private static final String MULTI = "testdata/xpt/05_multiple/sdtm.xpt";

    private static File copyOf(String aFixture, Path aTmp) throws IOException
    {
        File src = new File(System.getProperty("repoRoot"), aFixture);
        assertTrue(src.isFile(), () -> "expected fixture: " + src.getAbsolutePath());
        Path copy = aTmp.resolve(src.getName());
        Files.copy(src.toPath(), copy);
        return copy.toFile();
    }

    /** A provider that fails the test if anything routes a local file through the downloader. */
    private static final class NoDownloadProvider extends XptTableProvider
    {

        @Override
        protected File downloadToFile(URI aUri) throws IOException
        {
            throw new IOException("must not download " + aUri);
        }
    }

    // ==================== file: must not go through the downloader ====================

    @Test
    void aLocalFileIsReadInPlaceRatherThanCopiedThroughTheDownloader(@TempDir Path aTmp)
        throws IOException
    {
        // The downloader copies the whole file to a temp file first. For a 5 GB SAS dataset that is
        // a second 5 GB — and the temp copy is then deleted in a finally, which is why the local
        // arm must stay out of that block by construction rather than by a runtime flag.
        File file = copyOf(SINGLE, aTmp);
        NoDownloadProvider provider = new NoDownloadProvider();

        IDataTable table = provider.provide(file.toURI(), NO_FILE_INFO);
        assertEquals(49, table.getColumnCount());
        assertTrue(file.isFile(), "the caller's own file must still be there afterwards");
    }


    @Test
    void aLocalFileIsAlsoReadInPlaceForMetadataOnly(@TempDir Path aTmp) throws IOException
    {
        File file = copyOf(SINGLE, aTmp);
        NoDownloadProvider provider = new NoDownloadProvider();

        DataTableMeta meta = provider.provideMetaData(file.toURI(), null);
        assertEquals("ADSL", meta.getName());
        assertTrue(file.isFile(), "the caller's own file must never be deleted");
    }


    @Test
    void aFragmentOnALocalUriSelectsThatMember(@TempDir Path aTmp) throws IOException
    {
        File file = copyOf(MULTI, aTmp);
        NoDownloadProvider provider = new NoDownloadProvider();

        IDataTable table = provider.provide(URI.create(file.toURI() + "#DD"), NO_FILE_INFO);
        assertEquals("DD", table.getMetaData().getName());
    }

    // ==================== the non-file: download path ====================


    @Test
    void aRemoteXptIsDownloadedAndParsed(@TempDir Path aTmp) throws IOException
    {
        File file = copyOf(SINGLE, aTmp);
        IDataTable table = new XptTableProvider().provide(serve(file.toPath(), "/adsl.xpt"),
                NO_FILE_INFO);
        assertEquals(49, table.getColumnCount());
        assertEquals("ADSL", table.getMetaData().getName());
    }


    @Test
    void aRemoteXptWithAFragmentSelectsThatMember(@TempDir Path aTmp) throws IOException
    {
        File file = copyOf(MULTI, aTmp);
        URI uri = URI.create(serve(file.toPath(), "/sdtm.xpt") + "#DD");
        IDataTable table = new XptTableProvider().provide(uri, NO_FILE_INFO);
        assertEquals("DD", table.getMetaData().getName(),
                "the fragment must select the member on the download path too, not the first one");
    }


    @Test
    void aRemoteXptServesMetadataOnly(@TempDir Path aTmp) throws IOException
    {
        File file = copyOf(SINGLE, aTmp);
        DataTableMeta meta = new XptTableProvider()
                .provideMetaData(serve(file.toPath(), "/adsl.xpt"), null);
        assertEquals("ADSL", meta.getName());
        assertEquals(49, meta.getColumnCount());
    }

    // ==================== fragment resolution on the metadata path ====================


    @Test
    void metadataForANamedMemberIsTheNamedMemberNotTheFirst(@TempDir Path aTmp) throws IOException
    {
        File file = copyOf(MULTI, aTmp);
        DataTableMeta meta = new NoDownloadProvider()
                .provideMetaData(URI.create(file.toURI() + "#dd"), null);
        // Case-insensitive by design: SAS member names are case-insensitive.
        assertEquals("DD", meta.getName());
        assertEquals(12, meta.getColumnCount());
    }


    @Test
    void metadataForAnUnknownMemberNamesTheMemberItCouldNotFind(@TempDir Path aTmp)
        throws IOException
    {
        File file = copyOf(MULTI, aTmp);
        IOException ex = assertThrows(IOException.class, () -> new NoDownloadProvider()
                .provideMetaData(URI.create(file.toURI() + "#NOSUCH"), null));
        assertTrue(ex.getMessage().contains("NOSUCH"), ex.getMessage());
    }

    // ==================== table-level metadata, identical on both paths ====================


    @Test
    void theTableMetadataCarriesTheFormatAndTheDatasetTimestamps(@TempDir Path aTmp)
        throws IOException
    {
        // External oracle: the bytes of the committed fixture. adsl.xpt's member header records
        // 16APR22:20:09:03 as both the created and the modified stamp, and its label as
        // "Subject-Level Analysis Dataset"; the library header says XPORT version 5.
        File file = copyOf(SINGLE, aTmp);
        NoDownloadProvider provider = new NoDownloadProvider();

        DataTableMeta fromMetaOnly = provider.provideMetaData(file.toURI(), null);
        DataTableMeta fromLoad = provider.provide(file.toURI(), NO_FILE_INFO).getMetaData();

        for (DataTableMeta meta : List.of(fromMetaOnly, fromLoad))
        {
            assertEquals("XPORT", meta.getMetaData(META_KEY_FILE_FORMAT));
            assertEquals("5", meta.getMetaData(META_KEY_TRANSPORT_VERSION));
            assertEquals("2022-04-16T20:09:03", meta.getMetaData(META_KEY_CREATED));
            assertEquals("2022-04-16T20:09:03", meta.getMetaData(META_KEY_MODIFIED));
            assertEquals("Subject-Level Analysis Dataset", meta.getLabel());
        }
    }


    @Test
    void theDatasetTimestampsAreTheHeaderWallClockWhateverTheDefaultZone(@TempDir Path aTmp)
        throws IOException
    {
        // The XPT member header stores its stamps as zone-free text (16APR22:20:09:03). They
        // must reach the metadata exactly as written, whatever the JVM default zone and locale:
        // under UTC+14 a one-way default-zone conversion moves the value by hours, and under a
        // Thai default locale a locale-sensitive formatter (the old SimpleDateFormat path) prints
        // Buddhist-era years (2565).
        File file = copyOf(SINGLE, aTmp);
        TimeZone saved = TimeZone.getDefault();
        Locale savedLocale = Locale.getDefault();
        Locale savedFormat = Locale.getDefault(Locale.Category.FORMAT);
        Locale savedDisplay = Locale.getDefault(Locale.Category.DISPLAY);
        try
        {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));
            Locale.setDefault(Locale.forLanguageTag("th-TH"));
            DataTableMeta meta = new NoDownloadProvider().provideMetaData(file.toURI(), null);
            assertEquals("2022-04-16T20:09:03", meta.getMetaData(META_KEY_CREATED));
            assertEquals("2022-04-16T20:09:03", meta.getMetaData(META_KEY_MODIFIED));
        }
        finally
        {
            TimeZone.setDefault(saved);
            Locale.setDefault(savedLocale);
            Locale.setDefault(Locale.Category.FORMAT, savedFormat);
            Locale.setDefault(Locale.Category.DISPLAY, savedDisplay);
        }
    }


    @Test
    void anEmptyDatasetLabelStaysAbsentRatherThanBecomingAnEmptyLabel(@TempDir Path aTmp)
        throws IOException
    {
        // Every member of sdtm.xpt carries a blank label. A blank label must not be stored as "",
        // which would render as an empty title in the UI where "no label" falls back to the name.
        File file = copyOf(MULTI, aTmp);
        NoDownloadProvider provider = new NoDownloadProvider();

        DataTableMeta fromMetaOnly = provider.provideMetaData(URI.create(file.toURI() + "#AE"),
                null);
        DataTableMeta fromLoad = provider.provide(URI.create(file.toURI() + "#AE"), NO_FILE_INFO)
                .getMetaData();

        assertNull(fromMetaOnly.getLabel(), "a blank XPT label must not be stored at all");
        assertNull(fromLoad.getLabel(), "a blank XPT label must not be stored at all");
        assertEquals("2026-01-29T16:38:38", fromMetaOnly.getMetaData(META_KEY_CREATED));
        assertEquals("2026-01-29T16:38:38", fromLoad.getMetaData(META_KEY_MODIFIED));
    }

    // ==================== progress events ====================

    // ==================== the charset property ====================


    @Test
    void theCharsetPropertyIsAdoptedForTheLoad(@TempDir Path aTmp) throws IOException
    {
        // XPT records no encoding, so the user's choice is the only source of truth. A provider
        // that ignores the property decodes with UTF-8 whatever the user asked for.
        File file = copyOf(SINGLE, aTmp);
        XptTableProvider provider = new NoDownloadProvider();
        Map<Property, String> props = Map.of(XptTableProvider.PROP_CHARSET, "ISO-8859-1");

        assertNotNull(provider.provide(file.toURI(), null, props));
        assertEquals(StandardCharsets.ISO_8859_1, provider.getCharset());
    }


    @Test
    void theCharsetPropertyIsAdoptedForALibraryMemberThatCarriesNone(@TempDir Path aTmp)
        throws IOException
    {
        // A member opened from an XPT library normally carries the charset the library was opened
        // with; one without falls back to the property, exactly like the plain URI path.
        File file = copyOf(SINGLE, aTmp);
        XptTableProvider provider = new NoDownloadProvider();
        XptLibraryMember member = XptLibraryMember.builder().name("ADSL").uri(file.toURI()).build();

        assertNotNull(provider.provide(member, null,
                Map.of(XptTableProvider.PROP_CHARSET, "windows-1252")));
        assertEquals("windows-1252", provider.getCharset().name());
    }


    @Test
    void aMemberThatCarriesACharsetKeepsItRatherThanRePromptingTheUser(@TempDir Path aTmp)
        throws IOException
    {
        File file = copyOf(SINGLE, aTmp);
        XptTableProvider provider = new NoDownloadProvider();
        XptLibraryMember member = XptLibraryMember.builder().name("ADSL").uri(file.toURI())
                .charset(StandardCharsets.ISO_8859_1).build();

        assertNotNull(provider.provide(member, null,
                Map.of(XptTableProvider.PROP_CHARSET, "windows-1252")));
        assertEquals(StandardCharsets.ISO_8859_1, provider.getCharset(),
                "the library's own charset wins over the property");
    }


    @Test
    void anIllegalCharsetNameFallsBackAndSaysSo()
    {
        // isSupported() itself throws IllegalCharsetNameException for a malformed name, so this is
        // a different path from "legal name, no such charset" — and it is reachable from the UI,
        // where the charset property is free text.
        try (LoggerCapture log = LoggerCapture.attach(LOGGER_NAME))
        {
            Map<Property, String> props = Map.of(XptTableProvider.PROP_CHARSET, "not a name!");
            assertEquals(ObservationIteratorXpt.DEFAULT_CHARSET,
                    XptTableProvider.resolveCharset(props));
            assertTrue(log.containsMessageContaining("is not a legal charset name"),
                    "the rejected name must reach the log, or round-trip mojibake is undiagnosable:"
                            + " " + log.messages());
        }
    }


    @Test
    void anUnsupportedCharsetNameFallsBackAndSaysSo()
    {
        try (LoggerCapture log = LoggerCapture.attach(LOGGER_NAME))
        {
            Map<Property, String> props = Map.of(XptTableProvider.PROP_CHARSET,
                    "NoSuchCharsetX-99");
            assertEquals(ObservationIteratorXpt.DEFAULT_CHARSET,
                    XptTableProvider.resolveCharset(props));
            assertTrue(log.containsMessageContaining("is not supported by the JVM"),
                    log.messages().toString());
            assertFalse(log.containsMessageContaining("is not a legal charset name"),
                    "a legal-but-unknown name must not be reported as malformed");
        }
    }

    // ==================== a truncated file fails loudly ====================


    @Test
    void aTruncatedXptFailsAsAnIoExceptionNotAnIllegalState(@TempDir Path aTmp) throws IOException
    {
        // XPT stores no row count, so a stream that ends mid-observation can only be detected here.
        // The iterator reports it as an IllegalStateException to honour the Iterator contract and
        // the provider must unwrap it: callers catch IOException, and an IllegalStateException
        // escaping the load reaches the UI as an unexpected-error dialog instead of "this file is
        // truncated".
        byte[] all = Files.readAllBytes(new File(System.getProperty("repoRoot"), SINGLE).toPath());
        int lastData = all.length;
        while (lastData > 0 && all[lastData - 1] == ' ')
        {
            lastData--;
        }
        assertTrue(lastData > 0 && lastData < all.length, "fixture has no blank padding to cut at");
        Path cut = aTmp.resolve("truncated.xpt");
        // Drop the final byte of the last observation's data, and all padding after it.
        Files.write(cut, java.util.Arrays.copyOf(all, lastData - 1));

        IOException ex = assertThrows(IOException.class,
                () -> new NoDownloadProvider().provide(cut.toUri(), cut.toFile()));
        assertTrue(ex.getMessage().contains("Truncated XPT file"), ex.getMessage());
    }


    /** Serve one file over loopback HTTP, once, so the non-{@code file:} arm is exercised. */
    private static URI serve(Path aFile, String aPath) throws IOException
    {
        // ⛔ The bind address and the URI host MUST come from the same object (review, 2026-09-17).
        // [AddressSelection] moved the bind to getLoopbackAddress() while the URI below still
        // hard-coded 127.0.0.1. Under -Djava.net.preferIPv6Addresses=true, or on a host whose
        // loopback is IPv6-only, getLoopbackAddress() returns ::1: the server binds [::1]:port and
        // the client dials 127.0.0.1:port -> ConnectException, and the non-file: arm this test
        // exists to exercise never runs. They used to agree by construction; after that fix they
        // agreed only by accident of preferIPv6Addresses defaulting to false.
        InetAddress loopback = InetAddress.getLoopbackAddress();
        HttpServer server = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
        server.createContext(aPath, exchange ->
        {
            byte[] bytes = Files.readAllBytes(aFile);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody(); var _ = exchange.getRequestBody())
            {
                os.write(bytes);
            }
            server.stop(0);
        });
        server.start();
        String host = loopback instanceof java.net.Inet6Address
                ? "[" + loopback.getHostAddress() + "]"
                : loopback.getHostAddress();
        return URI.create("http://" + host + ":" + server.getAddress().getPort() + aPath);
    }


    /**
     * E3 (PLAN-provider-encoding-metadata): XPT records no encoding, so the pair names the charset
     * the provider decoded with and whether a non-default one was requested — the same answer on
     * the load and on the metadata-only read, which until this plan ignored the property.
     */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(delimiter = '|', nullValues = "<none>", value =
    {
            "<none>|UTF-8|DEFAULT", //
            "UTF-8|UTF-8|DEFAULT", //
            "no-such-charset|UTF-8|DEFAULT", //
            "ISO-8859-1|ISO-8859-1|CONFIGURED"
    })
    void theEncodingPairNamesTheCharsetTheLoadAndTheMetadataReadDecodedWith(String aRequested,
            String aEncoding, String aSource, @TempDir Path aTmp)
        throws IOException
    {
        File file = copyOf(SINGLE, aTmp);
        Map<Property, String> props = aRequested == null ? Map.of()
                : Map.of(XptTableProvider.PROP_CHARSET, aRequested);
        for (DataTableMeta meta : List.of(
                new NoDownloadProvider().provide(file.toURI(), NO_FILE_INFO, props).getMetaData(),
                new NoDownloadProvider().provideMetaData(file.toURI(), NO_FILE_INFO, props)))
        {
            assertEquals(aEncoding, meta.getMetaData("Encoding"), aRequested);
            assertEquals(aSource, meta.getMetaData("EncodingSource"), aRequested);
        }
    }


    @Test
    void aLibraryMembersCharsetIsReportedOnTheLoadAndTheMetadataRead(@TempDir Path aTmp)
        throws IOException
    {
        // The member's charset (chosen when its library was opened) wins over the property on
        // both paths; the metadata-only read used to drop both.
        File file = copyOf(SINGLE, aTmp);
        XptLibraryMember member = XptLibraryMember.builder().name("ADSL").uri(file.toURI())
                .charset(StandardCharsets.ISO_8859_1).build();
        Map<Property, String> props = Map.of(XptTableProvider.PROP_CHARSET, "windows-1252");
        for (DataTableMeta meta : List.of(
                new NoDownloadProvider().provide(member, null, props).getMetaData(),
                new NoDownloadProvider().provideMetaData(member, null, props)))
        {
            assertEquals("ISO-8859-1", meta.getMetaData("Encoding"));
            assertEquals("CONFIGURED", meta.getMetaData("EncodingSource"));
        }
    }


    @Test
    void aLibraryMemberWithoutACharsetReportsTheProperty(@TempDir Path aTmp) throws IOException
    {
        File file = copyOf(SINGLE, aTmp);
        XptLibraryMember member = XptLibraryMember.builder().name("ADSL").uri(file.toURI()).build();
        Map<Property, String> props = Map.of(XptTableProvider.PROP_CHARSET, "windows-1252");
        for (DataTableMeta meta : List.of(
                new NoDownloadProvider().provide(member, null, props).getMetaData(),
                new NoDownloadProvider().provideMetaData(member, null, props)))
        {
            assertEquals("windows-1252", meta.getMetaData("Encoding"));
            assertEquals("CONFIGURED", meta.getMetaData("EncodingSource"));
        }
    }


    @Test
    void aLibraryMemberCarryingTheDefaultCharsetReportsDefault(@TempDir Path aTmp)
        throws IOException
    {
        // The library was opened with the default (the data browser fills every property with its
        // default): the member then reads exactly like a file nobody configured.
        File file = copyOf(SINGLE, aTmp);
        XptLibraryMember member = XptLibraryMember.builder().name("ADSL").uri(file.toURI())
                .charset(StandardCharsets.UTF_8).build();
        DataTableMeta meta = new NoDownloadProvider().provideMetaData(member, null, Map.of());
        assertEquals("UTF-8", meta.getMetaData("Encoding"));
        assertEquals("DEFAULT", meta.getMetaData("EncodingSource"));
    }
}
