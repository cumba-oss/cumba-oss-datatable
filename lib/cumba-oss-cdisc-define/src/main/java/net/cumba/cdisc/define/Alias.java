package net.cumba.cdisc.define;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

// Alias
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class Alias
{

    @JacksonXmlProperty(isAttribute = true, localName = "Context")
    String context;

    @JacksonXmlProperty(isAttribute = true, localName = "Name")
    String name;
}
