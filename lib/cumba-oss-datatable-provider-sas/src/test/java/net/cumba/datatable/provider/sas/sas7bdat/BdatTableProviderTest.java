package net.cumba.datatable.provider.sas.sas7bdat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.impl.CachedDataTableColumn;
import net.cumba.datatable.impl.provider.DataTableMetaSupport;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.MissingValue;
import net.cumba.sasutils.VariableType;
import net.cumba.sasutils.bdat.VariableBdat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BdatTableProviderTest
{

    private BdatTableProvider provider;

    @BeforeEach
    void setUp()
    {
        provider = new BdatTableProvider();
    }

    // --- getSupportedFileInfos ---


    @Test
    void testGetSupportedFileInfos()
    {
        List<FileInfo> infos = provider.getSupportedFileInfos();
        assertNotNull(infos);
        assertEquals(1, infos.size());
        assertEquals(BdatProviderSupplier.FI_BDAT, infos.get(0));
    }

    // --- createColumnFor ---


    @Test
    void testCreateColumnForDouble()
    {
        DataTableColumnMeta meta = DataTableColumnMeta.builder().index(0).name("AGE").label("Age")
                .type(DataValueType.DOUBLE).build();
        CachedDataTableColumn col = provider.createColumnFor(meta);
        assertNotNull(col);
        assertEquals(DataValueType.DOUBLE, col.getType());
    }


    @Test
    void testCreateColumnForString()
    {
        DataTableColumnMeta meta = DataTableColumnMeta.builder().index(1).name("NAME").label("Name")
                .type(DataValueType.STRING).build();
        CachedDataTableColumn col = provider.createColumnFor(meta);
        assertNotNull(col);
        assertEquals(DataValueType.STRING, col.getType());
    }

    // --- addColumn null-name fallback ---


    @Test
    void testAddColumnSubstitutesFallbackWhenNameIsNull()
    {
        DataTableMetaSupport support = new DataTableMetaSupport(null);
        support.setTable(URI.create("file:///tmp/anon.sas7bdat"));

        VariableBdat var = nullNameVar(VariableType.CHARACTER, 4);

        provider.addColumn(support, var, 2, URI.create("file:///tmp/anon.sas7bdat"));

        DataTableColumnMeta[] cols = support.getTableMeta().build().getColumns();
        assertEquals("V3", cols[0].getName());
        assertEquals(DataValueType.STRING, cols[0].getType());
    }

    // --- F-D17: clampExpectedRows guards against deleted > declared underflow ---


    @Test
    void testClampExpectedRowsNormalCase()
    {
        assertEquals(7L, BdatTableProvider.clampExpectedRows(10L, 3));
    }


    @Test
    void testClampExpectedRowsZeroDeleted()
    {
        assertEquals(10L, BdatTableProvider.clampExpectedRows(10L, 0));
    }


    @Test
    void testClampExpectedRowsExactlyEqual()
    {
        assertEquals(0L, BdatTableProvider.clampExpectedRows(5L, 5));
    }


    @Test
    void testClampExpectedRowsClampsNegativeToZero()
    {
        // F-D17: deleted > declared must clamp to 0, not produce a negative expected count.
        assertEquals(0L, BdatTableProvider.clampExpectedRows(3L, 7));
    }


    @Test
    void testClampExpectedRowsNullRowCountTreatedAsZero()
    {
        assertEquals(0L, BdatTableProvider.clampExpectedRows(null, 0));
        assertEquals(0L, BdatTableProvider.clampExpectedRows(null, 5));
    }

    // --- F-D11: default arm in addData2Column is type-aware ---


    @Test
    void testDefaultArmStringColumnGetsEmptyString() throws Exception
    {
        // An Object that is neither String nor Number triggers the `default` branch.
        Object unknown = new Object();

        CachedDataTableColumn col = invokeAddData2Column(DataValueType.STRING, unknown);

        assertEquals(1L, col.getRowCount());
        assertEquals("", col.getValue(0),
                "STRING column default arm must produce empty string (parso parity).");
    }


    @Test
    void testDefaultArmNonStringColumnGetsMisUnknown() throws Exception
    {
        Object unknown = new Object();

        CachedDataTableColumn col = invokeAddData2Column(DataValueType.DOUBLE, unknown);

        assertEquals(1L, col.getRowCount());
        Object v = col.getValue(0);
        assertInstanceOf(MissingValue.class, v,
                "Non-STRING default arm must yield MIS_UNKNOWN, not coerced to empty string.");
        assertEquals(MissingValue.MIS_UNKNOWN, v);
    }


    @Test
    void testStringBranchUnchanged() throws Exception
    {
        // Sanity guard: the F-D11 change must not break the String branch.
        CachedDataTableColumn col = invokeAddData2Column(DataValueType.STRING, "abc");
        assertEquals(1L, col.getRowCount());
        assertEquals("abc", col.getValue(0));
    }


    @Test
    void testNumberBranchUnchanged() throws Exception
    {
        // Sanity guard: the F-D11 change must not break the Number branch.
        CachedDataTableColumn col = invokeAddData2Column(DataValueType.DOUBLE, Double.valueOf(1.5));
        assertEquals(1L, col.getRowCount());
        assertEquals(1.5, ((Number) col.getValue(0)).doubleValue(), 0.0);
    }


    /**
     * Reflective invocation of the private inner {@code BdatTableDataParser#addData2Column} with a
     * single-row slice whose only value is the supplied {@code aRawValue}. The slice is built from
     * an anonymous {@link BdatObservation} subclass that bypasses the real byte-buffer parsing.
     */
    private CachedDataTableColumn invokeAddData2Column(DataValueType aColumnType, Object aRawValue)
        throws Exception
    {
        DataTableColumnMeta meta = DataTableColumnMeta.builder().index(0).name("X")
                .type(aColumnType).build();
        DataTableMeta tableMeta = DataTableMeta.builder().rowCount(1).totalRowCount(1)
                .setColumns(meta).build();

        // Find the private inner class
        Class<?> parserCls = Class.forName(
                "net.cumba.datatable.provider.sas.sas7bdat.BdatTableProvider$BdatTableDataParser");
        Constructor<?> ctor = parserCls.getDeclaredConstructor(BdatTableProvider.class,
                DataTableMeta.class);
        ctor.setAccessible(true);
        Object parser = ctor.newInstance(provider, tableMeta);

        // Build a 1-row slice with the desired value
        BdatObservation obs = new BdatObservation(Collections.emptyList(), new byte[0])
        {

            @Override
            public Object getValue(int aIndex)
            {
                return aRawValue;
            }
        };

        CachedDataTableColumn col = new CachedDataTableColumn(0, aColumnType);

        Method m = parserCls.getDeclaredMethod("addData2Column", List.class, int.class,
                DataTableColumnMeta.class, CachedDataTableColumn.class);
        m.setAccessible(true);
        m.invoke(parser, List.of(obs), 0, meta, col);

        col.complete();
        return col;
    }


    private static VariableBdat nullNameVar(VariableType type, int length)
    {
        return new VariableBdat(null, null, null)
        {

            @Override
            public VariableType getType()
            {
                return type;
            }


            @Override
            public Integer getLength()
            {
                return length;
            }


            @Override
            public String getName()
            {
                return null;
            }


            @Override
            public String getLabel()
            {
                return null;
            }
        };
    }
}
