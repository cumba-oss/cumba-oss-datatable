package net.cumba.datatable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.Test;

class DefaultDataTableMetaTest
{

    private DataTableColumnMeta col(int index, String name)
    {
        return DataTableColumnMeta.builder().index(index).name(name).type(DataValueType.STRING)
                .build();
    }


    @Test
    void testBuilder()
    {
        DataTableColumnMeta c0 = col(0, "col0");
        DataTableColumnMeta c1 = col(1, "col1");

        DataTableMeta meta = DataTableMeta.builder().name("table1").label("Table One").rowCount(10)
                .totalRowCount(100).setColumns(c0, c1).build();

        assertEquals("table1", meta.getName());
        assertEquals("Table One", meta.getLabel());
        assertEquals(10, meta.getRowCount());
        assertEquals(100, meta.getTotalRowCount());
        assertEquals(2, meta.getColumnCount());
    }


    @Test
    void testBuilderDefaults()
    {
        DataTableMeta meta = DataTableMeta.builder().setColumns(col(0, "col0")).build();

        assertEquals(0, meta.getRowCount());
        assertEquals(0, meta.getTotalRowCount());
        assertFalse(meta.isColumnNameCaseSensitive());
    }


    @Test
    void testGetColumnByIndex()
    {
        DataTableColumnMeta c0 = col(0, "alpha");
        DataTableColumnMeta c1 = col(1, "beta");

        DataTableMeta meta = DataTableMeta.builder().setColumns(c0, c1).build();

        assertEquals("alpha", meta.getColumn(0).getName());
        assertEquals("beta", meta.getColumn(1).getName());
    }


    @Test
    void testGetColumnByIndexOutOfBounds()
    {
        DataTableMeta meta = DataTableMeta.builder().setColumns(col(0, "col0")).build();

        assertThrows(IndexOutOfBoundsException.class, () -> meta.getColumn(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> meta.getColumn(5));
    }


    @Test
    void testGetColumnByName()
    {
        DataTableMeta meta = DataTableMeta.builder().setColumns(col(0, "Alpha"), col(1, "Beta"))
                .build();

        // Default is case insensitive
        DataTableColumnMeta found = meta.getColumn("alpha");
        assertNotNull(found);
        assertEquals("Alpha", found.getName());

        found = meta.getColumn("BETA");
        assertNotNull(found);
        assertEquals("Beta", found.getName());
    }


    @Test
    void testGetColumnByNameCaseSensitive()
    {
        DataTableMeta meta = DataTableMeta.builder().setColumns(col(0, "Alpha"), col(1, "Beta"))
                .columnNameCaseSensitive(true).build();

        assertNotNull(meta.getColumn("Alpha"));
        assertThrows(java.util.NoSuchElementException.class, () -> meta.getColumn("alpha"));
        assertThrows(java.util.NoSuchElementException.class, () -> meta.getColumn("ALPHA"));
    }


    @Test
    void testGetColumnByNameNotFound()
    {
        DataTableMeta meta = DataTableMeta.builder().setColumns(col(0, "col0")).build();

        assertThrows(java.util.NoSuchElementException.class, () -> meta.getColumn("nonexistent"));
    }


    @Test
    void testGetColumnIndex()
    {
        DataTableMeta meta = DataTableMeta.builder()
                .setColumns(col(0, "first"), col(1, "second"), col(2, "third")).build();

        assertEquals(0, meta.getColumnIndex("first"));
        assertEquals(1, meta.getColumnIndex("second"));
        assertEquals(2, meta.getColumnIndex("third"));
    }


    @Test
    void testGetColumnIndexNotFound()
    {
        DataTableMeta meta = DataTableMeta.builder().setColumns(col(0, "col0")).build();

        assertEquals(-1, meta.getColumnIndex("missing"));
    }


    @Test
    void testGetMetaData()
    {
        DataTableMeta meta = DataTableMeta.builder().setColumns(col(0, "col0"))
                .metaTable(new Object[]
                {
                        "key1", "value1", "key2", 42
                }).build();

        assertEquals("value1", meta.getMetaData("key1"));
        assertEquals(42, meta.getMetaData("key2"));
    }


    @Test
    void testGetMetaDataNotFound()
    {
        DataTableMeta meta = DataTableMeta.builder().setColumns(col(0, "col0"))
                .metaTable(new Object[]
                {
                        "key1", "value1"
                }).build();

        assertNull(meta.getMetaData("nonexistent"));
    }


    @Test
    void testGetMetaDataDefault()
    {
        DataTableMeta meta = DataTableMeta.builder().setColumns(col(0, "col0"))
                .metaTable(new Object[]
                {
                        "key1", "value1"
                }).build();

        assertEquals("value1", meta.getMetaData("key1", "fallback"));
        assertEquals("fallback", meta.getMetaData("missing", "fallback"));
    }


    @Test
    void testGetMetaDataNullTable()
    {
        DataTableMeta meta = DataTableMeta.builder().setColumns(col(0, "col0")).build();

        assertNull(meta.getMetaData("key1"));
        assertEquals("default", meta.getMetaData("key1", "default"));
    }


    @Test
    void testGetMetaDataKeys()
    {
        DataTableMeta meta = DataTableMeta.builder().setColumns(col(0, "col0"))
                .metaTable(new Object[]
                {
                        "alpha", 1, "beta", 2, "gamma", 3
                }).build();

        Collection<String> keys = meta.getMetaDataKeys();
        assertEquals(3, keys.size());
        assertTrue(keys.contains("alpha"));
        assertTrue(keys.contains("beta"));
        assertTrue(keys.contains("gamma"));
    }


    @Test
    void testGetMetaDataKeysEmpty()
    {
        DataTableMeta meta = DataTableMeta.builder().setColumns(col(0, "col0")).build();

        Collection<String> keys = meta.getMetaDataKeys();
        assertNotNull(keys);
        assertTrue(keys.isEmpty());
    }


    @Test
    void testGetColumns()
    {
        DataTableColumnMeta c0 = col(0, "col0");
        DataTableColumnMeta c1 = col(1, "col1");

        DataTableMeta meta = DataTableMeta.builder().setColumns(c0, c1).build();

        DataTableColumnMeta[] columns = meta.getColumns();
        assertEquals(2, columns.length);
        assertEquals("col0", columns[0].getName());
        assertEquals("col1", columns[1].getName());

        // Verify it is a copy (modifying the returned array should not affect the meta)
        columns[0] = null;
        assertNotNull(meta.getColumns()[0]);
    }


    @Test
    void testToBuilder()
    {
        DataTableMeta original = DataTableMeta.builder().name("original").label("Original Label")
                .setColumns(col(0, "col0")).rowCount(5).totalRowCount(5).build();

        DataTableMeta modified = original.toBuilder().name("modified").rowCount(10)
                .totalRowCount(10).build();

        assertEquals("modified", modified.getName());
        assertEquals(10, modified.getRowCount());
        // Unchanged fields should carry over
        assertEquals("Original Label", modified.getLabel());
        assertEquals(1, modified.getColumnCount());

        // Original should be unaffected
        assertEquals("original", original.getName());
        assertEquals(5, original.getRowCount());
    }


    @Test
    void testBuilderAddMetaData()
    {
        DataTableMeta meta = DataTableMeta.builder().setColumns(col(0, "col0"))
                .addMetaData("key1", "value1").addMetaData("key2", 42).build();

        assertEquals("value1", meta.getMetaData("key1"));
        assertEquals(42, meta.getMetaData("key2"));
        assertEquals(2, meta.getMetaDataKeys().size());
    }


    @Test
    void testBuilderColumnsValidation()
    {
        // Column with wrong index should throw
        DataTableColumnMeta wrongIndex = DataTableColumnMeta.builder().index(5).name("wrong")
                .type(DataValueType.STRING).build();

        assertThrows(IllegalArgumentException.class,
                () -> DataTableMeta.builder().setColumns(col(0, "col0"), wrongIndex).build());
    }


    @Test
    void testBuilderColumnsNullElement()
    {
        assertThrows(IllegalArgumentException.class,
                () -> DataTableMeta.builder().setColumns(col(0, "col0"), null).build());
    }


    @Test
    void testBuilderUpdateColumn()
    {
        DataTableColumnMeta replacement = DataTableColumnMeta.builder().index(1).name("replaced")
                .type(DataValueType.STRING).build();

        DataTableMeta meta = DataTableMeta.builder()
                .setColumns(col(0, "col0"), col(1, "col1"), col(2, "col2"))
                .updateColumn(1, replacement).build();

        assertEquals("replaced", meta.getColumn(1).getName());
        assertEquals("col0", meta.getColumn(0).getName());
        assertEquals("col2", meta.getColumn(2).getName());
    }


    @Test
    void testBuilderUpdateColumnOutOfBounds()
    {
        DataTableColumnMeta replacement = col(0, "x");

        assertThrows(IndexOutOfBoundsException.class, () -> DataTableMeta.builder()
                .setColumns(col(0, "col0")).updateColumn(5, replacement).build());
    }


    @Test
    void testBuilderAddColumns()
    {
        DataTableColumnMeta c0 = col(0, "col0");
        DataTableColumnMeta c1 = col(1, "col1");
        DataTableColumnMeta c2 = col(2, "col2");

        DataTableMeta meta = DataTableMeta.builder().setColumns(c0).addColumns(c1, c2).build();

        assertEquals(3, meta.getColumnCount());
        assertEquals("col0", meta.getColumn(0).getName());
        assertEquals("col1", meta.getColumn(1).getName());
        assertEquals("col2", meta.getColumn(2).getName());
    }


    @Test
    void testBuilderClearColumns()
    {
        DataTableMeta.DataTableMetaBuilder builder = DataTableMeta.builder()
                .setColumns(col(0, "col0"), col(1, "col1")).clearColumns();

        // After clearing, adding new columns should work from scratch
        DataTableMeta meta = builder.setColumns(col(0, "newCol")).build();

        assertEquals(1, meta.getColumnCount());
        assertEquals("newCol", meta.getColumn(0).getName());
    }

    // ==================== containsColumn ====================


    @Test
    void testContainsColumnFound()
    {
        DataTableMeta meta = DataTableMeta.builder().setColumns(col(0, "Alpha"), col(1, "Beta"))
                .build();

        assertTrue(meta.containsColumn("Alpha"));
        assertTrue(meta.containsColumn("Beta"));
    }


    @Test
    void testContainsColumnCaseInsensitive()
    {
        DataTableMeta meta = DataTableMeta.builder().setColumns(col(0, "Alpha")).build();

        assertTrue(meta.containsColumn("alpha"));
        assertTrue(meta.containsColumn("ALPHA"));
    }


    @Test
    void testContainsColumnCaseSensitive()
    {
        DataTableMeta meta = DataTableMeta.builder().setColumns(col(0, "Alpha"))
                .columnNameCaseSensitive(true).build();

        assertTrue(meta.containsColumn("Alpha"));
        assertFalse(meta.containsColumn("alpha"));
        assertFalse(meta.containsColumn("ALPHA"));
    }


    @Test
    void testContainsColumnNotFound()
    {
        DataTableMeta meta = DataTableMeta.builder().setColumns(col(0, "Alpha")).build();

        assertFalse(meta.containsColumn("missing"));
    }


    @Test
    void testContainsColumnNullThrows()
    {
        DataTableMeta meta = DataTableMeta.builder().setColumns(col(0, "col0")).build();

        assertThrows(NullPointerException.class, () -> meta.containsColumn(null));
    }

    // ==================== containsAllColumns ====================


    @Test
    void testContainsAllColumnsAllPresent()
    {
        DataTableMeta meta = DataTableMeta.builder()
                .setColumns(col(0, "A"), col(1, "B"), col(2, "C")).build();

        assertTrue(meta.containsAllColumns("A", "B", "C"));
        assertTrue(meta.containsAllColumns("A", "B"));
        assertTrue(meta.containsAllColumns("C"));
    }


    @Test
    void testContainsAllColumnsSomeMissing()
    {
        DataTableMeta meta = DataTableMeta.builder().setColumns(col(0, "A"), col(1, "B")).build();

        assertFalse(meta.containsAllColumns("A", "B", "C"));
        assertFalse(meta.containsAllColumns("X"));
    }


    @Test
    void testContainsAllColumnsEmpty()
    {
        DataTableMeta meta = DataTableMeta.builder().setColumns(col(0, "A")).build();

        // Vacuous truth: empty array returns true
        assertTrue(meta.containsAllColumns());
    }


    @Test
    void testContainsAllColumnsCaseInsensitive()
    {
        DataTableMeta meta = DataTableMeta.builder().setColumns(col(0, "Alpha"), col(1, "Beta"))
                .build();

        assertTrue(meta.containsAllColumns("alpha", "BETA"));
    }


    @Test
    void testContainsAllColumnsCaseSensitive()
    {
        DataTableMeta meta = DataTableMeta.builder().setColumns(col(0, "Alpha"), col(1, "Beta"))
                .columnNameCaseSensitive(true).build();

        assertTrue(meta.containsAllColumns("Alpha", "Beta"));
        assertFalse(meta.containsAllColumns("Alpha", "beta"));
    }

    // ==================== containsAnyColumn ====================


    @Test
    void testContainsAnyColumnPresent()
    {
        DataTableMeta meta = DataTableMeta.builder()
                .setColumns(col(0, "A"), col(1, "B"), col(2, "C")).build();

        assertTrue(meta.containsAnyColumn("A"));
        assertTrue(meta.containsAnyColumn("X", "B"));
        assertTrue(meta.containsAnyColumn("X", "Y", "C"));
    }


    @Test
    void testContainsAnyColumnNonePresent()
    {
        DataTableMeta meta = DataTableMeta.builder().setColumns(col(0, "A"), col(1, "B")).build();

        assertFalse(meta.containsAnyColumn("X", "Y", "Z"));
    }


    @Test
    void testContainsAnyColumnEmpty()
    {
        DataTableMeta meta = DataTableMeta.builder().setColumns(col(0, "A")).build();

        // Empty array returns false
        assertFalse(meta.containsAnyColumn());
    }


    @Test
    void testContainsAnyColumnCaseInsensitive()
    {
        DataTableMeta meta = DataTableMeta.builder().setColumns(col(0, "Alpha")).build();

        assertTrue(meta.containsAnyColumn("ALPHA"));
        assertTrue(meta.containsAnyColumn("missing", "alpha"));
    }


    @Test
    void testContainsAnyColumnCaseSensitive()
    {
        DataTableMeta meta = DataTableMeta.builder().setColumns(col(0, "Alpha"))
                .columnNameCaseSensitive(true).build();

        assertTrue(meta.containsAnyColumn("Alpha"));
        assertFalse(meta.containsAnyColumn("alpha", "ALPHA"));
    }

    // ==================== getColumnIndex ====================


    @Test
    void testGetColumnIndexCaseInsensitive()
    {
        DataTableMeta meta = DataTableMeta.builder().setColumns(col(0, "Alpha"), col(1, "Beta"))
                .build();

        assertEquals(0, meta.getColumnIndex("alpha"));
        assertEquals(0, meta.getColumnIndex("ALPHA"));
        assertEquals(1, meta.getColumnIndex("beta"));
    }


    @Test
    void testGetColumnIndexCaseSensitive()
    {
        DataTableMeta meta = DataTableMeta.builder().setColumns(col(0, "Alpha"), col(1, "Beta"))
                .columnNameCaseSensitive(true).build();

        assertEquals(0, meta.getColumnIndex("Alpha"));
        assertEquals(-1, meta.getColumnIndex("alpha"));
        assertEquals(-1, meta.getColumnIndex("ALPHA"));
    }

    // --- @NonNull parameter validation ---


    @Test
    void testGetColumnIndexNullThrows()
    {
        DataTableMeta meta = DataTableMeta.builder().setColumns(col(0, "col0")).build();

        assertThrows(NullPointerException.class, () -> meta.getColumnIndex(null));
    }


    @Test
    void testGetColumnByNameNullThrows()
    {
        DataTableMeta meta = DataTableMeta.builder().setColumns(col(0, "col0")).build();

        assertThrows(NullPointerException.class, () -> meta.getColumn((String) null));
    }


    @Test
    void testSetColumnsNullListThrows()
    {
        assertThrows(NullPointerException.class, () -> DataTableMeta.builder()
                .setColumns((java.util.List<DataTableColumnMeta>) null));
    }


    @Test
    void testMetaTableExposedViaGetMetaData()
    {
        DataTableMeta meta = DataTableMeta.builder().setColumns(col(0, "col0"))
                .metaTable(new Object[]
                {
                        "k1", "v1", "k2", "v2"
                }).build();

        assertEquals("v1", meta.getMetaData("k1"));
        assertEquals("v2", meta.getMetaData("k2"));
        assertTrue(meta.getMetaDataKeys().contains("k1"));
        assertTrue(meta.getMetaDataKeys().contains("k2"));
    }


    @Test
    void testGetMetaDataNoMetaTable()
    {
        DataTableMeta meta = DataTableMeta.builder().setColumns(col(0, "col0")).build();

        assertNull(meta.getMetaData("k1"));
        assertTrue(meta.getMetaDataKeys().isEmpty());
    }


    @Test
    void testAddMetaDataNullValueIgnored()
    {
        DataTableMeta meta = DataTableMeta.builder().setColumns(col(0, "col0"))
                .addMetaData("key1", "value1").addMetaData("key2", null).build();

        assertEquals("value1", meta.getMetaData("key1"));
        assertNull(meta.getMetaData("key2"));
        assertEquals(1, meta.getMetaDataKeys().size());
    }
}
