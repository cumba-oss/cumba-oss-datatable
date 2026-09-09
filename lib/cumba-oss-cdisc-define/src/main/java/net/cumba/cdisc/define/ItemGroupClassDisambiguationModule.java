package net.cumba.cdisc.define;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.deser.std.DelegatingDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import java.io.IOException;

/**
 * Jackson module that lets a Define-XML {@link ItemGroupDef} carry both the v2.0 {@code def:Class}
 * <em>attribute</em> and the v2.1 {@code <def:Class Name="…">} <em>element</em> (with optional
 * {@code <def:SubClass>} children), which share the local name {@code "Class"}.
 *
 * <p>
 * Same collision and same cure as {@link OriginDisambiguationModule} for {@code ItemDef}'s
 * {@code Origin}: Jackson-XML matches purely by local name, so the element form historically bound
 * as empty text into the {@code String clazz} attribute property and its {@code @Name} /
 * {@code SubClass} children were dropped — which is why a native 2.1 define yielded a {@code null}
 * class for every dataset. The module reads the {@code ItemGroupDef} subtree, and when the
 * {@code "Class"} node is an <em>object</em> (the v2.1 element) moves it to the synthetic
 * {@link ItemGroupDef#CLASS_ELEMENT_PROPERTY} so it binds to
 * {@code ItemGroupDef.getClassElement()}; a <em>textual</em> node (the v2.0 attribute) stays in
 * place. Consumers read the unified {@link ItemGroupDef#getEffectiveClassName()}.
 * </p>
 */
final class ItemGroupClassDisambiguationModule extends SimpleModule
{

    private static final long serialVersionUID = 1L;

    ItemGroupClassDisambiguationModule()
    {
        // A plain XmlMapper WITHOUT this module, to materialise the rewritten tree without
        // re-entering the wrapping deserializer. ACCEPT_SINGLE_VALUE_AS_ARRAY compensates for the
        // XML unwrapped-list handling the tree round-trip would otherwise lose (a lone ItemRef /
        // SubClass).
        XmlMapper treeMapper = new XmlMapper();
        treeMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        treeMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        setDeserializerModifier(new ClassModifier(treeMapper));
    }

    /**
     * Wraps {@link ItemGroupDef}'s builder deserializer with {@link ItemGroupClassDeserializer}.
     */
    private static final class ClassModifier extends BeanDeserializerModifier
    {

        private static final long serialVersionUID = 1L;

        private final XmlMapper treeMapper;

        ClassModifier(XmlMapper aTreeMapper)
        {
            this.treeMapper = aTreeMapper;
        }


        @Override
        public JsonDeserializer<?> modifyDeserializer(DeserializationConfig config,
                BeanDescription beanDesc, JsonDeserializer<?> deserializer)
        {
            if (beanDesc.getBeanClass() == ItemGroupDef.ItemGroupDefBuilder.class)
            {
                return new ItemGroupClassDeserializer(deserializer, treeMapper);
            }
            return deserializer;
        }
    }


    /**
     * Wraps the generated {@link ItemGroupDef} deserializer and redirects the element-form
     * {@code Class} node to {@link ItemGroupDef#CLASS_ELEMENT_PROPERTY} before materialising the
     * bean.
     */
    private static final class ItemGroupClassDeserializer extends DelegatingDeserializer
    {

        private static final long serialVersionUID = 1L;

        private final XmlMapper treeMapper;

        ItemGroupClassDeserializer(JsonDeserializer<?> aDelegate, XmlMapper aTreeMapper)
        {
            super(aDelegate);
            this.treeMapper = aTreeMapper;
        }


        @Override
        protected JsonDeserializer<?> newDelegatingInstance(JsonDeserializer<?> aNewDelegatee)
        {
            return new ItemGroupClassDeserializer(aNewDelegatee, treeMapper);
        }


        @Override
        public Object deserialize(JsonParser aParser, DeserializationContext aCtxt)
            throws IOException
        {
            ObjectMapper mapper = (ObjectMapper) aParser.getCodec();
            JsonNode tree = mapper.readTree(aParser);

            // Defensive: only an ObjectNode can carry a "Class" key to disambiguate.
            //
            // ⚠ NB. this guard replaced an unchecked assignment of readTree's result straight
            // to an ObjectNode. A review claimed a degenerate `<ItemGroupDef/>` parses as a
            // TextNode
            // and so threw ClassCastException there; that does NOT reproduce — Jackson-XML gives
            // an EMPTY ObjectNode for an empty element, which binds to an all-null bean (pinned
            // by an internal binding test). No input was found that reaches the fallback
            // below. The cast was genuinely unchecked, so the guard stays, but it is insurance
            // rather than a fix for an observed failure.
            if (tree instanceof ObjectNode node)
            {
                if (node.path("Class").getNodeType() == JsonNodeType.OBJECT)
                {
                    node.set(ItemGroupDef.CLASS_ELEMENT_PROPERTY, node.remove("Class"));
                }
                return treeMapper.treeToValue(node, ItemGroupDef.class);
            }
            return treeMapper.treeToValue(tree, ItemGroupDef.class);
        }
    }
}
