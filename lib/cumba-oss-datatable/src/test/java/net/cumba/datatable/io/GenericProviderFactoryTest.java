package net.cumba.datatable.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class GenericProviderFactoryTest
{

    // ==================== Test Stubs ====================

    static class TestProvider implements IGenericProvider<String>
    {

        private final List<FileInfo> fileInfos;

        private final String result;

        private TestProvider(List<FileInfo> fileInfos, String result)
        {
            this.fileInfos = fileInfos;
            this.result = result;
        }


        @Override
        public String getName()
        {
            return "test";
        }


        @Override
        public String getDescription()
        {
            return "test provider";
        }


        @Override
        public List<FileInfo> getSupportedFileInfos()
        {
            return fileInfos;
        }


        @Override
        public String provide(URI aUri, FileInfo aFileInfo) throws IOException
        {
            return result;
        }
    }


    public static class TestSupplier implements IGenericSupplier<TestProvider>
    {

        private final List<FileInfo> fileInfos;

        private final boolean canProvide;

        private final TestProvider provider;

        public TestSupplier(List<FileInfo> fileInfos, boolean canProvide, TestProvider provider)
        {
            this.fileInfos = fileInfos;
            this.canProvide = canProvide;
            this.provider = provider;
        }


        @Override
        public List<FileInfo> getSupportedFileInfos()
        {
            return fileInfos;
        }


        @Override
        public boolean canProvideFor(URI aUri, FileInfo aFileInfo)
        {
            return canProvide;
        }


        @Override
        public TestProvider getProvider(URI aUri, FileInfo aFileInfo)
        {
            return canProvide ? provider : null;
        }
    }


    /**
     * Testable subclass that allows injecting suppliers directly instead of loading via SPI.
     */
    private static class TestableProviderFactory
            extends GenericProviderFactory<TestSupplier, TestProvider, String>
    {

        private final List<TestSupplier> injectedSuppliers;

        private TestableProviderFactory(List<TestSupplier> suppliers)
        {
            super(TestSupplier.class);
            this.injectedSuppliers = suppliers;
        }


        @Override
        protected List<TestSupplier> getSuppliers()
        {
            return injectedSuppliers;
        }
    }

    // ==================== Constructor ====================

    @Test
    void testConstructorNullThrows()
    {
        assertThrows(NullPointerException.class, () ->
        {
            new GenericProviderFactory<>(null);
        });
    }

    // ==================== getFileInfos ====================


    @Test
    void testGetFileInfosEmpty()
    {
        TestableProviderFactory factory = new TestableProviderFactory(List.of());

        List<FileInfo> infos = factory.getFileInfos();
        assertNotNull(infos);
        assertTrue(infos.isEmpty());
    }


    @Test
    void testGetFileInfosSingleSupplier()
    {
        FileInfo csvInfo = FileInfo.createFor("csv", "CSV Files");
        TestSupplier supplier = new TestSupplier(List.of(csvInfo), true, null);
        TestableProviderFactory factory = new TestableProviderFactory(List.of(supplier));

        List<FileInfo> infos = factory.getFileInfos();
        assertEquals(1, infos.size());
        assertEquals("csv", infos.get(0).getFileExtension());
    }


    @Test
    void testGetFileInfosMultipleSuppliers()
    {
        FileInfo csvInfo = FileInfo.createFor("csv", "CSV");
        FileInfo jsonInfo = FileInfo.createFor("json", "JSON");
        TestSupplier supplier1 = new TestSupplier(List.of(csvInfo), true, null);
        TestSupplier supplier2 = new TestSupplier(List.of(jsonInfo), true, null);
        TestableProviderFactory factory = new TestableProviderFactory(
                List.of(supplier1, supplier2));

        List<FileInfo> infos = factory.getFileInfos();
        assertEquals(2, infos.size());
    }


    @Test
    void testGetFileInfosSupplierWithMultipleInfos()
    {
        FileInfo csvInfo = FileInfo.createFor("csv", "CSV");
        FileInfo tsvInfo = FileInfo.createFor("tsv", "TSV");
        TestSupplier supplier = new TestSupplier(List.of(csvInfo, tsvInfo), true, null);
        TestableProviderFactory factory = new TestableProviderFactory(List.of(supplier));

        List<FileInfo> infos = factory.getFileInfos();
        assertEquals(2, infos.size());
    }

    // ==================== getFirstProviderFor ====================


    @Test
    void testGetFirstProviderForMatch()
    {
        FileInfo csvInfo = FileInfo.createFor("csv", "CSV");
        TestProvider provider = new TestProvider(List.of(csvInfo), "csv-result");
        TestSupplier supplier = new TestSupplier(List.of(csvInfo), true, provider);
        TestableProviderFactory factory = new TestableProviderFactory(List.of(supplier));

        URI uri = URI.create("file:///data.csv");
        TestProvider result = factory.getFirstProviderFor(uri, csvInfo).orElse(null);
        assertNotNull(result);
    }


    @Test
    void testGetFirstProviderForNoMatch()
    {
        FileInfo csvInfo = FileInfo.createFor("csv", "CSV");
        TestSupplier supplier = new TestSupplier(List.of(csvInfo), false, null);
        TestableProviderFactory factory = new TestableProviderFactory(List.of(supplier));

        URI uri = URI.create("file:///data.xml");
        TestProvider result = factory.getFirstProviderFor(uri, null).orElse(null);
        assertNull(result);
    }


    @Test
    void testGetFirstProviderForReturnsFirst()
    {
        FileInfo csvInfo = FileInfo.createFor("csv", "CSV");
        TestProvider provider1 = new TestProvider(List.of(csvInfo), "first");
        TestProvider provider2 = new TestProvider(List.of(csvInfo), "second");
        TestSupplier supplier1 = new TestSupplier(List.of(csvInfo), true, provider1);
        TestSupplier supplier2 = new TestSupplier(List.of(csvInfo), true, provider2);
        TestableProviderFactory factory = new TestableProviderFactory(
                List.of(supplier1, supplier2));

        URI uri = URI.create("file:///data.csv");
        TestProvider result = factory.getFirstProviderFor(uri, csvInfo).orElse(null);
        assertNotNull(result);
        try
        {
            assertEquals("first", result.provide(uri, csvInfo));
        }
        catch (IOException _)
        {
            fail("Should not throw IOException");
        }
    }


    @Test
    void testGetFirstProviderForCanProvideButReturnsNull()
    {
        // Supplier says canProvideFor=true but getProvider returns null
        FileInfo csvInfo = FileInfo.createFor("csv", "CSV");
        TestSupplier supplier = new TestSupplier(List.of(csvInfo), true, null);
        TestableProviderFactory factory = new TestableProviderFactory(List.of(supplier));

        URI uri = URI.create("file:///data.csv");
        // canProvide is true, but provider is null, so it's skipped
        // However, our stub returns null when canProvide is true AND provider is null
        // because getProvider checks canProvide flag but returns null when provider is null
        TestProvider result = factory.getFirstProviderFor(uri, csvInfo).orElse(null);
        assertNull(result,
                "Should return null when supplier canProvide but getProvider returns null");
    }


    @Test
    void testGetFirstProviderForNoSuppliers()
    {
        TestableProviderFactory factory = new TestableProviderFactory(List.of());

        URI uri = URI.create("file:///data.csv");
        TestProvider result = factory.getFirstProviderFor(uri, null).orElse(null);
        assertNull(result);
    }

    // ==================== getAllProvidersFor ====================


    @Test
    void testGetAllProvidersForMatch()
    {
        FileInfo csvInfo = FileInfo.createFor("csv", "CSV");
        TestProvider provider = new TestProvider(List.of(csvInfo), "result");
        TestSupplier supplier = new TestSupplier(List.of(csvInfo), true, provider);
        TestableProviderFactory factory = new TestableProviderFactory(List.of(supplier));

        URI uri = URI.create("file:///data.csv");
        List<TestProvider> results = factory.getAllProvidersFor(uri, csvInfo);
        assertEquals(1, results.size());
    }


    @Test
    void testGetAllProvidersForMultipleMatches()
    {
        FileInfo csvInfo = FileInfo.createFor("csv", "CSV");
        TestProvider provider1 = new TestProvider(List.of(csvInfo), "first");
        TestProvider provider2 = new TestProvider(List.of(csvInfo), "second");
        TestSupplier supplier1 = new TestSupplier(List.of(csvInfo), true, provider1);
        TestSupplier supplier2 = new TestSupplier(List.of(csvInfo), true, provider2);
        TestableProviderFactory factory = new TestableProviderFactory(
                List.of(supplier1, supplier2));

        URI uri = URI.create("file:///data.csv");
        List<TestProvider> results = factory.getAllProvidersFor(uri, csvInfo);
        assertEquals(2, results.size());
    }


    @Test
    void testGetAllProvidersForNoMatch()
    {
        FileInfo csvInfo = FileInfo.createFor("csv", "CSV");
        TestSupplier supplier = new TestSupplier(List.of(csvInfo), false, null);
        TestableProviderFactory factory = new TestableProviderFactory(List.of(supplier));

        URI uri = URI.create("file:///data.xml");
        List<TestProvider> results = factory.getAllProvidersFor(uri, null);
        assertTrue(results.isEmpty());
    }


    @Test
    void testGetAllProvidersForEmpty()
    {
        TestableProviderFactory factory = new TestableProviderFactory(List.of());

        URI uri = URI.create("file:///data.csv");
        List<TestProvider> results = factory.getAllProvidersFor(uri, null);
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }


    @Test
    void testGetAllProvidersForNullFileInfo()
    {
        FileInfo csvInfo = FileInfo.createFor("csv", "CSV");
        TestProvider provider = new TestProvider(List.of(csvInfo), "result");
        TestSupplier supplier = new TestSupplier(List.of(csvInfo), true, provider);
        TestableProviderFactory factory = new TestableProviderFactory(List.of(supplier));

        URI uri = URI.create("file:///data.csv");
        List<TestProvider> results = factory.getAllProvidersFor(uri, null);
        assertEquals(1, results.size());
    }

    // ==================== IGenericSupplier default methods ====================


    @Test
    void testSupplierCanProvideForDefaultDelegatesToOverload()
    {
        FileInfo csvInfo = FileInfo.createFor("csv", "CSV");
        TestProvider provider = new TestProvider(List.of(csvInfo), "result");
        TestSupplier supplier = new TestSupplier(List.of(csvInfo), true, provider);

        URI uri = URI.create("file:///data.csv");
        // default canProvideFor(URI) should delegate to canProvideFor(URI, null)
        assertEquals(supplier.canProvideFor(uri, null), supplier.canProvideFor(uri));
    }


    @Test
    void testSupplierGetProviderDefaultDelegatesToOverload()
    {
        FileInfo csvInfo = FileInfo.createFor("csv", "CSV");
        TestProvider provider = new TestProvider(List.of(csvInfo), "result");
        TestSupplier supplier = new TestSupplier(List.of(csvInfo), true, provider);

        URI uri = URI.create("file:///data.csv");
        // default getProvider(URI) should delegate to getProvider(URI, null)
        assertEquals(supplier.getProvider(uri, null), supplier.getProvider(uri));
    }
}
