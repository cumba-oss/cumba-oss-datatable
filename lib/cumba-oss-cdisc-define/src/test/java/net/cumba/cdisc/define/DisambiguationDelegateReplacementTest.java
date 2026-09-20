package net.cumba.cdisc.define;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * What happens to the two Define-XML disambiguating deserializers when Jackson swaps their
 * delegatee.
 *
 * <p>
 * Both {@link OriginDisambiguationModule} and {@link ItemGroupClassDisambiguationModule} wrap a
 * generated deserializer in a {@code DelegatingDeserializer} whose whole job is to move an
 * element-form node ({@code Origin}, {@code Class}) out of the way of the identically-named v2.0
 * attribute. Jackson does not keep the instance the modifier returned: during contextualization it
 * calls {@code replaceDelegatee}, and whatever <em>that</em> hands back is what parses the
 * document. If the replacement were the bare delegatee — or the wrapper minus its tree mapper — the
 * collision would come back and every {@code <def:Origin>} / {@code <def:Class>} element would bind
 * as empty text into the attribute property, exactly the defect the modules were written to fix.
 * Nothing would fail; the parse would just quietly answer null.
 * </p>
 *
 * <p>
 * This is unreachable through a normal parse, because Jackson replaces the delegatee only when the
 * delegate itself contextualizes differently. The deserializers are package-visible so it can be
 * driven directly.
 * </p>
 */
class DisambiguationDelegateReplacementTest
{

    /** A delegatee that is only ever identity-compared; these wrappers never call through. */
    private static final class StubDelegatee extends JsonDeserializer<Object>
    {

        @Override
        public Object deserialize(JsonParser aParser, DeserializationContext aCtxt)
        {
            throw new UnsupportedOperationException("not expected to be called");
        }
    }

    /**
     * Runs one XML snippet through the given deserializer the way Jackson would: a parser whose
     * codec is a plain XmlMapper, positioned on the first token.
     */
    private static Object parse(JsonDeserializer<?> aDeserializer, String aXml) throws IOException
    {
        XmlMapper codec = new XmlMapper();
        try (JsonParser parser = codec.createParser(aXml))
        {
            parser.nextToken();
            return aDeserializer.deserialize(parser, null);
        }
    }

    // ===== ItemDef / Origin =====


    @Test
    void originWrapperSurvivesDelegateeReplacement() throws IOException
    {
        StubDelegatee first = new StubDelegatee();
        StubDelegatee second = new StubDelegatee();
        OriginDisambiguationModule.ItemDefOriginDeserializer original = new OriginDisambiguationModule.ItemDefOriginDeserializer(
                first, OriginDisambiguationModule.newTreeMapper());

        JsonDeserializer<?> replacement = original.replaceDelegatee(second);

        assertInstanceOf(OriginDisambiguationModule.ItemDefOriginDeserializer.class, replacement,
                "the replacement must still be the wrapper; a bare delegatee here is how the"
                        + " Origin disambiguation silently stops happening");
        assertSame(second, replacement.getDelegatee(), "and it must adopt the new delegatee");
        assertSame(original, original.replaceDelegatee(first),
                "replacing a delegatee with itself is a no-op");

        ItemDef def = (ItemDef) parse(replacement,
                "<ItemDef OID=\"IT.DM.X\"><Origin Type=\"Collected\"/></ItemDef>");

        assertNotNull(def.getOriginElement(),
                "the replacement must still redirect the element-form Origin");
        assertEquals("Collected", def.getOriginElement().getType());
        assertNull(def.getOrigin(), "and must still leave the v2.0 attribute property untouched");
        assertEquals("IT.DM.X", def.getOid(), "the rest of the element still binds");
    }

    // ===== ItemGroupDef / Class =====


    @Test
    void classWrapperSurvivesDelegateeReplacement() throws IOException
    {
        StubDelegatee first = new StubDelegatee();
        StubDelegatee second = new StubDelegatee();
        ItemGroupClassDisambiguationModule.ItemGroupClassDeserializer original = new ItemGroupClassDisambiguationModule.ItemGroupClassDeserializer(
                first, ItemGroupClassDisambiguationModule.newTreeMapper());

        JsonDeserializer<?> replacement = original.replaceDelegatee(second);

        assertInstanceOf(ItemGroupClassDisambiguationModule.ItemGroupClassDeserializer.class,
                replacement,
                "the replacement must still be the wrapper; otherwise a native 2.1 define goes"
                        + " back to yielding a null class for every dataset");
        assertSame(second, replacement.getDelegatee(), "and it must adopt the new delegatee");
        assertSame(original, original.replaceDelegatee(first),
                "replacing a delegatee with itself is a no-op");

        ItemGroupDef group = (ItemGroupDef) parse(replacement,
                "<ItemGroupDef OID=\"IG.DM\"><Class Name=\"FINDINGS\"/></ItemGroupDef>");

        assertEquals("FINDINGS", group.getEffectiveClassName(),
                "the replacement must still redirect the element-form Class");
        assertNull(group.getClazz(), "and must still leave the v2.0 attribute property untouched");
        assertEquals("IG.DM", group.getOid(), "the rest of the element still binds");
    }
}
