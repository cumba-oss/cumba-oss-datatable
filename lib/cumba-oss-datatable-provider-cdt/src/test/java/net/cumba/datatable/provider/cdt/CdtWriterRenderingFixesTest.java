package net.cumba.datatable.provider.cdt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.CachedDataTableColumn;
import net.cumba.datatable.impl.ColumnCachedDataTable;
import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pins for three authorised {@link CdtWriter} fixes:
 *
 * <ul>
 * <li>F-prov-03 — an integral double outside the {@code long} range must not be saturated to
 * {@code Long.MAX_VALUE} on the "print without .0" shortcut.</li>
 * <li>F-prov-04 — a SAS TIME value outside {@code [0, 86400)} cannot be represented in a CDT
 * {@code Time} field and must be refused with an {@link IOException} (not wrapped modulo 24 h, not
 * escaping as an unchecked {@code DateTimeException}).</li>
 * <li>F-prov-05 — {@code DATEAMPM} is a SAS <em>datetime</em> format and must be rendered on the
 * DATETIME path, not as a date of {@code SAS_EPOCH + seconds} days.</li>
 * </ul>
 */
class CdtWriterRenderingFixesTest
{

    private static IDataTable numTable(String aColName, String aFormat, double... aValues)
    {
        DataTableColumnMeta.DataTableColumnMetaBuilder cb = DataTableColumnMeta.builder().index(0)
                .name(aColName).type(DataValueType.DOUBLE);
        if (aFormat != null)
        {
            cb.displayFormat(aFormat);
        }
        DataTableMeta md = DataTableMeta.builder().name("T").rowCount(aValues.length)
                .totalRowCount(aValues.length).setColumns(cb.build()).build();
        CachedDataTableColumn col = new CachedDataTableColumn(0, DataValueType.DOUBLE);
        for (double v : aValues)
        {
            col.addElement(v);
        }
        col.complete();
        return new ColumnCachedDataTable(md, col);
    }


    private static String writeToString(IDataTable aTable, Path aTmp) throws IOException
    {
        Path out = aTmp.resolve("out.cdt");
        CdtWriter.write(aTable, out);
        return Files.readString(out, StandardCharsets.UTF_8);
    }

    // ---- F-prov-03 ----------------------------------------------------------------------------


    @Test
    void integralDoubleBeyondLongRangeIsNotSaturated(@TempDir Path tmp) throws IOException
    {
        String text = writeToString(numTable("BIG", null, 1.0E30), tmp);
        assertFalse(text.contains("9223372036854775807"),
                "1.0E30 must not saturate to Long.MAX_VALUE:\n" + text);
        assertTrue(text.contains("1.0E30"), text);
    }


    @Test
    void integralDoubleAtTwoPow63IsNotSaturated(@TempDir Path tmp) throws IOException
    {
        // 2^63 itself is NOT representable as a long (Long.MAX_VALUE is 2^63 - 1)
        String text = writeToString(numTable("BIG", null, 9.223372036854776E18), tmp);
        assertFalse(text.contains("9223372036854775807"), text);
        assertTrue(text.contains("9.223372036854776E18"), text);
    }


    @Test
    void integralDoubleInsideLongRangeStillRendersWithoutFraction(@TempDir Path tmp)
        throws IOException
    {
        // the largest double below 2^63 must still take the long path
        String text = writeToString(numTable("N", null, 42.0, 9.223372036854775E18), tmp);
        assertTrue(text.contains("\n42\n"), text);
        assertTrue(text.contains("9223372036854774784"), text);
    }

    // ---- F-prov-04 ----------------------------------------------------------------------------


    @Test
    void timeOfTwentyFiveHoursIsRefusedNotWrapped(@TempDir Path tmp)
    {
        IDataTable t = numTable("ELAPSED", "TIME8.", 90000.0);
        IOException ex = assertThrows(IOException.class, () -> writeToString(t, tmp));
        assertTrue(ex.getMessage().contains("ELAPSED"), ex.getMessage());
        assertTrue(ex.getMessage().contains("90000"), ex.getMessage());
    }


    @Test
    void negativeTimeIsRefusedWithIOExceptionNotDateTimeException(@TempDir Path tmp)
    {
        IDataTable t = numTable("NEGT", "TIME8.", -3600.0);
        IOException ex = assertThrows(IOException.class, () -> writeToString(t, tmp));
        assertTrue(ex.getMessage().contains("NEGT"), ex.getMessage());
    }


    @Test
    void timeInsideOneDayStillRenders(@TempDir Path tmp) throws IOException
    {
        String text = writeToString(numTable("T1", "TIME8.", 3661.0, 0.0, 86399.0), tmp);
        assertTrue(text.contains("01:01:01"), text);
        assertTrue(text.contains("00:00:00"), text);
        assertTrue(text.contains("23:59:59"), text);
    }

    // ---- F-prov-05 ----------------------------------------------------------------------------


    @Test
    void dateampmIsRenderedAsDatetimeNotDate(@TempDir Path tmp) throws IOException
    {
        // 1893456000 SAS seconds since 1960-01-01 = 2020-01-01T00:00:00
        String text = writeToString(numTable("ADTM", "DATEAMPM23.", 1.893456E9), tmp);
        assertTrue(text.contains("2020-01-01T00:00:00"),
                "DATEAMPM is a datetime format; got:\n" + text);
        assertFalse(text.contains("5185545"),
                "value must not be interpreted as 1.89e9 DAYS on the DATE path:\n" + text);
    }


    @Test
    void plainDateFormatStillClassifiedAsDate(@TempDir Path tmp) throws IOException
    {
        // 21929 SAS days since 1960-01-01 = 2020-01-15
        String text = writeToString(numTable("ADT", "DATE9.", 21929.0), tmp);
        assertEquals(true, text.contains("2020-01-15"), text);
    }
}
