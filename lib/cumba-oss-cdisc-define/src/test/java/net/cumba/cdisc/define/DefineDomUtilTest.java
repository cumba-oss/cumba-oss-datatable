package net.cumba.cdisc.define;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

class DefineDomUtilTest
{

    private static final String XML = "<ODM xmlns=\"http://www.cdisc.org/ns/odm/v1.3\""
            + " xmlns:def=\"http://www.cdisc.org/ns/def/v2.1\" FileOID=\"X\">"
            + "<Study OID=\"S\"><MetaDataVersion OID=\"M\" def:DefineVersion=\"2.1.0\">"
            + "<ItemGroupDef OID=\"IG.DM\"><ItemRef ItemOID=\"IT.A\"/><ItemRef ItemOID=\"IT.B\"/>"
            + "</ItemGroupDef><ItemDef OID=\"IT.A\"/><ItemDef OID=\"IT.B\"/>"
            + "</MetaDataVersion></Study></ODM>";

    private static Document parse() throws Exception
    {
        return DefineDomIo.parse(new ByteArrayInputStream(XML.getBytes(StandardCharsets.UTF_8)));
    }


    @Test
    void elementsAndFirstByLocalName() throws Exception
    {
        Document doc = parse();
        assertEquals(2, DefineDomUtil.elementsByLocalName(doc, "ItemDef").size());
        assertEquals(3, DefineDomUtil.elementsByLocalName(doc, "ItemRef").size() + 1);
        Element mdv = DefineDomUtil.firstByLocalName(doc, "MetaDataVersion");
        assertEquals("M", DefineDomUtil.attrIgnoreNs(mdv, "OID"));
        assertNull(DefineDomUtil.firstByLocalName(doc, "DoesNotExist"));
    }


    @Test
    void attrIgnoreNsMatchesPrefixedAttribute() throws Exception
    {
        Document doc = parse();
        Element mdv = DefineDomUtil.firstByLocalName(doc, "MetaDataVersion");
        assertEquals("2.1.0", DefineDomUtil.attrIgnoreNs(mdv, "DefineVersion"));
        assertNull(DefineDomUtil.attrIgnoreNs(mdv, "Missing"));
    }


    @Test
    void childrenByLocalNameReturnsDirectChildrenOnly() throws Exception
    {
        Document doc = parse();
        Element ig = DefineDomUtil.firstByLocalName(doc, "ItemGroupDef");
        List<Element> refs = DefineDomUtil.childrenByLocalName(ig, "ItemRef");
        assertEquals(2, refs.size());
        assertTrue(DefineDomUtil.childrenByLocalName(ig, "ItemDef").isEmpty());
    }


    @Test
    void collectExistingOids() throws Exception
    {
        Document doc = parse();
        Set<String> oids = DefineDomUtil.collectExistingOids(doc);
        assertTrue(oids.containsAll(Set.of("S", "M", "IG.DM", "IT.A", "IT.B")));
    }


    @Test
    void hasNamespaceDetectsDefAndAbsence() throws Exception
    {
        Document doc = parse();
        assertTrue(DefineDomUtil.hasNamespace(doc, DefineXmlConverter.DEF_NS_21));
        assertTrue(!DefineDomUtil.hasNamespace(doc, DefineXmlConverter.DEF_NS_10));
    }

}
