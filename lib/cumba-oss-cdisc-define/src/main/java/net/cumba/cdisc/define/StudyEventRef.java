package net.cumba.cdisc.define;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

// StudyEventRef
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class StudyEventRef
{

    @JacksonXmlProperty(isAttribute = true, localName = "StudyEventOID")
    String studyEventOID;

    @JacksonXmlProperty(isAttribute = true, localName = "OrderNumber")
    Integer orderNumber;

    @JacksonXmlProperty(isAttribute = true, localName = "Mandatory")
    String mandatory;
}
