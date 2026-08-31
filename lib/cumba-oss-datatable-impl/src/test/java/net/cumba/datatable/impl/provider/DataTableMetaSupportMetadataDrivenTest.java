package net.cumba.datatable.impl.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.metadata.IColumnMetadata;
import net.cumba.datatable.metadata.IDataTableMetadata;
import net.cumba.datatable.metadata.IMetadataLibrary;
import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Metadata-driven tests for {@link DataTableMetaSupport}. These hit branches that the existing
 * suite (which always passes a {@code null} metadata library) cannot reach: column-meta pre-fill,
 * by-URI vs. by-name table lookup, and the {@code copyMetaValue} cascade in {@code setTable}.
 * <p>
 * Each helper builds a minimal Mockito stub describing exactly the shape the support class inspects
 * — keeps the tests focused on behaviour rather than fighting a real {@code define.xml}.
 */
class DataTableMetaSupportMetadataDrivenTest
{

    // ==================== setTable: lookup-by-URI then by-name ====================

    @Test
    void setTable_findsTableByUri()
    {
        URI uri = URI.create("file:///data/dm.xpt");
        IDataTableMetadata dt = stubTableMeta("DM", "Demographics", uri, List.of(),
                Set.of(DataTableMetaSupport.META_KEY_COMMENT), "DM comment");
        IMetadataLibrary lib = stubLibrary(List.of(dt));

        DataTableMetaSupport sup = new DataTableMetaSupport(lib);
        sup.setTable(uri, null);

        DataTableMeta built = sup.getTableMeta().build();
        // label and comment copied through from the matched metadata
        assertEquals("Demographics", built.getLabel());
        assertEquals("DM comment", built.getMetaData(DataTableMetaSupport.META_KEY_COMMENT));
    }


    @Test
    void setTable_findsTableByNameWhenUriDoesNotMatch()
    {
        // metadata entry has a different URI, so the URI-lookup misses; falls back to name lookup.
        IDataTableMetadata dt = stubTableMeta("AE", "Adverse Events",
                URI.create("file:///elsewhere/ae.xpt"), List.of(), Set.of(), null);
        IMetadataLibrary lib = stubLibrary(List.of(dt));
        Mockito.when(lib.getDataTable("AE")).thenReturn(Optional.of(dt));

        DataTableMetaSupport sup = new DataTableMetaSupport(lib);
        sup.setTable(URI.create("file:///somewhere-different.csv"), "AE");

        assertEquals("Adverse Events", sup.getTableMeta().build().getLabel());
    }


    @Test
    void setTable_noMatchKeepsLabelNull()
    {
        // metadata library knows nothing about the requested table
        IMetadataLibrary lib = stubLibrary(List.of());
        Mockito.when(lib.getDataTable(Mockito.anyString())).thenReturn(Optional.empty());

        DataTableMetaSupport sup = new DataTableMetaSupport(lib);
        sup.setTable(URI.create("file:///x.csv"), "UNKNOWN");

        assertNull(sup.getTableMeta().build().getLabel());
    }


    @Test
    void getTableNameFor_pullsFromMetaTableNameWhenAvailable()
    {
        // URI has neither a fragment nor a usable path → falls back to metaTable.getName().
        URI uri = URI.create("urn:something:opaque");
        IDataTableMetadata dt = stubTableMeta("FROMMETA", null, uri, List.of(), Set.of(), null);
        IMetadataLibrary lib = stubLibrary(List.of(dt));
        Mockito.when(lib.getDataTable("FROMMETA")).thenReturn(Optional.of(dt));

        DataTableMetaSupport sup = new DataTableMetaSupport(lib);
        // explicit name omitted → getTableNameFor inspects fragment (none), then
        // metaTable.getName()
        sup.setTable(uri);

        assertEquals("FROMMETA", sup.getTableMeta().build().getName());
    }


    @Test
    void getTableNameFor_nullUriReturnsEmpty()
    {
        DataTableMetaSupport sup = new DataTableMetaSupport(null);
        sup.setTable(null, null);
        assertEquals("", sup.getTableMeta().build().getName());
    }

    // ==================== addColumn: metadata-driven column pre-fill ====================


    @Test
    void addColumn_pullsMetaFromMetadataLibrary()
    {
        IColumnMetadata cm = stubColumnMeta("USUBJID", "Unique Subject ID", "$CHAR20.", 20, 0,
                Set.of("Origin"), "Predecessor");
        IDataTableMetadata dt = stubTableMeta("DM", "DM Label", URI.create("file:///data/dm.xpt"),
                List.of(cm), Set.of(), null);
        Mockito.when(dt.getColumn("USUBJID")).thenReturn(Optional.of(cm));
        IMetadataLibrary lib = stubLibrary(List.of(dt));

        DataTableMetaSupport sup = new DataTableMetaSupport(lib);
        sup.setTable(URI.create("file:///data/dm.xpt"), null);

        sup.addColumn("USUBJID", DataValueType.STRING);
        DataTableColumnMeta[] cols = sup.getTableColumns();
        assertEquals(1, cols.length);
        assertEquals("Unique Subject ID", cols[0].getLabel());
        assertEquals("$CHAR20.", cols[0].getDisplayFormat());
        assertEquals(20, cols[0].getLength());
        // Origin is copied through addMetaData
        assertEquals("Predecessor", cols[0].getMetaData("Origin"));
    }


    @Test
    void addColumn_explicitColumnMetaWinsOverLibraryLookup()
    {
        // library returns a meta with one label; explicit parameter passes a different one.
        IColumnMetadata fromLib = stubColumnMeta("AGE", "OLD LABEL", null, 0, 0, Set.of(), null);
        IDataTableMetadata dt = stubTableMeta("DM", null, URI.create("file:///data/dm.xpt"),
                List.of(fromLib), Set.of(), null);
        Mockito.when(dt.getColumn("AGE")).thenReturn(Optional.of(fromLib));
        IMetadataLibrary lib = stubLibrary(List.of(dt));

        IColumnMetadata explicit = stubColumnMeta("AGE", "EXPLICIT", null, 0, 0, Set.of(), null);

        DataTableMetaSupport sup = new DataTableMetaSupport(lib);
        sup.setTable(URI.create("file:///data/dm.xpt"), null);
        sup.addColumn("AGE", DataValueType.LONG, explicit);

        // explicit wins
        assertEquals("EXPLICIT", sup.getTableColumns()[0].getLabel());
    }


    @Test
    void addColumn_lengthZeroDoesNotOverwrite()
    {
        IColumnMetadata cm = stubColumnMeta("FOO", null, null, 0, 0, Set.of(), null);
        IDataTableMetadata dt = stubTableMeta("X", null, URI.create("file:///data/x.csv"),
                List.of(cm), Set.of(), null);
        Mockito.when(dt.getColumn("FOO")).thenReturn(Optional.of(cm));
        IMetadataLibrary lib = stubLibrary(List.of(dt));

        DataTableMetaSupport sup = new DataTableMetaSupport(lib);
        sup.setTable(URI.create("file:///data/x.csv"), null);
        sup.addColumn("FOO", DataValueType.STRING);

        // length 0 was skipped → default 0 still
        assertEquals(0, sup.getTableColumns()[0].getLength());
    }

    // ==================== helpers ====================


    private static IDataTableMetadata stubTableMeta(String name, String label, URI uri,
            List<IColumnMetadata> cols, Set<String> metaKeys, Object metaValue)
    {
        IDataTableMetadata dt = Mockito.mock(IDataTableMetadata.class);
        Mockito.when(dt.getName()).thenReturn(name);
        Mockito.when(dt.getLabel()).thenReturn(label);
        Mockito.when(dt.getTableURI()).thenReturn(uri);
        Mockito.when(dt.getColumns()).thenReturn(cols);
        Mockito.when(dt.getMetaKeys()).thenReturn(metaKeys);
        for (String k : metaKeys)
        {
            Mockito.when(dt.getMetaValue(k)).thenReturn(Optional.of(metaValue));
        }
        return dt;
    }


    private static IColumnMetadata stubColumnMeta(String name, String label, String displayFormat,
            int length, int keySequence, Set<String> metaKeys, Object metaValue)
    {
        IColumnMetadata cm = Mockito.mock(IColumnMetadata.class);
        Mockito.when(cm.getName()).thenReturn(name);
        Mockito.when(cm.getLabel()).thenReturn(label);
        Mockito.when(cm.getDisplayFormat()).thenReturn(displayFormat);
        Mockito.when(cm.getLength()).thenReturn(length);
        Mockito.when(cm.getKeySequence()).thenReturn(keySequence);
        Mockito.when(cm.getMetaKeys()).thenReturn(metaKeys);
        for (String k : metaKeys)
        {
            Mockito.when(cm.getMetaValue(k)).thenReturn(Optional.of(metaValue));
        }
        return cm;
    }


    private static IMetadataLibrary stubLibrary(List<IDataTableMetadata> tables)
    {
        IMetadataLibrary lib = Mockito.mock(IMetadataLibrary.class);
        Mockito.when(lib.getDataTables()).thenReturn(tables);
        // sanity assertion to keep SpotBugs happy about unread fields after Mockito init.
        assertSame(lib, lib);
        return lib;
    }

    // ==================== F-A12: URI canonicalisation in findTableByUri ====================


    /**
     * F-A12: a metadata entry stored under {@code file:///path/with%20space/dm.xpt} must still
     * match when the lookup URI uses the equivalent literal-space form, and vice-versa.
     */
    @Test
    void findTableByUri_fileSchemeIgnoresPercentEncodedSpace()
    {
        URI encoded = URI.create("file:///data/has%20space/dm.xpt");
        URI literal = java.nio.file.Path.of("/data/has space/dm.xpt").toUri();

        IDataTableMetadata dt = stubTableMeta("DM", "DM Label", encoded, List.of(), Set.of(), null);
        IMetadataLibrary lib = stubLibrary(List.of(dt));

        DataTableMetaSupport sup = new DataTableMetaSupport(lib);
        sup.setTable(literal);

        assertEquals("DM Label", sup.getTableMeta().build().getLabel(),
                "URI lookup must canonicalise %20 vs literal space");
    }


    /**
     * F-A12: relative path segments ({@code /a/./b} vs {@code /a/b}) must not block the match.
     */
    @Test
    void findTableByUri_fileSchemeNormalisesDotSegments()
    {
        URI stored = URI.create("file:///data/dm.xpt");
        URI lookup = URI.create("file:///data/./dm.xpt");

        IDataTableMetadata dt = stubTableMeta("DM", "DM Label", stored, List.of(), Set.of(), null);
        IMetadataLibrary lib = stubLibrary(List.of(dt));

        DataTableMetaSupport sup = new DataTableMetaSupport(lib);
        sup.setTable(lookup);

        assertEquals("DM Label", sup.getTableMeta().build().getLabel());
    }


    /**
     * F-A12: non-{@code file} URIs go through {@link URI#normalize()}; same-after-normalisation
     * matches.
     */
    @Test
    void findTableByUri_nonFileSchemeUsesNormalize()
    {
        URI stored = URI.create("https://example.test/api/v1/datasets/dm");
        URI lookup = URI.create("https://example.test/api/v1/foo/../datasets/dm");

        IDataTableMetadata dt = stubTableMeta("DM", "DM Label", stored, List.of(), Set.of(), null);
        IMetadataLibrary lib = stubLibrary(List.of(dt));

        DataTableMetaSupport sup = new DataTableMetaSupport(lib);
        sup.setTable(lookup);

        assertEquals("DM Label", sup.getTableMeta().build().getLabel());
    }


    /**
     * F-A12: when the canonicalisation throws (e.g. URI references a non-existent filesystem
     * provider), we fall through to {@link URI#normalize()} comparison without crashing the lookup.
     */
    @Test
    void findTableByUri_unknownFsProviderFallsThroughToNormalize()
    {
        URI weird = URI.create("file://server/share/dm.xpt"); // host-style file URI
        IDataTableMetadata dt = stubTableMeta("DM", "DM Label", weird, List.of(), Set.of(), null);
        IMetadataLibrary lib = stubLibrary(List.of(dt));

        DataTableMetaSupport sup = new DataTableMetaSupport(lib);
        // The same URI as needle. Whether Path.of(URI) throws or returns a valid Path is
        // platform-dependent — either way the equality check must succeed.
        sup.setTable(weird);

        assertEquals("DM Label", sup.getTableMeta().build().getLabel());
    }
}
