package net.cumba.cdisc.define;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

// SupplementalDoc
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class SupplementalDoc
{

    @JacksonXmlProperty(localName = "DocumentRef")
    @JacksonXmlElementWrapper(useWrapping = false)
    List<DocumentRef> documentRefs;
}
