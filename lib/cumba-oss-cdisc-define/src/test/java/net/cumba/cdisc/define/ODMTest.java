package net.cumba.cdisc.define;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Test cases for the define package bean classes: ODM, Study, GlobalVariables, and MetaDataVersion.
 * These tests verify the Lombok @Builder pattern, getters, null handling, and list population.
 */
public class ODMTest
{

    // ========================
    // ODM Tests
    // ========================

    @Nested
    class ODMBuilderTests
    {

        @Test
        void testBuilderWithAllAttributes()
        {
            ODM odm = ODM.builder().fileOID("file-001").fileType("Snapshot").odmVersion("1.3.2")
                    .granularity("SingleSite").archival("Yes")
                    .creationDateTime("2025-01-15T10:30:00").priorFileOID("file-000")
                    .asOfDateTime("2025-01-14T12:00:00").sourceSystem("ClinicalDB")
                    .sourceSystemVersion("2.1.0").schemaLocation("http://www.cdisc.org/ns/odm/v1.3")
                    .build();

            assertEquals("file-001", odm.getFileOID());
            assertEquals("Snapshot", odm.getFileType());
            assertEquals("1.3.2", odm.getOdmVersion());
            assertEquals("SingleSite", odm.getGranularity());
            assertEquals("Yes", odm.getArchival());
            assertEquals("2025-01-15T10:30:00", odm.getCreationDateTime());
            assertEquals("file-000", odm.getPriorFileOID());
            assertEquals("2025-01-14T12:00:00", odm.getAsOfDateTime());
            assertEquals("ClinicalDB", odm.getSourceSystem());
            assertEquals("2.1.0", odm.getSourceSystemVersion());
            assertEquals("http://www.cdisc.org/ns/odm/v1.3", odm.getSchemaLocation());
        }


        @Test
        void testBuilderWithMinimalAttributes()
        {
            ODM odm = ODM.builder().fileOID("minimal-file").build();

            assertEquals("minimal-file", odm.getFileOID());
            assertNull(odm.getFileType());
            assertNull(odm.getOdmVersion());
        }


        @Test
        void testBuilderWithNullAttributes()
        {
            ODM odm = ODM.builder().fileOID(null).fileType(null).build();

            assertNull(odm.getFileOID());
            assertNull(odm.getFileType());
        }


        @Test
        void testBuilderWithStudiesList()
        {
            Study study1 = Study.builder().oid("STUDY-001").build();
            Study study2 = Study.builder().oid("STUDY-002").build();

            ODM odm = ODM.builder().fileOID("file-with-studies")
                    .studies(Arrays.asList(study1, study2)).build();

            assertNotNull(odm.getStudies());
            assertEquals(2, odm.getStudies().size());
            assertEquals("STUDY-001", odm.getStudies().get(0).getOid());
            assertEquals("STUDY-002", odm.getStudies().get(1).getOid());
        }


        @Test
        void testBuilderWithEmptyStudiesList()
        {
            ODM odm = ODM.builder().fileOID("file-empty-studies").studies(Collections.emptyList())
                    .build();

            assertNotNull(odm.getStudies());
            assertTrue(odm.getStudies().isEmpty());
        }


        @Test
        void testBuilderWithNullStudiesList()
        {
            ODM odm = ODM.builder().fileOID("file-null-studies").studies(null).build();

            assertNull(odm.getStudies());
        }
    }

    // ========================
    // Study Tests
    // ========================


    @Nested
    class StudyBuilderTests
    {

        @Test
        void testBuilderWithAllProperties()
        {
            GlobalVariables globalVars = GlobalVariables.builder().studyName("Test Study")
                    .studyDescription("A test study description").protocolName("PROTOCOL-001")
                    .build();

            MetaDataVersion mdv = MetaDataVersion.builder().oid("MDV-001")
                    .name("MetaData Version 1").build();

            Study study = Study.builder().oid("STUDY-001").globalVariables(globalVars)
                    .metaDataVersions(Collections.singletonList(mdv)).build();

            assertEquals("STUDY-001", study.getOid());
            assertNotNull(study.getGlobalVariables());
            assertEquals("Test Study", study.getGlobalVariables().getStudyName());
            assertNotNull(study.getMetaDataVersions());
            assertEquals(1, study.getMetaDataVersions().size());
        }


        @Test
        void testBuilderWithOidOnly()
        {
            Study study = Study.builder().oid("STUDY-MINIMAL").build();

            assertEquals("STUDY-MINIMAL", study.getOid());
            assertNull(study.getGlobalVariables());
            assertNull(study.getMetaDataVersions());
        }


        @Test
        void testBuilderWithMultipleMetaDataVersions()
        {
            MetaDataVersion mdv1 = MetaDataVersion.builder().oid("MDV-001").name("Version 1")
                    .build();
            MetaDataVersion mdv2 = MetaDataVersion.builder().oid("MDV-002").name("Version 2")
                    .build();
            MetaDataVersion mdv3 = MetaDataVersion.builder().oid("MDV-003").name("Version 3")
                    .build();

            Study study = Study.builder().oid("STUDY-MULTI-MDV")
                    .metaDataVersions(Arrays.asList(mdv1, mdv2, mdv3)).build();

            assertEquals(3, study.getMetaDataVersions().size());
            assertEquals("MDV-001", study.getMetaDataVersions().get(0).getOid());
            assertEquals("MDV-002", study.getMetaDataVersions().get(1).getOid());
            assertEquals("MDV-003", study.getMetaDataVersions().get(2).getOid());
        }


        @Test
        void testBuilderWithNullProperties()
        {
            Study study = Study.builder().oid(null).globalVariables(null).metaDataVersions(null)
                    .build();

            assertNull(study.getOid());
            assertNull(study.getGlobalVariables());
            assertNull(study.getMetaDataVersions());
        }
    }

    // ========================
    // GlobalVariables Tests
    // ========================


    @Nested
    class GlobalVariablesBuilderTests
    {

        @Test
        void testBuilderWithAllProperties()
        {
            GlobalVariables globalVars = GlobalVariables.builder().studyName("Clinical Trial ABC")
                    .studyDescription("Phase III randomized controlled trial")
                    .protocolName("ABC-2025-001").build();

            assertEquals("Clinical Trial ABC", globalVars.getStudyName());
            assertEquals("Phase III randomized controlled trial", globalVars.getStudyDescription());
            assertEquals("ABC-2025-001", globalVars.getProtocolName());
        }


        @Test
        void testBuilderWithStudyNameOnly()
        {
            GlobalVariables globalVars = GlobalVariables.builder().studyName("Minimal Study")
                    .build();

            assertEquals("Minimal Study", globalVars.getStudyName());
            assertNull(globalVars.getStudyDescription());
            assertNull(globalVars.getProtocolName());
        }


        @Test
        void testBuilderWithEmptyStrings()
        {
            GlobalVariables globalVars = GlobalVariables.builder().studyName("")
                    .studyDescription("").protocolName("").build();

            assertEquals("", globalVars.getStudyName());
            assertEquals("", globalVars.getStudyDescription());
            assertEquals("", globalVars.getProtocolName());
        }


        @Test
        void testBuilderWithNullValues()
        {
            GlobalVariables globalVars = GlobalVariables.builder().studyName(null)
                    .studyDescription(null).protocolName(null).build();

            assertNull(globalVars.getStudyName());
            assertNull(globalVars.getStudyDescription());
            assertNull(globalVars.getProtocolName());
        }


        @Test
        void testBuilderWithSpecialCharacters()
        {
            GlobalVariables globalVars = GlobalVariables.builder()
                    .studyName("Study with <special> & \"characters\"")
                    .studyDescription("Description with\nnewlines\tand\ttabs")
                    .protocolName("PROT-2025/001").build();

            assertEquals("Study with <special> & \"characters\"", globalVars.getStudyName());
            assertEquals("Description with\nnewlines\tand\ttabs", globalVars.getStudyDescription());
            assertEquals("PROT-2025/001", globalVars.getProtocolName());
        }
    }

    // ========================
    // MetaDataVersion Tests
    // ========================


    @Nested
    class MetaDataVersionBuilderTests
    {

        @Test
        void testBuilderWithAllStringAttributes()
        {
            MetaDataVersion mdv = MetaDataVersion.builder().oid("MDV-001")
                    .name("Metadata Version 1.0").description("Primary metadata version")
                    .defineVersion("2.1.0").standardName("CDISC SDTM").standardVersion("3.3")
                    .build();

            assertEquals("MDV-001", mdv.getOid());
            assertEquals("Metadata Version 1.0", mdv.getName());
            assertEquals("Primary metadata version", mdv.getDescription());
            assertEquals("2.1.0", mdv.getDefineVersion());
            assertEquals("CDISC SDTM", mdv.getStandardName());
            assertEquals("3.3", mdv.getStandardVersion());
        }


        @Test
        void testBuilderWithMinimalAttributes()
        {
            MetaDataVersion mdv = MetaDataVersion.builder().oid("MDV-MINIMAL").build();

            assertEquals("MDV-MINIMAL", mdv.getOid());
            assertNull(mdv.getName());
            assertNull(mdv.getDescription());
            assertNull(mdv.getDefineVersion());
        }


        @Test
        void testBuilderWithInclude()
        {
            Include include = Include.builder().studyOID("STUDY-REF-001")
                    .metaDataVersionOID("MDV-REF-001").build();

            MetaDataVersion mdv = MetaDataVersion.builder().oid("MDV-WITH-INCLUDE").include(include)
                    .build();

            assertNotNull(mdv.getInclude());
            assertEquals("STUDY-REF-001", mdv.getInclude().getStudyOID());
            assertEquals("MDV-REF-001", mdv.getInclude().getMetaDataVersionOID());
        }


        @Test
        void testBuilderWithProtocol()
        {
            Protocol protocol = Protocol.builder().studyEventRefs(Collections.emptyList())
                    .aliases(Collections.emptyList()).build();

            MetaDataVersion mdv = MetaDataVersion.builder().oid("MDV-WITH-PROTOCOL")
                    .protocol(protocol).build();

            assertNotNull(mdv.getProtocol());
        }


        @Test
        void testBuilderWithItemGroupDefsList()
        {
            ItemGroupDef igd1 = ItemGroupDef.builder().oid("IG.DM").name("DM").sasDatasetName("DM")
                    .domain("DM").build();

            ItemGroupDef igd2 = ItemGroupDef.builder().oid("IG.AE").name("AE").sasDatasetName("AE")
                    .domain("AE").build();

            MetaDataVersion mdv = MetaDataVersion.builder().oid("MDV-WITH-IGD")
                    .itemGroupDefs(Arrays.asList(igd1, igd2)).build();

            assertNotNull(mdv.getItemGroupDefs());
            assertEquals(2, mdv.getItemGroupDefs().size());
            assertEquals("IG.DM", mdv.getItemGroupDefs().get(0).getOid());
            assertEquals("IG.AE", mdv.getItemGroupDefs().get(1).getOid());
        }


        @Test
        void testBuilderWithItemDefsList()
        {
            ItemDef id1 = ItemDef.builder().oid("IT.DM.USUBJID").name("USUBJID").dataType("text")
                    .length(40).build();

            ItemDef id2 = ItemDef.builder().oid("IT.DM.AGE").name("AGE").dataType("integer")
                    .length(8).build();

            MetaDataVersion mdv = MetaDataVersion.builder().oid("MDV-WITH-ITEMS")
                    .itemDefs(Arrays.asList(id1, id2)).build();

            assertNotNull(mdv.getItemDefs());
            assertEquals(2, mdv.getItemDefs().size());
            assertEquals("IT.DM.USUBJID", mdv.getItemDefs().get(0).getOid());
            assertEquals("text", mdv.getItemDefs().get(0).getDataType());
            assertEquals(Integer.valueOf(40), mdv.getItemDefs().get(0).getLength());
        }


        @Test
        void testBuilderWithCodeListsList()
        {
            CodeList cl1 = CodeList.builder().oid("CL.SEX").name("Sex").dataType("text").build();

            CodeList cl2 = CodeList.builder().oid("CL.RACE").name("Race").dataType("text").build();

            MetaDataVersion mdv = MetaDataVersion.builder().oid("MDV-WITH-CODELISTS")
                    .codeLists(Arrays.asList(cl1, cl2)).build();

            assertNotNull(mdv.getCodeLists());
            assertEquals(2, mdv.getCodeLists().size());
            assertEquals("CL.SEX", mdv.getCodeLists().get(0).getOid());
            assertEquals("CL.RACE", mdv.getCodeLists().get(1).getOid());
        }


        @Test
        void testBuilderWithMethodDefsList()
        {
            MethodDef md1 = MethodDef.builder().oid("MT.DERIVE.AGE").name("Derive AGE")
                    .type("Computation").build();

            MetaDataVersion mdv = MetaDataVersion.builder().oid("MDV-WITH-METHODS")
                    .methodDefs(Collections.singletonList(md1)).build();

            assertNotNull(mdv.getMethodDefs());
            assertEquals(1, mdv.getMethodDefs().size());
            assertEquals("MT.DERIVE.AGE", mdv.getMethodDefs().get(0).getOid());
        }


        @Test
        void testBuilderWithCommentDefsList()
        {
            CommentDef cd1 = CommentDef.builder().oid("COM.001").build();

            MetaDataVersion mdv = MetaDataVersion.builder().oid("MDV-WITH-COMMENTS")
                    .commentDefs(Collections.singletonList(cd1)).build();

            assertNotNull(mdv.getCommentDefs());
            assertEquals(1, mdv.getCommentDefs().size());
            assertEquals("COM.001", mdv.getCommentDefs().get(0).getOid());
        }


        @Test
        void testBuilderWithWhereClauseDefsList()
        {
            WhereClauseDef wcd = WhereClauseDef.builder().oid("WC.001").build();

            MetaDataVersion mdv = MetaDataVersion.builder().oid("MDV-WITH-WHERECLAUSE")
                    .whereClauseDefs(Collections.singletonList(wcd)).build();

            assertNotNull(mdv.getWhereClauseDefs());
            assertEquals(1, mdv.getWhereClauseDefs().size());
        }


        @Test
        void testBuilderWithValueListDefsList()
        {
            ValueListDef vld = ValueListDef.builder().oid("VL.001").build();

            MetaDataVersion mdv = MetaDataVersion.builder().oid("MDV-WITH-VALUELIST")
                    .valueListDefs(Collections.singletonList(vld)).build();

            assertNotNull(mdv.getValueListDefs());
            assertEquals(1, mdv.getValueListDefs().size());
        }


        @Test
        void testBuilderWithLeavesList()
        {
            Leaf leaf1 = Leaf.builder().id("LF.001").href("dm.xpt").build();

            Leaf leaf2 = Leaf.builder().id("LF.002").href("ae.xpt").build();

            MetaDataVersion mdv = MetaDataVersion.builder().oid("MDV-WITH-LEAVES")
                    .leaves(Arrays.asList(leaf1, leaf2)).build();

            assertNotNull(mdv.getLeaves());
            assertEquals(2, mdv.getLeaves().size());
        }


        @Test
        void testBuilderWithStandardsList()
        {
            Standard std = Standard.builder().oid("STD.SDTM").name("SDTM-IG").type("IG")
                    .version("3.3").build();

            Standards stds = Standards.builder().standards(Collections.singletonList(std)).build();
            MetaDataVersion mdv = MetaDataVersion.builder().oid("MDV-WITH-STANDARDS")
                    .standards(stds).build();

            assertNotNull(mdv.getStandards());
            assertEquals(1, mdv.getStandards().getStandards().size());
            assertEquals("STD.SDTM", mdv.getStandards().getStandards().get(0).getOid());
        }


        @Test
        void testBuilderWithAllListsEmpty()
        {
            Standards stds = Standards.builder().standards(Collections.emptyList()).build();
            MetaDataVersion mdv = MetaDataVersion.builder().oid("MDV-EMPTY-LISTS")
                    .studyEventDefs(Collections.emptyList()).formDefs(Collections.emptyList())
                    .itemGroupDefs(Collections.emptyList()).itemDefs(Collections.emptyList())
                    .codeLists(Collections.emptyList()).methodDefs(Collections.emptyList())
                    .commentDefs(Collections.emptyList()).whereClauseDefs(Collections.emptyList())
                    .valueListDefs(Collections.emptyList()).leaves(Collections.emptyList())
                    .standards(stds).build();

            assertNotNull(mdv.getStudyEventDefs());
            assertTrue(mdv.getStudyEventDefs().isEmpty());
            assertNotNull(mdv.getFormDefs());
            assertTrue(mdv.getFormDefs().isEmpty());
            assertNotNull(mdv.getItemGroupDefs());
            assertTrue(mdv.getItemGroupDefs().isEmpty());
            assertNotNull(mdv.getItemDefs());
            assertTrue(mdv.getItemDefs().isEmpty());
            assertNotNull(mdv.getCodeLists());
            assertTrue(mdv.getCodeLists().isEmpty());
        }


        @Test
        void testBuilderWithAllListsNull()
        {
            MetaDataVersion mdv = MetaDataVersion.builder().oid("MDV-NULL-LISTS")
                    .studyEventDefs(null).formDefs(null).itemGroupDefs(null).itemDefs(null)
                    .codeLists(null).methodDefs(null).commentDefs(null).whereClauseDefs(null)
                    .valueListDefs(null).leaves(null).standards(null).build();

            assertNull(mdv.getStudyEventDefs());
            assertNull(mdv.getFormDefs());
            assertNull(mdv.getItemGroupDefs());
            assertNull(mdv.getItemDefs());
            assertNull(mdv.getCodeLists());
            assertNull(mdv.getMethodDefs());
        }
    }

    // ========================
    // Integration Tests
    // ========================


    @Nested
    class IntegrationTests
    {

        @Test
        void testFullODMStructure()
        {
            // Build GlobalVariables
            GlobalVariables globalVars = GlobalVariables.builder().studyName("CDISC SDTM Study")
                    .studyDescription("A comprehensive SDTM-IG conformant study")
                    .protocolName("SDTM-2025-001").build();

            // Build ItemDefs
            ItemDef usubjidDef = ItemDef.builder().oid("IT.DM.USUBJID").name("USUBJID")
                    .dataType("text").length(40).sasFieldName("USUBJID").build();

            ItemDef ageDef = ItemDef.builder().oid("IT.DM.AGE").name("AGE").dataType("integer")
                    .length(8).sasFieldName("AGE").build();

            // Build ItemGroupDef
            ItemGroupDef dmDataset = ItemGroupDef.builder().oid("IG.DM").name("DM")
                    .sasDatasetName("DM").domain("DM").repeating("No").isReferenceData("No")
                    .purpose("Tabulation").build();

            // Build CodeList
            CodeList sexCodeList = CodeList.builder().oid("CL.SEX").name("Sex").dataType("text")
                    .build();

            // Build MetaDataVersion
            MetaDataVersion mdv = MetaDataVersion.builder().oid("MDV.SDTM.3.3")
                    .name("Study Metadata Version 1.0")
                    .description("SDTM-IG 3.3 conformant metadata").defineVersion("2.1.0")
                    .standardName("CDISC SDTM").standardVersion("1.8")
                    .itemGroupDefs(Collections.singletonList(dmDataset))
                    .itemDefs(Arrays.asList(usubjidDef, ageDef))
                    .codeLists(Collections.singletonList(sexCodeList)).build();

            // Build Study
            Study study = Study.builder().oid("STUDY.SDTM.001").globalVariables(globalVars)
                    .metaDataVersions(Collections.singletonList(mdv)).build();

            // Build ODM
            ODM odm = ODM.builder().fileOID("ODM.SDTM.DEFINE.001").fileType("Snapshot")
                    .odmVersion("1.3.2").creationDateTime("2025-01-15T10:00:00")
                    .sourceSystem("Test System").sourceSystemVersion("1.0.0")
                    .studies(Collections.singletonList(study)).build();

            // Verify top-level ODM
            assertEquals("ODM.SDTM.DEFINE.001", odm.getFileOID());
            assertEquals("Snapshot", odm.getFileType());
            assertNotNull(odm.getStudies());
            assertEquals(1, odm.getStudies().size());

            // Verify Study
            Study retrievedStudy = odm.getStudies().get(0);
            assertEquals("STUDY.SDTM.001", retrievedStudy.getOid());
            assertNotNull(retrievedStudy.getGlobalVariables());
            assertEquals("CDISC SDTM Study", retrievedStudy.getGlobalVariables().getStudyName());

            // Verify MetaDataVersion
            assertNotNull(retrievedStudy.getMetaDataVersions());
            assertEquals(1, retrievedStudy.getMetaDataVersions().size());
            MetaDataVersion retrievedMdv = retrievedStudy.getMetaDataVersions().get(0);
            assertEquals("MDV.SDTM.3.3", retrievedMdv.getOid());
            assertEquals("2.1.0", retrievedMdv.getDefineVersion());

            // Verify ItemGroupDefs
            assertNotNull(retrievedMdv.getItemGroupDefs());
            assertEquals(1, retrievedMdv.getItemGroupDefs().size());
            assertEquals("DM", retrievedMdv.getItemGroupDefs().get(0).getDomain());

            // Verify ItemDefs
            assertNotNull(retrievedMdv.getItemDefs());
            assertEquals(2, retrievedMdv.getItemDefs().size());

            // Verify CodeLists
            assertNotNull(retrievedMdv.getCodeLists());
            assertEquals(1, retrievedMdv.getCodeLists().size());
            assertEquals("CL.SEX", retrievedMdv.getCodeLists().get(0).getOid());
        }


        @Test
        void testODMWithMultipleStudies()
        {
            Study study1 = Study.builder().oid("STUDY-001")
                    .globalVariables(GlobalVariables.builder().studyName("Study One").build())
                    .build();

            Study study2 = Study.builder().oid("STUDY-002")
                    .globalVariables(GlobalVariables.builder().studyName("Study Two").build())
                    .build();

            Study study3 = Study.builder().oid("STUDY-003")
                    .globalVariables(GlobalVariables.builder().studyName("Study Three").build())
                    .build();

            ODM odm = ODM.builder().fileOID("MULTI-STUDY-ODM")
                    .studies(Arrays.asList(study1, study2, study3)).build();

            assertEquals(3, odm.getStudies().size());
            assertEquals("Study One", odm.getStudies().get(0).getGlobalVariables().getStudyName());
            assertEquals("Study Two", odm.getStudies().get(1).getGlobalVariables().getStudyName());
            assertEquals("Study Three",
                    odm.getStudies().get(2).getGlobalVariables().getStudyName());
        }


        @Test
        void testMetaDataVersionWithCompleteStructure()
        {
            // Build Include
            Include include = Include.builder().studyOID("PARENT-STUDY")
                    .metaDataVersionOID("PARENT-MDV").build();

            // Build Protocol
            Protocol protocol = Protocol.builder().studyEventRefs(Collections.emptyList()).build();

            // Build AnnotatedCRF
            AnnotatedCRF annotatedCRF = AnnotatedCRF.builder().build();

            // Build SupplementalDoc
            SupplementalDoc supplementalDoc = SupplementalDoc.builder().build();

            Standards stds = Standards.builder().standards(Collections.emptyList()).build();

            // Build complete MetaDataVersion
            MetaDataVersion mdv = MetaDataVersion.builder().oid("MDV-COMPLETE")
                    .name("Complete MetaData").description("A fully populated metadata version")
                    .defineVersion("2.1.0").standardName("SDTM-IG").standardVersion("3.4")
                    .include(include).protocol(protocol).annotatedCRF(annotatedCRF)
                    .supplementalDoc(supplementalDoc).studyEventDefs(Collections.emptyList())
                    .formDefs(Collections.emptyList()).itemGroupDefs(Collections.emptyList())
                    .itemDefs(Collections.emptyList()).codeLists(Collections.emptyList())
                    .methodDefs(Collections.emptyList()).commentDefs(Collections.emptyList())
                    .whereClauseDefs(Collections.emptyList()).valueListDefs(Collections.emptyList())
                    .leaves(Collections.emptyList()).standards(stds).build();

            // Verify all properties
            assertEquals("MDV-COMPLETE", mdv.getOid());
            assertEquals("Complete MetaData", mdv.getName());
            assertNotNull(mdv.getInclude());
            assertEquals("PARENT-STUDY", mdv.getInclude().getStudyOID());
            assertNotNull(mdv.getProtocol());
            assertNotNull(mdv.getAnnotatedCRF());
            assertNotNull(mdv.getSupplementalDoc());
            assertNotNull(mdv.getStudyEventDefs());
            assertNotNull(mdv.getFormDefs());
            assertNotNull(mdv.getItemGroupDefs());
            assertNotNull(mdv.getItemDefs());
            assertNotNull(mdv.getCodeLists());
        }
    }

    // ========================
    // Supporting Bean Tests
    // ========================


    @Nested
    class SupportingBeanTests
    {

        @Test
        void testIncludeBuilder()
        {
            Include include = Include.builder().studyOID("STUDY-REF").metaDataVersionOID("MDV-REF")
                    .build();

            assertEquals("STUDY-REF", include.getStudyOID());
            assertEquals("MDV-REF", include.getMetaDataVersionOID());
        }


        @Test
        void testProtocolBuilder()
        {
            Protocol protocol = Protocol.builder().studyEventRefs(Collections.emptyList())
                    .aliases(Collections.emptyList()).build();

            assertNotNull(protocol.getStudyEventRefs());
            assertNotNull(protocol.getAliases());
        }


        @Test
        void testItemGroupDefBuilder()
        {
            ItemGroupDef igd = ItemGroupDef.builder().oid("IG.TEST").name("TEST").repeating("Yes")
                    .isReferenceData("No").sasDatasetName("TEST").domain("TEST")
                    .purpose("Tabulation").hasNoData("No").structure("One record per subject")
                    .clazz("EVENTS").archiveLocationID("LF.TEST").commentOID("COM.001")
                    .label("Test Dataset Label").domainKeys("STUDYID, USUBJID")
                    .standardOID("STD.001").build();

            assertEquals("IG.TEST", igd.getOid());
            assertEquals("TEST", igd.getName());
            assertEquals("Yes", igd.getRepeating());
            assertEquals("No", igd.getIsReferenceData());
            assertEquals("Tabulation", igd.getPurpose());
            assertEquals("EVENTS", igd.getClazz());
            assertEquals("Test Dataset Label", igd.getLabel());
        }


        @Test
        void testItemDefBuilder()
        {
            ItemDef itemDef = ItemDef.builder().oid("IT.DM.SUBJID").name("SUBJID").dataType("text")
                    .length(8).significantDigits(null).sasFieldName("SUBJID").displayFormat("$8.")
                    .commentOID("COM.SUBJID").origin("CRF").standardOID("STD.001")
                    .label("Subject Identifier").build();

            assertEquals("IT.DM.SUBJID", itemDef.getOid());
            assertEquals("SUBJID", itemDef.getName());
            assertEquals("text", itemDef.getDataType());
            assertEquals(Integer.valueOf(8), itemDef.getLength());
            assertNull(itemDef.getSignificantDigits());
            assertEquals("$8.", itemDef.getDisplayFormat());
            assertEquals("Subject Identifier", itemDef.getLabel());
        }


        @Test
        void testItemDefWithIntegerType()
        {
            ItemDef itemDef = ItemDef.builder().oid("IT.DM.AGE").name("AGE").dataType("integer")
                    .length(3).significantDigits(0).build();

            assertEquals("integer", itemDef.getDataType());
            assertEquals(Integer.valueOf(3), itemDef.getLength());
            assertEquals(Integer.valueOf(0), itemDef.getSignificantDigits());
        }


        @Test
        void testCodeListBuilder()
        {
            CodeList codeList = CodeList.builder().oid("CL.YES_NO").name("Yes No Response")
                    .dataType("text").build();

            assertEquals("CL.YES_NO", codeList.getOid());
            assertEquals("Yes No Response", codeList.getName());
            assertEquals("text", codeList.getDataType());
        }


        @Test
        void testMethodDefBuilder()
        {
            MethodDef methodDef = MethodDef.builder().oid("MT.DERIVE.AAGE")
                    .name("Algorithm for AAGE derivation").type("Computation").build();

            assertEquals("MT.DERIVE.AAGE", methodDef.getOid());
            assertEquals("Algorithm for AAGE derivation", methodDef.getName());
            assertEquals("Computation", methodDef.getType());
        }


        @Test
        void testLeafBuilder()
        {
            Leaf leaf = Leaf.builder().id("LF.DM").href("dm.xpt").build();

            assertEquals("LF.DM", leaf.getId());
            assertEquals("dm.xpt", leaf.getHref());
        }


        @Test
        void testStandardBuilder()
        {
            Standard standard = Standard.builder().oid("STD.SDTM.IG")
                    .name("SDTM Implementation Guide").type("IG").version("3.4").build();

            assertEquals("STD.SDTM.IG", standard.getOid());
            assertEquals("SDTM Implementation Guide", standard.getName());
            assertEquals("IG", standard.getType());
            assertEquals("3.4", standard.getVersion());
        }
    }

    // ========================
    // Edge Case Tests
    // ========================


    @Nested
    class EdgeCaseTests
    {

        @Test
        void testODMBuilderCreatesNewInstance()
        {
            ODM.ODMBuilder builder = ODM.builder();
            builder.fileOID("test-1");
            ODM odm1 = builder.build();

            builder.fileOID("test-2");
            ODM odm2 = builder.build();

            // Verify they are different objects with different values
            assertEquals("test-1", odm1.getFileOID());
            assertEquals("test-2", odm2.getFileOID());
        }


        @Test
        void testStudyBuilderCreatesNewInstance()
        {
            Study.StudyBuilder builder = Study.builder();
            builder.oid("study-1");
            Study study1 = builder.build();

            builder.oid("study-2");
            Study study2 = builder.build();

            assertEquals("study-1", study1.getOid());
            assertEquals("study-2", study2.getOid());
        }


        @Test
        void testListModificationDoesNotAffectOriginal()
        {
            List<Study> studies = Arrays.asList(Study.builder().oid("S1").build(),
                    Study.builder().oid("S2").build());

            ODM odm = ODM.builder().fileOID("test").studies(studies).build();

            // The @Value annotation makes the class immutable,
            // so the list returned should be the same reference
            assertEquals(2, odm.getStudies().size());
        }


        @Test
        void testVeryLongStringValues()
        {
            String longString = "A".repeat(10000);

            GlobalVariables gv = GlobalVariables.builder().studyName(longString)
                    .studyDescription(longString).protocolName(longString).build();

            assertEquals(10000, gv.getStudyName().length());
            assertEquals(10000, gv.getStudyDescription().length());
            assertEquals(10000, gv.getProtocolName().length());
        }


        @Test
        void testUnicodeStringValues()
        {
            GlobalVariables gv = GlobalVariables.builder().studyName("Internationale Studie")
                    .studyDescription("Etude clinique randomisee")
                    .protocolName("Protocol-Alpha-Beta-Gamma").build();

            assertEquals("Internationale Studie", gv.getStudyName());
            assertEquals("Etude clinique randomisee", gv.getStudyDescription());
        }
    }
}
