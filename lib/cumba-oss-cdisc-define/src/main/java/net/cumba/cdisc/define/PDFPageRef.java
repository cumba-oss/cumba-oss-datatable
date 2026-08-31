package net.cumba.cdisc.define;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

// PDFPageRef
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class PDFPageRef
{

    @JacksonXmlProperty(isAttribute = true, localName = "PageRefs")
    String pageRefs;

    @JacksonXmlProperty(isAttribute = true, localName = "Type")
    String type;

    @JacksonXmlProperty(isAttribute = true, localName = "FirstPage")
    Integer firstPage;

    @JacksonXmlProperty(isAttribute = true, localName = "LastPage")
    Integer lastPage;

    @JacksonXmlProperty(isAttribute = true, localName = "Title")
    String title;
}
