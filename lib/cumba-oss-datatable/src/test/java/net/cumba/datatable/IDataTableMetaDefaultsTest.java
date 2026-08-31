package net.cumba.datatable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Stream;
import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.Test;

/**
 * Exercises the default-method implementations on {@link IDataTableMeta} via a minimal stub that
 * provides only the abstract methods. {@link DataTableMeta} overrides {@code getColumn(int)} and
 * {@code getColumn(String)}, so they need a non-DataTableMeta stub to exercise.
 */
class IDataTableMetaDefaultsTest
{

    /**
     * Stub IDataTableMeta backed by an array of columns. Only abstract methods are implemented.
     */
    private static final class StubMeta implements IDataTableMeta
    {

        private final DataTableColumnMeta[] columns;

        private final boolean caseSensitive;

        StubMeta(boolean aCaseSensitive, DataTableColumnMeta... aColumns)
        {
            this.caseSensitive = aCaseSensitive;
            this.columns = aColumns;
        }


        @Override
        public String getName()
        {
            return "stub";
        }


        @Override
        public String getLabel()
        {
            return null;
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
        public int getColumnCount()
        {
            return columns.length;
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
        public java.util.Collection<String> getMetaDataKeys()
        {
            return java.util.Collections.emptyList();
        }


        @Override
        public Stream<DataTableColumnMeta> getAllColumns()
        {
            return Stream.of(columns);
        }


        @Override
        public boolean isColumnNameCaseSensitive()
        {
            return caseSensitive;
        }
    }

    private static DataTableColumnMeta col(int idx, String name)
    {
        return DataTableColumnMeta.builder().index(idx).name(name).type(DataValueType.STRING)
                .build();
    }


    @Test
    void getColumn_byIndex_inRange_returnsColumn()
    {
        StubMeta m = new StubMeta(true, col(0, "A"), col(1, "B"));
        assertEquals("A", m.getColumn(0).getName());
        assertEquals("B", m.getColumn(1).getName());
    }


    @Test
    void getColumn_byIndex_outOfRange_throws()
    {
        StubMeta m = new StubMeta(true, col(0, "A"));
        assertThrows(IndexOutOfBoundsException.class, () -> m.getColumn(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> m.getColumn(99));
    }


    @Test
    void getColumn_byIndex_missingInStream_throws()
    {
        // Column has index 5 but column count is 1; default getColumn(0) won't find a matching
        // index in the stream so it throws the secondary IndexOutOfBoundsException.
        StubMeta m = new StubMeta(true, col(5, "A"));
        assertThrows(IndexOutOfBoundsException.class, () -> m.getColumn(0));
    }


    @Test
    void getColumn_byName_caseSensitive_match_returnsColumn()
    {
        StubMeta m = new StubMeta(true, col(0, "A"), col(1, "b"));
        assertEquals(0, m.getColumn("A").getIndex());
        assertEquals(1, m.getColumn("b").getIndex());
    }


    @Test
    void getColumn_byName_caseSensitive_mismatch_throws()
    {
        StubMeta m = new StubMeta(true, col(0, "A"));
        assertThrows(NoSuchElementException.class, () -> m.getColumn("a"));
    }


    @Test
    void getColumn_byName_caseInsensitive_matchesAnyCase()
    {
        StubMeta m = new StubMeta(false, col(0, "A"), col(1, "B"));
        assertEquals(0, m.getColumn("a").getIndex());
        assertEquals(1, m.getColumn("b").getIndex());
    }


    @Test
    void getColumn_byName_caseInsensitive_unknown_throws()
    {
        StubMeta m = new StubMeta(false, col(0, "A"));
        assertThrows(NoSuchElementException.class, () -> m.getColumn("DOES_NOT_EXIST"));
    }


    @Test
    void getColumnIndex_unknown_returnsMinusOne()
    {
        StubMeta m = new StubMeta(false, col(0, "A"));
        assertEquals(-1, m.getColumnIndex("DOES_NOT_EXIST"));
    }


    @Test
    void getColumnIndex_found_returnsIndex()
    {
        StubMeta m = new StubMeta(false, col(0, "A"), col(1, "B"));
        assertEquals(0, m.getColumnIndex("A"));
        assertEquals(1, m.getColumnIndex("B"));
    }


    @Test
    void getColumns_byIndices_default_streamsRequested()
    {
        StubMeta m = new StubMeta(true, col(0, "A"), col(1, "B"), col(2, "C"));
        List<DataTableColumnMeta> got = m.getColumns(2, 0).toList();
        assertEquals(2, got.size());
        assertEquals("C", got.get(0).getName());
        assertEquals("A", got.get(1).getName());
    }


    @Test
    void getOptionalColumn_present_returnsColumn()
    {
        StubMeta m = new StubMeta(false, col(0, "A"));
        DataTableColumnMeta r = m.getOptionalColumn("a");
        assertNotNull(r);
        assertEquals(0, r.getIndex());
    }


    @Test
    void getOptionalColumn_missing_returnsNull()
    {
        StubMeta m = new StubMeta(false, col(0, "A"));
        assertNull(m.getOptionalColumn("X"));
    }
}
