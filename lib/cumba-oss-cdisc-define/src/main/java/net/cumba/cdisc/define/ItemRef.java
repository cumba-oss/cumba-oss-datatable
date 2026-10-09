package net.cumba.cdisc.define;

// OSS-IDENTITY cdisc-define-codelist: byte-identical in cumba-cdisc and cumba-oss-datatable.

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

// ItemRef
@Value
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class ItemRef
{

    @JacksonXmlProperty(isAttribute = true, localName = "ItemOID")
    String itemOID;

    @JacksonXmlProperty(isAttribute = true, localName = "OrderNumber")
    Integer orderNumber;

    @JacksonXmlProperty(isAttribute = true, localName = "Mandatory")
    String mandatory;

    @JacksonXmlProperty(isAttribute = true, localName = "KeySequence")
    Integer keySequence;

    @JacksonXmlProperty(isAttribute = true, localName = "MethodOID")
    String methodOID;

    @JacksonXmlProperty(isAttribute = true, localName = "Role")
    String role;

    @JacksonXmlProperty(isAttribute = true, localName = "RoleCodeListOID")
    String roleCodeListOID;

    @JacksonXmlProperty(isAttribute = true, localName = "HasNoData")
    String hasNoData;

    /**
     * {@code def:IsNonStandard="Yes"} (Define-XML 2.1 §4.2.2): a variable that is not part of the
     * dataset's definition in the declared standard — a sponsor-added variable. The schema allows
     * only {@code "Yes"} (odm:YesOnly); absent means standard.
     */
    @JacksonXmlProperty(isAttribute = true, localName = "IsNonStandard")
    String isNonStandard;

    @JacksonXmlProperty(localName = "WhereClauseRef")
    @JacksonXmlElementWrapper(useWrapping = false)
    List<WhereClauseRef> whereClauseRefs;
}
