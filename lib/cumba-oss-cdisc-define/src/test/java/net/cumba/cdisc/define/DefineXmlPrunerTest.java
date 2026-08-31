package net.cumba.cdisc.define;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DefineXmlPrunerTest
{

    private static final String MINIMAL_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ODM xmlns="http://www.cdisc.org/ns/odm/v1.3"
                 xmlns:def="http://www.cdisc.org/ns/def/v2.0"
                 FileOID="test" FileType="Snapshot" ODMVersion="1.3.2">
              <Study OID="S1">
                <MetaDataVersion OID="MDV1" Name="V1">
                  <ItemGroupDef OID="IG.DM" Name="DM" Domain="DM">
                    <ItemRef ItemOID="IT.USUBJID" OrderNumber="1" Mandatory="Yes" />
                    <ItemRef ItemOID="IT.AGE" OrderNumber="2" Mandatory="No" MethodOID="MT.AGE" />
                  </ItemGroupDef>
                  <ItemGroupDef OID="IG.AE" Name="AE" Domain="AE">
                    <ItemRef ItemOID="IT.AETERM" OrderNumber="1" Mandatory="Yes" />
                  </ItemGroupDef>
                  <ItemDef OID="IT.USUBJID" Name="USUBJID" DataType="text" Length="20">
                    <CodeListRef CodeListOID="CL.SUBJ" />
                  </ItemDef>
                  <ItemDef OID="IT.AGE" Name="AGE" DataType="integer" def:CommentOID="COM.AGE" />
                  <ItemDef OID="IT.AETERM" Name="AETERM" DataType="text" />
                  <CodeList OID="CL.SUBJ" Name="Subject" DataType="text" />
                  <CodeList OID="CL.UNUSED" Name="Unused" DataType="text" />
                  <MethodDef OID="MT.AGE" Name="Age Derivation" Type="Computation" />
                  <MethodDef OID="MT.UNUSED" Name="Unused Method" Type="Computation" />
                  <def:CommentDef OID="COM.AGE">
                    <Description><TranslatedText>Age comment</TranslatedText></Description>
                  </def:CommentDef>
                  <def:CommentDef OID="COM.UNUSED">
                    <Description><TranslatedText>Unused comment</TranslatedText></Description>
                  </def:CommentDef>
                </MetaDataVersion>
              </Study>
            </ODM>
            """;

    private DefineXmlPruner createPruner(String xml) throws Exception
    {
        return DefineXmlPruner
                .forInputStream(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    @Nested
    class FactoryTests
    {

        @Test
        void testForInputStream() throws Exception
        {
            DefineXmlPruner pruner = createPruner(MINIMAL_XML);
            assertNotNull(pruner);
            assertNotNull(pruner.getDocument());
        }
    }


    @Nested
    class RemoveTests
    {

        @Test
        void testRemoveItemGroups() throws Exception
        {
            DefineXmlPruner pruner = createPruner(MINIMAL_XML);
            pruner.removeItemGroups("IG.AE");

            List<String> log = pruner.getLog();
            assertTrue(log.stream().anyMatch(l -> l.contains("Removed ItemGroupDef: IG.AE")));
        }


        @Test
        void testRemoveItemGroups_notFound() throws Exception
        {
            DefineXmlPruner pruner = createPruner(MINIMAL_XML);
            pruner.removeItemGroups("IG.NONEXISTENT");

            List<String> log = pruner.getLog();
            assertTrue(log.stream()
                    .anyMatch(l -> l.contains("ItemGroupDef not found: IG.NONEXISTENT")));
        }


        @Test
        void testRemoveItemDefs() throws Exception
        {
            DefineXmlPruner pruner = createPruner(MINIMAL_XML);
            pruner.removeItemDefs("IT.AETERM");

            List<String> log = pruner.getLog();
            assertTrue(log.stream().anyMatch(l -> l.contains("Removed ItemDef: IT.AETERM")));
        }


        @Test
        void testRemoveMethodDefs() throws Exception
        {
            DefineXmlPruner pruner = createPruner(MINIMAL_XML);
            pruner.removeMethodDefs("MT.UNUSED");

            List<String> log = pruner.getLog();
            assertTrue(log.stream().anyMatch(l -> l.contains("Removed MethodDef: MT.UNUSED")));
        }


        @Test
        void testRemoveCodeLists() throws Exception
        {
            DefineXmlPruner pruner = createPruner(MINIMAL_XML);
            pruner.removeCodeLists("CL.UNUSED");

            List<String> log = pruner.getLog();
            assertTrue(log.stream().anyMatch(l -> l.contains("Removed CodeList: CL.UNUSED")));
        }


        @Test
        void testRemoveCommentDefs() throws Exception
        {
            DefineXmlPruner pruner = createPruner(MINIMAL_XML);
            pruner.removeCommentDefs("COM.UNUSED");

            List<String> log = pruner.getLog();
            assertTrue(log.stream().anyMatch(l -> l.contains("Removed CommentDef: COM.UNUSED")));
        }
    }


    @Nested
    class CascadeTests
    {

        @Test
        void testCascadeOrphans_removesOrphanedItemDefs() throws Exception
        {
            DefineXmlPruner pruner = createPruner(MINIMAL_XML);
            // Remove AE group, which should orphan IT.AETERM
            pruner.removeItemGroups("IG.AE");
            pruner.cascadeOrphans();

            List<String> log = pruner.getLog();
            assertTrue(
                    log.stream().anyMatch(l -> l.contains("Removed orphaned ItemDef: IT.AETERM")),
                    "Should remove orphaned IT.AETERM. Log: " + log);
        }


        @Test
        void testCascadeOrphans_removesOrphanedMethodDefs() throws Exception
        {
            // MT.UNUSED is not referenced by any ItemRef
            DefineXmlPruner pruner = createPruner(MINIMAL_XML);
            pruner.cascadeOrphans();

            List<String> log = pruner.getLog();
            assertTrue(
                    log.stream().anyMatch(l -> l.contains("Removed orphaned MethodDef: MT.UNUSED")),
                    "Should remove orphaned MT.UNUSED. Log: " + log);
        }


        @Test
        void testCascadeOrphans_removesOrphanedCodeLists() throws Exception
        {
            // CL.UNUSED is not referenced by any CodeListRef
            DefineXmlPruner pruner = createPruner(MINIMAL_XML);
            pruner.cascadeOrphans();

            List<String> log = pruner.getLog();
            assertTrue(
                    log.stream().anyMatch(l -> l.contains("Removed orphaned CodeList: CL.UNUSED")),
                    "Should remove orphaned CL.UNUSED. Log: " + log);
        }


        @Test
        void testCascadeOrphans_logContainsPassInfo() throws Exception
        {
            DefineXmlPruner pruner = createPruner(MINIMAL_XML);
            pruner.cascadeOrphans();

            List<String> log = pruner.getLog();
            assertTrue(log.stream().anyMatch(l -> l.startsWith("Cascade pass")));
        }
    }


    @Nested
    class OutputTests
    {

        @Test
        void testToByteArray() throws Exception
        {
            DefineXmlPruner pruner = createPruner(MINIMAL_XML);
            byte[] bytes = pruner.toByteArray();

            assertNotNull(bytes);
            assertTrue(bytes.length > 0);

            String output = new String(bytes, StandardCharsets.UTF_8);
            assertTrue(output.contains("ODM"));
        }


        @Test
        void testWriteToOutputStream() throws Exception
        {
            DefineXmlPruner pruner = createPruner(MINIMAL_XML);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DefineXmlPruner result = pruner.writeTo(baos);

            // Fluent API returns same instance
            assertSame(pruner, result);
            assertTrue(baos.size() > 0);
        }


        @Test
        void testOutputAfterRemoval() throws Exception
        {
            DefineXmlPruner pruner = createPruner(MINIMAL_XML);
            pruner.removeItemGroups("IG.AE");

            String output = new String(pruner.toByteArray(), StandardCharsets.UTF_8);

            // IG.AE should be gone
            assertFalse(output.contains("IG.AE"));
            // IG.DM should still be there
            assertTrue(output.contains("IG.DM"));
        }


        private void assertSame(DefineXmlPruner expected, DefineXmlPruner actual)
        {
            assertEquals(expected, actual);
        }
    }


    @Nested
    class FluentApiTests
    {

        @Test
        void testChainedCalls() throws Exception
        {
            DefineXmlPruner pruner = createPruner(MINIMAL_XML);
            DefineXmlPruner result = pruner.removeItemGroups("IG.AE").removeCodeLists("CL.UNUSED")
                    .removeMethodDefs("MT.UNUSED").cascadeOrphans();

            assertNotNull(result);
            // All operations should have been logged
            List<String> log = result.getLog();
            assertTrue(log.size() >= 3);
        }
    }


    @Nested
    class GetLogTests
    {

        @Test
        void testGetLog_initiallyEmpty() throws Exception
        {
            DefineXmlPruner pruner = createPruner(MINIMAL_XML);
            List<String> log = pruner.getLog();
            assertNotNull(log);
            assertTrue(log.isEmpty());
        }


        @Test
        void testGetLog_isUnmodifiable() throws Exception
        {
            DefineXmlPruner pruner = createPruner(MINIMAL_XML);
            List<String> log = pruner.getLog();

            try
            {
                log.add("should fail");
                // If we got here, the list accepted the add — the externally
                // exposed log must not have grown (defensive copy is also acceptable).
                assertTrue(pruner.getLog().isEmpty(),
                        "Mutating the returned log must not affect the pruner's internal log");
            }
            catch (UnsupportedOperationException _)
            {
                // Expected for a truly unmodifiable list
                assertTrue(pruner.getLog().isEmpty());
            }
        }
    }


    @Nested
    class DefineVersionDetectionTests
    {

        @Test
        void testDetectsDefine20Namespace() throws Exception
        {
            DefineXmlPruner pruner = createPruner(MINIMAL_XML);
            // If it parsed without error, namespace detection worked
            assertNotNull(pruner.getDocument());
        }


        @Test
        void testDetectsDefine21Namespace() throws Exception
        {
            String xml21 = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <ODM xmlns="http://www.cdisc.org/ns/odm/v1.3"
                         xmlns:def="http://www.cdisc.org/ns/def/v2.1"
                         FileOID="test21">
                      <Study OID="S1">
                        <MetaDataVersion OID="MDV1" Name="V1">
                          <ItemGroupDef OID="IG.DM" Name="DM" />
                        </MetaDataVersion>
                      </Study>
                    </ODM>
                    """;

            DefineXmlPruner pruner = createPruner(xml21);
            assertNotNull(pruner.getDocument());
        }
    }
}
