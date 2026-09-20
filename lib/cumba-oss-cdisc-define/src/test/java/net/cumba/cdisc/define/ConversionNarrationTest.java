package net.cumba.cdisc.define;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.cumba.cdisc.define.DefineXmlConverter.Version;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.ProcessingInstruction;

/**
 * What a conversion <em>says it did</em>, and the two rewrites nothing was watching.
 *
 * <p>
 * The conversion log and the warning list are the only account anyone gets of what the converter
 * changed on the way from one Define-XML version to another. A step that silently stops logging
 * still produces a document — a different one — and the operator comparing before and after has
 * nothing to go on. That makes a dropped {@code ctx.log} call indistinguishable from a clean run,
 * which is why the log lines are asserted here as outcomes in their own right rather than as
 * debugging niceties.
 * </p>
 */
class ConversionNarrationTest
{

    private static String v10(String aPrologue, String aBody)
    {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + aPrologue + "<ODM xmlns=\""
                + DefineXmlConverter.ODM_NS_12 + "\" xmlns:def=\"" + DefineXmlConverter.DEF_NS_10
                + "\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" FileType=\"Snapshot\""
                + " FileOID=\"X\" ODMVersion=\"1.2\">"
                + "<Study OID=\"S\"><MetaDataVersion OID=\"MDV.1\" Name=\"m\""
                + " def:DefineVersion=\"1.0.0\">" + aBody + "</MetaDataVersion></Study></ODM>";
    }


    private static String v20(String aMdvAttrs, String aBody)
    {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><ODM xmlns=\""
                + DefineXmlConverter.ODM_NS_13 + "\" xmlns:def=\"" + DefineXmlConverter.DEF_NS_20
                + "\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" FileType=\"Snapshot\""
                + " FileOID=\"X\" ODMVersion=\"1.3.2\">"
                + "<Study OID=\"S\"><MetaDataVersion OID=\"MDV.1\" Name=\"m\""
                + " def:DefineVersion=\"2.0.0\" " + aMdvAttrs + ">" + aBody
                + "</MetaDataVersion></Study></ODM>";
    }


    private static DefineXmlConverter run(String aXml, Version aFrom, Version aTo) throws Exception
    {
        return DefineXmlConverter
                .forInputStream(new ByteArrayInputStream(aXml.getBytes(StandardCharsets.UTF_8)))
                .from(aFrom).to(aTo).convert();
    }


    private static boolean any(List<String> aLines, String aNeedle)
    {
        return aLines.stream().anyMatch(line -> line.contains(aNeedle));
    }


    private static String stylesheetData(DefineXmlConverter aConverter)
    {
        for (Node n = aConverter.getDocument().getFirstChild(); n != null; n = n.getNextSibling())
        {
            if (n instanceof ProcessingInstruction pi && "xml-stylesheet".equals(pi.getTarget()))
            {
                return pi.getData();
            }
        }
        return "";
    }

    // ---------- the stylesheet processing instruction ----------


    /**
     * A Define-XML 1.0 file usually carries an {@code xml-stylesheet} PI pointing at the v1
     * stylesheet, and that PI is what a reviewer's browser uses to render the document. Left
     * un-rewritten it renders a 2.0 document through a 1.0 stylesheet: elements the stylesheet does
     * not know about simply do not appear, so whole sections of the metadata go missing on screen
     * while the file itself is correct.
     *
     * <p>
     * Both legacy spellings occur in the wild and each is recognised by its own clause of the
     * guard, so each needs its own document — a single fixture leaves the other clause free to
     * answer anything.
     * </p>
     */
    @Test
    void bothLegacyStylesheetSpellingsAreRewrittenAndLogged() throws Exception
    {
        DefineXmlConverter hyphenated = run(
                v10("<?xml-stylesheet type=\"text/xsl\" href=\"define-v1-0-0.xsl\"?>", ""),
                Version.V1_0, Version.V2_0);
        assertTrue(stylesheetData(hyphenated).contains("define-v2-0-0.xsl"),
                "was: " + stylesheetData(hyphenated));
        assertTrue(any(hyphenated.getLog(), "updated stylesheet reference to define2"),
                hyphenated.getLog().toString());

        DefineXmlConverter plain = run(
                v10("<?xml-stylesheet type=\"text/xsl\" href=\"define1-0-0.xsl\"?>", ""),
                Version.V1_0, Version.V2_0);
        assertTrue(stylesheetData(plain).contains("define2-0-0.xsl"),
                "was: " + stylesheetData(plain));
        assertTrue(any(plain.getLog(), "updated stylesheet reference to define2"));
    }


    /**
     * A stylesheet that is already current must be left alone, and must not be reported as changed.
     */
    @Test
    void aStylesheetThatIsNotALegacyOneIsUntouched() throws Exception
    {
        DefineXmlConverter c = run(
                v10("<?xml-stylesheet type=\"text/xsl\" href=\"house-style.xsl\"?>", ""),
                Version.V1_0, Version.V2_0);

        assertEquals("type=\"text/xsl\" href=\"house-style.xsl\"", stylesheetData(c));
        assertFalse(any(c.getLog(), "updated stylesheet reference"), c.getLog().toString());
    }

    // ---------- inline @Comment to def:CommentDef ----------


    /**
     * v1.0 carried a comment as an attribute on the ItemDef; 2.0 requires a {@code def:CommentDef}
     * with an OID. The conversion invents those OIDs, and the count in the log is the only place
     * the operator is told how many definitions were invented on their behalf.
     */
    @Test
    void theNumberOfSynthesisedCommentDefsIsReported() throws Exception
    {
        DefineXmlConverter c = run(
                v10("", "<ItemDef OID=\"IT.A\" def:Comment=\"Collected at screening\"/>"
                        + "<ItemDef OID=\"IT.B\" def:Comment=\"Derived from IT.A\"/>"),
                Version.V1_0, Version.V2_0);

        assertEquals(2, DefineDomUtil.elementsByLocalName(c.getDocument(), "CommentDef").size());
        assertTrue(any(c.getLog(), "created 2 def:CommentDef from inline @Comment attributes"),
                c.getLog().toString());
    }


    /**
     * With no inline comments there is nothing to report, and a "created 0" line would be noise.
     */
    @Test
    void aDocumentWithNoInlineCommentsReportsNothing() throws Exception
    {
        DefineXmlConverter c = run(v10("", "<ItemDef OID=\"IT.A\"/>"), Version.V1_0, Version.V2_0);

        assertTrue(DefineDomUtil.elementsByLocalName(c.getDocument(), "CommentDef").isEmpty());
        assertFalse(any(c.getLog(), "def:CommentDef from inline @Comment"), c.getLog().toString());
    }

    // ---------- synthesised WhereClauses ----------


    /**
     * The WhereClause the converter invents must be a complete RangeCheck. ODM requires
     * {@code SoftHard}, and a missing one is not a formatting detail: it is the field that says
     * whether failing the condition is an error or a query, and the document does not
     * schema-validate without it.
     *
     * <p>
     * The same fixture pins the two halves of the qualifier decoding. A five-segment OID
     * {@code VL.VS.VSPOS.SUPINE.VSORRES} has one complete (variable, value) qualifier pair, so it
     * is <em>regular</em> and must draw no structural warning — while the variable it names,
     * {@code VS.VSPOS}, has no ItemDef here and must draw an unresolved-reference one. Those are
     * opposite answers about the same OID, and only asserting both distinguishes the guard from a
     * constant.
     * </p>
     */
    @Test
    void aSynthesisedRangeCheckIsCompleteAndItsQualifiersAreResolved() throws Exception
    {
        DefineXmlConverter c = run(
                v10("", "<ItemDef OID=\"VS.VSORRES\"/>"
                        + "<def:ValueListDef OID=\"VL.VS.VSPOS.SUPINE.VSORRES\">"
                        + "<ItemRef ItemOID=\"IT.VS.VSORRES.SUPINE\"/></def:ValueListDef>"),
                Version.V1_0, Version.V2_0);

        List<Element> checks = DefineDomUtil.elementsByLocalName(c.getDocument(), "RangeCheck");
        assertFalse(checks.isEmpty(), "the converter must synthesise a WhereClause condition");
        for (Element rc : checks)
        {
            assertEquals("EQ", rc.getAttribute("Comparator"));
            assertEquals("Soft", rc.getAttribute("SoftHard"),
                    "ODM requires SoftHard on a RangeCheck");
        }

        assertFalse(any(c.getWarnings(), "irregular ValueListDef OID structure"),
                "a complete qualifier pair is regular: " + c.getWarnings());
        assertTrue(
                any(c.getWarnings(),
                        "references ItemOID \"VS.VSPOS\" which has no matching ItemDef"),
                "the qualifier variable is unresolved and must be flagged: " + c.getWarnings());
    }

    // ---------- synthesised def:Standards ----------


    /**
     * 2.1 replaced the {@code def:StandardName}/{@code def:StandardVersion} attribute pair with a
     * {@code def:Standards} block carrying an invented OID. The log line is where that invented OID
     * and the name it was normalised to become visible; without it the operator sees a
     * {@code def:StandardOID} on every ItemGroupDef pointing at an OID that appears in the log
     * nowhere.
     */
    @Test
    void theSynthesisedStandardIsReportedWithItsOidNameAndVersion() throws Exception
    {
        DefineXmlConverter c = run(v20("def:StandardName=\"SDTM-IG\" def:StandardVersion=\"3.1.2\"",
                "<ItemGroupDef OID=\"IG.DM\" Name=\"DM\"/>"), Version.V2_0, Version.V2_1);

        List<Element> standards = DefineDomUtil.elementsByLocalName(c.getDocument(), "Standard");
        assertEquals(1, standards.size());
        String oid = standards.get(0).getAttribute("OID");
        String name = standards.get(0).getAttribute("Name");

        assertEquals("3.1.2", standards.get(0).getAttribute("Version"));
        assertTrue(
                any(c.getLog(),
                        "synthesised def:Standards/def:Standard OID=" + oid + " Name=" + name
                                + " Version=3.1.2"),
                "the log must name the OID it invented: " + c.getLog());
    }
}
