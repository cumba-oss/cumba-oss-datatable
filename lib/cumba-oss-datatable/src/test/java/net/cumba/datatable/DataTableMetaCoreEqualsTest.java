package net.cumba.datatable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Stream;
import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link DataTableMeta} that exercise the {@code builderFrom}, {@code cloneFrom},
 * custom-metadata, and builder-validation paths.
 */
class DataTableMetaCoreEqualsTest
{

    private static DataTableColumnMeta col(int idx, String name)
    {
        return DataTableColumnMeta.builder().index(idx).name(name).type(DataValueType.STRING)
                .build();
    }

    // ===== builderFrom / cloneFrom =====


    @Test
    void builderFrom_withDataTableMeta_usesToBuilder()
    {
        DataTableMeta source = DataTableMeta.builder().name("orig").rowCount(3).totalRowCount(3)
                .setColumns(col(0, "A")).build();

        DataTableMeta rebuilt = DataTableMeta.builderFrom(source).build();
        assertEquals(source, rebuilt);
        assertEquals("orig", rebuilt.getName());
        assertEquals(3, rebuilt.getRowCount());
    }


    @Test
    void builderFrom_withAlternateImpl_buildsACopy()
    {
        AltMeta alt = new AltMeta(col(0, "A"), col(1, "B"));
        DataTableMeta rebuilt = DataTableMeta.builderFrom(alt).build();
        assertEquals(alt.getName(), rebuilt.getName());
        assertEquals(alt.getColumnCount(), rebuilt.getColumnCount());
        assertEquals("A", rebuilt.getColumn(0).getName());
        assertEquals("B", rebuilt.getColumn(1).getName());
    }


    @Test
    void cloneFrom_concreteDataTableMeta_returnsSameInstance()
    {
        DataTableMeta source = DataTableMeta.builder().name("t").setColumns(col(0, "A")).build();
        DataTableMeta cloned = DataTableMeta.cloneFrom(source);
        // immutable -> same instance is returned
        assertSame(source, cloned);
    }


    @Test
    void cloneFrom_alternateImpl_buildsACopy()
    {
        AltMeta alt = new AltMeta(col(0, "A"));
        DataTableMeta cloned = DataTableMeta.cloneFrom(alt);
        assertNotNull(cloned);
        assertEquals("alt", cloned.getName());
        assertEquals(1, cloned.getColumnCount());
    }

    // ===== metaTable handling =====


    @Test
    void getMetaData_exposesMetaTableValues()
    {
        DataTableMeta meta = DataTableMeta.builder().setColumns(col(0, "A")).metaTable(new Object[]
        {
                "k", "v"
        }).build();
        assertEquals("v", meta.getMetaData("k"));
        assertTrue(meta.getMetaDataKeys().contains("k"));
    }


    @Test
    void getMetaData_noMetaTableReturnsNull()
    {
        DataTableMeta meta = DataTableMeta.builder().setColumns(col(0, "A")).build();
        assertNull(meta.getMetaData("k"));
        assertTrue(meta.getMetaDataKeys().isEmpty());
    }


    @Test
    void getMetaData_withDefault_returnsDefaultIfMissing()
    {
        DataTableMeta meta = DataTableMeta.builder().setColumns(col(0, "A")).metaTable(new Object[]
        {
                "alpha", 1
        }).build();
        assertEquals(1, meta.getMetaData("alpha"));
        assertEquals("fallback", meta.getMetaData("missing", "fallback"));
        assertNull(meta.getMetaData("missing"));
    }


    @Test
    void builder_addMetaDataIgnoresNull()
    {
        // addMetaData(key, null) is a silent no-op.
        DataTableMeta meta = DataTableMeta.builder().setColumns(col(0, "A"))
                .addMetaData("key1", null).addMetaData("key2", "v2").build();
        assertNull(meta.getMetaData("key1"));
        assertEquals("v2", meta.getMetaData("key2"));
    }


    @Test
    void builder_addColumns_varargs()
    {
        DataTableMeta meta1 = DataTableMeta.builder().setColumns(col(0, "A"))
                .addColumns(col(1, "B"), col(2, "C")).build();
        assertEquals(3, meta1.getColumnCount());

        // Empty varargs is a no-op
        DataTableMeta meta2 = DataTableMeta.builder().setColumns(col(0, "A"))
                .addColumns(new DataTableColumnMeta[0]).build();
        assertEquals(1, meta2.getColumnCount());

        // Adding via varargs from empty start works
        DataTableMeta meta3 = DataTableMeta.builder().clearColumns().addColumns(col(0, "A"))
                .build();
        assertEquals(1, meta3.getColumnCount());
    }


    @Test
    void builder_setColumnsFromStream()
    {
        DataTableMeta meta = DataTableMeta.builder().setColumns(Stream.of(col(0, "A"), col(1, "B")))
                .build();
        assertEquals(2, meta.getColumnCount());
    }


    @Test
    void builder_columnsFromNullArrayClears()
    {
        // columns(null) is a "clear" — leaves the @NonNull failing at build time, but exercise
        // the branch. The setter just stores null; build() is the throwing call.
        DataTableMeta.DataTableMetaBuilder b = DataTableMeta.builder()
                .columns((DataTableColumnMeta[]) null);
        assertThrows(NullPointerException.class, b::build);
    }


    @Test
    void builder_totalRowCountLessThanRowCount_throws()
    {
        // totalRowCount < rowCount must fail at build().
        DataTableMeta.DataTableMetaBuilder b = DataTableMeta.builder().setColumns(col(0, "A"))
                .rowCount(10).totalRowCount(5);
        assertThrows(IllegalStateException.class, b::build);
    }

    /**
     * Minimal alternate {@link IDataTableMeta} implementation that is NOT a DataTableMeta — used to
     * exercise the {@code builderFrom(IDataTableMeta)} branch that copies field-by-field.
     */
    private static final class AltMeta implements IDataTableMeta
    {

        private final DataTableColumnMeta[] cols;

        AltMeta(DataTableColumnMeta... cols)
        {
            this.cols = cols;
        }


        @Override
        public String getName()
        {
            return "alt";
        }


        @Override
        public String getLabel()
        {
            return "alt label";
        }


        @Override
        public long getRowCount()
        {
            return 0;
        }


        @Override
        public long getTotalRowCount()
        {
            return 0;
        }


        @Override
        public boolean isColumnNameCaseSensitive()
        {
            return false;
        }


        @Override
        public Stream<DataTableColumnMeta> getAllColumns()
        {
            return Arrays.stream(cols);
        }


        @Override
        public int getColumnCount()
        {
            return cols.length;
        }


        @Override
        public DataTableColumnMeta getColumn(int aColumnIndex)
        {
            return cols[aColumnIndex];
        }


        @Override
        public DataTableColumnMeta getColumn(String aColumnName)
        {
            for (DataTableColumnMeta c : cols)
            {
                if (c.getName().equalsIgnoreCase(aColumnName))
                {
                    return c;
                }
            }
            return null;
        }


        @Override
        public int getColumnIndex(String aColumnName)
        {
            for (int i = 0; i < cols.length; i++)
            {
                if (cols[i].getName().equalsIgnoreCase(aColumnName))
                {
                    return i;
                }
            }
            return -1;
        }


        @Override
        public URI getTableURI()
        {
            return null;
        }


        @Override
        public Object getMetaData(String aKey)
        {
            return null;
        }


        @Override
        public Object getMetaData(String aKey, Object aDefault)
        {
            return aDefault;
        }


        @Override
        public Collection<String> getMetaDataKeys()
        {
            return java.util.Collections.emptyList();
        }
    }
}
