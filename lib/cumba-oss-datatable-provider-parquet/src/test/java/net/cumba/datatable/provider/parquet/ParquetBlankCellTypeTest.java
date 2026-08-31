package net.cumba.datatable.provider.parquet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.io.FileInfo;
import net.cumba.datatable.values.DataValueType;
import net.cumba.datatable.values.MissingValue;
import org.apache.hadoop.conf.Configuration;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.example.data.simple.SimpleGroupFactory;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.example.ExampleParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.io.LocalOutputFile;
import org.apache.parquet.schema.LogicalTypeAnnotation;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName;
import org.apache.parquet.schema.Types;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A Parquet {@code null} is loaded as a {@link MissingValue} in <b>every</b> column type, character
 * included; an empty string the file genuinely contains stays an empty string.
 *
 * <h2>⚠ This is a re-based Fix #161 guard, not a new one</h2>
 * <p>
 * Fix #161's defect was real and still is: <b>the same study, the same rule and the same blank cell
 * produced different findings depending only on which file format the sponsor wrote.</b> This
 * provider caused it by mapping a null cell to {@code MissingValue.MIS} <em>regardless of type</em>
 * while every other loader mapped a blank CHARACTER cell to {@code ""}.
 * <p>
 * Fix #161 secured that at ingestion, by flattening a null STRING cell to {@code ""} here. That is
 * no longer the mechanism: Parquet <em>can</em> express a null in a character column, the model now
 * keeps it, and finding-equivalence is guaranteed at consumption instead — every blankness consumer
 * asks {@code isEmptyOrMissing()}, and value resolution goes through
 * {@code ScalarSemantics.resolvedString}, for which a {@code MissingValue} in a character column
 * reads exactly as {@code ""} does.
 * <p>
 * So the assertion that moved is the <em>representation</em> one; the guard itself is kept, because
 * the defect it was written against has not gone away.
 * <p>
 * The fixture is built so that only the null path can produce the cell under test: row 1 leaves
 * both fields unset, so the reader hands the parser a {@code null} for each and no other branch of
 * {@code addData2Column} is reachable for them. Row 2 then carries a <em>present</em> empty string
 * in the same character column — without it the test would pass under either design and pin
 * nothing.
 */
class ParquetBlankCellTypeTest
{

    @TempDir
    Path tempDir;

    private static MessageType schema()
    {
        return Types.buildMessage()//
                .optional(PrimitiveTypeName.BINARY).as(LogicalTypeAnnotation.stringType())
                .named("CHARCOL")//
                .optional(PrimitiveTypeName.DOUBLE).named("NUMCOL")//
                .named("table");
    }


    private Path writeFixture() throws IOException
    {
        MessageType schema = schema();
        Path file = tempDir.resolve("blankcell.parquet");
        SimpleGroupFactory factory = new SimpleGroupFactory(schema);
        try (ParquetWriter<Group> writer = ExampleParquetWriter.builder(new LocalOutputFile(file))
                .withType(schema).withCompressionCodec(CompressionCodecName.SNAPPY)
                .withConf(new Configuration()).build())
        {
            // Row 0 carries values, so the columns are typed from real data.
            writer.write(factory.newGroup().append("CHARCOL", "A").append("NUMCOL", 1.0d));
            // Row 1 leaves both fields unset — the only way to reach the null branch.
            writer.write(factory.newGroup());
            // Row 2 carries a PRESENT empty string in the same character column, so the two
            // blanks under test differ only in what the file said.
            writer.write(factory.newGroup().append("CHARCOL", "").append("NUMCOL", 2.0d));
        }
        return file;
    }


    private IDataTable load() throws IOException
    {
        Path file = writeFixture();
        ParquetTableProvider provider = new ParquetTableProvider();
        IDataTable table = provider.provide(file.toUri(), (FileInfo) null);

        assertEquals(3, table.getRowCount());
        assertEquals(DataValueType.STRING, table.getMetaData().getColumn(0).getType());
        assertEquals(DataValueType.DOUBLE, table.getMetaData().getColumn(1).getType());
        // Sanity: row 0 proves the fixture carries real values, so the blanks below are the
        // provider's doing and not an empty read.
        assertEquals("A", table.getValue(0, 0));
        return table;
    }


    /** The model half: a source null and a genuine empty string are <b>not</b> the same cell. */
    @Test
    void aSourceNullBecomesAMissingValueInEveryColumnTypeAndAnEmptyStringStaysEmpty()
        throws Exception
    {
        IDataTable table = load();

        // Character column, row 1: the file said null.
        assertInstanceOf(MissingValue.class, table.getValue(1, 0),
                "a Parquet null in a CHARACTER column must load as a MissingValue");
        assertTrue(table.getDataValue(1, 0).isMissingOrInvalid());

        // Character column, row 2: the file said "". It must never be promoted to a MissingValue.
        assertEquals("", table.getValue(2, 0));
        assertFalse(table.getDataValue(2, 0).isMissingOrInvalid(),
                "an empty string the file genuinely contains must stay an empty string");

        // The numeric column is unchanged: a null is still missing.
        assertTrue(table.getDataValue(1, 1).isMissingOrInvalid(),
                "a blank NUMERIC cell stays missing");
        assertInstanceOf(MissingValue.class, table.getValue(1, 1));
    }


    /**
     * The engine half, and the actual Fix #161 contract: <b>no blankness consumer can tell the two
     * apart.</b> Row 0 is the control — without it a predicate that answered "blank" to everything
     * would satisfy this test.
     */
    @Test
    void everyBlanknessConsumerTreatsTheNullAndTheEmptyStringIdentically() throws Exception
    {
        IDataTable table = load();

        for (long row : new long[]
        {
                1L, 2L
        })
        {
            assertTrue(table.isEmptyOrMissing(row, 0),
                    "IDataTable.isEmptyOrMissing must fold both blanks, row " + row);
            assertTrue(table.getColumn(0).isEmptyOrMissing(row),
                    "IDataTableColumn.isEmptyOrMissing must fold both blanks, row " + row);
            assertTrue(table.getDataValue(row, 0).isEmptyOrMissing(),
                    "IDataValue.isEmptyOrMissing must fold both blanks, row " + row);
        }

        assertFalse(table.isEmptyOrMissing(0, 0), "the populated control row must not read blank");
        assertFalse(table.getColumn(0).isEmptyOrMissing(0),
                "the populated control row must not read blank (column level)");
    }
}
