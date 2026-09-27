package net.cumba.datatable.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.cumba.datatable.impl.databuffer.DataBufferDouble;
import net.cumba.datatable.impl.databuffer.DataBufferFactory;
import net.cumba.datatable.values.DataValueDouble;
import net.cumba.datatable.values.DataValueSupport;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.IDataValue;
import net.cumba.datatable.values.MissingValue;
import org.junit.jupiter.api.Test;

/**
 * ⭐ Owner direction 2026-09-27 (register {@code NZL O1}, PLAN-negative-zero-on-load): <i>"a -0.0
 * gets read as 0.0"</i> — no DOUBLE column filled through the default {@code DataBufferFactory}
 * holds a {@code -0.0}. In this twin every DOUBLE column is one {@link DataBufferDouble} (the
 * default factory maps DOUBLE to it and never swaps buffers), whose store calls
 * {@code DataValueDouble.normalizeForStore}.
 *
 * <p>
 * Every cell is compared as <b>raw bits</b>: {@code -0.0 == 0.0} is {@code true}, so a value
 * comparison could not see the rule.
 * </p>
 */
class NegativeZeroOnLoadTest
{

    private static final int FILLER_ROWS = 30;

    /**
     * A boxed {@code -0.0}, so {@code setValue(int, Object)} / {@code addElement(Object)} is
     * chosen.
     */
    private static final Object BOXED_NEGATIVE_ZERO = -0.0;

    /** A boxed {@code -0.0f}. */
    private static final Object BOXED_NEGATIVE_FLOAT_ZERO = -0.0f;

    private static long rawBits(Object aValue)
    {
        return Double.doubleToRawLongBits(((Number) aValue).doubleValue());
    }


    /** Every channel of the row answers exactly {@code +0.0}. */
    private static void assertPositiveZero(CachedDataTableColumn aCol, long aRow, String aWhere)
    {
        Object raw = aCol.getValue(aRow);
        assertInstanceOf(Double.class, raw, aWhere + ": raw value is a Double");
        assertEquals(0L, rawBits(raw), aWhere + ": raw bits");
        IDataValue typed = aCol.getDataValue(aRow);
        assertEquals(0L, Double.doubleToRawLongBits(typed.getValueAsDouble()),
                aWhere + ": typed bits");
        assertEquals(new DataValueDouble(0.0), typed, aWhere + ": equals DataValueDouble(0.0)");
        assertEquals(0L, Double.doubleToRawLongBits(aCol.presentNumericValue(aRow)),
                aWhere + ": present-value fast path");
        assertEquals(0, aCol.hashCodeAt(aRow), aWhere + ": hashCodeAt (Double.hashCode(0.0))");
        assertEquals("0", typed.getValueAsString(), aWhere + ": text");
    }


    /** Non-vacuity: the one shape this twin has is the one the store rule sits in. */
    @Test
    void theDefaultFactoryBacksADoubleColumnWithDataBufferDouble()
    {
        assertInstanceOf(DataBufferDouble.class,
                DataBufferFactory.get().createColumnBuffer(DataValueType.DOUBLE));
    }


    @Test
    void aLoadedColumnHoldsNoNegativeZero()
    {
        CachedDataTableColumn col = new CachedDataTableColumn(0, DataValueType.DOUBLE);
        col.addElement(-0.0);
        col.addElement(0.0);
        col.addElement(BOXED_NEGATIVE_FLOAT_ZERO);
        col.addElement(BOXED_NEGATIVE_ZERO);
        for (int i = 0; i < FILLER_ROWS; i++)
        {
            col.addElement(i + 0.1);
        }
        col.complete();
        for (long r = 0; r < 4; r++)
        {
            assertPositiveZero(col, r, "row " + r);
        }
        assertEquals(col.hashCodeAt(0), col.hashCodeAt(1));
    }


    @Test
    void aColumnOfNegativeZerosHoldsZeros()
    {
        CachedDataTableColumn col = new CachedDataTableColumn(0, DataValueType.DOUBLE);
        for (int i = 0; i < FILLER_ROWS; i++)
        {
            col.addElement(-0.0);
        }
        col.complete();
        assertPositiveZero(col, 0, "first row");
        assertPositiveZero(col, FILLER_ROWS - 1L, "last row");
    }


    /** The store site, driven directly. */
    @Test
    void theStoreSiteDropsTheZeroSign()
    {
        DataBufferDouble array = new DataBufferDouble();
        array.setDoubleValue(0, -0.0);
        array.setValue(1, BOXED_NEGATIVE_ZERO);
        array.setValue(2, BOXED_NEGATIVE_FLOAT_ZERO);
        for (int i = 0; i < 3; i++)
        {
            assertEquals(0L, Double.doubleToRawLongBits(array.getValueAsDouble(i)), "row " + i);
        }
    }


    /** Controls: the store rule touches the negative zero and the bare NaN, nothing else. */
    @Test
    void everyOtherValueKeepsItsBits()
    {
        double[] kept =
        {
                0.0, -Double.MIN_VALUE, -1.5, MissingValue.MIS_A.asDouble(),
                Double.NEGATIVE_INFINITY
        };
        CachedDataTableColumn col = new CachedDataTableColumn(0, DataValueType.DOUBLE);
        for (double d : kept)
        {
            col.addElement(d);
        }
        // a bare NaN is still MIS (plan 14, BNM N2 (b)): the composed rule keeps it
        col.addElement(Double.NaN);
        col.complete();
        for (int i = 0; i < kept.length; i++)
        {
            Object raw = col.getValue(i);
            if (raw instanceof MissingValue mv)
            {
                assertSame(MissingValue.MIS_A, mv, "row " + i);
            }
            else
            {
                assertEquals(Double.doubleToRawLongBits(kept[i]), rawBits(raw), "row " + i);
            }
        }
        assertSame(MissingValue.MIS,
                DataValueSupport.getMissingValue(col.getDataValue(kept.length)),
                "bare NaN reads MIS");
        assertTrue(col.isMissingOrNull(kept.length));
    }
}
