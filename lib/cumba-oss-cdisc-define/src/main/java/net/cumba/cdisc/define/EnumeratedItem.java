package net.cumba.cdisc.define;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

// EnumeratedItem
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class EnumeratedItem
{

    @JacksonXmlProperty(isAttribute = true, localName = "CodedValue")
    String codedValue;

    /** {@code def:ExtendedValue="Yes"} marks a sponsor-extended term (Define-XML v2.x). */
    @JacksonXmlProperty(isAttribute = true, localName = "ExtendedValue")
    String extendedValue;

    @JacksonXmlProperty(isAttribute = true, localName = "Rank")
    Double rank;

    @JacksonXmlProperty(isAttribute = true, localName = "OrderNumber")
    Integer orderNumber;

    @JacksonXmlProperty(localName = "Alias")
    @JacksonXmlElementWrapper(useWrapping = false)
    List<Alias> aliases;
}
