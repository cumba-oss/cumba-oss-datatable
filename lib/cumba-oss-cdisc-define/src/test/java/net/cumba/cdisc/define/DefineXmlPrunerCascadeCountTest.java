package net.cumba.cdisc.define;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

/**
 * The pruner's <em>counters</em>, as opposed to its edits.
 *
 * <p>
 * Every orphan sweep is written twice over: once applying, once counting. The counting half
 * ({@link DefineXmlPruner#countOrphans()}) is what decides whether a cascade that stopped at the
 * pass cap reports its output as clean or as truncated, and the applying half reports how much it
 * removed through the {@code "Cascade pass N: removed M orphans"} log. Neither number is visible in
 * the pruned document, so a wrong count — or a detection sweep that quietly edits the document it
 * is only supposed to inspect — leaves no trace at all in the assertions that check which elements
 * survived. That is exactly the shape these tests exist to catch: the previous suite asserted the
 * outcome of a prune thoroughly and the arithmetic behind it not at all.
 * </p>
 *
 * <p>
 * The shared fixture carries <strong>exactly one</strong> orphan of each of the eight families, so
 * every per-family contribution to a total is 1 and a single wrong summand moves the total by a
 * detectable amount.
 * </p>
 */
class DefineXmlPrunerCascadeCountTest
{

    private static final String ODM_NS = "http://www.cdisc.org/ns/odm/v1.3";

    private static final String DEF_NS_20 = "http://www.cdisc.org/ns/def/v2.0";

    private static final String OTHER_NS = "http://example.org/vendor";

    /**
     * One orphan per family: ItemRef, ItemDef, CodeList, MethodDef, CommentDef, ValueListDef,
     * WhereClauseDef, leaf.
     */
    private static final String ONE_ORPHAN_PER_FAMILY = "<ItemGroupDef OID=\"IG.DM\" Name=\"DM\"><ItemRef ItemOID=\"IT.MISSING\"/></ItemGroupDef>"
            + "<ItemDef OID=\"IT.ORPHAN\" Name=\"X\" DataType=\"text\"/>"
            + "<CodeList OID=\"CL.ORPHAN\" Name=\"C\" DataType=\"text\"/>"
            + "<MethodDef OID=\"MT.ORPHAN\" Name=\"M\" Type=\"Computation\"/>"
            + "<def:CommentDef OID=\"COM.ORPHAN\"/>" + "<def:ValueListDef OID=\"VL.ORPHAN\"/>"
            + "<def:WhereClauseDef OID=\"WC.ORPHAN\"/>"
            + "<def:leaf ID=\"LF.ORPHAN\" xlink:href=\"orphan.pdf\"/>";

    private static String odm(String aBody)
    {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><ODM xmlns=\"" + ODM_NS
                + "\" xmlns:def=\"" + DEF_NS_20 + "\" xmlns:x=\"" + OTHER_NS
                + "\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" FileOID=\"X\">"
                + "<Study OID=\"S\"><MetaDataVersion OID=\"MDV.1\">" + aBody
                + "</MetaDataVersion></Study></ODM>";
    }


    private static DefineXmlPruner pruner(String aXml) throws Exception
    {
        return DefineXmlPruner
                .forInputStream(new ByteArrayInputStream(aXml.getBytes(StandardCharsets.UTF_8)));
    }


    private static List<String> cascadeLines(DefineXmlPruner aPruner)
    {
        return aPruner.getLog().stream().filter(l -> l.startsWith("Cascade pass ")).toList();
    }


    private static List<String> oidsOf(Document aDoc, String aLocalName)
    {
        return DefineDomUtil.elementsByLocalName(aDoc, aLocalName).stream()
                .map(el -> el.getAttribute("OID")).toList();
    }

    // ------------------------------------------------------------ the applying half


    /**
     * The per-pass total is the sum of eight per-family counters, and the document alone cannot
     * tell them apart: whichever counter is wrong, the same eight elements disappear. Pinning the
     * exact log line is the only way to say "and it counted them correctly".
     *
     * <p>
     * The pass <em>count</em> matters as much as the removal count. A sweep that reported its
     * candidates without removing them would keep the loop finding the same work every pass, all
     * the way to the cap, and the pruned document would still come out right — because the
     * detection sweep the cap then triggers would do the removing. Two passes, and no truncation
     * warning, is what says the cascade actually converged.
     * </p>
     */
    @Test
    void oneOrphanOfEachFamilyIsRemovedAndCountedInASinglePass() throws Exception
    {
        DefineXmlPruner p = pruner(odm(ONE_ORPHAN_PER_FAMILY)).cascadeOrphans();

        assertEquals(
                List.of("Cascade pass 1: removed 8 orphans", "Cascade pass 2: removed 0 orphans"),
                cascadeLines(p),
                "eight independent orphans go in pass 1; pass 2 confirms convergence");
        assertFalse(p.getLog().stream().anyMatch(l -> l.startsWith("WARNING:")),
                "a converged cascade records no truncation warning; log was: " + p.getLog());

        Document doc = p.getDocument();
        assertTrue(DefineDomUtil.elementsByLocalName(doc, "ItemRef").isEmpty());
        assertTrue(DefineDomUtil.elementsByLocalName(doc, "ItemDef").isEmpty());
        assertTrue(DefineDomUtil.elementsByLocalName(doc, "CodeList").isEmpty());
        assertTrue(DefineDomUtil.elementsByLocalName(doc, "MethodDef").isEmpty());
        assertTrue(DefineDomUtil.elementsByLocalName(doc, "CommentDef").isEmpty());
        assertTrue(DefineDomUtil.elementsByLocalName(doc, "ValueListDef").isEmpty());
        assertTrue(DefineDomUtil.elementsByLocalName(doc, "WhereClauseDef").isEmpty());
        assertTrue(DefineDomUtil.elementsByLocalName(doc, "leaf").isEmpty());
        assertEquals(List.of("IG.DM"), oidsOf(doc, "ItemGroupDef"),
                "the ItemGroupDef itself is never an orphan candidate");
    }

    // ------------------------------------------------------------ the counting half


    /**
     * The detection sweep must see all eight families. Counting is the whole basis of the
     * truncation warning: a family that reported zero would make a genuinely dirty output read as
     * converged — the silent half of F-cdisc-define-19, which fixed the opposite (a warning on
     * clean output) without ever asserting that the new counter counts.
     */
    @Test
    void countOrphansSeesEveryFamily() throws Exception
    {
        assertEquals(8, pruner(odm(ONE_ORPHAN_PER_FAMILY)).countOrphans(),
                "one orphan per family, eight families");
    }


    /**
     * Counting must not edit. The sweep shares its implementation with the removing pass and is
     * distinguished from it only by a boolean; if that boolean were read the wrong way round, the
     * "count" would prune the document — and every outcome assertion in the suite would still pass,
     * because the elements removed are the very ones that ought to go.
     */
    @Test
    void countOrphansLeavesTheDocumentUntouched() throws Exception
    {
        DefineXmlPruner p = pruner(odm(ONE_ORPHAN_PER_FAMILY));
        byte[] before = p.toByteArray();

        assertEquals(8, p.countOrphans());
        assertEquals(8, p.countOrphans(), "a pure count is idempotent");

        assertArrayEquals(before, p.toByteArray(),
                "countOrphans is a detection sweep, not a prune");
        assertTrue(p.getLog().isEmpty(),
                "a detection sweep logs no removals; log was: " + p.getLog());
    }


    /** A converged document has nothing left for the detector to find. */
    @Test
    void countOrphansIsZeroOnAConvergedDocument() throws Exception
    {
        DefineXmlPruner p = pruner(odm(ONE_ORPHAN_PER_FAMILY)).cascadeOrphans();

        assertEquals(0, p.countOrphans());
    }

    // ------------------------------------------------------------ namespace fallbacks


    /**
     * The leaf sweep looks in the def namespace first and falls back to a local-name scan. With the
     * fallback disarmed a leaf carrying a vendor prefix becomes invisible to the sweep and survives
     * a prune it should not survive — and the document stays well-formed, so nothing else notices.
     * The def-namespaced sibling above pins the primary path.
     */
    @Test
    void anOrphanedLeafOutsideTheDefNamespaceIsStillRemoved() throws Exception
    {
        DefineXmlPruner p = pruner(odm("<x:leaf ID=\"LF.GONE\" xlink:href=\"orphan.pdf\"/>"))
                .cascadeOrphans();

        assertTrue(DefineDomUtil.elementsByLocalName(p.getDocument(), "leaf").isEmpty(),
                "a leaf is a leaf whatever prefix it carries");
        assertEquals(
                List.of("Cascade pass 1: removed 1 orphans", "Cascade pass 2: removed 0 orphans"),
                cascadeLines(p));
    }


    /**
     * Explicit removal by OID has the same two-tier lookup, and the same silent failure mode: the
     * fallback returning nothing is logged as "not found" and the caller is told nothing at all.
     */
    @Test
    void aWhereClauseDefOutsideTheDefNamespaceIsStillRemovableByOid() throws Exception
    {
        DefineXmlPruner p = pruner(odm("<x:WhereClauseDef OID=\"WC.1\"/>"))
                .removeWhereClauseDefs("WC.1");

        assertTrue(DefineDomUtil.elementsByLocalName(p.getDocument(), "WhereClauseDef").isEmpty());
        assertTrue(p.getLog().contains("Removed WhereClauseDef: WC.1"), p.getLog().toString());
    }


    /**
     * A WhereClauseDef reached by a reference in a vendor namespace is alive. The reference sweep
     * is namespace-agnostic on purpose (F-10/F-20): over-retaining a definition is harmless, while
     * deleting a referenced one leaves a dangling condition in the output.
     */
    @Test
    void aWhereClauseRefKeepsItsWhereClauseDefAliveWhateverItsPrefix() throws Exception
    {
        DefineXmlPruner p = pruner(
                odm("<ItemGroupDef OID=\"IG.VS\" Name=\"VS\">" + "<ItemRef ItemOID=\"IT.A\">"
                        + "<x:WhereClauseRef WhereClauseOID=\"WC.LIVE\"/></ItemRef></ItemGroupDef>"
                        + "<ItemDef OID=\"IT.A\" Name=\"A\" DataType=\"text\"/>"
                        + "<def:WhereClauseDef OID=\"WC.LIVE\"/>"
                        + "<def:WhereClauseDef OID=\"WC.GONE\"/>")).cascadeOrphans();

        assertEquals(List.of("WC.LIVE"), oidsOf(p.getDocument(), "WhereClauseDef"));
    }

    // ------------------------------------------------------------ the fluent contract


    /**
     * Every builder method returns {@code this} so removals chain, which is the usage the class
     * javadoc documents. A method returning null breaks the chain with a NullPointerException that
     * names nothing useful, and the three that were never chained in a test were exactly the three
     * nothing would have caught.
     */
    @Test
    void everyExplicitRemovalIsChainable() throws Exception
    {
        DefineXmlPruner p = pruner(odm("<ItemGroupDef OID=\"IG.AE\" Name=\"AE\"/>"
                + "<ItemDef OID=\"IT.A\" Name=\"A\" DataType=\"text\"/>"
                + "<MethodDef OID=\"MT.1\" Name=\"M\" Type=\"Computation\"/>"
                + "<def:CommentDef OID=\"COM.1\"/>" + "<CodeList OID=\"CL.1\" Name=\"C\"/>"
                + "<def:ValueListDef OID=\"VL.1\"/>" + "<def:WhereClauseDef OID=\"WC.1\"/>"
                + "<def:leaf ID=\"LF.1\" xlink:href=\"a.pdf\"/>"));

        DefineXmlPruner chained = p.removeItemGroups("IG.AE").removeItemDefs("IT.A")
                .removeMethodDefs("MT.1").removeCommentDefs("COM.1").removeCodeLists("CL.1")
                .removeValueListDefs("VL.1").removeWhereClauseDefs("WC.1").removeLeafs("LF.1");

        assertSame(p, chained, "the fluent API returns the same pruner throughout");
        Document doc = chained.getDocument();
        assertTrue(DefineDomUtil.elementsByLocalName(doc, "ItemGroupDef").isEmpty());
        assertTrue(DefineDomUtil.elementsByLocalName(doc, "ItemDef").isEmpty());
        assertTrue(DefineDomUtil.elementsByLocalName(doc, "MethodDef").isEmpty());
        assertTrue(DefineDomUtil.elementsByLocalName(doc, "CommentDef").isEmpty());
        assertTrue(DefineDomUtil.elementsByLocalName(doc, "CodeList").isEmpty());
        assertTrue(DefineDomUtil.elementsByLocalName(doc, "ValueListDef").isEmpty());
        assertTrue(DefineDomUtil.elementsByLocalName(doc, "WhereClauseDef").isEmpty());
        assertTrue(DefineDomUtil.elementsByLocalName(doc, "leaf").isEmpty());
    }


    /** The write side of the fluent chain, which is what a caller actually ends a prune with. */
    @Test
    void writeToReturnsThePrunerSoAPruneCanEndInAChain() throws Exception
    {
        DefineXmlPruner p = pruner(odm(ONE_ORPHAN_PER_FAMILY));
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();

        assertSame(p, p.cascadeOrphans().writeTo(out));
        assertNotNull(p.toByteArray());
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("IG.DM"));
    }
}
