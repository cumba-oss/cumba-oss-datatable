package net.cumba.cdisc.define;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

// Standard
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class Standard implements INamedElement
{

    @JacksonXmlProperty(isAttribute = true, localName = "OID")
    String oid;

    @JacksonXmlProperty(isAttribute = true, localName = "Name")
    String name;

    @JacksonXmlProperty(isAttribute = true, localName = "Type")
    String type;

    @JacksonXmlProperty(isAttribute = true, localName = "PublishingSet")
    String publishingSet;

    @JacksonXmlProperty(isAttribute = true, localName = "Version")
    String version;

    @JacksonXmlProperty(isAttribute = true, localName = "Status")
    String status;

    @JacksonXmlProperty(isAttribute = true, localName = "CommentOID")
    String commentOID;
}
