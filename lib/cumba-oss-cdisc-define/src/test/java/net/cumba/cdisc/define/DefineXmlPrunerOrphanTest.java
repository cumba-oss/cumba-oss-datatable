package net.cumba.cdisc.define;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Orphan-cascade tests for {@link DefineXmlPruner}. A wrongly cascaded deletion removes metadata a
 * regulator relies on and leaves a document that is still well-formed, so the only defence is
 * asserting exactly which elements survive a prune — and, for the counters, the progress log, which
 * is their only exposed surface.
 *
 * <p>
 * The owner rulings on F-cdisc-define-10, -11 and -13 are executed and pinned below: the ItemDef
 * reference set covers every ItemOID spelling (an ItemDef reachable only through a
 * RangeCheck/@def:ItemOID survives), a cascade truncated by the pass cap records a WARNING line in
 * the log (pinned through the package-visible pass-cap overload), and def-namespace detection
 * delegates to {@code DefineDomUtil.hasNamespace}, seeing declarations below the root — the
 * outcome-level namespace test is kept as well.
 * </p>
 */
class DefineXmlPrunerOrphanTest
{

    private static final String ODM_NS = "http://www.cdisc.org/ns/odm/v1.3";

    private static final String DEF_NS_20 = "http://www.cdisc.org/ns/def/v2.0";

    private static final String DEF_NS_21 = "http://www.cdisc.org/ns/def/v2.1";

    private static final String OTHER_NS = "http://example.org/vendor";

    private static String odm(String aDefNs, String aBody)
    {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><ODM xmlns=\"" + ODM_NS
                + "\" xmlns:def=\"" + aDefNs + "\" xmlns:x=\"" + OTHER_NS
                + "\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" FileOID=\"X\">"
                + "<Study OID=\"S\"><MetaDataVersion OID=\"MDV.1\">" + aBody
                + "</MetaDataVersion></Study></ODM>";
    }


    private static String odm(String aBody)
    {
        return odm(DEF_NS_20, aBody);
    }


    private static DefineXmlPruner pruner(String aXml) throws Exception
    {
        return DefineXmlPruner
                .forInputStream(new ByteArrayInputStream(aXml.getBytes(StandardCharsets.UTF_8)));
    }


    private static List<Element> all(Document aDoc, String aLocalName)
    {
        return DefineDomUtil.elementsByLocalName(aDoc, aLocalName);
    }


    private static List<String> oidsOf(Document aDoc, String aLocalName)
    {
        return all(aDoc, aLocalName).stream().map(el -> el.getAttribute("OID")).toList();
    }


    private static boolean any(List<String> aLines, String aNeedle)
    {
        return aLines.stream().anyMatch(line -> line.contains(aNeedle));
    }


    private static long countContaining(List<String> aLines, String aNeedle)
    {
        return aLines.stream().filter(line -> line.contains(aNeedle)).count();
    }

    // ------------------------------------------------------------ the cascade loop


    /**
     * Boundary at zero removals: a consistent document must run exactly one pass. The mutant
     * {@code totalRemoved >= 0} would spin to the 20-pass cap, which only the log reveals.
     */
    @Test
    void aConsistentDocumentRunsExactlyOneCascadePass() throws Exception
    {
        DefineXmlPruner p = pruner(odm("<ItemGroupDef OID=\"IG.DM\">"
                + "<ItemRef ItemOID=\"IT.A\"/></ItemGroupDef><ItemDef OID=\"IT.A\"/>"))
                        .cascadeOrphans();

        assertEquals(1, countContaining(p.getLog(), "Cascade pass"));
        assertTrue(any(p.getLog(), "Cascade pass 1: removed 0 orphans"), p.getLog().toString());
        assertEquals(List.of("IT.A"), oidsOf(p.getDocument(), "ItemDef"));
    }


    /**
     * A genuine two-pass document: pass 1 removes an ItemDef, which orphans the ValueListDef that
     * held the only reference to a second ItemDef, which pass 2 then removes. Nothing but a second
     * pass can reach it, so this is the one input that pins the accumulating {@code +=}.
     */
    @Test
    void anOrphanExposedByPassOneIsRemovedInPassTwo() throws Exception
    {
        DefineXmlPruner p = pruner(odm("<ItemGroupDef OID=\"IG.X\">"
                + "<ItemRef ItemOID=\"IT.A\"/></ItemGroupDef>"
                + "<ItemDef OID=\"IT.A\"><def:ValueListRef ValueListOID=\"VL.A\"/></ItemDef>"
                + "<def:ValueListDef OID=\"VL.A\"><ItemRef ItemOID=\"IT.B\"/></def:ValueListDef>"
                + "<ItemDef OID=\"IT.B\"/>")).removeItemGroups("IG.X").cascadeOrphans();

        assertTrue(any(p.getLog(), "Cascade pass 2"),
                "a second pass must actually run: " + p.getLog());
        assertTrue(all(p.getDocument(), "ItemDef").isEmpty(),
                "IT.B loses its last reference only once VL.A is gone: "
                        + oidsOf(p.getDocument(), "ItemDef"));
        assertTrue(all(p.getDocument(), "ValueListDef").isEmpty());
    }

    // ------------------------------------------------------------ ItemRef


    @Test
    void onlyTheDanglingItemRefIsRemovedAndItIsLogged() throws Exception
    {
        DefineXmlPruner p = pruner(odm("<ItemGroupDef OID=\"IG.DM\">"
                + "<ItemRef ItemOID=\"IT.LIVE\"/><ItemRef ItemOID=\"IT.GONE\"/>"
                + "</ItemGroupDef><ItemDef OID=\"IT.LIVE\"/>")).cascadeOrphans();
        List<Element> refs = all(p.getDocument(), "ItemRef");

        assertEquals(1, refs.size());
        assertEquals("IT.LIVE", refs.get(0).getAttribute("ItemOID"));
        assertTrue(any(p.getLog(), "Removed orphaned ItemRef -> IT.GONE"), p.getLog().toString());
    }


    /** Boundary: a document holding exactly one ItemRef, which is itself the orphan. */
    @Test
    void aDocumentWithASingleOrphanedItemRefStillLosesIt() throws Exception
    {
        DefineXmlPruner p = pruner(odm(
                "<ItemGroupDef OID=\"IG.DM\">" + "<ItemRef ItemOID=\"IT.GONE\"/></ItemGroupDef>"))
                        .cascadeOrphans();

        assertTrue(all(p.getDocument(), "ItemRef").isEmpty());
    }


    /** Absence vs empty: an ItemRef with no ItemOID at all must be left alone, not deleted. */
    @Test
    void anItemRefWithoutAnItemOidIsKept() throws Exception
    {
        DefineXmlPruner p = pruner(
                odm("<ItemGroupDef OID=\"IG.DM\">" + "<ItemRef/></ItemGroupDef>")).cascadeOrphans();

        assertEquals(1, all(p.getDocument(), "ItemRef").size());
        assertFalse(any(p.getLog(), "Removed orphaned ItemRef"));
    }


    /** An ItemDef with no OID is not a removal candidate however unreferenced it looks. */
    @Test
    void anItemDefWithoutAnOidIsNeverRemoved() throws Exception
    {
        DefineXmlPruner p = pruner(odm("<ItemDef Name=\"nameless\"/>")).cascadeOrphans();

        assertEquals(1, all(p.getDocument(), "ItemDef").size());
    }

    // ------------------------------------------------------------ the definition families


    @Test
    void onlyTheUnreferencedCodeListIsRemoved() throws Exception
    {
        DefineXmlPruner p = pruner(
                odm("<ItemGroupDef OID=\"IG.DM\">" + "<ItemRef ItemOID=\"IT.A\"/></ItemGroupDef>"
                        + "<ItemDef OID=\"IT.A\"><CodeListRef CodeListOID=\"CL.LIVE\"/></ItemDef>"
                        + "<CodeList OID=\"CL.LIVE\"/><CodeList OID=\"CL.GONE\"/>"))
                                .cascadeOrphans();

        assertEquals(List.of("CL.LIVE"), oidsOf(p.getDocument(), "CodeList"));
        assertTrue(any(p.getLog(), "Removed orphaned CodeList: CL.GONE"));
    }


    /** A CodeListRef with no CodeListOID must not make the empty string a live reference. */
    @Test
    void aCodeListRefWithoutAnOidShieldsNothing() throws Exception
    {
        DefineXmlPruner p = pruner(odm("<ItemGroupDef OID=\"IG.DM\">"
                + "<ItemRef ItemOID=\"IT.A\"/></ItemGroupDef>"
                + "<ItemDef OID=\"IT.A\"><CodeListRef/></ItemDef>" + "<CodeList OID=\"CL.GONE\"/>"))
                        .cascadeOrphans();

        assertTrue(all(p.getDocument(), "CodeList").isEmpty());
    }


    @Test
    void onlyTheUnreferencedMethodDefIsRemoved() throws Exception
    {
        DefineXmlPruner p = pruner(odm(
                "<ItemGroupDef OID=\"IG.DM\">" + "<ItemRef ItemOID=\"IT.A\" MethodOID=\"MT.LIVE\"/>"
                        + "<ItemRef ItemOID=\"IT.B\"/></ItemGroupDef>"
                        + "<ItemDef OID=\"IT.A\"/><ItemDef OID=\"IT.B\"/>"
                        + "<MethodDef OID=\"MT.LIVE\"/><MethodDef OID=\"MT.GONE\"/>"))
                                .cascadeOrphans();

        assertEquals(List.of("MT.LIVE"), oidsOf(p.getDocument(), "MethodDef"));
        assertTrue(any(p.getLog(), "Removed orphaned MethodDef: MT.GONE"));
    }


    /** The CommentDef scan matches {@code def:CommentOID} by local name, prefix and all. */
    @Test
    void aPrefixedCommentOidKeepsItsCommentDefAlive() throws Exception
    {
        DefineXmlPruner p = pruner(
                odm("<ItemGroupDef OID=\"IG.DM\">" + "<ItemRef ItemOID=\"IT.A\"/></ItemGroupDef>"
                        + "<ItemDef OID=\"IT.A\" def:CommentOID=\"COM.LIVE\"/>"
                        + "<def:CommentDef OID=\"COM.LIVE\"/><def:CommentDef OID=\"COM.GONE\"/>"))
                                .cascadeOrphans();

        assertEquals(List.of("COM.LIVE"), oidsOf(p.getDocument(), "CommentDef"));
        assertTrue(any(p.getLog(), "Removed orphaned CommentDef: COM.GONE"));
    }


    @Test
    void onlyTheUnreferencedValueListDefIsRemoved() throws Exception
    {
        DefineXmlPruner p = pruner(odm("<ItemGroupDef OID=\"IG.DM\">"
                + "<ItemRef ItemOID=\"IT.A\"/></ItemGroupDef>"
                + "<ItemDef OID=\"IT.A\"><def:ValueListRef ValueListOID=\"VL.LIVE\"/></ItemDef>"
                + "<def:ValueListDef OID=\"VL.LIVE\"><ItemRef ItemOID=\"IT.A\"/></def:ValueListDef>"
                + "<def:ValueListDef OID=\"VL.GONE\"/>")).cascadeOrphans();

        assertEquals(List.of("VL.LIVE"), oidsOf(p.getDocument(), "ValueListDef"));
        assertTrue(any(p.getLog(), "Removed orphaned ValueListDef: VL.GONE"));
    }


    /**
     * Boundary at zero def-namespace hits: a ValueListDef in a foreign namespace makes the
     * def-namespace query return nothing, so the local-name fallback has to find it.
     */
    @Test
    void aValueListDefOutsideTheDefNamespaceIsStillReachable() throws Exception
    {
        DefineXmlPruner p = pruner(odm("<x:ValueListDef OID=\"VL.GONE\"/>")).cascadeOrphans();

        assertTrue(all(p.getDocument(), "ValueListDef").isEmpty(),
                "the local-name fallback must reach a foreign-namespace ValueListDef");
        assertTrue(any(p.getLog(), "Removed orphaned ValueListDef: VL.GONE"));
    }


    @Test
    void onlyTheUnreferencedWhereClauseDefIsRemoved() throws Exception
    {
        DefineXmlPruner p = pruner(odm("<ItemGroupDef OID=\"IG.DM\">" + "<ItemRef ItemOID=\"IT.A\">"
                + "<def:WhereClauseRef WhereClauseOID=\"WC.LIVE\"/></ItemRef></ItemGroupDef>"
                + "<ItemDef OID=\"IT.A\"/>"
                + "<def:WhereClauseDef OID=\"WC.LIVE\"/><def:WhereClauseDef OID=\"WC.GONE\"/>"))
                        .cascadeOrphans();

        assertEquals(List.of("WC.LIVE"), oidsOf(p.getDocument(), "WhereClauseDef"));
        assertTrue(any(p.getLog(), "Removed orphaned WhereClauseDef: WC.GONE"));
    }


    /** A WhereClauseRef with no WhereClauseOID shields nothing. */
    @Test
    void aWhereClauseRefWithoutAnOidShieldsNothing() throws Exception
    {
        DefineXmlPruner p = pruner(odm("<ItemGroupDef OID=\"IG.DM\">"
                + "<ItemRef ItemOID=\"IT.A\"><def:WhereClauseRef/></ItemRef></ItemGroupDef>"
                + "<ItemDef OID=\"IT.A\"/><def:WhereClauseDef OID=\"WC.GONE\"/>")).cascadeOrphans();

        assertTrue(all(p.getDocument(), "WhereClauseDef").isEmpty());
    }


    @Test
    void aWhereClauseDefOutsideTheDefNamespaceIsStillReachable() throws Exception
    {
        DefineXmlPruner p = pruner(odm("<x:WhereClauseDef OID=\"WC.GONE\"/>")).cascadeOrphans();

        assertTrue(all(p.getDocument(), "WhereClauseDef").isEmpty());
        assertTrue(any(p.getLog(), "Removed orphaned WhereClauseDef: WC.GONE"));
    }

    // ------------------------------------------------------------ leaves


    /**
     * ⭐ The dataset-file leaf. A {@code def:leaf} inside an ItemGroupDef names the dataset file
     * itself and is never referenced by a DocumentRef; deleting it destroys real data.
     */
    @Test
    void aLeafInsideAnItemGroupDefSurvivesWhileAFreeStandingOneDoesNot() throws Exception
    {
        DefineXmlPruner p = pruner(
                odm("<ItemGroupDef OID=\"IG.DM\">" + "<ItemRef ItemOID=\"IT.A\"/>"
                        + "<def:leaf ID=\"LF.DATASET\" xlink:href=\"dm.xpt\"/></ItemGroupDef>"
                        + "<ItemDef OID=\"IT.A\"/>"
                        + "<def:leaf ID=\"LF.GONE\" xlink:href=\"orphan.pdf\"/>")).cascadeOrphans();
        List<Element> leafs = all(p.getDocument(), "leaf");

        assertEquals(1, leafs.size(), "the dataset leaf must survive");
        assertEquals("LF.DATASET", leafs.get(0).getAttribute("ID"));
        assertTrue(any(p.getLog(), "Removed orphaned leaf: LF.GONE"));
    }


    /** A leaf named by a {@code def:DocumentRef/@leafID} is referenced and must be kept. */
    @Test
    void aLeafReferencedByADocumentRefIsKept() throws Exception
    {
        DefineXmlPruner p = pruner(
                odm("<def:AnnotatedCRF>" + "<def:DocumentRef leafID=\"LF.CRF\"/></def:AnnotatedCRF>"
                        + "<def:leaf ID=\"LF.CRF\" xlink:href=\"acrf.pdf\"/>"
                        + "<def:leaf ID=\"LF.GONE\" xlink:href=\"orphan.pdf\"/>")).cascadeOrphans();
        List<Element> leafs = all(p.getDocument(), "leaf");

        assertEquals(1, leafs.size());
        assertEquals("LF.CRF", leafs.get(0).getAttribute("ID"));
    }

    // ------------------------------------------------------------ namespaces


    /**
     * Both Define-XML dialects must prune identically. This asserts the outcome rather than the
     * detection rule, so it stays valid whatever F-cdisc-define-13 is resolved to.
     */
    @Test
    void anUnreferencedWhereClauseDefGoesInBoth20And21Documents() throws Exception
    {
        for (String ns : List.of(DEF_NS_20, DEF_NS_21))
        {
            DefineXmlPruner p = pruner(odm(ns, "<def:WhereClauseDef OID=\"WC.GONE\"/>"))
                    .cascadeOrphans();
            assertTrue(all(p.getDocument(), "WhereClauseDef").isEmpty(),
                    "WC.GONE must be pruned from a document declaring " + ns);
        }
    }


    /** The three-step element lookup: ODM namespace, then def namespace, then any namespace. */
    @Test
    void anUnreferencedCodeListIsFoundInEveryNamespaceTier() throws Exception
    {
        for (String body : List.of("<CodeList OID=\"CL.GONE\"/>", "<def:CodeList OID=\"CL.GONE\"/>",
                "<x:CodeList OID=\"CL.GONE\"/>"))
        {
            DefineXmlPruner p = pruner(odm(body)).cascadeOrphans();
            assertTrue(all(p.getDocument(), "CodeList").isEmpty(), "not pruned for: " + body);
            assertTrue(any(p.getLog(), "Removed orphaned CodeList: CL.GONE"), body);
        }
    }

    // ------------------------------------------------------------ explicit removal


    @Test
    void aValueListDefIsRemovableByOidInEitherNamespace() throws Exception
    {
        for (String body : List.of("<ValueListDef OID=\"VL.1\"/>",
                "<def:ValueListDef OID=\"VL.1\"/>"))
        {
            DefineXmlPruner p = pruner(odm(body)).removeValueListDefs("VL.1");
            assertTrue(all(p.getDocument(), "ValueListDef").isEmpty(), "not removed for: " + body);
            assertTrue(any(p.getLog(), "Removed ValueListDef: VL.1"), body);
        }
    }


    /** {@code removeLeafs} must find a leaf that is not in the detected def namespace. */
    @Test
    void aLeafIsRemovableByIdEvenOutsideTheDefNamespace() throws Exception
    {
        DefineXmlPruner p = pruner(odm("<x:leaf ID=\"LF.1\" xlink:href=\"a.pdf\"/>"))
                .removeLeafs("LF.1");

        assertTrue(all(p.getDocument(), "leaf").isEmpty());
        assertTrue(any(p.getLog(), "Removed leaf: LF.1"));
    }

    // ================ fixes pinned after the owner rulings (F-10, F-11, F-13) ================


    /** F-10: a RangeCheck/@def:ItemOID is a live reference; its ItemDef must survive. */
    @Test
    void anItemDefReferencedOnlyByARangeCheckSurvivesTheCascade() throws Exception
    {
        DefineXmlPruner p = pruner(odm("<ItemGroupDef OID=\"IG.VS\" Name=\"VS\">"
                + "<ItemRef ItemOID=\"IT.VS.VSTESTCD\">"
                + "<def:WhereClauseRef WhereClauseOID=\"WC.1\"/></ItemRef></ItemGroupDef>"
                + "<ItemDef OID=\"IT.VS.VSTESTCD\" Name=\"VSTESTCD\" DataType=\"text\"/>"
                + "<ItemDef OID=\"IT.VS.VSPOS\" Name=\"VSPOS\" DataType=\"text\"/>"
                + "<def:WhereClauseDef OID=\"WC.1\">"
                + "<RangeCheck def:ItemOID=\"IT.VS.VSPOS\" Comparator=\"EQ\" SoftHard=\"Soft\">"
                + "<CheckValue>SUPINE</CheckValue></RangeCheck></def:WhereClauseDef>"));
        p.cascadeOrphans();

        assertTrue(oidsOf(p.getDocument(), "ItemDef").contains("IT.VS.VSPOS"),
                "an ItemDef referenced only by a RangeCheck def:ItemOID is not an orphan; deleting"
                        + " it leaves the surviving WhereClauseDef dangling");
        assertEquals(List.of("WC.1"), oidsOf(p.getDocument(), "WhereClauseDef"));
    }


    /**
     * F-11/F-19: the truncation WARNING must mean "orphans remain in the output", not "the last
     * pass removed something". The fixture needs a genuine two-pass chain: the WhereClauseDef is
     * removed in pass 1 (nothing references it), which only THEN orphans IT.B — the RangeCheck that
     * referenced IT.B ran ahead of the WhereClauseDef sweep in the same pass. Capped at 1 pass,
     * IT.B is really left behind.
     */
    @Test
    void aCascadeTruncatedByThePassCapRecordsAWarning() throws Exception
    {
        String body = "<ItemDef OID=\"IT.B\" Name=\"B\" DataType=\"text\"/>"
                + "<def:WhereClauseDef OID=\"WC.GONE\">"
                + "<RangeCheck def:ItemOID=\"IT.B\" Comparator=\"EQ\" SoftHard=\"Soft\">"
                + "<CheckValue>X</CheckValue></RangeCheck></def:WhereClauseDef>";

        DefineXmlPruner truncated = pruner(odm(body));
        truncated.cascadeOrphans(1);
        assertTrue(oidsOf(truncated.getDocument(), "ItemDef").contains("IT.B"),
                "precondition: the cap must actually leave an orphan behind");
        assertTrue(
                truncated.getLog().stream()
                        .anyMatch(l -> l.startsWith("WARNING:") && l.contains("1-pass cap")),
                "a truncated cascade must record a WARNING; log was: " + truncated.getLog());

        DefineXmlPruner converged = pruner(odm(body));
        converged.cascadeOrphans();
        assertTrue(oidsOf(converged.getDocument(), "ItemDef").isEmpty(),
                "the uncapped cascade removes the whole chain");
        assertFalse(converged.getLog().stream().anyMatch(l -> l.startsWith("WARNING:")),
                "a converged cascade records no warning; log was: " + converged.getLog());
    }


    /**
     * F-19: a document whose last orphan is removed exactly in the capped pass has CONVERGED — the
     * output is clean, so no truncation warning may fire. Round 1 pinned the opposite (the false
     * positive) with this very fixture.
     */
    @Test
    void aCascadeConvergingExactlyAtTheCapRecordsNoWarning() throws Exception
    {
        String body = "<ItemGroupDef OID=\"IG.DM\" Name=\"DM\">"
                + "<ItemRef ItemOID=\"IT.A\"/></ItemGroupDef>"
                + "<ItemDef OID=\"IT.A\" Name=\"A\" DataType=\"text\"/>"
                + "<CodeList OID=\"CL.UNUSED\" Name=\"X\" DataType=\"text\"/>";

        DefineXmlPruner p = pruner(odm(body));
        p.cascadeOrphans(1);
        assertTrue(all(p.getDocument(), "CodeList").isEmpty(),
                "the only orphan is gone in pass 1: the output is clean");
        assertFalse(p.getLog().stream().anyMatch(l -> l.startsWith("WARNING:")),
                "clean output must not carry a truncation warning; log was: " + p.getLog());
    }


    /** F-13: detection must see a def 2.1 declaration below the root or under another prefix. */
    @Test
    void defineNamespaceDetectionSeesDescendantDeclarations() throws Exception
    {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><ODM xmlns=\"" + ODM_NS
                + "\" FileOID=\"X\"><Study OID=\"S\"><MetaDataVersion OID=\"MDV.1\">"
                + "<d21:ValueListDef xmlns:d21=\"" + DEF_NS_21 + "\" OID=\"VL.A\"/>"
                + "</MetaDataVersion></Study></ODM>";
        Document deep = DefineDomIo
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        assertEquals(DEF_NS_21, DefineXmlPruner.detectDefineNamespace(deep),
                "a 2.1 declaration on a descendant (legal XML) still makes the document 2.1");

        Document v20 = DefineDomIo
                .parse(new ByteArrayInputStream(odm("").getBytes(StandardCharsets.UTF_8)));
        assertEquals(DEF_NS_20, DefineXmlPruner.detectDefineNamespace(v20));
    }
}
