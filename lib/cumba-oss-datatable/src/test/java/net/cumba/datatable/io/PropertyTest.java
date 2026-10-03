package net.cumba.datatable.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Property}. Exercises the static factory methods, the Lombok-generated
 * {@code withXxx} accessors, and the {@code toBuilder} path.
 */
class PropertyTest
{

    @Test
    void testForString()
    {
        Property p = Property.forString("name", "desc", "default");
        assertEquals("name", p.name());
        assertEquals("desc", p.description());
        assertEquals(PropertyType.STRING, p.type());
        assertEquals("default", p.defaultValue());
        assertNull(p.allowedValues());
        assertFalse(p.editable());
        assertNull(p.dependsOn());
        assertNull(p.conditionalAllowedValues());
        assertNull(p.dynamicResolverKey());
    }


    @Test
    void testForBoolean()
    {
        Property pTrue = Property.forBoolean("flag", "desc", true);
        Property pFalse = Property.forBoolean("flag", "desc", false);
        assertEquals(PropertyType.BOOLEAN, pTrue.type());
        assertEquals("true", pTrue.defaultValue());
        assertEquals("false", pFalse.defaultValue());
    }


    @Test
    void testForInteger()
    {
        Property p = Property.forInteger("count", "desc", 42L);
        assertEquals(PropertyType.INTEGER, p.type());
        assertEquals("42", p.defaultValue());
    }


    @Test
    void testForNumber()
    {
        Property p = Property.forNumber("value", "desc", 3.14);
        assertEquals(PropertyType.NUMBER, p.type());
        assertEquals("3.14", p.defaultValue());
    }


    @Test
    void testForOneOfVarargs()
    {
        Property p = Property.forOneOf("pick", "desc", "a", "a", "b", "c");
        assertEquals(PropertyType.ONE_OF, p.type());
        assertEquals(List.of("a", "b", "c"), p.allowedValues());
        assertFalse(p.editable());
    }


    @Test
    void testForOneOfList()
    {
        List<String> options = List.of("x", "y");
        Property p = Property.forOneOf("pick", "desc", "x", options);
        assertEquals(PropertyType.ONE_OF, p.type());
        assertEquals(options, p.allowedValues());
        assertFalse(p.editable());
    }


    @Test
    void testForOneOfOrCustomVarargs()
    {
        Property p = Property.forOneOfOrCustom("pick", "desc", "a", "a", "b");
        assertEquals(PropertyType.ONE_OF, p.type());
        assertTrue(p.editable(), "forOneOfOrCustom must produce an editable property");
    }


    @Test
    void testForOneOfOrCustomList()
    {
        Property p = Property.forOneOfOrCustom("pick", "desc", "a", List.of("a", "b"));
        assertEquals(PropertyType.ONE_OF, p.type());
        assertTrue(p.editable());
    }


    @Test
    void testForSomeOfVarargs()
    {
        Property p = Property.forSomeOf("pick", "desc", "a,b", "a", "b", "c");
        assertEquals(PropertyType.SOME_OF, p.type());
        assertEquals("a,b", p.defaultValue());
        assertEquals(List.of("a", "b", "c"), p.allowedValues());
    }


    @Test
    void testForSomeOfList()
    {
        Property p = Property.forSomeOf("pick", "desc", "a", List.of("a", "b"));
        assertEquals(PropertyType.SOME_OF, p.type());
    }


    @Test
    void testForAnyOfVarargs()
    {
        Property p = Property.forAnyOf("pick", "desc", "", "x", "y");
        assertEquals(PropertyType.ANY_OF, p.type());
        assertEquals(List.of("x", "y"), p.allowedValues());
    }


    @Test
    void testForAnyOfList()
    {
        Property p = Property.forAnyOf("pick", "desc", "", List.of("x", "y"));
        assertEquals(PropertyType.ANY_OF, p.type());
    }


    @Test
    void testForPassword()
    {
        Property p = Property.forPassword("pw", "desc", "secret");
        assertEquals(PropertyType.PASSWORD, p.type());
        assertEquals("secret", p.defaultValue());
    }


    @Test
    void testForDirectory()
    {
        Property p = Property.forDirectory("dir", "desc", "/tmp");
        assertEquals(PropertyType.DIRECTORY, p.type());
        assertEquals("/tmp", p.defaultValue());
    }


    @Test
    void testForFile()
    {
        Property p = Property.forFile("file", "desc", "file:///tmp/a.csv");
        assertEquals(PropertyType.FILE, p.type());
        assertEquals("file:///tmp/a.csv", p.defaultValue());
    }


    @Test
    void testForFiles()
    {
        Property p = Property.forFiles("files", "desc", "");
        assertEquals(PropertyType.FILES, p.type());
        assertEquals("", p.defaultValue());
    }


    @Test
    void testForSomeOrAllOff()
    {
        Property p = Property.forSomeOrAllOff("pick", "desc", "", "key-1");
        assertEquals(PropertyType.SOME_OR_ALL_OF, p.type());
        assertEquals("key-1", p.dynamicResolverKey());
        assertNull(p.allowedValues(),
                "SOME_OR_ALL_OF uses dynamicResolverKey rather than a static list");
    }


    @Test
    void testBuilderWithAllFields()
    {
        Property p = Property.builder()//
                .name("custom")//
                .description("desc")//
                .type(PropertyType.ONE_OF)//
                .defaultValue("a")//
                .allowedValues(List.of("a", "b"))//
                .editable(true)//
                .dependsOn("controller")//
                .conditionalAllowedValues(java.util.Map.of("ctlA", List.of("a")))//
                .dynamicResolverKey("k")//
                .build();
        assertNotNull(p);
        assertEquals("controller", p.dependsOn());
        assertEquals(List.of("a"), p.conditionalAllowedValues().get("ctlA"));
        assertEquals("k", p.dynamicResolverKey());
    }


    @Test
    void testWithDefaultValueGeneratesCopy()
    {
        Property original = Property.forString("n", "d", "orig");
        Property modified = original.withDefaultValue("new");
        assertEquals("orig", original.defaultValue(), "Original must remain unchanged");
        assertEquals("new", modified.defaultValue());
        assertEquals(original.name(), modified.name());
    }


    @Test
    void testToBuilderRoundtrip()
    {
        Property original = Property.forOneOf("pick", "desc", "a", "a", "b");
        Property copy = original.toBuilder().build();
        assertEquals(original, copy);
    }


    @Test
    void testEqualsAndHashCode()
    {
        Property p1 = Property.forString("a", "b", "c");
        Property p2 = Property.forString("a", "b", "c");
        assertEquals(p1, p2);
        assertEquals(p1.hashCode(), p2.hashCode());
    }


    @Test
    void theTypeEnumerationIsComplete()
    {
        // A dropped constant would silently change how a UI renders a ported property; the
        // dataviewer ships exactly these twelve.
        assertEquals(
                List.of("STRING", "INTEGER", "NUMBER", "BOOLEAN", "ONE_OF", "SOME_OF", "ANY_OF",
                        "FILE", "DIRECTORY", "PASSWORD", "FILES", "SOME_OR_ALL_OF"),
                List.of(PropertyType.values()).stream().map(Enum::name).toList());
        assertEquals(PropertyType.STRING, PropertyType.valueOf("STRING"));
    }
}
