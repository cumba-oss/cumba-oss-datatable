package net.cumba.cdisc.define;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.cumba.cdisc.define.DefineXmlConverter.Version;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Branch-level tests for the Define-XML 2.0 → 2.1 step. The existing {@code Step20To21Test} drives
 * one large fixture and asserts on the serialised text; this one drives the individual decisions —
 * each with an input sitting on its boundary — and asserts the resulting DOM plus the log and
 * warning sinks, which are the only observable surface for several of them.
 *
 * <p>
 * The owner rulings on F-cdisc-define-08 and -09 are executed and pinned below: a missing or blank
 * {@code def:StandardVersion} synthesises no {@code def:Standards} block and warns (symmetric with
 * the missing-name branch, since {@code @Version} is required on {@code def:Standard} in 2.1), and
 * an ItemGroupDef that already carries a {@code def:Class} element has the deprecated attribute
 * removed as well, with a warning when the two disagree.
 * </p>
 */
class Step20To21BranchTest
{

    private static final String DEF_NS = DefineXmlConverter.DEF_NS_21;

    private static String v20(String aMdvAttrs, String aMdvBody)
    {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><ODM xmlns=\""
                + DefineXmlConverter.ODM_NS_13 + "\" xmlns:def=\"" + DefineXmlConverter.DEF_NS_20
                + "\" xmlns:xlink=\"http://www.w3.org/1999/xlink\""
                + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                + " ODMVersion=\"1.3.2\" FileType=\"Snapshot\" FileOID=\"X\">"
                + "<Study OID=\"S\"><MetaDataVersion OID=\"MDV.1\" Name=\"m\" " + aMdvAttrs + ">"
                + aMdvBody + "</MetaDataVersion></Study></ODM>";
    }


    private static DefineXmlConverter run(String aXml, boolean aKeepLegacy) throws Exception
    {
        return DefineXmlConverter
                .forInputStream(new ByteArrayInputStream(aXml.getBytes(StandardCharsets.UTF_8)))
                .from(Version.V2_0).to(Version.V2_1).keepLegacyStandardAttributes(aKeepLegacy)
                .convert();
    }


    private static DefineXmlConverter runWithContext(String aXml, String aContext) throws Exception
    {
        return DefineXmlConverter
                .forInputStream(new ByteArrayInputStream(aXml.getBytes(StandardCharsets.UTF_8)))
                .from(Version.V2_0).to(Version.V2_1).context(aContext).convert();
    }


    private static DefineXmlConverter run(String aXml) throws Exception
    {
        return run(aXml, false);
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

    // ------------------------------------------------------------ def:Context


    @Test
    void contextIsAddedOnlyWhenTheDocumentDoesNotAlreadyCarryIt() throws Exception
    {
        // The value written comes from the caller's option, not a constant in the step.
        DefineXmlConverter added = runWithContext(v20("def:StandardName=\"SDTMIG\"", ""), "Other");
        Element root = added.getDocument().getDocumentElement();
        assertEquals("Other", DefineDomUtil.attrIgnoreNs(root, "Context"));
        assertTrue(any(added.getLog(), "added def:Context=\"Other\""));

        String preset = v20("def:StandardName=\"SDTMIG\"", "").replace("FileOID=\"X\">",
                "FileOID=\"X\" def:Context=\"Submission\">");
        DefineXmlConverter kept = runWithContext(preset, "Other");
        Element keptRoot = kept.getDocument().getDocumentElement();
        assertEquals("Submission", DefineDomUtil.attrIgnoreNs(keptRoot, "Context"),
                "an existing def:Context must survive untouched");
        assertEquals(1, countAttributesNamed(keptRoot, "Context"));
        assertFalse(any(kept.getLog(), "added def:Context"),
                "an existing def:Context must not be logged as added");
    }

    // ------------------------------------------------------------ def:Standards


    @Test
    void standardsAlreadyPresentAreLeftUntouched() throws Exception
    {
        DefineXmlConverter c = run(v20("def:StandardName=\"SDTMIG\" def:StandardVersion=\"3.4\"",
                "<def:Standards><def:Standard OID=\"STD.KEEP\" Name=\"ADAMIG\" Type=\"IG\"/>"
                        + "</def:Standards>"));

        assertEquals(1, DefineDomUtil.elementsByLocalName(c.getDocument(), "Standard").size());
        assertEquals("STD.KEEP",
                DefineDomUtil.firstByLocalName(c.getDocument(), "Standard").getAttribute("OID"));
        assertTrue(any(c.getLog(), "already present"));
        assertFalse(any(c.getLog(), "synthesised def:Standards"));
    }


    @Test
    void aMissingOrBlankStandardNameSynthesisesNothingAndWarns() throws Exception
    {
        for (String attrs : List.of("def:StandardVersion=\"3.4\"",
                "def:StandardName=\" \" def:StandardVersion=\"3.4\""))
        {
            DefineXmlConverter c = run(v20(attrs, ""));
            assertTrue(DefineDomUtil.elementsByLocalName(c.getDocument(), "Standards").isEmpty(),
                    "no def:Standards may be minted for: " + attrs);
            assertTrue(any(c.getWarnings(), "could not synthesise a def:Standards"),
                    "expected the synthesis warning for: " + attrs);
            // F-15: the failed synthesis must not leave the deprecated 2.0 attributes behind.
            Element mdv = DefineDomUtil.firstByLocalName(c.getDocument(), "MetaDataVersion");
            assertNull(DefineDomUtil.attrIgnoreNs(mdv, "StandardName"), attrs);
            assertNull(DefineDomUtil.attrIgnoreNs(mdv, "StandardVersion"), attrs);
        }
    }


    @Test
    void aSynthesisedStandardCarriesEveryAttributeAndANormalisedName() throws Exception
    {
        DefineXmlConverter c = run(v20("def:StandardName=\"SDTM-IG\" def:StandardVersion=\"3.1.2\"",
                "<ItemGroupDef OID=\"IG.DM\" Name=\"DM\"/>"));
        Element std = DefineDomUtil.firstByLocalName(c.getDocument(), "Standard");

        assertEquals(DEF_NS, std.getNamespaceURI());
        assertEquals("SDTMIG", std.getAttribute("Name"), "the hyphen must be stripped");
        assertEquals("IG", std.getAttribute("Type"));
        assertEquals("Final", std.getAttribute("Status"));
        assertEquals("3.1.2", std.getAttribute("Version"));
        assertFalse(std.getAttribute("OID").isEmpty(), "a minted OID must be written");
        // Placement: def:Standards ranks before ItemGroupDef in the MetaDataVersion sequence.
        assertEquals(List.of("Standards", "ItemGroupDef"),
                childNames(DefineDomUtil.firstByLocalName(c.getDocument(), "MetaDataVersion")));
        assertTrue(any(c.getWarnings(), "CT (controlled terminology) Standard rows"));
    }


    @Test
    void aStandardNameWithoutAHyphenIsPassedThroughUnchanged() throws Exception
    {
        DefineXmlConverter c = run(
                v20("def:StandardName=\"ADAMIG\" def:StandardVersion=\"1.1\"", ""));
        assertEquals("ADAMIG",
                DefineDomUtil.firstByLocalName(c.getDocument(), "Standard").getAttribute("Name"));
    }


    @Test
    void onlyItemGroupDefsWithoutAStandardOidAreStamped() throws Exception
    {
        DefineXmlConverter c = run(v20("def:StandardName=\"SDTMIG\" def:StandardVersion=\"3.4\"",
                "<ItemGroupDef OID=\"IG.KEEP\" def:StandardOID=\"STD.OTHER\"/>"
                        + "<ItemGroupDef OID=\"IG.BARE\"/>"));
        List<Element> groups = DefineDomUtil.elementsByLocalName(c.getDocument(), "ItemGroupDef");
        String minted = DefineDomUtil.firstByLocalName(c.getDocument(), "Standard")
                .getAttribute("OID");

        assertEquals("STD.OTHER", DefineDomUtil.attrIgnoreNs(groups.get(0), "StandardOID"),
                "an existing StandardOID must be preserved");
        assertEquals(minted, DefineDomUtil.attrIgnoreNs(groups.get(1), "StandardOID"));
    }


    @Test
    void legacyStandardAttributesAreDroppedUnlessTheFlagKeepsThem() throws Exception
    {
        String xml = v20("def:StandardName=\"SDTMIG\" def:StandardVersion=\"3.4\"", "");

        DefineXmlConverter dropped = run(xml, false);
        Element mdv = DefineDomUtil.firstByLocalName(dropped.getDocument(), "MetaDataVersion");
        assertNull(DefineDomUtil.attrIgnoreNs(mdv, "StandardName"));
        assertNull(DefineDomUtil.attrIgnoreNs(mdv, "StandardVersion"));
        assertTrue(any(dropped.getLog(), "dropped deprecated"));

        DefineXmlConverter kept = run(xml, true);
        Element keptMdv = DefineDomUtil.firstByLocalName(kept.getDocument(), "MetaDataVersion");
        assertEquals("SDTMIG", DefineDomUtil.attrIgnoreNs(keptMdv, "StandardName"));
        assertEquals("3.4", DefineDomUtil.attrIgnoreNs(keptMdv, "StandardVersion"));
        assertTrue(any(kept.getWarnings(), "kept deprecated"));
        assertFalse(any(kept.getLog(), "dropped deprecated"));
    }

    // ------------------------------------------------------------ def:DefineVersion


    @Test
    void defineVersionIsUpdatedInPlaceOrCreatedDefPrefixed() throws Exception
    {
        DefineXmlConverter updated = run(
                v20("def:DefineVersion=\"2.0.0\" def:StandardName=\"SDTMIG\"", ""));
        Element mdv = DefineDomUtil.firstByLocalName(updated.getDocument(), "MetaDataVersion");
        assertEquals("2.1.0", DefineDomUtil.attrIgnoreNs(mdv, "DefineVersion"));
        assertEquals(1, countAttributesNamed(mdv, "DefineVersion"),
                "the existing attribute must be updated, not duplicated");

        DefineXmlConverter created = run(v20("def:StandardName=\"SDTMIG\"", ""));
        Element bare = DefineDomUtil.firstByLocalName(created.getDocument(), "MetaDataVersion");
        assertEquals("2.1.0", bare.getAttributeNS(DEF_NS, "DefineVersion"),
                "a missing attribute must be created in the def namespace");
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

    // ------------------------------------------------------------ def:Class


    @Test
    void anItemGroupDefWithoutAClassAttributeIsLeftAlone() throws Exception
    {
        DefineXmlConverter c = run(
                v20("def:StandardName=\"SDTMIG\"", "<ItemGroupDef OID=\"IG.DM\" Name=\"DM\"/>"));

        assertTrue(DefineDomUtil.elementsByLocalName(c.getDocument(), "Class").isEmpty());
        assertFalse(any(c.getLog(), "def:Class attribute -> element"));
    }


    @Test
    void theClassAttributeBecomesAnElementPlacedBeforeTheLeaf() throws Exception
    {
        DefineXmlConverter withLeaf = run(v20("def:StandardName=\"SDTMIG\"",
                "<ItemGroupDef OID=\"IG.DM\" def:Class=\"SPECIAL PURPOSE\">"
                        + "<ItemRef ItemOID=\"IT.A\"/>"
                        + "<def:leaf ID=\"LF.DM\" xlink:href=\"dm.xpt\"/></ItemGroupDef>"));
        Element ig = DefineDomUtil.firstByLocalName(withLeaf.getDocument(), "ItemGroupDef");

        assertNull(DefineDomUtil.attrIgnoreNs(ig, "Class"), "the attribute must be removed");
        assertEquals(List.of("ItemRef", "Class", "leaf"), childNames(ig));
        Element cls = DefineDomUtil.firstByLocalName(withLeaf.getDocument(), "Class");
        assertEquals(DEF_NS, cls.getNamespaceURI());
        assertEquals("SPECIAL PURPOSE", cls.getAttribute("Name"));
        assertTrue(any(withLeaf.getLog(), "IG.DM: def:Class attribute -> element"));

        DefineXmlConverter noLeaf = run(v20("def:StandardName=\"SDTMIG\"",
                "<ItemGroupDef OID=\"IG.DM\" def:Class=\"EVENTS\">"
                        + "<ItemRef ItemOID=\"IT.A\"/></ItemGroupDef>"));
        assertEquals(List.of("ItemRef", "Class"),
                childNames(DefineDomUtil.firstByLocalName(noLeaf.getDocument(), "ItemGroupDef")));
    }

    // ------------------------------------------------------------ def:Origin


    @Test
    void anOriginThatAlreadyCarriesASourceIsLeftUntouched() throws Exception
    {
        DefineXmlConverter c = run(v20("def:StandardName=\"SDTMIG\"",
                "<ItemDef OID=\"IT.A\"><def:Origin Type=\"CRF\" Source=\"Investigator\"/>"
                        + "</ItemDef>"));
        Element origin = DefineDomUtil.firstByLocalName(c.getDocument(), "Origin");

        assertEquals("CRF", origin.getAttribute("Type"), "the type must not be remapped");
        assertEquals("Investigator", origin.getAttribute("Source"));
    }


    /** Absence vs empty: {@code Source=""} reads as absent and the origin is converted. */
    @Test
    void anEmptySourceAttributeCountsAsAbsent() throws Exception
    {
        DefineXmlConverter c = run(v20("def:StandardName=\"SDTMIG\"",
                "<ItemDef OID=\"IT.A\"><def:Origin Type=\"CRF\" Source=\"\"/></ItemDef>"));
        Element origin = DefineDomUtil.firstByLocalName(c.getDocument(), "Origin");

        assertEquals("Collected", origin.getAttribute("Type"));
        assertEquals("Investigator", origin.getAttribute("Source"));
    }


    @Test
    void anOriginWithoutATypeWarnsAndIsLeftUnchanged() throws Exception
    {
        DefineXmlConverter c = run(v20("def:StandardName=\"SDTMIG\"",
                "<ItemDef OID=\"IT.A\"><def:Origin/></ItemDef>"));
        Element origin = DefineDomUtil.firstByLocalName(c.getDocument(), "Origin");

        assertFalse(origin.hasAttribute("Type"));
        assertFalse(origin.hasAttribute("Source"));
        assertTrue(any(c.getWarnings(), "def:Origin without @Type"));
    }


    @Test
    void anUnrecognisedOriginTypeWarnsAndAddsNoSource() throws Exception
    {
        DefineXmlConverter c = run(v20("def:StandardName=\"SDTMIG\"",
                "<ItemDef OID=\"IT.A\"><def:Origin Type=\"Bogus\"/></ItemDef>"));
        Element origin = DefineDomUtil.firstByLocalName(c.getDocument(), "Origin");

        assertEquals("Bogus", origin.getAttribute("Type"));
        assertFalse(origin.hasAttribute("Source"));
        assertTrue(any(c.getWarnings(), "unrecognised Origin @Type \"Bogus\""));
    }


    /**
     * Boundary on {@code !newType.equals(type)}: {@code Derived} maps to itself, so no
     * {@code setAttribute} happens, while {@code CRF} maps to {@code Collected} and does.
     */
    @Test
    void onlyARemappedTypeIsRewrittenAndAnEmptySourceIsOmitted() throws Exception
    {
        DefineXmlConverter c = run(v20("def:StandardName=\"SDTMIG\"",
                "<ItemDef OID=\"IT.A\"><def:Origin Type=\"Derived\"/></ItemDef>"
                        + "<ItemDef OID=\"IT.B\"><def:Origin Type=\"CRF\"/></ItemDef>"
                        + "<ItemDef OID=\"IT.C\"><def:Origin Type=\"Predecessor\"/></ItemDef>"));
        List<Element> origins = DefineDomUtil.elementsByLocalName(c.getDocument(), "Origin");

        assertEquals("Derived", origins.get(0).getAttribute("Type"));
        assertEquals("Sponsor", origins.get(0).getAttribute("Source"));
        assertEquals("Collected", origins.get(1).getAttribute("Type"));
        assertEquals("Investigator", origins.get(1).getAttribute("Source"));
        assertEquals("Predecessor", origins.get(2).getAttribute("Type"));
        assertFalse(origins.get(2).hasAttribute("Source"),
                "Predecessor maps to an empty Source, which must not be written");
    }


    @Test
    void ambiguousOriginTypesWarnWhileTheRestOnlyLog() throws Exception
    {
        DefineXmlConverter ambiguous = run(v20("def:StandardName=\"SDTMIG\"",
                "<ItemDef OID=\"IT.A\"><def:Origin Type=\"eDT\"/></ItemDef>"
                        + "<ItemDef OID=\"IT.B\"><def:Origin Type=\"Derived\"/></ItemDef>"
                        + "<ItemDef OID=\"IT.C\"><def:Origin Type=\"Assigned\"/></ItemDef>"));
        assertEquals(3, countContaining(ambiguous.getWarnings(), "Source is ambiguous"));
        assertEquals(0, countContaining(ambiguous.getLog(), "Origin @Type"));

        DefineXmlConverter plain = run(v20("def:StandardName=\"SDTMIG\"",
                "<ItemDef OID=\"IT.A\"><def:Origin Type=\"CRF\"/></ItemDef>"
                        + "<ItemDef OID=\"IT.B\"><def:Origin Type=\"Protocol\"/></ItemDef>"
                        + "<ItemDef OID=\"IT.C\"><def:Origin Type=\"Predecessor\"/></ItemDef>"));
        assertEquals(0, countContaining(plain.getWarnings(), "Source is ambiguous"));
        assertEquals(3, countContaining(plain.getLog(), "Origin @Type"));
        assertTrue(any(plain.getLog(),
                "Origin @Type \"CRF\" -> Type=\"Collected\" Source=\"Investigator\""));
        assertTrue(any(plain.getLog(), "Origin @Type \"Predecessor\" -> Type=\"Predecessor\""),
                "an empty source must not be appended to the log line");
    }

    // ------------------------------------------------------------ CodeList standards


    @Test
    void theCodeListStandardWarningIsRaisedAtMostOnce() throws Exception
    {
        DefineXmlConverter bare = run(v20("def:StandardName=\"SDTMIG\"",
                "<CodeList OID=\"CL.1\"/><CodeList OID=\"CL.2\"/><CodeList OID=\"CL.3\"/>"));
        assertEquals(1, countContaining(bare.getWarnings(), "CodeLists left without"));

        DefineXmlConverter covered = run(v20("def:StandardName=\"SDTMIG\"",
                "<CodeList OID=\"CL.1\" def:StandardOID=\"STD.CT\"/>"
                        + "<CodeList OID=\"CL.2\"><ExternalCodeList Dictionary=\"MedDRA\"/>"
                        + "</CodeList>"));
        assertEquals(0, countContaining(covered.getWarnings(), "CodeLists left without"),
                "either a StandardOID or an ExternalCodeList suppresses the warning");
    }

    // ------------------------------------------------------------ schemaLocation / stylesheet


    @Test
    void schemaLocationIsRewrittenForAllThreeSpellings() throws Exception
    {
        String xml = v20("def:StandardName=\"SDTMIG\"", "").replace("FileOID=\"X\">",
                "FileOID=\"X\" xsi:schemaLocation=\"" + DefineXmlConverter.DEF_NS_20
                        + " define2-0-0.xsd http://example.org/def/v2.0 other.xsd\">");
        DefineXmlConverter c = run(xml);
        String actual = DefineDomUtil.attrIgnoreNs(c.getDocument().getDocumentElement(),
                "schemaLocation");

        assertEquals(DefineXmlConverter.DEF_NS_21
                + " define2-1-0.xsd http://example.org/def/v2.1 other.xsd", actual);
    }


    @Test
    void aDocumentWithoutASchemaLocationConvertsCleanly() throws Exception
    {
        DefineXmlConverter c = run(v20("def:StandardName=\"SDTMIG\"", ""));
        assertNull(
                DefineDomUtil.attrIgnoreNs(c.getDocument().getDocumentElement(), "schemaLocation"));
    }


    @Test
    void onlyAMatchingStylesheetProcessingInstructionIsRewritten() throws Exception
    {
        String pi = "<?xml-stylesheet type=\"text/xsl\" href=\"define2-0-0.xsl\"?>";
        DefineXmlConverter c = run(
                v20("def:StandardName=\"SDTMIG\"", "").replace("?><ODM", "?>" + pi + "<ODM"));
        String out = new String(c.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(out.contains("define2-1-0.xsl"), out);
        assertFalse(out.contains("define2-0-0.xsl"), out);
        assertTrue(any(c.getLog(), "updated stylesheet reference to define2-1"));

        String otherPi = "<?other-target href=\"define2-0-0.xsl\"?>";
        DefineXmlConverter untouched = run(
                v20("def:StandardName=\"SDTMIG\"", "").replace("?><ODM", "?>" + otherPi + "<ODM"));
        String otherOut = new String(untouched.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(otherOut.contains("define2-0-0.xsl"),
                "a processing instruction with another target must not be rewritten: " + otherOut);
        assertFalse(any(untouched.getLog(), "updated stylesheet reference"));
    }


    @Test
    void aDocumentWithoutAStylesheetLogsNoStylesheetUpdate() throws Exception
    {
        DefineXmlConverter c = run(v20("def:StandardName=\"SDTMIG\"", ""));
        assertFalse(any(c.getLog(), "updated stylesheet reference"));
    }


    /** The whole document must end up in the 2.1 def namespace, elements and attributes alike. */
    @Test
    void theDefNamespaceIsBumpedEverywhere() throws Exception
    {
        DefineXmlConverter c = run(v20("def:StandardName=\"SDTMIG\"",
                "<ItemGroupDef OID=\"IG.DM\" def:Structure=\"one\">"
                        + "<def:leaf ID=\"LF.DM\" xlink:href=\"dm.xpt\"/></ItemGroupDef>"));
        Document doc = c.getDocument();

        assertFalse(DefineDomUtil.hasNamespace(doc, DefineXmlConverter.DEF_NS_20));
        assertTrue(DefineDomUtil.hasNamespace(doc, DEF_NS));
        assertEquals(DEF_NS, DefineDomUtil.firstByLocalName(doc, "leaf").getNamespaceURI());
        assertEquals("one", DefineDomUtil.firstByLocalName(doc, "ItemGroupDef")
                .getAttributeNS(DEF_NS, "Structure"));
    }

    // ================= fixes pinned after the owner rulings (F-08, F-09) =================


    /** F-08: without a @Version no valid def:Standard exists; synthesise nothing and warn. */
    @Test
    void aMissingOrBlankStandardVersionSynthesisesNothingAndWarns() throws Exception
    {
        for (String attrs : List.of("def:StandardName=\"SDTM-IG\"",
                "def:StandardName=\"SDTM-IG\" def:StandardVersion=\"\"",
                "def:StandardName=\"SDTM-IG\" def:StandardVersion=\" \""))
        {
            DefineXmlConverter c = run(v20(attrs, ""));
            assertTrue(DefineDomUtil.elementsByLocalName(c.getDocument(), "Standard").isEmpty(),
                    "no def:Standard may be synthesised without a @Version (" + attrs + ")");
            assertTrue(any(c.getWarnings(), "no @def:StandardVersion in v2.0 input"),
                    "warnings for [" + attrs + "]: " + c.getWarnings());
            // F-15: round 1's early return skipped the deprecated-attribute removal, so a
            // document stamped 2.1.0 silently kept @def:StandardName — re-opening F-09.
            Element mdv = DefineDomUtil.firstByLocalName(c.getDocument(), "MetaDataVersion");
            assertNull(DefineDomUtil.attrIgnoreNs(mdv, "StandardName"),
                    "a 2.1-stamped document must not retain @def:StandardName (" + attrs + ")");
            assertNull(DefineDomUtil.attrIgnoreNs(mdv, "StandardVersion"), attrs);
        }
    }


    /** F-15: with the keep-legacy flag the attributes survive a failed synthesis, but warned. */
    @Test
    void aFailedSynthesisStillHonoursTheKeepLegacyFlag() throws Exception
    {
        DefineXmlConverter kept = run(v20("def:StandardName=\"SDTM-IG\"", ""), true);
        Element mdv = DefineDomUtil.firstByLocalName(kept.getDocument(), "MetaDataVersion");
        assertEquals("SDTM-IG", DefineDomUtil.attrIgnoreNs(mdv, "StandardName"));
        assertTrue(any(kept.getWarnings(), "kept deprecated"),
                "retaining deprecated attributes must never be silent");
        assertTrue(any(kept.getWarnings(), "no @def:StandardVersion in v2.0 input"));
    }


    /** F-09: a def:Class attribute beside a def:Class element is removed; disagreement warns. */
    @Test
    void aClassAttributeBesideAClassElementIsRemovedAndDisagreementWarns() throws Exception
    {
        DefineXmlConverter disagree = run(v20("", "<ItemGroupDef OID=\"IG.AE\""
                + " def:Class=\"EVENTS\"><def:Class Name=\"FINDINGS\"/></ItemGroupDef>"));
        Element ig = DefineDomUtil.firstByLocalName(disagree.getDocument(), "ItemGroupDef");
        assertNull(DefineDomUtil.attrIgnoreNs(ig, "Class"),
                "the deprecated 2.0 attribute must not survive beside the 2.1 element");
        List<Element> classEls = DefineDomUtil.childrenByLocalName(ig, "Class");
        assertEquals(1, classEls.size());
        assertEquals("FINDINGS", classEls.get(0).getAttribute("Name"));
        assertTrue(any(disagree.getWarnings(),
                "ItemGroupDef IG.AE: deprecated def:Class attribute \"EVENTS\" disagreed with"
                        + " the def:Class element \"FINDINGS\"; the attribute was removed"),
                "warnings: " + disagree.getWarnings());

        DefineXmlConverter agree = run(v20("", "<ItemGroupDef OID=\"IG.AE\""
                + " def:Class=\"EVENTS\"><def:Class Name=\"EVENTS\"/></ItemGroupDef>"));
        Element agreeIg = DefineDomUtil.firstByLocalName(agree.getDocument(), "ItemGroupDef");
        assertNull(DefineDomUtil.attrIgnoreNs(agreeIg, "Class"));
        assertFalse(any(agree.getWarnings(), "disagreed with"),
                "an agreeing attribute is removed silently: " + agree.getWarnings());
    }
}
