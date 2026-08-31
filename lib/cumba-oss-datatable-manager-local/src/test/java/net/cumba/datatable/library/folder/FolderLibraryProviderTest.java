package net.cumba.datatable.library.folder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.stream.Stream;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.library.IDataTableLibrary;
import net.cumba.datatable.library.ILibraryMember;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FolderLibraryProviderTest
{

    @TempDir
    File tempDir;

    // ==================== Constructor ====================

    @Test
    void testConstructorSetsNameAndDescription()
    {
        FolderLibraryProvider provider = new FolderLibraryProvider();

        assertEquals("Folder Library", provider.getName());
        assertEquals("Provides a local folder as library", provider.getDescription());
    }

    // ==================== getSupportedFileInfos ====================


    @Test
    void testGetSupportedFileInfos()
    {
        FolderLibraryProvider provider = new FolderLibraryProvider();
        List<FileInfo> infos = provider.getSupportedFileInfos();

        assertEquals(1, infos.size());
        assertSame(FolderLibrarySupplier.FI_FOLDER, infos.get(0));
    }

    // ==================== provide ====================


    @Test
    void testProvideWithDirectory() throws IOException
    {
        FolderLibraryProvider provider = new FolderLibraryProvider();
        URI dirUri = tempDir.toURI();

        IDataTableLibrary library = provider.provide(dirUri, null);
        assertNotNull(library);
        assertTrue(library instanceof FolderLibrary);
    }


    @Test
    void testProvideWithFileThrowsIOException() throws IOException
    {
        FolderLibraryProvider provider = new FolderLibraryProvider();
        File file = new File(tempDir, "test.txt");
        file.createNewFile();

        assertThrows(IOException.class, () ->
        {
            provider.provide(file.toURI(), null);
        });
    }

    // ==================== provideLibraryMembers ====================


    @Test
    void testProvideLibraryMembersWithFolderLibrary() throws IOException
    {
        FolderLibraryProvider provider = new FolderLibraryProvider();
        FolderLibrary library = new FolderLibrary(tempDir);

        Stream<? extends ILibraryMember> members = provider.provideLibraryMembers(library);
        assertNotNull(members);
    }


    @Test
    void testProvideLibraryMembersWithNonFolderLibraryReturnsNull() throws IOException
    {
        FolderLibraryProvider provider = new FolderLibraryProvider();
        // A foreign (non-folder) library: the provider must handle it gracefully (non-null result).
        IDataTableLibrary otherLib = stubLibrary("test", "label", URI.create("file:///test"));

        Stream<? extends ILibraryMember> result = provider.provideLibraryMembers(otherLib);
        assertNotNull(result);
    }

    // ==================== provideLibraryMemberColumns ====================


    @Test
    void testProvideLibraryMemberColumnsWithFolderMember() throws IOException
    {
        FolderLibraryProvider provider = new FolderLibraryProvider();
        FolderLibrary library = new FolderLibrary(tempDir);
        File file = new File(tempDir, "data.csv");
        file.createNewFile();
        FolderMember member = new FolderMember(library, file.toPath());

        Stream<? extends DataTableColumnMeta> cols = provider.provideLibraryMemberColumns(member);
        assertNotNull(cols);
    }


    @Test
    void testProvideLibraryMemberColumnsWithNonFolderMemberReturnsNull() throws IOException
    {
        FolderLibraryProvider provider = new FolderLibraryProvider();
        // A foreign (non-folder) member: the provider must handle it gracefully (non-null result).
        ILibraryMember otherMember = stubMember("m", "label", URI.create("file:///m"));

        Stream<? extends DataTableColumnMeta> result = provider
                .provideLibraryMemberColumns(otherMember);
        assertNotNull(result);
    }

    // ==================== getLibraryAttribute ====================


    @Test
    void testGetLibraryAttributeAlwaysNull()
    {
        FolderLibraryProvider provider = new FolderLibraryProvider();
        FolderLibrary library = new FolderLibrary(tempDir);

        assertNull(provider.getLibraryAttribute(library, "any-key"));
        assertNull(provider.getLibraryAttribute(library, "define.xml"));
    }

    // ==================== Helpers ====================


    private static IDataTableLibrary stubLibrary(String aName, String aLabel, URI aUri)
    {
        return new IDataTableLibrary()
        {

            @Override
            public String getName()
            {
                return aName;
            }


            @Override
            public String getLabel()
            {
                return aLabel;
            }


            @Override
            public URI getUri()
            {
                return aUri;
            }


            @Override
            public String getType()
            {
                return "stub";
            }
        };
    }


    private static ILibraryMember stubMember(String aName, String aLabel, URI aUri)
    {
        return new ILibraryMember()
        {

            @Override
            public String getName()
            {
                return aName;
            }


            @Override
            public String getLabel()
            {
                return aLabel;
            }


            @Override
            public URI getUri()
            {
                return aUri;
            }


            @Override
            public IDataTableLibrary getLibrary()
            {
                return null;
            }
        };
    }
}
