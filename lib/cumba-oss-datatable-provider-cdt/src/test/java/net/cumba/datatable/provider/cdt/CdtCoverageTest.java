package net.cumba.datatable.provider.cdt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.io.Property;
import net.cumba.datatable.library.IDataTableLibrary;
import net.cumba.datatable.library.ILibraryMember;
import net.cumba.datatable.library.ILibraryProvider;
import net.cumba.datatable.provider.IDataTableProvider;
import net.cumba.datatable.provider.cdt.library.CdtLibrary;
import net.cumba.datatable.provider.cdt.library.CdtLibraryProvider;
import net.cumba.datatable.provider.cdt.library.CdtLibrarySupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Coverage-oriented tests that exercise SPI suppliers, provider overloads, the writer's helper
 * entry points, and the parser's stream/path entry points. Kept narrowly focused so each test
 * covers a single uncovered branch.
 */
class CdtCoverageTest
{

    private static final String DM = """
            dataset DM label="Demographics"
            col USUBJID type=Char
            col AGE type=Num
            ---
            S001 | 42
            S002 | 37
            ---
            """;

    private static final String DM_AE = DM + "\n" + """
            dataset AE
            col USUBJID type=Char
            col AETERM type=Char
            ---
            S001 | Headache
            ---
            """;

    // ----- CdtProviderSupplier ----------------------------------------------------

    @Test
    void providerSupplierReturnsProviderForMatchingUri()
    {
        CdtProviderSupplier s = new CdtProviderSupplier();
        IDataTableProvider p = s.getProvider(URI.create("file:///tmp/x.cdt"),
                CdtProviderSupplier.FI_CDT);
        assertNotNull(p);
        assertTrue(p instanceof CdtTableProvider);
    }


    @Test
    void librarySupplierReturnsProviderForBareUri()
    {
        CdtLibrarySupplier s = new CdtLibrarySupplier();
        ILibraryProvider p = s.getProvider(URI.create("file:///tmp/x.cdt"),
                CdtProviderSupplier.FI_CDT);
        assertNotNull(p);
        assertTrue(p instanceof CdtLibraryProvider);
    }


    @Test
    void librarySupplierReturnsNullForFragmentUri()
    {
        CdtLibrarySupplier s = new CdtLibrarySupplier();
        assertNull(s.getProvider(URI.create("file:///tmp/x.cdt#DM"), CdtProviderSupplier.FI_CDT));
    }


    @Test
    void librarySupplierRejectsNullUri()
    {
        assertFalse(new CdtLibrarySupplier().canProvideFor(null, CdtProviderSupplier.FI_CDT));
    }

    // ----- CdtTableProvider -------------------------------------------------------


    @Test
    void tableProviderExposesSupportedFileInfosAndProperties(@TempDir Path tmp) throws IOException
    {
        CdtTableProvider p = new CdtTableProvider();
        assertEquals(CdtProviderSupplier.FIS, p.getSupportedFileInfos());
        // Both overloads return an empty list (placeholder).
        URI uri = write(tmp, "dm.cdt", DM).toUri();
        List<Property> props = p.getProviderProperties(uri, CdtProviderSupplier.FI_CDT);
        assertTrue(props.isEmpty());
        // Member overload simply returns the same empty list — pass a non-null member to
        // touch the line.
        ILibraryMember stub = new ILibraryMember()
        {

            @Override
            public IDataTableLibrary getLibrary()
            {
                return null;
            }


            @Override
            public String getName()
            {
                return "DM";
            }


            @Override
            public String getLabel()
            {
                return null;
            }


            @Override
            public URI getUri()
            {
                return uri;
            }
        };
        assertTrue(p.getProviderProperties(stub, CdtProviderSupplier.FI_CDT).isEmpty());
    }


    @Test
    void tableProviderProvideOverloadWithPropertiesMap(@TempDir Path tmp) throws IOException
    {
        Path file = write(tmp, "dm.cdt", DM);
        CdtTableProvider p = new CdtTableProvider();
        IDataTable t = p.provide(file.toUri(), CdtProviderSupplier.FI_CDT,
                Map.<Property, String> of());
        assertEquals("DM", t.getMetaData().getName());
    }


    @Test
    void tableProviderProvideMetaDataFirstDataset(@TempDir Path tmp) throws IOException
    {
        Path file = write(tmp, "dm.cdt", DM_AE);
        CdtTableProvider p = new CdtTableProvider();
        DataTableMeta meta = p.provideMetaData(file.toUri(), CdtProviderSupplier.FI_CDT);
        assertEquals("DM", meta.getName());
        assertEquals(2, meta.getColumnCount());
    }


    @Test
    void tableProviderProvideMetaDataNamedDataset(@TempDir Path tmp) throws IOException
    {
        Path file = write(tmp, "dm.cdt", DM_AE);
        URI frag = URI.create(file.toUri() + "#AE");
        CdtTableProvider p = new CdtTableProvider();
        DataTableMeta meta = p.provideMetaData(frag, CdtProviderSupplier.FI_CDT);
        assertEquals("AE", meta.getName());
    }


    @Test
    void tableProviderProvideMetaDataThrowsForUnknownFragment(@TempDir Path tmp) throws IOException
    {
        Path file = write(tmp, "dm.cdt", DM);
        URI frag = URI.create(file.toUri() + "#NOSUCH");
        CdtTableProvider p = new CdtTableProvider();
        assertThrows(IOException.class, () -> p.provideMetaData(frag, CdtProviderSupplier.FI_CDT));
    }

    // ----- CdtLibraryProvider -----------------------------------------------------


    @Test
    void libraryProviderExposesSupportedFileInfosAndProperties(@TempDir Path tmp) throws IOException
    {
        CdtLibraryProvider p = new CdtLibraryProvider();
        assertEquals(CdtLibrarySupplier.FIS, p.getSupportedFileInfos());

        URI uri = write(tmp, "lib.cdt", DM).toUri();
        List<Property> props = p.getProviderProperties(uri, CdtProviderSupplier.FI_CDT);
        // libraryNameProperty contributes one property entry.
        assertEquals(1, props.size());
    }


    @Test
    void libraryProviderProvideTwoArgOverload(@TempDir Path tmp) throws IOException
    {
        Path file = write(tmp, "lib.cdt", DM_AE);
        CdtLibraryProvider p = new CdtLibraryProvider();
        try (IDataTableLibrary lib = p.provide(file.toUri(), CdtProviderSupplier.FI_CDT))
        {
            ILibraryMember[] members = p.provideLibraryMembers(lib).toArray(ILibraryMember[]::new);
            assertEquals(2, members.length);
        }
    }


    @Test
    void libraryProviderColumnsForMember(@TempDir Path tmp) throws IOException
    {
        Path file = write(tmp, "lib.cdt", """
                dataset DM
                col USUBJID type=Char codelist=C66731
                col AGE type=Num
                ---
                S001 | 42
                ---
                """);
        CdtLibraryProvider p = new CdtLibraryProvider();
        try (IDataTableLibrary lib = p.provide(file.toUri(), CdtProviderSupplier.FI_CDT,
                Map.<Property, String> of()))
        {
            ILibraryMember m = p.provideLibraryMembers(lib).findFirst().orElseThrow();
            long cols = p.provideLibraryMemberColumns(m).count();
            assertEquals(2, cols);
        }
    }


    @Test
    void libraryProviderMembersForUnrelatedLibraryIsEmpty() throws IOException
    {
        CdtLibraryProvider p = new CdtLibraryProvider();
        IDataTableLibrary fake = new IDataTableLibrary()
        {

            @Override
            public String getName()
            {
                return "F";
            }


            @Override
            public String getLabel()
            {
                return null;
            }


            @Override
            public URI getUri()
            {
                return URI.create("file:///tmp/none.cdt");
            }


            @Override
            public String getType()
            {
                return "test";
            }


            @Override
            public FileInfo getFileInfo()
            {
                return CdtProviderSupplier.FI_CDT;
            }
        };
        assertEquals(0L, p.provideLibraryMembers(fake).count());
        // And provideLibraryMemberColumns for an unrelated member is empty too.
        ILibraryMember unrelated = new ILibraryMember()
        {

            @Override
            public IDataTableLibrary getLibrary()
            {
                return fake;
            }


            @Override
            public String getName()
            {
                return "M";
            }


            @Override
            public String getLabel()
            {
                return null;
            }


            @Override
            public URI getUri()
            {
                return URI.create("file:///tmp/none.cdt#M");
            }
        };
        assertEquals(0L, p.provideLibraryMemberColumns(unrelated).count());
    }


    @Test
    void libraryProviderHandlesUriWithoutPath()
    {
        // A bare opaque URI has no path — exercises the null-path branch in
        // getLibraryNameFor via getProviderProperties.
        URI opaque = URI.create("urn:abc");
        CdtLibraryProvider p = new CdtLibraryProvider();
        List<Property> props = p.getProviderProperties(opaque, CdtProviderSupplier.FI_CDT);
        assertEquals(1, props.size());
    }

    // ----- CdtLibrary -------------------------------------------------------------


    @Test
    void cdtLibraryGetTypeReturnsCdt()
    {
        try (CdtLibrary lib = new CdtLibrary("L", null, URI.create("file:///tmp/lib.cdt")))
        {
            assertEquals("Cdt", lib.getType());
        }
        catch (IOException ex)
        {
            fail(ex);
        }
    }

    // ----- CdtWriter --------------------------------------------------------------


    @Test
    void writerToStringRoundTrips(@TempDir Path tmp) throws IOException
    {
        Path file = write(tmp, "dm.cdt", DM);
        IDataTable t = new CdtTableProvider().provide(file.toUri(), CdtProviderSupplier.FI_CDT);
        String text = CdtWriter.toString(t);
        // Sanity: header + fences + at least one data row reference.
        assertTrue(text.contains("dataset DM"), text);
        assertTrue(text.contains("---\n"), text);
        assertTrue(text.contains("S001"), text);
    }


    @Test
    void writerWriteSingleArgPathOverload(@TempDir Path tmp) throws IOException
    {
        Path src = write(tmp, "dm.cdt", DM);
        IDataTable t = new CdtTableProvider().provide(src.toUri(), CdtProviderSupplier.FI_CDT);
        Path out = tmp.resolve("out.cdt");
        CdtWriter.write(t, out);
        String written = Files.readString(out, StandardCharsets.UTF_8);
        assertTrue(written.startsWith("dataset DM"));
        assertTrue(written.endsWith("---\n"));
    }


    /**
     * F-C9: CDT is a line-oriented text format. A CHAR cell containing an embedded CR or LF cannot
     * round-trip and would produce a corrupt file, so the writer must reject it explicitly.
     */
    @Test
    void writerRejectsCrInCharCell(@TempDir Path tmp) throws IOException
    {
        IDataTable t = singleCharCell("line1\rline2");
        Path out = tmp.resolve("crlf.cdt");
        IOException ex = assertThrows(IOException.class, () -> CdtWriter.write(t, out));
        assertTrue(ex.getMessage().contains("CDT export"), ex.getMessage());
        assertTrue(ex.getMessage().contains("multiline"), ex.getMessage());
    }


    @Test
    void writerRejectsLfInCharCell(@TempDir Path tmp) throws IOException
    {
        IDataTable t = singleCharCell("line1\nline2");
        Path out = tmp.resolve("crlf.cdt");
        IOException ex = assertThrows(IOException.class, () -> CdtWriter.write(t, out));
        assertTrue(ex.getMessage().contains("CDT export"), ex.getMessage());
    }


    /**
     * F-C9 header variant: CR or LF in a header attribute (column label, format, dataset label,
     * generic metadata value) is just as unsafe — the writer must reject it before producing the
     * corrupt file.
     */
    @Test
    void writerRejectsLfInColumnLabel(@TempDir Path tmp) throws IOException
    {
        net.cumba.datatable.DataTableColumnMeta cm = net.cumba.datatable.DataTableColumnMeta
                .builder().index(0).name("X").label("line1\nline2")
                .type(net.cumba.datatable.values.DataValueType.STRING).build();
        DataTableMeta md = DataTableMeta.builder().name("T").rowCount(1).totalRowCount(1)
                .setColumns(cm).build();
        net.cumba.datatable.impl.CachedDataTableColumn col = new net.cumba.datatable.impl.CachedDataTableColumn(
                0, net.cumba.datatable.values.DataValueType.STRING);
        col.addElement("v");
        col.complete();
        IDataTable t = new net.cumba.datatable.impl.ColumnCachedDataTable(md, col);

        Path out = tmp.resolve("hdr.cdt");
        IOException ex = assertThrows(IOException.class, () -> CdtWriter.write(t, out));
        assertTrue(ex.getMessage().contains("CDT export"), ex.getMessage());
    }


    private static IDataTable singleCharCell(String value)
    {
        net.cumba.datatable.DataTableColumnMeta cm = net.cumba.datatable.DataTableColumnMeta
                .builder().index(0).name("X").type(net.cumba.datatable.values.DataValueType.STRING)
                .build();
        DataTableMeta md = DataTableMeta.builder().name("T").rowCount(1).totalRowCount(1)
                .setColumns(cm).build();
        net.cumba.datatable.impl.CachedDataTableColumn col = new net.cumba.datatable.impl.CachedDataTableColumn(
                0, net.cumba.datatable.values.DataValueType.STRING);
        col.addElement(value);
        col.complete();
        return new net.cumba.datatable.impl.ColumnCachedDataTable(md, col);
    }


    @Test
    void parserParseAllFromPath(@TempDir Path tmp) throws IOException
    {
        Path file = write(tmp, "dm.cdt", DM);
        List<CdtDataset> all = CdtParser.parseAll(file);
        assertEquals(1, all.size());
        assertEquals("DM", all.get(0).getName());
    }


    @Test
    void parserParseAllFromInputStream() throws IOException
    {
        try (ByteArrayInputStream in = new ByteArrayInputStream(
                DM.getBytes(StandardCharsets.UTF_8)))
        {
            List<CdtDataset> all = CdtParser.parseAll(in, "stream");
            assertEquals(1, all.size());
            assertEquals("DM", all.get(0).getName());
        }
    }


    @Test
    void parserParseAllFromReader() throws IOException
    {
        try (StringReader r = new StringReader(DM))
        {
            List<CdtDataset> all = CdtParser.parseAll(r, "reader");
            assertEquals(1, all.size());
            assertEquals("DM", all.get(0).getName());
        }
    }


    @Test
    void parserParseNamedWithNullNameReturnsNull()
    {
        assertNull(CdtParser.parseNamed(DM, "t", null));
    }


    /**
     * {@code Reader.read(char[])} is permitted by its general contract to return {@code 0} without
     * having reached end-of-stream (only guaranteed to read at least one character when the buffer
     * is non-empty; a defensive caller still loops rather than treating 0 as EOF). A adversarial
     * {@link Reader} that actually does this, once, before yielding real content, distinguishes the
     * correct {@code >= 0} loop guard from a {@code > 0} one that would stop early and silently
     * truncate the read to nothing.
     */
    @Test
    void parserParseAllFromReaderToleratesZeroLengthRead() throws IOException
    {
        try (Reader zeroThenContent = new Reader()
        {

            private final Reader delegate = new StringReader(DM);

            private boolean first = true;

            @Override
            public int read(char[] cbuf, int off, int len)
            {
                if (first)
                {
                    first = false;
                    return 0;
                }
                try
                {
                    return delegate.read(cbuf, off, len);
                }
                catch (IOException ex)
                {
                    throw new java.io.UncheckedIOException(ex);
                }
            }


            @Override
            public void close()
            {
                // no-op
            }
        })
        {
            List<CdtDataset> all = CdtParser.parseAll(zeroThenContent, "reader");
            assertEquals(1, all.size());
            assertEquals("DM", all.get(0).getName());
        }
    }


    /** {@link CdtDataset#copyAttrs()} returns an independent, mutable copy of the attribute map. */
    @Test
    void datasetCopyAttrsReturnsIndependentMutableCopy()
    {
        CdtDataset ds = CdtParser.parseFirst("""
                dataset ADSL class=ADSL standard="SDTMIG 3.4"
                col USUBJID type=Char
                ---
                S1
                ---
                """, "t");
        Map<String, String> copy = ds.copyAttrs();
        assertEquals("ADSL", copy.get("class"));
        assertEquals("SDTMIG 3.4", copy.get("standard"));
        // must be independent of - and mutable, unlike - the builder's @Singular map
        copy.put("extra", "value");
        assertNull(ds.getAttrs().get("extra"));
    }


    /** {@link CdtType#token()} round-trips every enum constant to its file-format spelling. */
    @Test
    void cdtTypeTokenCoversEveryConstant()
    {
        assertEquals("Char", CdtType.CHAR.token());
        assertEquals("Num", CdtType.NUM.token());
        assertEquals("Date", CdtType.DATE.token());
        assertEquals("Time", CdtType.TIME.token());
        assertEquals("DateTime", CdtType.DATETIME.token());
    }


    /**
     * {@link net.cumba.datatable.provider.cdt.library.CdtLibrarySupplier} rejects a non-.cdt URI.
     */
    @Test
    void librarySupplierRejectsUnsupportedExtension()
    {
        FileInfo other = FileInfo.createFor("xpt", "Other", "00000000-0000-0000-0000-000000000098");
        assertFalse(new CdtLibrarySupplier().canProvideFor(URI.create("file:///tmp/x.xpt"), other));
    }

    // ----- helpers ----------------------------------------------------------------


    private static Path write(Path aDir, String aName, String aContent) throws IOException
    {
        Path p = aDir.resolve(aName);
        Files.writeString(p, aContent, StandardCharsets.UTF_8);
        return p;
    }


    @Test
    void providerSupplierReturnsNullForMismatchedExtension()
    {
        CdtProviderSupplier s = new CdtProviderSupplier();
        FileInfo other = FileInfo.createFor("xpt", "Other", "00000000-0000-0000-0000-000000000099");
        assertNull(s.getProvider(URI.create("file:///tmp/x.xpt"), other));
    }

    // ----- <init> setName/setDescription ------------------------------------------


    @Test
    void tableProviderConstructorSetsNameAndDescription()
    {
        CdtTableProvider p = new CdtTableProvider();
        assertEquals("CdtTableProvider", p.getName());
        assertTrue(p.getDescription().contains("CDT"), p.getDescription());
    }


    @Test
    void libraryProviderConstructorSetsNameAndDescription()
    {
        CdtLibraryProvider p = new CdtLibraryProvider();
        assertEquals("CdtLibraryProvider", p.getName());
        assertTrue(p.getDescription().contains("CDT"), p.getDescription());
    }

    // ----- getLibraryNameFor --------------------------------------------------------


    @Test
    void libraryNameIsFileNameWithoutExtensionUppercased(@TempDir Path tmp) throws IOException
    {
        URI uri = write(tmp, "dm.cdt", DM).toUri();
        CdtLibraryProvider p = new CdtLibraryProvider();
        List<Property> props = p.getProviderProperties(uri, CdtProviderSupplier.FI_CDT);
        assertEquals(1, props.size());
        assertEquals("DM", props.get(0).defaultValue());
    }


    @Test
    void libraryNameForNullUriFallsBackToGenericDefaultInsteadOfNpe()
    {
        CdtLibraryProvider p = new CdtLibraryProvider();
        List<Property> props = p.getProviderProperties(null, CdtProviderSupplier.FI_CDT);
        assertEquals(1, props.size());
        // ILibraryProvider.defaultLibraryName(null, ...) == "library" once getLibraryNameFor(null)
        // returns "" (blank) rather than throwing on aUri.getPath().
        assertEquals("library", props.get(0).defaultValue());
    }

    // ----- readContent: non-"file" scheme branch (both providers share the shape) ---


    @Test
    void libraryProviderReadsContentOverHttp() throws IOException, InterruptedException
    {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer
                .create(new java.net.InetSocketAddress("localhost", 0), 0);
        try
        {
            byte[] body = DM.getBytes(StandardCharsets.UTF_8);
            server.createContext("/dm.cdt", ex ->
            {
                ex.sendResponseHeaders(200, body.length);
                try (var os = ex.getResponseBody())
                {
                    os.write(body);
                }
            });
            server.start();
            URI uri = URI.create("http://localhost:" + server.getAddress().getPort() + "/dm.cdt");

            CdtLibraryProvider p = new CdtLibraryProvider();
            try (IDataTableLibrary lib = p.provide(uri, CdtProviderSupplier.FI_CDT,
                    Map.<Property, String> of()))
            {
                ILibraryMember[] members = p.provideLibraryMembers(lib)
                        .toArray(ILibraryMember[]::new);
                assertEquals(1, members.length);
                assertEquals("DM", members[0].getName());
            }
        }
        finally
        {
            server.stop(0);
        }
    }


    @Test
    void tableProviderReadsContentOverHttp() throws IOException
    {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer
                .create(new java.net.InetSocketAddress("localhost", 0), 0);
        try
        {
            byte[] body = DM.getBytes(StandardCharsets.UTF_8);
            server.createContext("/dm.cdt", ex ->
            {
                ex.sendResponseHeaders(200, body.length);
                try (var os = ex.getResponseBody())
                {
                    os.write(body);
                }
            });
            server.start();
            URI uri = URI.create("http://localhost:" + server.getAddress().getPort() + "/dm.cdt");

            IDataTable t = new CdtTableProvider().provide(uri, CdtProviderSupplier.FI_CDT);
            assertEquals("DM", t.getMetaData().getName());
            assertEquals(2L, t.getRowCount());
        }
        finally
        {
            server.stop(0);
        }
    }

    // ----- CdtLibraryProvider.readContent fragment-stripping branch -----------------


    @Test
    void libraryProviderReadContentStripsFragmentBeforeReading(@TempDir Path tmp) throws IOException
    {
        Path file = write(tmp, "dm_ae.cdt", DM + "\n" + """
                dataset AE
                col USUBJID type=Char
                col AETERM type=Char
                ---
                S001 | Headache
                ---
                """);
        // The library provider only ever receives bare URIs in production (CdtLibrarySupplier
        // rejects fragments), but provide() itself does not enforce that - call it directly with
        // a fragment to exercise the defensive stripping branch in readContent.
        URI withFragment = URI.create(file.toUri() + "#AE");
        CdtLibraryProvider p = new CdtLibraryProvider();
        try (IDataTableLibrary lib = p.provide(withFragment, CdtProviderSupplier.FI_CDT,
                Map.<Property, String> of()))
        {
            ILibraryMember[] members = p.provideLibraryMembers(lib).toArray(ILibraryMember[]::new);
            // Fragment is ignored for content reading; both datasets in the file are still
            // returned.
            assertEquals(2, members.length);
        }
    }

    // ----- CdtTableProvider.provide()/provideMetaData(): tableUri fragment branch ---


    @Test
    void provideSetsTableUriFragmentToResolvedDatasetNameWhenNoneRequested(@TempDir Path tmp)
        throws IOException
    {
        Path file = write(tmp, "dm.cdt", DM);
        CdtTableProvider p = new CdtTableProvider();
        IDataTable t = p.provide(file.toUri(), CdtProviderSupplier.FI_CDT);
        assertEquals("DM", t.getMetaData().getTableURI().getFragment());
    }


    @Test
    void provideKeepsOriginalUriWhenFragmentWasRequested(@TempDir Path tmp) throws IOException
    {
        Path file = write(tmp, "dm_ae.cdt", DM + "\n" + """
                dataset AE
                col USUBJID type=Char
                col AETERM type=Char
                ---
                S001 | Headache
                ---
                """);
        URI frag = URI.create(file.toUri() + "#AE");
        CdtTableProvider p = new CdtTableProvider();
        IDataTable t = p.provide(frag, CdtProviderSupplier.FI_CDT);
        assertEquals(frag, t.getMetaData().getTableURI());
    }


    @Test
    void provideMetaDataSetsTableUriFragmentToResolvedDatasetNameWhenNoneRequested(
            @TempDir Path tmp)
        throws IOException
    {
        Path file = write(tmp, "dm.cdt", DM);
        CdtTableProvider p = new CdtTableProvider();
        DataTableMeta meta = p.provideMetaData(file.toUri(), CdtProviderSupplier.FI_CDT);
        assertEquals("DM", meta.getTableURI().getFragment());
    }


    @Test
    void provideMetaDataKeepsOriginalUriWhenFragmentWasRequested(@TempDir Path tmp)
        throws IOException
    {
        Path file = write(tmp, "dm_ae.cdt", DM + "\n" + """
                dataset AE
                col USUBJID type=Char
                col AETERM type=Char
                ---
                S001 | Headache
                ---
                """);
        URI frag = URI.create(file.toUri() + "#AE");
        CdtTableProvider p = new CdtTableProvider();
        DataTableMeta meta = p.provideMetaData(frag, CdtProviderSupplier.FI_CDT);
        assertEquals(frag, meta.getTableURI());
    }

    // ----- CdtLibraryProvider.mapColumns: the loop must actually run -----------------


    @Test
    void mapColumnsProducesRealColumnMetadataNotJustAnArrayOfTheRightSize(@TempDir Path tmp)
        throws IOException
    {
        Path file = write(tmp, "lib.cdt", """
                dataset DM
                col USUBJID type=Char codelist=C66731
                col AGE type=Num label="Age in years"
                ---
                S001 | 42
                ---
                """);
        CdtLibraryProvider p = new CdtLibraryProvider();
        try (IDataTableLibrary lib = p.provide(file.toUri(), CdtProviderSupplier.FI_CDT,
                Map.<Property, String> of()))
        {
            ILibraryMember m = p.provideLibraryMembers(lib).findFirst().orElseThrow();
            var cols = p.provideLibraryMemberColumns(m).toList();
            assertEquals(2, cols.size());
            assertEquals("USUBJID", cols.get(0).getName());
            assertEquals("USUBJID", cols.get(0).getLabel(),
                    "label falls back to the name when unset");
            assertEquals("C66731", cols.get(0).getMetaData(CdtTableBuilder.COLUMN_META_CODELIST));
            assertEquals("AGE", cols.get(1).getName());
            assertEquals("Age in years", cols.get(1).getLabel());
            assertNull(cols.get(1).getMetaData(CdtTableBuilder.COLUMN_META_CODELIST));
        }
    }

}
