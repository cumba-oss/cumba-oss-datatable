package net.cumba.cdisc.define;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

// Leaf (for PDF documents)
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class Leaf
{

    @JacksonXmlProperty(isAttribute = true, localName = "ID")
    String id;

    @JacksonXmlProperty(isAttribute = true, localName = "href")
    String href;

    @JacksonXmlProperty(localName = "title")
    Title title;
}
