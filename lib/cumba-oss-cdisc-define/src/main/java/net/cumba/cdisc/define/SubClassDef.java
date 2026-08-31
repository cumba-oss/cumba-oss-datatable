package net.cumba.cdisc.define;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Define-XML 2.1 {@code <def:SubClass Name="…" ParentClass="…"/>} — a subclass refinement of an
 * {@link ClassDef ItemGroup class} (vocabulary: the {@code ItemGroupSubClass} XSD enumeration, e.g.
 * {@code TIME-TO-EVENT}, {@code ADVERSE EVENT}).
 */
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubClassDef
{

    @JacksonXmlProperty(isAttribute = true, localName = "Name")
    String name;

    @JacksonXmlProperty(isAttribute = true, localName = "ParentClass")
    String parentClass;

}
