package net.cumba.cdisc.define;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

// Root element
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(localName = "ODM")
public class ODM
{

    @JacksonXmlProperty(isAttribute = true, localName = "FileOID")
    String fileOID;

    @JacksonXmlProperty(isAttribute = true, localName = "FileType")
    String fileType;

    @JacksonXmlProperty(isAttribute = true, localName = "ODMVersion")
    String odmVersion;

    @JacksonXmlProperty(isAttribute = true, localName = "Granularity")
    String granularity;

    @JacksonXmlProperty(isAttribute = true, localName = "Archival")
    String archival;

    @JacksonXmlProperty(isAttribute = true, localName = "CreationDateTime")
    String creationDateTime;

    @JacksonXmlProperty(isAttribute = true, localName = "PriorFileOID")
    String priorFileOID;

    @JacksonXmlProperty(isAttribute = true, localName = "AsOfDateTime")
    String asOfDateTime;

    // def:Context — Define-XML 2.1 only ("Submission" | "Other"). The def: prefix is stripped by
    // NamespaceAgnosticAttrFactory, so the plain local name binds both dialects.
    @JacksonXmlProperty(isAttribute = true, localName = "Context")
    String context;

    @JacksonXmlProperty(isAttribute = true, localName = "SourceSystem")
    String sourceSystem;

    @JacksonXmlProperty(isAttribute = true, localName = "SourceSystemVersion")
    String sourceSystemVersion;

    @JacksonXmlProperty(isAttribute = true, localName = "schemaLocation")
    String schemaLocation;

    @JacksonXmlProperty(localName = "Study")
    @JacksonXmlElementWrapper(useWrapping = false)
    List<Study> studies;
}
