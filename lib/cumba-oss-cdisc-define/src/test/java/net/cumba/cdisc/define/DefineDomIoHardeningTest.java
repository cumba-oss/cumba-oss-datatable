package net.cumba.cdisc.define;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * {@link DefineDomIo#parse} refuses a document type declaration.
 *
 * <p>
 * A Define-XML file arrives from outside — a sponsor, a CRO, a vendor tool — and is parsed here
 * before anything has validated it. The DOCTYPE ban is what stops an external entity in such a file
 * reading the host's filesystem through the parser (XXE). It is a single call whose removal is
 * silent, and the other hardening on the same factory hides its absence: with the ban gone a
 * <em>malicious</em> DOCTYPE still fails, just for a different reason, so only an <em>innocent</em>
 * DOCTYPE and the wording of the refusal can tell the two configurations apart. Both are asserted
 * below, which is why this reads as a fussier test than it looks.
 * </p>
 */
class DefineDomIoHardeningTest
{

    private static final String ODM_NS = "http://www.cdisc.org/ns/odm/v1.3";

    private static Document parse(String aXml) throws Exception
    {
        return DefineDomIo.parse(new ByteArrayInputStream(aXml.getBytes(StandardCharsets.UTF_8)));
    }


    /**
     * The classic XXE payload: a DOCTYPE declaring an external entity that resolves a local file.
     * The document must be refused for <em>being</em> a DOCTYPE — refusing it later, when the
     * entity is dereferenced, means the ban is off and only the secure-processing backstop is
     * holding.
     */
    @Test
    void anExternalEntityPayloadIsRefusedAtTheDoctype(@TempDir Path aTempDir) throws Exception
    {
        File secret = aTempDir.resolve("secret.txt").toFile();
        Files.writeString(secret.toPath(), "TOP-SECRET-SUBJECT-DATA");

        String xxe = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<!DOCTYPE ODM [<!ENTITY xxe SYSTEM \"" + secret.toURI() + "\">]>"
                + "<ODM xmlns=\"" + ODM_NS + "\" FileOID=\"&xxe;\"/>";

        SAXException ex = assertThrows(SAXException.class, () -> parse(xxe),
                "a Define-XML file is untrusted input; a DOCTYPE must be refused, not resolved");
        assertTrue(ex.getMessage().contains("DOCTYPE is disallowed"),
                "the refusal must come from the DOCTYPE ban, not from a later backstop: "
                        + ex.getMessage());
    }


    /**
     * The same ban applies to a DOCTYPE carrying no entity at all — the declaration itself is what
     * is refused. This is the case that no other setting on the factory covers: with the ban
     * removed, this document parses cleanly.
     */
    @Test
    void anInnocentDocumentTypeDeclarationIsRefusedToo()
    {
        String doctype = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><!DOCTYPE ODM><ODM xmlns=\""
                + ODM_NS + "\" FileOID=\"X\"/>";

        SAXException ex = assertThrows(SAXException.class, () -> parse(doctype));
        assertTrue(ex.getMessage().contains("DOCTYPE is disallowed"), ex.getMessage());
    }


    /** A document without a DOCTYPE is the ordinary case and must parse unharmed. */
    @Test
    void anOrdinaryDocumentStillParses() throws Exception
    {
        Document doc = parse("<?xml version=\"1.0\" encoding=\"UTF-8\"?><ODM xmlns=\"" + ODM_NS
                + "\" FileOID=\"define-001\"/>");

        assertEquals("define-001", doc.getDocumentElement().getAttribute("FileOID"));
        assertEquals("ODM", doc.getDocumentElement().getLocalName());
        assertEquals(ODM_NS, doc.getDocumentElement().getNamespaceURI(),
                "the parse must be namespace-aware; every lookup downstream is by namespace");
    }
}
