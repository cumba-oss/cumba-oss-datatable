package net.cumba.cdisc.define;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.cumba.cdisc.define.DefineXmlConverter.Version;
import org.junit.jupiter.api.Test;

/** Phase 2: Define-XML 2.0 -> 2.1 conversion. */
class Step20To21Test
{

    private static final String V20 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<?xml-stylesheet type=\"text/xsl\" href=\"define2-0-0.xsl\"?>"
            + "<ODM xmlns=\"http://www.cdisc.org/ns/odm/v1.3\""
            + " xmlns:def=\"http://www.cdisc.org/ns/def/v2.0\""
            + " xmlns:xlink=\"http://www.w3.org/1999/xlink\""
            + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
            + " xsi:schemaLocation=\"http://www.cdisc.org/ns/def/v2.0 define2-0-0.xsd\""
            + " ODMVersion=\"1.3.2\" FileType=\"Snapshot\" FileOID=\"X\">"
            + "<Study OID=\"S\"><GlobalVariables><StudyName>n</StudyName>"
            + "<StudyDescription>d</StudyDescription><ProtocolName>p</ProtocolName></GlobalVariables>"
            + "<MetaDataVersion OID=\"MDV.1\" Name=\"m\" def:DefineVersion=\"2.0.0\""
            + " def:StandardName=\"SDTM-IG\" def:StandardVersion=\"3.1.2\">"
            + "<ItemGroupDef OID=\"IG.DM\" Name=\"DM\" Repeating=\"No\" Purpose=\"Tabulation\""
            + " def:Structure=\"one\" def:Class=\"SPECIAL PURPOSE\" def:ArchiveLocationID=\"LF.DM\">"
            + "<Description><TranslatedText xml:lang=\"en\">Demographics</TranslatedText></Description>"
            + "<ItemRef ItemOID=\"IT.DM.A\" Mandatory=\"Yes\"/>"
            + "<def:leaf ID=\"LF.DM\" xlink:href=\"dm.xpt\"><def:title>dm.xpt</def:title></def:leaf>"
            + "</ItemGroupDef>"
            + "<ItemDef OID=\"IT.DM.A\" Name=\"A\" DataType=\"text\" Length=\"2\"><def:Origin Type=\"CRF\"/></ItemDef>"
            + "<ItemDef OID=\"IT.DM.B\" Name=\"B\" DataType=\"text\"><def:Origin Type=\"eDT\"/></ItemDef>"
            + "<ItemDef OID=\"IT.DM.C\" Name=\"C\" DataType=\"text\"><def:Origin Type=\"Derived\"/></ItemDef>"
            + "<ItemDef OID=\"IT.DM.D\" Name=\"D\" DataType=\"text\"><def:Origin Type=\"Assigned\"/></ItemDef>"
            + "<ItemDef OID=\"IT.DM.E\" Name=\"E\" DataType=\"text\"><def:Origin Type=\"Protocol\"/></ItemDef>"
            + "<ItemDef OID=\"IT.DM.F\" Name=\"F\" DataType=\"text\"><def:Origin Type=\"Predecessor\">"
            + "<Description><TranslatedText xml:lang=\"en\">DM.OLD</TranslatedText></Description></def:Origin></ItemDef>"
            + "<ItemDef OID=\"IT.DM.G\" Name=\"G\" DataType=\"text\"><def:Origin Type=\"Mystery\"/></ItemDef>"
            + "<CodeList OID=\"CL.SEX\" Name=\"SEX\" DataType=\"text\"><EnumeratedItem CodedValue=\"M\"/></CodeList>"
            + "</MetaDataVersion></Study></ODM>";

    private static DefineXmlConverter of(String xml) throws Exception
    {
        return DefineXmlConverter
                .forInputStream(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }


    private static String convert(String xml) throws Exception
    {
        return new String(of(xml).to(Version.V2_1).convert().toByteArray(), StandardCharsets.UTF_8);
    }


    private static int count(String haystack, String needle)
    {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1))
        {
            n++;
        }
        return n;
    }

    // ---------- synthetic, precise assertions ----------


    @Test
    void namespaceAndVersionBumped() throws Exception
    {
        String out = convert(V20);
        assertTrue(out.contains("http://www.cdisc.org/ns/def/v2.1"));
        assertFalse(out.contains("ns/def/v2.0"));
        assertTrue(out.contains("def:DefineVersion=\"2.1.0\""));
        assertTrue(out.contains("def:Context=\"Submission\""));
        assertTrue(out.contains("define2-1-0.xsl"), "stylesheet PI updated");
        assertTrue(out.contains("def/v2.1 define2-1-0.xsd"), "schemaLocation updated");
    }


    @Test
    void standardsSynthesisedAndLegacyDropped() throws Exception
    {
        String out = convert(V20);
        assertTrue(out.contains("<def:Standards>"));
        assertTrue(out.contains("Type=\"IG\""));
        assertTrue(out.contains("Name=\"SDTMIG\""), "SDTM-IG normalised to SDTMIG");
        assertTrue(out.contains("Version=\"3.1.2\""));
        assertTrue(out.contains("def:StandardOID=\"STD.1\""), "ItemGroupDef references standard");
        assertFalse(out.contains("def:StandardName"), "legacy attr dropped by default");
        assertFalse(out.contains("def:StandardVersion"));
    }


    @Test
    void keepLegacyStandardAttributesRetainsAndWarns() throws Exception
    {
        DefineXmlConverter c = of(V20).keepLegacyStandardAttributes(true).to(Version.V2_1)
                .convert();
        String out = new String(c.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(out.contains("def:StandardName=\"SDTM-IG\""));
        assertTrue(c.getWarnings().stream().anyMatch(w -> w.contains("not strictly")));
    }


    @Test
    void classAttributeBecomesElement() throws Exception
    {
        String out = convert(V20);
        assertFalse(out.contains("def:Class=\""), "no Class attribute remains");
        assertTrue(out.contains("<def:Class Name=\"SPECIAL PURPOSE\""), "Class element added");
    }


    @Test
    void originTypeSourceMapping() throws Exception
    {
        String out = convert(V20);
        // Attribute serialisation order is not guaranteed, so assert presence independently.
        assertFalse(out.contains("Type=\"CRF\""), "CRF remapped");
        assertFalse(out.contains("Type=\"eDT\""), "eDT remapped");
        assertTrue(out.contains("Type=\"Collected\""), "CRF/eDT -> Collected");
        assertTrue(out.contains("Source=\"Investigator\""), "CRF -> Investigator");
        assertTrue(out.contains("Source=\"Vendor\""), "eDT -> Vendor");
        assertTrue(out.contains("Type=\"Derived\""));
        assertTrue(out.contains("Type=\"Assigned\""));
        assertTrue(out.contains("Type=\"Protocol\""));
        assertTrue(out.contains("Source=\"Sponsor\""), "Derived/Assigned/Protocol -> Sponsor");
        // CRF, eDT, Derived, Assigned, Protocol get a Source; Predecessor + Mystery do not.
        assertEquals(5, count(out, "Source="));
        // Predecessor preserved without a Source
        assertTrue(out.contains("Type=\"Predecessor\""));
        // Mystery left unchanged
        assertTrue(out.contains("Type=\"Mystery\""));
    }


    @Test
    void warningsCoverAmbiguousAndUnknownOriginsAndCt() throws Exception
    {
        List<String> warnings = of(V20).to(Version.V2_1).convert().getWarnings();
        assertTrue(warnings.stream().anyMatch(w -> w.contains("CT")), "CT warning");
        assertTrue(warnings.stream().anyMatch(w -> w.contains("eDT")), "eDT ambiguous warning");
        assertTrue(warnings.stream().anyMatch(w -> w.contains("Mystery")),
                "unknown-origin warning");
    }


    @Test
    void strictModeThrowsWhenWarningsRaised() throws Exception
    {
        assertThrows(DefineConversionException.class,
                () -> of(V20).failOnWarning(true).to(Version.V2_1).convert());
    }


    @Test
    void missingStandardNameSkipsStandardsWithWarning() throws Exception
    {
        String noName = V20.replace(" def:StandardName=\"SDTM-IG\" def:StandardVersion=\"3.1.2\"",
                "");
        DefineXmlConverter c = of(noName).to(Version.V2_1).convert();
        String out = new String(c.toByteArray(), StandardCharsets.UTF_8);
        assertFalse(out.contains("<def:Standards>"));
        assertTrue(c.getWarnings().stream().anyMatch(w -> w.contains("StandardName")));
    }


    @Test
    void originWithExistingSourceLeftUntouched() throws Exception
    {
        String already = "<ODM xmlns=\"http://www.cdisc.org/ns/odm/v1.3\""
                + " xmlns:def=\"http://www.cdisc.org/ns/def/v2.0\""
                + " ODMVersion=\"1.3.2\" FileType=\"Snapshot\" FileOID=\"X\">"
                + "<Study OID=\"S\"><MetaDataVersion OID=\"M\" Name=\"m\" def:DefineVersion=\"2.0.0\">"
                + "<ItemDef OID=\"IT.A\" Name=\"A\" DataType=\"text\">"
                + "<def:Origin Type=\"Collected\" Source=\"Subject\"/></ItemDef>"
                + "</MetaDataVersion></Study></ODM>";
        String out = convert(already);
        assertEquals(1, count(out, "Source="));
        assertTrue(out.contains("Source=\"Subject\""));
    }


    @Test
    void reParsesViaDefineXmlParser() throws Exception
    {
        byte[] bytes = of(V20).to(Version.V2_1).convert().toByteArray();
        ODM odm = new DefineXmlParser().parse(new ByteArrayInputStream(bytes));
        assertNotNull(odm);
        MetaDataVersion mdv = odm.getStudies().get(0).getMetaDataVersions().get(0);
        assertEquals("2.1.0", mdv.getDefineVersion());
        assertNotNull(mdv.getStandards(), "Standards block parsed back");
    }

    // ---------- real fixture integration ----------


    @Test
    void realV20FixtureConvertsAndReParses() throws Exception
    {
        DefineXmlConverter c;
        try (InputStream in = getClass().getResourceAsStream("/convert/define-v20-sdtm.xml"))
        {
            assertNotNull(in, "fixture present");
            c = DefineXmlConverter.forInputStream(in);
        }
        byte[] bytes = c.to(Version.V2_1).convert().toByteArray();
        String out = new String(bytes, StandardCharsets.UTF_8);

        assertFalse(out.contains("ns/def/v2.0"), "no v2.0 namespace remains");
        assertTrue(out.contains("def:DefineVersion=\"2.1.0\""));
        assertTrue(out.contains("def:Context="));
        assertTrue(out.contains("<def:Standards>"));
        assertEquals(0, count(out, "def:Class=\""), "no Class attributes remain");
        assertEquals(34, count(out, "<def:Class "), "all 34 datasets got a Class element");
        assertFalse(out.contains("Type=\"CRF\""), "all CRF origins remapped");
        assertTrue(count(out, "def:StandardOID=\"") >= 34, "every dataset references a standard");

        ODM odm = new DefineXmlParser().parse(new ByteArrayInputStream(bytes));
        assertNotNull(odm.getStudies().get(0).getMetaDataVersions().get(0).getStandards());
    }

}
