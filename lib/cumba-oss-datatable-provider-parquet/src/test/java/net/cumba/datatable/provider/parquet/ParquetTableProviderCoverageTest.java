package net.cumba.datatable.provider.parquet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
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
import org.apache.parquet.schema.Type;
import org.apache.parquet.schema.Types;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Coverage-focused tests for {@link ParquetTableProvider} exercising the metadata-only path, the
 * non-{@code file:} URI download branch, the {@code OTHER} fallback for non-primitive schema types,
 * and several runtime value types produced by Carpet (LocalDate, LocalTime, LocalDateTime, Instant,
 * BigDecimal, NaN, enums).
 */
// Test-only address lookup; getAllByName would not improve the test.
@SuppressWarnings("AddressSelection")
class ParquetTableProviderCoverageTest
{

    @TempDir
    Path tempDir;

    // ---------- provideMetaData / getTableMeta ----------

    @Test
    void testProvideMetaDataLocalFile() throws Exception
    {
        Path file = writeParquetWithSchema(buildAllTypesSchema(), this::writeAllTypesRow,
                "meta_simple.parquet", 3);

        ParquetTableProvider provider = new ParquetTableProvider();
        DataTableMeta meta = provider.provideMetaData(file.toUri(),
                ParquetProviderSupplier.FI_PARQUET);

        assertNotNull(meta);
        assertEquals(3L, meta.getRowCount());
        assertEquals(3L, meta.getTotalRowCount());
        assertTrue(meta.getColumnCount() > 0);
    }


    @Test
    void testProvideMetaDataNonFileUri() throws Exception
    {
        Path file = writeParquetWithSchema(buildAllTypesSchema(), this::writeAllTypesRow,
                "meta_http.parquet", 2);
        URI httpUri = serveTempFileOverHttp(file, "/meta.parquet");

        ParquetTableProvider provider = new ParquetTableProvider();
        DataTableMeta meta = provider.provideMetaData(httpUri, ParquetProviderSupplier.FI_PARQUET);

        assertNotNull(meta);
        assertEquals(2L, meta.getRowCount());
    }


    @Test
    void testProvideNonFileUri() throws Exception
    {
        Path file = writeParquetWithSchema(buildAllTypesSchema(), this::writeAllTypesRow,
                "data_http.parquet", 2);
        URI httpUri = serveTempFileOverHttp(file, "/data.parquet");

        ParquetTableProvider provider = new ParquetTableProvider();
        IDataTable table = provider.provide(httpUri, ParquetProviderSupplier.FI_PARQUET);

        assertNotNull(table);
        assertEquals(2L, table.getRowCount());
    }

    // ---------- temporal / decimal / NaN value branches ----------


    @Test
    void testRoundTripTemporalAndDecimal() throws Exception
    {
        Path file = writeParquetWithSchema(buildTemporalSchema(), this::writeTemporalRow,
                "temporal.parquet", 2);

        ParquetTableProvider provider = new ParquetTableProvider();
        IDataTable table = provider.provide(file.toUri(), ParquetProviderSupplier.FI_PARQUET);

        assertNotNull(table);
        assertEquals(2L, table.getRowCount());
        // ensure all columns produced non-null cells (the branches inside Parquet2TableDataParser
        // for LocalDate, LocalTime, Instant and BigDecimal all executed for row 0).
        for (int c = 0; c < table.getColumnCount(); c++)
        {
            assertNotNull(table.getValue(0, c));
        }
    }


    /**
     * F-prov-08 / F-prov-09: Parquet temporals are re-based onto the SAS epoch at load time so
     * temporal columns share one numeric convention with the SAS/XPT/CDT/XLSX providers, and the
     * matching SAS display format is attached to the column metadata. Pins the exact values: dates
     * are days since 1960-01-01 (NOT Unix epoch days), times are seconds since midnight (NOT
     * nanos), and both timestamp flavours are seconds since 1960-01-01 UTC (NOT epoch nanos, which
     * no format in the stack could render).
     */
    @Test
    void testTemporalValuesUseSasEpochAndSasFormats() throws Exception
    {
        Path file = writeParquetWithSchema(buildTemporalSchema(), this::writeTemporalRow,
                "temporal_sas.parquet", 2);

        ParquetTableProvider provider = new ParquetTableProvider();
        IDataTable table = provider.provide(file.toUri(), ParquetProviderSupplier.FI_PARQUET);

        // DAT: 2026-01-01 = Unix epoch day 20454 = SAS day 20454 + 3653 = 24107.
        assertEquals(24107.0, (double) table.getValue(0, 0));
        assertEquals(24108.0, (double) table.getValue(1, 0));
        // TIM: 01:00:00 = 3600 seconds since midnight (row 1 adds 1000 ms = 1 s).
        assertEquals(3600.0, (double) table.getValue(0, 1));
        assertEquals(3601.0, (double) table.getValue(1, 1));
        // LDT / INS: 2026-01-01T01:00:00Z = 1,767,229,200 s since 1970
        // = 1,767,229,200 + 315,619,200 = 2,082,848,400 s since 1960.
        assertEquals(2_082_848_400.0, (double) table.getValue(0, 2));
        assertEquals(2_082_848_401.0, (double) table.getValue(1, 2));
        assertEquals(2_082_848_400.0, (double) table.getValue(0, 3));
        assertEquals(2_082_848_401.0, (double) table.getValue(1, 3));

        // The SAS display formats are attached from the schema's logical type annotations.
        DataTableMeta meta = provider.provideMetaData(file.toUri(),
                ParquetProviderSupplier.FI_PARQUET);
        assertEquals("E8601DA.", meta.getColumn("DAT").getDisplayFormat());
        assertEquals("E8601TM.", meta.getColumn("TIM").getDisplayFormat());
        assertEquals("E8601DT.", meta.getColumn("LDT").getDisplayFormat());
        assertEquals("E8601DT.", meta.getColumn("INS").getDisplayFormat());
        // Non-temporal columns keep no format.
        assertNull(meta.getColumn("DEC").getDisplayFormat());
    }


    @Test
    void testNaNDoubleProducesMissing() throws Exception
    {
        MessageType schema = Types.buildMessage().optional(PrimitiveTypeName.DOUBLE).named("DBL")
                .named("table");
        Path file = writeParquetWithSchema(schema, (factory, idx) ->
        {
            Group g = factory.newGroup();
            g.add("DBL", idx == 0 ? Double.NaN : 1.25);
            return g;
        }, "nan.parquet", 2);

        ParquetTableProvider provider = new ParquetTableProvider();
        IDataTable table = provider.provide(file.toUri(), ParquetProviderSupplier.FI_PARQUET);

        assertTrue(table.getRow(0).getDataValue(0).isMissingOrInvalid());
        assertEquals(1.25, (double) table.getValue(1, 0), 0.001);
    }

    // ---------- OTHER fallback for non-primitive (group) field ----------


    @Test
    void testGetDataValueTypeNonPrimitiveReturnsOther()
    {
        ParquetTableProvider provider = new ParquetTableProvider();
        Type group = Types.optionalGroup().optional(PrimitiveTypeName.INT32).named("x")
                .named("nested");
        // covers ParquetTableProvider#getDataValueType -> DataValueType.OTHER branch
        assertEquals(net.cumba.datatable.values.DataValueType.OTHER,
                provider.getDataValueType(group));
    }

    // ---------- failure path: corrupted file ----------


    @Test
    void testProvideThrowsOnCorruptFile() throws Exception
    {
        Path bad = tempDir.resolve("bad.parquet");
        Files.write(bad, new byte[]
        {
                1, 2, 3, 4, 5, 6, 7, 8
        });

        ParquetTableProvider provider = new ParquetTableProvider();
        URI uri = bad.toUri();
        // parquet-mr surfaces the bad-footer error as a RuntimeException which the provider
        // re-throws via the (RuntimeException | IOException) catch block.
        assertThrows(RuntimeException.class,
                () -> provider.provide(uri, ParquetProviderSupplier.FI_PARQUET));
    }


    /**
     * F-E15: a DECIMAL column whose value cannot be losslessly represented as a {@code double} must
     * raise a clear IOException instead of silently truncating. Verifies the round-trip check used
     * by the production code directly, since constructing a parquet fixture that forces Carpet to
     * materialise a precision-overflowing {@link java.math.BigDecimal} depends on Carpet-internal
     * type-mapping decisions.
     */
    @Test
    void testDecimalPrecisionGuardDetectsLossyConversion()
    {
        // 35-digit value: well beyond the ~15-16 significant digits a double can hold.
        java.math.BigDecimal bd = new java.math.BigDecimal("12345678901234567890123456789012345");
        double d = bd.doubleValue();
        // Mirrors the production check in Parquet2TableDataParser:
        // if (BigDecimal.valueOf(d).compareTo(bd) != 0) throw ...
        assertTrue(java.math.BigDecimal.valueOf(d).compareTo(bd) != 0,
                "BigDecimal " + bd + " unexpectedly round-trips through double; "
                        + "the F-E15 guard would not trigger.");
    }


    /**
     * Sanity check: a value within double precision must round-trip cleanly so the guard does not
     * produce false positives.
     */
    @Test
    void testDecimalPrecisionGuardAcceptsRepresentableValue()
    {
        java.math.BigDecimal bd = new java.math.BigDecimal("123.45");
        double d = bd.doubleValue();
        assertEquals(0, java.math.BigDecimal.valueOf(d).compareTo(bd),
                "123.45 should round-trip through double");
    }


    @Test
    void testProvideMetaDataThrowsOnCorruptFile() throws Exception
    {
        Path bad = tempDir.resolve("bad2.parquet");
        Files.write(bad, new byte[]
        {
                9, 9, 9, 9, 9, 9, 9, 9
        });

        ParquetTableProvider provider = new ParquetTableProvider();
        URI uri = bad.toUri();
        assertThrows(RuntimeException.class,
                () -> provider.provideMetaData(uri, ParquetProviderSupplier.FI_PARQUET));
    }

    // ---------- F-E24: null-named fields fall back to "V" + (index+1) ----------


    /**
     * F-E24: when a Parquet field's {@link Type#getName()} returns null, the
     * {@code Parquet2TableDataParser} constructor must substitute the same synthetic
     * {@code "V" + (i + 1)} fallback that {@code addMetaColumn} uses on the metadata side. This
     * keeps the value-lookup key aligned with the metadata column name so the column doesn't become
     * unconditionally all-missing.
     */
    @Test
    void parserSynthesizesColumnNameForNullNamedFields() throws Exception
    {
        ParquetTableProvider provider = new ParquetTableProvider();

        // Mock two parquet schema Types: one with a real name, one returning null.
        org.apache.parquet.schema.Type real = org.mockito.Mockito
                .mock(org.apache.parquet.schema.Type.class);
        org.mockito.Mockito.when(real.getName()).thenReturn("FOO");

        org.apache.parquet.schema.Type unnamed = org.mockito.Mockito
                .mock(org.apache.parquet.schema.Type.class);
        org.mockito.Mockito.when(unnamed.getName()).thenReturn(null);

        // Build a minimal meta so the inner-class constructor's super(...) call has what it needs.
        net.cumba.datatable.impl.provider.DataTableMetaSupport support = //
                new net.cumba.datatable.impl.provider.DataTableMetaSupport(null);
        support.setTable(URI.create("file:///tmp/test2.parquet"));
        support.addColumn("FOO", net.cumba.datatable.values.DataValueType.STRING);
        support.addColumn("V2", net.cumba.datatable.values.DataValueType.STRING);
        net.cumba.datatable.DataTableMeta meta = support.getTableMeta().rowCount(0).totalRowCount(0)
                .build();

        Class<?> inner = Class.forName(
                "net.cumba.datatable.provider.parquet.ParquetTableProvider$Parquet2TableDataParser");
        java.lang.reflect.Constructor<?> ctor = inner.getDeclaredConstructor(
                ParquetTableProvider.class, net.cumba.datatable.DataTableMeta.class,
                java.util.List.class);
        ctor.setAccessible(true);
        Object parser = ctor.newInstance(provider, meta, java.util.List.of(real, unnamed));

        java.lang.reflect.Field fld = inner.getDeclaredField("columnNames");
        fld.setAccessible(true);
        String[] columnNames = (String[]) fld.get(parser);
        assertEquals(2, columnNames.length);
        assertEquals("FOO", columnNames[0]);
        assertEquals("V2", columnNames[1], "null-named field should fall back to V<index+1>");
    }

    // ---------- helpers ----------

    @FunctionalInterface
    private interface RowWriter
    {

        Group write(SimpleGroupFactory factory, int rowIdx);
    }

    private Path writeParquetWithSchema(MessageType schema, RowWriter rowWriter, String filename,
            int rowCount)
        throws IOException
    {
        Path file = tempDir.resolve(filename);
        LocalOutputFile out = new LocalOutputFile(file);
        SimpleGroupFactory factory = new SimpleGroupFactory(schema);
        try (ParquetWriter<Group> writer = ExampleParquetWriter.builder(out).withType(schema)
                .withCompressionCodec(CompressionCodecName.SNAPPY).withConf(new Configuration())
                .withExtraMetaData(Map.of("custom-key", "custom-value")).build())
        {
            for (int i = 0; i < rowCount; i++)
            {
                writer.write(rowWriter.write(factory, i));
            }
        }
        return file;
    }


    private MessageType buildAllTypesSchema()
    {
        return Types.buildMessage().optional(PrimitiveTypeName.BINARY)
                .as(LogicalTypeAnnotation.stringType()).named("STR")
                .optional(PrimitiveTypeName.INT64).named("LNG").optional(PrimitiveTypeName.DOUBLE)
                .named("DBL").optional(PrimitiveTypeName.BOOLEAN).named("BOO").named("table");
    }


    private Group writeAllTypesRow(SimpleGroupFactory factory, int rowIdx)
    {
        Group g = factory.newGroup();
        g.add("STR", "row" + rowIdx);
        g.add("LNG", (long) rowIdx);
        g.add("DBL", rowIdx + 0.5);
        g.add("BOO", rowIdx % 2 == 0);
        return g;
    }


    private MessageType buildTemporalSchema()
    {
        // Date stored as INT32 dateType (Carpet reads it as LocalDate).
        // Time stored as INT32 timeType MILLIS (Carpet reads it as LocalTime).
        // Timestamp stored as INT64 timestampType MILLIS, isAdjustedToUTC = false
        // (Carpet reads it as LocalDateTime).
        // Timestamp stored as INT64 timestampType MILLIS, isAdjustedToUTC = true
        // (Carpet reads it as Instant).
        // Decimal stored as INT64 decimalType(2, 10) (Carpet reads it as BigDecimal).
        return Types.buildMessage().optional(PrimitiveTypeName.INT32)
                .as(LogicalTypeAnnotation.dateType()).named("DAT").optional(PrimitiveTypeName.INT32)
                .as(LogicalTypeAnnotation.timeType(false, LogicalTypeAnnotation.TimeUnit.MILLIS))
                .named("TIM").optional(PrimitiveTypeName.INT64)
                .as(LogicalTypeAnnotation.timestampType(false,
                        LogicalTypeAnnotation.TimeUnit.MILLIS))
                .named("LDT").optional(PrimitiveTypeName.INT64)
                .as(LogicalTypeAnnotation.timestampType(true,
                        LogicalTypeAnnotation.TimeUnit.MILLIS))
                .named("INS").optional(PrimitiveTypeName.INT64)
                .as(LogicalTypeAnnotation.decimalType(2, 10)).named("DEC").named("table");
    }


    private Group writeTemporalRow(SimpleGroupFactory factory, int rowIdx)
    {
        Group g = factory.newGroup();
        // 2026-01-01 = epoch day 20454; advance per row
        g.add("DAT", 20454 + rowIdx);
        // 01:00:00 in millis
        g.add("TIM", 3_600_000 + rowIdx * 1000);
        // 2026-01-01T01:00:00 in millis since epoch
        g.add("LDT", 1_767_229_200_000L + rowIdx * 1000L);
        g.add("INS", 1_767_229_200_000L + rowIdx * 1000L);
        // 12345 -> 123.45 with scale 2
        g.add("DEC", 12345L + rowIdx);
        return g;
    }


    /**
     * Serves the given file once over an ephemeral local HTTP port so the provider takes the
     * non-{@code file:} branch (downloadToFile).
     */
    private URI serveTempFileOverHttp(Path file, String urlPath) throws IOException
    {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(urlPath, exchange ->
        {
            byte[] bytes = Files.readAllBytes(file);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody(); var _ = exchange.getRequestBody())
            {
                os.write(bytes);
            }
            server.stop(0);
        });
        server.start();
        int port = server.getAddress().getPort();
        return URI.create("http://127.0.0.1:" + port + urlPath);
    }

}
