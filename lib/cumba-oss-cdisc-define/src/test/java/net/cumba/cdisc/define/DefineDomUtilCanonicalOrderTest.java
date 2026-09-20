package net.cumba.cdisc.define;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Boundary tests for the placement and namespace helpers in {@link DefineDomUtil}.
 *
 * <p>
 * {@code insertInCanonicalOrder} decides where every synthesised element lands inside a
 * {@code MetaDataVersion}; a wrong position is a schema-invalid Define-XML that still parses. Two
 * of its comparisons are observable only exactly on their boundary — the rank-0 child
 * ({@code Description}) and the equal-rank child (a second {@code ItemDef}) — so both are pinned
 * here explicitly.
 * </p>
 */
class DefineDomUtilCanonicalOrderTest
{

    private static final String ODM_NS = DefineXmlConverter.ODM_NS_13;

    private static final String DEF_NS = DefineXmlConverter.DEF_NS_21;

    private static Document parse(String aXml) throws Exception
    {
        return DefineDomIo.parse(new ByteArrayInputStream(aXml.getBytes(StandardCharsets.UTF_8)));
    }


    /** A MetaDataVersion carrying the given (already serialised) children. */
    private static Element mdv(String aChildren) throws Exception
    {
        Document doc = parse("<ODM xmlns=\"" + ODM_NS + "\" xmlns:def=\"" + DEF_NS + "\">"
                + "<Study><MetaDataVersion>" + aChildren + "</MetaDataVersion></Study></ODM>");
        return DefineDomUtil.firstByLocalName(doc, "MetaDataVersion");
    }


    private static Element newOdmElement(Element aSibling, String aLocalName)
    {
        return aSibling.getOwnerDocument().createElementNS(ODM_NS, aLocalName);
    }


    /** Local names of the element children of {@code parent}, in document order. */
    private static List<String> childNames(Element aParent)
    {
        List<String> names = new ArrayList<>();
        NodeList kids = aParent.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++)
        {
            if (kids.item(i) instanceof Element el)
            {
                names.add(DefineDomUtil.localNameOf(el));
            }
        }
        return names;
    }


    private static List<String> childOids(Element aParent)
    {
        List<String> oids = new ArrayList<>();
        NodeList kids = aParent.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++)
        {
            if (kids.item(i) instanceof Element el)
            {
                oids.add(el.getAttribute("OID"));
            }
        }
        return oids;
    }


    /**
     * ⭐ Boundary at rank 0. {@code Description} is {@code MDV_ORDER.get(0)}, so {@code childRank}
     * is exactly 0: it must still be placed before a higher-ranked sibling rather than appended.
     * This is the only input that separates {@code childRank >= 0} from {@code childRank > 0}.
     */
    @Test
    void insertsRankZeroChildBeforeHigherRankedSibling() throws Exception
    {
        Element parent = mdv("<ItemGroupDef OID=\"IG.DM\"/>");
        DefineDomUtil.insertInCanonicalOrder(parent, newOdmElement(parent, "Description"),
                Step20To21.MDV_ORDER);

        assertEquals(List.of("Description", "ItemGroupDef"), childNames(parent));
    }


    /** A child whose local name is not in the order list has rank -1 and must be appended. */
    @Test
    void appendsChildWhoseNameIsNotInTheOrder() throws Exception
    {
        Element parent = mdv("<ItemGroupDef OID=\"IG.DM\"/>");
        DefineDomUtil.insertInCanonicalOrder(parent, newOdmElement(parent, "VendorExtension"),
                Step20To21.MDV_ORDER);

        assertEquals(List.of("ItemGroupDef", "VendorExtension"), childNames(parent));
    }


    /**
     * ⭐ Boundary at equal rank. A second {@code ItemDef} must go <em>after</em> the existing one:
     * {@code rank > childRank} is strict, and the mutant {@code >=} would reverse ItemDef sequence,
     * which is meaningful in Define-XML.
     */
    @Test
    void appendsAfterAnEquallyRankedSibling() throws Exception
    {
        Element parent = mdv("<ItemDef OID=\"IT.A\"/>");
        Element second = newOdmElement(parent, "ItemDef");
        second.setAttribute("OID", "IT.B");
        DefineDomUtil.insertInCanonicalOrder(parent, second, Step20To21.MDV_ORDER);

        assertEquals(List.of("ItemDef", "ItemDef"), childNames(parent));
        assertEquals(List.of("IT.A", "IT.B"), childOids(parent));
    }


    /** All existing children rank higher: the new child is inserted in front of all of them. */
    @Test
    void insertsBeforeAllHigherRankedChildren() throws Exception
    {
        Element parent = mdv("<CodeList OID=\"CL.1\"/>");
        DefineDomUtil.insertInCanonicalOrder(parent, newOdmElement(parent, "ItemDef"),
                Step20To21.MDV_ORDER);

        assertEquals(List.of("ItemDef", "CodeList"), childNames(parent));
    }


    /** All existing children rank lower: no anchor is found and the child is appended. */
    @Test
    void appendsAfterAllLowerRankedChildren() throws Exception
    {
        Element parent = mdv("<Description/>");
        DefineDomUtil.insertInCanonicalOrder(parent, newOdmElement(parent, "ItemDef"),
                Step20To21.MDV_ORDER);

        assertEquals(List.of("Description", "ItemDef"), childNames(parent));
    }


    /** An empty parent has no anchor at all: the child is appended and becomes the only one. */
    @Test
    void appendsIntoAnEmptyParent() throws Exception
    {
        Element parent = mdv("");
        DefineDomUtil.insertInCanonicalOrder(parent, newOdmElement(parent, "ItemGroupDef"),
                Step20To21.MDV_ORDER);

        assertEquals(List.of("ItemGroupDef"), childNames(parent));
    }


    /**
     * Whitespace text nodes and comments (which a parsed document always carries) must never be
     * taken for anchors — the anchor has to be the first higher-ranked <em>element</em>.
     */
    @Test
    void ignoresTextAndCommentNodesWhenLookingForAnAnchor() throws Exception
    {
        Element parent = mdv("\n  <!-- dataset code lists -->\n  <CodeList OID=\"CL.1\"/>\n");
        DefineDomUtil.insertInCanonicalOrder(parent, newOdmElement(parent, "ItemDef"),
                Step20To21.MDV_ORDER);

        assertEquals(List.of("ItemDef", "CodeList"), childNames(parent));
        assertEquals(Node.COMMENT_NODE, parent.getChildNodes().item(1).getNodeType(),
                "the comment must still precede the inserted element");
    }


    /**
     * {@code insertBeforeFirstChild}: zero matches appends, one or more inserts before the first.
     */
    @Test
    void insertBeforeFirstChildHandlesBothSidesOfTheEmptyBoundary() throws Exception
    {
        Element withRef = mdv("<ItemGroupDef/><ValueListRef/><ItemRef/>");
        DefineDomUtil.insertBeforeFirstChild(withRef, newOdmElement(withRef, "Origin"),
                "ValueListRef");
        assertEquals(List.of("ItemGroupDef", "Origin", "ValueListRef", "ItemRef"),
                childNames(withRef));

        Element withoutRef = mdv("<ItemGroupDef/><ItemRef/>");
        DefineDomUtil.insertBeforeFirstChild(withoutRef, newOdmElement(withoutRef, "Origin"),
                "ValueListRef");
        assertEquals(List.of("ItemGroupDef", "ItemRef", "Origin"), childNames(withoutRef));
    }


    /** {@code insertFirstChild} places the child ahead of every existing node. */
    @Test
    void insertFirstChildPlacesTheChildFirst() throws Exception
    {
        Element parent = mdv("<ItemRef/>");
        Element desc = newOdmElement(parent, "Description");
        DefineDomUtil.insertFirstChild(parent, desc);

        assertSame(desc, parent.getFirstChild());
        assertEquals(List.of("Description", "ItemRef"), childNames(parent));
    }


    /**
     * {@code localNameOf} falls back to stripping the prefix when the DOM is not namespace-aware
     * and {@code getLocalName()} therefore returns {@code null}. This fallback is live code —
     * {@code cumba-corej-define-conformance} consumes the helper — but nothing reached it before.
     */
    @Test
    void localNameOfStripsThePrefixOnANonNamespaceAwareDom() throws Exception
    {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        String xml = "<ItemDef xmlns:def=\"" + DEF_NS + "\" OID=\"IT.A\" def:DisplayFormat=\"$X.\">"
                + "<def:Origin/></ItemDef>";
        Document doc = dbf.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        Element root = doc.getDocumentElement();

        assertNull(root.getLocalName(), "the fixture must be a non-namespace-aware DOM");
        // colon >= 0: the prefix is stripped.
        assertEquals("Origin",
                DefineDomUtil.localNameOf(root.getElementsByTagName("def:Origin").item(0)));
        assertEquals("DisplayFormat",
                DefineDomUtil.localNameOf(root.getAttributeNode("def:DisplayFormat")));
        // colon < 0: the whole node name is the local name.
        assertEquals("ItemDef", DefineDomUtil.localNameOf(root));
        assertEquals("OID", DefineDomUtil.localNameOf(root.getAttributeNode("OID")));
    }


    /** A namespace-aware node reports its own local name and never reaches the fallback. */
    @Test
    void localNameOfUsesTheParsedLocalNameWhenAvailable() throws Exception
    {
        Element parent = mdv("<ItemDef/>");
        Element it = DefineDomUtil.childrenByLocalName(parent, "ItemDef").get(0);
        assertEquals("ItemDef", it.getLocalName());
        assertEquals("ItemDef", DefineDomUtil.localNameOf(it));
    }


    /** All three disjuncts of {@code hasNamespace}, plus the "nowhere at all" case. */
    @Test
    void hasNamespaceCoversBindingByDefPrefixByOtherPrefixAndByUsage() throws Exception
    {
        Document boundAsDef = parse("<ODM xmlns=\"" + ODM_NS + "\" xmlns:def=\"" + DEF_NS + "\"/>");
        assertTrue(DefineDomUtil.hasNamespace(boundAsDef, DEF_NS));

        Document boundAsD = parse("<ODM xmlns=\"" + ODM_NS + "\" xmlns:d=\"" + DEF_NS + "\"/>");
        assertNull(boundAsD.getDocumentElement().lookupNamespaceURI("def"));
        assertTrue(DefineDomUtil.hasNamespace(boundAsD, DEF_NS));

        Document usedDeeper = parse("<ODM xmlns=\"" + ODM_NS + "\"><Study>" + "<x:leaf xmlns:x=\""
                + DEF_NS + "\"/></Study></ODM>");
        assertNull(usedDeeper.getDocumentElement().lookupPrefix(DEF_NS));
        assertTrue(DefineDomUtil.hasNamespace(usedDeeper, DEF_NS));

        Document absent = parse("<ODM xmlns=\"" + ODM_NS + "\"><Study/></ODM>");
        assertFalse(DefineDomUtil.hasNamespace(absent, DEF_NS));
    }


    /**
     * {@code renameNamespace} must move elements, attributes <em>and</em> the root declaration
     * while preserving each node's prefix.
     */
    @Test
    void renameNamespaceMovesElementsAttributesAndTheRootDeclaration() throws Exception
    {
        String oldNs = DefineXmlConverter.DEF_NS_20;
        Document doc = parse("<ODM xmlns=\"" + ODM_NS + "\" xmlns:d=\"" + oldNs + "\">"
                + "<Study><MetaDataVersion d:DefineVersion=\"2.0.0\"><d:leaf ID=\"LF.1\"/>"
                + "</MetaDataVersion></Study></ODM>");

        DefineDomUtil.renameNamespace(doc, oldNs, DEF_NS);

        Element leaf = DefineDomUtil.firstByLocalName(doc, "leaf");
        assertEquals(DEF_NS, leaf.getNamespaceURI());
        assertEquals("d", leaf.getPrefix(), "the node's own prefix must be preserved");
        Element mdvEl = DefineDomUtil.firstByLocalName(doc, "MetaDataVersion");
        Attr version = mdvEl.getAttributeNodeNS(DEF_NS, "DefineVersion");
        assertEquals("2.0.0", version.getValue());
        assertEquals("d", version.getPrefix());
        assertEquals(DEF_NS, doc.getDocumentElement().getAttributeNS(DefineDomUtil.XMLNS_NS, "d"));
    }


    /** {@code defPrefix} returns the root's binding, else the {@code "def"} fallback. */
    @Test
    void defPrefixPrefersTheRootBindingOverTheFallback() throws Exception
    {
        assertEquals("d", DefineDomUtil.defPrefix(
                parse("<ODM xmlns=\"" + ODM_NS + "\" xmlns:d=\"" + DEF_NS + "\"/>"), DEF_NS));
        assertEquals("def",
                DefineDomUtil.defPrefix(parse("<ODM xmlns=\"" + ODM_NS + "\"/>"), DEF_NS));
    }


    /** {@code removeAttrByLocalName} matches a prefixed attribute and reports what it removed. */
    @Test
    void removeAttrByLocalNameReturnsTheValueAndRemovesTheAttribute() throws Exception
    {
        Element parent = mdv("<ItemGroupDef def:Class=\"EVENTS\" OID=\"IG.AE\"/>");
        Element ig = DefineDomUtil.childrenByLocalName(parent, "ItemGroupDef").get(0);

        assertEquals("EVENTS", DefineDomUtil.removeAttrByLocalName(ig, "Class"));
        assertNull(DefineDomUtil.attrIgnoreNs(ig, "Class"));
        assertEquals("IG.AE", ig.getAttribute("OID"), "unrelated attributes must be untouched");
        assertNull(DefineDomUtil.removeAttrByLocalName(ig, "Class"),
                "a second removal finds nothing");
    }


    /**
     * An {@code OID=""} or whitespace-only OID must not enter the set that feeds the OID minter.
     */
    @Test
    void collectExistingOidsIgnoresBlankOids() throws Exception
    {
        Document doc = parse("<ODM xmlns=\"" + ODM_NS + "\"><Study OID=\"ST.1\">"
                + "<MetaDataVersion OID=\"\"><ItemGroupDef OID=\"  \"/>"
                + "<ItemDef OID=\"IT.A\"/></MetaDataVersion></Study></ODM>");

        Set<String> oids = DefineDomUtil.collectExistingOids(doc);
        assertEquals(Set.of("ST.1", "IT.A"), oids);
    }

}
