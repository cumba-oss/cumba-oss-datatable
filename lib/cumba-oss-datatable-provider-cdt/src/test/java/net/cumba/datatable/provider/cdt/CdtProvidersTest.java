package net.cumba.datatable.provider.cdt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.library.IDataTableLibrary;
import net.cumba.datatable.library.ILibraryMember;
import net.cumba.datatable.provider.IDataTableProvider;
import net.cumba.datatable.provider.cdt.library.CdtLibrary;
import net.cumba.datatable.provider.cdt.library.CdtLibraryMember;
import net.cumba.datatable.provider.cdt.library.CdtLibraryProvider;
import net.cumba.datatable.provider.cdt.library.CdtLibrarySupplier;
import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for the {@code .cdt} SPI suppliers and providers ({@link CdtProviderSupplier},
 * {@link CdtTableProvider}, {@link CdtLibrarySupplier}, {@link CdtLibraryProvider}).
 */
class CdtProvidersTest
{

    private static final String MULTI = """
            dataset DM label="Demographics" class=SDTM
            col USUBJID type=Char label="Subject" length=10 codelist=NO
            col AGE type=Num
            ---
            S001 | 42
            S002 | 37
            ---

            dataset AE
            col USUBJID type=Char
            col AETERM type=Char
            ---
            S001 | Headache
            ---
            """;

    private static Path write(Path aDir, String aName, String aBody) throws IOException
    {
        Path p = aDir.resolve(aName);
        Files.writeString(p, aBody, StandardCharsets.UTF_8);
        return p;
    }

    // ---- CdtProviderSupplier -------------------------------------------------------


    @Test
    void supplierReturnsProviderForCdtUri()
    {
        CdtProviderSupplier s = new CdtProviderSupplier();
        IDataTableProvider p = s.getProvider(URI.create("file:/some/x.cdt"),
                CdtProviderSupplier.FI_CDT);
        assertNotNull(p);
        assertTrue(p instanceof CdtTableProvider);
    }


    @Test
    void supplierReturnsNullForNonCdtUri()
    {
        CdtProviderSupplier s = new CdtProviderSupplier();
        // No FileInfo and a non-.cdt extension → canProvideFor is false → null.
        assertNull(s.getProvider(URI.create("file:/some/x.csv"), null));
    }


    @Test
    void supplierCanProvideForByExtension()
    {
        CdtProviderSupplier s = new CdtProviderSupplier();
        assertTrue(s.canProvideFor(URI.create("file:/d/data.cdt")));
        assertFalse(s.canProvideFor(URI.create("file:/d/data.txt")));
    }


    @Test
    void fileInfoIsCdtExtension()
    {
        assertEquals("cdt", CdtProviderSupplier.FI_CDT.getFileExtension());
        assertEquals(1, CdtProviderSupplier.FIS.size());
    }

    // ---- CdtTableProvider.provide --------------------------------------------------


    @Test
    void provideReturnsFirstDatasetWhenNoFragment(@TempDir Path tmp) throws IOException
    {
        Path p = write(tmp, "multi.cdt", MULTI);
        IDataTable t = new CdtTableProvider().provide(p.toUri(), CdtProviderSupplier.FI_CDT);
        assertEquals("DM", t.getMetaData().getName());
        assertEquals(2L, t.getRowCount());
        assertEquals("S001", t.getValue(0, 0));
        // The table URI gets the dataset name as its fragment.
        assertEquals("DM", t.getMetaData().getTableURI().getFragment());
    }


    @Test
    void provideReturnsNamedDatasetForFragment(@TempDir Path tmp) throws IOException
    {
        Path p = write(tmp, "multi.cdt", MULTI);
        URI uri = URI.create(p.toUri() + "#AE");
        IDataTable t = new CdtTableProvider().provide(uri, CdtProviderSupplier.FI_CDT);
        assertEquals("AE", t.getMetaData().getName());
        assertEquals(1L, t.getRowCount());
        assertEquals("Headache", t.getValue(0, 1));
    }


    @Test
    void provideThrowsForMissingFragmentDataset(@TempDir Path tmp) throws IOException
    {
        Path p = write(tmp, "multi.cdt", MULTI);
        URI uri = URI.create(p.toUri() + "#NOPE");
        IOException ex = assertThrows(IOException.class,
                () -> new CdtTableProvider().provide(uri, CdtProviderSupplier.FI_CDT));
        assertTrue(ex.getMessage().contains("NOPE"), ex.getMessage());
    }


    @Test
    void provideMetaDataReturnsFirstDatasetMeta(@TempDir Path tmp) throws IOException
    {
        Path p = write(tmp, "multi.cdt", MULTI);
        DataTableMeta meta = new CdtTableProvider().provideMetaData(p.toUri(),
                CdtProviderSupplier.FI_CDT);
        assertEquals("DM", meta.getName());
        assertEquals("Demographics", meta.getLabel());
        assertEquals(2, meta.getColumnCount());
        assertEquals(2L, meta.getRowCount());
        // class attribute propagated to table-level metadata.
        assertEquals("SDTM", meta.getMetaData("class"));
        assertEquals("DM", meta.getTableURI().getFragment());
    }


    @Test
    void provideMetaDataReturnsNamedDatasetMeta(@TempDir Path tmp) throws IOException
    {
        Path p = write(tmp, "multi.cdt", MULTI);
        URI uri = URI.create(p.toUri() + "#AE");
        DataTableMeta meta = new CdtTableProvider().provideMetaData(uri,
                CdtProviderSupplier.FI_CDT);
        assertEquals("AE", meta.getName());
        assertEquals(2, meta.getColumnCount());
    }


    @Test
    void provideMetaDataThrowsForMissingFragmentDataset(@TempDir Path tmp) throws IOException
    {
        Path p = write(tmp, "multi.cdt", MULTI);
        URI uri = URI.create(p.toUri() + "#NOPE");
        assertThrows(IOException.class,
                () -> new CdtTableProvider().provideMetaData(uri, CdtProviderSupplier.FI_CDT));
    }


    @Test
    void providerExposesSupportedFileInfosAndNameDescription()
    {
        CdtTableProvider p = new CdtTableProvider();
        assertEquals(CdtProviderSupplier.FIS, p.getSupportedFileInfos());
        assertEquals("CdtTableProvider", p.getName());
        assertNotNull(p.getDescription());
    }

    private static final String SINGLE = """
            dataset ONE
            col A type=Char
            ---
            v
            ---
            """;

    /**
     * Serves {@code aBody} over a one-shot local HTTP server and runs {@code aConsumer} with the
     * resulting {@code http://...} URI. An {@code http:} scheme is hierarchical (so it supports the
     * fragment replacement the providers do) yet not {@code "file"}, which forces
     * {@code readContent} down its {@code toURL().openStream()} branch.
     */
    private static void withHttpCdt(String aBody, HttpUriConsumer aConsumer) throws IOException
    {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 0), 0);
        byte[] bytes = aBody.getBytes(StandardCharsets.UTF_8);
        server.createContext("/data.cdt", exchange ->
        {
            exchange.sendResponseHeaders(200, bytes.length);
            try (var os = exchange.getResponseBody())
            {
                os.write(bytes);
            }
        });
        server.start();
        try
        {
            URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/data.cdt");
            aConsumer.accept(uri);
        }
        finally
        {
            server.stop(0);
        }
    }

    @FunctionalInterface
    private interface HttpUriConsumer
    {

        void accept(URI aUri) throws IOException;
    }

    @Test
    void provideReadsNonFileUriViaStream() throws IOException
    {
        withHttpCdt(SINGLE, uri ->
        {
            // The http: scheme drives readContent's openStream branch (non-"file").
            IDataTable t = new CdtTableProvider().provide(uri, CdtProviderSupplier.FI_CDT);
            assertEquals("ONE", t.getMetaData().getName());
            assertEquals("v", t.getValue(0, 0));

            DataTableMeta meta = new CdtTableProvider().provideMetaData(uri,
                    CdtProviderSupplier.FI_CDT);
            assertEquals("ONE", meta.getName());
        });
    }


    @Test
    void libraryProvideReadsNonFileUriViaStream() throws IOException
    {
        withHttpCdt(SINGLE, uri ->
        {
            CdtLibraryProvider lp = new CdtLibraryProvider();
            // The http: scheme drives the library readContent's openStream branch too.
            IDataTableLibrary lib = lp.provide(uri, CdtProviderSupplier.FI_CDT);
            ILibraryMember[] members = lp.provideLibraryMembers(lib).toArray(ILibraryMember[]::new);
            assertEquals(1, members.length);
            assertEquals("ONE", members[0].getName());
        });
    }

    // ---- column metadata mapping in provideMetaData --------------------------------


    @Test
    void provideMetaDataMapsColumnTypesLabelsLengthAndCodelist(@TempDir Path tmp) throws IOException
    {
        Path p = write(tmp, "multi.cdt", MULTI);
        DataTableMeta meta = new CdtTableProvider().provideMetaData(p.toUri(),
                CdtProviderSupplier.FI_CDT);
        DataTableColumnMeta usubjid = meta.getColumn(0);
        assertEquals("USUBJID", usubjid.getName());
        assertEquals("Subject", usubjid.getLabel());
        assertEquals(DataValueType.STRING, usubjid.getType());
        assertEquals("Char", usubjid.getNativeType());
        assertEquals(10, usubjid.getLength());
        assertEquals("NO", usubjid.getMetaData(CdtTableBuilder.COLUMN_META_CODELIST));

        DataTableColumnMeta age = meta.getColumn(1);
        assertEquals(DataValueType.DOUBLE, age.getType());
        assertEquals("Num", age.getNativeType());
    }

    // ---- CdtLibrarySupplier --------------------------------------------------------


    @Test
    void librarySupplierAcceptsBareCdtUri()
    {
        CdtLibrarySupplier s = new CdtLibrarySupplier();
        assertTrue(s.canProvideFor(URI.create("file:/d/x.cdt"), CdtProviderSupplier.FI_CDT));
        assertNotNull(s.getProvider(URI.create("file:/d/x.cdt"), CdtProviderSupplier.FI_CDT));
    }


    @Test
    void librarySupplierRejectsFragmentUri()
    {
        CdtLibrarySupplier s = new CdtLibrarySupplier();
        URI withFragment = URI.create("file:/d/x.cdt#DM");
        // A fragment identifies a single dataset → the library supplier declines.
        assertFalse(s.canProvideFor(withFragment, CdtProviderSupplier.FI_CDT));
        assertNull(s.getProvider(withFragment, CdtProviderSupplier.FI_CDT));
    }


    @Test
    void librarySupplierRejectsNullUri()
    {
        CdtLibrarySupplier s = new CdtLibrarySupplier();
        assertFalse(s.canProvideFor(null, CdtProviderSupplier.FI_CDT));
    }


    @Test
    void librarySupplierExposesFileInfos()
    {
        assertEquals(List.of(CdtProviderSupplier.FI_CDT), CdtLibrarySupplier.FIS);
    }

    // ---- CdtLibraryProvider --------------------------------------------------------


    @Test
    void libraryProvideListsAllDatasetsAsMembers(@TempDir Path tmp) throws IOException
    {
        Path p = write(tmp, "study.cdt", MULTI);
        CdtLibraryProvider lp = new CdtLibraryProvider();
        IDataTableLibrary lib = lp.provide(p.toUri(), CdtProviderSupplier.FI_CDT);
        assertTrue(lib instanceof CdtLibrary);
        assertEquals("Cdt", lib.getType());
        // Library name derived from the file name, upper-cased.
        assertEquals("STUDY", lib.getName());

        ILibraryMember[] members = lp.provideLibraryMembers(lib).toArray(ILibraryMember[]::new);
        assertEquals(2, members.length);
        assertEquals("DM", members[0].getName());
        assertEquals("Demographics", members[0].getLabel());
        assertEquals("AE", members[1].getName());
        // Member URI carries the dataset name as the fragment.
        assertEquals("DM", members[0].getUri().getFragment());
        assertEquals(lib, members[0].getLibrary());
    }


    @Test
    void libraryProvideMemberColumnsMapMetadata(@TempDir Path tmp) throws IOException
    {
        Path p = write(tmp, "study.cdt", MULTI);
        CdtLibraryProvider lp = new CdtLibraryProvider();
        IDataTableLibrary lib = lp.provide(p.toUri(), CdtProviderSupplier.FI_CDT);
        ILibraryMember dm = lp.provideLibraryMembers(lib).filter(m -> "DM".equals(m.getName()))
                .findFirst().orElseThrow();

        DataTableColumnMeta[] cols = lp.provideLibraryMemberColumns(dm)
                .toArray(DataTableColumnMeta[]::new);
        assertEquals(2, cols.length);
        assertEquals("USUBJID", cols[0].getName());
        assertEquals("Subject", cols[0].getLabel());
        assertEquals(DataValueType.STRING, cols[0].getType());
        assertEquals(10, cols[0].getLength());
        assertEquals("NO", cols[0].getMetaData(CdtTableBuilder.COLUMN_META_CODELIST));
        assertEquals(DataValueType.DOUBLE, cols[1].getType());
    }


    @Test
    void libraryProvideMembersReturnsEmptyForForeignLibrary() throws IOException
    {
        CdtLibraryProvider lp = new CdtLibraryProvider();
        // A non-CdtLibrary argument yields an empty stream rather than throwing.
        IDataTableLibrary foreign = new IDataTableLibraryStub();
        assertEquals(0, lp.provideLibraryMembers(foreign).count());
    }


    @Test
    void libraryProvideMemberColumnsReturnsEmptyForForeignMember() throws IOException
    {
        CdtLibraryProvider lp = new CdtLibraryProvider();
        ILibraryMember foreign = new ILibraryMemberStub();
        assertEquals(0, lp.provideLibraryMemberColumns(foreign).count());
    }


    @Test
    void libraryProvideMemberColumnsReturnsEmptyWhenColumnsNull(@TempDir Path tmp)
        throws IOException
    {
        // A CdtLibraryMember whose columns array is null → empty stream.
        CdtLibraryMember member = CdtLibraryMember.builder().name("X").build();
        CdtLibraryProvider lp = new CdtLibraryProvider();
        assertEquals(0, lp.provideLibraryMemberColumns(member).count());
    }


    @Test
    void libraryProviderExposesNameAndDescription()
    {
        CdtLibraryProvider lp = new CdtLibraryProvider();
        assertEquals(CdtLibrarySupplier.FIS, lp.getSupportedFileInfos());
        assertEquals("CdtLibraryProvider", lp.getName());
        assertNotNull(lp.getDescription());
    }


    @Test
    void emptyMembersStreamWhenLibraryHasNoMembers()
    {
        try (CdtLibrary lib = new CdtLibrary("L", null, URI.create("file:/x.cdt")))
        {
            // members never set → empty stream (guards the null-members branch).
            assertEquals(0, lib.getMembers().count());
        }
        catch (Exception ex)
        {
            fail(ex);
        }
    }

    // ---- minimal stubs for the "foreign" branches ----------------------------------

    private static final class IDataTableLibraryStub implements IDataTableLibrary
    {

        @Override
        public String getName()
        {
            return "stub";
        }


        @Override
        public String getLabel()
        {
            return null;
        }


        @Override
        public URI getUri()
        {
            return URI.create("file:/stub");
        }


        @Override
        public FileInfo getFileInfo()
        {
            return null;
        }


        @Override
        public String getType()
        {
            return "Stub";
        }
    }


    private static final class ILibraryMemberStub implements ILibraryMember
    {

        @Override
        public String getName()
        {
            return "stub";
        }


        @Override
        public String getLabel()
        {
            return null;
        }


        @Override
        public URI getUri()
        {
            return URI.create("file:/stub#stub");
        }


        @Override
        public IDataTableLibrary getLibrary()
        {
            return null;
        }
    }
}
