package net.cumba.datatable.values;

// OSS-IDENTITY datatable-numeric-text: byte-identical in cumba-datatable and cumba-oss-datatable.
// Edit in cumba-datatable, then copy; check_oss_identity.py fails on any divergence.

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * A micro-timing of the hot path ({@code PLAN-numeric-cleaning-and-key-text} phase 1's stop
 * condition): {@link DataValueSupport#getAsDoubleCleaned(double)} and
 * {@link DataValueSupport#toCleanText(double)} against {@link String#valueOf(double)} as the floor
 * any text rendering pays, over 2^20 values of the shapes clinical data has — counts, fractions,
 * 4-decimal text data and widened floats.
 *
 * <p>
 * It <b>reports</b> (the ratio is read from the log by the plan's lane) and asserts only what keeps
 * the measurement honest: every timed loop produces the same checksum as the untimed pass, so the
 * JIT cannot have hoisted the work away. Like {@code BdatLoadBenchmarkTest} it is a JUnit
 * micro-timing, indicative rather than a benchmark; the precedent for the report line is that
 * class's {@code [bench]} format.
 * </p>
 */
class NumericCleaningTimingTest
{

    private static final int N = 1 << 20;

    private static final int WARMUP = 3;

    private static final int REPS = 3;

    private static double[] values()
    {
        Random rnd = new Random(20260925L);
        double[] v = new double[N];
        for (int i = 0; i < N; i++)
        {
            v[i] = switch (i % 4)
            {
            case 0 -> rnd.nextInt(100_000); // counts, --SEQ, VISITNUM
            case 1 -> rnd.nextDouble() * 1000; // full-precision fractions
            case 2 -> rnd.nextInt(10_000_000) / 10_000.0; // 4-decimal text data
            default -> rnd.nextFloat() * 100; // widened floats
            };
        }
        return v;
    }


    private static double cleanChecksum(double[] aValues)
    {
        double s = 0;
        for (double v : aValues)
        {
            s += DataValueSupport.getAsDoubleCleaned(v);
        }
        return s;
    }


    private static long textChecksum(double[] aValues)
    {
        long len = 0;
        for (double v : aValues)
        {
            len += DataValueSupport.toCleanText(v).length();
        }
        return len;
    }


    private static long valueOfChecksum(double[] aValues)
    {
        long len = 0;
        for (double v : aValues)
        {
            len += String.valueOf(v).length();
        }
        return len;
    }


    @Test
    void reportTheHotPathCost()
    {
        double[] v = values();
        double cleanExpected = cleanChecksum(v);
        long textExpected = textChecksum(v);
        long valueOfExpected = valueOfChecksum(v);
        for (int i = 0; i < WARMUP; i++)
        {
            assertEquals(cleanExpected, cleanChecksum(v));
            assertEquals(textExpected, textChecksum(v));
            assertEquals(valueOfExpected, valueOfChecksum(v));
        }
        double cleanNs = 0;
        double textNs = 0;
        double valueOfNs = 0;
        for (int i = 0; i < REPS; i++)
        {
            long t0 = System.nanoTime();
            double c = cleanChecksum(v);
            long t1 = System.nanoTime();
            long t = textChecksum(v);
            long t2 = System.nanoTime();
            long s = valueOfChecksum(v);
            long t3 = System.nanoTime();
            assertEquals(cleanExpected, c, "the timed loop must do the real work");
            assertEquals(textExpected, t, "the timed loop must do the real work");
            assertEquals(valueOfExpected, s, "the timed loop must do the real work");
            cleanNs += (t1 - t0) / (double) N;
            textNs += (t2 - t1) / (double) N;
            valueOfNs += (t3 - t2) / (double) N;
        }
        cleanNs /= REPS;
        textNs /= REPS;
        valueOfNs /= REPS;
        System.out.printf(Locale.ROOT,
                "[bench] getAsDoubleCleaned=%.1f ns/op  toCleanText=%.1f ns/op"
                        + "  String.valueOf=%.1f ns/op  toCleanText/String.valueOf=%.2fx%n",
                cleanNs, textNs, valueOfNs, textNs / valueOfNs);
    }
}
