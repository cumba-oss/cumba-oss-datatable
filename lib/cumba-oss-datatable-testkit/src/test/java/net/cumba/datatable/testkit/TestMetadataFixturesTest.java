package net.cumba.datatable.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import net.cumba.datatable.metadata.ICodeList;
import net.cumba.datatable.metadata.ICodelistEntry;
import net.cumba.datatable.metadata.IColumnMetadata;
import net.cumba.datatable.metadata.IDataTableMetadata;
import net.cumba.datatable.metadata.IMetadataLibrary;
import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.Test;

/**
 * Behavioural pins for {@link TestMetadataFixtures}.
 *
 * <p>
 * <b>Every assertion checks a VALUE arriving at its matching getter, never merely non-null.</b>
 * These are fluent builders facing a mutation gate that is first exercised in CI; a happy-path
 * non-null assertion would let every "this setter stored nothing" mutant survive. Each builder
 * setter therefore gets both a set case and an unset (null / empty default) case.
 * </p>
 */
class TestMetadataFixturesTest
{

    // ---- LibBuilder --------------------------------------------------

    @Test
    void lib_nameAndVersionRoundTrip()
    {
        IMetadataLibrary lib = TestMetadataFixtures.lib("SDTMIG").version("3.4").build();

        assertEquals("SDTMIG", lib.getName());
        assertEquals("3.4", lib.getVersion());
    }


    @Test
    void lib_unsetVersionIsNull()
    {
        assertNull(TestMetadataFixtures.lib("SDTMIG").build().getVersion());
    }


    @Test
    void lib_isNeverColumnNameCaseSensitive()
    {
        assertFalse(TestMetadataFixtures.lib("L").build().isColumnNameCaseSensitive());
    }


    @Test
    void lib_tablesAccumulateInDeclarationOrder()
    {
        IDataTableMetadata dm = TestMetadataFixtures.table("DM").build();
        IDataTableMetadata ae = TestMetadataFixtures.table("AE").build();
        IMetadataLibrary lib = TestMetadataFixtures.lib("L").table(dm).table(ae).build();

        assertEquals(2, lib.getDataTables().size());
        assertSame(dm, lib.getDataTables().get(0));
        assertSame(ae, lib.getDataTables().get(1));
    }


    @Test
    void lib_getDataTableIsPresentCaseInsensitivelyAndEmptyOtherwise()
    {
        IMetadataLibrary lib = TestMetadataFixtures.lib("L")
                .table(TestMetadataFixtures.table("DM").build()).build();

        assertEquals("DM", lib.getDataTable("dm").orElseThrow().getName());
        assertEquals("DM", lib.getDataTable("DM").orElseThrow().getName());
        assertTrue(lib.getDataTable("AE").isEmpty());
    }


    @Test
    void lib_getCodelistIsPresentCaseSensitivelyAndEmptyOtherwise()
    {
        IMetadataLibrary lib = TestMetadataFixtures.lib("L")
                .codelist(TestMetadataFixtures.codelist("NY").build()).build();

        assertEquals("NY", lib.getCodelist("NY").orElseThrow().getName());
        assertTrue(lib.getCodelist("ny").isEmpty());
        assertEquals(1, lib.getCodelists().size());
    }


    @Test
    void lib_emptyLibraryHasNoTablesOrCodelists()
    {
        IMetadataLibrary lib = TestMetadataFixtures.lib("L").build();

        assertEquals(List.of(), lib.getDataTables());
        assertEquals(List.of(), lib.getCodelists());
        assertEquals(Set.of(), lib.getMetaKeys());
    }


    @Test
    void lib_metaRoundTripsAndUnknownKeyIsEmpty()
    {
        IMetadataLibrary lib = TestMetadataFixtures.lib("L").meta("origin", "CDISC")
                .meta("year", 2026).build();

        assertEquals(Set.of("origin", "year"), lib.getMetaKeys());
        assertEquals("CDISC", lib.getMetaValue("origin").orElseThrow());
        assertEquals(2026, lib.getMetaValue("year").orElseThrow());
        assertTrue(lib.getMetaValue("absent").isEmpty());
    }

    // ---- TableBuilder ------------------------------------------------


    @Test
    void table_everySetterReachesItsGetter()
    {
        IDataTableMetadata t = TestMetadataFixtures.table("ADSL").label("Subject Level")
                .className("ADAM OTHER").subClassNames("BDS", "OCCDS")
                .structure("One row per USUBJID").build();

        assertEquals("ADSL", t.getName());
        assertEquals("Subject Level", t.getLabel());
        assertEquals("ADAM OTHER", t.getClassName());
        assertEquals(List.of("BDS", "OCCDS"), t.getSubClassNames());
        assertEquals("One row per USUBJID", t.getStructure());
    }


    @Test
    void table_unsetOptionalsAreNullAndSubClassNamesDefaultEmpty()
    {
        IDataTableMetadata t = TestMetadataFixtures.table("DM").build();

        assertNull(t.getLabel());
        assertNull(t.getClassName());
        assertNull(t.getStructure());
        assertNull(t.getTableURI());
        assertEquals(List.of(), t.getSubClassNames());
        assertEquals(List.of(), t.getColumns());
    }


    @Test
    void table_tableUriIsAlwaysNull()
    {
        // Pinned deliberately: the fixture models library metadata, which has no source file.
        assertNull(TestMetadataFixtures.table("DM").label("x").build().getTableURI());
    }


    @Test
    void table_columnsAccumulateInDeclarationOrder()
    {
        IColumnMetadata a = TestMetadataFixtures.column("USUBJID", 0, DataValueType.STRING).build();
        IColumnMetadata b = TestMetadataFixtures.column("AGE", 1, DataValueType.LONG).build();
        IDataTableMetadata t = TestMetadataFixtures.table("DM").column(a).column(b).build();

        assertEquals(2, t.getColumns().size());
        assertSame(a, t.getColumns().get(0));
        assertSame(b, t.getColumns().get(1));
    }


    @Test
    void table_getColumnIsPresentCaseInsensitivelyAndEmptyOtherwise()
    {
        IDataTableMetadata t = TestMetadataFixtures.table("DM")
                .column(TestMetadataFixtures.column("USUBJID", 0, DataValueType.STRING).build())
                .build();

        assertEquals(0, t.getColumn("usubjid").orElseThrow().getIndex());
        assertEquals("USUBJID", t.getColumn("USUBJID").orElseThrow().getName());
        assertTrue(t.getColumn("NOPE").isEmpty());
    }


    @Test
    void table_metaRoundTripsAndUnknownKeyIsEmpty()
    {
        IDataTableMetadata t = TestMetadataFixtures.table("DM").meta("k", "v").build();

        assertEquals(Set.of("k"), t.getMetaKeys());
        assertEquals("v", t.getMetaValue("k").orElseThrow());
        assertTrue(t.getMetaValue("other").isEmpty());
    }


    @Test
    void table_subClassNamesOverwritesRatherThanAppends()
    {
        IDataTableMetadata t = TestMetadataFixtures.table("DM").subClassNames("A")
                .subClassNames("B", "C").build();

        assertEquals(List.of("B", "C"), t.getSubClassNames());
    }

    // ---- ColumnBuilder -----------------------------------------------


    @Test
    void column_everySetterReachesItsGetter()
    {
        IColumnMetadata c = TestMetadataFixtures.column("RACE", 4, DataValueType.STRING)
                .label("Race").core("Exp").role("Qualifier").codelist("RACE").length(40).build();

        assertEquals("RACE", c.getName());
        assertEquals(4, c.getIndex());
        assertEquals(DataValueType.STRING, c.getType());
        assertEquals("Race", c.getLabel());
        assertEquals("Exp", c.getCore());
        assertEquals("Qualifier", c.getRole());
        assertEquals("RACE", c.getCodelist());
        assertEquals(40, c.getLength());
    }


    @Test
    void column_unsetOptionalsAreNullOrZero()
    {
        IColumnMetadata c = TestMetadataFixtures.column("AGE", 2, DataValueType.LONG).build();

        assertNull(c.getLabel());
        assertNull(c.getCore());
        assertNull(c.getRole());
        assertNull(c.getCodelist());
        assertEquals(0, c.getLength());
    }


    @Test
    void column_displayFormatNativeTypeKeySequenceAndByGroupArePinnedDefaults()
    {
        IColumnMetadata c = TestMetadataFixtures.column("AGE", 2, DataValueType.LONG).length(8)
                .build();

        assertNull(c.getDisplayFormat());
        assertNull(c.getNativeType());
        assertEquals(0, c.getKeySequence());
        assertFalse(c.isByGroup());
    }


    @Test
    void column_indexAndTypeAreTakenFromTheFactoryArguments()
    {
        IColumnMetadata c = TestMetadataFixtures.column("X", 7, DataValueType.DOUBLE).build();

        assertEquals(7, c.getIndex());
        assertEquals(DataValueType.DOUBLE, c.getType());
    }


    @Test
    void column_metaRoundTripsAndUnknownKeyIsEmpty()
    {
        IColumnMetadata c = TestMetadataFixtures.column("X", 0, DataValueType.STRING)
                .meta("origin", "CRF").build();

        assertEquals(Set.of("origin"), c.getMetaKeys());
        assertEquals("CRF", c.getMetaValue("origin").orElseThrow());
        assertTrue(c.getMetaValue("nope").isEmpty());
    }

    // ---- CodelistBuilder ---------------------------------------------


    @Test
    void codelist_everySetterReachesItsGetter()
    {
        ICodeList cl = TestMetadataFixtures.codelist("NY").extensible(true).entry("Y", "Yes")
                .entry("N", "No").build();

        assertEquals("NY", cl.getName());
        assertEquals(true, cl.isExtensible());
        assertEquals(2, cl.getEntries().size());
        assertEquals("Y", cl.getEntries().get(0).getCodeValue());
        assertEquals("No", cl.getEntries().get(1).getDecodeValue());
    }


    @Test
    void codelist_unsetExtensibleIsNullAndDefaultTypeIsString()
    {
        ICodeList cl = TestMetadataFixtures.codelist("NY").build();

        assertNull(cl.isExtensible());
        assertEquals(DataValueType.STRING, cl.getValueType());
        assertEquals(List.of(), cl.getEntries());
    }


    @Test
    void codelist_extensibleFalseIsDistinctFromUnset()
    {
        assertEquals(false,
                TestMetadataFixtures.codelist("NY").extensible(false).build().isExtensible());
    }


    @Test
    void codelist_entryOverloadCarriesTheConceptId()
    {
        ICodeList cl = TestMetadataFixtures.codelist("RACE").entry("C41260", "ASIAN", "C41260")
                .build();
        ICodelistEntry e = cl.getEntries().get(0);

        assertEquals("C41260", e.getCodeValue());
        assertEquals("ASIAN", e.getDecodeValue());
        assertEquals("C41260", e.getConceptId());
    }


    @Test
    void codelist_metaRoundTripsAndUnknownKeyIsEmpty()
    {
        ICodeList cl = TestMetadataFixtures.codelist("NY").meta("src", "CT").build();

        assertEquals(Set.of("src"), cl.getMetaKeys());
        assertEquals("CT", cl.getMetaValue("src").orElseThrow());
        assertTrue(cl.getMetaValue("nope").isEmpty());
    }


    @Test
    void codelist_prebuiltEntryIsStoredAsGiven()
    {
        ICodelistEntry e = TestMetadataFixtures.entry("Y", "Yes");
        ICodeList cl = TestMetadataFixtures.codelist("NY").entry(e).build();

        assertSame(e, cl.getEntries().get(0));
    }

    // ---- entry(...) --------------------------------------------------


    @Test
    void entry_twoArgOverloadLeavesTheConceptIdNull()
    {
        // Pinned because a fixture built this way yields an EMPTY code map from
        // getCodelistCodeMap, which makes any test of that accessor pass vacuously.
        ICodelistEntry e = TestMetadataFixtures.entry("Y", "Yes");

        assertEquals("Y", e.getCodeValue());
        assertEquals("Yes", e.getDecodeValue());
        assertNull(e.getConceptId());
    }


    @Test
    void entry_threeArgOverloadCarriesAllThree()
    {
        ICodelistEntry e = TestMetadataFixtures.entry("N", "No", "C49487");

        assertEquals("N", e.getCodeValue());
        assertEquals("No", e.getDecodeValue());
        assertEquals("C49487", e.getConceptId());
    }

    // ---- builder identity --------------------------------------------


    @Test
    void builderSettersReturnTheSameInstanceSoChainsAccumulate()
    {
        TestMetadataFixtures.LibBuilder lb = TestMetadataFixtures.lib("L");
        assertSame(lb, lb.version("1.0"));
        assertSame(lb, lb.meta("k", "v"));

        TestMetadataFixtures.TableBuilder tb = TestMetadataFixtures.table("T");
        assertSame(tb, tb.label("l"));
        assertSame(tb, tb.className("c"));
        assertSame(tb, tb.structure("s"));
        assertSame(tb, tb.subClassNames("x"));
        assertSame(tb, tb.meta("k", "v"));

        TestMetadataFixtures.ColumnBuilder cb = TestMetadataFixtures.column("C", 0,
                DataValueType.STRING);
        assertSame(cb, cb.label("l"));
        assertSame(cb, cb.core("Req"));
        assertSame(cb, cb.role("r"));
        assertSame(cb, cb.codelist("cl"));
        assertSame(cb, cb.length(3));
        assertSame(cb, cb.meta("k", "v"));

        TestMetadataFixtures.CodelistBuilder clb = TestMetadataFixtures.codelist("CL");
        assertSame(clb, clb.extensible(true));
        assertSame(clb, clb.entry("A", "a"));
        assertSame(clb, clb.entry("B", "b", "C1"));
        assertSame(clb, clb.meta("k", "v"));
    }

    // ---- a whole library ---------------------------------------------


    @Test
    void aFullyPopulatedLibraryResolvesEndToEnd()
    {
        IMetadataLibrary lib = TestMetadataFixtures.lib("SDTMIG").version("3.4")
                .table(TestMetadataFixtures.table("DM").label("Demographics")
                        .column(TestMetadataFixtures.column("USUBJID", 0, DataValueType.STRING)
                                .core("Req").length(20).build())
                        .build())
                .codelist(TestMetadataFixtures.codelist("NY").extensible(false)
                        .entry("Y", "Yes", "C49488").build())
                .build();

        IDataTableMetadata dm = lib.getDataTable("DM").orElseThrow();
        IColumnMetadata usubjid = dm.getColumn("USUBJID").orElseThrow();
        ICodeList ny = lib.getCodelist("NY").orElseThrow();

        assertEquals("3.4", lib.getVersion());
        assertEquals("Demographics", dm.getLabel());
        assertEquals("Req", usubjid.getCore());
        assertEquals(20, usubjid.getLength());
        assertEquals(false, ny.isExtensible());
        assertEquals("C49488", ny.getEntries().get(0).getConceptId());
    }

    // ---- immutability ------------------------------------------------


    @Test
    void everyReturnedCollectionIsImmutable()
    {
        // The builders pass List.copyOf(...) / Map.copyOf(...) into the records, and the module's
        // spotbugs_ignore.xml suppresses EI_EXPOSE_REP on exactly that basis. Nothing asserted it,
        // so replacing a copyOf with the raw list in any builder was a surviving mutant AND would
        // have quietly invalidated the suppression. This pins the property the suppression claims.
        IMetadataLibrary lib = TestMetadataFixtures.lib("L")
                .table(TestMetadataFixtures.table("DM").subClassNames("BDS")
                        .column(TestMetadataFixtures.column("USUBJID", 0, DataValueType.STRING)
                                .build())
                        .build())
                .codelist(TestMetadataFixtures.codelist("NY").entry("Y", "Yes").build()).build();
        IDataTableMetadata dm = lib.getDataTable("DM").orElseThrow();
        ICodeList ny = lib.getCodelist("NY").orElseThrow();

        assertThrows(UnsupportedOperationException.class, () -> lib.getDataTables().clear());
        assertThrows(UnsupportedOperationException.class, () -> lib.getCodelists().clear());
        assertThrows(UnsupportedOperationException.class, () -> dm.getColumns().clear());
        assertThrows(UnsupportedOperationException.class, () -> dm.getSubClassNames().clear());
        assertThrows(UnsupportedOperationException.class, () -> ny.getEntries().clear());
    }

}
