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
 * JUnit 5 tests for bean classes in the net.cumba.datatable.define package.
 */
public class FormDefTest
{

    @Nested
    class FormDefTests
    {

        @Test
        void testBuilderWithAllFields()
        {
            Description description = new Description();
            description.setLang("en");
            description.setValue("Test Form Description");

            ItemGroupRef itemGroupRef = ItemGroupRef.builder().itemGroupOID("IG.TEST")
                    .orderNumber(1).mandatory("Yes").build();

            ArchiveLayout archiveLayout = ArchiveLayout.builder().oid("AL.001")
                    .pdfFileName("test.pdf").presentationOID("P.001").build();

            Alias alias = Alias.builder().context("SDTM").name("TESTFORM").build();

            FormDef formDef = FormDef.builder().oid("F.TEST").name("Test Form").repeating("No")
                    .description(description).itemGroupRefs(Arrays.asList(itemGroupRef))
                    .archiveLayouts(Arrays.asList(archiveLayout)).aliases(Arrays.asList(alias))
                    .build();

            assertEquals("F.TEST", formDef.getOid());
            assertEquals("Test Form", formDef.getName());
            assertEquals("No", formDef.getRepeating());
            assertNotNull(formDef.getDescription());
            assertEquals("en", formDef.getDescription().getLang());
            assertEquals(1, formDef.getItemGroupRefs().size());
            assertEquals(1, formDef.getArchiveLayouts().size());
            assertEquals(1, formDef.getAliases().size());
        }


        @Test
        void testBuilderWithMinimalFields()
        {
            FormDef formDef = FormDef.builder().oid("F.MINIMAL").name("Minimal Form").build();

            assertEquals("F.MINIMAL", formDef.getOid());
            assertEquals("Minimal Form", formDef.getName());
            assertNull(formDef.getRepeating());
            assertNull(formDef.getDescription());
            assertNull(formDef.getItemGroupRefs());
            assertNull(formDef.getArchiveLayouts());
            assertNull(formDef.getAliases());
        }


        @Test
        void testListsCanBePopulated()
        {
            ItemGroupRef ref1 = ItemGroupRef.builder().itemGroupOID("IG.1").build();
            ItemGroupRef ref2 = ItemGroupRef.builder().itemGroupOID("IG.2").build();
            List<ItemGroupRef> refs = Arrays.asList(ref1, ref2);

            FormDef formDef = FormDef.builder().oid("F.LIST").name("List Form").itemGroupRefs(refs)
                    .build();

            assertEquals(2, formDef.getItemGroupRefs().size());
            assertEquals("IG.1", formDef.getItemGroupRefs().get(0).getItemGroupOID());
            assertEquals("IG.2", formDef.getItemGroupRefs().get(1).getItemGroupOID());
        }


        @Test
        void testEmptyLists()
        {
            FormDef formDef = FormDef.builder().oid("F.EMPTY").name("Empty Lists Form")
                    .itemGroupRefs(Collections.emptyList()).archiveLayouts(Collections.emptyList())
                    .aliases(Collections.emptyList()).build();

            assertNotNull(formDef.getItemGroupRefs());
            assertTrue(formDef.getItemGroupRefs().isEmpty());
            assertNotNull(formDef.getArchiveLayouts());
            assertTrue(formDef.getArchiveLayouts().isEmpty());
            assertNotNull(formDef.getAliases());
            assertTrue(formDef.getAliases().isEmpty());
        }
    }


    @Nested
    class FormRefTests
    {

        @Test
        void testBuilderWithAllFields()
        {
            FormRef formRef = FormRef.builder().formOID("F.TEST").orderNumber(10).mandatory("Yes")
                    .build();

            assertEquals("F.TEST", formRef.getFormOID());
            assertEquals(Integer.valueOf(10), formRef.getOrderNumber());
            assertEquals("Yes", formRef.getMandatory());
        }


        @Test
        void testBuilderWithMinimalFields()
        {
            FormRef formRef = FormRef.builder().formOID("F.MINIMAL").build();

            assertEquals("F.MINIMAL", formRef.getFormOID());
            assertNull(formRef.getOrderNumber());
            assertNull(formRef.getMandatory());
        }


        @Test
        void testOrderNumberAsInteger()
        {
            FormRef formRef = FormRef.builder().formOID("F.ORDER").orderNumber(Integer.valueOf(99))
                    .build();

            assertEquals(99, formRef.getOrderNumber().intValue());
        }
    }


    @Nested
    class ItemGroupRefTests
    {

        @Test
        void testBuilderWithAllFields()
        {
            ItemGroupRef ref = ItemGroupRef.builder().itemGroupOID("IG.TEST").orderNumber(5)
                    .mandatory("No").build();

            assertEquals("IG.TEST", ref.getItemGroupOID());
            assertEquals(Integer.valueOf(5), ref.getOrderNumber());
            assertEquals("No", ref.getMandatory());
        }


        @Test
        void testBuilderWithMinimalFields()
        {
            ItemGroupRef ref = ItemGroupRef.builder().itemGroupOID("IG.MINIMAL").build();

            assertEquals("IG.MINIMAL", ref.getItemGroupOID());
            assertNull(ref.getOrderNumber());
            assertNull(ref.getMandatory());
        }
    }


    @Nested
    class StudyEventDefTests
    {

        @Test
        void testBuilderWithAllFields()
        {
            Description description = new Description();
            description.setLang("en");
            description.setValue("Study Event Description");

            FormRef formRef = FormRef.builder().formOID("F.TEST").orderNumber(1).mandatory("Yes")
                    .build();

            Alias alias = Alias.builder().context("CDASH").name("SE_ALIAS").build();

            StudyEventDef eventDef = StudyEventDef.builder().oid("SE.TEST").name("Test Event")
                    .repeating("Yes").type("Scheduled").description(description)
                    .formRefs(Arrays.asList(formRef)).aliases(Arrays.asList(alias)).build();

            assertEquals("SE.TEST", eventDef.getOid());
            assertEquals("Test Event", eventDef.getName());
            assertEquals("Yes", eventDef.getRepeating());
            assertEquals("Scheduled", eventDef.getType());
            assertNotNull(eventDef.getDescription());
            assertEquals(1, eventDef.getFormRefs().size());
            assertEquals(1, eventDef.getAliases().size());
        }


        @Test
        void testBuilderWithMinimalFields()
        {
            StudyEventDef eventDef = StudyEventDef.builder().oid("SE.MINIMAL").name("Minimal Event")
                    .build();

            assertEquals("SE.MINIMAL", eventDef.getOid());
            assertEquals("Minimal Event", eventDef.getName());
            assertNull(eventDef.getRepeating());
            assertNull(eventDef.getType());
            assertNull(eventDef.getDescription());
            assertNull(eventDef.getFormRefs());
            assertNull(eventDef.getAliases());
        }


        @Test
        void testMultipleFormRefs()
        {
            FormRef ref1 = FormRef.builder().formOID("F.1").orderNumber(1).build();
            FormRef ref2 = FormRef.builder().formOID("F.2").orderNumber(2).build();
            FormRef ref3 = FormRef.builder().formOID("F.3").orderNumber(3).build();

            StudyEventDef eventDef = StudyEventDef.builder().oid("SE.MULTI")
                    .name("Multi Form Event").formRefs(Arrays.asList(ref1, ref2, ref3)).build();

            assertEquals(3, eventDef.getFormRefs().size());
            assertEquals("F.1", eventDef.getFormRefs().get(0).getFormOID());
            assertEquals("F.2", eventDef.getFormRefs().get(1).getFormOID());
            assertEquals("F.3", eventDef.getFormRefs().get(2).getFormOID());
        }
    }


    @Nested
    class StudyEventRefTests
    {

        @Test
        void testBuilderWithAllFields()
        {
            StudyEventRef ref = StudyEventRef.builder().studyEventOID("SE.TEST").orderNumber(15)
                    .mandatory("Yes").build();

            assertEquals("SE.TEST", ref.getStudyEventOID());
            assertEquals(Integer.valueOf(15), ref.getOrderNumber());
            assertEquals("Yes", ref.getMandatory());
        }


        @Test
        void testBuilderWithMinimalFields()
        {
            StudyEventRef ref = StudyEventRef.builder().studyEventOID("SE.MINIMAL").build();

            assertEquals("SE.MINIMAL", ref.getStudyEventOID());
            assertNull(ref.getOrderNumber());
            assertNull(ref.getMandatory());
        }
    }


    @Nested
    class ProtocolTests
    {

        @Test
        void testBuilderWithAllFields()
        {
            StudyEventRef eventRef = StudyEventRef.builder().studyEventOID("SE.TEST").orderNumber(1)
                    .mandatory("Yes").build();

            Alias alias = Alias.builder().context("Protocol").name("PROT_ALIAS").build();

            Protocol protocol = Protocol.builder().studyEventRefs(Arrays.asList(eventRef))
                    .aliases(Arrays.asList(alias)).build();

            assertNotNull(protocol.getStudyEventRefs());
            assertEquals(1, protocol.getStudyEventRefs().size());
            assertEquals("SE.TEST", protocol.getStudyEventRefs().get(0).getStudyEventOID());
            assertNotNull(protocol.getAliases());
            assertEquals(1, protocol.getAliases().size());
        }


        @Test
        void testBuilderWithMinimalFields()
        {
            Protocol protocol = Protocol.builder().build();

            assertNull(protocol.getStudyEventRefs());
            assertNull(protocol.getAliases());
        }


        @Test
        void testMultipleStudyEventRefs()
        {
            StudyEventRef ref1 = StudyEventRef.builder().studyEventOID("SE.1").orderNumber(1)
                    .build();
            StudyEventRef ref2 = StudyEventRef.builder().studyEventOID("SE.2").orderNumber(2)
                    .build();

            Protocol protocol = Protocol.builder().studyEventRefs(Arrays.asList(ref1, ref2))
                    .build();

            assertEquals(2, protocol.getStudyEventRefs().size());
            assertEquals("SE.1", protocol.getStudyEventRefs().get(0).getStudyEventOID());
            assertEquals("SE.2", protocol.getStudyEventRefs().get(1).getStudyEventOID());
        }


        @Test
        void testEmptyLists()
        {
            Protocol protocol = Protocol.builder().studyEventRefs(Collections.emptyList())
                    .aliases(Collections.emptyList()).build();

            assertNotNull(protocol.getStudyEventRefs());
            assertTrue(protocol.getStudyEventRefs().isEmpty());
            assertNotNull(protocol.getAliases());
            assertTrue(protocol.getAliases().isEmpty());
        }
    }


    @Nested
    class ArchiveLayoutTests
    {

        @Test
        void testBuilderWithAllFields()
        {
            ArchiveLayout layout = ArchiveLayout.builder().oid("AL.TEST")
                    .pdfFileName("document.pdf").presentationOID("PRES.001").build();

            assertEquals("AL.TEST", layout.getOid());
            assertEquals("document.pdf", layout.getPdfFileName());
            assertEquals("PRES.001", layout.getPresentationOID());
        }


        @Test
        void testBuilderWithMinimalFields()
        {
            ArchiveLayout layout = ArchiveLayout.builder().oid("AL.MINIMAL").build();

            assertEquals("AL.MINIMAL", layout.getOid());
            assertNull(layout.getPdfFileName());
            assertNull(layout.getPresentationOID());
        }
    }


    @Nested
    class IncludeTests
    {

        @Test
        void testBuilderWithAllFields()
        {
            Include include = Include.builder().studyOID("STUDY.001").metaDataVersionOID("MDV.001")
                    .build();

            assertEquals("STUDY.001", include.getStudyOID());
            assertEquals("MDV.001", include.getMetaDataVersionOID());
        }


        @Test
        void testBuilderWithMinimalFields()
        {
            Include include = Include.builder().build();

            assertNull(include.getStudyOID());
            assertNull(include.getMetaDataVersionOID());
        }


        @Test
        void testBuilderWithStudyOIDOnly()
        {
            Include include = Include.builder().studyOID("STUDY.ONLY").build();

            assertEquals("STUDY.ONLY", include.getStudyOID());
            assertNull(include.getMetaDataVersionOID());
        }


        @Test
        void testBuilderWithMetaDataVersionOIDOnly()
        {
            Include include = Include.builder().metaDataVersionOID("MDV.ONLY").build();

            assertNull(include.getStudyOID());
            assertEquals("MDV.ONLY", include.getMetaDataVersionOID());
        }
    }


    @Nested
    class StandardTests
    {

        @Test
        void testBuilderWithAllFields()
        {
            Standard standard = Standard.builder().oid("STD.CDISC").name("CDISC Standard")
                    .type("IG").publishingSet("SDTM").version("3.3").status("Final")
                    .commentOID("COM.001").build();

            assertEquals("STD.CDISC", standard.getOid());
            assertEquals("CDISC Standard", standard.getName());
            assertEquals("IG", standard.getType());
            assertEquals("SDTM", standard.getPublishingSet());
            assertEquals("3.3", standard.getVersion());
            assertEquals("Final", standard.getStatus());
            assertEquals("COM.001", standard.getCommentOID());
        }


        @Test
        void testBuilderWithMinimalFields()
        {
            Standard standard = Standard.builder().oid("STD.MINIMAL").name("Minimal Standard")
                    .build();

            assertEquals("STD.MINIMAL", standard.getOid());
            assertEquals("Minimal Standard", standard.getName());
            assertNull(standard.getType());
            assertNull(standard.getPublishingSet());
            assertNull(standard.getVersion());
            assertNull(standard.getStatus());
            assertNull(standard.getCommentOID());
        }


        @Test
        void testBuilderWithPartialFields()
        {
            Standard standard = Standard.builder().oid("STD.PARTIAL").name("Partial Standard")
                    .version("2.0").status("Draft").build();

            assertEquals("STD.PARTIAL", standard.getOid());
            assertEquals("Partial Standard", standard.getName());
            assertNull(standard.getType());
            assertNull(standard.getPublishingSet());
            assertEquals("2.0", standard.getVersion());
            assertEquals("Draft", standard.getStatus());
            assertNull(standard.getCommentOID());
        }
    }


    @Nested
    class SupplementalDocTests
    {

        @Test
        void testBuilderWithDocumentRefs()
        {
            DocumentRef docRef1 = DocumentRef.builder().leafID("DOC.001").build();
            DocumentRef docRef2 = DocumentRef.builder().leafID("DOC.002").build();

            SupplementalDoc suppDoc = SupplementalDoc.builder()
                    .documentRefs(Arrays.asList(docRef1, docRef2)).build();

            assertNotNull(suppDoc.getDocumentRefs());
            assertEquals(2, suppDoc.getDocumentRefs().size());
            assertEquals("DOC.001", suppDoc.getDocumentRefs().get(0).getLeafID());
            assertEquals("DOC.002", suppDoc.getDocumentRefs().get(1).getLeafID());
        }


        @Test
        void testBuilderWithMinimalFields()
        {
            SupplementalDoc suppDoc = SupplementalDoc.builder().build();

            assertNull(suppDoc.getDocumentRefs());
        }


        @Test
        void testEmptyDocumentRefsList()
        {
            SupplementalDoc suppDoc = SupplementalDoc.builder()
                    .documentRefs(Collections.emptyList()).build();

            assertNotNull(suppDoc.getDocumentRefs());
            assertTrue(suppDoc.getDocumentRefs().isEmpty());
        }


        @Test
        void testSingleDocumentRef()
        {
            DocumentRef docRef = DocumentRef.builder().leafID("DOC.SINGLE").build();

            SupplementalDoc suppDoc = SupplementalDoc.builder()
                    .documentRefs(Collections.singletonList(docRef)).build();

            assertEquals(1, suppDoc.getDocumentRefs().size());
            assertEquals("DOC.SINGLE", suppDoc.getDocumentRefs().get(0).getLeafID());
        }
    }


    @Nested
    class AnnotatedCRFTests
    {

        @Test
        void testBuilderWithDocumentRefs()
        {
            DocumentRef docRef1 = DocumentRef.builder().leafID("CRF.001").build();
            DocumentRef docRef2 = DocumentRef.builder().leafID("CRF.002").build();

            AnnotatedCRF annotatedCRF = AnnotatedCRF.builder()
                    .documentRefs(Arrays.asList(docRef1, docRef2)).build();

            assertNotNull(annotatedCRF.getDocumentRefs());
            assertEquals(2, annotatedCRF.getDocumentRefs().size());
            assertEquals("CRF.001", annotatedCRF.getDocumentRefs().get(0).getLeafID());
            assertEquals("CRF.002", annotatedCRF.getDocumentRefs().get(1).getLeafID());
        }


        @Test
        void testBuilderWithMinimalFields()
        {
            AnnotatedCRF annotatedCRF = AnnotatedCRF.builder().build();

            assertNull(annotatedCRF.getDocumentRefs());
        }


        @Test
        void testEmptyDocumentRefsList()
        {
            AnnotatedCRF annotatedCRF = AnnotatedCRF.builder().documentRefs(Collections.emptyList())
                    .build();

            assertNotNull(annotatedCRF.getDocumentRefs());
            assertTrue(annotatedCRF.getDocumentRefs().isEmpty());
        }


        @Test
        void testSingleDocumentRef()
        {
            DocumentRef docRef = DocumentRef.builder().leafID("CRF.SINGLE").build();

            AnnotatedCRF annotatedCRF = AnnotatedCRF.builder()
                    .documentRefs(Collections.singletonList(docRef)).build();

            assertEquals(1, annotatedCRF.getDocumentRefs().size());
            assertEquals("CRF.SINGLE", annotatedCRF.getDocumentRefs().get(0).getLeafID());
        }
    }


    @Nested
    class AliasTests
    {

        @Test
        void testBuilderWithAllFields()
        {
            Alias alias = Alias.builder().context("SDTM").name("ALIAS_NAME").build();

            assertEquals("SDTM", alias.getContext());
            assertEquals("ALIAS_NAME", alias.getName());
        }


        @Test
        void testBuilderWithMinimalFields()
        {
            Alias alias = Alias.builder().build();

            assertNull(alias.getContext());
            assertNull(alias.getName());
        }
    }


    @Nested
    class DocumentRefTests
    {

        @Test
        void testBuilderWithAllFields()
        {
            PDFPageRef pageRef = PDFPageRef.builder().pageRefs("1-10").type("PhysicalRef").build();

            DocumentRef docRef = DocumentRef.builder().leafID("LEAF.001")
                    .pdfPageRefs(Arrays.asList(pageRef)).build();

            assertEquals("LEAF.001", docRef.getLeafID());
            assertNotNull(docRef.getPdfPageRefs());
            assertEquals(1, docRef.getPdfPageRefs().size());
        }


        @Test
        void testBuilderWithMinimalFields()
        {
            DocumentRef docRef = DocumentRef.builder().leafID("LEAF.MINIMAL").build();

            assertEquals("LEAF.MINIMAL", docRef.getLeafID());
            assertNull(docRef.getPdfPageRefs());
        }
    }
}
