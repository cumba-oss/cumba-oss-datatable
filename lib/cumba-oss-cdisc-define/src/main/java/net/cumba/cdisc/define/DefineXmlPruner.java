package net.cumba.cdisc.define;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
 * A library for pruning CDISC Define-XML 2.0/2.1 documents. Removes specified elements and
 * cascading cleans all orphaned references.
 *
 * <p>
 * Usage example:
 *
 * <pre>{@code
 * DefineXmlPruner.forFile(inputPath).removeItemGroups("IG.AE", "IG.CM")
 *         .removeMethodDefs("MT.AE.AESER").removeItemDefs("IT.AE.AEBODSYS").cascadeOrphans()
 *         .writeTo(outputPath);
 * }</pre>
 */
public class DefineXmlPruner
{

    private static final String ODM_NS = "http://www.cdisc.org/ns/odm/v1.3";

    private static final String DEF_NS = "http://www.cdisc.org/ns/def/v2.0";

    private static final String DEF21_NS = "http://www.cdisc.org/ns/def/v2.1";

    private static final String ELEM_ITEM_DEF = "ItemDef";

    private static final String ELEM_VALUE_LIST_DEF = "ValueListDef";

    private static final String ELEM_WHERE_CLAUSE_DEF = "WhereClauseDef";

    private static final String ELEM_ITEM_REF = "ItemRef";

    private static final int MAX_CASCADE_PASSES = 20;

    private static final String ATTR_ITEM_OID = "ItemOID";

    private final Document doc;

    private final String defNs;

    private final Set<String> removedItemGroupOIDs = new LinkedHashSet<>();

    private final Set<String> removedItemDefOIDs = new LinkedHashSet<>();

    private final Set<String> removedMethodDefOIDs = new LinkedHashSet<>();

    private final Set<String> removedCommentDefOIDs = new LinkedHashSet<>();

    private final Set<String> removedCodeListOIDs = new LinkedHashSet<>();

    private final Set<String> removedValueListOIDs = new LinkedHashSet<>();

    private final Set<String> removedWhereClauseOIDs = new LinkedHashSet<>();

    private final Set<String> removedLeafIDs = new LinkedHashSet<>();

    private final List<String> log = new ArrayList<>();

    private DefineXmlPruner(Document doc)
    {
        this.doc = doc;
        this.defNs = detectDefineNamespace(doc);
    }

    // ========== Factory ==========


    /**
     * Create a pruner from a file path.
     */
    public static DefineXmlPruner forFile(Path path)
        throws IOException, org.xml.sax.SAXException, javax.xml.parsers.ParserConfigurationException
    {
        return forFile(path.toFile());
    }


    /**
     * Create a pruner from a File.
     */
    public static DefineXmlPruner forFile(File file)
        throws IOException, org.xml.sax.SAXException, javax.xml.parsers.ParserConfigurationException
    {
        try (FileInputStream fin = new FileInputStream(file))
        {
            return forInputStream(fin);
        }
    }


    /**
     * Create a pruner from an InputStream.
     */
    public static DefineXmlPruner forInputStream(InputStream is)
        throws IOException, javax.xml.parsers.ParserConfigurationException, org.xml.sax.SAXException
    {
        return new DefineXmlPruner(DefineDomIo.parse(is));
    }

    // ========== Builder methods: explicit removal ==========


    /**
     * Remove one or more ItemGroupDef elements by OID. This also removes all ItemRef children and
     * marks referenced ItemDefs for orphan check.
     */
    public DefineXmlPruner removeItemGroups(String... oids)
    {
        for (String oid : oids)
        {
            Element el = findElementByOID("ItemGroupDef", oid);
            if (el != null)
            {
                el.getParentNode().removeChild(el);
                removedItemGroupOIDs.add(oid);
                log("Removed ItemGroupDef: " + oid);
            }
            else
            {
                log("ItemGroupDef not found: " + oid);
            }
        }
        return this;
    }


    /**
     * Remove one or more ItemDef elements by OID.
     */
    public DefineXmlPruner removeItemDefs(String... oids)
    {
        for (String oid : oids)
        {
            Element el = findElementByOID(ELEM_ITEM_DEF, oid);
            if (el != null)
            {
                el.getParentNode().removeChild(el);
                removedItemDefOIDs.add(oid);
                log("Removed ItemDef: " + oid);
            }
            else
            {
                log("ItemDef not found: " + oid);
            }
        }
        return this;
    }


    /**
     * Remove one or more MethodDef elements by OID.
     */
    public DefineXmlPruner removeMethodDefs(String... oids)
    {
        for (String oid : oids)
        {
            Element el = findElementByOID("MethodDef", oid);
            if (el != null)
            {
                el.getParentNode().removeChild(el);
                removedMethodDefOIDs.add(oid);
                log("Removed MethodDef: " + oid);
            }
            else
            {
                log("MethodDef not found: " + oid);
            }
        }
        return this;
    }


    /**
     * Remove one or more CommentDef elements by OID.
     */
    public DefineXmlPruner removeCommentDefs(String... oids)
    {
        for (String oid : oids)
        {
            Element el = findElementByOID("CommentDef", oid);
            if (el != null)
            {
                el.getParentNode().removeChild(el);
                removedCommentDefOIDs.add(oid);
                log("Removed CommentDef: " + oid);
            }
            else
            {
                log("CommentDef not found: " + oid);
            }
        }
        return this;
    }


    /**
     * Remove one or more CodeList elements by OID.
     */
    public DefineXmlPruner removeCodeLists(String... oids)
    {
        for (String oid : oids)
        {
            Element el = findElementByOID("CodeList", oid);
            if (el != null)
            {
                el.getParentNode().removeChild(el);
                removedCodeListOIDs.add(oid);
                log("Removed CodeList: " + oid);
            }
            else
            {
                log("CodeList not found: " + oid);
            }
        }
        return this;
    }


    /**
     * Remove one or more ValueListDef elements by OID.
     */
    public DefineXmlPruner removeValueListDefs(String... oids)
    {
        for (String oid : oids)
        {
            Element el = findElementByOID(ELEM_VALUE_LIST_DEF, oid);
            if (el == null)
            {
                // ValueListDef is in the def: namespace
                el = findDefElementByOID(ELEM_VALUE_LIST_DEF, oid);
            }
            if (el != null)
            {
                el.getParentNode().removeChild(el);
                removedValueListOIDs.add(oid);
                log("Removed ValueListDef: " + oid);
            }
            else
            {
                log("ValueListDef not found: " + oid);
            }
        }
        return this;
    }


    /**
     * Remove one or more WhereClauseDef elements by OID.
     */
    public DefineXmlPruner removeWhereClauseDefs(String... oids)
    {
        for (String oid : oids)
        {
            Element el = findDefElementByOID(ELEM_WHERE_CLAUSE_DEF, oid);
            if (el != null)
            {
                el.getParentNode().removeChild(el);
                removedWhereClauseOIDs.add(oid);
                log("Removed WhereClauseDef: " + oid);
            }
            else
            {
                log("WhereClauseDef not found: " + oid);
            }
        }
        return this;
    }


    /**
     * Remove one or more leaf elements by ID.
     */
    public DefineXmlPruner removeLeafs(String... ids)
    {
        for (String id : ids)
        {
            Element el = findDefElementByID("leaf", id);
            if (el != null)
            {
                el.getParentNode().removeChild(el);
                removedLeafIDs.add(id);
                log("Removed leaf: " + id);
            }
            else
            {
                log("leaf not found: " + id);
            }
        }
        return this;
    }

    // ========== Cascading orphan removal ==========


    /**
     * Perform cascading removal of all orphaned elements. This iterates until no more orphans are
     * found, up to a safety cap of 20 passes; a run truncated by the cap records a WARNING line in
     * the log.
     */
    public DefineXmlPruner cascadeOrphans()
    {
        return cascadeOrphans(MAX_CASCADE_PASSES);
    }


    /**
     * Cascading removal with an explicit pass cap; the public overload uses the 20-pass default.
     * Package-visible so the truncation warning is testable.
     */
    DefineXmlPruner cascadeOrphans(int maxPasses)
    {
        int pass = 0;
        int totalRemoved;
        do
        {
            pass++;
            totalRemoved = 0;
            totalRemoved += removeOrphanedItemRefs(true);
            totalRemoved += removeOrphanedItemDefs(true);
            totalRemoved += removeOrphanedCodeLists(true);
            totalRemoved += removeOrphanedMethodDefs(true);
            totalRemoved += removeOrphanedCommentDefs(true);
            totalRemoved += removeOrphanedValueListDefs(true);
            totalRemoved += removeOrphanedWhereClauseDefs(true);
            totalRemoved += removeOrphanedLeafs(true);
            log("Cascade pass " + pass + ": removed " + totalRemoved + " orphans");
        }
        while (totalRemoved > 0 && pass < maxPasses);
        // The loop exiting at the cap while the last pass still removed something does NOT
        // mean the output is dirty -- a document can converge exactly at the cap. Warn only
        // when orphans actually remain, established by a non-mutating detection sweep
        // (F-cdisc-define-19: the previous condition, totalRemoved > 0, was a false positive
        // on clean output).
        if (totalRemoved > 0 && countOrphans() > 0)
        {
            log("WARNING: cascade stopped at the " + maxPasses + "-pass cap before converging;"
                    + " the output retains orphaned elements");
        }
        return this;
    }


    /**
     * Count, without removing, the elements the next cascade pass would remove. Zero means the
     * document has converged: each family's detector is run against the unmodified document, so if
     * all report zero, a real pass would remove nothing either.
     */
    private int countOrphans()
    {
        int count = 0;
        count += removeOrphanedItemRefs(false);
        count += removeOrphanedItemDefs(false);
        count += removeOrphanedCodeLists(false);
        count += removeOrphanedMethodDefs(false);
        count += removeOrphanedCommentDefs(false);
        count += removeOrphanedValueListDefs(false);
        count += removeOrphanedWhereClauseDefs(false);
        count += removeOrphanedLeafs(false);
        return count;
    }

    // ========== Output ==========


    /**
     * Write the pruned document to a file.
     */
    public DefineXmlPruner writeTo(Path path)
        throws IOException, javax.xml.transform.TransformerException
    {
        return writeTo(path.toFile());
    }


    /**
     * Write the pruned document to a File.
     */
    public DefineXmlPruner writeTo(File file)
        throws IOException, javax.xml.transform.TransformerException
    {
        try (FileOutputStream fos = new FileOutputStream(file))
        {
            return writeTo(fos);
        }
    }


    /**
     * Write the pruned document to an OutputStream.
     */
    public DefineXmlPruner writeTo(OutputStream os) throws javax.xml.transform.TransformerException
    {
        DefineDomIo.write(doc, os);
        return this;
    }


    /**
     * Return the pruned document as a byte array.
     */
    public byte[] toByteArray() throws javax.xml.transform.TransformerException
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeTo(baos);
        return baos.toByteArray();
    }


    /**
     * Get the underlying DOM Document for further manipulation.
     */
    public Document getDocument()
    {
        return doc;
    }


    /**
     * Get the pruning log.
     */
    public List<String> getLog()
    {
        return Collections.unmodifiableList(log);
    }

    // ========== Orphan removal internals ==========


    /**
     * Remove ItemRef elements that reference non-existent ItemDefs.
     */
    private int removeOrphanedItemRefs(boolean aApply)
    {
        int count = 0;
        Set<String> existingItemOIDs = collectOIDs(ELEM_ITEM_DEF);

        // ItemRefs in ItemGroupDefs
        NodeList itemRefs = getElementsByLocalName(ELEM_ITEM_REF);
        List<Element> toRemove = new ArrayList<>();
        for (int i = 0; i < itemRefs.getLength(); i++)
        {
            Element ref = (Element) itemRefs.item(i);
            String itemOID = ref.getAttribute(ATTR_ITEM_OID);
            if (!itemOID.isEmpty() && !existingItemOIDs.contains(itemOID))
            {
                toRemove.add(ref);
            }
        }
        if (!aApply)
        {
            return toRemove.size();
        }
        for (Element el : toRemove)
        {
            log("Removed orphaned ItemRef -> " + el.getAttribute(ATTR_ITEM_OID));
            el.getParentNode().removeChild(el);
            count++;
        }
        return count;
    }


    /**
     * Remove ItemDefs that are not referenced by any ItemRef (in ItemGroupDefs or ValueListDefs).
     */
    private int removeOrphanedItemDefs(boolean aApply)
    {
        int count = 0;
        // Collect every ItemOID reference wherever it appears: ItemRef/@ItemOID and the
        // RangeCheck/@def:ItemOID of a value-level WhereClause condition both keep an ItemDef
        // alive. The collector matches by local name, so both spellings are found.
        Set<String> referencedOIDs = collectAllAttributeValues(ATTR_ITEM_OID);

        NodeList itemDefs = getElementsByLocalName(ELEM_ITEM_DEF);
        List<Element> toRemove = new ArrayList<>();
        for (int i = 0; i < itemDefs.getLength(); i++)
        {
            Element def = (Element) itemDefs.item(i);
            String oid = def.getAttribute("OID");
            if (!oid.isEmpty() && !referencedOIDs.contains(oid))
            {
                toRemove.add(def);
            }
        }
        if (!aApply)
        {
            return toRemove.size();
        }
        for (Element el : toRemove)
        {
            String oid = el.getAttribute("OID");
            log("Removed orphaned ItemDef: " + oid);
            el.getParentNode().removeChild(el);
            removedItemDefOIDs.add(oid);
            count++;
        }
        return count;
    }


    /**
     * Remove CodeLists not referenced by any existing ItemDef's CodeListRef.
     */
    private int removeOrphanedCodeLists(boolean aApply)
    {
        int count = 0;
        Set<String> referencedOIDs = new HashSet<>();

        NodeList codeListRefs = getElementsByLocalName("CodeListRef");
        for (int i = 0; i < codeListRefs.getLength(); i++)
        {
            Element ref = (Element) codeListRefs.item(i);
            String oid = ref.getAttribute("CodeListOID");
            if (!oid.isEmpty())
            {
                referencedOIDs.add(oid);
            }
        }

        NodeList codeLists = getElementsByLocalName("CodeList");
        List<Element> toRemove = new ArrayList<>();
        for (int i = 0; i < codeLists.getLength(); i++)
        {
            Element cl = (Element) codeLists.item(i);
            String oid = cl.getAttribute("OID");
            if (!oid.isEmpty() && !referencedOIDs.contains(oid))
            {
                toRemove.add(cl);
            }
        }
        if (!aApply)
        {
            return toRemove.size();
        }
        for (Element el : toRemove)
        {
            String oid = el.getAttribute("OID");
            log("Removed orphaned CodeList: " + oid);
            el.getParentNode().removeChild(el);
            removedCodeListOIDs.add(oid);
            count++;
        }
        return count;
    }


    /**
     * Remove MethodDefs not referenced by any existing ItemRef or ItemDef.
     */
    private int removeOrphanedMethodDefs(boolean aApply)
    {
        int count = 0;
        // Namespace-agnostic, like the ItemOID collection in removeOrphanedItemDefs (F-10):
        // MethodOID has no def: spelling today, but the asymmetric qualified-name lookup read
        // as an oversight (F-cdisc-define-20).
        Set<String> referencedOIDs = collectAllAttributeValues("MethodOID");

        NodeList methodDefs = getElementsByLocalName("MethodDef");
        List<Element> toRemove = new ArrayList<>();
        for (int i = 0; i < methodDefs.getLength(); i++)
        {
            Element md = (Element) methodDefs.item(i);
            String oid = md.getAttribute("OID");
            if (!oid.isEmpty() && !referencedOIDs.contains(oid))
            {
                toRemove.add(md);
            }
        }
        if (!aApply)
        {
            return toRemove.size();
        }
        for (Element el : toRemove)
        {
            String oid = el.getAttribute("OID");
            log("Removed orphaned MethodDef: " + oid);
            el.getParentNode().removeChild(el);
            removedMethodDefOIDs.add(oid);
            count++;
        }
        return count;
    }


    /**
     * Remove CommentDefs not referenced by any existing element's def:CommentOID attribute.
     */
    private int removeOrphanedCommentDefs(boolean aApply)
    {
        int count = 0;
        Set<String> referencedOIDs = collectAllAttributeValues("CommentOID");

        NodeList commentDefs = getElementsByLocalName("CommentDef");
        List<Element> toRemove = new ArrayList<>();
        for (int i = 0; i < commentDefs.getLength(); i++)
        {
            Element cd = (Element) commentDefs.item(i);
            String oid = cd.getAttribute("OID");
            if (!oid.isEmpty() && !referencedOIDs.contains(oid))
            {
                toRemove.add(cd);
            }
        }
        if (!aApply)
        {
            return toRemove.size();
        }
        for (Element el : toRemove)
        {
            String oid = el.getAttribute("OID");
            log("Removed orphaned CommentDef: " + oid);
            el.getParentNode().removeChild(el);
            removedCommentDefOIDs.add(oid);
            count++;
        }
        return count;
    }


    /**
     * Remove ValueListDefs not referenced by any existing ItemDef's def:ValueListOID.
     */
    private int removeOrphanedValueListDefs(boolean aApply)
    {
        int count = 0;
        Set<String> referencedOIDs = collectAllAttributeValues("ValueListOID");

        // ValueListDef can be in def: namespace
        NodeList vlDefs = doc.getElementsByTagNameNS(defNs, ELEM_VALUE_LIST_DEF);
        if (vlDefs.getLength() == 0)
        {
            vlDefs = getElementsByLocalName(ELEM_VALUE_LIST_DEF);
        }

        List<Element> toRemove = new ArrayList<>();
        for (int i = 0; i < vlDefs.getLength(); i++)
        {
            Element vl = (Element) vlDefs.item(i);
            String oid = vl.getAttribute("OID");
            if (!oid.isEmpty() && !referencedOIDs.contains(oid))
            {
                toRemove.add(vl);
            }
        }
        if (!aApply)
        {
            return toRemove.size();
        }
        for (Element el : toRemove)
        {
            String oid = el.getAttribute("OID");
            log("Removed orphaned ValueListDef: " + oid);
            el.getParentNode().removeChild(el);
            removedValueListOIDs.add(oid);
            count++;
        }
        return count;
    }


    /**
     * Remove WhereClauseDefs not referenced by any existing ItemRef's def:WhereClauseRef.
     */
    private int removeOrphanedWhereClauseDefs(boolean aApply)
    {
        int count = 0;
        Set<String> referencedOIDs = new HashSet<>();

        // WhereClauseRef is in def: namespace
        NodeList wcRefs = doc.getElementsByTagNameNS(defNs, "WhereClauseRef");
        for (int i = 0; i < wcRefs.getLength(); i++)
        {
            Element ref = (Element) wcRefs.item(i);
            String oid = ref.getAttribute("WhereClauseOID");
            if (!oid.isEmpty())
            {
                referencedOIDs.add(oid);
            }
        }
        // Fallback: check local name
        NodeList wcRefsLocal = getElementsByLocalName("WhereClauseRef");
        for (int i = 0; i < wcRefsLocal.getLength(); i++)
        {
            Element ref = (Element) wcRefsLocal.item(i);
            String oid = ref.getAttribute("WhereClauseOID");
            if (!oid.isEmpty())
            {
                referencedOIDs.add(oid);
            }
        }

        NodeList wcDefs = doc.getElementsByTagNameNS(defNs, ELEM_WHERE_CLAUSE_DEF);
        if (wcDefs.getLength() == 0)
        {
            wcDefs = getElementsByLocalName(ELEM_WHERE_CLAUSE_DEF);
        }

        List<Element> toRemove = new ArrayList<>();
        for (int i = 0; i < wcDefs.getLength(); i++)
        {
            Element wc = (Element) wcDefs.item(i);
            String oid = wc.getAttribute("OID");
            if (!oid.isEmpty() && !referencedOIDs.contains(oid))
            {
                toRemove.add(wc);
            }
        }
        if (!aApply)
        {
            return toRemove.size();
        }
        for (Element el : toRemove)
        {
            String oid = el.getAttribute("OID");
            log("Removed orphaned WhereClauseDef: " + oid);
            el.getParentNode().removeChild(el);
            removedWhereClauseOIDs.add(oid);
            count++;
        }
        return count;
    }


    /**
     * Remove leaf elements not referenced by any DocumentRef.
     */
    private int removeOrphanedLeafs(boolean aApply)
    {
        int count = 0;
        Set<String> referencedIDs = new HashSet<>();

        NodeList docRefs = getElementsByLocalName("DocumentRef");
        for (int i = 0; i < docRefs.getLength(); i++)
        {
            Element ref = (Element) docRefs.item(i);
            String leafID = ref.getAttribute("leafID");
            if (!leafID.isEmpty())
            {
                referencedIDs.add(leafID);
            }
        }

        NodeList leafs = doc.getElementsByTagNameNS(defNs, "leaf");
        if (leafs.getLength() == 0)
        {
            leafs = getElementsByLocalName("leaf");
        }

        List<Element> toRemove = new ArrayList<>();
        for (int i = 0; i < leafs.getLength(); i++)
        {
            Element leaf = (Element) leafs.item(i);
            // Skip leaf elements inside ItemGroupDef (dataset file references)
            if (isChildOfItemGroupDef(leaf))
            {
                continue;
            }
            String id = leaf.getAttribute("ID");
            if (!id.isEmpty() && !referencedIDs.contains(id))
            {
                toRemove.add(leaf);
            }
        }
        if (!aApply)
        {
            return toRemove.size();
        }
        for (Element el : toRemove)
        {
            String id = el.getAttribute("ID");
            log("Removed orphaned leaf: " + id);
            el.getParentNode().removeChild(el);
            removedLeafIDs.add(id);
            count++;
        }
        return count;
    }

    // ========== Utility methods ==========


    private @Nullable Element findElementByOID(String localName, String oid)
    {
        NodeList list = getElementsByLocalName(localName);
        for (int i = 0; i < list.getLength(); i++)
        {
            Element el = (Element) list.item(i);
            if (oid.equals(el.getAttribute("OID")))
            {
                return el;
            }
        }
        return null;
    }


    private @Nullable Element findDefElementByOID(String localName, String oid)
    {
        NodeList list = doc.getElementsByTagNameNS(defNs, localName);
        for (int i = 0; i < list.getLength(); i++)
        {
            Element el = (Element) list.item(i);
            if (oid.equals(el.getAttribute("OID")))
            {
                return el;
            }
        }
        // Fallback
        return findElementByOID(localName, oid);
    }


    private @Nullable Element findDefElementByID(String localName, String id)
    {
        NodeList list = doc.getElementsByTagNameNS(defNs, localName);
        for (int i = 0; i < list.getLength(); i++)
        {
            Element el = (Element) list.item(i);
            if (id.equals(el.getAttribute("ID")))
            {
                return el;
            }
        }
        // Fallback by local name
        NodeList fallback = getElementsByLocalName(localName);
        for (int i = 0; i < fallback.getLength(); i++)
        {
            Element el = (Element) fallback.item(i);
            if (id.equals(el.getAttribute("ID")))
            {
                return el;
            }
        }
        return null;
    }


    /**
     * Check if an element is a direct or nested child of an ItemGroupDef.
     */
    private boolean isChildOfItemGroupDef(Element element)
    {
        Node parent = element.getParentNode();
        while (parent instanceof Element)
        {
            if ("ItemGroupDef".equals(parent.getLocalName()))
            {
                return true;
            }
            parent = parent.getParentNode();
        }
        return false;
    }


    private NodeList getElementsByLocalName(String localName)
    {
        // Try ODM namespace first, then def namespace, then wildcard
        NodeList list = doc.getElementsByTagNameNS(ODM_NS, localName);
        if (list.getLength() == 0)
        {
            list = doc.getElementsByTagNameNS(defNs, localName);
        }
        if (list.getLength() == 0)
        {
            list = doc.getElementsByTagNameNS("*", localName);
        }
        return list;
    }


    private Set<String> collectOIDs(String localName)
    {
        Set<String> oids = new HashSet<>();
        NodeList list = getElementsByLocalName(localName);
        for (int i = 0; i < list.getLength(); i++)
        {
            Element el = (Element) list.item(i);
            String oid = el.getAttribute("OID");
            if (!oid.isEmpty())
            {
                oids.add(oid);
            }
        }
        return oids;
    }


    /**
     * Collect all values of a given attribute name from any element in the document. This is used
     * to find references like def:CommentOID, def:ValueListOID etc. which can appear as namespaced
     * attributes on various elements.
     */
    private Set<String> collectAllAttributeValues(String attrLocalName)
    {
        Set<String> values = new HashSet<>();
        collectAttributeValuesRecursive(doc.getDocumentElement(), attrLocalName, values);
        return values;
    }


    private void collectAttributeValuesRecursive(Element element, String attrLocalName,
            Set<String> values)
    {
        NamedNodeMap attrs = element.getAttributes();
        if (attrs != null)
        {
            for (int i = 0; i < attrs.getLength(); i++)
            {
                Attr attr = (Attr) attrs.item(i);
                if (attrLocalName.equals(attr.getLocalName()) && !attr.getValue().isEmpty())
                {
                    values.add(attr.getValue());
                }
            }
        }
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++)
        {
            Node child = children.item(i);
            if (child instanceof Element childElement)
            {
                collectAttributeValuesRecursive(childElement, attrLocalName, values);
            }
        }
    }


    /**
     * Package-visible for tests. Delegates to {@link DefineDomUtil#hasNamespace}, which also finds
     * a declaration on a descendant element or under a non-{@code def} prefix -- the converter side
     * detects the same fact this way, and the two implementations had drifted.
     */
    static String detectDefineNamespace(Document doc)
    {
        return DefineDomUtil.hasNamespace(doc, DEF21_NS) ? DEF21_NS : DEF_NS;
    }


    private void log(String message)
    {
        log.add(message);
    }

}
