package net.cumba.cdisc.define;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

// Origin
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class Origin implements IDescribedElement
{

    @JacksonXmlProperty(isAttribute = true, localName = "Type")
    String type;

    @JacksonXmlProperty(isAttribute = true, localName = "Source")
    String source;

    @JacksonXmlProperty(isAttribute = true, localName = "CommentOID")
    String commentOID;

    @JacksonXmlProperty(localName = "Description")
    Description description;

    @JacksonXmlProperty(localName = "DocumentRef")
    @JacksonXmlElementWrapper(useWrapping = false)
    List<DocumentRef> documentRefs;
}
