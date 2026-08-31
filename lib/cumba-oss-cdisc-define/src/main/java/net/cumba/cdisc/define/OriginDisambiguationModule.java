package net.cumba.cdisc.define;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.deser.std.DelegatingDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import java.io.IOException;

/**
 * Jackson module that lets a Define-XML {@link ItemDef} carry both the v2.0 {@code Origin}
 * <em>attribute</em> and the v2.1 {@code <def:Origin Type="…"/>} <em>element</em>, which share the
 * local name {@code "Origin"}.
 *
 * <p>
 * Jackson-XML matches incoming attributes and elements purely by local name (ignoring the
 * attribute/element and namespace distinction), so two bean properties named {@code "Origin"}
 * collide and only one survives — historically the {@code ItemDef.getOrigin()} String attribute,
 * which made a v2.1 {@code <def:Origin>} element bind as empty text and dropped its {@code @Type}.
 * To break the collision {@code originElement} is bound under the synthetic name
 * {@link ItemDef#ORIGIN_ELEMENT_PROPERTY} (which never occurs in real Define-XML), and this module
 * redirects the element-form {@code Origin} node to it.
 * </p>
 *
 * <p>
 * The module wraps {@code ItemDef}'s deserializer: it reads the element into a tree, and when the
 * {@code "Origin"} node is an <em>object</em> (i.e. the v2.1 element, whose
 * {@code @Type}/{@code @Source} attributes and children became fields) it moves that node to the
 * synthetic property so it binds to {@code ItemDef.getOriginElement()}. A <em>textual</em>
 * {@code "Origin"} node (the v2.0 attribute value) is left in place and still binds to the
 * {@code String origin} property. The rewritten tree is then materialised by a private
 * {@link XmlMapper} that does <em>not</em> carry this module (so there is no recursion) but does
 * enable {@link DeserializationFeature#ACCEPT_SINGLE_VALUE_AS_ARRAY} so unwrapped single-element
 * lists (e.g. a lone {@code TranslatedText}) still deserialize from the JSON tree.
 * </p>
 */
final class OriginDisambiguationModule extends SimpleModule
{

    private static final long serialVersionUID = 1L;

    OriginDisambiguationModule()
    {
        // A plain XmlMapper WITHOUT this module: used to materialise the rewritten tree without
        // re-entering the wrapping deserializer (no infinite recursion).
        // ACCEPT_SINGLE_VALUE_AS_ARRAY compensates for the XML unwrapped-list handling that the
        // tree
        // round-trip would otherwise lose (e.g. a lone TranslatedText).
        XmlMapper treeMapper = new XmlMapper();
        treeMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        treeMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        setDeserializerModifier(new OriginModifier(treeMapper));
    }

    /** Wraps {@link ItemDef}'s builder deserializer with {@link ItemDefOriginDeserializer}. */
    private static final class OriginModifier extends BeanDeserializerModifier
    {

        private static final long serialVersionUID = 1L;

        private final XmlMapper treeMapper;

        OriginModifier(XmlMapper aTreeMapper)
        {
            this.treeMapper = aTreeMapper;
        }


        @Override
        public JsonDeserializer<?> modifyDeserializer(DeserializationConfig config,
                BeanDescription beanDesc, JsonDeserializer<?> deserializer)
        {
            if (beanDesc.getBeanClass() == ItemDef.ItemDefBuilder.class)
            {
                return new ItemDefOriginDeserializer(deserializer, treeMapper);
            }
            return deserializer;
        }
    }


    /**
     * Wraps the generated {@link ItemDef} deserializer and redirects the element-form
     * {@code Origin} node to {@link ItemDef#ORIGIN_ELEMENT_PROPERTY} before materialising the bean.
     */
    private static final class ItemDefOriginDeserializer extends DelegatingDeserializer
    {

        private static final long serialVersionUID = 1L;

        private final XmlMapper treeMapper;

        ItemDefOriginDeserializer(JsonDeserializer<?> aDelegate, XmlMapper aTreeMapper)
        {
            super(aDelegate);
            this.treeMapper = aTreeMapper;
        }


        @Override
        protected JsonDeserializer<?> newDelegatingInstance(JsonDeserializer<?> aNewDelegatee)
        {
            return new ItemDefOriginDeserializer(aNewDelegatee, treeMapper);
        }


        @Override
        public Object deserialize(JsonParser aParser, DeserializationContext aCtxt)
            throws IOException
        {
            ObjectMapper mapper = (ObjectMapper) aParser.getCodec();
            ObjectNode node = mapper.readTree(aParser);
            if (node.path("Origin").getNodeType() == JsonNodeType.OBJECT)
            {
                node.set(ItemDef.ORIGIN_ELEMENT_PROPERTY, node.remove("Origin"));
            }
            return treeMapper.treeToValue(node, ItemDef.class);
        }
    }
}
