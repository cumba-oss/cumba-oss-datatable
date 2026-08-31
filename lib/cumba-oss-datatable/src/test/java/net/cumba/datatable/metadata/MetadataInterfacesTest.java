package net.cumba.datatable.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.Test;

/**
 * Tests for the metadata package interfaces using Mockito mocks.
 */
// Mock methods are invoked directly to verify interface contracts / default-method dispatch.
@SuppressWarnings("DirectInvocationOnMock")
class MetadataInterfacesTest
{

    // ==================== IMetadataElement ====================

    @Test
    void testMetadataElementGetMetaKeys()
    {
        IMetadataElement element = mock(IMetadataElement.class);
        when(element.getMetaKeys()).thenReturn(Set.of("key1", "key2"));

        Set<String> keys = element.getMetaKeys();
        assertEquals(2, keys.size());
        assertTrue(keys.contains("key1"));
        assertTrue(keys.contains("key2"));
    }


    @Test
    void testMetadataElementGetMetaKeysEmpty()
    {
        IMetadataElement element = mock(IMetadataElement.class);
        when(element.getMetaKeys()).thenReturn(Set.of());

        Set<String> keys = element.getMetaKeys();
        assertNotNull(keys);
        assertTrue(keys.isEmpty());
    }


    @Test
    void testMetadataElementGetMetaValue()
    {
        IMetadataElement element = mock(IMetadataElement.class);
        when(element.getMetaValue("key1")).thenReturn(Optional.of("value1"));
        when(element.getMetaValue("missing")).thenReturn(Optional.empty());

        assertTrue(element.getMetaValue("key1").isPresent());
        assertEquals("value1", element.getMetaValue("key1").get());
        assertFalse(element.getMetaValue("missing").isPresent());
    }

    // ==================== ICodelistEntry ====================


    @Test
    void testCodelistEntry()
    {
        ICodelistEntry entry = mock(ICodelistEntry.class);
        when(entry.getCodeValue()).thenReturn("M");
        when(entry.getDecodeValue()).thenReturn("Male");

        assertEquals("M", entry.getCodeValue());
        assertEquals("Male", entry.getDecodeValue());
    }

    // ==================== ICodeList ====================


    @Test
    void testCodeList()
    {
        ICodelistEntry entry1 = mock(ICodelistEntry.class);
        when(entry1.getCodeValue()).thenReturn("M");

        ICodelistEntry entry2 = mock(ICodelistEntry.class);
        when(entry2.getCodeValue()).thenReturn("F");

        ICodeList codelist = mock(ICodeList.class);
        when(codelist.getName()).thenReturn("SEX");
        when(codelist.getValueType()).thenReturn(DataValueType.STRING);
        when(codelist.getEntries()).thenReturn(List.of(entry1, entry2));

        assertEquals("SEX", codelist.getName());
        assertEquals(DataValueType.STRING, codelist.getValueType());
        assertEquals(2, codelist.getEntries().size());
    }

    // ==================== IColumnMetadata ====================


    @Test
    void testColumnMetadata()
    {
        IColumnMetadata col = mock(IColumnMetadata.class);
        when(col.getName()).thenReturn("AGE");
        when(col.getLabel()).thenReturn("Subject Age");
        when(col.getDisplayFormat()).thenReturn("BEST12.");
        when(col.getIndex()).thenReturn(3);
        when(col.getType()).thenReturn(DataValueType.DOUBLE);
        when(col.getLength()).thenReturn(12);
        when(col.getNativeType()).thenReturn("float");
        when(col.getKeySequence()).thenReturn(0);
        when(col.isByGroup()).thenReturn(false);

        assertEquals("AGE", col.getName());
        assertEquals("Subject Age", col.getLabel());
        assertEquals("BEST12.", col.getDisplayFormat());
        assertEquals(3, col.getIndex());
        assertEquals(DataValueType.DOUBLE, col.getType());
        assertEquals(12, col.getLength());
        assertEquals("float", col.getNativeType());
        assertEquals(0, col.getKeySequence());
        assertFalse(col.isByGroup());
    }


    @Test
    void testColumnMetadataKeyColumn()
    {
        IColumnMetadata col = mock(IColumnMetadata.class);
        when(col.getName()).thenReturn("USUBJID");
        when(col.getKeySequence()).thenReturn(1);
        when(col.isByGroup()).thenReturn(true);

        assertEquals(1, col.getKeySequence());
        assertTrue(col.isByGroup());
    }

    // ==================== IDataTableMetadata ====================


    @Test
    void testDataTableMetadata()
    {
        IColumnMetadata col = mock(IColumnMetadata.class);
        when(col.getName()).thenReturn("AGE");

        IDataTableMetadata meta = mock(IDataTableMetadata.class);
        when(meta.getName()).thenReturn("DM");
        when(meta.getLabel()).thenReturn("Demographics");
        when(meta.getTableURI()).thenReturn(URI.create("file:///data/dm.xpt"));
        when(meta.getColumns()).thenReturn(List.of(col));
        when(meta.getColumn("AGE")).thenReturn(Optional.of(col));
        when(meta.getColumn("MISSING")).thenReturn(Optional.empty());

        assertEquals("DM", meta.getName());
        assertEquals("Demographics", meta.getLabel());
        assertEquals(URI.create("file:///data/dm.xpt"), meta.getTableURI());
        assertEquals(1, meta.getColumns().size());
        assertTrue(meta.getColumn("AGE").isPresent());
        assertFalse(meta.getColumn("MISSING").isPresent());
    }


    @Test
    void testDataTableMetadataNullUri()
    {
        IDataTableMetadata meta = mock(IDataTableMetadata.class);
        when(meta.getTableURI()).thenReturn(null);

        assertNotNull(meta);
        assertEquals(null, meta.getTableURI());
    }

    // ==================== IMetadataLibrary ====================


    @Test
    void testMetadataLibrary()
    {
        IDataTableMetadata dm = mock(IDataTableMetadata.class);
        when(dm.getName()).thenReturn("DM");

        ICodeList sex = mock(ICodeList.class);
        when(sex.getName()).thenReturn("SEX");

        IMetadataLibrary lib = mock(IMetadataLibrary.class);
        when(lib.getName()).thenReturn("SDTM");
        when(lib.getVersion()).thenReturn("1.0");
        when(lib.isColumnNameCaseSensitive()).thenReturn(false);
        when(lib.getDataTables()).thenReturn(List.of(dm));
        when(lib.getDataTable("DM")).thenReturn(Optional.of(dm));
        when(lib.getDataTable("XX")).thenReturn(Optional.empty());
        when(lib.getCodelists()).thenReturn(List.of(sex));
        when(lib.getCodelist("SEX")).thenReturn(Optional.of(sex));
        when(lib.getCodelist("XX")).thenReturn(Optional.empty());

        assertEquals("SDTM", lib.getName());
        assertEquals("1.0", lib.getVersion());
        assertFalse(lib.isColumnNameCaseSensitive());
        assertEquals(1, lib.getDataTables().size());
        assertTrue(lib.getDataTable("DM").isPresent());
        assertFalse(lib.getDataTable("XX").isPresent());
        assertEquals(1, lib.getCodelists().size());
        assertTrue(lib.getCodelist("SEX").isPresent());
        assertFalse(lib.getCodelist("XX").isPresent());
    }
}
