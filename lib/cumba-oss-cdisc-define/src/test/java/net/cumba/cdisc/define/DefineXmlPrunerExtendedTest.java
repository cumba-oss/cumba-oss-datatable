package net.cumba.cdisc.define;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * Extended tests for {@link DefineXmlPruner} covering def:-namespaced removal, value lists, where
 * clauses, leaf cascades, file-based factory/output methods, and DOM roundtrip preservation.
 */
class DefineXmlPrunerExtendedTest
{

    /** XML with def:-prefixed ValueListDef/WhereClauseDef/leaf — namespace v2.0 form. */
    private static final String DEF20_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ODM xmlns="http://www.cdisc.org/ns/odm/v1.3"
                 xmlns:def="http://www.cdisc.org/ns/def/v2.0"
                 FileOID="test" FileType="Snapshot" ODMVersion="1.3.2">
              <Study OID="S1">
                <MetaDataVersion OID="MDV1" Name="V1">
                  <ItemGroupDef OID="IG.DM" Name="DM" Domain="DM" def:CommentOID="COM.DM">
                    <ItemRef ItemOID="IT.USUBJID" OrderNumber="1" Mandatory="Yes" />
                    <ItemRef ItemOID="IT.AGE" OrderNumber="2" Mandatory="No" />
                    <def:leaf ID="L.IG.DM" xlink:href="dm.xpt"
                              xmlns:xlink="http://www.w3.org/1999/xlink">
                      <def:title>DM</def:title>
                    </def:leaf>
                  </ItemGroupDef>
                  <ItemDef OID="IT.USUBJID" Name="USUBJID" DataType="text" />
                  <ItemDef OID="IT.AGE" Name="AGE" DataType="integer"
                          def:ValueListOID="VL.AGE" />
                  <CodeList OID="CL.UNUSED" Name="X" DataType="text" />
                  <def:ValueListDef OID="VL.AGE">
                    <ItemRef ItemOID="IT.AGE" OrderNumber="1" Mandatory="No">
                      <def:WhereClauseRef WhereClauseOID="WC.AGE" />
                    </ItemRef>
                  </def:ValueListDef>
                  <def:ValueListDef OID="VL.UNUSED" />
                  <def:WhereClauseDef OID="WC.AGE">
                    <RangeCheck Comparator="GE" SoftHard="Soft">
                      <CheckValue>0</CheckValue>
                    </RangeCheck>
                  </def:WhereClauseDef>
                  <def:WhereClauseDef OID="WC.UNUSED" />
                  <def:CommentDef OID="COM.DM">
                    <Description><TranslatedText>x</TranslatedText></Description>
                  </def:CommentDef>
                  <def:leaf ID="L.DOC" xlink:href="doc.pdf"
                            xmlns:xlink="http://www.w3.org/1999/xlink">
                    <def:title>Doc</def:title>
                  </def:leaf>
                  <def:leaf ID="L.UNUSED" xlink:href="orphan.pdf"
                            xmlns:xlink="http://www.w3.org/1999/xlink">
                    <def:title>Orphan</def:title>
                  </def:leaf>
                </MetaDataVersion>
              </Study>
            </ODM>
            """;

    /** XML using the def: 2.1 namespace so detectDefineNamespace selects DEF21_NS. */
    private static final String DEF21_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ODM xmlns="http://www.cdisc.org/ns/odm/v1.3"
                 xmlns:def="http://www.cdisc.org/ns/def/v2.1"
                 FileOID="t21" FileType="Snapshot" ODMVersion="1.3.2">
              <Study OID="S1">
                <MetaDataVersion OID="MDV1" Name="V1">
                  <ItemGroupDef OID="IG.DM" Name="DM" Domain="DM">
                    <ItemRef ItemOID="IT.A" OrderNumber="1" Mandatory="Yes" />
                  </ItemGroupDef>
                  <ItemDef OID="IT.A" Name="A" DataType="text" />
                  <def:ValueListDef OID="VL.A" />
                  <def:WhereClauseDef OID="WC.A" />
                </MetaDataVersion>
              </Study>
            </ODM>
            """;

    private static DefineXmlPruner prunerOf(String xml) throws Exception
    {
        return DefineXmlPruner
                .forInputStream(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    // ==================== Def-namespaced explicit removal ====================


    @Test
    void removeValueListDefs_inDefNamespace_logged() throws Exception
    {
        DefineXmlPruner pruner = prunerOf(DEF20_XML);
        pruner.removeValueListDefs("VL.UNUSED");

        assertTrue(
                pruner.getLog().stream()
                        .anyMatch(l -> l.contains("Removed ValueListDef: VL.UNUSED")),
                "Log should contain ValueListDef removal entry: " + pruner.getLog());
    }


    @Test
    void removeValueListDefs_notFound_logsMessage() throws Exception
    {
        DefineXmlPruner pruner = prunerOf(DEF20_XML);
        pruner.removeValueListDefs("VL.NOTHERE");

        assertTrue(pruner.getLog().stream()
                .anyMatch(l -> l.contains("ValueListDef not found: VL.NOTHERE")));
    }


    @Test
    void removeWhereClauseDefs_inDefNamespace_logged() throws Exception
    {
        DefineXmlPruner pruner = prunerOf(DEF20_XML);
        pruner.removeWhereClauseDefs("WC.UNUSED");

        assertTrue(pruner.getLog().stream()
                .anyMatch(l -> l.contains("Removed WhereClauseDef: WC.UNUSED")));
    }


    @Test
    void removeWhereClauseDefs_notFound_logsMessage() throws Exception
    {
        DefineXmlPruner pruner = prunerOf(DEF20_XML);
        pruner.removeWhereClauseDefs("WC.NOTHERE");

        assertTrue(pruner.getLog().stream()
                .anyMatch(l -> l.contains("WhereClauseDef not found: WC.NOTHERE")));
    }


    @Test
    void removeLeafs_inDefNamespace_logged() throws Exception
    {
        DefineXmlPruner pruner = prunerOf(DEF20_XML);
        pruner.removeLeafs("L.UNUSED");

        assertTrue(pruner.getLog().stream().anyMatch(l -> l.contains("Removed leaf: L.UNUSED")));
    }


    @Test
    void removeLeafs_notFound_logsMessage() throws Exception
    {
        DefineXmlPruner pruner = prunerOf(DEF20_XML);
        pruner.removeLeafs("L.MISSING");

        assertTrue(pruner.getLog().stream().anyMatch(l -> l.contains("leaf not found: L.MISSING")));
    }


    @Test
    void removeItemDefs_notFound_logsMessage() throws Exception
    {
        DefineXmlPruner pruner = prunerOf(DEF20_XML);
        pruner.removeItemDefs("IT.MISSING");

        assertTrue(pruner.getLog().stream()
                .anyMatch(l -> l.contains("ItemDef not found: IT.MISSING")));
    }


    @Test
    void removeMethodDefs_notFound_logsMessage() throws Exception
    {
        DefineXmlPruner pruner = prunerOf(DEF20_XML);
        pruner.removeMethodDefs("MT.MISSING");

        assertTrue(pruner.getLog().stream()
                .anyMatch(l -> l.contains("MethodDef not found: MT.MISSING")));
    }


    @Test
    void removeCommentDefs_notFound_logsMessage() throws Exception
    {
        DefineXmlPruner pruner = prunerOf(DEF20_XML);
        pruner.removeCommentDefs("COM.MISSING");

        assertTrue(pruner.getLog().stream()
                .anyMatch(l -> l.contains("CommentDef not found: COM.MISSING")));
    }


    @Test
    void removeCodeLists_notFound_logsMessage() throws Exception
    {
        DefineXmlPruner pruner = prunerOf(DEF20_XML);
        pruner.removeCodeLists("CL.MISSING");

        assertTrue(pruner.getLog().stream()
                .anyMatch(l -> l.contains("CodeList not found: CL.MISSING")));
    }

    // ==================== Cascade cleanup across def: namespace ====================


    @Test
    void cascadeOrphans_removesOrphanValueListDefs() throws Exception
    {
        DefineXmlPruner pruner = prunerOf(DEF20_XML);
        pruner.cascadeOrphans();

        assertTrue(
                pruner.getLog().stream()
                        .anyMatch(l -> l.contains("Removed orphaned ValueListDef: VL.UNUSED")),
                "Log: " + pruner.getLog());
    }


    @Test
    void cascadeOrphans_removesOrphanWhereClauseDefs() throws Exception
    {
        DefineXmlPruner pruner = prunerOf(DEF20_XML);
        pruner.cascadeOrphans();

        assertTrue(
                pruner.getLog().stream()
                        .anyMatch(l -> l.contains("Removed orphaned WhereClauseDef: WC.UNUSED")),
                "Log: " + pruner.getLog());
    }


    @Test
    void cascadeOrphans_removesOrphanLeaf() throws Exception
    {
        DefineXmlPruner pruner = prunerOf(DEF20_XML);
        pruner.cascadeOrphans();

        // L.UNUSED is not referenced by any DocumentRef; L.IG.DM is inside ItemGroupDef → skipped.
        assertTrue(
                pruner.getLog().stream()
                        .anyMatch(l -> l.contains("Removed orphaned leaf: L.UNUSED")),
                "Log: " + pruner.getLog());
        // The ItemGroupDef-nested leaf must survive
        assertFalse(
                pruner.getLog().stream()
                        .anyMatch(l -> l.contains("Removed orphaned leaf: L.IG.DM")),
                "ItemGroupDef-nested leaf must be preserved: " + pruner.getLog());
    }


    @Test
    void cascadeOrphans_keepsCommentReferencedByItemGroupDef() throws Exception
    {
        DefineXmlPruner pruner = prunerOf(DEF20_XML);
        pruner.cascadeOrphans();

        // COM.DM is referenced via def:CommentOID on the ItemGroupDef → must NOT be removed.
        assertFalse(
                pruner.getLog().stream()
                        .anyMatch(l -> l.contains("Removed orphaned CommentDef: COM.DM")),
                "COM.DM is referenced and must be kept: " + pruner.getLog());
    }


    @Test
    void cascadeOrphans_chainItemGroupRemovalRemovesItsItemRefsAndDeps() throws Exception
    {
        DefineXmlPruner pruner = prunerOf(DEF20_XML);
        pruner.removeItemGroups("IG.DM").cascadeOrphans();

        List<String> log = pruner.getLog();
        // After removing IG.DM, IT.USUBJID and IT.AGE are no longer referenced (the only ItemGroup
        // was removed, and there are no other ItemRefs that reference them outside the now-removed
        // ValueListDef chain).
        assertTrue(log.stream().anyMatch(l -> l.contains("Removed orphaned ItemDef: IT.USUBJID")),
                "Expected IT.USUBJID to be orphaned. Log: " + log);
    }

    // ==================== Output & roundtrip ====================


    @Test
    void writeTo_outputStream_returnsThis() throws Exception
    {
        DefineXmlPruner pruner = prunerOf(DEF20_XML);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        DefineXmlPruner returned = pruner.writeTo(baos);

        assertSame(pruner, returned);
        assertTrue(baos.size() > 0);
    }


    @Test
    void domRoundtrip_preservesIgDmAndCorePayload() throws Exception
    {
        DefineXmlPruner pruner = prunerOf(DEF20_XML);
        byte[] bytes = pruner.toByteArray();

        // Parse the produced bytes back through a plain DOM parser and inspect.
        Document doc = parseDom(bytes);
        NodeList groups = doc.getElementsByTagNameNS("http://www.cdisc.org/ns/odm/v1.3",
                "ItemGroupDef");
        assertNotNull(groups);
        assertTrue(groups.getLength() >= 1);
    }

    // ==================== File-based factory and writeTo overloads ====================


    @Test
    void forFile_path_and_writeTo_path(@TempDir Path tempDir) throws Exception
    {
        Path inputPath = tempDir.resolve("in.xml");
        Files.writeString(inputPath, DEF20_XML, StandardCharsets.UTF_8);

        DefineXmlPruner pruner = DefineXmlPruner.forFile(inputPath);
        Path outputPath = tempDir.resolve("out.xml");
        DefineXmlPruner returned = pruner.writeTo(outputPath);

        assertSame(pruner, returned);
        assertTrue(Files.exists(outputPath));
        assertTrue(Files.size(outputPath) > 0L);
    }


    @Test
    void forFile_file_and_writeTo_file(@TempDir Path tempDir) throws Exception
    {
        File inputFile = tempDir.resolve("in2.xml").toFile();
        Files.writeString(inputFile.toPath(), DEF20_XML, StandardCharsets.UTF_8);

        DefineXmlPruner pruner = DefineXmlPruner.forFile(inputFile);
        File outputFile = tempDir.resolve("out2.xml").toFile();
        DefineXmlPruner returned = pruner.writeTo(outputFile);

        assertSame(pruner, returned);
        assertTrue(outputFile.exists());
        assertTrue(outputFile.length() > 0L);
    }

    // ==================== Define v2.1 namespace detection ====================


    @Test
    void def21Namespace_isRecognisedAndPruneable() throws Exception
    {
        DefineXmlPruner pruner = prunerOf(DEF21_XML);
        pruner.removeValueListDefs("VL.A").removeWhereClauseDefs("WC.A");

        assertTrue(pruner.getLog().stream().anyMatch(l -> l.contains("Removed ValueListDef: VL.A")),
                pruner.getLog().toString());
        assertTrue(
                pruner.getLog().stream().anyMatch(l -> l.contains("Removed WhereClauseDef: WC.A")),
                pruner.getLog().toString());
    }

    // ==================== Helpers ====================


    private static Document parseDom(byte[] bytes) throws Exception
    {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes))
        {
            return dbf.newDocumentBuilder().parse(in);
        }
    }
}
