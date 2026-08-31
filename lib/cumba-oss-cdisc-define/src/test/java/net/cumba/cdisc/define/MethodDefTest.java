package net.cumba.cdisc.define;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class MethodDefTest
{

    // =========================================================================
    // MethodDef Tests
    // =========================================================================

    @Nested
    class MethodDefTests
    {

        @Test
        void testBuilderWithAllFields()
        {
            Description desc = new Description();
            desc.setValue("Test description");

            FormalExpression expr = new FormalExpression();
            expr.setValue("USUBJID");
            expr.setContext("SAS");

            DocumentRef docRef = DocumentRef.builder().leafID("LF.blankcrf").build();

            MethodDef methodDef = MethodDef.builder().oid("MT.001").name("Derivation Method")
                    .type("Computation").commentOID("COM.001").description(desc)
                    .formalExpressions(Collections.singletonList(expr))
                    .documentRefs(Collections.singletonList(docRef)).build();

            assertEquals("MT.001", methodDef.getOid());
            assertEquals("Derivation Method", methodDef.getName());
            assertEquals("Computation", methodDef.getType());
            assertEquals("COM.001", methodDef.getCommentOID());
            assertNotNull(methodDef.getDescription());
            assertEquals("Test description", methodDef.getDescription().getValue());
            assertEquals(1, methodDef.getFormalExpressions().size());
            assertEquals(1, methodDef.getDocumentRefs().size());
        }


        @Test
        void testBuilderWithMinimalFields()
        {
            MethodDef methodDef = MethodDef.builder().oid("MT.002").name("Simple Method").build();

            assertEquals("MT.002", methodDef.getOid());
            assertEquals("Simple Method", methodDef.getName());
            assertNull(methodDef.getType());
            assertNull(methodDef.getCommentOID());
            assertNull(methodDef.getDescription());
            assertNull(methodDef.getFormalExpressions());
            assertNull(methodDef.getDocumentRefs());
        }


        @Test
        void testBuilderWithMultipleFormalExpressions()
        {
            FormalExpression expr1 = new FormalExpression();
            expr1.setValue("AGE = TRTEDT - BRTHDTC");
            expr1.setContext("SAS");

            FormalExpression expr2 = new FormalExpression();
            expr2.setValue("AGE := TRTEDT - BRTHDTC");
            expr2.setContext("Python");

            MethodDef methodDef = MethodDef.builder().oid("MT.003").name("Age Calculation")
                    .formalExpressions(Arrays.asList(expr1, expr2)).build();

            assertEquals(2, methodDef.getFormalExpressions().size());
            assertEquals("SAS", methodDef.getFormalExpressions().get(0).getContext());
            assertEquals("Python", methodDef.getFormalExpressions().get(1).getContext());
        }
    }

    // =========================================================================
    // CommentDef Tests
    // =========================================================================


    @Nested
    class CommentDefTests
    {

        @Test
        void testBuilderWithAllFields()
        {
            Description desc = new Description();
            desc.setValue("Comment description");

            DocumentRef docRef = DocumentRef.builder().leafID("LF.doc1").build();

            CommentDef commentDef = CommentDef.builder().oid("COM.001").description(desc)
                    .documentRefs(Collections.singletonList(docRef)).build();

            assertEquals("COM.001", commentDef.getOid());
            assertNotNull(commentDef.getDescription());
            assertEquals("Comment description", commentDef.getDescription().getValue());
            assertEquals(1, commentDef.getDocumentRefs().size());
        }


        @Test
        void testBuilderWithMinimalFields()
        {
            CommentDef commentDef = CommentDef.builder().oid("COM.002").build();

            assertEquals("COM.002", commentDef.getOid());
            assertNull(commentDef.getDescription());
            assertNull(commentDef.getDocumentRefs());
        }
    }

    // =========================================================================
    // DocumentRef Tests
    // =========================================================================


    @Nested
    class DocumentRefTests
    {

        @Test
        void testBuilderWithAllFields()
        {
            PDFPageRef pageRef = PDFPageRef.builder().pageRefs("1-5").type("PhysicalRef")
                    .firstPage(1).lastPage(5).title("Introduction").build();

            DocumentRef documentRef = DocumentRef.builder().leafID("LF.blankcrf")
                    .pdfPageRefs(Collections.singletonList(pageRef)).build();

            assertEquals("LF.blankcrf", documentRef.getLeafID());
            assertNotNull(documentRef.getPdfPageRefs());
            assertEquals(1, documentRef.getPdfPageRefs().size());
        }


        @Test
        void testBuilderWithMinimalFields()
        {
            DocumentRef documentRef = DocumentRef.builder().leafID("LF.doc123").build();

            assertEquals("LF.doc123", documentRef.getLeafID());
            assertNull(documentRef.getPdfPageRefs());
        }


        @Test
        void testBuilderWithMultiplePageRefs()
        {
            PDFPageRef pageRef1 = PDFPageRef.builder().pageRefs("1-10").type("PhysicalRef").build();

            PDFPageRef pageRef2 = PDFPageRef.builder().pageRefs("20-30").type("NamedDestination")
                    .build();

            DocumentRef documentRef = DocumentRef.builder().leafID("LF.protocol")
                    .pdfPageRefs(Arrays.asList(pageRef1, pageRef2)).build();

            assertEquals(2, documentRef.getPdfPageRefs().size());
            assertEquals("PhysicalRef", documentRef.getPdfPageRefs().get(0).getType());
            assertEquals("NamedDestination", documentRef.getPdfPageRefs().get(1).getType());
        }
    }

    // =========================================================================
    // PDFPageRef Tests
    // =========================================================================


    @Nested
    class PDFPageRefTests
    {

        @Test
        void testBuilderWithAllFields()
        {
            PDFPageRef pageRef = PDFPageRef.builder().pageRefs("10-20").type("PhysicalRef")
                    .firstPage(10).lastPage(20).title("Section A").build();

            assertEquals("10-20", pageRef.getPageRefs());
            assertEquals("PhysicalRef", pageRef.getType());
            assertEquals(Integer.valueOf(10), pageRef.getFirstPage());
            assertEquals(Integer.valueOf(20), pageRef.getLastPage());
            assertEquals("Section A", pageRef.getTitle());
        }


        @Test
        void testBuilderWithMinimalFields()
        {
            PDFPageRef pageRef = PDFPageRef.builder().type("NamedDestination").build();

            assertEquals("NamedDestination", pageRef.getType());
            assertNull(pageRef.getPageRefs());
            assertNull(pageRef.getFirstPage());
            assertNull(pageRef.getLastPage());
            assertNull(pageRef.getTitle());
        }


        @Test
        void testBuilderWithSinglePage()
        {
            PDFPageRef pageRef = PDFPageRef.builder().pageRefs("5").type("PhysicalRef").firstPage(5)
                    .lastPage(5).build();

            assertEquals("5", pageRef.getPageRefs());
            assertEquals(Integer.valueOf(5), pageRef.getFirstPage());
            assertEquals(Integer.valueOf(5), pageRef.getLastPage());
        }
    }

    // =========================================================================
    // FormalExpression Tests
    // =========================================================================


    @Nested
    class FormalExpressionTests
    {

        @Test
        void testSettersAndGetters()
        {
            FormalExpression expr = new FormalExpression();
            expr.setValue("AETERM = 'HEADACHE'");
            expr.setContext("SAS");

            assertEquals("AETERM = 'HEADACHE'", expr.getValue());
            assertEquals("SAS", expr.getContext());
        }


        @Test
        void testDefaultConstructor()
        {
            FormalExpression expr = new FormalExpression();

            assertNull(expr.getValue());
            assertNull(expr.getContext());
        }


        @Test
        void testDifferentContexts()
        {
            FormalExpression sasFormal = new FormalExpression();
            sasFormal.setValue("AGE > 18");
            sasFormal.setContext("SAS");

            FormalExpression pythonFormal = new FormalExpression();
            pythonFormal.setValue("age > 18");
            pythonFormal.setContext("Python");

            assertEquals("SAS", sasFormal.getContext());
            assertEquals("Python", pythonFormal.getContext());
        }
    }

    // =========================================================================
    // Leaf Tests
    // =========================================================================


    @Nested
    class LeafTests
    {

        @Test
        void testBuilderWithAllFields()
        {
            Title title = new Title();
            title.setValue("Blank CRF");

            Leaf leaf = Leaf.builder().id("LF.blankcrf").href("blankcrf.pdf").title(title).build();

            assertEquals("LF.blankcrf", leaf.getId());
            assertEquals("blankcrf.pdf", leaf.getHref());
            assertNotNull(leaf.getTitle());
            assertEquals("Blank CRF", leaf.getTitle().getValue());
        }


        @Test
        void testBuilderWithMinimalFields()
        {
            Leaf leaf = Leaf.builder().id("LF.001").build();

            assertEquals("LF.001", leaf.getId());
            assertNull(leaf.getHref());
            assertNull(leaf.getTitle());
        }


        @Test
        void testBuilderWithHrefOnly()
        {
            Leaf leaf = Leaf.builder().id("LF.protocol").href("protocol/protocol.pdf").build();

            assertEquals("LF.protocol", leaf.getId());
            assertEquals("protocol/protocol.pdf", leaf.getHref());
            assertNull(leaf.getTitle());
        }
    }

    // =========================================================================
    // Title Tests
    // =========================================================================


    @Nested
    class TitleTests
    {

        @Test
        void testSettersAndGetters()
        {
            Title title = new Title();
            title.setValue("Study Protocol Document");

            assertEquals("Study Protocol Document", title.getValue());
        }


        @Test
        void testDefaultConstructor()
        {
            Title title = new Title();

            assertNull(title.getValue());
        }


        @Test
        void testEmptyValue()
        {
            Title title = new Title();
            title.setValue("");

            assertEquals("", title.getValue());
        }
    }

    // =========================================================================
    // Origin Tests
    // =========================================================================


    @Nested
    class OriginTests
    {

        @Test
        void testBuilderWithAllFields()
        {
            Description desc = new Description();
            desc.setValue("Origin description");

            DocumentRef docRef = DocumentRef.builder().leafID("LF.acrf").build();

            Origin origin = Origin.builder().type("Derived").source("Sponsor")
                    .commentOID("COM.ORIGIN.001").description(desc)
                    .documentRefs(Collections.singletonList(docRef)).build();

            assertEquals("Derived", origin.getType());
            assertEquals("Sponsor", origin.getSource());
            assertEquals("COM.ORIGIN.001", origin.getCommentOID());
            assertNotNull(origin.getDescription());
            assertEquals("Origin description", origin.getDescription().getValue());
            assertEquals(1, origin.getDocumentRefs().size());
        }


        @Test
        void testBuilderWithMinimalFields()
        {
            Origin origin = Origin.builder().type("CRF").build();

            assertEquals("CRF", origin.getType());
            assertNull(origin.getSource());
            assertNull(origin.getCommentOID());
            assertNull(origin.getDescription());
            assertNull(origin.getDocumentRefs());
        }


        @Test
        void testDifferentOriginTypes()
        {
            Origin derivedOrigin = Origin.builder().type("Derived").source("Sponsor").build();

            Origin collectedOrigin = Origin.builder().type("Collected").source("CRF").build();

            Origin assignedOrigin = Origin.builder().type("Assigned").source("Sponsor").build();

            assertEquals("Derived", derivedOrigin.getType());
            assertEquals("Collected", collectedOrigin.getType());
            assertEquals("Assigned", assignedOrigin.getType());
        }
    }

    // =========================================================================
    // Alias Tests
    // =========================================================================


    @Nested
    class AliasTests
    {

        @Test
        void testBuilderWithAllFields()
        {
            Alias alias = Alias.builder().context("CDASH").name("AETERM").build();

            assertEquals("CDASH", alias.getContext());
            assertEquals("AETERM", alias.getName());
        }


        @Test
        void testBuilderWithContextOnly()
        {
            Alias alias = Alias.builder().context("SDTM").build();

            assertEquals("SDTM", alias.getContext());
            assertNull(alias.getName());
        }


        @Test
        void testBuilderWithNameOnly()
        {
            Alias alias = Alias.builder().name("USUBJID").build();

            assertNull(alias.getContext());
            assertEquals("USUBJID", alias.getName());
        }


        @Test
        void testDifferentContexts()
        {
            Alias cdashAlias = Alias.builder().context("CDASH").name("AETERM").build();

            Alias sdtmAlias = Alias.builder().context("SDTM").name("AETERM").build();

            assertEquals("CDASH", cdashAlias.getContext());
            assertEquals("SDTM", sdtmAlias.getContext());
            assertEquals(cdashAlias.getName(), sdtmAlias.getName());
        }
    }

    // =========================================================================
    // Nested Object Integration Tests
    // =========================================================================


    @Nested
    class NestedObjectIntegrationTests
    {

        @Test
        void testMethodDefWithNestedDocumentRefAndPDFPageRef()
        {
            PDFPageRef pageRef = PDFPageRef.builder().pageRefs("1-10").type("PhysicalRef")
                    .firstPage(1).lastPage(10).title("Method Documentation").build();

            DocumentRef docRef = DocumentRef.builder().leafID("LF.methods")
                    .pdfPageRefs(Collections.singletonList(pageRef)).build();

            Description desc = new Description();
            desc.setValue("Complex derivation method");

            MethodDef methodDef = MethodDef.builder().oid("MT.COMPLEX.001")
                    .name("Complex Derivation").type("Computation").description(desc)
                    .documentRefs(Collections.singletonList(docRef)).build();

            // Verify top-level
            assertEquals("MT.COMPLEX.001", methodDef.getOid());

            // Verify nested DocumentRef
            assertNotNull(methodDef.getDocumentRefs());
            assertEquals(1, methodDef.getDocumentRefs().size());
            assertEquals("LF.methods", methodDef.getDocumentRefs().get(0).getLeafID());

            // Verify deeply nested PDFPageRef
            List<PDFPageRef> pageRefs = methodDef.getDocumentRefs().get(0).getPdfPageRefs();
            assertNotNull(pageRefs);
            assertEquals(1, pageRefs.size());
            assertEquals("1-10", pageRefs.get(0).getPageRefs());
            assertEquals(Integer.valueOf(1), pageRefs.get(0).getFirstPage());
        }


        @Test
        void testLeafWithNestedTitle()
        {
            Title title = new Title();
            title.setValue("Annotated CRF Document");

            Leaf leaf = Leaf.builder().id("LF.acrf").href("documents/acrf.pdf").title(title)
                    .build();

            assertEquals("LF.acrf", leaf.getId());
            assertEquals("documents/acrf.pdf", leaf.getHref());
            assertNotNull(leaf.getTitle());
            assertEquals("Annotated CRF Document", leaf.getTitle().getValue());
        }


        @Test
        void testOriginWithNestedDescription()
        {
            Description desc = new Description();
            desc.setValue("Variable derived from collected data");
            desc.setLang("en");

            Origin origin = Origin.builder().type("Derived").source("Sponsor").description(desc)
                    .build();

            assertNotNull(origin.getDescription());
            assertEquals("Variable derived from collected data",
                    origin.getDescription().getValue());
            assertEquals("en", origin.getDescription().getLang());
        }


        @Test
        void testCommentDefWithNestedObjects()
        {
            PDFPageRef pageRef = PDFPageRef.builder().pageRefs("15").type("PhysicalRef")
                    .firstPage(15).lastPage(15).build();

            DocumentRef docRef = DocumentRef.builder().leafID("LF.define")
                    .pdfPageRefs(Collections.singletonList(pageRef)).build();

            Description desc = new Description();
            desc.setValue("Comment explaining data transformation");

            CommentDef commentDef = CommentDef.builder().oid("COM.TRANSFORM.001").description(desc)
                    .documentRefs(Collections.singletonList(docRef)).build();

            assertEquals("COM.TRANSFORM.001", commentDef.getOid());
            assertNotNull(commentDef.getDescription());
            assertEquals("Comment explaining data transformation",
                    commentDef.getDescription().getValue());
            assertNotNull(commentDef.getDocumentRefs());
            assertEquals("LF.define", commentDef.getDocumentRefs().get(0).getLeafID());
        }


        @Test
        void testMethodDefWithMultipleNestedLists()
        {
            FormalExpression expr1 = new FormalExpression();
            expr1.setValue("AGE = floor((RFSTDTC - BRTHDTC)/365.25)");
            expr1.setContext("SAS");

            FormalExpression expr2 = new FormalExpression();
            expr2.setValue("age = math.floor((rfstdtc - brthdtc).days / 365.25)");
            expr2.setContext("Python");

            PDFPageRef pageRef = PDFPageRef.builder().pageRefs("5-8").type("PhysicalRef").build();

            DocumentRef docRef1 = DocumentRef.builder().leafID("LF.sap")
                    .pdfPageRefs(Collections.singletonList(pageRef)).build();

            DocumentRef docRef2 = DocumentRef.builder().leafID("LF.protocol").build();

            Description desc = new Description();
            desc.setValue("Age calculation method");

            MethodDef methodDef = MethodDef.builder().oid("MT.AGE.001").name("Age Derivation")
                    .type("Computation").description(desc)
                    .formalExpressions(Arrays.asList(expr1, expr2))
                    .documentRefs(Arrays.asList(docRef1, docRef2)).build();

            assertEquals(2, methodDef.getFormalExpressions().size());
            assertEquals(2, methodDef.getDocumentRefs().size());

            assertEquals("SAS", methodDef.getFormalExpressions().get(0).getContext());
            assertEquals("Python", methodDef.getFormalExpressions().get(1).getContext());

            assertNotNull(methodDef.getDocumentRefs().get(0).getPdfPageRefs());
            assertNull(methodDef.getDocumentRefs().get(1).getPdfPageRefs());
        }
    }
}
