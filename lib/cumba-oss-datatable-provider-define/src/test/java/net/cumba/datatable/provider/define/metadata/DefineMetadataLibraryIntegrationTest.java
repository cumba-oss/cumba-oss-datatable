package net.cumba.datatable.provider.define.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.cumba.cdisc.define.CodeList;
import net.cumba.cdisc.define.CodeListItem;
import net.cumba.cdisc.define.CodeListRef;
import net.cumba.cdisc.define.CommentDef;
import net.cumba.cdisc.define.Decode;
import net.cumba.cdisc.define.DefineSupport;
import net.cumba.cdisc.define.Description;
import net.cumba.cdisc.define.GlobalVariables;
import net.cumba.cdisc.define.ItemDef;
import net.cumba.cdisc.define.ItemGroupDef;
import net.cumba.cdisc.define.ItemRef;
import net.cumba.cdisc.define.Leaf;
import net.cumba.cdisc.define.MetaDataVersion;
import net.cumba.cdisc.define.MethodDef;
import net.cumba.cdisc.define.ODM;
import net.cumba.cdisc.define.Origin;
import net.cumba.cdisc.define.Standard;
import net.cumba.cdisc.define.Standards;
import net.cumba.cdisc.define.Study;
import net.cumba.cdisc.define.TranslatedText;
import net.cumba.datatable.impl.provider.DataTableMetaSupport;
import net.cumba.datatable.metadata.IColumnMetadata;
import net.cumba.datatable.metadata.IDataTableMetadata;
import net.cumba.datatable.metadata.IMetadataLibrary;
import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Integration tests for {@link DefineMetadataLibrary}, exercising:
 * <ul>
 * <li>name + standard / version / define-version meta values</li>
 * <li>CT-package id assembly from {@code Standard} elements</li>
 * <li>{@code DefineDataTableMetadata} columns, label, tableURI, comment, structure</li>
 * <li>{@code DefineColumnMetadata} type mapping, code-list reference, display format, core, role,
 * key sequence, method, origin</li>
 * </ul>
 */
class DefineMetadataLibraryIntegrationTest
{

    private static final URI BASE_URI = URI.create("file:///study/define.xml");

    // ==================== Builders ====================

    private static Description description(String aText)
    {
        Description d = new Description();
        TranslatedText tt = new TranslatedText();
        tt.setValue(aText);
        d.setTranslatedTexts(List.of(tt));
        return d;
    }


    private static Decode decode(String aText)
    {
        Decode d = new Decode();
        TranslatedText tt = new TranslatedText();
        tt.setValue(aText);
        d.setTranslatedTexts(List.of(tt));
        return d;
    }


    private static IMetadataLibrary buildLibrary(MetaDataVersion aMdv, GlobalVariables aGv)
    {
        Study study = Study.builder().oid("S.1").globalVariables(aGv)
                .metaDataVersions(List.of(aMdv)).build();
        ODM odm = ODM.builder().fileOID("f1").studies(List.of(study)).build();
        DefineSupport support = new DefineSupport(BASE_URI, odm);
        return DefineMetadataLibrary.from(support);
    }

    // ==================== name / standardName / standardVersion / defineVersion
    // ====================


    @Test
    void name_takenFromStudyName_whenPresent()
    {
        GlobalVariables gv = GlobalVariables.builder().studyName("MY-STUDY").build();
        MetaDataVersion mdv = MetaDataVersion.builder().oid("M.1").name("V1")
                .standardName("SDTM-IG").standardVersion("3.4").defineVersion("2.1.0").build();

        IMetadataLibrary lib = buildLibrary(mdv, gv);

        assertEquals("MY-STUDY", lib.getName());
        assertEquals("SDTM-IG", lib.getVersion());
    }


    @Test
    void name_defaultsToUnknown_whenStudyNameMissing()
    {
        MetaDataVersion mdv = MetaDataVersion.builder().oid("M.1").name("V1").build();
        IMetadataLibrary lib = buildLibrary(mdv, null);

        assertEquals("<unknown>", lib.getName());
    }


    @Test
    void name_unknown_whenNoStudies()
    {
        ODM odm = ODM.builder().fileOID("f1").build();
        DefineSupport support = new DefineSupport(BASE_URI, odm);
        IMetadataLibrary lib = DefineMetadataLibrary.from(support);

        assertEquals("<unknown>", lib.getName());
    }


    @Test
    void caseInsensitiveColumnNames_byDefault()
    {
        MetaDataVersion mdv = MetaDataVersion.builder().oid("M.1").name("V1").build();
        IMetadataLibrary lib = buildLibrary(mdv, null);

        assertFalse(lib.isColumnNameCaseSensitive());
    }


    @Test
    void metaValue_lookup_byKnownKeys()
    {
        GlobalVariables gv = GlobalVariables.builder().studyName("S").build();
        MetaDataVersion mdv = MetaDataVersion.builder().oid("M.1").name("V1")
                .standardName("SDTM-IG").standardVersion("3.4").defineVersion("2.1.0").build();

        IMetadataLibrary lib = buildLibrary(mdv, gv);

        assertEquals("SDTM-IG",
                lib.getMetaValue(IMetadataLibrary.META_KEY_STANDARD_NAME).orElseThrow());
        assertEquals("3.4",
                lib.getMetaValue(IMetadataLibrary.META_KEY_STANDARD_VERSION).orElseThrow());
        assertEquals("2.1.0",
                lib.getMetaValue(IMetadataLibrary.META_KEY_DEFINE_VERSION).orElseThrow());
        assertEquals(BASE_URI.toString(),
                lib.getMetaValue(IMetadataLibrary.META_KEY_SOURCE_URI).orElseThrow());
        assertTrue(lib.getMetaValue("unknown-key").isEmpty());
    }


    @Test
    void getMetaKeys_includesPopulatedFieldsOnly()
    {
        GlobalVariables gv = GlobalVariables.builder().studyName("S").build();
        // only standardName is populated
        MetaDataVersion mdv = MetaDataVersion.builder().oid("M.1").name("V1")
                .standardName("SDTM-IG").build();
        IMetadataLibrary lib = buildLibrary(mdv, gv);

        Set<String> keys = lib.getMetaKeys();
        assertTrue(keys.contains(IMetadataLibrary.META_KEY_STANDARD_NAME));
        assertFalse(keys.contains(IMetadataLibrary.META_KEY_STANDARD_VERSION));
        assertFalse(keys.contains(IMetadataLibrary.META_KEY_DEFINE_VERSION));
        assertTrue(keys.contains(IMetadataLibrary.META_KEY_SOURCE_URI));
    }

    // ==================== Ct-package id assembly ====================


    @Test
    void ctPackages_assembledFromStandards()
    {
        GlobalVariables gv = GlobalVariables.builder().studyName("S").build();
        Standard ct1 = Standard.builder().oid("STD.1").name("SDTM-CT").type("CT")
                .publishingSet("SDTM").version("2022-03-25").build();
        Standard ct2 = Standard.builder().oid("STD.2").name("ADaM-CT").type("CT")
                .publishingSet("ADAM").version("2022-09-30").build();
        // not-CT standard should be ignored
        Standard ig = Standard.builder().oid("STD.IG").name("SDTM-IG").type("IG")
                .publishingSet("SDTM").version("3.4").build();
        Standards stds = Standards.builder().standards(List.of(ct1, ig, ct2)).build();
        MetaDataVersion mdv = MetaDataVersion.builder().oid("M.1").name("V1").standards(stds)
                .build();

        IMetadataLibrary lib = buildLibrary(mdv, gv);

        Optional<Object> ctp = lib.getMetaValue(IMetadataLibrary.META_KEY_CT_PACKAGES);
        assertTrue(ctp.isPresent());
        String ids = (String) ctp.orElseThrow();
        assertTrue(ids.contains("sdtmct-2022-03-25"), ids);
        assertTrue(ids.contains("adamct-2022-09-30"), ids);
        assertFalse(ids.contains("ig"));
        assertTrue(lib.getMetaKeys().contains(IMetadataLibrary.META_KEY_CT_PACKAGES));
    }


    @Test
    void ctPackages_skipsStandardsWithBlankPublishingSetOrVersion()
    {
        GlobalVariables gv = GlobalVariables.builder().studyName("S").build();
        // CT-type but no publishingSet
        Standard bad1 = Standard.builder().oid("STD.B1").type("CT").version("2022").build();
        // CT-type but no version
        Standard bad2 = Standard.builder().oid("STD.B2").type("CT").publishingSet("SDTM").build();
        Standards stds = Standards.builder().standards(List.of(bad1, bad2)).build();
        MetaDataVersion mdv = MetaDataVersion.builder().oid("M.1").name("V1").standards(stds)
                .build();

        IMetadataLibrary lib = buildLibrary(mdv, gv);
        assertTrue(lib.getMetaValue(IMetadataLibrary.META_KEY_CT_PACKAGES).isEmpty());
        assertFalse(lib.getMetaKeys().contains(IMetadataLibrary.META_KEY_CT_PACKAGES));
    }


    @Test
    void ctPackages_absentWhenNoStandards()
    {
        MetaDataVersion mdv = MetaDataVersion.builder().oid("M.1").name("V1").build();
        IMetadataLibrary lib = buildLibrary(mdv, null);

        assertTrue(lib.getMetaValue(IMetadataLibrary.META_KEY_CT_PACKAGES).isEmpty());
    }

    // ==================== DefineDataTableMetadata ====================


    @Test
    void dataTable_nameLabelTableUriClassStructure()
    {
        ItemGroupDef ig = ItemGroupDef.builder().oid("IG.DM").name("DM").domain("DM")
                .label("Demographics").clazz("SPECIAL PURPOSE").structure("One per subject")
                .leaf(Leaf.builder().id("L.DM").href("dm.xpt").build()).build();
        MetaDataVersion mdv = MetaDataVersion.builder().oid("M.1").name("V1")
                .itemGroupDefs(List.of(ig)).build();
        IMetadataLibrary lib = buildLibrary(mdv, null);

        IDataTableMetadata dt = lib.getDataTable("DM").orElseThrow();
        assertEquals("DM", dt.getName());
        assertEquals("Demographics", dt.getLabel());
        assertEquals(BASE_URI.resolve("dm.xpt"), dt.getTableURI());
        assertEquals("SPECIAL PURPOSE", dt.getClassName());
        assertEquals("One per subject", dt.getStructure());
    }


    @Test
    void dataTable_label_fallsBackToDescription_whenLabelBlank()
    {
        ItemGroupDef ig = ItemGroupDef.builder().oid("IG.DM").name("DM").domain("DM")
                .description(description("Demog-from-description")).build();
        MetaDataVersion mdv = MetaDataVersion.builder().oid("M.1").name("V1")
                .itemGroupDefs(List.of(ig)).build();
        IMetadataLibrary lib = buildLibrary(mdv, null);

        IDataTableMetadata dt = lib.getDataTable("DM").orElseThrow();
        assertEquals("Demog-from-description", dt.getLabel());
    }


    @Test
    void dataTable_tableUri_nullWhenLeafAbsent()
    {
        ItemGroupDef ig = ItemGroupDef.builder().oid("IG.X").name("X").build();
        MetaDataVersion mdv = MetaDataVersion.builder().oid("M.1").name("V1")
                .itemGroupDefs(List.of(ig)).build();
        IMetadataLibrary lib = buildLibrary(mdv, null);

        assertNull(lib.getDataTable("X").orElseThrow().getTableURI());
    }


    @Test
    void dataTable_tableUri_nullWhenLeafHrefMissing()
    {
        ItemGroupDef ig = ItemGroupDef.builder().oid("IG.Y").name("Y")
                .leaf(Leaf.builder().id("L.Y").build()).build();
        MetaDataVersion mdv = MetaDataVersion.builder().oid("M.1").name("V1")
                .itemGroupDefs(List.of(ig)).build();
        IMetadataLibrary lib = buildLibrary(mdv, null);

        assertNull(lib.getDataTable("Y").orElseThrow().getTableURI());
    }


    @Test
    void dataTable_emptyColumns_whenNoItemRefs()
    {
        ItemGroupDef ig = ItemGroupDef.builder().oid("IG.Z").name("Z").build();
        MetaDataVersion mdv = MetaDataVersion.builder().oid("M.1").name("V1")
                .itemGroupDefs(List.of(ig)).build();
        IMetadataLibrary lib = buildLibrary(mdv, null);

        assertEquals(0, lib.getDataTable("Z").orElseThrow().getColumns().size());
    }


    @Test
    void dataTable_columnLookup_byCaseInsensitiveName()
    {
        ItemDef name = ItemDef.builder().oid("I.NAME").name("USUBJID").dataType("text").build();
        ItemRef ref = ItemRef.builder().itemOID("I.NAME").orderNumber(1).build();
        ItemGroupDef ig = ItemGroupDef.builder().oid("IG.DM").name("DM").itemRefs(List.of(ref))
                .build();
        MetaDataVersion mdv = MetaDataVersion.builder().oid("M.1").name("V1")
                .itemGroupDefs(List.of(ig)).itemDefs(List.of(name)).build();
        IMetadataLibrary lib = buildLibrary(mdv, null);

        IDataTableMetadata dt = lib.getDataTable("DM").orElseThrow();
        assertTrue(dt.getColumn("usubjid").isPresent());
        assertTrue(dt.getColumn("USUBJID").isPresent());
        assertTrue(dt.getColumn("nonexistent").isEmpty());
    }


    @Test
    void dataTable_metaMap_collectsAllPopulatedFields()
    {
        Standard std = Standard.builder().oid("STD.1").name("SDTM-IG").type("IG").version("3.4")
                .build();
        Standards stds = Standards.builder().standards(List.of(std)).build();

        ItemGroupDef ig = ItemGroupDef.builder().oid("IG.DM").name("DM")
                .structure("One per subject").repeating("No").purpose("Tabulation")
                .sasDatasetName("DM").hasNoData("No").standardOID("STD.1").comment("inline")
                .build();
        MetaDataVersion mdv = MetaDataVersion.builder().oid("M.1").name("V1")
                .itemGroupDefs(List.of(ig)).standards(stds).build();
        IMetadataLibrary lib = buildLibrary(mdv, null);

        IDataTableMetadata dt = lib.getDataTable("DM").orElseThrow();
        assertTrue(dt.getMetaKeys().contains(DataTableMetaSupport.META_KEY_COMMENT));
        assertEquals("inline",
                dt.getMetaValue(DataTableMetaSupport.META_KEY_COMMENT).orElseThrow());
        assertEquals("Tabulation",
                dt.getMetaValue(DataTableMetaSupport.META_KEY_PURPOSE).orElseThrow());
        assertEquals("One per subject",
                dt.getMetaValue(DataTableMetaSupport.META_KEY_STRUCTURE).orElseThrow());
        assertEquals("No", dt.getMetaValue(DataTableMetaSupport.META_KEY_REPEATING).orElseThrow());
        assertEquals("DM",
                dt.getMetaValue(DataTableMetaSupport.META_KEY_SAS_DATASET_NAME).orElseThrow());
        assertEquals("No",
                dt.getMetaValue(DataTableMetaSupport.META_KEY_ITEM_NO_DATA).orElseThrow());
        // standard string formatted as "Name-Version (Type)"
        assertEquals("SDTM-IG-3.4 (IG)",
                dt.getMetaValue(DataTableMetaSupport.META_KEY_STANDARD).orElseThrow());
    }


    @Test
    void dataTable_comment_resolvesFromCommentDef_whenInlineMissing()
    {
        CommentDef cd = CommentDef.builder().oid("C.1").description(description("Comment X"))
                .build();
        ItemGroupDef ig = ItemGroupDef.builder().oid("IG.D").name("D").commentOID("C.1").build();
        MetaDataVersion mdv = MetaDataVersion.builder().oid("M.1").name("V1")
                .itemGroupDefs(List.of(ig)).commentDefs(List.of(cd)).build();
        IMetadataLibrary lib = buildLibrary(mdv, null);

        IDataTableMetadata dt = lib.getDataTable("D").orElseThrow();
        assertEquals("Comment X",
                dt.getMetaValue(DataTableMetaSupport.META_KEY_COMMENT).orElseThrow());
    }

    // ==================== DefineColumnMetadata ====================


    @ParameterizedTest
    @CsvSource(
    {
            "text,STRING", "integer,LONG", "float,DOUBLE", "datetime,STRING", "date,STRING",
            "partialDate,STRING", "partialDatetime,STRING", "durationDatetime,STRING",
            "weird,OTHER",
    })
    void column_typeMapping(String aDataType, DataValueType aExpected)
    {
        ItemDef def = ItemDef.builder().oid("I.X").name("X").dataType(aDataType).length(8).build();
        ItemRef ref = ItemRef.builder().itemOID("I.X").orderNumber(1).build();
        ItemGroupDef ig = ItemGroupDef.builder().oid("IG.D").name("D").itemRefs(List.of(ref))
                .build();
        MetaDataVersion mdv = MetaDataVersion.builder().oid("M.1").name("V1")
                .itemGroupDefs(List.of(ig)).itemDefs(List.of(def)).build();
        IMetadataLibrary lib = buildLibrary(mdv, null);

        IColumnMetadata col = lib.getDataTable("D").orElseThrow().getColumns().get(0);
        assertEquals(aExpected, col.getType());
        assertEquals(8, col.getLength());
        assertEquals(aDataType, col.getNativeType());
    }


    @Test
    void column_typeMapping_nullDataType()
    {
        ItemDef def = ItemDef.builder().oid("I.N").name("N").dataType(null).build();
        ItemRef ref = ItemRef.builder().itemOID("I.N").orderNumber(1).build();
        ItemGroupDef ig = ItemGroupDef.builder().oid("IG.D").name("D").itemRefs(List.of(ref))
                .build();
        MetaDataVersion mdv = MetaDataVersion.builder().oid("M.1").name("V1")
                .itemGroupDefs(List.of(ig)).itemDefs(List.of(def)).build();
        IMetadataLibrary lib = buildLibrary(mdv, null);

        IColumnMetadata col = lib.getDataTable("D").orElseThrow().getColumns().get(0);
        assertEquals(DataValueType.OTHER, col.getType());
        // length default is 0 when ItemDef.length is null
        assertEquals(0, col.getLength());
    }


    @Test
    void column_labelFallsBackToDescription()
    {
        ItemDef def = ItemDef.builder().oid("I.X").name("X").dataType("text")
                .description(description("From-Description")).build();
        ItemRef ref = ItemRef.builder().itemOID("I.X").orderNumber(1).build();
        ItemGroupDef ig = ItemGroupDef.builder().oid("IG.D").name("D").itemRefs(List.of(ref))
                .build();
        MetaDataVersion mdv = MetaDataVersion.builder().oid("M.1").name("V1")
                .itemGroupDefs(List.of(ig)).itemDefs(List.of(def)).build();
        IMetadataLibrary lib = buildLibrary(mdv, null);

        IColumnMetadata col = lib.getDataTable("D").orElseThrow().getColumns().get(0);
        assertEquals("From-Description", col.getLabel());
    }


    @Test
    void column_displayFormat_fromCodeList_textType()
    {
        CodeList cl = CodeList.builder().oid("CL.1").name("RACE").dataType("text")
                .codeListItems(List
                        .of(CodeListItem.builder().codedValue("W").decode(decode("White")).build()))
                .build();
        ItemDef def = ItemDef.builder().oid("I.RACE").name("RACE").dataType("text")
                .codeListRef(CodeListRef.builder().codeListOID("CL.1").build()).build();
        ItemRef ref = ItemRef.builder().itemOID("I.RACE").orderNumber(1).build();
        ItemGroupDef ig = ItemGroupDef.builder().oid("IG.DM").name("DM").itemRefs(List.of(ref))
                .build();
        MetaDataVersion mdv = MetaDataVersion.builder().oid("M.1").name("V1")
                .itemGroupDefs(List.of(ig)).itemDefs(List.of(def)).codeLists(List.of(cl)).build();
        IMetadataLibrary lib = buildLibrary(mdv, null);

        IColumnMetadata col = lib.getDataTable("DM").orElseThrow().getColumns().get(0);
        assertEquals("$RACE.", col.getDisplayFormat());
        assertEquals("RACE", col.getCodelist());
    }


    @Test
    void column_displayFormat_fromCodeList_numericType()
    {
        CodeList cl = CodeList.builder().oid("CL.1").name("YN").dataType("integer")
                .codeListItems(List
                        .of(CodeListItem.builder().codedValue("1").decode(decode("Yes")).build()))
                .build();
        ItemDef def = ItemDef.builder().oid("I.YN").name("YNF").dataType("integer")
                .codeListRef(CodeListRef.builder().codeListOID("CL.1").build()).build();
        ItemRef ref = ItemRef.builder().itemOID("I.YN").orderNumber(1).build();
        ItemGroupDef ig = ItemGroupDef.builder().oid("IG.DM").name("DM").itemRefs(List.of(ref))
                .build();
        MetaDataVersion mdv = MetaDataVersion.builder().oid("M.1").name("V1")
                .itemGroupDefs(List.of(ig)).itemDefs(List.of(def)).codeLists(List.of(cl)).build();
        IMetadataLibrary lib = buildLibrary(mdv, null);

        IColumnMetadata col = lib.getDataTable("DM").orElseThrow().getColumns().get(0);
        assertEquals("YN.", col.getDisplayFormat());
    }


    @Test
    void column_displayFormat_fallsBackToItemDefDisplayFormat()
    {
        ItemDef def = ItemDef.builder().oid("I.X").name("X").dataType("integer")
                .displayFormat("BEST12.").build();
        ItemRef ref = ItemRef.builder().itemOID("I.X").orderNumber(1).build();
        ItemGroupDef ig = ItemGroupDef.builder().oid("IG.D").name("D").itemRefs(List.of(ref))
                .build();
        MetaDataVersion mdv = MetaDataVersion.builder().oid("M.1").name("V1")
                .itemGroupDefs(List.of(ig)).itemDefs(List.of(def)).build();
        IMetadataLibrary lib = buildLibrary(mdv, null);

        IColumnMetadata col = lib.getDataTable("D").orElseThrow().getColumns().get(0);
        assertEquals("BEST12.", col.getDisplayFormat());
        assertNull(col.getCodelist());
    }


    @Test
    void column_displayFormat_codeListRef_pointsToMissingCodeList()
    {
        // CodeListRef points to non-existent OID — codelist resolves to null,
        // so the displayFormat falls back to the ItemDef.displayFormat.
        ItemDef def = ItemDef.builder().oid("I.X").name("X").dataType("integer")
                .displayFormat("8.2")
                .codeListRef(CodeListRef.builder().codeListOID("MISSING").build()).build();
        ItemRef ref = ItemRef.builder().itemOID("I.X").orderNumber(1).build();
        ItemGroupDef ig = ItemGroupDef.builder().oid("IG.D").name("D").itemRefs(List.of(ref))
                .build();
        MetaDataVersion mdv = MetaDataVersion.builder().oid("M.1").name("V1")
                .itemGroupDefs(List.of(ig)).itemDefs(List.of(def)).build();
        IMetadataLibrary lib = buildLibrary(mdv, null);

        IColumnMetadata col = lib.getDataTable("D").orElseThrow().getColumns().get(0);
        assertEquals("8.2", col.getDisplayFormat());
        assertNull(col.getCodelist());
    }


    @Test
    void column_core_fromMandatoryFlag()
    {
        ItemDef def1 = ItemDef.builder().oid("I.A").name("A").dataType("text").build();
        ItemDef def2 = ItemDef.builder().oid("I.B").name("B").dataType("text").build();
        ItemDef def3 = ItemDef.builder().oid("I.C").name("C").dataType("text").build();
        ItemRef r1 = ItemRef.builder().itemOID("I.A").orderNumber(1).mandatory("Yes").build();
        ItemRef r2 = ItemRef.builder().itemOID("I.B").orderNumber(2).mandatory("No").build();
        // unknown / null mandatory → null core
        ItemRef r3 = ItemRef.builder().itemOID("I.C").orderNumber(3).build();
        ItemGroupDef ig = ItemGroupDef.builder().oid("IG.D").name("D").itemRefs(List.of(r1, r2, r3))
                .build();
        MetaDataVersion mdv = MetaDataVersion.builder().oid("M.1").name("V1")
                .itemGroupDefs(List.of(ig)).itemDefs(List.of(def1, def2, def3)).build();
        IMetadataLibrary lib = buildLibrary(mdv, null);

        List<IColumnMetadata> cols = lib.getDataTable("D").orElseThrow().getColumns();
        assertEquals("Req", cols.get(0).getCore());
        assertEquals("Exp", cols.get(1).getCore());
        assertNull(cols.get(2).getCore());
    }


    @Test
    void column_role_andIndex()
    {
        ItemDef def1 = ItemDef.builder().oid("I.A").name("A").dataType("text").build();
        ItemRef r1 = ItemRef.builder().itemOID("I.A").orderNumber(1).role("Identifier").build();
        ItemGroupDef ig = ItemGroupDef.builder().oid("IG.D").name("D").itemRefs(List.of(r1))
                .build();
        MetaDataVersion mdv = MetaDataVersion.builder().oid("M.1").name("V1")
                .itemGroupDefs(List.of(ig)).itemDefs(List.of(def1)).build();
        IMetadataLibrary lib = buildLibrary(mdv, null);

        IColumnMetadata col = lib.getDataTable("D").orElseThrow().getColumns().get(0);
        assertEquals("Identifier", col.getRole());
        assertEquals(0, col.getIndex());
        assertFalse(col.isByGroup());
    }


    @Test
    void column_metaMap_includesItemDefAndItemRefFields()
    {
        MethodDef md = MethodDef.builder().oid("M.1").name("calc")
                .description(description("Method-Description")).build();
        Origin originElem = Origin.builder().description(description("Origin-Description")).build();
        ItemDef def = ItemDef.builder().oid("I.A").name("A").dataType("integer")
                .significantDigits(2).sasFieldName("AVAL").originElement(originElem).build();
        ItemRef ref = ItemRef.builder().itemOID("I.A").orderNumber(7).mandatory("Yes")
                .keySequence(1).methodOID("M.1").role("Result").build();
        ItemGroupDef ig = ItemGroupDef.builder().oid("IG.D").name("D").itemRefs(List.of(ref))
                .build();
        MetaDataVersion mdv = MetaDataVersion.builder().oid("M.1V").name("V1")
                .itemGroupDefs(List.of(ig)).itemDefs(List.of(def)).methodDefs(List.of(md)).build();
        IMetadataLibrary lib = buildLibrary(mdv, null);

        IColumnMetadata col = lib.getDataTable("D").orElseThrow().getColumns().get(0);
        assertEquals(2, col.getMetaValue(DataTableMetaSupport.META_KEY_ITEM_SIGNIFICANT_DIGITS)
                .orElseThrow());
        assertEquals("AVAL",
                col.getMetaValue(DataTableMetaSupport.META_KEY_SAS_FIELD_NAME).orElseThrow());
        assertEquals(1,
                col.getMetaValue(DataTableMetaSupport.META_KEY_ITEM_KEY_SEQUENCE).orElseThrow());
        assertEquals("Yes",
                col.getMetaValue(DataTableMetaSupport.META_KEY_ITEM_MANDATORY).orElseThrow());
        assertEquals(7,
                col.getMetaValue(DataTableMetaSupport.META_KEY_ITEM_ORDER_NUMBER).orElseThrow());
        assertEquals("Result",
                col.getMetaValue(DataTableMetaSupport.META_KEY_ITEM_ROLE).orElseThrow());
        assertEquals("Method-Description",
                col.getMetaValue(DataTableMetaSupport.META_KEY_ITEM_METHOD).orElseThrow());
        assertEquals("Origin-Description",
                col.getMetaValue(DataTableMetaSupport.META_KEY_ITEM_ORIGIN).orElseThrow());
    }


    @Test
    void column_origin_directField_takesPrecedenceOverOriginElement()
    {
        ItemDef def = ItemDef.builder().oid("I.A").name("A").dataType("text").origin("Predecessor")
                .originElement(Origin.builder().description(description("From-Element")).build())
                .build();
        ItemRef ref = ItemRef.builder().itemOID("I.A").orderNumber(1).build();
        ItemGroupDef ig = ItemGroupDef.builder().oid("IG.D").name("D").itemRefs(List.of(ref))
                .build();
        MetaDataVersion mdv = MetaDataVersion.builder().oid("M.1").name("V1")
                .itemGroupDefs(List.of(ig)).itemDefs(List.of(def)).build();
        IMetadataLibrary lib = buildLibrary(mdv, null);

        IColumnMetadata col = lib.getDataTable("D").orElseThrow().getColumns().get(0);
        assertEquals("Predecessor",
                col.getMetaValue(DataTableMetaSupport.META_KEY_ITEM_ORIGIN).orElseThrow());
    }


    @Test
    void column_comment_resolvesFromCommentDef_whenInlineMissing()
    {
        CommentDef cd = CommentDef.builder().oid("C.1").description(description("From-CommentDef"))
                .build();
        ItemDef def = ItemDef.builder().oid("I.A").name("A").dataType("text").commentOID("C.1")
                .build();
        ItemRef ref = ItemRef.builder().itemOID("I.A").orderNumber(1).build();
        ItemGroupDef ig = ItemGroupDef.builder().oid("IG.D").name("D").itemRefs(List.of(ref))
                .build();
        MetaDataVersion mdv = MetaDataVersion.builder().oid("M.1").name("V1")
                .itemGroupDefs(List.of(ig)).itemDefs(List.of(def)).commentDefs(List.of(cd)).build();
        IMetadataLibrary lib = buildLibrary(mdv, null);

        IColumnMetadata col = lib.getDataTable("D").orElseThrow().getColumns().get(0);
        assertEquals("From-CommentDef",
                col.getMetaValue(DataTableMetaSupport.META_KEY_COMMENT).orElseThrow());
    }


    @Test
    void column_comment_inlineWins()
    {
        CommentDef cd = CommentDef.builder().oid("C.1").description(description("From-CommentDef"))
                .build();
        ItemDef def = ItemDef.builder().oid("I.A").name("A").dataType("text").commentOID("C.1")
                .comment("inline").build();
        ItemRef ref = ItemRef.builder().itemOID("I.A").orderNumber(1).build();
        ItemGroupDef ig = ItemGroupDef.builder().oid("IG.D").name("D").itemRefs(List.of(ref))
                .build();
        MetaDataVersion mdv = MetaDataVersion.builder().oid("M.1").name("V1")
                .itemGroupDefs(List.of(ig)).itemDefs(List.of(def)).commentDefs(List.of(cd)).build();
        IMetadataLibrary lib = buildLibrary(mdv, null);

        IColumnMetadata col = lib.getDataTable("D").orElseThrow().getColumns().get(0);
        assertEquals("inline",
                col.getMetaValue(DataTableMetaSupport.META_KEY_COMMENT).orElseThrow());
    }


    @Test
    void column_keySequence_fromItemRef_winsOverDerived()
    {
        // explicit KeySequence = 9; no DomainKeys
        ItemDef def = ItemDef.builder().oid("I.A").name("A").dataType("text").build();
        ItemRef ref = ItemRef.builder().itemOID("I.A").orderNumber(1).keySequence(9).build();
        ItemGroupDef ig = ItemGroupDef.builder().oid("IG.D").name("D").itemRefs(List.of(ref))
                .build();
        MetaDataVersion mdv = MetaDataVersion.builder().oid("M.1").name("V1")
                .itemGroupDefs(List.of(ig)).itemDefs(List.of(def)).build();
        IMetadataLibrary lib = buildLibrary(mdv, null);

        IColumnMetadata col = lib.getDataTable("D").orElseThrow().getColumns().get(0);
        assertEquals(9, col.getKeySequence());
    }


    @Test
    void column_keySequence_returnsZero_whenNeitherItemRefNorDomainKeys()
    {
        ItemDef def = ItemDef.builder().oid("I.A").name("A").dataType("text").build();
        ItemRef ref = ItemRef.builder().itemOID("I.A").orderNumber(1).build();
        ItemGroupDef ig = ItemGroupDef.builder().oid("IG.D").name("D").itemRefs(List.of(ref))
                .build();
        MetaDataVersion mdv = MetaDataVersion.builder().oid("M.1").name("V1")
                .itemGroupDefs(List.of(ig)).itemDefs(List.of(def)).build();
        IMetadataLibrary lib = buildLibrary(mdv, null);

        IColumnMetadata col = lib.getDataTable("D").orElseThrow().getColumns().get(0);
        assertEquals(0, col.getKeySequence());
    }


    @Test
    void itemRefWithoutMatchingItemDef_isSkipped()
    {
        // ItemRef points to non-existent ItemDef
        ItemRef ref = ItemRef.builder().itemOID("I.MISSING").orderNumber(1).build();
        ItemGroupDef ig = ItemGroupDef.builder().oid("IG.D").name("D").itemRefs(List.of(ref))
                .build();
        MetaDataVersion mdv = MetaDataVersion.builder().oid("M.1").name("V1")
                .itemGroupDefs(List.of(ig)).build();
        IMetadataLibrary lib = buildLibrary(mdv, null);

        assertEquals(0, lib.getDataTable("D").orElseThrow().getColumns().size());
    }


    @Test
    void getDataTables_lazyAndCached()
    {
        ItemGroupDef ig = ItemGroupDef.builder().oid("IG.D").name("D").build();
        MetaDataVersion mdv = MetaDataVersion.builder().oid("M.1").name("V1")
                .itemGroupDefs(List.of(ig)).build();
        IMetadataLibrary lib = buildLibrary(mdv, null);

        List<IDataTableMetadata> first = lib.getDataTables();
        List<IDataTableMetadata> second = lib.getDataTables();
        assertEquals(first, second);
    }


    @Test
    void getDataTable_byUnknownName_returnsEmpty()
    {
        ItemGroupDef ig = ItemGroupDef.builder().oid("IG.D").name("D").build();
        MetaDataVersion mdv = MetaDataVersion.builder().oid("M.1").name("V1")
                .itemGroupDefs(List.of(ig)).build();
        IMetadataLibrary lib = buildLibrary(mdv, null);

        assertTrue(lib.getDataTable("NOT_HERE").isEmpty());
    }


    @Test
    void columnMetaKeys_reflectsPopulatedFields()
    {
        ItemDef def = ItemDef.builder().oid("I.A").name("A").dataType("text").sasFieldName("A1")
                .build();
        ItemRef ref = ItemRef.builder().itemOID("I.A").orderNumber(1).mandatory("Yes").build();
        ItemGroupDef ig = ItemGroupDef.builder().oid("IG.D").name("D").itemRefs(List.of(ref))
                .build();
        MetaDataVersion mdv = MetaDataVersion.builder().oid("M.1").name("V1")
                .itemGroupDefs(List.of(ig)).itemDefs(List.of(def)).build();
        IMetadataLibrary lib = buildLibrary(mdv, null);

        IColumnMetadata col = lib.getDataTable("D").orElseThrow().getColumns().get(0);
        Set<String> keys = col.getMetaKeys();
        assertNotNull(keys);
        assertTrue(keys.contains(DataTableMetaSupport.META_KEY_SAS_FIELD_NAME));
        assertTrue(keys.contains(DataTableMetaSupport.META_KEY_ITEM_MANDATORY));
        assertTrue(col.getMetaValue("not-a-real-key").isEmpty());
    }
}
