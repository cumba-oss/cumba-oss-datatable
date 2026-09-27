package net.cumba.datatable.values;

// OSS-IDENTITY datatable-key-identity: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The composite group key's case table ({@code PLAN-grouping-key-identity}): one key exactly when
 * every component is one {@link GroupKeyPolicy.KeyPart} identity. Built the way the engine builds
 * it, from real cells through {@link GroupKeyPolicy#keyIdentity}, so each row pins the pair
 * (identity, key) the grouped lookup relies on.
 *
 * <p>
 * The rows are the plan's §3 axes: the noise pair two keys ({@code D64h}), the zeros one key
 * ({@code D84}, owner Q4), {@code LONG}/{@code DOUBLE} one key where a double holds the long
 * ({@code D99b}) and two beyond ({@code D64h}), the blanks pairwise distinct ({@code JKM R5}), and
 * an absent column ({@code ""}) on both sides of a lookup.
 * </p>
 */
class GroupKeyTest
{

    private static final GroupKeyPolicy KEEP = GroupKeyPolicy.KEEP_MISSING_KEYS;

    private static final long TWO_53 = 9_007_199_254_740_992L;

    private static Object id(IDataValue aCell)
    {
        return KEEP.keyIdentity(aCell);
    }


    /** The two-column key of subject {@code "S1"} and {@code aCell}. */
    private static Object key(IDataValue aCell)
    {
        return GroupKey.of("S1", id(aCell));
    }


    private static void assertOneKey(Object aLeft, Object aRight)
    {
        assertEquals(aLeft, aRight);
        assertEquals(aRight, aLeft);
        assertEquals(aLeft.hashCode(), aRight.hashCode());
    }


    @Test
    void oneComponentIsTheBareIdentity()
    {
        String subject = String.valueOf("S1".toCharArray());
        assertSame(subject, GroupKey.of(subject));
        assertSame(MissingValue.MIS_A, GroupKey.of(MissingValue.MIS_A));
        assertEquals(5.0, GroupKey.of(id(new DataValueLong(5))));
        assertEquals("", GroupKey.of(""));
    }


    @Test
    void twoOrMoreComponentsAreAGroupKeyEqualByComponent()
    {
        Object k = GroupKey.of("S1", 5.0, "SYSBP");
        GroupKey gk = assertInstanceOf(GroupKey.class, k);
        assertEquals(3, gk.size());
        assertEquals("S1", gk.get(0));
        assertEquals(5.0, gk.get(1));
        assertEquals("SYSBP", gk.get(2));
        assertThrows(IndexOutOfBoundsException.class, () -> gk.get(3));
        assertOneKey(k, GroupKey.of("S1", 5.0, "SYSBP"));
        assertEquals(Arrays.hashCode(new Object[]
        {
                "S1", 5.0, "SYSBP"
        }), k.hashCode());
        assertNotEquals(k, GroupKey.of("S1", "SYSBP", 5.0));
        assertNotEquals(k, GroupKey.of("S1", 5.0));
        assertNotEquals(GroupKey.of("S1", 5.0), GroupKey.of("S1"));
        assertNotEquals(k, "S1\u00005\u0000SYSBP");
        assertEquals("GroupKey[S1, 5.0, SYSBP]", k.toString());
    }


    @Test
    void noComponentsIsTheWholeTableKey()
    {
        Object none = GroupKey.of();
        GroupKey gk = assertInstanceOf(GroupKey.class, none);
        assertEquals(0, gk.size());
        assertOneKey(none, GroupKey.of());
        assertNotEquals(none, GroupKey.of(""));
    }


    /**
     * H1: a table lacking all k grouping columns keys every row as k empty components, on the
     * building side and on the probing side alike — for k = 2 a {@code GroupKey}, never the
     * {@code "\0"}-joined text the lookup would miss.
     */
    @Test
    void anAllAbsentKeyEqualsItselfForEveryWidth()
    {
        assertOneKey(GroupKey.of("", ""), GroupKey.of("", ""));
        assertOneKey(GroupKey.of("", "", ""), GroupKey.of("", "", ""));
        assertNotEquals(GroupKey.of("", ""), "\u0000");
        assertNotEquals(GroupKey.of("", ""), GroupKey.of(""));
        // an absent column ("") and an empty cell ("") are one key component (EC-44 / EC-43)
        assertOneKey(GroupKey.of("", ""), GroupKey.of(id(new DataValueString("")), ""));
    }


    @Test
    void theNoisePairIsTwoKeys()
    {
        assertNotEquals(key(new DataValueDouble(4.9999999999994)), key(new DataValueDouble(5.0)));
        assertNotEquals(key(new DataValueDouble(0.1 + 0.2)), key(new DataValueDouble(0.3)));
    }


    @Test
    void theTwoZerosAreOneKey()
    {
        assertOneKey(key(new DataValueDouble(-0.0)), key(new DataValueDouble(0.0)));
        assertOneKey(key(new DataValueDouble(-0.0)), key(new DataValueLong(0)));
        assertOneKey(GroupKey.of(id(new DataValueDouble(-0.0))), GroupKey.of(0.0));
    }


    @Test
    void longAndDoubleAreOneKeyWhereADoubleHoldsTheLong()
    {
        assertOneKey(key(new DataValueLong(2)), key(new DataValueDouble(2.0)));
        assertOneKey(key(new DataValueLong(TWO_53)),
                key(new DataValueDouble(9_007_199_254_740_992.0)));
        assertNotEquals(key(new DataValueLong(TWO_53)), key(new DataValueLong(TWO_53 + 1)));
        assertNotEquals(key(new DataValueLong(Long.MAX_VALUE)),
                key(new DataValueLong(Long.MAX_VALUE - 1)));
        assertNotEquals(key(new DataValueLong(TWO_53 + 1)), key(new DataValueDouble(0x1p53)));
    }


    @Test
    void theBlanksArePairwiseDistinct()
    {
        List<Object> keys = List.of(key(new DataValueMissing(MissingValue.MIS)),
                key(new DataValueMissing(MissingValue.MIS_A)), key(new DataValueString("")),
                key(new DataValueString(".")));
        for (int i = 0; i < keys.size(); i++)
        {
            for (int j = 0; j < keys.size(); j++)
            {
                assertEquals(i == j, keys.get(i).equals(keys.get(j)), i + " / " + j);
            }
        }
        assertOneKey(key(new DataValueMissing(MissingValue.MIS)),
                key(new DataValueDouble(MissingValue.MIS.asDouble())));
    }


    @Test
    void onlyIdentityObjectsMakeAKey()
    {
        assertThrows(IllegalArgumentException.class, () -> GroupKey.of("S1", 5));
        assertThrows(IllegalArgumentException.class, () -> GroupKey.of(1.0f));
        assertThrows(IllegalArgumentException.class,
                () -> GroupKey.of("S1", GroupKeyPolicy.KeyPart.EMPTY));
        assertThrows(IllegalArgumentException.class, () -> GroupKey.of(GroupKey.of("A", "B")));
        // non-canonical numbers are refused: each would silently never equal the key the same cell
        // builds through keyIdentity (review round 1, L1)
        assertThrows(IllegalArgumentException.class, () -> GroupKey.of("S1", -0.0));
        assertThrows(IllegalArgumentException.class, () -> GroupKey.of(Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> GroupKey.of("S1", MissingValue.MIS_A.asDouble()));
        assertThrows(IllegalArgumentException.class, () -> GroupKey.of(5L));
        assertThrows(IllegalArgumentException.class, () -> GroupKey.of("S1", TWO_53));
        assertThrows(IllegalArgumentException.class, () -> GroupKey.of(Long.MIN_VALUE));
        assertEquals(TWO_53 + 1, GroupKey.of(TWO_53 + 1));
        assertEquals(0.0, GroupKey.of(0.0));
        Object[] withNull =
        {
                "S1", null
        };
        NullPointerException npe = assertThrows(NullPointerException.class,
                () -> GroupKey.of(withNull));
        assertTrue(String.valueOf(npe.getMessage()).contains("never null"));
    }
}
