package net.cumba.cdisc.define;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Shared, parser-free DOM read/write helpers for Define-XML manipulation. Both
 * {@link DefineXmlPruner} and {@link DefineXmlConverter} operate on the raw DOM (rather than the
 * Jackson bean model) so that comments, element ordering, and unknown content are preserved on
 * write. This class centralises the secure {@link DocumentBuilderFactory} configuration and the
 * indenting serialisation so the two callers cannot drift apart.
 */
public final class DefineDomIo
{

    private DefineDomIo()
    {
    }


    /**
     * Parse an XML stream into a namespace-aware DOM with secure processing enabled (no DOCTYPE, no
     * external entities).
     */
    public static Document parse(InputStream is)
        throws IOException, ParserConfigurationException, SAXException
    {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(is);
        doc.getDocumentElement().normalize();
        return doc;
    }


    /**
     * Serialise a DOM to a stream with 2-space indentation and the XML declaration retained, after
     * stripping whitespace-only text nodes left behind by element edits.
     */
    public static void write(Document doc, OutputStream os) throws TransformerException
    {
        stripEmptyTextNodes(doc.getDocumentElement());

        TransformerFactory tf = TransformerFactory.newInstance();
        tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");

        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(os);
        transformer.transform(source, result);
    }


    /**
     * Recursively remove whitespace-only text nodes so re-serialisation does not accumulate blank
     * lines after elements have been inserted or removed.
     */
    static void stripEmptyTextNodes(Node node)
    {
        NodeList children = node.getChildNodes();
        List<Node> toRemove = new ArrayList<>();
        for (int i = 0; i < children.getLength(); i++)
        {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE && child.getTextContent().isBlank())
            {
                toRemove.add(child);
            }
            else if (child.getNodeType() == Node.ELEMENT_NODE)
            {
                stripEmptyTextNodes(child);
            }
        }
        for (Node n : toRemove)
        {
            node.removeChild(n);
        }
    }

}
