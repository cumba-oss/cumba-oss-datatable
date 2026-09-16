package net.cumba.datatable.impl.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The two properties that make {@link HashLookup}'s avalanche finalizer worth having, plus the
 * parts of the probe arithmetic that are not pinned elsewhere.
 *
 * <p>
 * ⚠ {@code mix} cannot be tested through {@code put}/{@code get}: both sides apply the
 * <em>same</em> function, so any deterministic replacement - even {@code return 0} - still finds
 * every key it stored. What a broken finalizer destroys is the <b>distribution</b>: structured
 * clinical keys ({@code 31*h + columnHash} over sequential subject ids) collapse onto a handful of
 * slots and the linear-probe chains grow from a handful to thousands. That is why the two tests
 * below assert the function's properties directly rather than its output, and why neither pins a
 * constant: any finalizer that is injective and avalanches passes them.
 * </p>
 */
class HashLookupMutationTest
{

    /** Deterministic, JVM-independent sample: the golden-ratio stride over the int range. */
    private static final int GOLDEN = 0x9E3779B9;

    private static final int SAMPLE_COUNT = 4096;

    /**
     * A finalizer must be injective: two distinct keys must not be folded onto one another before
     * they even reach the modulo. A structured family is the realistic worst case.
     */
    @Test
    void mixIsInjectiveOverAStructuredKeyFamily()
    {
        Set<Integer> mixed = new HashSet<>();
        for (int i = 0; i < 1000; i++)
        {
            mixed.add(Integer.valueOf(HashLookup.mix(31 * 31 + i)));
        }
        assertEquals(1000, mixed.size(),
                "mix folded distinct structured keys onto the same value - every such pair is a "
                        + "guaranteed slot collision and a longer probe chain");
    }


    /**
     * Strict avalanche criterion: flipping any single input bit must flip any given output bit
     * about half the time. A band of [0.40, 0.60] is far looser than murmur3's fmix32 actually
     * achieves here (its worst case over this sample is 0.476) but tight enough to reject a
     * finalizer that propagates in one direction only.
     */
    @Test
    void mixSatisfiesTheStrictAvalancheCriterion()
    {
        int[] samples = new int[SAMPLE_COUNT];
        for (int i = 0; i < SAMPLE_COUNT; i++)
        {
            samples[i] = i * GOLDEN;
        }

        for (int inBit = 0; inBit < Integer.SIZE; inBit++)
        {
            int[] flips = new int[Integer.SIZE];
            for (int sample : samples)
            {
                int delta = HashLookup.mix(sample) ^ HashLookup.mix(sample ^ (1 << inBit));
                for (int outBit = 0; outBit < Integer.SIZE; outBit++)
                {
                    flips[outBit] += (delta >>> outBit) & 1;
                }
            }
            for (int outBit = 0; outBit < Integer.SIZE; outBit++)
            {
                double p = flips[outBit] / (double) SAMPLE_COUNT;
                assertTrue(p >= 0.40 && p <= 0.60, "input bit " + inBit + " flips output bit "
                        + outBit + " with probability " + p + " - no avalanche");
            }
        }
    }


    /**
     * The three-argument {@code get} walks the same ring as the other two probe loops, and its own
     * step must wrap: two entries are forced onto the <b>last</b> slot, so the second one sits on
     * slot 0 and can only be reached by stepping past the end of the table.
     */
    @Test
    void theBiRowMatcherProbeWrapsAroundTheEndOfTheTable()
    {
        HashLookup lookup = new HashLookup(4, 0.75f);
        int capacity = lookup.capacity();
        assertEquals(16, capacity);

        int hLast = hashForSlot(capacity - 1, capacity);
        lookup.put(hLast, 100); // occupies the last slot
        lookup.put(hLast, 200); // collides, so the insert probe wrapped to slot 0

        // The probe starts on the last slot, rejects the occupant through the matcher, and must
        // wrap to slot 0 to reach the second row.
        assertEquals(200, lookup.get(7, hLast, (row1, row2) -> row1 == 200 && row2 == 7),
                "the probe must wrap past the end of the table");
        assertEquals(100, lookup.get(7, hLast, (row1, row2) -> row1 == 100));
        assertEquals(-1, lookup.get(7, hLast, (row1, row2) -> false),
                "an absent key must answer -1 after walking past the wrap");
    }


    /** An insert into a full table must name the entry it refused, not the one before it. */
    @Test
    void theFullTableMessageNamesTheRejectedEntry()
    {
        HashLookup lookup = new HashLookup(4, 0.75f);
        for (int i = 0; i < 15; i++)
        {
            lookup.put(i, i);
        }

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> lookup.put(99, 99));
        assertTrue(ex.getMessage().contains("entry number 16"),
                "unexpected message: " + ex.getMessage());
    }


    /** Finds a hash whose mixed value lands on the wanted slot. */
    private static int hashForSlot(int aWantedSlot, int aCapacity)
    {
        for (int h = 0; h < 1_000_000; h++)
        {
            if (Math.floorMod(HashLookup.mix(h), aCapacity) == aWantedSlot)
            {
                return h;
            }
        }
        throw new IllegalStateException("no hash found for slot " + aWantedSlot);
    }
}
