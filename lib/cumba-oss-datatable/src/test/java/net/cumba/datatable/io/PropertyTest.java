package net.cumba.datatable.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Covers the {@link Property} factory set, the Lombok-generated builder/{@code with} surface, and
 * the {@link PropertyType} enumeration.
 *
 * <h2>This type is a PORT, and this test is its drift pin</h2>
 *
 * <p>
 * {@code Property} and {@code PropertyType} were copied <b>verbatim</b> — same package, same
 * components, same factories, same javadoc — from the upstream project's
 * {@code net.cumba.datatable.io} package. Only whitespace differs: the two projects'
 * {@code eclipse-formatter.xml} are not identical, so this project's Spotless reformats the copy.
 * </p>
 *
 * <p>
 * ⚠⚠ <b>Do not "improve" this type here.</b> Its whole reason for living at the original
 * coordinates is that the later {@code IDataTableProvider} migration can then be a pure add, with
 * no relocation and no second divergence. Anything worth changing is changed upstream first and
 * re-ported. In particular the values are always {@link String} — {@code forInteger} stores
 * {@code Long.toString} — because that string form is what crosses the JSON-RPC wire.
 * </p>
 */
class PropertyTest
{

    @Test
    void scalarFactoriesCarryTheirTypeAndStringifiedDefault()
    {
        Property text = Property.forString("name", "a name", "abc");
        assertEquals("name", text.name());
        assertEquals("a name", text.description());
        assertEquals(PropertyType.STRING, text.type());
        assertEquals("abc", text.defaultValue());
        assertNull(text.allowedValues());
        assertFalse(text.editable());
        assertNull(text.dependsOn());
        assertNull(text.conditionalAllowedValues());
        assertNull(text.dynamicResolverKey());
        assertNull(text.maxLength());

        assertEquals(PropertyType.BOOLEAN, Property.forBoolean("b", "d", true).type());
        assertEquals("true", Property.forBoolean("b", "d", true).defaultValue());
        assertEquals(PropertyType.INTEGER, Property.forInteger("i", "d", 10_000L).type());
        assertEquals("10000", Property.forInteger("i", "d", 10_000L).defaultValue(),
                "an integer default crosses the SPI as a string");
        assertEquals(PropertyType.NUMBER, Property.forNumber("n", "d", 1.5).type());
        assertEquals("1.5", Property.forNumber("n", "d", 1.5).defaultValue());
        assertEquals(PropertyType.PASSWORD, Property.forPassword("p", "d", "").type());
    }


    @Test
    void listFactoriesAcceptBothVarargsAndListForms()
    {
        assertEquals(List.of("a", "b"), Property.forOneOf("o", "d", "a", "a", "b").allowedValues());
        assertEquals(List.of("a", "b"),
                Property.forOneOf("o", "d", "a", List.of("a", "b")).allowedValues());
        assertEquals(PropertyType.ONE_OF, Property.forOneOf("o", "d", "a", "a").type());

        assertTrue(Property.forOneOfOrCustom("o", "d", "a", "a", "b").editable(),
                "the custom variant renders an editable combo box");
        assertTrue(Property.forOneOfOrCustom("o", "d", "a", List.of("a")).editable());
        assertFalse(Property.forOneOf("o", "d", "a", "a").editable());

        assertEquals(PropertyType.SOME_OF, Property.forSomeOf("s", "d", "", "a", "b").type());
        assertEquals(List.of("a"), Property.forSomeOf("s", "d", "", List.of("a")).allowedValues());
        assertEquals(PropertyType.ANY_OF, Property.forAnyOf("a", "d", "", "a", "b").type());
        assertEquals(List.of("a"), Property.forAnyOf("a", "d", "", List.of("a")).allowedValues());
    }


    @Test
    void pathFactoriesAndTheDynamicResolver()
    {
        assertEquals(PropertyType.DIRECTORY, Property.forDirectory("d", "d", "/tmp").type());
        assertEquals(PropertyType.FILE, Property.forFile("f", "d", "").type());
        assertEquals(PropertyType.FILES, Property.forFiles("f", "d", "").type());

        Property universe = Property.forSomeOrAllOff("u", "d", "", "tables");
        assertEquals(PropertyType.SOME_OR_ALL_OF, universe.type());
        assertEquals("tables", universe.dynamicResolverKey());
        assertNull(universe.allowedValues(), "the universe is resolved at runtime, not declared");
    }


    @Test
    void theBuilderAndWithSurfaceRoundTrip()
    {
        Property base = Property.builder().name("sheet").description("Sheet name")
                .type(PropertyType.STRING).defaultValue("Sheet1").allowedValues(List.of("Sheet1"))
                .editable(true).dependsOn("format")
                .conditionalAllowedValues(Map.of("xlsx", List.of("Sheet1", "Sheet2")))
                .dynamicResolverKey("sheets").maxLength(31).build();

        assertEquals("sheet", base.name());
        assertEquals(31, base.maxLength());
        assertEquals(List.of("Sheet1", "Sheet2"), base.conditionalAllowedValues().get("xlsx"));
        assertEquals(base, base.toBuilder().build(), "toBuilder round-trips every component");

        // maxLength is three-valued on purpose: null = undeclared, 0 = explicitly unbounded.
        assertNull(Property.forString("s", "d", "").maxLength());
        assertEquals(0, base.withMaxLength(0).maxLength());
        assertEquals("other", base.withName("other").name());
        assertEquals("sheet", base.name(), "with* returns a copy and leaves the original alone");
    }


    @Test
    void theTypeEnumerationIsComplete()
    {
        // A dropped constant would silently change how a UI renders a ported property; upstream
        // ships exactly these twelve.
        assertEquals(
                List.of("STRING", "INTEGER", "NUMBER", "BOOLEAN", "ONE_OF", "SOME_OF", "ANY_OF",
                        "FILE", "DIRECTORY", "PASSWORD", "FILES", "SOME_OR_ALL_OF"),
                List.of(PropertyType.values()).stream().map(Enum::name).toList());
        assertEquals(PropertyType.STRING, PropertyType.valueOf("STRING"));
    }
}
