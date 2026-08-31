package net.cumba.datatable.provider.dsj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.MissingValue;
import org.junit.jupiter.api.Test;

/**
 * A Dataset-JSON {@code null} is loaded as a {@link MissingValue} in <b>every</b> column type,
 * character included; an empty string the file genuinely contains stays an empty string.
 *
 * <h2>⚠ This supersedes Fix #161's mechanism while keeping its goal</h2>
 * <p>
 * Fix #161 made a blank cell format-independent <em>at ingestion</em> — this provider mapped a
 * {@code null} in a STRING column to {@code ""} so that no consumer could see a difference. Under
 * the settled design the model keeps the distinction (Dataset-JSON can express a character null;
 * CSV, CDT, XLSX, SAS7BDAT and XPT cannot) and the engine is made blind to it <em>at
 * consumption</em>: every blankness consumer asks {@code isEmptyOrMissing()}, and value resolution
 * goes through {@code ScalarSemantics.resolvedString}, which renders a blank character cell as
 * {@code ""} whichever way the file spelled it.
 * <p>
 * ⚠⚠ The fixture carries the {@code null} and the {@code ""} in the <b>same character column</b>.
 * One without the other is vacuous — it passes under either design.
 */
class DsjCharacterNullTest
{

    private static final String FIXTURE = "/fixtures/dsj2/char-null-and-empty.json";

    /** Row 0 — a real value; the control that stops every assertion below being vacuous. */
    private static final long ROW_POPULATED = 0L;

    /** Row 1 — an explicit JSON {@code null} in the character column. */
    private static final long ROW_SOURCE_NULL = 1L;

    /** Row 2 — an empty string the file genuinely contains. */
    private static final long ROW_EMPTY_STRING = 2L;

    private static final int CHARCOL = 0;

    private static final int NUMCOL = 1;

    private IDataTable load() throws IOException
    {
        File tmp = File.createTempFile("dsj2-charnull", ".json");
        tmp.deleteOnExit();
        try (InputStream in = DsjCharacterNullTest.class.getResourceAsStream(FIXTURE))
        {
            assertNotNull(in, "fixture not found: " + FIXTURE);
            Files.copy(in, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        IDataTable table = new DsjTableProvider().provide(URI.create(tmp.toURI().toString()),
                DsjProviderSupplier.FI_DSJ_JSON);

        assertNotNull(table);
        assertEquals(3, table.getRowCount());
        assertEquals(DataValueType.STRING, table.getMetaData().getColumn(CHARCOL).getType());
        assertEquals("A", table.getValue(ROW_POPULATED, CHARCOL),
                "the fixture must carry a real character value, or the blanks below prove nothing");
        return table;
    }


    /** The model half: the two blanks are different things. */
    @Test
    void aJsonNullBecomesAMissingValueEvenInACharacterColumn() throws Exception
    {
        IDataTable table = load();

        assertInstanceOf(MissingValue.class, table.getValue(ROW_SOURCE_NULL, CHARCOL),
                "a JSON null in a CHARACTER column must load as a MissingValue");
        assertTrue(table.getDataValue(ROW_SOURCE_NULL, CHARCOL).isMissingOrInvalid());

        assertEquals("", table.getValue(ROW_EMPTY_STRING, CHARCOL));
        assertFalse(table.getDataValue(ROW_EMPTY_STRING, CHARCOL).isMissingOrInvalid(),
                "an empty string the file genuinely contains must never become a MissingValue");

        // The numeric column is unchanged: a JSON null there was already a MissingValue.
        assertInstanceOf(MissingValue.class, table.getValue(ROW_SOURCE_NULL, NUMCOL));
    }


    /**
     * The engine half — the actual Fix #161 contract. Row 0 is the control: without it a predicate
     * answering "blank" to everything would satisfy this test.
     */
    @Test
    void everyBlanknessConsumerTreatsTheNullAndTheEmptyStringIdentically() throws Exception
    {
        IDataTable table = load();

        for (long row : new long[]
        {
                ROW_SOURCE_NULL, ROW_EMPTY_STRING
        })
        {
            assertTrue(table.isEmptyOrMissing(row, CHARCOL),
                    "IDataTable.isEmptyOrMissing must fold both blanks, row " + row);
            assertTrue(table.getColumn(CHARCOL).isEmptyOrMissing(row),
                    "IDataTableColumn.isEmptyOrMissing must fold both blanks, row " + row);
            assertTrue(table.getDataValue(row, CHARCOL).isEmptyOrMissing(),
                    "IDataValue.isEmptyOrMissing must fold both blanks, row " + row);
        }

        assertFalse(table.isEmptyOrMissing(ROW_POPULATED, CHARCOL),
                "the populated control row must not read blank");
        assertFalse(table.getColumn(CHARCOL).isEmptyOrMissing(ROW_POPULATED),
                "the populated control row must not read blank (column level)");
    }
}
