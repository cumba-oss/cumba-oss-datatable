package net.cumba.cdisc.define;

import static net.cumba.cdisc.define.DefineXmlConverter.DEF_NS_20;
import static net.cumba.cdisc.define.DefineXmlConverter.DEF_NS_21;

import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.ProcessingInstruction;

/**
 * Define-XML 2.0 → 2.1 conversion (additive). Bumps the {@code def:} namespace and DefineVersion,
 * adds the required {@code def:Context}, synthesises a {@code def:Standards} block from the legacy
 * {@code @def:StandardName}/{@code @def:StandardVersion} attributes, converts the {@code def:Class}
 * attribute into a child element, and splits each {@code def:Origin/@Type} into {@code Type}+
 * {@code Source}. See {@code DEFINE-XML-SPECIFICATION.md} and {@code PLAN-define-xml-converter.md}.
 */
final class Step20To21 implements ConversionStep
{

    /** v2.0 Origin {@code @Type} → {v2.1 {@code @Type}, {@code @Source}}; empty source = omit. */
    private static final Map<String, String[]> ORIGIN_MAP = Map.of("CRF", new String[]
    {
            "Collected", "Investigator"
    }, "eDT", new String[]
    {
            "Collected", "Vendor"
    }, "Derived", new String[]
    {
            "Derived", "Sponsor"
    }, "Assigned", new String[]
    {
            "Assigned", "Sponsor"
    }, "Protocol", new String[]
    {
            "Protocol", "Sponsor"
    }, "Predecessor", new String[]
    {
            "Predecessor", ""
    });

    /** Origin types whose Source mapping is ambiguous and should be warned about. */
    private static final Set<String> ORIGIN_WARN_TYPES = Set.of("eDT", "Derived", "Assigned");

    /** Schema-mandated child order of MetaDataVersion (local names), for ordered inserts. */
    static final java.util.List<String> MDV_ORDER = java.util.List.of("Description", "Include",
            "Protocol", "Standards", "AnnotatedCRF", "SupplementalDoc", "ValueListDef",
            "WhereClauseDef", "ItemGroupDef", "ItemDef", "CodeList", "MethodDef", "CommentDef",
            "leaf");

    @Override
    public DefineXmlConverter.Version from()
    {
        return DefineXmlConverter.Version.V2_0;
    }


    @Override
    public DefineXmlConverter.Version to()
    {
        return DefineXmlConverter.Version.V2_1;
    }


    @Override
    public void apply(Document doc, ConversionContext ctx)
    {
        DefineDomUtil.renameNamespace(doc, DEF_NS_20, DEF_NS_21);
        String defNs = DEF_NS_21;
        String prefix = DefineDomUtil.defPrefix(doc, defNs);
        Element root = doc.getDocumentElement();

        updateSchemaLocation(root);
        updateStylesheet(doc, ctx);

        if (root != null && DefineDomUtil.attrIgnoreNs(root, "Context") == null)
        {
            DefineDomUtil.setDefAttribute(root, defNs, prefix, "Context", ctx.context());
            ctx.log("added def:Context=\"" + ctx.context() + "\"");
        }

        for (Element mdv : DefineDomUtil.elementsByLocalName(doc, "MetaDataVersion"))
        {
            setOrUpdateDefAttr(mdv, defNs, prefix, "DefineVersion", "2.1.0");
            synthesiseStandards(doc, mdv, defNs, prefix, ctx);
        }

        convertClassAttributes(doc, defNs, prefix, ctx);
        convertOrigins(doc, ctx);
        warnCodeListStandards(doc, ctx);
    }


    private void synthesiseStandards(Document doc, Element mdv, String defNs, String prefix,
            ConversionContext ctx)
    {
        if (!DefineDomUtil.childrenByLocalName(mdv, "Standards").isEmpty())
        {
            ctx.log("def:Standards already present; left unchanged");
            return;
        }
        String stdName = DefineDomUtil.attrIgnoreNs(mdv, "StandardName");
        String stdVer = DefineDomUtil.attrIgnoreNs(mdv, "StandardVersion");
        if (stdName == null || stdName.isBlank())
        {
            ctx.warn("no @def:StandardName in v2.0 input; could not synthesise a def:Standards"
                    + " block (required when def:Context=\"Submission\")");
            return;
        }

        String oid = ctx.oids().mint("STD");
        Element standards = DefineDomUtil.createDefElement(doc, defNs, prefix, "Standards");
        Element standard = DefineDomUtil.createDefElement(doc, defNs, prefix, "Standard");
        standard.setAttribute("OID", oid);
        standard.setAttribute("Name", normaliseStandardName(stdName));
        standard.setAttribute("Type", "IG");
        if (stdVer != null && !stdVer.isBlank())
        {
            standard.setAttribute("Version", stdVer);
        }
        standard.setAttribute("Status", "Final");
        standards.appendChild(standard);
        DefineDomUtil.insertInCanonicalOrder(mdv, standards, MDV_ORDER);
        ctx.log("synthesised def:Standards/def:Standard OID=" + oid + " Name="
                + normaliseStandardName(stdName) + " Version=" + stdVer);
        ctx.warn("CT (controlled terminology) Standard rows could not be derived; no CT version is"
                + " stored in Define-XML v2.0");

        for (Element ig : DefineDomUtil.descendantElements(mdv, "ItemGroupDef"))
        {
            if (DefineDomUtil.attrIgnoreNs(ig, "StandardOID") == null)
            {
                DefineDomUtil.setDefAttribute(ig, defNs, prefix, "StandardOID", oid);
            }
        }

        if (ctx.keepLegacyStandardAttributes())
        {
            ctx.warn(
                    "kept deprecated @def:StandardName/@def:StandardVersion; output is not strictly"
                            + " Define-XML v2.1 conformant");
        }
        else
        {
            DefineDomUtil.removeAttrByLocalName(mdv, "StandardName");
            DefineDomUtil.removeAttrByLocalName(mdv, "StandardVersion");
            ctx.log("dropped deprecated @def:StandardName/@def:StandardVersion");
        }
    }


    private void convertClassAttributes(Document doc, String defNs, String prefix,
            ConversionContext ctx)
    {
        for (Element ig : DefineDomUtil.elementsByLocalName(doc, "ItemGroupDef"))
        {
            if (!DefineDomUtil.childrenByLocalName(ig, "Class").isEmpty())
            {
                continue; // already an element
            }
            String cls = DefineDomUtil.attrIgnoreNs(ig, "Class");
            if (cls == null)
            {
                continue;
            }
            DefineDomUtil.removeAttrByLocalName(ig, "Class");
            Element classEl = DefineDomUtil.createDefElement(doc, defNs, prefix, "Class");
            classEl.setAttribute("Name", cls);
            DefineDomUtil.insertBeforeFirstChild(ig, classEl, "leaf");
            ctx.log("ItemGroupDef " + DefineDomUtil.attrIgnoreNs(ig, "OID")
                    + ": def:Class attribute -> element");
        }
    }


    private void convertOrigins(Document doc, ConversionContext ctx)
    {
        for (Element origin : DefineDomUtil.elementsByLocalName(doc, "Origin"))
        {
            String existingSource = origin.getAttribute("Source");
            if (!existingSource.isEmpty())
            {
                continue; // already has a Source (already v2.1-shaped)
            }
            String type = origin.getAttribute("Type");
            if (type.isEmpty())
            {
                ctx.warn("def:Origin without @Type; left unchanged");
                continue;
            }
            String[] mapped = ORIGIN_MAP.get(type);
            if (mapped == null)
            {
                ctx.warn("unrecognised Origin @Type \"" + type
                        + "\"; left unchanged (no Source added)");
                continue;
            }
            String newType = mapped[0];
            String source = mapped[1];
            if (!newType.equals(type))
            {
                origin.setAttribute("Type", newType);
            }
            if (!source.isEmpty())
            {
                origin.setAttribute("Source", source);
            }
            if (ORIGIN_WARN_TYPES.contains(type))
            {
                ctx.warn(
                        "Origin @Type \"" + type + "\" mapped to Type=\"" + newType + "\" Source=\""
                                + source + "\" — verify (Source is ambiguous for this type)");
            }
            else
            {
                ctx.log("Origin @Type \"" + type + "\" -> Type=\"" + newType + "\""
                        + (source.isEmpty() ? "" : " Source=\"" + source + "\""));
            }
        }
    }


    private void warnCodeListStandards(Document doc, ConversionContext ctx)
    {
        for (Element cl : DefineDomUtil.elementsByLocalName(doc, "CodeList"))
        {
            if (DefineDomUtil.attrIgnoreNs(cl, "StandardOID") == null
                    && DefineDomUtil.childrenByLocalName(cl, "ExternalCodeList").isEmpty())
            {
                ctx.warn("CodeLists left without def:StandardOID (no CT version available from a"
                        + " v2.0 input); add CT Standards manually if required");
                return;
            }
        }
    }


    private static void updateSchemaLocation(@Nullable Element root)
    {
        if (root == null)
        {
            return;
        }
        NamedNodeMap attrs = root.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++)
        {
            Node attr = attrs.item(i);
            if ("schemaLocation".equals(DefineDomUtil.localNameOf(attr)))
            {
                String v = attr.getNodeValue().replace(DEF_NS_20, DEF_NS_21)
                        .replace("def/v2.0", "def/v2.1").replace("define2-0-0", "define2-1-0");
                attr.setNodeValue(v);
            }
        }
    }


    private static void updateStylesheet(Document doc, ConversionContext ctx)
    {
        Node node = doc.getFirstChild();
        while (node != null)
        {
            if (node instanceof ProcessingInstruction pi && "xml-stylesheet".equals(pi.getTarget())
                    && pi.getData().contains("define2-0"))
            {
                pi.setData(pi.getData().replace("define2-0", "define2-1"));
                ctx.log("updated stylesheet reference to define2-1");
            }
            node = node.getNextSibling();
        }
    }


    private static void setOrUpdateDefAttr(Element el, String defNs, String prefix,
            String localName, String value)
    {
        NamedNodeMap attrs = el.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++)
        {
            Node attr = attrs.item(i);
            if (localName.equals(DefineDomUtil.localNameOf(attr)))
            {
                attr.setNodeValue(value);
                return;
            }
        }
        DefineDomUtil.setDefAttribute(el, defNs, prefix, localName, value);
    }


    private static String normaliseStandardName(String name)
    {
        return name.replace("-", "");
    }

}
