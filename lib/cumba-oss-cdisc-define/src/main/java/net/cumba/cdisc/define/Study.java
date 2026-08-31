package net.cumba.cdisc.define;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

// Study
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class Study
{

    @JacksonXmlProperty(isAttribute = true, localName = "OID")
    String oid;

    @JacksonXmlProperty(localName = "GlobalVariables")
    GlobalVariables globalVariables;

    @JacksonXmlProperty(localName = "MetaDataVersion")
    @JacksonXmlElementWrapper(useWrapping = false)
    List<MetaDataVersion> metaDataVersions;
}
