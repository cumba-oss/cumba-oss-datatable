package net.cumba.cdisc.define;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Namespace-agnostic DOM query helpers shared by {@link DefineXmlConverter} (version detection) and
 * the {@link ConversionStep} implementations. Define-XML mixes the ODM and {@code def:} namespaces
 * and, in v1.0, namespace-qualifies attributes; these helpers match purely on local name so callers
 * do not have to special-case the dialect.
 *
 * <p>
 * The read-only query surface ({@code elementsByLocalName}, {@code childrenByLocalName},
 * {@code attrIgnoreNs}, {@code hasNamespace}, {@code localNameOf}, {@code descendantElements}) is
 * public for external DOM-level consumers (a Define-XML conformance validator's ordering and
 * namespace checks); the mutating helpers stay package-private for the converter.
 * </p>
 */
public final class DefineDomUtil
{

    private DefineDomUtil()
    {
    }


    /** All elements in the document with the given local name, in document order. */
    public static List<Element> elementsByLocalName(Document doc, String localName)
    {
        return toElementList(doc.getElementsByTagNameNS("*", localName));
    }


    /** The first element in the document with the given local name, or {@code null}. */
    public static @Nullable Element firstByLocalName(Document doc, String localName)
    {
        NodeList list = doc.getElementsByTagNameNS("*", localName);
        return list.getLength() == 0 ? null : (Element) list.item(0);
    }


    /** Direct child elements of {@code parent} with the given local name. */
    public static List<Element> childrenByLocalName(Element parent, String localName)
    {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++)
        {
            Node child = children.item(i);
            if (child instanceof Element el && localName.equals(localNameOf(el)))
            {
                result.add(el);
            }
        }
        return result;
    }


    /**
     * Read an attribute by local name, ignoring any namespace prefix (so {@code def:DefineVersion}
     * and {@code DefineVersion} both match {@code "DefineVersion"}). Returns {@code null} when
     * absent.
     */
    public static @Nullable String attrIgnoreNs(Element el, String localName)
    {
        NamedNodeMap attrs = el.getAttributes();
        if (attrs == null)
        {
            return null;
        }
        for (int i = 0; i < attrs.getLength(); i++)
        {
            Attr attr = (Attr) attrs.item(i);
            if (localName.equals(localNameOf(attr)))
            {
                return attr.getValue();
            }
        }
        return null;
    }


    /** Whether the given namespace URI is declared on the root or used by any node. */
    public static boolean hasNamespace(Document doc, String namespaceUri)
    {
        Element root = doc.getDocumentElement();
        if (root != null)
        {
            if (namespaceUri.equals(root.lookupNamespaceURI("def")))
            {
                return true;
            }
            if (root.lookupPrefix(namespaceUri) != null)
            {
                return true;
            }
        }
        return doc.getElementsByTagNameNS(namespaceUri, "*").getLength() > 0;
    }


    /** Collect every {@code OID} attribute value in the document (namespace-agnostic). */
    static Set<String> collectExistingOids(Document doc)
    {
        Set<String> oids = new HashSet<>();
        collectOidsRecursive(doc.getDocumentElement(), oids);
        return oids;
    }

    static final String XMLNS_NS = "http://www.w3.org/2000/xmlns/";

    /**
     * Rebind every element and attribute currently in {@code oldUri} to {@code newUri}, preserving
     * each node's prefix, and update the matching {@code xmlns:*} declaration on the root. Used to
     * bump the {@code def:} namespace (e.g. v2.0 → v2.1) without disturbing the ODM namespace.
     */
    static void renameNamespace(Document doc, String oldUri, String newUri)
    {
        List<Node> toRename = new ArrayList<>();
        collectInNamespace(doc.getDocumentElement(), oldUri, toRename);
        for (Node n : toRename)
        {
            String prefix = n.getPrefix();
            String qname = prefix == null ? localNameOf(n) : prefix + ":" + localNameOf(n);
            doc.renameNode(n, newUri, qname);
        }
        Element root = doc.getDocumentElement();
        NamedNodeMap attrs = root.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++)
        {
            Attr a = (Attr) attrs.item(i);
            if (XMLNS_NS.equals(a.getNamespaceURI()) && oldUri.equals(a.getValue()))
            {
                a.setValue(newUri);
            }
        }
    }


    /** The prefix bound to {@code namespaceUri} on the root, or {@code def} as a fallback. */
    static String defPrefix(Document doc, String namespaceUri)
    {
        Element root = doc.getDocumentElement();
        String prefix = root == null ? null : root.lookupPrefix(namespaceUri);
        return prefix == null ? "def" : prefix;
    }


    /** Create an element in the {@code def:} namespace with the given prefix. */
    static Element createDefElement(Document doc, String defNs, String prefix, String localName)
    {
        return doc.createElementNS(defNs, prefix + ":" + localName);
    }


    /** Set a {@code def:}-namespaced attribute (prefixed) on an element. */
    static void setDefAttribute(Element el, String defNs, String prefix, String localName,
            String value)
    {
        el.setAttributeNS(defNs, prefix + ":" + localName, value);
    }


    /**
     * Remove the first attribute with the given local name (any namespace). Returns its value, or
     * {@code null} if not present.
     */
    static @Nullable String removeAttrByLocalName(Element el, String localName)
    {
        NamedNodeMap attrs = el.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++)
        {
            Attr attr = (Attr) attrs.item(i);
            if (localName.equals(localNameOf(attr)))
            {
                String value = attr.getValue();
                el.removeAttributeNode(attr);
                return value;
            }
        }
        return null;
    }


    /** Insert {@code child} as the first child element of {@code parent}. */
    static void insertFirstChild(Element parent, Element child)
    {
        parent.insertBefore(child, parent.getFirstChild());
    }


    /** All descendant elements of {@code ancestor} with the given local name, in document order. */
    public static List<Element> descendantElements(Element ancestor, String localName)
    {
        return toElementList(ancestor.getElementsByTagNameNS("*", localName));
    }


    /**
     * Insert {@code child} into {@code parent} at the position dictated by {@code order} (a list of
     * local names in their schema-mandated sequence): immediately before the first existing child
     * whose local name sorts after the child's, or appended if none does. Existing children whose
     * local name is not in {@code order} are ignored as anchors.
     */
    static void insertInCanonicalOrder(Element parent, Element child, List<String> order)
    {
        int childRank = order.indexOf(localNameOf(child));
        Element anchor = null;
        if (childRank >= 0)
        {
            NodeList kids = parent.getChildNodes();
            for (int i = 0; i < kids.getLength(); i++)
            {
                if (kids.item(i) instanceof Element el)
                {
                    int rank = order.indexOf(localNameOf(el));
                    if (rank > childRank)
                    {
                        anchor = el;
                        break;
                    }
                }
            }
        }
        if (anchor == null)
        {
            parent.appendChild(child);
        }
        else
        {
            parent.insertBefore(child, anchor);
        }
    }


    /**
     * Insert {@code child} immediately before the first descendant-or-child element with local name
     * {@code beforeLocalName}; if none exists, append it to {@code parent}.
     */
    static void insertBeforeFirstChild(Element parent, Element child, String beforeLocalName)
    {
        List<Element> matches = childrenByLocalName(parent, beforeLocalName);
        if (matches.isEmpty())
        {
            parent.appendChild(child);
        }
        else
        {
            parent.insertBefore(child, matches.get(0));
        }
    }


    private static void collectInNamespace(@Nullable Node node, String uri, List<Node> out)
    {
        if (!(node instanceof Element el))
        {
            return;
        }
        if (uri.equals(el.getNamespaceURI()))
        {
            out.add(el);
        }
        NamedNodeMap attrs = el.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++)
        {
            Attr attr = (Attr) attrs.item(i);
            if (uri.equals(attr.getNamespaceURI()))
            {
                out.add(attr);
            }
        }
        NodeList children = el.getChildNodes();
        for (int i = 0; i < children.getLength(); i++)
        {
            collectInNamespace(children.item(i), uri, out);
        }
    }


    /** Local name of a node, falling back to stripping any prefix from the qualified name. */
    public static String localNameOf(Node node)
    {
        String ln = node.getLocalName();
        if (ln != null)
        {
            return ln;
        }
        String name = node.getNodeName();
        int colon = name.indexOf(':');
        return colon < 0 ? name : name.substring(colon + 1);
    }


    private static void collectOidsRecursive(@Nullable Node node, Set<String> oids)
    {
        if (!(node instanceof Element el))
        {
            return;
        }
        String oid = attrIgnoreNs(el, "OID");
        if (oid != null && !oid.isBlank())
        {
            oids.add(oid);
        }
        NodeList children = el.getChildNodes();
        for (int i = 0; i < children.getLength(); i++)
        {
            collectOidsRecursive(children.item(i), oids);
        }
    }


    private static List<Element> toElementList(NodeList list)
    {
        List<Element> result = new ArrayList<>(list.getLength());
        for (int i = 0; i < list.getLength(); i++)
        {
            result.add((Element) list.item(i));
        }
        return result;
    }

}
