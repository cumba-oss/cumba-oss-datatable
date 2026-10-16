package net.cumba.datatable.provider.parquet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.MissingValue;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.io.LocalInputFile;
import org.apache.parquet.schema.LogicalTypeAnnotation;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType;
import org.junit.jupiter.api.Test;

/**
 * ECT-Q4 ⓐ (owner 2026-10-16, {@code PLAN-bugfix-empty-column-type} U4): a column whose declared
 * Parquet logical type is {@code UNKNOWN} - only nulls can be stored in it - is a column with no
 * value, so it loads as Char with every cell {@link MissingValue#MIS}. It used to fall through to
 * its physical type, {@code INT32}, and load as a {@code LONG} column.
 *
 * <p>
 * The fixture, {@code src/test/resources/pyfixtures/null_column.parquet}, was written once by
 * pyarrow 24.0.0 through pandas 3.0.1 (an all-{@code None} pandas column, the real-world source)
 * and is committed with its generator, {@code generate-fixtures.py}; no test runs Python.
 * </p>
 */
class ParquetNullTypeColumnTest
{

    private static Path fixture() throws URISyntaxException
    {
        URL url = ParquetNullTypeColumnTest.class.getResource("/pyfixtures/null_column.parquet");
        assertNotNull(url, "the committed pyarrow fixture must be on the test classpath");
        return Path.of(url.toURI());
    }


    /**
     * Non-vacuity: the file really declares {@code X} as a physical {@code INT32} with the logical
     * type {@code UNKNOWN}, read with parquet-java's footer reader rather than through the provider
     * under test.
     */
    @Test
    void theFixtureDeclaresAnInt32WithTheUnknownLogicalType() throws IOException, URISyntaxException
    {
        try (ParquetFileReader reader = ParquetFileReader.open(new LocalInputFile(fixture())))
        {
            MessageType schema = reader.getFooter().getFileMetaData().getSchema();
            PrimitiveType x = schema.getType("X").asPrimitiveType();
            assertEquals(PrimitiveType.PrimitiveTypeName.INT32, x.getPrimitiveTypeName());
            assertInstanceOf(LogicalTypeAnnotation.UnknownLogicalTypeAnnotation.class,
                    x.getLogicalTypeAnnotation());
            assertEquals(3L, reader.getRecordCount());
        }
    }


    @Test
    void aNullTypedColumnLoadsAsCharOfMisInEveryEntryPoint() throws IOException, URISyntaxException
    {
        ParquetTableProvider provider = new ParquetTableProvider();
        IDataTable table = provider.provide(fixture().toUri(), ParquetProviderSupplier.FI_PARQUET);
        DataTableMeta meta = provider.provideMetaData(fixture().toUri(),
                ParquetProviderSupplier.FI_PARQUET);

        int x = table.getMetaData().getColumnIndex("X");
        int id = table.getMetaData().getColumnIndex("ID");
        assertEquals(DataValueType.STRING, table.getMetaData().getColumn(x).getType());
        assertEquals(DataValueType.STRING, meta.getColumn(meta.getColumnIndex("X")).getType());
        assertEquals(DataValueType.LONG, table.getMetaData().getColumn(id).getType(),
                "the INT64 sibling keeps its declared type");
        for (int r = 0; r < 3; r++)
        {
            assertSame(MissingValue.MIS, table.getValue(r, x), "X row " + r);
            assertEquals((long) r + 1, table.getValue(r, id));
        }
    }
}
