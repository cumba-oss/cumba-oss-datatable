package net.cumba.datatable;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.Test;

class DefaultDataTableColumnMetaTest
{

    @Test
    void testBuilder()
    {
        DataTableColumnMeta meta = DataTableColumnMeta.builder().index(0).name("col1")
                .type(DataValueType.STRING).build();

        assertNotNull(meta);
    }


    @Test
    void testGetIndex()
    {
        DataTableColumnMeta meta = DataTableColumnMeta.builder().index(3).name("col")
                .type(DataValueType.STRING).build();

        assertEquals(3, meta.getIndex());
    }


    @Test
    void testGetName()
    {
        DataTableColumnMeta meta = DataTableColumnMeta.builder().index(0).name("myColumn")
                .type(DataValueType.STRING).build();

        assertEquals("myColumn", meta.getName());
    }


    @Test
    void testGetType()
    {
        DataTableColumnMeta meta = DataTableColumnMeta.builder().index(0).name("col")
                .type(DataValueType.DOUBLE).build();

        assertEquals(DataValueType.DOUBLE, meta.getType());
    }


    @Test
    void testGetMetaData()
    {
        DataTableColumnMeta meta = DataTableColumnMeta.builder().index(0).name("col")
                .type(DataValueType.STRING).metaTable(new Object[]
                {
                        "key1", "value1", "key2", "value2"
                }).build();

        assertEquals("value1", meta.getMetaData("key1"));
        assertEquals("value2", meta.getMetaData("key2"));
    }


    @Test
    void testGetMetaDataNotFound()
    {
        DataTableColumnMeta meta = DataTableColumnMeta.builder().index(0).name("col")
                .type(DataValueType.STRING).metaTable(new Object[]
                {
                        "key1", "value1"
                }).build();

        assertNull(meta.getMetaData("nonexistent"));
    }


    @Test
    void testGetMetaDataDefault()
    {
        DataTableColumnMeta meta = DataTableColumnMeta.builder().index(0).name("col")
                .type(DataValueType.STRING).metaTable(new Object[]
                {
                        "key1", "value1"
                }).build();

        assertEquals("fallback", meta.getMetaData("missing", "fallback"));
        assertEquals("value1", meta.getMetaData("key1", "fallback"));
    }


    @Test
    void testGetMetaDataNullTable()
    {
        DataTableColumnMeta meta = DataTableColumnMeta.builder().index(0).name("col")
                .type(DataValueType.STRING).build();

        assertNull(meta.getMetaData("anyKey"));
    }


    @Test
    void testGetMetaDataKeys()
    {
        DataTableColumnMeta meta = DataTableColumnMeta.builder().index(0).name("col")
                .type(DataValueType.STRING).metaTable(new Object[]
                {
                        "alpha", 1, "beta", 2, "gamma", 3
                }).build();

        Collection<String> keys = meta.getMetaDataKeys();
        assertNotNull(keys);
        assertEquals(3, keys.size());
        assertTrue(keys.contains("alpha"));
        assertTrue(keys.contains("beta"));
        assertTrue(keys.contains("gamma"));
    }


    @Test
    void testGetMetaDataKeysEmpty()
    {
        DataTableColumnMeta meta = DataTableColumnMeta.builder().index(0).name("col")
                .type(DataValueType.STRING).build();

        Collection<String> keys = meta.getMetaDataKeys();
        assertNotNull(keys);
        assertTrue(keys.isEmpty());
    }


    @Test
    void testGetMetaTable()
    {
        Object[] original = new Object[]
        {
                "k", "v"
        };
        DataTableColumnMeta meta = DataTableColumnMeta.builder().index(0).name("col")
                .type(DataValueType.STRING).metaTable(original).build();

        Object[] returned = meta.getMetaTable();
        assertNotNull(returned);
        assertArrayEquals(original, returned);
        assertNotSame(original, returned, "getMetaTable should return a copy");
    }


    @Test
    void testGetMetaTableNull()
    {
        DataTableColumnMeta meta = DataTableColumnMeta.builder().index(0).name("col")
                .type(DataValueType.STRING).build();

        assertNull(meta.getMetaTable());
    }


    @Test
    void testClone()
    {
        DataTableColumnMeta meta = DataTableColumnMeta.builder().index(1).name("col")
                .label("Column").type(DataValueType.LONG).build();

        DataTableColumnMeta cloned = meta.clone();
        assertEquals(meta, cloned);
    }


    @Test
    void testCloneFrom()
    {
        DataTableColumnMeta meta = DataTableColumnMeta.builder().index(1).name("col")
                .type(DataValueType.STRING).build();

        DataTableColumnMeta cloned = DataTableColumnMeta.cloneFrom(meta);
        assertSame(meta, cloned,
                "cloneFrom on exact DefaultDataTableColumnMeta class should return same instance");
    }


    @Test
    void testToBuilder()
    {
        DataTableColumnMeta original = DataTableColumnMeta.builder().index(0).name("col")
                .label("Original Label").type(DataValueType.STRING).build();

        DataTableColumnMeta modified = original.toBuilder().label("Modified Label").build();

        assertEquals("Original Label", original.getLabel());
        assertEquals("Modified Label", modified.getLabel());
        assertEquals(original.getName(), modified.getName());
        assertEquals(original.getIndex(), modified.getIndex());
        assertEquals(original.getType(), modified.getType());
    }


    @Test
    void testBuilderAddMetaData()
    {
        DataTableColumnMeta meta = DataTableColumnMeta.builder().index(0).name("col")
                .type(DataValueType.STRING).addMetaData("first", "one").addMetaData("second", "two")
                .build();

        assertEquals("one", meta.getMetaData("first"));
        assertEquals("two", meta.getMetaData("second"));

        Collection<String> keys = meta.getMetaDataKeys();
        assertEquals(2, keys.size());
    }


    @Test
    void testEquality()
    {
        DataTableColumnMeta a = DataTableColumnMeta.builder().index(5).name("amount")
                .label("Amount").length(10).nativeType("DECIMAL").type(DataValueType.DOUBLE)
                .displayFormat("#,##0.00").build();

        DataTableColumnMeta b = DataTableColumnMeta.builder().index(5).name("amount")
                .label("Amount").length(10).nativeType("DECIMAL").type(DataValueType.DOUBLE)
                .displayFormat("#,##0.00").build();

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }


    @Test
    void testAllFields()
    {
        Object[] table = new Object[]
        {
                "info", "details"
        };
        DataTableColumnMeta meta = DataTableColumnMeta.builder().index(2).name("price")
                .label("Price").length(12).nativeType("NUMBER").type(DataValueType.DOUBLE)
                .displayFormat("$#,##0.00").metaTable(table).build();

        assertEquals(2, meta.getIndex());
        assertEquals("price", meta.getName());
        assertEquals("Price", meta.getLabel());
        assertEquals(12, meta.getLength());
        assertEquals("NUMBER", meta.getNativeType());
        assertEquals(DataValueType.DOUBLE, meta.getType());
        assertEquals("$#,##0.00", meta.getDisplayFormat());
        assertArrayEquals(table, meta.getMetaTable());
    }


    @Test
    void testBuilderNameNullFailsImmediately()
    {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> DataTableColumnMeta.builder().name(null));
        assertTrue(ex.getMessage().contains("Name must be defined"), ex.getMessage());
    }


    @Test
    void testBuildWithoutNameFailsAtBuildTime()
    {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> DataTableColumnMeta.builder().index(0).type(DataValueType.STRING).build());
        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage().contains("name"), ex.getMessage());
    }


    @Test
    void testWithNameNullThrows()
    {
        DataTableColumnMeta meta = DataTableColumnMeta.builder().index(0).name("X")
                .type(DataValueType.STRING).build();
        assertThrows(NullPointerException.class, () -> meta.withName(null));
    }


    @Test
    void testBuildWithValidNameSucceeds()
    {
        DataTableColumnMeta meta = DataTableColumnMeta.builder().index(0).name("X")
                .type(DataValueType.STRING).build();
        assertEquals("X", meta.getName());
    }
}
