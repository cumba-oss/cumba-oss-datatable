package net.cumba.datatable.provider.sas.sas7bdat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * PLAN-negative-zero-on-load, reader row "SAS7BDAT" (NZL O1, owner 2026-09-27: <i>"we drop the sign
 * from any 0.0 so a -0.0 gets read as 0.0"</i>). A SAS7BDAT numeric is its raw IEEE bits;
 * {@code BdatVarParser.unpackFloat64} turns {@code 0x8000..} into a {@code -0.0}. After loading,
 * the cell must be {@code +0.0}.
 *
 * <p>
 * This repository has no SAS7BDAT writer (the internal twin writes its fixture with
 * {@code WriterBdat}), so the file is derived in-test from the SAS-written
 * {@code testdata/sas7bdat/01_plain/adsl.sas7bdat}: the 8-byte cell of {@code AVGDD} row 2
 * ({@code 77.7}, whose bytes occur exactly once in the file) is overwritten in a temporary copy
 * with the raw bits of {@code -0.0}. SAS7BDAT pages carry no checksum. {@code AVGDD} row 0 is a
 * {@code +0.0} in the file (the control), row 3 the non-zero control {@code 54.0}.
 * </p>
 */
class NegativeZeroOnLoadBdatTest
{

    private static final long NEGATIVE_ZERO_BITS = 0x8000_0000_0000_0000L;

    private static final String COLUMN = "AVGDD";

    private static final int POSITIVE_ROW = 0;

    private static final int NEGATIVE_ROW = 2;

    private static final int CONTROL_ROW = 3;

    private static final double ORIGINAL = 77.7;

    private static final double CONTROL = 54.0;

    @TempDir
    Path tmp;

    private static File source()
    {
        File f = new File(System.getProperty("repoRoot"),
                "testdata/sas7bdat/01_plain/adsl.sas7bdat");
        assertTrue(f.isFile(), "fixture missing: " + f);
        return f;
    }


    private static byte[] littleEndian(long aBits)
    {
        return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(aBits).array();
    }


    private static List<Integer> offsetsOf(byte[] aHaystack, byte[] aNeedle)
    {
        List<Integer> hits = new ArrayList<>();
        for (int i = 0; i <= aHaystack.length - aNeedle.length; i++)
        {
            if (Arrays.equals(aHaystack, i, i + aNeedle.length, aNeedle, 0, aNeedle.length))
            {
                hits.add(i);
            }
        }
        return hits;
    }


    /** The source's {@code AVGDD} row 2 cell, located by its bytes (asserted unique). */
    private static int assertCellOffset(byte[] aSource)
    {
        List<Integer> hits = offsetsOf(aSource, littleEndian(Double.doubleToRawLongBits(ORIGINAL)));
        assertEquals(1, hits.size(), "77.7's bytes occur exactly once in the source");
        return hits.get(0);
    }


    private Path writePatched() throws IOException
    {
        byte[] bytes = Files.readAllBytes(source().toPath());
        int offset = assertCellOffset(bytes);
        System.arraycopy(littleEndian(NEGATIVE_ZERO_BITS), 0, bytes, offset, 8);
        Path file = tmp.resolve("negzero.sas7bdat");
        Files.write(file, bytes);
        return file;
    }


    /**
     * (1) Non-vacuity: the source's row-2 {@code AVGDD} cell, as the provider reads it, is
     * {@code 77.7}; the patched copy differs from the source in exactly those 8 bytes, which now
     * hold, little-endian, {@code 0x8000..}.
     */
    @Test
    void theFileHoldsTheRawNegativeZeroBits() throws IOException
    {
        IDataTable original = new BdatTableProvider().provide(source().toURI(),
                BdatProviderSupplier.FI_BDAT);
        int col = original.getMetaData().getColumnIndex(COLUMN);
        assertEquals(ORIGINAL, ((Number) original.getValue(NEGATIVE_ROW, col)).doubleValue(),
                "AVGDD row 2 in the source");

        byte[] source = Files.readAllBytes(source().toPath());
        byte[] patched = Files.readAllBytes(writePatched());
        int offset = assertCellOffset(source);
        List<Integer> diff = new ArrayList<>();
        for (int i = 0; i < source.length; i++)
        {
            if (source[i] != patched[i])
            {
                diff.add(i);
            }
        }
        assertTrue(
                diff.size() <= 8 && !diff.isEmpty() && diff.get(0) >= offset
                        && diff.get(diff.size() - 1) < offset + 8,
                "only the cell's 8 bytes differ: " + diff);
        assertEquals(NEGATIVE_ZERO_BITS,
                ByteBuffer.wrap(patched, offset, 8).order(ByteOrder.LITTLE_ENDIAN).getLong(),
                "the cell holds the raw bits 0x8000..");
    }


    /** (2) The loaded cell is {@code +0.0}; controls untouched. */
    @Test
    void aRawNegativeZeroLoadsAsPositiveZero() throws IOException
    {
        IDataTable table = new BdatTableProvider().provide(writePatched().toUri(),
                BdatProviderSupplier.FI_BDAT);
        int col = table.getMetaData().getColumnIndex(COLUMN);
        assertSame(DataValueType.DOUBLE, table.getMetaData().getColumn(col).getType(), COLUMN);
        assertCellBits(table, POSITIVE_ROW, col, 0L, "row 0 (+0.0 control)");
        assertCellBits(table, CONTROL_ROW, col, Double.doubleToRawLongBits(CONTROL),
                "row 3 (54.0 control)");
        assertCellBits(table, NEGATIVE_ROW, col, 0L, "row 2 (raw bits 0x8000..)");
    }


    private static void assertCellBits(IDataTable table, long row, int col, long expectedBits,
            String where)
    {
        Number raw = assertInstanceOf(Number.class, table.getValue(row, col), where);
        assertEquals(expectedBits, Double.doubleToRawLongBits(raw.doubleValue()),
                where + ": raw bits of getValue");
        assertEquals(expectedBits,
                Double.doubleToRawLongBits(table.getDataValue(row, col).getValueAsDouble()),
                where + ": raw bits of getDataValue().getValueAsDouble()");
    }
}
