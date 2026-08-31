package net.cumba.cdisc.define;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

// StudyEventDef
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class StudyEventDef implements IDescribedElement, INamedElement
{

    @JacksonXmlProperty(isAttribute = true, localName = "OID")
    String oid;

    @JacksonXmlProperty(isAttribute = true, localName = "Name")
    String name;

    @JacksonXmlProperty(isAttribute = true, localName = "Repeating")
    String repeating;

    @JacksonXmlProperty(isAttribute = true, localName = "Type")
    String type;

    @JacksonXmlProperty(localName = "Description")
    Description description;

    @JacksonXmlProperty(localName = "FormRef")
    @JacksonXmlElementWrapper(useWrapping = false)
    List<FormRef> formRefs;

    @JacksonXmlProperty(localName = "Alias")
    @JacksonXmlElementWrapper(useWrapping = false)
    List<Alias> aliases;
}
