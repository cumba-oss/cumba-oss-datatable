package net.cumba.cdisc.define;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

// ItemDef (Variable)
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class ItemDef implements IDescribedElement, INamedElement
{

    @JacksonXmlProperty(isAttribute = true, localName = "OID")
    String oid;

    @JacksonXmlProperty(isAttribute = true, localName = "Name")
    String name;

    @JacksonXmlProperty(isAttribute = true, localName = "DataType")
    String dataType;

    @JacksonXmlProperty(isAttribute = true, localName = "Length")
    Integer length;

    @JacksonXmlProperty(isAttribute = true, localName = "SignificantDigits")
    Integer significantDigits;

    @JacksonXmlProperty(isAttribute = true, localName = "SASFieldName")
    String sasFieldName;

    @JacksonXmlProperty(isAttribute = true, localName = "DisplayFormat")
    String displayFormat;

    @JacksonXmlProperty(isAttribute = true, localName = "CommentOID")
    String commentOID;

    @JacksonXmlProperty(isAttribute = true, localName = "Origin")
    String origin;

    @JacksonXmlProperty(isAttribute = true, localName = "StandardOID")
    String standardOID;

    @JacksonXmlProperty(isAttribute = true, localName = "Label")
    String label;

    @JacksonXmlProperty(localName = "Description")
    Description description;

    @JacksonXmlProperty(localName = "CodeListRef")
    CodeListRef codeListRef;

    @JacksonXmlProperty(localName = "ValueListRef")
    ValueListRef valueListRef;

    // The Define-XML v2.1 {@code <def:Origin Type="…"/>} child element. This shares the local name
    // "Origin" with the v2.0 {@link #origin} attribute above, which Jackson-XML cannot disambiguate
    // on its own (it matches by local name, ignoring the attribute/element and namespace
    // distinction). The property is therefore bound under a synthetic name that never occurs in the
    // XML, and {@link OriginDisambiguationModule} redirects the element-form "Origin" node here at
    // deserialization time. See {@link DefineXmlParser}.
    @JacksonXmlProperty(localName = ORIGIN_ELEMENT_PROPERTY)
    Origin originElement;

    /**
     * Synthetic Jackson property name for {@link #originElement}. Deliberately not a real
     * Define-XML name so it never collides with the {@code Origin} attribute/element;
     * {@link OriginDisambiguationModule} rewrites the parsed tree to feed the {@code <def:Origin>}
     * element into this property.
     */
    static final String ORIGIN_ELEMENT_PROPERTY = "__def_OriginElement";

    @JacksonXmlProperty(localName = "Alias")
    @JacksonXmlElementWrapper(useWrapping = false)
    List<Alias> aliases;

    @JacksonXmlProperty(localName = "Comment")
    String comment;
}
