package net.cumba.datatable.impl.library.dblib;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.impl.library.dblib.beans.DataBrowserColumnMetaBean;
import net.cumba.datatable.impl.library.dblib.beans.DataBrowserLibraryBean;
import net.cumba.datatable.impl.library.dblib.beans.DataBrowserSourceBean;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.library.IDataTableLibrary;
import net.cumba.datatable.library.ILibraryMember;
import net.cumba.datatable.metadata.IColumnMetadata;
import net.cumba.datatable.metadata.IDataTableMetadata;
import net.cumba.datatable.metadata.IMetadataLibrary;
import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataBrowserLibraryProviderTest
{

    private final DataBrowserLibraryProvider provider = new DataBrowserLibraryProvider();

    // ==================== getSupportedFileInfos ====================

    @Test
    void testGetSupportedFileInfos()
    {
        List<FileInfo> infos = provider.getSupportedFileInfos();
        assertEquals(1, infos.size());
        assertSame(DataBrowserLibrarySupplier.FI_DBLIB, infos.get(0));
    }

    // ==================== provide ====================


    @Test
    void testProvideFromJsonFile(@TempDir Path tempDir) throws IOException
    {
        String json = """
                {
                  "name": "TestLib",
                  "label": "Test Library"
                }
                """;
        Path dblibFile = tempDir.resolve("test.dblib");
        Files.writeString(dblibFile, json);

        IDataTableLibrary lib = provider.provide(dblibFile.toUri(), null);

        assertNotNull(lib);
        assertInstanceOf(DataBrowserLibrary.class, lib);
        assertEquals("TestLib", lib.getName());
        assertEquals("Test Library", lib.getLabel());
    }


    @Test
    void testProvideWithSources(@TempDir Path tempDir) throws IOException
    {
        // Create a source file
        Path dataFile = tempDir.resolve("dm.csv");
        Files.writeString(dataFile, "test");

        String json = String.format("""
                {
                  "name": "TestLib",
                  "sources": [
                    { "uri": "%s", "name": "DM", "label": "Demographics" }
                  ]
                }
                """, dataFile.toUri().toString());
        Path dblibFile = tempDir.resolve("test.dblib");
        Files.writeString(dblibFile, json);

        DataBrowserLibrary lib = (DataBrowserLibrary) provider.provide(dblibFile.toUri(), null);
        List<DataBrowserMember> members = lib.getMembers().toList();

        assertEquals(1, members.size());
        assertEquals("DM", members.get(0).getName());
    }

    // ==================== provideLibraryMembers ====================


    @Test
    void testProvideLibraryMembersWithDataBrowserLibrary(@TempDir Path tempDir) throws IOException
    {
        Path file = tempDir.resolve("ae.csv");
        Files.writeString(file, "test");

        DataBrowserSourceBean source = DataBrowserSourceBean.builder().uri(file.toUri().toString())
                .name("AE").build();
        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("TestLib")
                .sources(new DataBrowserSourceBean[]
                {
                        source
                }).build();

        DataBrowserLibrary lib = new DataBrowserLibrary(URI.create("file:///lib/test.dblib"), bean);

        Stream<? extends ILibraryMember> members = provider.provideLibraryMembers(lib);

        assertEquals(1, members.count());
    }


    @Test
    void testProvideLibraryMembersWithNonDataBrowserLibrary() throws IOException
    {
        // Mock a non-DataBrowserLibrary
        IDataTableLibrary otherLib = new IDataTableLibrary()
        {

            @Override
            public String getName()
            {
                return "Other";
            }


            @Override
            public String getLabel()
            {
                return null;
            }


            @Override
            public URI getUri()
            {
                return URI.create("file:///other");
            }


            @Override
            public String getType()
            {
                return "other";
            }

        };

        Stream<? extends ILibraryMember> members = provider.provideLibraryMembers(otherLib);
        assertEquals(0, members.count());
    }

    // ==================== provideLibraryMemberColumns ====================


    @Test
    void testProvideLibraryMemberColumnsWithInternalMeta() throws IOException
    {
        URI memberUri = URI.create("file:///data/dm.csv");
        URI libraryUri = URI.create("file:///lib/test.dblib");

        DataBrowserColumnMetaBean col1 = DataBrowserColumnMetaBean.builder()
                .uri(memberUri.toString()).name("SUBJID").label("Subject ID").type("STRING")
                .format("$20.").build();
        DataBrowserColumnMetaBean col2 = DataBrowserColumnMetaBean.builder()
                .uri(memberUri.toString()).name("AGE").label("Age").type("DOUBLE").build();
        // Column for a different member (should not match)
        DataBrowserColumnMetaBean col3 = DataBrowserColumnMetaBean.builder()
                .uri("file:///data/ae.csv").name("AEDECOD").label("Decoded Term").build();

        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("TestLib")
                .columnMeta(new DataBrowserColumnMetaBean[]
                {
                        col1, col2, col3
                }).build();

        DataBrowserLibrary lib = new DataBrowserLibrary(libraryUri, bean);
        DataBrowserMember member = new DataBrowserMember(lib, memberUri, "DM", null);

        List<? extends DataTableColumnMeta> columns = provider.provideLibraryMemberColumns(member)
                .toList();

        assertEquals(2, columns.size());
        assertEquals("SUBJID", columns.get(0).getName());
        assertEquals("Subject ID", columns.get(0).getLabel());
        assertEquals("$20.", columns.get(0).getDisplayFormat());
        assertEquals(0, columns.get(0).getIndex());
        assertEquals("AGE", columns.get(1).getName());
        assertEquals("Age", columns.get(1).getLabel());
        assertEquals(1, columns.get(1).getIndex());
    }


    /** A null element in the internal columnMeta array must be skipped, not dereferenced. */
    @Test
    void testProvideLibraryMemberColumnsSkipsNullColumnMeta() throws IOException
    {
        URI memberUri = URI.create("file:///data/dm.csv");
        URI libraryUri = URI.create("file:///lib/test.dblib");

        DataBrowserColumnMetaBean col = DataBrowserColumnMetaBean.builder()
                .uri(memberUri.toString()).name("AGE").label("Age").type("DOUBLE").build();
        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("TestLib")
                .columnMeta(new DataBrowserColumnMetaBean[]
                {
                        null, col
                }).build();

        DataBrowserLibrary lib = new DataBrowserLibrary(libraryUri, bean);
        DataBrowserMember member = new DataBrowserMember(lib, memberUri, "DM", null);

        List<? extends DataTableColumnMeta> columns = assertDoesNotThrow(
                () -> provider.provideLibraryMemberColumns(member).toList());

        assertEquals(1, columns.size());
        assertEquals("AGE", columns.get(0).getName());
    }


    @Test
    void testProvideLibraryMemberColumnsWithRelativeUri() throws IOException
    {
        URI libraryUri = URI.create("file:///lib/test.dblib");
        URI memberUri = URI.create("file:///lib/dm.csv");

        DataBrowserColumnMetaBean col = DataBrowserColumnMetaBean.builder().uri("dm.csv") // relative
                                                                                          // to
                                                                                          // library
                                                                                          // file
                .name("AGE").label("Age").type("DOUBLE").build();

        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("TestLib")
                .columnMeta(new DataBrowserColumnMetaBean[]
                {
                        col
                }).build();

        DataBrowserLibrary lib = new DataBrowserLibrary(libraryUri, bean);
        DataBrowserMember member = new DataBrowserMember(lib, memberUri, "DM", null);

        List<? extends DataTableColumnMeta> columns = provider.provideLibraryMemberColumns(member)
                .toList();

        assertEquals(1, columns.size());
        assertEquals("AGE", columns.get(0).getName());
    }


    @Test
    void testProvideLibraryMemberColumnsWithCustomAttributes() throws IOException
    {
        URI memberUri = URI.create("file:///data/dm.csv");
        URI libraryUri = URI.create("file:///lib/test.dblib");

        DataBrowserColumnMetaBean col = DataBrowserColumnMetaBean.builder()
                .uri(memberUri.toString()).name("SUBJID").label("Subject ID")
                .attributes(Map.of("origin", "CRF", "role", "Identifier")).build();

        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("TestLib")
                .columnMeta(new DataBrowserColumnMetaBean[]
                {
                        col
                }).build();

        DataBrowserLibrary lib = new DataBrowserLibrary(libraryUri, bean);
        DataBrowserMember member = new DataBrowserMember(lib, memberUri, "DM", null);

        List<? extends DataTableColumnMeta> columns = provider.provideLibraryMemberColumns(member)
                .toList();

        assertEquals(1, columns.size());
        assertEquals("CRF", columns.get(0).getMetaData("origin"));
        assertEquals("Identifier", columns.get(0).getMetaData("role"));
    }


    @Test
    void testProvideLibraryMemberColumnsNoMeta() throws IOException
    {
        URI memberUri = URI.create("file:///data/dm.csv");
        URI libraryUri = URI.create("file:///lib/test.dblib");

        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("TestLib").build();

        DataBrowserLibrary lib = new DataBrowserLibrary(libraryUri, bean);
        DataBrowserMember member = new DataBrowserMember(lib, memberUri, "DM", null);

        Stream<? extends DataTableColumnMeta> columns = provider
                .provideLibraryMemberColumns(member);

        assertEquals(0, columns.count());
    }


    @Test
    void testProvideLibraryMemberColumnsNonDataBrowserMember() throws IOException
    {
        ILibraryMember otherMember = new ILibraryMember()
        {

            @Override
            public String getName()
            {
                return "Other";
            }


            @Override
            public String getLabel()
            {
                return null;
            }


            @Override
            public URI getUri()
            {
                return URI.create("file:///other");
            }


            @Override
            public IDataTableLibrary getLibrary()
            {
                return null;
            }
        };

        Stream<? extends DataTableColumnMeta> columns = provider
                .provideLibraryMemberColumns(otherMember);

        assertEquals(0, columns.count());
    }


    @Test
    void testProvideLibraryMemberColumnsFallsBackToMetadata() throws IOException
    {
        URI memberUri = URI.create("file:///data/dm.csv");
        URI libraryUri = URI.create("file:///lib/test.dblib");

        // Bean with no column metadata
        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("TestLib").build();

        DataBrowserLibrary lib = new DataBrowserLibrary(libraryUri, bean);

        // Set up mock metadata library
        IColumnMetadata col1 = mock(IColumnMetadata.class);
        when(col1.getName()).thenReturn("STUDYID");
        when(col1.getLabel()).thenReturn("Study Identifier");
        when(col1.getType()).thenReturn(DataValueType.STRING);
        when(col1.getDisplayFormat()).thenReturn(null);
        when(col1.getNativeType()).thenReturn("text");
        when(col1.getIndex()).thenReturn(0);
        when(col1.getLength()).thenReturn(20);

        IColumnMetadata col2 = mock(IColumnMetadata.class);
        when(col2.getName()).thenReturn("AGE");
        when(col2.getLabel()).thenReturn("Age");
        when(col2.getType()).thenReturn(DataValueType.DOUBLE);
        when(col2.getDisplayFormat()).thenReturn(null);
        when(col2.getNativeType()).thenReturn("float");
        when(col2.getIndex()).thenReturn(1);
        when(col2.getLength()).thenReturn(8);

        IDataTableMetadata tableMeta = mock(IDataTableMetadata.class);
        when(tableMeta.getColumns()).thenReturn(List.of(col1, col2));

        IMetadataLibrary metadata = mock(IMetadataLibrary.class);
        when(metadata.getDataTable("DM")).thenReturn(Optional.of(tableMeta));

        lib.setMetadata(metadata);

        DataBrowserMember member = new DataBrowserMember(lib, memberUri, "DM", null);

        List<? extends DataTableColumnMeta> columns = provider.provideLibraryMemberColumns(member)
                .toList();

        assertEquals(2, columns.size());
        assertEquals("STUDYID", columns.get(0).getName());
        assertEquals("Study Identifier", columns.get(0).getLabel());
        assertEquals(DataValueType.STRING, columns.get(0).getType());
        assertEquals(20, columns.get(0).getLength());
        assertEquals("AGE", columns.get(1).getName());
        assertEquals(DataValueType.DOUBLE, columns.get(1).getType());
    }


    @Test
    void testProvideLibraryMemberColumnsNoMetaNoBean() throws IOException
    {
        URI memberUri = URI.create("file:///data/dm.csv");
        URI libraryUri = URI.create("file:///lib/test.dblib");

        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("TestLib").build();

        DataBrowserLibrary lib = new DataBrowserLibrary(libraryUri, bean);

        // Metadata present but no matching table
        IMetadataLibrary metadata = mock(IMetadataLibrary.class);
        when(metadata.getDataTable("DM")).thenReturn(Optional.empty());
        lib.setMetadata(metadata);

        DataBrowserMember member = new DataBrowserMember(lib, memberUri, "DM", null);

        Stream<? extends DataTableColumnMeta> columns = provider
                .provideLibraryMemberColumns(member);

        assertEquals(0, columns.count());
    }

    // ==================== getLibraryAttribute ====================


    @Test
    void testGetLibraryAttributeReturnsNullForNoAttributes()
    {
        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("TestLib").build();
        DataBrowserLibrary lib = new DataBrowserLibrary(URI.create("file:///lib/test.dblib"), bean);

        assertNull(provider.getLibraryAttribute(lib, "anything"));
    }


    @Test
    void testGetLibraryAttributeReturnsValue()
    {
        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("TestLib")
                .attributes(Map.of("study", "ABC-123", "sponsor", "Acme")).build();
        DataBrowserLibrary lib = new DataBrowserLibrary(URI.create("file:///lib/test.dblib"), bean);

        assertEquals("ABC-123", provider.getLibraryAttribute(lib, "study"));
        assertEquals("Acme", provider.getLibraryAttribute(lib, "sponsor"));
    }


    @Test
    void testGetLibraryAttributeReturnsNullForMissingKey()
    {
        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("TestLib")
                .attributes(Map.of("study", "ABC-123")).build();
        DataBrowserLibrary lib = new DataBrowserLibrary(URI.create("file:///lib/test.dblib"), bean);

        assertNull(provider.getLibraryAttribute(lib, "nonexistent"));
    }


    @Test
    void testGetLibraryAttributeReturnsNullForNonDataBrowserLibrary()
    {
        IDataTableLibrary otherLib = new IDataTableLibrary()
        {

            @Override
            public String getName()
            {
                return "Other";
            }


            @Override
            public String getLabel()
            {
                return null;
            }


            @Override
            public URI getUri()
            {
                return URI.create("file:///other");
            }


            @Override
            public String getType()
            {
                return "other";
            }

        };

        assertNull(provider.getLibraryAttribute(otherLib, "anything"));
    }
}
