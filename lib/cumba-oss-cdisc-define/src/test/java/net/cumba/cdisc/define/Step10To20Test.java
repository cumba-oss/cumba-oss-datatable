package net.cumba.cdisc.define;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import net.cumba.cdisc.define.DefineXmlConverter.Version;
import org.junit.jupiter.api.Test;

/** Phase 3: Define-XML 1.0 (CRT-DDS) -> 2.0 conversion. */
class Step10To20Test
{

    private static final String V10 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<?xml-stylesheet type=\"text/xsl\" href=\"define1-0-0.xsl\"?>"
            + "<ODM xmlns=\"http://www.cdisc.org/ns/odm/v1.2\""
            + " xmlns:def=\"http://www.cdisc.org/ns/def/v1.0\""
            + " xmlns:xlink=\"http://www.w3.org/1999/xlink\""
            + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
            + " xsi:schemaLocation=\"http://www.cdisc.org/ns/odm/v1.2"
            + " http://www.cdisc.org/schema/def/v1.0/define1-0-0.xsd\""
            + " ODMVersion=\"1.2\" FileType=\"Snapshot\" FileOID=\"X\">"
            + "<Study OID=\"S\"><GlobalVariables><StudyName>n</StudyName>"
            + "<StudyDescription>d</StudyDescription><ProtocolName>p</ProtocolName></GlobalVariables>"
            + "<MetaDataVersion OID=\"MDV.1\" Name=\"m\" def:DefineVersion=\"1.0.0\""
            + " def:StandardName=\"CDISC SDTM\" def:StandardVersion=\"3.1.0\">"
            + "<def:ComputationMethod OID=\"CM.STUDYDAY\">date diff plus one</def:ComputationMethod>"
            + "<def:ValueListDef OID=\"ValueList.LB.LBCAT\">"
            + "<ItemRef ItemOID=\"LB.LBCAT.CHEMISTRY\" Mandatory=\"No\" OrderNumber=\"1\"/>"
            + "<ItemRef ItemOID=\"LB.LBCAT.HEMATOLOGY\" Mandatory=\"No\" OrderNumber=\"2\"/>"
            + "</def:ValueListDef>"
            + "<ItemGroupDef OID=\"DM\" Name=\"DM\" Repeating=\"No\" Purpose=\"Tabulation\""
            + " def:Label=\"Demographics\" def:Structure=\"one per subj\" def:Class=\"Special Purpose\""
            + " def:DomainKeys=\"STUDYID, USUBJID\" def:ArchiveLocationID=\"Location.DM\">"
            + "<ItemRef ItemOID=\"DM.STUDYID\" Mandatory=\"Yes\" OrderNumber=\"1\"/>"
            + "<ItemRef ItemOID=\"DM.USUBJID\" Mandatory=\"Yes\" OrderNumber=\"2\"/>"
            + "<ItemRef ItemOID=\"DM.AGE\" Mandatory=\"No\" OrderNumber=\"3\"/>"
            + "<ItemRef ItemOID=\"DM.SITEID\" Mandatory=\"No\" OrderNumber=\"4\"/>"
            + "<ItemRef ItemOID=\"DM.WEIRD\" Mandatory=\"No\" OrderNumber=\"5\"/>"
            + "<def:leaf ID=\"Location.DM\" xlink:href=\"dm.xpt\"><def:title>dm.xpt</def:title></def:leaf>"
            + "</ItemGroupDef>"
            + "<ItemDef OID=\"DM.STUDYID\" Name=\"STUDYID\" DataType=\"text\" Length=\"12\""
            + " Origin=\"CRF Page 7\" Comment=\"A comment\" def:Label=\"Study Identifier\"/>"
            + "<ItemDef OID=\"DM.USUBJID\" Name=\"USUBJID\" DataType=\"text\" Length=\"11\""
            + " Origin=\"Derived\" Comment=\"A comment\" def:Label=\"Unique Subject Identifier\"/>"
            + "<ItemDef OID=\"DM.AGE\" Name=\"AGE\" DataType=\"integer\" Length=\"3\""
            + " Origin=\"Assigned\" Comment=\" \" def:Label=\"Age\""
            + " def:ComputationMethodOID=\"CM.STUDYDAY\"/>"
            + "<ItemDef OID=\"DM.SITEID\" Name=\"SITEID\" DataType=\"text\" Length=\"4\""
            + " Origin=\"DM.OLDSITE\" def:Label=\"Site\"/>"
            + "<ItemDef OID=\"DM.WEIRD\" Name=\"WEIRD\" DataType=\"text\" Length=\"4\""
            + " Origin=\"telepathy\" def:Label=\"Weird\"/>"
            + "<ItemDef OID=\"LB.LBCAT.CHEMISTRY\" Name=\"LBCAT\" DataType=\"text\" def:Label=\"Cat CHEM\"/>"
            + "<ItemDef OID=\"LB.LBCAT.HEMATOLOGY\" Name=\"LBCAT\" DataType=\"text\" def:Label=\"Cat HEM\"/>"
            + "<CodeList OID=\"CL.NY\" Name=\"NY\" DataType=\"text\">"
            + "<CodeListItem CodedValue=\"Y\" def:Rank=\"1\"><Decode><TranslatedText>Yes</TranslatedText></Decode></CodeListItem>"
            + "<CodeListItem CodedValue=\"N\" def:Rank=\"2\"><Decode><TranslatedText>No</TranslatedText></Decode></CodeListItem>"
            + "</CodeList></MetaDataVersion></Study></ODM>";

    private static DefineXmlConverter of(String xml) throws Exception
    {
        return DefineXmlConverter
                .forInputStream(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
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

    private DefineXmlConverter converted;

    private String out() throws Exception
    {
        converted = of(V10).to(Version.V2_0).convert();
        return new String(converted.toByteArray(), StandardCharsets.UTF_8);
    }

    // ---------- precise assertions ----------


    @Test
    void namespacesAndVersionsBumped() throws Exception
    {
        String out = out();
        assertTrue(out.contains("odm/v1.3"));
        assertTrue(out.contains("def/v2.0"));
        assertFalse(out.contains("odm/v1.2"));
        assertFalse(out.contains("def/v1.0"));
        assertTrue(out.contains("ODMVersion=\"1.3.2\""));
        assertTrue(out.contains("def:DefineVersion=\"2.0.0\""));
        assertTrue(out.contains("define2-0-0.xsl"), "stylesheet updated");
        assertTrue(out.contains("define2-0-0.xsd"), "schemaLocation updated");
    }


    @Test
    void labelsBecomeDescriptions() throws Exception
    {
        String out = out();
        assertFalse(out.contains("def:Label"), "no Label attributes remain");
        assertTrue(out.contains("<TranslatedText xml:lang=\"en\">Demographics</TranslatedText>"));
        assertTrue(
                out.contains("<TranslatedText xml:lang=\"en\">Study Identifier</TranslatedText>"));
    }


    @Test
    void domainKeysBecomeKeySequence() throws Exception
    {
        String out = out();
        assertFalse(out.contains("def:DomainKeys"));
        assertEquals(2, count(out, "KeySequence="), "only the 2 declared keys are sequenced");
        assertTrue(out.contains("KeySequence=\"1\""));
        assertTrue(out.contains("KeySequence=\"2\""));
    }


    @Test
    void originAttributeBecomesElement() throws Exception
    {
        String out = out();
        assertFalse(out.contains(" Origin=\""), "no ODM Origin attribute remains");
        assertEquals(5, count(out, "<def:Origin "), "one Origin element per ItemDef");
        assertTrue(out.contains("Type=\"CRF\""));
        assertTrue(out.contains("Type=\"Derived\""));
        assertTrue(out.contains("Type=\"Assigned\""));
        assertTrue(out.contains("Type=\"Predecessor\""), "DM.OLDSITE -> Predecessor (has dot)");
        // CRF page reference + unmappable origin both warn
        assertTrue(converted.getWarnings().stream().anyMatch(w -> w.contains("page reference")));
        assertTrue(converted.getWarnings().stream().anyMatch(w -> w.contains("telepathy")));
    }


    @Test
    void commentsBecomeCommentDefsDeduped() throws Exception
    {
        String out = out();
        assertFalse(out.contains(" Comment=\""), "no Comment attribute remains");
        assertEquals(1, count(out, "<def:CommentDef "), "identical comments deduped to one");
        assertEquals(2, count(out, "def:CommentOID="), "both items reference the comment");
    }


    @Test
    void computationMethodBecomesMethodDef() throws Exception
    {
        String out = out();
        // attribute order is not guaranteed by the serialiser, so assert independently
        assertFalse(out.contains("ComputationMethod"), "no ComputationMethod or OID attr remains");
        assertTrue(out.contains("<MethodDef "));
        assertTrue(out.contains("Name=\"Computation CM.STUDYDAY\""), "MethodDef synthesised");
        assertTrue(out.contains("Type=\"Computation\""));
        assertTrue(out.contains("MethodOID=\"CM.STUDYDAY\""), "rewritten onto ItemRef");
    }


    @Test
    void rankAttributeDeNamespaced() throws Exception
    {
        String out = out();
        assertFalse(out.contains("def:Rank"));
        assertTrue(out.contains("Rank=\"1\""));
        assertTrue(out.contains("Rank=\"2\""));
    }


    @Test
    void whereClausesSynthesised() throws Exception
    {
        String out = out();
        assertEquals(2, count(out, "<def:WhereClauseDef "));
        assertEquals(2, count(out, "<def:WhereClauseRef "));
        assertTrue(out.contains("Comparator=\"EQ\""));
        assertTrue(out.contains("def:ItemOID=\"LB.LBCAT\""));
        assertTrue(out.contains("<CheckValue>CHEMISTRY</CheckValue>"));
        assertTrue(out.contains("<CheckValue>HEMATOLOGY</CheckValue>"));
        assertTrue(converted.getWarnings().stream().anyMatch(w -> w.contains("synthesised WC")));
    }

    private static final String NESTED_VL = "<ODM xmlns=\"http://www.cdisc.org/ns/odm/v1.2\""
            + " xmlns:def=\"http://www.cdisc.org/ns/def/v1.0\" ODMVersion=\"1.2\""
            + " FileType=\"Snapshot\" FileOID=\"X\"><Study OID=\"S\">"
            + "<MetaDataVersion OID=\"M\" Name=\"m\" def:DefineVersion=\"1.0.0\">"
            + "<def:ValueListDef OID=\"ValueList.LB.LBCAT.CHEMISTRY.LBTESTCD\">"
            + "<ItemRef ItemOID=\"LB.LBCAT.CHEMISTRY.LBTESTCD.ALB\" Mandatory=\"No\"/>"
            + "</def:ValueListDef>" + "<def:ValueListDef OID=\"ValueList.LB.LBCAT.NULL.LBTESTCD\">"
            + "<ItemRef ItemOID=\"LB.LBCAT.NULL.LBTESTCD.HBA1C\" Mandatory=\"No\"/>"
            + "</def:ValueListDef>" + "<ItemDef OID=\"LB.LBCAT\" Name=\"LBCAT\" DataType=\"text\"/>"
            + "<ItemDef OID=\"LB.LBTESTCD\" Name=\"LBTESTCD\" DataType=\"text\"/>"
            + "</MetaDataVersion></Study></ODM>";

    @Test
    void nestedValueListProducesConjunction() throws Exception
    {
        DefineXmlConverter c = of(NESTED_VL).to(Version.V2_0).convert();
        String out = new String(c.toByteArray(), StandardCharsets.UTF_8);
        // CHEMISTRY list: LB.LBCAT EQ CHEMISTRY AND LB.LBTESTCD EQ ALB -> 2 RangeChecks in 1 WC
        assertTrue(out.contains("def:ItemOID=\"LB.LBCAT\""), "qualifier condition present");
        assertTrue(out.contains("<CheckValue>CHEMISTRY</CheckValue>"));
        assertTrue(out.contains("def:ItemOID=\"LB.LBTESTCD\""), "final-variable condition present");
        assertTrue(out.contains("<CheckValue>ALB</CheckValue>"));
        // the NULL qualifier is skipped and warned, not emitted as a condition
        assertFalse(out.contains("<CheckValue>NULL</CheckValue>"));
        assertTrue(c.getWarnings().stream().anyMatch(w -> w.contains("placeholder value NULL")));
    }


    @Test
    void reParsesViaDefineXmlParser() throws Exception
    {
        byte[] bytes = of(V10).to(Version.V2_0).convert().toByteArray();
        ODM odm = new DefineXmlParser().parse(new ByteArrayInputStream(bytes));
        assertNotNull(odm);
        MetaDataVersion mdv = odm.getStudies().get(0).getMetaDataVersions().get(0);
        assertEquals("2.0.0", mdv.getDefineVersion());
    }

    // ---------- real fixture integration ----------


    @Test
    void realV10FixtureConvertsAndReParses() throws Exception
    {
        DefineXmlConverter c;
        try (InputStream in = getClass().getResourceAsStream("/convert/define-v10-sdtm.xml"))
        {
            assertNotNull(in, "fixture present");
            c = DefineXmlConverter.forInputStream(in);
        }
        byte[] bytes = c.to(Version.V2_0).convert().toByteArray();
        String out = new String(bytes, StandardCharsets.UTF_8);

        assertFalse(out.contains("odm/v1.2"), "ODM namespace bumped");
        assertFalse(out.contains("def/v1.0"), "def namespace bumped");
        assertTrue(out.contains("def:DefineVersion=\"2.0.0\""));
        assertFalse(out.contains("def:Label"), "all labels converted");
        assertFalse(java.util.regex.Pattern.compile("<ItemDef[^>]*\\sOrigin=").matcher(out).find(),
                "no ItemDef retains an Origin attribute");
        assertTrue(out.contains("<def:Origin "), "origins lifted to elements");
        assertFalse(out.contains("def:ComputationMethod"), "all computation methods converted");

        ODM odm = new DefineXmlParser().parse(new ByteArrayInputStream(bytes));
        assertEquals("2.0.0",
                odm.getStudies().get(0).getMetaDataVersions().get(0).getDefineVersion());
    }

}
