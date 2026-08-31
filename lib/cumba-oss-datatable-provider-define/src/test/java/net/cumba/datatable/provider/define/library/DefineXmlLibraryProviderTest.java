package net.cumba.datatable.provider.define.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.stream.Stream;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.library.IDataTableLibrary;
import net.cumba.datatable.library.ILibraryMember;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DefineXmlLibraryProviderTest
{

    // ==================== getSupportedFileInfos ====================

    @Test
    void testGetSupportedFileInfos()
    {
        DefineXmlLibraryProvider provider = new DefineXmlLibraryProvider();
        List<FileInfo> infos = provider.getSupportedFileInfos();

        assertEquals(1, infos.size());
        assertSame(DefineXmlLibrarySupplier.FI_DEFINE_XML, infos.get(0));
    }

    // ==================== provideLibraryMembers ====================


    @Test
    void testProvideLibraryMembersAlwaysReturnsEmpty() throws IOException
    {
        DefineXmlLibraryProvider provider = new DefineXmlLibraryProvider();
        IDataTableLibrary otherLib = stubLibrary("test", "label", URI.create("file:///test"));

        Stream<? extends ILibraryMember> result = provider.provideLibraryMembers(otherLib);
        assertNotNull(result);
        assertEquals(0, result.count());
    }

    // ==================== provideLibraryMemberColumns ====================


    @Test
    void testProvideLibraryMemberColumnsAlwaysReturnsEmpty() throws IOException
    {
        DefineXmlLibraryProvider provider = new DefineXmlLibraryProvider();
        ILibraryMember otherMember = stubMember("m", "label", URI.create("file:///m"));

        Stream<? extends DataTableColumnMeta> result = provider
                .provideLibraryMemberColumns(otherMember);
        assertNotNull(result);
        assertEquals(0, result.count());
    }

    // ==================== getLibraryAttribute ====================


    @Test
    void testGetLibraryAttributeWithNonDataBrowserLibrary()
    {
        DefineXmlLibraryProvider provider = new DefineXmlLibraryProvider();
        IDataTableLibrary otherLib = stubLibrary("test", "label", URI.create("file:///test"));

        assertNull(provider.getLibraryAttribute(otherLib, "any-key"));
    }


    @Test
    void testGetLibraryAttributeUnknownKey()
    {
        DefineXmlLibraryProvider provider = new DefineXmlLibraryProvider();
        IDataTableLibrary otherLib = stubLibrary("test", "label", URI.create("file:///test"));

        assertNull(provider.getLibraryAttribute(otherLib, "unknown-key"));
    }

    // ==================== Name and Description ====================


    @Test
    void testNameInitiallyNull()
    {
        DefineXmlLibraryProvider provider = new DefineXmlLibraryProvider();

        assertNull(provider.getName());
    }


    @Test
    void testDescriptionInitiallyNull()
    {
        DefineXmlLibraryProvider provider = new DefineXmlLibraryProvider();

        assertNull(provider.getDescription());
    }

    // ==================== F-E19: validation-report leaf id matcher ====================


    /**
     * F-E19: the relaxed predicate accepts all three real-world separator/casing variants the
     * previous regex was rejecting.
     */
    @ParameterizedTest
    @ValueSource(strings =
    {
            "validation_report-2025", //
            "ValidationReport_v1", //
            "validationreport-2025-04-15-rev2", //
            "VALIDATION_REPORT", //
            "validation-report", //
            "validationreport-x", //
            "Validation_Report_Final"
    })
    void validationReportLeafIdMatches(String leafId)
    {
        assertTrue(DefineXmlLibraryProvider.isValidationReportLeafId(leafId),
                "expected '" + leafId + "' to match the validation-report prefix");
    }


    @ParameterizedTest
    @ValueSource(strings =
    {
            "annotated-crf", //
            "study-protocol", //
            "validate_other", // starts with "validate" not "validation"
            "validation_summary", //
            "validations-report" // extra char between "validation" and the optional separator
    })
    void validationReportLeafIdRejectsUnrelated(String leafId)
    {
        assertFalse(DefineXmlLibraryProvider.isValidationReportLeafId(leafId),
                "did not expect '" + leafId + "' to match the validation-report prefix");
    }


    @Test
    void validationReportLeafIdRejectsNull()
    {
        assertFalse(DefineXmlLibraryProvider.isValidationReportLeafId(null));
    }

    // ==================== F-E26: null-filter on metadata data tables ====================


    /**
     * F-E26: a defensive {@code filter(Objects::nonNull)} sits in front of the
     * {@code dt.getTableURI() != null} predicate so a null entry in the metadata list — which the
     * contract forbids today but a future regression could introduce — does not surface as an NPE.
     */
    @Test
    void buildSourcesSkipsNullDataTableEntries()
    {
        net.cumba.datatable.metadata.IMetadataLibrary metadata = org.mockito.Mockito
                .mock(net.cumba.datatable.metadata.IMetadataLibrary.class);

        // Real entry: has a URI, is not no-data.
        net.cumba.datatable.metadata.IDataTableMetadata real = org.mockito.Mockito
                .mock(net.cumba.datatable.metadata.IDataTableMetadata.class);
        org.mockito.Mockito.when(real.getTableURI()).thenReturn(URI.create("file:///dm.xpt"));
        org.mockito.Mockito.when(real.getName()).thenReturn("DM");
        org.mockito.Mockito.when(real.getLabel()).thenReturn("Demographics");
        org.mockito.Mockito.when(real.getMetaValue(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(java.util.Optional.empty());

        // Null entry — must be silently dropped, not NPE.
        java.util.List<net.cumba.datatable.metadata.IDataTableMetadata> list = new java.util.ArrayList<>();
        list.add(real);
        list.add(null);
        org.mockito.Mockito.when(metadata.getDataTables()).thenReturn(list);

        net.cumba.datatable.impl.library.dblib.beans.DataBrowserSourceBean[] sources = DefineXmlLibraryProvider
                .buildSources(metadata);
        assertEquals(1, sources.length);
        assertEquals("DM", sources[0].getName());
    }


    @Test
    void buildSourcesEmptyListReturnsEmptyArray()
    {
        net.cumba.datatable.metadata.IMetadataLibrary metadata = org.mockito.Mockito
                .mock(net.cumba.datatable.metadata.IMetadataLibrary.class);
        org.mockito.Mockito.when(metadata.getDataTables()).thenReturn(java.util.List.of());

        net.cumba.datatable.impl.library.dblib.beans.DataBrowserSourceBean[] sources = DefineXmlLibraryProvider
                .buildSources(metadata);
        assertEquals(0, sources.length);
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
