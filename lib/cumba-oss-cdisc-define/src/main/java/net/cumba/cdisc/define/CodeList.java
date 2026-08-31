package net.cumba.cdisc.define;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

// CodeList
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class CodeList implements IDescribedElement, INamedElement
{

    @JacksonXmlProperty(isAttribute = true, localName = "OID")
    String oid;

    @JacksonXmlProperty(isAttribute = true, localName = "Name")
    String name;

    @JacksonXmlProperty(isAttribute = true, localName = "DataType")
    String dataType;

    @JacksonXmlProperty(isAttribute = true, localName = "SASFormatName")
    String sasFormatName;

    @JacksonXmlProperty(isAttribute = true, localName = "StandardOID")
    String standardOID;

    @JacksonXmlProperty(isAttribute = true, localName = "CommentOID")
    String commentOID;

    @JacksonXmlProperty(localName = "Description")
    Description description;

    @JacksonXmlProperty(localName = "CodeListItem")
    @JacksonXmlElementWrapper(useWrapping = false)
    List<CodeListItem> codeListItems;

    @JacksonXmlProperty(localName = "EnumeratedItem")
    @JacksonXmlElementWrapper(useWrapping = false)
    List<EnumeratedItem> enumeratedItems;

    @JacksonXmlProperty(localName = "ExternalCodeList")
    ExternalCodeList externalCodeList;

    @JacksonXmlProperty(localName = "Alias")
    @JacksonXmlElementWrapper(useWrapping = false)
    List<Alias> aliases;
}
