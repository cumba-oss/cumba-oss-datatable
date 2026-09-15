package net.cumba.datatable.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.Test;

/**
 * {@link SyntheticDataTable} — the column TYPES are the point of this class, so they are what gets
 * asserted. A synthetic table that declares every column {@code STRING} regardless of what the
 * variable is, which is what this class replaced, is a fixture lying about the standard; once a
 * column-type gate exists that lie stops being harmless and starts erroring correctly-authored
 * rules.
 */
class SyntheticDataTableTest
{

    @Test
    void theStringOnlyConstructorDeclaresEveryColumnString()
    {
        SyntheticDataTable t = new SyntheticDataTable("DM", List.of("USUBJID", "AGE"), new String[]
        {
                "A", "B"
        }, 4);

        assertEquals(4, t.getRowCount());
        assertEquals(2, t.getMetaData().getColumnCount());
        assertEquals(DataValueType.STRING, t.getMetaData().getColumn(0).getType());
        assertEquals(DataValueType.STRING, t.getMetaData().getColumn(1).getType());
        assertEquals(List.of("USUBJID", "AGE"), t.columnNames());
    }


    @Test
    void aNumericColumnIsDeclaredNumericAndHoldsNumbersNotText()
    {
        Map<String, DataValueType> cols = new LinkedHashMap<>();
        cols.put("USUBJID", DataValueType.STRING);
        cols.put("AGE", DataValueType.DOUBLE);
        cols.put("VISITNUM", DataValueType.LONG);
        SyntheticDataTable t = new SyntheticDataTable("DM", cols, 5);

        assertEquals(DataValueType.STRING, t.getMetaData().getColumn(0).getType());
        assertEquals(DataValueType.DOUBLE, t.getMetaData().getColumn(1).getType());
        assertEquals(DataValueType.LONG, t.getMetaData().getColumn(2).getType());

        // ⭐ The declared type and the stored VALUE must agree. Declaring a column numeric while
        // still storing "A" in it would swap a fixture that lies about the type for one that lies
        // about the value — no better than what this class replaced.
        for (long r = 0; r < t.getRowCount(); r++)
        {
            Object age = t.getValue(r, 1);
            if (age != null)
            {
                assertInstanceOf(Double.class, age, "a DOUBLE column must hold a Double");
            }
            Object visit = t.getValue(r, 2);
            if (visit != null)
            {
                assertInstanceOf(Long.class, visit, "a LONG column must hold a Long");
            }
            Object id = t.getValue(r, 0);
            assertInstanceOf(String.class, id, "a STRING column must hold a String");
        }
    }


    @Test
    void bothKindsOfColumnExerciseAMissingCell()
    {
        // A harness that never produces a missing cell silently stops testing the missing-value
        // contract, so this is asserted rather than assumed.
        Map<String, DataValueType> cols = new LinkedHashMap<>();
        cols.put("TEXT", DataValueType.STRING);
        cols.put("NUM", DataValueType.DOUBLE);
        SyntheticDataTable t = new SyntheticDataTable("DM", cols, 5);

        boolean blankText = false;
        boolean nullNum = false;
        for (long r = 0; r < t.getRowCount(); r++)
        {
            blankText |= "".equals(t.getValue(r, 0));
            nullNum |= t.getValue(r, 1) == null;
        }
        assertTrue(blankText, "a STRING column must cycle through the empty-string missing marker");
        assertTrue(nullNum, "a numeric column must cycle through a null missing marker");
    }


    @Test
    void withNumericDeclaresOnlyTheNamedColumnsNumeric()
    {
        SyntheticDataTable t = SyntheticDataTable.withNumeric("LB",
                List.of("USUBJID", "LBSTRESN", "LBSTRESC"), Set.of("LBSTRESN"), 3);

        assertEquals(DataValueType.STRING, t.getMetaData().getColumn(0).getType());
        assertEquals(DataValueType.DOUBLE, t.getMetaData().getColumn(1).getType());
        assertEquals(DataValueType.STRING, t.getMetaData().getColumn(2).getType());
    }


    @Test
    void columnsHoldDifferentValuesOnTheSameRow()
    {
        // The cycle is offset by column index on purpose: two columns holding identical values on
        // every row would make a comparison between them trivially true and hide a real defect.
        SyntheticDataTable t = new SyntheticDataTable("DM", List.of("A", "B"), new String[]
        {
                "x", "y"
        }, 2);
        assertEquals("x", t.getValue(0, 0));
        assertEquals("y", t.getValue(0, 1));
    }


    @Test
    void aRowOutsideTheTableThrowsRatherThanReturningNull()
    {
        SyntheticDataTable t = new SyntheticDataTable("DM", List.of("A"), new String[]
        {
                "x"
        }, 2);
        assertThrows(IndexOutOfBoundsException.class, () -> t.getValue(2, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> t.getValue(-1, 0));
    }

    // ⚠ REDUCTION (register R2): the internal twin ends with noFormatCatalogIsConfigured(),
    // which pins IDataTable's format-catalog defaults. net.cumba.datatable.formats is absent
    // from this distribution by policy, so IDataTable here declares neither getFormatCatalog()
    // nor getFormatAt(int) and the test cannot exist. The class under test is unaffected — it
    // never touches the catalog; only this assertion about the interface default is dropped.
}
