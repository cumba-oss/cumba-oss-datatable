package net.cumba.cdisc.define;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Define-XML 2.1 {@code <def:Class Name="…">} element of an {@code ItemGroupDef}, optionally
 * carrying {@code <def:SubClass>} children — the element form that replaced the Define-XML 2.0
 * {@code def:Class} <em>attribute</em>. The name vocabulary is the {@code ItemGroupClass} XSD
 * enumeration (e.g. {@code BASIC DATA STRUCTURE}, {@code EVENTS}).
 *
 * <p>
 * Bound to {@code ItemGroupDef.getClassElement()} via the synthetic property redirected by
 * {@link ItemGroupClassDisambiguationModule} (the attribute and element share the local name
 * {@code "Class"}, which Jackson-XML cannot disambiguate on its own — same collision and cure as
 * {@code ItemDef}'s {@code Origin}).
 * </p>
 */
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClassDef
{

    @JacksonXmlProperty(isAttribute = true, localName = "Name")
    String name;

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "SubClass")
    List<SubClassDef> subClasses;

}
