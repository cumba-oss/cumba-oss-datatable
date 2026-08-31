package net.cumba.cdisc.define;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

// CommentDef
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class CommentDef implements IDescribedElement
{

    @JacksonXmlProperty(isAttribute = true, localName = "OID")
    String oid;

    @JacksonXmlProperty(localName = "Description")
    Description description;

    @JacksonXmlProperty(localName = "DocumentRef")
    @JacksonXmlElementWrapper(useWrapping = false)
    List<DocumentRef> documentRefs;
}
