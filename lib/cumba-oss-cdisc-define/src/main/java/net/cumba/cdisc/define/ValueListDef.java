package net.cumba.cdisc.define;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

// ValueListDef
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class ValueListDef
{

    @JacksonXmlProperty(isAttribute = true, localName = "OID")
    String oid;

    @JacksonXmlProperty(isAttribute = true, localName = "CommentOID")
    String commentOID;

    @JacksonXmlProperty(localName = "ItemRef")
    @JacksonXmlElementWrapper(useWrapping = false)
    List<ItemRef> itemRefs;
}
