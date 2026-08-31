package net.cumba.cdisc.define;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

// GlobalVariables
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class GlobalVariables
{

    @JacksonXmlProperty(localName = "StudyName")
    String studyName;

    @JacksonXmlProperty(localName = "StudyDescription")
    String studyDescription;

    @JacksonXmlProperty(localName = "ProtocolName")
    String protocolName;
}
