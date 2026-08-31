package net.cumba.cdisc.define;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Deserialization tests for the {@code Origin} attribute (Define-XML v1.0) vs the {@code
 * <def:Origin Type="…"/>} element (Define-XML v2.0/v2.1). Both share the local name {@code
 * "Origin"} on {@link ItemDef}; {@link OriginDisambiguationModule} keeps them from colliding.
 */
class ItemDefOriginBindingTest
{

    private final DefineXmlParser parser = new DefineXmlParser();

    private ItemDef item(ODM odm, String oid)
    {
        List<ItemDef> defs = odm.getStudies().get(0).getMetaDataVersions().get(0).getItemDefs();
        return defs.stream().filter(d -> oid.equals(d.getOid())).findFirst().orElseThrow();
    }


    private ODM parseResource(String path) throws IOException
    {
        try (InputStream in = getClass().getResourceAsStream(path))
        {
            assertNotNull(in, "missing test resource " + path);
            return parser.parse(in);
        }
    }


    @Test
    void v21ElementOriginResolvesTypeAndSource() throws IOException
    {
        ODM odm = parseResource("/convert/define-v21-sdtm.xml");
        ItemDef def = item(odm, "IT.AE.STUDYID");

        assertNotNull(def.getOriginElement(),
                "v2.1 <def:Origin> element must populate originElement");
        assertEquals("Protocol", def.getOriginElement().getType());
        assertEquals("Sponsor", def.getOriginElement().getSource());
        // No v2.x @Origin attribute, so the String form stays null.
        assertNull(def.getOrigin());
    }


    @Test
    void v21ElementOriginWithNestedChildren() throws IOException
    {
        ODM odm = parseResource("/convert/define-v21-sdtm.xml");
        // IT.AE.AELNKID carries <def:Origin Type="Collected"> with a nested def:DocumentRef.
        ItemDef def = item(odm, "IT.AE.AELNKID");

        assertNotNull(def.getOriginElement());
        assertEquals("Collected", def.getOriginElement().getType());
        assertEquals("Investigator", def.getOriginElement().getSource());
        assertNotNull(def.getOriginElement().getDocumentRefs());
        assertEquals(1, def.getOriginElement().getDocumentRefs().size());
    }


    @Test
    void v20ElementOriginResolvesType() throws IOException
    {
        ODM odm = parseResource("/convert/define-v20-sdtm.xml");
        ItemDef def = item(odm, "IT.AE.DOMAIN");

        assertNotNull(def.getOriginElement(),
                "v2.0 <def:Origin> element must populate originElement");
        assertEquals("Assigned", def.getOriginElement().getType());
    }


    @Test
    void v10AttributeOriginStillResolves() throws IOException
    {
        ODM odm = parseResource("/convert/define-v10-sdtm.xml");
        // Define-XML 1.0 carries Origin as a plain attribute on ItemDef.
        ItemDef def = item(odm, "AE.DOMAIN");

        assertEquals("Assigned", def.getOrigin(), "v1.0 @Origin attribute must populate origin");
        assertNull(def.getOriginElement(), "attribute form must not create an Origin element");
    }


    @Test
    void syntheticAttributeAndElementFormsAreIndependent() throws IOException
    {
        String attrXml = "<ODM><Study><MetaDataVersion>"
                + "<ItemDef OID=\"IT.X\" Name=\"X\" DataType=\"text\" Origin=\"Assigned\"/>"
                + "</MetaDataVersion></Study></ODM>";
        ItemDef attrDef = item(parser.parse(attrXml), "IT.X");
        assertEquals("Assigned", attrDef.getOrigin());
        assertNull(attrDef.getOriginElement());

        String elemXml = "<ODM xmlns:def=\"http://www.cdisc.org/ns/def/v2.1\">"
                + "<Study><MetaDataVersion>" + "<ItemDef OID=\"IT.Y\" Name=\"Y\" DataType=\"text\">"
                + "<def:Origin Type=\"Collected\" Source=\"Investigator\"/>"
                + "</ItemDef></MetaDataVersion></Study></ODM>";
        ItemDef elemDef = item(parser.parse(elemXml), "IT.Y");
        assertNull(elemDef.getOrigin());
        assertNotNull(elemDef.getOriginElement());
        assertEquals("Collected", elemDef.getOriginElement().getType());
        assertEquals("Investigator", elemDef.getOriginElement().getSource());
    }
}
