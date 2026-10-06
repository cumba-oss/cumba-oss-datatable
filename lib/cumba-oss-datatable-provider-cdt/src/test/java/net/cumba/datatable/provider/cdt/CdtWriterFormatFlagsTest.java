package net.cumba.datatable.provider.cdt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTableColumnMeta;
import net.cumba.datatable.impl.CachedDataTableColumn;
import net.cumba.datatable.impl.ColumnCachedDataTable;
import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A {@code .cdt} file does not carry the two load-time format annotations: they describe the format
 * catalog of the table as loaded, which the file does not hold, and the parser would hand them back
 * as live values (PLAN-unavailable-display-format-highlight D5).
 */
class CdtWriterFormatFlagsTest
{

    @Test
    void theColumnLineSkipsTheLoadTimeFormatFlags(@TempDir Path aDir) throws IOException
    {
        DataTableColumnMeta sex = DataTableColumnMeta.builder().index(0).name("SEX")
                .type(DataValueType.STRING).displayFormat("$SEXF.")
                .addMetaData(IDataTableColumnMeta.META_KEY_DISPLAY_FORMAT_UNAVAILABLE,
                        IDataTableColumnMeta.DISPLAY_FORMAT_NOT_IN_CATALOG)
                .addMetaData(IDataTableColumnMeta.META_KEY_HAS_UNMAPPED_FORMAT_VALUES, true)
                .addMetaData("origin", "CRF").build();
        DataTableMeta meta = DataTableMeta.builder().name("DM").setColumns(sex).rowCount(1)
                .totalRowCount(1).build();
        CachedDataTableColumn c0 = new CachedDataTableColumn(0, DataValueType.STRING);
        c0.addElement("M");
        c0.complete(1);

        Path out = aDir.resolve("dm.cdt");
        CdtWriter.write(new ColumnCachedDataTable(meta, c0), out);
        String text = Files.readString(out, StandardCharsets.UTF_8);
        assertFalse(text.contains(IDataTableColumnMeta.META_KEY_DISPLAY_FORMAT_UNAVAILABLE), text);
        assertFalse(text.contains(IDataTableColumnMeta.META_KEY_HAS_UNMAPPED_FORMAT_VALUES), text);
        assertTrue(text.contains("origin=CRF"), "other column keys survive: " + text);
        assertTrue(text.contains("$SEXF."), "the display format itself is written: " + text);
    }
}
