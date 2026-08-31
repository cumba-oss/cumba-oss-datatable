package net.cumba.datatable.impl.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.CachedDataTableColumn;
import net.cumba.datatable.provider.IDataTableProvider;
import net.cumba.datatable.values.DataValueType;
import org.junit.jupiter.api.Test;

class AbstractTableDataParserTest
{

    // ==================== Concrete test implementation ====================

    private static class StringParser extends AbstractTableDataParser<String>
    {

        private StringParser(IDataTableProvider aProvider, DataTableMeta aMeta)
        {
            super(aProvider, aMeta);
        }


        @Override
        protected void addData2Column(List<String> aRowSlice, int aColumnIndex,
                DataTableColumnMeta aMetaColumn, CachedDataTableColumn aDataColumn)
        {
            for (String row : aRowSlice)
            {
                aDataColumn.addElement(row);
            }
        }
    }

    // ==================== Stub provider ====================

    private static final IDataTableProvider STUB_PROVIDER = new StubProvider();

    /**
     * Minimal stub — just enough to satisfy @NonNull in constructor.
     */
    private static class StubProvider implements IDataTableProvider
    {

        @Override
        public String getDescription()
        {
            return null;
        }


        @Override
        public String getName()
        {
            return null;
        }


        @Override
        public void setMetadata(net.cumba.datatable.metadata.IMetadataLibrary aMetadata)
        {
        }


        @Override
        public IDataTable provide(net.cumba.datatable.library.ILibraryMember aMember,
                net.cumba.datatable.io.FileInfo aFileInfo)
            throws IOException
        {
            return null;
        }


        @Override
        public java.util.List<net.cumba.datatable.io.FileInfo> getSupportedFileInfos()
        {
            return java.util.List.of();
        }


        @Override
        public IDataTable provide(java.net.URI aUri, net.cumba.datatable.io.FileInfo aFileInfo)
            throws IOException
        {
            return null;
        }


        @Override
        public net.cumba.datatable.DataTableMeta provideMetaData(java.net.URI aUri,
                net.cumba.datatable.io.FileInfo aFileInfo)
            throws IOException
        {
            return null;
        }
    }

    // ==================== Helper ====================

    private DataTableMeta createSingleColumnMeta()
    {
        DataTableColumnMeta cm = DataTableColumnMeta.builder().index(0).name("COL0")
                .type(DataValueType.STRING).build();
        return DataTableMeta.builder().name("test").setColumns(cm).rowCount(0).totalRowCount(0)
                .build();
    }


    private DataTableMeta createTwoColumnMeta()
    {
        DataTableColumnMeta cm0 = DataTableColumnMeta.builder().index(0).name("COL0")
                .type(DataValueType.STRING).build();
        DataTableColumnMeta cm1 = DataTableColumnMeta.builder().index(1).name("COL1")
                .type(DataValueType.STRING).build();
        return DataTableMeta.builder().name("test").setColumns(cm0, cm1).rowCount(0)
                .totalRowCount(0).build();
    }

    // ==================== tri() static methods ====================


    @Test
    void tri_nullReturnsNull()
    {
        assertEquals(null, AbstractTableDataParser.tri(null));
    }


    @Test
    void tri_nullReturnsDefault()
    {
        assertEquals("def", AbstractTableDataParser.tri(null, "def"));
    }


    @Test
    void tri_trimRightAndIntern()
    {
        String result = AbstractTableDataParser.tri("hello   ");
        assertEquals("hello", result);

        // tri() canonicalises through CDT's application-global StringInterner, NOT the JVM's
        // String.intern() pool — so the canonical instance is the one that interner hands out.
        // Feed it a second, independently-built equal string (a literal would prove nothing:
        // javac folds equal literals to one constant-pool reference) and require the same
        // instance back. Built from a char[] so the fresh allocation is unambiguous.
        String equalButDistinct = String.valueOf(new char[]
        {
                'h', 'e', 'l', 'l', 'o', ' ', ' ', ' '
        });
        assertNotSame(result, equalButDistinct, "precondition: a genuinely distinct instance");
        assertSame(result, AbstractTableDataParser.tri(equalButDistinct),
                "tri() returns the canonical instance for equal content");
    }


    @Test
    void tri_noTrailingSpaces()
    {
        assertEquals("abc", AbstractTableDataParser.tri("abc"));
    }


    @Test
    void tri_emptyString()
    {
        assertEquals("", AbstractTableDataParser.tri(""));
    }


    @Test
    void tri_defaultNotUsedWhenStringPresent()
    {
        assertEquals("val", AbstractTableDataParser.tri("val", "default"));
    }

    // ==================== Constructor ====================


    @Test
    void constructor_nullProviderThrows()
    {
        DataTableMeta meta = createSingleColumnMeta();
        assertThrows(NullPointerException.class, () -> new StringParser(null, meta));
    }


    @Test
    void constructor_nullMetaThrows()
    {
        assertThrows(NullPointerException.class, () -> new StringParser(STUB_PROVIDER, null));
    }


    @Test
    void constructor_zeroColumnsThrows()
    {
        DataTableMeta meta = DataTableMeta.builder().name("test")
                .columns(new DataTableColumnMeta[0]).rowCount(0).totalRowCount(0).build();
        assertThrows(IllegalArgumentException.class, () -> new StringParser(STUB_PROVIDER, meta));
    }


    @Test
    void constructor_setsDefaults()
    {
        DataTableMeta meta = createSingleColumnMeta();
        StringParser parser = new StringParser(STUB_PROVIDER, meta);

        assertEquals(10_000, parser.getRowSliceSize());
        assertEquals(null, parser.getExecutor());
    }

    // ==================== Setters ====================


    @Test
    void setRowSliceSize()
    {
        DataTableMeta meta = createSingleColumnMeta();
        StringParser parser = new StringParser(STUB_PROVIDER, meta);
        parser.setRowSliceSize(500);
        assertEquals(500, parser.getRowSliceSize());
    }


    @Test
    void setRowSliceSize_rejectsZeroAndNegative()
    {
        // F-A11: slice size < 1 must throw — a zero / negative slice is degenerate.
        DataTableMeta meta = createSingleColumnMeta();
        StringParser parser = new StringParser(STUB_PROVIDER, meta);

        IllegalArgumentException ex0 = assertThrows(IllegalArgumentException.class,
                () -> parser.setRowSliceSize(0));
        org.junit.jupiter.api.Assertions.assertTrue(ex0.getMessage().contains("rowSliceSize"));

        assertThrows(IllegalArgumentException.class, () -> parser.setRowSliceSize(-1));
        assertThrows(IllegalArgumentException.class,
                () -> parser.setRowSliceSize(Integer.MIN_VALUE));

        // value remained at the default
        assertEquals(10_000, parser.getRowSliceSize());
    }


    @Test
    void setRowSliceSize_acceptsOne() throws IOException
    {
        // boundary: slice size == 1 is the minimum legal value
        DataTableMeta meta = createSingleColumnMeta();
        StringParser parser = new StringParser(STUB_PROVIDER, meta);
        parser.setRowSliceSize(1);
        assertEquals(1, parser.getRowSliceSize());

        parser.addDataRow("only");
        IDataTable table = parser.completeTable();
        assertEquals(1, table.getRowCount());
    }


    @Test
    void setExecutor()
    {
        DataTableMeta meta = createSingleColumnMeta();
        StringParser parser = new StringParser(STUB_PROVIDER, meta);
        Executor exec = Runnable::run;
        parser.setExecutor(exec);
        assertSame(exec, parser.getExecutor());
    }

    // ==================== completeTable — basic ====================


    @Test
    void completeTable_noRows() throws IOException
    {
        DataTableMeta meta = createSingleColumnMeta();
        StringParser parser = new StringParser(STUB_PROVIDER, meta);

        IDataTable table = parser.completeTable();

        assertNotNull(table);
        assertEquals(0, table.getRowCount());
        assertEquals(1, table.getColumnCount());
    }


    @Test
    void completeTable_singleRow() throws IOException
    {
        DataTableMeta meta = createSingleColumnMeta();
        StringParser parser = new StringParser(STUB_PROVIDER, meta);

        parser.addDataRow("hello");
        IDataTable table = parser.completeTable();

        assertEquals(1, table.getRowCount());
        assertEquals("hello", table.getColumn(0).getValue(0));
    }


    @Test
    void completeTable_multipleRows() throws IOException
    {
        DataTableMeta meta = createSingleColumnMeta();
        StringParser parser = new StringParser(STUB_PROVIDER, meta);

        parser.addDataRow("a");
        parser.addDataRow("b");
        parser.addDataRow("c");
        IDataTable table = parser.completeTable();

        assertEquals(3, table.getRowCount());
        assertEquals("a", table.getColumn(0).getValue(0));
        assertEquals("b", table.getColumn(0).getValue(1));
        assertEquals("c", table.getColumn(0).getValue(2));
    }

    // ==================== completeTable — batching ====================


    @Test
    void completeTable_exceedsSliceSize() throws IOException
    {
        DataTableMeta meta = createSingleColumnMeta();
        StringParser parser = new StringParser(STUB_PROVIDER, meta);
        parser.setRowSliceSize(3);

        for (int i = 0; i < 10; i++)
        {
            parser.addDataRow("row" + i);
        }
        IDataTable table = parser.completeTable();

        assertEquals(10, table.getRowCount());
        assertEquals("row0", table.getColumn(0).getValue(0));
        assertEquals("row9", table.getColumn(0).getValue(9));
    }


    @Test
    void completeTable_exactSliceSize() throws IOException
    {
        DataTableMeta meta = createSingleColumnMeta();
        StringParser parser = new StringParser(STUB_PROVIDER, meta);
        parser.setRowSliceSize(5);

        for (int i = 0; i < 5; i++)
        {
            parser.addDataRow("r" + i);
        }
        IDataTable table = parser.completeTable();

        assertEquals(5, table.getRowCount());
    }

    // ==================== completeTable — with executor ====================


    @Test
    void completeTable_withCustomExecutor() throws IOException
    {
        DataTableMeta meta = createSingleColumnMeta();
        StringParser parser = new StringParser(STUB_PROVIDER, meta);
        // synchronous executor for deterministic testing
        parser.setExecutor(Runnable::run);

        parser.addDataRow("x");
        parser.addDataRow("y");
        IDataTable table = parser.completeTable();

        assertEquals(2, table.getRowCount());
    }

    // ==================== addDataRow after complete ====================


    @Test
    void addDataRow_afterCompleteThrows() throws IOException
    {
        DataTableMeta meta = createSingleColumnMeta();
        StringParser parser = new StringParser(STUB_PROVIDER, meta);

        parser.completeTable();

        assertThrows(IllegalStateException.class, () -> parser.addDataRow("too late"));
    }

    // ==================== completeTable — multiple columns ====================


    @Test
    void completeTable_twoColumns() throws IOException
    {
        // For this we need a parser that handles 2 columns — but our StringParser
        // puts the same value into each column. That's still a valid test of the framework.
        DataTableMeta meta = createTwoColumnMeta();
        StringParser parser = new StringParser(STUB_PROVIDER, meta);

        parser.addDataRow("val");
        IDataTable table = parser.completeTable();

        assertEquals(1, table.getRowCount());
        assertEquals(2, table.getColumnCount());
    }

    // ==================== completeTable — rows stay in insertion order ====================


    /**
     * The parser must NOT sort the rows it produces — the data stays in insertion order.
     */
    @Test
    void completeTable_preservesInsertionOrder() throws IOException
    {
        DataTableColumnMeta cm = DataTableColumnMeta.builder().index(0).name("NAME")
                .type(DataValueType.STRING).build();
        DataTableMeta meta = DataTableMeta.builder().name("test").setColumns(cm).rowCount(0)
                .totalRowCount(0).build();

        StringParser parser = new StringParser(STUB_PROVIDER, meta);
        parser.addDataRow("Charlie");
        parser.addDataRow("Alpha");
        parser.addDataRow("Bravo");
        IDataTable table = parser.completeTable();

        assertEquals(3, table.getRowCount());
        // Rows must remain in insertion order — the parser does not sort.
        assertEquals("Charlie", table.getColumn(0).getValue(0));
        assertEquals("Alpha", table.getColumn(0).getValue(1));
        assertEquals("Bravo", table.getColumn(0).getValue(2));
    }
}
