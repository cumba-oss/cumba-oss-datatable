package net.cumba.datatable.impl.provider;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.io.Property;
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
    void testProvideWithPropertiesReturnsNullWhenNoSupplierMatches() throws IOException
    {
        URI uri = URI.create("file:///nope/whatever.unknown");
        assertNull(factory.provide(uri, null, null, Collections.emptyMap()));
    }


    @Test
    void testProvideMetaDataReturnsNullWhenNoSupplierMatches() throws IOException
    {
        URI uri = URI.create("file:///nope/whatever.unknown");
        assertNull(factory.provideMetaData(uri, null, null, Collections.emptyMap()));
    }


    @Test
    void testGetProviderPropertiesReturnsEmptyForUnknownUri()
    {
        URI uri = URI.create("file:///nope/whatever.unknown");
        List<Property> props = factory.getProviderProperties(uri, null);
        assertNotNull(props);
        assertTrue(props.isEmpty());
    }


    @Test
    void testGetProviderPropertiesForNullMemberIsEmpty()
    {
        // The library-member overload explicitly handles null
        List<Property> props = factory.getProviderProperties((ILibraryMember) null, null);
        assertNotNull(props);
        assertTrue(props.isEmpty());
    }


    @Test
    void testGetProviderPropertiesForMemberWithUnknownFileInfoIsEmpty()
    {
        ILibraryMember member = Mockito.mock(ILibraryMember.class);
        Mockito.when(member.getUri()).thenReturn(URI.create("file:///x.unknown"));
        Mockito.when(member.getFileInfo()).thenReturn(null);

        List<Property> props = factory.getProviderProperties(member, null);
        assertNotNull(props);
        assertTrue(props.isEmpty());
        // Touch the mock so SpotBugs URF_UNREAD_FIELD does not complain
        Mockito.verify(member, Mockito.atLeastOnce()).getUri();
    }


    @Test
    void testProvideOnLibraryMemberWithUnknownFileInfo() throws IOException
    {
        ILibraryMember member = Mockito.mock(ILibraryMember.class);
        URI memberUri = URI.create("file:///nope/whatever.unknown");
        Mockito.when(member.getUri()).thenReturn(memberUri);

        // No supplier matches: provide returns null
        assertNull(factory.provide(member, null, (IMetadataLibrary) null));
        assertNull(factory.provide(member, null, null, Collections.emptyMap()));
        assertNull(factory.provideMetaData(member, null, null, Collections.emptyMap()));
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
