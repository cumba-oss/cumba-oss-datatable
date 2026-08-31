package net.cumba.cdisc.define;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link ItemGroupClassDisambiguationModule}: the Define-XML 2.0 {@code def:Class}
 * <em>attribute</em> and the 2.1 {@code <def:Class Name="…">} <em>element</em> (with
 * {@code <def:SubClass>} children) share the local name {@code "Class"}; both forms must populate
 * {@link ItemGroupDef#getEffectiveClassName()} / {@link ItemGroupDef#getSubClassNames()}. Before
 * the module existed, a native 2.1 define yielded a {@code null} class for every dataset (the
 * element bound as empty text into the attribute property).
 */
class ItemGroupClassDisambiguationTest
{

    private DefineXmlParser parser;

    @BeforeEach
    void setUp()
    {
        parser = new DefineXmlParser();
    }


    private static ItemGroupDef firstItemGroup(ODM odm)
    {
        return odm.getStudies().getFirst().getMetaDataVersions().getFirst().getItemGroupDefs()
                .getFirst();
    }


    private static String wrap(String itemGroup)
    {
        return """
                <ODM xmlns="http://www.cdisc.org/ns/odm/v1.3"
                     xmlns:def="http://www.cdisc.org/ns/def/v2.1" FileOID="test">
                  <Study OID="ST.1">
                    <MetaDataVersion OID="MDV.1" Name="MDV">
                """ + itemGroup + """
                    </MetaDataVersion>
                  </Study>
                </ODM>
                """;
    }


    @Test
    void attributeForm_v20_stillBinds() throws IOException
    {
        ODM odm = parser.parse(wrap("""
                <ItemGroupDef OID="IG.AE" Name="AE" Repeating="Yes" def:Class="EVENTS"/>
                """));
        ItemGroupDef igd = firstItemGroup(odm);
        assertEquals("EVENTS", igd.getClazz());
        assertEquals("EVENTS", igd.getEffectiveClassName());
        assertTrue(igd.getSubClassNames().isEmpty());
    }


    @Test
    void elementForm_v21_bindsNameAndSubClasses() throws IOException
    {
        ODM odm = parser.parse(wrap("""
                <ItemGroupDef OID="IG.ADTTE" Name="ADTTE" Repeating="Yes">
                  <def:Class Name="BASIC DATA STRUCTURE">
                    <def:SubClass Name="TIME-TO-EVENT"/>
                  </def:Class>
                </ItemGroupDef>
                """));
        ItemGroupDef igd = firstItemGroup(odm);
        assertNotNull(igd.getClassElement());
        assertEquals("BASIC DATA STRUCTURE", igd.getEffectiveClassName());
        assertEquals(List.of("TIME-TO-EVENT"), igd.getSubClassNames());
    }


    @Test
    void elementForm_multipleSubClasses() throws IOException
    {
        ODM odm = parser.parse(wrap("""
                <ItemGroupDef OID="IG.X" Name="ADX" Repeating="Yes">
                  <def:Class Name="OCCURRENCE DATA STRUCTURE">
                    <def:SubClass Name="ADVERSE EVENT"/>
                    <def:SubClass Name="TIME-TO-EVENT" ParentClass="BASIC DATA STRUCTURE"/>
                  </def:Class>
                </ItemGroupDef>
                """));
        ItemGroupDef igd = firstItemGroup(odm);
        assertEquals(List.of("ADVERSE EVENT", "TIME-TO-EVENT"), igd.getSubClassNames());
        assertEquals("BASIC DATA STRUCTURE",
                igd.getClassElement().getSubClasses().get(1).getParentClass());
    }


    @Test
    void elementForm_noSubClasses() throws IOException
    {
        ODM odm = parser.parse(wrap("""
                <ItemGroupDef OID="IG.ADSL" Name="ADSL" Repeating="No">
                  <def:Class Name="SUBJECT LEVEL ANALYSIS DATASET"/>
                </ItemGroupDef>
                """));
        ItemGroupDef igd = firstItemGroup(odm);
        assertEquals("SUBJECT LEVEL ANALYSIS DATASET", igd.getEffectiveClassName());
        assertTrue(igd.getSubClassNames().isEmpty());
    }


    @Test
    void noClassAtAll() throws IOException
    {
        ODM odm = parser.parse(wrap("""
                <ItemGroupDef OID="IG.X" Name="X" Repeating="Yes"/>
                """));
        ItemGroupDef igd = firstItemGroup(odm);
        assertNull(igd.getEffectiveClassName());
        assertTrue(igd.getSubClassNames().isEmpty());
    }


    @Test
    void realWorldSample_dataExchangeAdamDefine() throws IOException
    {
        // The repo's real Define-2.1 ADaM example (if present in this environment) — ADTTE
        // declares BASIC DATA STRUCTURE / TIME-TO-EVENT via the element form.
        java.io.File sample = new java.io.File(
                "/data/testdata/DataExchange-DatasetJson/adam/define.xml");
        org.junit.jupiter.api.Assumptions.assumeTrue(sample.isFile());
        ODM odm = parser.parse(sample);
        ItemGroupDef adtte = odm.getStudies().getFirst().getMetaDataVersions().getFirst()
                .getItemGroupDefs().stream().filter(g -> "ADTTE".equals(g.getName())).findFirst()
                .orElseThrow();
        assertEquals("BASIC DATA STRUCTURE", adtte.getEffectiveClassName());
        assertEquals(List.of("TIME-TO-EVENT"), adtte.getSubClassNames());
    }

}
