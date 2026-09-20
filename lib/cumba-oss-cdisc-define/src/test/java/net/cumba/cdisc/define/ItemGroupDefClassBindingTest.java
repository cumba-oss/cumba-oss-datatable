package net.cumba.cdisc.define;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Binding tests for the {@code def:Class} <em>attribute</em> (Define-XML v2.0) versus the
 * {@code <def:Class Name="…">} <em>element</em> with {@code <def:SubClass>} children (v2.1). Both
 * share the local name {@code "Class"} on {@link ItemGroupDef};
 * {@link ItemGroupClassDisambiguationModule} keeps them from colliding, exactly as
 * {@link OriginDisambiguationModule} does for {@code ItemDef}'s {@code Origin}.
 *
 * <p>
 * Before that module existed, the element form bound as empty text into the {@code String clazz}
 * attribute property and its {@code Name} was dropped — so a native-2.1 define yielded a
 * {@code null} class for <b>every</b> dataset. {@link #v21ElementFormPopulatesEveryDataset()} is
 * the regression test for precisely that.
 * </p>
 */
class ItemGroupDefClassBindingTest
{

    private final DefineXmlParser parser = new DefineXmlParser();

    private static List<ItemGroupDef> groups(ODM aOdm)
    {
        return aOdm.getStudies().get(0).getMetaDataVersions().get(0).getItemGroupDefs();
    }


    private static ItemGroupDef group(ODM aOdm, String aOid)
    {
        return groups(aOdm).stream().filter(g -> aOid.equals(g.getOid())).findFirst().orElseThrow();
    }


    private ODM parseResource(String aPath) throws IOException
    {
        try (InputStream in = getClass().getResourceAsStream(aPath))
        {
            assertNotNull(in, "missing test resource " + aPath);
            return parser.parse(in);
        }
    }

    // ===== real documents =====


    /** The regression case: a native 2.1 define must not yield a null class for any dataset. */
    @Test
    void v21ElementFormPopulatesEveryDataset() throws IOException
    {
        ODM odm = parseResource("/convert/define-v21-sdtm.xml");

        for (ItemGroupDef g : groups(odm))
        {
            assertNotNull(g.getEffectiveClassName(),
                    "2.1 <def:Class> element must resolve for " + g.getOid());
            assertNull(g.getClazz(),
                    "the 2.0 attribute is absent in a native 2.1 document: " + g.getOid());
        }

        assertEquals("TRIAL DESIGN", group(odm, "IG.TA").getEffectiveClassName());
        assertEquals("SPECIAL PURPOSE", group(odm, "IG.DM").getEffectiveClassName());
        assertEquals("INTERVENTIONS", group(odm, "IG.CM").getEffectiveClassName());
    }


    /** The 2.0 attribute form must keep working unchanged. */
    @Test
    void v20AttributeFormStillResolves() throws IOException
    {
        ODM odm = parseResource("/convert/define-v20-sdtm.xml");

        ItemGroupDef ta = group(odm, "IG.TA");
        assertEquals("TRIAL DESIGN", ta.getClazz(), "the 2.0 attribute must still bind");
        assertEquals("TRIAL DESIGN", ta.getEffectiveClassName());
        assertTrue(ta.getSubClassNames().isEmpty(), "2.0 has no subclass concept");
    }


    /**
     * Parsing a 2.1 document must not disturb the rest of {@link ItemGroupDef} — the disambiguation
     * module rewrites the tree and re-materialises the bean, so an unwrapped list such as
     * {@code ItemRef} is the thing most at risk.
     */
    @Test
    void v21ParseKeepsTheRestOfTheItemGroupIntact() throws IOException
    {
        ODM odm = parseResource("/convert/define-v21-sdtm.xml");
        ItemGroupDef dm = group(odm, "IG.DM");

        assertEquals("DM", dm.getName());
        assertEquals("DM", dm.getDomain());
        assertNotNull(dm.getItemRefs());
        assertTrue(dm.getItemRefs().size() > 1,
                "the unwrapped ItemRef list must survive the tree round-trip");
    }

    // ===== the four combinations, at bean level =====


    @Test
    void attributeOnly()
    {
        ItemGroupDef g = ItemGroupDef.builder().oid("IG.AE").name("AE").clazz("EVENTS").build();

        assertEquals("EVENTS", g.getEffectiveClassName());
        assertTrue(g.getSubClassNames().isEmpty());
    }


    @Test
    void elementOnly()
    {
        ItemGroupDef g = ItemGroupDef.builder().oid("IG.ADTTE").name("ADTTE")
                .classElement(ClassDef.builder().name("BASIC DATA STRUCTURE")
                        .subClasses(List.of(SubClassDef.builder().name("TIME-TO-EVENT").build()))
                        .build())
                .build();

        assertEquals("BASIC DATA STRUCTURE", g.getEffectiveClassName());
        assertEquals(List.of("TIME-TO-EVENT"), g.getSubClassNames());
    }


    @Test
    void bothPresent_elementWins()
    {
        ItemGroupDef g = ItemGroupDef.builder().oid("IG.X").name("ADX").clazz("EVENTS")
                .classElement(ClassDef.builder().name("OCCURRENCE DATA STRUCTURE").build()).build();

        assertEquals("OCCURRENCE DATA STRUCTURE", g.getEffectiveClassName());
    }


    @Test
    void neitherPresent()
    {
        ItemGroupDef g = ItemGroupDef.builder().oid("IG.Y").name("Y").build();

        assertNull(g.getEffectiveClassName());
        assertTrue(g.getSubClassNames().isEmpty());
    }


    /**
     * An element with no usable name falls back to the attribute rather than returning "".
     *
     * <p>
     * ⚠ Whitespace counts as "no usable name". Testing only {@code isEmpty()} let
     * {@code <def:Class Name="   ">} beat a perfectly good 2.0 attribute, and the blank then failed
     * {@code cvc-enumeration-valid} on write — the schema restricts the name to the
     * {@code ItemGroupClass} enumeration.
     * </p>
     */
    @Test
    void unusableElementNameFallsBackToTheAttribute()
    {
        ItemGroupDef emptyName = ItemGroupDef.builder().oid("IG.Z").name("Z").clazz("EVENTS")
                .classElement(ClassDef.builder().name("").build()).build();
        assertEquals("EVENTS", emptyName.getEffectiveClassName());

        ItemGroupDef nullName = ItemGroupDef.builder().oid("IG.Z2").name("Z2").clazz("FINDINGS")
                .classElement(ClassDef.builder().build()).build();
        assertEquals("FINDINGS", nullName.getEffectiveClassName());

        ItemGroupDef whitespaceName = ItemGroupDef.builder().oid("IG.Z3").name("Z3")
                .clazz("INTERVENTIONS").classElement(ClassDef.builder().name("   ").build())
                .build();
        assertEquals("INTERVENTIONS", whitespaceName.getEffectiveClassName(),
                "a whitespace-only element name must not beat the 2.0 attribute");
    }


    /** A whitespace-only subclass name is not a subclass. */
    @Test
    void subClassNamesSkipWhitespaceOnlyEntries()
    {
        ItemGroupDef g = ItemGroupDef.builder().oid("IG.W").name("W")
                .classElement(ClassDef.builder().name("BASIC DATA STRUCTURE")
                        .subClasses(List.of(SubClassDef.builder().name("TIME-TO-EVENT").build(),
                                SubClassDef.builder().name("  ").build()))
                        .build())
                .build();

        assertEquals(List.of("TIME-TO-EVENT"), g.getSubClassNames());
    }


    @Test
    void subClassNamesSkipBlankAndNullEntries()
    {
        ItemGroupDef g = ItemGroupDef.builder().oid("IG.S").name("S").classElement(ClassDef
                .builder().name("BASIC DATA STRUCTURE")
                .subClasses(List.of(SubClassDef.builder().name("TIME-TO-EVENT").build(),
                        SubClassDef.builder().name("").build(), SubClassDef.builder().build()))
                .build()).build();

        assertEquals(List.of("TIME-TO-EVENT"), g.getSubClassNames());
    }


    @Test
    void subClassNamesEmptyWhenElementCarriesNone()
    {
        ItemGroupDef g = ItemGroupDef.builder().oid("IG.N").name("N")
                .classElement(ClassDef.builder().name("EVENTS").build()).build();

        assertTrue(g.getSubClassNames().isEmpty());
    }

    // ===== degenerate input =====


    /**
     * A degenerate {@code <ItemGroupDef/>} — no attributes, no children — binds to an all-null
     * {@link ItemGroupDef} and does <b>not</b> throw.
     *
     * <p>
     * ⚠ <b>This corrects a claim, which is why the test exists.</b> A review reported that such an
     * element parses as a {@code TextNode}, so {@link ItemGroupClassDisambiguationModule} — which
     * assigned the parsed tree straight to an {@code ObjectNode} — blew up with a bare
     * {@code ClassCastException}. That does <em>not</em> reproduce: Jackson-XML represents an empty
     * element as an <b>empty {@code ObjectNode}</b>, which binds to an empty bean. The unchecked
     * cast was real and is now a pattern match, but that is a <b>defensive</b> change — no input
     * was found that reaches the non-object branch.
     * </p>
     *
     * <p>
     * What is pinned here is therefore the actual contract: tolerant parsing, consistent with the
     * module's {@code FAIL_ON_UNKNOWN_PROPERTIES=false} stance — a malformed element yields an
     * empty bean rather than failing the whole document.
     * </p>
     */
    @Test
    void degenerateItemGroupDefBindsToAnEmptyBeanWithoutThrowing() throws IOException
    {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <ODM xmlns="http://www.cdisc.org/ns/odm/v1.3"\
                 xmlns:def="http://www.cdisc.org/ns/def/v2.1"\
                 ODMVersion="1.3.2" FileType="Snapshot" FileOID="X">
                <Study OID="S"><MetaDataVersion OID="MDV.1" Name="m">
                <ItemGroupDef/>
                </MetaDataVersion></Study></ODM>""";

        ODM odm = parser.parse(xml);
        List<ItemGroupDef> gs = groups(odm);

        assertEquals(1, gs.size());
        ItemGroupDef only = gs.get(0);
        assertNull(only.getOid());
        assertNull(only.getName());
        // The accessors this change added stay safe on a bean with nothing in it.
        assertNull(only.getEffectiveClassName());
        assertTrue(only.getSubClassNames().isEmpty());
    }


    /** The non-degenerate minimum — a single attribute — is an object and still binds. */
    @Test
    void anItemGroupDefWithOnlyAnAttributeStillBinds() throws IOException
    {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <ODM xmlns="http://www.cdisc.org/ns/odm/v1.3"\
                 xmlns:def="http://www.cdisc.org/ns/def/v2.1"\
                 ODMVersion="1.3.2" FileType="Snapshot" FileOID="X">
                <Study OID="S"><MetaDataVersion OID="MDV.1" Name="m">
                <ItemGroupDef OID="IG.ONLY"/>
                </MetaDataVersion></Study></ODM>""";

        ODM odm = parser.parse(xml);
        ItemGroupDef only = group(odm, "IG.ONLY");

        assertEquals("IG.ONLY", only.getOid());
        assertNull(only.getEffectiveClassName());
        assertTrue(only.getSubClassNames().isEmpty());
    }
}
