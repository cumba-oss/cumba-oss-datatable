package net.cumba.datatable.impl.io;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import net.cumba.datatable.io.FileInfo;
import org.junit.jupiter.api.Test;

class AbstractGenericProviderTest
{

    private static class TestProvider extends AbstractGenericProvider<String>
    {

        @Override
        public List<FileInfo> getSupportedFileInfos()
        {
            return List.of(FileInfo.createFor("txt", "Text"));
        }


        @Override
        public String provide(URI aUri, FileInfo aFileInfo)
        {
            return "test";
        }


        private void setTestName(String name)
        {
            setName(name);
        }


        private void setTestDescription(String desc)
        {
            setDescription(desc);
        }
    }

    @Test
    void testInitialNameNull()
    {
        TestProvider provider = new TestProvider();

        assertNull(provider.getName());
    }


    @Test
    void testInitialDescriptionNull()
    {
        TestProvider provider = new TestProvider();

        assertNull(provider.getDescription());
    }


    @Test
    void testSetName()
    {
        TestProvider provider = new TestProvider();
        provider.setTestName("MyProvider");

        assertEquals("MyProvider", provider.getName());
    }


    @Test
    void testSetDescription()
    {
        TestProvider provider = new TestProvider();
        provider.setTestDescription("A test provider");

        assertEquals("A test provider", provider.getDescription());
    }


    @Test
    void testGetSupportedFileInfos()
    {
        TestProvider provider = new TestProvider();
        List<FileInfo> infos = provider.getSupportedFileInfos();

        assertEquals(1, infos.size());
        assertEquals("txt", infos.get(0).getFileExtension());
    }


    @Test
    void testProvide()
    {
        TestProvider provider = new TestProvider();
        URI uri = URI.create("file:///test.txt");

        assertEquals("test", provider.provide(uri, null));
    }


    @Test
    void testProvideDefaultMethod() throws IOException
    {
        TestProvider provider = new TestProvider();
        URI uri = URI.create("file:///test.txt");

        assertEquals("test", provider.provide(uri));
    }
}
