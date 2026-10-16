package net.cumba.datatable.provider.dsj;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.DeflaterOutputStream;
import net.cumba.datatable.DataTableMeta;
import net.cumba.datatable.IDataTable;
import net.cumba.datatable.impl.provider.DataTableMetaSupport;
import net.cumba.datatable.io.FileInfo;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Owner ruling B2-Q3 (2026-10-16, ⓒ <i>"keep reporting UTF-8"</i>): a Dataset-JSON file that is
 * physically UTF-16 (Jackson auto-detects it) still loads, and its table reports {@code UTF-8} /
 * {@code FORMAT} &mdash; for Dataset-JSON the {@code Encoding} names the format's mandated charset
 * (RFC 8259 §8.1; Dataset-JSON lengths are characters), the one its byte counts are meant in, not
 * the transport's. Pins the documented choice on every load path that reads such a file: JSON,
 * NDJSON single-threaded, NDJSON parallel, DSJC; full load and metadata-only agree.
 *
 * <p>
 * ⚠ Which UTF-16 / UTF-32 files load at all is decided upstream ({@code cumba-formats}
 * {@code DataSetJsonTableParser} sniffs the first two bytes; measured at P0 of
 * {@code PLAN-bugfix-datatable-readers-views}, 2026-10-16): an <b>uncompressed</b> file loads only
 * when its first byte is {@code '{'} (UTF-16LE / UTF-32LE without a byte-order mark); with a BOM,
 * or big-endian, both paths refuse it with {@code "Unexpected Header: …"}. A DSJC (zlib) file loads
 * in all eight shapes. The refused shapes are deliberately not pinned here: that refusal is not a
 * documented choice.
 * </p>
 */
class DsjNonUtf8EncodingTest
{

    private static final String META = "{\"datasetJSONCreationDateTime\": \"2026-10-16T10:00:00\","
            + " \"datasetJSONVersion\": \"1.1.0\", \"itemGroupOID\": \"IG.U16\", \"name\": \"U16\","
            + " \"label\": \"utf-16\", \"records\": 2, \"columns\": [{\"itemOID\": \"IT.A\","
            + " \"name\": \"A\", \"label\": \"A\", \"dataType\": \"string\"}]";

    private static final String JSON = META + ", \"rows\": [[\"Müller\"], [\"x\"]]}";

    private static final String NDJSON = META + "}\n[\"Müller\"]\n[\"x\"]\n";

    @TempDir
    Path tmp;

    static Stream<Arguments> variants()
    {
        List<Arguments> out = new ArrayList<>();
        for (String layout : List.of("json", "ndjson", "ndjson-parallel"))
        {
            // Uncompressed: only a file whose first byte is '{' reaches Jackson (P0, 2026-10-16).
            for (String cs : List.of("UTF-16LE", "UTF-32LE"))
            {
                out.add(Arguments.of(layout, Charset.forName(cs), false));
            }
        }
        for (String cs : List.of("UTF-16LE", "UTF-16BE", "UTF-32LE", "UTF-32BE"))
        {
            out.add(Arguments.of("dsjc", Charset.forName(cs), true));
            out.add(Arguments.of("dsjc", Charset.forName(cs), false));
        }
        return out.stream();
    }


    @ParameterizedTest(name = "{0} {1} bom={2}")
    @MethodSource("variants")
    void aUtf16FileLoadsAndReportsTheFormatsUtf8(String aLayout, Charset aCharset, boolean aBom)
        throws IOException
    {
        boolean nd = aLayout.startsWith("ndjson");
        String text = nd ? NDJSON : JSON;
        byte[] body = ((aBom ? "﻿" : "") + text).getBytes(aCharset);
        FileInfo fi;
        String ext;
        if ("dsjc".equals(aLayout))
        {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (DeflaterOutputStream z = new DeflaterOutputStream(bos))
            {
                z.write(body);
            }
            body = bos.toByteArray();
            fi = DsjProviderSupplier.FI_DSJ_DSJC;
            ext = ".dsjc";
        }
        else
        {
            fi = nd ? DsjProviderSupplier.FI_DSJ_NDJSON : DsjProviderSupplier.FI_DSJ_JSON;
            ext = nd ? ".ndjson" : ".json";
        }
        Path file = tmp.resolve("u16" + ext);
        Files.write(file, body);
        URI uri = file.toUri();

        DsjTableProvider provider = new DsjTableProvider();
        if ("ndjson-parallel".equals(aLayout))
        {
            provider.setMinBytesForParallel(0L);
            provider.setParallelism(2);
        }
        else if (nd)
        {
            provider.setMinBytesForParallel(Long.MAX_VALUE);
        }
        IDataTable table = provider.provide(uri, fi);
        assertEquals(2L, table.getRowCount());
        assertEquals("Müller", table.getValue(0, 0));
        assertEquals("x", table.getValue(1, 0));
        for (DataTableMeta meta : List.of(table.getMetaData(),
                new DsjTableProvider().provideMetaData(uri, fi)))
        {
            assertEquals("UTF-8", meta.getMetaData(DataTableMetaSupport.META_KEY_ENCODING));
            assertEquals(DataTableMetaSupport.ENCODING_SOURCE_FORMAT,
                    meta.getMetaData(DataTableMetaSupport.META_KEY_ENCODING_SOURCE));
        }
    }
}
