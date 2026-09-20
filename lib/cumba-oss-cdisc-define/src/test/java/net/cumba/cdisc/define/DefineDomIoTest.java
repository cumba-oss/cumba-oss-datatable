package net.cumba.cdisc.define;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * Tests for the shared parse / serialise helpers in {@link DefineDomIo}.
 *
 * <p>
 * The security hardening on the parser is a single load-bearing setting: {@code
 * disallow-doctype-decl} rejects the DOCTYPE outright, which is what makes the remaining
 * entity/external-access settings defence-in-depth rather than the guard itself. That one is
 * therefore asserted directly.
 * </p>
 */
class DefineDomIoTest
{

    private static final String ODM_NS = DefineXmlConverter.ODM_NS_13;

    private static Document parse(String aXml) throws Exception
    {
        return DefineDomIo.parse(new ByteArrayInputStream(aXml.getBytes(StandardCharsets.UTF_8)));
    }


    private static String write(Document aDoc) throws Exception
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DefineDomIo.write(aDoc, bos);
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }


    /** A DOCTYPE declaration must be refused, not parsed. */
    @Test
    void parseRejectsADoctypeDeclaration()
    {
        String withDoctype = "<?xml version=\"1.0\"?><!DOCTYPE ODM><ODM xmlns=\"" + ODM_NS + "\"/>";
        assertThrows(SAXException.class, () -> parse(withDoctype));
    }


    @Test
    void parseAcceptsAWellFormedDocumentWithoutADoctype() throws Exception
    {
        Document doc = parse("<ODM xmlns=\"" + ODM_NS + "\"><Study OID=\"ST.1\"/></ODM>");
        assertEquals("ODM", doc.getDocumentElement().getLocalName());
        assertEquals("ST.1", DefineDomUtil.firstByLocalName(doc, "Study").getAttribute("OID"));
    }


    /** The XML declaration is retained and names UTF-8; non-ASCII content round-trips. */
    @Test
    void writeKeepsTheXmlDeclarationAndUtf8Encoding() throws Exception
    {
        Document doc = parse("<ODM xmlns=\"" + ODM_NS + "\"><Study>µg/L</Study></ODM>");
        String out = write(doc);

        assertTrue(out.startsWith("<?xml"), out);
        assertTrue(out.contains("encoding=\"UTF-8\""), out);
        Document reparsed = parse(out);
        assertEquals("µg/L", DefineDomUtil.firstByLocalName(reparsed, "Study").getTextContent());
    }


    /** Indentation is on, and exactly two spaces per nesting level. */
    @Test
    void writeIndentsByTwoSpacesPerLevel() throws Exception
    {
        Document doc = parse("<ODM xmlns=\"" + ODM_NS + "\"><Study><MetaDataVersion OID=\"M\"/>"
                + "</Study></ODM>");
        String out = write(doc);

        assertTrue(Pattern.compile("^ {2}<Study", Pattern.MULTILINE).matcher(out).find(),
                "Study must be indented by 2 spaces: " + out);
        assertTrue(Pattern.compile("^ {4}<MetaDataVersion", Pattern.MULTILINE).matcher(out).find(),
                "MetaDataVersion must be indented by 4 spaces: " + out);
    }


    /**
     * Removing an element from a parsed, indented document leaves its surrounding whitespace text
     * nodes behind; {@code write} must strip them so no blank-line run survives re-serialisation.
     */
    @Test
    void writeLeavesNoBlankLineRunAfterAnElementIsRemoved() throws Exception
    {
        Document doc = parse("<ODM xmlns=\"" + ODM_NS + "\">\n  <Study OID=\"ST.1\">\n"
                + "    <MetaDataVersion OID=\"M\"/>\n  </Study>\n</ODM>");
        Element mdv = DefineDomUtil.firstByLocalName(doc, "MetaDataVersion");
        mdv.getParentNode().removeChild(mdv);

        String out = write(doc);
        assertFalse(Pattern.compile("\n[ \t]*\n").matcher(out).find(),
                "no blank-line run may survive: " + out);
    }


    /** Boundary: a whitespace-only text node goes, a text node with content stays. */
    @Test
    void stripEmptyTextNodesRemovesOnlyBlankText() throws Exception
    {
        Document doc = parse(
                "<ODM xmlns=\"" + ODM_NS + "\"><Study>  </Study>" + "<Study> x </Study></ODM>");
        Element root = doc.getDocumentElement();

        DefineDomIo.stripEmptyTextNodes(root);

        assertEquals(0,
                DefineDomUtil.elementsByLocalName(doc, "Study").get(0).getChildNodes().getLength(),
                "a blank text node must be removed");
        assertEquals(" x ", DefineDomUtil.elementsByLocalName(doc, "Study").get(1).getTextContent(),
                "a non-blank text node must survive verbatim");
    }


    /** The recursion matters: blank text two levels down must go too. */
    @Test
    void stripEmptyTextNodesRecursesIntoGrandchildren() throws Exception
    {
        Document doc = parse("<ODM xmlns=\"" + ODM_NS + "\"><Study><MetaDataVersion>   "
                + "</MetaDataVersion></Study></ODM>");

        DefineDomIo.stripEmptyTextNodes(doc.getDocumentElement());

        assertEquals(0,
                DefineDomUtil.firstByLocalName(doc, "MetaDataVersion").getChildNodes().getLength());
    }


    /** Boundary: a node with exactly one child is still visited by the scan loop. */
    @Test
    void stripEmptyTextNodesHandlesASingleChild() throws Exception
    {
        Document doc = parse("<ODM xmlns=\"" + ODM_NS + "\"><Study>   </Study></ODM>");
        Element study = DefineDomUtil.firstByLocalName(doc, "Study");
        assertEquals(1, study.getChildNodes().getLength());

        DefineDomIo.stripEmptyTextNodes(study);

        assertEquals(0, study.getChildNodes().getLength());
    }

}
