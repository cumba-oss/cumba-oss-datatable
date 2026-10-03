package net.cumba.datatable.provider.sas.xpt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.library.IDataTableLibrary;
import net.cumba.datatable.provider.sas.xpt.library.XptLibrary;
import net.cumba.datatable.provider.sas.xpt.library.XptLibraryMember;
import net.cumba.datatable.provider.sas.xpt.library.XptLibraryProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The charset a caller chooses must reach the decoder. An XPT file records no encoding, so
 * {@link XptTableProvider#PROP_CHARSET} is the only way to read a non-ASCII file correctly, and a
 * library member carries the charset its library was opened with. Each arm loads a one-row,
 * one-variable file whose single character cell holds the byte {@code 0xE9}: {@code "é"} in
 * windows-1252, a malformed sequence under UTF-8 (decoded as {@code U+FFFD}).
 *
 * <p>
 * The file is crafted in-test, as {@code NegativeZeroOnLoadXptTest} does for a numeric variable:
 * library, member and descriptor headers, one 140-byte CHAR(8) namestr padded to 80, the OBS
 * header, then the single observation padded with blanks.
 * </p>
 */
class XptCharsetReachesTheDecoderTest
{

    /** {@code "Café"} in windows-1252, blank-padded to the variable's width of 8. */
    private static final byte[] ROW =
    {
            'C', 'a', 'f', (byte) 0xE9, ' ', ' ', ' ', ' '
    };

    @TempDir
    Path tmp;

    private Path xpt() throws IOException
    {
        Path file = tmp.resolve("charset.xpt");
        Files.write(file, xptBytes());
        return file;
    }


    /** Sensitivity arm: under the default UTF-8 the byte is malformed and decodes as U+FFFD. */
    @Test
    void sensitivityArm_utf8DecodesTheByteAsReplacement() throws IOException
    {
        IDataTable t = new XptTableProvider().provide(xpt().toUri(), XptProviderSupplier.FI_XPT);
        assertEquals("Caf�", t.getValue(0, 0));
    }


    /** The URI path: the property map's charset decides the decoded text. */
    @Test
    void theUriPathHonoursTheCharsetProperty() throws IOException
    {
        IDataTable t = new XptTableProvider().provide(xpt().toUri(), XptProviderSupplier.FI_XPT,
                Map.of(XptTableProvider.PROP_CHARSET, "windows-1252"));
        assertEquals("Café", t.getValue(0, 0));
    }


    /**
     * The library path: a member stamped with the library's charset is decoded with it, even when
     * the member load itself passes no property.
     */
    @Test
    void aLibraryMembersCharsetReachesTheDecoder() throws IOException
    {
        try (IDataTableLibrary lib = new XptLibraryProvider().provide(xpt().toUri(),
                XptProviderSupplier.FI_XPT, Map.of(XptTableProvider.PROP_CHARSET, "windows-1252")))
        {
            XptLibraryMember m = ((XptLibrary) lib).getMembers().toList().get(0);
            // non-vacuity: the member really carries the chosen charset
            assertEquals("windows-1252", m.getCharset().name());
            IDataTable t = new XptTableProvider().provide(m, XptProviderSupplier.FI_XPT, Map.of());
            assertEquals("Café", t.getValue(0, 0));
        }
    }


    /**
     * The XPT v5 transport file, crafted byte by byte: one CHAR(8) variable {@code TXT}
     * ({@code ntype = 2}) and one observation, {@link #ROW}.
     */
    private static byte[] xptBytes()
    {
        String stamp = "01OCT26:00:00:00";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        record80(out, "HEADER RECORD*******LIBRARY HEADER RECORD!!!!!!!"
                + "000000000000000000000000000000  ");
        record80(out, "SAS     SAS     SASLIB  9.4     Linux" + " ".repeat(27) + stamp);
        record80(out, stamp);
        record80(out, "HEADER RECORD*******MEMBER  HEADER RECORD!!!!!!!"
                + "000000000000000001600000000140  ");
        record80(out, "HEADER RECORD*******DSCRPTR HEADER RECORD!!!!!!!"
                + "000000000000000000000000000000  ");
        record80(out, "SAS     CS      SASDATA 9.4     Linux" + " ".repeat(27) + stamp);
        record80(out, stamp);
        record80(out, "HEADER RECORD*******NAMESTR HEADER RECORD!!!!!!!"
                + "000000000100000000000000000000  ");
        padded80(out, namestr(1, "TXT", 8, 0));
        record80(out, "HEADER RECORD*******OBS     HEADER RECORD!!!!!!!"
                + "000000000000000000000000000000  ");
        padded80(out, ROW);
        return out.toByteArray();
    }


    /** One character namestr record (140 bytes, big-endian shorts, {@code ntype = 2}). */
    private static byte[] namestr(int aVarNum, String aName, int aLength, int aPosition)
    {
        ByteBuffer b = ByteBuffer.allocate(140);
        b.putShort((short) 2).putShort((short) 0).putShort((short) aLength)
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
}
