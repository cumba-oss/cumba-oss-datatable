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
import net.cumba.datatable.values.DataValueSupport;
import net.cumba.datatable.values.MissingValue;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Owner ruling B2-Q1 (2026-10-16, ⓐ <i>"yes MIS_ERROR"</i>): SAS itself has no infinity value, but
 * a SAS7BDAT numeric is its raw IEEE bits, so a file can hold {@code 0x7FF0..} / {@code 0xFFF0..}
 * (ReadStat and haven write it). Neither represents a real value, so the cell loads as
 * {@link MissingValue#MIS_ERROR} &mdash; through each of the provider's three load paths: the
 * default parallel page pipeline over a memory-mapped file, the parallel pipeline over a stream
 * ({@code -Dbdat.mmap=false}), and the sequential loop ({@code -Dbdat.parallel=false}).
 *
 * <p>
 * This repository has no SAS7BDAT writer (the internal twin writes its fixture with
 * {@code WriterBdat}), so the file is derived in-test from the SAS-written
 * {@code testdata/sas7bdat/01_plain/adsl.sas7bdat}, as {@code NegativeZeroOnLoadBdatTest} does: in
 * a temporary copy, three {@code AVGDD} cells whose bytes each occur exactly once in the file are
 * overwritten &mdash; row 2 ({@code 77.7}) with {@code +Infinity}, row 22 ({@code 76.8}) with
 * {@code -Infinity}, row 24 ({@code 76.3}) with the largest finite double (stays data). Row 3
 * ({@code 54.0}) is the untouched control. SAS7BDAT pages carry no checksum.
 * </p>
 */
class InfinityOnLoadBdatTest
{

    private static final String COLUMN = "AVGDD";

    private static final double CONTROL = 54.0;

    private static final int CONTROL_ROW = 3;

    private static final int MAX_ROW = 24;

    /** {row, original value, raw replacement bits}; the two infinities first. */
    private static final Object[][] PATCHES =
    {
            {
                    2, 77.7, Double.doubleToRawLongBits(Double.POSITIVE_INFINITY)
            },
            {
                    22, 76.8, Double.doubleToRawLongBits(Double.NEGATIVE_INFINITY)
            },
            {
                    MAX_ROW, 76.3, Double.doubleToRawLongBits(Double.MAX_VALUE)
            }
    };

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


    /** The source cell holding {@code aOriginal}, located by its bytes (asserted unique). */
    private static int assertCellOffset(byte[] aSource, double aOriginal)
    {
        List<Integer> hits = offsetsOf(aSource,
                littleEndian(Double.doubleToRawLongBits(aOriginal)));
        assertEquals(1, hits.size(), aOriginal + "'s bytes occur exactly once in the source");
        return hits.get(0);
    }


    private Path writePatched() throws IOException
    {
        byte[] bytes = Files.readAllBytes(source().toPath());
        for (Object[] p : PATCHES)
        {
            int offset = assertCellOffset(bytes, (Double) p[1]);
            System.arraycopy(littleEndian((Long) p[2]), 0, bytes, offset, 8);
        }
        Path file = tmp.resolve("infinity.sas7bdat");
        Files.write(file, bytes);
        return file;
    }


    /**
     * (1) Non-vacuity: the three source cells, as the provider reads them, hold their original
     * values; the patched copy differs from the source only inside those three 8-byte cells, which
     * now hold, little-endian, {@code 0x7FF0..}, {@code 0xFFF0..} and {@code Double.MAX_VALUE}.
     */
    @Test
    void theFileHoldsTheRawInfinityBits() throws IOException
    {
        IDataTable original = new BdatTableProvider().provide(source().toURI(),
                BdatProviderSupplier.FI_BDAT);
        int col = original.getMetaData().getColumnIndex(COLUMN);
        byte[] source = Files.readAllBytes(source().toPath());
        byte[] patched = Files.readAllBytes(writePatched());
        List<int[]> cells = new ArrayList<>();
        for (Object[] p : PATCHES)
        {
            int row = (Integer) p[0];
            double value = (Double) p[1];
            assertEquals(value, ((Number) original.getValue(row, col)).doubleValue(),
                    COLUMN + " row " + row + " in the source");
            int offset = assertCellOffset(source, value);
            assertEquals((Long) p[2],
                    ByteBuffer.wrap(patched, offset, 8).order(ByteOrder.LITTLE_ENDIAN).getLong(),
                    "row " + row + ": the cell holds the replacement bits");
            cells.add(new int[]
            {
                    offset, offset + 8
            });
        }
        for (int i = 0; i < source.length; i++)
        {
            if (source[i] != patched[i])
            {
                final int at = i;
                assertTrue(cells.stream().anyMatch(c -> at >= c[0] && at < c[1]),
                        "byte " + i + " differs outside the three patched cells");
            }
        }
    }


    /** (2) Default path (parallel page pipeline, memory-mapped). */
    @Test
    void anInfinityLoadsAsAnErrorParallelMmap() throws IOException
    {
        assertLoadsError(null, null);
    }


    /** (2) Parallel page pipeline over a stream ({@code -Dbdat.mmap=false}). */
    @Test
    void anInfinityLoadsAsAnErrorParallelStream() throws IOException
    {
        assertLoadsError(null, "false");
    }


    /** (2) Sequential loop ({@code -Dbdat.parallel=false}). */
    @Test
    void anInfinityLoadsAsAnErrorSequential() throws IOException
    {
        assertLoadsError("false", null);
    }


    private void assertLoadsError(@Nullable String aParallel, @Nullable String aMmap)
        throws IOException
    {
        Path file = writePatched();
        String oldParallel = System.getProperty("bdat.parallel");
        String oldMmap = System.getProperty("bdat.mmap");
        IDataTable table;
        try
        {
            setOrClear("bdat.parallel", aParallel);
            setOrClear("bdat.mmap", aMmap);
            table = new BdatTableProvider().provide(file.toUri(), BdatProviderSupplier.FI_BDAT);
        }
        finally
        {
            setOrClear("bdat.parallel", oldParallel);
            setOrClear("bdat.mmap", oldMmap);
        }
        int col = table.getMetaData().getColumnIndex(COLUMN);
        for (int p = 0; p < 2; p++)
        {
            int r = (Integer) PATCHES[p][0];
            assertSame(MissingValue.MIS_ERROR, table.getValue(r, col), "row " + r + ": getValue");
            assertSame(MissingValue.MIS_ERROR,
                    DataValueSupport.getMissingValue(table.getDataValue(r, col)),
                    "row " + r + ": getDataValue");
        }
        assertEquals(Double.MAX_VALUE,
                assertInstanceOf(Number.class, table.getValue(MAX_ROW, col)).doubleValue(),
                "row " + MAX_ROW + " (largest finite double) stays data");
        assertEquals(CONTROL,
                assertInstanceOf(Number.class, table.getValue(CONTROL_ROW, col)).doubleValue(),
                "row " + CONTROL_ROW + " (54.0 control)");
    }


    private static void setOrClear(String aKey, @Nullable String aValue)
    {
        if (aValue == null)
        {
            System.clearProperty(aKey);
        }
        else
        {
            System.setProperty(aKey, aValue);
        }
    }
}
