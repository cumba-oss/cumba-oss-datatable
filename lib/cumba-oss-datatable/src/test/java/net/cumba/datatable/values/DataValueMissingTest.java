package net.cumba.datatable.values;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class DataValueMissingTest
{

    @Test
    void testGetValue()
    {
        DataValueMissing dv = new DataValueMissing(MissingValue.MIS);
        assertSame(MissingValue.MIS, dv.getValue());
    }


    @Test
    void testConstructorWithInt()
    {
        DataValueMissing dv = new DataValueMissing(64); // MIS = 64
        assertSame(MissingValue.MIS, dv.getValue());
    }


    @Test
    void testConstructorWithInvalidInt()
    {
        DataValueMissing dv = new DataValueMissing(999);
        assertSame(MissingValue.MIS_ERROR, dv.getValue());
    }


    @Test
    void testGetType()
    {
        DataValueMissing dv = new DataValueMissing(MissingValue.NA);
        assertEquals(DataValueType.MISSING, dv.getType());
    }


    @Test
    void testGetValueAsDouble()
    {
        DataValueMissing dv = new DataValueMissing(MissingValue.MIS);
        assertTrue(Double.isNaN(dv.getValueAsDouble()));
    }


    @Test
    void testGetByteValue()
    {
        DataValueMissing dv = new DataValueMissing(MissingValue.MIS_A);
        assertEquals(65, dv.getByteValue());
    }


    @Test
    void testIsMissingOrInvalid()
    {
        DataValueMissing dv = new DataValueMissing(MissingValue.MIS);
        assertTrue(dv.isMissingOrInvalid());
    }


    @Test
    void testToString()
    {
        DataValueMissing dv = new DataValueMissing(MissingValue.MIS);
        assertEquals(".", dv.toString());
    }


    @Test
    void testToStringSasSpecial()
    {
        DataValueMissing dv = new DataValueMissing(MissingValue.MIS_A);
        assertEquals(".A", dv.toString());
    }


    @Test
    void testToStringNA()
    {
        DataValueMissing dv = new DataValueMissing(MissingValue.NA);
        assertEquals("NA", dv.toString());
    }


    @Test
    void testEquals()
    {
        DataValueMissing dv1 = new DataValueMissing(MissingValue.MIS);
        DataValueMissing dv2 = new DataValueMissing(MissingValue.MIS);
        DataValueMissing dv3 = new DataValueMissing(MissingValue.NA);

        assertEquals(dv1, dv2);
        assertNotEquals(dv1, dv3);
    }


    @Test
    void testBuilder()
    {
        DataValueMissing dv = DataValueMissing.builder().value(MissingValue.MIS_Z).build();
        assertSame(MissingValue.MIS_Z, dv.getValue());
    }


    @Test
    void testGetValueAsString()
    {
        DataValueMissing dv = new DataValueMissing(MissingValue.MIS);
        assertEquals(".", dv.getValueAsString());
    }


    /**
     * ⭐ <b>The target invariant, enforced at the one place an annotation can reach it.</b> An
     * {@link IDataValue}'s payload is a real value or a {@link MissingValue}, never {@code null} —
     * and {@code getValue()} is declared to return {@code Object}, so NullAway cannot see inside
     * it. Every other reference-typed implementation carries {@code @lombok.NonNull} on its value
     * field; this class did not, so a null argument silently produced a <em>null-carrying</em>
     * {@code IDataValue}: {@code getValue() == null}, {@code isMissingOrInvalid() == false} (the
     * default tests {@code instanceof MissingValue}) and {@code getValueAsString() == "null"}.
     *
     * <p>
     * ⚠ This is a deliberate <b>behaviour</b> change — a silent null becomes a thrown
     * {@link NullPointerException} — and it is the assertion that reds if the annotation is ever
     * dropped again.
     * </p>
     */
    @Test
    void aNullMissingValueIsRejectedRatherThanCarried()
    {
        assertThrows(NullPointerException.class, () -> new DataValueMissing((MissingValue) null));
    }


    /**
     * The same rejection through the Jackson-facing route. {@code @Jacksonized} deserialises this
     * class through its builder, so a {@code {"type":"missing"}} document with no {@code value}
     * property reaches {@code build()} with a null.
     *
     * <p>
     * ⚠ <b>Corrected 2026-09-18:</b> this javadoc used to call that "the one reachable production
     * path that could still mint a null-carrying cell". It is <b>not reachable from any document
     * this product writes</b> — see {@link #theSerialisedFormAlwaysCarriesItsValueProperty()} and
     * {@link #aMissingDocumentWithoutAValueFailsAsALocatableMappingError()}, and the evidence
     * recorded on {@code DataValueMissing.value}. It is reachable only from a hand-written or
     * foreign message, which is why the two tests below pin <em>how</em> it fails rather than
     * merely that it does.
     * </p>
     */
    @Test
    void aBuilderWithNoValueIsRejectedRatherThanCarried()
    {
        DataValueMissing.DataValueMissingBuilder b = DataValueMissing.builder();
        assertThrows(NullPointerException.class, b::build);
    }


    /**
     * ⭐ <b>Why the throw above is not a load-time regression:</b> a {@code missing} node this
     * product writes <b>always</b> carries its {@code value} property, so the document that would
     * hit the null check cannot be produced here. Pinned rather than argued, because the argument
     * rests on two things a later change could quietly move: Jackson's default inclusion (an
     * {@code @JsonInclude(NON_NULL)} anywhere in scope would start omitting the property) and
     * {@code TypedInterfaceSerializer}, the JSON-RPC wire serialiser, which writes <em>every</em>
     * non-{@code @JsonIgnore} bean property and emits an explicit null field for a null one.
     */
    @Test
    void theSerialisedFormAlwaysCarriesItsValueProperty() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(new DataValueMissing(MissingValue.MIS_A));

        assertTrue(json.contains("\"value\""), "a missing node must carry its value: " + json);
        assertTrue(json.contains("\"missing\""), "and its type discriminator: " + json);
        assertEquals(new DataValueMissing(MissingValue.MIS_A),
                mapper.readValue(json, IDataValue.class));
    }


    /**
     * ⭐ <b>And how it fails when a foreign message does carry that shape.</b> The {@code @NonNull}
     * check runs inside the builder's {@code build()}, which Jackson invokes, so the
     * {@link NullPointerException} is wrapped: both the property-absent and the explicit-null forms
     * surface as a {@link JsonMappingException} that names the type, the property and the source
     * location. That is the JSON-RPC layer's ordinary deserialisation-error channel — <b>not</b> an
     * unchecked exception escaping into the caller.
     *
     * <p>
     * ⛔ The assertion is on {@code JsonMappingException} rather than the narrower
     * {@code ValueInstantiationException} on purpose: only the property-absent form produces the
     * latter (measured), because the explicit-null form fails while binding the property rather
     * than while instantiating the builder. Pinning the narrow type would pin a Jackson internal.
     * </p>
     */
    @Test
    void aMissingDocumentWithoutAValueFailsAsALocatableMappingError()
    {
        ObjectMapper mapper = new ObjectMapper();

        for (String doc : new String[]
        {
                "{\"type\":\"missing\"}", "{\"type\":\"missing\",\"value\":null}"
        })
        {
            JsonMappingException ex = assertThrows(JsonMappingException.class,
                    () -> mapper.readValue(doc, IDataValue.class), doc);
            assertTrue(ex.getMessage().contains("value is marked non-null but is null"),
                    "the failure must name the property: " + ex.getMessage());
            assertNotNull(ex.getLocation(), "and must be locatable in the source document");
        }
    }
}
