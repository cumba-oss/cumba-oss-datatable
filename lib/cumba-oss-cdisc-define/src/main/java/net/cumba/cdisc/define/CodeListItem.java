package net.cumba.cdisc.define;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

// CodeListItem
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class CodeListItem
{

    @JacksonXmlProperty(isAttribute = true, localName = "CodedValue")
    String codedValue;

    @JacksonXmlProperty(isAttribute = true, localName = "Rank")
    Double rank;

    @JacksonXmlProperty(isAttribute = true, localName = "OrderNumber")
    Integer orderNumber;

    @JacksonXmlProperty(isAttribute = true, localName = "ExtendedValue")
    String extendedValue;

    @JacksonXmlProperty(localName = "Decode")
    Decode decode;

    @JacksonXmlProperty(localName = "Alias")
    @JacksonXmlElementWrapper(useWrapping = false)
    List<Alias> aliases;
}
