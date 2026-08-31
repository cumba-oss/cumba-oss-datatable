package net.cumba.datatable.impl.metadata.dblib;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.cumba.datatable.impl.library.dblib.beans.DataBrowserColumnMetaBean;
import net.cumba.datatable.impl.library.dblib.beans.DataBrowserLibraryBean;
import net.cumba.datatable.impl.library.dblib.beans.DataBrowserMemberMetaBean;
import net.cumba.datatable.impl.library.dblib.beans.DataBrowserValueFormatBean;
import net.cumba.datatable.metadata.ICodeList;
import net.cumba.datatable.metadata.ICodelistEntry;
import net.cumba.datatable.metadata.IColumnMetadata;
import net.cumba.datatable.metadata.IDataTableMetadata;
import net.cumba.datatable.metadata.IMetadataLibrary;
import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.Test;

class DataBrowserMetadataLibraryTest
{

    private static final URI LIB_URI = URI.create("file:///lib/test.dblib");

    private static IMetadataLibrary build(DataBrowserLibraryBean aBean)
    {
        return DataBrowserMetadataLibrary.from(aBean, LIB_URI);
    }

    // ============================================================
    // Top-level library metadata
    // ============================================================


    @Test
    void testLibraryBasicProperties()
    {
        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("MyLib").build();
        IMetadataLibrary lib = build(bean);

        assertEquals("MyLib", lib.getName());
        assertNull(lib.getVersion());
        assertFalse(lib.isColumnNameCaseSensitive());
    }


    @Test
    void testLibraryAttributes()
    {
        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("sponsor", "Acme");
        attrs.put("study", "ABC-123");
        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("L").attributes(attrs)
                .build();
        IMetadataLibrary lib = build(bean);

        assertEquals(Set.of("sponsor", "study"), lib.getMetaKeys());
        assertEquals(Optional.of("Acme"), lib.getMetaValue("sponsor"));
        assertEquals(Optional.empty(), lib.getMetaValue("missing"));
    }


    @Test
    void testLibraryWithoutAttributesHasEmptyMetaKeys()
    {
        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("L").build();
        IMetadataLibrary lib = build(bean);

        assertTrue(lib.getMetaKeys().isEmpty());
        assertEquals(Optional.empty(), lib.getMetaValue("anything"));
    }

    // ============================================================
    // Data tables (built from memberMeta + columnMeta)
    // ============================================================


    @Test
    void testGetDataTablesEmptyWhenNoMembersOrColumns()
    {
        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("L").build();
        IMetadataLibrary lib = build(bean);

        assertTrue(lib.getDataTables().isEmpty());
    }


    @Test
    void testGetDataTablesBuiltFromMemberMeta()
    {
        DataBrowserMemberMetaBean m = DataBrowserMemberMetaBean.builder().uri("dm.csv")
                .label("Demographics").build();
        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("L")
                .memberMeta(new DataBrowserMemberMetaBean[]
                {
                        m
                }).build();
        IMetadataLibrary lib = build(bean);

        List<IDataTableMetadata> tables = lib.getDataTables();
        assertEquals(1, tables.size());
        // Cached: second call returns same object
        assertSame(tables, lib.getDataTables());
        assertEquals("Demographics", tables.get(0).getLabel());
    }


    @Test
    void testGetDataTableByName()
    {
        DataBrowserMemberMetaBean m = DataBrowserMemberMetaBean.builder().uri("DM.csv")
                .label("Demographics").build();
        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("L")
                .memberMeta(new DataBrowserMemberMetaBean[]
                {
                        m
                }).build();
        IMetadataLibrary lib = build(bean);

        // Name is derived from URI -> "DM" (filename without extension, upper-cased)
        Optional<IDataTableMetadata> dt = lib.getDataTable("DM");
        assertTrue(dt.isPresent());
        assertEquals("DM", dt.get().getName());
        // Case-insensitive lookup
        assertTrue(lib.getDataTable("dm").isPresent());
        assertTrue(lib.getDataTable("Unknown").isEmpty());
    }


    @Test
    void testGetDataTablesFromColumnMetaOnly()
    {
        DataBrowserColumnMetaBean c1 = DataBrowserColumnMetaBean.builder().uri("dm.csv").name("AGE")
                .type("DOUBLE").label("Age").format("8.").key(2).attributes(Map.of("origin", "CRF"))
                .build();
        DataBrowserColumnMetaBean c2 = DataBrowserColumnMetaBean.builder().uri("dm.csv")
                .name("SUBJID").type("STRING").build();
        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("L")
                .columnMeta(new DataBrowserColumnMetaBean[]
                {
                        c1, c2
                }).build();
        IMetadataLibrary lib = build(bean);

        List<IDataTableMetadata> tables = lib.getDataTables();
        assertEquals(1, tables.size());

        IDataTableMetadata dt = tables.get(0);
        // Member meta absent → label is null
        assertNull(dt.getLabel());
        assertEquals(2, dt.getColumns().size());

        // Lookup column case-insensitive
        Optional<IColumnMetadata> ageOpt = dt.getColumn("age");
        assertTrue(ageOpt.isPresent());
        IColumnMetadata age = ageOpt.get();
        assertEquals("AGE", age.getName());
        assertEquals("Age", age.getLabel());
        assertEquals("8.", age.getDisplayFormat());
        assertEquals(DataValueType.DOUBLE, age.getType());
        assertEquals(2, age.getKeySequence());
        assertFalse(age.isByGroup());
        assertEquals(0, age.getLength());
        assertNull(age.getNativeType());
        assertEquals(Set.of("origin"), age.getMetaKeys());
        assertEquals(Optional.of("CRF"), age.getMetaValue("origin"));
        assertEquals(Optional.empty(), age.getMetaValue("missing"));

        // Column with no attributes
        IColumnMetadata sub = dt.getColumn("SUBJID").orElseThrow();
        assertTrue(sub.getMetaKeys().isEmpty());
        assertEquals(Optional.empty(), sub.getMetaValue("any"));

        // Column with absolute index based on insertion order
        assertEquals(0, dt.getColumns().get(0).getIndex());
        assertEquals(1, dt.getColumns().get(1).getIndex());

        // Lookup by null returns empty
        assertTrue(dt.getColumn(null).isEmpty());
        assertTrue(dt.getColumn("missing").isEmpty());
    }


    @Test
    void testColumnDefaultTypeIsStringWhenBlank()
    {
        DataBrowserColumnMetaBean c = DataBrowserColumnMetaBean.builder().uri("a.csv").name("X")
                .build();
        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("L")
                .columnMeta(new DataBrowserColumnMetaBean[]
                {
                        c
                }).build();
        IMetadataLibrary lib = build(bean);

        IColumnMetadata col = lib.getDataTables().get(0).getColumns().get(0);
        assertEquals(DataValueType.STRING, col.getType());
    }


    @Test
    void testColumnUnknownTypeFallsBackToString()
    {
        DataBrowserColumnMetaBean c = DataBrowserColumnMetaBean.builder().uri("a.csv").name("X")
                .type("NOSUCHTYPE").build();
        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("L")
                .columnMeta(new DataBrowserColumnMetaBean[]
                {
                        c
                }).build();
        IMetadataLibrary lib = build(bean);

        IColumnMetadata col = lib.getDataTables().get(0).getColumns().get(0);
        assertEquals(DataValueType.STRING, col.getType());
    }


    @Test
    void testTableNameFromUriPath()
    {
        DataBrowserMemberMetaBean m = DataBrowserMemberMetaBean.builder()
                .uri("file:///some/where/AE.csv").build();
        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("L")
                .memberMeta(new DataBrowserMemberMetaBean[]
                {
                        m
                }).build();
        IMetadataLibrary lib = build(bean);

        IDataTableMetadata dt = lib.getDataTables().get(0);
        assertEquals("AE", dt.getName());
        assertNotNull(dt.getTableURI());
        assertEquals("file:///some/where/AE.csv", dt.getTableURI().toString());
    }


    @Test
    void testTableNameFromUriFragment()
    {
        DataBrowserMemberMetaBean m = DataBrowserMemberMetaBean.builder()
                .uri("file:///lib/data.json#mytable").build();
        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("L")
                .memberMeta(new DataBrowserMemberMetaBean[]
                {
                        m
                }).build();
        IMetadataLibrary lib = build(bean);

        // Fragment wins over filename
        assertEquals("mytable", lib.getDataTables().get(0).getName());
    }


    @Test
    void testMemberMetaAttributes()
    {
        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("origin", "Source A");
        attrs.put("structure", "One per subject");
        DataBrowserMemberMetaBean m = DataBrowserMemberMetaBean.builder().uri("dm.csv")
                .label("Demographics").attributes(attrs).build();
        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("L")
                .memberMeta(new DataBrowserMemberMetaBean[]
                {
                        m
                }).build();
        IMetadataLibrary lib = build(bean);

        IDataTableMetadata dt = lib.getDataTables().get(0);
        assertEquals(attrs.keySet(), dt.getMetaKeys());
        assertEquals(Optional.of("Source A"), dt.getMetaValue("origin"));
        assertEquals(Optional.empty(), dt.getMetaValue("missing"));
    }


    @Test
    void testMemberMetaNoAttributesYieldsEmptyMetaKeys()
    {
        DataBrowserMemberMetaBean m = DataBrowserMemberMetaBean.builder().uri("dm.csv").build();
        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("L")
                .memberMeta(new DataBrowserMemberMetaBean[]
                {
                        m
                }).build();
        IMetadataLibrary lib = build(bean);

        IDataTableMetadata dt = lib.getDataTables().get(0);
        assertTrue(dt.getMetaKeys().isEmpty());
        assertEquals(Optional.empty(), dt.getMetaValue("any"));
    }

    // ============================================================
    // URI resolution
    // ============================================================


    @Test
    void testRelativeUriIsResolvedAgainstLibraryUri()
    {
        DataBrowserMemberMetaBean m = DataBrowserMemberMetaBean.builder().uri("nested/x.csv")
                .build();
        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("L")
                .memberMeta(new DataBrowserMemberMetaBean[]
                {
                        m
                }).build();
        IMetadataLibrary lib = build(bean);

        IDataTableMetadata dt = lib.getDataTables().get(0);
        assertEquals("file:/lib/nested/x.csv", dt.getTableURI().toString());
    }


    @Test
    void testAbsoluteUriIsKeptAsIs()
    {
        DataBrowserMemberMetaBean m = DataBrowserMemberMetaBean.builder()
                .uri("https://example.com/data/y.json").build();
        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("L")
                .memberMeta(new DataBrowserMemberMetaBean[]
                {
                        m
                }).build();
        IMetadataLibrary lib = build(bean);

        IDataTableMetadata dt = lib.getDataTables().get(0);
        assertEquals("https://example.com/data/y.json", dt.getTableURI().toString());
    }


    @Test
    void testInvalidUriFallsBackToRawString()
    {
        // Hard-to-parse fragment with spaces. URI throws → resolveUri falls back.
        DataBrowserMemberMetaBean m = DataBrowserMemberMetaBean.builder().uri("not a url at all")
                .build();
        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("L")
                .memberMeta(new DataBrowserMemberMetaBean[]
                {
                        m
                }).build();
        IMetadataLibrary lib = build(bean);

        IDataTableMetadata dt = lib.getDataTables().get(0);
        // The raw "not a url at all" was kept as the uriKey. getTableURI() then tries
        // to parse it as a URI and returns null on failure.
        assertNull(dt.getTableURI());
    }


    @Test
    void testMemberAndColumnsMergedByUri()
    {
        DataBrowserMemberMetaBean m = DataBrowserMemberMetaBean.builder().uri("dm.csv")
                .label("Demographics").build();
        DataBrowserColumnMetaBean c = DataBrowserColumnMetaBean.builder().uri("dm.csv").name("AGE")
                .build();
        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("L")
                .memberMeta(new DataBrowserMemberMetaBean[]
                {
                        m
                }).columnMeta(new DataBrowserColumnMetaBean[]
                {
                        c
                }).build();
        IMetadataLibrary lib = build(bean);

        List<IDataTableMetadata> tables = lib.getDataTables();
        assertEquals(1, tables.size(), "member and column with same URI yield one table");
        assertEquals("Demographics", tables.get(0).getLabel());
        assertEquals(1, tables.get(0).getColumns().size());
    }

    // ============================================================
    // Codelists
    // ============================================================


    @Test
    void testCodelistsEmptyWhenNoFormats()
    {
        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("L").build();
        IMetadataLibrary lib = build(bean);

        assertTrue(lib.getCodelists().isEmpty());
        assertTrue(lib.getCodelist("anything").isEmpty());
        // Second call returns cached list
        assertSame(lib.getCodelists(), lib.getCodelists());
    }


    @Test
    void testCodelistEntries()
    {
        Map<String, String> vm = new LinkedHashMap<>();
        vm.put("M", "Male");
        vm.put("F", "Female");
        DataBrowserValueFormatBean fmt = DataBrowserValueFormatBean.builder().name("SEX")
                .label("Sex codelist").valueMap(vm).build();
        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("L")
                .formats(new DataBrowserValueFormatBean[]
                {
                        fmt
                }).build();
        IMetadataLibrary lib = build(bean);

        List<ICodeList> cls = lib.getCodelists();
        assertEquals(1, cls.size());
        ICodeList cl = cls.get(0);
        assertEquals("SEX", cl.getName());
        assertEquals(DataValueType.STRING, cl.getValueType());
        assertEquals(Set.of("label"), cl.getMetaKeys());
        assertEquals(Optional.of("Sex codelist"), cl.getMetaValue("label"));
        assertEquals(Optional.empty(), cl.getMetaValue("missing"));

        List<ICodelistEntry> entries = cl.getEntries();
        // Cached
        assertSame(entries, cl.getEntries());
        assertEquals(2, entries.size());
        ICodelistEntry e0 = entries.get(0);
        assertEquals("M", e0.getCodeValue());
        assertEquals("Male", e0.getDecodeValue());
        assertTrue(e0.getMetaKeys().isEmpty());
        assertEquals(Optional.empty(), e0.getMetaValue("k"));

        Optional<ICodeList> sexOpt = lib.getCodelist("SEX");
        assertTrue(sexOpt.isPresent());
        assertTrue(lib.getCodelist("nope").isEmpty());
    }


    @Test
    void testCodelistWithoutLabelHasEmptyMeta()
    {
        DataBrowserValueFormatBean fmt = DataBrowserValueFormatBean.builder().name("X")
                .valueMap(Map.of()).build();
        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("L")
                .formats(new DataBrowserValueFormatBean[]
                {
                        fmt
                }).build();
        IMetadataLibrary lib = build(bean);

        ICodeList cl = lib.getCodelists().get(0);
        assertTrue(cl.getMetaKeys().isEmpty());
        assertTrue(cl.getEntries().isEmpty());
        assertEquals(Optional.empty(), cl.getMetaValue("label"));
    }


    /**
     * A null element in any of the deserialized bean arrays (e.g. {@code "columnMeta":[null]} in a
     * hand-edited .dblib) must be skipped, not dereferenced.
     */
    @Test
    void testNullArrayElementsAreSkipped()
    {
        DataBrowserColumnMetaBean col = DataBrowserColumnMetaBean.builder().uri("dm.csv")
                .name("AGE").build();
        DataBrowserMemberMetaBean mem = DataBrowserMemberMetaBean.builder().uri("dm.csv").build();
        DataBrowserValueFormatBean fmt = DataBrowserValueFormatBean.builder().name("SEX")
                .valueMap(Map.of()).build();
        DataBrowserLibraryBean bean = DataBrowserLibraryBean.builder().name("L")
                .columnMeta(new DataBrowserColumnMetaBean[]
                {
                        null, col
                }).memberMeta(new DataBrowserMemberMetaBean[]
                {
                        null, mem
                }).formats(new DataBrowserValueFormatBean[]
                {
                        null, fmt
                }).build();
        IMetadataLibrary lib = build(bean);

        // The non-null entries survive; the null elements are dropped rather than NPEing.
        assertEquals(1, assertDoesNotThrow(() -> lib.getDataTables()).size());
        assertEquals(1, assertDoesNotThrow(() -> lib.getCodelists()).size());
    }
}
