package net.cumba.cdisc.define;

import static net.cumba.cdisc.define.DefineXmlConverter.DEF_NS_10;
import static net.cumba.cdisc.define.DefineXmlConverter.DEF_NS_20;
import static net.cumba.cdisc.define.DefineXmlConverter.ODM_NS_12;
import static net.cumba.cdisc.define.DefineXmlConverter.ODM_NS_13;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.ProcessingInstruction;

/**
 * Define-XML 1.0 (CRT-DDS) to 2.0 conversion. This is a major, lossy rewrite: v1.0 stores labels,
 * origin, and comments as attributes, derivations as {@code def:ComputationMethod} text, and
 * value-level conditions only by naming convention (no WhereClause). Each transform follows the
 * v2.0 spec "Deprecated Components" table; constructs with no machine-readable v1.0 source
 * (WhereClauses, MethodDef/CommentDef wrappers, OIDs) are synthesised and warned about. See
 * {@code DEFINE-XML-SPECIFICATION.md} and {@code PLAN-define-xml-converter.md} section 4.6.
 */
final class Step10To20 implements ConversionStep
{

    private static final String XML_NS = "http://www.w3.org/XML/1998/namespace";

    private static final String DEFAULT_ORIGIN = "Assigned";

    @Override
    public DefineXmlConverter.Version from()
    {
        return DefineXmlConverter.Version.V1_0;
    }


    @Override
    public DefineXmlConverter.Version to()
    {
        return DefineXmlConverter.Version.V2_0;
    }


    @Override
    public void apply(Document doc, ConversionContext ctx)
    {
        DefineDomUtil.renameNamespace(doc, ODM_NS_12, ODM_NS_13);
        DefineDomUtil.renameNamespace(doc, DEF_NS_10, DEF_NS_20);
        String odmNs = ODM_NS_13;
        String defNs = DEF_NS_20;
        String defPrefix = DefineDomUtil.defPrefix(doc, defNs);

        stampVersions(doc, defNs, defPrefix);
        updateSchemaArtifacts(doc, ctx);

        convertLabels(doc, odmNs, ctx);
        convertDomainKeys(doc, ctx);
        convertOrigins(doc, defNs, defPrefix, ctx);
        convertComputationMethods(doc, odmNs, ctx);
        convertRanks(doc);
        for (Element mdv : DefineDomUtil.elementsByLocalName(doc, "MetaDataVersion"))
        {
            convertComments(mdv, odmNs, defNs, defPrefix, ctx);
            synthesiseWhereClauses(mdv, odmNs, defNs, defPrefix, ctx);
        }
    }


    private void stampVersions(Document doc, String defNs, String defPrefix)
    {
        Element root = doc.getDocumentElement();
        if (root != null && !updateAttrByLocalName(root, "ODMVersion", "1.3.2"))
        {
            root.setAttribute("ODMVersion", "1.3.2");
        }
        for (Element mdv : DefineDomUtil.elementsByLocalName(doc, "MetaDataVersion"))
        {
            if (!updateAttrByLocalName(mdv, "DefineVersion", "2.0.0"))
            {
                DefineDomUtil.setDefAttribute(mdv, defNs, defPrefix, "DefineVersion", "2.0.0");
            }
        }
    }


    private void updateSchemaArtifacts(Document doc, ConversionContext ctx)
    {
        Element root = doc.getDocumentElement();
        if (root != null)
        {
            NamedNodeMap attrs = root.getAttributes();
            for (int i = 0; i < attrs.getLength(); i++)
            {
                Node attr = attrs.item(i);
                if ("schemaLocation".equals(DefineDomUtil.localNameOf(attr)))
                {
                    String v = attr.getNodeValue().replace(ODM_NS_12, ODM_NS_13)
                            .replace(DEF_NS_10, DEF_NS_20).replace("def/v1.0", "def/v2.0")
                            .replace("define1-0-0", "define2-0-0");
                    attr.setNodeValue(v);
                }
            }
        }
        Node node = doc.getFirstChild();
        while (node != null)
        {
            if (node instanceof ProcessingInstruction pi && "xml-stylesheet".equals(pi.getTarget())
                    && (pi.getData().contains("define1") || pi.getData().contains("define-v1")))
            {
                pi.setData(pi.getData().replace("define1-0-0", "define2-0-0")
                        .replace("define1", "define2").replace("define-v1", "define-v2"));
                ctx.log("updated stylesheet reference to define2");
            }
            node = node.getNextSibling();
        }
    }


    /** v1.0 {@code @def:Label} on ItemGroupDef/ItemDef to {@code Description/TranslatedText}. */
    private void convertLabels(Document doc, String odmNs, ConversionContext ctx)
    {
        int converted = 0;
        for (String container : new String[]
        {
                "ItemGroupDef", "ItemDef"
        })
        {
            for (Element el : DefineDomUtil.elementsByLocalName(doc, container))
            {
                String label = DefineDomUtil.removeAttrByLocalName(el, "Label");
                if (label == null || label.isBlank())
                {
                    continue;
                }
                if (!DefineDomUtil.childrenByLocalName(el, "Description").isEmpty())
                {
                    continue; // already has a Description
                }
                DefineDomUtil.insertFirstChild(el, describedBy(doc, odmNs, label));
                converted++;
            }
        }
        if (converted > 0)
        {
            ctx.log("converted " + converted + " def:Label attributes to Description elements");
        }
    }


    /** v1.0 {@code @def:DomainKeys} (comma list) to per-{@code ItemRef} {@code @KeySequence}. */
    private void convertDomainKeys(Document doc, ConversionContext ctx)
    {
        for (Element ig : DefineDomUtil.elementsByLocalName(doc, "ItemGroupDef"))
        {
            String keys = DefineDomUtil.removeAttrByLocalName(ig, "DomainKeys");
            if (keys == null || keys.isBlank())
            {
                continue;
            }
            List<Element> refs = DefineDomUtil.childrenByLocalName(ig, "ItemRef");
            int seq = 0;
            for (String raw : keys.split(",", 0))
            {
                String key = raw.trim();
                if (key.isEmpty())
                {
                    continue;
                }
                seq++;
                Element match = findItemRefByVar(refs, key);
                if (match == null)
                {
                    ctx.warn("key variable \"" + key + "\" in def:DomainKeys of ItemGroupDef "
                            + DefineDomUtil.attrIgnoreNs(ig, "OID")
                            + " did not match any ItemRef; KeySequence not set");
                }
                else
                {
                    match.setAttribute("KeySequence", Integer.toString(seq));
                }
            }
        }
    }


    /** v1.0 free-text {@code @Origin} attribute to a {@code def:Origin} element with a Type. */
    private void convertOrigins(Document doc, String defNs, String defPrefix, ConversionContext ctx)
    {
        for (Element it : DefineDomUtil.elementsByLocalName(doc, "ItemDef"))
        {
            String origin = DefineDomUtil.removeAttrByLocalName(it, "Origin");
            if (origin == null || origin.isBlank())
            {
                continue;
            }
            if (!DefineDomUtil.childrenByLocalName(it, "Origin").isEmpty())
            {
                continue; // already has an Origin element
            }
            String type = mapOriginType(origin);
            Element originEl = DefineDomUtil.createDefElement(doc, defNs, defPrefix, "Origin");
            originEl.setAttribute("Type", type);
            DefineDomUtil.insertBeforeFirstChild(it, originEl, "ValueListRef");
            if ("CRF".equals(type) && origin.matches(".*\\d.*"))
            {
                ctx.warn("ItemDef " + DefineDomUtil.attrIgnoreNs(it, "OID")
                        + ": CRF page reference \"" + origin
                        + "\" dropped (v2.0 needs def:DocumentRef/def:PDFPageRef)");
            }
            else if (DEFAULT_ORIGIN.equals(type)
                    && !origin.toLowerCase(Locale.ROOT).contains("assign"))
            {
                ctx.warn("ItemDef " + DefineDomUtil.attrIgnoreNs(it, "OID")
                        + ": could not map Origin \"" + origin + "\"; defaulted to Type=\""
                        + DEFAULT_ORIGIN + "\"");
            }
        }
    }


    /** v1.0 {@code @Comment} attribute to {@code def:CommentDef} + {@code @def:CommentOID}. */
    private void convertComments(Element mdv, String odmNs, String defNs, String defPrefix,
            ConversionContext ctx)
    {
        Document doc = mdv.getOwnerDocument();
        Map<String, String> textToOid = new HashMap<>();
        for (Element it : DefineDomUtil.descendantElements(mdv, "ItemDef"))
        {
            String comment = DefineDomUtil.removeAttrByLocalName(it, "Comment");
            if (comment == null || comment.isBlank())
            {
                continue;
            }
            String text = comment.trim();
            String oid = textToOid.get(text);
            if (oid == null)
            {
                oid = ctx.oids().mint("COM");
                textToOid.put(text, oid);
                Element commentDef = DefineDomUtil.createDefElement(doc, defNs, defPrefix,
                        "CommentDef");
                commentDef.setAttribute("OID", oid);
                commentDef.appendChild(describedBy(doc, odmNs, text));
                DefineDomUtil.insertInCanonicalOrder(mdv, commentDef, Step20To21.MDV_ORDER);
            }
            DefineDomUtil.setDefAttribute(it, defNs, defPrefix, "CommentOID", oid);
        }
        if (!textToOid.isEmpty())
        {
            ctx.log("created " + textToOid.size()
                    + " def:CommentDef from inline @Comment attributes");
        }
    }


    /**
     * v1.0 {@code def:ComputationMethod} (text body) to {@code MethodDef}+{@code Description};
     * rewrite {@code ItemDef/@def:ComputationMethodOID} into {@code ItemRef/@MethodOID}.
     */
    private void convertComputationMethods(Document doc, String odmNs, ConversionContext ctx)
    {
        Map<String, String> itemOidToMethod = new HashMap<>();
        for (Element it : DefineDomUtil.elementsByLocalName(doc, "ItemDef"))
        {
            String method = DefineDomUtil.removeAttrByLocalName(it, "ComputationMethodOID");
            String itemOid = DefineDomUtil.attrIgnoreNs(it, "OID");
            if (method != null && !method.isBlank() && itemOid != null)
            {
                itemOidToMethod.put(itemOid, method);
            }
        }
        if (!itemOidToMethod.isEmpty())
        {
            for (Element ref : DefineDomUtil.elementsByLocalName(doc, "ItemRef"))
            {
                String itemOid = DefineDomUtil.attrIgnoreNs(ref, "ItemOID");
                String method = itemOid == null ? null : itemOidToMethod.get(itemOid);
                if (method != null)
                {
                    ref.setAttribute("MethodOID", method);
                }
            }
        }

        int methods = 0;
        for (Element cm : DefineDomUtil.elementsByLocalName(doc, "ComputationMethod"))
        {
            String oid = DefineDomUtil.attrIgnoreNs(cm, "OID");
            String text = cm.getTextContent();
            Element methodDef = doc.createElementNS(odmNs, "MethodDef");
            if (oid != null)
            {
                methodDef.setAttribute("OID", oid);
            }
            methodDef.setAttribute("Name", oid == null ? "Computation" : "Computation " + oid);
            methodDef.setAttribute("Type", "Computation");
            methodDef.appendChild(describedBy(doc, odmNs, text == null ? "" : text.trim()));
            Node parent = cm.getParentNode();
            parent.removeChild(cm);
            if (parent instanceof Element parentEl)
            {
                DefineDomUtil.insertInCanonicalOrder(parentEl, methodDef, Step20To21.MDV_ORDER);
            }
            methods++;
        }
        if (methods > 0)
        {
            ctx.log("converted " + methods + " def:ComputationMethod to MethodDef");
        }
    }


    /** v1.0 {@code @def:Rank} on codelist items to ODM {@code @Rank}. */
    private void convertRanks(Document doc)
    {
        for (String container : new String[]
        {
                "CodeListItem", "EnumeratedItem"
        })
        {
            for (Element item : DefineDomUtil.elementsByLocalName(doc, container))
            {
                String rank = DefineDomUtil.removeAttrByLocalName(item, "Rank");
                if (rank != null && !rank.isBlank())
                {
                    item.setAttribute("Rank", rank);
                }
            }
        }
    }


    /**
     * Synthesise a {@code def:WhereClauseDef}/{@code RangeCheck} for each {@code ItemRef} inside a
     * {@code def:ValueListDef}, inferring the condition from the OID naming convention. The
     * ValueListDef OID encodes {@code <prefix>.<dataset>.[<qualVar>.<qualVal>]*.<finalVar>}: each
     * leading {@code qualVar/qualVal} pair becomes a fixed {@code = qualVal} condition (an AND),
     * and the {@code finalVar} is compared to the value encoded in each child ItemRef's OID. A
     * {@code NULL} qualifier value means "no condition" and is skipped. Every inferred condition is
     * warned about, and references to ItemOIDs that do not resolve to an ItemDef are flagged.
     */
    private void synthesiseWhereClauses(Element mdv, String odmNs, String defNs, String defPrefix,
            ConversionContext ctx)
    {
        Document doc = mdv.getOwnerDocument();
        java.util.Set<String> itemDefOids = collectItemDefOids(doc);
        int created = 0;
        for (Element vld : DefineDomUtil.descendantElements(mdv, "ValueListDef"))
        {
            String vldOid = DefineDomUtil.attrIgnoreNs(vld, "OID");
            if (vldOid == null)
            {
                continue;
            }
            String[] segs = vldOid.split("\\.", 0);
            String dataset = segs.length >= 2 ? segs[1] : null;
            String finalItemOid = qualify(dataset, segs[segs.length - 1]);

            // Leading qualifier (var, value) pairs occupy segments [2 .. len-2].
            List<String[]> fixed = new java.util.ArrayList<>();
            for (int k = 2; k + 1 <= segs.length - 2; k += 2)
            {
                if ("NULL".equalsIgnoreCase(segs[k + 1]))
                {
                    ctx.warn(vldOid + ": qualifier " + segs[k]
                            + " has placeholder value NULL; omitted from the WhereClause");
                }
                else
                {
                    fixed.add(new String[]
                    {
                            qualify(dataset, segs[k]), segs[k + 1]
                    });
                }
            }
            if ((segs.length - 3) > 0 && (segs.length - 3) % 2 != 0)
            {
                ctx.warn(vldOid + ": irregular ValueListDef OID structure; synthesised WhereClause"
                        + " conditions may be incomplete");
            }
            warnUnresolved(finalItemOid, itemDefOids, vldOid, ctx);
            for (String[] cond : fixed)
            {
                warnUnresolved(cond[0], itemDefOids, vldOid, ctx);
            }

            for (Element ref : DefineDomUtil.childrenByLocalName(vld, "ItemRef"))
            {
                if (!DefineDomUtil.childrenByLocalName(ref, "WhereClauseRef").isEmpty())
                {
                    continue; // already conditioned
                }
                String itemOid = DefineDomUtil.attrIgnoreNs(ref, "ItemOID");
                String value = itemOid == null ? "" : lastSegment(itemOid);
                String wcOid = ctx.oids().mint("WC");

                Element wc = DefineDomUtil.createDefElement(doc, defNs, defPrefix,
                        "WhereClauseDef");
                wc.setAttribute("OID", wcOid);
                StringBuilder desc = new StringBuilder();
                for (String[] cond : fixed)
                {
                    wc.appendChild(rangeCheck(doc, odmNs, defNs, defPrefix, cond[0], cond[1]));
                    desc.append(cond[0]).append(" EQ \"").append(cond[1]).append("\" AND ");
                }
                wc.appendChild(rangeCheck(doc, odmNs, defNs, defPrefix, finalItemOid, value));
                desc.append(finalItemOid).append(" EQ \"").append(value).append('"');
                DefineDomUtil.insertInCanonicalOrder(mdv, wc, Step20To21.MDV_ORDER);

                Element wcRef = DefineDomUtil.createDefElement(doc, defNs, defPrefix,
                        "WhereClauseRef");
                wcRef.setAttribute("WhereClauseOID", wcOid);
                ref.appendChild(wcRef);
                created++;
                ctx.warn("synthesised " + wcOid + ": " + desc
                        + " (inferred from v1.0 ValueListDef OID convention; verify)");
            }
        }
        if (created > 0)
        {
            ctx.log("synthesised " + created + " def:WhereClauseDef from v1.0 value lists");
        }
    }


    private static String qualify(@Nullable String dataset, String var)
    {
        return dataset == null ? var : dataset + "." + var;
    }


    private static java.util.Set<String> collectItemDefOids(Document doc)
    {
        java.util.Set<String> oids = new java.util.HashSet<>();
        for (Element it : DefineDomUtil.elementsByLocalName(doc, "ItemDef"))
        {
            String oid = DefineDomUtil.attrIgnoreNs(it, "OID");
            if (oid != null)
            {
                oids.add(oid);
            }
        }
        return oids;
    }


    private static void warnUnresolved(String itemOid, java.util.Set<String> itemDefOids,
            String vldOid, ConversionContext ctx)
    {
        if (!itemDefOids.contains(itemOid))
        {
            ctx.warn(vldOid + ": synthesised WhereClause references ItemOID \"" + itemOid
                    + "\" which has no matching ItemDef; verify");
        }
    }


    private static Element rangeCheck(Document doc, String odmNs, String defNs, String defPrefix,
            String itemOid, String value)
    {
        Element rc = doc.createElementNS(odmNs, "RangeCheck");
        rc.setAttribute("Comparator", "EQ");
        rc.setAttribute("SoftHard", "Soft");
        DefineDomUtil.setDefAttribute(rc, defNs, defPrefix, "ItemOID", itemOid);
        Element cv = doc.createElementNS(odmNs, "CheckValue");
        cv.setTextContent(value);
        rc.appendChild(cv);
        return rc;
    }

    // ========== helpers ==========


    /** Build a {@code Description/TranslatedText} (ODM, xml:lang="en") carrying {@code text}. */
    private static Element describedBy(Document doc, String odmNs, String text)
    {
        Element description = doc.createElementNS(odmNs, "Description");
        Element translated = doc.createElementNS(odmNs, "TranslatedText");
        translated.setAttributeNS(XML_NS, "xml:lang", "en");
        translated.setTextContent(text);
        description.appendChild(translated);
        return description;
    }


    private static String mapOriginType(String raw)
    {
        String s = raw.toLowerCase(Locale.ROOT);
        if (s.contains("crf"))
        {
            return "CRF";
        }
        if (s.contains("deriv"))
        {
            return "Derived";
        }
        if (s.contains("assign"))
        {
            return "Assigned";
        }
        if (s.contains("protocol"))
        {
            return "Protocol";
        }
        if (s.contains("edt") || s.contains("electronic"))
        {
            return "eDT";
        }
        // a bare DATASET.VAR token (no spaces) is a predecessor reference; free text with a
        // sentence-ending period is not.
        if (s.contains("predecessor") || raw.matches("[A-Za-z][\\w]*\\.[A-Za-z][\\w]*"))
        {
            return "Predecessor";
        }
        return DEFAULT_ORIGIN;
    }


    /** Find an ItemRef whose ItemOID equals or ends with {@code .var}. */
    private static @Nullable Element findItemRefByVar(List<Element> refs, String var)
    {
        Element match = null;
        for (Element ref : refs)
        {
            String oid = DefineDomUtil.attrIgnoreNs(ref, "ItemOID");
            if (oid != null && (oid.equals(var) || oid.endsWith("." + var)))
            {
                match = ref;
                break;
            }
        }
        return match;
    }


    private static String lastSegment(String dotted)
    {
        int dot = dotted.lastIndexOf('.');
        return dot < 0 ? dotted : dotted.substring(dot + 1);
    }


    /** Update an attribute matched by local name; returns {@code true} if it existed. */
    private static boolean updateAttrByLocalName(Element el, String localName, String value)
    {
        NamedNodeMap attrs = el.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++)
        {
            Node attr = attrs.item(i);
            if (localName.equals(DefineDomUtil.localNameOf(attr)))
            {
                attr.setNodeValue(value);
                return true;
            }
        }
        return false;
    }

}
