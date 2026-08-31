package net.cumba.cdisc.define;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

// RangeCheck
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class RangeCheck
{

    @JacksonXmlProperty(isAttribute = true, localName = "Comparator")
    String comparator;

    @JacksonXmlProperty(isAttribute = true, localName = "SoftHard")
    String softHard;

    @JacksonXmlProperty(isAttribute = true, localName = "ItemOID")
    String itemOID;

    @JacksonXmlProperty(localName = "CheckValue")
    @JacksonXmlElementWrapper(useWrapping = false)
    List<CheckValue> checkValues;

    @JacksonXmlProperty(localName = "MeasurementUnitRef")
    MeasurementUnitRef measurementUnitRef;
}
