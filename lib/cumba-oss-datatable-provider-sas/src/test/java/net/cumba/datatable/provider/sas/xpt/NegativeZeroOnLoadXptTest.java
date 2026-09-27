package net.cumba.datatable.provider.sas.xpt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * PLAN-negative-zero-on-load, reader row "XPT" (NZL O1, owner 2026-09-27: <i>"we drop the sign from
 * any 0.0 so a -0.0 gets read as 0.0"</i>). An IBM zero with the sign bit set ({@code 80 00 ..})
 * decodes to {@code -0.0} in {@code XptVarParser.ibmToIeee}; after loading, the cell must be
 * {@code +0.0}, at full width (8) and truncated (3).
 *
 * <p>
 * The file is crafted in-test (this repository has no XPT writer; the layout mirrors the internal
 * twin's {@code WriterXpt} output byte for byte, datetime stamps aside), and its observation bytes
 * are asserted before the provider reads it. Variables {@code V8} (width 8) and {@code V3} (width
 * 3); row 0 {@code -0.0}, row 1 the {@code +0.0} control, row 2 the non-zero control {@code -1.5}.
 * </p>
 */
class NegativeZeroOnLoadXptTest
{

    private static final double CONTROL = -1.5;

    /**
     * The three 11-byte observations as the XPT format stores them: IBM {@code -0.0} is
     * {@code 80 00..}, {@code +0.0} all zero, IBM {@code -1.5} is {@code C1 18 00..}; each first at
     * width 8, then truncated to width 3.
     */
    private static final byte[] OBSERVATIONS =
    {
            (byte) 0x80, 0, 0, 0, 0, 0, 0, 0, (byte) 0x80, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            (byte) 0xC1, 0x18, 0, 0, 0, 0, 0, 0, (byte) 0xC1, 0x18, 0
    };

    @TempDir
    Path tmp;

    private Path writeXpt() throws IOException
    {
        Path file = tmp.resolve("negzero.xpt");
        Files.write(file, xptBytes());
        return file;
    }


    /**
     * The XPT v5 transport file, crafted byte by byte (this repository has no XPT writer). The
     * layout is the one the internal {@code WriterXpt} writes for the same two variables and three
     * rows (SAS TS-140): library, member and descriptor headers, two 140-byte namestr records
     * padded to 80, the OBS header, then {@link #OBSERVATIONS} padded with blanks.
     */
    private static byte[] xptBytes()
    {
        String stamp = "27SEP26:00:00:00";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        record80(out, "HEADER RECORD*******LIBRARY HEADER RECORD!!!!!!!"
                + "000000000000000000000000000000  ");
        record80(out, "SAS     SAS     SASLIB  9.4     Linux" + " ".repeat(27) + stamp);
        record80(out, stamp);
        record80(out, "HEADER RECORD*******MEMBER  HEADER RECORD!!!!!!!"
                + "000000000000000001600000000140  ");
        record80(out, "HEADER RECORD*******DSCRPTR HEADER RECORD!!!!!!!"
                + "000000000000000000000000000000  ");
        record80(out, "SAS     NZ      SASDATA 9.4     Linux" + " ".repeat(27) + stamp);
        record80(out, stamp);
        record80(out, "HEADER RECORD*******NAMESTR HEADER RECORD!!!!!!!"
                + "000000000200000000000000000000  ");
        ByteArrayOutputStream namestrs = new ByteArrayOutputStream();
        namestrs.writeBytes(namestr(1, "V8", 8, 0));
        namestrs.writeBytes(namestr(2, "V3", 3, 8));
        padded80(out, namestrs.toByteArray());
        record80(out, "HEADER RECORD*******OBS     HEADER RECORD!!!!!!!"
                + "000000000000000000000000000000  ");
        padded80(out, OBSERVATIONS);
        return out.toByteArray();
    }


    /** One numeric namestr record (140 bytes, big-endian shorts). */
    private static byte[] namestr(int aVarNum, String aName, int aLength, int aPosition)
    {
        ByteBuffer b = ByteBuffer.allocate(140);
        b.putShort((short) 1).putShort((short) 0).putShort((short) aLength)
                .putShort((short) aVarNum);
        b.put(blankPadded(aName, 8)).put(blankPadded("", 40)).put(blankPadded("", 8));
        b.putShort((short) 0).putShort((short) 0).putShort((short) 0).putShort((short) 0);
        b.put(blankPadded("", 8));
        b.putShort((short) 0).putShort((short) 0).putInt(aPosition);
        return b.array();
    }


    private static byte[] blankPadded(String aText, int aWidth)
    {
        return (aText + " ".repeat(aWidth - aText.length())).getBytes(StandardCharsets.US_ASCII);
    }


    private static void record80(ByteArrayOutputStream aOut, String aText)
    {
        aOut.writeBytes(blankPadded(aText, 80));
    }


    private static void padded80(ByteArrayOutputStream aOut, byte[] aBytes)
    {
        aOut.writeBytes(aBytes);
        int rest = (80 - aBytes.length % 80) % 80;
        aOut.writeBytes(blankPadded("", rest));
    }


    /**
     * (1) Non-vacuity: the file's observation records hold the IBM negative zero {@code 80 00..} at
     * both widths, and the reader's own decode turns it into {@code -0.0}.
     */
    @Test
    void theFileHoldsAnIbmNegativeZero() throws IOException
    {
        byte[] bytes = Files.readAllBytes(writeXpt());
        assertTrue(indexOf(bytes, OBSERVATIONS) > 0,
                "the observation bytes (80 00.. at width 8 and 3) must be in the file");
        assertEquals(0x8000_0000_0000_0000L,
                Double.doubleToRawLongBits(XptVarParser.ibmToIeee(new byte[]
                {
                        (byte) 0x80, 0, 0
                }, 0, 3)), "the reader's decode of 80 00 00 is -0.0");
    }


    /** (2) The loaded width-8 cell is {@code +0.0}; controls untouched. */
    @Test
    void anIbmNegativeZeroLoadsAsPositiveZeroAtWidth8() throws IOException
    {
        assertColumnLoadsPositiveZero("V8");
    }


    /** (2) The loaded width-3 cell is {@code +0.0}; controls untouched. */
    @Test
    void anIbmNegativeZeroLoadsAsPositiveZeroAtWidth3() throws IOException
    {
        assertColumnLoadsPositiveZero("V3");
    }


    private void assertColumnLoadsPositiveZero(String name) throws IOException
    {
        IDataTable table = new XptTableProvider().provide(writeXpt().toUri(),
                XptProviderSupplier.FI_XPT);
        assertEquals(3L, table.getRowCount());
        int col = table.getMetaData().getColumnIndex(name);
        assertSame(DataValueType.DOUBLE, table.getMetaData().getColumn(col).getType(), name);
        assertCellBits(table, 1, col, 0L, name + " row 1 (+0.0 control)");
        assertCellBits(table, 2, col, Double.doubleToRawLongBits(CONTROL),
                name + " row 2 (-1.5 control)");
        assertCellBits(table, 0, col, 0L, name + " row 0 (IBM 80 00..)");
    }


    private static int indexOf(byte[] aHaystack, byte[] aNeedle)
    {
        for (int i = 0; i <= aHaystack.length - aNeedle.length; i++)
        {
            if (Arrays.equals(aHaystack, i, i + aNeedle.length, aNeedle, 0, aNeedle.length))
            {
                return i;
            }
        }
        return -1;
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
