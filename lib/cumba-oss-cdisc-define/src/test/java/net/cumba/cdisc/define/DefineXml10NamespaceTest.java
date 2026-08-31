package net.cumba.cdisc.define;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * Verifies that Define-XML 1.0 (ODM 1.2) attributes in the {@code def:} namespace bind to the same
 * {@link MetaDataVersion} fields as the unqualified Define-XML 2.x form, via the namespace-agnostic
 * attribute factory wired into {@link DefineXmlParser}.
 */
class DefineXml10NamespaceTest
{

    @Test
    void defineXml10PrefixedAttributesBindToMetaDataVersion() throws IOException
    {
        String xml = """
                <ODM xmlns="http://www.cdisc.org/ns/odm/v1.2"
                     xmlns:def="http://www.cdisc.org/ns/def/v1.0"
                     FileOID="OID.TEST" FileType="Snapshot" ODMVersion="1.2">
                  <Study OID="STUDY.1">
                    <GlobalVariables>
                      <StudyName>Pilot</StudyName>
                      <StudyDescription>x</StudyDescription>
                      <ProtocolName>p</ProtocolName>
                    </GlobalVariables>
                    <MetaDataVersion OID="MDV.1"
                                     Name="SDTM 3.1.2"
                                     def:DefineVersion="1.0.0"
                                     def:StandardName="CDISC SDTM"
                                     def:StandardVersion="3.1.2">
                    </MetaDataVersion>
                  </Study>
                </ODM>
                """;

        ODM odm = new DefineXmlParser().parse(xml);
        assertNotNull(odm);
        MetaDataVersion mdv = odm.getStudies().get(0).getMetaDataVersions().get(0);

        assertEquals("1.0.0", mdv.getDefineVersion(),
                "def:DefineVersion must bind to DefineVersion field");
        assertEquals("CDISC SDTM", mdv.getStandardName(),
                "def:StandardName must bind to StandardName field");
        assertEquals("3.1.2", mdv.getStandardVersion(),
                "def:StandardVersion must bind to StandardVersion field");
    }


    @Test
    void defineXml2xUnqualifiedAttributesStillBind() throws IOException
    {
        // Regression: the namespace-agnostic attribute factory must not break the existing
        // Define-XML 2.x form where these attributes have no namespace prefix.
        String xml = """
                <ODM FileOID="OID.TEST" FileType="Snapshot" ODMVersion="1.3.2">
                  <Study OID="STUDY.1">
                    <GlobalVariables>
                      <StudyName>Pilot</StudyName>
                      <StudyDescription>x</StudyDescription>
                      <ProtocolName>p</ProtocolName>
                    </GlobalVariables>
                    <MetaDataVersion OID="MDV.1"
                                     Name="SDTM 3.4"
                                     DefineVersion="2.1"
                                     StandardName="SDTM-IG"
                                     StandardVersion="3.4">
                    </MetaDataVersion>
                  </Study>
                </ODM>
                """;

        ODM odm = new DefineXmlParser().parse(xml);
        MetaDataVersion mdv = odm.getStudies().get(0).getMetaDataVersions().get(0);

        assertEquals("2.1", mdv.getDefineVersion());
        assertEquals("SDTM-IG", mdv.getStandardName());
        assertEquals("3.4", mdv.getStandardVersion());
    }
}
