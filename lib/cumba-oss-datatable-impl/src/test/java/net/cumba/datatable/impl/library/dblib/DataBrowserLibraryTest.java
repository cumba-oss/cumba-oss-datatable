package net.cumba.datatable.impl.library.dblib;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import net.cumba.datatable.impl.library.dblib.beans.DataBrowserLibraryBean;
import net.cumba.datatable.impl.library.dblib.beans.DataBrowserSourceBean;
import net.cumba.datatable.library.ILibraryMember;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@SuppressWarnings("resource")
class DataBrowserLibraryTest
{

    private static final URI LIB_URI = URI.create("file:///lib/mylib.dblib");

    private DataBrowserLibraryBean createMinimalBean()
    {
        return DataBrowserLibraryBean.builder().name("TestLib").label("Test Library").build();
    }

    // ==================== Constructor ====================


    @Test
    void testConstructor()
    {
        DataBrowserLibraryBean bean = createMinimalBean();
        DataBrowserLibrary lib = new DataBrowserLibrary(LIB_URI, bean);

        assertEquals("TestLib", lib.getName());
        assertEquals("Test Library", lib.getLabel());
        assertEquals(LIB_URI, lib.getUri());
        assertSame(bean, lib.getBean());
    }


    @Test
    void testConstructorNullUriThrows()
    {
        assertThrows(NullPointerException.class, () ->
        {
            new DataBrowserLibrary(null, createMinimalBean());
        });
    }


    @Test
    void testConstructorNullBeanThrows()
    {
        assertThrows(NullPointerException.class, () ->
        {
            new DataBrowserLibrary(LIB_URI, null);
        });
    }


    @Test
    void testFileInfo()
    {
        DataBrowserLibrary lib = new DataBrowserLibrary(LIB_URI, createMinimalBean());
        assertSame(DataBrowserLibrarySupplier.FI_DBLIB, lib.getFileInfo());
    }

    // ==================== getType ====================


    @Test
    void testGetType()
    {
        DataBrowserLibrary lib = new DataBrowserLibrary(LIB_URI, createMinimalBean());
        assertEquals("databrowser", lib.getType());
    }

    // ==================== getMembers empty ====================


    @Test
    void testGetMembersEmptyBean() throws IOException
    {
        DataBrowserLibrary lib = new DataBrowserLibrary(LIB_URI, createMinimalBean());
        Stream<DataBrowserMember> members = lib.getMembers();

        assertEquals(0, members.count());
    }


    @Test
    void testGetMembersEmptySourcesArray() throws IOException
    {
        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("TestLib")
                .sources(new DataBrowserSourceBean[0]).build();

        DataBrowserLibrary lib = new DataBrowserLibrary(LIB_URI, bean);
        assertEquals(0, lib.getMembers().count());
    }

    // ==================== getMembers with file source ====================


    @Test
    void testGetMembersWithFileSource(@TempDir Path tempDir) throws IOException
    {
        Path file = tempDir.resolve("dm.csv");
        Files.writeString(file, "test");

        DataBrowserSourceBean source = DataBrowserSourceBean.builder().uri(file.toUri().toString())
                .name("DM").label("Demographics").build();
        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("TestLib")
                .sources(new DataBrowserSourceBean[]
                {
                        source
                }).build();

        DataBrowserLibrary lib = new DataBrowserLibrary(LIB_URI, bean);
        List<DataBrowserMember> members = lib.getMembers().toList();

        assertEquals(1, members.size());
        assertEquals("DM", members.get(0).getName());
        assertEquals("Demographics", members.get(0).getLabel());
        assertEquals(file.toUri(), members.get(0).getUri());
    }


    @Test
    void testGetMembersFileSourceDefaultName(@TempDir Path tempDir) throws IOException
    {
        Path file = tempDir.resolve("demographics.sas7bdat");
        Files.writeString(file, "test");

        DataBrowserSourceBean source = DataBrowserSourceBean.builder().uri(file.toUri().toString())
                .build();
        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("TestLib")
                .sources(new DataBrowserSourceBean[]
                {
                        source
                }).build();

        DataBrowserLibrary lib = new DataBrowserLibrary(LIB_URI, bean);
        List<DataBrowserMember> members = lib.getMembers().toList();

        assertEquals(1, members.size());
        assertEquals("DEMOGRAPHICS", members.get(0).getName());
        assertNull(members.get(0).getLabel());
    }

    // ==================== getMembers with directory source ====================


    @Test
    void testGetMembersWithDirectorySource(@TempDir Path tempDir) throws IOException
    {
        Files.writeString(tempDir.resolve("dm.csv"), "test");
        Files.writeString(tempDir.resolve("ae.csv"), "test");
        Files.createDirectory(tempDir.resolve("subdir"));

        DataBrowserSourceBean source = DataBrowserSourceBean.builder()
                .uri(tempDir.toUri().toString()).name("IGNORED").label("Also Ignored").build();
        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("TestLib")
                .sources(new DataBrowserSourceBean[]
                {
                        source
                }).build();

        DataBrowserLibrary lib = new DataBrowserLibrary(LIB_URI, bean);
        List<DataBrowserMember> members = lib.getMembers().toList();

        // Should have 2 file members (subdir excluded), sorted by name
        assertEquals(2, members.size());
        assertEquals("AE", members.get(0).getName());
        assertNull(members.get(0).getLabel());
        assertEquals("DM", members.get(1).getName());
        assertNull(members.get(1).getLabel());
    }

    // ==================== getMembers with non-existing path ====================


    @Test
    void testGetMembersNonExistingPathSilentlyIgnored() throws IOException
    {
        DataBrowserSourceBean source = DataBrowserSourceBean.builder()
                .uri("file:///nonexistent/path/file.csv").name("GHOST").build();
        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("TestLib")
                .sources(new DataBrowserSourceBean[]
                {
                        source
                }).build();

        DataBrowserLibrary lib = new DataBrowserLibrary(LIB_URI, bean);
        List<DataBrowserMember> members = lib.getMembers().toList();

        assertEquals(0, members.size());
    }

    // ==================== getMembers with non-file URI ====================


    @Test
    void testGetMembersWithNonFileUri() throws IOException
    {
        DataBrowserSourceBean source = DataBrowserSourceBean.builder()
                .uri("https://example.com/data/ae.csv").name("AE").label("Adverse Events").build();
        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("TestLib")
                .sources(new DataBrowserSourceBean[]
                {
                        source
                }).build();

        DataBrowserLibrary lib = new DataBrowserLibrary(LIB_URI, bean);
        List<DataBrowserMember> members = lib.getMembers().toList();

        assertEquals(1, members.size());
        assertEquals("AE", members.get(0).getName());
        assertEquals("Adverse Events", members.get(0).getLabel());
        assertEquals(URI.create("https://example.com/data/ae.csv"), members.get(0).getUri());
    }


    @Test
    void testGetMembersNonFileUriDefaultName() throws IOException
    {
        String uri = "https://example.com/data/ae.csv";
        DataBrowserSourceBean source = DataBrowserSourceBean.builder().uri(uri).build();
        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("TestLib")
                .sources(new DataBrowserSourceBean[]
                {
                        source
                }).build();

        DataBrowserLibrary lib = new DataBrowserLibrary(LIB_URI, bean);
        List<DataBrowserMember> members = lib.getMembers().toList();

        assertEquals(1, members.size());
        assertEquals(uri, members.get(0).getName());
    }

    // ==================== getMembers mixed sources ====================


    @Test
    void testGetMembersMixedSources(@TempDir Path tempDir) throws IOException
    {
        Path file = tempDir.resolve("vs.csv");
        Files.writeString(file, "test");

        DataBrowserSourceBean fileSrc = DataBrowserSourceBean.builder().uri(file.toUri().toString())
                .name("VS").build();
        DataBrowserSourceBean nonExist = DataBrowserSourceBean.builder()
                .uri("file:///nonexistent/file.csv").build();
        DataBrowserSourceBean httpSrc = DataBrowserSourceBean.builder()
                .uri("https://example.com/dm.csv").name("DM").build();

        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("TestLib")
                .sources(new DataBrowserSourceBean[]
                {
                        fileSrc, nonExist, httpSrc
                }).build();

        DataBrowserLibrary lib = new DataBrowserLibrary(LIB_URI, bean);
        List<DataBrowserMember> members = lib.getMembers().toList();

        // Non-existing silently skipped, remaining 2 sorted by name
        assertEquals(2, members.size());
        assertEquals("DM", members.get(0).getName());
        assertEquals("VS", members.get(1).getName());
    }

    // ==================== Dynamic re-evaluation ====================


    @Test
    void testGetMembersDynamicEvaluation(@TempDir Path tempDir) throws IOException
    {
        DataBrowserSourceBean source = DataBrowserSourceBean.builder()
                .uri(tempDir.toUri().toString()).build();
        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("TestLib")
                .sources(new DataBrowserSourceBean[]
                {
                        source
                }).build();

        DataBrowserLibrary lib = new DataBrowserLibrary(LIB_URI, bean);

        // Initially empty directory
        assertEquals(0, lib.getMembers().count());

        // Add a file
        Files.writeString(tempDir.resolve("dm.csv"), "test");
        assertEquals(1, lib.getMembers().count());

        // Add another file
        Files.writeString(tempDir.resolve("ae.csv"), "test");
        assertEquals(2, lib.getMembers().count());

        // Remove a file
        Files.delete(tempDir.resolve("dm.csv"));
        List<DataBrowserMember> members = lib.getMembers().toList();
        assertEquals(1, members.size());
        assertEquals("AE", members.get(0).getName());
    }

    // ==================== getMembers with invalid URI ====================


    @Test
    void testGetMembersInvalidUriSilentlyIgnored() throws IOException
    {
        DataBrowserSourceBean source = DataBrowserSourceBean.builder().uri(":::invalid uri:::")
                .build();
        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("TestLib")
                .sources(new DataBrowserSourceBean[]
                {
                        source
                }).build();

        DataBrowserLibrary lib = new DataBrowserLibrary(LIB_URI, bean);
        assertEquals(0, lib.getMembers().count());
    }

    // ==================== Members are DataBrowserMember instances ====================


    @Test
    void testMembersAreDataBrowserMemberInstances(@TempDir Path tempDir) throws IOException
    {
        Files.writeString(tempDir.resolve("dm.csv"), "test");

        DataBrowserSourceBean source = DataBrowserSourceBean.builder()
                .uri(tempDir.toUri().toString()).build();
        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("TestLib")
                .sources(new DataBrowserSourceBean[]
                {
                        source
                }).build();

        DataBrowserLibrary lib = new DataBrowserLibrary(LIB_URI, bean);
        ILibraryMember member = lib.getMembers().findFirst().orElseThrow();

        assertInstanceOf(DataBrowserMember.class, member);
    }


    /** A null element in the sources array (e.g. a hand-edited .dblib) must be skipped, not NPE. */
    @Test
    void testNullSourceElementIsSkipped(@TempDir Path tempDir) throws IOException
    {
        Path file = tempDir.resolve("dm.csv");
        Files.writeString(file, "test");

        DataBrowserSourceBean source = DataBrowserSourceBean.builder().uri(file.toUri().toString())
                .name("DM").build();
        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("TestLib")
                .sources(new DataBrowserSourceBean[]
                {
                        null, source
                }).build();

        DataBrowserLibrary lib = new DataBrowserLibrary(LIB_URI, bean);
        List<DataBrowserMember> members = assertDoesNotThrow(() -> lib.getMembers().toList());

        assertEquals(1, members.size());
        assertEquals("DM", members.get(0).getName());
    }
}
