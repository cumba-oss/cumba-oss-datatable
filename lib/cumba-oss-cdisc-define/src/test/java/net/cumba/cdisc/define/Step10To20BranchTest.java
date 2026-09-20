package net.cumba.cdisc.define;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import net.cumba.cdisc.define.DefineXmlConverter.Version;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Branch-level tests for the Define-XML 1.0 → 2.0 conversion. Every decision here either invents
 * metadata (a WhereClause, a MethodDef, a KeySequence) or discards it (a label, an origin, a
 * comment), so an unasserted branch is a silent change to what the submission says the data means.
 *
 * <p>
 * The owner rulings on F-cdisc-define-05, -06, -07 and -12 are executed and pinned below: a
 * ValueListDef OID with fewer than 3 segments is warned about and skipped (never decoded into a
 * self-referential ItemOID), both silent-drop paths of the computation-method rewrite warn, the
 * already-has-a-{@code Description}/{@code Origin} short circuits warn about the discarded
 * attribute text, and in the origin-keyword chain {@code deriv} outranks {@code crf} while the
 * predecessor token is matched trimmed.
 * </p>
 */
class Step10To20BranchTest
{

    private static final String DEF_NS_20 = DefineXmlConverter.DEF_NS_20;

    private static String v10(String aRootAttrs, String aMdvAttrs, String aBody)
    {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><ODM xmlns=\""
                + DefineXmlConverter.ODM_NS_12 + "\" xmlns:def=\"" + DefineXmlConverter.DEF_NS_10
                + "\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" FileType=\"Snapshot\""
                + " FileOID=\"X\" " + aRootAttrs
                + "><Study OID=\"S\"><MetaDataVersion OID=\"MDV.1\"" + " Name=\"m\" " + aMdvAttrs
                + ">" + aBody + "</MetaDataVersion></Study></ODM>";
    }


    /** The common case: a v1.0 root and MetaDataVersion that already carry their version stamps. */
    private static String v10(String aBody)
    {
        return v10("ODMVersion=\"1.2\"", "def:DefineVersion=\"1.0.0\"", aBody);
    }


    private static DefineXmlConverter run(String aXml) throws Exception
    {
        return DefineXmlConverter
                .forInputStream(new ByteArrayInputStream(aXml.getBytes(StandardCharsets.UTF_8)))
                .from(Version.V1_0).to(Version.V2_0).convert();
    }


    private static boolean any(List<String> aLines, String aNeedle)
    {
        return aLines.stream().anyMatch(line -> line.contains(aNeedle));
    }


    private static long countContaining(List<String> aLines, String aNeedle)
    {
        return aLines.stream().filter(line -> line.contains(aNeedle)).count();
    }


    private static List<String> childNames(Element aParent)
    {
        List<String> names = new ArrayList<>();
        NodeList kids = aParent.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++)
        {
            if (kids.item(i) instanceof Element child)
            {
                names.add(DefineDomUtil.localNameOf(child));
            }
        }
        return names;
    }


    private static long countAttributesNamed(Element aEl, String aLocalName)
    {
        long n = 0;
        for (int i = 0; i < aEl.getAttributes().getLength(); i++)
        {
            if (aLocalName.equals(DefineDomUtil.localNameOf(aEl.getAttributes().item(i))))
            {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------ stampVersions


    @Test
    void versionAttributesAreUpdatedInPlaceWhenTheyExist() throws Exception
    {
        Document doc = run(v10("")).getDocument();
        Element root = doc.getDocumentElement();

        assertEquals("1.3.2", root.getAttribute("ODMVersion"));
        assertEquals(1, countAttributesNamed(root, "ODMVersion"));
        Element mdv = DefineDomUtil.firstByLocalName(doc, "MetaDataVersion");
        assertEquals("2.0.0", DefineDomUtil.attrIgnoreNs(mdv, "DefineVersion"));
        assertEquals(1, countAttributesNamed(mdv, "DefineVersion"));
    }


    @Test
    void versionAttributesAreCreatedWhenTheInputLacksThem() throws Exception
    {
        Document doc = run(v10("", "", "")).getDocument();
        Element root = doc.getDocumentElement();

        assertEquals("1.3.2", root.getAttribute("ODMVersion"));
        Element mdv = DefineDomUtil.firstByLocalName(doc, "MetaDataVersion");
        assertEquals("2.0.0", mdv.getAttributeNS(DEF_NS_20, "DefineVersion"),
                "a missing DefineVersion must be created in the def namespace");
    }

    // ------------------------------------------------------------ def:Label


    @Test
    void labelsBecomeDescriptionsAndTheAttributeAlwaysGoes() throws Exception
    {
        DefineXmlConverter c = run(v10("<ItemGroupDef OID=\"IG.DM\" def:Label=\"Demographics\">"
                + "<ItemRef ItemOID=\"IT.A\"/></ItemGroupDef>"
                + "<ItemDef OID=\"IT.A\" def:Label=\" \"/>" + "<ItemDef OID=\"IT.B\"/>"));
        Document doc = c.getDocument();
        List<Element> items = DefineDomUtil.elementsByLocalName(doc, "ItemDef");
        Element ig = DefineDomUtil.firstByLocalName(doc, "ItemGroupDef");

        assertNull(DefineDomUtil.attrIgnoreNs(ig, "Label"));
        assertNull(DefineDomUtil.attrIgnoreNs(items.get(0), "Label"),
                "a blank label is still removed");
        assertEquals(List.of("Description", "ItemRef"), childNames(ig),
                "the Description must be inserted as the first child");
        Element tt = DefineDomUtil.firstByLocalName(doc, "TranslatedText");
        assertEquals("Demographics", tt.getTextContent());
        assertEquals("en", tt.getAttributeNS(XMLConstants.XML_NS_URI, "lang"));
        assertTrue(childNames(items.get(0)).isEmpty(), "a blank label yields no Description");
        assertTrue(childNames(items.get(1)).isEmpty());
        assertTrue(any(c.getLog(), "converted 1 def:Label attributes"));
    }


    @Test
    void bothContainerKindsFeedTheSameLabelCounterAndNoneMeansNoLogLine() throws Exception
    {
        DefineXmlConverter both = run(v10("<ItemGroupDef OID=\"IG.DM\" def:Label=\"Demographics\"/>"
                + "<ItemDef OID=\"IT.A\" def:Label=\"Age\"/>"));
        assertTrue(any(both.getLog(), "converted 2 def:Label attributes"));

        DefineXmlConverter none = run(v10("<ItemDef OID=\"IT.A\"/>"));
        assertFalse(any(none.getLog(), "def:Label attributes"),
                "a document with no labels must log nothing");
    }

    // ------------------------------------------------------------ def:DomainKeys


    @Test
    void domainKeysNumberOnlyTheNonEmptySegments() throws Exception
    {
        DefineXmlConverter c = run(
                v10("<ItemGroupDef OID=\"IG.DM\" def:DomainKeys=\"STUDYID,,USUBJID\">"
                        + "<ItemRef ItemOID=\"IT.DM.STUDYID\"/><ItemRef ItemOID=\"IT.DM.USUBJID\"/>"
                        + "</ItemGroupDef>"));
        Element ig = DefineDomUtil.firstByLocalName(c.getDocument(), "ItemGroupDef");
        List<Element> refs = DefineDomUtil.childrenByLocalName(ig, "ItemRef");

        assertNull(DefineDomUtil.attrIgnoreNs(ig, "DomainKeys"));
        assertEquals("1", refs.get(0).getAttribute("KeySequence"));
        assertEquals("2", refs.get(1).getAttribute("KeySequence"),
                "the empty middle segment must not consume a sequence number");
    }


    @Test
    void aKeyMatchesExactlyOrOnADottedSuffixButNeverOnASubstring() throws Exception
    {
        DefineXmlConverter c = run(v10("<ItemGroupDef OID=\"IG.DM\" def:DomainKeys=\"USUBJID\">"
                + "<ItemRef ItemOID=\"IT.DM.XUSUBJID\"/><ItemRef ItemOID=\"IT.DM.USUBJID\"/>"
                + "</ItemGroupDef>" + "<ItemGroupDef OID=\"IG.VS\" def:DomainKeys=\"USUBJID\">"
                + "<ItemRef ItemOID=\"USUBJID\"/></ItemGroupDef>"));
        List<Element> groups = DefineDomUtil.elementsByLocalName(c.getDocument(), "ItemGroupDef");
        List<Element> dmRefs = DefineDomUtil.childrenByLocalName(groups.get(0), "ItemRef");

        assertFalse(dmRefs.get(0).hasAttribute("KeySequence"),
                "IT.DM.XUSUBJID must not match the key USUBJID");
        assertEquals("1", dmRefs.get(1).getAttribute("KeySequence"));
        assertEquals("1", DefineDomUtil.childrenByLocalName(groups.get(1), "ItemRef").get(0)
                .getAttribute("KeySequence"), "an exact ItemOID must match too");
    }


    @Test
    void anUnmatchedDomainKeyWarnsAndSetsNoKeySequence() throws Exception
    {
        DefineXmlConverter c = run(v10("<ItemGroupDef OID=\"IG.DM\" def:DomainKeys=\"RFSTDTC\">"
                + "<ItemRef ItemOID=\"IT.DM.USUBJID\"/></ItemGroupDef>"));

        assertFalse(DefineDomUtil.firstByLocalName(c.getDocument(), "ItemRef")
                .hasAttribute("KeySequence"));
        assertTrue(any(c.getWarnings(),
                "key variable \"RFSTDTC\" in def:DomainKeys of ItemGroupDef IG.DM"));
    }

    // ------------------------------------------------------------ @Origin


    @Test
    void originKeywordsMapToTheirTypes() throws Exception
    {
        DefineXmlConverter c = run(v10("<ItemDef OID=\"IT.A\" Origin=\"CRF\"/>"
                + "<ItemDef OID=\"IT.B\" Origin=\"Derived\"/>"
                + "<ItemDef OID=\"IT.C\" Origin=\"Assigned\"/>"
                + "<ItemDef OID=\"IT.D\" Origin=\"Protocol\"/>"
                + "<ItemDef OID=\"IT.E\" Origin=\"eDT\"/>"
                + "<ItemDef OID=\"IT.F\" Origin=\"predecessor\"/>"));
        List<Element> origins = DefineDomUtil.elementsByLocalName(c.getDocument(), "Origin");

        assertEquals(6, origins.size());
        assertEquals(List.of("CRF", "Derived", "Assigned", "Protocol", "eDT", "Predecessor"),
                origins.stream().map(o -> o.getAttribute("Type")).toList());
        assertEquals(DEF_NS_20, origins.get(0).getNamespaceURI());
        assertNull(
                DefineDomUtil.attrIgnoreNs(
                        DefineDomUtil.firstByLocalName(c.getDocument(), "ItemDef"), "Origin"),
                "the v1.0 attribute must be removed");
        assertEquals(0, countContaining(c.getWarnings(), "could not map Origin"));
    }


    @Test
    void anElectronicTransferPhraseAlsoMapsToEdt() throws Exception
    {
        DefineXmlConverter c = run(v10("<ItemDef OID=\"IT.A\" Origin=\"electronic transfer\"/>"));
        assertEquals("eDT",
                DefineDomUtil.firstByLocalName(c.getDocument(), "Origin").getAttribute("Type"));
    }


    @Test
    void anAbsentOrBlankOriginProducesNoElement() throws Exception
    {
        DefineXmlConverter c = run(
                v10("<ItemDef OID=\"IT.A\"/>" + "<ItemDef OID=\"IT.B\" Origin=\" \"/>"));
        List<Element> items = DefineDomUtil.elementsByLocalName(c.getDocument(), "ItemDef");

        assertTrue(DefineDomUtil.elementsByLocalName(c.getDocument(), "Origin").isEmpty());
        assertNull(DefineDomUtil.attrIgnoreNs(items.get(1), "Origin"),
                "a blank Origin attribute is still removed");
    }


    /**
     * The page-reference warning fires on a digit in the text, whatever the mapped type — the free
     * text is dropped in every case (F-16). A digit-free origin stays silent.
     */
    @Test
    void aCrfOriginWarnsOnlyWhenItCarriesAPageNumber() throws Exception
    {
        assertEquals(0,
                countContaining(run(v10("<ItemDef OID=\"IT.A\" Origin=\"CRF\"/>")).getWarnings(),
                        "page reference"));

        DefineXmlConverter paged = run(v10("<ItemDef OID=\"IT.A\" Origin=\"CRF page 4\"/>"));
        assertTrue(any(paged.getWarnings(),
                "ItemDef IT.A: page reference in Origin \"CRF page 4\" dropped"));
        assertEquals("CRF",
                DefineDomUtil.firstByLocalName(paged.getDocument(), "Origin").getAttribute("Type"));
    }


    /** The fallback type warns unless the source text actually said "assign". */
    @Test
    void anUnmappableOriginDefaultsToAssignedAndWarns() throws Exception
    {
        DefineXmlConverter unknown = run(v10("<ItemDef OID=\"IT.A\" Origin=\"unknown\"/>"));
        assertEquals("Assigned", DefineDomUtil.firstByLocalName(unknown.getDocument(), "Origin")
                .getAttribute("Type"));
        assertTrue(any(unknown.getWarnings(),
                "ItemDef IT.A: could not map Origin \"unknown\"; defaulted to Type=\"Assigned\""));

        assertEquals(0,
                countContaining(
                        run(v10("<ItemDef OID=\"IT.A\" Origin=\"Assigned\"/>")).getWarnings(),
                        "could not map Origin"));
    }


    @Test
    void theOriginElementIsInsertedBeforeAnyValueListRef() throws Exception
    {
        DefineXmlConverter withRef = run(v10("<ItemDef OID=\"IT.A\" Origin=\"CRF\">"
                + "<def:ValueListRef ValueListOID=\"VL.A\"/></ItemDef>"));
        assertEquals(List.of("Origin", "ValueListRef"),
                childNames(DefineDomUtil.firstByLocalName(withRef.getDocument(), "ItemDef")));

        DefineXmlConverter withDescription = run(v10("<ItemDef OID=\"IT.A\" Origin=\"CRF\">"
                + "<Description><TranslatedText xml:lang=\"en\">x</TranslatedText></Description>"
                + "</ItemDef>"));
        assertEquals(List.of("Description", "Origin"),
                childNames(
                        DefineDomUtil.firstByLocalName(withDescription.getDocument(), "ItemDef")),
                "with no ValueListRef the Origin is appended");
    }

    // ------------------------------------------------------------ def:Rank


    @Test
    void aRankAttributeIsUnprefixedAndABlankOneIsDropped() throws Exception
    {
        DefineXmlConverter c = run(
                v10("<CodeList OID=\"CL.1\">" + "<CodeListItem CodedValue=\"M\" def:Rank=\"2\"/>"
                        + "<EnumeratedItem CodedValue=\"F\" def:Rank=\" \"/></CodeList>"));
        Element item = DefineDomUtil.firstByLocalName(c.getDocument(), "CodeListItem");
        Element enumerated = DefineDomUtil.firstByLocalName(c.getDocument(), "EnumeratedItem");

        assertEquals("2", item.getAttribute("Rank"));
        assertEquals(1, countAttributesNamed(item, "Rank"), "the def: form must be gone");
        assertFalse(enumerated.hasAttribute("Rank"), "a blank rank is dropped entirely");
    }

    // ------------------------------------------------------------ def:ComputationMethod


    @Test
    void aComputationMethodBecomesAMethodDefWithATrimmedDescription() throws Exception
    {
        DefineXmlConverter c = run(v10("<CodeList OID=\"CL.1\"/>"
                + "<def:ComputationMethod OID=\"CM.1\">  AGE = INT(x)  </def:ComputationMethod>"
                + "<def:CommentDef OID=\"COM.1\"/>"));
        Document doc = c.getDocument();
        Element method = DefineDomUtil.firstByLocalName(doc, "MethodDef");

        assertEquals("CM.1", method.getAttribute("OID"));
        assertEquals("Computation CM.1", method.getAttribute("Name"));
        assertEquals("Computation", method.getAttribute("Type"));
        assertEquals("AGE = INT(x)",
                DefineDomUtil.firstByLocalName(doc, "TranslatedText").getTextContent(),
                "the derivation text must be trimmed");
        assertTrue(DefineDomUtil.elementsByLocalName(doc, "ComputationMethod").isEmpty());
        // MDV_ORDER sequences CodeList, MethodDef, CommentDef.
        assertEquals(List.of("CodeList", "MethodDef", "CommentDef"),
                childNames(DefineDomUtil.firstByLocalName(doc, "MetaDataVersion")));
        assertTrue(any(c.getLog(), "converted 1 def:ComputationMethod to MethodDef"));
    }


    @Test
    void aComputationMethodWithoutAnOidStillYieldsANamedMethodDef() throws Exception
    {
        DefineXmlConverter c = run(v10("<def:ComputationMethod></def:ComputationMethod>"));
        Element method = DefineDomUtil.firstByLocalName(c.getDocument(), "MethodDef");

        assertFalse(method.hasAttribute("OID"));
        assertEquals("Computation", method.getAttribute("Name"));
        assertEquals("",
                DefineDomUtil.firstByLocalName(c.getDocument(), "TranslatedText").getTextContent(),
                "an empty body must yield empty text, not null");
    }


    /** Boundary at an empty map: a derivation with no referencing ItemDef touches no ItemRef. */
    @Test
    void noItemRefIsRewrittenWhenNoItemDefReferencesAComputationMethod() throws Exception
    {
        DefineXmlConverter c = run(v10("<ItemGroupDef OID=\"IG.DM\">"
                + "<ItemRef ItemOID=\"IT.A\"/></ItemGroupDef><ItemDef OID=\"IT.A\"/>"
                + "<def:ComputationMethod OID=\"CM.1\">x</def:ComputationMethod>"));

        assertFalse(DefineDomUtil.firstByLocalName(c.getDocument(), "ItemRef")
                .hasAttribute("MethodOID"));
        assertEquals(1, DefineDomUtil.elementsByLocalName(c.getDocument(), "MethodDef").size());
    }


    @Test
    void aDocumentWithoutComputationMethodsLogsNothing() throws Exception
    {
        assertFalse(any(run(v10("<ItemDef OID=\"IT.A\"/>")).getLog(),
                "def:ComputationMethod to MethodDef"));
    }

    // ------------------------------------------------------------ WhereClause synthesis


    /**
     * Boundary at five segments: {@code k + 1 <= len - 2} holds exactly once, so two conditions.
     */
    @Test
    void aFiveSegmentValueListOidYieldsTwoRangeChecks() throws Exception
    {
        // The fixture lists its children in canonical order so the insert position is meaningful.
        DefineXmlConverter c = run(v10("<def:ValueListDef OID=\"VL.VS.VSPOS.SUPINE.VSORRES\">"
                + "<ItemRef ItemOID=\"IT.VS.VSORRES.SYSBP\"/></def:ValueListDef>"
                + "<ItemGroupDef OID=\"IG.VS\"/>"
                + "<ItemDef OID=\"VS.VSPOS\"/><ItemDef OID=\"VS.VSORRES\"/>"));
        Document doc = c.getDocument();
        Element wc = DefineDomUtil.firstByLocalName(doc, "WhereClauseDef");
        List<Element> checks = DefineDomUtil.childrenByLocalName(wc, "RangeCheck");

        assertEquals(2, checks.size());
        assertEquals("VS.VSPOS", checks.get(0).getAttributeNS(DEF_NS_20, "ItemOID"));
        assertEquals("SUPINE", DefineDomUtil.childrenByLocalName(checks.get(0), "CheckValue").get(0)
                .getTextContent());
        assertEquals("EQ", checks.get(0).getAttribute("Comparator"));
        assertEquals("VS.VSORRES", checks.get(1).getAttributeNS(DEF_NS_20, "ItemOID"));
        assertEquals("SYSBP", DefineDomUtil.childrenByLocalName(checks.get(1), "CheckValue").get(0)
                .getTextContent());
        assertTrue(any(c.getWarnings(), "VS.VSPOS EQ \"SUPINE\" AND VS.VSORRES EQ \"SYSBP\""),
                "the AND-joined description is the only place the pair ordering shows: "
                        + c.getWarnings());
        // The synthesised def:WhereClauseDef must sort after ValueListDef and before ItemGroupDef.
        assertEquals(
                List.of("ValueListDef", "WhereClauseDef", "ItemGroupDef", "ItemDef", "ItemDef"),
                childNames(DefineDomUtil.firstByLocalName(doc, "MetaDataVersion")));
        assertTrue(any(c.getLog(), "synthesised 1 def:WhereClauseDef"));
    }


    @Test
    void aThreeSegmentValueListOidYieldsASingleRangeCheck() throws Exception
    {
        DefineXmlConverter c = run(
                v10("<ItemDef OID=\"VS.VSORRES\"/>" + "<def:ValueListDef OID=\"VL.VS.VSORRES\">"
                        + "<ItemRef ItemOID=\"IT.VS.VSORRES.SYSBP\"/></def:ValueListDef>"));
        Element wc = DefineDomUtil.firstByLocalName(c.getDocument(), "WhereClauseDef");

        assertEquals(1, DefineDomUtil.childrenByLocalName(wc, "RangeCheck").size());
        assertEquals(0, countContaining(c.getWarnings(), "irregular ValueListDef OID structure"));
        assertEquals(0, countContaining(c.getWarnings(), "has no matching ItemDef"));
    }


    /** A four-segment OID is irregular: the qualifier pair is incomplete, so the step warns. */
    @Test
    void aFourSegmentValueListOidIsReportedAsIrregular() throws Exception
    {
        DefineXmlConverter c = run(v10("<def:ValueListDef OID=\"VL.VS.SYSBP.VSORRES\">"
                + "<ItemRef ItemOID=\"IT.A\"/></def:ValueListDef>"));
        assertTrue(
                any(c.getWarnings(), "VL.VS.SYSBP.VSORRES: irregular ValueListDef OID structure"));
    }


    @Test
    void aNullQualifierValueIsWarnedAboutAndOmittedFromTheCondition() throws Exception
    {
        DefineXmlConverter c = run(v10("<def:ValueListDef OID=\"VL.VS.VSPOS.NULL.VSORRES\">"
                + "<ItemRef ItemOID=\"IT.VS.VSORRES.SYSBP\"/></def:ValueListDef>"));
        Element wc = DefineDomUtil.firstByLocalName(c.getDocument(), "WhereClauseDef");

        assertEquals(1, DefineDomUtil.childrenByLocalName(wc, "RangeCheck").size(),
                "the NULL qualifier must not become a condition");
        assertTrue(any(c.getWarnings(),
                "qualifier VSPOS has placeholder value NULL; omitted from the WhereClause"));
    }


    @Test
    void aValueListDefWithoutAnOidIsSkipped() throws Exception
    {
        DefineXmlConverter c = run(
                v10("<def:ValueListDef>" + "<ItemRef ItemOID=\"IT.A\"/></def:ValueListDef>"));

        assertTrue(DefineDomUtil.elementsByLocalName(c.getDocument(), "WhereClauseDef").isEmpty());
        assertFalse(any(c.getLog(), "def:WhereClauseDef from v1.0 value lists"));
    }


    @Test
    void anItemRefThatAlreadyHasAWhereClauseRefIsLeftAlone() throws Exception
    {
        DefineXmlConverter c = run(v10("<def:ValueListDef OID=\"VL.VS.VSORRES\">"
                + "<ItemRef ItemOID=\"IT.A\"><def:WhereClauseRef WhereClauseOID=\"WC.EXISTING\"/>"
                + "</ItemRef></def:ValueListDef>"));

        assertTrue(DefineDomUtil.elementsByLocalName(c.getDocument(), "WhereClauseDef").isEmpty());
        assertEquals(1,
                DefineDomUtil.elementsByLocalName(c.getDocument(), "WhereClauseRef").size());
        assertFalse(any(c.getLog(), "def:WhereClauseDef from v1.0 value lists"),
                "nothing was created, so nothing may be logged");
    }


    /** An ItemRef with no ItemOID still yields a WhereClause, with an empty CheckValue. */
    @Test
    void anItemRefWithoutAnItemOidYieldsAnEmptyCheckValue() throws Exception
    {
        DefineXmlConverter c = run(
                v10("<def:ValueListDef OID=\"VL.VS.VSORRES\">" + "<ItemRef/></def:ValueListDef>"));
        Element wc = DefineDomUtil.firstByLocalName(c.getDocument(), "WhereClauseDef");

        assertEquals("",
                DefineDomUtil.firstByLocalName(c.getDocument(), "CheckValue").getTextContent());
        assertTrue(any(c.getWarnings(), "VS.VSORRES EQ \"\""));
        Element ref = DefineDomUtil.firstByLocalName(c.getDocument(), "ItemRef");
        assertEquals(wc.getAttribute("OID"),
                DefineDomUtil.childrenByLocalName(ref, "WhereClauseRef").get(0)
                        .getAttribute("WhereClauseOID"),
                "the minted WhereClauseRef must point at the minted WhereClauseDef");
    }


    /** {@code lastSegment} on an ItemOID with no dot returns the whole string. */
    @Test
    void anUndottedItemOidIsUsedWholeAsTheCheckValue() throws Exception
    {
        DefineXmlConverter c = run(v10("<def:ValueListDef OID=\"VL.VS.VSORRES\">"
                + "<ItemRef ItemOID=\"SYSBP\"/></def:ValueListDef>"));
        assertEquals("SYSBP",
                DefineDomUtil.firstByLocalName(c.getDocument(), "CheckValue").getTextContent());
    }


    /**
     * An inferred condition whose ItemOID has no ItemDef must be flagged; a resolved one must not.
     */
    @Test
    void unresolvedWhereClauseReferencesAreWarnedAbout() throws Exception
    {
        DefineXmlConverter unresolved = run(v10("<def:ValueListDef OID=\"VL.VS.VSORRES\">"
                + "<ItemRef ItemOID=\"IT.A\"/></def:ValueListDef>"));
        assertTrue(any(unresolved.getWarnings(),
                "references ItemOID \"VS.VSORRES\" which has no matching ItemDef"));

        DefineXmlConverter resolved = run(
                v10("<ItemDef OID=\"VS.VSORRES\"/>" + "<def:ValueListDef OID=\"VL.VS.VSORRES\">"
                        + "<ItemRef ItemOID=\"IT.A\"/></def:ValueListDef>"));
        assertEquals(0, countContaining(resolved.getWarnings(), "has no matching ItemDef"));
    }

    // ============ fixes pinned after the owner rulings (F-05, F-06, F-07, F-12) ============


    /** F-05: a ValueListDef OID with fewer than 3 segments cannot be decoded; warn and skip. */
    @Test
    void aShortValueListOidIsWarnedAboutAndSkippedNotFabricated() throws Exception
    {
        DefineXmlConverter two = run(v10("<def:ValueListDef OID=\"VL.LBTEST\">"
                + "<ItemRef ItemOID=\"IT.LB.LBTEST.GLUC\"/></def:ValueListDef>"));
        assertTrue(any(two.getWarnings(), "VL.LBTEST: irregular ValueListDef OID structure"),
                "warnings: " + two.getWarnings());
        assertTrue(DefineDomUtil.elementsByLocalName(two.getDocument(), "WhereClauseDef").isEmpty(),
                "no WhereClause may be fabricated from the self-referential LBTEST.LBTEST");

        DefineXmlConverter one = run(v10("<def:ValueListDef OID=\"VLONLY\">"
                + "<ItemRef ItemOID=\"IT.A\"/></def:ValueListDef>"));
        assertTrue(any(one.getWarnings(), "VLONLY: irregular ValueListDef OID structure"));
        assertTrue(
                DefineDomUtil.elementsByLocalName(one.getDocument(), "WhereClauseDef").isEmpty());
    }


    /** F-06: a derivation reference dropped because no ItemRef consumes it must be warned about. */
    @Test
    void aDerivationWhoseItemDefHasNoItemRefWarns() throws Exception
    {
        DefineXmlConverter c = run(v10("<ItemDef OID=\"IT.X\" def:ComputationMethodOID=\"CM.1\"/>"
                + "<def:ComputationMethod OID=\"CM.1\">x = y</def:ComputationMethod>"));
        assertTrue(
                any(c.getWarnings(), "ItemDef IT.X is not referenced by any ItemRef; its"
                        + " def:ComputationMethodOID=\"CM.1\" derivation reference was dropped"),
                "warnings: " + c.getWarnings());
        // the MethodDef itself is still converted; only the reference is lost
        assertEquals(1, DefineDomUtil.elementsByLocalName(c.getDocument(), "MethodDef").size());
    }


    /**
     * F-06: a derivation reference on an ItemDef with no OID cannot be mapped; warn, not silence.
     */
    @Test
    void aDerivationOnAnOidLessItemDefWarns() throws Exception
    {
        DefineXmlConverter c = run(v10("<ItemDef def:ComputationMethodOID=\"CM.2\"/>"));
        assertTrue(any(c.getWarnings(),
                "ItemDef without an OID carried def:ComputationMethodOID=\"CM.2\"; the derivation"
                        + " reference was dropped"),
                "warnings: " + c.getWarnings());
    }


    /** F-07: the v1.0 attribute discarded beside an existing v2.0 element is fidelity loss. */
    @Test
    void labelAndOriginDiscardedBesideExistingElementsAreWarnedAbout() throws Exception
    {
        DefineXmlConverter c = run(v10("<ItemGroupDef OID=\"IG.AE\" def:Label=\"A\">"
                + "<Description><TranslatedText xml:lang=\"en\">B</TranslatedText></Description>"
                + "</ItemGroupDef>"
                + "<ItemDef OID=\"IT.A\" Origin=\"CRF\"><def:Origin Type=\"Assigned\"/>"
                + "</ItemDef>"));

        assertTrue(any(c.getWarnings(),
                "ItemGroupDef IG.AE: def:Label=\"A\" dropped; a Description element was already"
                        + " present"),
                "warnings: " + c.getWarnings());
        assertTrue(any(c.getWarnings(),
                "ItemDef IT.A: Origin=\"CRF\" dropped; a def:Origin element was already present"),
                "warnings: " + c.getWarnings());

        Element ig = DefineDomUtil.firstByLocalName(c.getDocument(), "ItemGroupDef");
        assertNull(DefineDomUtil.attrIgnoreNs(ig, "Label"), "the deprecated attribute is removed");
        assertEquals("B",
                DefineDomUtil.childrenByLocalName(ig, "Description").get(0).getTextContent(),
                "the pre-existing Description wins");
        Element item = DefineDomUtil.firstByLocalName(c.getDocument(), "ItemDef");
        assertNull(DefineDomUtil.attrIgnoreNs(item, "Origin"));
        assertEquals("Assigned",
                DefineDomUtil.childrenByLocalName(item, "Origin").get(0).getAttribute("Type"),
                "the pre-existing def:Origin element wins");
    }


    /**
     * F-12/F-16: "Derived from CRF page 4" records a derived value, not a CRF collection — and the
     * discarded page reference must STILL be warned about (round 1 traded the warned wrong type for
     * a silent loss).
     */
    @Test
    void derivedOutranksCrfInTheOriginKeywordChain() throws Exception
    {
        DefineXmlConverter c = run(
                v10("<ItemDef OID=\"IT.A\" Origin=\"Derived from CRF page 4\"/>"));
        assertEquals("Derived",
                DefineDomUtil.firstByLocalName(c.getDocument(), "Origin").getAttribute("Type"));
        assertTrue(any(c.getWarnings(),
                "ItemDef IT.A: page reference in Origin \"Derived from CRF page 4\" dropped"),
                "the page reference is discarded whatever the type; warnings: " + c.getWarnings());
    }


    /** F-12: an untrimmed DATASET.VAR token is still a predecessor reference. */
    @Test
    void anUntrimmedPredecessorTokenIsStillRecognised() throws Exception
    {
        DefineXmlConverter c = run(v10("<ItemDef OID=\"IT.A\" Origin=\" DM.USUBJID \"/>"));
        assertEquals("Predecessor",
                DefineDomUtil.firstByLocalName(c.getDocument(), "Origin").getAttribute("Type"));
    }
}
