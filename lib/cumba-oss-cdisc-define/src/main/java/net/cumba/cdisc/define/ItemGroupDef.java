package net.cumba.cdisc.define;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

// ItemGroupDef (Dataset)
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class ItemGroupDef implements IDescribedElement, INamedElement
{

    @JacksonXmlProperty(isAttribute = true, localName = "OID")
    String oid;

    @JacksonXmlProperty(isAttribute = true, localName = "Name")
    String name;

    @JacksonXmlProperty(isAttribute = true, localName = "Repeating")
    String repeating;

    @JacksonXmlProperty(isAttribute = true, localName = "IsReferenceData")
    String isReferenceData;

    @JacksonXmlProperty(isAttribute = true, localName = "SASDatasetName")
    String sasDatasetName;

    @JacksonXmlProperty(isAttribute = true, localName = "Domain")
    String domain;

    @JacksonXmlProperty(isAttribute = true, localName = "Purpose")
    String purpose;

    @JacksonXmlProperty(isAttribute = true, localName = "HasNoData")
    String hasNoData;

    @JacksonXmlProperty(isAttribute = true, localName = "Structure")
    String structure;

    @JacksonXmlProperty(isAttribute = true, localName = "Class")
    String clazz;

    // The Define-XML v2.1 {@code <def:Class Name="…">} child element (with optional
    // {@code <def:SubClass>} children). Shares the local name "Class" with the v2.0 attribute
    // above, which Jackson-XML cannot disambiguate on its own — bound under a synthetic name that
    // never occurs in real Define-XML; {@link ItemGroupClassDisambiguationModule} redirects the
    // element-form "Class" node here at parse time (same pattern as ItemDef's Origin).
    @JacksonXmlProperty(localName = CLASS_ELEMENT_PROPERTY)
    ClassDef classElement;

    /**
     * Synthetic Jackson property name for {@link #classElement}. Deliberately not a real Define-XML
     * name so it never collides with the {@code Class} attribute/element;
     * {@link ItemGroupClassDisambiguationModule} rewrites the parsed tree to feed the
     * {@code <def:Class>} element here.
     */
    static final String CLASS_ELEMENT_PROPERTY = "__def_ClassElement";

    @JacksonXmlProperty(isAttribute = true, localName = "ArchiveLocationID")
    String archiveLocationID;

    @JacksonXmlProperty(isAttribute = true, localName = "CommentOID")
    String commentOID;

    @JacksonXmlProperty(isAttribute = true, localName = "Label")
    String label;

    @JacksonXmlProperty(isAttribute = true, localName = "DomainKeys")
    String domainKeys;

    @JacksonXmlProperty(isAttribute = true, localName = "StandardOID")
    String standardOID;

    @JacksonXmlProperty(localName = "Description")
    Description description;

    @JacksonXmlProperty(localName = "ItemRef")
    @JacksonXmlElementWrapper(useWrapping = false)
    List<ItemRef> itemRefs;

    @JacksonXmlProperty(localName = "Alias")
    @JacksonXmlElementWrapper(useWrapping = false)
    List<Alias> aliases;

    @JacksonXmlProperty(localName = "Comment")
    String comment;

    @JacksonXmlProperty(localName = "leaf")
    Leaf leaf;

    /**
     * The dataset's class name from whichever Define-XML form the document used: the v2.1
     * {@code <def:Class Name="…">} element when present (and named), else the v2.0
     * {@code def:Class} attribute. {@code null} when neither is present.
     *
     * <p>
     * ⚠ The element wins only when it carries a <b>non-blank</b> name. Testing merely for
     * {@code isEmpty()} let a whitespace-only {@code <def:Class Name="   ">} beat a perfectly good
     * 2.0 attribute — and the blank then failed {@code cvc-enumeration-valid} on write, because the
     * schema restricts the name to the {@code ItemGroupClass} enumeration.
     * </p>
     */
    public String getEffectiveClassName()
    {
        if (classElement != null && classElement.getName() != null
                && !classElement.getName().isBlank())
        {
            return classElement.getName();
        }
        return clazz;
    }


    /**
     * The declared {@code <def:SubClass>} names of the v2.1 class element, in document order; empty
     * when the document carries none (always empty for a v2.0 document — the attribute form has no
     * subclass concept).
     */
    public List<String> getSubClassNames()
    {
        if (classElement == null || classElement.getSubClasses() == null)
        {
            return List.of();
        }
        return classElement.getSubClasses().stream().map(SubClassDef::getName)
                .filter(n -> n != null && !n.isBlank()).toList();
    }
}
