package net.cumba.datatable.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.databuffer.DataBufferDouble;
import net.cumba.datatable.impl.databuffer.DataBufferObject;
import net.cumba.datatable.values.DataValueDouble;
import net.cumba.datatable.values.DataValueSupport;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.IDataValue;
import net.cumba.datatable.values.MissingValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;

/**
 * ⭐ Owner ruling <b>E5</b>, 2026-09-25: <i>"NaN should get mis"</i> &mdash; a NaN that carries no
 * missing-value marker is {@link MissingValue#MIS} on every path (PLAN-bare-nan-is-mis, OSS phase).
 * The reduced twin of {@code cumba-datatable}'s test of the same name: this repository backs a
 * DOUBLE column with a {@link DataBufferDouble} and never swaps it, so there is ONE column shape,
 * and a {@code null} is refused by that buffer, so ruling H1 (a null stored as
 * {@code MIS_UNKNOWN}'s payload) has nothing to change here.
 *
 * <p>
 * What is "bare" is ruling <b>N1</b>, {@link MissingValue#isBareNaN(double)} (pinned in
 * {@code MissingValueBareNaNTest}, byte-identical in both twins). Ruling <b>N2 (b)</b> stores a
 * bare NaN as {@code MIS}'s payload in {@link DataBufferDouble}, so the raw, hash and typed
 * channels agree by construction; the typed arms of {@code AbstractDataBuffer} (ported from the
 * internal twin) and {@code AbstractNumericDataBuffer} decode with
 * {@link MissingValue#forNaN(double)}.
 * </p>
 */
class BareNanIsMisTest
{

    /**
     * One cell under test and the marker it must read as.
     *
     * @param name
     *            the display name.
     * @param cell
     *            what is stored (a {@code Double} or a {@link MissingValue}).
     * @param marker
     *            the missing value every channel must answer.
     */
    record Probe(String name, Object cell, MissingValue marker)
    {

        @Override
        public String toString()
        {
            return name;
        }


        boolean unrecognised()
        {
            return cell instanceof Double d && !MissingValue.isBareNaN(d)
                    && MissingValue.forValue(d, null) == null;
        }
    }

    static final List<Probe> PROBES = List.of(
            new Probe("bare NaN 0x7FF8", Double.longBitsToDouble(0x7FF8_0000_0000_0000L),
                    MissingValue.MIS),
            new Probe("bare NaN 0xFFF8", Double.longBitsToDouble(0xFFF8_0000_0000_0000L),
                    MissingValue.MIS),
            // N1 control: an unrecognised payload keeps MIS_UNKNOWN
            new Probe("unrecognised 0xFFFF payload",
                    Double.longBitsToDouble(0xFFFF_0000_0000_0000L), MissingValue.MIS_UNKNOWN),
            new Probe("MIS_A payload", MissingValue.MIS_A.asDouble(), MissingValue.MIS_A),
            new Probe("explicit MIS", MissingValue.MIS, MissingValue.MIS));

    static CachedDataTableColumn column(Object aCell)
    {
        CachedDataTableColumn col = new CachedDataTableColumn(0, DataValueType.DOUBLE);
        col.addElement(aCell);
        col.addElement(1.0);
        col.complete();
        return col;
    }


    @ParameterizedTest(name = "{0}")
    @FieldSource("PROBES")
    void everyChannelReadsTheSameMarker(Probe aProbe)
    {
        CachedDataTableColumn col = column(aProbe.cell());
        assertSame(DataBufferDouble.class, col.getDataBuffer().getClass(), aProbe + ": buffer");

        IDataValue typed = col.getDataValue(0);
        assertTrue(typed.isMissingOrInvalid(), aProbe + ": typed read must be missing");
        assertSame(aProbe.marker(), DataValueSupport.getMissingValue(typed), aProbe + ": typed");

        IDataValue wrapped = DataValueSupport.getAsDataValue(col.getValue(0), DataValueType.DOUBLE);
        assertSame(aProbe.marker(), DataValueSupport.getMissingValue(wrapped),
                aProbe + ": default wrap");

        Object raw = col.getValue(0);
        if (aProbe.unrecognised())
        {
            assertInstanceOf(Double.class, raw, aProbe + ": raw");
            assertEquals(Double.doubleToRawLongBits((Double) aProbe.cell()),
                    Double.doubleToRawLongBits((Double) raw),
                    aProbe + ": the raw bits of a non-bare NaN are kept");
        }
        else
        {
            assertSame(aProbe.marker(), raw, aProbe + ": raw value");
            assertEquals(aProbe.marker().hashCodeStable(), col.hashCodeAt(0), aProbe + ": hash");
        }
    }


    /** N2 (b) as the join it protects: a bare NaN keys as an explicit {@code MIS}. */
    @Test
    void aBareNaNKeysAsMisInTheHashAndMatchChannel()
    {
        IDataTable misSide = table(column(MissingValue.MIS));
        int[] key =
        {
                0
        };
        for (Probe p : PROBES)
        {
            IDataTable probeSide = table(column(p.cell()));
            boolean sameKey = p.marker() == MissingValue.MIS;
            KeyHashSupport.KeyColumnMatcher matcher = new KeyHashSupport.KeyColumnMatcher(probeSide,
                    key, misSide, key);
            assertEquals(sameKey, matcher.matches(0, 0), p + ": key match");
            if (sameKey)
            {
                assertEquals(KeyHashSupport.computeKeyHash(misSide, 0, key),
                        KeyHashSupport.computeKeyHash(probeSide, 0, key), p + ": key hash");
            }
        }
    }

    /**
     * A numeric buffer that reports its one cell missing with a {@code null} raw value - what only
     * a custom buffer can do here (a {@link DataBufferDouble} refuses a {@code null}, below). A
     * small fake, not a mock.
     */
    static final class NullRawNumericBuffer
            extends net.cumba.datatable.impl.databuffer.AbstractNumericDataBuffer
    {

        NullRawNumericBuffer()
        {
            size = 1;
        }


        @Override
        public void setExpectedSize(int aLength)
        {
            // fixed size of one
        }


        @Override
        public boolean canStore(@org.jspecify.annotations.Nullable Object aValue)
        {
            return false;
        }


        @Override
        public void setValue(int aIndex, @org.jspecify.annotations.Nullable Object aValue)
        {
            throw new IllegalArgumentException("read-only");
        }


        @Override
        public @org.jspecify.annotations.Nullable Object getValue(int aIndex)
        {
            return null;
        }


        @Override
        public boolean isMissing(int aIndex)
        {
            return true;
        }


        @Override
        public void trimToSize()
        {
            // nothing to trim
        }


        @Override
        public boolean canStoreDouble(double aValue)
        {
            return false;
        }


        @Override
        public boolean canStoreLong(long aValue)
        {
            return false;
        }


        @Override
        public void setDoubleValue(int aIndex, double aValue)
        {
            throw new IllegalArgumentException("read-only");
        }


        @Override
        public void setLongValue(int aIndex, long aValue)
        {
            throw new IllegalArgumentException("read-only");
        }


        @Override
        public double getValueAsDouble(int aIndex)
        {
            return Double.NaN;
        }
    }

    /**
     * Ruling F-RS9 in the OSS twin (owner ruling BNM-F2 (a), 2026-10-12: <i>"I think this already
     * was ruled and aligned in between, please confirm. If still differs, then yes align to
     * MIS_UNKNOWN."</i>): a missing numeric cell whose raw value is {@code null} is
     * {@code MIS_UNKNOWN}, as in cumba-datatable. It answered {@code MIS}.
     */
    @Test
    void aNullRawMissingNumericCellIsMisUnknown()
    {
        NullRawNumericBuffer buf = new NullRawNumericBuffer();
        for (DataValueType type : List.of(DataValueType.DOUBLE, DataValueType.LONG))
        {
            IDataValue v = buf.getDataValue(0, type);
            assertSame(MissingValue.MIS_UNKNOWN, v.getValue(), type + " arm");
        }
    }


    /** The buffer this repository gives a DOUBLE column refuses a {@code null}: no H1 here. */
    @Test
    void aDoubleColumnRefusesANull()
    {
        CachedDataTableColumn col = new CachedDataTableColumn(0, DataValueType.DOUBLE);
        assertThrows(IllegalArgumentException.class, () -> col.addElement((Object) null));
    }


    /**
     * The object buffer hands a boxed NaN out undecoded, so its typed arms decode it. Before the
     * port a boxed NaN read back as a PRESENT {@code DataValueLong(0)} on the LONG arm and a
     * {@code DataValueString("NaN")} on the STRING arm.
     */
    @Test
    void anObjectBufferDecodesABoxedNaNOnEveryArm()
    {
        for (Probe p : PROBES)
        {
            if (!(p.cell() instanceof Double d))
            {
                continue;
            }
            DataBufferObject buf = new DataBufferObject();
            buf.setValue(0, (Object) d);
            for (DataValueType type : List.of(DataValueType.LONG, DataValueType.STRING,
                    DataValueType.OTHER))
            {
                assertSame(p.marker(), buf.getDataValue(0, type).getValue(), p + " / " + type);
            }
            IDataValue asDouble = buf.getDataValue(0, DataValueType.DOUBLE);
            assertInstanceOf(DataValueDouble.class, asDouble, p + ": DOUBLE arm keeps the carrier");
            assertSame(p.marker(), DataValueSupport.getMissingValue(asDouble), p + " / DOUBLE");
        }
    }


    private static IDataTable table(CachedDataTableColumn aColumn)
    {
        DataTableColumnMeta c = DataTableColumnMeta.builder().index(0).name("X")
                .type(DataValueType.DOUBLE).build();
        DataTableMeta meta = DataTableMeta.builder().name("T").columns(new DataTableColumnMeta[]
        {
                c
        }).rowCount(2).totalRowCount(2).build();
        return new ColumnCachedDataTable(meta, aColumn);
    }
}
