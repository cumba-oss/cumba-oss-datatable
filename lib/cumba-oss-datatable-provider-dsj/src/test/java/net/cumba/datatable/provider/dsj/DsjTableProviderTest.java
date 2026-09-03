package net.cumba.datatable.provider.dsj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import net.cumba.cdisc.dsj.ColumnDataType;
import net.cumba.cdisc.dsj.ColumnTargetDataType;
import net.cumba.datatable.DataTableColumnMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.IDataTableColumn;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.MissingValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link DsjTableProvider}.
 */
class DsjTableProviderTest
{

    private DsjTableProvider provider;

    @BeforeEach
    void setUp()
    {
        provider = new DsjTableProvider();
    }


    /**
     * Copies a fixture from classpath resources to a temp file. When {@code compress} is set, the
     * fixture is zlib-compressed (yielding the DSJC on-disk shape).
     */
    private URI materialiseFixture(String fixtureName, String suffix, boolean compress)
        throws IOException
    {
        File tmp = File.createTempFile("dsj2test", suffix);
        tmp.deleteOnExit();
        try (InputStream in = getClass().getResourceAsStream("/fixtures/dsj2/" + fixtureName))
        {
            assertNotNull(in, "fixture not found: " + fixtureName);
            if (compress)
            {
                try (Deflater df = new Deflater(Deflater.BEST_SPEED);
                        DeflaterOutputStream dos = new DeflaterOutputStream(
                                Files.newOutputStream(tmp.toPath()), df))
                {
                    in.transferTo(dos);
                }
            }
            else
            {
                Files.copy(in, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return tmp.toURI();
    }


    @Test
    void testGetSupportedFileInfos()
    {
        List<FileInfo> fileInfos = provider.getSupportedFileInfos();
        assertNotNull(fileInfos);
        assertEquals(3, fileInfos.size());
        assertSame(DsjProviderSupplier.FIS, fileInfos);
    }


    @Test
    void testDefaultRowSliceSize()
    {
        assertEquals(10000, provider.getRowSliceSize());
    }


    @Test
    void testSetRowSliceSize()
    {
        provider.setRowSliceSize(5000);
        assertEquals(5000, provider.getRowSliceSize());
    }


    @Test
    void testProvideSimpleTable() throws Exception
    {
        URI uri = materialiseFixture("simple-2col-3rows.json", ".json", false);

        IDataTable table = provider.provide(uri, DsjProviderSupplier.FI_DSJ_JSON);

        assertNotNull(table);
        assertEquals(3, table.getRowCount());
        assertEquals(2, table.getColumnCount());
        assertEquals("TEST", table.getMetaData().getName());
        assertEquals("TEST Table", table.getMetaData().getLabel());
    }


    @Test
    void testProvideReadsStringValues() throws Exception
    {
        URI uri = materialiseFixture("simple-2col-3rows.json", ".json", false);

        IDataTable table = provider.provide(uri, DsjProviderSupplier.FI_DSJ_JSON);

        assertEquals("Subj0", table.getValue(0, 0));
        assertEquals("Subj1", table.getValue(1, 0));
        assertEquals("Subj2", table.getValue(2, 0));
    }


    @Test
    void testProvideReadsDoubleValues() throws Exception
    {
        URI uri = materialiseFixture("simple-2col-3rows.json", ".json", false);

        IDataTable table = provider.provide(uri, DsjProviderSupplier.FI_DSJ_JSON);

        assertEquals(0.0, (double) table.getValue(0, 1), 0.001);
        assertEquals(1.5, (double) table.getValue(1, 1), 0.001);
        assertEquals(3.0, (double) table.getValue(2, 1), 0.001);
    }


    @Test
    void testProvideWithNullValues() throws Exception
    {
        URI uri = materialiseFixture("with-nulls.json", ".json", false);

        IDataTable table = provider.provide(uri, DsjProviderSupplier.FI_DSJ_JSON);

        assertNotNull(table);
        assertEquals(3, table.getRowCount());
        assertEquals(1.0, (double) table.getValue(0, 0), 0.001);
        assertInstanceOf(MissingValue.class, table.getValue(1, 0));
        assertEquals(3.0, (double) table.getValue(2, 0), 0.001);
    }


    @Test
    void testProvideWithIntegerColumn() throws Exception
    {
        URI uri = materialiseFixture("integer-col.json", ".json", false);

        IDataTable table = provider.provide(uri, DsjProviderSupplier.FI_DSJ_JSON);

        assertNotNull(table);
        assertEquals(3, table.getRowCount());
    }


    @Test
    void testIntegerColumnHoldingDecimalIsNotTruncated() throws Exception
    {
        // J2: an `integer`-declared column holding a non-conformant decimal must NOT be truncated.
        // It is stored as floating point (DOUBLE); a whole number still renders without ".0".
        URI uri = materialiseFixture("integer-holds-decimal.json", ".json", false);

        IDataTable table = provider.provide(uri, DsjProviderSupplier.FI_DSJ_JSON);

        assertNotNull(table);
        assertEquals(3, table.getRowCount());
        assertEquals(DataValueType.DOUBLE, table.getMetaData().getColumn(0).getType());
        IDataTableColumn col = table.getColumn(0);
        assertEquals("3", col.getDataValue(0).getValueAsString());
        assertEquals("0.2", col.getDataValue(1).getValueAsString());
        assertEquals(0.2, col.getDataValue(1).getValueAsDouble(), 1e-9);
        assertTrue(col.getDataValue(2).isMissingOrInvalid(), "null cell is missing");
    }


    @Test
    void testProvideWithMixedColumns() throws Exception
    {
        URI uri = materialiseFixture("mixed-cols.json", ".json", false);

        IDataTable table = provider.provide(uri, DsjProviderSupplier.FI_DSJ_JSON);

        assertNotNull(table);
        assertEquals(2, table.getRowCount());
        assertEquals(3, table.getColumnCount());
        assertEquals("Alice", table.getValue(0, 0));
        assertEquals("Bob", table.getValue(1, 0));
    }


    @Test
    void testProvidePreservesColumnMetadata() throws Exception
    {
        URI uri = materialiseFixture("with-length-meta.json", ".json", false);

        IDataTable table = provider.provide(uri, DsjProviderSupplier.FI_DSJ_JSON);

        DataTableColumnMeta colMeta = table.getMetaData().getColumn(0);
        assertEquals("TESTCOL", colMeta.getName());
        assertEquals("Test Column", colMeta.getLabel());
        assertEquals(20, colMeta.getLength());
        assertEquals(DataValueType.STRING, colMeta.getType());
        assertEquals("IT.TEST", colMeta.getMetaData("itemOID"));
    }


    @Test
    void testGetColumnDataTypeForValidTypes()
    {
        assertEquals(ColumnDataType.STRING, provider.getColumnDataTypeFor("string"));
        assertEquals(ColumnDataType.INTEGER, provider.getColumnDataTypeFor("integer"));
        assertEquals(ColumnDataType.DECIMAL, provider.getColumnDataTypeFor("decimal"));
        assertEquals(ColumnDataType.FLOAT, provider.getColumnDataTypeFor("float"));
        assertEquals(ColumnDataType.DOUBLE, provider.getColumnDataTypeFor("double"));
        assertEquals(ColumnDataType.BOOLEAN, provider.getColumnDataTypeFor("boolean"));
        assertEquals(ColumnDataType.DATE, provider.getColumnDataTypeFor("date"));
        assertEquals(ColumnDataType.DATETIME, provider.getColumnDataTypeFor("datetime"));
        assertEquals(ColumnDataType.TIME, provider.getColumnDataTypeFor("time"));
        assertEquals(ColumnDataType.URI, provider.getColumnDataTypeFor("uri"));
    }


    @Test
    void testGetColumnDataTypeForCaseInsensitive()
    {
        assertEquals(ColumnDataType.STRING, provider.getColumnDataTypeFor("STRING"));
        assertEquals(ColumnDataType.STRING, provider.getColumnDataTypeFor("String"));
        assertEquals(ColumnDataType.INTEGER, provider.getColumnDataTypeFor("Integer"));
    }


    @Test
    void testGetColumnDataTypeForUnknownReturnsOther()
    {
        assertEquals(ColumnDataType.OTHER, provider.getColumnDataTypeFor("foobar"));
    }


    @Test
    void testGetColumnDataTypeForNullReturnsOther()
    {
        assertEquals(ColumnDataType.OTHER, provider.getColumnDataTypeFor(null));
    }


    @Test
    void testGetColumnTargetDataTypeForValidTypes()
    {
        assertEquals(ColumnTargetDataType.INTEGER, provider.getColumnTargetDataTypeFor("integer"));
        assertEquals(ColumnTargetDataType.DECIMAL, provider.getColumnTargetDataTypeFor("decimal"));
    }


    @Test
    void testGetColumnTargetDataTypeForCaseInsensitive()
    {
        assertEquals(ColumnTargetDataType.INTEGER, provider.getColumnTargetDataTypeFor("INTEGER"));
        assertEquals(ColumnTargetDataType.DECIMAL, provider.getColumnTargetDataTypeFor("Decimal"));
    }


    @Test
    void testGetColumnTargetDataTypeForNullReturnsUnknown()
    {
        assertEquals(ColumnTargetDataType.UNKNOWN, provider.getColumnTargetDataTypeFor(null));
    }


    @Test
    void testGetColumnTargetDataTypeForUnknownReturnsOther()
    {
        assertEquals(ColumnTargetDataType.OTHER, provider.getColumnTargetDataTypeFor("foobar"));
    }


    @Test
    void testCreateDataTypeMapper()
    {
        DataTableColumnMeta colMeta = DataTableColumnMeta.builder()//
                .index(0).name("COL").type(DataValueType.DOUBLE)//
                .addMetaData("dataType", ColumnDataType.DOUBLE)//
                .addMetaData("targetDataType", ColumnTargetDataType.DECIMAL)//
                .build();

        assertNotNull(provider.createDataTypeMapper(colMeta));
    }


    @Test
    void testProvideWithTargetDataTypeInteger() throws Exception
    {
        URI uri = materialiseFixture("target-integer.json", ".json", false);

        IDataTable table = provider.provide(uri, DsjProviderSupplier.FI_DSJ_JSON);
        assertNotNull(table);
        assertEquals(2, table.getRowCount());
    }


    @Test
    void testProvideWithTargetDataTypeDecimal() throws Exception
    {
        URI uri = materialiseFixture("target-decimal.json", ".json", false);

        IDataTable table = provider.provide(uri, DsjProviderSupplier.FI_DSJ_JSON);
        assertNotNull(table);
        assertEquals(2, table.getRowCount());
    }


    @Test
    void testProvideNdjsonFormat() throws Exception
    {
        URI uri = materialiseFixture("simple-2col-2rows.ndjson", ".ndjson", false);

        IDataTable table = provider.provide(uri, DsjProviderSupplier.FI_DSJ_NDJSON);

        assertNotNull(table);
        assertEquals(2, table.getRowCount());
        assertEquals("Alpha", table.getValue(0, 0));
        assertEquals("Beta", table.getValue(1, 0));
        assertEquals(1.0, (double) table.getValue(0, 1), 0.001);
        assertEquals(2.0, (double) table.getValue(1, 1), 0.001);
    }


    @Test
    void testProvideDsjcFormat() throws Exception
    {
        // DSJC is just zlib-compressed JSON; the parser auto-detects the zlib header.
        URI uri = materialiseFixture("simple-2col-2rows.ndjson", ".dsjc", true);

        IDataTable table = provider.provide(uri, DsjProviderSupplier.FI_DSJ_DSJC);

        assertNotNull(table);
        assertEquals(2, table.getRowCount());
        assertEquals("Alpha", table.getValue(0, 0));
        assertEquals("Beta", table.getValue(1, 0));
    }


    @Test
    void testProvideWithSmallSliceSize() throws Exception
    {
        URI uri = materialiseFixture("simple-2col-25rows.json", ".json", false);

        // The rowSliceSize on the provider doesn't directly control AbstractTableDataParser's
        // slice size, but ensures the provider can handle various data sizes
        IDataTable table = provider.provide(uri, DsjProviderSupplier.FI_DSJ_JSON);
        assertNotNull(table);
        assertEquals(25, table.getRowCount());
    }


    @Test
    void testProvideInvalidURIThrowsException() throws Exception
    {
        URI uri = new URI("file:///nonexistent/path/file.json");
        assertThrows(Exception.class, () -> provider.provide(uri, DsjProviderSupplier.FI_DSJ_JSON));
    }

}
