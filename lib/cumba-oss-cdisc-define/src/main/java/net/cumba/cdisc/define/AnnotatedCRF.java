package net.cumba.cdisc.define;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

// AnnotatedCRF
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnnotatedCRF
{

    @JacksonXmlProperty(localName = "DocumentRef")
    @JacksonXmlElementWrapper(useWrapping = false)
    List<DocumentRef> documentRefs;
}
