package net.cumba.datatable.provider.parquet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.cumba.datatable.DataTableMeta;
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
 * Parquet STRING is UTF-8 by the format, so both read paths report one {@code UTF-8} /
 * {@code FORMAT} pair, and neither a raw footer key nor a {@code cumba:meta} blob written by an
 * exporter that still carried the source table's charset leaks through
 * (PLAN-provider-encoding-metadata, E8).
 */
class ParquetTableProviderEncodingTest
{

    @TempDir
    Path tempDir;

    @Test
    void bothPathsReportUtf8FormatAndNoFooterEncodingLeaksThrough() throws IOException
    {
        MessageType schema = Types.buildMessage().optional(PrimitiveTypeName.BINARY)
                .as(LogicalTypeAnnotation.stringType()).named("USUBJID").named("table");
        Path file = tempDir.resolve("enc.parquet");
        Map<String, String> extra = new HashMap<>();
        extra.put("Encoding", "latin1"); // a foreign writer's raw key
        extra.put("EncodingSource", "DECLARED");
        extra.put(ParquetMetadataCodec.META_KEY,
                "{\"tableMeta\":{\"Encoding\":\"windows-1252\",\"EncodingSource\":\"DECLARED\"}}");
        try (ParquetWriter<Group> writer = ExampleParquetWriter.builder(new LocalOutputFile(file))
                .withType(schema).withCompressionCodec(CompressionCodecName.SNAPPY)
                .withConf(new Configuration()).withExtraMetaData(extra).build())
        {
            writer.write(new SimpleGroupFactory(schema).newGroup().append("USUBJID", "SUBJ-1"));
        }

        for (DataTableMeta meta : List.of(
                new ParquetTableProvider().provide(file.toUri(), ParquetProviderSupplier.FI_PARQUET)
                        .getMetaData(),
                new ParquetTableProvider().provideMetaData(file.toUri(),
                        ParquetProviderSupplier.FI_PARQUET)))
        {
            assertEquals("UTF-8", meta.getMetaData("Encoding"));
            assertEquals("FORMAT", meta.getMetaData("EncodingSource"));
            assertEquals(1, meta.getMetaDataKeys().stream().filter("Encoding"::equals).count(),
                    "one Encoding row, not a duplicate from the footer: " + meta.getMetaDataKeys());
            assertEquals(1,
                    meta.getMetaDataKeys().stream().filter("EncodingSource"::equals).count());
        }
    }
}
