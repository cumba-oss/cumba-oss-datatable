package net.cumba.cdisc.define;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefineXmlParserTest
{

    private DefineXmlParser parser;

    @BeforeEach
    void setUp()
    {
        parser = new DefineXmlParser();
    }

    // ==================== Constructor ====================


    @Test
    void testConstructor()
    {
        DefineXmlParser p = new DefineXmlParser();
        assertNotNull(p);
    }

    // ==================== parse(String) ====================


    @Test
    void testParseMinimalXml() throws IOException
    {
        String xml = "<ODM FileOID=\"test\" />";

        ODM odm = parser.parse(xml);

        assertNotNull(odm);
        assertEquals("test", odm.getFileOID());
    }


    @Test
    void testParseWithFileType() throws IOException
    {
        String xml = "<ODM FileOID=\"test\" FileType=\"Snapshot\" />";

        ODM odm = parser.parse(xml);

        assertEquals("Snapshot", odm.getFileType());
    }


    @Test
    void testParseWithODMVersion() throws IOException
    {
        String xml = "<ODM ODMVersion=\"1.3.2\" />";

        ODM odm = parser.parse(xml);

        assertEquals("1.3.2", odm.getOdmVersion());
    }


    @Test
    void testParseWithCreationDateTime() throws IOException
    {
        String xml = "<ODM CreationDateTime=\"2024-01-15T10:30:00\" />";

        ODM odm = parser.parse(xml);

        assertEquals("2024-01-15T10:30:00", odm.getCreationDateTime());
    }


    @Test
    void testParseWithStudy() throws IOException
    {
        String xml = """
                <ODM>
                  <Study OID="S1">
                    <GlobalVariables>
                      <StudyName>Test Study</StudyName>
                    </GlobalVariables>
                  </Study>
                </ODM>
                """;

        ODM odm = parser.parse(xml);

        assertNotNull(odm.getStudies());
        assertEquals(1, odm.getStudies().size());
        assertEquals("S1", odm.getStudies().get(0).getOid());
        assertEquals("Test Study", odm.getStudies().get(0).getGlobalVariables().getStudyName());
    }


    @Test
    void testParseWithMetaDataVersion() throws IOException
    {
        String xml = """
                <ODM>
                  <Study OID="S1">
                    <MetaDataVersion OID="MDV1" Name="Version 1" DefineVersion="2.1" />
                  </Study>
                </ODM>
                """;

        ODM odm = parser.parse(xml);

        assertEquals(1, odm.getStudies().get(0).getMetaDataVersions().size());
        assertEquals("MDV1", odm.getStudies().get(0).getMetaDataVersions().get(0).getOid());
        assertEquals("Version 1", odm.getStudies().get(0).getMetaDataVersions().get(0).getName());
    }


    @Test
    void testParseWithItemGroupDef() throws IOException
    {
        String xml = """
                <ODM>
                  <Study OID="S1">
                    <MetaDataVersion OID="MDV1" Name="V1">
                      <ItemGroupDef OID="IG.DM" Name="DM" Domain="DM" />
                    </MetaDataVersion>
                  </Study>
                </ODM>
                """;

        ODM odm = parser.parse(xml);

        var mdv = odm.getStudies().get(0).getMetaDataVersions().get(0);
        assertEquals(1, mdv.getItemGroupDefs().size());
        assertEquals("DM", mdv.getItemGroupDefs().get(0).getName());
        assertEquals("DM", mdv.getItemGroupDefs().get(0).getDomain());
    }


    @Test
    void testParseWithItemDef() throws IOException
    {
        String xml = """
                <ODM>
                  <Study OID="S1">
                    <MetaDataVersion OID="MDV1" Name="V1">
                      <ItemDef OID="IT.USUBJID" Name="USUBJID" DataType="text" Length="20" />
                    </MetaDataVersion>
                  </Study>
                </ODM>
                """;

        ODM odm = parser.parse(xml);

        var mdv = odm.getStudies().get(0).getMetaDataVersions().get(0);
        assertEquals(1, mdv.getItemDefs().size());
        assertEquals("USUBJID", mdv.getItemDefs().get(0).getName());
        assertEquals("text", mdv.getItemDefs().get(0).getDataType());
        assertEquals(20, mdv.getItemDefs().get(0).getLength());
    }


    @Test
    void testParseWithCodeList() throws IOException
    {
        String xml = """
                <ODM>
                  <Study OID="S1">
                    <MetaDataVersion OID="MDV1" Name="V1">
                      <CodeList OID="CL.SEX" Name="SEX" DataType="text">
                        <CodeListItem CodedValue="M">
                          <Decode>
                            <TranslatedText>Male</TranslatedText>
                          </Decode>
                        </CodeListItem>
                      </CodeList>
                    </MetaDataVersion>
                  </Study>
                </ODM>
                """;

        ODM odm = parser.parse(xml);

        var mdv = odm.getStudies().get(0).getMetaDataVersions().get(0);
        assertEquals(1, mdv.getCodeLists().size());
        assertEquals("SEX", mdv.getCodeLists().get(0).getName());
        assertEquals(1, mdv.getCodeLists().get(0).getCodeListItems().size());
        assertEquals("M", mdv.getCodeLists().get(0).getCodeListItems().get(0).getCodedValue());
    }


    @Test
    void testParseIgnoresUnknownProperties() throws IOException
    {
        String xml = """
                <ODM UnknownAttribute="value">
                  <UnknownElement>content</UnknownElement>
                  <Study OID="S1" />
                </ODM>
                """;

        // Should not throw
        ODM odm = parser.parse(xml);

        assertNotNull(odm);
        assertEquals(1, odm.getStudies().size());
    }

    // ==================== parse(InputStream) ====================


    @Test
    void testParseInputStream() throws IOException
    {
        String xml = "<ODM FileOID=\"stream-test\" />";
        InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

        ODM odm = parser.parse(is);

        assertEquals("stream-test", odm.getFileOID());
    }

    // ==================== Invalid XML ====================


    @Test
    void testParseInvalidXmlThrows()
    {
        String xml = "<ODM><unclosed>";

        assertThrows(IOException.class, () -> parser.parse(xml));
    }


    @Test
    void testParseEmptyStringThrows()
    {
        assertThrows(Exception.class, () -> parser.parse(""));
    }

    // ==================== Complex structure ====================


    @Test
    void testParseComplexStructure() throws IOException
    {
        String xml = """
                <ODM FileOID="define" FileType="Snapshot" ODMVersion="1.3.2">
                  <Study OID="STUDY001">
                    <GlobalVariables>
                      <StudyName>Phase 3 Trial</StudyName>
                      <StudyDescription>A clinical trial</StudyDescription>
                      <ProtocolName>PROTO-001</ProtocolName>
                    </GlobalVariables>
                    <MetaDataVersion OID="MDV.001" Name="SDTM 3.3" DefineVersion="2.1"
                                     StandardName="SDTM-IG" StandardVersion="3.3">
                      <ItemGroupDef OID="IG.DM" Name="DM" Domain="DM" Purpose="Tabulation">
                        <ItemRef ItemOID="IT.USUBJID" OrderNumber="1" Mandatory="Yes" KeySequence="1" />
                        <ItemRef ItemOID="IT.AGE" OrderNumber="2" Mandatory="No" />
                      </ItemGroupDef>
                      <ItemDef OID="IT.USUBJID" Name="USUBJID" DataType="text" Length="20" Label="Subject ID" />
                      <ItemDef OID="IT.AGE" Name="AGE" DataType="integer" Label="Age" />
                    </MetaDataVersion>
                  </Study>
                </ODM>
                """;

        ODM odm = parser.parse(xml);

        assertEquals("define", odm.getFileOID());
        assertEquals("Snapshot", odm.getFileType());

        Study study = odm.getStudies().get(0);
        assertEquals("STUDY001", study.getOid());
        assertEquals("Phase 3 Trial", study.getGlobalVariables().getStudyName());
        assertEquals("PROTO-001", study.getGlobalVariables().getProtocolName());

        MetaDataVersion mdv = study.getMetaDataVersions().get(0);
        assertEquals("SDTM-IG", mdv.getStandardName());
        assertEquals("3.3", mdv.getStandardVersion());

        assertEquals(1, mdv.getItemGroupDefs().size());
        assertEquals(2, mdv.getItemGroupDefs().get(0).getItemRefs().size());
        assertEquals(2, mdv.getItemDefs().size());
    }
}
