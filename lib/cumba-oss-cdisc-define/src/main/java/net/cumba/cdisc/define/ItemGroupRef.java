package net.cumba.cdisc.define;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

// ItemGroupRef
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class ItemGroupRef
{

    @JacksonXmlProperty(isAttribute = true, localName = "ItemGroupOID")
    String itemGroupOID;

    @JacksonXmlProperty(isAttribute = true, localName = "OrderNumber")
    Integer orderNumber;

    @JacksonXmlProperty(isAttribute = true, localName = "Mandatory")
    String mandatory;
}
