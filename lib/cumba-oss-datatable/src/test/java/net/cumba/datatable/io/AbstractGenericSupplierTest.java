package net.cumba.datatable.io;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AbstractGenericSupplierTest
{

    /** Minimal concrete supplier so the abstract base can be exercised. */
    private static final class TestSupplier
            extends AbstractGenericSupplier<IGenericProvider<Object>>
    {

        private TestSupplier(List<FileInfo> aSupportedFiles)
        {
            super(aSupportedFiles);
        }


        @Override
        public IGenericProvider<Object> getProvider(URI aUri, FileInfo aFileInfo)
        {
            return null;
        }
    }

    @Test
    void testNonNullListIsWrappedUnmodifiable()
    {
        // A mutable source list: the base must expose an unmodifiable copy, not the
        // original, so mutating the exposed view must fail.
        TestSupplier supplier = new TestSupplier(new ArrayList<>());

        List<FileInfo> exposed = supplier.getSupportedFileInfos();

        assertNotNull(exposed);
        assertTrue(exposed.isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> exposed.add(null));
    }


    @Test
    void testNullListBecomesEmpty()
    {
        TestSupplier supplier = new TestSupplier(null);

        assertNotNull(supplier.getSupportedFileInfos());
        assertTrue(supplier.getSupportedFileInfos().isEmpty());
    }
}
