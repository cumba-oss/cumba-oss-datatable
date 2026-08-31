package net.cumba.cdisc.define;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

// WhereClauseRef
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class WhereClauseRef
{

    @JacksonXmlProperty(isAttribute = true, localName = "WhereClauseOID")
    String whereClauseOID;
}
