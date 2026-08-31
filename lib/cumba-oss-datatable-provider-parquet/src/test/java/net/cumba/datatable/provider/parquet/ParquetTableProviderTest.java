package net.cumba.datatable.provider.parquet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.impl.provider.DataTableMetaSupport;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.values.DataValueType;
import org.apache.parquet.schema.LogicalTypeAnnotation;
import org.apache.parquet.schema.PrimitiveType;
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName;
import org.apache.parquet.schema.Type;
import org.apache.parquet.schema.Types;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ParquetTableProvider}. Focuses on unit-testable methods that do not require a
 * live Parquet file.
 */
class ParquetTableProviderTest
{

    private ParquetTableProvider provider;

    @BeforeEach
    void setUp()
    {
        provider = new ParquetTableProvider();
    }

    // ---- getSupportedFileInfos ----


    @Test
    void testGetSupportedFileInfosNotEmpty()
    {
        List<FileInfo> infos = provider.getSupportedFileInfos();
        assertNotNull(infos);
        assertFalse(infos.isEmpty());
    }


    @Test
    void testGetSupportedFileInfosContainsParquet()
    {
        List<FileInfo> infos = provider.getSupportedFileInfos();
        assertTrue(infos.contains(ParquetProviderSupplier.FI_PARQUET));
    }

    // ---- getDataValueType ----


    @Test
    void testGetDataValueTypeString()
    {
        Type field = Types.optional(PrimitiveTypeName.BINARY).as(LogicalTypeAnnotation.stringType())
                .named("col");
        DataValueType dvt = provider.getDataValueType(field);
        assertEquals(DataValueType.STRING, dvt);
    }


    @Test
    void testGetDataValueTypeEnum()
    {
        Type field = Types.optional(PrimitiveTypeName.BINARY).as(LogicalTypeAnnotation.enumType())
                .named("col");
        DataValueType dvt = provider.getDataValueType(field);
        assertEquals(DataValueType.STRING, dvt);
    }


    @Test
    void testGetDataValueTypeDouble()
    {
        Type field = Types.optional(PrimitiveTypeName.DOUBLE).named("col");
        DataValueType dvt = provider.getDataValueType(field);
        assertEquals(DataValueType.DOUBLE, dvt);
    }


    @Test
    void testGetDataValueTypeFloat()
    {
        Type field = Types.optional(PrimitiveTypeName.FLOAT).named("col");
        DataValueType dvt = provider.getDataValueType(field);
        assertEquals(DataValueType.DOUBLE, dvt);
    }


    @Test
    void testGetDataValueTypeInt32()
    {
        Type field = Types.optional(PrimitiveTypeName.INT32).named("col");
        DataValueType dvt = provider.getDataValueType(field);
        assertEquals(DataValueType.LONG, dvt);
    }


    @Test
    void testGetDataValueTypeInt64()
    {
        Type field = Types.optional(PrimitiveTypeName.INT64).named("col");
        DataValueType dvt = provider.getDataValueType(field);
        assertEquals(DataValueType.LONG, dvt);
    }


    @Test
    void testGetDataValueTypeBoolean()
    {
        Type field = Types.optional(PrimitiveTypeName.BOOLEAN).named("col");
        DataValueType dvt = provider.getDataValueType(field);
        assertEquals(DataValueType.BOOLEAN, dvt);
    }


    @Test
    void testGetDataValueTypeDate()
    {
        Type field = Types.optional(PrimitiveTypeName.INT32).as(LogicalTypeAnnotation.dateType())
                .named("col");
        DataValueType dvt = provider.getDataValueType(field);
        assertEquals(DataValueType.DOUBLE, dvt);
    }


    @Test
    void testGetDataValueTypeTimestamp()
    {
        Type field = Types.optional(PrimitiveTypeName.INT64).as(
                LogicalTypeAnnotation.timestampType(true, LogicalTypeAnnotation.TimeUnit.MILLIS))
                .named("col");
        DataValueType dvt = provider.getDataValueType(field);
        assertEquals(DataValueType.DOUBLE, dvt);
    }


    @Test
    void testGetDataValueTypeTime()
    {
        Type field = Types.optional(PrimitiveTypeName.INT32)
                .as(LogicalTypeAnnotation.timeType(true, LogicalTypeAnnotation.TimeUnit.MILLIS))
                .named("col");
        DataValueType dvt = provider.getDataValueType(field);
        assertEquals(DataValueType.DOUBLE, dvt);
    }


    @Test
    void testGetDataValueTypeDecimal()
    {
        Type field = Types.optional(PrimitiveTypeName.INT64)
                .as(LogicalTypeAnnotation.decimalType(2, 10)).named("col");
        DataValueType dvt = provider.getDataValueType(field);
        assertEquals(DataValueType.DOUBLE, dvt);
    }


    @Test
    void testGetDataValueTypeIntLogicalType()
    {
        Type field = Types.optional(PrimitiveTypeName.INT32)
                .as(LogicalTypeAnnotation.intType(32, true)).named("col");
        DataValueType dvt = provider.getDataValueType(field);
        assertEquals(DataValueType.LONG, dvt);
    }


    @Test
    void testGetDataValueTypeBinaryFallsBackToString()
    {
        Type field = Types.optional(PrimitiveTypeName.BINARY).named("col");
        DataValueType dvt = provider.getDataValueType(field);
        assertEquals(DataValueType.STRING, dvt);
    }


    @Test
    void testGetDataValueTypeInt96FallsBackToDouble()
    {
        Type field = Types.optional(PrimitiveTypeName.INT96).named("col");
        DataValueType dvt = provider.getDataValueType(field);
        assertEquals(DataValueType.DOUBLE, dvt);
    }

    // ---- addMetaColumn fallback for null field names (F-E24) ----


    /**
     * Regression: a parquet-mr {@link Type} whose name is null must not propagate through to the
     * {@link DataTableMeta} column meta — {@code addMetaColumn} substitutes a deterministic
     * {@code "V" + (index + 1)} fallback so the producer never feeds a null name into the
     * {@code @NonNull} column-name contract. parquet-mr's concrete {@code PrimitiveType} rejects a
     * null name in its constructor, so the test stubs at the {@code Type#getName()} seam.
     */
    @Test
    void testAddMetaColumnFallsBackWhenParquetFieldHasNullName()
    {
        // Use a real primitive as the spy base, then override only getName().
        PrimitiveType realBinary = Types.optional(PrimitiveTypeName.BINARY)
                .as(LogicalTypeAnnotation.stringType()).named("placeholder");
        Type field = mock(Type.class);
        when(field.getName()).thenReturn(null);
        when(field.isPrimitive()).thenReturn(true);
        when(field.asPrimitiveType()).thenReturn(realBinary);

        DataTableMetaSupport support = new DataTableMetaSupport(null);
        support.setTable(java.net.URI.create("file:///tmp/test.parquet"));

        provider.addMetaColumn(field, 4, null, support);

        DataTableMeta meta = support.getTableMeta().rowCount(0).totalRowCount(0).build();
        assertEquals(1, meta.getColumnCount());
        assertEquals("V5", meta.getColumn(0).getName());
        assertEquals(DataValueType.STRING, meta.getColumn(0).getType());
    }


    @Test
    void testAddMetaColumnUsesFieldNameWhenPresent()
    {
        Type field = Types.optional(PrimitiveTypeName.BINARY).as(LogicalTypeAnnotation.stringType())
                .named("realName");
        DataTableMetaSupport support = new DataTableMetaSupport(null);
        support.setTable(java.net.URI.create("file:///tmp/test.parquet"));

        provider.addMetaColumn(field, 0, null, support);

        DataTableMeta meta = support.getTableMeta().rowCount(0).totalRowCount(0).build();
        assertEquals(1, meta.getColumnCount());
        assertEquals("realName", meta.getColumn(0).getName());
    }

}
