package net.cumba.datatable.provider.parquet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.values.DataValueSupport;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.IDataValue;
import net.cumba.datatable.values.MissingValue;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.api.ReadSupport;
import org.apache.parquet.hadoop.example.GroupReadSupport;
import org.apache.parquet.io.LocalInputFile;
import org.junit.jupiter.api.Test;

/**
 * ⭐ Owner ruling E5, 2026-09-25 (PLAN-bare-nan-is-mis): <i>"NaN should get mis"</i>, against the
 * one real source of a bare NaN in the stack &mdash; a pandas / pyarrow NaN in a Parquet DOUBLE or
 * FLOAT column. It read {@code MIS_UNKNOWN} until that ruling ({@code ParquetTableProvider}'s
 * {@code MissingValue.forValue(dbl, MIS_UNKNOWN)}).
 *
 * <p>
 * The fixture, {@code src/test/resources/pyfixtures/bare_nan.parquet}, was written <b>once</b> by
 * pyarrow 24.0.0 and is committed with its generator, {@code generate-fixtures.py}; no test runs
 * Python. It is an external oracle on purpose: the module's other NaN test writes its file with
 * parquet-java, the same library family the reader uses, so it could only ever meet the bits a Java
 * writer chose. Rows 0 and 1 of both columns are the two canonical quiet NaNs of ruling N1
 * ({@code np.nan} and the x86 arithmetic {@code 0.0 / 0.0}, which carries the sign bit), row 2 a
 * Parquet null, row 3 the present value {@code 1.25}.
 * </p>
 *
 * <p>
 * ⚠ <b>Sensitivity:</b> reverting the provider's NaN arm to {@code forValue(dbl, MIS_UNKNOWN)} reds
 * rows 0 and 1 of both columns with {@code MIS_UNKNOWN}. The null row cannot tell the difference
 * &mdash; a Parquet null is {@code MIS} by its own, older ruling &mdash; which is why the NaN rows
 * are asserted separately and their bits are proven first.
 * </p>
 */
class ParquetPyarrowBareNanTest
{

    private static final long POSITIVE_BARE_BITS = 0x7FF8_0000_0000_0000L;

    private static final long NEGATIVE_BARE_BITS = 0xFFF8_0000_0000_0000L;

    private static final int POSITIVE_BARE_FLOAT_BITS = 0x7FC0_0000;

    private static final int NEGATIVE_BARE_FLOAT_BITS = 0xFFC0_0000;

    private static Path fixture() throws URISyntaxException
    {
        return Path.of(assertResource().toURI());
    }


    private static URL assertResource()
    {
        URL url = ParquetPyarrowBareNanTest.class.getResource("/pyfixtures/bare_nan.parquet");
        assertNotNull(url, "the committed pyarrow fixture must be on the test classpath");
        return url;
    }


    /**
     * Non-vacuity: the file really holds both bare patterns, read with parquet-java's example
     * reader rather than through the provider under test. A regenerated fixture whose writer
     * canonicalised the sign bit away would otherwise test one pattern twice, green.
     */
    @Test
    void theFixtureHoldsBothBarePatternsInEachColumn() throws IOException, URISyntaxException
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
        assertEquals(4, rows.size(), "four rows");
        assertEquals(POSITIVE_BARE_BITS,
                Double.doubleToRawLongBits(rows.get(0).getDouble("DBL", 0)), "DBL row 0");
        assertEquals(NEGATIVE_BARE_BITS,
                Double.doubleToRawLongBits(rows.get(1).getDouble("DBL", 0)), "DBL row 1");
        assertEquals(POSITIVE_BARE_FLOAT_BITS,
                Float.floatToRawIntBits(rows.get(0).getFloat("FLT", 0)), "FLT row 0");
        assertEquals(NEGATIVE_BARE_FLOAT_BITS,
                Float.floatToRawIntBits(rows.get(1).getFloat("FLT", 0)), "FLT row 1");
        assertEquals(0, rows.get(2).getFieldRepetitionCount("DBL"), "DBL row 2 is a null");
        assertEquals(0, rows.get(2).getFieldRepetitionCount("FLT"), "FLT row 2 is a null");
        assertEquals(1.25, rows.get(3).getDouble("DBL", 0));
        assertEquals(1.25f, rows.get(3).getFloat("FLT", 0));
    }


    @Test
    void aPyarrowNaNReadsMisInDoubleAndFloatColumns() throws IOException, URISyntaxException
    {
        URI uri = fixture().toUri();
        IDataTable table = new ParquetTableProvider().provide(uri,
                ParquetProviderSupplier.FI_PARQUET);
        assertEquals(4L, table.getRowCount());
        for (String name : List.of("DBL", "FLT"))
        {
            int col = table.getMetaData().getColumnIndex(name);
            assertTrue(col >= 0, name + " must exist");
            assertEquals(DataValueType.DOUBLE, table.getMetaData().getColumn(col).getType(),
                    name + ": FLOAT and DOUBLE both map to DOUBLE");
            for (int row = 0; row <= 1; row++)
            {
                String where = name + " row " + row + " (a bare NaN)";
                IDataValue dv = table.getDataValue(row, col);
                assertTrue(dv.isMissingOrInvalid(), where);
                assertSame(MissingValue.MIS, DataValueSupport.getMissingValue(dv), where);
                assertSame(MissingValue.MIS, table.getValue(row, col), where + ": raw channel");
            }
            assertSame(MissingValue.MIS, table.getValue(2, col),
                    name + " row 2: a Parquet null is MIS by its own ruling");
            assertEquals(1.25, (double) table.getValue(3, col), name + " row 3");
        }
    }
}
