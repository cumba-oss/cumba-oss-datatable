package net.cumba.cdisc.define;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

// Protocol
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class Protocol
{

    @JacksonXmlProperty(localName = "StudyEventRef")
    @JacksonXmlElementWrapper(useWrapping = false)
    List<StudyEventRef> studyEventRefs;

    @JacksonXmlProperty(localName = "Alias")
    @JacksonXmlElementWrapper(useWrapping = false)
    List<Alias> aliases;
}
