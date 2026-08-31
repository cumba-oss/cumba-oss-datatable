package net.cumba.cdisc.define;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DefineSupportTest
{

    private DefineSupport support;

    private ODM odm;

    @BeforeEach
    void setUp()
    {
        // Build a realistic ODM structure
        ItemRef itemRef1 = ItemRef.builder().itemOID("IT.DM.USUBJID").orderNumber(1)
                .mandatory("Yes").keySequence(1).build();
        ItemRef itemRef2 = ItemRef.builder().itemOID("IT.DM.AGE").orderNumber(2).mandatory("No")
                .methodOID("MT.AGE").build();

        Leaf leaf = Leaf.builder().id("LF.DM").href("dm.xpt").build();

        ItemGroupDef igDM = ItemGroupDef.builder().oid("IG.DM").name("DM").domain("DM")
                .itemRefs(Arrays.asList(itemRef1, itemRef2)).leaf(leaf).build();

        ItemGroupDef igAE = ItemGroupDef.builder().oid("IG.AE").name("AE").domain("AE")
                .itemRefs(Collections.emptyList()).build();

        Description desc = new Description();
        TranslatedText tt = new TranslatedText();
        tt.setValue("Subject Identifier");
        tt.setLang("en");
        desc.setTranslatedTexts(Arrays.asList(tt));

        ItemDef itemUSUBJID = ItemDef.builder().oid("IT.DM.USUBJID").name("USUBJID")
                .dataType("text").length(20).description(desc).build();

        ItemDef itemAGE = ItemDef.builder().oid("IT.DM.AGE").name("AGE").dataType("integer")
                .length(3).build();

        CodeList clSex = CodeList.builder().oid("CL.SEX").name("Sex").dataType("text").build();
        CodeList clRace = CodeList.builder().oid("CL.RACE").name("Race").dataType("text").build();

        MethodDef mdAge = MethodDef.builder().oid("MT.AGE").name("Age Derivation")
                .type("Computation").build();

        CommentDef cd1 = CommentDef.builder().oid("COM.001").build();

        WhereClauseDef wc1 = WhereClauseDef.builder().oid("WC.001").build();

        ValueListDef vl1 = ValueListDef.builder().oid("VL.001").build();

        Standard std1 = Standard.builder().oid("STD.SDTM").name("SDTM-IG").version("3.3").build();
        Standards stds = Standards.builder().standards(Arrays.asList(std1)).build();

        MetaDataVersion mdv = MetaDataVersion.builder().oid("MDV.001").name("Version 1")
                .itemGroupDefs(Arrays.asList(igDM, igAE))
                .itemDefs(Arrays.asList(itemUSUBJID, itemAGE))
                .codeLists(Arrays.asList(clSex, clRace)).methodDefs(Arrays.asList(mdAge))
                .commentDefs(Arrays.asList(cd1)).whereClauseDefs(Arrays.asList(wc1))
                .valueListDefs(Arrays.asList(vl1)).standards(stds).build();

        GlobalVariables gv = GlobalVariables.builder().studyName("Test Study")
                .protocolName("PROTO-001").build();

        Study study = Study.builder().oid("STUDY.001").globalVariables(gv)
                .metaDataVersions(Arrays.asList(mdv)).build();

        odm = ODM.builder().fileOID("define-001").fileType("Snapshot").odmVersion("1.3.2")
                .studies(Arrays.asList(study)).build();

        support = new DefineSupport(URI.create("file:///test/define.xml"), odm);
    }

    // ========== Constructor Tests ==========

    @Nested
    class ConstructorTests
    {

        @Test
        void testConstructorWithUriAndOdm()
        {
            URI uri = URI.create("file:///test/path.xml");
            DefineSupport ds = new DefineSupport(uri, odm);
            assertEquals(uri, ds.getUri());
            assertEquals(odm, ds.getOdm());
        }


        @Test
        void testConstructorWithFileAndOdm()
        {
            java.io.File file = new java.io.File("/tmp/define.xml");
            DefineSupport ds = new DefineSupport(file, odm);
            assertNotNull(ds.getUri());
            assertEquals(odm, ds.getOdm());
        }
    }

    // ========== Stream Access Tests ==========


    @Nested
    class StreamTests
    {

        @Test
        void testGetStudies()
        {
            List<Study> studies = support.getStudies().toList();
            assertEquals(1, studies.size());
            assertEquals("STUDY.001", studies.get(0).getOid());
        }


        @Test
        void testGetGlobalVariables()
        {
            List<GlobalVariables> gvs = support.getGlobalVariables().toList();
            assertEquals(1, gvs.size());
            assertEquals("Test Study", gvs.get(0).getStudyName());
        }


        @Test
        void testGetMetaDataVersions()
        {
            List<MetaDataVersion> mdvs = support.getMetaDataVersions().toList();
            assertEquals(1, mdvs.size());
            assertEquals("MDV.001", mdvs.get(0).getOid());
        }


        @Test
        void testGetItemGroupDefs()
        {
            List<ItemGroupDef> igs = support.getItemGroupDefs().toList();
            assertEquals(2, igs.size());
        }


        @Test
        void testGetItemDefs()
        {
            List<ItemDef> items = support.getItemDefs().toList();
            assertEquals(2, items.size());
        }


        @Test
        void testGetCodeLists()
        {
            List<CodeList> cls = support.getCodeLists().toList();
            assertEquals(2, cls.size());
        }


        @Test
        void testGetMethodDefs()
        {
            List<MethodDef> mds = support.getMethodDefs().toList();
            assertEquals(1, mds.size());
            assertEquals("MT.AGE", mds.get(0).getOid());
        }


        @Test
        void testGetCommentDefs()
        {
            List<CommentDef> cds = support.getCommentDefs().toList();
            assertEquals(1, cds.size());
        }


        @Test
        void testGetValueListDefs()
        {
            List<ValueListDef> vls = support.getValueListDefs().toList();
            assertEquals(1, vls.size());
        }


        @Test
        void testGetStandards()
        {
            List<Standard> stds = support.getStandards().toList();
            assertEquals(1, stds.size());
            assertEquals("STD.SDTM", stds.get(0).getOid());
        }
    }

    // ========== Lookup by OID Tests ==========


    @Nested
    class LookupByOidTests
    {

        @Test
        void testGetItemDefByOid_found()
        {
            Optional<ItemDef> result = support.getItemDefByOid("IT.DM.USUBJID");
            assertTrue(result.isPresent());
            assertEquals("USUBJID", result.get().getName());
        }


        @Test
        void testGetItemDefByOid_notFound()
        {
            Optional<ItemDef> result = support.getItemDefByOid("IT.NONEXISTENT");
            assertFalse(result.isPresent());
        }


        @Test
        void testGetCodeListByOid_found()
        {
            Optional<CodeList> result = support.getCodeListByOid("CL.SEX");
            assertTrue(result.isPresent());
            assertEquals("Sex", result.get().getName());
        }


        @Test
        void testGetCodeListByOid_notFound()
        {
            Optional<CodeList> result = support.getCodeListByOid("CL.NONEXISTENT");
            assertFalse(result.isPresent());
        }


        @Test
        void testGetMethodDefByOID_found()
        {
            Optional<MethodDef> result = support.getMethodDefByOID("MT.AGE");
            assertTrue(result.isPresent());
            assertEquals("Age Derivation", result.get().getName());
        }


        @Test
        void testGetMethodDefByOID_notFound()
        {
            Optional<MethodDef> result = support.getMethodDefByOID("MT.NONE");
            assertFalse(result.isPresent());
        }


        @Test
        void testGetCommentDefByOID_found()
        {
            Optional<CommentDef> result = support.getCommentDefByOID("COM.001");
            assertTrue(result.isPresent());
        }


        @Test
        void testGetCommentDefByOID_notFound()
        {
            Optional<CommentDef> result = support.getCommentDefByOID("COM.NONE");
            assertFalse(result.isPresent());
        }


        @Test
        void testGetStandardByOID_found()
        {
            Optional<Standard> result = support.getStandardByOID("STD.SDTM");
            assertTrue(result.isPresent());
            assertEquals("SDTM-IG", result.get().getName());
        }


        @Test
        void testGetStandardByOID_notFound()
        {
            Optional<Standard> result = support.getStandardByOID("STD.NONE");
            assertFalse(result.isPresent());
        }


        @Test
        void testGetItemGroupDefByOid_found()
        {
            Optional<ItemGroupDef> result = support.getItemGroupDefByOid("IG.DM");
            assertTrue(result.isPresent());
            assertEquals("DM", result.get().getName());
        }


        @Test
        void testGetItemGroupDefByOid_notFound()
        {
            Optional<ItemGroupDef> result = support.getItemGroupDefByOid("IG.NONE");
            assertFalse(result.isPresent());
        }
    }

    // ========== Lookup by Name Tests ==========


    @Nested
    class LookupByNameTests
    {

        @Test
        void testGetItemDefByName_caseInsensitive()
        {
            Optional<ItemDef> result = support.getItemDefByName("usubjid");
            assertTrue(result.isPresent());
            assertEquals("IT.DM.USUBJID", result.get().getOid());
        }


        @Test
        void testGetItemDefByName_caseSensitive()
        {
            Optional<ItemDef> result = support.getItemDefByName("USUBJID", false);
            assertTrue(result.isPresent());

            Optional<ItemDef> notFound = support.getItemDefByName("usubjid", false);
            assertFalse(notFound.isPresent());
        }


        @Test
        void testGetItemGroupDefByName_caseInsensitive()
        {
            Optional<ItemGroupDef> result = support.getItemGroupDefByName("dm");
            assertTrue(result.isPresent());
            assertEquals("IG.DM", result.get().getOid());
        }


        @Test
        void testGetItemGroupDefByName_caseSensitive()
        {
            Optional<ItemGroupDef> result = support.getItemGroupDefByName("DM", false);
            assertTrue(result.isPresent());

            Optional<ItemGroupDef> notFound = support.getItemGroupDefByName("dm", false);
            assertFalse(notFound.isPresent());
        }


        @Test
        void testGetItemGroupDefByName_notFound()
        {
            Optional<ItemGroupDef> result = support.getItemGroupDefByName("NONEXISTENT");
            assertFalse(result.isPresent());
        }
    }

    // ========== ItemGroup-specific Tests ==========


    @Nested
    class ItemGroupTests
    {

        @Test
        void testGetItemsForItemGroup()
        {
            ItemGroupDef dm = support.getItemGroupDefByName("DM").orElseThrow();
            List<ItemDef> items = support.getItemsForItemGroup(dm).toList();
            assertEquals(2, items.size());
        }


        @Test
        void testGetItemsForItemGroup_nullGroup()
        {
            Stream<ItemDef> result = support.getItemsForItemGroup(null);
            assertEquals(0, result.count());
        }


        @Test
        void testGetItemsForItemGroup_emptyRefs()
        {
            ItemGroupDef ae = support.getItemGroupDefByName("AE").orElseThrow();
            List<ItemDef> items = support.getItemsForItemGroup(ae).toList();
            assertEquals(0, items.size());
        }


        @Test
        void testGetItemForName()
        {
            ItemGroupDef dm = support.getItemGroupDefByName("DM").orElseThrow();
            Optional<ItemDef> result = support.getItemForName(dm, "AGE");
            assertTrue(result.isPresent());
            assertEquals("IT.DM.AGE", result.get().getOid());
        }


        @Test
        void testGetItemForName_notFound()
        {
            ItemGroupDef dm = support.getItemGroupDefByName("DM").orElseThrow();
            Optional<ItemDef> result = support.getItemForName(dm, "NONEXISTENT");
            assertFalse(result.isPresent());
        }


        @Test
        void testGetItemForName_nullGroup()
        {
            Optional<ItemDef> result = support.getItemForName(null, "AGE");
            assertFalse(result.isPresent());
        }


        @Test
        void testGetItemGroupDefByURI()
        {
            URI expectedUri = URI.create("file:///test/dm.xpt");
            Optional<ItemGroupDef> result = support.getItemGroupDefByURI(expectedUri);
            assertTrue(result.isPresent());
            assertEquals("IG.DM", result.get().getOid());
        }


        @Test
        void testGetItemGroupDefByURI_notFound()
        {
            URI uri = URI.create("file:///test/nonexistent.xpt");
            Optional<ItemGroupDef> result = support.getItemGroupDefByURI(uri);
            assertFalse(result.isPresent());
        }


        @Test
        void testGetUriFor()
        {
            ItemGroupDef dm = support.getItemGroupDefByName("DM").orElseThrow();
            URI uri = support.getUriFor(dm);
            assertNotNull(uri);
            assertTrue(uri.toString().endsWith("dm.xpt"));
        }


        @Test
        void testGetUriFor_nullGroup()
        {
            URI uri = support.getUriFor(null);
            assertNull(uri);
        }


        @Test
        void testGetUriFor_noLeaf()
        {
            ItemGroupDef ae = support.getItemGroupDefByName("AE").orElseThrow();
            URI uri = support.getUriFor(ae);
            assertNull(uri);
        }
    }

    // ========== Static Method Tests ==========


    @Nested
    class StaticMethodTests
    {

        @Test
        void testFilterByName_nullStream()
        {
            Optional<ItemDef> result = DefineSupport.filterByName(null, "test", false);
            assertFalse(result.isPresent());
        }


        @Test
        void testFilterByName_emptyStream()
        {
            Optional<ItemDef> result = DefineSupport.filterByName(Stream.empty(), "test", false);
            assertFalse(result.isPresent());
        }


        @Test
        void testGetDescriptionString_element()
        {
            ItemDef item = support.getItemDefByOid("IT.DM.USUBJID").orElseThrow();
            String desc = DefineSupport.getDescriptionString(item);
            assertEquals("Subject Identifier", desc);
        }


        @Test
        void testGetDescriptionString_nullElement()
        {
            String desc = DefineSupport.getDescriptionString((IDescribedElement) null);
            assertNull(desc);
        }


        @Test
        void testGetDescriptionString_nullDescription()
        {
            String desc = DefineSupport.getDescriptionString((Description) null);
            assertNull(desc);
        }


        @Test
        void testGetDescriptionString_emptyTranslations()
        {
            Description d = new Description();
            d.setTranslatedTexts(Collections.emptyList());
            String desc = DefineSupport.getDescriptionString(d);
            assertNull(desc);
        }


        @Test
        void testGetDescriptionString_noTranslations()
        {
            Description d = new Description();
            String desc = DefineSupport.getDescriptionString(d);
            assertNull(desc);
        }
    }

    // ========== Null/Empty ODM Tests ==========


    @Nested
    class NullOdmTests
    {

        @Test
        void testNullStudiesList()
        {
            ODM emptyOdm = ODM.builder().fileOID("empty").build();
            DefineSupport ds = new DefineSupport(URI.create("file:///empty"), emptyOdm);
            assertEquals(0, ds.getStudies().count());
            assertEquals(0, ds.getMetaDataVersions().count());
            assertEquals(0, ds.getItemGroupDefs().count());
            assertEquals(0, ds.getItemDefs().count());
            assertEquals(0, ds.getCodeLists().count());
            assertEquals(0, ds.getMethodDefs().count());
            assertEquals(0, ds.getCommentDefs().count());
            assertEquals(0, ds.getValueListDefs().count());
            assertEquals(0, ds.getStandards().count());
            assertEquals(0, ds.getGlobalVariables().count());
        }


        @Test
        void testStudyWithNullMetaDataVersions()
        {
            Study study = Study.builder().oid("S1").build();
            ODM o = ODM.builder().studies(Arrays.asList(study)).build();
            DefineSupport ds = new DefineSupport(URI.create("file:///test"), o);

            assertEquals(0, ds.getMetaDataVersions().count());
            assertEquals(0, ds.getItemGroupDefs().count());
        }


        @Test
        void testMetaDataVersionWithNullLists()
        {
            MetaDataVersion mdv = MetaDataVersion.builder().oid("MDV.1").build();
            Study study = Study.builder().oid("S1").metaDataVersions(Arrays.asList(mdv)).build();
            ODM o = ODM.builder().studies(Arrays.asList(study)).build();
            DefineSupport ds = new DefineSupport(URI.create("file:///test"), o);

            assertEquals(0, ds.getItemGroupDefs().count());
            assertEquals(0, ds.getItemDefs().count());
            assertEquals(0, ds.getCodeLists().count());
            assertEquals(0, ds.getMethodDefs().count());
            assertEquals(0, ds.getCommentDefs().count());
            assertEquals(0, ds.getValueListDefs().count());
            assertEquals(0, ds.getStandards().count());
        }


        @Test
        void testMetaDataVersionWithNullStandards()
        {
            MetaDataVersion mdv = MetaDataVersion.builder().oid("MDV.1").standards(null).build();
            Study study = Study.builder().oid("S1").metaDataVersions(Arrays.asList(mdv)).build();
            ODM o = ODM.builder().studies(Arrays.asList(study)).build();
            DefineSupport ds = new DefineSupport(URI.create("file:///test"), o);

            assertEquals(0, ds.getStandards().count());
        }
    }
}
