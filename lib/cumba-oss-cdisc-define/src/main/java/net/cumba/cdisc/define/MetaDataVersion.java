package net.cumba.cdisc.define;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

// MetaDataVersion
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class MetaDataVersion implements INamedElement
{

    @JacksonXmlProperty(isAttribute = true, localName = "OID")
    String oid;

    @JacksonXmlProperty(isAttribute = true, localName = "Name")
    String name;

    @JacksonXmlProperty(isAttribute = true, localName = "Description")
    String description;

    @JacksonXmlProperty(isAttribute = true, localName = "DefineVersion")
    String defineVersion;

    @JacksonXmlProperty(isAttribute = true, localName = "StandardName")
    String standardName;

    @JacksonXmlProperty(isAttribute = true, localName = "StandardVersion")
    String standardVersion;

    // def:CommentOID — Define-XML 2.1 metadata-version-level comment reference.
    @JacksonXmlProperty(isAttribute = true, localName = "CommentOID")
    String commentOID;

    @JacksonXmlProperty(localName = "Include")
    Include include;

    @JacksonXmlProperty(localName = "Protocol")
    Protocol protocol;

    @JacksonXmlProperty(localName = "StudyEventDef")
    @JacksonXmlElementWrapper(useWrapping = false)
    List<StudyEventDef> studyEventDefs;

    @JacksonXmlProperty(localName = "FormDef")
    @JacksonXmlElementWrapper(useWrapping = false)
    List<FormDef> formDefs;

    @JacksonXmlProperty(localName = "ItemGroupDef")
    @JacksonXmlElementWrapper(useWrapping = false)
    List<ItemGroupDef> itemGroupDefs;

    @JacksonXmlProperty(localName = "ItemDef")
    @JacksonXmlElementWrapper(useWrapping = false)
    List<ItemDef> itemDefs;

    @JacksonXmlProperty(localName = "CodeList")
    @JacksonXmlElementWrapper(useWrapping = false)
    List<CodeList> codeLists;

    @JacksonXmlProperty(localName = "MethodDef")
    @JacksonXmlElementWrapper(useWrapping = false)
    List<MethodDef> methodDefs;

    @JacksonXmlProperty(localName = "CommentDef")
    @JacksonXmlElementWrapper(useWrapping = false)
    List<CommentDef> commentDefs;

    @JacksonXmlProperty(localName = "WhereClauseDef")
    @JacksonXmlElementWrapper(useWrapping = false)
    List<WhereClauseDef> whereClauseDefs;

    @JacksonXmlProperty(localName = "ValueListDef")
    @JacksonXmlElementWrapper(useWrapping = false)
    List<ValueListDef> valueListDefs;

    @JacksonXmlProperty(localName = "AnnotatedCRF")
    AnnotatedCRF annotatedCRF;

    @JacksonXmlProperty(localName = "SupplementalDoc")
    SupplementalDoc supplementalDoc;

    @JacksonXmlProperty(localName = "leaf")
    @JacksonXmlElementWrapper(useWrapping = false)
    List<Leaf> leaves;

    @JacksonXmlProperty(localName = "Standards")
    @JacksonXmlElementWrapper(useWrapping = false)
    Standards standards;
}
