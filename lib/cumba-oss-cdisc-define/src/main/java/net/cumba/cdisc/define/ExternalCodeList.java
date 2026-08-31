package net.cumba.cdisc.define;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

// ExternalCodeList
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExternalCodeList
{

    @JacksonXmlProperty(isAttribute = true, localName = "Dictionary")
    String dictionary;

    @JacksonXmlProperty(isAttribute = true, localName = "Version")
    String version;

    @JacksonXmlProperty(isAttribute = true, localName = "ref")
    String ref;

    @JacksonXmlProperty(isAttribute = true, localName = "href")
    String href;
}
