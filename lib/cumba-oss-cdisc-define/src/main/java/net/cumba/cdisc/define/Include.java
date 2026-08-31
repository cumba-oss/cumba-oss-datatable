package net.cumba.cdisc.define;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

// Include
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class Include
{

    @JacksonXmlProperty(isAttribute = true, localName = "StudyOID")
    String studyOID;

    @JacksonXmlProperty(isAttribute = true, localName = "MetaDataVersionOID")
    String metaDataVersionOID;
}
