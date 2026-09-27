package net.cumba.datatable.provider.parquet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.values.DataValueType;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.api.ReadSupport;
import org.apache.parquet.hadoop.example.GroupReadSupport;
import org.apache.parquet.io.LocalInputFile;
import org.junit.jupiter.api.Test;

/**
 * PLAN-negative-zero-on-load, reader row "Parquet" (NZL O1, owner 2026-09-27: <i>"we drop the sign
 * from any 0.0 so a -0.0 gets read as 0.0"</i>). A pyarrow {@code -0.0} in a DOUBLE or FLOAT column
 * is stored as its raw IEEE bits; after loading, the cell must be {@code +0.0}.
 *
 * <p>
 * The fixture, {@code src/test/resources/pyfixtures/negative_zero.parquet}, was written <b>once</b>
 * by pyarrow 24.0.0 and is committed with its generator, {@code generate-fixtures.py} (plan-14
 * precedent: {@code bare_nan.parquet}). Row 0 is {@code -0.0}, row 1 the {@code +0.0} control, row
 * 2 the non-zero control {@code -1.5}, in both columns.
 * </p>
 */
class NegativeZeroOnLoadParquetTest
{

    private static final long NEGATIVE_ZERO_BITS = 0x8000_0000_0000_0000L;

    private static final int NEGATIVE_ZERO_FLOAT_BITS = 0x8000_0000;

    private static final double CONTROL = -1.5;

    private static Path fixture() throws URISyntaxException
    {
        URL url = NegativeZeroOnLoadParquetTest.class
                .getResource("/pyfixtures/negative_zero.parquet");
        assertNotNull(url, "the committed pyarrow fixture must be on the test classpath");
        return Path.of(url.toURI());
    }


    /**
     * (1) Non-vacuity: the file really holds a negative zero in each column, read with
     * parquet-java's example reader rather than through the provider under test.
     */
    @Test
    void theFixtureHoldsANegativeZeroInEachColumn() throws IOException, URISyntaxException
    {
        List<Group> rows = new ArrayList<>();
        try (ParquetReader<Group> reader = new ParquetReader.Builder<Group>(
                new LocalInputFile(fixture()))
        {

            @Override
            protected ReadSupport<Group> getReadSupport()
            {
                return new GroupReadSupport();
            }
        }.build())
        {
            for (Group g = reader.read(); g != null; g = reader.read())
            {
                rows.add(g);
            }
        }
        assertEquals(3, rows.size(), "three rows");
        assertEquals(NEGATIVE_ZERO_BITS,
                Double.doubleToRawLongBits(rows.get(0).getDouble("DBL", 0)), "DBL row 0");
        assertEquals(NEGATIVE_ZERO_FLOAT_BITS,
                Float.floatToRawIntBits(rows.get(0).getFloat("FLT", 0)), "FLT row 0");
        assertEquals(0L, Double.doubleToRawLongBits(rows.get(1).getDouble("DBL", 0)), "DBL row 1");
        assertEquals(0, Float.floatToRawIntBits(rows.get(1).getFloat("FLT", 0)), "FLT row 1");
        assertEquals(CONTROL, rows.get(2).getDouble("DBL", 0), "DBL row 2");
        assertEquals((float) CONTROL, rows.get(2).getFloat("FLT", 0), "FLT row 2");
    }


    /** (2) The loaded DOUBLE cell is {@code +0.0}; controls untouched. */
    @Test
    void aPyarrowNegativeZeroDoubleLoadsAsPositiveZero() throws IOException, URISyntaxException
    {
        assertColumnLoadsPositiveZero("DBL");
    }


    /** (2) The loaded FLOAT cell (widened to DOUBLE) is {@code +0.0}; controls untouched. */
    @Test
    void aPyarrowNegativeZeroFloatLoadsAsPositiveZero() throws IOException, URISyntaxException
    {
        assertColumnLoadsPositiveZero("FLT");
    }


    private static void assertColumnLoadsPositiveZero(String name)
        throws IOException, URISyntaxException
    {
        IDataTable table = new ParquetTableProvider().provide(fixture().toUri(),
                ParquetProviderSupplier.FI_PARQUET);
        assertEquals(3L, table.getRowCount());
        int col = table.getMetaData().getColumnIndex(name);
        assertTrue(col >= 0, name + " must exist");
        assertEquals(DataValueType.DOUBLE, table.getMetaData().getColumn(col).getType(), name);
        assertCellBits(table, 1, col, 0L, name + " row 1 (+0.0 control)");
        assertCellBits(table, 2, col, Double.doubleToRawLongBits(CONTROL),
                name + " row 2 (-1.5 control)");
        assertCellBits(table, 0, col, 0L, name + " row 0 (a -0.0 in the file)");
    }


    private static void assertCellBits(IDataTable table, long row, int col, long expectedBits,
            String where)
    {
        Number raw = assertInstanceOf(Number.class, table.getValue(row, col), where);
        assertEquals(expectedBits, Double.doubleToRawLongBits(raw.doubleValue()),
                where + ": raw bits of getValue");
        assertEquals(expectedBits,
                Double.doubleToRawLongBits(table.getDataValue(row, col).getValueAsDouble()),
                where + ": raw bits of getDataValue().getValueAsDouble()");
    }
}
