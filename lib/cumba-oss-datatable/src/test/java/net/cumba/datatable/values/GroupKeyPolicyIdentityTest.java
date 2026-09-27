package net.cumba.datatable.values;

// OSS-IDENTITY datatable-key-identity: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.cumba.datatable.values.GroupKeyPolicy.Blankness;
import net.cumba.datatable.values.GroupKeyPolicy.KeyPart;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The key-identity case table of the shared type ({@code PLAN-shared-key-identity}), where the type
 * now lives: {@code JKM R5} (owner, 2026-09-21: <i>"a MIS will not join a record with an empty
 * string and a MIS_A will not join a record with a MIS or MIS_B."</i>) over <b>every</b>
 * {@link MissingValue} marker, and the owner's D2 (c) ruling that the identity object is a
 * <b>bijection</b> with {@link KeyPart}.
 *
 * <p>
 * Each case asserts two relations over the same two cells: the <b>typed</b> identity
 * ({@link GroupKeyPolicy#keyPart}, exact numbers, char {@code ≠} num — {@code D64h}, {@code D4-R1})
 * and the <b>text-join</b> identity ({@link GroupKeyPolicy#textKeyIdentity}, the RELREC link
 * identity — {@code D4-R5}, {@code RRK E2}). The consumers carry their own case tables through
 * their own sites (the engine's {@code RelrecMissingSubjectKeyTest}, the manager's
 * {@code RelrecMissingKeyIdentityTest}, the data browser's
 * {@code RelrecDialogMissingKeyIdentityTest}); this one pins the type they all classify through.
 * </p>
 *
 * <p>
 * ⚠ Real {@link IDataValue} objects only, never mocks: a mocked cell's unstubbed defaults would
 * answer the very classification under test.
 * </p>
 */
class GroupKeyPolicyIdentityTest
{

    private static final GroupKeyPolicy KEEP = GroupKeyPolicy.KEEP_MISSING_KEYS;

    /** A NaN whose payload decodes to no marker: the {@code forValue(..., MIS)} arm. */
    private static final double BARE_NAN = Double.NaN;

    private static final List<GroupKeyPolicy> POLICIES = List.of(GroupKeyPolicy.KEEP_MISSING_KEYS,
            GroupKeyPolicy.DROP_MISSING_KEYS, GroupKeyPolicy.FOLD_BLANK_KEYS,
            GroupKeyPolicy.COALESCE_COMPONENT);

    private static IDataValue str(String aValue)
    {
        return new DataValueString(aValue);
    }


    private static IDataValue mis(MissingValue aMarker)
    {
        return new DataValueMissing(aMarker);
    }


    private static IDataValue dbl(double aValue)
    {
        return new DataValueDouble(aValue);
    }


    private static IDataValue lng(long aValue)
    {
        return new DataValueLong(aValue);
    }

    /**
     * One row of the case table.
     *
     * @param name
     *            the display name.
     * @param left
     *            one cell ({@code null} is the cell no loader hands out).
     * @param right
     *            the other cell.
     * @param typed
     *            whether the two are one TYPED identity ({@code keyPart} equality).
     * @param text
     *            whether the two are one TEXT-JOIN identity ({@code textKeyIdentity} equality).
     */
    record KeyCase(String name, @Nullable IDataValue left, @Nullable IDataValue right,
            boolean typed, boolean text)
    {

        @Override
        public String toString()
        {
            return name;
        }
    }

    private static final List<KeyCase> CASES = List.of(
            // JKM R5, the violation the string collapse committed: a missing key is not ""
            new KeyCase("MIS vs empty", mis(MissingValue.MIS), str(""), false, false),
            // two different markers are two identities, whichever two they are
            new KeyCase("MIS_A vs MIS", mis(MissingValue.MIS_A), mis(MissingValue.MIS), false,
                    false),
            new KeyCase("MIS_A vs MIS_B", mis(MissingValue.MIS_A), mis(MissingValue.MIS_B), false,
                    false),
            new KeyCase("MIS_UNKNOWN vs MIS_ERROR", mis(MissingValue.MIS_UNKNOWN),
                    mis(MissingValue.MIS_ERROR), false, false),
            // a missing is never a present value, however it is spelt
            new KeyCase("present vs MIS", str("P1"), mis(MissingValue.MIS), false, false),
            new KeyCase("dot vs MIS", str("."), mis(MissingValue.MIS), false, false),
            // ⭐ D2 (c): the identity object CANNOT collide. The hand-copied string encoding made a
            // text cell spelling the marker token equal the missing cell; the enum constant is
            // never equal to any String.
            new KeyCase("marker token text vs MIS", str("\u0001MIS"), mis(MissingValue.MIS), false,
                    false),
            // NaN-encoded markers keep their identity: forValue decodes each payload
            new KeyCase("NaN-coded MIS_A vs MIS_B", dbl(MissingValue.MIS_A.asDouble()),
                    dbl(MissingValue.MIS_B.asDouble()), false, false),
            new KeyCase("NaN-coded MIS_A vs stored MIS_A", dbl(MissingValue.MIS_A.asDouble()),
                    mis(MissingValue.MIS_A), true, true),
            // a bare NaN is the generic MIS (BNM E5) -- not MIS_A ...
            new KeyCase("bare NaN vs MIS_A", dbl(BARE_NAN), mis(MissingValue.MIS_A), false, false),
            // ... but MIS itself
            new KeyCase("bare NaN vs MIS", dbl(BARE_NAN), mis(MissingValue.MIS), true, true),
            // controls: the same identity on both sides is one key (no over-correction)
            new KeyCase("MIS vs MIS", mis(MissingValue.MIS), mis(MissingValue.MIS), true, true),
            new KeyCase("empty vs empty", str(""), str(""), true, true),
            new KeyCase("present vs present", str("P1"), str("P1"), true, true),
            // whitespace is a real value under MISSING_OR_EMPTY (the notion of every KEEP site)
            new KeyCase("whitespace vs empty", str("  "), str(""), false, false),
            // the cell no loader hands out classifies as MIS_UNKNOWN
            new KeyCase("null vs MIS_UNKNOWN", null, mis(MissingValue.MIS_UNKNOWN), true, true),
            // numbers: LONG 2 and DOUBLE 2.0 are one key, and so are the two zeros
            new KeyCase("DOUBLE vs DOUBLE", dbl(2.5), dbl(2.5), true, true),
            new KeyCase("LONG vs DOUBLE", lng(2), dbl(2.0), true, true),
            new KeyCase("-0.0 vs 0.0", dbl(-0.0), dbl(0.0), true, true),
            // D64h: the typed identity is EXACT; the text join folds the ruled noise (E7)
            new KeyCase("DOUBLE within noise", dbl(4.9999999999994), dbl(5.0), false, true),
            // E2: the text is lossless outside the noise -- 13 digits stay 13 digits
            new KeyCase("LONG 13 digits vs +1", lng(1_234_567_890_123L), lng(1_234_567_890_124L),
                    false, false),
            new KeyCase("DOUBLE beyond threshold", dbl(123_456_789_012.4), dbl(123_456_789_012.3),
                    false, false),
            // D4-R1 on typed sites (char != num) vs D4-R5 on the text join (one key)
            new KeyCase("STRING vs LONG 13 digits", str("1234567890123"), lng(1_234_567_890_123L),
                    false, true),
            new KeyCase("STRING vs DOUBLE", str("5"), dbl(5.0), false, true),
            new KeyCase("STRING vs LONG +1", str("1234567890123"), lng(1_234_567_890_124L), false,
                    false));

    static Stream<KeyCase> cases()
    {
        return CASES.stream();
    }


    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void aKeyIsOnlyItsOwnIdentity(KeyCase aCase)
    {
        assertEquals(aCase.typed(), KEEP.keyPart(aCase.left()).equals(KEEP.keyPart(aCase.right())),
                "typed identity");
        assertEquals(aCase.text(),
                KEEP.textKeyIdentity(aCase.left()).equals(KEEP.textKeyIdentity(aCase.right())),
                "text-join identity");
        // Both relations are symmetric.
        assertEquals(aCase.typed(), KEEP.keyPart(aCase.right()).equals(KEEP.keyPart(aCase.left())));
        assertEquals(aCase.text(),
                KEEP.textKeyIdentity(aCase.right()).equals(KEEP.textKeyIdentity(aCase.left())));
    }


    /**
     * Every pair of {@link MissingValue}'s markers — all of them, the SAS, R and Python ones —
     * stored and NaN-coded: one identity iff one marker.
     */
    static Stream<Arguments> markerPairs()
    {
        List<Arguments> out = new ArrayList<>();
        for (MissingValue a : MissingValue.values())
        {
            for (MissingValue b : MissingValue.values())
            {
                out.add(Arguments.of(a, b));
            }
        }
        return out.stream();
    }


    @ParameterizedTest(name = "{0} vs {1}")
    @MethodSource("markerPairs")
    void everyMarkerIsItsOwnIdentity(MissingValue aLeft, MissingValue aRight)
    {
        boolean same = aLeft == aRight;
        for (IDataValue left : List.of(mis(aLeft), dbl(aLeft.asDouble())))
        {
            for (IDataValue right : List.of(mis(aRight), dbl(aRight.asDouble())))
            {
                String where = left + " / " + right;
                assertEquals(same, KEEP.keyPart(left).equals(KEEP.keyPart(right)), where);
                assertEquals(same, KEEP.textKeyIdentity(left).equals(KEEP.textKeyIdentity(right)),
                        where);
            }
        }
        // ... and a missing cell is never the empty cell nor the marker's own name as text
        assertNotEquals(KeyPart.EMPTY, KEEP.keyPart(mis(aLeft)));
        assertNotEquals("", KEEP.textKeyIdentity(mis(aLeft)));
        assertNotEquals(KEEP.textKeyIdentity(str(aLeft.name())), KEEP.textKeyIdentity(mis(aLeft)));
    }

    // ---------------------------------------------------------------- the bijection (D2 (c))


    /** Parts of every kind, including every marker and the numeric edges. */
    private static List<KeyPart> parts()
    {
        List<KeyPart> out = new ArrayList<>(List.of(new KeyPart.Present("P1"),
                new KeyPart.Present("."), new KeyPart.Present("  "),
                new KeyPart.Present("\u0001MIS"), new KeyPart.Present("5"),
                new KeyPart.PresentNumber(0.0), new KeyPart.PresentNumber(-0.0),
                new KeyPart.PresentNumber(5.0), new KeyPart.PresentNumber(4.9999999999994),
                new KeyPart.PresentNumber(-2.5), new KeyPart.PresentNumber(Double.MAX_VALUE),
                new KeyPart.PresentNumber(Double.MIN_VALUE),
                new KeyPart.PresentNumber(Double.NEGATIVE_INFINITY), KeyPart.EMPTY));
        for (MissingValue m : MissingValue.values())
        {
            out.add(KeyPart.missing(m));
        }
        return out;
    }


    @Test
    void theIdentityObjectIsABijectionWithKeyPart()
    {
        List<KeyPart> parts = parts();
        for (KeyPart p : parts)
        {
            Object id = p.identity();
            assertTrue(id instanceof String || id instanceof Double || id instanceof MissingValue,
                    () -> "identity of " + p + " is a " + id.getClass());
            assertEquals(p, KeyPart.ofIdentity(id), () -> "round trip of " + p);
            assertEquals(id, KeyPart.ofIdentity(id).identity(), () -> "inverse of " + id);
        }
        for (KeyPart p : parts)
        {
            for (KeyPart q : parts)
            {
                assertEquals(p.equals(q), p.identity().equals(q.identity()),
                        () -> p + " / " + q + ": part equality and identity equality disagree");
            }
        }
    }


    @Test
    void theIdentityKindsAreTheRuledOnes()
    {
        assertEquals("P1", new KeyPart.Present("P1").identity());
        assertEquals("", KeyPart.EMPTY.identity());
        assertSame(MissingValue.MIS_A, KeyPart.missing(MissingValue.MIS_A).identity());
        assertEquals(5.0, new KeyPart.PresentNumber(5.0).identity());
        // -0.0 is canonical 0.0 in the part AND in its identity
        assertEquals(Double.doubleToRawLongBits(0.0),
                Double.doubleToRawLongBits((Double) new KeyPart.PresentNumber(-0.0).identity()));
        assertSame(KeyPart.EMPTY, KeyPart.ofIdentity(""));
        assertSame(KeyPart.MISSING_MIS, KeyPart.ofIdentity(MissingValue.MIS));
    }


    @Test
    void ofIdentityRejectsWhatNoPartCanBe()
    {
        assertThrows(IllegalArgumentException.class, () -> KeyPart.ofIdentity(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> KeyPart.ofIdentity(-0.0));
        assertThrows(IllegalArgumentException.class, () -> KeyPart.ofIdentity(5L));
        assertThrows(IllegalArgumentException.class, () -> KeyPart.ofIdentity(5));
        assertThrows(IllegalArgumentException.class, () -> KeyPart.ofIdentity(true));
    }


    /** Every cell shape the case table uses, plus whitespace and a boolean. */
    private static List<@Nullable IDataValue> cells()
    {
        List<@Nullable IDataValue> out = new ArrayList<>();
        for (KeyCase c : CASES)
        {
            out.add(c.left());
            out.add(c.right());
        }
        for (MissingValue m : MissingValue.values())
        {
            out.add(mis(m));
            out.add(dbl(m.asDouble()));
        }
        out.addAll(Arrays.asList(str(" "), str(" x "), new DataValueBoolean(true), lng(0),
                lng(Long.MIN_VALUE + 1)));
        return out;
    }


    @Test
    void textKeyIdentityIsTheTextProjectionOfKeyPart()
    {
        for (GroupKeyPolicy policy : POLICIES)
        {
            for (IDataValue dv : cells())
            {
                KeyPart part = policy.keyPart(dv);
                assertEquals(part.asText().identity(), policy.textKeyIdentity(dv),
                        () -> policy + " / " + dv);
                // the text identity is never a Double: RELREC's present axis stays text (D4-R5)
                Object id = policy.textKeyIdentity(dv);
                assertTrue(id instanceof String || id instanceof MissingValue,
                        () -> String.valueOf(dv));
            }
        }
    }


    @Test
    void textKeyIdentityDoesNotWrapATextCell()
    {
        String value = String.valueOf("P1".toCharArray());
        // the cell's own String, not a copy -- allocation-free for text, empty and missing cells
        assertSame(value, KEEP.textKeyIdentity(str(value)));
        assertSame(MissingValue.MIS_B, KEEP.textKeyIdentity(mis(MissingValue.MIS_B)));
        assertEquals("", KEEP.textKeyIdentity(str("")));
        assertEquals("5", KEEP.textKeyIdentity(lng(5)));
        assertEquals("0", KEEP.textKeyIdentity(dbl(-0.0)));
    }

    // ---------------------------------------------------------------- the parts themselves


    @Test
    void presentCannotSpellTheEmptyIdentityAndNaNIsNeverANumber()
    {
        assertThrows(IllegalArgumentException.class, () -> new KeyPart.Present(""));
        assertThrows(IllegalArgumentException.class, () -> new KeyPart.PresentNumber(Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> new KeyPart.PresentNumber(MissingValue.MIS_A.asDouble()));
        assertEquals(new KeyPart.PresentNumber(0.0), new KeyPart.PresentNumber(-0.0));
    }


    @Test
    void theHotMarkersAreInternedAndTheRestAreEqualByValue()
    {
        assertSame(KeyPart.MISSING_MIS, KeyPart.missing(MissingValue.MIS));
        assertSame(KeyPart.MISSING_UNKNOWN, KeyPart.missing(MissingValue.MIS_UNKNOWN));
        assertSame(KeyPart.MISSING_ERROR, KeyPart.missing(MissingValue.MIS_ERROR));
        assertEquals(new KeyPart.Missing(MissingValue.MIS_A), KeyPart.missing(MissingValue.MIS_A));
        assertNotSame(KeyPart.missing(MissingValue.MIS_A), KeyPart.missing(MissingValue.MIS_A));
        assertSame(KeyPart.MISSING_MIS, KEEP.keyPart(dbl(BARE_NAN)));
        assertSame(KeyPart.MISSING_UNKNOWN, KEEP.keyPart(null));
        assertSame(KeyPart.EMPTY, KEEP.keyPart(str("")));
    }


    @Test
    void reportingFormAndPresenceOfEveryKind()
    {
        assertEquals("P1", new KeyPart.Present("P1").reportingForm());
        assertEquals("5", new KeyPart.PresentNumber(5.0).reportingForm());
        assertEquals("4.5", new KeyPart.PresentNumber(4.5).reportingForm());
        assertEquals("", KeyPart.EMPTY.reportingForm());
        assertEquals("\u0001MIS_A", KeyPart.missing(MissingValue.MIS_A).reportingForm());
        assertTrue(new KeyPart.Present("P1").present());
        assertTrue(new KeyPart.PresentNumber(1.0).present());
        assertFalse(KeyPart.EMPTY.present());
        assertFalse(KeyPart.MISSING_MIS.present());
    }


    @Test
    void asTextProjectsOnlyAPresentNumber()
    {
        KeyPart text = new KeyPart.Present("P1");
        assertSame(text, text.asText());
        assertSame(KeyPart.EMPTY, KeyPart.EMPTY.asText());
        assertSame(KeyPart.MISSING_MIS, KeyPart.MISSING_MIS.asText());
        assertEquals(new KeyPart.Present("5"), new KeyPart.PresentNumber(5.0).asText());
        assertEquals(new KeyPart.Present("5"), new KeyPart.PresentNumber(4.9999999999994).asText());
    }


    @Test
    void numericKeysAreExactAndOneRecordForBothNumericTypes()
    {
        assertInstanceOf(KeyPart.PresentNumber.class, KEEP.keyPart(lng(2)));
        assertEquals(KEEP.keyPart(lng(2)), KEEP.keyPart(dbl(2.0)));
        assertNotEquals(KEEP.keyPart(dbl(4.9999999999994)), KEEP.keyPart(dbl(5.0)));
        assertInstanceOf(KeyPart.Present.class, KEEP.keyPart(new DataValueBoolean(true)));
    }

    // ---------------------------------------------------------------- the policies


    @Test
    void theFourPoliciesAndTheirTwoBlanknessNotions()
    {
        assertTrue(GroupKeyPolicy.KEEP_MISSING_KEYS.keepMissings());
        assertEquals(Blankness.MISSING_OR_EMPTY, GroupKeyPolicy.KEEP_MISSING_KEYS.blankness());
        assertEquals(GroupKeyPolicy.KEEP_MISSING_KEYS, GroupKeyPolicy.FOLD_BLANK_KEYS);
        assertFalse(GroupKeyPolicy.DROP_MISSING_KEYS.keepMissings());
        assertEquals(Blankness.MISSING_OR_EMPTY, GroupKeyPolicy.DROP_MISSING_KEYS.blankness());
        assertFalse(GroupKeyPolicy.COALESCE_COMPONENT.keepMissings());
        assertEquals(Blankness.MISSING_OR_WHITESPACE,
                GroupKeyPolicy.COALESCE_COMPONENT.blankness());
    }


    @Test
    void blanknessIsTheOnlyAxisThePredicateReads()
    {
        for (GroupKeyPolicy policy : POLICIES)
        {
            boolean ws = policy.blankness() == Blankness.MISSING_OR_WHITESPACE;
            assertTrue(policy.isBlankKeyComponent(null));
            assertTrue(policy.isBlankKeyComponent(str("")));
            assertTrue(policy.isBlankKeyComponent(mis(MissingValue.MIS_Z)));
            assertTrue(policy.isBlankKeyComponent(dbl(BARE_NAN)));
            assertFalse(policy.isBlankKeyComponent(str("P1")));
            assertFalse(policy.isBlankKeyComponent(lng(0)));
            assertEquals(ws, policy.isBlankKeyComponent(str("  ")));
            // under the whitespace notion a whitespace-only cell is EMPTY, never Missing
            assertEquals(ws ? KeyPart.EMPTY : new KeyPart.Present("  "), policy.keyPart(str("  ")));
            assertEquals(ws ? "" : "  ", policy.textKeyIdentity(str("  ")));
        }
    }


    @Test
    void theDispositionIsOverriddenWithoutTouchingBlankness()
    {
        GroupKeyPolicy keep = GroupKeyPolicy.KEEP_MISSING_KEYS;
        assertSame(keep, keep.withKeepMissings(true));
        assertSame(keep, keep.withDeclared(null));
        assertSame(keep, keep.withDeclared(Boolean.TRUE));
        assertEquals(GroupKeyPolicy.DROP_MISSING_KEYS, keep.withDeclared(Boolean.FALSE));
        GroupKeyPolicy coalesceKept = GroupKeyPolicy.COALESCE_COMPONENT.withKeepMissings(true);
        assertTrue(coalesceKept.keepMissings());
        assertEquals(Blankness.MISSING_OR_WHITESPACE, coalesceKept.blankness());
    }
}
