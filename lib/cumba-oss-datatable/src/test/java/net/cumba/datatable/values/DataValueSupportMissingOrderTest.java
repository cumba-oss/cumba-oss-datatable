package net.cumba.datatable.values;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pins the missing-value ordering of {@code DataValueSupport.compare} (F-dt-06, SAS semantics): a
 * missing sorts below ANY non-missing value, and missings order among themselves by their
 * missing-value byte — the SAS collating sequence {@code ._ < . < .A < ... < .Z}.
 */
class DataValueSupportMissingOrderTest
{

    private final DataValueSupport dvs = new DataValueSupport();

    @Test
    void missingSortsBelowAnyNumber()
    {
        IDataValue mis = new DataValueMissing(MissingValue.MIS);

        assertTrue(dvs.compare(mis, new DataValueDouble(5.0)) < 0);
        assertTrue(dvs.compare(new DataValueDouble(5.0), mis) > 0);
        assertTrue(dvs.compare(mis, new DataValueDouble(-1e300)) < 0,
                "missing is below even the most negative number");
        assertTrue(dvs.compare(mis, new DataValueLong(Long.MIN_VALUE)) < 0);
    }


    @Test
    void missingSortsBelowAnyStringEvenOnesThatCollateBeforeTheDot()
    {
        IDataValue mis = new DataValueMissing(MissingValue.MIS);

        // "!" (0x21) sorts before "." (0x2E) lexicographically — the explicit branch must win
        assertTrue(dvs.compare(mis, new DataValueString("!")) < 0);
        assertTrue(dvs.compare(new DataValueString("!"), mis) > 0);
        assertTrue(dvs.compare(mis, new DataValueString("")) < 0);
    }


    /**
     * Missings order among themselves by their missing-value byte. (The internal twin of this test
     * exercises the full SAS special-missing sequence {@code ._ < . < .A < ... < .Z}; this variant
     * of {@link MissingValue} carries only {@code MIS}, {@code MIS_UNKNOWN} and {@code MIS_ERROR},
     * so the byte order is pinned on those.)
     */
    @Test
    void missingsOrderAmongThemselvesByTheirByte()
    {
        IDataValue mis = new DataValueMissing(MissingValue.MIS);
        IDataValue misUnknown = new DataValueMissing(MissingValue.MIS_UNKNOWN);
        IDataValue misError = new DataValueMissing(MissingValue.MIS_ERROR);

        assertTrue(dvs.compare(mis, misUnknown) < 0, ". (64) < <UKN> (101)");
        assertTrue(dvs.compare(misUnknown, misError) < 0, "<UKN> (101) < <ERR> (102)");
        assertEquals(0, dvs.compare(mis, new DataValueMissing(MissingValue.MIS)));
    }


    /**
     * A DOUBLE-typed value can carry a missing encoded as a NaN payload. Java's
     * {@code Double.compare} sorts every NaN LAST and calls all of them equal — losing both the
     * position and the byte order among missings. The payload byte must decide instead.
     */
    @Test
    void encodedNanPayloadsOrderLikeExplicitMissings()
    {
        IDataValue encodedMis = new DataValueDouble(MissingValue.MIS.asDouble());
        IDataValue encodedMisUnknown = new DataValueDouble(MissingValue.MIS_UNKNOWN.asDouble());

        assertTrue(dvs.compare(encodedMis, new DataValueDouble(1.0)) < 0,
                "an encoded missing sorts below any real number, not last");
        assertTrue(dvs.compare(encodedMis, encodedMisUnknown) < 0, ". < <UKN> survives encoding");
        assertEquals(0, dvs.compare(encodedMis, new DataValueMissing(MissingValue.MIS)),
                "encoded and explicit spellings of the same missing are the same value");
    }


    @Test
    void getMissingValueRecognisesBothSpellingsAndNothingElse()
    {
        assertSame(MissingValue.MIS,
                DataValueSupport.getMissingValue(new DataValueMissing(MissingValue.MIS)));
        assertSame(MissingValue.MIS,
                DataValueSupport.getMissingValue(new DataValueDouble(MissingValue.MIS.asDouble())));
        assertSame(MissingValue.MIS_UNKNOWN,
                DataValueSupport.getMissingValue(new DataValueDouble(Double.NaN)),
                "a plain NaN has no known payload");
        assertNull(DataValueSupport.getMissingValue(new DataValueDouble(1.5)));
        assertNull(DataValueSupport.getMissingValue(new DataValueString("abc")),
                "a STRING never counts as missing, whatever its numeric interpretation");
    }
}
