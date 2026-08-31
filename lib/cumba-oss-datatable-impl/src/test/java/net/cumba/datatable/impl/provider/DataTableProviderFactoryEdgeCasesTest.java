package net.cumba.datatable.impl.provider;

import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.net.URI;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.library.ILibraryMember;
import net.cumba.datatable.metadata.IMetadataLibrary;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Edge-case tests for {@link DataTableProviderFactory} covering the URI / library-member overloads
 * when no SPI provider is registered for the requested format. These complement the basic "factory
 * is a singleton" tests in {@code DataTableProviderFactoryTest}.
 */
class DataTableProviderFactoryEdgeCasesTest
{

    private final DataTableProviderFactory factory = DataTableProviderFactory.getFactory();

    @Test
    void testResolveFormatFromFileNameUnknownExtension()
    {
        // The test classpath of this module has no IProviderSupplier registrations,
        // so no FileInfo will match any filename — returns null.
        assertNull(factory.resolveFormatFromFileName("data.unknown"));
        assertNull(factory.resolveFormatFromFileName(null));
        assertNull(factory.resolveFormatFromFileName(""));
    }


    @Test
    void testProvideWithUriReturnsNullWhenNoSupplierMatches() throws IOException
    {
        URI uri = URI.create("file:///nope/whatever.unknown");
        assertNull(factory.provide(uri, null, (IMetadataLibrary) null));
    }


    @Test
    void testProvideMetaDataReturnsNullWhenNoSupplierMatches() throws IOException
    {
        URI uri = URI.create("file:///nope/whatever.unknown");
        assertNull(factory.provideMetaData(uri, null, null));
    }


    @Test
    void testProvideOnLibraryMemberWithUnknownFileInfo() throws IOException
    {
        ILibraryMember member = Mockito.mock(ILibraryMember.class);
        URI memberUri = URI.create("file:///nope/whatever.unknown");
        Mockito.when(member.getUri()).thenReturn(memberUri);

        // No supplier matches: provide returns null
        assertNull(factory.provide(member, null, (IMetadataLibrary) null));
        assertNull(factory.provideMetaData(member, null, null));
        // Touch the mock so SpotBugs URF_UNREAD_FIELD does not complain
        Mockito.verify(member, Mockito.atLeastOnce()).getUri();
    }


    @Test
    void testProvideWithUnknownFileInfoSupplied() throws IOException
    {
        // Explicit FileInfo that is also unsupported: same null-result path.
        URI uri = URI.create("file:///nope/x");
        FileInfo unknown = FileInfo.createFor("never", "Never used");
        assertNull(factory.provide(uri, unknown, (IMetadataLibrary) null));
    }
}
